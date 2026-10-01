package io.github.ygaray.voiceactionengine.core.provider

import java.util.Objects

private const val DEFAULT_CHARS_PER_TOKEN = 4.0

/**
 * What one model can do, as far as the engine needs to know before it sends a request. Build one with
 * `ModelCapabilities { supportsTools = false }`; unset fields keep their defaults, and new facts can be added later
 * without breaking callers. [UNKNOWN] is the answer for a model nobody has described.
 *
 * @property supportsTools false means a request that carries tools is refused before any call is made.
 * @property caching how the model's provider caches prompt prefixes.
 * @property minCacheablePrefixTokens the shortest prefix, in tokens, the provider will cache, or null when that is
 * unknown. Null keeps the cache diagnostic silent, because without a minimum the engine cannot tell a missed cache
 * from a prefix that was simply too short.
 * @property charsPerToken the divisor the engine uses to estimate a prefix's token count from its character count.
 * It is an estimate, never a limit: a larger value under-counts tokens, which errs toward silence in the cache
 * diagnostic. The default of 4.0 deliberately under-estimates tool-schema JSON, which measures nearer 3 characters per
 * token, so the diagnostic does not fire on a prefix that may be too short to cache. The diagnostic also caps its
 * estimate by the prompt total the response itself reports.
 */
public class ModelCapabilities internal constructor(
    public val supportsTools: Boolean,
    public val caching: CachingMode,
    public val minCacheablePrefixTokens: Int?,
    public val charsPerToken: Double,
) {
    init {
        require(minCacheablePrefixTokens == null || minCacheablePrefixTokens > 0) {
            "minCacheablePrefixTokens must be null or positive but was $minCacheablePrefixTokens"
        }
        require(charsPerToken.isFinite() && charsPerToken > 0.0) {
            "charsPerToken must be finite and positive but was $charsPerToken"
        }
    }

    internal fun toBuilder(): Builder = Builder().also {
        it.supportsTools = supportsTools
        it.caching = caching
        it.minCacheablePrefixTokens = minCacheablePrefixTokens
        it.charsPerToken = charsPerToken
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelCapabilities) return false
        return supportsTools == other.supportsTools &&
            caching == other.caching &&
            minCacheablePrefixTokens == other.minCacheablePrefixTokens &&
            charsPerToken == other.charsPerToken
    }

    override fun hashCode(): Int = Objects.hash(supportsTools, caching, minCacheablePrefixTokens, charsPerToken)

    override fun toString(): String =
        "ModelCapabilities(supportsTools=$supportsTools, caching=$caching, " +
            "minCacheablePrefixTokens=$minCacheablePrefixTokens, charsPerToken=$charsPerToken)"

    /** Mutable collector for a [ModelCapabilities]; every field starts at its default. */
    public class Builder internal constructor() {
        /** See [ModelCapabilities.supportsTools]. */
        public var supportsTools: Boolean = true

        /** See [ModelCapabilities.caching]. */
        public var caching: CachingMode = CachingMode.NONE

        /** See [ModelCapabilities.minCacheablePrefixTokens]. */
        public var minCacheablePrefixTokens: Int? = null

        /** See [ModelCapabilities.charsPerToken]. */
        public var charsPerToken: Double = DEFAULT_CHARS_PER_TOKEN

        internal fun build(): ModelCapabilities = ModelCapabilities(
            supportsTools = supportsTools,
            caching = caching,
            minCacheablePrefixTokens = minCacheablePrefixTokens,
            charsPerToken = charsPerToken,
        )
    }

    /** Entry points for creating capabilities. */
    public companion object {
        /** A model nobody has described: tools allowed, no caching, unknown minimum prefix, 4.0 chars per token. */
        public val UNKNOWN: ModelCapabilities = Builder().build()

        /**
         * Builds capabilities from [block]; throws [IllegalArgumentException] when a field is out of range, for
         * example a non-positive `charsPerToken`.
         */
        public operator fun invoke(block: Builder.() -> Unit): ModelCapabilities = Builder().apply(block).build()
    }
}
