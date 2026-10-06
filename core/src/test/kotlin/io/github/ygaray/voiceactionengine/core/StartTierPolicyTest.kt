package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val skipped = TraceCode.TIER_SKIPPED_POLICY

    /** Runs a NoMatch head and two cloud tiers under [policy]; the picker and the provider must stay unused. */
    private suspend fun noModelTierLeft(policy: TierPolicy): CommandOutcome.Unhandled {
        val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
        val a = llmTier("a") { _, _ -> StrategyOutcome.Completed("a") }
        val b = llmTier("b") { _, _ -> StrategyOutcome.Completed("b") }
        val picker = ScriptedPicker(emptyList())
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)

        val outcome = startTierPipeline(listOf(head, a, b), TierSelector.Custom(picker), fake, policy = policy)
            .execute(input)

        assertEquals(0, picker.calls)
        assertEquals(0, fake.callCount)
        assertEquals(listOf(skipped, skipped, TraceCode.ROUTER_FALLBACK), outcome.trace.codes)
        assertNull(outcome.trace.selection)
        assertEquals(0, a.executions + b.executions)
        return outcome as CommandOutcome.Unhandled
    }

    @Test
    fun offlineOnlyWithNoOnDeviceTierLeavesNoModelTierAndNeverCallsThePicker() = runTest {
        NoNetworkGuard.during { assertTrue(noModelTierLeft(TierPolicy { offlineOnly = true }).cappedByPolicy) }
    }

    @Test
    fun anEmptyAllowedProviderSetLeavesNoModelTierAndNeverCallsThePicker() = runTest {
        NoNetworkGuard.during {
            assertTrue(noModelTierLeft(TierPolicy { allowedProviders = emptySet() }).cappedByPolicy)
        }
    }

    @Test
    fun aMaxTierAtTheHeadLeavesNoModelTierAndNeverCallsThePicker() = runTest {
        NoNetworkGuard.during {
            assertTrue(noModelTierLeft(TierPolicy { maxTier = StrategyId("grammar") }).cappedByPolicy)
        }
    }

    @Test
    fun aWholeLadderRefusalIsAPlainFailureWithNoRouterFallback() = runTest {
        NoNetworkGuard.during {
            val a = llmTier("a") { _, _ -> StrategyOutcome.Completed("a") }
            val b = llmTier("b") { _, _ -> StrategyOutcome.Completed("b") }
            val picker = ScriptedPicker(emptyList())
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(
                listOf(a, b),
                TierSelector.Custom(picker),
                fake,
                policy = TierPolicy { offlineOnly = true },
            ).execute(input)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(
                FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "offline_unavailable"),
                (outcome as CommandOutcome.Failed).reason,
            )
            assertEquals(0, picker.calls)
            assertEquals(0, fake.callCount)
            assertTrue(outcome.trace.codes.contains(TraceCode.OFFLINE_UNAVAILABLE))
            assertFalse(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            assertNull(outcome.trace.selection)
            assertEquals(0, a.executions + b.executions)
        }
    }

    @Test
    fun aHeadThatHandlesOrFailsEndsTheWalkWithNoRouterFallbackAndNoSelection() = runTest {
        NoNetworkGuard.during {
            val outcomes = listOf(
                StrategyOutcome.Completed("h"),
                StrategyOutcome.Failed(FailureReason.NotConfigured(ProviderId.ANTHROPIC)),
            )
            outcomes.forEach { headOutcome ->
                val head = zeroCallTier("grammar", headOutcome)
                val a = llmTier("a") { _, _ -> StrategyOutcome.Completed("a") }
                val picker = ScriptedPicker(emptyList())

                val outcome = startTierPipeline(
                    listOf(head, a),
                    TierSelector.Custom(picker),
                    FakeAiProvider(ProviderId.ANTHROPIC),
                ).execute(input)

                assertEquals(0, picker.calls)
                assertFalse(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
                assertNull(outcome.trace.selection)
                assertEquals(0, a.executions)
            }
        }
    }

    @Test
    fun aPickerWhoseProvidersThePolicyExcludesIsNotCalledAndTheFirstModelTierRuns() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker(emptyList())
            val selector = TierSelector.Custom(picker) {
                capabilities = StrategyCapabilities(setOf(ProviderId.ANTHROPIC))
            }
            val fake = FakeAiProvider(ProviderId.OPENAI)

            val outcome = startTierPipeline(
                listOf(head, single, agentic),
                selector,
                fake,
                policy = TierPolicy { allowedProviders = setOf(ProviderId.OPENAI) },
            ).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, picker.calls)
            assertEquals(0, fake.callCount)
            assertEquals(listOf(TraceCode.ROUTER_FALLBACK), outcome.trace.codes)
            assertNull(outcome.trace.selection)
            assertEquals(0, agentic.executions)
        }
    }

    @Test
    fun anOnDevicePickerRunsOfflineButTheBindTimeGateStillRefusesACloudModel() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val local = localModel()
            val refusals = mutableListOf<FailureReason?>()
            val picker = ScriptedPicker({ _, _, ctx ->
                // The app mapped its picker to ANTHROPIC, which an on-device declaration and offline-only both forbid.
                refusals.add(ctx.model().refusal)
                null
            })
            val selector = TierSelector.Custom(picker) {
                capabilities = StrategyCapabilities(setOf(ProviderId.ON_DEVICE))
            }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)

            val outcome = startTierPipeline(
                listOf(head, local),
                selector,
                fake,
                policy = TierPolicy { offlineOnly = true },
                onDevice = ready,
            ).execute(input)

            assertEquals(1, picker.calls)
            assertEquals(idsOf("local_model"), picker.eligibleSeen.single())
            assertEquals(1, refusals.size)
            assertNotNull(refusals.single())
            assertEquals(0, fake.callCount)
            val notAllowed = TraceCode.PROVIDER_NOT_ALLOWED
            assertEquals(listOf(notAllowed, TraceCode.ROUTER_FALLBACK), outcome.trace.codes)
            assertEquals("d", (outcome as CommandOutcome.Completed).reply)
            assertEquals("provider_not_allowed", notAllowed.value)
        }
    }

    @Test
    fun aCustomPickerIsStillCalledOnceWhenExactlyOneModelTierIsEligible() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("single") })

            val outcome = startTierPipeline(
                listOf(head, single),
                TierSelector.Custom(picker),
                FakeAiProvider(ProviderId.ANTHROPIC),
            ).execute(input)

            assertEquals(1, picker.calls)
            assertEquals(idsOf("single"), picker.eligibleSeen.single())
            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals("picked", outcome.trace.selection!!.outcome)
        }
    }
}
