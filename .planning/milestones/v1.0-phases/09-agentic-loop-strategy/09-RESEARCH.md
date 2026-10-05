# Phase 9: Agentic Loop Strategy - Research

**Researched:** 2026-10-01
**Domain:** Kotlin multi-turn tool-use loop strategy in the pure-JVM `:core` module, over the Phase 2 write path and the Phase 3/8 neutral transcript and mappers
**Confidence:** HIGH for what exists in the code (all read this session); MEDIUM for the new-seam shape (needs the orchestrator sign-off, Open Question 1)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
(Copied from `09-CONTEXT.md` `## Implementation Decisions` and `## Runtime Decisions`. Trailing `_(source: ...)_` tags kept where they carry meaning.)

- **D-01 [strike-mid-turn]:** Verbatim SB: the strike counter is PER TOOL NAME (abort once the SAME tool has is_error'd twice in the run); the remaining calls in that turn still dispatch, the batched results are appended, then the run returns Failed(TOOL_FAILURE) carrying every executed action. Confirmed/corrected by secondbrain-2c. _(source: human)_
- **D-02 [user-turn]:** Same renderer hook as Phase 7; SB requires byte-for-byte reproduction of `Current local date-time: ${localDateTime} (${zoneId})\n\nVoice command: ${transcript}` (SB-confirmed). _(source: human)_ _(provisional — refresh at execution; depends on Phase 7)_
- **D-03 [mutating-flag]:** Mutating flag on ToolSpec (enables GATE-04 preview reporting and blocks a read tool that wrongly returns a Mutation from ever reaching the gate); unknown tool → is_error without calling the executor; combine with Phase 2's [finished-kind] _(source: ai-auto)_
- **D-04 [state-home]:** Coordinator-owned state; the loop never catches cancellation; a gate suspended at cancel leaves no record (SB contract) _(source: ai-auto)_
- **D-05 [executed-list]:** Mutating calls only; reads appear as tool names in the per-turn trace. Confirmed by secondbrain-2c (every SB consumer filters on `mutating`). _(source: human)_
- **D-06 [reply]:** First text block (also for a Done after tool rounds); prose-only run stays `Completed(reply)`; reply never in trace/events/toString. Confirmed by secondbrain-2c. _(source: human)_
- **D-07 [budget]:** Always `Failed(BudgetExceeded(MAX_ITERATIONS | TOKEN_CEILING))` carrying commits, held, errored and preview calls (SB retry-safety reads every mutating non-preview call). Confirmed by secondbrain-2c. _(source: human)_
- **D-08 [stop-leaves]:** Every SB reason stays distinguishable as a named leaf (HTTP error with HTTP status, network, malformed response, max-tokens, refusal, pause-turn, context-window-exceeded, unknown-stop, tool-failure); never collapse MAX_TOKENS or REFUSAL into unknown-stop. Confirmed by secondbrain-2c. _(source: human)_
- **D-09 [parallel]:** Allow parallel emission with sequential dispatch (Anthropic parity; the confirm mutex assumes sequential dispatch); revisit if strict-with-parallel research says otherwise _(source: ai-auto)_
- **D-10 [turn-validate]:** Reject the whole turn (SB invariant: no dispatch from a malformed turn) _(source: ai-auto)_
- **D-11 [dispatch-key]:** Neutral stop reason, with Phases 5/8 mappers deciding tool-turn-ness by tool_calls presence (consistent with Phase 5's [tool-turn]); a guard test for "stop + tool_calls" _(source: ai-auto)_
- **D-12 [ext-loop]:** Needs external research: strict enforcement with parallel tool calls; finish_reason "stop" with populated tool_calls on routed upstreams; reasoning context across turns on OpenAI vs OpenRouter reasoning_details; whether a stable OpenRouter session_id is needed for sticky routing/caching _(source: ai-auto)_ (settled below from Phase 8's live captures and the shipped code; see "D-12 resolution")
- **D-13 [a19-clarification]:** A19: in the agentic loop, a terminal-tool call ends the run after the turn's earlier calls dispatch in order (strike and gate rules unchanged); no tool_result is sent and no further turn starts; commits/held carried; the tier is terminal. Named tests for: terminal-only turn, terminal after a committing call, terminal alongside a held call. _(source: human — orchestrator, contract A19)_

Runtime Decisions (orchestrator mandates, 2026-10-01):
- **userTurn carry (Phase 7 seam sign-off):** `AgenticLoopStrategy` MUST accept the same `userTurn: UserTurnRenderer` seam that SingleShot ships (core/strategy/UserTurn.kt: `UserTurnRenderer`, engine-built `UserTurnContext(input, dateTime, carry)`, `UserTurnRenderer.standard()`). Phase 9's plan must state this explicitly. It must also reuse `ToolSpecProvider`/`ToolingSnapshot` (with singleShotTool = null) and keep system and tools as the invariant cached prefix.
- **Limits precondition (carried from Phase 11):** AgenticLoop must enforce AND test the 6 / 60000 / 4096 limits (maxIterations / token ceiling / per-turn tokens) from `session.policy`/`session.tokensUsed`, mirroring Phase 7's SingleShotLimitsTest. The v1.0.0 cut is blocked without them.
- **Phase 2 security O-1 (carried):** `CommitCoordinator.applyAll` has no per-item cancellation check. Assess it here.
- **user-turn:** add a test with an SB-shaped renderer asserting exact bytes on the wire for both Anthropic and Chat.
- **mutating-flag:** `ToolSpec.mutating` and `terminal` already exist; `ToolStep.Finished(toolName, kind: FinishedKind, result)` exists. A read tool returning a Mutation never reaches the gate (typed error). An unknown tool gives is_error without calling the executor. GATE-04 preview reporting reads `mutating`. Add no new ToolSpec member.
- **state-home:** coordinator-owned; the loop never catches CancellationException; a gate suspended at cancel leaves no record; a cancel during apply() records is_error with applied=true, then rethrows.
- **stop-leaves:** ALREADY SATISFIED by Phase 2: every leaf exists in core/failure/FailureReason.kt. Add a test per leaf.
- **dispatch-key:** the loop dispatches on the neutral reason plus the presence of tool calls. Add a guard test for "stop + tool_calls".
- **Phase 8 carry-forwards:** (1) the loop must use `ToolChoice.Auto`; (2) validate the whole turn so duplicate tool-call ids across turns are caught; (3) a ladder that escalates to a different model must drop native replays (thinking/reasoning_details) from the history it hands up.
- **StrategyOutcome.Completed.partial (post-Phase-7 fix 277a747):** reuse it. If the loop ends with work done but unfinished, report partial=true; do not add a parallel flag.

### Claude's Discretion
"Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder)."

### Deferred Ideas (OUT OF SCOPE)
"See REQUIREMENTS.md v2 / LATER items." (PlanThenExecute V11-02, LocalGrammar, `:undo`, conversation-tail caching, Responses dialect, on-device runtime: none are in this phase.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LOOP-01 | Consumer can run an `AgenticLoopStrategy` over its `ToolSpecProvider` + two-phase `ToolExecutor` (`prepare` → `Finished \| Mutation`) on any cloud provider, with mutating steps enforced through the gate by the engine. | `ToolExecutor` does NOT exist in code (Section "What is missing"); `ToolStep`, `CommitCoordinator`, `ToolSpecProvider` do. Pattern 1/2, Open Question 1. |
| LOOP-02 | The loop keeps SB's guards as named tests: whole-turn validation, token-ceiling check before dispatch, final-iteration guard, sequential dispatch, 2-strike tool-failure abort, unknown tool → `is_error`, bounds from `TierPolicy`. | SB guard order and semantics (Section "SB guard semantics"), limits mirror (Section "Limits"), named-test inventory in Validation Architecture. |
| LOOP-03 | Every exit path (done, budget, cancel, error) reports the executed actions and commits made so far; nothing committed is hidden behind a failure. | Largely provided by `TierWalk`/`CommandPipeline` effects snapshots; the strategy's job is to never swallow cancellation and never return a bare outcome that bypasses them. Exit-path test matrix in Validation Architecture. |
| CLN-02 | Library code contains no app-domain types or prompts (no `LogFood*`, `log_food`, SB `SYSTEM_PROMPT`, SB tool names, `MutationTier`) and hard-codes no tool count. | Section "CLN-02 scan": `scanBannedConstructs` blanks strings and comments, so it needs a raw-text rule class; recommended hosting and negative controls. |
</phase_requirements>

## Summary

The engine already owns almost everything the loop needs: the gate-then-apply write path (`CommitCoordinator`), the per-action sink delivery, run-close on every exit path, effects (executed, commits, held) on every `CommandOutcome` variant, token accounting (`session.tokensUsed`, which includes every turn made through `session.model().complete`), the limits (`TierPolicy` 6 / 60,000 / 4,096), the typed failure leaves, the neutral transcript with native replays, and the Anthropic/OpenAI/OpenRouter multi-turn mappers with live-captured goldens (Phase 8). Phase 7's `SingleShotStrategy` is the shape to mirror (builder, internal helpers split across small files, limits tests). The one structural gap is the seam the roadmap names `ToolExecutor`: it appears in REQUIREMENTS, ROADMAP and the contract, but there is no `ToolExecutor` type anywhere in `core/src/main` (grep: zero hits in any `.kt` file). It is a new public seam and, like Phase 7's `OutcomeResolver`, must go through an orchestrator sign-off before the strategy is built on it.

The loop itself is a port of SB's `AnthropicAgentLoop` onto the neutral model: build a history of neutral `Message`s, call `session.model().complete(...)` with `ToolChoice.Auto()`, decide on the stop reason first (failure leaves), then on tool-call presence, then apply SB's order for a tool turn: token ceiling, final-iteration guard, whole-turn validation, sequential dispatch with per-tool-name strikes. Everything committed is visible on every exit path automatically, because the pipeline builds `CommandOutcome.Failed(effects(), ...)` from the coordinator ledger (`TierWalk.kt:82-85`) and `closeRun` runs on cancel. The strategy must therefore (a) never catch `CancellationException` (use the internal `guarded` helper for app code, not a bare `catch (e: Exception)`), (b) never return an outcome that drops information the pipeline would have attached, and (c) test each exit path for the executed list and sink ordering.

Two findings need the planner's attention before tasks are written. First, Phase 8's carry (2) says duplicate tool-call ids across turns must be rejected by the loop's whole-turn validation, but Phase 8's own research records that some routed OpenAI-compatible upstreams regenerate `call_0, call_1` every turn, and SB's own unit test (`AnthropicAgentLoopTest.kt:803`) tolerates reuse. Rejecting is the locked direction; the risk and a fallback are in Open Question 2. Second, the CONTEXT bullet "if the loop ends with work done but unfinished (for example the iteration cap after a commit), report partial=true" contradicts D-07 and GATE-07 (`REQUIREMENTS.md:45`: "the agentic budget stop stays `Failed(BudgetExceeded)`"). The recommendation is: budget stays `Failed`, and `partial` is used only for calls dropped after a terminal call (Open Question 5).

**Primary recommendation:** Build `AgenticLoopStrategy` in `:core` (`core.strategy.agentic` package, mirroring `core.strategy.singleshot`), behind a new `fun interface ToolExecutor { suspend fun prepare(call: Extraction, input: CommandInput): ToolStep }` that reuses the existing `Extraction` type; gate the build on an orchestrator seam sign-off checkpoint (plan 2 of ~8); prove provider neutrality with a three-ProviderId fake-provider test in `:core` plus a three-dialect `AgenticLoopWireTest` in `:providers`; extend `scanBannedConstructs` with a raw-text rule class for CLN-02; fix O-1 additively with one `ensureActive()` per batch item and a test; run plans strictly one per wave.

## Planner Quick Index (answers to the 9 asks)

| Ask | Where |
|-----|-------|
| 1. Seams to reuse and what is missing | "Reusable seams (file:line)" and "What is missing" |
| 2. SingleShotLimitsTest mirror, gate evidence shape | "Limits: how SingleShot does it and the loop mapping", "Phase-gate evidence shape" |
| 3. SB guards as named tests | "SB guard semantics to port" and Validation Architecture test inventory |
| 4. O-1 | "O-1: CommitCoordinator.applyAll per-item cancellation" |
| 5. Phase 8 carries | "Phase 8 carries" |
| 6. CLN-02 | "CLN-02 scan" |
| 7. Validation Architecture | `## Validation Architecture` |
| 8. Gradle/worktree contention | "Gradle and worktree facts" |
| 9. Orchestrator questions | `## Open Questions` (marked ORCH) |

## Project Constraints (from CLAUDE.md)

Extracted from `.claude/CLAUDE.md` (project) and the global CLAUDE.md. Treat as locked.

- **Dependency structure:** `:core` depends on no other hub and has no HTTP dependency (L7, A7). The loop lives in `:core`; it must not import OkHttp, Android, DI. Enforced by `verifyCoreDependencyAllowlist` (`gradle/invariants.gradle.kts`, `if (project.name == "core")` block at line 298).
- **API evolution:** public API grows strictly additively once tagged; no `api.txt` and no tag exist yet (pre-cut); the Phase 7 gate still checks `git ls-files -- '*api.txt'` is empty.
- **Domain-free:** the library names no note/card/food; all app knowledge enters through §5.2 seams. This is CLN-02.
- **Quality:** detekt zero baseline (`maxIssues: 0`, no baseline); explicit API mode strict on all library modules; two-gate UAT where device-verifiable (Phase 9 is JVM-only: Gate-1 N/A, as in Phases 7 and 8).
- **Secrets:** API keys, transcripts, tool args and results never reach logs, telemetry, exceptions or `toString()`. Every new public type needs a redacted `toString` and a canary sweep.
- **Process:** contract changes only via §10 amendments through the control plane; never edit `CROSS-REPO-SCOPE-CONTRACT.md` here; never commit §11; always `git pull --rebase` before committing (peers commit contract changes).
- **User instruction (global):** all file changes go through a GSD workflow. This research is a read-only phase artifact; nothing is committed here.
- **Project-specific rules found in code (enforced mechanically):** no enums, no data-shaped classes, no public static fields (`ApiShapeTest`); no default-argument constructor stubs on new classes (`ApiShapeTest.kt:~128` `noClassOutsideTheDocumentedExceptionsDeclaresADefaultArgumentConstructorStub`, exceptions listed are `CommandInput` and `ToolSpec` only); sealed types limited to the seven in `scripts/review-api-surface.sh` (`ALLOWED_SEALED`), so the loop must add no sealed type.

## Architectural Responsibility Map

This is a library, not a tiered app. The "tiers" here are modules and seams.

| Capability | Primary owner | Secondary | Rationale |
|------------|---------------|-----------|-----------|
| Loop control (iterations, strikes, ceilings, validation, history) | `:core` `AgenticLoopStrategy` | — | Provider-neutral; must not know a dialect or import OkHttp. |
| Tool preparation (validate args, resolve, read) | App `ToolExecutor` | — | Domain knowledge. The engine passes arguments through untouched. |
| Gate decision and apply | `:core` `CommitCoordinator` (engine) | App `PreApplyGate`, `PendingMutation.apply` | The strategy has no other write path (`CommandStrategy` KDoc). |
| Per-action durability and run close | `CommitCoordinator` + `closeRun` | App `CommitSink` | Already done for every exit path (GATE-05). |
| Token accounting | `RoutedModel.complete` → `RunRecorder.tokensUsed` | Strategy reads `session.tokensUsed` | The strategy must not call `recordTurn` for turns made through `model().complete` (`CommandSession.kt:46-49` KDoc). |
| Wire shapes, replay, cache breakpoints | `:providers` mappers (Phase 4/5/8) | — | The loop only sees neutral `ModelRequest`/`ModelResponse`. |
| Provider-neutrality proof | `:core` fake-provider tests | `:providers` `AgenticLoopWireTest` (MockWebServer, 3 dialects) | The strategy cannot be tested against real mappers inside `:core` (no HTTP on its classpath). |
| CLN-02 enforcement | `gradle/invariants.gradle.kts` scanner | `config/negative-controls/`, `scripts/verify-negative-controls.sh` | Same place CLN-01/CLN-05 are enforced. |

## Standard Stack

No new libraries, no new Gradle plugins, no new dependencies. Everything is already on the `:core` classpath. [VERIFIED: core/build.gradle.kts dependencies block: `api(libs.coroutines.core)`, `api(libs.serialization.json)`, `testImplementation(libs.junit)`, `testImplementation(libs.coroutines.test)`, `testImplementation(testFixtures(project(":core")))`]

### Core
| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| kotlinx-coroutines-core | 1.11.0 | `suspend` seams, cancellation | already `api` in `:core` [CITED: .claude/CLAUDE.md stack table] |
| kotlinx-serialization-json | 1.11.0 | `JsonObject` tool arguments | already `api` in `:core` |
| JUnit 4 + coroutines-test | 4.13.2 / 1.11.0 | `runTest`, virtual time | repo standard [CITED: .claude/CLAUDE.md] |
| `java.time` | JDK 11 surface | clock for the user turn | `-Xjdk-release=11` is set (core/build.gradle.kts) |

### Alternatives Considered
| Instead of | Could use | Tradeoff |
|------------|-----------|----------|
| New `ToolInvocation` type for `prepare` | Reuse `Extraction(toolName, arguments)` | Reuse = zero new frozen types and a symmetric seam with `OutcomeResolver.resolve(Extraction, CommandInput)`. `Extraction` KDoc says later versions add grammar members (intent, slots), which is mild noise for an agentic call; acceptable. Recommended: reuse. Orchestrator decides (Open Question 1). |
| Gradle scanner rule for CLN-02 | A JUnit test in `:core` that walks `../providers/src/main` etc. | JUnit reuses the `NoHardCodedConstantsTest` positive-control idiom but couples `:core` tests to sibling module paths and only runs under `:core:test`. The Gradle scanner already runs per module under `check` and has negative controls. Recommended: Gradle scanner. |

**Installation:** none.

## Package Legitimacy Audit

No external packages are installed or added by this phase, so the package legitimacy gate does not apply. `gsd_run query package-legitimacy check` was not run because there is nothing to check.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Reusable seams (file:line)

All entries below were read this session. Quotes are verbatim.

### Strategy-side seams

| Seam | Evidence | Notes for the loop |
|------|----------|--------------------|
| `CommandStrategy` | `strategy/CommandStrategy.kt:12-26`: `public suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome`; `capabilities` defaults to `StrategyCapabilities.ANY_PROVIDER` | The loop implements this. |
| `CommandSession` | `strategy/CommandSession.kt:15-70`: `submit(step: ToolStep): DispatchResult` (35), `policy: TierPolicy` (26), `carry: Any?` (29), `tokensUsed: Long` (42), `recordTurn` (51), `internal abstract suspend fun recordCode(code: TraceCode)` (54), `model(): BoundModel` (65) | `recordCode` is `internal` (same-module only), which is why the loop must live in `:core`. |
| `ToolSpecProvider` / `ToolingSnapshot` | `strategy/ToolSpecProvider.kt:14-23` and `34-46`: `ToolingSnapshot(system: String, tools: List<ToolSpec>, singleShotTool: String?)`; `init` requires `singleShotTool == null \|\| this.tools.any { it.name == singleShotTool }` | Loop passes `singleShotTool = null` (KDoc: "A multi-turn tier leaves it null"). Call `tooling.tooling(input)` ONCE per command (KDoc: "A strategy calls it once per command"). |
| `ToolSpec` | `strategy/ToolSpec.kt:45-56`: `public class ToolSpec @JvmOverloads constructor(public val name: String, public val description: String, public val inputSchema: JsonObject, public val mutating: Boolean = false, public val terminal: Boolean = false, public val strict: Boolean? = null,)` with `require(!(terminal && mutating)) { "a terminal tool must be non-mutating" }` (55) | `mutating` and `terminal` are read NOWHERE in `core/src/main` today (grep `\.mutating` returns only `ActionLedger.kt:49` which is `ActionDetails.mutating`). The loop is their first consumer. Add no member (Runtime Decision). |
| `UserTurnRenderer` / `UserTurnContext` | `strategy/UserTurn.kt:15-27`, `44-47`: `UserTurnContext internal constructor(input, dateTime: ZonedDateTime, carry: Any?)`; default `standard()` renders `"Current local date-time: "` + ISO offset + `" (" + zone.id + ")\n\n"` + transcript (29-35) | The standard output has no `Voice command: ` prefix, so SB needs its own renderer (D-02). Loop builds `UserTurnContext(input, ZonedDateTime.now(clock), session.carry)` exactly like `SingleShotStrategy.kt:110`. |
| `ToolStep` | `commit/ToolStep.kt:4-45`: `Finished(toolName, kind: FinishedKind, result: StepResult, context: Any?)` + 3-arg ctor; `Mutation(mutations: List<PendingMutation>)` + single ctor | Already sealed and on the allowed-sealed list. `PendingMutation` (48-68) has `toolName`, `targetIds`, `context`, `suspend fun apply(): StepResult`. |
| `FinishedKind` | `commit/FinishedKind.kt:18,21,24`: `public val READ: FinishedKind = FinishedKind("read")`, `public val PREVIEW: FinishedKind = FinishedKind("preview")`, `public val ERROR: FinishedKind = FinishedKind("error")` | Open set; coordinator treats anything else as READ-like (`CommitCoordinator.kt:97-101`: `else -> null`). |
| `StepResult` / `DispatchResult` | `commit/ToolStep.kt:78-97`, `108-117`: `DispatchResult(contentForModel, isError, held, actions)` (internal ctor) | Loop builds the model-facing `ToolResult(callId, content, isError)` from `contentForModel`/`isError`. `held` is true only for the gate hold. |
| `StrategyOutcome` | `strategy/StrategyOutcome.kt:19-23`: `public class Completed(public val reply: String?, public val terminalCall: TerminalCall?, public val partial: Boolean,)`; `init` requires `reply == null \|\| terminalCall == null`; `Failed(reason, details)` (66-69) | `partial` already exists (post-fix 277a747). `Escalate`/`NoMatch` exist but the loop should not use them (Phase 8 carries). |
| `TerminalCall` | `strategy/TerminalCall.kt:14-17`: `TerminalCall(toolName: String, arguments: JsonObject)` | Build from the terminal `AssistantPart.ToolCall`. |
| `FailureReason` leaves | `failure/FailureReason.kt`: `Network` (57), `MalformedResponse` (65), `Refusal` (81), `MaxTokens` (89), `ToolFailure` (105), `PauseTurn` (129), `ContextWindowExceeded` (137), `UnknownStop` (145), `HttpError` (153), `BudgetExceeded(bound: BudgetBound)` (177) | All nine D-08 leaves exist. HTTP status rides in `FailureDetails.httpStatus` (`failure/FailureDetails.kt`). |
| `BudgetBound` | `failure/BudgetBound.kt:15,18`: `public val ITERATIONS: BudgetBound = BudgetBound("iterations")`, `public val TOKENS: BudgetBound = BudgetBound("tokens")` | NOTE: D-07 says `MAX_ITERATIONS \| TOKEN_CEILING` (SB's names). The engine names are `BudgetBound.ITERATIONS` and `BudgetBound.TOKENS`. Plans must use the engine names. |
| `StopReason` | `transcript/ModelResponse.kt:57-75`: `END_TURN`, `TOOL_USE`, `MAX_TOKENS`, `REFUSAL`, `PAUSE_TURN`, `CONTEXT_WINDOW_EXCEEDED`, `OTHER` (value class, open) | Keep an `else` branch. |
| `ToolChoice` | `transcript/ModelRequest.kt:78-80`: `public abstract class ToolChoice internal constructor()` with `public class Auto : ToolChoice()` | Loop sends `ToolChoice.Auto()`. |
| `ModelRequest` | `transcript/ModelRequest.kt:24-31` 7-arg ctor `(system, messages, tools, toolChoice, maxTokens, cache, singleToolCall)`; `init` requires non-empty messages, distinct tool names | SingleShot passes `attempt.session.policy.maxTokensPerTurn, CacheDirective(true), true,` (`SingleShotStrategy.kt:116-118`). The loop must pass `false` for `singleToolCall` (parallel emission allowed, D-09). |
| `Message` types | `transcript/Message.kt`: `UserMessage(text)`, `AssistantMessage(parts, nativeReplay)` (38), `ToolResultsMessage(results)` (74, requires non-empty and distinct call ids), `ToolResult(callId, content, isError)` (97) | A `ToolResultsMessage` with a repeated id throws `IllegalArgumentException`, so the loop must validate ids BEFORE building it. |
| `TierPolicy` | `pipeline/TierPolicy.kt:6-9`: `private const val DEFAULT_MAX_ITERATIONS = 6`, `DEFAULT_TOKEN_CEILING = 60_000L`, `DEFAULT_MAX_TOKENS_PER_TURN = 4_096`, `MIN_ITERATIONS = 2`; KDoc 15-18 says the three limits are "advisory: the engine counts tokens ... but never stops a tier for exceeding them, so a strategy that ignores `session.policy` is unbounded" | The loop is the enforcer. `commandTimeoutMillis` is the engine-enforced backstop. |
| Fake provider harness | `core/src/testFixtures/.../FakeAiProvider.kt`: `FakeAiProvider(id: ProviderId, vararg results)`, `calls: List<ProviderRequest>` (51), `callCount` (55), builders `reply`, `toolCall`, `refusal`, `toolCalls(usage, vararg calls)`; script exhaustion throws `AssertionError` | Sufficient. Missing: scripted `ToolExecutor` (new, in testFixtures). Test helpers in `core/src/test/.../SingleShotTestSupport.kt`: `pipelineOf(tiers, fake, gate, sink, listener, policy, credentials)` hard-codes `ProviderSelection(ProviderId.ANTHROPIC, "test-model")` and `testKey()` for Anthropic, and `answerOf(stop, vararg parts)` — generalize `pipelineOf` with a `providerId` parameter for the neutrality test. |

### Write-path seams

| Seam | Evidence | Behavior the loop relies on |
|------|----------|-----------------------------|
| `CommitCoordinator.submit` | `commit/CommitCoordinator.kt:49-55`: mutex-serialized; `admitCaller()` (`currentCoroutineContext().ensureActive()` at 68, `check(!closed)` at 69) before every submit | A cancelled caller or a closed run cannot write. A leaked coroutine cannot write after close. |
| Finished handling | `CommitCoordinator.kt:95-108`: `PREVIEW` → `ActionKind.PREVIEW`, `ERROR` → `ActionKind.IS_ERROR`, anything else → `null` kind → `DispatchResult(result.contentForModel, result.isError, false, emptyList())` with NO ledger entry and NO sink call | READ results are never in the executed list (D-05 holds automatically). Previews and rejections are recorded with `applied = false` and `mutating = false` (`ActionDetails(..., false)` line 103), and delivered to the sink. |
| Gate and hold | `CommitCoordinator.kt:110-134`: gate fails closed (`GateStep.kt:19-22`: a throwing gate becomes `GateDecision.Hold()` plus `gate_error`); `hold` returns `DispatchResult(heldForConfirmationContent(), false, true, actions)`; the bytes are `private const val HELD_FOR_CONFIRMATION = """{"applied":false,"status":"held_for_confirmation"}"""` (line 10) | The loop just forwards `contentForModel`. The held notice is byte-compatible with SB without any loop code. |
| Apply and errors | `commit/ApplyStep.kt:81-92`: throw from apply → `is_error` action with `applied = true`, content `{"status":"error"}` (line 12), never the exception text; cancellation → `journalCancelled` under `NonCancellable`, then rethrow (85-88) | Loop needs no extra handling for apply faults. |
| Sink delivery | `ApplyStep.kt:48-62` `ActionDelivery.deliver` runs under `NonCancellable` | "CommitSink has already been told about each commit" (SC3) is a property of the coordinator. |
| Escalation guard | `pipeline/TierWalk.kt:86-89` and `111`: `Escalate`/`NoMatch` after `coordinator.appliedCount + coordinator.heldCount > 0` becomes `Completed(partial = true)` with `escalation_suppressed` | Only relevant if the loop escalates (recommendation: it never does). |
| Outcome effects | `TierWalk.kt:77-85`: `is StrategyOutcome.Failed -> { ... CommandOutcome.Failed(effects(), outcome.reason, outcome.details)`; `Completed` → `CommandOutcome.Completed(effects(), outcome.reply, outcome.terminalCall, partial = outcome.partial)` (80) | Every `Failed` the loop returns automatically carries executed, commits, held. |
| Strategy throw | `TierWalk.kt:56-66` `executeGuarded` wraps `strategy.execute` in `guarded`, recording `strategy_error` and returning `Failed(Unexpected(<class>))` | A throwing `tooling`/`userTurn`/`executor` is already contained; real cancellation propagates (`internal/Guarded.kt:53-69`). |
| Run close | `pipeline/CommandPipeline.kt:97-113` (`drive`): `finally { coordinator.close(); ... closeRun(sink, runId, recorder, terminationOf(outcome, cancelled, effects)) }`; `terminationOf` (227-234) maps `Completed → Done`, `Failed → Failed`, cancel → `Cancelled` | GATE-05 once-per-exit is engine-owned. |

### What is missing for `AgenticLoopStrategy` (the gap list)

1. **`ToolExecutor` seam** (public, new). No type exists. [VERIFIED: `grep -rn "ToolExecutor" --include=*.kt .` returned no hit under `core/src`, `providers/src`, `keystore/src`; hits are only in `.md` files]. Recommended shape: `public fun interface ToolExecutor { public suspend fun prepare(call: Extraction, input: CommandInput): ToolStep }`.
2. **`AgenticLoopStrategy` + `Builder`** (public), package `io.github.ygaray.voiceactionengine.core.strategy.agentic`, internal constructor, `Companion.invoke(id, block)` like SingleShot (`SingleShotStrategy.kt:225-233`).
3. **Shared internal helpers currently private/misplaced**: `ceilingReached`/`ceilingCrossed`/`tokenBudgetFailure` live in `strategy/singleshot/SingleShotOutcomes.kt:70-78` (internal, so reachable, but in the wrong package); `private object CurrentZoneClock` is private to `SingleShotStrategy.kt:41-47`. Move both to a neutral internal file under `core.strategy` in the first task (API-neutral).
4. **New `TraceCode` constants** (public additive; ctor is internal, `recordCode` is internal): e.g. unknown tool, tool prepare fault, read tool returned a Mutation. Names are ASSUMED until sign-off.
5. **`ScriptedToolExecutor`** in `core/src/testFixtures/.../testing/` (not published; `verifyNoTestFixturesPublished` guards that).
6. **`pipelineOf` generalization** (providerId parameter) in `SingleShotTestSupport.kt`.
7. **`:providers` wire test** `AgenticLoopWireTest` (new) over MockWebServer for the three dialects.
8. **CLN-02 scanner rule class** (Gradle) + negative control + plants.
9. **O-1 hardening** in `CommitCoordinator.applyAll` (internal, one line) + tests.

## Limits: how SingleShot does it and the loop mapping

### SingleShot (analog), verified

- Pre-call: `ceilingReached(session)` → `if (session.tokensUsed >= session.policy.tokenCeiling) tokenBudgetFailure() else null` (`SingleShotOutcomes.kt:70-71`), called first in `execute` (`SingleShotStrategy.kt:87`).
- Post-call: `ceilingCrossed(session)` → `if (session.tokensUsed > session.policy.tokenCeiling) tokenBudgetFailure() else null` (`SingleShotOutcomes.kt:74-75`), before resolve/any write (`SingleShotStrategy.kt:135`).
- Failure value: `StrategyOutcome.Failed(FailureReason.BudgetExceeded(BudgetBound.TOKENS))` (`SingleShotOutcomes.kt:77-78`).
- Per-turn tokens: `attempt.session.policy.maxTokensPerTurn` passed as `ModelRequest.maxTokens` (`SingleShotStrategy.kt:116`).
- Iterations: SingleShot never reaches the limit (exactly one call).
- The ten `SingleShotLimitsTest` methods and what each pins (`core/src/test/.../SingleShotLimitsTest.kt`, verified by reading; summary in `evidence/singleshot-limits-tests.txt`): `atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall`, `oneTokenBelowTheDefaultCeilingTheCallIsMade`, `aCustomCeilingIsReadFromTheSessionPolicy`, `defaultsAreTheContractLimits`, `defaultPolicySendsTheDefaultPerTurnTokenLimit`, `customPerTurnLimitIsSentUnchanged`, `aResponseThatCrossesTheCeilingFailsBeforeTheResolverOrAnyWrite`, `aResponseLandingExactlyOnTheCeilingIsStillResolved`, `exactlyOneProviderCallAtMinimumIterations`, `exactlyOneProviderCallAtDefaultIterations`. The idiom: a `spendingTier(tokens)` that calls `session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, tokens), 1L))` then `Escalate`s, put in front of the tier under test to pre-spend `tokensUsed`; a `FakeAiProvider` script whose `Usage` totals set the cumulative spend; `NoNetworkGuard.during { ... }`.

### Loop mapping (recommended)

| Limit | Source | Loop behavior | Test mirror |
|-------|--------|---------------|-------------|
| `maxIterations` (6) | `session.policy.maxIterations` | Count one iteration per `model.complete` call. On a tool-use turn at `iteration == maxIterations`, return `Failed(BudgetExceeded(BudgetBound.ITERATIONS))` before any dispatch (SB final-iteration guard, `AnthropicAgentLoop.kt:195-197`). | `finalIterationGuard...`: script 6 tool-use answers; assert 6 provider calls, 5 applies, `BudgetExceeded(ITERATIONS)`, every executed id answered in a later request. Pin both `maxIterations = 2` (minimum, `TierPolicy.kt:9`) and the default. |
| `tokenCeiling` (60,000) | `session.tokensUsed` vs `session.policy.tokenCeiling` | Pre-call refuse at or over the ceiling (reuse `ceilingReached`); post-response `>` before dispatching a tool turn (reuse `ceilingCrossed`). End-of-turn answers are NOT failed for crossing the ceiling (SB parity, `AnthropicAgentLoop.kt:174` returns Done without the ceiling check). | Port the ten SingleShot boundary tests: 59,999 calls / 60,000 refuses (pre-call), exactly-at-ceiling response still dispatched, ceiling+1 response fails before the executor/gate/sink, custom ceiling read from the policy, an end-of-turn response over the ceiling is still `Completed`. |
| `maxTokensPerTurn` (4,096) | `session.policy.maxTokensPerTurn` | Pass as `ModelRequest.maxTokens` on EVERY request. | `defaultPolicySendsTheDefaultPerTurnTokenLimit`, `customPerTurnLimitIsSentUnchanged`, plus "every request of a 3-turn run carries it". |
| Defaults | `TierPolicy.DEFAULT` | `defaultsAreTheContractLimits` already exists in SingleShotLimitsTest; copy it into the loop's class so the loop's evidence file is self-contained. | same |

**Boundary decision (internal, low risk):** SB checks only `cumulative > ceiling` after a tool-use response. Mirroring SingleShot adds a pre-call `>=` on every iteration, which differs from SB only at exact equality (a further call certainly crosses the ceiling, since `ceilingReached`'s own comment says "a call costs at least a token"). Recommended: pre-call `>=` before EVERY call, plus post-response `>` before dispatch. Flagged for confirmation in Open Question 10.

**NoHardCodedConstantsTest interplay** (`core/src/test/.../NoHardCodedConstantsTest.kt`): `limitConstantsAreDeclaredOnlyByTheirOwners` flags any `const val` whose name matches `^(DEFAULT_|MIN_|MAX_)|TOKEN|ITERATION|CEILING` outside `TierPolicy.kt, ModelCapabilities.kt, AwaitingConfirmGate.kt, ToolSpec.kt`; `tierPolicyDefaultValuesAppearOnlyInTierPolicy` flags the literals 60000 and 4096; the model-id matcher flags words such as claude, sonnet, opus, haiku, gpt, gemini in ANY main line including KDoc. So loop main code must name the strike constant without those tokens (for example `STRIKES_TO_ABORT = 2`, not `MAX_FAILURES`) and must not mention model families in comments.

## SB guard semantics to port as named tests

Source: `~/Projects/AndroidApps/Personal/SecondBrain/app/src/main/java/com/example/secondbrain/core/agent/AnthropicAgentLoop.kt` and `AnthropicToolRegistry.kt` (read-only, read this session).

### Order inside a tool-use turn (SB `resolveToolUseTurn`, lines 184-206)

1. Add this turn's tokens; `cumulative > MAX_UTTERANCE_TOKENS` → `BudgetExceeded(TOKEN_CEILING)` (192-194).
2. `iteration == MAX_ITERATIONS` → `BudgetExceeded(MAX_ITERATIONS)` (195-197).
3. `!isValidToolUseTurn(...)` → `MALFORMED_RESPONSE` (198-200). SB validates: at least one tool_use block, every block has non-blank id and name, `input` absent/JsonNull/object, ids unique within the turn (213-225).
4. Dispatch (228-264), then if `toolFailureExceeded` → `Unavailable(TOOL_FAILURE)` (201-204).

Engine mapping: a tool turn is "response has `message.toolCalls.isNotEmpty()` and the stop reason is not one of the four failure stops". Engine-level malformed rows that SB checked in the wire layer are already typed: blank id/name throw in `AssistantPart.ToolCall.init` (`AssistantPart.kt`: `require(id.isNotBlank())`, `require(name.isNotBlank())`) and are `MalformedResponse` at decode (`ChatResponseParts.kt:47-49`, `AnthropicDecoder.kt:86-88` per 08-RESEARCH); non-object input is `MalformedToolArgs` at decode. What the loop must still validate: duplicate ids inside the turn, duplicate ids seen in an EARLIER turn (carry 2), and "stop reason `tool_use` with no calls" (SB: text-only row → MALFORMED).

### Named tests to port (SB test → engine test name)

SB test file: `.../app/src/test/java/com/example/secondbrain/core/agent/AnthropicAgentLoopTest.kt` (line numbers from `grep -n "fun \`"`).

| SB test (line) | Engine named test (proposed) | Proves |
|---|---|---|
| happy path dispatches through the gate and returns Done (135) | `aToolTurnThenProseCommitsThroughTheGateAndCompletes` | LOOP-01, D-06 reply = first text block of the final turn |
| 500 → HTTP_ERROR (249), dropped connection → NETWORK (263) | stop-leaf matrix rows `HttpError` (assert `details.httpStatus`) and `Network` | D-08 |
| missing title is_errors without consulting the gate (274) | `aRejectedMutatingCallNeverReachesTheGate` (executor returns `Finished(name, FinishedKind.ERROR, StepResult(.., true))`; `gate.calls == 0`; sink got one `is_error` action with `applied == false`) | LOOP-01, GATE-04 |
| multi-tool turn batches two tool_results into one message in order (322) | `aMultiToolTurnSendsOneResultsMessageInCallOrder` | sequential dispatch, D-09 |
| MAX_ITERATIONS final-iteration guard (367) | `theFinalIterationGuardNeverDispatchesTheLastPermittedTurn` | LOOP-02 |
| token ceiling stops before dispatch (406) | `theTokenCeilingStopsTheLoopBeforeDispatchingATool` | LOOP-02 |
| stop-reason matrix (434-515) | `everyStopReasonAndFailureLeafResolvesToItsOwnOutcome` (rows below) | D-08, D-11 |
| malformed tool_use turn matrix (520-598) | `aMalformedToolTurnIsRejectedWholeBeforeAnyDispatch` (rows below) | D-10 |
| repeated failure aborts, committed sibling still recorded (620) | `repeatedFailureOfTheSameToolAbortsButTheCommittedSiblingIsStillRecorded` | D-01 |
| thinking blocks echo back verbatim (655) | `theAssistantTurnIsAppendedWithItsNativeReplayUntouched` (same instance; next request holds it) | Phase 8 carry |
| undeclared argument key (685) | n/a in the engine: argument validation is the executor's job (the engine passes arguments untouched, `OutcomeResolver.kt` KDoc) | — |
| held call round-trips with an honest outcome (721) | `aHeldCallFeedsTheModelTheFixedNoticeAndTheLoopContinues` | GATE-03, SC1 |
| telemetry carries only non-sensitive counters (749) | extend `RedactionCanaryTest` with an agentic run | TEL-04 |
| cancellation propagates (782) | `aCancelDuringTheProviderCallPropagatesAndTheRunClosesCancelled` (+ gate, + apply variants) | D-04, LOOP-03 |
| reused tool_use id across iterations keeps each snapshot (803) | CONFLICT with carry (2): SB tolerates reuse. See Open Question 2. Replacement test: `aToolCallIdSeenInAnEarlierTurnRejectsTheWholeTurn` | D-10 |

Stop-reason matrix rows (neutral): `END_TURN` text → `Completed(reply)`; `MAX_TOKENS` → `Failed(MaxTokens)`; `REFUSAL` → `Failed(Refusal)`; `PAUSE_TURN` → `Failed(PauseTurn)`; `CONTEXT_WINDOW_EXCEEDED` → `Failed(ContextWindowExceeded)`; `OTHER` with no calls → `Failed(UnknownStop)`; `TOOL_USE` with no calls → `Failed(MalformedResponse)`; `ModelResult.Failure(HttpError, FailureDetails(429,...))` → `Failed(HttpError)` carrying details; `ModelResult.Failure(Network)`; `ModelResult.Failure(MalformedResponse)`. The first four failure stops are decided BEFORE looking at tool calls (SingleShot does the same: `SingleShotOutcomes.kt:40-47`), so a truncated answer can never dispatch.

Malformed-turn rows: duplicate id within the turn; duplicate id with an earlier turn; `TOOL_USE` stop with no calls. Each asserts: `MalformedResponse`, zero executor calls, zero gate calls, zero sink actions, and that a CHEAPER earlier committed call is still reported (nothing hidden).

### Details of SB behavior that decide design

- **Strike counting** (`AnthropicAgentLoop.kt:254-256`, `267-271`): `if (dispatchResult.isError && registerFailureAndCheckExceeded(state, toolName))`; `failures >= 2`. Counts any `isError`, per tool NAME, across the whole run, including unknown-tool names. The rest of the turn still dispatches; the loop returns `TOOL_FAILURE` after the turn. Engine: strike on `DispatchResult.isError` (held is not an error: `hold` returns `isError = false`).
- **Unknown tool** (`AnthropicToolRegistry.kt:196-203`): `isError = true`, content `"unknown tool: $name"`, `mutating = false`, no audit record, gate never consulted. Engine: no executor call, no `session.submit` needed (nothing to record), still a strike.
- **Read tool returns a mutation** (`AnthropicToolRegistry.kt:229-230`): `check(mutating) { "tool '${spec.name}' returned ToolStep.Mutate but is not a mutating tool" }` which is caught as a generic `Exception` and becomes `"internal error"` `is_error`. Engine equivalent (D-03): drop the `Mutation` without gating, tool_result is_error with fixed content, strike, trace code.
- **Prepare/validation throws** (`AnthropicToolRegistry.kt:~246-258`): collapsed to `is_error` content (SB echoes the message or "internal error"). Engine: fixed notice, never the exception text (secrets rule), strike, trace code; cancellation must still propagate (use the internal `guarded`).
- **Executed list in SB** includes reads (`ExecutedToolCall` for every dispatch, `mutating` = spec classification). The engine's ledger does NOT record reads (`CommitCoordinator.kt:97-102`), per D-05. Side note for the SB confirmation: a rejected call to a MUTATING tool is `mutating = true` in SB but `mutating = false` in the engine ledger (`finished()` passes `false` at line 103); this was fixed in Phase 2 and documented on `ExecutedAction.mutating` ("false for an action that only previews or rejects a change"); worth one line in the seam sign-off message.
- **Reply** (`AnthropicAgentLoop.kt:287-292`): first `text` block of the `end_turn` content, else null. Engine: `response.message.parts.filterIsInstance<AssistantPart.Text>().firstOrNull()?.text`.
- **Assistant turn echoed verbatim, then one batched user message of results** (234-262). Engine: append `response.message` (keeps `nativeReplay` by reference) then one `ToolResultsMessage` in call order.
- **SB's `error("unreachable")` after the while loop** (`AnthropicAgentLoop.kt:129-143`, WR-02): the lesson is "make exhaustion structurally impossible rather than return a plausible BudgetExceeded". Structure the Kotlin loop so the last iteration always returns a terminal outcome (for example iterate `1..maxIterations` and let the tool-turn branch at `iteration == maxIterations` return the budget failure; the post-loop statement should not exist or should be a loud `Failed(Other(...))` that a test can reach by policy construction only if it truly cannot happen).

## Architecture Patterns

### System Architecture Diagram

```
 spoken command
      |
      v
 CommandPipeline.execute(input) ----> TierWalk (ladder; policy; effects snapshots)
                                          |
                                          v   (RunSession: policy, tokensUsed, submit, model())
                              AgenticLoopStrategy.execute(input, session)
                                          |
        +---------------------------------+------------------------------------+
        | once per command                                                      |
        v                                                                       |
  ToolSpecProvider.tooling(input) --> ToolingSnapshot(system, tools, null)      |
  UserTurnRenderer.render(UserTurnContext(input, now(clock), carry))            |
        |  history = [UserMessage(rendered)]  (append-only from here)           |
        v                                                                       |
  +---- iteration 1..policy.maxIterations ------------------------------------+ |
  | pre-call ceilingReached?  -> Failed(BudgetExceeded(TOKENS))               | |
  | BoundModel.complete(ModelRequest(system, history, tools, Auto,            | |
  |      policy.maxTokensPerTurn, CacheDirective(true), singleToolCall=false))| |
  |   (RoutedModel records the turn -> session.tokensUsed)                    | |
  | ModelResult.Failure     -> Failed(reason, details)                        | |
  | stop MAX_TOKENS/REFUSAL/PAUSE_TURN/CONTEXT_WINDOW -> Failed(<leaf>)       | |
  | no calls: END_TURN -> Completed(first text) | TOOL_USE -> Malformed |     | |
  |           other -> UnknownStop                                            | |
  | tool turn:                                                                | |
  |   1 ceilingCrossed?      -> Failed(BudgetExceeded(TOKENS))                | |
  |   2 final iteration?     -> Failed(BudgetExceeded(ITERATIONS))            | |
  |   3 whole-turn valid?    -> else Failed(MalformedResponse)                | |
  |   4 history += response.message (nativeReplay by reference)               | |
  |   5 for each call, in order (sequential):                                 | |
  |        terminal tool  -> stop dispatching; Completed(terminalCall)        | |
  |        unknown tool   -> is_error result, strike (no executor, no submit) | |
  |        executor.prepare(Extraction, input)  [guarded; cancel propagates]  | |
  |          Finished -> session.submit -> DispatchResult                     | |
  |          Mutation on a non-mutating tool -> dropped, is_error, strike     | |
  |          Mutation -> session.submit -> gate -> apply -> sink              | |
  |                      (held => fixed notice bytes, isError=false)          | |
  |        isError => strike per tool name (2nd => abort flag)                | |
  |   6 abort flag? -> Failed(ToolFailure)                                    | |
  |   7 history += ToolResultsMessage(results in call order)                  | |
  +---------------------------------------------------------------------------+ |
                                          |                                     |
                                          v                                     |
                    StrategyOutcome (never Escalate/NoMatch)  <-----------------+
                                          |
                                          v
        TierWalk -> CommandOutcome(effects = coordinator ledger + held + trace)
        CommandPipeline.drive finally -> closeRun -> CommitSink.onRunClosed (once)
```

### Recommended project structure

```
core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/
├── strategy/
│   ├── ToolExecutor.kt                 # NEW public seam (fun interface), KDoc with the redaction rules
│   ├── StrategyLimits.kt               # MOVED internal: ceilingReached / ceilingCrossed / tokenBudgetFailure, CurrentZoneClock
│   ├── singleshot/SingleShotOutcomes.kt# keeps decideResult / resolutionOutcome; imports the moved helpers
│   └── agentic/
│       ├── AgenticLoopStrategy.kt      # class + Builder + Companion.invoke (small, delegating)
│       ├── AgenticTurn.kt              # internal: classify a ModelResult (failure leaves, stop table, tool-turn test)
│       ├── AgenticValidation.kt        # internal: whole-turn validation (ids within turn + across turns)
│       └── AgenticDispatch.kt          # internal: per-call dispatch, strikes, terminal handling
core/src/testFixtures/.../testing/ScriptedToolExecutor.kt   # NEW
core/src/test/.../AgenticLoop*Test.kt, AgenticLoopTestSupport.kt
providers/src/test/.../providers/AgenticLoopWireTest.kt     # NEW
gradle/invariants.gradle.kts, config/negative-controls/app-domain.kt.txt   # CLN-02
```

Why many small files: detekt runs on defaults (`buildUponDefaultConfig = true`, `maxIssues: 0`) with only `TooManyFunctions` (class threshold raised to 12) and `LongParameterList` customized (`config/detekt/detekt.yml`). The default rules most likely to bite a loop are `LongMethod`, `CyclomaticComplexMethod`, `ReturnCount`, `NestedBlockDepth`, `LoopWithTooManyJumpStatements`, `TooGenericExceptionCaught`, `SwallowedException` [ASSUMED: default thresholds from detekt 1.23.8 training knowledge; confirm with `./gradlew :core:detekt --offline` early]. Phase 7 handled this with an `Attempt` carrier class and a separate outcomes file; do the same (`LoopState` carrier, `Turn` carrier).

### Pattern 1: Seam shape (new) — `ToolExecutor`

**What:** the app's two-phase tool seam. The app validates and resolves in `prepare` and returns either a finished result or an unapplied mutation; the engine, not the app, gates and applies.

**Proposed (ASSUMED until signed off):**
```kotlin
// Source: mirrors core/strategy/OutcomeResolver.kt:17-20 (fun interface over Extraction + CommandInput)
public fun interface ToolExecutor {
    /** Prepares the call described by [call] for the command [input]; never writes. */
    public suspend fun prepare(call: Extraction, input: CommandInput): ToolStep
}
```
Rules for the KDoc (copy the `OutcomeResolver` wording): the executor validates the arguments itself; it must never write (every write is a `ToolStep.Mutation` the engine gates); a read tool must return `Finished`; a mutating tool may return `Finished` with `FinishedKind.PREVIEW` or `FinishedKind.ERROR`; returning `Mutation` from a tool whose `ToolSpec.mutating` is false is an engine-rejected error; throwing becomes an is_error result and a strike.

### Pattern 2: Strategy skeleton (names new, types quoted above)

```kotlin
// Source: structure of core/strategy/singleshot/SingleShotStrategy.kt:70-120, 179-233
public class AgenticLoopStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    override val capabilities: StrategyCapabilities = settings.capabilities
    // requireNotNull(settings.tooling) / requireNotNull(settings.executor), same messages style as SingleShot
    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        ceilingReached(session) ?: withTooling(input, session)
    // tooling once -> model() (refusal => Failed) -> run iterations
    public class Builder internal constructor() {
        public var tooling: ToolSpecProvider? = null
        public var executor: ToolExecutor? = null
        public var capabilities: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER
        public var userTurn: UserTurnRenderer = UserTurnRenderer.standard()
        public var clock: Clock = CurrentZoneClock   // after it moves out of SingleShotStrategy.kt
    }
    public companion object {
        public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): AgenticLoopStrategy =
            AgenticLoopStrategy(id, Builder().apply(block))
    }
}
```
No `maxIterations`, no `heldContent`, no `forceTool`, no `onNoToolCall`/`onRefusal` hooks in the builder: limits come from `session.policy` ("bounds taken from TierPolicy"), the held bytes are fixed by the coordinator (GATE-03), a `Required` tool choice is wrong for a loop (Phase 8 carry 1), and SB has no hooks. Builder members are additive later; a removed one is not.

### Pattern 3: Dispatch with `guarded` (never a bare catch)

```kotlin
// Source: internal/Guarded.kt:38-39 (guarded) and :53-69 (cancellation rethrown when the caller is cancelled)
val step: ToolStep? = guarded(onFault = { session.recordCode(<tool prepare fault code>); null }) {
    executor.prepare(Extraction(call.name, call.arguments), input)
}
```
`guarded` is `internal suspend inline` in package `...core.internal`; it is the repository's only place with `@file:Suppress("TooGenericExceptionCaught")`, and detekt's `TooGenericExceptionCaught`/`SwallowedException` are active, so a hand-written `catch (e: Exception)` in the loop will fail `detekt`. A `null` step (fault) maps to `ToolStep.Finished(name, if (spec.mutating) FinishedKind.ERROR else FinishedKind.READ, StepResult(<fixed notice>, true))` submitted through `session.submit`, so a failed mutating attempt is in the executed list and the sink (GATE-04) while a failed read is not.

### Pattern 4: Terminal tool (A19 / D-13)

Dispatch calls in order. At the first call whose `ToolSpec.terminal` is true: do not call the executor, build `TerminalCall(call.name, call.arguments)`, stop dispatching, send no `ToolResultsMessage`, make no further request, return `StrategyOutcome.Completed(null, terminalCall)` (a terminal outcome has `reply == null` by `Completed.init`). Earlier commits and holds are carried by the pipeline. The same rule SingleShot already implements: `SingleShotStrategy.kt:147` `tool.terminal -> StrategyOutcome.Completed(null, TerminalCall(call.name, call.arguments))`.

### Anti-patterns to avoid

- **Dispatching concurrently.** The confirm mutex assumes sequential dispatch (D-09) and `CommitCoordinator.submit` serializes with a mutex; keep one `for` loop.
- **Calling `session.recordTurn` for model turns.** The engine already records turns made through `session.model().complete` (`BoundModel.kt:108`); calling it again double-counts `tokensUsed` and breaks the ceiling tests.
- **Rebuilding assistant turns from parts.** Always append `response.message` as received. A mismatched stamp is refused by the mappers (`ConversationCheck.kt:73-81`).
- **Per-command text in `system` or `tools`.** The cached prefix must be byte-identical every iteration; per-command text goes only in the user message.
- **Returning `Escalate`/`NoMatch` from the loop.** After any apply or hold it would be suppressed into `Completed(partial = true)` (`TierWalk.kt:86-89`); before any, it hands the next tier a half-run conversation. Recommended: the loop returns only `Completed` or `Failed`.
- **Catching `CancellationException` or using `runCatching`.** `runCatching` is banned by the scanner (`gradle/invariants.gradle.kts` bannedRules).
- **Hard-coding 6, 60000, 4096 or a strike limit with a limit-looking name.** See the NoHardCodedConstantsTest note above.

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| Gate, apply, sink, run close, effects | Any write or journal logic in the loop | `session.submit(ToolStep)` | One write path, mutex-serialized, `NonCancellable` delivery, run-closed guard. |
| Token accounting | A loop-local token counter | `session.tokensUsed` | Includes earlier tiers and failed turns; saturating add. |
| Held notice bytes | A string literal | `DispatchResult.contentForModel` | Already byte-compatible (`CommitCoordinator.kt:10`). |
| Exception collapse around app code | `try/catch (e: Exception)` | internal `guarded` | The one justified suppression; preserves cancellation. |
| Failure mapping of stop reasons | A second `when` | Share the four-stop table with `stopOutcome` (`SingleShotOutcomes.kt:40-47`) | Same leaves, same order; avoid drift. Extract only what is shared; the hooks differ (END_TURN with no calls is `Completed` in the loop, a hook in SingleShot). |
| Clock for the user turn | A new default clock | the moved `CurrentZoneClock` | Same traveller-zone behavior as SingleShot (`SingleShotStrategy.kt:36-47`). |
| Cross-turn conversation coverage (every call answered, replay stamps) | A second conversation check in `:core` | `:providers` `ConversationCheck` (`providers/.../transcript/ConversationCheck.kt`) | Already enforced before any request; the loop adds only the id uniqueness rule the mapper deliberately left to it (`08-CONTEXT D-06`). |
| Fake provider | A new fake | `FakeAiProvider` | Records `ProviderRequest`s, fails loudly on an unplanned call. |

**Key insight:** every property in LOOP-03 (nothing committed is hidden) is already a property of the coordinator and the pipeline. The strategy's job is to not break it: no swallowed cancellation, no bare outcome that skips the effects snapshot, no state kept outside the coordinator.

## Phase 8 carries

1. **`ToolChoice.Auto`:** use `ToolChoice.Auto()` on every request (`ModelRequest.kt:78-80`). `Required` is only reshaped for single-turn use: AnthropicEncoder doc: "A multi-turn loop uses `ToolChoice.Auto`" (`AnthropicEncoder.kt:48`) and `ChatEncoder.kt` doc: the reshape line "is written for a single-turn call". Test: every request in a 3-turn run has `toolChoice is ToolChoice.Auto` and `singleToolCall == false`.
2. **Duplicate call ids across turns.** Where it lives today: `ConversationCheck.kt:83-92`: `callIds.toSet().size != callIds.size -> TOOL_CALL_ID_DUPLICATE`. That is WITHIN one assistant turn only (the function receives one turn's ids). Decoders reject blank ids; nothing rejects an id reused in a later turn. 08-CONTEXT D-06: "blank/duplicate ids left to Phase 9 whole-turn validation". What is left for the loop: keep a `HashSet<String>` of every id seen in the run; a turn whose id is already in the set (or repeats inside the turn) is rejected whole with `Failed(MalformedResponse)` before any dispatch. Risk and options: Open Question 2.
3. **Native replay dropping on a model-escalating ladder.** Today: the only mechanism is the documented rule in the `AssistantMessage` KDoc (`transcript/Message.kt`, the sentence ending at line 35): "A tier ladder that escalates to another model or provider in the middle of a conversation therefore sends a copy of the history whose assistant turns are rebuilt as `AssistantMessage(parts)` (no replay)". There is no helper function. Within one loop run the model is frozen (`RunSession.model()` binds once and keeps the handle, `RunSession.kt:73-75`), so a replay stamped at turn N always matches at turn N+1; a mismatch can only appear if a history crosses tiers. Because the loop never returns `Escalate` (recommended), it never hands a history up, so the carry is satisfied vacuously. Test: a `theLoopNeverHandsUpAConversation` sweep over all scripted paths asserting the outcome class is `Completed` or `Failed`; plus `theReplayStampIsTheSameProviderAndModelOnEveryTurn`. No new public helper in v1.0 (confirm, Open Question 9).
4. **Live facts to carry into docs/tests** [VERIFIED: `evidence/live-multiturn-capture.txt`, read this session]: OpenRouter accepts an echoed `tool_calls[].index`; `reasoning_details` echo accepted (R2 turns 1 and 3, R3 turn 1, signature 1611 chars); OpenAI accepts an unfiltered echo including `annotations`; Sonnet 5.5 returns a signed thinking block with no thinking parameter and the replay was accepted; Haiku read the cache on turn 2 (`cache_read:6754`).

## D-12 resolution (no external research needed)

D-12's four questions were settled by Phase 7/8 code and Phase 8's live captures; no web research was required for them.

| D-12 question | Resolution | Evidence |
|---|---|---|
| Strict enforcement with parallel tool calls | OpenAI-style requests switch `parallel_tool_calls:false` on whenever any tool is strict, a forced tool is required, or `singleToolCall` is set: `val oneCall = required != null \|\| strictNames.isNotEmpty() \|\| request.singleToolCall` then `put(KEY_PARALLEL_TOOL_CALLS, false)` when the vendor and model allow it (`ChatEncoder.kt:68-70`). Consequence for the loop: on OpenAI, if the engine decides any tool is strict-eligible, the model emits one call per turn, so a 4-call task consumes up to 4 of the 6 iterations. Strict eligibility requires every property required (`ChatStrict.kt` KDoc), so SB-style schemas with optional properties are not eligible. Anthropic: `disable_parallel_tool_use` is added only when `singleToolCall` is true (`AnthropicEncoder.kt:107`), so the loop (flag false) keeps parallel emission. Document in the README (Phase 10), no code. | [VERIFIED: ChatEncoder.kt:68-70; AnthropicEncoder.kt:107] |
| finish_reason `stop` with populated `tool_calls` | The decoder decides by presence: "7. tool calls make a tool turn whatever the finish reason says" and `calls.isNotEmpty() -> success(turn, StopReason.TOOL_USE, ...)` (`ChatDecoder.kt:153`); a decoder test exists (`ChatDecoderTest.kt:56`). The loop's guard test covers a custom provider returning `END_TURN` plus calls: presence wins and the turn dispatches (same as `SingleShotOutcomes.kt:46`). | [VERIFIED: ChatDecoder.kt:53-60,153] |
| Reasoning context across turns (OpenAI vs OpenRouter) | Settled by the live captures: OpenAI accepts the unfiltered echo; OpenRouter `reasoning_details` echo accepted incl. signed Anthropic reasoning. Replay is whole-message by reference; nothing for the loop to do except append `response.message`. | [VERIFIED: evidence/live-multiturn-capture.txt] |
| Stable OpenRouter `session_id` for sticky routing | Deferred by Phase 8 gate: "consider an OpenRouter session_id for sticky routing (deferred)". Not needed for correctness; additive later (a request field in `:providers`). | [VERIFIED: 08 evidence/phase-gate.txt CARRY-FORWARDS] |

## O-1: CommitCoordinator.applyAll per-item cancellation

**Is it a real gap?** Yes, narrow. `applyAll` is `val changes = mutations.map { applyStep.run(it) }` (`CommitCoordinator.kt:138`). The only cancellation checks are at the start of `submit`/`applyWithoutGate` (`admitCaller()` → `currentCoroutineContext().ensureActive()`, line 68) and after the gate answers (113). Inside one admitted batch, item N+1 starts without a check. A cancellation that arrives between items (for example during item N's sink delivery, which runs under `NonCancellable`, `ApplyStep.kt:55-60`) is observed by item N+1 only if its `apply()` reaches a cancellable suspension point; a non-suspending `apply()` runs and commits after the cancel. [VERIFIED: reading ApplyStep.run lines 81-92: `attempt` is `guarded { mutation.apply() }` with no pre-check.]

**How much it matters to the loop:** the loop submits one `ToolStep` per tool call, and each `submit` re-checks (`admitCaller`), so cross-call cancellation is already safe. The gap is real only for a `ToolStep.Mutation` that carries several `PendingMutation`s (a tool that expands to a batch; `Resolution.Steps` merging in SingleShot) and for `commitHeld` batches (`applyWithoutGate`).

**Recommendation: fix it (additive, internal, no sign-off):** in `applyAll`, check `currentCoroutineContext().ensureActive()` before each item, for example `mutations.map { currentCoroutineContext().ensureActive(); applyStep.run(it) }`. Semantics: items not yet started leave NO ledger entry (they were never applied; the executed list is honest) and the run closes `Cancelled` with the commits so far. `HeldCommit.run` already handles a `CancellationException` from `applyWithoutGate` generically (`HeldCommit.kt`: `catch (e: CancellationException) { cancelled = true; throw e }`, then `COMMIT_HELD_CANCELLED`), so the extra throw point is safe. Tests (in the style of `BatchIsolationTest`/`CommitPathTest.aCancelDuringAnApplyIsJournaledBeforeTheCancellationContinues`): `aCancelBetweenBatchItemsStopsTheRestAndKeepsTheCommittedOne` (sink hook for item 0 cancels the job; item 1 is a non-suspending `FakeMutation`; assert `applyCount == 0` for item 1, `closes.single() is RunTermination.Cancelled`, `commits` = item 0) and the same for `commitHeld`. Make it plan 1 (smallest, independent, and it unblocks the loop's cancel tests). If the orchestrator prefers dismissal instead, the alternative is a documented dismissal in the loop's SECURITY plus the same test asserting current behavior; the fix is cheaper than the paperwork.

## CLN-02 scan

**Current state of library code** (grep this session over `core/src/main`, `providers/src/main`, `keystore/src/main` for `secondbrain|caltracker|SB|CT|log_food|LogFood|MutationTier|SYSTEM_PROMPT|create_text_card|find_tags|edit_list_card`, case-insensitive): the only hits are three KDoc comments naming the SecondBrain and CalTracker apps as adopters (`AndroidKeyStoreKeyAccess.kt:25`, `AesGcm.kt:15`, `ApiKeyStore.kt:31`). No `LogFood*`, `log_food`, `MutationTier`, `SYSTEM_PROMPT` or tool names in main. App names in prose are not on CLN-02's list; decide explicitly (Open Question 7) and keep them or neutralize them in the same plan.
Test-side occurrences exist and are OUT of CLN-02's "library code" scope but worth a decision: `log_food` in `providers/src/test/.../ChatMessageEncoderTest.kt` (lines 34, 40, 45, 68, 81, 85, 212), `providers/src/test/resources/golden/chat/requests/{openai,openrouter}.json` and `golden/chat/responses/captured/openai-forced_log_food.json`. Renaming goldens risks MANIFEST/sanitizer checks; recommended: leave tests/goldens, scan `src/main` and `src/testFixtures` only.

**Why the existing scanner cannot host it as is:** `scanText` (`gradle/invariants.gradle.kts:153-163`) calls `splitCodeAndComments`, which blanks string and char literals and splits comments; every rule except "app planning id in comment" is matched against the code with literals blanked (`val target = if (rule.id.startsWith("app planning id")) comments else code`). `log_food` as a string literal is invisible to it, and an identifier rule would miss KDoc. CLN-02 needs a third target: the raw text.

**Recommended implementation (one plan, Gradle only):**
- In `gradle/invariants.gradle.kts` add a `rawRules` list (id, regex) matched against the unmodified file text, and extend `scanText` to run them. Deny-list (regexes, case-sensitive unless noted): `\bLogFood\w*`, `\blog_food\b`, `\bMutationTier\w*`, `\bSYSTEM_PROMPT\b`, `\bTAG_DISAMBIGUATION\w*`, plus SB tool names that already appear in tracked planning docs (for example `find_tags`, `create_tag`, `create_text_card`, `edit_list_card`: these strings are already tracked in `.planning/PROJECT.md`, so listing them adds no new disclosure; do not add names that are not already tracked), plus a tool-count rule: `\b(TOOL_COUNT|toolCount|EXPECTED_TOOL_COUNT|NUM_TOOLS)\b` and `\b1[78]\s+tools?\b` and `\btools?\s*(\.size|\.count\(\))\s*==\s*1[78]\b`. The deny-list lives in the `.kts` (not under `src/main`), so the scanner does not scan itself. It is applied by every published module because `invariants.gradle.kts` is applied per module (it already feeds `scanBannedConstructs` for `:core`, `:providers`, `:keystore`).
- Source set: `fileTree("src/main")` as now; add `src/testFixtures` for `:core` (published? no, but consumed by later phases and by `:sample`; cheap to include).
- Negative controls: add `config/negative-controls/app-domain.kt.txt` with header `// EXPECT: app-domain name in source` (rule id text must match the new rule id) and make `clean.kt.txt` include a near-miss (for example `LogFoodish` must NOT match `\bLogFood\w*` ... pick a regex that is exact where intended). The `verifyInvariantScannerControls` task (line 361) compares found rule ids to `EXPECT:` exactly, so the new file is self-checking. Add `expect_red "app-domain name ($m)" $m 'internal const val X = "log_food"' ":$m:scanBannedConstructs"` to `scripts/verify-negative-controls.sh` Part 1 (after line 66) for each module.
- Hard-coded tool count: also add a behavioral assertion in the loop tests that the loop works with 1, 2 and 25 tools and never reads `tools.size` against a constant (cheap, proves the property rather than only grepping).

## Phase-gate evidence shape (from 07-08)

Files under `.planning/phases/09-agentic-loop-strategy/evidence/` (mirror Phase 7; read this session):
- `phase-gate.txt`: title line; `date (UTC)`; `HEAD`; `COMMANDS (run one at a time, offline)` numbered with exit codes: (1) `./gradlew check --offline`, (2) `scripts/review-api-surface.sh --expect-sealed-complete` (expect `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=<n>`; Phase 7 had 177), (3) `scripts/verify-repo-hygiene.sh` (`HYGIENE OK`), (4) `scripts/verify-negative-controls.sh` (`negative-control failures: 0`); a NOTE that command 4 leaves failing JUnit XML in `providers/build/test-results` (it forces the OkHttp guard tests red with `expected=9.9.9`) so counts come from a genuine re-run after cleaning `:core:cleanTest :providers:cleanTest :providers:cleanTestOkhttp521 :providers:cleanTestOkhttp550`; `TEST COUNTS (summed over TEST-*.xml)` per module and per OkHttp leg (baseline at Phase 8 gate: core 535 tests in 55 suites; providers 555 per leg in 48 suites for 4.12.0 / 5.2.1 / 5.5.0; keystore 96 in 15); `REPO STATE` (`git ls-files -- '*api.txt'` empty, `git tag` empty); `REQUIREMENT COVERAGE` (requirement → test classes); `CARRY-FORWARDS`; `Gate-1: N/A`; `Live leg: none`; final line `PHASE GATE: PASS`.
- `agentic-limits-tests.txt` (mirrors `singleshot-limits-tests.txt`): "Obligation" paragraph (6 / 60000 / 4096 read from `session.policy`/`session.tokensUsed`), "Source of results: core/build/test-results/test/TEST-...AgenticLoopLimitsTest.xml (JUnit XML, read by script, not typed by hand)", per test `PASSED  <method>` + `pins: <sentence>`, test counts, the `NoHardCodedConstantsTest` line, "Boundary in force" paragraph. Required for the Phase 11 v1.0.0 cut.
- `agentic-surface-review.txt` (from `scripts/review-api-surface.sh --expect-sealed-complete --out <path>`; the `--out` path must not end in `api.txt` and must not lie under a module dir): list of public additions (expected: `ToolExecutor`, `AgenticLoopStrategy`, `AgenticLoopStrategy.Builder`, new `TraceCode` constants) and the checks (no enum, no copy/componentN, no public static field, sealed set still exactly seven).
- `seam-signoff.txt` if the checkpoint runs (exact precedent: `evidence/seam-signoff.txt` of Phase 7: date, relay description, reply verbatim, one `SIGNOFF: APPROVE|APPROVE-WITH-CHANGES|HOLD` line).

## Gradle and worktree facts (wave sizing)

- Convention carried from Phase 7/8: one plan per wave, strictly sequential, authoritative `wave:` frontmatter, `depends_on` the previous plan, at most 3 tasks per plan, tracer-first and test-first, an explicit `prohibitions` list (no `api.txt`, no tag, no live HTTP, never `git add -A`, never stage `.planning/graphs/`, `graphify-out/`, `.gsd/`, `.planning/intel/`, `.planning/config.json`, `.planning/v1.0-MILESTONE-RUN.md`, `.planning/state.json`, `.gsd-stage-*.done.json`). [CITED: 08-RESEARCH.md "Plan Size and Wave Guidance", line 586-601 and planning hygiene line 74]
- `.planning/config.json`: `use_worktrees: true`, `parallelization: true`, `workflow.nyquist_validation: true`, `security_enforcement: true`, `security_asvs_level: 1`, `code_review: true`. [VERIFIED: config.json read this session]
- Host: 8 cores, 31 GiB RAM with about 6 GiB available at the time of research (`free -g`: used 24, available 6) and `earlyoom -m 15,8` running (`ps`). Each Gradle daemon is capped by `org.gradle.jvmargs=-Xmx2048m` (`gradle.properties`) plus a Kotlin compile daemon; two parallel worktrees each running `check` is an OOM-kill risk. [VERIFIED: free/nproc/ps this session; gradle.properties]
- Worktrees have separate `build/` and `.gradle/` but share `~/.gradle` (dependency cache, build cache because `org.gradle.caching=true`) and its locks. `--offline` is the norm.
- Heavy: `:providers:check` (555 tests × 3 OkHttp legs: `test` 4.12.0, `testOkhttp521`, `testOkhttp550`; `providers/build.gradle.kts:133-142` makes each leg a `check` dependency), `:keystore` AGP unit tests and lint, `:sample:lint`, `scripts/verify-negative-controls.sh` (plants real violations in `src/main` and build files with a restore trap; "a few minutes warm"; must never overlap with any other Gradle run in the same tree), `scripts/review-api-surface.sh` (copies the tree to a temp dir and runs `:core:apiDump` there). Light: `:core:test --tests '<Class>' --offline -q` (verified: a filtered `SingleShotLimitsTest` run exits in about 2 s warm on this host, 2026-10-01).
- Measured whole-gate costs from Phase 8's gate evidence: `./gradlew check --offline` BUILD SUCCESSFUL in 34 s warm; `check --offline --rerun-tasks -x :sample:lintDebug -x :sample:lint -x :keystore:lintDebug -x :keystore:lint` in 1 m 13 s (148 tasks, none from cache). [CITED: 08 evidence/phase-gate.txt]
- Plan-level gate to use per plan: core-only plans run `./gradlew :core:check --offline` (detekt, scanBanned, ApiShapeTest, NoHardCodedConstantsTest, dependency/bytecode gates); the `:providers` plan runs `:providers:check` (all three legs); only the last plan runs the full `check` and the three scripts.

## Suggested plan breakdown (for the planner; one plan per wave)

| Wave | Plan | Objective | Main files | Tasks |
|------|------|-----------|-----------|-------|
| 1 | 09-01 | O-1 hardening + shared internal helpers moved out of `singleshot` + new TraceCodes | `commit/CommitCoordinator.kt`, `strategy/StrategyLimits.kt` (new), `strategy/singleshot/SingleShot*.kt` (imports), `telemetry/TraceCode.kt`, `BatchIsolationTest`/new `BatchCancellationTest` | 3 |
| 2 | 09-02 | `ToolExecutor` seam, `ScriptedToolExecutor` fixture, seam tests (redaction, holding gate leaves applyCount 0), then blocking `checkpoint:decision` seam sign-off relayed by the orchestrator (precedent: 07-03 Task 3) | `strategy/ToolExecutor.kt`, `testFixtures/.../ScriptedToolExecutor.kt`, `ToolExecutorSeamTest`, `RedactionCanaryTest`, `evidence/seam-signoff.txt` | 3 (autonomous: false) |
| 3 | 09-03 | Tracer: transcript → tool turn → read/finished → prose Done; `AgenticLoopStrategy` skeleton, builder, history, user turn, request shape (Auto, singleToolCall false, cached prefix) | `strategy/agentic/*`, `AgenticLoopTestSupport.kt`, `AgenticLoopDispatchTest`, `AgenticLoopUserTurnTest` | 3 |
| 4 | 09-04 | Gate path: mutation → gate → apply → sink, held notice, rejected, preview, unknown tool, read-returns-Mutation, prepare fault, strike rule (D-01), terminal (D-13) | `AgenticDispatch.kt`, `AgenticLoopGateTest`, `AgenticLoopTerminalTest` | 3 |
| 5 | 09-05 | Guards and limits: whole-turn validation (incl. cross-turn ids), ceiling-before-dispatch, final-iteration guard, stop-leaf matrix, "stop + tool_calls" guard, 6/60000/4096 `AgenticLoopLimitsTest` | `AgenticValidation.kt`, `AgenticTurn.kt`, `AgenticLoopGuardsTest`, `AgenticLoopLimitsTest` | 3 |
| 6 | 09-06 | LOOP-03 exit paths (done, budget, cancel at provider/gate/apply, error, strike), GATE-05 once, Phase 8 carries, provider-neutrality (3 ProviderIds), redaction canary run | `AgenticLoopExitPathsTest`, `AgenticLoopCarryTest`, `AgenticLoopProviderNeutralityTest`, `RedactionCanaryTest` | 3 |
| 7 | 09-07 | `:providers` `AgenticLoopWireTest`: Anthropic, OpenAI, OpenRouter bodies and outcomes on all three OkHttp legs; SB-shaped renderer bytes on Anthropic and Chat wires; held bytes on the wire | `providers/src/test/.../AgenticLoopWireTest.kt` | 2 |
| 8 | 09-08 | CLN-02 scanner + negative control + plants; surface review; limits evidence; full phase gate | `gradle/invariants.gradle.kts`, `config/negative-controls/app-domain.kt.txt`, `scripts/verify-negative-controls.sh`, `evidence/*` | 3 |

CLN-02 could run earlier (it is independent of the loop's code), but its scan must pass over the final loop sources, so it is safest as the last code change before the gate. If the orchestrator wants the seam question answered sooner, plan 09-02 can be reordered to wave 1.

## Common Pitfalls

### Pitfall 1: Double-counting tokens
**What goes wrong:** calling `session.recordTurn` after `model.complete` inflates `tokensUsed` and trips the ceiling early.
**Why:** `RoutedModel.complete` already calls `recorder.turnRecorded` (`BoundModel.kt:108`).
**Avoid:** never call `recordTurn` for routed turns; assert `outcome.trace.attempts.single{...}.turns.size == providerCallCount`.
**Warning sign:** a ceiling test passing only when the scripted usage is halved.

### Pitfall 2: Building a `ToolResultsMessage` that throws
**What goes wrong:** repeated ids make `ToolResultsMessage.init` throw `IllegalArgumentException` (`Message.kt:74`), which `TierWalk` turns into `Failed(Unexpected("IllegalArgumentException"))` after tools already ran.
**Avoid:** whole-turn validation BEFORE dispatch; the validation set must be a superset of the constructor's requirements (non-empty, distinct ids).

### Pitfall 3: Per-command text in the cached prefix
**What goes wrong:** system/tools differ per iteration or per command and the prompt cache never hits (SB's 7,016-token Gate-1 target).
**Avoid:** call `tooling.tooling(input)` once; pass `snapshot.system` and `snapshot.tools` (same instances) every iteration; test byte equality across iterations (analog of `twoCommandsShareTheSameSystemAndToolsBytes`).

### Pitfall 4: Parallel flag copied from SingleShot
**What goes wrong:** copying `SingleShotStrategy.request()` verbatim sends `singleToolCall = true` (line 118) and `ToolChoice` from a forced path; the loop then loses parallel emission on Anthropic and would reshape/force on the wire.
**Avoid:** `ToolChoice.Auto()` and `false`; test both.

### Pitfall 5: A terminal call on the final iteration
**What goes wrong:** applying the final-iteration guard to a turn that contains a terminal call reports `BudgetExceeded(ITERATIONS)` for a clarification the model legitimately asked on iteration 6, although no follow-up request is needed. SB had no terminal tools.
**Avoid:** decide explicitly (Open Question 3; recommended: a turn containing a terminal call is exempt from the final-iteration guard because the run ends without another request; the ceiling check still applies).

### Pitfall 6: Strike abort versus terminal call in one turn
**Avoid:** decide explicitly (Open Question 4; recommended: the strike abort wins, `Failed(ToolFailure)`, and the terminal call is not delivered).

### Pitfall 7: Banned words in main KDoc
**What goes wrong:** `NoHardCodedConstantsTest.noModelIdAppearsInCodeKdocOrComments` fails on a KDoc sentence such as "e.g. Opus"; the invariant scanner fails on a comment containing `Phase 9 D-01` or `T-09-03` (`bannedRules` line 31 and the detekt `ForbiddenComment` list).
**Avoid:** describe behavior, not planning ids or model names, in main comments.

### Pitfall 8: Public API shape tests
**What goes wrong:** `ApiShapeTest` fails on a new public class with a default-argument constructor stub, an enum, a data-shaped class or a public static field; `review-api-surface.sh` fails on a new sealed type.
**Avoid:** internal constructors with `Builder`s (as SingleShot), no sealed/enum/data; use `@JvmInline value class` only for open vocabularies (none needed here).

### Pitfall 9: Mutating-flag semantic mismatch for consumers
**What goes wrong:** SB's `ExecutedToolCall.mutating` is the tool classification; the engine's `ExecutedAction.mutating` is false for preview and rejection. A consumer filter on `mutating` drops rejected mutating attempts (nothing was written, so retry-safety is unaffected), but they should know.
**Avoid:** state it in the seam sign-off message and the README (Phase 10).

## Code Examples

### Reading the first text block and the tool calls (neutral)
```kotlin
// Source: transcript/AssistantPart.kt (Text, ToolCall), transcript/Message.kt (AssistantMessage.toolCalls)
val reply: String? = response.message.parts.filterIsInstance<AssistantPart.Text>().firstOrNull()?.text
val calls: List<AssistantPart.ToolCall> = response.message.toolCalls
```

### Scripting a pre-spent run for the limits tests
```kotlin
// Source: core/src/test/.../SingleShotLimitsTest.kt:53-60 (spendingTier) and :87-96
private fun spendingTier(tokens: Long): CommandStrategy =
    ScriptedStrategy(
        StrategyId("earlier"),
        { _, session ->
            session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, tokens), 1L))
            StrategyOutcome.Escalate(EscalationReason.NoToolCall())
        },
    )
```
(An earlier tier escalating without writing is allowed, so the loop tier then runs with `tokensUsed == tokens`.)

### Observing sequential dispatch and order
Use the shared `RecordingSink<String>` log with `ScriptedGate.admitAll(log)`, `FakeMutation(name, result, log = log)` and `RecordingCommitSink(log)`: the log then reads `gate, apply:<tool>, sink:action:<pos>:<kind>` per call, in call order (`CommitPathTest.kt:99-130` asserts exactly this shape). The loop's `ScriptedToolExecutor` should append `prepare:<tool>` to the same log so the interleaving `prepare:a, gate, apply:a, sink..., prepare:b, ...` proves sequential dispatch.

### SB-shaped renderer for the byte-exact test
```kotlin
// Source: SB AnthropicAgentLoop.kt:377-384 (userTextMessage) vs core/strategy/UserTurn.kt:15-27
val sbShaped = UserTurnRenderer { ctx ->
    "Current local date-time: ${ctx.dateTime.toLocalDateTime()} (${ctx.dateTime.zone.id})\n\n" +
        "Voice command: ${ctx.input.transcript}"
}
```
Assert on the `:providers` wire: the Anthropic request's first user message text and the Chat request's first `role:user` message content equal this exact string for a fixed clock. (The exact `localDateTime` formatting is the app's; the test pins that the engine adds nothing around the renderer output.)

## State of the Art

| Old approach | Current approach | Impact |
|---|---|---|
| SB loop speaks Anthropic JSON directly (`rawContent: JsonArray`, `buildRequestBody`) | Neutral `Message`/`ModelRequest` and per-provider mappers (Phase 3/4/5/8) | The loop has no JSON wire knowledge. |
| SB `MutationGate` consulted inside the tool registry per call | Engine-owned `CommitCoordinator` + `PreApplyGate` + `CommitSink` | The gate cannot be skipped by an app executor. |
| SB result sealed interface with `executedTools` on each variant | `CommandOutcome` variants all carry `executed`, `commits`, `held`, `trace` | LOOP-03 is structural. |
| SB `BudgetBound.MAX_ITERATIONS/TOKEN_CEILING`, enum `UnavailableReason` | `BudgetBound.ITERATIONS/TOKENS` value class, open `FailureReason` | Plans and docs must use the engine names. |

**Deprecated/outdated within this repo's own docs:** `.planning/research/ARCHITECTURE.md` section 3.2 (`CommitSink.commit/undo`, `MutationOutcome`, `HoldReason`, `ProposalOrigin`) was superseded by the A17 per-action `onAction`/`onRunClosed` sink and `PendingMutation.apply(): StepResult`; do not copy that sketch. Its `ToolExecutor.prepare(call: ToolCall, ctx: ToolContext)` line (ARCHITECTURE.md:403) is a sketch, not a shipped type.

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | `ToolExecutor` should be `prepare(call: Extraction, input: CommandInput): ToolStep` (reusing `Extraction`) | Pattern 1, Open Question 1 | SB/orchestrator may want a distinct type or extra context; changing after sign-off is cheap, after the cut is breaking. |
| A2 | New `TraceCode` names (unknown tool, tool prepare fault, read tool returned a mutation) | What is missing | Public constants freeze at the cut; names are pure proposals. |
| A3 | detekt default rule thresholds (LongMethod, CyclomaticComplexMethod, ReturnCount, NestedBlockDepth) apply to the new files | Recommended project structure | Plan may over- or under-split files; confirm with an early `:core:detekt` run. |
| A4 | Anthropic rejects a `tool_use` id repeated across the conversation ("tool_use ids must be unique") | Phase 8 carries 2, Open Question 2 | Reports found were about duplicates inside a request, incl. parallel/streaming bugs; the exact conversation-wide rule is not confirmed from primary docs. If tolerant, strict cross-turn rejection is stricter than the provider. [CITED: github.com/anthropics/claude-code/issues/20631, /21317, litellm PR #23507 (search results, LOW)] |
| A5 | Some routed OpenAI-compatible upstreams regenerate `call_0, call_1` ids every turn | Open Question 2 | If rare, strict rejection is fine; if common, the loop breaks on those routes. [CITED: 08-RESEARCH.md row 11 (reports, MEDIUM)] |
| A6 | A terminal-call turn should be exempt from the final-iteration guard; strike abort beats a terminal call; calls after a terminal call are dropped and mark `partial = true` | Pitfalls 5, 6, Open Questions 3 to 5 | Behavior choices not in D-13; the orchestrator may rule otherwise. |
| A7 | The loop never returns `Escalate`/`NoMatch` | Phase 8 carries, Open Question 9 | If a future ladder wants a loop escalation, a history-stripping helper becomes necessary (additive). |
| A8 | Pre-call `>=` ceiling check before every iteration (vs SB's post-response `>` only) | Limits | Differs from SB only at exact equality; a different expectation would change one boundary test. |

## Open Questions

Items marked ORCH need the orchestrator (`yahir-gsd-control-plane-f2`, reached through the milestone master, never Yahir or a peer directly) before or during planning.

1. **ORCH: `ToolExecutor` seam-shape sign-off** (blocking checkpoint, precedent 07-03 Task 3, `evidence/seam-signoff.txt`)
   - Know: no such type exists; roadmap and requirements name it; SB (`secondbrain-2c`) plugs `RoomToolFacade`/`AnthropicToolRegistry` specs into it; CT does not use it in v1.0.
   - Unclear: reuse `Extraction` vs a new `ToolInvocation`; whether `prepare` needs the `CommandInput`; builder member set.
   - Recommendation: relay this text: "`ToolExecutor { suspend fun prepare(call: Extraction, input: CommandInput): ToolStep }`; `AgenticLoopStrategy(StrategyId) { tooling; executor; userTurn; clock; capabilities }`; `ToolingSnapshot.singleShotTool = null`; limits from `session.policy`; held bytes fixed by the coordinator; the loop returns only Completed or Failed; reads are not in the executed list (a rejected or previewed call to a mutating tool is `mutating = false` in the engine ledger, unlike SB); user-turn via the Phase 7 `UserTurnRenderer`." Ask `secondbrain-2c` about all items; CT is not affected.

2. **ORCH: Cross-turn duplicate tool-call ids (Phase 8 carry 2) versus upstreams that reuse ids**
   - Know: mapper catches within-turn only (`ConversationCheck.kt:86`); SB tolerates reuse (`AnthropicAgentLoopTest.kt:803`); Phase 8 research found providers that regenerate `call_N` each turn (A5); the engine identifies actions by `position`, not provider ids (`ExecutedAction` KDoc), so reuse is harmless to the ledger.
   - Unclear: whether to reject always, or only where the provider does.
   - Recommendation: implement the locked behavior (reject, `Failed(MalformedResponse)`, loud) and ask the orchestrator whether to relax it to "within-turn only" (the SB behavior) if a regenerating route matters to Yahir. A provider-aware variant (`BoundModel.provider`) is possible but adds branching to a provider-neutral loop; not recommended.

3. **Final-iteration guard versus a turn that contains a terminal call** (A6). Recommended: exempt; add `aTerminalTurnOnTheFinalIterationCompletesInsteadOfBudgetExceeded`. Claude's discretion unless the orchestrator objects.

4. **Strike abort and terminal call in the same turn; calls after a terminal call.** Recommended: strike abort wins (`Failed(ToolFailure)`, terminal not delivered); calls after a terminal call are never dispatched, recorded with `EXTRA_TOOL_CALLS_DROPPED`, and make the `Completed` partial (SingleShot precedent, `SingleShotStrategy.kt:134-139`).

5. **ORCH (CONTEXT conflict): `partial` versus D-07/GATE-07.** The Runtime Decision says a loop that ends "with work done but unfinished (for example the iteration cap after a commit)" reports `partial = true`; D-07 and `REQUIREMENTS.md:45` say budget is ALWAYS `Failed(BudgetExceeded)`. Recommended reading: budget stays `Failed` carrying commits; `partial` is used only for dropped-after-terminal calls. Please confirm so the plan states one rule.

6. **New public `TraceCode` constants** (A2): three additive constants; names to be approved together with Question 1.

7. **CLN-02 scope and deny-list.** Recommended: scan `src/main` (all three modules) and `core/src/testFixtures`; leave `log_food` in `providers` tests/goldens; allow adopter app names in KDoc or neutralize the three keystore comments in the same plan; deny-list limited to names already tracked in planning docs (see CLN-02 section). Needs a yes from the orchestrator only if they want the test-side `log_food` renamed.

8. **O-1:** fix, no sign-off needed (internal). Listed so the SECURITY artifact records the disposition.

9. **ORCH: Loop is terminal (never Escalate); no history-stripping helper in v1.0** (A7). Satisfies Phase 8 carry (3) vacuously with a guard test. Confirm.

10. **Ceiling boundary** (A8): pre-call `>=` each iteration plus post-response `>` before dispatch. Internal; mention in the sign-off message for transparency.

11. **Live legs:** none recommended. Everything in this phase is JVM-verifiable; Phase 10 VER-03 already extended to P8 Chat/OpenRouter multi-turn on the TESTER (`bbf91a4`), and the Anthropic agentic cache-read gate is Phase 10's Gate-1. If the orchestrator wants a live agentic smoke earlier, it must be opt-in, outside `check`, via `with-test-keys`, cheapest models (Haiku 4.5, gpt-5.4-mini, a cheap OpenRouter route), at most about 6 requests per provider, stated in the plan; not required here.

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|---|---|---|---|---|
| JDK 17 | Gradle build | yes | OpenJDK 17.0.19 (ps shows `/usr/lib/jvm/java-17-openjdk-amd64`) | — |
| Gradle wrapper | all tests | yes | 9.4.1 (`gradle/wrapper`, per hygiene script) | — |
| Dependency cache (offline) | `--offline` runs | yes | verified by a successful filtered `:core:test --offline` run (exit 0, about 2 s) | — |
| Provider keys / `with-test-keys` | live legs | not needed | — | none required |
| Device / adb | Gate-1 | not needed (JVM-only phase) | — | Phase 10 |
| MockWebServer (legacy `okhttp3.mockwebserver`) | `AgenticLoopWireTest` | yes (already used by `SingleShotWireTest`) | tracks the OkHttp leg | — |

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`); hand-written fakes (no MockK) |
| Config file | `core/build.gradle.kts`, `providers/build.gradle.kts` (OkHttp legs `testOkhttp521`, `testOkhttp550`), `config/detekt/detekt.yml`, `gradle/invariants.gradle.kts` |
| Quick run command | `./gradlew :core:test --tests '*AgenticLoop*' --offline -q` (verified pattern: `--tests '*SingleShotLimitsTest'` ran green, exit 0) |
| Providers quick | `./gradlew :providers:test --tests '*AgenticLoopWireTest' --offline -q`; per-leg spot check `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*AgenticLoopWireTest' --offline` |
| Static gates | `./gradlew :core:detekt :core:scanBannedConstructs --offline -q` (and `:providers:` / `:keystore:` for CLN-02) |
| Full suite command | `./gradlew check --offline` |
| Phase gate scripts | `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test type | Automated command | File exists? |
|--------|----------|-----------|-------------------|--------------|
| LOOP-01 | `ToolExecutor` composes with the write path; a holding gate leaves `applyCount == 0`; seam types redact `toString` | unit | `./gradlew :core:test --tests '*ToolExecutorSeamTest' --offline -q` | no, Wave 0 |
| LOOP-01 | Tool turn then prose: sequential dispatch in call order, one results message, history append-only, reply = first text block, reads absent from executed list but their names in the turn trace | unit | `... --tests '*AgenticLoopDispatchTest'` | no, Wave 0 |
| LOOP-01 | Mutation passes the gate; held feeds `{"applied":false,"status":"held_for_confirmation"}` and the loop continues; rejected/preview do not reach the gate; unknown tool never calls the executor; read tool returning a Mutation never reaches the gate; prepare throw is an is_error with the fixed notice | unit | `... --tests '*AgenticLoopGateTest'` | no, Wave 0 |
| LOOP-01 | Same scripted conversation on `ProviderId.ANTHROPIC`, `OPENAI`, `OPENROUTER` gives identical outcomes; agentic source contains no provider branching | unit | `... --tests '*AgenticLoopProviderNeutralityTest'` | no, Wave 0 |
| LOOP-01 | Real mappers: Anthropic/OpenAI/OpenRouter request bodies per turn (Auto choice, no forced tool, tools+system identical, history append-only), tool results encoded per dialect, held bytes, SB-shaped user turn bytes | integration (MockWebServer) | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --tests '*AgenticLoopWireTest' --offline` | no, Wave 0 |
| LOOP-02 | whole-turn validation rows (within-turn dup, cross-turn dup, TOOL_USE with no calls): zero executor/gate/sink calls | unit | `... --tests '*AgenticLoopGuardsTest'` | no, Wave 0 |
| LOOP-02 | token-ceiling check before dispatch; final-iteration guard; 2-strike abort with committed sibling recorded; per-tool-name strikes; unknown tool strike; stop-leaf matrix; "stop + tool_calls" guard | unit | `... --tests '*AgenticLoopGuardsTest'` | no, Wave 0 |
| LOOP-02 | 6 / 60000 / 4096 read from policy (mirror of SingleShotLimitsTest's ten, plus iteration and multi-turn per-turn limit) | unit | `... --tests '*AgenticLoopLimitsTest'` | no, Wave 0 |
| LOOP-02 | sequential dispatch with a suspending gate (no deadlock, order preserved) | unit | `... --tests '*AgenticLoopGateTest'` | no, Wave 0 |
| D-13 | terminal-only turn; terminal after a committing call; terminal alongside a held call; calls after terminal dropped; terminal on the final iteration | unit | `... --tests '*AgenticLoopTerminalTest'` | no, Wave 0 |
| LOOP-03 | each exit path (done, budget iterations, budget tokens, strike, provider failure after a commit, cancel during provider call, cancel while gate suspended, cancel during apply, malformed turn): outcome/termination `executed`, `commits`, `held` complete; sink actions precede `onRunClosed`; `closes.size == 1` | unit | `... --tests '*AgenticLoopExitPathsTest'` | no, Wave 0 |
| LOOP-03 / O-1 | cancel between batch items stops the rest; `commitHeld` same | unit | `... --tests '*BatchIsolationTest' --tests '*BatchCancellationTest'` | partly (BatchIsolationTest exists), Wave 0 |
| Carries | Auto choice and `singleToolCall == false` every request; replay appended by reference and stamped same provider/model each turn; loop never hands up a conversation | unit | `... --tests '*AgenticLoopCarryTest'` | no, Wave 0 |
| D-02 | SB-shaped renderer exact text in the first user message (core: FakeAiProvider request; providers: wire) | unit + integration | `AgenticLoopUserTurnTest`, `AgenticLoopWireTest` | no, Wave 0 |
| TEL-04 | canary in transcript, system, args, results, reply never appears in outcome/trace/events/toString/exceptions of an agentic run | unit | `... --tests '*RedactionCanaryTest'` | exists, extend |
| CLN-02 | banned app-domain words in any published module's `src/main` fail `scanBannedConstructs`; negative control proves each rule; no tool-count literal | build gate | `./gradlew :core:scanBannedConstructs :providers:scanBannedConstructs :keystore:scanBannedConstructs :core:verifyInvariantScannerControls --offline -q`; `scripts/verify-negative-controls.sh` | gate exists, rules and control are Wave 0 |
| CLN-02 | loop works with 1, 2 and 25 tools; never compares `tools.size` to a constant | unit | `AgenticLoopDispatchTest` | no, Wave 0 |
| Surface | no new sealed/enum/data/public static; default-arg stub rule; limit-constant naming | unit | `... --tests '*ApiShapeTest' --tests '*NoHardCodedConstantsTest'` | exists |

### Sampling Rate
- **Per task commit:** the plan's quick command (`:core:test --tests '<new classes>'`) plus `./gradlew :core:detekt :core:scanBannedConstructs --offline -q`.
- **Per wave merge:** `./gradlew :core:check --offline` (core plans) or `./gradlew :providers:check --offline` (the wire plan, all three legs).
- **Phase gate:** `./gradlew check --offline` green, then the three scripts (`review-api-surface.sh --expect-sealed-complete`, `verify-repo-hygiene.sh`, `verify-negative-controls.sh`), then re-run `check` from cleaned test tasks to get genuine counts (the negative-controls script leaves red XML), before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `core/src/testFixtures/.../testing/ScriptedToolExecutor.kt` — scripted `prepare` answers, records calls, optional shared `RecordingSink<String>` log (`prepare:<tool>`), can throw or suspend on cue.
- [ ] `core/src/test/.../AgenticLoopTestSupport.kt` — `agenticLoop(...)` factory with `fixedClock`, generalized `pipelineOf(..., providerId)`, answer builders for tool turns with several calls and usage.
- [ ] Test classes listed in the table above (`ToolExecutorSeamTest`, `AgenticLoopDispatchTest`, `AgenticLoopGateTest`, `AgenticLoopGuardsTest`, `AgenticLoopLimitsTest`, `AgenticLoopTerminalTest`, `AgenticLoopExitPathsTest`, `AgenticLoopCarryTest`, `AgenticLoopProviderNeutralityTest`, `AgenticLoopUserTurnTest`, `BatchCancellationTest`).
- [ ] `providers/src/test/.../AgenticLoopWireTest.kt` (reuse the `successBody/toolUseBlock/textBlock` and `chatBody/chatMessage/chatToolCall` helpers that `SingleShotWireTest.kt` imports).
- [ ] `config/negative-controls/app-domain.kt.txt` and its plant lines in `scripts/verify-negative-controls.sh`.
- [ ] No framework install needed.

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high` (config.json). The phase adds no network surface and no persistence; the relevant risk is information disclosure through diagnostics and unsafe handling of model output.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard control |
|---------------|---------|------------------|
| V2 Authentication | no | — (key handling is Phases 4-6) |
| V3 Session Management | no | — |
| V4 Access Control | yes (write authorization) | Every `Mutation` goes through `PreApplyGate`; the gate fails closed (`GateStep.kt:19-22`); a read tool can never reach the gate or apply; the executor cannot write because it receives no write capability. |
| V5 Input Validation | yes | Tool name from the model is checked against `ToolingSnapshot.tools` before any executor call; arguments are passed through untouched and validated by the app executor; ids validated whole-turn before dispatch; `ToolResultsMessage` invariants respected. |
| V6 Cryptography | no | — |
| V7 Error handling and logging | yes | No logging in the library (`android.util.Log`, `println` banned by the scanner); fixed notices for faults; `toString` of every new public type prints counts/names/lengths only; canary sweep. |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard mitigation |
|---------|--------|---------------------|
| Model emits a tool name that is not offered (hallucinated or injected) | Tampering / Elevation | Unknown tool: no executor call, is_error, strike; two strikes abort. |
| Model-controlled arguments reach app code | Tampering | Passed as `JsonObject` untouched; validation is the executor's duty (KDoc); never logged. |
| Prompt injection via tool results (card titles etc.) | Tampering | Out of engine scope; the app's system prompt must treat tool results as data (SB's does). Document in README (Phase 10). |
| A read tool returns a Mutation to smuggle a write past the flag | Elevation | `ToolSpec.mutating == false` + `Mutation` → dropped before the gate, is_error, strike. |
| Cancellation swallowed so writes continue | Tampering | Loop never catches cancellation (internal `guarded`); `submit` refuses a cancelled caller; O-1 hardening for batches. |
| Unbounded loop or token spend (cost/DoS) | Denial of service | `maxIterations`, `tokenCeiling`, `maxTokensPerTurn` from `session.policy`; `commandTimeoutMillis` backstop; final-iteration guard so no tool runs unanswered. |
| Secret or content in diagnostics | Information disclosure | Fixed fault notices; trace codes only; redaction canary test; no model text in `FailureReason` (they carry codes). |
| Committed work hidden behind a failure | Repudiation | All `Failed` outcomes carry the ledger; exit-path tests; `onRunClosed` once. |
| Duplicate tool-call ids confuse result matching | Tampering | Whole-turn id validation; results keyed by id inside one `ToolResultsMessage` (distinct ids enforced). |

## Sources

### Primary (HIGH confidence; read this session)
- Repo code: `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/` — `strategy/{CommandSession,CommandStrategy,StrategyOutcome,StrategyCapabilities,ToolSpec,ToolSpecProvider,UserTurn,TerminalCall,Clarification,OutcomeResolver}.kt`, `strategy/singleshot/{SingleShotStrategy,SingleShotOutcomes}.kt`, `commit/{ToolStep,FinishedKind,ApplyStep,CommitCoordinator,GateStep,PreApplyGate,CommitSink,ActionKind,ActionLedger,HeldProposal,RunTermination}.kt`, `pipeline/{TierPolicy,RunSession,TierWalk,CommandPipeline,CommandOutcome,HeldCommit}.kt`, `failure/{FailureReason,BudgetBound,EscalationReason,FailureDetails}.kt`, `transcript/{AssistantPart,Message,ModelRequest,ModelResponse,NativeReplay}.kt`, `telemetry/{TurnRecord,Usage,TraceCode,RunRecorder}.kt`, `provider/{BoundModel,ModelResult,AiProvider,ProviderRequest}.kt`, `internal/Guarded.kt`; `core/src/testFixtures/.../testing/*.kt`; `core/src/test/.../{SingleShotLimitsTest,SingleShotTestSupport,SingleShotFixtures,NoHardCodedConstantsTest,ApiShapeTest,RunClosedPathsTest,CommitPathTest}.kt`.
- `providers/src/main/.../{transcript/ConversationCheck,chat/ChatDecoder,chat/ChatEncoder,chat/ChatStrict,anthropic/AnthropicEncoder}.kt`; `providers/src/test/.../SingleShotWireTest.kt`, `conformance/WireDialect.kt`.
- Build/gates: `build.gradle.kts`, `core/build.gradle.kts`, `gradle.properties`, `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml`, `config/negative-controls/*`, `scripts/{review-api-surface,verify-negative-controls,verify-repo-hygiene}.sh`.
- Planning: `09-CONTEXT.md`, `REQUIREMENTS.md`, `ROADMAP.md` (Phase 9, 10), `STATE.md`, `cross-repo/HANDOFF.md`, `CROSS-REPO-SCOPE-CONTRACT.md` (§6.2 step 6b, A17, A19), `research/ARCHITECTURE.md` §3-4, Phase 2 `02-SECURITY.md` (O-1), Phase 7 plans 07-01..08 headers, `07-03-PLAN.md` Task 3, `evidence/{phase-gate,singleshot-limits-tests,seam-signoff,singleshot-surface-review}.txt`, Phase 8 `08-CONTEXT.md`, `08-RESEARCH.md` (rows 11, 586-601), `evidence/{phase-gate,live-multiturn-capture}.txt`.
- SB (read-only): `.../SecondBrain/app/src/main/java/com/example/secondbrain/core/agent/{AnthropicAgentLoop,AgentLoopResult,AnthropicToolRegistry}.kt`; test names from `.../app/src/test/.../AnthropicAgentLoopTest.kt` (`grep -n "fun \`"` plus rows read at 360-600).
- Runtime probes: `./gradlew :core:test --tests '*SingleShotLimitsTest' --offline -q` (exit 0, about 2 s); `free -g`, `nproc`, `ps` (earlyoom).

### Secondary (MEDIUM/LOW confidence)
- WebSearch (one query): Anthropic "tool_use ids must be unique" reports, [anthropics/claude-code#20631](https://github.com/anthropics/claude-code/issues/20631), [#21317](https://github.com/anthropics/claude-code/issues/21317), [BerriAI/litellm#23507](https://github.com/BerriAI/litellm/pull/23507). LOW: community issues; scope (within a request versus whole conversation) not confirmed from first-party docs. Not cached to the research store (no seam call was made for this single query).
- `08-RESEARCH.md` row 11 citing community reports of per-turn regenerated ids (MEDIUM).

## Metadata

**Confidence breakdown:**
- Reusable seams and the gap list: HIGH — every file read this session, line numbers re-grepped where a concatenated listing could have skewed them.
- SB guard semantics: HIGH — SB loop, registry and test names read directly.
- Seam shape for `ToolExecutor`/builder: MEDIUM — proposal pending sign-off.
- Cross-turn duplicate-id rule: MEDIUM — locked by CONTEXT, but a real-world compatibility risk is documented.
- CLN-02 hosting: HIGH on the mechanics (scanner source read), MEDIUM on the exact deny-list.
- Detekt thresholds: LOW-MEDIUM (A3).

**Research date:** 2026-10-01
**Valid until:** the next change to `core/src/main` or `gradle/invariants.gradle.kts` (this research is code-grounded); re-check the seam list if Phase 9 planning is delayed past another phase's merge.
