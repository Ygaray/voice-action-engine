package io.github.ygaray.voiceactionengine.providers.transcript

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import kotlinx.serialization.json.JsonElement

private const val REPLAY_MISMATCH = "replay_mismatch"

/**
 * The first reason, in message order, why the conversation of [call] cannot be sent honestly to [provider], or null
 * when it can. It is pure: no I/O and no logging.
 *
 * The check covers which turns may be replayed to this provider and model: a turn that carries a native replay must be
 * stamped for [provider] and for the model [call] asks for, and its raw turn must have the shape [replayShapeOk]
 * accepts. A turn with no replay is always legal. It runs once, before any request, so a retry or a reshaped request
 * never repeats it.
 *
 * A reason carries the kind of violation only, because call ids, tool names and text all come from the model.
 */
internal fun conversationViolation(
    call: ProviderRequest,
    provider: ProviderId,
    replayShapeOk: (JsonElement) -> Boolean,
): String? {
    for (message in call.request.messages) {
        if (message is AssistantMessage && replayMismatch(message, call.model, provider, replayShapeOk)) {
            return REPLAY_MISMATCH
        }
    }
    return null
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

/** The typed failure for [conversationViolation], or null when the conversation may be sent. */
internal fun conversationRefusal(
    call: ProviderRequest,
    provider: ProviderId,
    replayShapeOk: (JsonElement) -> Boolean,
): ModelResult.Failure? =
    conversationViolation(call, provider, replayShapeOk)?.let { ModelResult.Failure(FailureReason.Other(it)) }
