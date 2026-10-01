package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.transcript.resultsInCallOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val ROLE = "role"
private const val CONTENT = "content"
private const val TOOL_CALLS = "tool_calls"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val ROLE_TOOL = "tool"
private const val TEXT_SEPARATOR = "\n"

// Fixed text: no id, model or provider is ever interpolated into it.
private const val REPLAY_REFUSED = "a replay stamped for another provider or model reached the encoder"

/**
 * Encodes the request's conversation as Chat Completions messages, oldest first. The system prompt is not part of it.
 *
 * The three message kinds are switched over exhaustively. Each tool result becomes its own `tool` message, because the
 * dialect has no batch message, in the order of the calls it answers. An error result's text goes inside an `error`
 * object because the dialect has no error flag; other results go out exactly as the app produced them.
 */
internal fun encodeChatMessages(call: ProviderRequest, vendor: ChatVendor): JsonArray = buildJsonArray {
    val messages = call.request.messages
    messages.forEachIndexed { index, message ->
        when (message) {
            is UserMessage -> add(userMessage(message))
            is AssistantMessage -> add(assistantMessage(message, call, vendor))
            is ToolResultsMessage ->
                toolMessages(message, precedingCalls(messages.getOrNull(index - 1))).forEach { add(it) }
        }
    }
}

private fun userMessage(message: UserMessage): JsonObject = buildJsonObject {
    put(ROLE, ROLE_USER)
    put(CONTENT, message.text)
}

// A turn this vendor produced for this model goes back as received, keeping only the fields the endpoint takes as
// input; a turn with no replay is rebuilt from the neutral parts. The transport refuses any other stamp before
// encoding, so the check here is a backstop: a stamped turn is never rebuilt.
private fun assistantMessage(message: AssistantMessage, call: ProviderRequest, vendor: ChatVendor): JsonObject {
    if (message.nativeReplay == null) return rebuiltAssistantMessage(message)
    val replay = message.nativeFor(vendor.providerId, call.model)?.let { chatReplayMessage(it) }
    return repairedReplay(checkNotNull(replay) { REPLAY_REFUSED })
}

private fun rebuiltAssistantMessage(message: AssistantMessage): JsonObject =
    buildJsonObject {
        put(ROLE, ROLE_ASSISTANT)
        val toolCalls = message.toolCalls
        val text = message.parts.filterIsInstance<AssistantPart.Text>().map { it.text }.filter { it.isNotEmpty() }
        if (text.isEmpty() && toolCalls.isNotEmpty()) {
            put(CONTENT, JsonNull)
        } else {
            put(CONTENT, text.joinToString(TEXT_SEPARATOR))
        }
        if (toolCalls.isNotEmpty()) put(TOOL_CALLS, rebuiltToolCalls(toolCalls))
    }

private fun rebuiltToolCalls(calls: List<AssistantPart.ToolCall>): JsonArray = buildJsonArray {
    calls.forEach { call ->
        add(
            buildJsonObject {
                put("id", call.id)
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", call.name)
                        // The wire carries the arguments as a string of compact JSON, in the model's key order.
                        put("arguments", Json.encodeToString(JsonObject.serializer(), call.arguments))
                    },
                )
            },
        )
    }
}

private fun toolMessages(message: ToolResultsMessage, calls: List<AssistantPart.ToolCall>): List<JsonObject> =
    resultsInCallOrder(message.results, calls).map { result ->
        buildJsonObject {
            put(ROLE, ROLE_TOOL)
            put("tool_call_id", result.callId)
            put(CONTENT, toolContent(result))
        }
    }

// The dialect has no error flag, so an error's text goes inside an `error` object, built with a JSON builder so any
// quote, backslash or newline in the text is escaped. Every other result is sent exactly as the app produced it.
private fun toolContent(result: ToolResult): String =
    if (result.isError) {
        Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", result.content) })
    } else {
        result.content
    }

private fun precedingCalls(previous: Message?): List<AssistantPart.ToolCall> =
    (previous as? AssistantMessage)?.toolCalls.orEmpty()
