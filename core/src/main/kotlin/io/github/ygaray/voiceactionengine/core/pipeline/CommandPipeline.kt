package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.EngineFault
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

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
) {
    /** The ladder's tier ids, lowest tier first. */
    public val tiers: List<StrategyId> = preCheck.strategies.map { it.id }

    /**
     * Runs [input] up the ladder and returns what happened. It never throws: a failing strategy, policy source or
     * engine fault becomes a failed outcome. Only cancellation of the calling coroutine propagates, and the sink's
     * `onRunClosed` is still called exactly once, even then.
     */
    public suspend fun execute(input: CommandInput): CommandOutcome {
        val runId = runIds()
        val recorder = RunRecorder(runId, input.parentRunId, input.language, input.transcript.length, clock)
        val coordinator = CommitCoordinator(runId, input.parentRunId, gate, sink, recorder)
        var outcome: CommandOutcome? = null
        var cancelled = false
        try {
            outcome = guarded(onFault = { collapsed(it, runId, input.parentRunId, coordinator, recorder) }) {
                runCommand(input, runId, coordinator, recorder)
            }
            return outcome
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            val effects = snapshotEffects(runId, input.parentRunId, coordinator, recorder)
            close(runId, recorder, terminationOf(outcome, cancelled, effects))
        }
    }

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

    private fun timedOut(
        runId: String,
        parentRunId: String?,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        recorder.recordCode(TraceCode.ENGINE_TIMEOUT)
        val effects = snapshotEffects(runId, parentRunId, coordinator, recorder)
        return CommandOutcome.Failed(effects, FailureReason.Timeout(), null)
    }

    /** An engine fault that reached the top: a leaked timeout is a timeout, anything else is unexpected. */
    private fun collapsed(
        fault: EngineFault,
        runId: String,
        parentRunId: String?,
        coordinator: CommitCoordinator,
        recorder: RunRecorder,
    ): CommandOutcome {
        val reason = if (fault.timeoutLeak) FailureReason.Timeout() else FailureReason.Unexpected(fault.errorClass)
        return CommandOutcome.Failed(snapshotEffects(runId, parentRunId, coordinator, recorder), reason, null)
    }

    private suspend fun close(runId: String, recorder: RunRecorder, termination: RunTermination) {
        withContext(NonCancellable) {
            guarded(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) }) {
                sink.onRunClosed(runId, termination)
            }
        }
    }

    override fun toString(): String = "CommandPipeline(tiers=$tiers)"
}

/**
 * How the run ends for the sink. A produced outcome maps directly; no outcome after a cancellation is `Cancelled`; no
 * outcome otherwise means an error the engine does not collapse (such as an assertion failure) is escaping.
 */
private fun terminationOf(outcome: CommandOutcome?, cancelled: Boolean, effects: RunEffects): RunTermination =
    when {
        outcome is CommandOutcome.Completed -> RunTermination.Done(outcome.effects, outcome.partial)
        outcome is CommandOutcome.Failed -> RunTermination.Failed(outcome.effects, outcome.reason, outcome.details)
        outcome is CommandOutcome.Unhandled -> RunTermination.Exhausted(outcome.effects, outcome.lastReason)
        cancelled -> RunTermination.Cancelled(effects)
        else -> RunTermination.Failed(effects, FailureReason.Unexpected("Error"), null)
    }
