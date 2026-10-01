package io.github.ygaray.voiceactionengine.providers.chat

import java.util.Objects

/**
 * Hears about every HTTP request the Chat Completions provider sends for a call, so an app can count retries, see why
 * each one happened and notice when a vendor's finish reason disagrees with its answer. Set it with
 * [ChatCompletionsProvider.Builder.attemptObserver].
 *
 * The observer receives facts only: the attempt's number, why it was made, the HTTP status, the finish reason and how
 * many tool calls the answer carried. It never receives text, headers, a body, a key or an exception message.
 * Observing is diagnostics only: if the observer throws, the exception is dropped unread and the call goes on as if it
 * had returned.
 */
public fun interface ChatCompletionsAttemptObserver {
    /**
     * Called once per HTTP attempt, on the provider's I/O dispatcher, after the status is known (or after the attempt
     * failed without an answer). Return quickly. An exception it throws is ignored and never changes the call's result.
     */
    public fun onAttempt(attempt: ChatCompletionsAttempt)
}

/**
 * One HTTP request the provider sent for a call.
 *
 * A tool call reported together with a finish reason other than `tool_calls` (for example `stop`) is how the provider
 * reports that the vendor's finish reason disagreed with the answer. The provider trusts the tool call and returns a
 * tool turn either way.
 *
 * @property number which request this was within the call, starting at 1.
 * @property kind why the provider sent it.
 * @property httpStatus the HTTP status of the answer, or null when the attempt ended without one (a timeout or a lost
 * connection).
 * @property finishReason the finish reason the vendor reported, or null when the attempt had no usable answer.
 * @property toolCalls how many tool calls the answer carried; 0 when the attempt had no usable answer.
 */
public class ChatCompletionsAttempt internal constructor(
    public val number: Int,
    public val kind: ChatCompletionsAttemptKind,
    public val httpStatus: Int?,
    public val finishReason: String?,
    public val toolCalls: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is ChatCompletionsAttempt &&
            number == other.number &&
            kind == other.kind &&
            httpStatus == other.httpStatus &&
            finishReason == other.finishReason &&
            toolCalls == other.toolCalls

    override fun hashCode(): Int = Objects.hash(number, kind.value, httpStatus, finishReason, toolCalls)

    override fun toString(): String =
        "ChatCompletionsAttempt(number=$number, kind=$kind, httpStatus=$httpStatus, " +
            "finishReason=$finishReason, toolCalls=$toolCalls)"
}

/**
 * Why the provider sent a request. The set is open (later versions may add reasons), so keep an `else` branch when
 * switching on one. Only the provider creates values; apps compare against the constants.
 */
@JvmInline
public value class ChatCompletionsAttemptKind internal constructor(public val value: String) {
    /** The kind's stable string value. */
    override fun toString(): String = value

    /** The kinds the provider currently sends. */
    public companion object {
        /** The first request of a call. */
        public val INITIAL: ChatCompletionsAttemptKind = ChatCompletionsAttemptKind("initial")

        /** The single repeat of a request after a failure that can clear on its own. */
        public val TRANSIENT_RETRY: ChatCompletionsAttemptKind = ChatCompletionsAttemptKind("transient_retry")
    }
}
