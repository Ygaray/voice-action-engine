PHASE GATE: PASS

---
phase: 07-singleshot-strategy
plan: 08
subsystem: verification-evidence
tags: [phase-gate, evidence, limits, api-surface]
requires: [07-07]
provides:
  - offline phase gate record (evidence/phase-gate.txt)
  - named limits tests for the Phase 11 Runtime Decision precondition (evidence/singleshot-limits-tests.txt)
  - :core public surface after Phase 7 with seam sign-off (evidence/singleshot-surface-review.txt)
affects: [phase-10-VER-03, phase-11-v1.0.0-cut]
key-files:
  created:
    - .planning/phases/07-singleshot-strategy/evidence/phase-gate.txt
    - .planning/phases/07-singleshot-strategy/evidence/singleshot-limits-tests.txt
    - .planning/phases/07-singleshot-strategy/evidence/singleshot-surface-review.txt
  modified: []
decisions: []
status: complete
plan_head_before: 35e8e920681dfe96b08d9a0ea3b682171a4fe7db
commits: 2
actuals:
  tokens: 0
  tasks: 2
  commits: 2
requirements-completed: [SHOT-01, SHOT-02, SHOT-03]
---

# Phase 7 Plan 08: Phase Gate and Limits Evidence Summary

Offline Phase 7 gate is green on the merged tree, and three evidence files record the gate, the named SingleShot limits tests, and the :core public surface frozen by the seam sign-off. No source, test or build change.

## Evidence paths

- `.planning/phases/07-singleshot-strategy/evidence/phase-gate.txt` (last line `PHASE GATE: PASS`)
- `.planning/phases/07-singleshot-strategy/evidence/singleshot-limits-tests.txt`
- `.planning/phases/07-singleshot-strategy/evidence/singleshot-surface-review.txt`

## Results

| Command | Exit | Decisive line |
|---|---|---|
| `./gradlew check --offline` | 0 | BUILD SUCCESSFUL |
| `scripts/review-api-surface.sh --expect-sealed-complete` | 0 | API SURFACE OK, seven allowed sealed types, 177 classes |
| `scripts/verify-repo-hygiene.sh` | 0 | HYGIENE OK |
| `scripts/verify-negative-controls.sh` | 0 | negative-control failures: 0 |

Test counts (genuine run, zero failures, zero skipped): core 526; providers 430 on each of the 4.12.0, 5.2.1 and 5.5.0 legs; keystore 96.

Limits evidence: all ten SingleShotLimitsTest methods and all five SingleShotUserTurnTest methods are listed by name with results read from the JUnit XML (10/10 and 5/5 passed). NoHardCodedConstantsTest is green (12/12): no literal 60000 or 4096 in core main outside TierPolicy.kt.

Surface review: all Phase 7 public additions are in the dump (ModelRequest 7-arg constructor and singleToolCall, TraceCode.EXTRA_TOOL_CALLS_DROPPED, ToolSpecProvider, ToolingSnapshot, OutcomeResolver, Extraction, Resolution and four leaves, UserTurnRenderer, UserTurnContext, SingleShotStrategy and Builder); no internal type and no post-commit edit API; `SIGNOFF: APPROVE` quoted from seam-signoff.txt.

Carry-forwards (recorded in phase-gate.txt): Phase 10 VER-03 (assert `disable_parallel_tool_use` and 200 in the Anthropic smoke; optional two cheap calls for `cache_read_input_tokens > 0`, opt-in via `with-test-keys`); Phase 5 follow-up shipped in 07-02; Gate-1 N/A; no live leg in Phase 7. Also from the seam sign-off: Phase 9 AgenticLoopStrategy must accept the same `userTurn: UserTurnRenderer`.

## Deviations from Plan

None to code. Two process observations, no impact on the result:

1. `scripts/verify-negative-controls.sh` deliberately forces the OkHttp guard tests red (expected=9.9.9) and leaves failing JUnit XML in `providers/build/test-results`. Test counts were therefore taken from a clean re-run (`cleanTest` on all four test tasks, then `./gradlew check --offline --no-build-cache`, exit 0), not from the stale XML. Recorded in phase-gate.txt.
2. A filtered `:core:test --tests '*NoHardCodedConstantsTest'` run replaced the core XML with a single suite; the core tests were re-run in full (`:core:cleanTest :core:test --no-build-cache`, 526 tests, 0 failures) before the limits evidence was generated from the XML.

The persisted plan-head ledger file under the shared git dir could not be written (worktree path guard), so `plan_head_before` is the base sha verified at start (HEAD matched the expected sha). `commits: 2` counts the two task commits; this SUMMARY is committed separately.

## Compliance

No api.txt (`git ls-files -- '*api.txt'` empty), no tag, no live call, no `with-test-keys`, 07-VALIDATION.md, contract section 11 ledger and STATE.md/ROADMAP.md untouched.

## Self-Check: PASSED

- phase-gate.txt, singleshot-limits-tests.txt, singleshot-surface-review.txt exist
- task commits f0f83b6 and 0617467 exist
