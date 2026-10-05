---
phase: 05-openai-openrouter-transports
plan: 08
subsystem: providers-chat-transport-resilience
tags: [retry, timeout, cancellation, malformed-key, chat-completions, openai, openrouter, okhttp-matrix]
requires:
  - phase: 05-06
    provides: ChatTransport, ChatCompletionsProvider, attempt observer
  - phase: 05-07
    provides: Chat fixtures and error-map golden coverage
provides:
  - ChatPipelineRetryTest (no duplicate tool execution or commit under retry, both vendors)
  - ChatRetryTest (retry matrix, retry-after, envelope retry, observer facts)
  - ChatTimeoutTest (timeouts and network failures typed)
  - ChatCancellationTest (cancellation and engine deadline)
  - ChatMalformedKeyTest (header-unsafe keys refused before the network, both vendors)
affects: [05-09, 05-10, 05-11, 05-12]
tech-stack:
  added: []
  patterns: [scenario runner per vendor, queue (not custom Dispatcher) when a SocketPolicy must act before the read]
key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatPipelineRetryTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatRetryTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTimeoutTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCancellationTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMalformedKeyTest.kt
  modified: []
key-decisions:
  - "No production change: every behavior bullet held against ChatTransport as built in 05-06, so providers/src/main is untouched"
requirements-completed: [PROV-09, PROV-11, PROV-13, BLD-06]
status: complete
plan_head_before: ff59d487f6f91b90fe80e011b5d4a095bff64134
commits: 3
actuals:
  tokens: 11400
  tasks: 3
  commits: 3
duration: 20min
completed: 2026-10-01
---

# Phase 5 Plan 08: Chat Transport Resilience Summary

**Retry, timeout, network, cancellation and malformed-key behavior of the Chat Completions transport is proven on OpenAI and OpenRouter and on OkHttp 4.12.0, 5.2.1 and 5.5.0 with 43 tests per leg and no change to production code.**

## Accomplishments

- Tracer (Task 1): `ChatPipelineRetryTest` runs a forced `log_food` call through `commandPipeline` with a `FakeMutation` and a `RecordingCommitSink`. For OpenAI (529, dropped connection) and OpenRouter (529, dropped connection, 200 envelope with code 503) it asserts 2 requests, 1 mutation applied, 1 commit, 1 trace turn and a single 500 ms backoff wait. The tracer gate (re-run of the verify on all three legs) passed before expansion.
- Retry matrix (Task 2): all of 408, 429, 500, 502, 503, 504, 524 and 529 are retried once on both vendors; retry-after 2 gives a 2000 ms wait, 60 (beyond the 5000 ms cap) ends after one request with `rate_limited`, a date form and an absent header use the backoff, `Retry-After: 0` is followed by the transport. Insufficient-quota 429, 402, 400, 401, 403 and 404 end after one request as billing, billing, http_error, auth, auth and model_not_found; a second transient failure is final after exactly 2 requests; a non-JSON 200 is `malformed_response` after 1. OpenRouter 200 envelopes 429 and 502 are retried, 401 is final. A warm pooled connection dropped after the request is retried only by the transport (3 requests, 3 observed attempts), with a positive control showing OkHttp does resend a replayable body. Observer facts, value equality over all five fields, toString and kind wire values are covered.
- Timeouts and cancellation (Task 3): header delay past `callTimeoutMillis` and a stalled body past `readTimeoutMillis` end `timeout` after 2 requests on both vendors; a refused connection, `DISCONNECT_AFTER_REQUEST`, `DISCONNECT_AT_START` and a mid-body drop end `network` (the mid-body case records two null-status attempts, never `malformed_response`). Cancelling the command while the server holds the answer cancels the call within 2 s, the strategy sees the cancellation, exactly one request was sent, and the commit sink stays empty; an engine `commandTimeoutMillis` of 500 ms ends `timeout` with `engine_timeout` and an idle HTTP dispatcher. Keys `...\n`, `...\rmore` and `...ë` end `auth` with 0 requests on both vendors through the pipeline, with the canary key absent from the failure, reason, outcome, trace and every recorded event.

## Test counts (identical on every leg)

| Class | Tests |
|-------|-------|
| ChatPipelineRetryTest | 5 |
| ChatRetryTest | 23 |
| ChatTimeoutTest | 8 |
| ChatCancellationTest | 4 |
| ChatMalformedKeyTest | 3 |
| Total | 43 |

Per-leg results: `test` (OkHttp 4.12.0), `testOkhttp521` (5.2.1) and `testOkhttp550` (5.5.0) each ran all 43 with 0 failures, 0 errors, 0 skipped. `./gradlew :providers:check --offline` is green (detekt zero issues, scanner, explicit API, bytecode level, compile floor and all three legs).

## Deviations from Plan

### Transport deviations

None. `git log ff59d48..HEAD -- providers/src/main` is empty.

### Test-side corrections (not transport defects)

**1. [Rule 1 - Test bug] `DISCONNECT_AT_START` through a custom Dispatcher answered a 200 instead of dropping**
- Found by: the first run of `ChatTimeoutTest.aConnectionDroppedBeforeAnyAnswerIsANetworkFailure` (expected network, got malformed_response).
- Cause: a custom `Dispatcher` inherits the default `peek()` that reports `KEEP_OPEN`, so the server never applies `AT_START` before reading and returns the 200 with an empty body. The transport behaved correctly for what it received.
- Fix: the `AT_START` cases enqueue two responses on the default queue (initial plus retry) and assert 2 requests; `DISCONNECT_AFTER_REQUEST` keeps the custom dispatcher. The test was split into one method per policy.

## Verification notes

- No device or behavioral verification was performed or claimed; all checks are the plan's automated acceptance criteria.
- No real-looking key prefixes are used (`sk-test-key`, `sk-CANARY-KEY-BODY`); no test or method name contains `Live`; no `QueueDispatcher` subclass anywhere (`grep -c QueueDispatcher ChatRetryTest.kt` is 0).
- STATE.md and ROADMAP.md were not modified (orchestrator-owned).

## Self-Check: PASSED

- Created files exist: the five test classes listed under key-files (verified present on disk and committed).
- Commits exist: 88fe6cd (Task 1), 7fec3ea (Task 2), 24cdcfb (Task 3).
