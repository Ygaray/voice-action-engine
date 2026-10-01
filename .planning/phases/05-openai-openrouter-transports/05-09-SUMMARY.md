---
phase: 05-openai-openrouter-transports
plan: 09
subsystem: testing
tags: [token-parity, telemetry, tier-policy, anthropic, openai, openrouter, mockwebserver]
status: complete
commits: 1
plan_head_before: 52658e0dfa413cacf88d267ded7393aada0f7cb9

requires:
  - phase: 05-openai-openrouter-transports
    provides: ChatCompletionsProvider (05-05/05-06), usage decoding, Anthropic transport (phase 4)
provides:
  - cross-provider token parity proof (TEL-01) through the real pipeline and the real providers
  - CORE-04 token-ceiling semantics pinned across Anthropic, OpenAI and OpenRouter
affects: [09-agentic-loop, phase-5-verification]

actuals:
  tokens: 3000
  tasks: 2
  commits: 1

tech-stack:
  added: []
  patterns:
    - "parity runner: one private run(case, body, ceiling) drives commandPipeline against MockWebServer for any provider"

key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/parity/TokenParityTest.kt
  modified: []

key-decisions:
  - "No decoder defect found: every parity assertion held on the first run, so no main-source change was made"

requirements-completed: [TEL-01]

coverage:
  - id: D1
    description: "Same logical call yields identical Usage and tokensUsed on Anthropic, OpenAI, OpenRouter (base and cache-write variants)"
    requirement: "TEL-01"
    verification:
      - kind: unit
        ref: "providers/src/test/.../parity/TokenParityTest.kt#theSameCallCostsTheSameTokensOnAllThreeProviders, #aCacheWriteCountsOnceOnAllThreeProviders"
        status: pass
  - id: D2
    description: "OpenRouter extra usage fields (cost, cost_details, reasoning_tokens) change nothing"
    requirement: "TEL-01"
    verification:
      - kind: unit
        ref: "providers/src/test/.../parity/TokenParityTest.kt#openRouterExtraUsageFieldsChangeNothing"
        status: pass
  - id: D3
    description: "tokensUsed > tokenCeiling gives true,false,false at total-1,total,total+1 on every provider"
    requirement: "TEL-01"
    verification:
      - kind: unit
        ref: "providers/src/test/.../parity/TokenParityTest.kt#theTokenCeilingTripsAtTheSamePointOnEveryProvider"
        status: pass
---

# Phase 5 Plan 09: Token Parity Summary

**Cross-provider token parity test: the same work in Anthropic, OpenAI and OpenRouter wire formats normalizes to the same four usage buckets, the same `tokensUsed`, and the same token-ceiling answer.**

## Accomplishments

- `TokenParityTest` drives the real `commandPipeline` with the real `AnthropicProvider`, `ChatCompletionsProvider.openAi { }` and `ChatCompletionsProvider.openRouter { }` against MockWebServer, one `session.model().complete(...)` per run.
- Base fixture: 120 uncached, 1 800 cache-read, 0 cache-write, 55 output. Anthropic wording is `input_tokens 120` with cache read 1 800; Chat wording is `prompt_tokens 1920` with `cached_tokens 1800`. All three give `Usage(120, 1800, 0, 55)` in the `ModelResponse` and in `outcome.trace.usage`, and `tokensUsed` 1 975.
- Cache-write fixture: Anthropic `cache_creation_input_tokens 500`; Chat `prompt_tokens 2420`, `cached_tokens 1800`, `cache_write_tokens 500`. All three give `Usage(120, 1800, 500, 55)` and `tokensUsed` 2 475. Assumption A5 (OpenAI-shaped `prompt_tokens` includes `cache_write_tokens`) is documented in the test KDoc.
- OpenRouter with `cost`, `cost_details` and `completion_tokens_details.reasoning_tokens 20` yields identical usage and trace to the plain body, for both variants.
- Ceiling comparison `session.tokensUsed > session.policy.tokenCeiling` with `tokenCeiling` at total - 1, total, total + 1 gives `[true, false, false]` on every provider for both variants (1 975 and 2 475).

## Decoder deviation

None. No parity assertion failed, so no decoder or main-source change was made; `git diff` against the plan base touches only the new test file. Nothing under `core/` or `providers/src/main` changed.

## Task Commits

Tasks 1 (tracer) and 2 were implemented together in one test class sharing one runner and landed as a single commit, `4d797ce` (`test(05-09): cross-provider token parity ...`). Minor process deviation: the plan asked for one commit per task; splitting would have left unused private helpers in the first commit.

## Verification

- `./gradlew :providers:test --tests '*TokenParityTest' :providers:testOkhttp521 ... :providers:testOkhttp550 ... --offline`: 4 tests, 0 failures on each of the 4.12, 5.2.1 and 5.5.0 legs.
- `./gradlew check --offline`: green (detekt zero issues, all matrix legs, keystore and sample lint).
- First detekt run flagged two MaxLineLength lines in the new test; fixed before the commit (Rule 1, same task).

## Deviations from Plan

- [Process] Tasks 1 and 2 committed together (above).
- [Rule 1 - Bug] Initial pipeline composition lacked the required `commitSink`; added `RecordingCommitSink()`.

## Self-Check: PASSED

- FOUND: providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/parity/TokenParityTest.kt
- FOUND commit: 4d797ce
