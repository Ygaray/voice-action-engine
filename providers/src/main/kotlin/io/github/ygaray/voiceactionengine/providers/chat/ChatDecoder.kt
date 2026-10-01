package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.providers.http.safeRequestId
import io.github.ygaray.voiceactionengine.providers.http.safeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val FINISH_STOP = "stop"
private const val FINISH_TOOL_CALLS = "tool_calls"

/**
 * What decoding a 2xx Chat Completions answer produced.
 *
 * @property result the typed outcome.
 * @property transient true when the answer was an error wrapped in a 200 that one more attempt can clear.
 * @property finishReason the answer's finish reason when it is a safe identifier, else null; reported to the attempt
 * observer, never treated as an error by itself.
 * @property toolCalls how many tool calls the answer carried.
 */
internal class ChatDecoded(
    val result: ModelResult,
    val transient: Boolean,
    val finishReason: String?,
    val toolCalls: Int,
) {
    override fun toString(): String =
        "ChatDecoded(result=$result, transient=$transient, finishReason=$finishReason, toolCalls=$toolCalls)"
}

/**
 * Turns the body of a 2xx Chat Completions answer into a typed result. It never throws and never copies body text
 * anywhere: every unusable answer becomes a reason-only failure.
 *
 * A turn is a tool turn when the answer carries tool calls, whatever its finish reason says. The request id is the
 * vendor's header value, else, only for a vendor that puts it in the body, the body's `id`.
 */
internal fun decodeChatResponse(
    body: String?,
    requestIdHeader: String?,
    model: String,
    vendor: ChatVendor,
    toolRequired: Boolean,
): ChatDecoded =
    try {
        decodeAnswer(body, requestIdHeader, model, vendor, toolRequired)
    } catch (e: IllegalArgumentException) {
        // Also covers SerializationException, a subclass: both mean the body is not the JSON we were promised.
        val reason = (e as? ChatMalformed)?.reason ?: FailureReason.MalformedResponse()
        ChatDecoded(ModelResult.Failure(reason), false, null, 0)
    }

private fun decodeAnswer(
    body: String?,
    requestIdHeader: String?,
    model: String,
    vendor: ChatVendor,
    toolRequired: Boolean,
): ChatDecoded {
    val root = parseRoot(body)
    val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: unusableAnswer()
    val message = choice["message"] as? JsonObject ?: unusableAnswer()
    val bodyId = if (vendor.requestIdInBody) safeRequestId(chatStringField(root, "id")) else null
    val turn = Turn(
        message = message,
        finish = chatStringField(choice, "finish_reason"),
        replay = NativeReplay(vendor.providerId, model, message),
        usage = decodeChatUsage(root["usage"]),
        requestId = safeRequestId(requestIdHeader) ?: bodyId,
    )
    return decodeToolTurn(turn, toolRequired)
}

/** The parts of one answer's first choice that every outcome row reads. */
private class Turn(
    val message: JsonObject,
    val finish: String?,
    val replay: NativeReplay,
    val usage: Usage,
    val requestId: String?,
)

private fun parseRoot(body: String?): JsonObject {
    if (body.isNullOrBlank()) unusableAnswer()
    return Json.parseToJsonElement(body) as? JsonObject ?: unusableAnswer()
}

// Presence decides: tool calls make a tool turn whatever the finish reason says.
private fun decodeToolTurn(turn: Turn, toolRequired: Boolean): ChatDecoded {
    val calls = decodeChatToolCalls(turn.message)
    return when {
        calls.isNotEmpty() -> success(turn, StopReason.TOOL_USE, textParts(turn.message) + calls, calls.size)
        turn.finish == FINISH_STOP && toolRequired -> ChatDecoded(
            ModelResult.Failure(FailureReason.NoToolCall()),
            false,
            safeToken(turn.finish),
            0,
        )
        turn.finish == FINISH_STOP -> success(turn, StopReason.END_TURN, textParts(turn.message), 0)
        turn.finish == FINISH_TOOL_CALLS -> unusableAnswer()
        else -> success(turn, StopReason.OTHER, textParts(turn.message), 0)
    }
}

private fun success(turn: Turn, stopReason: StopReason, parts: List<AssistantPart>, toolCalls: Int): ChatDecoded =
    ChatDecoded(
        ModelResult.Success(
            ModelResponse(AssistantMessage(parts, turn.replay), stopReason, turn.usage, turn.requestId),
        ),
        false,
        safeToken(turn.finish),
        toolCalls,
    )

private fun textParts(message: JsonObject): List<AssistantPart> =
    chatStringField(message, "content")?.takeIf { it.isNotEmpty() }?.let { listOf(AssistantPart.Text(it)) }
        ?: emptyList()
