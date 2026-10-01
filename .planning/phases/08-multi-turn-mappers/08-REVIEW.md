---
phase: 08-multi-turn-mappers
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 31
files_reviewed_list:
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt
  - providers/build.gradle.kts
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheck.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicLiveCaptureTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransportTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsLiveCaptureTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCapture.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGolden.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizer.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationScript.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/MultiTurnConformanceSuite.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenRouterMultiTurnConformanceTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/WireDialect.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheckTest.kt
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-10-01
**Depth:** standard
**Files Reviewed:** 31
**Status:** issues_found

## Summary

Reviewed the multi-turn mappers: the replay stamp check and tool-call coverage pre-flight
(`ConversationCheck.kt`), the Anthropic and Chat message encoders, the empty-argument repair, result ordering, and the
conformance, golden and live-capture test infrastructure. I also ran `:providers:detekt` and `:core:detekt` (both clean)
and `:providers:test` (green, offline).

The core mechanics hold up. The pre-flight runs once per logical call before any request. The `FailureReason.Other`
codes are fixed tokens, so no id, text or model name reaches a reason, `toString()` or an exception message. The live
recorder never prints bodies, keys or ids, and the committed goldens contain only `*GOLDEN<n>` ids. I scanned all 11
golden files and found no raw call or message ids and no key-shaped strings outside the exempt reasoning values.

No critical issues. The four warnings are all the same class: places where the neutral view the pre-flight validates
differs from the wire the encoder actually sends, so a conversation passes the check and then fails at the provider, or
where the golden hygiene guarantee ("no raw ids") is weaker than its documentation claims.

## Warnings

### WR-01: Chat replay can carry tool calls the neutral turn does not, so the coverage pre-flight is bypassed

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoder.kt:127` (`decodeOutcome`), `ChatResponseParts.kt:47`, `providers/transcript/ConversationCheck.kt:67,80`
**Issue:** `coverageViolation` is computed from `message.toolCalls`, the neutral parts. For Chat, the decoder produces
neutral parts that are a strict subset of the stored raw turn in three cases:
- `finish_reason == "length"` returns `success(turn, MAX_TOKENS, textParts(...), 0)`. Truncated `tool_calls` are
  deliberately not decoded, but the whole `message`, including those `tool_calls`, is stored as the `NativeReplay`.
- A refusal or `content_filter` turn returns an empty parts list, with the same stored raw message.
- A tool call whose `type` is present and not `function` is skipped (`return null`) but stays in the replay.

If the app continues the conversation after any of these turns (for example, appends a user message after a `length`
stop), `conversationViolation` sees zero tool calls and reports the conversation as legal. The encoder then replays the
raw `tool_calls` with no matching `tool` messages. OpenAI and OpenRouter reject that with a 400 ("assistant message with
tool_calls must be followed by tool messages"). That is exactly the failure the pre-flight exists to catch with a
specific reason, and here it is reported as an opaque HTTP error after a billed request.
**Fix:** Make the pre-flight validate what will actually be sent. Either derive the call ids from the replay for stamped
turns, or have the Chat mapper store a replay with the undecoded `tool_calls` removed when it decodes a turn it does not
treat as a tool turn. A minimal pre-flight variant:
```kotlin
// ConversationCheck.kt: for a stamped turn, count the raw tool_calls too
private fun wireCallIds(message: AssistantMessage): List<String> =
    (message.nativeReplay?.raw as? JsonObject)
        ?.get("tool_calls").let { it as? JsonArray }
        ?.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }
        ?: message.toolCalls.map { it.id }
```
Add a conformance test: a `length`-stopped turn with `tool_calls`, followed by a user message, must be refused before
any request.

### WR-02: Object-form tool arguments are decoded but replayed as an object, which the endpoint rejects

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt:82-90` (`repairedCall`), `ChatResponseParts.kt:66-71`
**Issue:** `decodeArguments` explicitly accepts `function.arguments` as a JSON object (the KDoc says "by some routed
upstreams"). `repairedCall` only rewrites arguments that satisfy `isEmptyArgumentsForm`, which is false for any
`JsonObject`, including `{}`. So a turn decoded from an object-form call is replayed on the next request with
`"arguments":{...}` as an object. Chat Completions requires a string there and answers 400 (invalid type). The repair
comment says it fills in only what "the endpoint rejects as input", but this case is rejected too. No test covers it:
`ChatDecoderTest` pins the object decode, and `ChatMessageEncoderTest`/the goldens only exercise string, blank, `null`
and absent forms.
**Fix:** In `repairedCall`, stringify an object-valued `arguments` (compact, key order preserved), and teach the
conformance dialect's `repairedReplayWire` the same rule:
```kotlin
private fun repairedCall(entry: JsonElement): JsonElement {
    val call = entry as? JsonObject ?: return entry
    val function = call[FUNCTION] as? JsonObject ?: return entry
    val args = function[ARGUMENTS]
    val fixed = when {
        isEmptyArgumentsForm(args) -> JsonPrimitive("{}")
        args is JsonObject -> JsonPrimitive(Json.encodeToString(JsonObject.serializer(), args))
        else -> return entry
    }
    return JsonObject(call + (FUNCTION to JsonObject(function + (ARGUMENTS to fixed))))
}
```

### WR-03: Anthropic decoder tolerates an absent or null `input`, but the replay sends it back unrepaired

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt:93-98` (`decodeInput`), `AnthropicMessageEncoder.kt:53-58`
**Issue:** `decodeInput` turns an absent or `null` `input` into an empty object, so the answer decodes as a success.
The stamped content array, however, is replayed verbatim (`nativeFor(...)`), so the next request carries
`{"type":"tool_use","id":...,"name":...}` with no `input` (or `"input":null`). The Messages API requires `input` to be an
object. `AnthropicDecoderTest.anAbsentOrNullToolInputDecodesAsAnEmptyObject` even pins that
`nativeReplay.raw == JsonArray(listOf(noInput))`. The Chat side got a replay repair for the same tolerance; the Anthropic
side did not. The decode succeeds and the following turn 400s.
**Fix:** Either remove the tolerance (keep `MalformedToolArgs`), or repair on replay as Chat does: when a stamped
`tool_use` block lacks an object `input`, write `"input":{}` in `assistantMessage`. Add an encoder test for the repaired
bytes and a note that the repair is deterministic, so the cached prefix stays stable.

### WR-04: Golden hygiene leaves raw provider ids in exempt keys, and the test suite asserts that on purpose

**File:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizer.kt:84`, `ConversationGolden.kt:46-50,164-170`, `ConversationGoldenTest.kt:220-240`
**Issue:** The phase requirement is that goldens must not leak raw ids. Two gaps weaken it:
1. Everything under `EXEMPT_KEYS` is copied untouched and excluded from the id scan. `reasoning_details` entries
   routinely carry their own non-signed fields; for OpenAI-routed models these include ids such as `rs_...`, and Anthropic
   routes can include `toolu_...`. `hygieneAcceptsGoldenIdsAndExemptReasoningText` and
   `reasoningValuesComeOutByteIdentical` assert that `"id":"toolu_real"` and `"id":"rs_call_1"` inside
   `reasoning_details` pass hygiene and are written to the committed file. Only the opaque `signature`/`data` need to be
   byte-exact; an `id` field is not signed.
2. `CONVERSATION_ID_IN_TEXT` only knows `call_`, `toolu_`, `msg_`, `chatcmpl-`, `gen-`, `req_`. OpenAI/OpenRouter also
   issue `resp_`, `rs_`, `fc_`, and so on, which pass hygiene untouched even outside exempt keys.
The key exemption is also by key name at any depth, so a tool argument or content field that happens to be named `data`,
`signature` or `reasoning` bypasses id scrubbing (and `cost`/`user_id` arguments are silently dropped by `DROPPED_KEYS`).
The 11 committed goldens are clean today (verified by scan), so this is a latent leak that the next live capture on an
OpenAI-routed reasoning model could commit.
**Fix:** Narrow the exemption to the signed leaves (`signature`, `data`, and the text values of `thinking`/`reasoning`),
scrub or reject `id` inside `reasoning_details`, and extend the id prefix list:
```kotlin
internal val CONVERSATION_ID_IN_TEXT =
    Regex("(?<![A-Za-z0-9_])(call_|toolu_|msg_|chatcmpl-|gen-|req_|resp_|rs_|fc_)([A-Za-z0-9_-]*)")
```
and flip the two tests so a non-GOLDEN id inside `reasoning_details` is a hygiene violation.

## Info

### IN-01: Pre-flight and encoder backstop use two separate predicates for "replay usable"

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt:56`, `chat/ChatMessageEncoder.kt:67-68`, `AnthropicTransport.kt:113`, `ChatTransport.kt:103`
**Issue:** The "never throws" guarantee relies on the transport's `replayShapeOk` lambda (`is JsonArray` / `is JsonObject`)
agreeing with the encoder's `nativeFor(...)` plus cast. Today they agree. If someone widens one (for example, accepts
`JsonNull` in the pre-flight), the encoder's `checkNotNull` throws `IllegalStateException` out of `complete()` instead of
returning a typed failure, because `attempt()` only catches `IOException`.
**Fix:** Export one internal predicate per dialect (for example `isAnthropicReplayShape`) and use it in both places.

### IN-02: An empty Anthropic assistant turn is rebuilt or replayed as `content: []`

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt:61-73`
**Issue:** `Message.kt` states a provider may return an empty turn. An empty (or all-empty-text) turn is encoded as
`"content":[]`, and a stamped empty turn replays `[]`. The Messages API rejects an empty non-final assistant message with
a 400. `ConversationCheckTest` calls an empty unstamped turn "legal".
**Fix:** Either skip empty assistant turns in `encodeMessages`, or add an `empty_assistant_turn` pre-flight code.

### IN-03: Sanitizer and hygiene details

**File:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizer.kt:51-54`, `ChatGoldenSanitizer.kt:35` (shared `BEARER_IN_TEXT`)
**Issue:**
- `BEARER_IN_TEXT = Regex("Bearer\\s+\\S+")` is case-sensitive, so `bearer <token>` is not flagged or redacted.
- The error refusal in `sanitize` inspects only the top-level object, so an error nested under `choices[0]` (a wrapped
  vendor error) is not refused.
- `ConversationRecorder.finish` hard-codes the conversation code `"A2"` as the thinking conversation, coupling it to
  `ConversationPlans` by string.
- Hygiene tests iterate manifest rows only, so a golden file that is not in `MANIFEST.tsv` is never scanned.
**Fix:** Use `(?i)Bearer\s+\S+`; recurse for `error` in `sanitize` or at the choices level; add a `requiresThinking`
flag to `ConversationPlan`; add a test that every `*.json` under `golden/conversations/` has a manifest row.

### IN-04: Cross-provider or cross-model history is now a hard refusal instead of a lossy rebuild

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheck.kt:70-78`
**Issue:** Before this phase a stamped turn for another provider or model was rebuilt from the neutral parts. Now it is a
`replay_mismatch` failure. That is consistent with the loud-failure core value and is documented in `Message.kt`, but a
tier ladder that escalates mid-conversation across models (for example Haiku to Sonnet) will fail every such request with
no recovery path in the engine. The consumer must strip the stamps itself.
**Fix:** Keep the behavior, but document the escalation consequence at the `AiProvider`/agentic-loop level, or provide a
public, additive helper that returns the conversation with replays removed so a ladder can opt in to the lossy rebuild.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
