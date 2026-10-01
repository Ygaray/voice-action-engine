package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId

private const val DEFAULT_MAX_ITERATIONS = 6
private const val DEFAULT_TOKEN_CEILING = 60_000L
private const val DEFAULT_MAX_TOKENS_PER_TURN = 4_096
private const val MIN_ITERATIONS = 2

/**
 * The limits one command runs under. Build one with `TierPolicy { maxIterations = 8 }`; unset fields keep their
 * defaults, and new limits can be added later without breaking callers.
 *
 * @property offlineOnly when true, no tier that needs the network may run.
 * @property maxTier the last tier allowed to run, by its stable [StrategyId] (never a ladder index), or null for
 * no cap.
 * @property allowedProviders the providers a tier may use. Null means every provider; an empty set means no
 * provider-backed tier may run.
 * @property maxIterations the most model turns a looping strategy may take (at least 2).
 * @property tokenCeiling the most tokens one run may use across all turns.
 * @property maxTokensPerTurn the most output tokens one model turn may produce.
 * @property commandTimeoutMillis the engine-imposed deadline for a whole command, or null for none. An app that sets
 * one accepts that it also bounds a gate that is suspended waiting for a person.
 */
public class TierPolicy internal constructor(
    public val offlineOnly: Boolean,
    public val maxTier: StrategyId?,
    public val allowedProviders: Set<ProviderId>?,
    public val maxIterations: Int,
    public val tokenCeiling: Long,
    public val maxTokensPerTurn: Int,
    public val commandTimeoutMillis: Long?,
) {
    init {
        require(maxIterations >= MIN_ITERATIONS) {
            "maxIterations must be at least $MIN_ITERATIONS but was $maxIterations"
        }
        require(tokenCeiling > 0) { "tokenCeiling must be positive but was $tokenCeiling" }
        require(maxTokensPerTurn > 0) { "maxTokensPerTurn must be positive but was $maxTokensPerTurn" }
        require(commandTimeoutMillis == null || commandTimeoutMillis > 0) {
            "commandTimeoutMillis must be null or positive but was $commandTimeoutMillis"
        }
    }

    override fun toString(): String =
        "TierPolicy(offlineOnly=$offlineOnly, maxTier=$maxTier, allowedProviders=$allowedProviders, " +
            "maxIterations=$maxIterations, tokenCeiling=$tokenCeiling, maxTokensPerTurn=$maxTokensPerTurn, " +
            "commandTimeoutMillis=$commandTimeoutMillis)"

    /** Mutable collector for a [TierPolicy]; every field starts at its default. */
    public class Builder internal constructor() {
        /** See [TierPolicy.offlineOnly]. */
        public var offlineOnly: Boolean = false

        /** See [TierPolicy.maxTier]. */
        public var maxTier: StrategyId? = null

        /** See [TierPolicy.allowedProviders]. */
        public var allowedProviders: Set<ProviderId>? = null

        /** See [TierPolicy.maxIterations]. */
        public var maxIterations: Int = DEFAULT_MAX_ITERATIONS

        /** See [TierPolicy.tokenCeiling]. */
        public var tokenCeiling: Long = DEFAULT_TOKEN_CEILING

        /** See [TierPolicy.maxTokensPerTurn]. */
        public var maxTokensPerTurn: Int = DEFAULT_MAX_TOKENS_PER_TURN

        /** See [TierPolicy.commandTimeoutMillis]. */
        public var commandTimeoutMillis: Long? = null

        internal fun build(): TierPolicy = TierPolicy(
            offlineOnly = offlineOnly,
            maxTier = maxTier,
            allowedProviders = allowedProviders?.toSet(),
            maxIterations = maxIterations,
            tokenCeiling = tokenCeiling,
            maxTokensPerTurn = maxTokensPerTurn,
            commandTimeoutMillis = commandTimeoutMillis,
        )
    }

    /** Entry points for creating policies. */
    public companion object {
        /** The default policy: 6 iterations, 60,000 tokens, 4,096 tokens per turn, no engine deadline. */
        public val DEFAULT: TierPolicy = Builder().build()

        /**
         * Builds a policy from [block]; throws [IllegalArgumentException] when a limit is out of range, for example
         * `maxIterations` below 2.
         */
        public operator fun invoke(block: Builder.() -> Unit): TierPolicy = Builder().apply(block).build()
    }
}
