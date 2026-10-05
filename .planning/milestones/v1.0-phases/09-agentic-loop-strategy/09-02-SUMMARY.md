---
phase: 09-agentic-loop-strategy
plan: 02
subsystem: core-strategy
tags: [tool-executor, trace-codes, seam-signoff, agentic-loop]
requires:
  - phase: 09-01
    provides: O-1 batch cancellation and the shared limit helpers in core.strategy
provides:
  - ToolExecutor, the app's two-phase tool seam (prepare -> Finished | Mutation)
  - TraceCode.UNKNOWN_TOOL, TOOL_PREPARE_ERROR, READ_TOOL_MUTATION_REJECTED
  - ScriptedToolExecutor test fixture
  - evidence/seam-signoff.txt with verdict APPROVE-WITH-CHANGES (frozen for v1.0.0)
affects: [09-03, 09-04, 09-05, 09-06, 09-08]
tech-stack:
  added: []
  patterns: [fun interface seam reusing Extraction, scripted fixture failing with AssertionError when dry]
key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolExecutor.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedToolExecutor.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolExecutorSeamTest.kt
    - .planning/phases/09-agentic-loop-strategy/evidence/seam-signoff.txt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
key-decisions:
  - "Seam shapes (ToolExecutor, trace code names) were provisional until the relayed sign-off; the verdict froze them as built, so no rename and no SUBSTITUTIONS"
  - "Six behavior changes carried to later plans (see Sign-off below); none contradicts a locked decision"
requirements-completed: [LOOP-01]
status: complete
commits: 4
plan_head_before: a415f30da719b054482ae2d8e0ad5013024bfddf
actuals:
  tokens: 9000
  tasks: 3
  commits: 4
duration: one session
completed: 2026-10-01
---

# Phase 9 Plan 02: ToolExecutor seam, loop trace codes and seam sign-off Summary

**The app's two-phase `ToolExecutor` seam, three additive loop trace codes and a scripted executor fixture, proven against the shipped gate and sink, with the relayed sign-off recorded as APPROVE-WITH-CHANGES.**

## What was built

- `ToolExecutor` (`public fun interface`, `suspend fun prepare(call: Extraction, input: CommandInput): ToolStep`), reusing Phase 7's `Extraction`. KDoc states: the executor validates arguments and never writes; reads return `Finished(READ)`; a mutating tool may return `Finished` PREVIEW or ERROR; a mutation from a non-mutating tool is rejected before the gate; a throw becomes a fixed error result; calls are one at a time in model order. `ToolSpec` is untouched.
- `TraceCode.UNKNOWN_TOOL` (`unknown_tool`), `TOOL_PREPARE_ERROR` (`tool_prepare_error`), `READ_TOOL_MUTATION_REJECTED` (`read_tool_mutation_rejected`); wire values and distinctness pinned in `TraceTest.theAgenticLoopCodesHaveTheirSnakeCaseWireValues`. Calls dropped after a terminal call reuse `EXTRA_TOOL_CALLS_DROPPED`.
- `ScriptedToolExecutor` (testFixtures, unpublished): records each `Extraction`, logs `prepare:<toolName>` to an optional shared log, `sequence(log, vararg steps)` companion, `AssertionError` when the script runs dry.
- `ToolExecutorSeamTest`: tracer `anExecutorMutationComposesWithTheGateAndTheSink` (admitting gate: one COMMITTED action, applyCount 1), `aHoldingGateLeavesTheExecutorMutationUnapplied` (applyCount 0, one held proposal, `held=true`, `isError=false`, exact bytes `{"applied":false,"status":"held_for_confirmation"}`), `aReadStepIsNeverRecorded`, plus the three fixture behavior tests.

## Commits

- e2fc412 feat(09-02): ToolExecutor seam, scripted fixture and seam tracer
- c6f1ae9 feat(09-02): three loop trace codes and fixture behavior tests
- 47bd40a docs(09-02): relayed seam sign-off evidence
- (this SUMMARY commit)

## Sign-off (Task 3, already relayed and answered)

Verdict line, verbatim: `SIGNOFF: APPROVE-WITH-CHANGES`. The shapes were provisional until this verdict; they are now frozen for v1.0.0. No identifier is renamed, so the SUBSTITUTIONS block is "none". Items 1, 2, 4, 6, 7, 8, 9 approved as written (SB confirmed byte-exact userTurn parity and the 6/60000/4096 limits). Items 1/4 requested no change, so Tasks 1-2 files stand as planned.

CARRY lines (verbatim from evidence/seam-signoff.txt):

```
CARRY: 5 -> 09-05 : relax duplicate-id rejection to within-turn only (a turn that repeats an id within itself is still rejected whole as Failed(MalformedResponse)); cross-turn id reuse is allowed, so the OpenRouter call_0 regeneration must not fail; amend the cross-turn duplicate-id test and its validation accordingly
CARRY: 3 -> 09-06 : ExecutedAction must keep held, errored and previewed distinguishable (a status/kind, not just mutating=false); ActionKind already carries HELD, PREVIEW and IS_ERROR, so verify the loop's outcome mapping preserves them and add an additive status/kind only if the shape cannot express held today, decided before the v1.0.0 freeze
CARRY: precedence -> 09-04 and 09-06 : when the token ceiling and the iteration cap trip on the same turn, report BudgetExceeded(TOKENS); KDoc the order; add a test where both caps trip on the same turn and TOKENS wins
CARRY: 10 -> 09-08 : add edit_text_card to the CLN-02 deny-list
```

PLANS-AMENDED: not appended. The orchestrator amends 09-03..09-09 and appends `PLANS-AMENDED: yes`; 09-03 must not start before that.

## Verification

- `./gradlew :core:check --offline` green (detekt zero issues, banned-construct scan, explicit-API strict, all tests).
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=178` (no new sealed type, enum or data-shaped class).
- `git diff --stat` of `ToolSpec.kt`, `providers/` and `keystore/` against `plan_head_before` is empty.

## Deviations from Plan

- `ScriptedToolExecutor.sequence` throws its own `AssertionError` rather than relying on `ScriptedResponses.next()`, which throws `IllegalStateException`; the plan required an `AssertionError`, so it checks `remaining` first. [Rule 1 - fidelity to plan]
- Execution note: the dispatch's `BASE_SHA_PLACEHOLDER` was not substituted; the worktree HEAD `a415f30` (the latest Phase 9 wave-1 tracking commit) was used as the base and recorded as `plan_head_before`.
- Other than the above, plan executed as written. No git tag, no api.txt, STATE.md and ROADMAP.md untouched.

## Self-Check: PASSED

Created files exist (ToolExecutor.kt, ScriptedToolExecutor.kt, ToolExecutorSeamTest.kt, seam-signoff.txt); commits e2fc412, c6f1ae9, 47bd40a exist; seam-signoff.txt has exactly one line starting `SIGNOFF: `, as its last line.
