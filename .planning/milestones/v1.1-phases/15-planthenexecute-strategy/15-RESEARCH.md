# Phase 15: PlanThenExecute Strategy - Research

**Researched:** 2026-10-06
**Domain:** Kotlin/JVM library tier (`:core`), provider-neutral tool-calling conversation, JsonObject argument binding, gated sequential writes
**Confidence:** HIGH on code anchors and integration points (every anchor below was read this session); MEDIUM on model-facing prompt wording (needs the D-04 live probe); items tagged `[ASSUMED]` are in the Assumptions Log.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 [plan-call]:** Forced `submit_plan` + app tools in the request (model sees inputSchemas), SingleShot's request shape; a reshaped answer that calls an app tool directly is a malformed plan. _(source: ai-auto)_
- **D-02 [schema]:** `steps:[{id, tool, arguments}]` + an in-schema needs-lookup escape (see [lookup]), sent non-strict, whole plan validated before step 1, no model-written reply. _(source: ai-auto)_
- **D-03 [call-id]:** The planning call's id (SEAM-06 says null only for zero-call tiers); `position` distinguishes steps. Confirm SB 178 undo grouping. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #4): each step's ExecutedAction MUST keep a distinct ordinal/step index (SB keys undo by runId + ordinal, not providerCallId).
- **D-04 [binding]:** Whole-value string `"$<stepId>.<key>"` with stable model-assigned ids, no interpolation, resolved in JsonObject space before prepare; validate with a small bounded live probe on the cheap models. — **Reversibility:** one-way — the `$<stepId>.<key>` syntax is written into consumers' planning prompts and frozen at the v1.1.0 tag _(source: human)_
- **D-05 [bindable]:** COMMITTED only; HELD/PREVIEW/IS_ERROR never bindable; keys are app-owned. _(source: ai-auto)_
- **D-06 [ref-check]:** Static → replan; dynamic → no replan (suppressed partial), so SC2/SC3 and SC4 both hold. _(source: ai-auto)_
- **D-07 [hold]:** Stop at first hold; "worked" = any applied (incl. errored) or HELD, mirroring TierWalk.hasWorked; return Escalate so TierWalk records escalation_suppressed. Confirm with SB 177 (destructive-step confirm). _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #3): steps committed before the hold stay committed (escalation suppressed); the held step surfaces for the needs-confirmation sheet.
- **D-08 [replan]:** Conversation continuation, identical prefix (prefix byte-comparison test), hard-coded 1. _(source: ai-auto)_
- **D-09 [builder]:** Minimal Builder reusing Phase 12's OutcomeHooks/decideResult; truncated plan → MalformedExtraction → escalate (Agentic can still handle it) rather than failing the command. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
- **D-10 [lookup]:** Static pre-execution check with read tools in the enum + in-schema needs-lookup flag; zero side effects before escalating; terminal tools excluded from the enum. _(source: ai-auto)_
- **D-11 [escalation]:** `Other("plan_needs_lookup")` / `MalformedExtraction`, forward `session.carry` unchanged so SingleShot's carry still reaches Agentic. _(source: ai-auto)_
- **D-12 [budget]:** Existing helpers + a Builder `maxSteps` (default ~8); model-call count from TierAttempt.turns. _(source: ai-auto)_

**Runtime Decisions (bottom of CONTEXT.md, refreshed 2026-10-06 against Phase 12 output):**
- **[call-id] refreshed:** Each step carries the submit_plan call id as providerCallId; null only for zero-call tiers, per SEAM-06 as shipped in P12 CommitCoordinator.submit(step, providerCallId: String?). Each step ExecutedAction MUST keep a distinct ordinal/position. That is BINDING from SB (R-v1.1-CONSUMER-ANSWERS row 4): SB keys undo by runId + ordinal, not providerCallId, so the SB 178 undo-grouping question is closed.
- **[builder] refreshed:** Minimal Builder that reuses P12 internal OutcomeHooks + decideResult (core/strategy/singleshot/SingleShotOutcomes.kt:17/27, internal, so same-module reuse is fine; move them to a shared strategy file only if PlanThenExecute is not in the singleshot package, and do it without touching P14 StepSubmission.kt functions). A truncated or unparseable plan returns StrategyOutcome.Escalate(EscalationReason.MalformedExtraction()), the same shape as SingleShotStrategy.kt:138, so AgenticLoop can still take it rather than failing the command.

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass. (Only D-04 is `source: human` and is locked.)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLAN-01 | `PlanThenExecuteStrategy` makes one model call that returns a plan of steps over the app's `ToolExecutor`, then runs the steps in order through the gate. | Request shape (§Architecture, Pattern 1), per-step `session.submit` (Pattern 3), `ToolExecutor.prepare` seam, one gate proposal per step. |
| PLAN-02 | a later step can reference an earlier step's write output (`ExecutedAction.targetIds`). A binding that doesn't resolve fails that step. | Binding grammar and JsonObject-space resolver (Pattern 2); `targetIds` provenance in `ApplyStep.kt:111`; committed-only environment. |
| PLAN-03 | a failed step triggers at most one replan call, then `Escalate`. A step that needs a lookup result escalates instead of planning (contract §4). | Replan rule (single predicate, Pattern 4), needs-lookup escape (Pattern 1), prefix-identical continuation, call accounting via `TierAttempt.turns`. |
| PLAN-04 | after the first commit, no escalation happens (`escalation_suppressed` → partial `Completed`). A Plan-specific test proves it. | `TierWalk.kt:87-88,112,118-122` (verified); Plan returns `Escalate` for every post-work stop; test design in Validation Architecture. |
| PLAN-05 | `PlanThenExecuteStrategy.Builder.onFailed` has the same hook as SEAM-01. | Reuse `OutcomeHooks`/`decideResult` in place (`SingleShotOutcomes.kt:17-32`); parity test matrix. |
</phase_requirements>

## Summary

PlanThenExecute is a new `CommandStrategy` in `:core` (`core.strategy.plan`). It builds one forced `submit_plan` request over the app's tools, validates the whole returned plan with zero side effects, then runs the steps strictly one at a time: bind arguments (JsonObject space), `ToolExecutor.prepare`, `session.submit(step, planCallId)`. Every step is its own gate proposal, so the app gate sees each write separately and a later step can bind to an earlier step's real `targetIds`. All the machinery it needs already exists and was read this session: `session.submit(step, providerCallId)` returns a `DispatchResult` carrying the recorded `ExecutedAction`s; `ApplyStep` merges the mutation's and the result's `targetIds`; `TierWalk` converts any `Escalate` into a partial `Completed` + `escalation_suppressed` once the run applied or held anything.

Three findings change how the plan should be built versus the decision text and the master notes. **(1)** `submitSteps`/`resolutionOutcome` (Phase 14's `StepSubmission.kt`) cannot be called by Plan: `submitSteps` returns a `StrategyOutcome.Completed` and discards the `DispatchResult`, but binding needs the per-step `ExecutedAction.targetIds` and hold/error status. Plan calls `session.submit(step, callId)` directly, exactly like `AgenticDispatch.settle`. Nothing in `StepSubmission.kt` is edited. What Plan does reuse from earlier phases: `OutcomeHooks`/`decideResult` (imported in place), `ceilingReached`/`ceilingCrossed`, `UserTurnRenderer`, `ToolSpecProvider`, `ToolExecutor`, and (after a small pure extraction) the guarded-prepare logic of `AgenticDispatch`. **(2)** `decideResult` maps a `MAX_TOKENS` stop to `Failed(MaxTokens)` (`StrategyLimits.kt:39-45` via `SingleShotOutcomes.kt:45`), which contradicts D-09's "truncated plan → MalformedExtraction → escalate". Plan must intercept `StopReason.MAX_TOKENS` before calling `decideResult`. **(3)** The repo's mechanical gates constrain naming: `NoHardCodedConstantsTest` bans `const val` names matching `^(DEFAULT_|MIN_|MAX_)|TOKEN|ITERATION|CEILING` outside four owner files, so the `maxSteps` default and the "1 replan" constant need other names; `$` in Kotlin string literals must be escaped; no public enum/data/sealed; two planning-id patterns are banned in comments.

The D-06/D-07/SC3/SC4 tension resolves into one predicate: **a replan is permitted only while nothing has been applied or held, at most once, and only after `ceilingReached`**. Static rejections (all happen before step 1) and a first-step failure satisfy it; a "dynamic" binding miss is always at step ≥ 2 and so always follows a commit, which makes D-06 a corollary rather than a separate rule. Every stop after work has been done returns `Escalate`, never `Failed`, so SC4 holds.

**Primary recommendation:** Build `strategy/plan/` as five small internal files plus the public `PlanThenExecuteStrategy` (mirror of `SingleShotStrategy`), after one pure-move commit that extracts the guarded `prepare` out of `AgenticDispatch.kt`; resolve bindings only for whole-value strings that are exactly `$<id>.<key>` with a letter-leading declared id; replan exactly once by continuing the conversation with the identical system+tools+first user message and a `ToolResultsMessage` digest; prove the byte-identical prefix at wire level in `:providers`; and put the D-04 live probe in an opt-in `*Live*` Gradle task (no device, no TESTER window).

## Architectural Responsibility Map

This is a library, not a multi-tier app; "tiers" here are the engine's own layers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Plan request/continuation building, plan parse + static validation, binding resolution, step loop, outcome mapping | `:core` (`strategy/plan/`) | — | Pure Kotlin, no HTTP (L7/A7). Provider-neutral `ModelRequest`/`Message` only. |
| Gate decision per step | App (`PreApplyGate`) via `CommitCoordinator` | `:core` | Engine never decides; Plan only calls `session.submit`. |
| Step preparation (arg validation, `PendingMutation` creation) | App (`ToolExecutor`) | `:core` guards faults | Engine passes arguments through untouched; guards executor faults. |
| Binding keys (`targetIds` names) | App | — | D-05: keys are app-owned; engine never inspects names. App tool descriptions must tell the model which keys each write returns. |
| "No escalation after work" | `:core` pipeline (`TierWalk`) | Plan returns `Escalate` | Already implemented; Plan must not return `Failed` after work. |
| Wire encoding of the continuation (tool_use / tool_result pairs, cache breakpoint) | `:providers` | — | Already done by the mappers; Plan only supplies neutral messages. Prefix byte test lives here. |
| Live binding-syntax probe | `:providers` test source set (opt-in task) | — | Needs real HTTP; `:core` has none. No Android device involved. |
| API documentation rows | repo docs (`API.md`) | Phase 19 owns full DOC-02 | C20 gate requires the new public type be named. |

## Standard Stack

No new libraries. Everything is already on the classpath. [VERIFIED: CLAUDE.md Technology Stack; `core/build.gradle.kts` dependencies block read this session: `api(libs.coroutines.core)`, `api(libs.serialization.json)`, JUnit 4, coroutines-test]

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx.serialization-json | 1.11.0 | `JsonObject` tree walk for bindings, schema building | Already the argument type of `ToolCall.arguments` and `Extraction.arguments`. |
| kotlinx.coroutines-core | 1.11.0 | `suspend` strategy | Pure `:core` rule: `-core` only. |

### Supporting (test only)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 4 | 4.13.2 | tests | all |
| kotlinx-coroutines-test | 1.11.0 | `runTest` | all `:core` strategy tests |
| `com.squareup.okhttp3:mockwebserver` (legacy `okhttp3.mockwebserver`) | tracks matrix leg | wire-level prefix test in `:providers` | only the byte-prefix test and the live probe harness |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ToolExecutor` for plan steps | `OutcomeResolver` (SingleShot's seam) | Resolver returns `Resolution.Steps` with a combined mutation and cannot expose per-step `targetIds`; PLAN-01 and ROADMAP SC1 say `ToolExecutor`; SB can reuse one executor for Agentic and Plan. Use `ToolExecutor`. |
| `{"$ref":{...}}` structured refs | whole-value string | Locked by D-04 (human). |

**Installation:** none.

## Package Legitimacy Audit

No external packages are added in this phase (`gsd_run query package-legitimacy check` not applicable).

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Verified Code Anchors (every integration point)

All values below were read from the file this session. Quotes are verbatim.

| # | Integration point | Anchor | Verbatim quote |
|---|-------------------|--------|----------------|
| 1 | Escalate after work is suppressed | `TierWalk.kt:87-88` | `is StrategyOutcome.Escalate ->` / `if (hasWorked()) suppressed(strategy, outcome.reason) else handUp(strategy, outcome)` |
| 2 | What "worked" means | `TierWalk.kt:112` | `private fun hasWorked(): Boolean = coordinator.appliedCount + coordinator.heldCount > 0` |
| 3 | Suppressed result | `TierWalk.kt:118-122`, `:19` | `return CommandOutcome.Completed(effects(), reply = null, terminalCall = null, partial = true)`; `private const val ATTEMPT_SUPPRESSED = "escalation_suppressed"` |
| 4 | Not-worked Escalate carries forward | `TierWalk.kt:94-99` | `carry = outcome.carry` / `lastReason = outcome.reason` |
| 5 | The one write path with call id (internal) | `CommandSession.kt:41` | `internal abstract suspend fun submit(step: ToolStep, providerCallId: String?): DispatchResult` |
| 6 | Session delegates to coordinator | `RunSession.kt:61-62` | `override suspend fun submit(step: ToolStep, providerCallId: String?): DispatchResult =` / `scope.coordinator.submit(step, providerCallId)` |
| 7 | targetIds merge (result wins) | `ApplyStep.kt:111` | `targetIds = facts.targetIds + (result?.targetIds ?: emptyMap()),` |
| 8 | Error vs committed kind (both `applied = true`) | `ApplyStep.kt:107,115-116` | `val failed = result == null \|\| result.isError`; `val kind = if (failed) ActionKind.IS_ERROR else ActionKind.COMMITTED`; `val action = ledger.record(kind, applied = true, details = details)` |
| 9 | Positions are ledger-assigned, rising | `ActionLedger.kt:44` | `position = actions.size,` |
| 10 | Hold result | `CommitCoordinator.kt:155` | `return DispatchResult(heldForConfirmationContent(), false, true, actions)` |
| 11 | Finished steps: preview/error recorded, read not | `CommitCoordinator.kt:106-111` | `FinishedKind.PREVIEW -> ActionKind.PREVIEW` / `FinishedKind.ERROR -> ActionKind.IS_ERROR` / `if (kind == null) return DispatchResult(result.contentForModel, result.isError, false, emptyList())` |
| 12 | DispatchResult shape | `ToolStep.kt:111-116` | `public class DispatchResult internal constructor(` `public val contentForModel: String,` `public val isError: Boolean,` `public val held: Boolean,` `public val actions: List<ExecutedAction>,` |
| 13 | ExecutedAction fields | `CommitSink.kt` (class `ExecutedAction`) | `position`, `kind`, `applied`, `appOutcomeToken`, `toolName`, `targetIds: Map<String, String>`, `context`, `providerCallId`, `mutating` |
| 14 | ActionKind values | `ActionKind.kt:18-27` | `COMMITTED = ActionKind("committed")`, `HELD = ActionKind("held")`, `PREVIEW = ActionKind("preview")`, `IS_ERROR = ActionKind("is_error")` |
| 15 | SingleShot request shape to mirror | `SingleShotStrategy.kt:102-111` | `ModelRequest(` `attempt.snapshot.system,` `listOf(UserMessage(userTurn.render(context))),` `attempt.snapshot.tools,` `attempt.choice,` `attempt.session.policy.maxTokensPerTurn,` `CacheDirective(true),` `true,` `reasoning,` `)` |
| 16 | Refused model handle fails, not onFailed | `SingleShotStrategy.kt:94` | `return model.refusal?.let { StrategyOutcome.Failed(it) } ?: ask(attempt, model)` |
| 17 | Hooks bundle | `SingleShotStrategy.kt:75`; `SingleShotOutcomes.kt:17-21` | `private val hooks = OutcomeHooks(settings.onNoToolCall, settings.onRefusal, settings.onFailed)`; `internal class OutcomeHooks(` `val onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome,` `val onRefusal: ...,` `val onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome,` |
| 18 | decideResult (internal, same-module importable) | `SingleShotOutcomes.kt:27,42-47` | `internal suspend fun decideResult(result: ModelResult, hooks: OutcomeHooks): StrategyOutcome? =`; `StopReason.REFUSAL -> hooks.onRefusal(response)` / `else -> stopFailure(response.stopReason)` |
| 19 | MAX_TOKENS becomes Failed (conflicts with D-09) | `StrategyLimits.kt:39-45` | `internal fun stopFailure(stopReason: StopReason): StrategyOutcome? =` ... `StopReason.MAX_TOKENS -> StrategyOutcome.Failed(FailureReason.MaxTokens())` |
| 20 | Budget helpers | `StrategyLimits.kt:24,28` | `internal fun ceilingReached(session: CommandSession): StrategyOutcome? =` / `internal fun ceilingCrossed(session: CommandSession): StrategyOutcome? =` (Failed(BudgetExceeded(TOKENS))) |
| 21 | Unknown tool in a SingleShot answer | `SingleShotStrategy.kt:138` | `tool == null -> StrategyOutcome.Escalate(EscalationReason.MalformedExtraction())` |
| 22 | Phase 14 helpers (do not edit) | `StepSubmission.kt:9,29-34` | `internal suspend fun resolutionOutcome(`; `internal suspend fun submitSteps(` `session: CommandSession,` `steps: Resolution.Steps,` `providerCallId: String?,` `): StrategyOutcome {` — returns `StrategyOutcome.Completed(...)`, so the `DispatchResult` is discarded |
| 23 | Agentic prepare + fault step (to extract) | `AgenticDispatch.kt:127-136` | `guarded(onFault = {` `context.session.recordCode(TraceCode.TOOL_PREPARE_ERROR)` `faultStep(spec)` `}) { context.executor.prepare(Extraction(call.name, call.arguments, call.id), context.input) }`; `val kind = if (spec.mutating) FinishedKind.ERROR else FinishedKind.READ`; `private const val TOOL_ERROR_CONTENT = """{"status":"error","reason":"tool_error"}"""` (`:21`) |
| 24 | Agentic's settle = the submit pattern Plan copies | `AgenticDispatch.kt:98-102` | `val step = guardWrites(context, spec, prepare(context, spec, call))` / `val dispatch = context.session.submit(step, call.id)` |
| 25 | Continuation history shape | `AgenticLoopStrategy.kt:225-226` | `history.add(response.message)` / `history.add(ToolResultsMessage(turn.results))` |
| 26 | Tool result shape | `Message.kt` (`ToolResult`, `ToolResultsMessage`) | `public class ToolResult(` `public val callId: String,` `public val content: String,` `public val isError: Boolean,`; `require(this.results.isNotEmpty()) { "a tool result batch must not be empty" }`; `require(ids.toSet().size == ids.size) { "a tool result batch must not repeat a call id" }` |
| 27 | Request invariants | `ModelRequest.kt:76-79` | `require(names.toSet().size == names.size) { "tool names must be distinct" }`; `"a required tool choice must name a tool in the request"` |
| 28 | Per-model turn recording (call count) | `BoundModel.kt:108`; `CommandTrace.kt:66` | `recorder.turnRecorded(strategy, turnOf(result, recorder.runClock.read() - started))`; `public val turns: List<TurnRecord> = turns.toList()` |
| 29 | Open escalation reason | `EscalationReason.kt:55` | `public class Other(override val code: String) : EscalationReason {` (init requires non-blank) |
| 30 | Reusable trace codes | `TraceCode.kt:105,111,114` | `EXTRA_TOOL_CALLS_DROPPED = TraceCode("extra_tool_calls_dropped")`, `UNKNOWN_TOOL = TraceCode("unknown_tool")`, `TOOL_PREPARE_ERROR = TraceCode("tool_prepare_error")` |
| 31 | Banned limit-constant names | `NoHardCodedConstantsTest.kt:35,19` | `private val limitName = Regex("^(DEFAULT_\|MIN_\|MAX_)\|TOKEN\|ITERATION\|CEILING")`; `setOf("TierPolicy.kt", "ModelCapabilities.kt", "AwaitingConfirmGate.kt", "ToolSpec.kt")` |
| 32 | Banned planning ids in comments | `invariants.gradle.kts:31`; `detekt.yml` ForbiddenComment | `Regex("""\b(T-\d+-\d+\|WR-\d+\|Phase\s+\d+\s+D-\d+)\b""")` |
| 33 | Forced-tool reshape (model rejects forced choice) | `AnthropicEncoder.kt:66,98-109,111-115` | `put("messages", encodeMessages(call).withInstruction(requiredToolInstruction(call).takeIf { reshape }))`; `"Call the ${it.toolName} tool with your result."` appended to the LAST user-role message |
| 34 | Live tests excluded from check | `providers/build.gradle.kts:65,68-86` | `filter { excludeTestsMatching("*Live*") }`; task `liveAnthropicCapture`: `val optIn = providers.environmentVariable("VAE_LIVE_ANTHROPIC")` / `onlyIf { optIn.orNull == "1" }` |

## Architecture Patterns

### System Architecture Diagram

```
CommandInput ──▶ TierWalk ──▶ PlanThenExecuteStrategy.execute(input, session)
                                   │
        ceilingReached(session) ───┤ (Failed BudgetExceeded)
                                   ▼
                 tooling.tooling(input) ──▶ ToolingSnapshot(system, tools)
                                   │   name clash "submit_plan" / no non-terminal tool ─▶ Failed(Other(..)) [no call]
                                   ▼
                 session.model() ── refusal ─▶ Failed(refusal)
                                   ▼
        ┌─ request#1 = [system, tools=(submit_plan + app tools), userTurn text(rendered ONCE),
        │               Required(submit_plan), maxTokensPerTurn, CacheDirective(true), single=true, reasoning]
        ▼
   model.complete ──▶ MAX_TOKENS? ──▶ Escalate(MalformedExtraction)            (D-09, pre-decideResult intercept)
        │            decideResult(result, hooks): provider failure ─▶ hooks.onFailed (PLAN-05)
        │                                         refusal/no tool call ─▶ hooks (defaults Failed / Escalate NoToolCall)
        ▼
   ceilingCrossed(session) ─▶ Failed(BudgetExceeded)
        ▼
   parsePlan(calls): first call must be submit_plan
        ├─ needs_lookup=true  OR  any step names a known READ tool ─▶ Escalate(Other("plan_needs_lookup"), session.carry)  [no replan, zero side effects]
        ├─ malformed / unknown or terminal tool / dup or bad id / empty / > maxSteps / forward-self-unknown ref ─▶ REJECTED
        └─ valid ParsedPlan
        ▼
   REJECTED ─▶ replan allowed? (replans==0 && !worked && ceilingReached==null)
        │          yes ─▶ request#2 = request#1 messages + [assistant(response#1.message), ToolResultsMessage(digest for EVERY call id)]
        │                  ─▶ model.complete ─▶ (same MAX_TOKENS / decideResult / ceilingCrossed / parse path; second REJECTED ─▶ Escalate(MalformedExtraction))
        │          no  ─▶ Escalate(MalformedExtraction, session.carry)
        ▼
   step loop (strictly sequential, plan order):
        bind(args, env) ── unresolved ─▶ stop: BindingUnresolved            (dynamic)
        prepareGuarded(executor.prepare(Extraction(tool, boundArgs, planCallId))) ── fault ─▶ Finished(ERROR)
        session.submit(step, planCallId) ──▶ gate ─▶ apply ─▶ sink ─▶ DispatchResult(actions)
             held ─▶ worked=true; stop: Held(last?)
             all actions COMMITTED ─▶ env[stepId] = merged targetIds (conflicting key dropped)
             else (IS_ERROR / PREVIEW / READ / gate fault) ─▶ worked ||= actions.any{applied}; stop: StepFailed
        ▼
   outcome map
     all steps committed                         ─▶ Completed(reply=null, partial=extrasDropped)
     last step held                              ─▶ Completed(null)   [A1: held reported via outcome.held]
     held, steps remain                          ─▶ Escalate(Other("plan_step_held"))      ─▶ TierWalk: suppressed partial
     BindingUnresolved                           ─▶ Escalate(Other("plan_binding_unresolved"))
     StepFailed, !worked, replans==0, budget ok  ─▶ replan (above)
     StepFailed otherwise                        ─▶ Escalate(Other("plan_step_failed"))
        ▼
   TierWalk: Escalate + hasWorked ─▶ escalation_suppressed, Completed(partial=true), no later tier
             Escalate + !hasWorked ─▶ handUp(carry) ─▶ next tier (Agentic)
```

### Recommended Project Structure
```
core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/
├── PreparedStep.kt              # NEW (pure extraction): prepareGuarded(session, spec, executor, input, extraction) + TOOL_ERROR_CONTENT
├── agentic/AgenticDispatch.kt   # EDIT (move only): prepare() delegates to prepareGuarded; behavior byte-identical
└── plan/                        # NEW package core.strategy.plan
    ├── PlanThenExecuteStrategy.kt   # public class, Builder, companion invoke, execute() skeleton, toString
    ├── PlanSchema.kt                # submitPlanSpec(snapshotTools, maxSteps): ToolSpec (byte-stable)
    ├── PlanParse.kt                 # parsePlan(calls, snapshot, maxSteps) -> PlanVerdict (internal; never throws)
    ├── PlanBinding.kt               # collectRefs / bindArguments / mergeTargets (pure JsonObject functions)
    ├── PlanRun.kt                   # step loop -> RunStop (internal class holding env + worked)
    └── PlanReplan.kt                # replan messages + failure digest (internal)
core/src/test/kotlin/.../core/       # PlanThenExecute*Test.kt, PlanBindingTest.kt, PlanSchemaTest.kt, PlanTestSupport.kt
providers/src/test/kotlin/.../providers/PlanThenExecuteWireTest.kt           # byte-prefix proof (check)
providers/src/test/kotlin/.../providers/PlanBindingLiveProbeTest.kt          # opt-in, excluded from check by *Live*
providers/build.gradle.kts                                                    # + livePlanProbe task (mirror liveAnthropicCapture)
```
`internal` is module-wide, so `strategy.plan` imports `OutcomeHooks`/`decideResult` from `strategy.singleshot` in place; no file in `singleshot/` or `StepSubmission.kt` is touched. Moving them is optional and not recommended (extra diff, no benefit). [VERIFIED: anchor 17-18]

### Pattern 1: The planning request and the `submit_plan` schema (D-01, D-02, D-10)

**What:** One `ModelRequest` identical in shape to SingleShot's (anchor 15) except `tools = [submit_plan] + snapshot.tools` and `toolChoice = ToolChoice.Required("submit_plan")`. `submit_plan` is engine-owned: `mutating=false, terminal=false, strict=false` (`ToolSpec` has a `strict: Boolean?` where false asks the provider not to be strict). Place it first in the tools list so the app's block order is untouched after it; the whole list is built once from the snapshot and reused for the replan so bytes are stable.

**Schema (non-strict, key order is part of the cached prefix, so build it with `buildJsonObject` in one fixed order):**
```json
{ "type":"object",
  "properties":{
    "steps":{"type":"array","minItems":1,"maxItems":<maxSteps>,
      "items":{"type":"object",
        "properties":{
          "id":{"type":"string"},
          "tool":{"type":"string","enum":[<non-terminal snapshot tool names, snapshot order>]},
          "arguments":{"type":"object"}},
        "required":["id","tool","arguments"]}},
    "needs_lookup":{"type":"boolean"}},
  "required":["steps"] }
```
- The enum holds every non-terminal tool name (mutating AND read), per D-10. Terminal tools (e.g. an ask-the-user tool) are left out of the enum but stay in the request tools list (the model still "sees" them; it just cannot put them in a step).
- `needs_lookup` is the in-schema escape (the model is forced to call `submit_plan`, so the escape cannot be a separate tool). `{"needs_lookup":true,"steps":[]}` is a valid escape.
- No reply field (D-02): a clean plan ends `Completed(reply = null)`.
- Name guard: if `snapshot.tools` already contains `submit_plan`, `ModelRequest` would throw (anchor 27) and `TierWalk` would collapse it into `Failed(Unexpected)`. Check first and return `Failed(FailureReason.Other("plan_tool_name_taken"))` before any call (same pattern as `TOOL_MISSING_CODE`, `SingleShotStrategy.kt:37`). Same for a snapshot with no non-terminal tool (empty enum): `Failed(Other("plan_no_tools"))`. [ASSUMED: code names, A10]
- Description text (frozen with the tag, domain-free, `$` escaped in Kotlin as `\$`): states the whole-value reference rule, "never inside a longer string", "only to a step listed earlier", "key names are given by the earlier tool's description", "do not call any other tool", "set needs_lookup true and leave steps empty when the command needs information you do not have". The exact words are what the D-04 probe validates (see Live Probe).

**Wire note:** with a forced `tool_choice`, models that reject it go through the reshape path (anchor 33): tool_choice becomes `auto` and an instruction line is appended to the last user-role message. In that mode the model may answer in prose: `decideResult` then yields `Escalate(NoToolCall)` via the default hook, which is correct. [VERIFIED: AnthropicEncoder.kt:98-115]

### Pattern 2: Binding grammar and JsonObject-space resolution (D-04, D-05)

**Grammar (frozen at the tag; recommended exact form, A3):**
- A *reference* is a `JsonPrimitive` that `isString` and whose whole content matches `^\$([A-Za-z][A-Za-z0-9_-]*)\.(\S+)$`. Group 1 = step id, group 2 = key (everything after the first `.`, no whitespace; keys are app-owned, so dots inside a key are allowed).
- Step ids declared in the plan must match `^[A-Za-z][A-Za-z0-9_-]*$` and be unique; otherwise the plan is rejected.
- The leading-letter rule is what keeps dictated literals like `"$5.00"` (digit after `$`) plain strings. A string that matches the reference shape but names an id that is not declared earlier (unknown, forward, self) is a **static rejection**, not a literal. Residual collision (a dictated string like `"$abc.def"`) is accepted and documented. An escape (`"$$..."`) can be added later without breaking anything, because `$$x.y` does not match the grammar today. [ASSUMED, A3]
- No interpolation: `"note for $a.id"` is a literal. Substitution replaces the whole primitive with `JsonPrimitive(<string value>)`; arrays and nested objects are walked recursively; object **keys** are never rewritten; non-string primitives are never touched.

**Environment:** `env: MutableMap<String, Map<String,String>>`, written only after a step whose `DispatchResult.actions` is non-empty and all `ActionKind.COMMITTED`. The value is the union of those actions' `targetIds`; a key that appears with two different values is dropped (unresolved). `targetIds` are `mutation.targetIds + result.targetIds` with the result winning (anchor 7), so apps can publish keys from either the pending mutation (before apply) or the step result (after apply).

**Dynamic miss** (referenced step committed but the key is absent or conflicted) → `BindingUnresolved` (no substitution is guessed). A type mismatch (a bound string given to an integer parameter) is not an engine concern: the app's executor validates and returns an error step, which is an ordinary `StepFailed`.

```kotlin
// Sketch (internal, pure, never throws). Types and imports are the kotlinx.serialization.json ones already used by ToolSpec.kt.
private val REF = Regex("""^\$([A-Za-z][A-Za-z0-9_-]*)\.(\S+)$""")

internal class Ref(val stepId: String, val key: String)

internal fun refOf(element: JsonElement): Ref? =
    (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { REF.matchEntire(it) }
        ?.let { Ref(it.groupValues[1], it.groupValues[2]) }

/** All references in [element], depth-first; static validation walks this. */
internal fun refsIn(element: JsonElement): List<Ref> = when (element) {
    is JsonObject -> element.values.flatMap { refsIn(it) }
    is JsonArray -> element.flatMap { refsIn(it) }
    else -> listOfNotNull(refOf(element))
}

/** Returns the bound arguments, or null when any reference cannot be resolved from [env]. */
internal fun bind(arguments: JsonObject, env: Map<String, Map<String, String>>): JsonObject? = bindElement(arguments, env) as? JsonObject

private fun bindElement(element: JsonElement, env: Map<String, Map<String, String>>): JsonElement? = when (element) {
    is JsonObject -> element.mapValues { (_, v) -> bindElement(v, env) ?: return null }.let(::JsonObject)
    is JsonArray -> element.map { bindElement(it, env) ?: return null }.let(::JsonArray)
    else -> refOf(element)?.let { ref -> env[ref.stepId]?.get(ref.key)?.let(::JsonPrimitive) } ?: element.takeIf { refOf(it) == null }
}
```
(The `$` in `"""..."""` raw strings and in the `\$` of the pattern must be checked against the Kotlin template rules; see Pitfall 3.)

### Pattern 3: Per-step submit (PLAN-01) and the success predicate

```kotlin
// One gate proposal per step. The call id is the planning call's id (D-03); position (ledger-assigned) is the step identity.
val prepared = prepareGuarded(session, spec, executor, input, Extraction(step.tool, bound, planCall.id))
val dispatch = session.submit(prepared, planCall.id)
```
Classification of a `DispatchResult` (anchors 10-12, 14):
- `dispatch.held` → `Held` (nothing applied for that step; `worked = true`, matching `heldCount > 0`).
- `dispatch.actions.isNotEmpty() && dispatch.actions.all { it.kind == ActionKind.COMMITTED }` → committed; update `env`.
- anything else → `StepFailed`: `IS_ERROR` (applied or a gate fault), `PREVIEW` (not applied), or no action at all (a `Finished(READ)` from a mutating tool). `worked ||= dispatch.actions.any { it.applied }`.

Do NOT use `submitSteps` here (anchor 22): it combines mutations and returns only `Completed`. Do NOT combine a step's mutations with other steps' (that makes write-output binding impossible; ARCHITECTURE §4.3). A step's own `ToolStep.Mutation(listOf(...))` still arrives as one gate proposal, which is the app's choice.

`prepareGuarded` extraction (commit 1, pure move, Agentic suites must stay green): move the body of `AgenticDispatch.prepare` + `faultStep` + `TOOL_ERROR_CONTENT` into `strategy/PreparedStep.kt` with parameters `(session, spec, executor, input, extraction)`; `AgenticDispatch.prepare` becomes a one-line delegate. Plan's validation guarantees every executed step's tool is mutating, so `guardWrites` (the non-mutating branch) is dead code for Plan and stays Agentic-only. Phase 14's SUMMARY explicitly left this extraction to Phase 15. [VERIFIED: 14-01-SUMMARY.md:139; anchors 23-24]

### Pattern 4: The single replan predicate and the outcome table (D-06, D-07, D-08, D-12)

```
canReplan = replansUsed == 0 && !worked && ceilingReached(session) == null
```
`replansUsed` is a plain local counter; the limit of 1 is a `private const val` named so it does not match the banned regex (e.g. `REPLAN_LIMIT`, not `MAX_REPLANS`). The decision says "hard-coded 1": no Builder knob, no `TierPolicy` field.

| Situation | `!worked` (nothing applied/held) | `worked` |
|---|---|---|
| Provider failure on plan call | `onFailed` hook (default `Failed(reason, details)`) | n/a (no model call after work) |
| Refusal / no tool call | hooks (defaults `Failed(Refusal)` / `Escalate(NoToolCall)`) | n/a |
| `MAX_TOKENS` (truncated) | `Escalate(MalformedExtraction)`, no replan (A5) | n/a |
| `needs_lookup` or read tool in plan | `Escalate(Other("plan_needs_lookup"), carry)`; 1 call, 0 executor calls, 0 gate calls | n/a |
| Static rejection | replan once, else `Escalate(MalformedExtraction, carry)` | n/a (rejection precedes step 1) |
| Step failed (error/preview/gate fault) | replan once, else `Escalate(Other("plan_step_failed"))` | `Escalate(Other("plan_step_failed"))` → suppressed |
| Binding unresolved (dynamic) | (unreachable, see below) | `Escalate(Other("plan_binding_unresolved"))` → suppressed |
| Hold on a non-last step | n/a | `Escalate(Other("plan_step_held"))` → suppressed |
| Hold on the last step | n/a | `Completed(null)` (A1) |
| Ceiling crossed after a call | `Failed(BudgetExceeded(TOKENS))` | n/a |

Why dynamic misses never replan: a binding at step `k` is only evaluated after steps `0..k-1` all committed, and a reference to a non-earlier step is a static rejection, so `k >= 1` and `worked` is already true. That makes D-06 ("dynamic → no replan") and the unified predicate identical in every reachable state. [VERIFIED by reasoning over anchors 7,8,12 and the step-loop ordering]

Never return `Failed` once `worked` is true (SC4): `Failed` ends the command as `CommandOutcome.Failed`, not a partial `Completed` (anchor 1 shows only `Escalate`/`NoMatch` are suppressed). The only post-work exits are `Escalate` (suppressed by `TierWalk`) and `Completed`.

Replan = whole-plan replacement: because a replan is only allowed before anything applied, the new plan is the full plan and the binding environment starts empty. (FEATURES.md's "replan covers only the remaining tail, binding env persists" does not apply under D-06/D-07; do not build it.)

### Pattern 5: Replan request and the prefix-identity contract (D-08)

Request #2 = the same `system`, `tools` (same `ModelRequest.tools` list instance or equal contents), `toolChoice`, `maxTokens`, `cache`, `singleToolCall`, `reasoning` as request #1, with `messages = [firstUser, response1.message, ToolResultsMessage(results)]`:
- `firstUser` is the **same** `UserMessage` object from request #1. Do not call `userTurn.render` again: `StandardUserTurnRenderer` embeds `ZonedDateTime.now(clock)` to the second, so re-rendering can change the text and break the cache and the byte test. (`SingleShotStrategy.kt:101` shows the render happens per request; Plan renders once per command.)
- `response1.message` is passed unchanged (keeps `nativeReplay`; the bound model is frozen per tier so provider+model match, `Message.kt` `AssistantMessage.nativeFor`). Anthropic and the chat mappers need every tool call of that turn answered, so `ToolResultsMessage.results` has one `ToolResult` per call id in the turn, in call order: the plan call gets the digest with `isError = true`; every other call (extras, a direct app-tool call) gets a fixed `{"status":"error","reason":"ignored_call"}`. `ToolResultsMessage` requires non-empty distinct ids (anchor 26). [ASSUMED for the provider-side "every tool_use needs a tool_result" rule; the mappers' own tests (`AgenticLoopWireTest.anthropicParallelToolUseIsAnsweredInOneMessageInOrder`) show Agentic answers every call, so answer every call.]
- Digest = engine-owned compact JSON, fixed vocabulary, no app values: `{"status":"plan_rejected","reason":"<code>","step":"<id|null>"}` where `<code>` ∈ `malformed|unknown_tool|duplicate_id|bad_reference|too_many_steps|step_failed|empty`. Includes the model's own step id and nothing from app results (A4). Counts and codes only reach the trace.

**What "byte-identical prefix" means precisely.** On every provider the cached static prefix is tools + system. The Anthropic body orders `tools`, `tool_choice`, `system`, then `messages`, and the cache breakpoint sits on the last tool or on system (`AnthropicEncoder.kt:61-66`, `encodeTools`/`encodeSystem`). The existing wire test already asserts exactly this for Agentic (`anthropicSystemAndToolsBytesAreIdenticalOnEveryTurn`, `AgenticLoopWireTest.kt:246`, using `cachedPrefix`). Copy that for Plan. Do **not** assert byte-equality of the whole first user message at wire level in reshape mode: there the encoder appends the "Call the submit_plan tool..." line to the last user-role message, which is `messages[0]` in request #1 but the tool-results message in request #2 (anchor 33). Assert `messages[0]` equality at the neutral level in `:core` instead (same `UserMessage` text), and the tools+system bytes at wire level.

### Pattern 6: Builder surface and API additivity (PLAN-05, D-09, D-12)

Mirror `SingleShotStrategy` exactly: `public class PlanThenExecuteStrategy internal constructor(override val id: StrategyId, settings: Builder) : CommandStrategy`, `public class Builder internal constructor()`, `public companion object { public operator fun invoke(id: StrategyId, block: Builder.() -> Unit) }`. Public vars (all with Kotlin property defaults, which do not create constructor stubs because the Builder constructor is internal):

| Property | Type / default | Notes |
|---|---|---|
| `tooling` | `ToolSpecProvider?` required | `requireNotNull` message names the setting, like `SingleShotStrategy.kt:65-67` |
| `executor` | `ToolExecutor?` required | |
| `capabilities` | `StrategyCapabilities.ANY_PROVIDER` | |
| `userTurn` | `UserTurnRenderer.standard()` | |
| `clock` | `CurrentZoneClock` | |
| `reasoning` | `ReasoningMode.OFF` | applied to every call |
| `maxSteps` | `Int`, default 8 (D-12 "~8") | `require(maxSteps >= 1)` at construction; named constant must not match the banned regex (Pitfall 4) |
| `onFailed` | `suspend (FailureReason, FailureDetails?) -> StrategyOutcome = { r, d -> Failed(r, d) }` | KDoc mirrors SingleShot's `onFailed` paragraph (anchor in `SingleShotStrategy.kt:210-224`), including the on-device branch warning |

`onNoToolCall`/`onRefusal` stay internal defaults (decision-map [builder] option 1): `{ Escalate(NoToolCall()) }` and `{ Failed(Refusal()) }`, passed into `OutcomeHooks(...)`. `toString()` prints id and `maxSteps` only.

**Additive-only (§11):** a new public class in a new package is purely additive, so `:core:metalavaCheckCompatibility` against the committed v1.0.1 `core/api.txt` stays green; `api.txt` is **not** regenerated this phase (it equals the last tag; Phase 14 followed the same rule: `14-VALIDATION.md` sampling "plus `:core:metalavaCheckCompatibility` when public API changed"). [VERIFIED: `git ls-files` shows `core/api.txt` tracked, tags `v1.0.0`,`v1.0.1`]. Repo gates to satisfy (from `scripts/review-api-surface.sh`, header lines 5-12): no new public `sealed` type, no `copy(`/`componentN(` (no `data`), no enum, no public static field (so no public `const val`; keep constants `private`/`internal`). The Builder's vars compile to instance getters/setters, which is the same shape as `SingleShotStrategy.Builder`.

If new `TraceCode`s are added (recommended minimal set, A7: `plan_replanned`, `plan_rejected`, `plan_binding_unresolved`) each needs a row in `TraceTest`'s hand list (Phase 14 hit this: `TraceTest.kt:238-243` lists the grammar codes) and a sentence in `API.md` Telemetry. Reuse `UNKNOWN_TOOL` (a step names a tool the snapshot did not offer), `TOOL_PREPARE_ERROR` (executor fault) and `EXTRA_TOOL_CALLS_DROPPED` instead of inventing duplicates.

`API.md`: add `PlanThenExecuteStrategy` to the package table (`core.strategy.plan`) and a type row (C20 `check_C20` greps for the backticked type name), plus a Strategies bullet. Avoid the words "food"/"card" and `<word>_note` tool names (C21). INTEGRATION.md/README prose and doc-snippet regions are Phase 19 (DOC-02); do not add a required doc-snippet region here.

### Anti-Patterns to Avoid
- **Re-rendering the user turn for the replan.** Breaks prefix identity (clock-dependent text).
- **`submitSteps` for plan steps.** Discards `DispatchResult`; loses `targetIds`, hold and error status.
- **Treating a `PREVIEW`/`READ` result as success** and continuing: the next step's binding would fail late.
- **Returning `Failed` after any apply/hold.** Defeats SC4.
- **Interpolating or regex-replacing inside strings.** Resolve only whole-value primitives in the parsed tree.
- **Putting model-written step ids, tool arguments, or bound ids into `TraceCode`, `EscalationReason.Other`, exception messages or `toString()`.** Only fixed codes and counts.
- **Reading `policy.maxIterations` as a step cap.** Step cap is the Builder's `maxSteps`; iteration limit is irrelevant (≤ 2 calls, and `TierPolicy` requires `maxIterations >= 2`).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Provider error / refusal / no-tool-call / stop-reason routing | A new `when` over `ModelResult` | `decideResult(result, hooks)` with `OutcomeHooks` (in place) | Exact SingleShot parity for PLAN-05; but intercept `MAX_TOKENS` first (D-09) |
| Token budget gating | Manual token math | `ceilingReached` / `ceilingCrossed` | Same boundary semantics as the other tiers |
| "No escalation after commit" | Strategy-side suppression or a `Partial` outcome | Return `Escalate`; `TierWalk` suppresses | Read from the coordinator's counts, never from the strategy's claim (`TierWalk.kt:108-112` KDoc) |
| Gate/commit/sink/ledger | Any apply path | `session.submit(step, callId)` | Single write path (`CommandSession` KDoc) |
| Executor fault handling | try/catch in the strategy | `guarded`-based `prepareGuarded` (extracted) | `detekt` `TooGenericExceptionCaught`; the repo's only suppression is in `Guarded.kt` |
| Model call accounting | Counters in the strategy | `TierAttempt.turns` (engine-recorded in `RoutedModel.complete`) | Trace shows exactly how many calls ran (SC3) |
| JSON ref substitution | String `replace` / regex over serialized JSON | Tree walk over `JsonObject`/`JsonArray` | Pitfall: dictated text collisions, quoting bugs |
| Wire encoding of the continuation | Provider-specific JSON | Neutral `AssistantMessage` + `ToolResultsMessage` | The three mappers already encode it (and the replay stamp) |

**Key insight:** the only genuinely new logic is plan parsing/validation, binding, the step loop and the replan digest. Everything that decides *outcomes* (failure routing, suppression, ledger, budget) is existing, tested engine code; reusing it is what makes PLAN-04/05 nearly free to prove.

## Common Pitfalls

### Pitfall 1: `decideResult` turns a truncated plan into `Failed(MaxTokens)`
**What goes wrong:** D-09 wants `Escalate(MalformedExtraction)`, but `decideResult` → `stopOutcome` → `stopFailure` returns `Failed(MaxTokens)` for `StopReason.MAX_TOKENS` (anchors 18-19).
**Why:** The helper was written for SingleShot, where truncation is a hard failure.
**How to avoid:** Before `decideResult`, `if (result is ModelResult.Success && result.response.stopReason == StopReason.MAX_TOKENS) return Escalate(MalformedExtraction(), session.carry)`. Keep `PAUSE_TURN`/`CONTEXT_WINDOW_EXCEEDED` on the shared `Failed` path. Do not parse a truncated plan leniently.
**Warning signs:** a Plan test for truncation that expects `Escalate` but sees `Failed`.

### Pitfall 2: Spending a model call that cannot help, or replanning after a commit
**What goes wrong:** step 2 fails after step 1 committed; a replan call is paid and its tail would double-write.
**How to avoid:** the single `canReplan` predicate (Pattern 4). Assert call counts: success = 1 turn, pre-commit replan = 2, post-commit failure = 1, needs-lookup = 1.

### Pitfall 3: `$` in Kotlin strings
**What goes wrong:** `"$a.id"` in source is a string template referencing `a`; `"$<stepId>"` happens to compile as literal but is fragile. The tool description and the regex are both affected.
**How to avoid:** write `\$` in normal strings; in raw strings use `${'$'}`; put the sigil in one `private const val`/function and test the rendered description contains the exact characters `$<stepId>.<key>`. Add a unit test that the schema description is byte-stable.

### Pitfall 4: Banned constant names and other repo gates
**What goes wrong:** `private const val MAX_STEPS = 8` or `DEFAULT_MAX_STEPS` fails `NoHardCodedConstantsTest.limitConstantsAreDeclaredOnlyByTheirOwners` (anchor 31; Phase 14 had to rename "text-span ceiling" for this).
**How to avoid:** name them `PLAN_STEP_LIMIT` and `REPLAN_LIMIT` (neither starts with `DEFAULT_|MIN_|MAX_` nor contains `TOKEN|ITERATION|CEILING`); do not edit the owner list. No planning ids (`Phase NN D-NN`, `T-nn-nn`, `WR-nn`) in comments or KDoc (anchor 32). No `println`/`runCatching`/`System.out`. Domain-free wording in KDoc and API.md: no food/card/note tool names; use neutral tool names in tests (`create_item`, `tag_item`, key `item_id`).

### Pitfall 5: Reshape mode appends an instruction to the last user-role message
**What goes wrong:** a wire test comparing request #1 and #2 `messages[0]` bytes fails on models that reject forced tool choice (anchor 33).
**How to avoid:** compare tools+system bytes at wire level; compare `messages[0]` at neutral level. Also test both modes' `tool_choice` bytes are equal across the two requests within the same mode.

### Pitfall 6: Held step semantics and `commitHeld`
**What goes wrong:** after a mid-plan hold, later steps never run and `commitHeld` applies only the held proposal, not the rest of the plan.
**How to avoid:** document in KDoc/API.md: Plan is gate-per-step with no resume; steps after a hold are not executed and the command ends partial. A hold on the last step is a clean pending state (A1).

### Pitfall 7: Answer every tool call in the replan
**What goes wrong:** the plan answer contains extra calls (or a direct app-tool call alongside/instead of `submit_plan`); a `ToolResultsMessage` that answers only `submit_plan` is rejected by the provider.
**How to avoid:** one `ToolResult` per call id, in call order; extras get the fixed ignored notice. When `submit_plan` is absent, the plan is rejected (`malformed`) and every call is answered with the ignored notice.

### Pitfall 8: Host memory
**What goes wrong:** the host is under memory pressure (research-time probe: swap 2047/2047 MB used, ~4.9 GB free of 32 GB); earlyoom kills runs (project memory note `vae-release-cut-host-oom`).
**How to avoid:** one Gradle run at a time, daemon off, `workers.max=2`, in-process Kotlin compiler, `--offline`, `:core:test --tests`. Never run the full `check` while another Gradle is alive.

## Code Examples

### Step loop skeleton (strictly sequential, Escalate after work)
```kotlin
// Sketch. Types: CommandSession, ToolStep, DispatchResult, ActionKind, StrategyOutcome, EscalationReason from the anchors above.
internal class PlanRun(private val session: CommandSession, private val callId: String /* planning call id */) {
    private val env = mutableMapOf<String, Map<String, String>>()
    var worked = false
        private set

    suspend fun run(plan: ParsedPlan, prepare: suspend (PlanStep, JsonObject) -> ToolStep): RunStop {
        for ((index, step) in plan.steps.withIndex()) {
            val bound = bind(step.arguments, env) ?: return RunStop.BindingUnresolved
            val dispatch = session.submit(prepare(step, bound), callId)
            if (dispatch.held) { worked = true; return RunStop.Held(last = index == plan.steps.lastIndex) }
            worked = worked || dispatch.actions.any { it.applied }
            if (!dispatch.committedOnly()) return RunStop.StepFailed
            env[step.id] = mergeTargets(dispatch.actions)
        }
        return RunStop.Done
    }
}
private fun DispatchResult.committedOnly(): Boolean = actions.isNotEmpty() && actions.all { it.kind == ActionKind.COMMITTED }
```
(`detekt` `ReturnCount`/`LongMethod`: keep each function short; split as the file layout suggests. SingleShot's style of small private functions chained with `?:` is the model.)

### Test rig (existing fixtures only)
```kotlin
// Source: core/src/test/.../SingleShotTestSupport.kt (pipelineOf, testKey, fixedClock) and testFixtures (FakeAiProvider, FakeMutation, ScriptedGate, ScriptedToolExecutor, RecordingCommitSink, ScriptedStrategy)
val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.toolCall("plan-1", "submit_plan", planArgs, Usage(1, 0, 0, 1)))
val executor = ScriptedToolExecutor(log) { call, _ ->
    ToolStep.Mutation(FakeMutation(call.toolName, StepResult("ok", false, "tok", mapOf("item_id" to "id-for-${call.toolName}"))))
}
val plan = planStrategy { tooling = ToolSpecProvider.fixed(snapshot); this.executor = executor; clock = fixedClock }
val pipeline = pipelineOf(listOf(plan, nextTier), fake, ScriptedGate.admitAll(log), sink)
// asserts: fake.callCount == 1; outcome.trace.attempts.first().turns.size == 1; executor.calls[1].arguments["item_id"] == JsonPrimitive("id-for-create_item")
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| One combined gate proposal per command (SingleShot) | One proposal per plan step | This phase | Required for write-output binding (ARCHITECTURE §4.3) |
| `providerCallId` null for plan steps (ARCHITECTURE D-PLANID) | The planning call's id; `position` is the identity | D-03 refreshed 2026-10-06 | Supersedes ARCHITECTURE.md line 252; follow CONTEXT.md |
| Replan tail with persistent binding env (FEATURES.md) | Whole-plan replan only before any apply/hold | D-06/D-07 | Simpler; no done-steps digest |

**Deprecated/outdated:** ARCHITECTURE.md §4.3 sketch passes `callId=null` to `Extraction` and says `prepareGuarded` already exists; neither is true at HEAD (Phase 14 summary: guarded-prepare set was empty, extraction left to Phase 15).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A hold on the **last** step returns `Completed(null)` (non-partial, held reported via `outcome.held`); a hold with steps remaining returns `Escalate(Other("plan_step_held"))` → suppressed partial. D-07's resolution text says "return Escalate" without the last-step carve-out, which only appears in the decision-map option text. | Pattern 4 | If SB 177 wants a hold always rendered partial, flip the last-step case to `Escalate`; one-line change plus one test. Confirm with SB 177 (D-07 already says "Confirm with SB 177"). |
| A2 | A mutating step whose result is `PREVIEW`, `READ` or "no action" counts as a failed step (nothing committed). | Pattern 3 | An app that intentionally previews inside a plan would see its plan stop; low likelihood, documented in KDoc. |
| A3 | Reference grammar details: id `^[A-Za-z][A-Za-z0-9_-]*$`; key = non-space remainder; undeclared `$id.key`-shaped string is a static rejection, not a literal; no escape in v1.1. Because D-04 is one-way, the planner should confirm these specifics (they are not in the locked text beyond "whole-value `$<stepId>.<key>`"). | Pattern 2 | Wrong id charset or collision handling is frozen at the tag. The live probe plus a review of the grammar before 15 closes mitigates it. |
| A4 | Replan digest carries only engine-owned codes plus the model's own step id, never app `contentForModel`. | Pattern 5 | Replans may be less effective for app-specific validation errors; extend later (additive). |
| A5 | A truncated plan (`MAX_TOKENS`) escalates with `MalformedExtraction` without spending a replan (the same-size answer would truncate again). D-09 fixes the outcome, not the replan. | Pitfall 1 | If replan is wanted on truncation, add it behind the same predicate. |
| A6 | `maxSteps` default 8, constants named `PLAN_STEP_LIMIT` / `REPLAN_LIMIT`; `maxSteps` also emitted as `maxItems` in the schema. | Pattern 6 | Cosmetic; "~8" is in D-12. |
| A7 | New `TraceCode`s: `plan_replanned`, `plan_rejected`, `plan_binding_unresolved` (names and count are the planner's call). | Pattern 6 | Each adds public API + a `TraceTest` row. Zero new codes is viable (turns count + escalation reason already show calls and why). |
| A8 | Request flags `singleToolCall = true`, `CacheDirective(true)` mirror SingleShot. | Pattern 1 | Parallel plan calls would be handled by the "extras dropped" rule anyway. |
| A9 | `submit_plan` placed first in the tools list. | Pattern 1 | Cache order only; any stable order works. |
| A10 | Fixed tool name `submit_plan`; clash → `Failed(Other("plan_tool_name_taken"))`; no non-terminal tool → `Failed(Other("plan_no_tools"))`; package `core.strategy.plan`. | Pattern 1 | Names are new frozen strings; cheap to choose now. |
| A11 | Live probe models: `claude-haiku-4-5` and `gpt-5.4-mini`, as the existing opt-in captures use them ("the cheapest" per the comment in `AnthropicLiveCaptureTest.kt`). | Live probe | Probe cost/representativeness only. |
| A12 | Provider rule that every `tool_use` must be answered by a `tool_result` in the next message (Anthropic) and every `tool_call_id` by a `tool` message (chat). | Pattern 5 | Answering every call is harmless if the rule were looser. |
| A13 | Extracting `prepare`/`faultStep` from `AgenticDispatch.kt` into a shared file (instead of duplicating 10 lines) is acceptable now that Phase 14 is closed. | Pattern 3 | If rejected, duplicate with a different private name; the fault content constant must stay byte-identical to Agentic's. |

## Open Questions

1. **Hold on the last step: `Completed(null)` or `Escalate` (A1)?**
   - Known: SB condition #3 is satisfied either way (committed steps stay, held step surfaces). Literal D-07 says Escalate; decision-map option text distinguishes "last step → Completed".
   - Unclear: whether SB 177 renders a last-step hold as partial.
   - Recommendation: implement the carve-out, cover both cases with tests, and ask SB 177 via the orchestrator before the phase closes.
2. **Is the D-04 grammar detail set (A3) acceptable to freeze?**
   - Recommendation: the planner includes a short grammar spec (id charset, whole-value, letter-leading, undeclared-id-is-invalid) in the plan's must-haves, and the probe confirms models emit it.
3. **Live probe pass criteria and recording.**
   - Recommendation: per model, 2 scenarios (create-then-tag with a ref; a 2-step plan with no ref plus a literal `"$5.00"` string), ≤ 8 HTTP requests total; pass = ref substituted with the real id, literal untouched, ≤ 1 replan, counts logged. Record counts and verdict in `15-LIVE-PROBE.md` (ids/counts only, never bodies or keys). A failure means rewording the `submit_plan` description (not the syntax); only a systematic inability to emit whole-value refs reopens D-04.
4. **If keys or the wrapper are unavailable when the phase executes:** skip (task `onlyIf` false), record a `deferred_obligation` to Phase 19 (its Gate-1 plan leg and "Keys note" already make live plan calls). Because the syntax freezes at the v1.1.0 tag, the probe must have run by Phase 19 at the latest.

## Runtime State Inventory

Not a rename/refactor/migration phase. Omitted.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | all builds | ✓ | OpenJDK 17.0.19 | — |
| Gradle wrapper + offline cache | `:core:test`, `:providers:test` | ✓ (`~/.gradle` has `caches`, `daemon`, `jdks`) | 9.4.1 (pinned) | — |
| Host memory | Gradle runs | ⚠ tight (32 GB total, ~4.9 GB free, swap 2047/2047 MB used at probe time) | — | one Gradle at a time, low-memory recipe below |
| `with-test-keys` wrapper | live probe | ✓ `/home/yahir/.local/bin/with-test-keys` (key files `anthropic.key`, `openai.key`, `openrouter.key` exist; contents never read) | — | skip + defer to Phase 19 |
| TESTER device | **not required** | n/a | — | Phase 15 has no device step; the probe is host-only (provider calls with a fake executor). Phase 19 owns Gate-1 on the TESTER. |
| Network to api.anthropic.com / api.openai.com | live probe | not probed (no live call made in research) | — | skip + defer |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** live keys (deferral path above).

Low-memory recipe (proven in Phase 14, `14-VALIDATION.md`): `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q :core:test --tests '<Class>'`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0; hand-written fakes in `core/src/testFixtures` (`FakeAiProvider`, `FakeMutation`, `ScriptedGate`, `ScriptedToolExecutor`, `RecordingCommitSink`, `ScriptedStrategy`, `NoNetworkGuard`) |
| Config file | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts` |
| Quick run command | `GRADLE_OPTS="<low-mem recipe>" ./gradlew --offline -q :core:test --tests '*PlanThenExecute*'` |
| Full suite command | same GRADLE_OPTS, `./gradlew check --offline`, then `scripts/verify-docs-coverage.sh` and `scripts/review-api-surface.sh` |
| Wire test | `... :providers:test --tests '*PlanThenExecuteWireTest*'` |
| Live probe (opt-in) | `with-test-keys --only anthropic,openai -- env VAE_LIVE_PLAN=1 GRADLE_OPTS="<recipe>" ./gradlew --offline :providers:livePlanProbe` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PLAN-01 | 2-step plan: exactly 1 provider call; `turns.size == 1`; order log `prepare:a, gate, apply:a, sink, prepare:b, gate, apply:b, sink`; gate asked once per step; `executed` positions 0,1 | pipeline | `:core:test --tests '*PlanThenExecuteRunTest*'` | ❌ Wave 0 |
| PLAN-01 | every action carries the plan call id; positions distinct (SB condition #4) | pipeline | `... '*PlanThenExecuteCallIdTest*'` | ❌ Wave 0 |
| PLAN-01 | held step reported held, never success; first-hold stop; last-step hold → Completed (A1) | pipeline | `... '*PlanThenExecuteHoldTest*'` | ❌ Wave 0 |
| PLAN-02 | later step receives the real id (executor `calls[1].arguments`); nested array/object refs; literals `"$5.00"` and mid-string `$a.id` untouched; conflicting keys → unresolved; unresolved → step fails, executor not called for it | unit + pipeline | `... '*PlanBindingTest*' '*PlanThenExecuteBindingTest*'` | ❌ Wave 0 |
| PLAN-02 | HELD/PREVIEW/IS_ERROR steps never bindable | pipeline | `... '*PlanThenExecuteBindingTest*'` | ❌ Wave 0 |
| PLAN-03 | static rejection → exactly 2 turns, replan request prefix identical (neutral), then valid plan runs; second rejection → `Escalate(MalformedExtraction)` and next tier runs once | pipeline | `... '*PlanThenExecuteReplanTest*'` | ❌ Wave 0 |
| PLAN-03 | pre-commit step failure → replan once, never twice; call count asserted | pipeline | `... '*PlanThenExecuteReplanTest*'` | ❌ Wave 0 |
| PLAN-03 | `needs_lookup` and read-tool step → `Escalate(Other("plan_needs_lookup"))`, 1 turn, 0 executor calls, 0 gate calls, carry forwarded to next tier | pipeline | `... '*PlanThenExecuteLookupTest*'` | ❌ Wave 0 |
| PLAN-03 | byte-identical tools+system prefix across plan and replan on the Anthropic wire (and chat dialects) | wire | `:providers:test --tests '*PlanThenExecuteWireTest*'` | ❌ Wave 0 |
| PLAN-04 | **Plan-specific suppression test:** step 1 commits, step 2 fails (and, separately, binding miss; apply error; mid-plan hold) → `Completed(partial=true)`, attempt outcome `escalation_suppressed`, `TraceCode.ESCALATION_SUPPRESSED`, next tier `executions == 0`, model calls == 1, commit count preserved | pipeline | `... '*PlanThenExecuteSuppressionTest*'` | ❌ Wave 0 |
| PLAN-05 | `onFailed` matrix mirroring `SingleShotOutcomeMappingTest`: fires for HTTP 400/transport/model-unsupported; default = `Failed(reason, details)`; not fired for refusal, no tool call, no bound model, ceiling, gate; throwing hook → `strategy_error`; escalating hook; on-device reason passes through | pipeline | `... '*PlanThenExecuteOutcomeMappingTest*'` | ❌ Wave 0 |
| D-09 | `MAX_TOKENS` plan → `Escalate(MalformedExtraction)` not `Failed`; PAUSE_TURN/CONTEXT stay `Failed` | pipeline | `... '*PlanThenExecuteOutcomeMappingTest*'` | ❌ Wave 0 |
| D-12 | `maxSteps` exceeded → rejected; ceilingReached before replan; ceilingCrossed after plan | pipeline | `... '*PlanThenExecuteLimitsTest*'` | ❌ Wave 0 |
| D-02 | `submit_plan` schema byte-stable; enum = non-terminal tools; description contains `$<stepId>.<key>`; name clash / no-tool guards | unit | `... '*PlanSchemaTest*'` | ❌ Wave 0 |
| security | `toString` of strategy/Builder/trace/events carries no step ids, args, bound ids (redaction canary pattern of `RedactionCanaryTest`) | unit | `... '*PlanThenExecuteRedactionTest*'` | ❌ Wave 0 |
| move | Agentic suites unchanged after the `prepareGuarded` extraction | unit | `... '*AgenticLoopDispatchTest*' '*AgenticLoopGuardsTest*' '*AgenticLoopGateTest*' '*ToolExecutorSeamTest*'` | ✅ exist |
| gates | no banned constant/ids; API shape; trace list; docs | gate | `:core:test --tests '*NoHardCodedConstantsTest*' '*TraceTest*' '*ApiShapeTest*'`; `:core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility`; `scripts/verify-docs-coverage.sh --only C20,C21`; `scripts/review-api-surface.sh` | ✅ exist |
| D-04 | live binding syntax on cheap models | opt-in live | `livePlanProbe` (above) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the single touched test class (quick command); ≈ 60-120 s.
- **Per wave merge:** `:core:test :core:detekt :core:scanBannedConstructs` in ONE invocation, plus `:core:metalavaCheckCompatibility` once public API exists.
- **Phase gate:** full `check` green, `verify-docs-coverage.sh`, `review-api-surface.sh`, and the wire test; live probe result recorded or deferral recorded.

### Wave 0 Gaps
- [ ] `core/src/test/.../PlanTestSupport.kt`: neutral tools (`create_item`, `tag_item`, a read tool `find_item`, a terminal ask tool), `planArgs(...)` builder, `planStrategy {}` helper, `fixedClock` reuse.
- [ ] The test classes named in the map (all new); `TraceTest` rows if codes are added; `ApiShapeTest` assertion for the Builder shape.
- [ ] `providers/.../PlanThenExecuteWireTest.kt` and `PlanBindingLiveProbeTest.kt`; `livePlanProbe` task in `providers/build.gradle.kts`.
- [ ] No framework install needed.

## Security Domain

`security_enforcement` is enabled (absent or true) in `.planning/config.json` (`"security_enforcement": true`, `"security_asvs_level": 1`).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | (API keys are handled by existing `Credential`/`BoundModel`; Plan never sees a key) |
| V3 Session Management | no | — |
| V4 Access Control | yes | Every write goes through `PreApplyGate` per step; tools not offered in the snapshot are never prepared; terminal tools never executed |
| V5 Input Validation | yes | The model's plan is untrusted input: whole-plan validation before step 1 (shape, tool names against the snapshot, id grammar/uniqueness, reference order, step cap); executor validates arguments itself (engine passes them through) |
| V6 Cryptography | no | — |
| V7 Error Handling and Logging | yes | Never log or expose keys, transcripts, tool args/results: codes/counts only in `TraceCode`, `EscalationReason.Other`, `FailureReason.Other` (`isSafeToken`), `toString()` |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via the transcript yields a destructive plan | Tampering / Elevation | Per-step gate (`AwaitingConfirmGate` etc.); stop at first hold; step cap |
| Model-forged reference to an id it never received, or forward/self reference | Tampering | Static ref check; committed-only env; unresolved = step failure, never a guessed value |
| Bound ids/values leak into telemetry | Information disclosure | Bound values only inside `Extraction.arguments` handed to the app; trace/events carry counts and codes |
| Unbounded plan / runaway cost | DoS | `maxSteps`, `ceilingReached/Crossed`, hard replan limit, `maxTokensPerTurn` on both calls, optional `commandTimeoutMillis` |
| Replan re-emits committed writes | Tampering | Replan only while nothing applied/held; whole-plan replacement |
| Calling a tool the app never offered | Elevation | Plan validated against `snapshot.tools`; non-offered name is rejected, `UNKNOWN_TOOL` recorded, executor not called |
| Live probe key leakage | Information disclosure | `with-test-keys` redaction; probe prints ids/counts only; no raw bodies committed |

## Live Probe Shape (D-04): how Phase 14's window pattern maps, and what is different

Phase 14's guarded pattern (14-02/14-09: `14-WINDOW-GRANT.md` with `grant: pending|open|consumed|deferred`, `scripts/run-stt-capture.sh` refusing every device subcommand until `grant: open`, shared flock with the other TESTER runners) exists because it drives the **TESTER phone**. [VERIFIED: 14-WINDOW-GRANT.md and run-stt-capture.sh header read this session] The binding probe needs no device: it exercises the real `PlanThenExecuteStrategy` over the real `AnthropicProvider`/`ChatCompletionsProvider` against a fake `ToolExecutor` on the host. So the right shape is the **provider-only live-capture pattern already in `:providers`** (anchor 34), not the window-grant pattern:

- Gradle task `livePlanProbe` (group verification, `outputs.upToDateWhen { false }`, `onlyIf { providers.environmentVariable("VAE_LIVE_PLAN").orNull == "1" }`, `filter { includeTestsMatching("*PlanBindingLiveProbeTest") }`); the class name contains `Live`, so `test` and every OkHttp matrix leg keep excluding it (`filter { excludeTestsMatching("*Live*") }`). Never a dependency of `check`.
- Test class skips with `Assume` unless the opt-in variable and the key variable are present; a hard request counter ceiling (≤ 8 across both models, counted before each send, never retried); prints ids, counts, statuses only.
- Run under the wrapper per the test-keys workflow: `with-test-keys --only anthropic,openai -- env VAE_LIVE_PLAN=1 ...`. Agents may run this (host test, redacted output); no TESTER window, no `push-test-key`, no device lock.
- Non-default, skippable, and recorded: result and request count go to `15-LIVE-PROBE.md`; unavailable keys → `deferred_obligation` to Phase 19.
- Phase 19's Gate-1 "plan with binding" leg (ROADMAP Phase 19 SC1 and Keys note) is the end-to-end device proof and is separate; do not add any device step to Phase 15.

## Sources

### Primary (HIGH confidence, read this session)
- `/home/yahir/Projects/Reusable/android/voice-action-engine/core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/`: `pipeline/TierWalk.kt`, `pipeline/RunSession.kt`, `pipeline/CommandOutcome.kt`, `pipeline/TierPolicy.kt`, `commit/CommitCoordinator.kt`, `commit/ApplyStep.kt`, `commit/ActionLedger.kt`, `commit/ToolStep.kt`, `commit/CommitSink.kt`, `commit/ActionKind.kt`, `strategy/CommandSession.kt`, `strategy/StrategyOutcome.kt`, `strategy/StepSubmission.kt`, `strategy/StrategyLimits.kt`, `strategy/OutcomeResolver.kt`, `strategy/ToolExecutor.kt`, `strategy/ToolSpec.kt`, `strategy/ToolSpecProvider.kt`, `strategy/UserTurn.kt`, `strategy/singleshot/SingleShotStrategy.kt`, `strategy/singleshot/SingleShotOutcomes.kt`, `strategy/agentic/AgenticDispatch.kt`, `strategy/agentic/AgenticLoopStrategy.kt`, `strategy/agentic/AgenticTurn.kt`, `transcript/*` (ModelRequest, Message, AssistantPart, ModelResponse, ReasoningMode), `provider/BoundModel.kt`, `provider/ModelResult.kt`, `provider/ModelCapabilities.kt`, `failure/EscalationReason.kt`, `telemetry/TraceCode.kt`, `telemetry/CommandTrace.kt`, `internal/Guarded.kt`.
- `core/src/test/.../NoHardCodedConstantsTest.kt`, `SingleShotTestSupport.kt`, `SingleShotFixtures.kt`, `EscalationSafetyTest.kt`, `LocalGrammarPipelineTest.kt`; `core/src/testFixtures/.../FakeAiProvider.kt`, `FakeMutation.kt`, `ScriptedGate.kt`, `ScriptedToolExecutor.kt`, `RecordingCommitSink.kt`, `ScriptedResponses.kt`.
- `providers/.../anthropic/AnthropicEncoder.kt`, `AnthropicTransport.kt`; `providers/src/test/.../AgenticLoopWireTest.kt`; `providers/build.gradle.kts`; `anthropic/AnthropicLiveCaptureTest.kt`.
- `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml`, `scripts/review-api-surface.sh`, `scripts/verify-docs-coverage.sh`, `scripts/run-stt-capture.sh`, `API.md`.
- Planning: `15-CONTEXT.md`, `.planning/ROADMAP.md` (Phase 15, 19), `.planning/REQUIREMENTS.md` (PLAN-01..05), `.planning/v1.1-DECISION-MAP.md` (Phase 14/15), `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md`, `.planning/research/ARCHITECTURE.md` §4.3-4.4, `PITFALLS.md` (14, 15, 25), `FEATURES.md` §2, `SUMMARY.md`, `14-RESEARCH.md`, `14-VALIDATION.md`, `14-WINDOW-GRANT.md`, `14-01-SUMMARY.md`; `~/.claude/context/workflows/test-keys.md`.

### Secondary (MEDIUM confidence)
- Provider-side conversation rules (answer every tool call) inferred from the existing mapper tests, not from provider docs (A12).

### Tertiary (LOW confidence)
- None used for recommendations. No web research was needed: this phase adds no external library and no external API behavior beyond what the existing mappers already encode. The research-plan/store seams were not used (no external questions).

## Project Constraints (from CLAUDE.md)

- Library is domain-free: no note/card/food words in main sources, KDoc, API.md; neutral tool names in tests and docs (CLN-02 scanner, C21).
- `:core` has no HTTP dependency and no other hub (L7/A7); only `kotlinx-coroutines-core` and `kotlinx-serialization-json`.
- Public API grows strictly additively; no data class, no enum, no public sealed beyond the allow-list, no default-argument constructor stubs; explicit API mode (`explicitApi()`).
- detekt zero baseline, default rules on, plain `detekt` task only; no `printStackTrace`, `println`, `runCatching`; `TooGenericExceptionCaught` stays active (use `guarded`).
- Secrets never reach logs, telemetry, exceptions or `toString()`; keys, transcripts, tool args/results are never printed.
- Mostly JVM-tested; two-gate UAT only where device-verifiable (Phase 15 is JVM-only; device proof is Phase 19).
- Contract changes only via §10 amendments through the control plane; never commit §11; tags are agent-owned under A12 but no tag is cut in this phase.
- GSD workflow enforcement: all file changes via a GSD command (`/gsd-execute-phase`).
- OkHttp floor 4.12: any `:providers` test uses legacy `okhttp3.mockwebserver`, never `mockwebserver3`, never `QueueDispatcher`.
- Host is memory-tight: one Gradle process at a time, low-memory recipe, `--offline`.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; versions read from CLAUDE.md and `core/build.gradle.kts`.
- Architecture: HIGH — every integration point read and quoted; the replan/hold predicate is derived from verified code.
- Pitfalls: HIGH for the repo gates (constants, `$`, planning ids, MAX_TOKENS), MEDIUM for model behavior (prompt wording, reshape mode) until the live probe runs.

**Research date:** 2026-10-06
**Valid until:** 2026-11-05 for the code anchors, provided Phases 16-18 do not touch `TierWalk.kt`/`SingleShotOutcomes.kt` (Phase 16 will edit `TierWalk.run`; re-check anchors 1-4 after it lands). Model/prompt findings are fast-moving: re-run the probe if the cheap-model ids change.
