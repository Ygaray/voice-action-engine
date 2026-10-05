---
phase: 04-anthropic-transport-okhttp-matrix
plan: 03
subsystem: providers
tags: [anthropic, messages-api, prompt-caching, encoder, decoder, okhttp, tracer]
requires: ["04-01", "04-02"]
provides:
  - "public AnthropicProvider { } (internal constructor, nested Builder, companion invoke)"
  - "internal AnthropicTransport(client, baseUrl, ioDispatcher).send(call)"
  - "internal encodeAnthropicRequest(call): ByteArray (+ AnthropicMessageEncoder.kt)"
  - "internal decodeAnthropicResponse(body, requestIdHeader, model): ModelResult"
  - "test fixtures: textBlock, thinkingBlock, toolUseBlock, usageJson, successBody, errorBody, anthropicRequest"
affects: [04-04, 04-05, 04-06, 04-07, 04-08, phase-08]
tech-stack:
  added: []
  patterns: ["public type + internal constructor + Builder with companion invoke", "decoder collapses every unusable 2xx into a reason-only failure through one private carrier exception"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransportTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicFixtures.kt
  modified: []
key-decisions:
  - "Blank system + tools + caching: the single breakpoint goes on the last sorted tool; blank system and no tools: no breakpoint"
  - "Required tool choice on a model whose capabilities say forced choice is unsupported is sent as auto (the reactive 400 reshape stays with 04-06)"
  - "Native replay convention: NativeReplay(anthropic, model, raw = the response content array, thinking blocks included); sent back verbatim only when provider and model both match"
requirements-completed: [PROV-04, PROV-05, PROV-06, PROV-11, PROV-13]
status: complete
commits: 3
plan_head_before: f15ba0cad69e0b7c6ccc7ff754f17f7bb9172c9f
actuals:
  tokens: 21000
  tasks: 3
  commits: 3
metrics:
  completed: 2026-10-01
---

# Phase 4 Plan 03: AnthropicProvider tracer, cache-correct encoding and complete decoding Summary

`AnthropicProvider { }` now serves a routed command end to end: one POST to `/v1/messages` with the routed key, a byte-stable request with exactly one cache breakpoint, and a typed `ModelResult` back, all proven on OkHttp 4.12.0, 5.2.1 and 5.5.0.

## Public surface

- `AnthropicProvider internal constructor(transport, callTimeoutMillis, readTimeoutMillis) : AiProvider`; `id = ANTHROPIC`, `requiresCredential` default true, `capabilities(model) = AnthropicModels.capabilities(model)`, `toString()` = `AnthropicProvider(callTimeoutMillis=<n>, readTimeoutMillis=<n>)`.
- Public `Builder` members: `httpClient: OkHttpClient?` (null), `callTimeoutMillis: Long` (60 000), `readTimeoutMillis: Long` (60 000). A non-positive timeout throws `IllegalArgumentException` whose message names the field.
- Entry point `AnthropicProvider { ... }` via `companion operator fun invoke`.

## Internal seams

- Builder `baseUrl: HttpUrl` (default `https://api.anthropic.com/`; `build()` rejects non-https unless the host is `localhost`, `127.0.0.1` or `::1`) and `ioDispatcher: CoroutineDispatcher` (default `Dispatchers.IO`). Later plans add Builder members only.
- `AnthropicTransport(client, baseUrl, ioDispatcher)` with `send(call)`, plus `client` and `baseUrl` visible to tests. A null credential or one for another provider returns `Failure(NotConfigured(anthropic))` before any request. Non-2xx statuses currently return `Failure(HttpError(), FailureDetails(status, null, safeRequestId))`; 04-04 replaces that branch with the full error table and adds IOException handling.
- Client comes from `cleanClient(httpClient, call, read)`; the call runs inside `withContext(ioDispatcher)`.

## Wire conventions fixed here

- Top-level key order, always: `model`, `max_tokens`, `tools` (only with tools), `tool_choice` (only with tools), `system` (only when non-blank), `messages`. Nothing else is ever sent (no thinking, temperature, top_p, top_k, stream, metadata, stop_sequences).
- Tools sorted by name; `input_schema` is the app's object untouched (key order preserved byte for byte); `strict: true` only when `ToolSpec.strict == true`.
- Exactly one `cache_control {type: ephemeral}`: on the last system block when `cache.staticPrefix` and `capabilities.caching == EXPLICIT_BREAKPOINTS`; never on a message; `conversationTail` is ignored in v1.0.
- `tool_choice`: Auto is `{"type":"auto"}`; Required is `{"type":"tool","name":...}` when `capabilities.supportsForcedToolChoice`, else auto.
- Messages: user text block; tool results as ONE user message of `tool_result` blocks in order (`is_error: true` only on failures); assistant turns replayed verbatim from `nativeFor(anthropic, model)`, otherwise rebuilt from non-empty text and `tool_use` parts.
- Decoding: usage maps `input_tokens` to inputUncached, `cache_read_input_tokens` to cacheRead, `cache_creation_input_tokens` to cacheWrite, `output_tokens` to output (missing/null/negative counts become 0, missing usage object is `Usage.ZERO`). Stop reasons end_turn, tool_use, max_tokens, refusal, pause_turn map by name; `model_context_window_exceeded` maps to `CONTEXT_WINDOW_EXCEEDED`; anything else (including `stop_sequence`) is `OTHER`. Native replay raw = the response `content` array including thinking blocks. A non-object tool input is `MalformedToolArgs`; null/empty/non-JSON bodies, a non-object root, missing or non-array `content`, non-object blocks and tool_use blocks with a blank id or name are `MalformedResponse`. Failures carry reasons only, never body text.

## Tests

- `AnthropicTransportTest` (7): tracer through `commandPipeline` (headers, path, body model/max_tokens, one `cache_control` inside `system`, trace turn provider/model/stop reason, result usage/request id/tool call), default config, timeouts honored and rejected by name, cleartext base URL rejection and loopback acceptance, `toString`, key isolation (foreign and null credentials, server request count 0), provider id and capabilities delegation.
- `AnthropicEncoderTest` (11), `AnthropicDecoderTest` (9), all run on the three legs. RED was observed before each implementation step (2 of 11 encoder tests, 1 of 9 decoder tests).

## Deviations

- [Rule 3 - detekt] `AnthropicEncoder.kt` hit detekt's 11-functions-per-file threshold once all message kinds were encoded, so message encoding moved into a new `AnthropicMessageEncoder.kt` (not in the plan's file list). The shared `TYPE`/`TEXT` constants and `textBlock` helper are `internal` there.
- [Rule 3 - detekt] `SwallowedException` rejects an unused caught exception, so the decoder uses a single `catch (e: IllegalArgumentException)` that reads `e` (`(e as? MalformedAnswer)?.reason ?: MalformedResponse()`). `SerializationException` is a subclass, so it is covered.
- The plan lists the tracer, config and key-isolation tests only; an extra delegation test (id, requiresCredential, capabilities) was added. I deliberately did not test the interim non-2xx branch, since 04-04 replaces it.
- Plan commit ledger (`.git/worktrees/.../gsd-plan-head-before-04-03`) could not be written because the sandbox blocks writes into the git dir; the base `f15ba0cad69e0b7c6ccc7ff754f17f7bb9172c9f` (the dispatch EXPECTED_BASE) was used directly for `commits:` (`git rev-list --count base..HEAD` = 3 at SUMMARY time).
- The `[ -f .git ]` cwd-drift sentinel file was likewise not writable; commits were made from the worktree root and verified by branch name `worktree-agent-af60987bc02b3252a`.

## Verification

- `./gradlew check --offline` green for the whole build (detekt zero baseline, `scanBannedConstructs`, `verifyExplicitApiStrict`, bytecode level, compile floor, 4.12.0 / 5.2.1 / 5.5.0 legs, `:core`, `:keystore`, `:sample`).
- Acceptance greps: `class AnthropicProvider internal constructor` matches once; no `public var/val baseUrl`; `2023-06-01` appears once in the transport; `withContext(ioDispatcher)` present; `model_context_window_exceeded` present in the decoder; no forbidden request keys in the encoder.
- No api.txt, tags, dependency or catalog changes; no ledger or CROSS-REPO-SCOPE-CONTRACT edits; nothing under `.planning/graphs/` or `graphify-out/` staged; STATE.md and ROADMAP.md untouched. No files deleted.

## Commits

- `b351511` feat(04-03): wire a routed command through AnthropicProvider end to end
- `3f0e392` feat(04-03): cache-correct byte-stable encoding with tool results and native replay
- `08af7f8` feat(04-03): complete response decoding with stop-reason table and malformed-answer tests

## Self-Check: PASSED

All nine created files exist and the three task commits are on the branch.
