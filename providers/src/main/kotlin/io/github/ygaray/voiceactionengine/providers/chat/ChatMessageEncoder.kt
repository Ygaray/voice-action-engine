package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
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

// Response-only fields (annotations, reasoning text and the like) are rejected or ignored as input, so a replay keeps
// just these. The reasoning details stay because a router needs them back to continue a reasoning turn.
private val REPLAY_FIELDS = setOf("role", "content", "tool_calls", "refusal", "reasoning_details")

/**
 * Encodes the request's conversation as Chat Completions messages, oldest first. The system prompt is not part of it.
 *
 * The three message kinds are switched over exhaustively. Each tool result becomes its own `tool` message, because the
 * dialect has no batch message.
 */
internal fun encodeChatMessages(call: ProviderRequest, vendor: ChatVendor): JsonArray = buildJsonArray {
    call.request.messages.forEach { message ->
        when (message) {
            is UserMessage -> add(userMessage(message))
            is AssistantMessage -> add(assistantMessage(message, call, vendor))
            is ToolResultsMessage -> toolMessages(message).forEach { add(it) }
        }
    }
}

private fun userMessage(message: UserMessage): JsonObject = buildJsonObject {
    put(ROLE, ROLE_USER)
    put(CONTENT, message.text)
}

// A reply this vendor produced for this model goes back as received, keeping only the fields the endpoint takes as
// input; anything else is rebuilt from the neutral parts.
private fun assistantMessage(message: AssistantMessage, call: ProviderRequest, vendor: ChatVendor): JsonObject {
    val replay = message.nativeFor(vendor.providerId, call.model) as? JsonObject
    return if (replay != null) replayedFields(replay) else rebuiltAssistantMessage(message)
}

private fun replayedFields(replay: JsonObject): JsonObject =
    JsonObject(replay.filterKeys { it in REPLAY_FIELDS })

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

private fun toolMessages(message: ToolResultsMessage): List<JsonObject> = message.results.map { result ->
    buildJsonObject {
        put(ROLE, ROLE_TOOL)
        put("tool_call_id", result.callId)
        put(CONTENT, result.content)
    }
}
