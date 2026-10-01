---
phase: 04-anthropic-transport-okhttp-matrix
plan: 02
subsystem: providers
tags: [okhttp, http, cancellation, redirects, one-shot-body, validation]
requires: []
provides:
  - "internal cleanClient(app, callTimeoutMillis, readTimeoutMillis): OkHttpClient"
  - "internal OneShotJsonBody(bytes): RequestBody (isOneShot true)"
  - "internal HttpReply(code, headers, body) and Call.await()"
  - "internal safeToken / safeRequestId"
affects: [04-03, 04-05, 04-06, phase-05]
tech-stack:
  added: []
  patterns: ["Response confined to the OkHttp callback; resume with plain data", "all symbols internal in providers.http"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClient.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/OneShotBody.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwait.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFields.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClientTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwaitTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFieldsTest.kt
  modified: []
key-decisions:
  - "Body is read inside onResponse and closed there with body?.close() in a finally, not Response.use: OkHttp 4.12 throws IllegalStateException from Response.close() when the response has no body"
requirements-completed: [PROV-04, PROV-11, PROV-13]
status: complete
commits: 3
plan_head_before: f00a22501983c4414c7721c59a505e50c121cc9f
actuals:
  tokens: 9000
  tasks: 3
  commits: 3
metrics:
  completed: 2026-09-30
---

# Phase 4 Plan 02: Shared HTTP plumbing Summary

The provider-agnostic HTTP layer now exists: a hook-free derived client, a one-shot request body, a cancellation-safe `Call.await()` and server-string validators, all internal and proven identically on OkHttp 4.12.0, 5.2.1 and 5.5.0.

## Final internal signatures (package `io.github.ygaray.voiceactionengine.providers.http`)

- `internal fun cleanClient(app: OkHttpClient?, callTimeoutMillis: Long, readTimeoutMillis: Long): OkHttpClient`
- `internal class OneShotJsonBody(bytes: ByteArray) : RequestBody` (content type `application/json; charset=utf-8`, known length, `isOneShot() == true`)
- `internal class HttpReply(val code: Int, val headers: Headers, val body: String?)` with `isSuccessful` (200..299) and `toString()` = `HttpReply(code=<code>)`
- `internal suspend fun Call.await(): HttpReply`
- `internal fun safeToken(value: String?): String?` (1..64 of `[A-Za-z0-9_.:-]`) and `internal fun safeRequestId(value: String?): String?` (1..128)

## What was built

- `cleanClient` derives with `newBuilder()`, clears application and network interceptors, installs `EventListener.NONE`, `Authenticator.NONE`, `CookieJar.NO_COOKIES`, turns both redirect flags off, sets connect 10 s and call/read from the arguments. `retryOnConnectionFailure` is left as the app set it. The app client is not mutated; pool and dispatcher are shared (asserted by identity).
- `Call.await()` uses `suspendCancellableCoroutine` with `invokeOnCancellation { cancel() }`. The body is read null-safely inside the callback; a response that arrives after cancellation is closed unread; `onFailure` after cancellation is ignored.
- `ProvidersModule.kt` removed.

## Tests (all run on the 4.12.0, 5.2.1 and 5.5.0 legs)

- CleanClientTest (4): tracer (one-shot POST, all three app-hook counters 0, no `x-app-hook` header at the server, body intact, pool/dispatcher shared, timeouts 60 000/60 000/10 000), redirects, timeouts (including `cleanClient(null, ...)`), app authenticator on a 401.
- Redirect statuses tested: 301, 302, 303, 307, 308 (plan required 302/307/308). Each returns unfollowed and the second server's `requestCount` is 0.
- CallAwaitTest (6): cancel calls `cancel()` once; late response closed and not read (read counter 0, close flag true); normal response; body-less response; `onFailure` resumes the awaiter and is ignored after cancel; real MockWebServer with a 5 s headers delay where cancel completes well under 1 s and `runningCallsCount()` reaches 0.
- SafeFieldsTest (5): accept and reject lists for both functions plus a `FailureDetails(400, safeToken(x), safeRequestId(x))` sweep that never throws.
- Mutation check: temporarily setting `followRedirects(true)` made the redirect test fail; restored before commit.

## Deviations

- [Rule 1 - Bug] The plan's `response.use { ... }` form breaks on a body-less `Response`: OkHttp 4.12 throws `IllegalStateException: response is not eligible for a body and must not be closed` from `Response.close()`. Replaced with reading `response.body` once and `body?.close()` in a `finally`. Behavior for real responses is identical (real network responses always carry a body); the null-body test now passes.
- Test-only: coroutine stack-trace recovery returns a copy of the exception on `resumeWithException`, so the failure test compares exception class and message instead of identity.

## Leg-specific observations

- No behavioral difference between legs in any assertion. The body-less-response test asserts `isNullOrEmpty()` because 4.12 gives a null body there and I did not distinguish later versions' behavior (the assertion holds on all three legs).
- The real-server cancel test takes about 5 s on the 4.12.0 leg (MockWebServer shutdown waits out the delayed response) and under half a second on the 5.x legs.

## Verification

- `./gradlew check --offline` green across all modules (detekt zero baseline, `scanBannedConstructs`, `verifyOkHttpCompileFloor`, 4.12.0 / 5.2.1 / 5.5.0 legs).
- Acceptance greps: `isOneShot` present, `interceptors().clear()` and `networkInterceptors().clear()` once each, `ProvidersModule.kt` gone, no `body!!` or `.body.string(` in `providers/src/main`.
- No api.txt, tags, dependency or build-file changes; STATE.md and ROADMAP.md untouched.

## Commits

- `a75651a` feat(04-02): clean client, one-shot body and cancellation-safe Call.await tracer
- `1755576` feat(04-02): prove Call.await cancellation and close the body without Response.use
- `e148781` feat(04-02): harden against redirects, app authenticators and hostile server strings

## Self-Check: PASSED

All seven created files exist, `ProvidersModule.kt` is gone, and the three commits are on the branch.
