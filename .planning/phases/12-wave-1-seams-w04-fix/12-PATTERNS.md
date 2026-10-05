# Phase 12: Wave-1 Seams & W04 Fix - Pattern Map

**Mapped:** 2026-10-05
**Files analyzed:** 30 (new or modified)
**Analogs found:** 30 / 30 (every file is an edit to, or a sibling of, an existing mechanism). All analog paths were confirmed tracked via `git ls-files`.

Path abbreviations: `C` = `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`, `P` = `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers`, `K` = `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore`. Tests mirror these under `*/src/test/kotlin/...`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `P/chat/OpenAiModelRules.kt` (PROV-14) | config/rules table | transform | itself (`wireRules` arms) | exact |
| `P/chat/ChatErrors.kt` (PROV-15) | utility (error classifier) | transform | itself (`refine`, `isUnsupportedEndpoint`) | exact |
| `P/anthropic/AnthropicModels.kt` (SEAM-03) | config/rules table | transform | itself (`haiku` row + `when`) | exact |
| `C/transcript/ReasoningMode.kt` (new, SEAM-02) | model (value type) | none | `CacheDirective` in `C/transcript/ModelRequest.kt:117-130`; value class `ProviderId` (`C/ProviderId.kt`) | role-match |
| `C/transcript/ModelRequest.kt` (SEAM-02) | model | transform | itself (secondary-ctor chain, lines 24-49) | exact |
| `C/strategy/singleshot/SingleShotStrategy.kt` (SEAM-01/02/06) | strategy | request-response | itself (`Builder.onRefusal`, `OutcomeHooks(...)` line 72, `ModelRequest(` line 99) | exact |
| `C/strategy/singleshot/SingleShotOutcomes.kt` (SEAM-01) | utility | transform | itself (`OutcomeHooks`, `failureOutcome`) | exact |
| `C/strategy/agentic/AgenticLoopStrategy.kt`, `AgenticDispatch.kt` (SEAM-02/06) | strategy | request-response | SingleShot Builder/request code | role-match |
| `C/strategy/OutcomeResolver.kt` `Extraction` (SEAM-06) | model | transform | `CacheDirective` secondary ctor idiom | role-match |
| `C/telemetry/CommandTrace.kt` `TierAttempt` (SEAM-04) | model | event-driven | itself (internal ctor with trailing `turns` default) | exact |
| `C/telemetry/RunRecorder.kt` (SEAM-04) | service | event-driven | itself (`tierStarted`, `TierBook.start`) | exact |
| `C/pipeline/TierWalk.kt` (SEAM-04/05) | service | request-response | itself (`runTier` line 75, `run` Unhandled site) | exact |
| `C/pipeline/PolicyPreCheck.kt` `Ladder` (SEAM-05) | service | transform | itself (`check`, `refused`) | exact |
| `C/pipeline/CommandOutcome.kt` `Unhandled` (SEAM-05) | model | transform | itself (`Unhandled internal constructor`) | exact |
| `C/strategy/CommandSession.kt`, `C/pipeline/RunSession.kt` (SEAM-06) | service | request-response | itself (`submit(step)`) | exact |
| `C/commit/CommitCoordinator.kt`, `ApplyStep.kt`, `ActionLedger.kt`, `CommitSink.kt`, `HeldProposal.kt`, `C/pipeline/HeldCommit.kt` (SEAM-06) | service/model | CRUD (ledger) | `ActionDetails` -> `record` chain | exact |
| `K/KeyAccess.kt` (SEAM-07) | interface | request-response | itself (internal -> public + opt-in) | exact |
| `K/DelicateKeyAccess.kt` (new) | annotation | none | none in repo (use RESEARCH Pattern 4) | no analog |
| `K/ApiKeyStore.kt` (SEAM-07) | service | CRUD | itself (public secondary ctors lines 48-53) | exact |
| `K/AndroidKeyStoreKeyAccess.kt`, `K/SecretReader.kt`, `keystore/build.gradle.kts` | impl/config | request-response | itself | exact |
| `scripts/verify-negative-controls.sh` (new plant) | test script | batch | `expect_red` Part 1 plants (lines 35-52) | role-match |
| `scripts/run-sample-gate1.sh`, `scripts/verify-sample-device-guard.sh` | script | batch | itself (lines 39-40) | exact |
| `sample/.../legs/LegRunner.kt` (judge), `LegCatalog.kt`, `verdict/VerdictTypes.kt` | utility | transform | itself (`LegKind.SINGLE_SHOT` judged arm, lines ~280-295) | exact |
| `sample/src/test/.../SmokeLegTest.kt`, `docs/DocSnippetsTest.kt` | test | request-response | themselves | exact |
| `INTEGRATION.md`, `API.md`, `.planning/REQUIREMENTS.md` | docs | n/a | existing sections | exact |
| Tests: `OpenAiModelRulesTest`, `ChatErrorMapTest`, `ChatTransportTest`, `AnthropicModelsTest`, `SingleShotOutcomeMappingTest`, `TierPolicyTest`, `ApiShapeTest`, `KeystoreApiShapeTest`, keystore test using `SoftwareKeyAccess` | test | request-response | themselves | exact |
| `12-LIVE-LEG-DECISION.md` (new) | doc | n/a | archived Phase 10 decision file (format read by `run-sample-gate1.sh`) | role-match |

## Pattern Assignments

### `P/chat/OpenAiModelRules.kt` (PROV-14)

**Analog:** itself. The `wireRules` `when` (lines 80-93) takes a new arm BEFORE the `GPT_6_FAMILY` arm, plus a `-pro`/`-codex` exclusion.

```kotlin
// lines 83-92 (current)
return when {
    viaRouter && RESPONSES_ONLY.matches(id) ->
        rules(EFFORT_LOW, MAX_COMPLETION_TOKENS, NO_MIN_TOKENS, parallel)
    GPT_6_FAMILY.matches(id) || isLaterGpt5(id) ->
        rules(EFFORT_NONE, MAX_COMPLETION_TOKENS, NO_MIN_TOKENS, parallel)
    GPT_5_BASE.matches(id) || GPT_5_MINOR.matches(id) || O_SERIES.matches(id) -> completion
```
Add: `!viaRouter && RESPONSES_ONLY.matches(id) -> completion` (no effort) and `(GPT_6_FAMILY... || isLaterGpt5(id)) && !isProOrCodex(id)`. Define the new regex next to the others (lines 14-25: anchored `private val X = Regex("""^...$""")` with a leading comment). Add named consts for strings (detekt). Update the `wireRules` KDoc ordering sentence (lines 70-79). Optional: `toolsOnChat` (line 64) `viaRouter || !RESPONSES_ONLY.matches(id)` gains the `-pro`/`-codex` direct check. Do not add default args; keep `ChatWireRules` as is.

### `P/chat/ChatErrors.kt` (PROV-15)

**Analog:** itself. Add one arm to `refine` (lines 137-144) after the `isUnsupportedEndpoint` arm:
```kotlin
private fun refine(status: Int, error: JsonObject?, quota: Boolean): FailureReason? = when {
    quota -> FailureReason.Billing()
    textField(error, KEY_CODE) == CONTEXT_LENGTH_CODE -> FailureReason.ContextWindowExceeded()
    isUnsupportedEndpoint(status, textField(error, KEY_MESSAGE)) -> FailureReason.ModelUnsupported()
    // NEW: status == STATUS_BAD_REQUEST && textField(error, KEY_PARAM) == PARAM_REASONING_EFFORT &&
    //      textField(error, KEY_CODE) == CODE_UNSUPPORTED_VALUE -> FailureReason.ModelUnsupported()
```
Constants go in the block at lines 29-45 (`private const val KEY_CODE = "code"` style; add `KEY_PARAM`, `PARAM_REASONING_EFFORT`, `CODE_UNSUPPORTED_VALUE`). Reads use the existing `textField` helper (never the message, nothing stored). Require all three conditions (Pitfall 5).

**Test (MockWebServer replay):** copy `ChatTransportTest.route(...)` (lines 98-123: builds `commandPipeline { tier(ScriptedStrategy(...)); provider(provider); providerSelection = ScriptedSelectionSource.fixed(...); credentials = ScriptedCredentialSource.keys(...); gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink() }`), enqueue `MockResponse().setResponseCode(400).setBody(W04_BODY)`, assert `reason.code == "model_unsupported"`. Legacy `okhttp3.mockwebserver` only; legs 4.12/5.2.1/5.5.0 re-run the same classes automatically.

### `P/anthropic/AnthropicModels.kt` (SEAM-03)

**Analog:** itself, `haiku` row (lines 31-34) and `capabilities` `when` (lines 42-46).
```kotlin
private const val HAIKU_MIN_CACHEABLE_PREFIX_TOKENS = 4096
private val haiku = ModelCapabilities {
    caching = CachingMode.EXPLICIT_BREAKPOINTS
    minCacheablePrefixTokens = HAIKU_MIN_CACHEABLE_PREFIX_TOKENS
}
fun capabilities(model: String): ModelCapabilities = when (model) {
    OPUS_5_5, SONNET_5_5, FABLE_5_1, MYTHOS_5_1 -> rejectsForcedToolChoice
    HAIKU_4_5 -> haiku
    else -> unknownModel
}
```
Add `SONNET_5 = "claude-sonnet-5"` (line 6-10 const block), `SONNET_5_MIN_CACHEABLE_PREFIX_TOKENS = 1_024`, `sonnet5` row mirroring `haiku`, and `SONNET_5 -> sonnet5` arm. Exact-id only; update the KDoc "checked on" date (line 18). Extend `AnthropicModelsTest` and the routed-id cases in the `ChatModels` test.

### `C/transcript/ReasoningMode.kt` (new) and `ModelRequest.kt` (SEAM-02)

**Analog:** `ModelRequest.kt` ctor chain (lines 24-49): a primary ctor plus explicit secondary ctors, each KDoc'd, no default args.
```kotlin
public class ModelRequest(
    public val system: String, messages: List<Message>, tools: List<ToolSpec>,
    public val toolChoice: ToolChoice, public val maxTokens: Int,
    public val cache: CacheDirective, public val singleToolCall: Boolean,
) {
    /** A request that leaves the number of tool calls to the model. */
    public constructor(system: String, messages: List<Message>, tools: List<ToolSpec>,
        toolChoice: ToolChoice, maxTokens: Int, cache: CacheDirective) :
        this(system, messages, tools, toolChoice, maxTokens, cache, false)
```
New 8-arg primary adds `public val reasoning: ReasoningMode`; the existing 7/6/4/3-arg ctors become secondaries delegating with `ReasoningMode.OFF`. Extend `toString()` (line 69-71) only with a non-sensitive value. For the type shape, copy `ToolChoice`'s companion/internal-ctor style (lines 78-105) or `ProviderId` (value class with constants and `toString`). `ReasoningMode` must have an `internal` ctor and `OFF`/`PROVIDER_DEFAULT` on the companion. Add a reflection test that the 7-arg public ctor still exists (extend `ApiShapeTest`, which tracks ctor stubs). Encoders in `P/anthropic/AnthropicEncoder.kt` and `P/chat/ChatEncoder.kt` are NOT touched (byte-identical wire).

### `C/strategy/singleshot/SingleShotOutcomes.kt` + `SingleShotStrategy.kt` (SEAM-01)

**Analog:** itself.
```kotlin
// SingleShotOutcomes.kt:15-18, 31-36
internal class OutcomeHooks(
    val onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome,
    val onRefusal: suspend (ModelResponse?) -> StrategyOutcome,
)
private suspend fun failureOutcome(failure: ModelResult.Failure, hooks: OutcomeHooks): StrategyOutcome =
    when (failure.reason) {
        is FailureReason.NoToolCall -> hooks.onNoToolCall(null)
        is FailureReason.Refusal -> hooks.onRefusal(null)
        else -> StrategyOutcome.Failed(failure.reason, failure.details)   // becomes hooks.onFailed(...)
    }
```
Add third hook `onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome`; import `FailureDetails` (`core.failure`). Keep `decideResult(result, hooks)` signature. In `SingleShotStrategy.kt` line 72 `OutcomeHooks(settings.onNoToolCall, settings.onRefusal)` gains `settings.onFailed`; Builder property (copy the KDoc'd `onRefusal` style, lines 203-209):
```kotlin
public var onRefusal: suspend (ModelResponse?) -> StrategyOutcome =
    { StrategyOutcome.Failed(FailureReason.Refusal()) }
```
Default: `{ reason, details -> StrategyOutcome.Failed(reason, details) }`. No extra guard: a throw reaches `TierWalk.executeGuarded` -> `strategy_error`. Tests: extend `SingleShotOutcomeMappingTest`.

### `C/telemetry/CommandTrace.kt`, `RunRecorder.kt`, `C/pipeline/TierWalk.kt` (SEAM-04)

**Analog:** itself. `TierAttempt internal constructor(...)` with trailing `turns: List<TurnRecord> = emptyList()` (CommandTrace.kt:53-61): insert `public val carryIn: Boolean` BEFORE `turns` so the trailing default (listed in `ApiShapeTest.STUB_EXCEPTIONS`) stays. `RunRecorder.tierStarted(strategy: StrategyId)` (line 51) gains `carryIn: Boolean`, passes it into `TierBook.start(strategy, now)` (line 170) which stores it, and `close(...)`'s `build` lambda / `flushInFlight` read it. Call site `TierWalk.kt:75`: `recorder.tierStarted(strategy.id)` -> `recorder.tierStarted(strategy.id, carry != null)` using the walk's own `carry` (the same value passed to `RunSession(scope, strategy.id, ..., carry)` on the next line). Add KDoc `@property carryIn` in the `TierAttempt` KDoc (lines 43-51). Tests: `TierWalkTest`, `InFlightTierTraceTest`, trace tests.

### `C/pipeline/PolicyPreCheck.kt`, `TierWalk.kt`, `CommandOutcome.kt` (SEAM-05)

**Analog:** itself. `Ladder(tiers, selector, onDeviceAvailable, refusal)` (PolicyPreCheck.kt:15-20) gains `val cappedByPolicy: Boolean`; `check` computes it from both skip sites:
```kotlin
val within = if (capIndex == null) strategies else strategies.take(capIndex + 1)
strategies.drop(within.size).forEach { recorder.tierSkipped(it.id, TraceCode.TIER_SKIPPED_POLICY) }
val eligible = within.filter { ... permits(...) ... }
return if (eligible.isNotEmpty()) Ladder(eligible, selector, onDevice, null) else refused(...)
```
-> `Ladder(eligible, selector, onDevice, null, eligible.size < strategies.size)`; `refused` passes false. `TierWalk.run` (Unhandled site): `CommandOutcome.Unhandled(effects(), lastReason)` -> add `ladder.cappedByPolicy`. `Unhandled internal constructor(effects, lastReason)` (CommandOutcome.kt:134-140) gains `public val cappedByPolicy: Boolean` with the KDoc from RESEARCH "Unhandled.cappedByPolicy KDoc". Keep `toString` redaction posture; add the flag only. Tests: `TierPolicyTest` truth table.

### providerCallId plumbing (SEAM-06)

**Analog:** the existing `ActionDetails` -> `ActionLedger.record` -> `ExecutedAction` chain.
```kotlin
// C/commit/ActionLedger.kt:15-21
internal class ActionDetails(val toolName: String, val appOutcomeToken: String?,
    val targetIds: Map<String, String>, val context: Any?, val mutating: Boolean = true)
// C/commit/CommitSink.kt:51-60  (ExecutedAction internal ctor; mutating = true stays LAST)
public class ExecutedAction internal constructor(position, kind, applied, appOutcomeToken, toolName, targetIds, context, public val mutating: Boolean = true)
```
Insert `providerCallId: String?` before `mutating` in both. `CommitCoordinator.submit(step: ToolStep)` (line 55) gets an overload `(step, providerCallId: String?)`; the old delegates with null. `CommandSession.submit(step)` (CommandSession.kt:35, public abstract) stays; add an `internal abstract suspend fun submit(step, providerCallId)`; `RunSession.kt:59` `override suspend fun submit(step) = scope.coordinator.submit(step)` implements both. `HeldProposal` gets an internal `providerCallId` field; `HeldCommit.run` passes `held.providerCallId` to `applyWithoutGate`. SingleShot (`resolve`/`submitAll`) stamps its single `call.id`; `AgenticDispatch.prepare/settle` stamps per call. `Extraction` (OutcomeResolver.kt:31-41) uses the overload idiom:
```kotlin
public class Extraction(public val toolName: String, public val arguments: JsonObject, public val callId: String?) {
    public constructor(toolName: String, arguments: JsonObject) : this(toolName, arguments, null)
```
Keep `init { require(toolName.isNotBlank()) ... }` and the `toString` printing only name + argumentCount. Tests: `CommitPathTest`, `HeldReportingTest`, `RedactionCanaryTest`; add a test that the 2-arg `Extraction` ctor exists.

### `K/KeyAccess.kt`, `K/ApiKeyStore.kt`, `K/DelicateKeyAccess.kt` (SEAM-07)

**Analog:** `KeyAccess.kt` (internal interface, two members, lines 9-22; KDoc contract "null is the only absent signal; never creates") becomes `@DelicateKeyAccess public interface KeyAccess` with KDoc kept. `ApiKeyStore.kt` public ctor idiom (lines 48-53):
```kotlin
public constructor(dataStore: DataStore<Preferences>, slots: List<KeySlot>, ioDispatcher: CoroutineDispatcher) :
    this(dataStore, slots, ioDispatcher, AndroidKeyStoreKeyAccess)
```
Add `@DelicateKeyAccess public constructor(dataStore, slots, keyAccess: KeyAccess) : this(dataStore, slots, Dispatchers.IO, keyAccess)` (distinct by type from the dispatcher overload). `K/DelicateKeyAccess.kt` has no repo analog: use RESEARCH Pattern 4 (`@RequiresOptIn(level = ERROR)`, `@Retention(BINARY)`, `@Target(CLASS, CONSTRUCTOR)`, `public annotation class`; explicit API mode requires `public`). `:keystore` itself needs the opt-in (`@OptIn` per file, or `freeCompilerArgs.add("-opt-in=...")`, the build file already uses `freeCompilerArgs.add` for `-Xjdk-release=11`).

**Test fake:** model `KeystoreTestSupport.kt:30-48` `SoftwareKeyAccess` (`ConcurrentHashMap<String, SecretKey>`, `existingKey` returns `keys[alias]`, never creates). Update `KeystoreApiShapeTest` for the new public surface.

**Negative-compile proof (different module):** model on `scripts/verify-negative-controls.sh` `expect_red` (lines 35-50: writes `ZzPlant.kt` into a module, runs the Gradle task, greps a marker, removes on exit trap). The plant lives in `:sample` (not `:keystore`), constructs `ApiKeyStore(ds, slots, fake)` without `@OptIn`, expects `:sample:compileDebugKotlin` red with the opt-in marker; copy the real Kotlin error text on first run (Assumption A5). `expect_red` is hard-wired to `<mod>/src/main/kotlin/.../$mod`, so a `:sample` variant needs the package path `.../sample`.

### Live smoke PROV-16 (`LegRunner.kt`, `run-sample-gate1.sh`, `verify-sample-device-guard.sh`, `SmokeLegTest.kt`)

**Analog:** the judged arms in `LegRunner.kt` vs the capture-only arm (lines 290-295):
```kotlin
LegKind.RESPONSES_PROBE -> {
    // Capture only: ... never passed or failed.
    val status = facts.attempts.lastOrNull()?.httpStatus
    val extras = if (status == null) emptyMap() else mapOf("http" to status.toLong())
    Judged(Verdict(VerdictKind.CAPTURED, facts.summary.reason ?: facts.summary.kind), extras)
}
```
Change to PASS when reason is `model_unsupported` (http 400) else FAIL (`http_error`); copy the PASS/FAIL construction from the `SINGLE_SHOT` arm just above (line ~288 `Judged(result.verdict, result.extras(...))`). Keep line 177 `capabilities(...) { supportsTools = true }` so the engine does not pre-refuse. Update `LegCatalog.kt` comment "Recorded as CAPTURED, never judged" and `SmokeLegTest.theResponsesProbeIsCapturedNotPassed`. Runner paths (`run-sample-gate1.sh:39-40`): retarget `PHASE_DIR` to `.planning/phases/12-wave-1-seams-w04-fix` and `DECISION_FILE` to `12-LIVE-LEG-DECISION.md`; mirror in `verify-sample-device-guard.sh` (fixture paths around lines 124-125 and 274). Do not touch the device-pinning guard.

### Docs (`INTEGRATION.md`, `API.md`, `DocSnippetsTest.kt`, `REQUIREMENTS.md`)

**Analog:** existing gated sections. Every ```` ```kotlin ```` fence is preceded by `<!-- doc-snippet: NAME -->` and equals a `// doc-snippet:start NAME` ... `end NAME` region in `sample/src/test/kotlin/.../sample/docs/DocSnippetsTest.kt` (gate C06/C07). Import lists use ```` ```text ```` fences. `API.md` must name every new public top-level type in backticks (`ReasoningMode`, `KeyAccess`, `DelicateKeyAccess`; C20). No "food"/"card" words (C21), no `~/` or `/home/` (C25). Fix REQUIREMENTS.md SEAM-07 wording from "fun interface" to plain interface.

## Shared Patterns

### No default arguments on public ctors; overload idiom
**Source:** `C/transcript/ModelRequest.kt:33-49`, `C/transcript/ModelRequest.kt:121-122` (`CacheDirective(staticPrefix)` secondary).
**Apply to:** `ModelRequest`, `Extraction`, `ApiKeyStore`. Internal-ctor classes keep trailing defaults and new params go BEFORE them (`TierAttempt.turns`, `ExecutedAction.mutating`), because `ApiShapeTest.STUB_EXCEPTIONS` flags both directions.

### Redacted `toString()`
**Source:** `ModelRequest.toString` (lines 69-71), `ExecutedAction.toString` (62-64), `Extraction.toString` (line 40).
**Apply to:** all new fields: ids/flags only, never prompts, args, keys. Run `RedactionCanaryTest`.

### Error text never stored
**Source:** `ChatErrors.kt` `ChatErrorInfo` KDoc (lines 61-80) and `textField` (lines 33-36).
**Apply to:** PROV-15 classifier.

### Internal-only plumbing, additive public API
**Source:** `Unhandled internal constructor` (`CommandOutcome.kt:134`), `TierAttempt internal constructor`.
**Apply to:** SEAM-04/05/06; no `api.txt` edits in Phase 12 (D-02); Metalava compat must stay green.

### Source hygiene (invariants scanner / detekt)
No `runCatching`, `println`, `printStackTrace`, `android.util.Log`, DI annotations; no app names or planning ids (`D-03`, `SEAM-06`, `Phase 12`) in source comments; named consts instead of magic numbers/strings (see const blocks in `ChatErrors.kt:14-45`, `OpenAiModelRules.kt:3-25`); explicit `public`/`internal`; catch specific exceptions.

### Test harness
**Source:** `ChatTransportTest.route` (lines 98-123) for provider replays; `ScriptedStrategy`/`ScriptedGate`/`RecordingCommitSink` fakes from `core` testFixtures for pipeline tests; `SoftwareKeyAccess` for keystore.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `K/DelicateKeyAccess.kt` | annotation | none | No `@RequiresOptIn` marker exists in the repo; use RESEARCH Pattern 4 |
| `:sample`-side negative-compile plant | test script | batch | `expect_red` plants only target a module's own `src/main`; opt-in must be proven from a second module, so the helper needs a `:sample` variant |

## Notes for the planner

- Hot files shared across plans: `SingleShotStrategy.kt` (SEAM-01, 02, 06) and `CommitCoordinator.kt`; sequence 12-B before 12-D as RESEARCH recommends.
- Open items needing the orchestrator, not code: P19 D-13 wording vs PROV-16 (RESEARCH Open Question 1) and `12-LIVE-LEG-DECISION.md` approval.
- A host-side "Probe C" of the post-fix wire (RESEARCH Pitfall 7) should precede any device run.

## Metadata

**Analog search scope:** `core/`, `providers/`, `keystore/` main and test, `sample/` legs and docs tests, `scripts/`.
**Files scanned:** about 35 read or grepped.
**Pattern extraction date:** 2026-10-05
