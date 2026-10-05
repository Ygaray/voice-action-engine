---
phase: 05-openai-openrouter-transports
plan: 03
subsystem: providers
tags: [openai, openrouter, error-map, tel-04, header-safety]
requires:
  - phase: 05-openai-openrouter-transports/05-02
    provides: ChatVendor and model knowledge in providers/chat
provides:
  - chat.parseChatError and chat.chatEnvelopeError (status-first error reader for Chat Completions, non-2xx and 2xx-envelope)
  - chat.ChatErrorInfo with reason() and details()
  - http.isHeaderSafe shared by every provider's key header
affects: [05-05, 05-06, 05-07, 05-08]
tech-stack:
  added: []
  patterns: [parse-and-discard error reader, status-first table with in-memory refinements]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFields.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFieldsTest.kt
key-decisions:
  - "Refinement order: quota, context length, Responses-API / no-endpoints (ModelUnsupported), 403 moderation reasons (Refusal); then the status table"
  - "Envelope status = numeric error.code in 100..599, else 200; request id = valid header, else body id only when requestIdInBody"
requirements-completed: [PROV-08, TEL-04]
status: complete
duration: ~25m
completed: 2026-10-01
actuals:
  tokens: 14000
  tasks: 3
  commits: 3
commits: 3
plan_head_before: 68d1841b40447de6ff856a716daa904d1ab50481
---

# Phase 5 Plan 03: Chat error map and shared key-header check Summary

**Status-first error reader for OpenAI and OpenRouter (non-2xx and HTTP-200 envelopes) keeping only status, error type and request id, plus a shared `isHeaderSafe`.**

## Final table

401, 403 Auth; 402 Billing; 404 ModelNotFound; 408, 504 Timeout; 429 RateLimited; 503, 529 Overloaded; every other status (500, 502, 524, 418...) HttpError. Transient = `isTransientStatus` or 524, and not quota.

## Refinement order (wins over the table)

1. error code or type `insufficient_quota` -> Billing (final, never retried, any status)
2. error code `context_length_exceeded` -> ContextWindowExceeded
3. status 400/404 and message contains `/v1/responses` -> ModelUnsupported; status 404 and message contains `No endpoints found that support` -> ModelUnsupported
4. status 403 and `error.metadata.reasons` is a JSON array -> Refusal

The message is read into a local for the two `contains` checks and never stored.

## Envelope status rule and request-id precedence

`chatEnvelopeError` returns null unless root `error` is an object. Status is a numeric `error.code` in 100..599, else 200 (string codes such as `server_error` stay 200 and become the error type). Request id: `safeRequestId(header)`, else, only when `requestIdInBody`, `safeRequestId(root.id)`. Error type: safe string `error.code`, else `error.type`, else `error.metadata.error_type`.

## Tasks

1. Tracer (bd7405a): reader, envelope and non-2xx paths, 401/429, reflection test pinning ChatErrorInfo to five fields.
2. Full table and refinements (843d241): all behavior bullets tested (table, transient set, quota, context, unsupported, refusal, envelope, hostile values, odd bodies, no server text in any rendering).
3. Shared isHeaderSafe (73e723c): moved to http/SafeFields.kt; AnthropicTransport diff is +1/-3 (import added, comment and private function removed).

## Deviations

- [Rule 3 - detekt] `TooManyFunctions` (threshold 11) forced folding the 403-reasons check, the numeric-code read and the error-type chain into their single callers instead of separate private functions; behavior unchanged.

## Verification

`./gradlew :providers:check --offline` green (detekt, scanner, explicit API, compat floor, all OkHttp legs). No change under `core/`; no planning ids in sources; no api.txt or dependency change. `git status` shows only pre-existing untracked/unstaged files left unstaged.

## Self-Check: PASSED

Files exist, commits bd7405a, 843d241, 73e723c present; `commits: 3` measured from the ledger (68d1841..HEAD before this SUMMARY commit).
