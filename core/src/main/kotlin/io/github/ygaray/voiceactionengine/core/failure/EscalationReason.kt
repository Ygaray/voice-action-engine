package io.github.ygaray.voiceactionengine.core.failure

import io.github.ygaray.voiceactionengine.core.ProviderId

/**
 * Why a strategy handed the command up to the next tier instead of finishing or failing. The set is open by design,
 * so a consumer must keep an `else` branch. [code] is a stable snake_case identifier, never a message.
 */
public interface EscalationReason {
    /** Stable identifier for this kind of escalation. */
    public val code: String

    /** The model answered without calling a tool. */
    public class NoToolCall : EscalationReason {
        override val code: String get() = "no_tool_call"
        override fun equals(other: Any?): Boolean = other is NoToolCall
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("EscalationReason", "NoToolCall", code)
    }

    /** The model declined to handle the command. */
    public class ModelDeclined : EscalationReason {
        override val code: String get() = "model_declined"
        override fun equals(other: Any?): Boolean = other is ModelDeclined
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("EscalationReason", "ModelDeclined", code)
    }

    /** The model's extraction could not be parsed into what the tier needs. */
    public class MalformedExtraction : EscalationReason {
        override val code: String get() = "malformed_extraction"
        override fun equals(other: Any?): Boolean = other is MalformedExtraction
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("EscalationReason", "MalformedExtraction", code)
    }

    /** The app's resolver could not pick a single target. */
    public class ResolverAmbiguous : EscalationReason {
        override val code: String get() = "resolver_ambiguous"
        override fun equals(other: Any?): Boolean = other is ResolverAmbiguous
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("EscalationReason", "ResolverAmbiguous", code)
    }

    /** The tier's [provider] could not be used, so the ladder moves on. */
    public class ProviderUnavailable(public val provider: ProviderId) : EscalationReason {
        override val code: String get() = "provider_unavailable"
        override fun equals(other: Any?): Boolean = other is ProviderUnavailable && provider == other.provider
        override fun hashCode(): Int = mixHash(code.hashCode(), provider.hashCode())
        override fun toString(): String =
            describe("EscalationReason", "ProviderUnavailable", code, "provider" to provider)
    }

    /** A reason defined outside the engine, identified by its own stable [code]. */
    public class Other(override val code: String) : EscalationReason {
        init {
            require(code.isNotBlank()) { "Other code must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is Other && code == other.code
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("EscalationReason", "Other", code)
    }
}
