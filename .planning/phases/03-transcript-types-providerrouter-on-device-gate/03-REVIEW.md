---
phase: 03-transcript-types-providerrouter-on-device-gate
reviewed: 2026-09-30T00:00:00Z
depth: standard
files_reviewed: 47
files_reviewed_list:
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/AiProvider.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CacheDetector.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CachingMode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CredentialSource.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilities.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilityTable.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelResult.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/OnDeviceCapability.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderCall.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderSelection.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TurnRecord.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/AssistantPart.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelResponse.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/NativeReplay.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/KeyIsolationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NativeReplayTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SeamTypesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolSpecClarificationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeAiProvider.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedSources.kt
  - scripts/review-api-surface.sh
findings:
  critical: 0
  warning: 8
  info: 3
  total: 11
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-09-30
**Depth:** standard
**Files Reviewed:** 47 (all main sources, fixtures and the surface script read in full; tests read selectively: KeyIsolation, RedactionCanary, NoHardCodedConstants, ApiShape, ModelCapabilityTable, plus ModelRouter, ProviderRouter, OnDeviceGate and CacheNotEngaged by test inventory and targeted reads)

## Summary

The routing core is sound on the points the focus list called out. Verified by tracing, not assumed:

- **Refusal ordering** in `ModelRouter.resolve` is selection, tier/policy gate, on-device probe, registration, credential (for that provider only), capabilities. No path reaches `source.credential(...)` before the gate, and no path reaches `AiProvider.complete` after a `Step.Stop`.
- **Key isolation**: the `Credential` lives only in the private `Binding`. `present()` re-checks the stamped provider against the registered provider id. Every `toString` on the new types (`BoundModel`, `ProviderCall`, `CredentialLookup`, `ProviderSelection`, `SelectionRequest`, `ModelRequest`, `Message` family, `NativeReplay`) prints ids, counts and lengths only.
- **Frozen handle**: `RunSession.model()` uses a double-checked `@Volatile` slot plus a `Mutex`, and assigns the slot only after `bind` returns. A cancelled bind freezes nothing.
- **Cache arithmetic**: the `Long` accumulation, the saturating prompt-total cap, the truncating division and the `>= minimum` boundary are all correct.

No BLOCKER was provable. The findings below are contract mismatches, an additivity trap that becomes unfixable at the `v1.0.0` tag, one cache-diagnostic false positive, and consistency gaps in the redaction invariant.

## Warnings

### WR-01: A foreign `CancellationException` from app code escapes `complete()` / `execute()`, contradicting the new "never throws" contracts

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt:53-55` (surfaced through `provider/BoundModel.kt:100-108`, `pipeline/CommandPipeline.kt:102-104`)
**Issue:** `BoundModel.complete` is documented as "never throws, except for the caller's own cancellation", and `CommandPipeline.execute` as throwing only "for the caller's own cancellation or a JVM Error". But `guardedCore(cancellationIsFault = false)` rethrows every non-timeout `CancellationException` without asking whether the caller is actually cancelled. A provider or source that throws a plain `CancellationException` while the caller is still active leaks out. Typical triggers are a `Call.await()` bridge whose call was cancelled by an OkHttp dispatcher shutdown, or an awaited `Deferred` that was cancelled elsewhere. It propagates out of `complete()`, through the strategy, and `TierWalk.executeGuarded` rethrows it too. `drive()` then treats it as `cancelled = true`, closes the run as `RunTermination.Cancelled`, and `execute()` throws to a caller that never cancelled. `GuardedTest.plainCancellationExceptionWhileActivePropagates` pins this as intended. However, the Phase-3 call sites (`select`, `credential`, `onDeviceProbe`, `provider.complete`, `capabilities`) make the "only the caller's own cancellation" promise load-bearing in public KDoc, and the `:providers` transports will hit this path first.
**Fix:** Apply the same liveness test the timeout branch already uses, so only a real caller cancellation is rethrown:
```kotlin
} catch (e: CancellationException) {
    if (!cancellationIsFault && !currentCoroutineContext().isActive) throw e
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
}
```
Update `GuardedTest` to expect a fault while the context is active and a rethrow when it is cancelled. If the current behavior is deliberate, correct the KDoc on `BoundModel.complete`, `CommandPipeline.execute` and `CommandSession.model`.

### WR-02: `ToolSpec` keeps Kotlin default arguments, which the project's own growth rule says freezes the constructor; this phase added another default

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt:42-49`; guard gap at `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt:153`
**Issue:** `ApiShapeTest.hasDefaultArgumentStub` documents the rule: a class with a synthetic `(…, int, DefaultConstructorMarker)` stub "cannot grow a parameter without removing a constructor". `@JvmOverloads` keeps the Java-visible constructors but not the synthetic stub that Kotlin call sites compile against. A Kotlin consumer built against `v1.0.0` that writes `ToolSpec(name, desc, schema, mutating = true)` binds to `<init>(String,String,JsonObject,boolean,boolean,Boolean,int,DefaultConstructorMarker)`. The next additive field removes that signature and throws `NoSuchMethodError` at runtime. `ToolSpec` is the class every consumer constructs most often, and this phase just extended it from 5 to 6 parameters (`strict`), so the stub has already changed once. `GROWTH_PACKAGES = listOf("transcript", "provider")` excludes `strategy`, so the lint cannot see it. The reflection test `toolSpecKeepsItsFiveArgumentConstructor…` covers only the Java-visible constructors. This violates §11 rule 2 (strictly additive) as soon as the type needs a seventh field, and tags are immutable.
**Fix:** Before the `v1.0.0` cut, replace defaults with explicit secondary constructors (the pattern already used for `TurnRecord`, `ModelResult.Failure` and `ModelRequest`), or move optional attributes to a builder:
```kotlin
public class ToolSpec(
    public val name: String, public val description: String, public val inputSchema: JsonObject,
    public val mutating: Boolean, public val terminal: Boolean, public val strict: Boolean?,
) {
    public constructor(name: String, description: String, inputSchema: JsonObject) :
        this(name, description, inputSchema, false, false, null)
    public constructor(name: String, description: String, inputSchema: JsonObject, mutating: Boolean) :
        this(name, description, inputSchema, mutating, false, null)
    public constructor(name: String, description: String, inputSchema: JsonObject, mutating: Boolean, terminal: Boolean) :
        this(name, description, inputSchema, mutating, terminal, null)
}
```
Add `"strategy"` (and `"pipeline"`, `"telemetry"`, `"failure"`) to `GROWTH_PACKAGES`. `CommandInput` and `CommitSink.kt:55` have the same shape from earlier phases and should be handled in the same pass.

### WR-03: Automatic-caching diagnostic can raise a false `CacheNotEngaged` for concurrent first requests

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt:110, 115-120` with `CacheDetector.kt:60-61`
**Issue:** The turn number is taken after the response, with `successfulResponses.incrementAndGet()`. For `CachingMode.AUTOMATIC` the rule is "an earlier request must have written the entry", so turn 2 is judged. Two requests that are in flight together (parallel tool-result turns, or two coroutines sharing the handle, which the class KDoc explicitly allows) both start before any cache entry exists. When both succeed with `cacheRead == 0`, the second to finish gets turn 2 and is flagged, although no earlier request had completed when it was sent. The event then tells the app its cache is broken when nothing is wrong. `twoConcurrentCompletesOnOneHandleRecordExactlyTwoTurns` does not use a caching model, so the case is untested.
**Fix:** Snapshot the count before sending and judge on completed predecessors, not on completion order:
```kotlin
val priorSuccesses = successfulResponses.get()          // before binding.provider.complete(call)
...
if (result is ModelResult.Success) {
    successfulResponses.incrementAndGet()
    reportMissedCache(request, result.response.usage, priorSuccesses + 1)
}
```
Two overlapping first calls both see 0 and are never judged.

### WR-04: Credential-source and capability faults are mapped to misleading reasons; the leaked-timeout signal is dropped

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt:181-186, 196-201, 140`
**Issue:** `select` converts an `EngineFault` through `unexpectedOrTimeout`, so a leaked timeout becomes `Timeout`. The other guarded sites discard the fault. `lookUp` uses `onFault = { null }` and always answers `CredentialUnreadable(id, "source_error")`. The type's own KDoc says that reason means "the key exists but cannot be read; ask the user to re-enter it". A transient storage exception (a closed DataStore, an IO error) therefore tells the user to retype a key that is fine, and a leaked inner timeout is reported as an unreadable key. `capabilities` maps every fault to `Unexpected`, so a leaked timeout there becomes `Unexpected("TimeoutCancellationException")`, inconsistent with `select`. `ModelRouterTest.anUnreadableKeyAndAThrowingCredentialSourceAreDistinctFromMissing` pins the current mapping, so this is a semantic choice, not an accident. It is user-visible, though, and hard to reverse after the tag.
**Fix:** Keep `CredentialUnreadable` for `CredentialLookup.Unreadable` only. Report a throwing source as `Unexpected(errorClass)` (or `Timeout()` when `timeoutLeak`), keeping the `CREDENTIAL_SOURCE_ERROR` trace code. Route the `capabilities` fault through `unexpectedOrTimeout` as well:
```kotlin
val lookup = guarded<Step<CredentialLookup>>(
    onFault = { Step.Stop(TraceCode.CREDENTIAL_SOURCE_ERROR, unexpectedOrTimeout(it)) },
) { Step.Go(source.credential(id)) }
```

### WR-05: App-supplied `clock` is called outside any guard in `complete()` and under the recorder lock

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt:99, 109`; `telemetry/RunRecorder.kt:49-50, 82, 97, 144`
**Issue:** `PipelineBuilder.clock` is a public app-replaceable `() -> Long`. `RoutedModel.complete` calls `clock()` twice outside `guarded`. A throwing clock makes `complete()` throw, contrary to its KDoc, and it does so after the provider call has already been made and billed. `RunRecorder` calls `clock()` inside `synchronized(lock)`, which contradicts its own header ("app code never runs while the lock is held"). A blocking or re-entrant clock stalls every recorder operation. `CommandPipeline.execute` guards only the first clock read (inside `startRun`).
**Fix:** Read the clock before taking any lock and inside a guard (for example `val now = guarded(onFault = { 0L }) { clock() }`). In `RoutedModel.complete`, take `started` inside the same collapse that wraps the provider call, so a clock fault becomes a typed `Failure` instead of an escape. Alternatively wrap `clock` once in `PipelineBuilder.build()` with a fallback to the last good reading, so every consumer gets a total function.

### WR-06: Free-text escape hatches break the "reasons and details never carry free-form text" invariant

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt:230, 238-240`; `failure/FailureDetails.kt:12-30`; `transcript/ModelResponse.kt:14-18, 25-27`
**Issue:** The file header says "Codes and nested values are never free-form messages, so a reason cannot carry a transcript, a key or a response body." This phase added `isStableCode` enforcement to `CredentialUnreadable.cause`, `ProviderUnavailable.cause`, `Unreadable.cause`, `Unavailable.code` and `StopReason`, but left the routing path open elsewhere:
- `FailureReason.Other(code)` only checks non-blank, and its code reaches `toString`, the trace and event streams verbatim.
- `FailureReason.Unexpected(errorClass)` has a public constructor with no validation.
- `FailureDetails.providerErrorType` and `requestId`, and `ModelResponse.requestId`, are server-supplied strings printed unfiltered by `toString`. A hostile or buggy gateway (OpenRouter-style proxies echo user content in error fields) can put prompt text, a key fragment or a log-injection newline into them.

The RedactionCanary test never feeds hostile text through these slots, so the invariant is untested exactly where the `:providers` transports will write.
**Fix:** Validate at construction. Require `isStableCode`, or a looser `[A-Za-z0-9_.:-]{1,64}`, for `Other.code`, `Unexpected.errorClass` and `FailureDetails.providerErrorType`. Bound the length and charset of `requestId`. Add canary cases that put `"sk-CANARY\nsecret"` into each slot and assert construction is refused. If `Other` must stay open, document that it is the one deliberate exception and have the engine's own `toString` print only the code length.

### WR-07: Two public types named `ProviderCall` in one API

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderCall.kt:17` and `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEvent.kt:63`
**Issue:** This phase introduces `provider.ProviderCall` (the request handed to an `AiProvider`) while `PipelineEvent.ProviderCall` (the listener event, from Phase 2) already exists. Any listener or test that handles both needs import aliases, and `when (event) { is ProviderCall -> … }` silently resolves to whichever import wins. Both names are frozen at `v1.0.0`.
**Fix:** Rename one before the tag. The cheap choice is the new type, to `ProviderRequest`. Alternatively rename the event to `PipelineEvent.ModelTurn`, which matches its `TurnRecord` payload.

### WR-08: `Message` and `AssistantPart` are sealed, which freezes the conversation universe against the strictly-additive rule; the surface script now blesses it

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt:13`, `transcript/AssistantPart.kt:11`; `scripts/review-api-surface.sh:15`
**Issue:** Every other open set in this phase (`FailureReason`, `StopReason`, `ToolChoice`, `CachingMode`, `CredentialLookup`, `OnDeviceAvailability`, `ModelResult`) is deliberately non-sealed so that new leaves are additive and consumers keep an `else` branch. `Message` and `AssistantPart` are sealed and documented as "the whole universe". A new leaf is a source-breaking change for every exhaustive `when` in consumers and mappers. A new leaf is plausible: an image or audio `UserMessage`, a server-tool or citation `AssistantPart`, a `SystemMessage`. The script's allow-list was widened to include the two types, which turns a guard that existed to stop sealed growth into one that approves it.
**Fix:** Decide deliberately before the tag. If additivity wins, make both hierarchies abstract classes with an internal constructor, like `ToolChoice` and `ModelResult`, and drop them from `ALLOWED_SEALED`. If sealed is a contract decision (§5), record it as a §10 amendment, note that a new leaf is a major-version change, and keep `thinking` and the rest in `NativeReplay` as now.

## Info

### IN-01: "Exactly one trace code" is not true on two refusal paths, and a cancelled bind can double-record

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt:70-73, 140, 159-168`
**Issue:** The class KDoc promises "a typed reason and exactly one trace code". A refused fallback records `ON_DEVICE_UNAVAILABLE` and then `FALLBACK_REFUSED`. A probe fault records `ON_DEVICE_PROBE_ERROR` and then `ON_DEVICE_UNAVAILABLE`. `PROVIDER_FALLBACK` is recorded before the fallback's credential lookup. If that lookup is cancelled, nothing is frozen (correct), but the next `model()` call records `PROVIDER_FALLBACK` again. Two codes on the refusal paths look intentional, since the cause is retained, but the text is wrong.
**Fix:** Reword to "one terminal code, preceded by the cause code when there is one", and record `PROVIDER_FALLBACK` only once the fallback binding succeeds.

### IN-02: The fault-to-reason mapping is copied four times

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt:102-106`, `provider/ModelRouter.kt:30-31`, `pipeline/TierWalk.kt:62`, `pipeline/CommandPipeline.kt:201`
**Issue:** `if (fault.timeoutLeak) Timeout() else Unexpected(errorClass)` is repeated. `RoutedModel` cannot reuse the `private` helper in `ModelRouter.kt`. The copies already disagree elsewhere (`capabilities`, `lookUp`), which is how WR-04 happened.
**Fix:** Move one `internal fun EngineFault.toReason(): FailureReason` next to `EngineFault` in `internal/Guarded.kt` and use it at every site.

### IN-03: The surface script compares simple names and tolerates dropped files

**File:** `scripts/review-api-surface.sh:43-44, 58-61`
**Issue:** Sealed types are reduced to the segment after the last dot and matched against the allow-list. A new sealed `Foo.Message` would pass as the allowed `Message`. `tar --ignore-failed-read` silently drops an unreadable tracked file from the isolated copy, so the dump could be produced from an incomplete tree and still print `API SURFACE OK`.
**Fix:** Match fully-qualified names (`io.github.ygaray.voiceactionengine.core.transcript.Message`). Drop `--ignore-failed-read`, or compare the extracted file count with the `git ls-files` count and fail on a mismatch.

---

_Reviewed: 2026-09-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
