---
phase: 16-start-tier-selection
plan: 01
subsystem: testing
tags: [characterization, tier-walk, plan-parse, step-id-cap]

requires:
  - phase: 15-planthenexecute-strategy
    provides: PlanBinding.isStepId, the rejected-plan path (plan_rejected, one replan, Escalate(MalformedExtraction))
provides:
  - TierWalkLinearCharacterizationTest, a whole-walk pin of the v1.0.1 Linear and Fixed walk, green before TierWalk is edited
  - RT-01 step id cap (64 characters) as a parse-only bad_id rule
affects: [16-02, 16-03, 16-04, 16-05, 16-06]

actuals:
  tokens: 6000
  tasks: 3
  commits: 3

plan_head_before: 6f4bbb2fa713574cfadd88d6359b5ad2e05e6f03
commits: 3

tech-stack:
  added: []
  patterns:
    - "Whole-walk characterization: exact attempt triples, code list, event class sequence, usage, call count and carry identity pinned before a seam edit"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkLinearCharacterizationTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanParseTest.kt

key-decisions:
  - "STEP_ID_CAP is a private const val so it neither compiles to a public static field nor trips the banned limit-name pattern"
  - "The no-match fresh-start case uses a three-tier ladder (carry-escalating tier, NoMatch tier, completing tier) so the carry reset is actually observable"

requirements-completed: [ROUT-05]

duration: 25min
completed: 2026-10-06
status: complete
---

# Phase 16 Plan 01: Linear/Fixed walk characterization and RT-01 step id cap Summary

**Ten whole-walk characterization tests pin the v1.0.1 Linear/Fixed walk (attempts, codes, events, usage, carry) on unmodified main code, plus a 64-character plan step id cap through the existing bad_id path.**

## Performance

- **Duration:** about 25 min
- **Tasks:** 3 of 3
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `TierWalkLinearCharacterizationTest` has ten cases covering an SB-shaped ladder (zero-call head, escalating model tier, completing tier), a CT-shaped single tier, a head that handles the command, Fixed mid-ladder, offline-only capped Unhandled, maxTier capped, a no-match fresh start, an escalation suppressed after a write, a whole-ladder policy refusal, and the eligible-start invariant for Linear and Fixed. Each pins attempt triples (strategy, outcome, carryIn), the exact `trace.codes`, the exact `PipelineEvent` class sequence and the provider call count. The default `TierSelector.Linear` is exercised by never setting a selector.
- Both walk tasks landed green on the first run against unmodified main (`git diff 9c88961 -- core/src/main` was empty after Tasks 1 and 2), so no expectation was corrected.
- RT-01: `isStepId` is now `text.length <= STEP_ID_CAP && STEP_ID.matches(text)` with `private const val STEP_ID_CAP = 64`. One new test, `aStepIdLongerThanSixtyFourCharactersIsABadId`, pins 64 characters Valid, 65 characters `Rejected(bad_id, 0)`, and an overlong second step `Rejected(bad_id, 1)`. It was red first (65 characters accepted), then green.
- `PlanSchema.kt`, `PlanParse.kt`, `outcomeOf`, `remainingStepIds` and `core/api.txt` are untouched (P15 OI-1, DO NOT NARROW).

## Task Commits

1. **Task 1: SB-shaped Linear ladder pinned whole (tracer)** - `ad5ec91` (test)
2. **Task 2: every Linear/Fixed shape apps rely on** - `c41e123dfae75359f278bc955b74cc4001b3a3c9` (test)
3. **Task 3: RT-01 step id cap with boundary test** - `50247d4` (fix)

**Task 2 commit SHA for plan 16-02 to diff against:** `c41e123dfae75359f278bc955b74cc4001b3a3c9`
(`git diff c41e123dfae75359f278bc955b74cc4001b3a3c9 -- core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkLinearCharacterizationTest.kt` must show added lines only.)

## Deviations from Plan

- Task 2 case 6 (`aNoMatchStartsTheNextTierFresh`) uses ladder [single (escalates with a carry), mid (NoMatch), agentic] instead of [grammar, single (NoMatch), agentic]. It asserts the same fact the plan names (the tier after a NoMatch gets a null carry and carryIn false) and additionally pins that the NoMatch tier itself received the carry. With a NoMatch head the carry would be null anyway, so the plan's literal shape would not have observed a reset. No scope change.
- The test-only helper `Run.skips` (TierSkipped strategy and code pairs) was added; it asserts the order PolicyPreCheck emits skips in.

No auto-fixes (Rules 1-3) and no Rule 4 items.

## Verification

Wave-end gate, one Gradle invocation, host-safe recipe, exit 0:
`:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility`.
Acceptance greps and `git diff` checks: only `PlanBinding.kt` differs under `core/src/main` against 9c88961.

## Issues Encountered

None. No Gradle run was killed by earlyoom.

## Self-Check: PASSED

- FOUND: TierWalkLinearCharacterizationTest.kt (10 `@Test`), PlanBinding.kt `STEP_ID_CAP`, PlanParseTest method
- FOUND commits: ad5ec91, c41e123, 50247d4
