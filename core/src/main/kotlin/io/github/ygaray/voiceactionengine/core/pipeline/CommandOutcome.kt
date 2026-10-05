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

// The run id of an outcome for a run that never began, so nothing the app made (ids, clock readings) is involved.
private const val UNSTARTED_RUN_ID = "unstarted"

/**
 * The failed outcome for a run that could not begin because an app hook (the run id maker or the clock) threw. Nothing
 * ran, so nothing was reported to the sink; the effects are empty and the failure names only the exception class.
 */
internal fun unstartedFailure(
    parentRunId: String?,
    language: String?,
    transcriptLength: Int,
    errorClass: String,
): CommandOutcome.Failed {
    val trace = RunRecorder(UNSTARTED_RUN_ID, parentRunId, language, transcriptLength, { 0L }).snapshot()
    val effects = RunEffects(UNSTARTED_RUN_ID, parentRunId, emptyList(), emptyList(), trace)
    return CommandOutcome.Failed(effects, FailureReason.Unexpected(errorClass), null)
}

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

    /**
     * What the run did, as of the moment the run ended and before it was closed. A code recorded while closing (the
     * sink or the listener failing on the close itself) is not in this trace; it reaches the event listener only.
     */
    public val trace: CommandTrace
        get() = effects.trace

    /**
     * A tier finished the command, or the engine stopped the ladder to avoid writing twice.
     *
     * @property reply text to show the user, or null.
     * @property terminalCall the terminal tool call that ended the run, or null.
     * @property partial true when the command did some work and could not finish, in either of two cases. A tier
     * committed or held a change and then asked for a later tier, which the engine blocks so nothing is written twice
     * ([reply] and [terminalCall] are null then, and the tier's reason is on its trace attempt as the suppressed
     * escalation). Or a tier acted on the model's first tool call and dropped the extra calls of the same answer, so
     * the user may have asked for more than was done ([reply] and [terminalCall] are those of the first call, and the
     * trace holds `extra_tool_calls_dropped`). Render it as "did X, couldn't finish" and never as full success;
     * [commits] and [held] say what was done.
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
     * @property cappedByPolicy true when the command's policy skipped at least one tier (a `tier_skipped_policy` in
     * the trace: a tier above `maxTier`, or a tier its provider restriction or offline-only mode does not permit,
     * offline-only included) and no tier handled the command. False when nothing was skipped by policy, including
     * when a selector simply started past the earlier tiers. A ladder whose tiers were all skipped is never
     * unhandled: it fails with `NoEligibleTier` or `ProviderUnavailable`.
     */
    public class Unhandled internal constructor(
        internal override val effects: RunEffects,
        public val lastReason: EscalationReason?,
        public val cappedByPolicy: Boolean,
    ) : CommandOutcome() {
        override fun toString(): String =
            "Unhandled(runId=$runId, lastReason=$lastReason, cappedByPolicy=$cappedByPolicy, " +
                "executed=${executed.size}, held=${held.size})"
    }
}
