package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.HeldProposal
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.EngineFault
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.internal.guardedUncancellable
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private const val ATTEMPT_TIMEOUT = "timeout"
private const val ATTEMPT_CANCELLED = "cancelled"
private const val ATTEMPT_FAILED = "failed"

/**
 * An app's composed ladder. Build one with [commandPipeline] and call [execute] once per spoken command.
 */
public class CommandPipeline internal constructor(
    private val preCheck: PolicyPreCheck,
    private val gate: PreApplyGate,
    private val sink: CommitSink,
    private val policySource: TierPolicySource,
    private val clock: () -> Long,
    private val runIds: () -> String,
    private val listener: PipelineEventListener?,
) {
    /** The ladder's tier ids, lowest tier first. */
    public val tiers: List<StrategyId> = preCheck.strategies.map { it.id }

    private val heldCommit = HeldCommit(gate, sink, clock, runIds, listener)

    /**
     * Runs [input] up the ladder and returns what happened. It never throws: a failing strategy, policy source or
     * engine fault becomes a failed outcome. Only cancellation of the calling coroutine propagates, and the sink's
     * `onRunClosed` is still called exactly once, even then.
     */
    public suspend fun execute(input: CommandInput): CommandOutcome {
        val runId = runIds()
        val recorder = RunRecorder(runId, input.parentRunId, input.language, input.transcript.length, clock, listener)
        val coordinator = CommitCoordinator(runId, input.parentRunId, gate, sink, recorder)
        var outcome: CommandOutcome? = null
        var cancelled = false
        try {
            recorder.commandStarted()
            outcome = guarded(onFault = { collapsed(it, runId, input.parentRunId, coordinator, recorder) }) {
                runCommand(input, runId, coordinator, recorder)
            }
            return outcome
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            coordinator.close()
            // A tier still in flight (cancelled, or ended by an error nothing collapsed) must reach the trace.
            val unfinished = if (cancelled) ATTEMPT_CANCELLED else ATTEMPT_FAILED
            withContext(NonCancellable) { recorder.flushInFlight(unfinished, null) }
            val effects = snapshotEffects(runId, input.parentRunId, coordinator, recorder)
            closeRun(sink, runId, recorder, terminationOf(outcome, cancelled, effects))
        }
    }

    /**
     * Commits the changes the gate [held] earlier, without asking the gate again.
     *
     * It opens a new run whose `parentRunId` is the held run's id and which closes once on its own, so the original
     * run's close stays final. The first call applies; any later or concurrent call for the same [held] returns that
     * call's outcome with nothing applied. Like [execute] it never throws, except for the caller's own cancellation;
     * if that cancels the first call mid-apply, the proposal stays used up and later calls get a failed outcome with
     * reason `Other("commit_held_cancelled")` carrying what was journaled. A change that threw or reported an error
     * is an `is_error` action inside a `Completed` outcome, as the apps count them; read [CommandOutcome.commits] and
     * [CommandOutcome.executed] to learn what was actually written, never the outcome type alone.
     *
     * The engine sets no deadline on this run: `TierPolicy.commandTimeoutMillis` covers [execute] only, so an `apply`
     * that never returns holds the proposal's claim and every waiting caller. Bound it inside `apply` if that matters.
     */
    public suspend fun commitHeld(held: HeldProposal): CommandOutcome = heldCommit.resolve(held, held.mutations)

    /**
     * Like [commitHeld], but applies [amended] instead of the held changes; the held changes are never applied.
     *
     * Throws [IllegalArgumentException], before the proposal is used up, when [amended] is empty. The list is copied.
     */
    public suspend fun commitHeld(held: HeldProposal, amended: List<PendingMutation>): CommandOutcome =
        heldCommit.resolve(held, amended)

    private suspend fun runCommand(
        input: CommandInput,
        runId: String,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        val policy = guarded(onFault = { recorder.recordCode(TraceCode.POLICY_SOURCE_ERROR); null }) {
            policySource.current()
        }
        return if (policy == null) {
            val effects = snapshotEffects(runId, input.parentRunId, coordinator, recorder)
            CommandOutcome.Failed(effects, FailureReason.PolicyUnavailable(), null)
        } else {
            withDeadline(input, policy, runId, coordinator, recorder)
        }
    }

    private suspend fun withDeadline(
        input: CommandInput,
        policy: TierPolicy,
        runId: String,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        val deadline = policy.commandTimeoutMillis
        if (deadline == null) return walk(input, policy, runId, coordinator, recorder)
        return withTimeoutOrNull(deadline) { walk(input, policy, runId, coordinator, recorder) }
            ?: timedOut(runId, input.parentRunId, coordinator, recorder)
    }

    private suspend fun walk(
        input: CommandInput,
        policy: TierPolicy,
        runId: String,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        val ladder = preCheck.check(policy, recorder)
        return TierWalk(ladder, policy, coordinator, recorder, runId, input.parentRunId).run(input)
    }

    private suspend fun timedOut(
        runId: String,
        parentRunId: String?,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        recorder.recordCode(TraceCode.ENGINE_TIMEOUT)
        recorder.flushInFlight(ATTEMPT_TIMEOUT, FailureReason.Timeout())
        val effects = snapshotEffects(runId, parentRunId, coordinator, recorder)
        return CommandOutcome.Failed(effects, FailureReason.Timeout(), null)
    }

    /** An engine fault that reached the top: a leaked timeout is a timeout, anything else is unexpected. */
    private suspend fun collapsed(
        fault: EngineFault,
        runId: String,
        parentRunId: String?,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        val reason = if (fault.timeoutLeak) FailureReason.Timeout() else FailureReason.Unexpected(fault.errorClass)
        recorder.flushInFlight(ATTEMPT_FAILED, reason)
        return CommandOutcome.Failed(snapshotEffects(runId, parentRunId, coordinator, recorder), reason, null)
    }

    override fun toString(): String = "CommandPipeline(tiers=$tiers)"
}

/**
 * Tells the sink the run ended, then the listener. It runs to completion even when the run is cancelled; a sink fault
 * is only a code.
 */
internal suspend fun closeRun(sink: CommitSink, runId: String, recorder: RunRecorder, termination: RunTermination) {
    withContext(NonCancellable) {
        guardedUncancellable(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) }) {
            sink.onRunClosed(runId, termination)
        }
        recorder.runClosed(termination)
    }
}

/**
 * How the run ends for the sink. A produced outcome maps directly; no outcome after a cancellation is `Cancelled`; no
 * outcome otherwise means an error the engine does not collapse (such as an assertion failure) is escaping.
 */
internal fun terminationOf(outcome: CommandOutcome?, cancelled: Boolean, effects: RunEffects): RunTermination =
    when {
        outcome is CommandOutcome.Completed -> RunTermination.Done(outcome.effects, outcome.partial)
        outcome is CommandOutcome.Failed -> RunTermination.Failed(outcome.effects, outcome.reason, outcome.details)
        outcome is CommandOutcome.Unhandled -> RunTermination.Exhausted(outcome.effects, outcome.lastReason)
        cancelled -> RunTermination.Cancelled(effects)
        else -> RunTermination.Failed(effects, FailureReason.Unexpected("Error"), null)
    }
