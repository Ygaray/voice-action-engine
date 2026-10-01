package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.providers.http.safeRequestId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// Anything not listed (stop_sequence, a value added later, a missing field) is reported as OTHER.
private val STOP_REASONS: Map<String, StopReason> = mapOf(
    "end_turn" to StopReason.END_TURN,
    "tool_use" to StopReason.TOOL_USE,
    "max_tokens" to StopReason.MAX_TOKENS,
    "refusal" to StopReason.REFUSAL,
    "pause_turn" to StopReason.PAUSE_TURN,
    "model_context_window_exceeded" to StopReason.CONTEXT_WINDOW_EXCEEDED,
)

/** Carries the typed reason for a 2xx answer that cannot be used; the message never holds any body text. */
private class MalformedAnswer(val reason: FailureReason) : IllegalArgumentException("unusable model answer")

/**
 * Turns the body of a 2xx Messages answer into a typed result. It never throws and never copies body text anywhere:
 * every unusable answer becomes a reason-only failure.
 */
internal fun decodeAnthropicResponse(body: String?, requestIdHeader: String?, model: String): ModelResult =
    try {
        ModelResult.Success(decodeResponse(body, requestIdHeader, model))
    } catch (e: IllegalArgumentException) {
        // Also covers SerializationException, a subclass: both mean the body is not the JSON we were promised.
        ModelResult.Failure((e as? MalformedAnswer)?.reason ?: FailureReason.MalformedResponse())
    }

private fun decodeResponse(body: String?, requestIdHeader: String?, model: String): ModelResponse {
    val root = parseRoot(body)
    val content = root["content"] as? JsonArray ?: throw MalformedAnswer(FailureReason.MalformedResponse())
    val parts = content.mapNotNull { decodePart(it) }
    return ModelResponse(
        message = AssistantMessage(parts, NativeReplay(ProviderId.ANTHROPIC, model, content)),
        stopReason = STOP_REASONS[stringField(root, "stop_reason")] ?: StopReason.OTHER,
        usage = decodeUsage(root["usage"]),
        requestId = safeRequestId(requestIdHeader),
    )
}

private fun parseRoot(body: String?): JsonObject {
    if (body.isNullOrBlank()) throw MalformedAnswer(FailureReason.MalformedResponse())
    return Json.parseToJsonElement(body) as? JsonObject ?: throw MalformedAnswer(FailureReason.MalformedResponse())
}

private fun decodePart(element: JsonElement): AssistantPart? {
    val block = element as? JsonObject ?: throw MalformedAnswer(FailureReason.MalformedResponse())
    return when (stringField(block, "type")) {
        "text" -> stringField(block, "text")?.let { AssistantPart.Text(it) }
        "tool_use" -> decodeToolUse(block)
        else -> null
    }
}

private fun decodeToolUse(block: JsonObject): AssistantPart.ToolCall {
    val id = stringField(block, "id")?.takeIf { it.isNotBlank() }
    val name = stringField(block, "name")?.takeIf { it.isNotBlank() }
    if (id == null || name == null) throw MalformedAnswer(FailureReason.MalformedResponse())
    val input = block["input"] as? JsonObject ?: throw MalformedAnswer(FailureReason.MalformedToolArgs())
    return AssistantPart.ToolCall(id, name, input)
}

private fun decodeUsage(element: JsonElement?): Usage {
    val usage = element as? JsonObject ?: return Usage.ZERO
    return Usage(
        inputUncached = count(usage, "input_tokens"),
        cacheRead = count(usage, "cache_read_input_tokens"),
        cacheWrite = count(usage, "cache_creation_input_tokens"),
        output = count(usage, "output_tokens"),
    )
}

private fun count(usage: JsonObject, key: String): Long =
    ((usage[key] as? JsonPrimitive)?.longOrNull ?: 0L).coerceAtLeast(0L)

private fun stringField(obj: JsonObject, key: String): String? =
    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
