package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId

private const val DEFAULT_MAX_ITERATIONS = 6
private const val DEFAULT_TOKEN_CEILING = 60_000L
private const val DEFAULT_MAX_TOKENS_PER_TURN = 4_096
private const val DEFAULT_PICKER_TIMEOUT_MILLIS = 2_000L
private const val MIN_ITERATIONS = 2

/**
 * The limits one command runs under. Build one with `TierPolicy { maxIterations = 8 }`; unset fields keep their
 * defaults, and new limits can be added later without breaking callers.
 *
 * The engine itself enforces only [offlineOnly], [maxTier], the static part of [allowedProviders] and
 * [commandTimeoutMillis] and [pickerTimeoutMillis]. [maxIterations], [tokenCeiling] and [maxTokensPerTurn] are
 * advisory: the engine counts tokens (`CommandSession.tokensUsed`) but never stops a tier for exceeding them, so a
 * strategy that ignores `session.policy` is unbounded. Set [commandTimeoutMillis] as the engine-enforced backstop.
 *
 * @property offlineOnly when true, no tier that needs the network may run.
 * @property maxTier the last tier allowed to run, by its stable [StrategyId] (never a ladder index), or null for
 * no cap.
 * @property allowedProviders the providers a tier may use, checked against each tier's declared providers only: a tier
 * passes when it declares at least one allowed provider, so a tier that declares every provider passes any non-empty
 * filter. Null means every provider; an empty set
 * means no provider-backed tier may run.
 * @property maxIterations the most model turns a looping strategy may take (at least 2). Advisory: the strategy
 * enforces it.
 * @property tokenCeiling the most tokens one run should use across all turns. Advisory: the strategy compares it with
 * `CommandSession.tokensUsed` and stops itself.
 * @property maxTokensPerTurn the most output tokens one model turn may produce. Advisory: the strategy passes it to
 * its provider.
 * @property commandTimeoutMillis the engine-imposed deadline for a whole command, or null for none. An app that sets
 * one accepts that it also bounds a gate that is suspended waiting for a person.
 * @property pickerTimeoutMillis how long the engine waits for a start-tier picker before it starts the walk at the
 * first eligible tier and records `router_fallback`, in milliseconds and at least 1. The command deadline still
 * bounds the whole run, so the earlier of the two wins. A call cut off by the timeout is cancelled and produces no turn
 * record, though the provider may still bill it, so that spend is not in `tokensUsed` or the trace. Set it high enough
 * for a cold connection to your router's provider.
 */
public class TierPolicy internal constructor(
    public val offlineOnly: Boolean,
    public val maxTier: StrategyId?,
    public val allowedProviders: Set<ProviderId>?,
    public val maxIterations: Int,
    public val tokenCeiling: Long,
    public val maxTokensPerTurn: Int,
    public val commandTimeoutMillis: Long?,
    public val pickerTimeoutMillis: Long,
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
        require(pickerTimeoutMillis > 0) { "pickerTimeoutMillis must be positive but was $pickerTimeoutMillis" }
    }

    override fun toString(): String =
        "TierPolicy(offlineOnly=$offlineOnly, maxTier=$maxTier, allowedProviders=$allowedProviders, " +
            "maxIterations=$maxIterations, tokenCeiling=$tokenCeiling, maxTokensPerTurn=$maxTokensPerTurn, " +
            "commandTimeoutMillis=$commandTimeoutMillis, pickerTimeoutMillis=$pickerTimeoutMillis)"

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

        /** See [TierPolicy.pickerTimeoutMillis]. */
        public var pickerTimeoutMillis: Long = DEFAULT_PICKER_TIMEOUT_MILLIS

        internal fun build(): TierPolicy = TierPolicy(
            offlineOnly = offlineOnly,
            maxTier = maxTier,
            allowedProviders = allowedProviders?.toSet(),
            maxIterations = maxIterations,
            tokenCeiling = tokenCeiling,
            maxTokensPerTurn = maxTokensPerTurn,
            commandTimeoutMillis = commandTimeoutMillis,
            pickerTimeoutMillis = pickerTimeoutMillis,
        )
    }

    /** Entry points for creating policies. */
    public companion object {
        /**
         * The default policy: 6 iterations, 60,000 tokens, 4,096 tokens per turn, no engine deadline, and a 2,000 ms
         * picker timeout.
         */
        public val DEFAULT: TierPolicy = Builder().build()

        /**
         * Builds a policy from [block]; throws [IllegalArgumentException] when a limit is out of range, for example
         * `maxIterations` below 2.
         */
        public operator fun invoke(block: Builder.() -> Unit): TierPolicy = Builder().apply(block).build()
    }
}
