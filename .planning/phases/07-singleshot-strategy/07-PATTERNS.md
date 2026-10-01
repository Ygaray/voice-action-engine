# Phase 7: SingleShot Strategy - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 30 (new/modified, per RESEARCH "Recommended Plan Decomposition")
**Analogs found:** 29 / 30 (all analog paths are git-tracked; verified via `git ls-files`)

Base paths (abbreviated below):
- `CORE` = `/home/yahir/Projects/Reusable/android/voice-action-engine/core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`
- `CORE_TEST` = `.../core/src/test/kotlin/io/github/ygaray/voiceactionengine/core`
- `FIX` = `.../core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing`
- `PROV` = `.../providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers`
- `PROV_TEST` = `.../providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers`

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `CORE/transcript/ModelRequest.kt` (MOD: 7-arg primary + `singleToolCall`) | model | transform | itself (`CacheDirective` secondary-ctor growth, lines 102-115) | exact |
| `CORE/telemetry/TraceCode.kt` (MOD: `EXTRA_TOOL_CALLS_DROPPED`) | model | event-driven | itself, `FALLBACK_REFUSED` (line 98-99) | exact |
| `CORE/strategy/CommandSession.kt` (MOD: internal `recordCode`) | service | event-driven | itself + `RunSession.recordTurn` | exact |
| `CORE/pipeline/RunSession.kt` (MOD: implement `recordCode`) | service | event-driven | itself, `recordTurn` (60-62) | exact |
| `CORE/strategy/ToolSpecProvider.kt` (NEW: fun interface + `ToolingSnapshot`) | provider/seam | request-response | `CORE/pipeline/TierPolicySource.kt`; `ModelRequest` copy-in-ctor pattern | role-match |
| `CORE/strategy/OutcomeResolver.kt` (NEW: `Extraction`, abstract `Resolution`) | seam | request-response | `ToolChoice` (abstract + internal ctor, ModelRequest.kt:63-90); `StrategyOutcome` | exact (shape) |
| `CORE/strategy/singleshot/UserTurn.kt` (NEW) | utility/seam | transform | `TierPolicy.Builder`/`companion invoke`; `FakeClock` for clock | partial |
| `CORE/strategy/singleshot/SingleShotStrategy.kt` (NEW) | strategy | request-response | `CommandStrategy` impl `FIX/ScriptedStrategy.kt` + `TierPolicy.Builder` DSL | role-match |
| `CORE/strategy/singleshot/SingleShotOutcomes.kt` (NEW, internal) | utility | transform | `CORE/pipeline/TierWalk.kt:60-90` mapping | role-match |
| `PROV/anthropic/AnthropicEncoder.kt` (MOD) | encoder | transform | itself `encodeToolChoice` (90-100) | exact |
| `PROV/chat/ChatEncoder.kt` (MOD) | encoder | transform | itself (65-67) | exact |
| `PROV/chat/OpenAiModelRules.kt` (MOD: `acceptsParallelToolCalls`) | config/rules | transform | itself `ChatWireRules` + `O_SERIES` | exact |
| `FIX/FakeAiProvider.kt` (MOD: `refusal`, `toolCalls`) | test fixture | request-response | itself `reply`/`toolCall` (72-91) | exact |
| `CORE_TEST/TranscriptTypesTest.kt` (MOD) | test | unit | itself | exact |
| `CORE_TEST/SingleShotRequestTest.kt` (NEW) | test | pipeline | `RedactionCanaryTest.routedRun` (358-380) | exact |
| `CORE_TEST/SingleShotResolveTest.kt` (NEW) | test | pipeline | `DeferModeTest` | role-match |
| `CORE_TEST/SingleShotOutcomeMappingTest.kt` (NEW) | test | pipeline | `FakeAiProviderTest`, `RedactionCanaryTest` | role-match |
| `CORE_TEST/SingleShotTerminalTest.kt` (NEW) | test | pipeline | `TerminalCallTest.kt` | exact |
| `CORE_TEST/SingleShotLimitsTest.kt` (NEW) | test | pipeline | `TierPolicyTest`, RESEARCH "Limits test skeleton" | role-match |
| `CORE_TEST/SingleShotUserTurnTest.kt` (NEW) | test | pipeline | `RedactionCanaryTest.routedRun` | partial |
| `CORE_TEST/SingleShotSeamTypesTest.kt` (NEW) | test | unit | `ToolSpecClarificationTest`, `TranscriptTypesTest` | role-match |
| `CORE_TEST/SingleShotFixtures.kt` (NEW) | test fixture | transform | `TerminalCallTest` helpers (34-47), `DeferModeTest.ok` | role-match |
| `CORE_TEST/SingleShotAcceptanceTest.kt` (NEW, S1-S10) | test | pipeline | `DeferModeTest`, `BatchIsolationTest` | exact |
| `CORE_TEST/RedactionCanaryTest.kt` (MOD sweep) | test | unit | itself (`sweepBuiltTypes`, ~389) | exact |
| `PROV_TEST/anthropic/AnthropicEncoderTest.kt` (MOD) | test | unit | itself (170-185) | exact |
| `PROV_TEST/chat/ChatEncoderTest.kt` (MOD) | test | unit | itself (~172, 257) | exact |
| `PROV_TEST/chat/OpenAiModelRulesTest.kt` (MOD) | test | unit | itself | exact |
| `PROV_TEST/SingleShotWireTest.kt` (NEW) | test | request-response (MockWebServer) | `chat/ChatPipelineRetryTest.kt`, `anthropic/AnthropicPipelineRetryTest.kt` | exact |

## Pattern Assignments

### `CORE/transcript/ModelRequest.kt` (model, transform) - add `singleToolCall`

**Analog:** itself. Keep the existing 6-arg signature as a public secondary constructor (Pitfall 3); make a 7-arg primary. No default args (`ApiShapeTest` forbids default-arg stubs). Existing secondary-ctor idiom (lines 28-34):
```kotlin
public constructor(system: String, messages: List<Message>, maxTokens: Int) :
    this(system, messages, emptyList(), ToolChoice.Auto(), maxTokens, CacheDirective(true))
```
Add: `public constructor(system, messages, tools, toolChoice, maxTokens, cache) : this(..., cache, false)`. Existing `init` validation (42-51) and the redacted `toString` (53-56) must be extended with `singleToolCall=$singleToolCall`. Growth precedent: `CacheDirective(staticPrefix)` delegating to the 2-arg primary (106-107).

### `CORE/telemetry/TraceCode.kt` (model) - add code
```kotlin
/** The declared fallback was forbidden by the policy or the tier's declaration, so nothing was sent. */
public val FALLBACK_REFUSED: TraceCode = TraceCode("fallback_refused")
```
Add `EXTRA_TOOL_CALLS_DROPPED = TraceCode("extra_tool_calls_dropped")` the same way (KDoc one line, lower snake wire value).

### `CORE/strategy/CommandSession.kt` + `CORE/pipeline/RunSession.kt` - internal `recordCode`
`CommandSession` is `abstract class ... internal constructor()` (line 14); add an `internal abstract suspend fun recordCode(code: TraceCode)`. `RunSession` already forwards to the recorder (lines 60-62):
```kotlin
override suspend fun recordTurn(turn: TurnRecord) {
    scope.recorder.turnRecorded(strategy, turn)
}
```
Mirror it with `scope.recorder.<existing code-recording method>(strategy, code)`; grep `RunRecorder` for how `CAPABILITY_REFUSED` is recorded from `BoundModel.kt:~95-114` and reuse that method. Keep `CommandSession.toString` final/redacted (63-65).

### `CORE/strategy/ToolSpecProvider.kt`, `OutcomeResolver.kt` (seams)

**Shape analog for `Resolution`:** `ToolChoice` (ModelRequest.kt:63-90): abstract class, `internal constructor()`, nested public subclasses, NOT sealed (script allow-lists exactly seven sealed types):
```kotlin
public abstract class ToolChoice internal constructor() {
    public class Required(public val toolName: String) : ToolChoice() {
        init { require(toolName.isNotBlank()) { "a required tool name must not be blank" } }
        override fun toString(): String = "ToolChoice.Required(toolName=$toolName)"
    }
}
```
**Copy-in-ctor + validation analog for `ToolingSnapshot`:** `ModelRequest` lines 36-51 (`public val tools: List<ToolSpec> = tools.toList()` + `init { require(...) }`). `ToolStep.Mutation` (ToolStep.kt:36-55) is the analog for `Resolution.Steps` (defensive copy, `require(isNotEmpty())`, secondary ctor without optional arg, redacted `toString` printing counts and names):
```kotlin
public class Mutation(mutations: List<PendingMutation>) : ToolStep() {
    public val mutations: List<PendingMutation> = mutations.toList()
    init { require(this.mutations.isNotEmpty()) { "Mutation needs at least one pending mutation" } }
    public constructor(mutation: PendingMutation) : this(listOf(mutation))
    override fun toString(): String = "Mutation(mutations=${mutations.size}, tools=${mutations.map { it.toolName }})"
}
```
`fun interface` seams: `TierPolicySource.kt` (read before writing). Never print `arguments`/`system` contents, only lengths/counts/names.

### `CORE/strategy/singleshot/SingleShotStrategy.kt` (strategy, request-response)

**Builder DSL analog (no default-arg ctor):** `TierPolicy` (TierPolicy.kt:63-122): `internal constructor`, nested `Builder internal constructor()` with `public var` fields, `internal fun build()`, and
```kotlin
public companion object {
    public operator fun invoke(block: Builder.() -> Unit): TierPolicy = Builder().apply(block).build()
}
```
Build-time validation style: `PipelineBuilder.build()` (PipelineBuilder.kt:117-119) `requireNotNull(gate) { "commandPipeline: gate is required ..." }`.

**Interface to implement:** `CommandStrategy` (`id`, `capabilities` default `ANY_PROVIDER`, `suspend fun execute(input, session): StrategyOutcome`). Reference impl: `FIX/ScriptedStrategy.kt:47-51`.

**Model call:** `session.model()` returns `BoundModel`; `model.refusal` non-null means refused (return `Failed(it)`, never escalate); `model.complete(request)` returns open `ModelResult`, so `when` needs `else` (RESEARCH Pattern 1). Engine records the turn itself; do NOT call `session.recordTurn` (double count).

**Limits:** `session.policy.maxTokensPerTurn` -> `ModelRequest.maxTokens`; `session.tokensUsed` vs `session.policy.tokenCeiling` (pre `>=`, post `>`); do not write literals 60000/4096 (NoHardCodedConstantsTest). Failure: `FailureReason.BudgetExceeded(BudgetBound.TOKENS)`.

**Submit path:** `session.submit(ToolStep.Finished(...))` in order, then one merged `ToolStep.Mutation(list)` (gate once per proposal). The `Finished` ctor has a 3-arg secondary (ToolStep.kt:18-20).

**Return/detekt:** `ReturnCount` max 2 -> use `?:` chain + small private functions (RESEARCH Pattern 1). Do not catch `Exception`; TierWalk collapses throws (`TierWalk.kt:60-66`). No `runCatching`, no model-family words or planning ids (`D-03`, `Phase 7`) in main KDoc/comments.

### `CORE/strategy/singleshot/SingleShotOutcomes.kt` (internal mapping)
Analog: `TierWalk.kt:77-90` (maps `Completed`/`Escalate`/`NoMatch` to `CommandOutcome`). Use RESEARCH Pattern 4 table as the closed `when` over `StopReason` (check REFUSAL, MAX_TOKENS, PAUSE_TURN, CONTEXT_WINDOW_EXCEEDED before touching tool calls). Terminal routing: `ToolSpec(... terminal ...)` lookup in snapshot; carrier `StrategyOutcome.Completed(reply = null, terminalCall = TerminalCall(name, args))` (init requires not both set).

### `PROV/anthropic/AnthropicEncoder.kt` (encoder) lines 90-100
```kotlin
private fun encodeToolChoice(call: ProviderRequest, reshape: Boolean): JsonObject {
    val choice = call.request.toolChoice
    return buildJsonObject {
        if (choice is ToolChoice.Required && !reshape && call.capabilities.supportsForcedToolChoice) {
            put(TYPE, "tool")
            put("name", choice.toolName)
        } else {
            put(TYPE, "auto")
        }
        // ADD after the if/else, both shapes, only when true (existing test asserts exact {"type":"auto"}):
        // if (call.request.singleToolCall) put("disable_parallel_tool_use", true)
    }
}
```
Introduce a named const for the key like the file's other keys (`TYPE`). Body key order must not change.

### `PROV/chat/ChatEncoder.kt` lines 65-67 and `OpenAiModelRules.kt`
```kotlin
if (vendor.parallelToolCallsFalseOnForced && (required != null || strictNames.isNotEmpty())) {
    put(KEY_PARALLEL_TOOL_CALLS, false)
}
```
Extend to `|| request.singleToolCall` and `&& rules.acceptsParallelToolCalls`. `rules` comes from `ChatModels.wireRules(vendor, call.model)`. Add the field to internal `ChatWireRules(reasoningEffortWithTools, tokenParam, minTokens)` (OpenAiModelRules.kt:~34-43), update its `toString`, and derive false from `O_SERIES = Regex("""^o\d.*$""")` (line 17) in `OpenAiModelRules.wireRules` (the `rules(...)` helper builds instances, so thread the new arg there). OpenRouter stays untouched (`ChatVendor.OPENROUTER.parallelToolCallsFalseOnForced = false`).

### `FIX/FakeAiProvider.kt` - helpers `refusal(usage)`, `toolCalls(vararg)`
Copy `toolCall` (lines 83-90):
```kotlin
public fun toolCall(callId: String, name: String, arguments: JsonObject, usage: Usage): ModelResult =
    ModelResult.Success(ModelResponse(
        AssistantMessage(listOf(AssistantPart.ToolCall(callId, name, arguments))), StopReason.TOOL_USE, usage))
```
`refusal` = `Success(ModelResponse(AssistantMessage(emptyList()), StopReason.REFUSAL, usage))` (verify `AssistantMessage` accepts empty parts). Fake exposes `calls`, `callCount`, throws `AssertionError` on exhausted script. Constructors: `FakeAiProvider(id, vararg results)`.

### Pipeline-level test wiring (SingleShotRequest/Resolve/OutcomeMapping/Terminal/Limits/UserTurn/Acceptance)
**Analog:** `RedactionCanaryTest.kt:358-380` (routed) - copy this skeleton:
```kotlin
val source = ScriptedSelectionSource.fixed(selection)      // ProviderSelection(provider, model)
val pipeline = commandPipeline {
    tier(strategy)
    provider(fake)
    providerSelection = source
    this.credentials = credentials                          // ScriptedCredentialSource.keys(ProviderId.X to "key")
    this.listener = listener
    gate = ScriptedGate.admitAll()
    commitSink = RecordingCommitSink()
}
val outcome = pipeline.execute(CommandInput("text", "en", ctx))
```
Wrap in `runTest { NoNetworkGuard.during { ... } }` (DeferModeTest.kt:54-55). Run ids: `runIds = { "run-${ids.incrementAndGet()}" }` (DeferModeTest.kt:50).

**S2/S4/S11 deferred and amended flow** (DeferModeTest.kt:54-97):
```kotlin
val held = pipeline.execute(CommandInput("save it")).held.single()
val child = pipeline.commitHeld(held)                     // or commitHeld(held, listOf(amended))
assertEquals(original.runId, child.parentRunId)
assertEquals(listOf(original.runId, child.runId), sink.closedRunIds)
assertSame(first, pipeline.commitHeld(held))              // idempotent
```
Mutation fixture: `FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))` with `applyCount`; for context-carrying/per-item: ctor `FakeMutation(toolName, behavior, targetIds, context, log)` (FakeMutation.kt:16-22). Gate lambda reading context: `ScriptedGate { proposal -> if (weak(proposal)) GateDecision.Hold("weak_match","needs_confirm") else GateDecision.Admit() }`; asserts `gate.calls`, `gate.proposals.single().mutations.size`.

**S9 terminal:** `TerminalCallTest.kt:34-47,49-70` (`TerminalCall`, `ToolSpec.clarification(name)`, `completed.terminalCall`, `assertNull(completed.reply)`).

**Limits test pre-call ceiling:** tier-1 `ScriptedStrategy(StrategyId("grammar"), { _, session -> session.recordTurn(TurnRecord(null,null,null,emptyList(),Usage(0,0,0,TierPolicy.DEFAULT.tokenCeiling),1L)); StrategyOutcome.Escalate(EscalationReason.NoToolCall()) })` then SingleShot tier; assert `fake.callCount == 0`. Custom policy via `TierPolicy { maxTokensPerTurn = 777 }`; assert `fake.calls.single().request.maxTokens`. Use `fake.calls[i].request.system/tools` equality for cache-safety.

### `PROV_TEST/SingleShotWireTest.kt` (MockWebServer, strategy -> real transport)
**Analog:** `chat/ChatPipelineRetryTest.kt:60-105` (and `anthropic/AnthropicPipelineRetryTest.kt` for Anthropic). Copy:
```kotlin
MockWebServer().use { server ->
    server.enqueue(MockResponse().setResponseCode(200).setBody(chatBody(chatMessage(null, listOf(chatToolCall("call_1","log_food",args))), "tool_calls", chatUsage(1920,55,cached=1800))))
    server.start()
    val configure: ChatCompletionsProvider.Builder.() -> Unit = { baseUrl = server.url("/") ; sleep = { waits.add(it) } }
    val chat = ChatCompletionsProvider.openAi(configure)   // .openRouter(configure) for OpenRouter
    // pipeline: tier(SingleShotStrategy(...)); provider(chat); providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model)); credentials = ScriptedCredentialSource.keys(vendor.providerId to "sk-test-key")
}
```
Inspect the body with `server.takeRequest().body.readUtf8()`; assert OpenRouter body lacks `parallel_tool_calls` and Anthropic body `tool_choice` has `disable_parallel_tool_use`. Legacy `okhttp3.mockwebserver` only (never `mockwebserver3`). Helpers `chatBody`, `chatToolCall`, `logFoodTool()`, `FIXED_SYSTEM` live in `chat/ChatFixtures.kt`/`ChatRequestFixtures.kt`; Anthropic equivalents in `anthropic/AnthropicFixtures.kt`. Runs under `runBlocking` with `@Test(timeout = 30_000)`. The neutral flag test fixtures must use neutral tool names (note existing fixtures use `log_food`, only in test code; CLN-02 scans `src/main` only).

### Encoder tests (`AnthropicEncoderTest`, `ChatEncoderTest`, `OpenAiModelRulesTest`)
Anthropic (AnthropicEncoderTest.kt:170-185): helper `call(...)` builds `ProviderRequest`; `parse(call(...)).getValue("tool_choice")` and `assertEquals(buildJsonObject { put("type","tool"); put("name","add_item") }, forced)`. Add `singleToolCall` param to the helper; cover forced, reshape (`ModelCapabilities { supportsForcedToolChoice = false }`), and flag-false-omitted. OpenAiModelRulesTest: `assertRules(id, viaRouter, effort, tokenParam, minTokens)` style; add a check on `acceptsParallelToolCalls` for `o3`, `o4-mini` (false) vs `gpt-5.4-mini` (true). ChatEncoderTest already has `assertNull(body["parallel_tool_calls"])` (line 172) and a key-order list (line 257): keep order unchanged.

## Shared Patterns

### Redaction / `toString`
**Source:** `ModelRequest.toString` (ModelRequest.kt:53-56), `CommandSession.toString` (63-65), `ToolStep.Mutation.toString`.
**Apply to:** every new public type (`ToolingSnapshot`, `Extraction`, `Resolution.*`, `UserTurnContext`, `SingleShotStrategy`). Print lengths, counts, tool names only. Add each to `RedactionCanaryTest.sweepBuiltTypes` and `see(...)` sweeps (RedactionCanaryTest.kt:~350-389) plus a SingleShot-routed run with canary transcript/args/system/reply.

### Frozen-API shape rules
**Source:** `ApiShapeTest.kt` (no default-arg ctor stubs; closed STUB_EXCEPTIONS), `scripts/review-api-surface.sh` (exactly seven sealed types allowed; no enums/data classes), `TierPolicy.Builder` growth idiom. `explicitApi()` strict: every public member needs `public` + explicit types + KDoc.

### Scanners
**Source:** `NoHardCodedConstantsTest.kt`, `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml`. No model-family words in `src/main` (even comments), no `const val` named DEFAULT_/MIN_/MAX_/TOKEN/ITERATION/CEILING outside owner files, no `60000`/`4096` literals outside TierPolicy.kt, no `System.getenv/getProperty/java.io.File`, no `runCatching/println/printStackTrace`, no planning ids in comments, detekt ReturnCount<=2/MagicNumber/LongMethod.

### Never-throw collapse
**Source:** `TierWalk.kt:60-66`. Strategy lets resolver/exception propagate; no `catch (e: Exception)`.

### Write path
**Source:** `CommitCoordinator.submitMutation` (CommitCoordinator.kt:110-118) and `applyAll` (137-145). Strategy never applies; `session.submit(ToolStep...)` only. Gate once per `ToolStep.Mutation`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `CORE/strategy/singleshot/UserTurn.kt` default renderer | utility | transform | No existing date-time/clock rendering code in `:core` main; use `java.time.Clock`/`ZonedDateTime` + `DateTimeFormatter.ISO_OFFSET_DATE_TIME` per RESEARCH. Test side may reuse `FIX/FakeClock` (check whether it is `java.time.Clock` compatible; if not, build `Clock.fixed(...)`). |

Also note: `SingleShotStrategy` is the first real tier (only `ScriptedStrategy` exists), so there is no production strategy analog; patterns above are composite.

## Metadata

**Analog search scope:** `core/src/{main,test,testFixtures}`, `providers/src/{main,test}`, `scripts/`
**Files scanned:** ~35 read or grepped
**Pattern extraction date:** 2026-10-01
