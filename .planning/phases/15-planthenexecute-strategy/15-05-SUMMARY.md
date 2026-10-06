---
phase: 15-planthenexecute-strategy
plan: 05
subsystem: core/strategy/plan
status: complete
tags: [planthenexecute, onfailed, truncation, limits, redaction, plan-05, plan-03]

requires:
  - phase: 15-04
    provides: hold semantics (RT-01), Completed.remainingStepIds, PlanFlow replan predicate
provides:
  - "StopReason.MAX_TOKENS pre-intercept on the plan call and the replan call: Escalate(MalformedExtraction) with the incoming carry, no replan, nothing run (D-09)"
  - "PlanThenExecuteOutcomeMappingTest (14): onFailed parity with SingleShot case for case, plus the truncation and stop-reason rules"
  - "PlanThenExecuteLimitsTest (13): maxSteps cap, both ceiling checks, per-turn limit, exact call accounting"
  - "PlanThenExecuteRedactionTest (5): canary sweep over five plan runs, including a post-commit hold (RT-01)"
affects: [15-06, 15-07]

plan_head_before: 4f175aad56372a82c2c138074955010364e1e4db
actuals:
  tokens: 10500
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Truncation is decided in the strategy before decideResult, so SingleShot's shared helper stays byte-identical"
    - "Private test helper classes get tier-specific names (OutcomeRig, LimitsRig, CanaryRun): private top-level classes in one package still collide on the JVM"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteOutcomeMappingTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteLimitsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteRedactionTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt

key-decisions:
  - "A truncated answer escalates before its tool calls are read, even when it holds a complete-looking submit_plan call; the check sits ahead of decideResult and ahead of the ceiling-crossed check, in one helper used by both the plan and the replan call"
  - "No strategy change was needed for Task 2: every limit case passed against the 15-03/15-04 implementation"

requirements-completed: [PLAN-05, PLAN-03]

coverage:
  - id: D1
    description: "PLAN-05 / SC5: onFailed fires for provider failures other than NoToolCall and Refusal (HTTP, transport, ModelUnsupported, on-device ProviderUnavailable), on the plan or the replan call, receives the same reason and details objects, defaults to Failed(reason, details); not called for NoToolCall, prose, Refusal (failure, empty answer, REFUSAL stop), a missing credential, a reached ceiling, a gate hold or a throwing executor; a throwing hook ends the tier as strategy_error"
    requirement: "PLAN-05"
    verification:
      - kind: unit
        ref: "PlanThenExecuteOutcomeMappingTest (14 tests)"
        status: pass
      - kind: unit
        ref: "SingleShotOutcomeMappingTest (unchanged, still green)"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-09 / T-15-23: MAX_TOKENS answer (holding a valid submit_plan call) or a truncated replan answer ends Escalate(MalformedExtraction) with the incoming carry, one provider call (two for the replan case), executor never called, no third call; PAUSE_TURN and CONTEXT_WINDOW_EXCEEDED stay Failed; TOOL_USE with no call is Failed(MalformedResponse)"
    requirement: "PLAN-05"
    verification:
      - kind: unit
        ref: "PlanThenExecuteOutcomeMappingTest#aTruncatedPlanEscalatesMalformedWithTheIncomingCarryAndRunsNothing, aTruncatedReplanAnswerEscalatesMalformedWithTheCarryAndNeverAsksAThirdTime, pauseAndContextWindowStopsFailAndAToolUseStopWithNoCallIsMalformed"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-12 / T-15-24: nine steps at the default cap are rejected too_many_steps and replanned; maxSteps = 2 puts maxItems 2 in the sent schema; ceiling reached before the call fails BudgetExceeded(TOKENS) with no call; ceiling crossed by the plan answer fails before any step (executor and gate untouched); a rejected plan landing exactly on the ceiling is not replanned; both requests carry maxTokensPerTurn and the Builder's reasoning; maxIterations = 2 still allows the replan and a five-step plan"
    requirement: "PLAN-03"
    verification:
      - kind: unit
        ref: "PlanThenExecuteLimitsTest (13 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "ROADMAP SC3 call accounting: TierAttempt.turns is 1 for a clean plan, a needs-lookup plan and a post-commit failure, 2 for a replanned plan; trace usage equals the scripted usage (the sum for the replan)"
    requirement: "PLAN-03"
    verification:
      - kind: unit
        ref: "PlanThenExecuteLimitsTest#aCleanPlanIsOneCallWithTheScriptedUsage, aNeedsLookupPlanIsOneCallAndHandsTheCommandUp, aFailureAfterACommitIsStillOneCallAndNeverReplans, aReplannedPlanIsTwoCallsAndTheTraceUsageIsTheSumOfBoth"
        status: pass
    human_judgment: false
  - id: D5
    description: "T-15-25: canary in transcript, step ids, argument values, bound ids, step result content and tokens appears in no outcome toString, trace, attempt, turn, code, reason, event, strategy or Builder toString, ExecutedAction, HeldProposal, gate proposal, sink action or replan digest, across five runs; non-vacuous (canary is in the executor's arguments and in remainingStepIds; the outcome prints remainingSteps=1)"
    requirement: "PLAN-03"
    verification:
      - kind: unit
        ref: "PlanThenExecuteRedactionTest (5 tests); RedactionCanaryTest unchanged and green"
        status: pass
    human_judgment: false
---

# Phase 15 Plan 05: onFailed parity, truncation, limits and redaction Summary

The plan tier's provider-failure hook now behaves exactly like SingleShot's (proved case for case on both the plan and the replan call), a truncated plan escalates to the next tier instead of failing the command, the step cap, token ceilings and per-scenario model-call counts are pinned, and a canary sweep over five runs shows no step id, argument, bound id, transcript or app result leaks through any sink.

**Tasks:** 3. **Files:** 4 (3 created, 1 modified). **Commits:** 3.

## What was built

- **Truncation intercept (Task 1).** `PlanFlow.handle` now asks a small `truncated(result)` helper first: a `ModelResult.Success` whose `stopReason` is `MAX_TOKENS` returns `Escalate(MalformedExtraction, session.carry)` without reading the answer and without a replan. It runs for both the plan call and the replan call because both go through `handle`. PAUSE_TURN and CONTEXT_WINDOW_EXCEEDED still reach `decideResult`'s shared Failed path. `SingleShotOutcomes.kt` and `StrategyLimits.kt` are untouched (`git diff 21e9547` empty).
- **Outcome mapping test (Task 1).** 14 tests mirror `SingleShotOutcomeMappingTest`: default Failed with the same reason and details objects, hook-to-Escalate runs the next tier once, hook receives the same objects once per failing call, ModelUnsupported / Network / Timeout / `ProviderUnavailable(ON_DEVICE, ...)` pass through unchanged, a failing replan call reaches the hook once, the not-called list (NoToolCall failure and prose, Refusal in three shapes, missing credential, reached ceiling, gate hold, throwing executor), a throwing hook is `strategy_error`, the two truncation cases, and the pause / context-window / tool-use stops. The two truncation tests failed first (Failed instead of escalated), then passed after the intercept.
- **Limits test (Task 2).** 13 tests: the nine-step plan, a plan exactly at the cap, `maxSteps = 2` in the sent schema, the ceiling before the call, after the call, and at the replan decision (exactly at the ceiling is not replanned), `maxTokensPerTurn` and `reasoning` on both requests, `maxIterations = 2` allowing the replan and a five-step plan, and the turn / usage accounting for the clean, lookup, post-commit-failure and replanned plans.
- **Redaction test (Task 3).** 5 runs through a full pipeline with a listener, sink and gate; a `Sweep` collects the printed form of everything returned, delivered or printed, and asserts no `CANARY`; event fields and trace codes are checked against identifier shapes. Run (b) also checks every `ToolResultsMessage` in the replan request (digest carries engine codes and an index only). Run (e) is the RT-01 hold after a commit: the never-run step id is in `remainingStepIds` (the sanctioned consumer-data channel) while `toString` shows `remainingSteps=1`.

## Task Commits

| Task | Name | Commit |
|---|---|---|
| 1 | onFailed parity and the truncated-plan escalation | d4c7db2 |
| 2 | Step cap, token ceilings, call accounting | 1a14c50 |
| 3 | Redaction canary sweep | d7d1274 |

## Deviations from Plan

None in behavior. Task 2's contingency (change the strategy if a case exposes a defect) did not trigger: every case passed first time. Own-work fixes only: a private test class name clash (`Rig`, `CountingHook` collided with other files in the package; renamed), a wrong expected value in the first redaction run (both steps report the bound id, so the executed targetIds list has two entries), and detekt line-length wraps.

## Decisions Made

- The truncation check precedes the ceiling-crossed check. A truncated answer that also crosses the ceiling therefore escalates MalformedExtraction; the next tier then sees the ceiling reached and fails `BudgetExceeded(TOKENS)`, so the command still ends loudly with the budget reason. Putting the ceiling first would be equally valid; it was not chosen because the plan specifies the truncation intercept first.

## For plan 15-06 (surface review)

- No public surface change in this plan: `PlanThenExecuteStrategy.kt` gained one private function and an import; `git diff --quiet 21e9547 -- core/api.txt` exits 0.
- The Builder KDoc for `onFailed` already describes the SingleShot semantics this plan proves; no KDoc change was needed.

## Verification

- Task gates (host-safe recipe, one Gradle invocation each): Task 1 (`PlanThenExecuteOutcomeMappingTest`, `SingleShotOutcomeMappingTest`, `PlanThenExecuteReplanTest`) exit 0; Task 2 (`PlanThenExecuteLimitsTest`, `*PlanThenExecute*`) exit 0; Task 3 (`PlanThenExecuteRedactionTest`, `RedactionCanaryTest`) exit 0.
- Wave-end gate, one invocation: `:core:test :core:detekt :core:scanBannedConstructs :core:apiCheck` exit 0 (whole `:core` suite). Result XML: OutcomeMapping 14/14, Limits 13/13, Redaction 5/5.
- Acceptance greps: `StopReason.MAX_TOKENS` 1, `ON_DEVICE` 1, `onFailed` 13, `BudgetExceeded` 3, `turns.size` 5, `too_many_steps|maxSteps` 4, `CANARY` 20, `ToolResultsMessage` 3, `remainingStepIds` 2; `git diff --quiet 21e9547 -- core/.../strategy/singleshot core/.../strategy/StrategyLimits.kt` exit 0.
- Threat mitigations: T-15-23 (truncation intercepted before parsing on both calls, executor count 0), T-15-24 (ceiling before, after and at the replan, maxSteps, per-turn limit), T-15-25 (canary sweep with non-vacuity checks), T-15-26 (case-for-case parity matrix including on-device and replan-call failures).
- No device, live provider or test key was used; no live request was made.

## Issues Encountered

None.

## Self-Check: PASSED

Files verified present: the three new test files and the modified `PlanThenExecuteStrategy.kt`. Commits d4c7db2, 1a14c50, d7d1274 present in `git log`.
