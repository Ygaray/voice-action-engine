---
phase: 09-agentic-loop-strategy
plan: 04
subsystem: core-strategy
tags: [agentic-loop, gate-path, strikes, terminal-tool, budget-precedence]
requires:
  - phase: 09-03
    provides: AgenticLoopStrategy tracer, AgenticRun, sequential dispatch through session.submit, AgenticLoopTestSupport
provides:
  - read-tool mutation rejection (a tool not declared mutating can never reach the gate)
  - per-tool-name 2-strike abort that lets the rest of the turn run
  - A19 terminal tool exit with dropped-call partial and final-iteration exemption
  - class KDoc stating the ceiling-before-iteration-limit order
affects: [09-05, 09-06, 09-07, 09-08]
tech-stack:
  added: []
  patterns: [per-command dispatch context holding the strike map, walk stopped at first terminal call, ceiling checked before the iteration guard]
key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopGateTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopGuardsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopTerminalTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
key-decisions:
  - "Strike counter lives in DispatchContext (built once per command), never on the strategy; STRIKES_TO_ABORT = 2 is a private constant in AgenticDispatch.kt; any is_error result counts, a held result does not"
  - "dispatchCalls finds the first terminal call up front, dispatches only the calls before it, records EXTRA_TOOL_CALLS_DROPPED once when calls follow it, and returns a TurnDispatch (internal) carrying results, struckOut, terminal and dropped"
  - "The strike abort is checked before the terminal exit in AgenticRun.dispatchTurn, so Failed(ToolFailure) beats a terminal call of the same turn"
  - "A turn whose first call is terminal skips the final-iteration guard; any other tool turn on the last permitted iteration still fails ITERATIONS before dispatch"
requirements-completed: [LOOP-01, LOOP-02]
status: complete
commits: 4
plan_head_before: 249adb2dc356e40037effb534a77c776eacb31b0
actuals:
  tokens: 26000
  tasks: 3
  commits: 4
duration: one session
completed: 2026-10-01
---

# Phase 9 Plan 04: Gate path, 2-strike abort and terminal tool Summary

**The agentic loop now answers the whole gate path (read-tool mutations dropped, held/rejected/previewed/faulty calls reported with fixed notices), aborts on a second error from the same tool after finishing the turn, and ends on a terminal tool call with its edge rules, without adding any public symbol.**

## What was built

- `AgenticDispatch.kt` (internal): `guardWrites` drops a `ToolStep.Mutation` from a tool whose `ToolSpec.mutating` is false, records `READ_TOOL_MUTATION_REJECTED` and submits a READ `Finished` with the fixed `{"status":"error","reason":"not_a_mutating_tool"}` is_error content (never recorded, never gated). `DispatchContext` now owns the strike map (`strike`, `struckOut`) and `specOf`. `dispatchCalls` returns a `TurnDispatch` (results, struckOut, terminal, dropped); the first terminal call stops the walk and the calls after it are never prepared (`EXTRA_TOOL_CALLS_DROPPED` once).
- `AgenticLoopStrategy.kt`: `dispatchTurn` returns `Failed(ToolFailure())` when a tool struck out (after the whole turn ran, results appended), otherwise `Completed(null, TerminalCall, dropped)` for a terminal call (no results message, no further request), otherwise continues. `lastTurnGuard` exempts a turn whose first call is terminal. Held, rejected, preview and apply-error handling needed no loop code: `DispatchResult.contentForModel` and `isError` are forwarded unchanged.
- Class KDoc: "When the token ceiling and the iteration limit trip on the same turn, the failure is the token ceiling's; the ceiling is checked first", plus the strike and terminal behavior in prose.

## Where things live

- Strike counter: `DispatchContext` (AgenticDispatch.kt), counted in `dispatchCall`; abort decided in `AgenticRun.dispatchTurn` (AgenticLoopStrategy.kt).
- Terminal exit: `dispatchCalls` (AgenticDispatch.kt) finds it and drops later calls; `AgenticRun.dispatchTurn` turns it into the Completed outcome.
- Ceiling-before-iteration-cap order: `AgenticRun.toolTurn` (`ceilingCrossed` then `lastTurnGuard` then `dispatchTurn`), KDoc'd on `AgenticLoopStrategy`. The named same-turn test is 09-05's.

## Tests

- `AgenticLoopGateTest` (8): read-tool mutation never reaches the gate, gate/apply/sink before the next provider call, held notice with isError false, rejected and previewed calls never gated, throwing prepare yields the fixed notice with a canary sweep, apply error content, suspending gate keeps dispatch sequential.
- `AgenticLoopGuardsTest` (6): committed sibling recorded on abort, per-name counting across turns, two different tools once each, unknown tool strikes, prepare fault plus apply error, held is not a strike.
- `AgenticLoopTerminalTest` (8): terminal-only, commit carried, hold carried, later calls dropped and partial, terminal on final iteration completes, call before terminal on final iteration fails ITERATIONS, strike abort beats terminal, budget stop after a commit stays Failed with the commit.
- Prior `AgenticLoopDispatchTest` (11) and `AgenticLoopUserTurnTest` (9) still pass.

## Verification

- `./gradlew :core:check --offline` green (detekt zero issues, banned-construct scan, NoHardCodedConstantsTest, ApiShapeTest, all tests). `:providers` untouched, so no OkHttp matrix leg was needed.
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK ... classes=181` (unchanged from 09-03; no public symbol added).
- `git diff --stat` of `providers/`, `keystore/` and `core/.../commit/` against `plan_head_before` is empty.

## Commits

- d086d2f feat(09-04): gate path tracer - a read tool's mutation is dropped before the gate
- 3fbe939 feat(09-04): per-tool-name 2-strike abort - the turn finishes, then the run fails ToolFailure
- 9c11756 feat(09-04): terminal tool ends the agentic run with its edge rules
- (this SUMMARY commit)

## Deviations from Plan

None to the plan's behavior. Execution notes: Task 1's held/rejected/preview/fault/apply-error tests passed with no loop code beyond the read-tool rejection, as the plan predicted. detekt also lints test sources, so a `throw IllegalStateException` in a test was changed to `error(...)`. Compound git commands are refused in this worktree, so git was run one command at a time. The ledger file for plan_head_before could not be written under the shared git dir; the base hash given in the dispatch (249adb2) is recorded directly. No git tag, no api.txt; STATE.md and ROADMAP.md untouched.

## Self-Check: PASSED

Created and modified files exist; commits d086d2f, 3fbe939 and 9c11756 exist.
