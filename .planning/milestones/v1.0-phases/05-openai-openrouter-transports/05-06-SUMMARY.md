---
phase: 05-openai-openrouter-transports
plan: 06
subsystem: providers
tags: [openai, openrouter, chat-completions, transport, retry, attempt-observer]
requires: [05-03, 05-04, 05-05]
provides:
  - "chat.ChatCompletionsProvider.openAi { } / .openRouter { } (public AiProvider over the internal ChatVendor)"
  - "chat.ChatCompletionsAttemptObserver, ChatCompletionsAttempt, ChatCompletionsAttemptKind (public)"
  - "chat.ChatTransport(client, baseUrl, vendor, ioDispatcher, timing, observer).send(call) and chat.ChatRetryTiming (internal)"
affects: [05-08 robustness suites, 05-09 parity, 05-10 absent-optional and canary, Phase 7 SingleShot, Phase 9 AgenticLoop, Phase 10 sample smoke]
tech-stack:
  added: []
  patterns: ["Anthropic provider/transport/observer shape mirrored without extracting a shared transport", "bounded recursion for the single retry", "single broad catch in the non-suspend observer wrapper"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsProvider.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsAttemptObserver.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt
  modified:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersApiShapeTest.kt
key-decisions:
  - "Task 1 shipped the transport without retry, timing or observer (constructor of four parameters); Task 2 widened it to the planned six, so each commit compiles and passes its own gates"
  - "The attempt number maps to the kind (1 is INITIAL, 2 is TRANSIENT_RETRY); there is no Progress/Resend structure because Chat has no reshape request"
requirements-completed: [PROV-08, PROV-09, PROV-11, PROV-13]
status: complete
plan_head_before: f109f94dd299a705255b2d1654035099844ed1e7
commits: 3
actuals:
  tokens: 10500
  tasks: 3
  commits: 3
---

# Phase 5 Plan 6: Chat Completions provider and transport Summary

`ChatCompletionsProvider.openAi { }` and `.openRouter { }` now serve routed commands end to end: each vendor receives its committed golden request body, tool calls come back typed, and a failure is retried at most once and only when waiting can help.

## Public surface (package `io.github.ygaray.voiceactionengine.providers.chat`)

- `ChatCompletionsProvider` (internal constructor, implements `AiProvider`). `id` is the vendor's provider id (`openai` or `openrouter`), `capabilities(model)` is `ChatModels.capabilities(vendor, model)`, `complete` is `transport.send`. `toString` is `ChatCompletionsProvider(provider=<id>, callTimeoutMillis=<n>, readTimeoutMillis=<n>)`.
- `ChatCompletionsProvider.Builder` (internal constructor taking the vendor): public `httpClient: OkHttpClient?`, `callTimeoutMillis: Long` (60 000), `readTimeoutMillis: Long` (60 000), `attemptObserver: ChatCompletionsAttemptObserver?`.
- `ChatCompletionsProvider.Companion`: `openAi(block)` and `openRouter(block)`. No public constructor, base URL, dispatcher, sleep or vendor.
- `fun interface ChatCompletionsAttemptObserver { fun onAttempt(attempt) }`.
- `ChatCompletionsAttempt(number, kind, httpStatus, finishReason, toolCalls)` with manual `equals`, `hashCode`, `toString` over all five facts.
- `@JvmInline value class ChatCompletionsAttemptKind` with `INITIAL` ("initial") and `TRANSIENT_RETRY` ("transient_retry").

## Internal seams the tests use

Builder: `vendor`, `baseUrl`, `ioDispatcher`, `sleep`, `retryAfterCapMillis`, `transientBackoffMillis` (defaults: vendor production URL, `Dispatchers.IO`, `delay`, 5 000, 500). Provider: `transport` (exposes `client`, `baseUrl`, `vendor`). `ChatRetryTiming(sleep, retryAfterCapMillis, transientBackoffMillis)`. `build()` validates positive timeouts naming the field, non-negative waits, and https or loopback host, then derives the client with `cleanClient`.

## Retry classification as implemented

`MAX_REQUESTS = 2`. Before any request: a null credential or one for another provider is `NotConfigured(<vendor>)`; a key that fails `isHeaderSafe` is `Auth`; both send zero requests. After an attempt, a second request is sent only when the attempt was transient and `transientWaitMillis(retry-after, cap, backoff)` is not null:

- Retried: non-2xx with `ChatErrorInfo.transient` (408, 429 without a quota marker, 500, 502, 503, 504, 524, 529), a transient error envelope inside a 200 (`ChatDecoded.transient`), `InterruptedIOException` (Timeout) and any other `IOException` (Network). IO failures call `ensureActive()` first so a cancelled command is not turned into a failure.
- Final: quota 429 (Billing), 400/401/403/404/413 and other client errors, a malformed success, and a transient failure on the second attempt.
- Wait: `retry-after` seconds when within the cap, else the backoff; a `retry-after` beyond the cap ends the call with the failure it already has, with no wait.

The observer is told about every attempt after its status is known: `INITIAL` or `TRANSIENT_RETRY`, HTTP status (null for IO failures), the decoded finish reason (null for failures) and the tool-call count. A tool call reported with `stop` is the finish-reason disagreement note; the result stays a TOOL_USE success. The observer wrapper is the only broad catch (`catch (ignored: Exception)`, no `@Suppress`).

## Tests

`ChatTransportTest` (23 cases, run on the 4.12.0, 5.2.1 and 5.5.0 legs): both vendors through `commandPipeline` with a golden body and typed tool call (OpenAI request id from `x-request-id`, OpenRouter from the body `gen-` id), foreign and missing credentials, the `gpt-6-astra` capability refusal with zero requests, the public `supportsTools` facts, all retry/observer behaviors, three malformed keys, defaults, validation, loopback rule, interceptor stripping and `toString`. `ProvidersApiShapeTest` sweeps the four new public types.

## Verification

`./gradlew :providers:check --offline` green (detekt zero issues, banned-construct scan, strict explicit API, bytecode level, compile floor, all three OkHttp legs); `:core:check` green (no core change).

## Deviations from Plan

None needing a rule. Two process notes:
- Task 2's behavior tests and the transport rewrite were written in the same working session without a separately recorded failing run; the earlier Task 1 transport had no retry, so those tests could not have passed against it.
- The per-plan commit ledger file under the worktree git dir could not be written (the sandbox refuses writes to the shared git dir path), so `plan_head_before` is the base commit named in the dispatch and `commits` was measured with `rev-list` against it.

## Self-Check: PASSED

All five files exist; commits bea8a0d, 2e79f13 and 9f9cd89 exist on the worktree branch; STATE.md and ROADMAP.md untouched.
