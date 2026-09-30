package io.github.ygaray.voiceactionengine.core.failure

import io.github.ygaray.voiceactionengine.core.ProviderId

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

    /** The provider rejected the credentials. */
    public class Auth : FailureReason {
        override val code: String get() = "auth"
        override fun equals(other: Any?): Boolean = other is Auth
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Auth", code)
    }

    /** The provider account has a billing or quota problem. */
    public class Billing : FailureReason {
        override val code: String get() = "billing"
        override fun equals(other: Any?): Boolean = other is Billing
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Billing", code)
    }

    /** The provider rejected the request as rate limited. */
    public class RateLimited : FailureReason {
        override val code: String get() = "rate_limited"
        override fun equals(other: Any?): Boolean = other is RateLimited
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("RateLimited", code)
    }

    /** The provider is temporarily overloaded. */
    public class Overloaded : FailureReason {
        override val code: String get() = "overloaded"
        override fun equals(other: Any?): Boolean = other is Overloaded
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Overloaded", code)
    }

    /** A time limit expired before the provider or a tool answered. */
    public class Timeout : FailureReason {
        override val code: String get() = "timeout"
        override fun equals(other: Any?): Boolean = other is Timeout
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Timeout", code)
    }

    /** The network failed before a usable response arrived. */
    public class Network : FailureReason {
        override val code: String get() = "network"
        override fun equals(other: Any?): Boolean = other is Network
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Network", code)
    }

    /** The provider's response could not be understood. */
    public class MalformedResponse : FailureReason {
        override val code: String get() = "malformed_response"
        override fun equals(other: Any?): Boolean = other is MalformedResponse
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("MalformedResponse", code)
    }

    /** The model produced tool arguments that do not match the tool's schema. */
    public class MalformedToolArgs : FailureReason {
        override val code: String get() = "malformed_tool_args"
        override fun equals(other: Any?): Boolean = other is MalformedToolArgs
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("MalformedToolArgs", code)
    }

    /** The model refused to answer. */
    public class Refusal : FailureReason {
        override val code: String get() = "refusal"
        override fun equals(other: Any?): Boolean = other is Refusal
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("Refusal", code)
    }

    /** The model stopped because it hit its output token limit. */
    public class MaxTokens : FailureReason {
        override val code: String get() = "max_tokens"
        override fun equals(other: Any?): Boolean = other is MaxTokens
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("MaxTokens", code)
    }

    /** The model answered without calling any tool. */
    public class NoToolCall : FailureReason {
        override val code: String get() = "no_tool_call"
        override fun equals(other: Any?): Boolean = other is NoToolCall
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("NoToolCall", code)
    }

    /** A tool reported a failure that ended the run. */
    public class ToolFailure : FailureReason {
        override val code: String get() = "tool_failure"
        override fun equals(other: Any?): Boolean = other is ToolFailure
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("ToolFailure", code)
    }

    /** The chosen model does not support what was asked of it. */
    public class ModelUnsupported : FailureReason {
        override val code: String get() = "model_unsupported"
        override fun equals(other: Any?): Boolean = other is ModelUnsupported
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("ModelUnsupported", code)
    }

    /** The provider does not know the requested model. */
    public class ModelNotFound : FailureReason {
        override val code: String get() = "model_not_found"
        override fun equals(other: Any?): Boolean = other is ModelNotFound
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("ModelNotFound", code)
    }

    /** The provider paused the turn and the run could not continue it. */
    public class PauseTurn : FailureReason {
        override val code: String get() = "pause_turn"
        override fun equals(other: Any?): Boolean = other is PauseTurn
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("PauseTurn", code)
    }

    /** The conversation no longer fits the model's context window. */
    public class ContextWindowExceeded : FailureReason {
        override val code: String get() = "context_window_exceeded"
        override fun equals(other: Any?): Boolean = other is ContextWindowExceeded
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("ContextWindowExceeded", code)
    }

    /** The model stopped for a reason the engine does not recognise. */
    public class UnknownStop : FailureReason {
        override val code: String get() = "unknown_stop"
        override fun equals(other: Any?): Boolean = other is UnknownStop
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("UnknownStop", code)
    }

    /** The provider answered with an HTTP error that no other reason covers. */
    public class HttpError : FailureReason {
        override val code: String get() = "http_error"
        override fun equals(other: Any?): Boolean = other is HttpError
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("HttpError", code)
    }

    /** No tier in the ladder was allowed to run under the current policy. */
    public class NoEligibleTier : FailureReason {
        override val code: String get() = "no_eligible_tier"
        override fun equals(other: Any?): Boolean = other is NoEligibleTier
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("NoEligibleTier", code)
    }

    /** The tier policy could not be read, so nothing was run. */
    public class PolicyUnavailable : FailureReason {
        override val code: String get() = "policy_unavailable"
        override fun equals(other: Any?): Boolean = other is PolicyUnavailable
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = describe("PolicyUnavailable", code)
    }

    /** The run exceeded a budget; [bound] says which one. */
    public class BudgetExceeded(public val bound: BudgetBound) : FailureReason {
        override val code: String get() = "budget_exceeded"
        override fun equals(other: Any?): Boolean = other is BudgetExceeded && bound == other.bound
        override fun hashCode(): Int = code.hashCode() * HASH_PRIME + bound.hashCode()
        override fun toString(): String = describe("BudgetExceeded", code, "bound" to bound)
    }

    /** A provider is needed but has no credential or configuration; [provider] is null when not provider-specific. */
    public class NotConfigured(public val provider: ProviderId?) : FailureReason {
        override val code: String get() = "not_configured"
        override fun equals(other: Any?): Boolean = other is NotConfigured && provider == other.provider
        override fun hashCode(): Int = code.hashCode() * HASH_PRIME + provider.hashCode()
        override fun toString(): String = describe("NotConfigured", code, "provider" to provider)
    }

    /** A provider cannot be used right now; [cause] is a stable code (never a message) or null. */
    public class ProviderUnavailable(public val provider: ProviderId, public val cause: String?) : FailureReason {
        override val code: String get() = "provider_unavailable"
        override fun equals(other: Any?): Boolean =
            other is ProviderUnavailable && provider == other.provider && cause == other.cause
        override fun hashCode(): Int =
            (code.hashCode() * HASH_PRIME + provider.hashCode()) * HASH_PRIME + cause.hashCode()
        override fun toString(): String =
            describe("ProviderUnavailable", code, "provider" to provider, "cause" to cause)
    }

    /** An unexpected exception ended the run; only its class name [errorClass] is kept, never its message. */
    public class Unexpected(public val errorClass: String) : FailureReason {
        override val code: String get() = "unexpected"
        override fun equals(other: Any?): Boolean = other is Unexpected && errorClass == other.errorClass
        override fun hashCode(): Int = code.hashCode() * HASH_PRIME + errorClass.hashCode()
        override fun toString(): String = describe("Unexpected", code, "errorClass" to errorClass)
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

private const val HASH_PRIME = 31

/** Shared redaction-safe rendering: leaf name, code, and any extra stable fields. */
private fun describe(leaf: String, code: String, vararg extras: Pair<String, Any?>): String {
    val tail = extras.joinToString("") { ", ${it.first}=${it.second}" }
    return "FailureReason.$leaf(code=$code$tail)"
}
