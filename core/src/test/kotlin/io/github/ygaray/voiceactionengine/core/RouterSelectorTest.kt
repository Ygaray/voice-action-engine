package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class RouterSelectorTest {
    private val input = CommandInput("turn it on", "en", null)

    private fun pick(tier: String) =
        FakeAiProvider.toolCall("r1", "pick_start_tier", buildJsonObject { put("tier", tier) }, Usage(20, 0, 0, 3))

    @Test
    fun theRouterPicksTheStartTierWithOneForcedCall() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, pick("plan"))
            val selection = MappedSelection(
                mapOf("start_tier_router" to ProviderSelection(ProviderId.ANTHROPIC, "router-model")),
                ProviderSelection(ProviderId.ANTHROPIC, "test-model"),
            )
            val selector = TierSelector.Router {
                tierDescriptions = mapOf(StrategyId("single") to "one call", StrategyId("plan") to "several steps")
            }

            val outcome = startTierPipeline(
                listOf(head, single, plan, agentic),
                selector,
                fake,
                selection = selection,
            ).execute(input)

            assertEquals("p", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, fake.callCount)
            val request = fake.calls[0]
            assertEquals("router-model", request.model)
            assertEquals(listOf("pick_start_tier"), request.request.tools.map { it.name })
            assertEquals(ToolChoice.Required("pick_start_tier"), request.request.toolChoice)
            assertEquals(ReasoningMode.OFF, request.request.reasoning)
            assertEquals(true, selection.requested.contains(StrategyId("start_tier_router")))
            val chosen = outcome.trace.selection!!
            assertEquals(StrategyId("start_tier_router"), chosen.picker)
            assertEquals(StrategyId("plan"), chosen.picked)
            assertEquals(idsOf("single", "plan", "agentic"), chosen.eligible)
            assertEquals(1, chosen.tiersBypassed)
            assertEquals(listOf(listOf("pick_start_tier")), chosen.turns.map { it.toolNames })
            assertEquals(listOf(0, 1, 0), listOf(single, plan, agentic).map { it.executions })
        }
    }
}
