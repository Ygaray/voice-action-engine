package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnthropicErrorMapTest {

    private val canary = "CANARY-ECHO"

    /** A server that answers every request the same way, so a test never depends on how many calls were made. */
    private class SameAnswer(private val answer: () -> MockResponse) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = answer()
    }

    private val model = "claude-haiku-4-5"

    private fun request(): ModelRequest = ModelRequest(
        "You are a test system.",
        listOf(UserMessage("add milk")),
        listOf(ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })),
        256,
    )

    @Test
    fun aProviderErrorReachesTheAppAsATypedFailureWithStatusTypeAndRequestIdOnly() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer {
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("request-id", "req_err_1")
                    .setBody(errorBody("authentication_error", "invalid x-api-key $canary", "req_body_1"))
            }
            server.start()
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(request())
                if (result is ModelResult.Failure) {
                    StrategyOutcome.Failed(result.reason, result.details)
                } else {
                    StrategyOutcome.Completed(null)
                }
            }
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(AnthropicProvider { baseUrl = server.url("/") })
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
                credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("add milk", "en", null)) as CommandOutcome.Failed

            assertEquals("auth", outcome.reason.code)
            assertEquals(FailureDetails(401, "authentication_error", "req_err_1"), outcome.details)
            assertFalse(outcome.toString().contains(canary))
            assertFalse(outcome.trace.toString().contains(canary))
            assertFalse(outcome.details.toString().contains(canary))
        }
    }
}
