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
import io.github.ygaray.voiceactionengine.core.internal.toReason
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.internal.guardedUncancellable
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
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
private const val ERROR_CLASS = "Error"

/** The identity, trace and write path of a run that has begun. */
private class StartedRun(
    val runId: String,
    val recorder: RunRecorder,
    val coordinator: CommitCoordinator,
)

/** The build-time collaborators a command consults before and during the walk, kept together. */
internal class PipelineWiring(val preCheck: PolicyPreCheck, val router: ModelRouter, val table: ModelCapabilityTable)

/**
 * An app's composed ladder. Build one with [commandPipeline] and call [execute] once per spoken command.
 */
public class CommandPipeline internal constructor(
    private val wiring: PipelineWiring,
    private val gate: PreApplyGate,
    private val sink: CommitSink,
    private val policySource: TierPolicySource,
    private val clock: () -> Long,
    private val runIds: () -> String,
    private val listener: PipelineEventListener?,
) {
    /** The ladder's tier ids, lowest tier first. */
    public val tiers: List<StrategyId> = wiring.preCheck.strategies.map { it.id }

    /**
     * What each registered model can do, after the app's `capabilities(...)` overrides. Public so a model picker can
     * mark or filter models that cannot take tools. Precedence, highest first: the app's override for the exact
     * provider and model, the provider's own default for that model, then the unknown-model default.
     */
    public val capabilityTable: ModelCapabilityTable = wiring.table

    private val heldCommit = HeldCommit(gate, sink, clock, runIds, listener)

    /**
     * Runs [input] up the ladder and returns what happened. A failing strategy, policy source or engine fault becomes
     * a failed outcome, and the sink's `onRunClosed` is called exactly once. What can still escape:
     * - the calling coroutine's own cancellation (the run still closes once, as cancelled);
     * - a JVM `Error` such as out of memory, a stack overflow or an assertion failure, which the engine does not
     *   catch (the run still closes once, as failed with `Unexpected("Error")`, before the error propagates).
     *
     * If the app's run id maker or clock throws before the run can begin, the result is `Failed(Unexpected)` with no
     * actions, and the sink is not told, because no run began.
     */
    public suspend fun execute(input: CommandInput): CommandOutcome {
        var failure: EngineFault? = null
        val started = guarded<StartedRun?>(onFault = { failure = it; null }) { startRun(input) }
        if (started == null) {
            val errorClass = failure?.errorClass ?: ERROR_CLASS
            return unstartedFailure(input.parentRunId, input.language, input.transcript.length, errorClass)
        }
        return drive(input, started)
    }

    private fun startRun(input: CommandInput): StartedRun {
        val runId = runIds()
        val recorder = RunRecorder(runId, input.parentRunId, input.language, input.transcript.length, clock, listener)
        return StartedRun(runId, recorder, CommitCoordinator(runId, input.parentRunId, null, gate, sink, recorder))
    }

    private suspend fun drive(input: CommandInput, started: StartedRun): CommandOutcome {
        val runId = started.runId
        val recorder = started.recorder
        val coordinator = started.coordinator
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
     * call's outcome with nothing applied. Like [execute] it throws only for the caller's own cancellation or a JVM
     * `Error`, and a throwing run id maker or clock gives `Failed(Unexpected)`. If the caller's cancellation cancels
     * the first call mid-apply, the proposal stays used up and later calls get a failed outcome with
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
        val ladder = wiring.preCheck.check(policy, recorder)
        return TierWalk(ladder, policy, coordinator, recorder, runId, input.parentRunId, wiring.router).run(input)
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
        val reason = fault.toReason()
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
        else -> RunTermination.Failed(effects, FailureReason.Unexpected(ERROR_CLASS), null)
    }
