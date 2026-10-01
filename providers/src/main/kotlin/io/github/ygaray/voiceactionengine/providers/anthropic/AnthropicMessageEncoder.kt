package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.transcript.resultsInCallOrder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private const val TYPE = "type"
private const val TEXT = "text"
private const val TOOL_USE = "tool_use"
private const val INPUT = "input"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

// Fixed text: no id, model or provider is ever interpolated into it.
private const val REPLAY_REFUSED = "a replay stamped for another provider or model reached the encoder"

internal fun encodeMessages(call: ProviderRequest): JsonArray = buildJsonArray {
    val messages = call.request.messages
    messages.forEachIndexed { index, message ->
        add(
            when (message) {
                is UserMessage -> userMessage(message)
                is AssistantMessage -> assistantMessage(message, call.model)
                is ToolResultsMessage -> toolResultsMessage(message, precedingCalls(messages.getOrNull(index - 1)))
            },
        )
    }
}

private fun precedingCalls(previous: Message?): List<AssistantPart.ToolCall> =
    (previous as? AssistantMessage)?.toolCalls.orEmpty()

private fun userMessage(message: UserMessage): JsonObject = buildJsonObject {
    put("role", ROLE_USER)
    put("content", buildJsonArray { addJsonObject { textBlock(message.text) } })
}

// A turn this provider produced for this model goes back as received, so thinking blocks and their signatures survive
// the round trip, except that a tool_use block without an object input gets an empty one (see repairedBlock); a turn
// with no replay is rebuilt from the neutral parts. The transport refuses any other
// stamp before encoding, so the check here is a backstop: a stamped turn is never rebuilt.
private fun assistantMessage(message: AssistantMessage, model: String): JsonObject = buildJsonObject {
    put("role", ROLE_ASSISTANT)
    val content = if (message.nativeReplay == null) {
        rebuiltContent(message)
    } else {
        repairedContent(checkNotNull(message.nativeFor(ProviderId.ANTHROPIC, model)) { REPLAY_REFUSED })
    }
    put("content", content)
}

private fun repairedContent(replay: JsonElement): JsonElement =
    if (replay is JsonArray) JsonArray(replay.map { repairedBlock(it) }) else replay

// The decoder reads an absent or null input as no arguments, but the Messages API requires an object, so the replay
// writes `{}` there. Nothing else in a block is touched, and a valid block is returned unchanged. The result depends
// only on the stored turn, so a later request repeats the same bytes and the cached prefix stays stable.
private fun repairedBlock(block: JsonElement): JsonElement {
    val fields = block as? JsonObject
    val isToolUse = (fields?.get(TYPE) as? JsonPrimitive)?.contentOrNull == TOOL_USE
    return if (fields != null && isToolUse && fields[INPUT] !is JsonObject) {
        JsonObject(fields + (INPUT to JsonObject(emptyMap())))
    } else {
        block
    }
}

private fun rebuiltContent(message: AssistantMessage): JsonArray = buildJsonArray {
    message.parts.forEach { part ->
        when (part) {
            is AssistantPart.Text -> if (part.text.isNotEmpty()) addJsonObject { textBlock(part.text) }
            is AssistantPart.ToolCall -> addJsonObject {
                put(TYPE, TOOL_USE)
                put("id", part.id)
                put("name", part.name)
                put(INPUT, part.arguments)
            }
        }
    }
}

// All results of one assistant turn travel in a single user message, in the order the calls were made. A result with
// no text carries no content, because the API makes it optional; an error keeps its flag either way.
private fun toolResultsMessage(message: ToolResultsMessage, calls: List<AssistantPart.ToolCall>): JsonObject =
    buildJsonObject {
        put("role", ROLE_USER)
        put(
            "content",
            buildJsonArray {
                resultsInCallOrder(message.results, calls).forEach { result ->
                    addJsonObject {
                        put(TYPE, "tool_result")
                        put("tool_use_id", result.callId)
                        if (result.content.isNotEmpty()) put("content", result.content)
                        if (result.isError) put("is_error", true)
                    }
                }
            },
        )
    }

internal fun JsonObjectBuilder.textBlock(text: String) {
    put(TYPE, TEXT)
    put(TEXT, text)
}
