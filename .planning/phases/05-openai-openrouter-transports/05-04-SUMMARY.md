---
phase: 05-openai-openrouter-transports
plan: 04
subsystem: providers
tags: [openai, openrouter, chat-completions, request-encoder, golden-bodies]
requires: [05-01, 05-02]
provides:
  - "chat.encodeChatRequest(call, vendor): ByteArray (internal, pure, byte-stable)"
  - "chat.encodeChatMessages(call, vendor): JsonArray (internal)"
  - "test fixtures: logFoodTool, editListCardTool, chatCall, goldenRequest, goldenCaseNames, FIXED_SYSTEM"
  - "golden request bodies for OpenAI and OpenRouter (8 named cases)"
affects: [05-06 transport, 05-10 absent-optional wire leg, 05-11 live capture, Phase 8 transcript conformance]
tech-stack:
  added: []
  patterns: ["hand-written golden bodies compared on the compact string (key order asserted)", "vendor differences read from ChatVendor flags, never by vendor identity"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatRequestFixtures.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt
    - providers/src/test/resources/golden/chat/requests/openai.json
    - providers/src/test/resources/golden/chat/requests/openrouter.json
  modified: []
key-decisions:
  - "parallel_tool_calls false is keyed on the call being Required (named or reshaped) or any strict tool, when the vendor flag allows it; the plan's task text said 'named choice sent' while its must_haves said 'the call is forced'; reshaped OpenAI calls are single-shot too, so the must_haves wording won"
  - "Task 1 already wired the native-replay check (verbatim raw message); Task 3 narrowed it to the allow-list. This kept the unused-parameter gate green at Task 1 without a suppression"
requirements-completed: [PROV-08, PROV-12]
status: complete
plan_head_before: 47b065a1b8944579de9e7ed749cd8d0ac83ab584
commits: 3
actuals:
  tokens: 13500
  tasks: 3
  commits: 3
---

# Phase 5 Plan 4: Chat request encoder Summary

A neutral request now encodes to the exact Chat Completions body each vendor needs, pinned by eight hand-written golden bodies that assert key order, nested tool shape, strict only where safe, and the per-family reasoning and token parameters.

## Tasks

| Task | Commit | What |
|------|--------|------|
| 1 (tracer) | 33a0299 | `encodeChatRequest`, `encodeChatMessages`, fixtures, golden `forced_log_food_strict` for OpenAI; byte-for-byte comparison |
| 2 | 064fa65 | Remaining seven golden cases plus override, strict-authority, purity and key allow-list tests (21 tests in `ChatEncoderTest`) |
| 3 | a6a30bb | Replay allow-list, rebuilt turns, one tool message per result (9 tests in `ChatMessageEncoderTest`) |

## Recorded values

**Final key order:** `model`, `messages`, `tools`, `tool_choice`, `parallel_tool_calls`, `reasoning_effort`, then exactly one of `max_completion_tokens` / `max_tokens`, then `provider`. `tools`, `tool_choice`, `parallel_tool_calls` and `reasoning_effort` appear only when tools are present.

**Golden cases.** `openai.json`: `forced_log_food_strict`, `auto_two_tools`, `legacy_forced`, `no_tools`. `openrouter.json`: `forced_log_food_strict`, `anthropic_reshaped`, `other_vendor_small_budget`, `astra_forced`. Counts match the acceptance greps: `require_parameters` 0 in OpenAI and 2 in OpenRouter, `parallel_tool_calls` 0 in OpenRouter, `reasoning_effort` 2 in OpenAI.

**Replay allow-list:** `role`, `content`, `tool_calls`, `refusal`, `reasoning_details`, kept in the stored order. Everything else (annotations, reasoning text) is dropped because it is response-only. The check is `message.nativeFor(vendor.providerId, call.model)`; a replay stamped for the other Chat vendor, for Anthropic, for another model, or absent is rebuilt.

**Rebuilt assistant turn:** `content` is the non-empty text parts joined by a newline, `null` when there are tool calls and no text, and `""` when the turn is empty; `tool_calls` is omitted when there are none; arguments are the compact JSON string in the model's key order. Tool results become one `{role: tool, tool_call_id, content}` message each; there is no error flag on the wire.

**Reshape:** a required tool on a model that cannot be forced (read from `call.capabilities`, so app overrides apply) sends `tool_choice: "auto"` and appends a blank line plus `Call the <name> tool with your result.` to the last user message. No `require_parameters`. Strict can still apply on OpenAI.

**Detekt-driven split:** none beyond reflowing a few long comment and test lines. `ChatEncoder.kt` has seven private helpers.

## Verification

- `./gradlew :providers:check --offline` green: detekt zero issues, banned-construct scanner, explicit API strict, bytecode level, compile floor, and the 4.12.0, 5.2.1 and 5.5.0 legs.
- `ChatEncoderTest` 21 tests, `ChatMessageEncoderTest` 9 tests; `ChatStrictTest` unchanged and green.
- No planning ids in chat sources; no new dependency, `@Suppress`, `runCatching`, enum, data class or public declaration; nothing under `anthropic/` or `core/` changed.

## Deviations

None of Rules 1-4. Two notes:
- Task 2 was written test-first but the encoder from Task 1 already covered every branch (reshape, floor, require_parameters, strict gating), so the new tests passed without an encoder change; Task 2's commit is tests and goldens only.
- Golden JSON was composed from literal ordered structures typed independently of the encoder, then the tests proved the encoder matches them; the encoder output was never dumped into a golden.

## Self-Check: PASSED

All seven plan files exist; commits 33a0299, 064fa65, a6a30bb are on the worktree branch.
