package io.github.ygaray.voiceactionengine.core.provider

/**
 * How a model's provider caches prompt prefixes. The set is open (later versions may add modes), so keep an `else`
 * branch when switching on one. Only the engine and providers create values; apps compare against the constants.
 */
@JvmInline
public value class CachingMode internal constructor(public val value: String) {
    /** The mode's stable string value. */
    override fun toString(): String = value

    /** The modes the engine currently knows about. */
    public companion object {
        /** The request marks where the cacheable prefix ends, and the provider caches up to the mark. */
        public val EXPLICIT_BREAKPOINTS: CachingMode = CachingMode("explicit_breakpoints")

        /** The provider caches a long enough shared prefix on its own, with nothing marked in the request. */
        public val AUTOMATIC: CachingMode = CachingMode("automatic")

        /** The model does not cache prompt prefixes. */
        public val NONE: CachingMode = CachingMode("none")
    }
}
