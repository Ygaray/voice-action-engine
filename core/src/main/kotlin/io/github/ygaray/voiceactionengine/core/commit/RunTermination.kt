package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.RunEffects
import io.github.ygaray.voiceactionengine.core.telemetry.CommandTrace

/**
 * How a run ended, as told to [CommitSink.onRunClosed]. The set is closed. Running out of budget and a provider error
 * are both [Failed], told apart by their reason.
 *
 * A gate fault is an error, never a hold: it is reported as an is_error action with code gate_error, and there is
 * nothing to commitHeld. It is never reported as committed either: it is not in [commits], and no action of kind
 * [ActionKind.COMMITTED] stands for it.
 */
public sealed class RunTermination {
    internal abstract val effects: RunEffects

    /** A stable lower-case code for this kind of ending. */
    public abstract val code: String

    /** The id of the run. */
    public val runId: String
        get() = effects.runId

    /** The id of the earlier run this command answers, or null. */
    public val parentRunId: String?
        get() = effects.parentRunId

    /** Every action recorded, in order. */
    public val executed: List<ExecutedAction>
        get() = effects.executed

    /** The actions whose change was committed, in order. */
    public val commits: List<ExecutedAction>
        get() = effects.commits

    /** Changes held instead of applied. A gate fault is never listed here; it is an is_error action. */
    public val held: List<HeldProposal>
        get() = effects.held

    /**
     * What the run did, as of the moment the run ended and before it was closed. A code recorded while closing (the
     * sink or the listener failing on the close itself) is not in this trace; it reaches the event listener only.
     */
    public val trace: CommandTrace
        get() = effects.trace

    override fun toString(): String =
        "RunTermination(code=$code, runId=$runId, executed=${executed.size}, held=${held.size})"

    /**
     * The run finished with an answer.
     *
     * @property partial true when a later tier was needed but blocked because earlier work was already done.
     */
    public class Done internal constructor(
        internal override val effects: RunEffects,
        public val partial: Boolean,
    ) : RunTermination() {
        override val code: String
            get() = "done"
    }

    /**
     * The run failed.
     *
     * @property reason why.
     * @property details transport facts, or null.
     */
    public class Failed internal constructor(
        internal override val effects: RunEffects,
        public val reason: FailureReason,
        public val details: FailureDetails?,
    ) : RunTermination() {
        override val code: String
            get() = "failed"
    }

    /**
     * Every tier was tried and none could handle the command.
     *
     * @property lastReason the last tier's reason for handing up, or null.
     */
    public class Exhausted internal constructor(
        internal override val effects: RunEffects,
        public val lastReason: EscalationReason?,
    ) : RunTermination() {
        override val code: String
            get() = "exhausted"
    }

    /** The run was cancelled before it produced an outcome. */
    public class Cancelled internal constructor(
        internal override val effects: RunEffects,
    ) : RunTermination() {
        override val code: String
            get() = "cancelled"
    }
}
