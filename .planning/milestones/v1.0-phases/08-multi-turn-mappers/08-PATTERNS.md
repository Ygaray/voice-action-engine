# Phase 8: Multi-turn Mappers - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 22 (1 new main file, 6 edited main files, ~9 new test files, ~8 edited tests, build.gradle.kts, resources)
**Analogs found:** all matched in-module (all tracked under `providers/`; no mirror paths)

All paths relative to `/home/yahir/Projects/Reusable/android/voice-action-engine/providers/src`. `B` = `main/kotlin/io/github/ygaray/voiceactionengine/providers`, `T` = `test/kotlin/io/github/ygaray/voiceactionengine/providers`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `B/transcript/ConversationCheck.kt` (new, internal) | utility (pure validator) | transform | `B/chat/ChatStrict.kt`, `B/chat/ChatResponseParts.kt` (internal pure fns) | role-match |
| `B/anthropic/AnthropicMessageEncoder.kt` (edit) | encoder | transform | itself (lines 41-76) | exact |
| `B/chat/ChatMessageEncoder.kt` (edit) | encoder | transform | itself (lines 49-97) | exact |
| `B/anthropic/AnthropicTransport.kt`, `B/chat/ChatTransport.kt` (edit, pre-flight) | transport | request-response | their own `send` (Chat 89-96, Anthropic 98-106) | exact |
| `B/chat/ChatResponseParts.kt`, `B/anthropic/AnthropicDecoder.kt` (edit, empty args) | decoder | transform | themselves | exact |
| `T/conformance/WireDialect.kt` + `MultiTurnConformanceSuite.kt` + 3 subclasses | test (abstract suite) | request-response | `T/chat/ChatMessageEncoderTest.kt`, `T/chat/ChatGoldenReplayTest.kt` | role-match |
| `T/conformance/ConversationGolden.kt` (manifest loader + hygiene) | test util | file-I/O | `T/chat/ChatGoldenReplayTest.kt` (manifest + `goldenHygieneViolations`, line 189) | exact |
| `T/conformance/ConversationSanitizer.kt` | test util | transform | `T/chat/ChatGoldenSanitizer.kt` (regexes at 32-35, scrub ~126) | role-match (shared id map instead of per-body `Pass`) |
| `T/conformance/ConversationCapture.kt` | test (live harness) | request-response | `T/chat/ChatCompletionsLiveCaptureTest.kt` `CaptureRun` (329-373) | exact |
| `T/conformance/ConversationCaptureRunTest.kt` (key-free; no "Live" in name) | test | request-response | `T/chat/ChatCaptureRunTest.kt` (MockWebServer loopback) | exact |
| `T/ConversationCheckTest.kt` | test | transform | `T/chat/ChatMessageEncoderTest.kt` | role-match |
| Anthropic live recorder edits | test (live) | request-response | `T/anthropic/AnthropicLiveCaptureTest.kt` + `CaptureRun` | role-match |
| `providers/build.gradle.kts` (edit) | config | n/a | `liveChatCompletionsCapture` (lines ~85-100) | exact |
| `test/resources/golden/conversations/MANIFEST.tsv` + fixtures | resource | file-I/O | `test/resources/golden/chat/responses/MANIFEST.tsv` | exact |

## Pattern Assignments

### Chat message encoder edits (`B/chat/ChatMessageEncoder.kt`)

Current silent-rebuild site to flip to guard-then-replay (lines 49-57, 91-97):
```kotlin
private val REPLAY_FIELDS = setOf("role", "content", "tool_calls", "refusal", "reasoning_details")   // line 26, keep
private fun assistantMessage(message: AssistantMessage, call: ProviderRequest, vendor: ChatVendor): JsonObject {
    val replay = message.nativeFor(vendor.providerId, call.model) as? JsonObject
    return if (replay != null) replayedFields(replay) else rebuiltAssistantMessage(message)
}
private fun replayedFields(replay: JsonObject): JsonObject = JsonObject(replay.filterKeys { it in REPLAY_FIELDS })
private fun toolMessages(message: ToolResultsMessage): List<JsonObject> = message.results.map { result ->
    buildJsonObject { put(ROLE, ROLE_TOOL); put("tool_call_id", result.callId); put(CONTENT, result.content) }
}
```
Style to copy: file-private `const val`s, `buildJsonObject`/`put`, short "why" comments, no string-concatenated JSON. For D-05, wrap with `Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", result.content) })` only when `isError` (file already imports `Json`, `buildJsonObject`, `put`). Rebuilt tool-call arguments already use `Json.encodeToString(JsonObject.serializer(), call.arguments)` (line 83).

### Anthropic message encoder edits (`B/anthropic/AnthropicMessageEncoder.kt`)

```kotlin
private fun assistantMessage(message: AssistantMessage, model: String): JsonObject = buildJsonObject {
    put("role", ROLE_ASSISTANT)
    put("content", message.nativeFor(ProviderId.ANTHROPIC, model) ?: rebuiltContent(message))   // line 43: becomes nativeReplay?.raw ?: rebuilt after guard
}
// toolResultsMessage lines 61-76: one user message, tool_result blocks via addJsonObject, `if (result.isError) put("is_error", true)`
```
Omit `content` when result text is empty; order by preceding turn's call emission order.

### Transport pre-flight (`ConversationCheck` call site)

Chat (`B/chat/ChatTransport.kt:89-96`); Anthropic is identical in shape (`AnthropicTransport.kt:98-106`):
```kotlin
suspend fun send(call: ProviderRequest): ModelResult {
    val credential = call.credential
    val refusal = refusalFor(vendor, credential)
    if (refusal != null || credential == null) {
        return ModelResult.Failure(refusal ?: FailureReason.NotConfigured(vendor.providerId))
    }
    // INSERT here: conversationViolation(call, vendor.providerId)?.let { return ModelResult.Failure(FailureReason.Other(it)) }
    return withContext(ioDispatcher) { sendWithRetry(call, credential, 1) }
}
```
Check goes after the credential check and before `withContext` so it runs once and sends zero requests. `FailureReason.Other(code)` requires `[A-Za-z0-9_.:-]`, 1-64 chars.

### Internal helper style (`ConversationCheck.kt`)
Pure `internal fun` returning `String?` (sketch in RESEARCH Pattern 1, lines 232-251). Follow: no I/O, no logging, no `runCatching`, no `catch (Exception)` (only `core/internal/Guarded.kt` may collapse), named constants for codes (detekt MagicNumber), no planning ids in comments. Codes: `replay_mismatch`, `tool_result_missing`, `tool_result_unexpected`, `tool_call_id_duplicate`, `tool_call_unanswered`.

### Test fixtures (reuse, do not fork)
- `T/chat/ChatFixtures.kt` (lines 14-60): `CHAT_GOLDEN_MODEL`, `chatToolCall(id, name, arguments)`, `chatMessage(content, toolCalls, refusal)`, `chatUsage`, `chatBody(message, finishReason, usage)`.
- `T/chat/ChatRequestFixtures.kt`: `logFoodTool`, `editListCardTool`, `chatCall(vendor, model, request, key)`, `goldenRequest`.
- `T/anthropic/AnthropicFixtures.kt`: `textBlock`, `thinkingBlock` (fixed `"sig-1"`), `toolUseBlock`, `usageJson`, `successBody`, `anthropicRequest(model, request, key)`.
- Unit-test style (`T/chat/ChatMessageEncoderTest.kt:1-40`): JUnit4 `class XTest { @Test fun backtickFreeSentenceNames() }`, raw JSON parsed with `Json.parseToJsonElement("""...""".trimIndent()) as JsonObject`, `org.junit.Assert.*`. Tests to flip deliberately (call out in plan objectives): `AnthropicEncoderTest.aMatchingNativeReplayIsSentVerbatimAndAnyOtherIsRebuiltFromParts`, `ChatMessageEncoderTest.aReplayFromAnotherVendorProviderOrModelIsRebuilt`, `ChatMessageEncoderTest.eachToolResultIsItsOwnToolMessageInOrderAndErrorsAreSentUnchanged`, `ChatDecoderTest.badArgumentsAreMalformedToolArgs`.

### Golden/manifest + hygiene (`ConversationGolden.kt`, `ConversationSanitizer.kt`)
- Manifest convention: `test/resources/golden/chat/responses/MANIFEST.tsv` header comments start with `#`, tab-separated, `provenance` = `derived|captured`, `source` = `derived.json#case` or `captured/<vendor>-<label>.json`. Mirror for `golden/conversations/MANIFEST.tsv`.
- Hygiene: reuse, do not copy, `ChatGoldenSanitizer.kt:32-35` (`ID_IN_TEXT`, `GOLDEN_TAIL`, `KEY_IN_TEXT`, `BEARER_IN_TEXT`; all `internal val`) and `goldenHygieneViolations(text)` (`ChatGoldenReplayTest.kt:189-192`). They live in package `...providers.chat`, so import them from the conformance package.
- Deviation required (Pitfall 4): one shared `idMap` per conversation, exact-match rewrite, skip rewriting under keys `thinking`, `signature`, `data`, `reasoning`, `reasoning_details` (scan only), compact canonical output via `Json.encodeToString(JsonElement.serializer(), …)`; existing sanitizer pretty-prints and numbers ids per body.

### Capture runner (`ConversationCapture.kt`)
Copy `CaptureRun` shape (`ChatCompletionsLiveCaptureTest.kt:329-373`):
```kotlin
private val client = cleanClient(null, CALL_TIMEOUT_MILLIS, CALL_TIMEOUT_MILLIS)
// count goes up before the request is built, so a failed send still counts
sent[call.vendorName] = perVendor(call.vendorName) + 1
val bytes = encodeChatRequest(chatCall(call.vendor, call.model, call.request, key), call.vendor)
val reply = try { runBlocking { client.newCall(request(call, key, bytes)).await() } }
            catch (failure: IOException) { println("LIVE_CAPTURE call=... result=no_answer cause=${failure.javaClass.simpleName}"); unmet.add(...); return }
```
Constructor takes `keys`, `rawDir`, `goldenDir`, and `baseUrlFor: (ChatVendor) -> String` (default production URL; tests point it at MockWebServer). For Anthropic use `encodeAnthropicRequest(call)` + `decodeAnthropicResponse` the same way (Pitfall 7). Separate `ConversationPlan` with its own ceilings; do not touch `CapturePlan.all` (`ChatCaptureCallPlanTest` pins 11 calls, 12/6 ceilings). Key-free proof: copy `ChatCaptureRunTest.kt` (loopback MockWebServer; asserts `goldenHygieneViolations` empty at line 75). Test `println("LIVE_CAPTURE ...")` is allowed in tests only.

### Gradle opt-in tasks (`providers/build.gradle.kts`)
Extend, do not fork. `liveChatCompletionsCapture` pattern (lines ~85-100):
```kotlin
filter { includeTestsMatching("*ChatCompletionsLiveCaptureTest") }
outputs.upToDateWhen { false }
val optIn = providers.environmentVariable("VAE_LIVE_CHAT"); onlyIf { optIn.orNull == "1" }
systemProperty("vae.golden.dir", layout.projectDirectory.dir("src/test/resources/golden/chat/responses/captured").asFile.absolutePath)
systemProperty("vae.raw.dir", layout.buildDirectory.dir("live-chat/raw").get().asFile.absolutePath)
```
`liveAnthropicCapture` (line 70) has no system properties today; add `vae.golden.dir`/`vae.raw.dir` the same way (conversation dirs under `golden/conversations`). `test` and matrix legs use `filter { excludeTestsMatching("*Live*") }`, so key-free classes must not contain "Live".

## Shared Patterns

- **Typed failure, no throws:** `ModelResult.Failure(FailureReason.Other(code))`; codes only, never ids/transcripts/args (secrets rule).
- **JSON construction:** always `buildJsonObject`/`put`; never string concatenation.
- **Canonical encoding:** `Json.encodeToString(JsonElement.serializer(), x)` is the single printer for replay, goldens, and idempotence assertions (`canonical(file) == file.trim()`).
- **Detekt/banned constructs (library code):** no `runCatching`, `println`, `printStackTrace`, `android.util.Log`, `okhttp3.internal.*`; named constants for numbers; no planning ids in comments.
- **Tests:** JUnit4 + legacy `okhttp3.mockwebserver` only; run on 4.12.0/5.2.1/5.5.0 legs, so no `mockwebserver3`, no `QueueDispatcher` subclass.

## No Analog Found

| File | Role | Reason |
|---|---|---|
| `MultiTurnConformanceSuite.kt` (abstract + `WireDialect`) | test | No abstract/shared dialect suite exists; build from the two dialects' encoder tests and `chatCall`/`anthropicRequest` fixtures per RESEARCH "Pattern 4" (append-only prefix, negative control stub mapper). |
| Anthropic goldens | resource | None exist; format follows the Chat manifest convention. |

## Metadata

**Analog search scope:** `providers/src/main`, `providers/src/test`, `providers/build.gradle.kts`
**Files read:** ChatMessageEncoder, AnthropicMessageEncoder, ChatTransport/AnthropicTransport `send`, ChatFixtures, CaptureRun excerpt, hygiene grep, build.gradle.kts tasks, MANIFEST header
**Pattern extraction date:** 2026-10-01
