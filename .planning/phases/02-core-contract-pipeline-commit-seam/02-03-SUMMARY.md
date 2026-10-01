---
phase: 02-core-contract-pipeline-commit-seam
plan: 03
subsystem: core
status: complete
tags: [kotlin, pipeline, commit-seam, pre-apply-gate, commit-sink, sealed-outcome, dsl, fixtures, detekt]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "02-01 identity/input/failure/policy value types and the surface lint; 02-02 ActionKind, FinishedKind, TraceCode, TerminalCall, guarded helper"
provides:
  - "commandPipeline { tier(..); gate; commitSink; policy; clock; runIds } DSL and CommandPipeline.execute"
  - "CommandStrategy / CommandSession / sealed StrategyOutcome (Completed, Escalate, NoMatch, Failed)"
  - "ToolStep (Finished, Mutation), PendingMutation, StepResult, DispatchResult, PreApplyGate, sealed GateDecision (Admit, Hold), CommitProposal, CommitSink, ActionEvent, ExecutedAction, HeldProposal, sealed RunTermination"
  - "sealed CommandOutcome (Completed, Failed, Unhandled) with effects on the parent; CommandTrace and TierAttempt shell"
  - "internal CommitCoordinator (the single write path), RunSession, TierWalk, RunRecorder, RunEffects"
  - "testFixtures ScriptedStrategy, ScriptedGate, RecordingCommitSink, FakeMutation"
affects: [02-04, 02-05, 02-06, 02-07, 02-08, 02-09, Phase 3, Phase 4, Phase 6]

plan_head_before: d6b42da0cf25bae4b10c6fbd3a4facba3d044880

actuals:
  tokens: 19000   # chars/4 over the realized diff of core/ (76,343 chars incl. diff headers)
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Sealed only for the five contract-closed types; engine-produced leaves have internal constructors and public getters on the parent"
    - "One mutex-serialized coordinator owns positions and the write path; the sink only observes, delivered under NonCancellable through guarded"
    - "Run close from finally under NonCancellable, exactly once per execute"
    - "Constants are file-private (a top-level internal const would be a public static field and fail ApiShapeTest); the held bytes are read through an internal function"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyOutcome.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ToolStep.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/PreApplyGate.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/HeldProposal.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/RunTermination.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedStrategy.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedGate.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingCommitSink.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeMutation.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineSpineTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineBuilderTest.kt
  modified: []

key-decisions:
  - "The held-content literal is a file-private const in CommitCoordinator.kt, read through internal fun heldForConfirmationContent(); an internal const would surface as a public static field and fail ApiShapeTest"
  - "CommandTrace's internal constructor orders required fields first and defaults attempts/codes, to stay under the constructor-parameter threshold without banking debt"
  - "A throwing apply is guarded in the interim coordinator (trace code APPLY_ERROR, is_error action, applied true); plan 05 replaces this with the full apply step"

patterns-established:
  - "Fixtures use only public API (no internal symbol), so Phases 4 to 9 can reuse them"
  - "Tracer test asserts the full event order through one shared RecordingSink<String> log"

requirements-completed: []
requirements-partial: [CORE-01, CORE-02, CORE-09, GATE-01, GATE-04, GATE-05, GATE-06]

duration: ~45min
completed: 2026-09-30
---

# Phase 2 Plan 03: Keystone spine (pipeline, strategy seam, commit seam, outcomes) Summary

**One real end-to-end path: `commandPipeline {}` to `execute` to strategy to `session.submit(Mutation)` to gate Admit to `apply()` to per-action `onAction` to typed `CommandOutcome` to exactly-once `onRunClosed`, with all five contract-closed sealed types now present.**

## Performance

- **Tasks:** 2 of 2 (Task 1 tracer, Task 2 TDD)
- **Commits:** 9d6cbf9 (task 1), 956be2b (task 2)
- **Files:** 22 created (16 main, 4 fixtures, 2 test classes), 0 modified, 0 deleted

## Accomplishments

- Tracer: a scripted strategy's mutation is gated, applied, journaled per action and closed once. The shared log reads exactly `gate`, `apply:<tool>`, `sink:action:0:committed`, `sink:closed:<runId>:done`. Token verbatim, merged targetIds (result wins), context by identity, positions 0..n in dispatch order, parentRunId on outcome, trace, proposal, every event and the termination.
- A two-mutation step applies in order with a sink event between the two applies; an `isError` result is recorded as `is_error` (applied true) and excluded from `commits`.
- `StrategyOutcome.Completed(null, terminalCall)` reaches `CommandOutcome.Completed.terminalCall` by identity; `Completed("x", call)` throws `IllegalArgumentException`.
- Build-time validation (D-17): zero tiers, duplicate tier id, missing gate (no auto-commit default) and missing commitSink each throw `IllegalArgumentException` with the exact planned messages; `execute` returns a typed outcome for every `StrategyOutcome` variant.
- Redaction: canaries in reply, transcript, content, token and context never appear in the `toString` of the outcome, action, event, termination, trace, session or pipeline.
- Surface review in sealed-complete mode: `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=96`.

## Internal signatures later plans build on

```
CommitCoordinator(runId: String, parentRunId: String?, gate: PreApplyGate, sink: CommitSink, recorder: RunRecorder)
  suspend fun submit(step: ToolStep): DispatchResult
  fun executed(): List<ExecutedAction>      fun held(): List<HeldProposal>
  val appliedCount: Int                     val heldCount: Int
  internal fun heldForConfirmationContent(): String   // top-level, exact bytes {"applied":false,"status":"held_for_confirmation"}

RunRecorder(runId, parentRunId, language: String?, transcriptLength: Int, clock: () -> Long)
  tierStarted(strategy: StrategyId)
  tierSkipped(strategy: StrategyId, code: TraceCode)
  tierFinished(strategy: StrategyId, outcome: String, escalation: EscalationReason?, failure: FailureReason?, suppressed: EscalationReason?)
  recordCode(code: TraceCode)
  snapshot(): CommandTrace

RunEffects(runId, parentRunId, executed, held, trace)  // commits derived by kind committed
snapshotEffects(runId, parentRunId, coordinator, recorder): RunEffects   // top-level, in CommandOutcome.kt
TierWalk(strategies, policy, coordinator, recorder, runId, parentRunId).run(input): CommandOutcome
RunSession(runId, parentRunId, strategy, policy, carry, coordinator): CommandSession
CommandPipeline internal constructor(strategies, gate, sink, policySource, clock, runIds)
```

Trace attempt outcome strings used by `TierWalk` (private consts): `completed`, `escalated`, `no_match`, `failed`. `escalation_suppressed` is plan 06's.

## Fixture APIs as built (public, testFixtures)

- `ScriptedStrategy(id: StrategyId, steps: List<StrategyStep>)` plus `ScriptedStrategy(id, vararg steps)`; `typealias StrategyStep = suspend (CommandInput, CommandSession) -> StrategyOutcome`; `executions: Int`, `receivedCarries: List<Any?>`; script exhaustion fails with "script exhausted".
- `ScriptedGate(log: RecordingSink<String>? = null, decide: suspend (CommitProposal) -> GateDecision)`; `proposals`, `calls`; logs `gate`; companion `admitAll(log)`, `holdAll(reason, appOutcomeToken, log)`, `sequence(log, vararg decisions)`.
- `RecordingCommitSink(log = null, onActionHook = {}, onRunClosedHook = { _, _ -> })`; records, then logs `sink:action:<position>:<kind>` / `sink:closed:<runId>:<code>`, then runs the hook; `actions: List<ActionEvent>`, `closes: List<RunTermination>`, `closedRunIds: List<String>`.
- `FakeMutation(toolName, behavior: suspend () -> StepResult, targetIds = emptyMap(), context = null, log = null)` plus `FakeMutation(toolName, result: StepResult, log = null)`; `applyCount`; logs `apply:<toolName>` when apply starts.

## Interim behaviors left for the next plans

- **Hold** (plan 05): returns `DispatchResult(held = true)` with the held bytes, records no action, adds no `HeldProposal`, and does not call the sink. Plan 05 adds the held actions, the `HeldProposal` and delivery.
- **Finished** (plan 05): returns its content with no recorded action and no sink call; READ/PREVIEW/ERROR classification is plan 05's.
- **Apply** (plan 05): guarded; a thrown apply records `APPLY_ERROR`, an `is_error` action with `applied = true` and content `{"status":"error"}` (file-private `APPLY_FAILED_CONTENT`). Cancellation mid-apply is not yet specially handled (it propagates); plan 05 owns that.
- **Strategy and policy-source guarding** (plan 04): `strategy.execute` and `policySource.current()` are not yet guarded, so a throw currently escapes `execute` (the run still closes once, as `Cancelled`). Plan 04 adds never-throw, the deadline and the termination refinement for escaping errors.
- **Escalation guard** (plan 06): `TierWalk` escalates on `Escalate`/`NoMatch` regardless of `appliedCount`/`heldCount`.
- **Selector, policy pre-check** (plan 04): the walk is strictly linear from the first tier.

## Verification evidence

- `./gradlew :core:check :providers:check -q`: exit 0 (detekt zero issues, scanBannedConstructs, structural gates, fixtures-not-published, detekt negative controls).
- Tests in `:core`: 84 total, 0 failures, 0 skipped, 0 errors (new: PipelineSpineTest 5, PipelineBuilderTest 9; ApiShapeTest 6 still green with the new classes swept).
- Task 2 RED confirmed first: PipelineBuilderTest ran 9 tests with 4 failures before `build()` validation existed.
- `scripts/review-api-surface.sh --expect-sealed-complete`: exit 0, `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=96`.
- `@Suppress` count in `core/src/main`: 1 (Guarded.kt only). No `api.txt` in any module directory.
- Acceptance greps: held literal and `NonCancellable` in CommitCoordinator.kt; `finally`, `NonCancellable`, `onRunClosed` in CommandPipeline.kt; `sealed class` in the five sealed files; no `public constructor(` in CommandOutcome.kt or RunTermination.kt.

## Deviations from Plan

**1. [Rule 1 - Bug] `internal const val HELD_FOR_CONFIRMATION` would fail the surface lint**
- A top-level `internal const` compiles to a public static field, which ApiShapeTest forbids (the same finding recorded in 02-02).
- Fix: the constant is file-private and exposed to same-module code through `internal fun heldForConfirmationContent()`. The literal and constant name are still in CommitCoordinator.kt, so the acceptance grep holds. Plan 05's golden test should call the function instead of referencing the constant.

**2. [Rule 3 - Blocking] CommandTrace constructor had 8 required parameters**
- detekt `LongParameterList` (constructorThreshold 8) would fire. Fix: `attempts` and `codes` default to empty on the internal constructor (not API); no config tuning, no baseline.

**3. Two line-length findings** in ToolStep.kt and CommandPipeline.kt were reflowed.

## Requirements bookkeeping

`requirements mark-complete` was intentionally NOT run. Every ID this plan lists is only partly delivered (CORE-01 selector/policy wiring, CORE-02 carry tests and escalation guard, CORE-09 and GATE-01/04/05/06 cover the Admit path and tracer only; Hold, preview, per-exit-path close tests and escalation guarding land in plans 04 to 06). All seven stay Pending in REQUIREMENTS.md.

## Known stubs / deferred

The interim branches listed above are intentional and owned by plans 04, 05 and 06. Pre-existing unrelated working-tree changes (.planning/graphs, config.json, milestone files, graphify-out, .gsd) were left untouched and unstaged.

## Self-Check: PASSED

- All 22 created files exist; commits 9d6cbf9 and 956be2b present in `git log`; `git rev-list --count d6b42da..HEAD` = 2 at the last task commit.
