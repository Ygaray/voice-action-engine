---
phase: 12-wave-1-seams-w04-fix
plan: 01
subsystem: providers
tags: [openai, chat-completions, wire-rules, error-classifier, anthropic, capabilities, w04]
requires: []
provides:
  - direct Responses-only OpenAI ids (gpt-6-astra, gpt-6.1-sol families) send no reasoning_effort
  - direct gpt-5.N / gpt-6 -pro and -codex ids never get effort none and refuse tools pre-call
  - 400 with param reasoning_effort and code unsupported_value maps to ModelUnsupported
  - exact claude-sonnet-5 capability row (forced allowed, 1,024-token minimum)
  - astra_direct golden request case
affects: [12-08]
tech-stack:
  added: []
  patterns: [targeted deny-list instead of allow-table, three-fact error classifier keyed on param and code only]
key-files:
  created: []
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRulesTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModelsTest.kt
    - providers/src/test/resources/golden/chat/requests/openai.json
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModelsTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicForcedToolTest.kt
key-decisions:
  - "Deny-list only: no positive id table, every id that worked in v1.0 keeps its wire rule"
  - "Pro/codex exclusion applies to direct OpenAI only; routed pro/codex ids keep today's rules (router behavior unverified)"
  - "Classifier reads only param and code (never the message) and requires status 400"
requirements-completed: [PROV-14, PROV-15, SEAM-03]
status: complete
plan_head_before: 9ab3c0817492489b5d3e3431e80b5e61b2c5d4e8
commits: 3
metrics:
  completed: 2026-10-05
actuals:
  tokens: 7400
  tasks: 3
  commits: 3
---

# Phase 12 Plan 01: W04 fix and claude-sonnet-5 row Summary

W04 is fixed on the JVM inside `:providers`: a direct gpt-6-astra tools call sends no `reasoning_effort`, the captured W04 400 maps to `model_unsupported` (status 400) on OkHttp 4.12.0, 5.2.1 and 5.5.0, and `claude-sonnet-5` has its own exact-id capability row.

## What changed

- **OpenAiModelRules.wireRules** gained a direct Responses-only arm (no effort, `max_completion_tokens`) placed after the routed arm, plus a `PRO_OR_CODEX` exclusion from the effort-none arm (via a small `takesEffortNone` helper). `toolsOnChat` now also refuses direct pro/codex ids before any call. KDoc states the fixed arm order; checked-on date is 2026-10-05.
- **ChatErrors.refine** has one new arm: status 400 AND `param == reasoning_effort` AND `code == unsupported_value` gives `ModelUnsupported`, placed after quota, context length and the v1/responses marker. Nothing new is stored on `ChatErrorInfo`.
- **AnthropicModels** has a `claude-sonnet-5` row: forced tool choice allowed, `EXPLICIT_BREAKPOINTS`, `minCacheablePrefixTokens` 1,024. Exact match only; `claude-sonnet-5-5` unchanged; dated/cased ids get the unknown default. Routed `anthropic/claude-sonnet-5` reaches the row through the existing dot-to-dash mapping.
- **Golden**: `astra_direct` appended to `openai.json`; `openrouter.json` and `providers/api.txt` are byte-identical to v1.0.1.

## Verification

Full plan gate green: `:providers:test :providers:testOkhttp521 :providers:testOkhttp550 :providers:detekt :providers:scanBannedConstructs :providers:apiCheck`. The W04 replay test (`aDirectResponsesOnlyModelUnderAToolsOverrideSendsNoEffortAndTheW04AnswerIsModelUnsupported`) appears in the JUnit XML of all three legs. `git diff v1.0.1 -- providers/api.txt providers/src/test/resources/golden/chat/requests/openrouter.json` is empty.

## Deviations from Plan

**[Rule 3 - blocking] detekt rejections during Task 1 and Task 2.** Task 1: a separate `isUnsupportedReasoningValue` helper pushed `ChatErrors.kt` to 11 functions (`TooManyFunctions`); the three-condition check was inlined as the `refine` arm instead (same behavior). Task 2: the extra `wireRules` arm hit cyclomatic complexity 15; the none-arm condition moved to a private `takesEffortNone` helper.

**[Test helper] ChatTransportTest `route(...)`** gained an optional trailing `override` parameter (test code only) so the W04 replay can apply the `supportsTools` capability override through the pipeline.

**astra_direct built textually.** The case was derived from the existing `forced_log_food_strict` block (model changed, effort line removed) to keep the file's pretty-print style; byte-equality with the encoder output is enforced by the new `ChatEncoderTest` golden test, which passes.

W04_BODY is assembled from the recorded fields of the captured answer (Probe B in `W04-host-wording-check.txt`), not its raw bytes, as the plan specified.

## Flagged assumptions carried (not re-verified)

A1 forced tool choice works on `claude-sonnet-5`; A3 other `-pro`/`-codex` ids behave like the re-fetched ones; A4 routed pro/codex ids left on today's rules.

## Commits

- d15c56f fix(12-01): direct Responses-only ids send no effort; reasoning_effort 400 is ModelUnsupported
- dba4fab fix(12-01): direct pro/codex ids drop effort none and refuse tools pre-call; astra_direct golden
- 364c786 feat(12-01): exact claude-sonnet-5 capability row (forced allowed, 1,024-token minimum)

## Self-Check: PASSED

All 11 modified files exist, the three commits are in `git log`, the measured commit count is 3 and the full gate exited 0.
