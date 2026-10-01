---
phase: 07-singleshot-strategy
plan: 06
subsystem: core-strategy-acceptance
tags: [single-shot, acceptance, confirm-flows, defer-mode, redaction, v1.0.0-blocker]
status: complete

requires:
  - phase: 07-singleshot-strategy
    provides: "SingleShotStrategy with limits and cache safety (07-05); commitHeld, PreApplyGate, CommitSink (Phase 2)"
provides:
  - "Neutral CT-shaped fixtures (SingleShotFixtures.kt): entriesArgs, FixtureCatalog, EntryVerdict, EntryMutation, FixtureResolver, deferModeGate, fixtureTier, acceptancePipeline"
  - "SingleShotAcceptanceTest: S1-S10 and the held-proposal interplay through the full pipeline"
  - "RedactionCanaryTest.noCanaryLeaksFromASingleShotRun: TEL-04 regression for a SingleShot-routed run"
affects: [07-07, 07-08, caltracker-android-9a-migration]

plan_head_before: da09d74d282dc41abf9661a06bd7fc5dbf370c19
commits: 3

actuals:
  tokens: 8000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "app thresholds live only in test fixtures and ride the opaque PendingMutation.context; the gate reads only that context"
    - "one Rig per scenario wires answer, resolver, gate, sink and pipeline, so scenario bodies stay about the assertions"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotFixtures.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotAcceptanceTest.kt
  modified:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt

key-decisions:
  - "No src/main change: every scenario passed against the shipped engine, so no gap is reported to the orchestrator."
  - "Fixture weak rule: unresolved, any flag (non_positive_quantity, unmatched), confidence < 0.7 or match score < 0.8; constants are private to the fixture file."
  - "Failing rows are injected through a label-keyed failure map on FixtureResolver, so the error and throw variants go through the real resolver path."

requirements-completed: [SHOT-03, SHOT-01]

coverage:
  jvm_tests_added: 12
---

# Phase 7 Plan 06: SingleShot acceptance (CT confirm flows) Summary

CT's weak-hold, batch, amended-confirm and deferred-commitHeld flows now pass as acceptance tests through SingleShot with neutral fixtures, plus the edge flows, the held-tier interplay and a SingleShot-routed redaction canary run. No main source changed.

## Scenario map

| Scenario | Test method (SingleShotAcceptanceTest unless noted) |
|---|---|
| S1 tracer: strong single auto-commits in the original run | `s1StrongSingleAutoCommitsInTheOriginalRun` |
| S2 weak single held, then deferred linked child run, idempotent repeat | `s2WeakSingleIsHeldThenCommittedLaterAsALinkedRun` |
| S3 all-strong 3-entry batch still held, gate asked once | `s3AllStrongBatchIsStillHeldAndTheGateIsAskedOnce` |
| S4 amended confirm applies only the replacement list | `s4AmendedConfirmAppliesOnlyTheReplacementList` |
| S5 failing row (error result and throw) isolated from siblings | `s5AFailingRowDoesNotPoisonItsSiblings` |
| S6 unmatched row proposed, recovered at amend, per-item skip, NoMatch on one and two tiers | `s6UnmatchedRowIsProposedThenRecoveredAtAmend` |
| S7 refusal fails with no gate call; prose escalates with null carry | `s7RefusalFailsAndProseEscalates` |
| S8 gate admit with an amended list applies it in the original run | `s8GateAdmitWithAnAmendedListAppliesItInTheOriginalRun` |
| S9 clarification ends the tier, resolver never invoked | `s9ClarificationEndsTheTierWithoutTheResolver` |
| S10 only the first of several tool calls is resolved | `s10OnlyTheFirstOfSeveralToolCallsIsResolved` |
| GATE-07 interplay: held proposal never reaches the next tier | `aHeldProposalNeverReachesTheNextTier` |
| TEL-04 canary for a SingleShot run | `RedactionCanaryTest.noCanaryLeaksFromASingleShotRun` |

## Task commits

1. Task 1 (tracer): `4dafa85` - fixtures and S1
2. Task 2: `234eb41` - S2-S6
3. Task 3: `0c7fa55` - S7-S10, held-tier interplay, canary run

## Verification

- `./gradlew :core:test --tests '*SingleShotAcceptanceTest' --tests '*RedactionCanaryTest' --offline`: 11 + 4 tests, 0 failures.
- `./gradlew check --offline` (all modules, all OkHttp legs, detekt zero baseline): exit 0.
- `git diff --stat da09d74 HEAD` touches only the three test files; `core/src/main` and `providers/` are untouched.
- Fixture file has no food, note, card or calorie words and names no CT or SB class.

## Deviations from Plan

None. The plan executed as written; no engine defect surfaced.

## Self-Check: PASSED

- FOUND: SingleShotFixtures.kt, SingleShotAcceptanceTest.kt, RedactionCanaryTest.kt (modified)
- FOUND commits: 4dafa85, 234eb41, 0c7fa55
