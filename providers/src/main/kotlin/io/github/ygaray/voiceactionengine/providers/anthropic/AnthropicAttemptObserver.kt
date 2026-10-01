package io.github.ygaray.voiceactionengine.providers.anthropic

import java.util.Objects

/**
 * Hears about every HTTP request the Anthropic provider sends for a call, so an app can count retries and see why each
 * one happened. Set it with [AnthropicProvider.Builder.attemptObserver].
 *
 * The observer receives facts only: the attempt's number, why it was made and the HTTP status. It never receives text,
 * headers, a body, a key or an exception message. Observing is diagnostics only: if the observer throws, the exception
 * is dropped unread and the call goes on as if it had returned.
 */
public fun interface AnthropicAttemptObserver {
    /**
     * Called once per HTTP attempt, on the provider's I/O dispatcher, after the status is known (or after the attempt
     * failed without an answer). Return quickly. An exception it throws is ignored and never changes the call's result.
     */
    public fun onAttempt(attempt: AnthropicAttempt)
}

/**
 * One HTTP request the provider sent for a call.
 *
 * @property number which request this was within the call, starting at 1.
 * @property kind why the provider sent it.
 * @property httpStatus the HTTP status of the answer, or null when the attempt ended without one (a timeout or a lost
 * connection).
 */
public class AnthropicAttempt internal constructor(
    public val number: Int,
    public val kind: AnthropicAttemptKind,
    public val httpStatus: Int?,
) {
    override fun equals(other: Any?): Boolean =
        other is AnthropicAttempt && number == other.number && kind == other.kind && httpStatus == other.httpStatus

    override fun hashCode(): Int = Objects.hash(number, kind.value, httpStatus)

    override fun toString(): String = "AnthropicAttempt(number=$number, kind=$kind, httpStatus=$httpStatus)"
}

/**
 * Why the provider sent a request. The set is open (later versions may add reasons), so keep an `else` branch when
 * switching on one. Only the provider creates values; apps compare against the constants.
 */
@JvmInline
public value class AnthropicAttemptKind internal constructor(public val value: String) {
    /** The kind's stable string value. */
    override fun toString(): String = value

    /** The kinds the provider currently sends. */
    public companion object {
        /** The first request of a call. */
        public val INITIAL: AnthropicAttemptKind = AnthropicAttemptKind("initial")

        /** The single repeat of a request after a failure that can clear on its own. */
        public val TRANSIENT_RETRY: AnthropicAttemptKind = AnthropicAttemptKind("transient_retry")

        /** A request re-sent with a changed tool choice after the model refused the first one. */
        public val FORCED_TOOL_RESHAPE: AnthropicAttemptKind = AnthropicAttemptKind("forced_tool_reshape")
    }
}
