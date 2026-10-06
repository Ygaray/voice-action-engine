package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.internal.toReason
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

private const val ATTEMPT_COMPLETED = "completed"
private const val ATTEMPT_ESCALATED = "escalated"
private const val ATTEMPT_NO_MATCH = "no_match"
private const val ATTEMPT_FAILED = "failed"
private const val ATTEMPT_SUPPRESSED = "escalation_suppressed"

/**
 * Runs the ladder once, starting at the tier the selector picks. A completed or failed tier ends the walk; an
 * escalating tier hands its carry to the next; a tier with no match hands over nothing. When the selector asks a
 * picker, the tiers that make no model call run first and the picker chooses among the rest. Used for one command only.
 */
internal class TierWalk(
    private val ladder: Ladder,
    private val policy: TierPolicy,
    private val coordinator: CommitCoordinator,
    private val recorder: RunRecorder,
    private val runId: String,
    private val parentRunId: String?,
    router: ModelRouter,
) {
    private val scope = RunScope(runId, parentRunId, policy, coordinator, recorder, router)
    private var carry: Any? = null
    private var lastReason: EscalationReason? = null

    /** Walks the ladder and returns the command's outcome. */
    suspend fun run(input: CommandInput): CommandOutcome {
        val start = if (ladder.refusal == null) ladder.selector.startIndex(ladder.tiers.map { it.id }) else null
        if (start == null) {
            return CommandOutcome.Failed(effects(), ladder.refusal ?: FailureReason.NoEligibleTier(), null)
        }
        val picking = ladder.selector.picking
        val outcome = if (picking == null) climb(ladder.tiers.drop(start), input) else walkPicked(picking, input)
        return outcome ?: CommandOutcome.Unhandled(effects(), lastReason, ladder.cappedByPolicy)
    }

    /**
     * The zero-call head runs through [runTier] exactly like any tier (gate, suppression, trace), and its carry passes
     * unchanged to the picked tier. The picker then chooses among the tiers that call a model.
     */
    private suspend fun walkPicked(picking: PickingSpec, input: CommandInput): CommandOutcome? {
        val head = ladder.tiers.takeWhile { it.capabilities.providers.isEmpty() }
        val rest = ladder.tiers.drop(head.size)
        return climb(head, input) ?: climb(rest.drop(StartTierPicking(scope, ladder).startIn(rest, picking, input)), input)
    }

    private suspend fun climb(tiers: List<CommandStrategy>, input: CommandInput): CommandOutcome? {
        for (strategy in tiers) {
            val outcome = runTier(strategy, input)
            if (outcome != null) return outcome
        }
        return null
    }

    /** Runs the tier; anything it throws (except cancellation) becomes a failed outcome, never an escape. */
    private suspend fun executeGuarded(
        strategy: CommandStrategy,
        input: CommandInput,
        session: RunSession,
    ): StrategyOutcome = guarded(
        onFault = { fault ->
            recorder.recordCode(TraceCode.STRATEGY_ERROR)
            val reason = fault.toReason()
            StrategyOutcome.Failed(reason)
        },
    ) { strategy.execute(input, session) }

    private fun effects(): RunEffects = snapshotEffects(runId, parentRunId, coordinator, recorder)

    private suspend fun runTier(strategy: CommandStrategy, input: CommandInput): CommandOutcome? {
        if (ladder.blockedOnDevice(strategy)) {
            recorder.tierSkipped(strategy.id, TraceCode.ON_DEVICE_UNAVAILABLE)
            return CommandOutcome.Failed(effects(), ladder.onDeviceFailure(), null)
        }
        recorder.tierStarted(strategy.id, carry != null)
        val session = RunSession(scope, strategy.id, strategy.capabilities.providers, carry)
        return when (val outcome = executeGuarded(strategy, input, session)) {
            is StrategyOutcome.Completed -> {
                recorder.tierFinished(strategy.id, ATTEMPT_COMPLETED, null, null, null)
                CommandOutcome.Completed(
                    effects(),
                    outcome.reply,
                    outcome.terminalCall,
                    partial = outcome.partial,
                    remainingStepIds = outcome.remainingStepIds,
                )
            }
            is StrategyOutcome.Failed -> {
                recorder.tierFinished(strategy.id, ATTEMPT_FAILED, null, outcome.reason, null)
                CommandOutcome.Failed(effects(), outcome.reason, outcome.details)
            }
            is StrategyOutcome.Escalate ->
                if (hasWorked()) {
                    suppressed(strategy, outcome.reason, outcome.remainingStepIds)
                } else {
                    handUp(strategy, outcome)
                }
            is StrategyOutcome.NoMatch ->
                if (hasWorked()) suppressed(strategy, null, emptyList()) else startFresh(strategy)
        }
    }

    private suspend fun handUp(strategy: CommandStrategy, outcome: StrategyOutcome.Escalate): CommandOutcome? {
        recorder.tierFinished(strategy.id, ATTEMPT_ESCALATED, outcome.reason, null, null)
        carry = outcome.carry
        lastReason = outcome.reason
        return null
    }

    private suspend fun startFresh(strategy: CommandStrategy): CommandOutcome? {
        recorder.tierFinished(strategy.id, ATTEMPT_NO_MATCH, null, null, null)
        carry = null
        lastReason = null
        return null
    }

    /**
     * True once the run has applied anything (an errored or throwing apply included, since it may have written) or
     * holds a proposal. Read from the coordinator's own counts; a strategy's claim about what it did is never used.
     */
    private fun hasWorked(): Boolean = coordinator.appliedCount + coordinator.heldCount > 0

    /**
     * The tier asked to hand the command up after the run had already written or held something, so no later tier may
     * run: it would repeat the write. The command ends as a partial completion with the handed-up reason in the trace.
     * The tier's never-run step ids, if any, go on that outcome: this tier ended the command.
     */
    private suspend fun suppressed(
        strategy: CommandStrategy,
        reason: EscalationReason?,
        remainingStepIds: List<String>,
    ): CommandOutcome {
        recorder.recordCode(TraceCode.ESCALATION_SUPPRESSED)
        recorder.tierFinished(strategy.id, ATTEMPT_SUPPRESSED, null, null, reason)
        return CommandOutcome.Completed(effects(), null, null, partial = true, remainingStepIds = remainingStepIds)
    }
}
