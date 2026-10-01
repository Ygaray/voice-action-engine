package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The one on-device gate, read by the pre-check and by the router, and the declared fallback behind it. */
class OnDeviceGateTest {
    private val key = "sk-canary-anthropic"
    private val both = StrategyCapabilities(setOf(ProviderId.ON_DEVICE, ProviderId.ANTHROPIC))
    private val onlyOnDevice = StrategyCapabilities(setOf(ProviderId.ON_DEVICE))
    private val withFallback = ProviderSelection(
        ProviderId.ON_DEVICE,
        "local-a",
        ProviderSelection(ProviderId.ANTHROPIC, "model-a"),
    )

    private val onDeviceUnavailable = FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "on_device_unavailable")
    private val family = setOf(
        "on_device_unavailable", "fallback_refused", "provider_fallback", "credential_missing",
        "on_device_probe_error", "offline_unavailable",
    )

    /** What the strategy saw on its handle, so a test can assert on the bound model and not only on the outcome. */
    private class Seen {
        var provider: ProviderId? = null
        var model: String? = null
        var fallbackFrom: ProviderId? = null
    }

    private fun answer(result: ModelResult): String =
        (result as ModelResult.Success).response.message.parts.filterIsInstance<AssistantPart.Text>().single().text

    private fun tier(capabilities: StrategyCapabilities, seen: Seen = Seen()) =
        ScriptedStrategy(
            StrategyId("t"),
            capabilities,
            { input, session ->
                val handle: BoundModel = session.model()
                seen.provider = handle.provider
                seen.model = handle.model
                seen.fallbackFrom = handle.fallbackFrom
                val messages = listOf(UserMessage(input.transcript))
                val request = ModelRequest("sys", messages, session.policy.maxTokensPerTurn)
                when (val result = handle.complete(request)) {
                    is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
                    else -> StrategyOutcome.Completed(answer(result))
                }
            },
        )

    private fun anthropicFake(replies: Int = 1) =
        FakeAiProvider(ProviderId.ANTHROPIC, List(replies) { { _ -> FakeAiProvider.reply("cloud", Usage.ZERO) } })

    private fun onDeviceFake() = FakeAiProvider(
        ProviderId.ON_DEVICE,
        List(1) { { _ -> FakeAiProvider.reply("local", Usage.ZERO) } },
        requiresCredential = false,
    )

    private fun pipelineOf(
        tier: ScriptedStrategy,
        providers: List<FakeAiProvider>,
        selection: ScriptedSelectionSource,
        credentials: CredentialSource,
        policy: TierPolicy = TierPolicy.DEFAULT,
        gateOverride: OnDeviceCapability? = null,
    ) = commandPipeline {
        tier(tier)
        providers.forEach { provider(it) }
        providerSelection = selection
        this.credentials = credentials
        this.policy = TierPolicySource.fixed(policy)
        gateOverride?.let { onDevice = it }
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

    private fun fixed(selection: ProviderSelection?) = ScriptedSelectionSource.fixed(selection)

    /** A gate that plays [answers] one per read (the last repeats) and counts its reads. */
    private class SequencedGate(private vararg val answers: () -> OnDeviceAvailability) : OnDeviceCapability {
        private val count = AtomicInteger()
        val reads: Int get() = count.get()

        override suspend fun availability(): OnDeviceAvailability {
            val index = count.getAndIncrement()
            return answers[minOf(index, answers.size - 1)]()
        }
    }

    private fun available(): () -> OnDeviceAvailability = { OnDeviceAvailability.Available() }

    private fun unavailable(): () -> OnDeviceAvailability = { OnDeviceAvailability.Unavailable("test_unavailable") }

    private fun CommandOutcome.family(): List<String> = codes().filter { it in family }

    private fun CommandOutcome.codes(): List<String> = trace.codes.map { it.value }

    @Test
    fun theDefaultGateReportsNotImplementedBecauseVersionOneShipsNoOnDeviceCode() = runTest {
        val status = PipelineBuilder().onDevice.availability()
        assertEquals(OnDeviceAvailability.Unavailable("not_implemented"), status)
    }

    @Test
    fun anUnavailableOnDeviceSelectionIsServedByTheDeclaredFallbackWithItsOwnKey() = runTest {
        NoNetworkGuard.during {
            val seen = Seen()
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)

            val outcome = pipelineOf(tier(both, seen), listOf(cloud), fixed(withFallback), credentials)
                .execute(CommandInput("hi"))

            assertEquals("cloud", (outcome as CommandOutcome.Completed).reply)
            val call = cloud.calls.single()
            assertEquals("model-a", call.model)
            assertEquals(ProviderId.ANTHROPIC, call.credential?.provider)
            assertEquals(key, call.credential?.apiKey)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
            assertEquals(ProviderId.ON_DEVICE, outcome.trace.attempts.single().fallbackFrom)
            assertTrue(outcome.codes().contains("provider_fallback"))
            assertEquals(ProviderId.ANTHROPIC, seen.provider)
            assertEquals("model-a", seen.model)
            assertEquals(ProviderId.ON_DEVICE, seen.fallbackFrom)
        }
    }

    @Test
    fun anAvailableGateWithAKeylessOnDeviceProviderCallsItWithNoCredentialAndNoFallback() = runTest {
        NoNetworkGuard.during {
            val local = onDeviceFake()
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val gate = OnDeviceCapability { OnDeviceAvailability.Available() }

            val providers = listOf(local, cloud)

            val outcome = pipelineOf(tier(both), providers, fixed(withFallback), credentials, gateOverride = gate)
                .execute(CommandInput("hi"))

            assertEquals("local", (outcome as CommandOutcome.Completed).reply)
            assertNull(local.calls.single().credential)
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
            assertNull(outcome.trace.attempts.single().fallbackFrom)
            assertFalse(outcome.codes().contains("provider_fallback"))
        }
    }

    // ---- Task 2: forbidden and missing fallbacks, offline, probe faults, disagreement, counting ----

    private fun CommandOutcome.assertFailedWith(expected: FailureReason) =
        assertEquals(expected, (this as CommandOutcome.Failed).reason)

    @Test
    fun noFallbackAndTheDefaultGateFailsLoudlyWithZeroCallsAndNoKeyAsked() = runTest {
        NoNetworkGuard.during {
            val local = onDeviceFake()
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val selection = fixed(ProviderSelection(ProviderId.ON_DEVICE, "local-a"))

            val outcome = pipelineOf(tier(both), listOf(local, cloud), selection, credentials)
                .execute(CommandInput("hi"))

            outcome.assertFailedWith(onDeviceUnavailable)
            assertEquals(listOf("on_device_unavailable"), outcome.family())
            assertEquals(0, local.callCount)
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
        }
    }

    @Test
    fun aFallbackThePolicyForbidsIsRefusedWithZeroCallsAndNoKeyAsked() = runTest {
        NoNetworkGuard.during {
            val local = onDeviceFake()
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val policy = TierPolicy { allowedProviders = setOf(ProviderId.ON_DEVICE) }

            val outcome = pipelineOf(tier(both), listOf(local, cloud), fixed(withFallback), credentials, policy)
                .execute(CommandInput("hi"))

            outcome.assertFailedWith(onDeviceUnavailable)
            assertEquals(listOf("on_device_unavailable", "fallback_refused"), outcome.family())
            assertEquals(0, local.callCount)
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
        }
    }

    @Test
    fun aGateThatFlipsFromAvailableToUnavailableLetsTheRouterReadGovernAndRefusesAnUndeclaredFallback() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val gate = SequencedGate(available(), unavailable())
            val strategy = tier(onlyOnDevice)

            val outcome = pipelineOf(strategy, listOf(cloud), fixed(withFallback), credentials, gateOverride = gate)
                .execute(CommandInput("hi"))

            outcome.assertFailedWith(onDeviceUnavailable)
            assertEquals(1, strategy.executions)
            assertEquals(2, gate.reads)
            assertEquals(listOf("on_device_unavailable", "fallback_refused"), outcome.family())
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
        }
    }

    @Test
    fun offlineOnlyWithAFlippingGateRefusesTheCloudFallbackAndAsksNoCloudKey() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val gate = SequencedGate(available(), unavailable())
            val policy = TierPolicy { offlineOnly = true }

            val strategy = tier(onlyOnDevice)

            val outcome = pipelineOf(strategy, listOf(cloud), fixed(withFallback), credentials, policy, gate)
                .execute(CommandInput("hi"))

            outcome.assertFailedWith(onDeviceUnavailable)
            assertEquals(listOf("on_device_unavailable", "fallback_refused"), outcome.family())
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
        }
    }

    @Test
    fun offlineOnlyWithATierThatAlsoDeclaresTheCloudIsSkippedByThePreCheck() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val selection = fixed(withFallback)
            val strategy = tier(both)
            val policy = TierPolicy { offlineOnly = true }

            val outcome = pipelineOf(strategy, listOf(cloud), selection, credentials, policy)
                .execute(CommandInput("hi"))

            outcome.assertFailedWith(FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "offline_unavailable"))
            assertEquals(0, strategy.executions)
            assertEquals(0, selection.calls)
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
        }
    }

    @Test
    fun anOnDeviceOnlyTierWithTheDefaultGateFailsBeforeTheStrategyRunsAndNeverAsksTheSelection() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val selection = fixed(withFallback)
            val strategy = tier(onlyOnDevice)

            val outcome = pipelineOf(strategy, listOf(cloud), selection, credentials).execute(CommandInput("hi"))

            outcome.assertFailedWith(onDeviceUnavailable)
            assertEquals(0, strategy.executions)
            assertEquals(0, selection.calls)
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
        }
    }

    @Test
    fun aGateWhoseSecondReadThrowsRecordsTheProbeErrorAndUsesTheDeclaredFallback() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val gate = SequencedGate(available(), { error("device runtime broke") })

            val outcome = pipelineOf(tier(both), listOf(cloud), fixed(withFallback), credentials, gateOverride = gate)
                .execute(CommandInput("hi"))

            assertEquals("cloud", (outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf("on_device_probe_error", "provider_fallback"), outcome.family())
            assertEquals(ProviderId.ON_DEVICE, outcome.trace.attempts.single().fallbackFrom)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
        }
    }

    @Test
    fun aCountingGateIsReadOncePerCheckByThePreCheckAndTheRouter() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val gate = SequencedGate(unavailable())

            val outcome = pipelineOf(tier(both), listOf(cloud), fixed(withFallback), credentials, gateOverride = gate)
                .execute(CommandInput("hi"))

            assertEquals("cloud", (outcome as CommandOutcome.Completed).reply)
            assertEquals(2, gate.reads)
        }
    }

    @Test
    fun downloadableAndDownloadingEachCountAsUnavailableSoTheFallbackIsUsed() = runTest {
        NoNetworkGuard.during {
            val statuses = listOf<() -> OnDeviceAvailability>(
                { OnDeviceAvailability.Downloadable() },
                { OnDeviceAvailability.Downloading() },
            )
            for (status in statuses) {
                val local = onDeviceFake()
                val cloud = anthropicFake()
                val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)

                val outcome = pipelineOf(
                    tier(both),
                    listOf(local, cloud),
                    fixed(withFallback),
                    credentials,
                    gateOverride = SequencedGate(status),
                ).execute(CommandInput("hi"))

                assertEquals("cloud", (outcome as CommandOutcome.Completed).reply)
                assertEquals(ProviderId.ON_DEVICE, outcome.trace.attempts.single().fallbackFrom)
                assertEquals(0, local.callCount)
                assertEquals(1, cloud.callCount)
            }
        }
    }

    @Test
    fun aPermittedFallbackWhoseProviderHasNoKeyFailsNotConfiguredForThatProviderWithZeroCalls() = runTest {
        NoNetworkGuard.during {
            val cloud = anthropicFake()
            val credentials = ScriptedCredentialSource(emptyMap())

            val outcome = pipelineOf(tier(both), listOf(cloud), fixed(withFallback), credentials)
                .execute(CommandInput("hi"))

            outcome.assertFailedWith(FailureReason.NotConfigured(ProviderId.ANTHROPIC))
            // A fallback that never bound is not recorded as one; the refusal carries its own terminal code.
            assertEquals(listOf("credential_missing"), outcome.family())
            assertEquals(0, cloud.callCount)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
        }
    }
}
