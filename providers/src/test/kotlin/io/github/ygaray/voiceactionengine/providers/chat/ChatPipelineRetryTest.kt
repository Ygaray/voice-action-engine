package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** A retried call re-sends HTTP only: the app's change is applied and committed once, on both Chat vendors. */
class ChatPipelineRetryTest {

    private val toolArguments =
        """{"items":[{"name":"egg","quantity":2,"unit":null,"confidence":0.9}],"target_date":null,"source":null}"""

    private fun request(): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage("add two eggs")),
        listOf(logFoodTool()),
        ToolChoice.Required("log_food"),
        1024,
        CacheDirective(true),
    )

    private fun toolAnswer(): MockResponse = MockResponse().setResponseCode(200).setBody(
        chatBody(
            chatMessage(null, listOf(chatToolCall("call_1", "log_food", toolArguments))),
            "tool_calls",
            chatUsage(1920, 55, cached = 1800),
        ),
    )

    private fun overloaded(): MockResponse = MockResponse()
        .setResponseCode(529)
        .setBody(openAiErrorBody("server_error", "overloaded", "Overloaded"))

    private fun dropped(): MockResponse =
        MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

    private fun envelope503(): MockResponse =
        MockResponse().setResponseCode(200).setBody(chatErrorEnvelope(503, "upstream unavailable"))

    private fun runScenario(vendor: ChatVendor, first: MockResponse) = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(first)
            server.enqueue(toolAnswer())
            // An unexpected third request must be counted, not left hanging.
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val configure: ChatCompletionsProvider.Builder.() -> Unit = {
                baseUrl = server.url("/")
                sleep = { waits.add(it) }
            }
            val (chat: AiProvider, model) = when (vendor) {
                ChatVendor.OPENAI -> ChatCompletionsProvider.openAi(configure) to "gpt-5.4-mini"
                else -> ChatCompletionsProvider.openRouter(configure) to "openai/gpt-5.4-mini"
            }
            val mutation = FakeMutation("log_food", StepResult("logged", false, "tok", emptyMap()))
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(request()) as ModelResult.Success
                assertEquals(1, result.response.message.toolCalls.size)
                session.submit(ToolStep.Mutation(mutation))
                StrategyOutcome.Completed(null)
            }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(chat)
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
                credentials = ScriptedCredentialSource.keys(vendor.providerId to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("add two eggs", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(2, server.requestCount)
            assertEquals(1, mutation.applyCount)
            assertEquals(1, sink.actions.size)
            assertEquals(1, outcome.trace.attempts.single().turns.size)
            assertEquals(listOf(500L), waits.toList())
        }
    }

    @Test(timeout = 30_000)
    fun anOverloadedAnswerOnOpenAiIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(ChatVendor.OPENAI, overloaded())
    }

    @Test(timeout = 30_000)
    fun anOverloadedAnswerOnOpenRouterIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(ChatVendor.OPENROUTER, overloaded())
    }

    @Test(timeout = 30_000)
    fun aConnectionLostAfterTheRequestOnOpenAiIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(ChatVendor.OPENAI, dropped())
    }

    @Test(timeout = 30_000)
    fun aConnectionLostAfterTheRequestOnOpenRouterIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(ChatVendor.OPENROUTER, dropped())
    }

    @Test(timeout = 30_000)
    fun aTransientEnvelopeInsideA200OnOpenRouterIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(ChatVendor.OPENROUTER, envelope503())
    }
}
