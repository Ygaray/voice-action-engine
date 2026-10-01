package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.StrategyId

/**
 * Decides which tier of the ladder a command starts at. Use [Linear] to start at the first tier that may run, or
 * [Fixed] to start at one named tier and climb from there.
 *
 * This class is deliberately not sealed: new selectors can be added in later versions, so keep an `else` branch when
 * you switch on one.
 */
public abstract class TierSelector internal constructor() {
    /** The index in [eligible] to start at, or null when no eligible tier can be the start. */
    internal abstract fun startIndex(eligible: List<StrategyId>): Int?

    /** Starts at the first tier that may run. This is the default. */
    public object Linear : TierSelector() {
        override fun startIndex(eligible: List<StrategyId>): Int? = if (eligible.isEmpty()) null else 0

        override fun toString(): String = "Linear"
    }

    /** Starts at [tier] and climbs upward from it; the tiers below it never run. */
    public class Fixed(public val tier: StrategyId) : TierSelector() {
        override fun startIndex(eligible: List<StrategyId>): Int? = eligible.indexOf(tier).takeIf { it >= 0 }

        override fun toString(): String = "Fixed($tier)"
    }
}
