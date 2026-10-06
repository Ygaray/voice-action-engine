package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every picker mistake is a Linear walk with one `router_fallback`; the picker can never fail or hang a command. */
class StartTierFallbackTest {
    private val input = CommandInput("turn it on", "en", null)
    private val fake = { FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1))) }

    @Test
    fun aHungPickerUnderTheDefaultPolicyTimesOutToLinear() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ ->
                delay(HANG_MILLIS)
                StrategyId("agentic")
            })

            val outcome = startTierPipeline(listOf(grammar, single, agentic), TierSelector.Custom(picker), fake())
                .execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals(DEFAULT_PICKER_TIMEOUT, currentTime)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.ROUTER_FALLBACK })
            val selection = outcome.trace.selection!!
            assertEquals("router_fallback", selection.outcome)
            assertNull(selection.picked)
            assertEquals(0, selection.tiersBypassed)
            assertEquals(0, agentic.executions)
        }
    }

    @Test
    fun aPickerWithinItsTimeoutIsUsed() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ ->
                delay(WITHIN_MILLIS)
                StrategyId("agentic")
            })
            val policy = TierPolicy { pickerTimeoutMillis = SHORT_TIMEOUT }

            val outcome = startTierPipeline(
                listOf(single, agentic),
                TierSelector.Custom(picker),
                fake(),
                policy = policy,
            ).execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals("picked", outcome.trace.selection!!.outcome)
            assertEquals(1, agentic.executions)
            assertEquals(0, single.executions)
        }
    }

    private companion object {
        const val HANG_MILLIS = 60_000L
        const val DEFAULT_PICKER_TIMEOUT = 2_000L
        const val SHORT_TIMEOUT = 500L
        const val WITHIN_MILLIS = 400L
    }
}
