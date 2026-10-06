package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.StrategyId

/**
 * What a start-tier pick did, kept apart from the tiers' attempts so a "handled by" view never sees a phantom tier.
 * Ids, codes, counts and usage only; never the transcript.
 *
 * @property picker the id the pick's own model calls were routed and recorded under.
 * @property outcome how the pick ended: `picked` or `router_fallback`, or `cancelled`, `timeout` or `failed` when the
 * run ended while the pick was still running. An open set: keep an `else` branch.
 * @property picked the tier the picker chose, or null when nothing usable was chosen.
 * @property eligible the model tiers the picker was offered, in ladder order.
 * @property tiersBypassed the number of eligible model tiers a Linear walk would have tried before the picked one. It
 * is an upper bound on attempts avoided, not proven savings, since Linear might have stopped earlier. It is 0 when
 * nothing was picked, and tiers that make no model call are never counted.
 * @property latencyMillis how long the pick took, on the pipeline's clock.
 * @property turns the model round trips the pick reported, in order.
 * @property usage the tokens used by the pick's turns, summed bucket by bucket.
 */
public class StartTierSelection internal constructor(
    public val picker: StrategyId,
    public val outcome: String,
    public val picked: StrategyId?,
    eligible: List<StrategyId>,
    public val tiersBypassed: Int,
    public val latencyMillis: Long,
    turns: List<TurnRecord>,
) {
    /** A copy of the tiers the picker was offered. */
    public val eligible: List<StrategyId> = eligible.toList()

    /** A copy of the pick's round trips. */
    public val turns: List<TurnRecord> = turns.toList()

    /** The tokens used by the pick's turns. */
    public val usage: Usage = this.turns.fold(Usage.ZERO) { sum, turn -> sum + turn.usage }

    override fun toString(): String =
        "StartTierSelection(picker=$picker, outcome=$outcome, picked=$picked, eligible=${this.eligible}, " +
            "tiersBypassed=$tiersBypassed, latencyMillis=$latencyMillis, turns=${this.turns.size}, usage=$usage)"
}
