---
phase: 04-anthropic-transport-okhttp-matrix
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/04-anthropic-transport-okhttp-matrix/04-REVIEW.md
iteration: 1
findings_in_scope: 12
fixed: 9
skipped: 3
status: all_fixed
---

# Phase 4: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/04-anthropic-transport-okhttp-matrix/04-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 12 (CR-01, WR-01..WR-04, IN-01..IN-07; scope `all`)
- Fixed: 9 (IN-02 as documentation only, see below)
- Skipped: 3, all documented acceptable-skips (IN-04, IN-05, IN-07); no open critical, warning or blocker remains, so
  `all_fixed` is used with the "fixed or acceptable-skip" meaning.

**Verification (ran in the isolated worktree, not the main checkout):** `./gradlew check --offline` is BUILD SUCCESSFUL.
It ran `core:detekt`, `core:detektNegativeControls`, `providers:detekt`, `providers:verifyOkHttpCompileFloor`,
`core:test`, `providers:test` (OkHttp 4.12.0 floor leg), `providers:testOkhttp521` and `providers:testOkhttp550`, plus the
`sample` and `keystore` checks. It was re-run with `--rerun-tasks` for `core` and `providers` so the result is not a cache
hit. `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK ... classes=161`. No public
signature changed (KDoc only on public declarations), no `okhttp3.internal.*`, no tags, no baseline.

## Fixed Issues

### CR-01: A malformed API key is echoed in an OkHttp exception message and escapes the provider as an opaque failure

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMalformedKeyTest.kt` (new)
**Commit:** 4886324
**Applied fix:** `send` now checks the key before any request is built. A key with any character other than tab, space or
visible ASCII returns `FailureReason.Auth()` and sends nothing, so OkHttp's header validation (which quotes the whole
`x-api-key` value) is never reached. The check lives in a private top-level `refusalFor` helper that also holds the
existing `NotConfigured` rule (kept to one return path for detekt `ReturnCount`). Tests: a trailing `"\n"`, a mid-key
`"\r"` and a non-ASCII character each yield `Auth`, no thrown exception, no failure details, no key text in any printed
value, and `server.requestCount == 0`. Logic fix, requires human verification of the accepted character set (see note).

Note: the test key is `sk-CANARY-KEY-BODY`, not an `sk-ant-` shaped string, because the repo's commit-time secret scan
blocks the Anthropic key shape.

### WR-01: A throwing attempt observer discards a successful, already-billed model answer

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicAttemptObserver.kt`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicRetryTest.kt`
**Commits:** b16cd35, 4a42621, 25a7c89
**Applied fix:** The observer call goes through one private `notify` that drops any `Exception` unread. It carries a
function-level `@Suppress("TooGenericExceptionCaught")` with a written justification (app-supplied diagnostics must not
change the outcome; non-suspend, so no cancellation passes through it). The KDoc on `AnthropicAttemptObserver` and on the
builder's `attemptObserver` no longer says a throwing observer fails the call. New test: an observer that always throws
still gets a 529-then-200 call to return `Success` after exactly two requests. Logic fix, requires human verification.

### WR-02: `cleanClient` leaves the app's proxy authenticator in place

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClient.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClientTest.kt`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt`
**Commits:** e0ea40e (code and test), ceec7a8 (the provider KDoc now lists the proxy authenticator, shared with IN-03)
**Applied fix:** Added `.proxyAuthenticator(Authenticator.NONE)` and documented it. New test
`appProxyAuthenticatorNeverRunsForA407` routes through a MockWebServer acting as an HTTP proxy that answers 407, with an
app proxy authenticator that would re-send the request (key header included); it asserts the app authenticator is never
called, the 407 is returned as is, and only one request reached the proxy. I confirmed the test fails without the fix.

### WR-03: Anthropic's low-credit-balance error is probably classified as `HttpError`, not `Billing`

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrors.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrorMapTest.kt`
**Commit:** abbb3ab
**Applied fix:** A 400 whose message contains `credit balance is too low` is now Billing, through the same internal
`userSpendLimit` flag (only the boolean is stored; the message is still dropped, and being a 400 it is never retried).
New test covers the 400 case with a canary in the message and checks that the same words on a 429 stay `rate_limited`.
Not confirmed against a captured live response (the reviewer rated this medium confidence); that confirmation belongs to
the Phase 10 live smoke. Logic fix, requires human verification.

### WR-04: `aConnectionDroppedMidAnswerIsANetworkFailureNotAMalformedOne` does not drop the connection mid-answer

**Files modified:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTimeoutTest.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwaitTest.kt`
**Commit:** ff0383d
**Applied fix:** The old test is renamed `aConnectionDroppedBeforeAnyAnswerIsANetworkFailure`. A new test with the
original name uses `SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY` on a real success body (headers announce the full
length, the body is cut), asserts `Network` with no details, and asserts via the observer exactly two attempts
(`INITIAL`, `TRANSIENT_RETRY`) both with a null status. `CallAwaitTest` gains a case where the source's `read` throws
`IOException`: the awaiter fails and the source is closed. Test-only change.

### IN-01: `CacheDirective.conversationTail` is silently ignored

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt`
**Commit:** d5a1147
**Applied fix:** Documentation route (the reviewer's first option; the plans already defer the moving-tail breakpoint
to a later version, see 04-03-PLAN/SUMMARY and 04-RESEARCH). The `conversationTail` KDoc now says a provider may ignore
it and that Anthropic does in v1.0; the encoder KDoc says the same. The existing test
`aMovingConversationTailAddsNoSecondBreakpoint` already pins the behavior. KDoc only, no signature change.

### IN-02: A timeout is retried after it already spent the full call timeout

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt`
**Commit:** a74c93e
**Applied fix:** Documentation only (the reviewer's stated minimum). The class KDoc now states the worst case (two full
call timeouts plus backoff, about two minutes at the 60 s default, and the first request may still be billed) and the
remedy (lower the call timeout). The behavior change the reviewer floated (do not retry `Timeout`) is not made: decision
D-05 in 04-CONTEXT locks "one transient retry per logical call" for failures that can clear on their own, and
`AnthropicRetryTest`/`AnthropicTimeoutTest` pin it. That part is a design decision for the owner, not a review fix.

### IN-03: Dated or suffixed model ids cost an extra failed request on every call

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt`
**Commit:** ceec7a8
**Applied fix:** Documentation route (the reviewer's cheaper option). The public `AnthropicProvider` KDoc explains the
exact-match table, the doubled latency and spend for an unrecognised suffixed id, and the override
(`PipelineBuilder.capabilities` with `supportsForcedToolChoice = false`); it also notes the observer reports the extra
request as a forced-tool reshape. The README is a stub, so the provider KDoc is where the guidance lives. No learned
cache was added (D-06 says no memo in v1.0).

### IN-06: `CallAwait.onResponse` can leave the awaiter suspended on a non-IOException

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwait.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwaitTest.kt`
**Commit:** 6adfeb5
**Applied fix:** `onResponse` now also catches `RuntimeException` and resumes the awaiter with a text-free
`IOException("unreadable body")` (the original message and cause are not copied, since they could carry anything), with
a justified `@Suppress("TooGenericExceptionCaught")` and a comment on why. New test: a source throwing
`IllegalStateException("secret-bearing text")` fails the awaiter with an `IOException` whose text does not contain the
message, and the source is closed. The optional body size cap was not added (the reviewer rated it low risk, and a cap is
a new limit that the plan does not define). Logic fix, requires human verification.

## Skipped Issues

### IN-04: Duplicated constants and a copied identifier regex can drift

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt:36` (and `AnthropicErrors.kt`, `http/RetryPolicy.kt`, `http/SafeFields.kt`)
**Reason:** Acceptable-skip, attempted and rolled back (nothing was committed). A shared `HttpStatus.kt` of
`internal const val` is not possible here: each becomes a public static field on the file facade class, and the repo's own
`ProvidersApiShapeTest.noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion` fails on it. Non-const `internal val`
passes that test but then trips detekt `MagicNumber` and `MayBeConst` on every line, and the only way through would be
new `@Suppress` entries. The repo's pattern is per-file `private const val`, and the drift risk the reviewer names is
already guarded by `SafeFieldsTest.failureDetailsNeverThrowsForAnyValidatedServerString`. Not worth two quality gates
or new suppressions.
**Original issue:** `STATUS_BAD_REQUEST` and the retryable statuses are declared in more than one file, and `SafeFields.kt`
copies core's identifier regex and limits.

### IN-05: The loopback allow-list depends on the machine's reverse DNS in every test

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt:20, 90`
**Reason:** Acceptable-skip. The risk is test-environment only and fails loudly with a clear message. The only main-side
change that would address the stated risk (accept a host because it resolves to loopback) does a DNS lookup at build
time that can differ from the lookup at connect time, which could send the key in cleartext to a non-loopback address;
generalising the literal check (for example to all of 127.0.0.0/8) does not help the canonical-hostname case. The
test-side fix means rewriting about 26 `server.url(...)` call sites across 13 test files, which widens scope well past
this review; it can be done as one helper in a later test-hygiene pass.
**Original issue:** `MockWebServer.url("/")` reports the machine's canonical name for localhost, and the provider accepts
cleartext only for `localhost`, `127.0.0.1` and `::1`.

### IN-07: Blank user text or an all-empty assistant turn is sent and guarantees a 400

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt:36, 46-57`
**Reason:** Acceptable-skip as design-change heavy. Every available remedy needs a semantic choice the plan does not
make: dropping an empty turn can break role alternation and changes the cached prefix, a placeholder invents text that is
sent to the model (the library is domain-free and must not add words the app did not), and rejecting earlier needs a new
typed failure on the neutral request path in `:core`. The core types deliberately allow these inputs ("never throws",
"may be empty"), so the right place to decide is the core transcript contract, via the control plane (section 10),
not a provider-local patch.
**Original issue:** `userMessage` always emits a text block even when empty, and an `AssistantMessage` with no usable parts
becomes `"content": []`, which Anthropic rejects after a paid round trip.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
