---
phase: 04-anthropic-transport-okhttp-matrix
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 38
files_reviewed_list:
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilities.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt
  - providers/build.gradle.kts
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicAttemptObserver.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrors.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrict.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwait.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClient.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/OneShotBody.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/RetryPolicy.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFields.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalProperties.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersApiShapeTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicCanaryTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicCancellationTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrorMapTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicFixtures.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicForcedToolTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicLiveCaptureTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModelsTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicPipelineRetryTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicRetryTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrictTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTimeoutTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransportTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwaitTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClientTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/RetryPolicyTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFieldsTest.kt
findings:
  critical: 1
  warning: 4
  info: 7
  total: 12
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-10-01
**Depth:** standard
**Files Reviewed:** 38
**Status:** issues_found

## Summary

The Anthropic transport is well structured: the one-shot body, the clean-client derivation, the request ceiling of 3, the
error-body drop and the canary sweep are all sound, and I found no logic error in the retry/reshape state machine, the
decoder, or the strict-eligibility walk. The defects are at the edges.

The most serious one is a direct violation of the project's hard rule that API keys never reach an exception. A key
that contains a header-illegal character (a trailing newline from a paste is the realistic case) is placed into an
OkHttp exception message that quotes the key. That exception also escapes `AnthropicProvider.complete`, so the user gets
an opaque `Unexpected` failure instead of a specific `Auth` one. The canary test never exercises a malformed key, so it
cannot see this.

The remaining issues are a telemetry observer that can destroy a billed, successful answer, a claim in the client
isolation docs that is not true for the proxy authenticator, a probable mislabeling of Anthropic's low-credit error, and
a test that is named for a path it does not exercise.

No `okhttp3.internal.*` use, no logging sinks, and no 5.x-only API in main were found. The `CallAwait`/`OneShotJsonBody`
code is binary-safe across 4.12 and 5.x.

## Critical Issues

### CR-01: A malformed API key is echoed in an OkHttp exception message and escapes the provider as an opaque failure

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt:135`
**Issue:** `Request.Builder().header("x-api-key", credential.apiKey)` validates the value. `Credential` only rejects a
blank key (`Credential.kt:12`), so a key such as `"sk-ant-...\n"` or one with a non-ASCII character is accepted, then
`header(...)` throws `IllegalArgumentException("Unexpected char 0x0a at 38 in x-api-key value: <the whole key>")`.
I confirmed in the 4.12.0 bytecode that `Headers.Companion.checkValue` appends `": $value"` unless `Util.isSensitiveHeader`
is true, and that list is only `Authorization`, `Cookie`, `Proxy-Authorization` and `Set-Cookie`. `x-api-key` is not on it,
so the secret is in the message.

Consequences:
1. The project rule "API keys ... never reach logs, telemetry, exceptions or `toString()`" is broken. The exception
   propagates out of `send` and `AnthropicProvider.complete`. Any caller that uses the provider directly (as the tests do),
   or any uncaught-exception handler, can print the key. The engine's `guarded` helper only keeps the class name, so the
   leak is contained only on the pipeline path.
2. The throw is not caught inside `attempt` (only `IOException` is), so it is never mapped to a typed reason. On the
   pipeline it becomes `FailureReason.Unexpected("IllegalArgumentException")`, which contradicts the Core Value ("every
   failure surfaced as a specific, loud reason, never an opaque one"). A pasted key with a stray newline is a very likely
   user error and should read as `Auth`.
3. `AnthropicCanaryTest` has no malformed-key leg, so this is untested.

**Fix:** Validate the key before building the request and fail with a typed reason, without ever constructing the header
from a bad value.
```kotlin
private fun isHeaderSafe(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }

// in send(), after the provider check:
if (!isHeaderSafe(credential.apiKey)) return ModelResult.Failure(FailureReason.Auth())
```
Add a canary test leg that sends a key with a trailing `"\n"` and asserts an `Auth` failure, no thrown exception, and
that the key text appears in no printed value.

## Warnings

### WR-01: A throwing attempt observer discards a successful, already-billed model answer

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt:97`
**Issue:** `observer?.onAttempt(...)` runs between receiving the answer and returning it, with no guard. The KDoc says
"an observer that throws fails the call". That means an app's counting or telemetry callback with a bug turns a good
200 response (money already spent, tool call already decoded) into a thrown exception, and the pipeline reduces it to
`Unexpected`. On a retry path it also skips the remaining attempts. An observer is optional diagnostics and must not be able
to change the outcome. The comment on `AnthropicAttemptObserver` also says the observer "never receives an exception
message", but a throwing observer's own exception is now propagated, with whatever message the app put in it.
**Fix:** Isolate the callback in one place and ignore non-cancellation failures.
```kotlin
private fun notify(attempt: AnthropicAttempt) {
    try {
        observer?.onAttempt(attempt)
    } catch (ignored: Exception) {
        // Diagnostics must never change the outcome of the call; the observer's failure is dropped unread.
    }
}
```
(`CancellationException` is not thrown by a non-suspend function, so no special case is needed; keep this the repo's only
justified `@Suppress("TooGenericExceptionCaught")` if detekt flags it, or reuse `guardedPlain`.) Update the KDoc and add a
test where the observer throws and the call still returns its `Success`.

### WR-02: `cleanClient` leaves the app's proxy authenticator in place, so the "nothing the app installed can see the key" claim is false

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClient.kt:26-39`
**Issue:** The function clears interceptors, the event listener, the authenticator, the cookie jar and redirects, and the
KDoc on `AnthropicProvider` promises "nothing the app installed can see the key or the traffic". It does not replace
`proxyAuthenticator`. `Authenticator.authenticate(route, response)` receives the 407 `Response`, and `response.request`
carries the `x-api-key` header, so an app-supplied proxy authenticator (common on enterprise networks) sees the key. The
test suite covers only `authenticator` (`appAuthenticatorNeverRunsForA401`).
**Fix:** Add `.proxyAuthenticator(Authenticator.NONE)` to the builder chain, and add a test next to
`appAuthenticatorNeverRunsForA401` that routes through a MockWebServer-as-proxy returning 407 and asserts the app's proxy
authenticator is never called. If a proxy login is genuinely needed, document the exception rather than the blanket claim.

### WR-03: Anthropic's low-credit-balance error is probably classified as `HttpError`, not `Billing`

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrors.kt:23-24, 81-84`
**Issue:** Billing is recognised only by status 402, by `error_code == enforced_spend_limit_reached` on a 429, and by a
400 whose message starts with `"You have reached your specified"`. Anthropic has returned its out-of-credit condition as
`400 invalid_request_error` with the message "Your credit balance is too low to access the Anthropic API ...". That answer
matches none of the three rules, so it surfaces as a generic `http_error` that the app cannot tell apart from a bad
request. The Core Value requires that failure reasons be specific, and a depleted balance is the most actionable one for a
personal app. (Confidence: medium. I could not call the API from here; please confirm against a captured response and add
it to `AnthropicLiveCaptureTest` fixtures or a mocked body.)
**Fix:** Add a second prefix/contains check next to `USER_SPEND_LIMIT_PREFIX`, for example
`message?.contains("credit balance is too low") == true`, fold it into `userSpendLimit` (or a renamed `billingMessage`)
flag, and add a row to `AnthropicErrorMapTest`. Keep the message text out of the stored fields as today; only the boolean is kept.

### WR-04: `aConnectionDroppedMidAnswerIsANetworkFailureNotAMalformedOne` does not drop the connection mid-answer

**File:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTimeoutTest.kt:97-108`
**Issue:** The test uses `SocketPolicy.DISCONNECT_AFTER_REQUEST`, which closes the socket after reading the request and
before writing any status line. That is the `execute`-level IOException path (`onFailure`), the same one already covered
by the canary and retry tests. The path the name claims, an IOException thrown while `body.string()` reads a truncated
answer inside `onResponse`, is never exercised anywhere. That is the branch in `CallAwait.kt:47-48`, and it is the only
place where a body-read failure becomes a typed `Network` failure instead of a hang. `CallAwaitTest` only feeds it fake
sources that do not throw.
**Fix:** Use `SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY` with a `Content-Length` larger than the bytes sent (or
`setBodyDelay` plus disconnect), assert `Network`, assert two attempts (initial + transient retry) through the observer,
and rename the existing test to say "before any answer". Add a `CallAwaitTest` case with a `Source` whose `read` throws
`IOException`, asserting the awaiter fails and the source is closed.

## Info

### IN-01: `CacheDirective.conversationTail` is silently ignored

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt:45`
**Issue:** The public `CacheDirective.conversationTail` is documented as "true to also cache the moving tail of the
conversation", and `aMovingConversationTailAddsNoSecondBreakpoint` locks in that the provider never honours it. For the
agentic loop (growing transcripts), the tail breakpoint is the main saver of the cache the Core Value says must hit.
**Fix:** If this is deliberate for this phase, say so in the `conversationTail` KDoc ("Anthropic ignores this in v1.0")
and note the deferral in the loop phase plan; otherwise place a second ephemeral breakpoint on the last block of the last message.

### IN-02: A timeout is retried after it already spent the full call timeout

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt:171-173`
**Issue:** `ioFailure` marks every `InterruptedIOException`, including a call/read timeout, as transient. With the default
60 s call timeout a hung server costs about 120 s plus backoff before the failure reaches the user, and the first request
may still be running and billed server-side (the body was fully sent). A dropped connection before the answer is a better
retry candidate than a timeout.
**Fix:** Consider `transient = false` for `FailureReason.Timeout` (or retry it only when the elapsed time is small), and
update `AnthropicRetryTest`/`AnthropicTimeoutTest` accordingly. At minimum document the worst case in the class KDoc.

### IN-03: Dated or suffixed model ids cost an extra failed request on every call

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt:42-46`
**Issue:** Matching is exact by design. A dated id of a model that rejects forcing (for example `claude-opus-5-5-<date>`)
gets the "forced allowed" default, so each call sends a forced request, receives the `tool_choice` 400, and re-sends
reshaped. "Nothing is remembered between calls", so this repeats forever, doubling latency and spend on the failed leg.
The app can fix it with a capability override, but nothing tells it to.
**Fix:** Either surface the reshape through the observer kind (already done) and document the override in the README, or
add a per-provider "learned" cache keyed by model id. The documentation route is the cheaper one.

### IN-04: Duplicated constants and a copied identifier regex can drift

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt:36`,
`AnthropicErrors.kt:12-21`, `http/RetryPolicy.kt:3-9`, `http/SafeFields.kt:3-6`
**Issue:** `STATUS_BAD_REQUEST` is declared in both `AnthropicTransport.kt` and `AnthropicErrors.kt`; 408/429/503/504/529
are declared in both `AnthropicErrors.kt` and `RetryPolicy.kt`. `SafeFields.kt` copies core's identifier regex and length
limits, and a future tightening in core would make `FailureDetails(...)` in `AnthropicErrorInfo.details()` throw out of
`interpret`. `SafeFieldsTest.failureDetailsNeverThrowsForAnyValidatedServerString` guards that today.
**Fix:** Share one internal `HttpStatus` constants file in `providers/http`, and keep the SafeFields drift test (it is the
right guard).

### IN-05: The loopback allow-list depends on the machine's reverse DNS in every test

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt:20, 90`
**Issue:** `build()` accepts a cleartext URL only if the host string is `localhost`, `127.0.0.1` or `::1`. Nearly every
test builds the provider from `MockWebServer.url("/")`, whose host is `InetAddress.getByName("localhost").canonicalHostName`.
On a CI image or laptop whose `/etc/hosts` maps 127.0.0.1 to another canonical name, every test fails with "the base URL
must use https" instead of a network-level error.
**Fix:** Test loopback by address rather than name: for an IP-literal host use `InetAddress.getByName(host).isLoopbackAddress`
(no DNS lookup for a literal), keep the name `localhost`, and let tests pass `server.url("/")` rebuilt with `HttpUrl.Builder().host("127.0.0.1")`.

### IN-06: `CallAwait.onResponse` can leave the awaiter suspended on a non-IOException, and bodies are read unbounded

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwait.kt:41-52`
**Issue:** OkHttp 4.12's `AsyncCall.run` has already set `signalledCallback = true` before it calls `onResponse`, so any
`RuntimeException` out of `body.string()` is rethrown to the dispatcher thread and `onFailure` is never called. The
continuation is then never resumed, and the call hangs until the caller cancels. Only `IOException` is handled here.
Separately, `string()` reads the whole body into memory with no size cap; the fixed host and the call timeout make this
low risk.
**Fix:** `catch (e: RuntimeException) { if (continuation.isActive) continuation.resumeWithException(IOException("unreadable body")) }`
(no cause text), and optionally cap the read with `body.source().request(MAX_BYTES)` / `peek()`.

### IN-07: Blank user text or an all-empty assistant turn is sent and guarantees a 400

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt:36, 46-57`
**Issue:** `userMessage` always emits a text block, even for an empty string, and `rebuiltContent` drops empty text parts
without a fallback, so an `AssistantMessage` with no usable parts becomes `"content": []`. Anthropic rejects both ("text
content blocks must be non-empty", empty assistant content), which then comes back as an unexplained `http_error` after a
paid round trip. The core types allow both inputs (`UserMessage` "never throws", `AssistantMessage` "may be empty").
**Fix:** Skip or replace empty turns in the encoder (for example drop an empty assistant message, or send a single
placeholder text block), or reject them earlier with a typed failure before the request is made.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
