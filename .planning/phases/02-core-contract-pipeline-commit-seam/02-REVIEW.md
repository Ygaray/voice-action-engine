---
phase: 02-core-contract-pipeline-commit-seam
reviewed: 2026-09-30T00:00:00Z
depth: standard
files_reviewed: 48
files_reviewed_list:
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CommandInput.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/Credential.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ProviderId.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/StrategyId.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionKind.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionLedger.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/AwaitingConfirmGate.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/FinishedKind.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/GateStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/HeldProposal.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/PreApplyGate.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/RunTermination.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ToolStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/BudgetBound.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/EscalationReason.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureDetails.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicySource.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/Clarification.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyCapabilities.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/TerminalCall.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/EventDispatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEvent.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEventListener.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TurnRecord.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/Usage.kt
  - config/detekt/detekt.yml
  - scripts/review-api-surface.sh
findings:
  critical: 1
  warning: 7
  info: 7
  total: 15
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-09-30
**Depth:** standard
**Files Reviewed:** 48
**Status:** issues_found

## Summary

I reviewed all 46 main sources of `:core`, `config/detekt/detekt.yml` and `scripts/review-api-surface.sh`. I used the tests only to check whether a behaviour was deliberate.

The structure is mostly sound:
- **Claim and close:** the `commitHeld` claim is a real atomic CAS. `execute` closes the run exactly once from a `finally`. Delivery and close run under `NonCancellable`.
- **Redaction:** every `toString` I read is redaction-safe.
- **Escalation:** the escalation guard reads coordinator counts, not strategy claims.
- **Public surface:** public constructors are consistently `internal`.

The weak point is the never-throw collapse (`Guarded.kt`). Its "rethrow every CancellationException" rule is also applied inside `NonCancellable` regions, where a `CancellationException` cannot be the caller's cancellation. A sink or listener that throws one there makes `execute` throw, loses the outcome and skips the final listener event (CR-01).

The write path has smaller gaps:
- It does not check for cancellation or for the run being closed once past the entry check (WR-01).
- It reads app-supplied properties unguarded, after the write has happened (WR-03).
- `AwaitingConfirmGate.resolve` can report a confirm that is then discarded (WR-02).
- `commitHeld` can hang other callers (WR-04) and reports success when every apply failed (WR-05).
- Trace and usage for a tier cut off by timeout or cancel are lost (WR-06).
- The "never throws" KDoc is broader than what the code does (WR-07).

## Critical Issues

### CR-01: A foreign CancellationException inside a `NonCancellable` region escapes `guarded`, so `execute` throws, the outcome is lost and `RunClosed` is never emitted

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt:34-35`, used at `commit/ApplyStep.kt:34-38`, `pipeline/CommandPipeline.kt:152-158`, `pipeline/HeldCommit.kt:60-67`, `telemetry/EventDispatch.kt:22`

**Issue:**
`guarded` rethrows every non-timeout `CancellationException` unconditionally. That is right in ordinary code. It is wrong in the three places the engine runs app code under `withContext(NonCancellable)`:
- `ActionDelivery.deliver` calls `sink.onAction`.
- `closeRun` calls `sink.onRunClosed`.
- `closeRun` then calls `recorder.runClosed`, which calls `listener.onEvent`.

Under `NonCancellable` the caller's cancellation cannot be what is being thrown. Any `CancellationException` that surfaces there is foreign. Typical sources are `Deferred.await()` on a cancelled deferred, a closed Room or DataStore scope, and a flow collector that was cancelled.

What happens next:
- `closeRun` fails: `execute`'s `finally` block throws. Because `finally` runs after `return outcome`, the thrown `CancellationException` replaces the real outcome. The caller loses a completed or failed outcome even though writes may have been committed. `execute` is documented to throw only for the caller's own cancellation.
- `closeRun` fails: `recorder.runClosed(...)` on the next line is skipped, so the listener's "last event of a run" is never sent.
- `deliver` fails (`ApplyStep.run` line 67, outside the `try`): the exception aborts `applyAll`. Remaining siblings of the batch are never applied, and the strategy sees a cancellation. This contradicts D-08: a sink failure is only a code and the other items still apply.
- `HeldCommit.settle` is partly protected, because it completes `held.result` in a `finally`. The child run's `closeRun` still throws out of `commitHeld`.

`GuardedTest.plainCancellationExceptionWhileActivePropagates` and `NeverThrowTest.aStrategyThrowingCancellationWhileActivePropagates...` pin the "rethrow" rule for ordinary code. No test covers a sink or listener throwing `CancellationException` under `NonCancellable`.

**Fix:**
Give the `NonCancellable` call sites a variant that treats any `CancellationException` as a fault.

```kotlin
// Guarded.kt
@Suppress("TooGenericExceptionCaught")
internal suspend inline fun <T> guardedUncancellable(onFault: (EngineFault) -> T, block: () -> T): T = try {
    block()
} catch (e: LinkageError) {
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
} catch (e: Exception) { // includes every CancellationException: none can be the caller's under NonCancellable
    onFault(EngineFault(errorClassOf(e), timeoutLeak = e is TimeoutCancellationException))
}
```

Use it in `ActionDelivery.deliver`, `closeRun` and `EventDispatch.send` when the caller is inside `NonCancellable`. For `EventDispatch.send` the simplest option is to always use the uncancellable variant, because `PipelineEventListener.onEvent` is not `suspend`. A listener therefore cannot legitimately signal the caller's cancellation. Alternatively, make `guarded` rethrow a `CancellationException` only when `currentCoroutineContext().isActive` is false. If you keep the tested "foreign cancellation while active propagates" behaviour for strategies, restrict that to the strategy and apply-callback sites.

## Warnings

### WR-01: `submit` and `applyWithoutGate` do not check cancellation, and `closed` is only checked at entry, so writes can happen after cancel or close

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt:40-60`

**Issue:**
There are three gaps in the "no apply after close" and "no write after cancel" guarantee.
1. `Mutex.withLock` does not check cancellation when the lock is uncontended, and `gate.admit` or `apply()` may not suspend either. A strategy that swallows `CancellationException` with `catch (e: Exception)`, which is common, can keep calling `session.submit(...)` after the deadline fired or the caller cancelled. Those calls run the gate and `apply()` against a cancelled run.
2. `closed` is read once, right after the lock is acquired. The gate can wait up to 120 s (`AwaitingConfirmGate`). If `close()` runs during that wait, for example from a coroutine a strategy leaked, the admitted mutation is still applied and delivered after the sink's `onRunClosed`.
3. `close()` writes the flag without taking the mutex. The final `snapshotEffects` in `execute`'s `finally` can therefore race an in-flight apply, and the snapshot can miss that apply.

**Fix:**
```kotlin
suspend fun submit(step: ToolStep): DispatchResult = mutex.withLock {
    currentCoroutineContext().ensureActive()
    check(!closed) { "run $runId is closed" }
    ...
}
// in submitMutation, after the gate decides and before applyAll:
check(!closed) { "run $runId is closed" }

// make close() ordered with in-flight writes
suspend fun close() = withContext(NonCancellable) { mutex.withLock { closed = true } }
```
If waiting on a stuck apply in `close()` is unacceptable, keep the flag but add the post-gate re-check. Document that a leaked coroutine's late write is out of contract.

### WR-02: `AwaitingConfirmGate.resolve` can return `true` for an answer that is then discarded

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/AwaitingConfirmGate.kt:105-108, 122-133`

**Issue:**
`resolve` completes `deferred` and returns the result of `complete(confirmed)`. `awaitAnswer` only reads the deferred through `withTimeoutOrNull { await() } == true`. There is a window between the timeout firing (or the caller being cancelled) and the `finally` clearing `active`. `resolve(id, true)` can win `deferred.complete(true)` in that window. It returns `true`, which the KDoc documents as "this call answered the active confirmation". But `withTimeoutOrNull` already returned `null`, so `admit` answers `Hold`. The UI then shows "confirmed" while nothing is applied.

**Fix:**
Make the deferred the single source of truth by settling it after the timeout.
```kotlin
val answer = withTimeoutOrNull(timeoutMillis) { deferred.await() }
if (answer == null) deferred.complete(false)   // loses to a racing resolve(true)
return deferred.await()                         // already completed, returns the winner
```
Wrap this in the existing `try/finally`. If the caller is cancelled, call `deferred.complete(false)` in the `finally` so a racing `resolve` returns `false`.

### WR-03: App-supplied `PendingMutation` properties are read outside any guard, after the write, and in the cancel path

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt:76-87, 89-95`; also `commit/CommitCoordinator.kt:108-118`

**Issue:**
`recordOutcome` reads `mutation.toolName`, `mutation.targetIds` and `mutation.context` after `apply()` has already run. `PendingMutation` is an app interface, so these are arbitrary getters and may be lazily computed snapshots. If one throws:
- The write has happened, but no action is recorded or delivered, so the undo journal never hears about it.
- The exception escapes `ApplyStep.run`. The strategy sees an unexpected failure, and `appliedCount` stays 0, so the escalation guard may let a later tier repeat the write.

In `journalCancelled`, the same getters run under `NonCancellable`. A throwing getter replaces the in-flight `CancellationException`, so `execute` no longer reports a cancellation.

`CommitCoordinator.hold` has the same problem with `mutation.toolName`, `targetIds` and `context`, and it runs after `addHeld`, so it can leave a held proposal with only some of its actions recorded.

**Fix:**
Snapshot the descriptors once, inside a guard, before calling `apply()`.
```kotlin
val details = guarded(onFault = { recorder.recordCode(TraceCode.APPLY_ERROR); ActionDetails(FALLBACK_NAME, null, emptyMap(), null) }) {
    ActionDetails(mutation.toolName, null, mutation.targetIds, mutation.context)
}
```
Then merge `result.targetIds` and the token into that snapshot after `apply()`. Do the same snapshot step in `hold`. Taking the snapshot before `apply()` also fixes the semantics for "context = snapshot taken before the change".

### WR-04: A throw before the `try` in `HeldCommit.runChild` leaves the proposal claimed forever and hangs every other caller

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt:33-40`

**Issue:**
`resolve` wins the CAS (`claimed = true`) and calls `runChild`. `runChild` calls `runIds()`, then the `RunRecorder` constructor (which calls `clock()`), then the `CommitCoordinator` constructor. All of this is before the `try`. Both lambdas are replaceable builder hooks (`runIds`, `clock`). If either throws:
- `commitHeld` throws a non-cancellation exception, despite "never throws".
- `held.result` is never completed. Every later or concurrent `commitHeld` for the same proposal suspends forever in `held.result.await()`, and the proposal can never be retried.

`CommandPipeline.execute` lines 45-47 have the same unguarded setup. There the only effect is a throw instead of a typed failure.

**Fix:**
Move the setup inside the guarded region. Alternatively, wrap the whole body so that `result` is always completed.
```kotlin
suspend fun resolve(held: HeldProposal, mutations: List<PendingMutation>): CommandOutcome {
    if (!held.claimed.compareAndSet(false, true)) return held.result.await()
    return try { runChild(held, mutations) } catch (e: Throwable) {
        held.result.complete(CommandOutcome.Failed(/* effects, */ FailureReason.Unexpected(errorClassOf(e)), null))
        throw e
    }
}
```
Or resolve the ids and clock readings up front in a `guarded` block that maps a fault to `Failed(Unexpected)` before the claim is taken.

### WR-05: `commitHeld` reports `Completed` even when every apply threw or errored, and accepts an empty `amended` list that uses up the proposal

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt:44-47`, `pipeline/CommandPipeline.kt:75-79`, `commit/PreApplyGate.kt:22`

**Issue:**
1. The child run ignores the `DispatchResult` of `applyWithoutGate`. It always returns `Completed(partial = false)`, even if every mutation threw and the run's only actions are `is_error`. A UI that switches on `Completed` shows success, and `commits` is empty. Regular `execute` collapses failures into `Failed`.
2. `commitHeld(held, amended)` and `GateDecision.Admit(amended)` do not enforce the "at least one mutation" rule that `ToolStep.Mutation` enforces. An empty list claims the proposal, applies nothing, and returns `Completed`. The held changes are then permanently unreachable, because the proposal is used up.
3. The `amended` list is stored by reference, not copied, so the caller can mutate it after the call.
4. A child run has no engine deadline. `TierPolicy.commandTimeoutMillis` is never applied to `commitHeld`, so a hanging `apply()` holds the claim and all waiters forever.

**Fix:**
Surface the failure by returning `Failed(ToolFailure(), null)` when the dispatch `isError` and `commits` is empty. Add `require(amended.isNotEmpty())` and `amended.toList()` before the CAS, so a bad argument does not use up the proposal. Apply the policy deadline to the child run, or document that none exists.

### WR-06: A tier cut off by the engine deadline or by cancellation never reaches the trace, so its turns and usage are lost

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt:107-108, 122-131`, `telemetry/RunRecorder.kt:77-98`

**Issue:**
`tierFinished` is the only place the in-flight tier's `currentTurns` are turned into a `TierAttempt`. On `Failed(Timeout)` (via `timedOut`), on cancellation, and on the `collapsed` path, no attempt is recorded for the running tier. `CommandTrace.usage` is computed from `attempts` only. The tokens of those turns were counted in `recorder.tokensUsed` but never appear in `trace.attempts` or `trace.usage`. That is an under-count of paid tokens in exactly the runs that are most costly. The timed-out tier also has no latency entry, so the trace shows `tierStarted` with no matching finish.

**Fix:**
Add `RunRecorder.flushInFlight(outcome: String)`. Call it from `timedOut`, `collapsed` and the cancellation branch, before the snapshot. It turns `currentTurns` into an attempt with outcome `"timeout"`, `"cancelled"` or `"failed"`, but only if a tier has been started and not finished.

### WR-07: KDoc says `execute` "never throws", but `Error`s and a throwing `runIds`/`clock` still escape

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt:39-43, 45-47`, `internal/Guarded.kt:25`

**Issue:**
`guarded` deliberately catches only `Exception` and `LinkageError`. `NotImplementedError` (from `TODO()`), `AssertionError` and `StackOverflowError` are not caught. `StackOverflowError` is plausible in recursive parsing of deep model JSON. The run is still closed once, with `Failed(Unexpected("Error"))` for the sink. The caller gets an exception, and the public KDoc and README-level contract say "It never throws … only cancellation propagates". The doc and the code disagree. `runIds()` and `clock()` are also called before the `try`, so a throwing hook escapes before any run bookkeeping exists. `GuardedTest.assertionErrorIsNotCaught` shows the `Error` behaviour is intended, but the public contract text is not updated.

**Fix:**
Either update the KDoc on `execute`, `commitHeld` and `commandPipeline` to say "except for `Error`s and the caller's cancellation", or also catch the recoverable `Error` subclasses (`NotImplementedError`, `AssertionError`, `StackOverflowError`) and rethrow only `VirtualMachineError`s such as OOM. Move `runIds()` and the recorder construction inside the `try`.

## Info

### IN-01: Token ceiling, iteration limit and allowed providers are advisory only

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt:11-24`, `strategy/CommandSession.kt:35-40`, `pipeline/PolicyPreCheck.kt:73-81`

**Issue:**
The `TierPolicy` KDoc reads "the most tokens one run may use" and "the providers a tier may use". The engine enforces none of these. `recordTurn` only sums tokens. `allowedProviders` filters on a tier's static declaration, and `ANY_PROVIDER` includes `ON_DEVICE`, so it passes any filter. `commandTimeoutMillis` defaults to null, so there is no engine deadline by default. A strategy that ignores `session.policy` is unbounded. `CommandSession` does document that strategies enforce these limits, but the policy KDoc does not.

**Fix:**
Say "advisory; enforced by the strategy" on those `@property` lines. Optionally have `recordTurn` cancel the tier, or have the tier walk fail with `BudgetExceeded(TOKENS)`, when `tokensUsed > policy.tokenCeiling`.

### IN-02: Dead code in main sources

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt:14`, `commit/ActionLedger.kt:67`, `telemetry/RunRecorder.kt:114`

**Issue:**
- `applyErrorContent()` is unused in main. `ApplyStep` uses the constant directly, so the function exists only for tests.
- `ActionLedger.committed()` is unused in main.
- `RunRecorder.cacheNotEngaged` is unused in main. It is plausibly staged for later phases.

**Fix:**
Delete the first two, or use them. Mark `cacheNotEngaged` with a short comment naming the phase that will call it.

### IN-03: `PolicyPreCheck` swallows an on-device availability fault without any trace code

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt:40-41`

**Issue:**
`guarded(onFault = { false })` turns a throwing availability probe into "unavailable" silently. The user then sees `ProviderUnavailable(on_device_unavailable)` with no hint that the probe crashed. Every other fault path records a `TraceCode`.

**Fix:**
Record a code from `onFault`, for example a new `TraceCode.ON_DEVICE_PROBE_ERROR`. This is additive. Alternatively, reuse `STRATEGY_ERROR`.

### IN-04: The returned outcome's trace misses codes recorded while closing the run

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt:59-63`

**Issue:**
`snapshotEffects` is taken before `closeRun`. A `sink_error` from `onRunClosed` and a `listener_error` from the `RunClosed` event are recorded after the snapshot. The returned `CommandOutcome.trace` and `RunTermination.trace` therefore never show them, even though the trace is documented as "what the run did".

**Fix:**
Document it, or take the outcome's trace after `closeRun` for the returned outcome. That would need the effects to be built lazily.

### IN-05: Minor duplication and unvalidated mutable inputs in the failure and gate types

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/EscalationReason.kt:65-71`, `failure/FailureReason.kt:223-229`, `failure/FailureDetails.kt:32`, `commit/PreApplyGate.kt:22`

**Issue:**
- `HASH_PRIME` is declared in three files, and `describe` in two files, with identical bodies.
- `GateDecision.Admit.amended` and `FailureReason.ProviderUnavailable.cause` are stored without a defensive copy or format check. The KDoc claims `cause` is "a stable code (never a message)", but nothing enforces that. A message-like string would reach `toString` and the logs.

**Fix:**
Hoist the shared constants and the helper into one `internal` file in the package. Copy `amended` with `.toList()`. Optionally validate `cause` against `[a-z0-9_]+`.

### IN-06: `Usage` sums can overflow silently

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/Usage.kt:33-41`, `telemetry/RunRecorder.kt:71`

**Issue:**
`total`, `plus` and `tokenTotal +=` use plain `Long` addition. They can wrap to negative with adversarial provider-reported counts. A wrapped value would also disable a strategy's `tokensUsed > tokenCeiling` check.

**Fix:**
Use saturating addition (`Math.addExact` with a clamp to `Long.MAX_VALUE`) in `plus` and the recorder.

### IN-07: `scripts/review-api-surface.sh` comment is inaccurate and one check is brittle

**File:** `scripts/review-api-surface.sh:36, 69-72`

**Issue:**
- The comment "full tree plus build outputs" is wrong. `git ls-files -co --exclude-standard` excludes ignored build directories.
- The `public static field` check will fail on any future `public const val` (it appears as `field public static final`), even though that is legitimate additive API. The failure message only says "leaked".
- The `decl_lines` regex is substring-based (`class|interface|enum` after any prefix). It could match a member line that does not start with `method|field|ctor|property`, such as an annotation line.

**Fix:**
Correct the comment. In the failure message, say that `const val` also trips the check. Anchor the declaration regex on a visibility prefix (`^[[:space:]]*(public|protected)[[:space:]]`).

---

_Reviewed: 2026-09-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
