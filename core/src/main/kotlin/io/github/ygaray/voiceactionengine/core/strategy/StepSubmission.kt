package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason

private const val UNKNOWN_RESOLUTION_CODE = "unknown_resolution"

/** Maps every resolution except steps, which [onSteps] handles. The set is open, so anything else fails loudly. */
internal suspend fun resolutionOutcome(
    resolution: Resolution,
    onSteps: suspend (Resolution.Steps) -> StrategyOutcome,
): StrategyOutcome =
    when (resolution) {
        is Resolution.Steps -> onSteps(resolution)
        is Resolution.NoMatch -> StrategyOutcome.NoMatch()
        is Resolution.Escalate -> StrategyOutcome.Escalate(resolution.reason, resolution.carry)
        is Resolution.Failed -> StrategyOutcome.Failed(resolution.reason, resolution.details)
        else -> StrategyOutcome.Failed(FailureReason.Other(UNKNOWN_RESOLUTION_CODE))
    }

/**
 * Submits what a resolver prepared: finished steps first, in list order; then every mutation, in order, as one step so
 * the gate decides once. The resolver prepared the reply before the gate ran, so it cannot know whether the apply
 * succeeded: when an apply reported an error the reply is withheld and the caller reads the outcome's executed list
 * instead. A held proposal is a normal pending state that the outcome carries, so the reply is kept for it.
 *
 * Every action carries [providerCallId], which is null when no provider call produced the steps (a grammar tier).
 */
internal suspend fun submitSteps(
    session: CommandSession,
    steps: Resolution.Steps,
    providerCallId: String?,
): StrategyOutcome {
    steps.steps.filterIsInstance<ToolStep.Finished>().forEach { session.submit(it, providerCallId) }
    val mutations = steps.steps.filterIsInstance<ToolStep.Mutation>().flatMap { it.mutations }
    val applied = if (mutations.isEmpty()) null else session.submit(ToolStep.Mutation(mutations), providerCallId)
    return StrategyOutcome.Completed(if (applied?.isError == true) null else steps.reply)
}
