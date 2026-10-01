package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
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
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** A key belongs to one provider: it is asked for, handed over and sent to that provider alone, on every path. */
class KeyIsolationTest {
    private val openAiKey = "sk-CANARY-OPENAI"
    private val anthropicKey = "sk-CANARY-ANTHROPIC"
    private val keyOf = mapOf(ProviderId.OPENAI to openAiKey, ProviderId.ANTHROPIC to anthropicKey)

    private val openAiFake = fake(ProviderId.OPENAI)
    private val anthropicFake = fake(ProviderId.ANTHROPIC)

    private fun fake(id: ProviderId, replies: Int = 2) =
        FakeAiProvider(id, List(replies) { { _ -> FakeAiProvider.reply(id.value, Usage.ZERO) } })

    private fun bothKeys() = ScriptedCredentialSource.keys(
        ProviderId.OPENAI to openAiKey,
        ProviderId.ANTHROPIC to anthropicKey,
    )

    private val callStep: StrategyStep = { input, session ->
        val request = ModelRequest("sys", listOf(UserMessage(input.transcript)), session.policy.maxTokensPerTurn)
        when (val result = session.model().complete(request)) {
            is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
            else -> StrategyOutcome.Completed("done")
        }
    }

    private val callThenEscalate: StrategyStep = { input, session ->
        val request = ModelRequest("sys", listOf(UserMessage(input.transcript)), session.policy.maxTokensPerTurn)
        when (val result = session.model().complete(request)) {
            is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
            else -> StrategyOutcome.Escalate(EscalationReason.NoToolCall())
        }
    }

    private fun pipelineOf(
        tiers: List<ScriptedStrategy>,
        selection: ProviderSelectionSource,
        credentials: CredentialSource,
    ): CommandPipeline = commandPipeline {
        tiers.forEach { tier(it) }
        provider(openAiFake)
        provider(anthropicFake)
        providerSelection = selection
        this.credentials = credentials
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

    /** Every call on every fake carries a credential for that fake's own provider, and never another provider's key. */
    private fun assertNoKeyCrossedProviders() {
        for (fake in listOf(openAiFake, anthropicFake)) {
            val foreign = keyOf.filterKeys { it != fake.id }.values
            for (call in fake.calls) {
                assertEquals(fake.id, call.credential?.provider)
                assertEquals(keyOf[fake.id], call.credential?.apiKey)
                assertTrue(call.credential?.apiKey !in foreign)
            }
        }
    }

    private fun CommandOutcome.codes(): List<String> = trace.codes.map { it.value }

    @Test
    fun aTwoTierLadderAsksEachKeyOnceForItsOwnProviderAndNeverCrossesThem() = runTest {
        NoNetworkGuard.during {
            val first = ScriptedStrategy(
                StrategyId("first"),
                StrategyCapabilities(setOf(ProviderId.OPENAI)),
                callThenEscalate,
            )
            val second = ScriptedStrategy(
                StrategyId("second"),
                StrategyCapabilities(setOf(ProviderId.ANTHROPIC)),
                callStep,
            )
            val selection = ProviderSelectionSource { request ->
                if (request.strategy == StrategyId("first")) {
                    ProviderSelection(ProviderId.OPENAI, "model-o")
                } else {
                    ProviderSelection(ProviderId.ANTHROPIC, "model-a")
                }
            }
            val credentials = bothKeys()

            val outcome = pipelineOf(listOf(first, second), selection, credentials).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(ProviderId.OPENAI, ProviderId.ANTHROPIC), credentials.requested)
            assertEquals(listOf("model-o"), openAiFake.calls.map { it.model })
            assertEquals(listOf("model-a"), anthropicFake.calls.map { it.model })
            assertEquals(openAiKey, openAiFake.calls.single().credential?.apiKey)
            assertEquals(anthropicKey, anthropicFake.calls.single().credential?.apiKey)
            assertNoKeyCrossedProviders()
        }
    }

    @Test
    fun aSourceThatFailsLoudlyForAnyOtherProviderIsNeverTrippedByARunThatSelectsOnlyOneProvider() = runTest {
        NoNetworkGuard.during {
            val asked = CopyOnWriteArrayList<ProviderId>()
            val strict = CredentialSource { provider ->
                asked.add(provider)
                if (provider != ProviderId.ANTHROPIC) throw AssertionError("asked for $provider")
                CredentialLookup.Present(Credential(provider, anthropicKey))
            }
            val tier = ScriptedStrategy(StrategyId("only"), StrategyCapabilities.ANY_PROVIDER, callStep)
            val selection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))

            val outcome = pipelineOf(listOf(tier), selection, strict).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(ProviderId.ANTHROPIC), asked.toList())
            assertEquals(0, openAiFake.callCount)
            assertNoKeyCrossedProviders()
        }
    }

    @Test
    fun aHostileSourceHandingAnotherProvidersKeyIsRefusedAndTheKeyReachesNoCall() = runTest {
        NoNetworkGuard.during {
            val hostile = CredentialSource { _ -> CredentialLookup.Present(Credential(ProviderId.OPENAI, openAiKey)) }
            val tier = ScriptedStrategy(StrategyId("only"), StrategyCapabilities.ANY_PROVIDER, callStep)
            val selection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))

            val outcome = pipelineOf(listOf(tier), selection, hostile).execute(CommandInput("hi"))

            assertEquals(FailureReason.NotConfigured(ProviderId.ANTHROPIC), (outcome as CommandOutcome.Failed).reason)
            assertTrue(outcome.codes().contains("credential_mismatch"))
            assertEquals(0, openAiFake.callCount)
            assertEquals(0, anthropicFake.callCount)
            assertNoKeyCrossedProviders()
        }
    }

    @Test
    fun theFallbackPathAsksForTheFallbackProvidersKeyAloneAndSendsOnlyThatKey() = runTest {
        NoNetworkGuard.during {
            val tier = ScriptedStrategy(
                StrategyId("only"),
                StrategyCapabilities(setOf(ProviderId.ON_DEVICE, ProviderId.ANTHROPIC)),
                callStep,
            )
            val selection = ScriptedSelectionSource.fixed(
                ProviderSelection(ProviderId.ON_DEVICE, "local-a", ProviderSelection(ProviderId.ANTHROPIC, "model-a")),
            )
            val credentials = bothKeys()

            val outcome = pipelineOf(listOf(tier), selection, credentials).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
            assertEquals(anthropicKey, anthropicFake.calls.single().credential?.apiKey)
            assertEquals(0, openAiFake.callCount)
            assertNoKeyCrossedProviders()
        }
    }

    @Test
    fun twoCommandsWhoseSelectionSwitchesProvidersCarryOnlyTheirOwnProvidersKey() = runTest {
        NoNetworkGuard.during {
            val tier = ScriptedStrategy(StrategyId("only"), StrategyCapabilities.ANY_PROVIDER, callStep, callStep)
            val selection = ScriptedSelectionSource(
                ProviderSelection(ProviderId.OPENAI, "model-o"),
                ProviderSelection(ProviderId.ANTHROPIC, "model-a"),
            )
            val credentials = bothKeys()
            val pipeline = pipelineOf(listOf(tier), selection, credentials)

            val first = pipeline.execute(CommandInput("one"))
            assertEquals(listOf(openAiKey), openAiFake.calls.map { it.credential?.apiKey })
            assertEquals(0, anthropicFake.callCount)
            val second = pipeline.execute(CommandInput("two"))

            assertTrue(first is CommandOutcome.Completed)
            assertTrue(second is CommandOutcome.Completed)
            assertEquals(listOf(ProviderId.OPENAI, ProviderId.ANTHROPIC), credentials.requested)
            assertEquals(listOf(openAiKey), openAiFake.calls.map { it.credential?.apiKey })
            assertEquals(listOf(anthropicKey), anthropicFake.calls.map { it.credential?.apiKey })
            assertNoKeyCrossedProviders()
        }
    }
}
