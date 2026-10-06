package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason

/** How one strategy's attempt ended. The set is closed: a strategy finishes, hands up, declines, or fails. */
public sealed class StrategyOutcome {
    /**
     * The strategy finished the command.
     *
     * @property reply text to show the user, or null.
     * @property terminalCall the terminal tool call that ended the run (for example a clarification request), or null.
     * A completed outcome carries a reply or a terminalCall, never both.
     * @property partial true when the strategy finished but dropped part of what the model asked for (for example
     * extra tool calls it never acted on), so the user may have asked for more than was done. The engine reports the
     * command as a partial completion. False for a clean finish.
     */
    public class Completed internal constructor(
        public val reply: String?,
        public val terminalCall: TerminalCall?,
        public val partial: Boolean,
        /**
         * The ids of the planned steps this tier never ran because it stopped early. The engine reports them on the
         * command's outcome when this tier ends the command. Internal: no public constructor takes them.
         */
        internal val remainingStepIds: List<String>,
    ) : StrategyOutcome() {
        /** Finished with [reply] and [terminalCall], with [partial] saying whether part of the ask was dropped. */
        public constructor(reply: String?, terminalCall: TerminalCall?, partial: Boolean) :
            this(reply, terminalCall, partial, emptyList())

        /** Finished with [reply] and [terminalCall] and nothing dropped. */
        public constructor(reply: String?, terminalCall: TerminalCall?) : this(reply, terminalCall, false)

        /** Finished with [reply], no terminal call, and nothing dropped. */
        public constructor(reply: String?) : this(reply, null, false)

        init {
            require(reply == null || terminalCall == null) { "Completed carries a reply or a terminalCall, not both" }
        }

        override fun toString(): String =
            "Completed(replyLength=${reply?.length}, terminalCall=$terminalCall, partial=$partial)"
    }

    /**
     * The strategy could not finish and hands the command to the next tier.
     *
     * @property reason why it handed up.
     * @property carry an opaque object for the next tier to start from, or null. The engine never inspects it.
     */
    public class Escalate internal constructor(
        public val reason: EscalationReason,
        public val carry: Any?,
        /**
         * The ids of the planned steps this tier never ran because it stopped early. The engine reports them on the
         * command's outcome when it ends the command instead of moving on (a suppressed escalation), and drops them
         * when the command moves to the next tier. Internal: no public constructor takes them.
         */
        internal val remainingStepIds: List<String>,
    ) : StrategyOutcome() {
        /** Escalate with [reason] and [carry]. */
        public constructor(reason: EscalationReason, carry: Any?) : this(reason, carry, emptyList())

        /** Escalate with nothing to carry. */
        public constructor(reason: EscalationReason) : this(reason, null)

        override fun toString(): String =
            "Escalate(reason=$reason, carry=${carry?.let { it::class.simpleName }})"
    }

    /** The strategy found nothing it could handle. The next tier starts fresh. */
    public class NoMatch : StrategyOutcome() {
        override fun toString(): String = "NoMatch"
    }

    /**
     * The strategy failed and the command stops here.
     *
     * @property reason why it failed.
     * @property details transport facts that go with the failure, or null.
     */
    public class Failed(
        public val reason: FailureReason,
        public val details: FailureDetails?,
    ) : StrategyOutcome() {
        /** Failed with no transport details. */
        public constructor(reason: FailureReason) : this(reason, null)

        override fun toString(): String = "Failed(reason=$reason, details=$details)"
    }
}
