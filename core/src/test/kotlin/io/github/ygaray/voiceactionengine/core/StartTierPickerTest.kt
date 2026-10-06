package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StartTierPickerTest {
    private val input = CommandInput("turn it on", "en", null)

    @Test
    fun aCustomPickerChoosesWhereTheLlmWalkStarts() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            var tokensSeenByAgentic = -1L
            val agentic = llmTier("agentic") { _, session ->
                tokensSeenByAgentic = session.tokensUsed
                StrategyOutcome.Completed("a")
            }
            val picker = ScriptedPicker({ _, _, ctx ->
                ctx.model().complete(pickTurnRequest())
                StrategyId("agentic")
            })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))

            val outcome = startTierPipeline(listOf(grammar, single, agentic), TierSelector.Custom(picker), fake)
                .execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, picker.calls)
            assertEquals(idsOf("single", "agentic"), picker.eligibleSeen.single())
            assertEquals(input.transcript, picker.inputsSeen.single().transcript)
            assertEquals(listOf(1, 0, 1), listOf(grammar, single, agentic).map { it.executions })
            assertEquals(listOf("grammar", "agentic"), outcome.trace.attempts.map { it.strategy.value })
            assertFalse(outcome.trace.attempts.last().carryIn)
            assertEquals(5L, tokensSeenByAgentic)
            assertEquals(1, fake.callCount)
            assertFalse(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
        }
    }
}
