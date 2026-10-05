---
phase: 05-openai-openrouter-transports
plan: 10
subsystem: providers
tags: [chat-completions, openai, openrouter, contract-test, canary, strict-mode]
requires:
  - phase: 05-07
    provides: ChatCompletionsProvider factories and strict/encoder behavior
  - phase: 05-08
    provides: Chat transport resilience (retry, envelope errors)
  - phase: 05-09
    provides: token parity through the pipeline
provides:
  - per-vendor absent-optional contract test through commandPipeline (SC5, PROV-12, D-14)
  - strict-leg and authority-rule tests on the wire for both vendors (D-05, D-06, D-07)
  - Chat canary sweep for both vendors (TEL-04 for Chat, D-11)
affects: [05-12 captured EDIT rows flow into ChatAbsentOptionalTest automatically]
tech-stack:
  added: []
  patterns: [JUnit Parameterized over the two Chat vendors, manifest-driven EDIT rows, canary sweep mirrored from Anthropic]
key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatAbsentOptionalTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCanaryTest.kt
  modified: []
key-decisions:
  - "Parameterized constructors take (label, model) strings: ChatVendor is internal and a public test constructor cannot expose it; the vendor is derived inside the class"
  - "EDIT rows are matched in MANIFEST.tsv by case containing edit_absent_optional (derived) or ending forced_edit_captured (captured by 05-12); the loader throws on zero rows or a row with no absent_keys"
requirements-completed: [PROV-12, TEL-04, BLD-06]
status: complete
commits: 3
plan_head_before: 10195c65b7c6065858d45a622b3842ff495ab259
actuals:
  tokens: 14000
  tasks: 3
  commits: 3
---

# Phase 5 Plan 10: Absent-optional and canary contract tests Summary

**Two parameterized contract test classes prove omitted optionals reach the app absent and no secret escapes the Chat transport, on OpenAI and OpenRouter across all three OkHttp legs.**

## Accomplishments
- `ChatAbsentOptionalTest`: a forced `edit_list_card` call through `commandPipeline` and the real `ChatCompletionsProvider`. The request carries no `strict` key and `parameters` is byte-identical to the app schema. The mutation receives exactly the arguments the model sent (derived rows: only `card_id` and `items[0].text`), with every `absent_keys` path absent. One commit per run.
- Same class covers D-05/D-06/D-07: `log_food` is sent `strict: true` with the `stripForChatStrict` schema; `editListCardTool(strict = true)` is still sent without `strict` and with its schema untouched.
- The row loader fails (not skips) for a vendor with zero EDIT rows, and its test proves it.
- `ChatCanaryTest`: routed two-turn command per vendor (canary in transcript, system, Bearer key, tool argument, tool result, assistant text, reasoning / reasoning_content / reasoning_details), plus OpenAI and OpenRouter error echoes (400/401/429/500), a 200 envelope with `metadata.raw`, an OpenRouter-style 402, a refusal text, hostile error type and request id, a non-JSON 200 body, and a non-positive timeout with an app client on the builder. Positive controls confirm the server received `Authorization: Bearer <key>` and every canary.

## Evidence
- Parameter cases: `openai` (gpt-5.4-mini) and `openrouter` (openai/gpt-5.4-mini).
- EDIT rows exercised per vendor: 1 each (derived `openai_edit_absent_optional`, `openrouter_edit_absent_optional`); captured rows from 05-12 are picked up automatically.
- ChatAbsentOptionalTest: 10 tests (5 per vendor), `failures="0" skipped="0"` on 4.12.0, 5.2.1 and 5.5.0 legs.
- ChatCanaryTest: 16 tests, `failures="0" skipped="0"` on all three legs.
- Distinct values swept in the routed two-turn run: 49 on OpenAI and 49 on OpenRouter (minimum 25, same as the Anthropic sweep). Failure legs sweep 28 to 75 distinct values each.
- `./gradlew :providers:check --offline -q` green (detekt, explicit API, all three OkHttp legs).

## Task Commits
1. Task 1 (tracer): `3e580d4` - OpenAI EDIT call leaves omitted optionals absent at the mutation
2. Task 2: `78a219c` - parameterized over both vendors, strict leg and authority rule
3. Task 3: `0faf047` - canary sweep for the Chat transport

## Deviations from Plan
None. No production defect found; no change under `providers/src/main` (`git log 10195c6..HEAD -- providers/src/main` is empty). Two small test-side adjustments: the Parameterized constructor uses string labels because `ChatVendor` is internal (Kotlin explicit-visibility compile error), and a tracer-stage helper was renamed `runForced` once it served both tools.

## Issues Encountered
None blocking. STATE.md and ROADMAP.md were not touched (orchestrator owns them).

## Self-Check: PASSED
- ChatAbsentOptionalTest.kt and ChatCanaryTest.kt exist; commits 3e580d4, 78a219c, 0faf047 exist.
