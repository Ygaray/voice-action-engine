package io.github.ygaray.voiceactionengine.core.failure

/**
 * Why a command failed. The set is open by design: later engine versions and apps can add leaves, so a consumer must
 * always keep an `else` branch when it switches on a reason.
 *
 * [code] is a stable snake_case identifier safe to log and persist. Codes and nested values are never free-form
 * messages, so a reason cannot carry a transcript, a key or a response body.
 */
public interface FailureReason {
    /** Stable identifier for this kind of failure. */
    public val code: String

    /** The provider rejected the request as rate limited. */
    public class RateLimited : FailureReason {
        override val code: String get() = "rate_limited"
        override fun equals(other: Any?): Boolean = other is RateLimited
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("RateLimited", code)
    }

    /** A reason defined outside the engine, identified by its own stable [code]. */
    public class Other(override val code: String) : FailureReason {
        init {
            require(code.isNotBlank()) { "Other code must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is Other && code == other.code
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Other", code)
    }
}

/** Shared redaction-safe rendering: leaf name, code, and any extra stable fields. */
private fun describe(leaf: String, code: String, vararg extras: Pair<String, Any?>): String {
    val tail = extras.joinToString("") { ", ${it.first}=${it.second}" }
    return "FailureReason.$leaf(code=$code$tail)"
}
