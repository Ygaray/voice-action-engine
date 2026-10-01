---
phase: 07-singleshot-strategy
plan: 05
subsystem: core-strategy
tags: [single-shot, limits, token-ceiling, cache-safety, v1.0.0-blocker]
status: complete

requires:
  - phase: 07-singleshot-strategy
    provides: "SingleShotStrategy with the execute stage chain (07-04), seam sign-off APPROVE (07-03)"
provides:
  - "Pre-call token ceiling stage: Failed(BudgetExceeded(TOKENS)) with zero provider calls when tokensUsed >= policy.tokenCeiling"
  - "Post-call token ceiling stage: Failed(BudgetExceeded(TOKENS)) before terminal routing, the resolver, the gate or any write when tokensUsed > policy.tokenCeiling"
  - "SingleShotLimitsTest: the ten named 6 / 60000 / 4096 tests the v1.0.0 cut requires"
  - "SingleShotUserTurnTest: the five named cache-prefix and user-turn proofs (D-03)"
affects: [07-06, 07-07, 07-08, phase-11]

plan_head_before: fcd33ca7803b1213c14b06dd323c2a39113b3d3c
commits: 4

actuals:
  tokens: 14000
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "ceiling stages live as internal top-level functions beside the outcome mapping, so the strategy class stays under detekt TooManyFunctions and each stage is an elvis link"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotLimitsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotUserTurnTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt

key-decisions:
  - "Boundary used: pre-call refuses at or over the ceiling (>=), post-call fails strictly over it (>). evidence/seam-signoff.txt has no CARRY line for the token-ceiling boundary (its only CARRY is the Phase 9 userTurn note), so the plan's default boundary stands unchanged."
  - "No literal limit value appears in main source: the strategy reads session.policy.tokenCeiling and session.policy.maxTokensPerTurn only."

requirements-completed: [SHOT-01]

coverage:
  - id: D1
    description: "Tracer (limits precondition): after an earlier tier spends exactly TierPolicy.DEFAULT.tokenCeiling the SingleShot tier fails with BudgetExceeded(TOKENS), making zero provider calls, no resolver call and no gate call; one token lower makes exactly one call; a custom ceiling is read from the session policy"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "core SingleShotLimitsTest#atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall, #oneTokenBelowTheDefaultCeilingTheCallIsMade, #aCustomCeilingIsReadFromTheSessionPolicy"
        status: pass
    human_judgment: false
  - id: D2
    description: "Contract limits 6 / 60000 / 4096: defaults asserted, the policy's per-turn limit is sent as request.maxTokens (default and custom 777), a response crossing the ceiling fails before the resolver, gate or any write, a response landing exactly on it is still resolved, and exactly one provider call and one recorded turn happen at maxIterations 2 and 6 for NoToolCall failure, prose, resolved call, refusal and terminal call"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "core SingleShotLimitsTest#defaultsAreTheContractLimits, #defaultPolicySendsTheDefaultPerTurnTokenLimit, #customPerTurnLimitIsSentUnchanged, #aResponseThatCrossesTheCeilingFailsBeforeTheResolverOrAnyWrite, #aResponseLandingExactlyOnTheCeilingIsStillResolved, #exactlyOneProviderCallAtMinimumIterations, #exactlyOneProviderCallAtDefaultIterations"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-03 cache safety: per-command transcript, language and date reach only the single user message; system text and tool instances are identical across commands; the user message is the renderer's output byte for byte; the default framing, carry, input and clock reach the renderer"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "core SingleShotUserTurnTest (5 tests)"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-01
---

# Phase 7 Plan 05: SingleShot Limits and Cache Safety Summary

**SingleShot now enforces the 6 / 60000 / 4096 limits from the session policy alone (pre-call and post-call token ceiling, per-turn max tokens, exactly one provider call), covered by ten named tests, and a second set of five tests proves per-command text never touches the cached prefix.**

## Accomplishments

- Pre-call stage (`ceilingReached`) is the first link of `execute`: at or over `policy.tokenCeiling` the tier returns `Failed(BudgetExceeded(BudgetBound.TOKENS))` before the tooling provider, the model handle or the renderer run, so nothing is spent or bound.
- Post-call stage (`ceilingCrossed`) runs in `route` after the first call is identified and the extra-calls code is recorded, before terminal routing, the resolver or any submit. Strictly over the ceiling fails; landing exactly on it is still resolved. Refusal, truncation and no-tool-call answers keep their own mappings.
- `maxTokens` already came from `session.policy.maxTokensPerTurn` (07-04); the tests now pin it for default (4096) and custom (777).
- Exactly one provider call at `maxIterations = 2` and at the default 6, verified with one-result fake scripts (a second call would throw) and one recorded turn per attempt.
- D-03 proven: one strategy instance with a stepping clock, two commands with different transcript, language and time, gives equal `system`, pairwise-identical tool instances, differing user texts, and neither system contains either transcript or `2026-`.

## Boundary used

Pre-call `>=`, post-call `>`. `evidence/seam-signoff.txt` carries no `CARRY:` line for the token boundary (its only CARRY is the Phase 9 `userTurn` note), so no adjustment was needed.

## The fifteen test method names

SingleShotLimitsTest (10): `defaultsAreTheContractLimits`, `defaultPolicySendsTheDefaultPerTurnTokenLimit`, `customPerTurnLimitIsSentUnchanged`, `atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall`, `oneTokenBelowTheDefaultCeilingTheCallIsMade`, `aCustomCeilingIsReadFromTheSessionPolicy`, `aResponseThatCrossesTheCeilingFailsBeforeTheResolverOrAnyWrite`, `aResponseLandingExactlyOnTheCeilingIsStillResolved`, `exactlyOneProviderCallAtMinimumIterations`, `exactlyOneProviderCallAtDefaultIterations`.

SingleShotUserTurnTest (5): `twoCommandsShareTheSameSystemAndToolsBytes`, `theUserMessageIsExactlyTheRenderersOutput`, `theDefaultRendererFramesDateZoneAndTranscript`, `theRendererSeesTheEscalatingTiersCarryAndTheInput`, `perCommandTextNeverReachesTheSystemPrompt`.

## Task Commits

1. Task 1 (tracer): `e970259` feat(07-05): refuse before the call when the run has reached the token ceiling
2. Task 2: `4736ac9` feat(07-05): fail before resolving when the one call crossed the token ceiling
3. Task 3: `2ee3deb` test(07-05): prove per-command text reaches only the user message
4. Style fix: `0d1054c` style(07-05): shorten a test KDoc line to satisfy detekt
5. This summary: committed last.

plan_head_before: `fcd33ca7803b1213c14b06dd323c2a39113b3d3c`; measured 4 commits before this summary (`git rev-list --count fcd33ca..HEAD`).

## Verification

- Tracer gate re-run after Task 1: `:core:test --tests '*SingleShotLimitsTest' --tests '*SingleShotRequestTest'` green. Tracer verified, expansion proceeded.
- Task 2 was test-first: the post-call test `aResponseThatCrossesTheCeilingFailsBeforeTheResolverOrAnyWrite` ran RED (1 of 10 failed) before the post-call stage existed, then green.
- `./gradlew check --offline` exit 0 across all modules including the OkHttp matrix legs; `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK ... classes=177` (no public API change).
- Test results: SingleShotLimitsTest 10/10, SingleShotUserTurnTest 5/5, NoHardCodedConstantsTest green. `grep -cE '60_?000|4_?096'` on SingleShotStrategy.kt prints 0.
- `git log fcd33ca..HEAD -- providers/` prints nothing. No api.txt, no tag, no change to the contract, the ledger, STATE.md, ROADMAP.md or 07-VALIDATION.md.

## Deviations from Plan

**1. [Rule 3 - detekt gate] Ceiling stages live in SingleShotOutcomes.kt, not in the strategy class**
- Adding the two private stage functions plus a failure builder to `SingleShotStrategy` took the class to 13 functions, over detekt's TooManyFunctions threshold of 12 (it failed at 12 already). The stages are now `internal fun ceilingReached` / `ceilingCrossed` in `SingleShotOutcomes.kt`, called from the strategy as elvis links, with identical behavior. Consequence: the Task 2 acceptance grep `grep -c 'tokensUsed' SingleShotStrategy.kt` (at least 2) prints 0 for the strategy file; the two `tokensUsed` comparisons sit in `SingleShotOutcomes.kt` of the same package. `policy.tokenCeiling` and `BudgetBound.TOKENS` greps in the Task 1 criteria are satisfied in that file, and the strategy file still contains no limit literal. `execute` was split into `execute` (ceiling check) and a private `withTooling`, and `route` into `route` and `dispatch`, to keep ReturnCount at two.

**2. [Minor] Style commit**
- One test KDoc line exceeded the detekt line length; fixed in a separate commit `0d1054c`.

No main-source defect surfaced from Task 3's tests; UserTurn.kt is unchanged.

## Deferred Issues

None.

## Self-Check: PASSED

- Created files exist: SingleShotLimitsTest.kt and SingleShotUserTurnTest.kt; modified: SingleShotStrategy.kt and SingleShotOutcomes.kt.
- Commits `e970259`, `4736ac9`, `2ee3deb`, `0d1054c` exist on `worktree-agent-a93185b8677d29d0a`.
