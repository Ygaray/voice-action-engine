package io.github.ygaray.voiceactionengine.core.failure

/**
 * Which budget a run exceeded. The set is open (later versions may add bounds), so keep an `else` branch when
 * switching on one. Only the engine creates values; apps compare against the constants.
 */
@JvmInline
public value class BudgetBound internal constructor(public val value: String) {
    /** The bound's stable string value. */
    override fun toString(): String = value

    /** The bounds the engine currently reports. */
    public companion object {
        /** The iteration (turn) limit was reached. */
        public val ITERATIONS: BudgetBound = BudgetBound("iterations")

        /** The token ceiling was reached. */
        public val TOKENS: BudgetBound = BudgetBound("tokens")
    }
}
