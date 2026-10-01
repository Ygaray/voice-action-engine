package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val TYPE = "type"
internal const val TEXT = "text"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

internal fun encodeMessages(call: ProviderRequest): JsonArray = buildJsonArray {
    call.request.messages.forEach { message ->
        add(
            when (message) {
                is UserMessage -> userMessage(message)
                is AssistantMessage -> assistantMessage(message, call.model)
                is ToolResultsMessage -> toolResultsMessage(message)
            },
        )
    }
}

private fun userMessage(message: UserMessage): JsonObject = buildJsonObject {
    put("role", ROLE_USER)
    put("content", buildJsonArray { addJsonObject { textBlock(message.text) } })
}

// A reply this provider produced for this model goes back exactly as received, so thinking blocks and their signatures
// survive the round trip; anything else is rebuilt from the neutral parts.
private fun assistantMessage(message: AssistantMessage, model: String): JsonObject = buildJsonObject {
    put("role", ROLE_ASSISTANT)
    put("content", message.nativeFor(ProviderId.ANTHROPIC, model) ?: rebuiltContent(message))
}

private fun rebuiltContent(message: AssistantMessage): JsonArray = buildJsonArray {
    message.parts.forEach { part ->
        when (part) {
            is AssistantPart.Text -> if (part.text.isNotEmpty()) addJsonObject { textBlock(part.text) }
            is AssistantPart.ToolCall -> addJsonObject {
                put(TYPE, "tool_use")
                put("id", part.id)
                put("name", part.name)
                put("input", part.arguments)
            }
        }
    }
}

// All results of one assistant turn travel in a single user message, in the order the calls were made.
private fun toolResultsMessage(message: ToolResultsMessage): JsonObject = buildJsonObject {
    put("role", ROLE_USER)
    put(
        "content",
        buildJsonArray {
            message.results.forEach { result ->
                addJsonObject {
                    put(TYPE, "tool_result")
                    put("tool_use_id", result.callId)
                    put("content", result.content)
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
