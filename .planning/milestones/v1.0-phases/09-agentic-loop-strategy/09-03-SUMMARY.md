---
phase: 09-agentic-loop-strategy
plan: 03
subsystem: core-strategy
tags: [agentic-loop, tool-dispatch, user-turn, limits, tracer]
requires:
  - phase: 09-02
    provides: ToolExecutor seam, loop trace codes, ScriptedToolExecutor, signed-off seam (APPROVE-WITH-CHANGES, PLANS-AMENDED yes)
provides:
  - AgenticLoopStrategy (public tier) with Builder and companion invoke
  - internal per-command run, turn classifier and sequential dispatch
  - AgenticLoopTestSupport (agenticLoop, loopPipeline with providerId, tool and turn builders)
affects: [09-04, 09-05, 09-06, 09-07, 09-08]
tech-stack:
  added: []
  patterns: [carrier class for the run, guarded helper around app code, token ceiling checked before the iteration cap]
key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticTurn.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopTestSupport.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopDispatchTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopUserTurnTest.kt
  modified: []
key-decisions:
  - "Token ceiling is checked before the final-iteration guard on a tool turn, so a turn that trips both reports BudgetExceeded(TOKENS) (seam sign-off precedence); KDoc'd on the class"
  - "The read-tool mutation rejection, strikes and terminal tools are left to 09-04 as planned; settle() in AgenticDispatch.kt is the single seam where they plug in"
  - "Within-turn duplicate call ids are not yet validated (09-05); no run-wide seen-id set exists, per the sign-off"
requirements-completed: [LOOP-01, LOOP-02, CLN-02]
status: complete
commits: 4
plan_head_before: ddcb42a9291b98c3e93eddbd3140d798d2b7359c
actuals:
  tokens: 21000
  tasks: 3
  commits: 4
duration: one session
completed: 2026-10-01
---

# Phase 9 Plan 03: Agentic loop tracer, user-turn seam and dispatch semantics Summary

**A bounded agentic tier over the app's ToolSpecProvider, ToolExecutor and UserTurnRenderer: one command runs a gated tool turn, feeds the results back and completes on the model's first text, with an invariant cached prefix and no hard-coded tool count.**

## What was built

- `AgenticLoopStrategy` (public, internal constructor) with `Builder` (`tooling` and `executor` required, `capabilities`, `userTurn` defaulting to `UserTurnRenderer.standard()`, `clock` defaulting to `CurrentZoneClock`) and `Companion.invoke`. No iteration, held-content, forced-tool or outcome-hook members. `toString` prints the id only.
- `AgenticRun` (internal): renders the user turn once, binds the model once, iterates `1..policy.maxIterations`. Every request is `ModelRequest(snapshot.system, history, snapshot.tools, ToolChoice.Auto(), policy.maxTokensPerTurn, CacheDirective(true), false)`. Pre-call `ceilingReached`; on a tool turn `ceilingCrossed`, then the final-iteration guard, then append the response message as received and one `ToolResultsMessage` in call order. Never calls `recordTurn`; returns only Completed or Failed; a loud `agentic_loop_exhausted` failure covers the structurally unreachable post-loop path.
- `AgenticTurn.kt`: decides a provider result before tool calls are read (failure passthrough with details, REFUSAL, the three stop failures, no-call END_TURN -> Completed(first text), no-call TOOL_USE -> MalformedResponse, other -> UnknownStop; calls present -> tool turn whatever the stop reason).
- `AgenticDispatch.kt`: sequential per-call dispatch through `session.submit`; unknown tool answered `{"status":"error","reason":"unknown_tool"}` is_error with `UNKNOWN_TOOL` and no executor call; executor faults contained by `guarded`, answered with a fixed notice and `TOOL_PREPARE_ERROR` (mutating tool -> ERROR action, read -> unrecorded).

## Tests

- `AgenticLoopDispatchTest` (11): tracer, one results message in call order, one-at-a-time order on a shared log, append-only history with same instances, reply = first text block, prose-only run, reads absent from executed but in the turn trace, unknown tool, 1/2/25 tools, builder requirements, id-only toString.
- `AgenticLoopUserTurnTest` (9): renderer output exact, SB-shaped renderer byte-for-byte on both requests (`Current local date-time: 2026-10-01T16:30:12 (UTC)\n\nVoice command: add two things`), standard default, carry/input/time seen by the renderer, render and tooling once per command, same system and tools every iteration, Auto with parallel calls and policy per-turn tokens, a snapshot naming a single-shot tool is still Auto.

## Verification

- Precondition: `evidence/seam-signoff.txt` ends `SIGNOFF: APPROVE-WITH-CHANGES` with `PLANS-AMENDED: yes`; the amended text was followed.
- `./gradlew :core:check --offline` green (detekt zero issues, banned-construct scan, explicit-API strict, ApiShapeTest, NoHardCodedConstantsTest, all tests). `:providers` is untouched, so no OkHttp matrix leg was needed.
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=181` (178 -> 181: the strategy, its Builder and its companion; still exactly seven sealed types).
- `git diff --stat` of `providers/`, `keystore/`, `ToolSpec.kt` and `UserTurn.kt` against `plan_head_before` is empty.
- Seam-signoff verdict line it started from: `SIGNOFF: APPROVE-WITH-CHANGES`. No detekt-driven file split was needed.

## Commits

- bc8f115 feat(09-03): agentic loop tracer - tool turn through the gate and sink to a prose answer
- 7f0a499 test(09-03): user turn seam, byte-exact framing and invariant request shape for the loop
- 3af9e59 test(09-03): loop dispatch semantics - order, one results message, append-only history, reply rule, tool-count independence
- (this SUMMARY commit)

## Deviations from Plan

None. Tasks 2 and 3 added tests only; the Task 1 implementation already satisfied them, so `AgenticLoopStrategy.kt` and `AgenticDispatch.kt` were not changed afterwards. Execution notes: heredoc and compound git commands are refused in this worktree, so the test file was rewritten with the Write tool and git commands were run one at a time. No git tag, no api.txt; STATE.md and ROADMAP.md untouched.

## Self-Check: PASSED

Created files exist (the three main files, the support file and the two test classes); commits bc8f115, 7f0a499 and 3af9e59 exist.
