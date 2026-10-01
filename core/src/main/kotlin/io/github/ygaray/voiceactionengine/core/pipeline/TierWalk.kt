package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder

private const val ATTEMPT_COMPLETED = "completed"
private const val ATTEMPT_ESCALATED = "escalated"
private const val ATTEMPT_NO_MATCH = "no_match"
private const val ATTEMPT_FAILED = "failed"

/**
 * Runs the ladder once, lowest tier first. A completed or failed tier ends the walk; an escalating tier hands its
 * carry to the next; a tier with no match hands over nothing. Used for one command only.
 */
internal class TierWalk(
    private val strategies: List<CommandStrategy>,
    private val policy: TierPolicy,
    private val coordinator: CommitCoordinator,
    private val recorder: RunRecorder,
    private val runId: String,
    private val parentRunId: String?,
) {
    private var carry: Any? = null
    private var lastReason: EscalationReason? = null

    /** Walks the ladder and returns the command's outcome. */
    suspend fun run(input: CommandInput): CommandOutcome {
        for (strategy in strategies) {
            val outcome = runTier(strategy, input)
            if (outcome != null) return outcome
        }
        return CommandOutcome.Unhandled(effects(), lastReason)
    }

    private fun effects(): RunEffects = snapshotEffects(runId, parentRunId, coordinator, recorder)

    private suspend fun runTier(strategy: CommandStrategy, input: CommandInput): CommandOutcome? {
        recorder.tierStarted(strategy.id)
        val session = RunSession(runId, parentRunId, strategy.id, policy, carry, coordinator)
        return when (val outcome = strategy.execute(input, session)) {
            is StrategyOutcome.Completed -> {
                recorder.tierFinished(strategy.id, ATTEMPT_COMPLETED, null, null, null)
                CommandOutcome.Completed(effects(), outcome.reply, outcome.terminalCall, partial = false)
            }
            is StrategyOutcome.Failed -> {
                recorder.tierFinished(strategy.id, ATTEMPT_FAILED, null, outcome.reason, null)
                CommandOutcome.Failed(effects(), outcome.reason, outcome.details)
            }
            is StrategyOutcome.Escalate -> {
                recorder.tierFinished(strategy.id, ATTEMPT_ESCALATED, outcome.reason, null, null)
                carry = outcome.carry
                lastReason = outcome.reason
                null
            }
            is StrategyOutcome.NoMatch -> {
                recorder.tierFinished(strategy.id, ATTEMPT_NO_MATCH, null, null, null)
                carry = null
                lastReason = null
                null
            }
        }
    }
}
