package io.github.ygaray.voiceactionengine.providers.transcript

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.JsonElement

private const val REPLAY_MISMATCH = "replay_mismatch"
private const val TOOL_CALL_UNANSWERED = "tool_call_unanswered"
private const val TOOL_CALL_ID_DUPLICATE = "tool_call_id_duplicate"
private const val TOOL_RESULT_MISSING = "tool_result_missing"
private const val TOOL_RESULT_UNEXPECTED = "tool_result_unexpected"

/**
 * The first reason, in message order, why the conversation of [call] cannot be sent honestly to [provider], or null
 * when it can. It is pure: no I/O and no logging.
 *
 * The check covers which turns may be replayed to this provider and model, and whether every tool call is answered.
 * The rules, for each assistant turn in message order and then for each batch of results:
 *
 * - `replay_mismatch`: the turn carries a native replay that is not stamped for [provider] and the model [call] asks
 *   for, or whose raw turn is not a shape [replayShapeOk] accepts. A turn with no replay is always legal.
 * - `tool_call_id_duplicate`: two calls in the turn share an id.
 * - `tool_call_unanswered`: the turn has calls and is last, or is followed by anything but a batch of results.
 * - `tool_result_missing`: a call in the turn has no result in the batch that follows it.
 * - `tool_result_unexpected`: that batch holds a result for no call of the turn, or a batch of results does not
 *   directly follow a turn with calls.
 *
 * Inside one turn the rules apply in the order listed. The check runs once, before any request, so a retry or a
 * reshaped request never repeats it. A reason carries the kind of violation only, because call ids, tool names and
 * text all come from the model.
 */
internal fun conversationViolation(
    call: ProviderRequest,
    provider: ProviderId,
    replayShapeOk: (JsonElement) -> Boolean,
): String? {
    val messages = call.request.messages
    for ((index, message) in messages.withIndex()) {
        val violation = when (message) {
            is AssistantMessage ->
                assistantViolation(message, messages.getOrNull(index + 1), call.model, provider, replayShapeOk)
            is ToolResultsMessage -> if (answersCalls(messages.getOrNull(index - 1))) null else TOOL_RESULT_UNEXPECTED
            is UserMessage -> null
        }
        if (violation != null) return violation
    }
    return null
}

private fun assistantViolation(
    message: AssistantMessage,
    next: Message?,
    model: String,
    provider: ProviderId,
    replayShapeOk: (JsonElement) -> Boolean,
): String? = when {
    replayMismatch(message, model, provider, replayShapeOk) -> REPLAY_MISMATCH
    message.toolCalls.isEmpty() -> null
    else -> coverageViolation(message.toolCalls.map { it.id }, next)
}

private fun replayMismatch(
    message: AssistantMessage,
    model: String,
    provider: ProviderId,
    replayShapeOk: (JsonElement) -> Boolean,
): Boolean {
    val stamp = message.nativeReplay ?: return false
    return !(stamp.provider == provider && stamp.model == model && replayShapeOk(stamp.raw))
}

private fun coverageViolation(callIds: List<String>, next: Message?): String? {
    val resultIds = (next as? ToolResultsMessage)?.results?.map { it.callId }
    return when {
        callIds.toSet().size != callIds.size -> TOOL_CALL_ID_DUPLICATE
        resultIds == null -> TOOL_CALL_UNANSWERED
        !resultIds.containsAll(callIds) -> TOOL_RESULT_MISSING
        !callIds.containsAll(resultIds) -> TOOL_RESULT_UNEXPECTED
        else -> null
    }
}

private fun answersCalls(previous: Message?): Boolean = previous is AssistantMessage && previous.toolCalls.isNotEmpty()

/** The typed failure for [conversationViolation], or null when the conversation may be sent. */
internal fun conversationRefusal(
    call: ProviderRequest,
    provider: ProviderId,
    replayShapeOk: (JsonElement) -> Boolean,
): ModelResult.Failure? =
    conversationViolation(call, provider, replayShapeOk)?.let { ModelResult.Failure(FailureReason.Other(it)) }

/**
 * [results] sorted by the position of their call id among [calls]; a result for no call keeps its relative order after
 * the matched ones.
 */
internal fun resultsInCallOrder(results: List<ToolResult>, calls: List<AssistantPart.ToolCall>): List<ToolResult> {
    val position = calls.withIndex().associate { (index, call) -> call.id to index }
    return results.sortedBy { position[it.callId] ?: Int.MAX_VALUE }
}
