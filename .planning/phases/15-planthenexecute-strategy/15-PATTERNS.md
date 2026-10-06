# Phase 15: PlanThenExecute Strategy - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 17 new/modified
**Analogs found:** 16 / 17 (all analog paths are git-tracked; verified via `git ls-files`)

All main paths below are relative to
`core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/` (written `MAIN/`); tests relative to
`core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/` (`TEST/`); fixtures `core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/` (`FIX/`).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `MAIN/strategy/PreparedStep.kt` (NEW, pure move) | utility | request-response | `MAIN/strategy/agentic/AgenticDispatch.kt:127-136` (source of the move) | exact |
| `MAIN/strategy/agentic/AgenticDispatch.kt` (EDIT, delegate only) | service | request-response | itself | exact |
| `MAIN/strategy/plan/PlanThenExecuteStrategy.kt` | strategy (public class + Builder) | request-response, multi-step | `MAIN/strategy/singleshot/SingleShotStrategy.kt` | exact |
| `MAIN/strategy/plan/PlanSchema.kt` | utility | transform | `core/src/test/.../SingleShotTestSupport.kt:entriesTool()` (buildJsonObject schema); `ToolSpec.kt` | role-match |
| `MAIN/strategy/plan/PlanParse.kt` | utility | transform | `SingleShotStrategy.dispatch` (lines 133-141) | partial |
| `MAIN/strategy/plan/PlanBinding.kt` | utility | transform | none in repo (research sketch) | no analog |
| `MAIN/strategy/plan/PlanRun.kt` | service | sequential submit | `AgenticDispatch.settle` (98-102) + `TierWalk.hasWorked` | role-match |
| `MAIN/strategy/plan/PlanReplan.kt` | utility | request-response | `AgenticLoopStrategy.dispatchTurn` (history add, 218-232) | role-match |
| `MAIN/telemetry/TraceCode.kt` (EDIT, optional codes) | model | n/a | existing `agenticLoopCodes` entries (`TraceCode.kt:105-114`) | exact |
| `TEST/PlanTestSupport.kt` + `PlanThenExecute*Test.kt`, `PlanBindingTest.kt`, `PlanSchemaTest.kt` | test | pipeline | `TEST/SingleShotTestSupport.kt`, `TEST/SingleShotOutcomeMappingTest.kt` | exact |
| `TEST/TraceTest.kt` (EDIT, code rows) | test | n/a | `TraceTest.kt:230-250` (`grammarCodes` map) | exact |
| `TEST/ApiShapeTest.kt` (EDIT) | test | n/a | same file (no SingleShot-specific rows; follow its generic reflection style) | role-match |
| `providers/src/test/.../providers/PlanThenExecuteWireTest.kt` | test | request-response wire | `providers/src/test/.../AgenticLoopWireTest.kt:246-252` | exact |
| `providers/src/test/.../providers/PlanBindingLiveProbeTest.kt` | test (opt-in live) | request-response | `providers/src/test/.../anthropic/AnthropicLiveCaptureTest.kt` | exact |
| `providers/build.gradle.kts` (EDIT, `livePlanProbe` task) | config | n/a | `liveAnthropicCapture` task, lines 68-86 | exact |
| `API.md` (EDIT) | doc | n/a | existing `SingleShotStrategy` rows (lines 35, 79, 197) | exact |
| `core/api.txt` | config | n/a | NOT regenerated this phase (equals last tag v1.0.1) | n/a |

## Pattern Assignments

### `MAIN/strategy/plan/PlanThenExecuteStrategy.kt` (strategy, request-response)

**Analog:** `MAIN/strategy/singleshot/SingleShotStrategy.kt` (read in full).

**Imports** (lines 1-37): same list as SingleShot minus `OutcomeResolver`/`resolutionOutcome`/`submitSteps`; add `ToolExecutor`, `OutcomeHooks`/`decideResult` from `...strategy.singleshot`, `ToolMessage`s (`ToolResultsMessage`, `AssistantMessage` from `transcript`).

**Class skeleton** (lines 56-76, copy shape):
```kotlin
public class SingleShotStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    override val capabilities: StrategyCapabilities = settings.capabilities
    private val tooling: ToolSpecProvider = requireNotNull(settings.tooling) {
        "SingleShotStrategy: tooling is required"
    }
    private val userTurn: UserTurnRenderer = settings.userTurn
    private val clock: Clock = settings.clock
    private val reasoning: ReasoningMode = settings.reasoning
    private val hooks = OutcomeHooks(settings.onNoToolCall, settings.onRefusal, settings.onFailed)

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        ceilingReached(session) ?: withTooling(input, session)
```
Plan adds `executor` (requireNotNull, "PlanThenExecuteStrategy: executor is required") and `maxSteps` (`require(maxSteps >= 1)`).

**Model refusal + ask** (lines 92-98):
```kotlin
val model = attempt.session.model()
return model.refusal?.let { StrategyOutcome.Failed(it) } ?: ask(attempt, model)
```

**Request shape** (lines 100-112; render user turn ONCE per command for Plan, reuse same `UserMessage` in replan):
```kotlin
val context = UserTurnContext(attempt.input, ZonedDateTime.now(clock), attempt.session.carry)
return ModelRequest(
    attempt.snapshot.system,
    listOf(UserMessage(userTurn.render(context))),
    attempt.snapshot.tools,          // Plan: listOf(submitPlanSpec) + snapshot.tools
    attempt.choice,                  // Plan: ToolChoice.Required("submit_plan")
    attempt.session.policy.maxTokensPerTurn,
    CacheDirective(true),
    true,
    reasoning,
)
```

**Result routing**: `decideResult(result, hooks) ?: route(...)` (line 118). Plan must intercept `StopReason.MAX_TOKENS` BEFORE `decideResult` and return `Escalate(MalformedExtraction(), session.carry)` (RESEARCH Pitfall 1; `decideResult` otherwise yields `Failed(MaxTokens)` via `StrategyLimits.kt:39-45`).

**Tool-name guard pattern** (line 37, 78-80): `private const val TOOL_MISSING_CODE = "single_shot_tool_missing"` -> `Failed(FailureReason.Other(...))`; Plan uses `plan_tool_name_taken` / `plan_no_tools`. Constant names must avoid `^(DEFAULT_|MIN_|MAX_)|TOKEN|ITERATION|CEILING` (use `PLAN_STEP_LIMIT`, `REPLAN_LIMIT`).

**Extras dropped** (lines 124-128): `session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)`; completed outcome rebuilt with `partial = true`.

**toString** (line 145): `"SingleShotStrategy(id=$id, forceTool=$forceTool)"` -> Plan prints id and `maxSteps` only.

**Builder** (lines 156-232): public `Builder internal constructor()`, documented `public var` per setting, `capabilities`, `userTurn`, `clock`, `reasoning`, `onNoToolCall`, `onRefusal`, `onFailed` with the identical KDoc/defaults; companion:
```kotlin
public companion object {
    public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): SingleShotStrategy =
        SingleShotStrategy(id, Builder().apply(block))
}
```
Repo gates: no public `const`, `data`, enum, `sealed`; no `Phase NN D-NN` / `T-nn-nn` / `WR-nn` in comments; no food/card/note words in KDoc.

---

### `MAIN/strategy/PreparedStep.kt` + `AgenticDispatch.kt` edit (utility, pure move)

**Analog / source:** `MAIN/strategy/agentic/AgenticDispatch.kt:127-136` and `:21`.
```kotlin
private suspend fun prepare(context: DispatchContext, spec: ToolSpec, call: AssistantPart.ToolCall): ToolStep =
    guarded(onFault = {
        context.session.recordCode(TraceCode.TOOL_PREPARE_ERROR)
        faultStep(spec)
    }) { context.executor.prepare(Extraction(call.name, call.arguments, call.id), context.input) }

private fun faultStep(spec: ToolSpec): ToolStep {
    val kind = if (spec.mutating) FinishedKind.ERROR else FinishedKind.READ
    return ToolStep.Finished(spec.name, kind, StepResult(TOOL_ERROR_CONTENT, true))
}
// :21  private const val TOOL_ERROR_CONTENT = """{"status":"error","reason":"tool_error"}"""
```
New shape: `internal suspend fun prepareGuarded(session, spec, executor, input, extraction): ToolStep` in package `core.strategy`; `AgenticDispatch.prepare` becomes a one-line delegate; `TOOL_ERROR_CONTENT` bytes identical. Import `core.internal.guarded` (the only place with the justified `TooGenericExceptionCaught` suppression is `internal/Guarded.kt`; never catch generically elsewhere). Do not edit `StepSubmission.kt`. Re-run `AgenticLoopDispatchTest`, `AgenticLoopGuardsTest`, `AgenticLoopGateTest`, `ToolExecutorSeamTest`.

---

### `MAIN/strategy/plan/PlanRun.kt` (service, sequential submit)

**Analog:** `AgenticDispatch.kt:98-102` (settle) for the submit pattern:
```kotlin
val step = guardWrites(context, spec, prepare(context, spec, call))
val dispatch = context.session.submit(step, call.id)
return ToolResult(call.id, dispatch.contentForModel, dispatch.isError)
```
Plan: `prepareGuarded(session, spec, executor, input, Extraction(step.tool, boundArgs, planCall.id))` then `session.submit(prepared, planCall.id)`. Do NOT use `submitSteps` (returns `Completed`, discards `DispatchResult`). Classify with `DispatchResult.held` / `actions.all { it.kind == ActionKind.COMMITTED }` / `actions.any { it.applied }` (`ToolStep.kt:111-116`, `ActionKind.kt:18-27`). Merge `targetIds` per `ApplyStep.kt:111` (result wins). Post-work stops return `Escalate`, never `Failed`; `TierWalk.kt:87-88,112` converts to `escalation_suppressed` partial. Skeleton in RESEARCH "Step loop skeleton" (keep functions short for detekt `ReturnCount`/`LongMethod`).

---

### `MAIN/strategy/plan/PlanReplan.kt` (utility, conversation continuation)

**Analog:** `AgenticLoopStrategy.kt:218-232`:
```kotlin
history.add(response.message)
history.add(ToolResultsMessage(turn.results))
```
Replan request = same system/tools/choice/maxTokens/cache/single/reasoning with `messages = [firstUser, response1.message, ToolResultsMessage(one ToolResult per call id, in order)]`. `ToolResultsMessage` rejects empty / duplicate ids (`Message.kt`). Fixed-vocabulary digest only (no app values, no model-written text in Trace/Reason).

---

### `MAIN/strategy/plan/PlanSchema.kt` (utility, transform)

**Analog:** `TEST/SingleShotTestSupport.kt:entriesTool()` (lines 48-60) for `buildJsonObject` / `putJsonObject` schema construction and `ToolSpec(name, description, schema, mutating)` positional ctor. Build with one fixed key order (cache prefix bytes). `submit_plan`: `mutating=false, terminal=false, strict=false`. `$` must be `\$` in normal strings or `${'$'}` in raw strings; test the rendered description contains the literal `$<stepId>.<key>`.

---

### `MAIN/strategy/plan/PlanParse.kt` (utility, transform) and `PlanBinding.kt` (no analog)

**Partial analog:** `SingleShotStrategy.dispatch` (133-141): unknown tool -> `Escalate(MalformedExtraction())`, terminal -> not executed. Plan parse must never throw (return a verdict type, internal, not public sealed unless allow-listed; use internal class hierarchy is fine since only PUBLIC sealed is banned). Record `TraceCode.UNKNOWN_TOOL` when a step names a non-snapshot tool. `PlanBinding.kt` has no codebase analog: use the RESEARCH Pattern 2 sketch (`refOf`, `refsIn`, `bind`) tree-walking `JsonObject`/`JsonArray`; id regex `^[A-Za-z][A-Za-z0-9_-]*$`.

---

### `MAIN/telemetry/TraceCode.kt` (optional new codes) and `TEST/TraceTest.kt`

**Analog:** `TraceCode.kt:105-114` entries (`TraceCode("unknown_tool")`, etc.) and `TraceTest.kt:230-250`:
```kotlin
private val grammarCodes = mapOf(
    TraceCode.GRAMMAR_AMBIGUOUS to "grammar_ambiguous",
    ...
)
@Test
fun theGrammarCodesHaveTheirSnakeCaseWireValues() {
    assertEquals(SIX, grammarCodes.size)
    grammarCodes.forEach { (code, wire) -> assertEquals(wire, code.value) }
}
```
Add a `planCodes` map the same way for any new code (`plan_replanned`, `plan_rejected`, `plan_binding_unresolved`; zero new codes is viable) plus an `API.md` Telemetry sentence.

---

### `TEST/PlanTestSupport.kt` and `PlanThenExecute*Test.kt` (test, pipeline)

**Analog:** `TEST/SingleShotTestSupport.kt` (`fixedClock`, `pipelineOf`, `testKey`, `entriesTool()`, `ASK_TOOL`) and `TEST/SingleShotOutcomeMappingTest.kt` (imports lines 1-40: `FakeAiProvider`, `FakeMutation`, `ScriptedGate`, `ScriptedStrategy`, `RecordingCommitSink`, `runTest`; the `onFailed` matrix to mirror for PLAN-05). Fixtures in `FIX/`: `FakeAiProvider.toolCall(id, name, args, Usage(...))`, `FakeMutation`, `ScriptedToolExecutor`, `ScriptedGate.admitAll/holdAll`, `RecordingCommitSink`, `ScriptedStrategy` (next tier, assert `executions == 0` after suppression). Neutral tool names only (`create_item`, `tag_item`, `find_item`, `ask_user`, key `item_id`). Suppression-test and call-count assertions per RESEARCH Validation map. Banned-constant naming applies to test files too only where `NoHardCodedConstantsTest` scans (main sources), but keep tests tidy.

---

### `providers/src/test/.../PlanThenExecuteWireTest.kt` (test, wire)

**Analog:** `AgenticLoopWireTest.kt:246-252` and helper `cachedPrefix` (line 182), `LoopDialect`:
```kotlin
val (first, second) = exchange.bodies.map { it.cachedPrefix(LoopDialect.ANTHROPIC) }
assertEquals(first, second)
```
Compare tools+system bytes only (not `messages[0]`: reshape mode appends an instruction to the last user message). Legacy `okhttp3.mockwebserver` only. Use `@Test(timeout = 30_000)`.

### `providers/.../PlanBindingLiveProbeTest.kt` and `providers/build.gradle.kts`

**Analog:** `providers/src/test/.../anthropic/AnthropicLiveCaptureTest.kt` and the `liveAnthropicCapture` task (`build.gradle.kts:68-86`):
```kotlin
tasks.register<Test>("liveAnthropicCapture") {
    group = "verification"
    description = "..."
    val testSet = liveTestSet
    testClassesDirs = testSet.output.classesDirs
    classpath = testSet.runtimeClasspath
    filter { includeTestsMatching("*AnthropicLiveCaptureTest") }
    outputs.upToDateWhen { false }
    val optIn = providers.environmentVariable("VAE_LIVE_ANTHROPIC")
    onlyIf { optIn.orNull == "1" }
    testLogging { showStandardStreams = true }
}
```
Copy as `livePlanProbe` with `VAE_LIVE_PLAN`, `includeTestsMatching("*PlanBindingLiveProbeTest")`; class name contains `Live` so `test` (line 62 `excludeTestsMatching("*Live*")`) keeps excluding it. Hard request counter (<= 8), ids/counts only.

---

### `API.md`

**Analog:** the `SingleShotStrategy` rows: package table line 35 (`core.strategy.singleshot SingleShotStrategy` -> add `core.strategy.plan PlanThenExecuteStrategy`), type row line 79 (`| \`SingleShotStrategy\` | class | | ... |`), Strategies bullet line 197 (`- \`SingleShotStrategy(id) { ... }\`: ...`). C20 greps for the backticked type name; C21 bans domain words. Phase 19 owns the rest of DOC-02.

## Shared Patterns

### Never-throw guarding
**Source:** `MAIN/internal/Guarded.kt` (`guarded(onFault, block)`). **Apply to:** all executor calls (via `prepareGuarded`). Never `catch (e: Exception)` elsewhere; no `runCatching`/`println`.

### Escalate-after-work suppression
**Source:** `MAIN/pipeline/TierWalk.kt:87-88,112,118-122`. **Apply to:** every Plan stop after any apply/hold: return `Escalate`, never `Failed`.

### Outcome hooks / failure routing
**Source:** `MAIN/strategy/singleshot/SingleShotOutcomes.kt:17-47` (`OutcomeHooks`, `decideResult`, internal, import in place). **Apply to:** Plan execute (with MAX_TOKENS pre-intercept).

### Budget
**Source:** `MAIN/strategy/StrategyLimits.kt:24,28` (`ceilingReached` before call, `ceilingCrossed` after each call). **Apply to:** both plan and replan calls.

### Secrets/redaction
Fixed codes and counts only in `TraceCode`, `EscalationReason.Other` (non-blank safe token), `FailureReason.Other`, `toString()`; never model step ids, arguments or bound ids.

### Repo gates
`NoHardCodedConstantsTest` regex (avoid `DEFAULT_|MIN_|MAX_`, `TOKEN`, `ITERATION`, `CEILING` in const names), banned planning-id comment pattern (`gradle/invariants.gradle.kts:31`), detekt zero baseline, explicit API mode, `scripts/review-api-surface.sh` (no sealed/data/enum/public static).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `MAIN/strategy/plan/PlanBinding.kt` | utility | JsonObject transform | No existing reference-substitution code; use RESEARCH Pattern 2 sketch (note Kotlin `$` escaping) |

## Metadata

**Analog search scope:** `core/src/main/.../strategy/{singleshot,agentic}`, `core/src/test`, `core/src/testFixtures`, `providers/src/test`, `providers/build.gradle.kts`, `API.md`.
**Files read this session:** SingleShotStrategy.kt, SingleShotOutcomes.kt, AgenticDispatch.kt (excerpts), AgenticLoopStrategy.kt (excerpt), Guarded.kt (head), SingleShotTestSupport.kt (head), SingleShotOutcomeMappingTest.kt (head), AgenticLoopWireTest.kt (excerpt), TraceTest.kt (excerpt), providers/build.gradle.kts (excerpt), API.md (excerpts). Other anchors (TierWalk, ApplyStep, ToolStep, StrategyLimits) are taken from 15-RESEARCH.md anchor table (read there verbatim).
**Pattern extraction date:** 2026-10-06
