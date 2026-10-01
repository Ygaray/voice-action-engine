package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
     * Runs [input] up the ladder and returns what happened. The sink's `onRunClosed` is called exactly once, even
     * when the calling coroutine is cancelled.
     */
    public suspend fun execute(input: CommandInput): CommandOutcome {
        val runId = runIds()
        val recorder = RunRecorder(runId, input.parentRunId, input.language, input.transcript.length, clock)
        val coordinator = CommitCoordinator(runId, input.parentRunId, gate, sink, recorder)
        var outcome: CommandOutcome? = null
        try {
            outcome = runCommand(input, runId, coordinator, recorder)
            return outcome
        } finally {
            val termination = outcome?.let { terminationOf(it) }
                ?: RunTermination.Cancelled(snapshotEffects(runId, input.parentRunId, coordinator, recorder))
            close(runId, recorder, termination)
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
        } ?: return CommandOutcome.Failed(
            snapshotEffects(runId, input.parentRunId, coordinator, recorder),
            FailureReason.PolicyUnavailable(),
            null,
        )
        val ladder = preCheck.check(policy, recorder)
        return TierWalk(ladder, policy, coordinator, recorder, runId, input.parentRunId).run(input)
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

private fun terminationOf(outcome: CommandOutcome): RunTermination = when (outcome) {
    is CommandOutcome.Completed -> RunTermination.Done(outcome.effects, outcome.partial)
    is CommandOutcome.Failed -> RunTermination.Failed(outcome.effects, outcome.reason, outcome.details)
    is CommandOutcome.Unhandled -> RunTermination.Exhausted(outcome.effects, outcome.lastReason)
}
