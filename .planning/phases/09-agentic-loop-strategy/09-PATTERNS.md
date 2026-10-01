# Phase 9: Agentic Loop Strategy - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 17 new/modified (see table)
**Analogs found:** 17 / 17 (all analog paths verified git-tracked via `git ls-files`)

All paths are relative to `/home/yahir/Projects/Reusable/android/voice-action-engine`. `CORE` = `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`; `CT` = `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core`; `FIX` = `core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `CORE/strategy/agentic/AgenticLoopStrategy.kt` (class, Builder, Companion.invoke) | strategy | request-response, multi-turn loop | `CORE/strategy/singleshot/SingleShotStrategy.kt` | exact (shape), loop is new |
| `CORE/strategy/agentic/AgenticTurn.kt` (classify ModelResult / stop table) | utility | transform | `CORE/strategy/singleshot/SingleShotOutcomes.kt` (`decideResult`/`stopOutcome`) | exact |
| `CORE/strategy/agentic/AgenticValidation.kt` (whole-turn ids) | utility | transform | `providers/.../transcript/ConversationCheck.kt:83-92` (within-turn dup) + `Message.kt` `ToolResultsMessage` init | role-match |
| `CORE/strategy/agentic/AgenticDispatch.kt` (per-call dispatch, strikes, terminal) | service | CRUD/sequential dispatch | `SingleShotStrategy.kt:131-166` (`route/dispatch/submitAll`) | role-match |
| `CORE/strategy/ToolExecutor.kt` (new public `fun interface`) | seam | request-response | `CORE/strategy/OutcomeResolver.kt:17-20` (+ `Extraction` at 28-45) | exact |
| `CORE/strategy/StrategyLimits.kt` (moved `ceilingReached/ceilingCrossed/tokenBudgetFailure`, `CurrentZoneClock`) | utility | transform | `SingleShotOutcomes.kt:69-78`, `SingleShotStrategy.kt:34-47` | exact (move) |
| `CORE/telemetry/TraceCode.kt` (3 new constants) | model | n/a | `TraceCode.EXTRA_TOOL_CALLS_DROPPED` (line 105) | exact |
| `CORE/commit/CommitCoordinator.kt` `applyAll` (O-1 `ensureActive`) | service | CRUD | same file lines 66-69 (`admitCaller`), 137 | exact |
| `FIX/ScriptedToolExecutor.kt` | test fixture | request-response | `FIX/ScriptedGate.kt` (+ `CT/SingleShotTestSupport.kt` `RecordingResolver`) | exact |
| `CT/AgenticLoopTestSupport.kt` (`agenticLoop`, `pipelineOf(providerId)`, answer builders) | test util | n/a | `CT/SingleShotTestSupport.kt` | exact |
| `CT/AgenticLoop{Dispatch,Gate,Terminal,Guards,ExitPaths,Carry,ProviderNeutrality}Test.kt`, `ToolExecutorSeamTest.kt`, `BatchCancellationTest.kt` | test | request-response | `CT/SingleShot{Plumbing,Resolve,OutcomeMapping,Terminal}Test.kt`, `CT/BatchIsolationTest.kt` | role-match |
| `CT/AgenticLoopLimitsTest.kt` (6 / 60000 / 4096) | test | request-response | `CT/SingleShotLimitsTest.kt` | exact |
| `CT/AgenticLoopUserTurnTest.kt` | test | request-response | `CT/SingleShotUserTurnTest.kt` | exact |
| fake-provider scripts (inside the tests via `FakeAiProvider`) | test data | streaming script | `FIX/FakeAiProvider.kt` builders (`toolCall`, `toolCalls`, `reply`, `refusal`) + `answerOf`/`callOf` | exact |
| `providers/src/test/.../providers/AgenticLoopWireTest.kt` | test | request-response (MockWebServer) | `providers/src/test/.../providers/SingleShotWireTest.kt` | exact |
| `gradle/invariants.gradle.kts` (CLN-02 raw-text rules) + `config/negative-controls/app-domain.kt.txt` + `scripts/verify-negative-controls.sh` plants | config / build gate | batch scan | `scanText`/`bannedRules` in `gradle/invariants.gradle.kts:20-31,153-179`; `config/negative-controls/planning-ids.kt.txt`; plants at `scripts/verify-negative-controls.sh:53-65` | exact |
| `evidence/{phase-gate,agentic-limits-tests,agentic-surface-review,seam-signoff}.txt` | evidence | n/a | `.planning/phases/07-singleshot-strategy/evidence/{phase-gate,singleshot-limits-tests,singleshot-surface-review,seam-signoff}.txt` | exact |

Phase 8 mappers (`providers/.../anthropic`, `chat`) are NOT edited by Phase 9; they are consumed only through `ModelRequest`/`ModelResult` (see Pattern: neutral request). Their only touch point is the wire test and the `ConversationCheck` carry.

## Pattern Assignments

### `CORE/strategy/agentic/AgenticLoopStrategy.kt` (strategy, multi-turn request-response)

**Analog:** `CORE/strategy/singleshot/SingleShotStrategy.kt`

**Class shell, required-setting messages, toString** (lines 70-86, 168-169):
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
    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        ceilingReached(session) ?: withTooling(input, session)
    override fun toString(): String = "SingleShotStrategy(id=$id, forceTool=$forceTool)"
```
Copy for the loop: `AgenticLoopStrategy internal constructor(id, settings)`, `requireNotNull(settings.executor) { "AgenticLoopStrategy: executor is required" }`, toString prints id only. Builder members: `tooling`, `executor`, `capabilities`, `userTurn` (REQUIRED by the orchestrator carry: `UserTurnRenderer.standard()` default), `clock`. Do NOT copy `forceTool`, `resolver`, `onNoToolCall`, `onRefusal`.

**Tooling once, model refusal gate** (lines 89-104):
```kotlin
val snapshot = tooling.tooling(input)          // once per command; pass snapshot.system / snapshot.tools (same instances) every iteration
...
val model = attempt.session.model()
return model.refusal?.let { StrategyOutcome.Failed(it) } ?: ask(attempt, model)
```
Use a small carrier class like `private class Attempt(input, session, snapshot, ...)` (SingleShotStrategy.kt:171-176) to dodge detekt `LongParameterList`; add a `LoopState` carrier for history / seen ids / strikes.

**Request building** (lines 109-120). DIFFERENCES are load-bearing:
```kotlin
val context = UserTurnContext(attempt.input, ZonedDateTime.now(clock), attempt.session.carry)
return ModelRequest(
    attempt.snapshot.system,
    listOf(UserMessage(userTurn.render(context))),   // loop: history list, append-only after this
    attempt.snapshot.tools,
    attempt.choice,                                   // loop: ToolChoice.Auto() always
    attempt.session.policy.maxTokensPerTurn,          // same, on EVERY iteration
    CacheDirective(true),
    true,                                             // loop: FALSE (parallel emission, D-09)
)
```
Render the user turn once; later iterations reuse `UserMessage` and append `response.message` (same instance, keeps `nativeReplay`) then a `ToolResultsMessage` in call order. Never call `session.recordTurn` (the routed model already records).

**Terminal tool** (line 147): `tool.terminal -> StrategyOutcome.Completed(null, TerminalCall(call.name, call.arguments))`. Loop: same, but stop dispatching, send no results message, no further request (D-13).

**Dropped extras** (lines 131-141): `recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)` and `Completed(outcome.reply, outcome.terminalCall, true)` is the only precedent for `partial = true`; reuse for calls after a terminal call (Open Question 4/5; budget stays `Failed`).

**Builder + companion** (lines 178-233): copy the KDoc-per-member style and
```kotlin
public companion object {
    public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): SingleShotStrategy =
        SingleShotStrategy(id, Builder().apply(block))
}
```
Move `private object CurrentZoneClock` (lines 36-47) to `CORE/strategy/StrategyLimits.kt` as `internal` first; both strategies then default `clock = CurrentZoneClock`.

**Errors:** never catch `CancellationException`; wrap app code (`executor.prepare`, `tooling`, `userTurn`) with the internal `guarded` (`CORE/internal/Guarded.kt:38-39,53-69`), not `catch (e: Exception)` and not `runCatching` (banned by scanner). Constant naming: avoid `MAX_/DEFAULT_/MIN_` or names containing TOKEN/ITERATION/CEILING (`NoHardCodedConstantsTest`); use e.g. `STRIKES_TO_ABORT = 2`. No model-family words in KDoc. No planning ids (`Phase 9 D-01`, `T-09-03`) in comments.

---

### `CORE/strategy/agentic/AgenticTurn.kt` (utility, transform)

**Analog:** `CORE/strategy/singleshot/SingleShotOutcomes.kt:25-54`

Four-stop table is decided BEFORE tool-call presence; share it, do not fork the leaves:
```kotlin
when (response.stopReason) {
    StopReason.REFUSAL -> hooks.onRefusal(response)        // loop: Failed(FailureReason.Refusal())
    StopReason.MAX_TOKENS -> StrategyOutcome.Failed(FailureReason.MaxTokens())
    StopReason.PAUSE_TURN -> StrategyOutcome.Failed(FailureReason.PauseTurn())
    StopReason.CONTEXT_WINDOW_EXCEEDED -> StrategyOutcome.Failed(FailureReason.ContextWindowExceeded())
    else -> if (response.message.toolCalls.isEmpty()) noToolCallOutcome(response, hooks) else null
}
// no calls: END_TURN -> (loop) Completed(first Text part); TOOL_USE -> MalformedResponse(); else -> UnknownStop()
```
and `failureOutcome` (lines 32-37): `else -> StrategyOutcome.Failed(failure.reason, failure.details)` (keeps `httpStatus`). Keep the `else ->` branch on open value classes (`StopReason`, `ModelResult`, line 29: `FailureReason.Other(code)`). Dispatch is by tool-call presence (D-11): a `END_TURN` plus calls is a tool turn.

Reply (D-06): `response.message.parts.filterIsInstance<AssistantPart.Text>().firstOrNull()?.text`.

---

### `CORE/strategy/StrategyLimits.kt` (utility, moved from singleshot)

**Analog:** `SingleShotOutcomes.kt:69-78` (move verbatim, API-neutral, in the first task of the first plan)
```kotlin
internal fun ceilingReached(session: CommandSession): StrategyOutcome? =
    if (session.tokensUsed >= session.policy.tokenCeiling) tokenBudgetFailure() else null
internal fun ceilingCrossed(session: CommandSession): StrategyOutcome? =
    if (session.tokensUsed > session.policy.tokenCeiling) tokenBudgetFailure() else null
private fun tokenBudgetFailure(): StrategyOutcome =
    StrategyOutcome.Failed(FailureReason.BudgetExceeded(BudgetBound.TOKENS))
```
Add `iterationBudgetFailure()` = `Failed(BudgetExceeded(BudgetBound.ITERATIONS))` (engine names ITERATIONS/TOKENS, not SB's MAX_ITERATIONS/TOKEN_CEILING). Loop order on a tool turn: ceilingCrossed -> final-iteration (`iteration == policy.maxIterations`) -> whole-turn validation -> dispatch. Structure iteration as `1..policy.maxIterations` so the last iteration always returns (no `error("unreachable")`).

---

### `CORE/strategy/ToolExecutor.kt` (seam, public `fun interface`)

**Analog:** `CORE/strategy/OutcomeResolver.kt:17-45` (pending orchestrator sign-off, Open Question 1)
```kotlin
public fun interface OutcomeResolver {
    /** Resolves [extraction] for the command described by [input]. */
    public suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution
}
```
Proposed: `public fun interface ToolExecutor { public suspend fun prepare(call: Extraction, input: CommandInput): ToolStep }`. Copy the KDoc wording ("validates the arguments itself, because the engine passes them through untouched. It must never write: every write goes through the gate ... as a `ToolStep.Mutation`"). Reuses `Extraction` (redacted `toString`, line ~45). No sealed/enum/data types and no default-arg ctor stubs (`ApiShapeTest`; `scripts/review-api-surface.sh` sealed allow-list stays exactly seven). New public API needs a redacted-`toString` canary entry in `CT/RedactionCanaryTest.kt`.

Sign-off checkpoint precedent: `.planning/phases/07-singleshot-strategy/07-03-PLAN.md` Task 3 (blocking `checkpoint:decision`, `autonomous: false`), recorded in `evidence/seam-signoff.txt` (header: title, `Date:`, `Relay:`, `--- reply (verbatim) ---`, `--- end reply ---`, final `SIGNOFF: APPROVE` line).

---

### `CORE/telemetry/TraceCode.kt` (3 new constants)

**Analog:** line ~105
```kotlin
public val EXTRA_TOOL_CALLS_DROPPED: TraceCode = TraceCode("extra_tool_calls_dropped")
```
Add (names ASSUMED until sign-off) unknown-tool, tool-prepare-fault, read-tool-returned-mutation, each with a KDoc sentence describing the behavior, no planning ids. Codes only; never exception text.

---

### `CORE/commit/CommitCoordinator.kt` `applyAll` (O-1)

**Analog:** same file. Existing per-call guard (line 66-69: `admitCaller()` = `currentCoroutineContext().ensureActive()` + `check(!closed)`); `applyAll` at line 137 is `val changes = mutations.map { applyStep.run(it) }`. Change to `mutations.map { currentCoroutineContext().ensureActive(); applyStep.run(it) }`. Test analog: `CT/BatchIsolationTest.kt` plus `CommitPathTest.aCancelDuringAnApplyIsJournaledBeforeTheCancellationContinues` as `BatchCancellationTest` (assert item 1 `applyCount == 0`, one `RunTermination.Cancelled` close, commit for item 0 kept; same for `applyWithoutGate`/`commitHeld`).

---

### `FIX/ScriptedToolExecutor.kt` (test fixture)

**Analog:** `FIX/ScriptedGate.kt` (public class, KDoc with `@param log`, recording list + `calls`, `companion` of ready-made scripts, `RecordingSink<String>` log constant) and `CT/SingleShotTestSupport.kt:74-97` `RecordingResolver`:
```kotlin
internal class RecordingResolver(private val answer: suspend (Extraction, CommandInput) -> Resolution) : OutcomeResolver {
    private val seenExtractions = CopyOnWriteArrayList<Extraction>()
    private val count = AtomicInteger()
    val extractions: List<Extraction> get() = seenExtractions.toList()
    val invocations: Int get() = count.get()
    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution { ...record...; return answer(extraction, input) }
}
```
Fixture version: public (testFixtures is not published; `verifyNoTestFixturesPublished` guards), takes optional `log: RecordingSink<String>?` and logs `prepare:<tool>` (same idiom as `ScriptedGate`'s `GATE_LOG = "gate"`), so a shared log reads `prepare:a, gate, apply:a, sink:action:..., prepare:b` and proves sequential dispatch. Script exhaustion: reuse `ScriptedResponses` (throws `AssertionError`). Can throw/suspend on cue.

---

### `CT/AgenticLoopTestSupport.kt`

**Analog:** `CT/SingleShotTestSupport.kt`. Reuse as is: `fixedClock` (line 44), `entriesTool()`, `askTool()`, `entriesArguments(marker)`, `snapshotOf(... forced = null)` (loop needs `forced = null`), `answerOf(stop, vararg parts)`, `callOf(id, name, args)`, `testKey()`. Copy the factory shape:
```kotlin
internal fun singleShot(resolver, snapshot, id = "single_shot", configure: SingleShotStrategy.Builder.() -> Unit = {}) =
    SingleShotStrategy(StrategyId(id)) {
        tooling = ToolSpecProvider.fixed(snapshot); this.resolver = resolver; clock = fixedClock; configure()
    }
```
as `agenticLoop(executor, snapshot, id = "agentic", configure)`. Generalize `pipelineOf` (lines 114-135) with `providerId: ProviderId = ProviderId.ANTHROPIC` and credentials built from it; today it hard-codes `ProviderSelection(ProviderId.ANTHROPIC, "test-model")` at line 127 and `testKey()` at 121. Add helpers for multi-call tool-turn answers: `FakeAiProvider.toolCalls(usage, vararg calls)`.

---

### `CT/AgenticLoopLimitsTest.kt` (limits test, MANDATORY for v1.0.0 cut)

**Analog:** `CT/SingleShotLimitsTest.kt` (216 lines). Copy: the private-const block (lines 29-35), class KDoc, `Run` carrier, `spendingTier` (lines 53-60), `assertTokenBudgetFailure` (82-85), `NoNetworkGuard.during { }` wrapper and `runTest`:
```kotlin
private fun spendingTier(tokens: Long): CommandStrategy =
    ScriptedStrategy(StrategyId("earlier"), { _, session ->
        session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, tokens), 1L))
        StrategyOutcome.Escalate(EscalationReason.NoToolCall())
    })
// policy overrides:  val policy = TierPolicy { tokenCeiling = CUSTOM_CEILING }
```
Port all ten method names (atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall, oneTokenBelowTheDefaultCeilingTheCallIsMade, aCustomCeilingIsReadFromTheSessionPolicy, defaultsAreTheContractLimits, defaultPolicySendsTheDefaultPerTurnTokenLimit, customPerTurnLimitIsSentUnchanged, aResponseThatCrossesTheCeilingFailsBefore..., aResponseLandingExactlyOnTheCeilingIsStill..., exactlyOne... replaced by iteration tests). Loop-specific additions: `theFinalIterationGuardNeverDispatchesTheLastPermittedTurn` at `maxIterations = 2` (min) and default 6 (6 provider calls, 5 applies, `BudgetExceeded(ITERATIONS)`); every request of a 3-turn run carries `maxTokens` from policy; end-of-turn response over the ceiling is still `Completed`. Note the test files declare their own private consts named `DEFAULT_*`/`MIN_ITERATIONS`; `NoHardCodedConstantsTest` scans main only, tests are fine.

---

### `CT/AgenticLoopUserTurnTest.kt`

**Analog:** `CT/SingleShotUserTurnTest.kt` (imports lines 1-40: `UserTurnRenderer`, `UserTurnContext`, `Clock`/`ZonedDateTime`/`TimeZone`, `assertSame`, `FakeAiProvider`). Add the SB-shaped renderer assertion on exact bytes:
```kotlin
val sbShaped = UserTurnRenderer { ctx ->
    "Current local date-time: ${ctx.dateTime.toLocalDateTime()} (${ctx.dateTime.zone.id})\n\n" +
        "Voice command: ${ctx.input.transcript}"
}
```
Assert on `fake.calls.first()` first `UserMessage.text`. Also port `twoCommandsShareTheSameSystemAndToolsBytes` as "every iteration shares system and tools instances".

---

### `CT/AgenticLoop{Dispatch,Gate,Terminal,Guards,ExitPaths,Carry,ProviderNeutrality}Test.kt`, `ToolExecutorSeamTest`

**Analogs:** `CT/SingleShotPlumbingTest.kt`, `SingleShotResolveTest.kt`, `SingleShotOutcomeMappingTest.kt`, `SingleShotTerminalTest.kt`, `CommitPathTest.kt:99-130`, `RunClosedPathsTest`, `RedactionCanaryTest`. Same imports block as `SingleShotLimitsTest.kt:3-27`; hand-written fakes only (`FakeAiProvider`, `FakeMutation(name, StepResult(..), log = log)`, `ScriptedGate.admitAll(log)`/`holdAll`/`sequence`, `RecordingCommitSink(log)`, `RecordingEventListener`). Test names are enumerated in RESEARCH "Named tests to port" and the Validation table (e.g. `aRejectedMutatingCallNeverReachesTheGate`, `repeatedFailureOfTheSameToolAbortsButTheCommittedSiblingIsStillRecorded`, `aToolCallIdSeenInAnEarlierTurnRejectsTheWholeTurn`, `aTerminalTurnOnTheFinalIterationCompletesInsteadOfBudgetExceeded`; D-13 trio: terminal-only, terminal after a committing call, terminal alongside a held call). Provider neutrality: run the same script under `ProviderId.ANTHROPIC/OPENAI/OPENROUTER` using the generalized `pipelineOf`. Stop-leaf matrix: one test per leaf (HttpError asserting `details.httpStatus`, Network, MalformedResponse, MaxTokens, Refusal, PauseTurn, ContextWindowExceeded, UnknownStop, ToolFailure, plus "END_TURN + tool_calls" guard). Tool-count independence: run with 1, 2 and 25 tools.

---

### Fake-provider scripts

**Analog:** `FIX/FakeAiProvider.kt` (class lines ~25-75; `calls`, `callCount`, exhaustion throws `AssertionError("... script exhausted after N calls")`; companion builders `reply`, `toolCall(id, name, args, usage)`, `toolCalls(usage, vararg calls)`, `refusal`). Script a loop as a vararg of results: tool turn(s) then `reply("done", usage)`. Usage totals set cumulative `tokensUsed` (the engine records turns made via `model().complete`). Use `answerOf(StopReason.X, parts...)` when a specific stop reason is needed. Cross-turn id reuse and "END_TURN + calls" need `answerOf` (builders fix the stop reason).

---

### `providers/src/test/.../providers/AgenticLoopWireTest.kt`

**Analog:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/SingleShotWireTest.kt` (295 lines). Copy the import block (lines 1-50): `commandPipeline`, `ProviderSelection`, `ScriptedCredentialSource/SelectionSource`, `AnthropicProvider` + `successBody/textBlock/toolUseBlock`, `ChatCompletionsProvider` + `chatBody/chatMessage/chatToolCall`, MockWebServer (legacy `okhttp3.mockwebserver` only; never `mockwebserver3`, never subclass `QueueDispatcher`), `kotlinx.serialization.json.*`. Run on all three OkHttp legs (`:providers:test :providers:testOkhttp521 :providers:testOkhttp550`). Assertions: per-turn request bodies (Auto choice, no forced tool, tools+system identical every turn, history append-only), tool results encoded per dialect, held bytes `{"applied":false,"status":"held_for_confirmation"}` on the wire, SB-shaped user-turn bytes on Anthropic and Chat. Only `okhttp3` public 4.12 API (`body?.`).

---

### CLN-02 scanner extension

**Analog:** `gradle/invariants.gradle.kts`.

Current rule type and dispatch (lines 20-31, 153-164):
```kotlin
Rule("app planning id in comment", Regex("""\b(T-\d+-\d+|WR-\d+|Phase\s+\d+\s+D-\d+)\b""")),
...
fun scanText(label: String, text: String): List<String> {
    val (code, comments) = splitCodeAndComments(text)
    val out = mutableListOf<String>()
    for (rule in bannedRules) {
        val target = if (rule.id.startsWith("app planning id")) comments else code
        rule.regex.findAll(target).forEach { m ->
            val line = target.substring(0, m.range.first).count { it == '\n' } + 1
            out += "$label:$line [${rule.id}] '${m.value.trim()}'"
        }
    }
    return out
}
```
Task registration to extend (lines 166-179): `fileTree("src/main") { include("**/*.kt", "**/*.java") }`; add `src/testFixtures` for `:core` only if it exists. Add a `rawRules` list (same `Rule(id, Regex)` class) matched against the unmodified `text` because `splitCodeAndComments` blanks string literals (`log_food` would be invisible) and comments. Rule id prefix convention: ids are matched by `startsWith`, so name it `app-domain name ...` and branch `val target = ...` or run `rawRules` in a second loop with the same `label:line [id] 'match'` format. Deny-list per RESEARCH (names already tracked in planning docs only).

Negative control analog: `config/negative-controls/planning-ids.kt.txt`:
```
// EXPECT: app planning id in comment
package x
// T-01-02 leaked id
class C
```
New `config/negative-controls/app-domain.kt.txt` with `// EXPECT: app-domain name in source` (must equal the rule id) and an offending `internal const val X = "log_food"`; add a near-miss to `config/negative-controls/clean.kt.txt`. `verifyInvariantScannerControls` (invariants.gradle.kts:361) auto-picks up `*.kt.txt`.

Plant analog, `scripts/verify-negative-controls.sh` Part 1 loop (lines 53-65), add after the planning-id line:
```bash
expect_red "app-domain name ($m)"  $m 'internal const val X = "log_food"'  ":$m:scanBannedConstructs"
```

---

### Phase-gate evidence

**Analogs:** `.planning/phases/07-singleshot-strategy/evidence/phase-gate.txt` (numbered commands with exit codes: `./gradlew check --offline`, `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`; note about red XML left by command 4 so counts come from a clean re-run; TEST COUNTS per module/leg; REPO STATE `git ls-files -- '*api.txt'` empty and `git tag` empty; REQUIREMENT COVERAGE; CARRY-FORWARDS; `Gate-1: N/A`; final `PHASE GATE: PASS`), `evidence/singleshot-limits-tests.txt` (Obligation paragraph; "Source of results: ... JUnit XML, read by script, not typed by hand"; `PASSED  <method>` + `pins: <sentence>` per test; counts; Boundary-in-force paragraph) and `evidence/singleshot-surface-review.txt` (output of `scripts/review-api-surface.sh --out <path>`; path must not end in `api.txt`). Expected additions in the surface review: `ToolExecutor`, `AgenticLoopStrategy`, `AgenticLoopStrategy.Builder`, new `TraceCode` constants; sealed set unchanged at seven.

## Shared Patterns

### Write path (single route)
**Source:** `CORE/commit/CommitCoordinator.kt:49-69,95-134` via `session.submit(ToolStep)`
**Apply to:** `AgenticDispatch.kt`. All writes go through `session.submit`; READ results leave no ledger entry (D-05 automatic); `PREVIEW`/`ERROR` finished kinds are recorded `applied=false`; held bytes come from `DispatchResult.contentForModel`; strike on `DispatchResult.isError` (held is `isError=false`).

### Exception containment
**Source:** `CORE/internal/Guarded.kt:38-39,53-69` (the repo's only justified `TooGenericExceptionCaught` suppression)
**Apply to:** every call into app code (`tooling`, `userTurn`, `executor.prepare`). A fault becomes a fixed-notice `ToolStep.Finished(name, if (spec.mutating) FinishedKind.ERROR else FinishedKind.READ, StepResult(<fixed notice>, true))` plus a `TraceCode`; cancellation propagates. `TierWalk.kt:56-66` `executeGuarded` is the outer backstop.

### Outcome effects
**Source:** `CORE/pipeline/TierWalk.kt:77-85`, `CommandPipeline.kt:97-113`
**Apply to:** all strategies. Return only `Completed` or `Failed` from the loop; the pipeline attaches executed/commits/held and closes the run on every exit path.

### Redaction
**Source:** `Extraction.toString` (`OutcomeResolver.kt` ~45) and `RedactionCanaryTest.kt`
**Apply to:** every new public type's `toString` (names/counts only) and a canary run over an agentic run (transcript, system, args, results, reply never in outcome/trace/events/toString).

### Static gates for every core plan
`./gradlew :core:detekt :core:scanBannedConstructs --offline -q`, then `./gradlew :core:check --offline` per wave (includes `ApiShapeTest`, `NoHardCodedConstantsTest`). Many small files (detekt defaults: LongMethod, CyclomaticComplexMethod, ReturnCount, NestedBlockDepth are live). One plan per wave, at most 3 tasks, tracer-first; never `git add -A`; never stage `.planning/graphs/`, `.gsd/`, `.planning/intel/`, `.planning/config.json`, `.planning/state.json`, `.gsd-stage-*.done.json`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| Loop body itself (iteration, history append, per-tool-name strikes, whole-turn cross-turn id set) | strategy logic | multi-turn | SingleShot is one-shot; port semantics from SecondBrain `AnthropicAgentLoop.kt` (read-only, see RESEARCH "SB guard semantics") onto the neutral `Message`/`ModelRequest` types |
| Cross-turn duplicate tool-call id rejection | validation | transform | `ConversationCheck.kt:83-92` is within-turn only; loop adds a run-wide `HashSet<String>` (Open Question 2 risk with regenerating upstreams) |

## Metadata

**Analog search scope:** `core/src/{main,test,testFixtures}`, `providers/src/test`, `gradle/invariants.gradle.kts`, `config/negative-controls`, `scripts/`, Phase 7 evidence and plans.
**Files read for excerpts:** SingleShotStrategy.kt, SingleShotOutcomes.kt, SingleShotTestSupport.kt, SingleShotLimitsTest.kt (1-130), invariants.gradle.kts (20-204), ScriptedGate.kt, OutcomeResolver.kt, FakeAiProvider.kt, SingleShotWireTest.kt (1-50), planning-ids.kt.txt, verify-negative-controls.sh (50-80), Phase 7 evidence.
**Pattern extraction date:** 2026-10-01
