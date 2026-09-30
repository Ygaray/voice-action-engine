package io.github.ygaray.voiceactionengine.core

/**
 * Stable identity of a model provider. A value class rather than an enum so apps and later engine versions can add
 * providers without breaking anyone who branches on the known ones.
 */
@JvmInline
public value class ProviderId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ProviderId value must not be blank" }
    }

    /** The provider's wire value. */
    override fun toString(): String = value

    /** The four providers the engine knows about. */
    public companion object {
        /** Anthropic's hosted API. */
        public val ANTHROPIC: ProviderId = ProviderId("anthropic")

        /** OpenAI's hosted API. */
        public val OPENAI: ProviderId = ProviderId("openai")

        /** OpenRouter's hosted API. */
        public val OPENROUTER: ProviderId = ProviderId("openrouter")

        /** A model running on the device itself. */
        public val ON_DEVICE: ProviderId = ProviderId("on_device")
    }
}
