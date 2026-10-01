---
phase: 09-agentic-loop-strategy
plan: 07
subsystem: providers/wire-tests
tags: [agentic-loop, anthropic, openai, openrouter, wire, okhttp-matrix, tests]
requires: [09-06]
provides:
  - AgenticLoopWireTest (the agentic loop over the real Anthropic, OpenAI and OpenRouter transports, 15 tests, three OkHttp legs)
affects: [09-08, 09-09]
tech-stack:
  added: []
  patterns: [LoopScenario/LoopExchange/LoopDialect carriers mirroring SingleShotWireTest, enqueue-only legacy MockWebServer, cached-prefix byte equality across turns]
key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/AgenticLoopWireTest.kt
  modified: []
key-decisions:
  - "No main change: every wire assertion held on the first run against the shipped mappers, so no gap is returned to the orchestrator."
  - "Test carriers are named Loop* because SingleShotWireTest already declares private top-level Scenario, Exchange and drive in the same package, which clash on the JVM."
requirements-completed: [LOOP-01]
status: complete
plan_head_before: 386f67f8cd461ac9dfe2d3cb763b0dfc3b23853b
commits: 2
actuals:
  tokens: 4700
  tasks: 2
  commits: 2
metrics:
  completed: 2026-10-01
---

# Phase 9 Plan 07: Agentic Loop on the Real Wire Summary

The agentic loop, wired the way a consumer app wires it with `commandPipeline` and `AgenticLoopStrategy`, now runs over the real Anthropic, OpenAI and OpenRouter transports against a MockWebServer, and every shipped mapper produced exactly the bytes the contract needs.

## What was proven

Anthropic (Task 1, the tracer):
- Two-turn run: request 2 holds `[user, assistant with the toolu_1 tool_use block, one user message with the tool_result for toolu_1 carrying the mutation's content]`; the run completes with "done" and one COMMITTED action.
- `tool_choice` is `{"type":"auto"}` on both requests and `disable_parallel_tool_use` never appears.
- The SB-shaped first user message is exactly `Current local date-time: 2026-10-01T16:30:12 (UTC)\n\nVoice command: add two things` on every request.
- The serialized `system` and `tools` are identical on turn 1 and turn 2.
- A holding gate gives a tool_result content of exactly `{"applied":false,"status":"held_for_confirmation"}` with no `is_error`, one held proposal, no COMMITTED action.
- Two parallel tool_use blocks are answered by one user message with two tool_result blocks in call order.

Chat Completions (Task 2, OpenAI and OpenRouter):
- One `role:tool` message per call, with its `tool_call_id`, in call order, carrying the coordinator's content.
- SB framing exact on both vendors; the system message and tools array identical on both turns.
- Held bytes exact and not wrapped as an error; `tool_choice` is `"auto"` on every turn.
- `parallel_tool_calls`: absent when the only tool has an optional property; `false` on every OpenAI request when a strict-eligible tool is offered; never sent by OpenRouter, strict-eligible tool or not.
- An OpenRouter answer with `finish_reason: "stop"` and populated `tool_calls` dispatches (one COMMITTED action) and is answered in the next request.

## AgenticLoopWireTest counts per OkHttp leg

| Leg | Task | Tests | Failures |
|-----|------|-------|----------|
| 4.12.0 (`:providers:test`) | `*AgenticLoopWireTest` | 15 | 0 |
| 5.2.1 (`testOkhttp521`) | `*AgenticLoopWireTest` | 15 | 0 |
| 5.5.0 (`testOkhttp550`) | `*AgenticLoopWireTest` | 15 | 0 |

`plan_head_before` is `386f67f8cd461ac9dfe2d3cb763b0dfc3b23853b`.

## Verification

- `./gradlew :providers:check --offline -q` green (whole providers suite, all legs, detekt zero issues, scanBannedConstructs).
- `./gradlew check --offline -q` green across the repository.
- `git diff --stat 386f67f -- providers/src/main core/src/main keystore/src/main` prints nothing: no main source changed.
- No public API touched, so no api.txt and no API-surface review; no tag created.

## Deviations from Plan

None in behavior. Two small authoring adjustments, neither a rule deviation:
- Carrier types were renamed with a `Loop` prefix after a compile clash with SingleShotWireTest's private top-level names.
- A first draft asserted the sink saw no actions at all for a held call; the sink also hears HELD actions, so the assertion became "no COMMITTED action", which is the real invariant. This was a test-authoring error, not a mapper fault.

Tracer gate: the Task 1 slice was verified end-to-end on all three legs before the Chat tests were added.

## Self-Check: PASSED

- Created file exists: providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/AgenticLoopWireTest.kt
- Commits exist: 5619970, 5b8cb16
