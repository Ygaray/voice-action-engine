---
phase: 04-anthropic-transport-okhttp-matrix
plan: 05
subsystem: providers
tags: [anthropic, retry, okhttp, observer, tracer]
requires: ["04-04"]
provides:
  - "internal isTransientStatus, retryAfterSeconds, transientWaitMillis in providers.http (RetryPolicy.kt)"
  - "AnthropicTransport: at most three HTTP requests per logical call, one transient retry, injectable cancellable sleep"
  - "public AnthropicAttemptObserver, AnthropicAttempt, AnthropicAttemptKind and AnthropicProvider.Builder.attemptObserver"
  - "internal Builder members sleep, retryAfterCapMillis (5 000), transientBackoffMillis (500)"
affects: [04-06, 04-07, 04-08]
tech-stack:
  added: []
  patterns: ["retry lives in the transport below the AiProvider seam and never references tools, gates or the sink", "bounded recursion carries the request count and the retried flag", "open value-class vocabulary with internal constructor (as CachingMode)"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/RetryPolicy.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicAttemptObserver.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicPipelineRetryTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicRetryTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/RetryPolicyTest.kt
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt
key-decisions:
  - "A retry-after that is digits too long for a long is read as the largest long (far too long, so no retry) instead of being ignored and replaced by the backoff"
  - "A timeout or lost connection counts as transient (one retry); cancellation still wins because ensureActive runs before the failure is mapped"
  - "The attempt kind is derived from the retried flag now; the reshape request in the next plan passes FORCED_TOOL_RESHAPE through the same observer call"
requirements-completed: [PROV-09]
status: complete
commits: 3
plan_head_before: d129e1a88a0e553203b08b63f171b9b6d0e77ce2
actuals:
  tokens: 9600
  tasks: 3
  commits: 3
metrics:
  completed: 2026-10-01
---

# Phase 4 Plan 05: Single transient retry and attempt observer Summary

A transient failure (408, 429, 500, 502, 503, 504, 529, a timeout or a lost connection) is retried exactly once inside the Anthropic transport, with a three-request ceiling and a cancellable wait; through the real pipeline a retried call applies the app's change and commits it once; apps can watch every HTTP attempt by ids only. All green on OkHttp 4.12.0, 5.2.1 and 5.5.0.

## What was built

- `RetryPolicy.kt` (`providers.http`, internal): the seven-status transient set, `retryAfterSeconds` (trimmed non-negative integer only; HTTP-date, negative, decimal and text give null), and `transientWaitMillis(retryAfterSeconds, capMillis, backoffMillis)` (server ask times 1 000 within the cap, null above it, the backoff when there is no usable header).
- `AnthropicTransport`: the single attempt became a bounded loop (`MAX_REQUESTS = 3`; the transient retry may be used once). A spend-cap 429 is treated as final even though 429 is in the transient set. Each attempt builds a fresh request with a new `OneShotJsonBody`. The wait goes through the injectable `sleep` (default `delay`).
- `AnthropicProvider.Builder`: internal `sleep`, `retryAfterCapMillis`, `transientBackoffMillis` (the two millis values validated non-negative); public `attemptObserver`.
- `AnthropicAttemptObserver.kt`: `fun interface AnthropicAttemptObserver`, `AnthropicAttempt` (value equals/hashCode, `toString` = `AnthropicAttempt(number=1, kind=initial, httpStatus=529)`), open value class `AnthropicAttemptKind` (`initial`, `transient_retry`, `forced_tool_reshape`). No data class, enum or public const; no `:core` change.

## Tests

- `AnthropicPipelineRetryTest` (2 tests, through `commandPipeline` with the real provider and core fixtures): a 529 then a tool_use 200, and `DISCONNECT_AFTER_REQUEST` then a tool_use 200. Each asserts Completed, `requestCount == 2`, `FakeMutation.applyCount == 1`, one `RecordingCommitSink` action, one turn on the trace and `waits == [500]`.
- `RetryPolicyTest` (5 tests): parsing, wait rules, the transient set over statuses 100 to 599.
- `AnthropicRetryTest` (20 tests): every transient status retried once with a 500 ms wait; retry-after 2 gives 2 000, retry-after 30 gives `rate_limited` with one request and no wait, an HTTP-date falls back to 500; spend-cap 429 and user spend-limit 400 give `billing` with one request; 400/401/403/404/413 one request each; 529 then 529 gives `overloaded` with two requests; malformed 200 gives `malformed_response` with one request; disconnect and `Retry-After: 0` cases; a warm-connection disconnect through the provider; the positive control; and the observer sequences (529 then 200, disconnect then 200, 401, first-try success, refused before the network, value equality and `toString`, kind wire values, provider `toString` with an observer).

## Per-leg findings the plan asked for

| Case | 4.12.0 | 5.2.1 | 5.5.0 |
|---|---|---|---|
| Disconnect after request (fresh connection), then 200 through the provider | 2 requests, one wait `[500]` | 2 requests, `[500]` | 2 requests, `[500]` |
| 503 with `Retry-After: 0`, then 200 | 2 requests, one wait `[0]` | 2 requests, `[0]` | 2 requests, `[0]` |
| Warm pooled connection dropped, then retry (provider) | 3 requests in total (warm-up, drop, retry), `[500]` | same | same |
| Positive control, plain string body, fresh connection | does not reproduce: the call fails with `unexpected end of stream` | not observed (that first run stopped at the failing 4.12.0 leg) | not observed |
| Positive control, plain string body, warm connection | 2 executes, 3 requests (silent replay reproduced) | reproduced | reproduced |

`retryOnConnectionFailure(false)` was not needed on any leg (assumption A3 held): every provider assertion passed with the one-shot body alone.

## Deviations from Plan

- **Positive control moved to a warm connection (assumption A4 refined).** The plan's control, a plain POST against `DISCONNECT_AFTER_REQUEST` on a fresh connection, does not reproduce a silent replay on 4.12.0: OkHttp surfaces the dropped request as `unexpected end of stream` because there is no second route to try. The silent replay appears when the dropped connection was a pooled, already-used one, so the control now does one successful request first and then the drop; there it reproduces (two executes, three requests) on all three legs. For the same reason I added a provider test on a warm connection (`aConnectionDroppedOnAWarmPooledConnectionIsStillRetriedOnlyByTheTransport`: three requests and one recorded wait) so the proof does not rest on the cold case alone. This was not a removal of the control (the plan's remove-on-failure path was not taken).
- **Negative control for the one-shot body.** I temporarily flipped `OneShotJsonBody.isOneShot()` to false and ran `AnthropicRetryTest` on all three legs: three tests failed on each leg (the warm-connection drop, the `Retry-After: 0` case, and the every-transient-status case via 408/503 follow-ups), proving the assertions detect an OkHttp-added replay. The flip was reverted before any commit; `OneShotBody.kt` is unchanged by this plan.
- **Detekt fixes while implementing:** the two policy functions use `when` expressions (ReturnCount limit of 2), and long lines in the transport and builder were wrapped. No suppressions, no config change.
- The cold-connection control was only observed on 4.12.0, because that run stopped at the first failing leg; the warm control and every provider assertion ran and passed on all three legs.

## Verification

- `./gradlew :providers:test/testOkhttp521/testOkhttp550 --tests` for `AnthropicPipelineRetryTest`, `AnthropicRetryTest`, `RetryPolicyTest`: green on all three legs.
- `./gradlew :providers:check --offline` (detekt zero baseline, `scanBannedConstructs`, explicit API, all legs): green.
- `./gradlew check --offline` for the whole build: green.
- `grep -rnE 'CommitSink|CommitCoordinator|PreApplyGate|ToolStep' providers/src/main` prints nothing; `grep -cE 'data class|enum class|public const'` on the observer file is 0; `fun transientWaitMillis` is defined once.

No files were deleted. No tags created. No `api.txt`, catalog or dependency change; `:core`, the contract ledger and `.planning/graphs/` untouched; STATE.md and ROADMAP.md not modified (orchestrator-owned).

## Self-Check: PASSED

- Files found: RetryPolicy.kt, AnthropicAttemptObserver.kt, AnthropicPipelineRetryTest.kt, AnthropicRetryTest.kt, RetryPolicyTest.kt, modified AnthropicTransport.kt and AnthropicProvider.kt.
- Commits found: ed22c90, b2472d3, f35ab11.
