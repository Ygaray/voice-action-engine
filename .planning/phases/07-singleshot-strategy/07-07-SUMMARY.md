---
phase: 07-singleshot-strategy
plan: 07
subsystem: providers-wire-acceptance
tags: [single-shot, wire-test, anthropic, openai, openrouter, okhttp-matrix, parallel-tool-calls]
status: complete

requires:
  - phase: 07-singleshot-strategy
    provides: "SingleShotStrategy (07-02..07-05), single-call request shape in the Anthropic and Chat encoders (07-04), CT-shaped acceptance (07-06)"
provides:
  - "SingleShotWireTest: SingleShot over the real AnthropicProvider and ChatCompletionsProvider (OpenAI, OpenRouter) against MockWebServer, byte-level"
affects: [07-08, phase-10-live-smoke]

plan_head_before: 24180c7d56c32a9a93b85c08457a2aba3425ecf6
commits: 2

actuals:
  tokens: 3000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "one private drive(Scenario) runner starts MockWebServer, wires SingleShot plus the real provider through commandPipeline, and returns an Exchange (outcome, requestCount, request body text, resolver, gate, sink)"
    - "request bodies are read with takeRequest(timeout) so a missing request fails the assertion rather than hanging"

key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/SingleShotWireTest.kt
  modified: []

key-decisions:
  - "No main-source, golden, api.txt or tag change; every case passed against the shipped encoders and decoders, so no wire gap is reported."
  - "The :core SingleShotTestSupport is not on the :providers classpath, so the file carries a small private EntryResolver and Scenario/Exchange helpers instead of sharing it."

requirements-completed: [SHOT-02, SHOT-01]

coverage:
  jvm_tests_added: 6
---

# Phase 7 Plan 07: SingleShot over the real transports Summary

SingleShot now runs against the real Anthropic, OpenAI and OpenRouter wire code on all three OkHttp legs, and the single-call request shape each vendor accepts is asserted on the bytes sent. No main source changed.

## Cases

| Test method | What it proves |
|---|---|
| `anthropicForcedBodyAsksForOneToolAndCommits` | With the forcing override on `claude-opus-5-5`, `tool_choice` is exactly `{"type":"tool","name":"record_entries","disable_parallel_tool_use":true}`; one request, resolver called once, gate admits, one COMMITTED action |
| `anthropicReshapedBodyAsksForOneToolAndCommits` | Without the override, `tool_choice` is exactly `{"type":"auto","disable_parallel_tool_use":true}` and the last user block is "Call the record_entries tool with your result."; one request, one COMMITTED action |
| `openAiBodySendsParallelToolCallsFalseAndCommits` | `gpt-5.4-mini` body has a named function `tool_choice` and `parallel_tool_calls: false`; commits once |
| `openRouterBodyOmitsParallelToolCallsAndUsesTheFirstCall` | `openai/gpt-5.4-mini` body has no `parallel_tool_calls` text anywhere and `provider` is `{"require_parameters":true}`; an answer with two tool calls is resolved from the first call's arguments only and the trace holds `extra_tool_calls_dropped` (T-07-30) |
| `openAiProseAnswerEscalatesToTheNextTier` | A `finish_reason: stop` prose answer escalates with `NoToolCall`, the next tier runs and completes with "fallback"; one HTTP request, resolver never called |
| `anthropicRefusalFailsWithRefusal` | `stop_reason: refusal` ends as `CommandOutcome.Failed` with `FailureReason.Refusal`; gate never asked, resolver never called, one request |

## Per-leg results (SingleShotWireTest)

| Task | tests | failures | errors |
|---|---|---|---|
| `:providers:test` (OkHttp 4.12.0) | 6 | 0 | 0 |
| `:providers:testOkhttp521` | 6 | 0 | 0 |
| `:providers:testOkhttp550` | 6 | 0 | 0 |

`./gradlew :providers:check --offline -q` exits 0 (detekt zero issues, metalava, all test legs).

## Task commits

| Task | Commit | Description |
|---|---|---|
| 1 (tracer) | 977882c | SingleShot over the real AnthropicProvider, forced and reshaped single-call bodies |
| 2 | 1f0eaef | OpenAI, OpenRouter, prose escalation and refusal cases; three-leg run and `:providers:check` |

## Deviations from Plan

None. The plan's prohibitions held: `git status` over `core/src/main`, `providers/src/main` and `providers/src/test/resources` is empty; legacy `okhttp3.mockwebserver` only; fake key `sk-test-key`; no live network.

Task 1's tracer gate (re-run end-to-end before expanding) was satisfied by the Task 1 verify run itself (2 tests, 0 failures) before Task 2 began.

## Self-Check: PASSED

- FOUND: providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/SingleShotWireTest.kt
- FOUND commits: 977882c, 1f0eaef
