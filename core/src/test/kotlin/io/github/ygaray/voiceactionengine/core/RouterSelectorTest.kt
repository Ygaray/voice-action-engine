package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val routerSelection = MappedSelection(
        mapOf("start_tier_router" to ProviderSelection(ProviderId.ANTHROPIC, "router-model")),
        ProviderSelection(ProviderId.ANTHROPIC, "test-model"),
    )

    @Test
    fun exactlyOneModelTierMeansNoCallNoFallbackAndNoSelection() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val only = llmTier("only") { _, _ -> StrategyOutcome.Completed("o") }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(listOf(head, only), TierSelector.Router { }, fake).execute(input)

            assertEquals("o", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, fake.callCount)
            assertFalse(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            assertNull(outcome.trace.selection)
            assertEquals(1, only.executions)
        }
    }

    @Test
    fun noTokensLeftBeforeThePickMeansNoCallAndAFallback() = runTest {
        NoNetworkGuard.during {
            val head = ScriptedStrategy(StrategyId("grammar"), StrategyCapabilities.NO_PROVIDER, { _, session ->
                session.recordTurn(TurnRecord(null, "m", "end_turn", emptyList(), Usage(0, 0, 0, 10), 1))
                StrategyOutcome.NoMatch()
            })
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(
                listOf(head, single, plan),
                TierSelector.Router { },
                fake,
                policy = TierPolicy { tokenCeiling = 10 },
            ).execute(input)

            assertEquals(0, fake.callCount)
            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertTrue(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            assertEquals("router_fallback", outcome.trace.selection!!.outcome)
        }
    }

    @Test
    fun anUnmappedRouterIdIsLoudAndTheWalkIsLinear() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            val selection = MappedSelection(
                mapOf("start_tier_router" to null),
                ProviderSelection(ProviderId.ANTHROPIC, "test-model"),
            )
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(listOf(single, plan), TierSelector.Router { }, fake, selection = selection)
                .execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, fake.callCount)
            val codes = outcome.trace.codes
            assertTrue(codes.toString(), codes.indexOf(TraceCode.PROVIDER_NOT_SELECTED) in 0 until
                codes.indexOf(TraceCode.ROUTER_FALLBACK))
            assertEquals(listOf(1, 0), listOf(single, plan).map { it.executions })
        }
    }

    @Test
    fun aGarbledAnswerIsAFallbackNotAFailure() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            val wrongTool = FakeAiProvider.toolCall("r1", "other_tool", buildJsonObject { put("tier", "plan") },
                Usage(4, 0, 0, 1))
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, wrongTool)

            val outcome = startTierPipeline(
                listOf(single, plan),
                TierSelector.Router { },
                fake,
                selection = routerSelection,
            ).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, fake.callCount)
            assertTrue(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            assertEquals(0, outcome.trace.selection!!.tiersBypassed)
        }
    }

    @Test
    fun aPipelineWithNoSelectorNeverCallsTheRouter() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(listOf(single, plan), null, fake).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, fake.callCount)
            assertNull(outcome.trace.selection)
        }
    }

    @Test
    fun theRouterUnderOfflineOnlyNeverCallsAndTheCommandIsCapped() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val a = llmTier("a") { _, _ -> StrategyOutcome.Completed("a") }
            val b = llmTier("b") { _, _ -> StrategyOutcome.Completed("b") }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(
                listOf(head, a, b),
                TierSelector.Router { },
                fake,
                policy = TierPolicy { offlineOnly = true },
            ).execute(input)

            assertEquals(0, fake.callCount)
            assertTrue(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            assertTrue((outcome as CommandOutcome.Unhandled).cappedByPolicy)
            assertEquals(0, a.executions + b.executions)
        }
    }
}
