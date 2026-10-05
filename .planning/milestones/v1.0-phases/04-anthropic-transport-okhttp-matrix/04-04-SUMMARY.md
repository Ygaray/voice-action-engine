---
phase: 04-anthropic-transport-okhttp-matrix
plan: 04
subsystem: providers
tags: [anthropic, error-mapping, timeouts, cancellation, okhttp, tracer]
requires: ["04-03"]
provides:
  - "internal AnthropicErrorInfo(status, errorType, requestId, spendCapReached, userSpendLimit, mentionsToolChoice)"
  - "internal parseAnthropicError(status, requestIdHeader, body): AnthropicErrorInfo"
  - "internal AnthropicErrorInfo.reason(): FailureReason and .details(): FailureDetails"
  - "transport maps InterruptedIOException to Timeout and other IOException to Network"
affects: [04-05, 04-06, 04-08]
tech-stack:
  added: []
  patterns: ["parse-and-discard: the body and error.message are read in memory and only derived facts survive", "status-to-factory lookup table plus one spend refinement keeps detekt complexity quiet"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrors.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrorMapTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTimeoutTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicCancellationTest.kt
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
key-decisions:
  - "Both spend signals require their documented status (429 for the enforced cap, 400 for the user limit) so a stray error text on another status cannot turn into Billing"
  - "The cancellation tests use a never-answering server (SocketPolicy.NO_RESPONSE), not a 10 s header delay, because MockWebServer.shutdown gives up after 5 s on a sleeping handler"
requirements-completed: [PROV-04, PROV-13, TEL-04]
status: complete
commits: 3
plan_head_before: c249bb7642053f76415461397d14cad1b5d73aa7
actuals:
  tokens: 8000
  tasks: 3
  commits: 3
metrics:
  completed: 2026-10-01
---

# Phase 4 Plan 04: Typed provider errors, timeouts and cancellation Summary

Every way an Anthropic call can fail now returns a specific `FailureReason` carrying at most status, error type and request id, and cancelling a command (by the app or by the engine deadline) cancels the HTTP call; all green on OkHttp 4.12.0, 5.2.1 and 5.5.0.

## Final status table (D-08, orchestrator decision 3)

| Status | Reason code |
|---|---|
| 401, 403 | `auth` |
| 402 | `billing` |
| 429 | `rate_limited`, except `error.details.error_code == "enforced_spend_limit_reached"` which is `billing` |
| 400 whose `error.message` starts with `You have reached your specified` | `billing` |
| 404 | `model_not_found` |
| 408, 504 | `timeout` |
| 503, 529 | `overloaded` |
| every other non-2xx (400, 413, 500, 502, 3xx, ...) | `http_error` |

No new `FailureReason` leaf and no `:core` change. User spend-limit message prefix used: `You have reached your specified`.

## What was built

- `AnthropicErrors.kt`: `parseAnthropicError` tolerates a null, empty, non-JSON or oddly shaped body (the `SerializationException` subclass of `IllegalArgumentException` is the only thing caught). It keeps `error.type` only if it passes `safeToken`, the request id from the `request-id` header if it passes `safeRequestId` else the body's `request_id` if it passes else null, and three derived booleans (`spendCapReached`, `userSpendLimit`, `mentionsToolChoice`). `error.message` and the body never leave the function. `AnthropicErrorInfo.toString()` prints status, error type and request id only.
- `AnthropicTransport.kt`: the non-2xx branch is now `parseAnthropicError(...)` then `Failure(info.reason(), info.details())`. The await is wrapped: `InterruptedIOException` (call and read timeouts) becomes `Timeout`, any other `IOException` becomes `Network`, both with null details; `currentCoroutineContext().ensureActive()` runs before mapping so a cancelled coroutine propagates cancellation instead of reporting a failure. No exception message is read, and there is no coroutine timeout in `:providers`.
- Consumers: 04-05 reads `status` and `spendCapReached` for retry decisions, 04-06 reads `mentionsToolChoice` for the forced-tool matcher.

## Tests

- `AnthropicErrorMapTest` (8 tests): the pipeline tracer (401 with header `req_err_1` and an echoed `CANARY-ECHO` message gives `auth` with `FailureDetails(401, "authentication_error", "req_err_1")`, and the canary appears in neither the outcome, its trace nor the details); all 14 status rows; spend cap vs plain 429; user spend limit vs other 400; the `tool_choice` flag; request-id precedence with hostile header fallback (space, 129 chars); hostile `error.type` (space, quote, newline, 65 chars) giving null; null, empty, HTML, array, string and mis-shaped bodies keeping the status reason. Every case asserts the canary is absent from `info`, `details()` and `reason()` strings.
- `AnthropicTimeoutTest` (4 tests): call timeout 300 ms vs 3 s header delay gives `timeout`; read timeout 300 ms vs `NO_RESPONSE` gives `timeout`; a shut-down server gives `network`; a connection dropped after the request gives `network`. Each asserts null details and that the failure's `toString` equals the reason-only form (no `Exception` text).
- `AnthropicCancellationTest` (2 tests): app `cancelAndJoin` mid-call and an engine deadline (`commandTimeoutMillis = 500`) through a real pipeline with a shared `OkHttpClient`; both assert the dispatcher reaches 0 running calls within 2 s and the commit sink received no action; the deadline test also asserts `Failed` with `timeout` and trace code `engine_timeout`.
- All responses come from an always-same `Dispatcher` subclass and tests assert reasons and details only, never request counts, so they stay valid when the transient retry lands in 04-05.

## Measured timings (per leg: 4.12.0 / 5.2.1 / 5.5.0)

Whole-test wall times as reported by JUnit, including server start and shutdown; the join and idle assertions are bounded at 2 s and the deadline at 5 s, and all passed with large margin.

| Test | 4.12.0 | 5.2.1 | 5.5.0 |
|---|---|---|---|
| App cancel mid-call (cancel, join, dispatcher idle) | 0.017 s | 0.107 s | 0.107 s |
| Engine deadline 500 ms (Failed Timeout, dispatcher idle) | 0.970 s | 0.875 s | 0.855 s |
| Call timeout 300 ms | 3.008 s (server shutdown waits out the 3 s header delay) | 0.305 s | 0.306 s |
| Read timeout 300 ms | 0.319 s | 0.410 s | 0.407 s |

## Deviations from Plan

- **Test harness adjustment (not a behavior change):** the plan's cancellation cases specify a server delaying headers 10 s. `MockWebServer` gives up waiting for a sleeping handler after 5 s on shutdown (`IOException: Gave up waiting for queue to shut down`), which failed the tests at teardown. The cancellation tests use `SocketPolicy.NO_RESPONSE` instead (the call stays in flight until the client closes the socket, and the server thread ends with it). The call-timeout test keeps a 3 s header delay, which is within the shutdown wait.
- **TDD note:** the two cancellation tests and the engine-deadline test passed before the IOException handling existed, because cancellation already propagated through `Call.await()` (04-03) and the engine deadline lives in `:core`; they pin existing behavior. The four timeout/network tests were red first (all four failed with the raw `InterruptedIOException`, `SocketTimeoutException`, `ConnectException` and `IOException`) and green after the transport change.
- Task 2's tests were written alongside Task 1 and committed as a separate commit; the implementation of the full table was already complete from Task 1 (a lookup table plus the two spend checks), so Task 2 added tests only.

## Verification

- `./gradlew :providers:test/testOkhttp521/testOkhttp550 --tests` for the three new classes: green on all three legs.
- `./gradlew :providers:detekt :providers:scanBannedConstructs`: clean, zero baseline.
- `./gradlew :providers:check --offline` green; `./gradlew check --offline` green for the whole build.
- `grep -rnE 'withTimeout' providers/src/main` prints nothing; `InterruptedIOException` appears twice in the transport (import and catch).

No files were deleted. No tags created. No `api.txt`, catalog or dependency change.

## Self-Check: PASSED

- Files found: AnthropicErrors.kt, AnthropicErrorMapTest.kt, AnthropicTimeoutTest.kt, AnthropicCancellationTest.kt, modified AnthropicTransport.kt.
- Commits found: 4871a4a, 255e0ed, c72435f.
