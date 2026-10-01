package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val FINISH_STOP = "stop"
private const val FINISH_TOOL_CALLS = "tool_calls"
private const val FINISH_LENGTH = "length"
private const val FINISH_CONTENT_FILTER = "content_filter"
private const val FINISH_ERROR = "error"
private const val STATUS_OK = 200

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
 * The first row that applies decides, in this order:
 * 1. an unusable body (blank, not JSON, not an object) is malformed;
 * 2. an `error` object on a 2xx answer is the failure it describes, transient when its status says so;
 * 3. missing or empty `choices`, or a first choice without a message object, is malformed;
 * 4. finish reason `error` is an HTTP error that carries the provider's native finish reason as its type;
 * 5. a refusal, or finish reason `content_filter`, is a success with [StopReason.REFUSAL] and no parts;
 * 6. finish reason `length` is a success with [StopReason.MAX_TOKENS]; truncated tool arguments are never decoded;
 * 7. tool calls make a tool turn whatever the finish reason says, because their presence decides;
 * 8. no tool call and finish reason `stop` is [FailureReason.NoToolCall] when [toolRequired], else an end of turn;
 * 9. finish reason `tool_calls` without a usable call is malformed;
 * 10. any other or missing finish reason is a success with [StopReason.OTHER].
 *
 * The turn is stored for replay with the tool calls the decoder took and no others, so what is replayed and what the
 * conversation check reads agree.
 *
 * The request id is the vendor's header value, else, only for a vendor that puts it in the body, the body's `id`.
 * The usage is normalized as in [decodeChatUsage].
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
    val envelope = chatEnvelopeError(root, requestIdHeader, vendor.requestIdInBody)
    if (envelope != null) {
        return ChatDecoded(ModelResult.Failure(envelope.reason(), envelope.details()), envelope.transient, null, 0)
    }
    val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: unusableAnswer()
    val message = choice["message"] as? JsonObject ?: unusableAnswer()
    val bodyId = if (vendor.requestIdInBody) safeRequestId(chatStringField(root, "id")) else null
    val turn = Turn(
        message = message,
        finish = chatStringField(choice, "finish_reason"),
        nativeFinish = chatStringField(choice, "native_finish_reason"),
        replay = NativeReplay(vendor.providerId, model, message),
        usage = decodeChatUsage(root["usage"]),
        requestId = safeRequestId(requestIdHeader) ?: bodyId,
    )
    return decodeOutcome(turn, toolRequired)
}

/** The parts of one answer's first choice that every outcome row reads. */
private class Turn(
    val message: JsonObject,
    val finish: String?,
    val nativeFinish: String?,
    val replay: NativeReplay,
    val usage: Usage,
    val requestId: String?,
)

private fun parseRoot(body: String?): JsonObject {
    if (body.isNullOrBlank()) unusableAnswer()
    return Json.parseToJsonElement(body) as? JsonObject ?: unusableAnswer()
}

private fun decodeOutcome(turn: Turn, toolRequired: Boolean): ChatDecoded = when {
    turn.finish == FINISH_ERROR -> providerError(turn)
    isRefusal(turn) -> success(turn, StopReason.REFUSAL, emptyList(), 0)
    turn.finish == FINISH_LENGTH -> success(turn, StopReason.MAX_TOKENS, textParts(turn.message), 0)
    else -> decodeToolTurn(turn, toolRequired)
}

// The refusal text itself is read nowhere: it stays inside the native replay only. An empty string is not a refusal.
private fun isRefusal(turn: Turn): Boolean {
    val refusal = turn.message["refusal"]
    val present = refusal != null && refusal !is JsonNull && !(refusal is JsonPrimitive && refusal.content.isEmpty())
    return present || turn.finish == FINISH_CONTENT_FILTER
}

// Status 200 because the HTTP exchange succeeded; the provider's own finish reason is the only hint kept.
private fun providerError(turn: Turn): ChatDecoded = ChatDecoded(
    ModelResult.Failure(
        FailureReason.HttpError(),
        FailureDetails(STATUS_OK, safeToken(turn.nativeFinish), turn.requestId),
    ),
    false,
    safeToken(turn.finish),
    0,
)

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
            ModelResponse(
                AssistantMessage(parts, replayMatching(turn.replay, parts)),
                stopReason,
                turn.usage,
                turn.requestId,
            ),
        ),
        false,
        safeToken(turn.finish),
        toolCalls,
    )

private fun textParts(message: JsonObject): List<AssistantPart> =
    chatStringField(message, "content")?.takeIf { it.isNotEmpty() }?.let { listOf(AssistantPart.Text(it)) }
        ?: emptyList()
