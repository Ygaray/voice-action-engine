package io.github.ygaray.voiceactionengine.core

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

/** The one on-device gate, read by the pre-check and by the router, and the declared fallback behind it. */
class OnDeviceGateTest {
    private val key = "sk-canary-anthropic"
    private val both = StrategyCapabilities(setOf(ProviderId.ON_DEVICE, ProviderId.ANTHROPIC))
    private val withFallback = ProviderSelection(
        ProviderId.ON_DEVICE,
        "local-a",
        ProviderSelection(ProviderId.ANTHROPIC, "model-a"),
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
        selection: ProviderSelection?,
        credentials: CredentialSource,
        policy: TierPolicy = TierPolicy.DEFAULT,
        gateOverride: OnDeviceCapability? = null,
    ) = commandPipeline {
        tier(tier)
        providers.forEach { provider(it) }
        providerSelection = ScriptedSelectionSource.fixed(selection)
        this.credentials = credentials
        this.policy = TierPolicySource.fixed(policy)
        gateOverride?.let { onDevice = it }
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

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

            val outcome = pipelineOf(tier(both, seen), listOf(cloud), withFallback, credentials)
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

            val outcome = pipelineOf(tier(both), listOf(local, cloud), withFallback, credentials, gateOverride = gate)
                .execute(CommandInput("hi"))

            assertEquals("local", (outcome as CommandOutcome.Completed).reply)
            assertNull(local.calls.single().credential)
            assertEquals(0, cloud.callCount)
            assertTrue(credentials.requested.isEmpty())
            assertNull(outcome.trace.attempts.single().fallbackFrom)
            assertFalse(outcome.codes().contains("provider_fallback"))
        }
    }
}
