# Phase 16: Start-Tier Selection - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 19 new/modified (+ tests)
**Analogs found:** 19 / 19 (all git-tracked; verified `git ls-files` for pipeline/, telemetry/, testFixtures/)

Path roots (abbreviated below):
- `MAIN` = `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`
- `TEST` = `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core` (flat layout, e.g. `TierWalkTest.kt`, `PlanSchemaTest.kt`, `PlanParseTest.kt`)
- `FIX` = `core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing`

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `MAIN/pipeline/StartTierPicker.kt` (NEW: `StartTierPicker`, `PickContext`) | public seam | request-response | `MAIN/strategy/CommandSession.kt` | exact (role) |
| `MAIN/pipeline/StartTierPicking.kt` (NEW internal: `PickingSpec`, guarded+timeout pick, validate) | service | request-response | `MAIN/pipeline/TierWalk.kt` (`executeGuarded`) + `MAIN/internal/Guarded.kt` | role-match |
| `MAIN/pipeline/RouterRequest.kt` (NEW internal: prompt, forced-enum tool, decoder) | utility | transform | `MAIN/strategy/singleshot/SingleShotStrategy.kt` `request()` + `MAIN/strategy/plan/PlanSchema.kt` | role-match |
| `MAIN/pipeline/TierSelector.kt` (MOD: `picking` hook, `Custom`, `Router`) | model/config | n/a | same file (`Linear`, `Fixed`) + `TierPolicy.Builder` DSL | exact |
| `MAIN/pipeline/TierWalk.kt` (MOD: one branch in `run`) | service | event-driven | itself | exact |
| `MAIN/pipeline/PolicyPreCheck.kt` (MOD: extract `permits`, expose onDevice) | utility | transform | itself (lines 52-84) | exact |
| `MAIN/pipeline/PipelineBuilder.kt` (MOD: id-collision check) | config | validation | itself `build()` Fixed check | exact |
| `MAIN/pipeline/TierPolicy.kt` (MOD: `pickerTimeoutMillis`) | config | n/a | itself | exact |
| `MAIN/telemetry/StartTierSelection.kt` (NEW public record) | model | n/a | `TierAttempt` in `CommandTrace.kt` | exact |
| `MAIN/telemetry/SelectionBook.kt` (NEW internal) | service | event-driven | private `TierBook` in `RunRecorder.kt:165+` | exact |
| `MAIN/telemetry/RunRecorder.kt` (MOD: delegate; keep method count at 11) | service | event-driven | itself | exact |
| `MAIN/telemetry/CommandTrace.kt` (MOD: additive `selection`) | model | n/a | itself | exact |
| `MAIN/telemetry/PipelineEvent.kt` (MOD: `TierPicked`) | model | pub-sub | `PipelineEvent.TierFinished` | exact |
| `MAIN/telemetry/TraceCode.kt` (MOD: `ROUTER_FALLBACK`) | model | n/a | existing entries in that file | exact |
| `MAIN/strategy/plan/PlanBinding.kt` (MOD, RT-01: `isStepId` cap) | utility | transform | itself line 26 | exact |
| `FIX/ScriptedPicker.kt` (NEW) | test fixture | request-response | `FIX/ScriptedStrategy.kt` | exact |
| `TEST/TierWalkCharacterizationTest.kt` (NEW, Wave 0, before TierWalk edit) | test | request-response | `TEST/TierWalkTest.kt`, `TierPolicyTest`, `EventsTest` | role-match |
| `TEST/StartTierPickerTest.kt`, `RouterRequestTest.kt` (NEW) | test | request-response | `TEST/PlanSchemaTest.kt` (byte-pinned), `TEST/TierWalkTest.kt` | role-match |
| `TEST/PlanParseTest.kt` (MOD: RT-01 test), `TierPolicyTest`, `ApiShapeTest`, docs `API.md`/`INTEGRATION.md` | test/docs | n/a | existing tests | exact |

## Pattern Assignments

### `TierSelector.kt` (hook + Custom/Router)

**Analog:** itself, lines 12-28 (verbatim):
```kotlin
public abstract class TierSelector internal constructor() {
    /** The index in [eligible] to start at, or null when no eligible tier can be the start. */
    internal abstract fun startIndex(eligible: List<StrategyId>): Int?

    public object Linear : TierSelector() {
        override fun startIndex(eligible: List<StrategyId>): Int? = if (eligible.isEmpty()) null else 0
        override fun toString(): String = "Linear"
    }
    public class Fixed(public val tier: StrategyId) : TierSelector() {
        override fun startIndex(eligible: List<StrategyId>): Int? = eligible.indexOf(tier).takeIf { it >= 0 }
        override fun toString(): String = "Fixed($tier)"
    }
}
```
Add `internal open val picking: PickingSpec? get() = null` on the base (a public class cannot extend an internal intermediate class). `Custom`/`Router` implement `startIndex` as the Linear answer. KDoc keeps "not sealed, keep an `else`".
**DSL pattern for builders (no default-arg ctors; ApiShapeTest):** `TierPolicy.kt`:
```kotlin
public class Builder internal constructor() { public var offlineOnly: Boolean = false ... }
public companion object {
    public operator fun invoke(block: Builder.() -> Unit): TierPolicy = Builder().apply(block).build()
}
```

### `StartTierPicker.kt` (`PickContext`)

**Analog:** `MAIN/strategy/CommandSession.kt:14-40`:
```kotlin
public abstract class CommandSession internal constructor() {
    public abstract val runId: String
    public abstract val policy: TierPolicy
    public abstract suspend fun submit(step: ToolStep): DispatchResult   // OMIT on PickContext (no write path)
```
Copy the style (abstract class, internal ctor, one KDoc per member). Members per RESEARCH: `runId`, `policy`, `tokensUsed`, `suspend model(): BoundModel`, `suspend recordTurn`. `StartTierPicker` is a `fun interface` with exactly one abstract `suspend fun pick(input, eligible, ctx): StrategyId?`. Concrete impl `RunPickContext` mirrors `RunSession` (`MAIN/pipeline/RunSession.kt`, binds via `ModelRouter.bind` with the picker's own id).

### `StartTierPicking.kt` (guarded pick with timeout)

**Analog:** `TierWalk.executeGuarded` (TierWalk.kt:57-67) and `Guarded.kt`:
```kotlin
private suspend fun executeGuarded(strategy, input, session): StrategyOutcome = guarded(
    onFault = { fault ->
        recorder.recordCode(TraceCode.STRATEGY_ERROR)
        StrategyOutcome.Failed(fault.toReason())
    },
) { strategy.execute(input, session) }
```
Pick version (RESEARCH): `guarded(onFault = { null }) { withTimeoutOrNull(policy.pickerTimeoutMillis) { picker.pick(input, eligible, ctx) } }`. `guarded` is `internal suspend inline`, rethrows caller cancellation. Never `runCatching`/`catch (Exception)` (banned; the only `@Suppress` lives in Guarded.kt). `onFault` is not suspend-restricted inside the inline lambda, so `recordCode(ROUTER_FALLBACK)` can be called there as shown above. Validate `choice in eligible` yourself; otherwise record `TraceCode.ROUTER_FALLBACK` and start index 0.

### `TierWalk.kt` (branch in `run`)

**Analog:** itself, lines 39-54 (reuse `climb`, `runTier`; do not copy them; class already has 9 functions, cap 12):
```kotlin
suspend fun run(input: CommandInput): CommandOutcome {
    val start = if (ladder.refusal == null) ladder.selector.startIndex(ladder.tiers.map { it.id }) else null
    if (start == null) {
        return CommandOutcome.Failed(effects(), ladder.refusal ?: FailureReason.NoEligibleTier(), null)
    }
    return climb(ladder.tiers.drop(start), input)
        ?: CommandOutcome.Unhandled(effects(), lastReason, ladder.cappedByPolicy)
}
```
Keep this path verbatim when `ladder.selector.picking == null`. For picking: head = `ladder.tiers.takeWhile { it.capabilities.providers.isEmpty() }`, `climb(head)`; non-null result returns immediately (picker not called, no code); else compute start, then `climb(rest.drop(start), input) ?: Unhandled(...)` once. `carry` is private and untouched, so the head's carry reaches the picked tier (D-01). Suppression: `hasWorked()` at line 122 is already inside `runTier`.

### `PolicyPreCheck.kt`

**Analog:** itself lines 76-84 (extract body to `internal fun tierPermitted(capabilities, policy, onDevice)`; expose `Ladder.onDeviceAvailable`, currently `private val`, line 18):
```kotlin
private fun permits(tier: CommandStrategy, policy: TierPolicy, onDevice: Boolean): Boolean {
    val capabilities = tier.capabilities
    val allowed = policy.allowedProviders
    val providers = capabilities.providers
    if (allowed != null && providers.isNotEmpty() && providers.none { it in allowed }) return false
    return !policy.offlineOnly || providers.isEmpty() || (capabilities.onDeviceOnly && onDevice)
}
```

### `PipelineBuilder.kt`

**Analog:** `build()` lines 110-114:
```kotlin
val fixed = (selector as? TierSelector.Fixed)?.tier
require(fixed == null || strategies.any { it.id == fixed }) {
    "commandPipeline: selector names unknown tier $fixed"
}
```
Add next to it: `selector.picking?.let { require(strategies.none { s -> s.id == it.id }) { "commandPipeline: picker id ${it.id} collides with a tier id" } }`. Default stays `TierSelector.Linear` (line 44) so Router is off by default.

### `TierPolicy.kt` (`pickerTimeoutMillis`)

**Analog:** itself. Constants live at top (owner file, required by `NoHardCodedConstantsTest.LIMIT_OWNERS`):
```kotlin
private const val DEFAULT_MAX_ITERATIONS = 6
private const val DEFAULT_TOKEN_CEILING = 60_000L
```
Add `DEFAULT_PICKER_TIMEOUT_MILLIS = 2_000L`; ctor param `public val pickerTimeoutMillis: Long`; `init { require(pickerTimeoutMillis > 0) { "pickerTimeoutMillis must be positive but was $pickerTimeoutMillis" } }`; add to `toString()` (line 55-58 format) and `Builder` var + `build()` pass-through; update the KDoc line "The engine itself enforces only ... [commandTimeoutMillis]" and `@property`. Update `TierPolicyTest` (defaults, `toStringNamesEveryField`, `emptyBlockEqualsDefaults`).

### `StartTierSelection.kt` / `CommandTrace.kt`

**Analog:** `TierAttempt` (CommandTrace.kt:55+): public class, `internal constructor`, vals with KDoc `@property`, explicit `toString`, no `data class` (ApiShapeTest). `CommandTrace` (lines 21-38):
```kotlin
public class CommandTrace internal constructor(
    ..., public val attempts: List<TierAttempt> = emptyList(), public val codes: List<TraceCode> = emptyList(),
) {
    public val usage: Usage = attempts.fold(Usage.ZERO) { sum, attempt -> sum + attempt.usage }
```
Append `public val selection: StartTierSelection? = null` (last param, defaulted; CommandTrace is already in `STUB_EXCEPTIONS`), `usage = attemptsUsage + (selection?.usage ?: Usage.ZERO)`, add `selection=` (ids/counts only) to `toString`. `outcome` KDoc must say "open set: keep an `else`". `tiersBypassed` name is permanent (D-09).

### `SelectionBook.kt` / `RunRecorder.kt`

**Analog:** private `TierBook` (RunRecorder.kt:165-190) and `tierStarted`/`turnRecorded`/`flushInFlight` (lines 54-114):
```kotlin
suspend fun turnRecorded(strategy: StrategyId, turn: TurnRecord) {
    synchronized(lock) {
        book.turns.add(turn)
        tokenTotal = saturatedAdd(tokenTotal, turn.usage.total)
    }
    dispatch.send(PipelineEvent.ProviderCall(runId, strategy, turn))
}
suspend fun flushInFlight(outcome: String, failure: FailureReason?) {
    if (synchronized(lock) { book.inFlight } == null) return
    ...
    if (attempt != null) dispatch.send(PipelineEvent.TierFinished(runId, attempt))
}
```
Change: in `turnRecorded`, route to `selectionBook` when open and `strategy == pickerId` instead of `book.turns` (`tokenTotal` add unchanged); `flushInFlight` also closes an open selection (`cancelled`/`timeout`); `snapshot()` (line 148-161) passes `selection = selectionBook.current`. RunRecorder has 11 member functions, detekt `thresholdInClasses: 12` flags >=12: keep `SelectionBook` in its own file and add at most the minimum delegating members (consider making selection start/finish members of an internal collaborator object the recorder exposes as a property). Dispatch via `dispatch.send(...)` only (listener faults isolated).

### `PipelineEvent.kt` / `TraceCode.kt`

**Analog:** `PipelineEvent.TierFinished` (lines 120-129):
```kotlin
public class TierFinished internal constructor(
    override val runId: String,
    public val attempt: TierAttempt,
) : PipelineEvent {
    override fun toString(): String = "TierFinished(runId=$runId, attempt=$attempt)"
}
```
`TierPicked(runId, selection: StartTierSelection)` identically (emitted only when a picker ran). Add `ROUTER_FALLBACK` to `TraceCode` following its existing entry/code-string style (check the file's `code` snake_case convention, `"router_fallback"`).

### `RouterRequest.kt`

**Analog 1:** `SingleShotStrategy.request` (lines 102-112), 8-arg positional `ModelRequest(system, messages, tools, choice, maxTokens, CacheDirective(..), singleToolCall, reasoning)`. Use `ToolChoice.Required("pick_start_tier")`, `maxTokens = ctx.policy.maxTokensPerTurn`, `CacheDirective(false)`, `reasoning = ReasoningMode.OFF`.
**Analog 2:** `PlanSchema.kt` (`buildJsonObject`, fixed key order, byte-pinned in `TEST/PlanSchemaTest.kt` `theSchemaIsPinnedByteForByte` ~line 71 and `theDescriptionIsPinned...` ~line 95). Pin Router prompt + schema the same way. Constraints: no model-family words anywhere (even KDoc), no `const val` named `*TOKEN*/*CEILING*/*ITERATION*/DEFAULT_/MIN_/MAX_` outside the four owner files, no app-domain names (`SecondBrain`, `claude-haiku-4-5`) in main/testFixtures. Decoder never throws: non-Success / no tool call / wrong tool / non-string / blank -> null.

### `PlanBinding.kt` (RT-01)

**Analog:** itself lines 10-26:
```kotlin
private const val ID_FRAGMENT = "[A-Za-z][A-Za-z0-9_-]*"
private val STEP_ID = Regex(ID_FRAGMENT)
internal fun isStepId(text: String): Boolean = STEP_ID.matches(text)
```
Change to `private const val STEP_ID_CAP = 64` and `text.length <= STEP_ID_CAP && STEP_ID.matches(text)` (name avoids the limit-name regex). Do NOT touch `PlanSchema.kt` (byte pins in `PlanSchemaTest` and providers `PlanThenExecuteWireTest`). Reference regex unchanged. Test: one method in `TEST/PlanParseTest.kt` (near `perStepChecksRunInTheDocumentedOrder`): 64 chars ok, 65 -> `bad_id` (`PlanParse.kt:127`); optionally mirror `aSecondRejectionEscalatesMalformed...` in `PlanThenExecuteReplanTest.kt`. No change to `remainingStepIds`/`outcomeOf` (OI-1 DO NOT NARROW).

### `FIX/ScriptedPicker.kt`

**Analog:** `FIX/ScriptedStrategy.kt` (public class in explicit-API mode, `ScriptedResponses(steps)` script that throws `AssertionError` when dry, `AtomicInteger` execution count, `CopyOnWriteArrayList` of seen inputs). Record every `eligible` list shown and the call count; a dry script is an `Error` (not swallowed by `guarded`), so an unplanned call fails loudly. Zero-call head tier in tests: `ScriptedStrategy(id, StrategyCapabilities.NO_PROVIDER, step)`.

### Characterization test (Wave 0, commit green BEFORE editing `TierWalk.run`)

**Analogs:** `TEST/TierWalkTest.kt` (`traceListsTheTiersThatRanWithTheirOutcomeCodes`), `TierPolicyTest` capped matrix, `EventsTest`, `OnDeviceGateTest` (fake `OnDeviceCapability { OnDeviceAvailability.Available }`). Pin per scripted ladder (single-shot-like, agentic-like, zero-call head; cases offline, maxTier-capped, escalate-with-carry, no-match): exact event class sequence from `RecordingEventListener`, exact `codes`, `attempts` outcomes/`carryIn`, `selection == null`, no `router_fallback`.

## Shared Patterns

### Never-throw app callbacks
**Source:** `MAIN/internal/Guarded.kt` (`guarded`, lines ~38+). **Apply to:** picker call. Cancellation propagates; no `runCatching`, no `catch (e: Exception)`.

### Limits come from policy, single owner
**Source:** `MAIN/pipeline/TierPolicy.kt` top constants + `TEST/NoHardCodedConstantsTest.kt` (limit-owner list, model-family regex). **Apply to:** all new main code.

### Public-API shape rules
**Source:** `TEST/ApiShapeTest.kt` (no `data class`, no default-arg ctors except `STUB_EXCEPTIONS`), `scripts/verify-docs-coverage.sh` C20 (every top-level `public` name in backticks in `API.md`: `StartTierPicker`, `PickContext`, `StartTierSelection`), C21 (no `food`/`card(s)` in docs), Metalava additive-only. Document the selection-source mapping requirement (picker/router id) in `INTEGRATION.md`.

### Detekt zero-baseline
**Source:** `config/detekt/detekt.yml:10` (`thresholdInClasses: 12`). New behavior goes in new internal collaborator classes, not in `TierWalk`/`RunRecorder`. Plain `detekt` only.

### Secrets / logging
Ids, codes, counts only in `toString()`, events and trace; no transcripts or tool args (PROJECT constraints). Banned: `println`, `printStackTrace`, `android.util.Log`, planning ids in comments (`gradle/invariants.gradle.kts`).

## No Analog Found

| File | Role | Reason |
|---|---|---|
| Timeout-bounded app callback (`withTimeoutOrNull` inside `guarded`) | service | No existing per-callback engine timeout; only the whole-command timeout in `CommandPipeline.kt:165-168`. Use RESEARCH snippet; test with `runTest` virtual time. |
| Router prompt wording | utility | No prior router; wording is RESEARCH draft (MEDIUM confidence), pin byte-for-byte, live check in Phase 19. |

## Metadata

**Analog search scope:** `core/src/main/.../pipeline`, `telemetry`, `strategy`, `strategy/plan`, `strategy/singleshot`, `core/src/test`, `core/src/testFixtures`
**Tracked-source gate:** all analogs are under `core/src/**` (tracked); no `.gsd/capabilities` mirror paths used.
**Not re-verified here (taken from RESEARCH, which verified them):** ModelRouter.bind, BoundModel, PlanParse.kt:127, PlanThenExecuteStrategy escalate path, ApiShapeTest stubs, invariants rules.
**Pattern extraction date:** 2026-10-06
