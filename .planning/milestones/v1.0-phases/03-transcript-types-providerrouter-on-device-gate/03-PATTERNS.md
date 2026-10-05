# Phase 3: Transcript Types, ProviderRouter & On-Device Gate - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 30 new/modified
**Analogs found:** 30 / 30 (all analogs are git-tracked under `core/`; verified via `git ls-files core`)

Paths below are relative to `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/` (call it `$M`), `core/src/testFixtures/kotlin/.../core/testing/` (`$F`) and `core/src/test/kotlin/.../core/` (`$T`).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `$M/transcript/Message.kt` (sealed Message + User/Assistant/ToolResults) | model | transform | `$M/strategy/StrategyOutcome.kt` (sealed, secondary ctors, redacted toString) | exact |
| `$M/transcript/AssistantPart.kt` (sealed Text, ToolCall) | model | transform | `$M/strategy/StrategyOutcome.kt` | exact |
| `$M/transcript/ToolResult.kt`, `CacheDirective.kt` | model | transform | `$M/strategy/ToolSpec.kt` (init `require`, redacted toString) | role-match |
| `$M/transcript/NativeReplay.kt` | model | transform | `$M/Credential.kt` | exact |
| `$M/transcript/ModelRequest.kt`, `ModelResponse.kt` | model | request-response | `$M/strategy/ToolSpec.kt` + `$M/telemetry/TurnRecord.kt` (defensive list copy) | role-match |
| `$M/transcript/StopReason.kt`, `provider/CachingMode.kt` | model (value class) | transform | `$M/failure/BudgetBound.kt` | exact |
| `$M/provider/AiProvider.kt`, `ProviderSelectionSource.kt`, `CredentialSource.kt`, `OnDeviceCapability.kt` | service (seam, fun interface) | request-response | `$M/pipeline/TierPolicySource.kt`, `$M/commit/PreApplyGate.kt` | role-match |
| `$M/provider/ModelResult.kt`, `CredentialLookup.kt`, `OnDeviceAvailability.kt` (open, internal ctor, public leaves) | model | request-response | `$M/failure/FailureReason.kt` (leaves) | exact |
| `$M/provider/ProviderSelection.kt`, `SelectionRequest.kt` | model | request-response | `$M/Credential.kt` / `ToolSpec.kt` | role-match |
| `$M/provider/ModelCapabilities.kt` (Builder, internal ctor) | model/config | transform | `$M/pipeline/TierPolicy.kt` | exact |
| `$M/provider/ModelCapabilityTable.kt`, `CapabilityOverrides.kt` | service | CRUD (lookup) | `$M/pipeline/TierPolicySource.kt` + `TierPolicy.Builder` | role-match |
| `$M/provider/ModelRouter.kt` (internal) | service | request-response | `$M/pipeline/PolicyPreCheck.kt` + `$M/pipeline/TierWalk.kt` (`guarded`) | role-match |
| `$M/provider/BoundModel.kt` (abstract, internal ctor) + impl | service | request-response | `$M/strategy/CommandSession.kt` + `$M/pipeline/RunSession.kt` | exact |
| `$M/provider/CacheDetector.kt` (internal) | utility | transform | `$M/telemetry/RunRecorder.kt:121-123` (emit) | partial |
| `$M/strategy/CommandSession.kt` (modify: `model()`) | model (abstract class) | request-response | itself | exact |
| `$M/pipeline/RunSession.kt` (modify: lazy handle) | service | request-response | itself | exact |
| `$M/pipeline/TierWalk.kt` (modify: line ~72 ctor arg) | service | request-response | itself | exact |
| `$M/pipeline/PipelineBuilder.kt` (modify: providers/selection/credentials/onDevice/overrides) | config | request-response | itself | exact |
| `$M/strategy/ToolSpec.kt` (modify: `strict`) | model | transform | itself, plus `StrategyOutcome.kt` secondary-ctor pattern | exact |
| `$M/failure/FailureReason.kt` (modify: `CredentialUnreadable`) | model | transform | `FailureReason.ProviderUnavailable` (lines 196-208) | exact |
| `$M/telemetry/TurnRecord.kt`, `CommandTrace.kt` (modify: `fallbackFrom`) | model | transform | `TurnRecord` / `TierAttempt` derived fields | exact |
| `$M/telemetry/TraceCode.kt` (modify: new codes) | model | transform | itself | exact |
| `$M/telemetry/RunRecorder.kt` (modify: internal `now()`) | service | event-driven | itself | exact |
| `scripts/review-api-surface.sh` (modify: ALLOWED_SEALED) | config | batch | itself (line 14) | exact |
| `$F/FakeAiProvider.kt` | test fixture | request-response | `$F/ScriptedStrategy.kt` + `$F/ScriptedResponses.kt` | exact |
| `$F/ScriptedCredentialSource.kt`, `ScriptedSelectionSource.kt` | test fixture | request-response | `$F/ScriptedGate.kt`, `$F/ScriptedResponses.kt` | role-match |
| `$T/ProviderRouterTest`, `KeyIsolationTest`, `OnDeviceGateTest`, `ModelCapabilityTableTest` | test | request-response | `$T/TierPolicyTest.kt` | exact |
| `$T/CacheNotEngagedTest`, `TranscriptTypesTest`, `NativeReplayTest`, `FakeAiProviderTest` | test | transform | `$T/EventsTest.kt:219-230`, `$T/ValueTypesTest.kt` | role-match |
| `$T/NoHardCodedConstantsTest` (source scan) | test | batch | `$T/ApiShapeTest.kt` (reads `src/main/kotlin`) | role-match |
| `$T/RedactionCanaryTest.kt`, `ApiShapeTest.kt` (extend) | test | transform | themselves | exact |

## Pattern Assignments

### Value types with redacted toString: `NativeReplay`, `ProviderSelection`, `ToolResult`, `CacheDirective`, `ModelRequest`, `ModelResponse`

**Analog:** `$M/Credential.kt` (lines 1-17) and `$M/strategy/ToolSpec.kt` (lines 87-100)

```kotlin
public class Credential(public val provider: ProviderId, public val apiKey: String) {
    init { require(apiKey.isNotBlank()) { "Credential apiKey must not be blank" } }
    /** Prints the provider only: never the key and not even its length. */
    override fun toString(): String = "Credential(provider=$provider)"
}
```
Copy: KDoc with `@property`, `init { require }` validation (non-blank model per D-04), toString printing ids/counts/class names only. NativeReplay prints provider and model, never `raw`. No `data class` (ApiShapeTest bans it); hand-write `equals`/`hashCode` with `mixHash` (`failure/ReasonSupport.kt:162`) only where value equality is needed.

**Defensive list copy** (`$M/telemetry/TurnRecord.kt:348-357`): constructor param `toolNames: List<String>` then `public val toolNames: List<String> = toolNames.toList()`. Use for message lists, `parts`, `tools`.

**Growth pattern (must apply from the first line, research Pitfall 1):** app/provider-constructed classes get `@JvmOverloads constructor` with new params appended last, OR the secondary-constructor style of `StrategyOutcome.kt:103-108`:
```kotlin
public class Completed(public val reply: String?, public val terminalCall: TerminalCall?) : StrategyOutcome() {
    public constructor(reply: String?) : this(reply, null)
```
`ModelRequest.maxTokens` has no default (flows from `session.policy.maxTokensPerTurn`).

### `ToolSpec` modification (add `strict: Boolean?`)

**Analog:** `$M/strategy/ToolSpec.kt:87-100` (current 5-arg public ctor). Change to:
```kotlin
public class ToolSpec @JvmOverloads constructor(
    public val name: String, public val description: String, public val inputSchema: JsonObject,
    public val mutating: Boolean = false, public val terminal: Boolean = false,
    public val strict: Boolean? = null,   // appended last
) {
    init {
        require(name.isNotBlank()) { "a tool name must not be blank" }
        require(!(terminal && mutating)) { "a terminal tool must be non-mutating" }   // D-11 already satisfied
    }
```
Keep toString printing name and flags only. Add a test that the 5-arg ctor still resolves by reflection. File-private `const val` style (lines 58-70) is the only allowed way to hold defaults.

### Sealed `Message` / `AssistantPart`

**Analog:** `$M/strategy/StrategyOutcome.kt:95-155` (sealed class, nested public leaf classes, each with redacted `toString`).
Also edit `scripts/review-api-surface.sh` line 14 (`ALLOWED_SEALED="StrategyOutcome CommandOutcome RunTermination GateDecision ToolStep"`, plus header comment line 7-8 and the expect-complete loop) to add `Message AssistantPart`, in the same plan as the types (research Pitfall 4, Open Question 1).

### Open status vocabularies: `StopReason`, `CachingMode`

**Analog:** `$M/failure/BudgetBound.kt:178-191`
```kotlin
@JvmInline
public value class BudgetBound internal constructor(public val value: String) {
    override fun toString(): String = value
    public companion object {
        public val ITERATIONS: BudgetBound = BudgetBound("iterations")
```
Companion `val`s (never `const val`: leaks a public static field, Pitfall 5). `StopReason` is provider-constructed so its ctor is public with `require(isStableCode(value))` (`failure/ReasonSupport.kt:171`, internal; keep usage inside `:core`). `ProviderId.Companion` must stay at exactly four getters (`ProviderId.kt:34-46`); add no fifth constant.

### Open leaf taxonomies: `ModelResult`, `CredentialLookup`, `OnDeviceAvailability`, new `FailureReason.CredentialUnreadable`

**Analog:** `$M/failure/FailureReason.kt:185-208`
```kotlin
public class ProviderUnavailable(public val provider: ProviderId, public val cause: String?) : FailureReason {
    init { require(cause == null || isStableCode(cause)) { "ProviderUnavailable cause must be a stable code" } }
    override val code: String get() = "provider_unavailable"
    override fun equals(other: Any?): Boolean = other is ProviderUnavailable && provider == other.provider && cause == other.cause
    override fun hashCode(): Int = mixHash(mixHash(code.hashCode(), provider.hashCode()), cause.hashCode())
    override fun toString(): String = describe("FailureReason", "ProviderUnavailable", code, "provider" to provider, "cause" to cause)
}
```
Copy for `CredentialUnreadable(provider, cause)` (code `credential_unreadable`) and the `Unreadable(cause)` / `Unavailable(code)` leaves: `require(isStableCode(cause))`, `describe(...)` for toString, `mixHash` hashing. Base types are `open`/`abstract` with `internal constructor()` (as `CommandSession`) with public final leaves. Router treats an unknown subclass defensively. Reuse `NotConfigured(provider)` (lines 180-186), `ModelUnsupported` (line 113), `ProviderUnavailable(ON_DEVICE, "on_device_unavailable")` (Open Question 2: one consumer-visible code).

### Seams as `fun interface`: `AiProvider`, `ProviderSelectionSource`, `CredentialSource`, `OnDeviceCapability`

**Analog:** `$M/pipeline/TierPolicySource.kt` (`TierPolicySource { ... }` SAM used in `TierPolicyTest.kt` as `TierPolicySource { log.add("policy"); TierPolicy.DEFAULT }`) and `$M/commit/PreApplyGate.kt`. Exactly one abstract method each; later additions must be default members (Pitfall 2). `suspend` methods. App returns typed results, never null-for-error except `ProviderSelectionSource` returning null = not configured.

### `ModelCapabilities` (Builder, internal ctor) and `CapabilityOverrides`

**Analog:** `$M/pipeline/TierPolicy.kt:261-331`
```kotlin
public class TierPolicy internal constructor(public val offlineOnly: Boolean, ... ) {
    init { require(maxIterations >= MIN_ITERATIONS) { "..." } }
    public class Builder internal constructor() { public var offlineOnly: Boolean = false ... internal fun build(): TierPolicy = TierPolicy(...) }
    public companion object {
        public val DEFAULT: TierPolicy = Builder().build()
        public operator fun invoke(block: Builder.() -> Unit): TierPolicy = Builder().apply(block).build()
    }
}
```
Copy for `ModelCapabilities { minCacheablePrefixTokens = null; charsPerToken = ...; caching = ...; supportsTools = ... }`. Defaults as file-private `private const val` (lines 231-234; divisor default 4.0 here, read via the builder). `minCacheablePrefixTokens: Int?` null = silent. `ModelCapabilityTable.lookup(provider, id)`: override layer, then provider default, then unknown-id default. No model-id literals anywhere in `src/main`, KDoc included.

### `BoundModel` / frozen per-tier handle + `RunSession` lazy resolution

**Analog:** `$M/pipeline/RunSession.kt:208-225` and `$M/strategy/CommandSession.kt:155-193`
```kotlin
internal class RunSession(override val runId: String, override val parentRunId: String?, override val strategy: StrategyId,
    override val policy: TierPolicy, override val carry: Any?, private val coordinator: CommitCoordinator,
    private val recorder: RunRecorder) : CommandSession() {
    override suspend fun recordTurn(turn: TurnRecord) { recorder.turnRecorded(strategy, turn) }
}
```
Add to `CommandSession` (abstract class, `internal constructor()`, safe to add abstract members pre-tag): `public abstract suspend fun model(): BoundModel`. In `RunSession` add a router constructor argument and a lazy cached field (use a `Mutex` or suspend-safe once-holder, not `lazy`, since resolution suspends). `BoundModel` is `abstract` with `internal constructor()`; it never exposes `Credential`; `complete(request)` returns `ModelResult` and never throws; a refused binding returns `ModelResult.Failure` with zero provider calls. The handle calls `session.recordTurn(...)` (strategies must not also call it) and then the cache detector. Keep `final override fun toString()` ids-only style (`CommandSession.kt:190-192`).

**Wiring site:** `$M/pipeline/TierWalk.kt:72`: `val session = RunSession(runId, parentRunId, strategy.id, policy, carry, coordinator, recorder)`; add router arg. `TierWalk` is built at `CommandPipeline.kt:166`; `CommandPipeline` built at `PipelineBuilder.kt:70-79`.

### `ModelRouter` (internal) and every app/provider call

**Analog:** `$M/pipeline/TierWalk.kt:54-62` (guard pattern) and `$M/internal/Guarded.kt`
```kotlin
private suspend fun executeGuarded(...): StrategyOutcome = guarded(
    onFault = { fault ->
        recorder.recordCode(TraceCode.STRATEGY_ERROR)
        val reason = if (fault.timeoutLeak) FailureReason.Timeout() else FailureReason.Unexpected(fault.errorClass)
        StrategyOutcome.Failed(reason)
    },
) { strategy.execute(input, session) }
```
Wrap the selection source, credential source, on-device gate and `AiProvider.complete` in `guarded`. Do not add any `try/catch` or a second `@Suppress` (Guarded.kt:404-405 is the repo's only one). Throwing credential source gives `Unreadable("source_error")` + trace code; throwing selection gives `Unexpected(fault.errorClass)`. Resolution order: selection, policy gate (`session.policy.offlineOnly` / `allowedProviders`), on-device gate with one-level fallback re-run through the same policy gate, provider lookup, credential lookup for the selected provider ONLY plus `credential.provider == selection.provider` check, capability check.

### On-device gate wiring

**Analog:** `$M/pipeline/PipelineBuilder.kt:49-53` and `$M/pipeline/PolicyPreCheck.kt:40-43`
```kotlin
internal var onDeviceAvailability: suspend () -> Boolean = { false }
```
Keep the property (tests assign it: `TierPolicyTest.kt:122` `onDeviceAvailability = onDevice`). Add `public var onDevice: OnDeviceCapability` (default returns `Unavailable("not_implemented")`) and change the internal default to `{ onDevice.availability() is OnDeviceAvailability.Available }`. Add the new DSL properties beside the existing ones (lines 25-47), each with KDoc; extend `build()` validation in the existing `require(...)` style (lines 61-69). `PolicyPreCheck` is unchanged. Note Pitfall 6: an on-device-only tier is blocked before the router runs.

### `CacheDetector` (internal) and event emit

**Analog:** `$M/telemetry/RunRecorder.kt:117-123`
```kotlin
suspend fun cacheNotEngaged(strategy: StrategyId, provider: ProviderId, model: String?) {
    dispatch.send(PipelineEvent.CacheNotEngaged(runId, strategy, provider, model))
}
```
Call only this (not `recordCode`). Logic: pure function per research Pattern 4 (`shouldFlagCacheMiss`), estimate = `min(chars/charsPerToken, inputUncached+cacheRead+cacheWrite)`, once per successful response after `recordTurn`. Add internal `fun now(): Long` to `RunRecorder` (Pitfall 9) for latency instead of `System.nanoTime()`.

### `TurnRecord` / `TierAttempt` / `TraceCode` growth

**Analog:** `$M/telemetry/TurnRecord.kt:348-362` (add `fallbackFrom: ProviderId?` via secondary 6-arg ctor so the old shape survives), `CommandTrace.kt:66-69` (derived `provider`/`model` on `TierAttempt`; add derived `fallbackFrom`), `$M/telemetry/TraceCode.kt:14-` (`public val X: TraceCode = TraceCode("snake_code")` in the companion).

### Test fixtures: `FakeAiProvider`, `ScriptedCredentialSource`, `ScriptedSelectionSource`

**Analog:** `$F/ScriptedStrategy.kt:235-263` and `$F/ScriptedResponses.kt:198-212`
```kotlin
public class ScriptedResponses<T>(values: List<T>) {
    public fun next(): T = synchronized(lock) {
        check(index < script.size) { "script exhausted: all ${script.size} scripted replies were already used" }
        script[index++]
    }
}
```
Copy: public class with KDoc (explicitApi applies to testFixtures), vararg secondary ctor, `AtomicInteger` call counter (`calls`), `CopyOnWriteArrayList` of recorded requests and credentials (so tests assert key isolation), script via `ScriptedResponses`, fail loudly on exhaustion. FakeAiProvider declares its own `ModelCapabilities` with a fake cache minimum. Package `...core.testing`.

### Tests

**Analog:** `$T/TierPolicyTest.kt:100-125` (ladder helper using `commandPipeline { tiers.forEach { tier(it) }; ...; gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink() }`, `runTest { NoNetworkGuard.during { ... } }`, `trace.codes.map { it.value }`).
**Cache event test analog:** `$T/EventsTest.kt:219-230` (`RunRecorder("run-1", null, null, 0, { 0L }, listener)`, `listener.events.single() as PipelineEvent.CacheNotEngaged`). For the end-to-end detector test, run through a pipeline with FakeAiProvider and `RecordingEventListener`; assert exactly one event and no `EngineCode`.
**Redaction:** extend `$T/RedactionCanaryTest.kt` (uses `Canary(label)` objects whose toString contains `CANARY`, `KEY = "sk-CANARY-KEY"`, sweeps all printed output).
**Source scans (CLN-03/04):** model on `$T/ApiShapeTest.kt` reading `src/main/kotlin`.

## Shared Patterns

### Never-throw seam calls
**Source:** `$M/internal/Guarded.kt:391-392` (`guarded(onFault = {...}) { ... }`). **Apply to:** ModelRouter, BoundModel.complete, cache detector hook.

### Stable-code causes, no free text
**Source:** `$M/failure/ReasonSupport.kt:159-171` (`isStableCode`, `describe`, `mixHash`). **Apply to:** `Unreadable`, `Unavailable`, `CredentialUnreadable`, `StopReason`, any new cause.

### Redacted toString
**Source:** `$M/Credential.kt:16`, `$M/strategy/ToolSpec.kt:100`, `$M/telemetry/TurnRecord.kt:359`. **Apply to:** every new public type. Ids, counts and class names only; never keys, transcript text, tool args, `NativeReplay.raw`.

### Additive public API under Metalava
**Source:** research Pitfall 1/2, `StrategyOutcome.kt:107-108`. **Apply to:** every public type: `@JvmOverloads` or secondary-ctor pattern; seam interfaces are single-abstract-method; no `data class`, no enum, no `public const val`, no public static field.

### Policy gate on dynamic selection
**Source:** `$M/pipeline/TierPolicy.kt` (`offlineOnly`, `allowedProviders` KDoc, 240-260) and `PolicyPreCheck.kt`. **Apply to:** primary selection and fallback in the router.

### Constants
**Source:** `ToolSpec.kt:58-70`, `TierPolicy.kt:231-234` (file-private `private const val`). Limits live only in `TierPolicy.kt`; no model ids in `src/main`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `CacheDetector` estimate math | utility | transform | No existing token-estimate code; use research Pattern 4 and the D-09 calibration (divisor 4.0 default, capped by response prompt tokens) |
| Suspend-safe lazy once-holder in `RunSession` | service | request-response | No existing lazy suspend cache; use a `Mutex`-guarded field (kotlinx.coroutines already on the classpath) |

## Metadata

**Analog search scope:** `core/src/main`, `core/src/testFixtures`, `core/src/test`, `scripts/`
**Files scanned:** about 70 tracked files under `core/` (listed via `git ls-files`), 25 read
**Pattern extraction date:** 2026-09-30
