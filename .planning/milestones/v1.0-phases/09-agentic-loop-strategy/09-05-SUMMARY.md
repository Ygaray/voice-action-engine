---
phase: 09-agentic-loop-strategy
plan: 05
subsystem: core/strategy/agentic
tags: [agentic-loop, validation, budget-guards, limits, tests]
requires: [09-04]
provides:
  - internal whole-turn validation (AgenticValidation.isAnswerable)
  - SB guard order on every tool turn (ceiling, last-turn guard, validation, dispatch)
  - AgenticLoopStopLeavesTest (one test per failure leaf plus the stop + tool_calls guard)
  - AgenticLoopLimitsTest (6 / 60000 / 4096 limits, the v1.0.0 precondition)
affects: [09-06, 09-07, 09-08, 09-09]
tech-stack:
  added: []
  patterns: [elvis chain of guards returning StrategyOutcome?, within-turn only id validation]
key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticValidation.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopStopLeavesTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopLimitsTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopGuardsTest.kt
key-decisions:
  - "Duplicate tool-call ids are rejected within a turn only; an id reused from an earlier turn dispatches normally (seam sign-off item 5). The run keeps no run-wide id memory."
  - "Guard order on a tool turn: ceilingCrossed, last-turn guard, whole-turn validation, dispatch. A same-turn tie between the token ceiling and the iteration cap reports BudgetExceeded(TOKENS)."
requirements-completed: [LOOP-02, LOOP-03]
status: complete
plan_head_before: 46ce728fa466bfaa2b9808ff74736deb114cb884
commits: 3
actuals:
  tokens: 40000
  tasks: 3
  commits: 3
metrics:
  completed: 2026-10-01
---

# Phase 9 Plan 05: Guards, Stop Leaves and Limits Summary

Whole-turn validation (ids distinct within a turn, no cross-turn memory), SB's guard order with the ceiling winning ties, a per-leaf failure matrix, and the 6 / 60000 / 4096 limits as a named test class.

## What was built

- `AgenticValidation.kt` (internal): `isAnswerable(calls)` is true when the turn has at least one call and its ids are distinct. It takes no run state, so an id reused across turns is legal. It is a superset of the `ToolResultsMessage` constructor's rules, so a rejected turn can never reach the constructor (no `Unexpected`, no `strategy_error`).
- `AgenticLoopStrategy.kt`: `toolTurn` now chains `ceilingCrossed`, `lastTurnGuard`, `validated`, `dispatchTurn`. A malformed turn fails `MalformedResponse` before any executor, gate or sink call.
- `AgenticLoopGuardsTest` extended (6 strike tests plus 8 new: reused-id accepted, within-turn duplicate rejected incl. after a commit, tool_use without calls, never reaches the constructor, ceiling before dispatch, final-iteration guard, SB order, same-turn tie).
- `AgenticLoopStopLeavesTest` (15 tests): HttpError keeps `httpStatus`, Network, MalformedResponse, pass-through RateLimited details, MaxTokens (even with calls), Refusal (stop and failure), PauseTurn, ContextWindowExceeded, UnknownStop, ToolFailure, END_TURN/OTHER with calls is a tool turn, every leaf after a commit reports the commit, no leaf collapsed.
- `AgenticLoopLimitsTest` (14 tests): method-by-method port of `SingleShotLimitsTest` plus iteration guard at 2 and 6, per-turn limit on every request, ceiling checked each iteration, end-of-turn over ceiling completes, each routed turn counted once.

## Guard order as implemented

Per iteration: pre-call `ceilingReached` (>=); after the response: failure/stop leaves (`decideTurn`), then `ceilingCrossed` (>), then the last-turn guard (terminal-first exempt), then `isAnswerable`, then dispatch.

## Verification

- `./gradlew check --offline` green (detekt zero issues, scanBannedConstructs, OkHttp matrix legs, all modules).
- AgenticLoopLimitsTest XML: `tests="14" skipped="0" failures="0" errors="0"`.
- AgenticLoopGuardsTest XML: `tests="14" skipped="0" failures="0" errors="0"`.
- AgenticLoopStopLeavesTest: 15 `@Test`, all passing.
- `git log 46ce728..HEAD -- providers keystore` is empty. No public symbol, api.txt or tag added.

## Deviations from Plan

None in behavior. `AgenticTurn.kt` needed no change: every leaf test passed against the 09-04 classifier. `AgenticLoopLimitsTest` passed without any main change, so Task 3 touched no main source.

## Notes

- A tool turn landing exactly on the ceiling is dispatched and commits, then the next iteration's pre-call check refuses with `BudgetExceeded(TOKENS)`; the outcome still lists the commit (pinned by `theCeilingIsCheckedBeforeEveryIteration`).
- `scripts/review-api-surface.sh` was not run: the plan adds no public API.

## Self-Check: PASSED

Created files exist (AgenticValidation.kt, AgenticLoopStopLeavesTest.kt, AgenticLoopLimitsTest.kt); commits 7c617be, 304d142, 21faf62 exist.
