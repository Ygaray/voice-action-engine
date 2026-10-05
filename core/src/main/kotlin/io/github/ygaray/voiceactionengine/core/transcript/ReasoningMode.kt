package io.github.ygaray.voiceactionengine.core.transcript

/**
 * How much reasoning the engine asks the model for.
 *
 * [OFF] (the default everywhere) means the engine adds no reasoning request of its own, so the provider receives
 * exactly the request it received in v1.0. For some models that already includes the setting the endpoint needs to
 * accept tools. It does not mean the model does not think. [PROVIDER_DEFAULT] asks for the provider's own default and,
 * in this version, is sent exactly like [OFF].
 *
 * The set is open: later versions add modes, so keep an `else` branch when switching on one.
 */
@JvmInline
public value class ReasoningMode internal constructor(public val value: String) {
    /** The mode's stable lowercase token. */
    override fun toString(): String = value

    /** The modes the engine knows about. */
    public companion object {
        /** The engine adds no reasoning request of its own. */
        public val OFF: ReasoningMode = ReasoningMode("off")

        /** The engine asks for the provider's own default reasoning. */
        public val PROVIDER_DEFAULT: ReasoningMode = ReasoningMode("provider_default")
    }
}
