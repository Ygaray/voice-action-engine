package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeClock
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The router and the handle it returns, driven directly against a real [RunRecorder] and scripted fakes. */
class ModelRouterTest {
    private val clock = FakeClock()
    private val listener = RecordingEventListener()
    private val strategy = StrategyId("a")
    private val key = "sk-canary-key-1"
    private val request = ModelRequest("sys", listOf(UserMessage("hi")), TierPolicy.DEFAULT.maxTokensPerTurn)

    private fun recorder(): RunRecorder = RunRecorder("run-1", null, null, 0, clock, listener)

    private fun routerOf(
        providers: List<AiProvider>,
        selection: ProviderSelectionSource? = anthropicSelection(),
        credentials: CredentialSource? = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key),
        probe: suspend () -> Boolean = { false },
        table: ModelCapabilityTable = tableOf(providers),
    ): ModelRouter = ModelRouter(providers.associateBy { it.id }, selection, credentials, table, clock, probe)

    private fun anthropicSelection(): ProviderSelectionSource =
        ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))

    private fun tableOf(providers: List<AiProvider>): ModelCapabilityTable {
        val byId = providers.associateBy { it.id }
        return ModelCapabilityTable({ provider, model -> byId.getValue(provider).capabilities(model) }, emptyMap())
    }

    private fun textOf(result: ModelResult): String =
        (result as ModelResult.Success).response.message.parts.filterIsInstance<AssistantPart.Text>().single().text

    @Test
    fun bindResolvesTheModelAndACompleteCallReachesTheProviderAndTheTrace() = runTest {
        NoNetworkGuard.during {
            val usage = Usage(20, 0, 0, 4)
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                ModelCapabilities.UNKNOWN,
                { _ ->
                    clock.advanceBy(7)
                    FakeAiProvider.reply("ok", usage)
                },
            )
            val selection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val recorder = recorder()
            recorder.tierStarted(strategy)

            val handle = routerOf(listOf(fake), selection, credentials)
                .bind(strategy, setOf(ProviderId.ANTHROPIC), TierPolicy.DEFAULT, recorder)
            val result = handle.complete(request)

            assertEquals("ok", textOf(result))
            assertEquals(ProviderId.ANTHROPIC, handle.provider)
            assertEquals("model-a", handle.model)
            assertNull(handle.refusal)
            assertNull(handle.fallbackFrom)
            val call = fake.calls.single()
            assertEquals("model-a", call.model)
            assertEquals(ProviderId.ANTHROPIC, call.credential?.provider)
            assertEquals(key, call.credential?.apiKey)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, call.request.maxTokens)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
            assertEquals(strategy, selection.requests.single().strategy)

            recorder.tierFinished(strategy, "completed", null, null, null)
            val turn = recorder.snapshot().attempts.single().turns.single()
            assertEquals(ProviderId.ANTHROPIC, turn.provider)
            assertEquals("model-a", turn.model)
            assertEquals("end_turn", turn.stopReason)
            assertEquals(usage.total, turn.usage.total)
            assertEquals(7L, turn.latencyMillis)
            assertEquals(1, listener.events.filterIsInstance<PipelineEvent.ProviderCall>().size)
            assertEquals(usage.total, recorder.tokensUsed)
        }
    }

    @Test
    fun theHandlePrintsIdsOnlyAndNeverAKeyOrContent() = runTest {
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("ok", Usage.ZERO))
        val handle: BoundModel = routerOf(listOf(fake))
            .bind(strategy, setOf(ProviderId.ANTHROPIC), TierPolicy.DEFAULT, recorder())

        val printed = handle.toString()

        assertTrue(printed.contains("anthropic"))
        assertTrue(printed.contains("model-a"))
        assertFalse(printed.contains(key))
        assertFalse(printed.contains("sys"))
        assertFalse(printed.contains("hi"))
    }
}
