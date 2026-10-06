package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The picker obeys the same static policy rule as the tiers: a command whose policy leaves it no permitted model path
 * never calls the picker, so no transcript leaves the device for a routing decision.
 */
class StartTierPolicyTest {
    private val input = CommandInput("turn it on", "en", null)
    private val ready = OnDeviceCapability { OnDeviceAvailability.Available() }

    private fun localModel(): ScriptedStrategy = ScriptedStrategy(
        StrategyId("local_model"),
        StrategyCapabilities(setOf(ProviderId.ON_DEVICE)),
        { _, _ -> StrategyOutcome.Completed("d") },
    )

    @Test
    fun anOfflineCommandNeverCallsACloudPickerEvenWithAReadyOnDeviceTier() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val cloud = llmTier("cloud") { _, _ -> StrategyOutcome.Completed("c") }
            val picker = ScriptedPicker(emptyList())
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(
                listOf(head, localModel(), cloud),
                TierSelector.Custom(picker),
                fake,
                policy = TierPolicy { offlineOnly = true },
                onDevice = ready,
            ).execute(input)

            assertEquals(0, picker.calls)
            assertEquals(0, fake.callCount)
            assertEquals(listOf(TraceCode.TIER_SKIPPED_POLICY, TraceCode.ROUTER_FALLBACK), outcome.trace.codes)
            assertNull(outcome.trace.selection)
            assertEquals("d", (outcome as CommandOutcome.Completed).reply)
            assertEquals("local_model", outcome.trace.attempts.last().strategy.value)
        }
    }
}
