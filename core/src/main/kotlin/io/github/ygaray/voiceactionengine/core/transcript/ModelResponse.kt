package io.github.ygaray.voiceactionengine.core.transcript

import io.github.ygaray.voiceactionengine.core.failure.isRequestId
import io.github.ygaray.voiceactionengine.core.failure.isStableCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage

/**
 * What a model answered, in neutral terms.
 *
 * @property message the assistant turn.
 * @property stopReason why the model stopped.
 * @property usage the tokens the call used, in the same four buckets as the trace.
 * @property requestId the provider's id for the request, or null when it gave none.
 * @throws IllegalArgumentException when [requestId] is not a short identifier (letters, digits and `_ . : -`, 1 to
 * 128 characters); it comes from the server and is printed by `toString`, so a transport drops a value that does not
 * fit.
 */
public class ModelResponse(
    public val message: AssistantMessage,
    public val stopReason: StopReason,
    public val usage: Usage,
    public val requestId: String?,
) {
    init {
        require(requestId == null || isRequestId(requestId)) { "requestId must be a short identifier" }
    }

    /** A response with no request id. */
    public constructor(message: AssistantMessage, stopReason: StopReason, usage: Usage) :
        this(message, stopReason, usage, null)

    /** Prints counts, tool names, the stop reason, usage and the request id only, never text or arguments. */
    override fun toString(): String =
        "ModelResponse(parts=${message.parts.size}, toolCalls=${message.toolCalls.map { it.name }}, " +
            "stopReason=$stopReason, usage=$usage, requestId=$requestId)"
}

/**
 * Why a model stopped. The set is open (providers add reasons over time), so keep an `else` branch when switching on
 * one. A provider maps a wire value it does not know to [OTHER] and keeps the raw value only inside [NativeReplay].
 *
 * @property value the stable lower snake case code.
 * @throws IllegalArgumentException when [value] is not a lower snake case code.
 */
@JvmInline
public value class StopReason(public val value: String) {
    init {
        require(isStableCode(value)) { "a stop reason must be a lower snake case code" }
    }

    /** The reason's stable code. */
    override fun toString(): String = value

    /** The reasons the engine currently maps to. */
    public companion object {
        /** The model finished its turn. */
        public val END_TURN: StopReason = StopReason("end_turn")

        /** The model stopped to call one or more tools. */
        public val TOOL_USE: StopReason = StopReason("tool_use")

        /** The model hit the token limit of the request. */
        public val MAX_TOKENS: StopReason = StopReason("max_tokens")

        /** The model declined to answer. */
        public val REFUSAL: StopReason = StopReason("refusal")

        /** The provider paused a long turn and expects the conversation to continue. */
        public val PAUSE_TURN: StopReason = StopReason("pause_turn")

        /** The conversation no longer fits the model's context window. */
        public val CONTEXT_WINDOW_EXCEEDED: StopReason = StopReason("context_window_exceeded")

        /** Any reason the engine does not know. */
        public val OTHER: StopReason = StopReason("other")
    }
}
