package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.ExecutedAction
import io.github.ygaray.voiceactionengine.core.commit.HeldProposal
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.telemetry.CommandTrace
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder

/**
 * Everything a run did, shared by [CommandOutcome] and the run's termination so both report the same effects.
 * [commits] is derived from [executed] by kind.
 */
internal class RunEffects(
    val runId: String,
    val parentRunId: String?,
    val executed: List<ExecutedAction>,
    val held: List<HeldProposal>,
    val trace: CommandTrace,
) {
    val commits: List<ExecutedAction> = executed.filter { it.kind == ActionKind.COMMITTED }
}

/** What the run has done so far, taken from the coordinator's record and the recorder's trace. */
internal fun snapshotEffects(
    runId: String,
    parentRunId: String?,
    coordinator: CommitCoordinator,
    recorder: RunRecorder,
): RunEffects = RunEffects(runId, parentRunId, coordinator.executed(), coordinator.held(), recorder.snapshot())

/**
 * The typed result of one command. The set is closed: completed, failed or unhandled.
 *
 * Every outcome reports what the run did to the app, because a failure can follow real writes. Offer the user a
 * retry only when [commits] is empty.
 */
public sealed class CommandOutcome {
    internal abstract val effects: RunEffects

    /** The command's id. */
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

    /** Changes the gate held instead of applying. */
    public val held: List<HeldProposal>
        get() = effects.held

    /** What the run did. */
    public val trace: CommandTrace
        get() = effects.trace

    /**
     * A tier finished the command.
     *
     * @property reply text to show the user, or null.
     * @property terminalCall the terminal tool call that ended the run, or null.
     * @property partial true when a later tier was needed but blocked because earlier work was already done.
     */
    public class Completed internal constructor(
        internal override val effects: RunEffects,
        public val reply: String?,
        public val terminalCall: TerminalCall?,
        public val partial: Boolean,
    ) : CommandOutcome() {
        override fun toString(): String =
            "Completed(runId=$runId, replyLength=${reply?.length}, terminalCall=$terminalCall, partial=$partial, " +
                "executed=${executed.size}, held=${held.size})"
    }

    /**
     * The command failed.
     *
     * @property reason why.
     * @property details transport facts, or null.
     */
    public class Failed internal constructor(
        internal override val effects: RunEffects,
        public val reason: FailureReason,
        public val details: FailureDetails?,
    ) : CommandOutcome() {
        override fun toString(): String =
            "Failed(runId=$runId, reason=$reason, details=$details, executed=${executed.size}, held=${held.size})"
    }

    /**
     * Every tier was tried and none could handle the command.
     *
     * @property lastReason the last tier's reason for handing up, or null after a final no-match.
     */
    public class Unhandled internal constructor(
        internal override val effects: RunEffects,
        public val lastReason: EscalationReason?,
    ) : CommandOutcome() {
        override fun toString(): String =
            "Unhandled(runId=$runId, lastReason=$lastReason, executed=${executed.size}, held=${held.size})"
    }
}
