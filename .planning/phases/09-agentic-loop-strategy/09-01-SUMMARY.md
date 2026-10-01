---
phase: 09-agentic-loop-strategy
plan: 01
subsystem: core
tags: [commit, cancellation, limits, singleshot, o-1]
status: complete
requires: []
provides:
  - "CommitCoordinator.applyAll stops a batch at the next item once the caller is cancelled (O-1 FIXED)"
  - "internal core.strategy.StrategyLimits: CurrentZoneClock, ceilingReached, ceilingCrossed, iterationBudgetFailure, stopFailure"
affects: [09-02, 09-03]
tech-stack:
  added: []
  patterns: ["per-item ensureActive inside the single write path; coordinator owns cancel-safety"]
key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyLimits.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/BatchCancellationTest.kt
    - .planning/phases/09-agentic-loop-strategy/evidence/o1-disposition.txt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
    - config/detekt/detekt.yml
requirements: [LOOP-03, LOOP-02]
key-decisions:
  - "O-1 fixed additively and internally; no sign-off needed"
  - "detekt MatchingDeclarationName excludes StrategyLimits.kt (justified one-line comment), following the Guarded.kt precedent"
plan_head_before: 77c8900766f1d1463abc87e6ac926dc1f34da21a
commits: 3
actuals:
  tokens: 9000
  tasks: 3
  commits: 3
---

# Phase 9 Plan 01: O-1 batch cancellation and shared limit helpers Summary

A cancelled caller now stops a multi-item batch at the next item (gated and held paths), and the ceiling, clock and stop-leaf helpers the agentic loop will reuse live in one internal file that SingleShot already uses.

## What was built

- **Task 1 (tracer, 39a761b):** `CommitCoordinator.applyAll` calls `currentCoroutineContext().ensureActive()` before each item. The test `aCancelBetweenBatchItemsStopsTheRestAndKeepsTheCommittedOne` submits one two-item `ToolStep.Mutation` through a real `commandPipeline`; the sink hook cancels the execute job on item 0's COMMITTED action.
  - **RED before the fix:** failed with `expected:<0> but was:<1>` (item B was still applied).
  - **GREEN after the fix:** item B applyCount 0, item A applyCount 1, one COMMITTED sink action, one `RunTermination.Cancelled` close whose commits and executed hold item A only, job cancelled. `BatchIsolationTest` and `CommitPathTest` stay green.
- **Task 2 (92cbf4d):** the held path (`commitHeld`) test `aCancelBetweenHeldBatchItemsStopsTheRestAndKeepsTheCommittedOne` (child run closes Cancelled with item A, trace has `commit_held_cancelled`), the control `anUncancelledBatchStillAppliesEveryItem`, and `evidence/o1-disposition.txt`.
- **Task 3 (34c14d6):** pure move into `core/strategy/StrategyLimits.kt` (all internal) plus new `iterationBudgetFailure()` and `stopFailure(StopReason)`. `SingleShotOutcomes.stopOutcome` keeps `REFUSAL` first, then `stopFailure`, then the tool-call presence check. No SingleShot test edited.

## Verification

- `./gradlew :core:test --tests '*BatchCancellationTest' --tests '*BatchIsolationTest' --tests '*CommitPathTest' --tests '*HeldCommitGuardsTest' --offline -q`: green.
- `./gradlew :core:test --tests '*SingleShot*' --tests '*NoHardCodedConstantsTest' --tests '*ApiShapeTest' --offline -q`: green.
- `./gradlew :core:detekt :core:scanBannedConstructs --offline -q`: green. `./gradlew :core:check --offline -q`: exit 0.
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK ... classes=177` before Task 3 and `classes=177` after (unchanged).
- `git log PLAN_BASE..HEAD -- providers keystore`: empty. `git diff --stat PLAN_BASE HEAD -- core/src/test` lists only `BatchCancellationTest.kt`.
- O-1 disposition line: `O-1 DISPOSITION: FIXED`.
- Providers OkHttp matrix legs were not run: this plan changes only `:core` and no `:providers` source.

## Deviations from Plan

**1. [Rule 3 - Blocking] detekt MatchingDeclarationName on StrategyLimits.kt.** The file's only top-level class-like declaration is `CurrentZoneClock`, so detekt wanted the file named after it, while the plan fixes the file name and its contents. Added `'**/strategy/StrategyLimits.kt'` to the existing `MatchingDeclarationName` excludes in `config/detekt/detekt.yml` with a one-line justification (same precedent as `internal/Guarded.kt`). This file is not in the plan's `files_modified`. No baseline, no suppression annotation.

**2. [Rule 1 - Bug in own work] Evidence HEAD hash.** The first draft of `o1-disposition.txt` carried a mistyped HEAD hash; corrected to the real full hash (`39a761b783632b490ef8cbd30608eb82f3ba2bc6`) before it was committed.

## Notes

- `STATE.md` and `ROADMAP.md` were not touched (the orchestrator owns them). No tag, no `api.txt`.
- Output noise: `:core:check` prints detekt findings for `config/negative-controls/detekt/ForbiddenImports.kt`; that is the repo's negative-control task proving the gate fails, and the build exits 0.

## Self-Check: PASSED

- Files present: StrategyLimits.kt, BatchCancellationTest.kt, o1-disposition.txt (last line `O-1 DISPOSITION: FIXED`).
- Commits present: 39a761b, 92cbf4d, 34c14d6.
