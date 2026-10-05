# Phase 2: Core Contract, Pipeline & Commit Seam - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 41 new/modified (inventory from 02-RESEARCH.md "Public versus internal inventory" + Recommended Project Structure)
**Analogs found:** 14 in-repo or SB port analogs / 41; the rest have no code analog (pure new contract types, use RESEARCH shapes)

Base path `CORE` = `/home/yahir/Projects/Reusable/android/voice-action-engine/core/src`.
`SB` = `/home/yahir/Projects/AndroidApps/Personal/SecondBrain/app/src/main/java/com/example/secondbrain/core/agent` (all four files git-tracked; read-only port source).
Phase 1 `:core` has only 5 files, so in-repo analogs are conventions (style, explicit API, test shape), not domain code.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `CORE/main/.../core/CoreModule.kt` (DELETE) | placeholder | n/a | self (its KDoc says delete) | n/a |
| `core/{CommandInput,ProviderId,StrategyId,Credential}.kt` | model / value | request-response | none in repo; `RecordingSink.kt` for KDoc + explicit-API style | style only |
| `core/pipeline/CommandPipeline.kt` + `PipelineBuilder.kt` | service / DSL | request-response | none; RESEARCH Pattern 1 | no analog |
| `core/pipeline/{TierSelector,TierPolicy,CommandOutcome}.kt` | model | transform | none; RESEARCH Pattern 5 | no analog |
| `core/pipeline/TierWalk.kt` (internal) | service | batch (ladder walk) | none; RESEARCH Pattern 3/4 | no analog |
| `core/strategy/{CommandStrategy,StrategyOutcome,CommandSession,StrategyCapabilities}.kt` | interface / model | request-response | none | no analog |
| `core/strategy/{ToolSpec,TerminalCall,Clarification}.kt` | model | transform | none (Phase 3 also touches ToolSpec, O1) | no analog |
| `core/commit/ToolStep.kt`, `PendingMutation.kt` | model | event-driven | SB `AnthropicToolRegistry.kt:27-32` | role-match |
| `core/commit/{PreApplyGate,GateDecision,CommitProposal}.kt` | interface | request-response | SB `MutationGate.kt:1-41` | exact (shape) |
| `core/commit/AwaitingConfirmGate.kt` | service | event-driven | SB `VoiceConfirmGate.kt` | exact |
| `core/commit/{CommitSink,ActionEvent,ActionKind,RunTermination,HeldProposal}.kt` | interface / model | pub-sub | SB `AgentLoopResult.kt:56-111` (ExecutedToolCall) | role-match |
| `core/commit/CommitCoordinator.kt` (internal, split) | service | CRUD/event-driven | SB `AnthropicToolRegistry.kt` dispatch (~236-242) | role-match |
| `core/failure/{FailureReason,EscalationReason,FailureDetails}.kt` | model | transform | none | no analog |
| `core/telemetry/{CommandTrace,Usage,PipelineEvent,PipelineEventListener}.kt` | model | pub-sub | SB `AnthropicAgentLoop.kt:284-285` (usage sum) | partial |
| `core/internal/Guarded.kt` (+ RunRecorder, Clock/IdGen) | utility | request-response | SB `VoiceConfirmGate.kt` captureSnapshot catch order; RESEARCH Pattern 1 | partial |
| `CORE/testFixtures/.../core/testing/{ScriptedStrategy,ScriptedGate,RecordingCommitSink,FakeClock}.kt` | test fixture | event-driven | `CORE/testFixtures/.../testing/ScriptedResponses.kt`, `RecordingSink.kt` | exact |
| `CORE/test/.../core/*Test.kt` (one per requirement cluster, surface-lint, canary, exit-path tests) | test | request-response | `CORE/test/.../core/ScriptedHarnessTest.kt` | exact |
| `core/build.gradle.kts`, `config/detekt/detekt.yml` (only if a rule needs tuning) | config | n/a | existing files | exact |

## Pattern Assignments

### All new main files under `CORE/main` (conventions from Phase 1)

**Analog:** `CORE/testFixtures/.../testing/RecordingSink.kt` and `NoNetworkGuard.kt` (same explicit-API discipline applies to main).

- Package root `io.github.ygaray.voiceactionengine.core` (+ `.pipeline`, `.strategy`, `.commit`, `.failure`, `.telemetry`, `.internal`); fixtures stay in `...core.testing`.
- `kotlin { explicitApi() }` (`core/build.gradle.kts:16-25`): every declaration needs explicit `public`/`internal` and explicit return types.
- Every public member gets KDoc (Phase 1 style): one sentence on behavior, limits called out, e.g. `/** Immutable snapshot of the events recorded so far; later recordings do not change a snapshot already taken. */`
- Constants: top-level `private const val` (not `private companion object`; Phase 1 test uses companion only in tests, which is fine there since tests are not in `api.txt`). Named constants for 6 / 60_000 / 4_096 / 120_000L (detekt MagicNumber active in main).
- Comments must not contain `T-<n>-<n>`, `WR-<n>`, `Phase <n> D-<n>` (scanner `gradle/invariants.gradle.kts:14,31,168`, main only). Strip when porting SB comments (SB cites `T-166-05`, `Phase 166`, `IN-01`).
- No `runCatching`, `println`, `printStackTrace`, `android.util.Log`, `okhttp3.internal.*`; no `data class`.

### `core/commit/PreApplyGate.kt`, `GateDecision.kt` (interface, request-response)

**Analog:** `SB/MutationGate.kt:20-41`

```kotlin
fun interface MutationGate {
    suspend fun admit(toolName: String, input: JsonObject?): MutationGateDecision
}
const val MUTATION_HELD_OUTCOME = "held"
const val HELD_FOR_CONFIRMATION_CONTENT = """{"applied":false,"status":"held_for_confirmation"}"""
```
Adapt: `public fun interface PreApplyGate { public suspend fun admit(proposal: CommitProposal): GateDecision }`; `GateDecision` sealed with `Admit(amended)` / `Hold(reason: Any?, appOutcomeToken: String?)` (SB's `Admit`/`Hold` are data objects; here regular classes). Keep the held JSON as `internal const val HELD_FOR_CONFIRMATION` byte-identical; add golden-equality test.

### `core/commit/AwaitingConfirmGate.kt` (service, event-driven)

**Analog:** `SB/VoiceConfirmGate.kt` (class ~L50-150). Copy these exactly, drop Hilt `@Singleton/@Inject`, SB-specific `ConfirmSubject`/snapshot source (replace with opaque app policy + post-confirm amend hook, D-07).

Redaction pattern (L34 region):
```kotlin
override fun toString(): String = "PendingConfirmation(id=$id, subject=${subject::class.simpleName})"
```
Mutex + deferred + timeout + fail-closed (L132-147):
```kotlin
private suspend fun awaitConfirmation(subject: ConfirmSubject): MutationGateDecision = awaitMutex.withLock {
    val id = nextConfirmationId.incrementAndGet()
    val deferred = CompletableDeferred<Boolean>()
    active.set(id to deferred)
    _pendingConfirmation.value = PendingConfirmation(id, subject)
    try {
        val confirmed = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() }
        if (confirmed == true) MutationGateDecision.Admit else MutationGateDecision.Hold
    } finally {
        active.set(null)
        _pendingConfirmation.value = null
    }
}
companion object { const val CONFIRM_TIMEOUT_MS = 120_000L }   // -> top-level private const val in the port
```
Cancellation rule: a `CancellationException` while pending is never converted to a decision; only `confirmed == true` admits. `resolve(id, confirmed)` with stale id or second call is a no-op. Tests use `runTest` + `advanceTimeBy(120_000)`.

Catch-order excerpt to reuse inside the ONE guarded helper (SB `captureSnapshot`, L~110):
```kotlin
} catch (e: CancellationException) { throw e } catch (e: Exception) { ... }
```
In the port, the broad catch exists only in `internal/Guarded.kt` (single `@Suppress("TooGenericExceptionCaught")`); AwaitingConfirmGate must not add its own.

### `core/commit/ToolStep.kt`, `PendingMutation.kt`, `CommitCoordinator.kt`

**Analog:** `SB/AnthropicToolRegistry.kt:27-32` and dispatch ~236-242.
```kotlin
sealed interface ToolStep {
    data class Finished(val execution: ToolExecution) : ToolStep
    class Mutate(val apply: suspend () -> ToolExecution) : ToolStep
}
// dispatch
when (gate.admit(spec.name, input)) {
    MutationGateDecision.Hold -> { held = true ... }
    MutationGateDecision.Admit -> step.apply()
}
```
Adapt: `ToolStep` sealed {`Finished(kind)`, `Mutation(PendingMutation)`} as regular classes (no `data`), public ctors; `Finished` carries `FinishedKind` (D-12). Gate is called only for `Mutation`. Coordinator follows RESEARCH Pattern 2; SB registry is also the source for "cancel while gate suspended records nothing" (registry rethrows `CancellationException`).
Split coordinator per detekt (TooManyFunctions 11, ReturnCount 2, LongMethod 60): gate step / apply step / recorder / held registry in separate files.

### `core/commit/{ActionEvent,ExecutedAction}.kt`, `core/pipeline/CommandOutcome.kt`

**Analog:** `SB/AgentLoopResult.kt:56-111`
```kotlin
data class ExecutedToolCall(val toolUseId: String, val toolName: String, val isError: Boolean, val mutating: Boolean,
    val targetIds: Map<String, String>, val outcome: String?, val preMutationSnapshot: PreMutationSnapshot? = null)
sealed interface AgentLoopResult { val executedTools: List<ExecutedToolCall> ... Done, Unavailable, BudgetExceeded }
```
Adapt (D-11): `ExecutedAction(position, kind, applied, appOutcomeToken, toolName, mutating, targetIds, snapshot/context: Any?)` with internal ctor, public getters, no `data`; `position` replaces `toolUseId` as identity. Parent `CommandOutcome` exposes `executed`/`commits`/`held`/`trace` on every leaf, mirroring "effects on every variant".

### `core/telemetry/Usage.kt`

**Analog:** SB `AnthropicAgentLoop.kt:284-285` (total = `inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens`). Fields `inputUncached, cacheRead, cacheWrite, output`; `total` = sum of all four. Loop constants at `AnthropicAgentLoop.kt:421,429,432` (6 / 4096 / 60_000) become policy defaults.

### `core/internal/Guarded.kt` (utility)

No repo analog; use verified prototype in RESEARCH Pattern 1 (catch order: `TimeoutCancellationException` with `isActive` check, `CancellationException` rethrow, `LinkageError`, `Exception`). Lifecycle `try { ... } finally { withContext(NonCancellable) { sink.onRunClosed(...) } }`.

### Fixtures: `CORE/testFixtures/.../testing/*.kt`

**Analog:** `ScriptedResponses.kt` and `RecordingSink.kt` (both `public` classes, KDoc'd, thread-safe, fail loudly).
```kotlin
public class RecordingSink<T> {
    private val recorded = CopyOnWriteArrayList<T>()
    public val events: List<T> get() = recorded.toList()
    public fun record(event: T) { recorded.add(event) }
    public fun clear() { recorded.clear() }
}
```
Script exhausted throws `IllegalStateException("script exhausted ...")`. New fakes (`ScriptedStrategy`, scripted gate, recording `CommitSink` built on `RecordingSink<ActionEvent>`, fake clock/id generator) follow the same: public, explicit API, deterministic, exhaustion fails loudly. Keep them free of `runCatching` (testFixtures are detekt-linted; Phases 4-9 reuse). Wrap pipeline tests in `NoNetworkGuard.during { }`.

### Tests: `CORE/test/.../core/*Test.kt`

**Analog:** `ScriptedHarnessTest.kt`. Conventions: JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`, `assertThrows`), `runTest` from coroutines-test, hand-written stand-ins, constants in `private companion object { const val THREE = 3 ... }` (MagicNumber excluded for tests but repo uses named constants anyway), one test class per cluster, `NoNetworkGuard.assertNoHttpStackOnClasspath()` kept. The Phase 1 `StandInPipeline` is test-local; it can be removed/replaced by the real pipeline once available (its KDoc says placeholders never ship to main).
Exit-path tests (GATE-05): five tests, one per path, each asserts `onRunClosed` called exactly once (cancel test uses `Job.cancel()` with a suspending sink).

### Build/config (modify only if needed)

`core/build.gradle.kts:16-25` (explicitApi, JVM 11, `-Xjdk-release=11`), detekt config `config/detekt/detekt.yml` (constructorThreshold 8, ignoreDefaultParameters true). Tune rules with a one-line justification, never add a baseline. Do not commit `api.txt`; dump in an isolated copy at the phase gate. No new dependencies; `verifyNoTestFixturesPublished` must keep passing (don't expose fixtures in published component).

## Shared Patterns

### Redacted toString
**Source:** `SB/VoiceConfirmGate.kt:~34`. **Apply to:** every public type holding reply, carry, context, Hold reason, snapshot, TerminalCall args, CommandInput (print lengths/ids/class names only). Canary test sweeps all.

### Single broad catch
**Source:** RESEARCH Pattern 1 + SB `captureSnapshot` catch order. **Apply to:** gate call, `apply()`, sink, listener, policy source, top-level collapse; all route through `internal/Guarded.kt`.

### Cancellation contract
**Source:** `SB/VoiceConfirmGate.kt` KDoc (cancel while pending propagates, no record). **Apply to:** gate step, coordinator, AwaitingConfirmGate; deliver `onAction`/`onRunClosed` under `withContext(NonCancellable)`.

### API-shape rules (frozen surface)
**Source:** RESEARCH Pattern 5. **Apply to:** all public types: sealed only for StrategyOutcome, CommandOutcome, RunTermination, GateDecision, ToolStep; value classes not enums; internal ctors for engine-produced types; builders/overloads for growing inputs; default bodies on new seam-interface members.

## No Analog Found

Planner should use RESEARCH.md shapes/prototypes: `CommandPipeline`/DSL/`TierSelector`/`TierPolicy`/`TierWalk`, `CommandStrategy`/`CommandSession`/`StrategyOutcome`, `ToolSpec`/`TerminalCall`/`Clarification`, `FailureReason`/`EscalationReason` taxonomies, `CommandTrace`/`PipelineEvent`, `ProviderId`/`StrategyId`/`Credential`, `RunTermination`, surface-lint test. CalTracker port source (`VoiceLogViewModel.kt:839-880`, skip-and-count) is behavioral evidence for D-08 only (counted by is_error events), not code to copy.

## Metadata

**Analog search scope:** this repo `core/`, SecondBrain `core/agent/` (4 files, tracked via `git ls-files --error-unmatch`). No gitignored mirror paths used.
**Files scanned:** 5 in-repo, 4 SB, plus RESEARCH.md lines 1-423 (remaining lines 424-602 not read; they hold assumptions, open questions and Validation Architecture, already summarized upstream).
**Pattern extraction date:** 2026-09-30
