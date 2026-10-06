package io.github.ygaray.voiceactionengine.spike.trial

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec

/** A mutation that changes nothing: the gate sees it as a proposal, and applying it has no effect. */
private class NoOpMutation(override val toolName: String) : PendingMutation {
    override suspend fun apply(): StepResult = StepResult("ok")

    override fun toString(): String = "NoOpMutation(toolName=$toolName)"
}

/**
 * The resolver of the spike: it does no I/O and never writes. A mutating tool becomes one [ToolStep.Mutation] of a no-op,
 * so the gate counts the proposal and nothing is applied; a read tool becomes a finished read with an empty result. A
 * terminal tool never reaches the resolver (the single-shot tier completes it itself).
 */
internal class SpikeResolver(private val tools: () -> List<ToolSpec>) : OutcomeResolver {
    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        val tool = tools().firstOrNull { it.name == extraction.toolName }
        return if (tool != null && tool.mutating) {
            Resolution.Steps(listOf(ToolStep.Mutation(NoOpMutation(tool.name))))
        } else {
            Resolution.Steps(listOf(ToolStep.Finished(extraction.toolName, FinishedKind.READ, StepResult(""))))
        }
    }

    override fun toString(): String = "SpikeResolver"
}
