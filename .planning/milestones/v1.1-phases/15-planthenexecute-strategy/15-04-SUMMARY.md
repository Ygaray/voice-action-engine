---
phase: 15-planthenexecute-strategy
plan: 04
subsystem: core/strategy/plan, core/pipeline, core/strategy
status: complete
tags: [planthenexecute, hold, rt-01, remaining-step-ids, escalation-suppression, plan-04]

requires:
  - phase: 15-03
    provides: PlanFlow with the single replan predicate, PlanRun.worked, outcomeOf
provides:
  - "RT-01 hold mapping: zero-commit hold -> Escalate(plan_step_held) suppressed by TierWalk; hold after a commit -> terminal Completed(partial = true)"
  - "Public CommandOutcome.Completed.remainingStepIds (the only public surface change)"
  - "Internal outcome channel: StrategyOutcome.Completed/Escalate internal primary constructors carrying the ids"
  - "RemainingStepIdsTest (8), PlanThenExecuteHoldTest (6), PlanThenExecuteSuppressionTest (8)"
affects: [15-05, 15-06, 15-07]

plan_head_before: 2b4a9957dbf10ac19eaf8338de96501bdddd32f1
actuals:
  tokens: 12000
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Extraction precedent (14-SURFACE-REVIEW): the longer primary constructor is internal, every public constructor keeps its exact signature, no default argument"
    - "Hold outcome keyed on PlanRun.committedSteps, never on the held step's position"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RemainingStepIdsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteHoldTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteSuppressionTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyOutcome.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanRun.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt
    - API.md

key-decisions:
  - "RT-01 implemented as ruled: case decided only by committedSteps (> 0 -> terminal Completed, == 0 -> Escalate suppressed by TierWalk); no last-step carve-out"
  - "remainingStepIds = plan.steps.drop(run.preparedSteps): a step counts as prepared only once handed to prepareGuarded, so the held and the failed step are not listed, an unresolved-reference step is"
  - "Ids ride on the outcome only when the tier ends the command (Completed, or an Escalate TierWalk suppresses); handUp, NoMatch-suppressed and commitHeld carry none"

requirements-completed: [PLAN-04, PLAN-01]

coverage:
  - id: D1
    description: "RT-01 case 2 (hold after a commit, mid-plan or last step): Completed(partial = true), commits kept, held proposal with the planning call id, attempt completed, no ESCALATION_SUPPRESSED, next tier never runs, one provider call; remainingStepIds [s3] mid-plan, empty for a last-step hold"
    requirement: "PLAN-01"
    verification:
      - kind: unit
        ref: "PlanThenExecuteHoldTest#aHoldAfterACommitMidPlanEndsTheCommandWithoutEscalating, aHoldOnTheLastStepAfterACommitEndsTheCommandWithNothingRemaining"
        status: pass
    human_judgment: false
  - id: D2
    description: "RT-01 case 1 (hold with nothing committed, single-step plan included): Escalate(Other(plan_step_held)) suppressed by TierWalk: partial Completed, zero commits, one held proposal, escalation_suppressed attempt, ESCALATION_SUPPRESSED code, no replan, no later tier"
    requirement: "PLAN-01"
    verification:
      - kind: unit
        ref: "PlanThenExecuteHoldTest#aHoldWithNothingCommittedEscalatesAndTheEngineSuppressesIt, aSingleStepPlanThatIsHeldEndsPartialWithASuppressedEscalation"
        status: pass
    human_judgment: false
  - id: D3
    description: "commitHeld applies only the held proposal; steps after the hold never run (executor.callCount stays 2) and its outcome has partial false and no remaining ids"
    requirement: "PLAN-01"
    verification:
      - kind: unit
        ref: "PlanThenExecuteHoldTest#commitHeldAppliesOnlyTheHeldProposalAndNeverResumesThePlan, theHeldStepIsNeverInTheRemainingList"
        status: pass
    human_judgment: false
  - id: D4
    description: "RT-01 point 4: ids reach the ending outcome through TierWalk (Completed and suppressed Escalate), are dropped on a hand-up, absent for NoMatch-after-work and commitHeld, never printed by any toString; public constructors of both StrategyOutcome classes unchanged"
    requirement: "PLAN-01"
    verification:
      - kind: unit
        ref: "RemainingStepIdsTest (8 tests)"
        status: pass
      - kind: command
        ref: ":core:apiCheck exit 0; git diff 21e9547 -- core/api.txt empty"
        status: pass
    human_judgment: false
  - id: D5
    description: "PLAN-04 / ROADMAP SC4: after step 1 committed, an executor error step, an applied error, an executor throw, a gate fault, a preview and an unresolved binding each end Completed(partial) with escalation_suppressed and the plan's reason, one provider call, next tier never runs, commit kept, correct remaining ids; the control (nothing applied) is handed up and the next tier runs"
    requirement: "PLAN-04"
    verification:
      - kind: unit
        ref: "PlanThenExecuteSuppressionTest (8 tests)"
        status: pass
    human_judgment: false
---

# Phase 15 Plan 04: Hold semantics (RT-01), remainingStepIds and the PLAN-04 suppression proof Summary

A plan tier now stops at the first hold and ends the command either as a suppressed escalation (nothing committed) or as a terminal partial Completed that keeps the commits (something committed), reports the steps that never ran in a new public `CommandOutcome.Completed.remainingStepIds`, and a Plan-specific test proves no later tier ever runs once a step has committed.

**Tasks:** 3. **Files:** 10 (3 created, 7 modified). **Commits:** 3 of this plan (4 measured since `plan_head_before`, see Notes).

## What was built

- **Outcome channel (Task 1).** `StrategyOutcome.Completed` and `.Escalate` got internal primary constructors carrying an internal `remainingStepIds`; every public constructor keeps its exact signature (the 14-SURFACE-REVIEW Extraction precedent, no default argument, `core/api.txt` untouched). `CommandOutcome.Completed` gained `public val remainingStepIds: List<String>`, KDoc'd, with the plan-hold case added to the `partial` KDoc and a count-only `remainingSteps=N` in `toString`. `TierWalk` passes the ids on the Completed branch and on `suppressed(...)`, drops them in `handUp`, and `HeldCommit`'s outcome carries none.
- **Hold mapping (Task 2).** `PlanRun` counts `preparedSteps` (incremented immediately before `prepareGuarded`) and `committedSteps` (a dispatch whose actions were all COMMITTED); `RunStop.Held` lost its last-step flag. `outcomeOf` maps a hold with `committedSteps > 0` to `Completed(null, null, true, remaining)` and with `committedSteps == 0` to `Escalate(Other("plan_step_held"), carry, remaining)`; failures and unresolved bindings keep their codes and now carry `remaining = plan.steps.drop(run.preparedSteps).map { it.id }`. API.md has the hold, partial-outcome and `remainingStepIds` sentences (including "steps after a hold are not run") and the surface-table row.
- **Suppression proof (Task 3).** Eight tests: six post-commit failure kinds, a post-commit hold, and a control showing the same failure with nothing applied is handed up.

## Task Commits

| Task | Name | Commit |
|---|---|---|
| 1 | Outcome channel and public remainingStepIds | 4f52746 |
| 2 | RT-01 hold mapping, API.md | 5020de5 |
| 3 | Plan-specific suppression test | 34af98e |

## Deviations from Plan

None. Two small test-quality fixes (own work, no rule needed): a detekt `UseCheckOrError` finding in the new suppression test (the scripted throws now use `error(...)`), and over-long lines wrapped.

## For plan 15-06 (surface review)

- **OI-1 is resolved by RT-01.** No last-step carve-out: a hold with nothing committed is `Escalate(Other("plan_step_held"))`, which TierWalk suppresses into a partial Completed; a hold after a commit (last step included) is a terminal partial Completed that keeps the commits and `outcome.held`; a single-step plan whose step is held is the case-1 shape (partial, suppressed escalation in the trace), not the old clean Completed.
- **New public member:** `CommandOutcome.Completed.remainingStepIds: List<String>` on a class whose constructor is already internal: one getter, +-only in the dump, not a data class, no default argument, no stub. Fill rule: the planned steps never handed to the executor because the tier stopped early, in plan order; the held step is not listed (it is in `held`); a failed step is not listed (it is in `executed`); a step stopped by an unresolved reference is listed; filled on every early stop that ends the command (a hold in either case, and a post-work failure TierWalk suppresses); empty when the plan ran to its end, after `commitHeld`, after a hand-up and for every other tier. `toString` prints the count only.
- **Main files outside `strategy/plan/` changed:** `core/strategy/StrategyOutcome.kt` (two internal constructors), `core/pipeline/CommandOutcome.kt` (public field), `core/pipeline/TierWalk.kt`, `core/pipeline/HeldCommit.kt`. `core/api.txt` is unchanged until the tag cut; the isolated dump must show only the added getter.
- Because the ids travel for any early stop that TierWalk suppresses (not only holds), narrowing the fill rule to holds only is a one-line change plus tests, before the tag only.

## Verification

- Task gates: Task 1 (`RemainingStepIdsTest`, `ApiShapeTest`, `EscalationSafetyTest`, `HeldCommitGuardsTest`, `HeldReportingTest`, `PlanThenExecute*`, `:core:apiCheck`) exit 0; Task 2 (`PlanThenExecuteHoldTest`, binding, replan, `HeldReportingTest`, `RemainingStepIdsTest`, `verify-docs-coverage.sh --only C20,C21`) exit 0; Task 3 (`PlanThenExecuteSuppressionTest`, `EscalationSafetyTest`) exit 0.
- Wave-end gate, one Gradle invocation with the host-safe recipe: `:core:test :core:detekt :core:scanBannedConstructs :core:apiCheck` exit 0 (whole `:core` suite). Test counts from the result XML: RemainingStepIdsTest 8/8, PlanThenExecuteHoldTest 6/6, PlanThenExecuteSuppressionTest 8/8.
- All task acceptance greps pass; `git diff --quiet 21e9547 -- core/api.txt` exit 0.
- Threat mitigations: T-15-19 (six failure cases plus the post-commit hold, next tier `executions == 0`), T-15-20 (stop at first hold, `commitHeld` leaves later steps unrun, executor count asserted), T-15-21 (held actions HELD, hold always partial, remaining ids named), T-15-35 (ids only in the data field; `toString` of both `StrategyOutcome` classes and `CommandOutcome.Completed` print no id, asserted), T-15-36 (hand-up and control assert an empty list).

## Notes

- `commits: 4` is the measured count of `plan_head_before..HEAD`; one of the four (`b687551`, "record orchestrator GO for D-04 live probe") is an orchestrator docs commit that landed before this plan's first task commit; the plan's own task commits are 3.
- No device or live provider was touched; no live request was made.

## Issues Encountered

None.

## Self-Check: PASSED

Files verified present: `RemainingStepIdsTest.kt`, `PlanThenExecuteHoldTest.kt`, `PlanThenExecuteSuppressionTest.kt`, and the seven modified files. Commits 4f52746, 5020de5, 34af98e present in `git log`.
