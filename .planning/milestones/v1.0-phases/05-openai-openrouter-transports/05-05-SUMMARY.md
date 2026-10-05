---
phase: 05-openai-openrouter-transports
plan: 05
subsystem: providers
tags: [openai, openrouter, chat-completions, response-decoder, usage-normalization]
requires: [05-02, 05-03, 05-04]
provides:
  - "chat.decodeChatResponse(body, requestIdHeader, model, vendor, toolRequired): ChatDecoded (internal, pure, never throws)"
  - "chat.ChatDecoded(result, transient, finishReason, toolCalls)"
  - "chat.decodeChatToolCalls(message), chat.decodeChatUsage(element), chat.ChatMalformed(reason), chat.unusableAnswer(reason), chat.chatStringField(obj, key)"
  - "test fixtures: chatToolCall, chatMessage, chatUsage, chatBody, chatErrorEnvelope, openAiErrorBody, CHAT_GOLDEN_MODEL"
affects: [05-06 transport, 05-07 golden replay, 05-09 parity, 05-10 absent-optional and canary]
tech-stack:
  added: []
  patterns: ["private carrier exception caught as IllegalArgumentException (Anthropic decoder shape)", "Nothing-returning helper instead of repeated throw statements (detekt ThrowsCount)", "one small private function per precedence row"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatFixtures.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt
  modified: []
key-decisions:
  - "An empty-string message.refusal is not a refusal (a router sending refusal:\"\" must not turn a good tool call into REFUSAL); only a non-null, non-empty refusal or finish_reason content_filter is"
  - "Whitespace-only arguments (\" \") are invalid JSON and give MalformedToolArgs; only the exact empty string means no arguments"
  - "A refusal answer carries no parts at all (not even partial content), so no model text can leak through a part"
  - "finish_reason error reports the finish reason to the observer (finishReason set) while the failure itself carries only FailureDetails(200, safeToken(native_finish_reason), request id)"
requirements-completed: [PROV-08, PROV-12, TEL-01]
status: complete
plan_head_before: 8a85460cf36fe626167157d1da9de9bb023044b9
commits: 4
actuals:
  tokens: 8700
  tasks: 3
  commits: 4
---

# Phase 5 Plan 5: Chat response decoder Summary

Every 2xx Chat Completions answer from OpenAI or OpenRouter now becomes a typed engine result with a fixed precedence, tool-call arguments decode strictly without adding keys, and usage lands in the same four buckets as Anthropic with cached tokens counted once.

## Precedence as implemented

`decodeChatResponse` returns `ChatDecoded`; the first applicable row wins:

1. Blank, non-JSON or non-object body: `Failure(MalformedResponse)`.
2. Top-level `error` object on any 2xx (checked right after the root parse, before `choices`): `Failure(info.reason(), info.details())` through `chatEnvelopeError`, `transient` as the envelope says (429/502 transient, OpenAI-shaped error on a 200 final).
3. `choices` missing, not an array, empty, or `choices[0]` without a `message` object: `MalformedResponse`.
4. `finish_reason == "error"`: `Failure(HttpError)` with `FailureDetails(200, safeToken(native_finish_reason), request id)` (a hostile native value is dropped to null).
5. `message.refusal` non-null and non-empty, or `finish_reason == "content_filter"`: `Success`, `StopReason.REFUSAL`, no parts, toolCalls 0 (beats tool calls).
6. `finish_reason == "length"`: `Success`, `StopReason.MAX_TOKENS`, text part only, tool-call arguments never decoded.
7. Non-empty decoded `tool_calls`: `Success`, `StopReason.TOOL_USE` whatever the finish reason (presence decides).
8. No calls and finish `stop`: `Failure(NoToolCall)` when `toolRequired`, else `Success(END_TURN)` with the text part.
9. Finish `tool_calls` with no usable call (for example only `custom` calls): `MalformedResponse`.
10. Any other or missing finish reason: `Success(OTHER)`.

Request id: `safeRequestId(header)`, else (only when `vendor.requestIdInBody`) `safeRequestId(body id)`. The assistant message carries `NativeReplay(vendor.providerId, model, choices[0].message)` as received.

## ChatDecoded fields

`result: ModelResult`, `transient: Boolean` (true only for an error envelope that one more attempt can clear), `finishReason: String?` (`safeToken` of the received finish reason, null for malformed answers and envelope errors, reported as received for the transport's observer), `toolCalls: Int` (decoded calls; 0 for every failure, refusal and length). `toString` prints these four only.

## Tool-call arguments

A JSON string holding an object decodes to that object with keys exactly as sent; `""` decodes to `{}`; an already-object `arguments` is accepted. Invalid JSON, arrays, scalars, JSON null, a missing field or a non-string/non-object value give `MalformedToolArgs`. Blank id or name, a missing `function` object or a non-object call give `MalformedResponse`. A call whose `type` is present and not `function` is skipped. The decoder never consults a schema, so omitted optionals stay absent (tested with an EDIT-shaped call).

## Usage clamps

`prompt_tokens` is taken to include `cache_write_tokens` (documented assumption A5):

- `cacheRead = min(prompt_tokens_details.cached_tokens, prompt_tokens)`
- `cacheWrite = min(prompt_tokens_details.cache_write_tokens, prompt_tokens - cacheRead)`
- `inputUncached = prompt_tokens - cacheRead - cacheWrite`
- `output = completion_tokens` (reasoning tokens are already inside it; OpenRouter `cost`, `cost_details` and `*_tokens_details` extras are ignored)

Missing usage is `Usage.ZERO`; null, negative, string, fractional or boolean counts are 0. The total never exceeds `prompt_tokens + completion_tokens`. Verified: 1920/55/cached 1800 gives `Usage(120, 1800, 0, 55)` (total 1975); 2420/55/1800/500 gives `Usage(120, 1800, 500, 55)` (total 2475); cached 3000 on a prompt of 1000 clamps to cacheRead 1000, uncached 0.

## Fixture builder names (test source set, package `...providers.chat`, all `internal`)

`chatToolCall(id, name, arguments: String)`, `chatMessage(content, toolCalls = emptyList(), refusal = null)`, `chatUsage(prompt, completion, cached = null, cacheWrite = null)`, `chatBody(message, finishReason, usage = null, id = "chatcmpl-GOLDEN", nativeFinishReason = null)`, `chatErrorEnvelope(code, message, raw = null)`, `openAiErrorBody(type, code, message)`, constant `CHAT_GOLDEN_MODEL`. A null content, refusal or finish reason is written as JSON null, as the APIs do.

## Verification

- `ChatDecoderTest`: 26 tests, 0 failures on all three legs (OkHttp 4.12.0, 5.2.1, 5.5.0).
- `./gradlew check --offline` green for the whole repo: detekt zero issues, `scanBannedConstructs`, explicit API strict, bytecode level, compile floor, matrix legs.
- Acceptance greps: `chatEnvelopeError(` x1, `NativeReplay(vendor.providerId` x1, `catch (e: IllegalArgumentException)` x1, no `catch (e: Exception)` or `runCatching`, no planning ids in the chat sources.
- CANARY tests: bodies containing CANARY text (malformed body, array arguments, 200 envelope message and `metadata.raw`, refusal, hostile native finish reason) produce results whose `toString`, details and reason contain no CANARY text.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] detekt gates drove structure**
- Found during: Task 1
- Issue: `MatchingDeclarationName` (single top-level class `ChatDecoded` in `ChatDecoder.kt`), `ThrowsCount` (more than 2 throws per function), `UnusedParameter` (`toolRequired`), `MaxLineLength`.
- Fix: a private `Turn` class in the decoder file (also carries the per-answer context), a `Nothing`-returning `unusableAnswer()` helper instead of inline throws, `toolRequired` used by the no-tool-call row from Task 1, long lines wrapped.
- Files: `ChatDecoder.kt`, `ChatResponseParts.kt`
- Commit: 3da5415

### Process notes

- The arguments and usage decoding were written in full in Task 1 (they are the tracer's dependencies), so Task 2 and Task 3 tests for those edges were green on first run; the genuine RED set for Task 2 was the envelope, finish-reason and tool-turn rows (8 failing tests, committed as `test(05-05)` before the implementation).
- The empty-string refusal and whitespace-only arguments choices (key-decisions above) are small judgement calls inside the plan's stated rows, not scope changes.

**Total deviations:** 1 auto-fixed (Rule 3). **Impact:** none on behavior; structure only.

## Authentication gates

None.

## Deferred / out of scope

None. No public API, catalog, `api.txt`, `STATE.md` or `ROADMAP.md` changes.

## Self-Check: PASSED

- FOUND: providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoder.kt
- FOUND: providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt
- FOUND: providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatFixtures.kt
- FOUND: providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt
- FOUND commits: 3da5415, 68c2ca4, 6973780, 8dbc19c
