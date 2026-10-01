package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** A retried call re-sends HTTP only: the app's change is applied and committed once. */
class AnthropicPipelineRetryTest {

    private val model = "claude-haiku-4-5"

    private val addItem = ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })

    private fun request(): ModelRequest =
        ModelRequest("You are a test system.", listOf(UserMessage("add milk")), listOf(addItem), 256)

    private fun toolAnswer(): MockResponse = MockResponse().setResponseCode(200).setBody(
        successBody(
            listOf(toolUseBlock("toolu_1", "add_item", buildJsonObject { put("item", "milk") })),
            "tool_use",
        ),
    )

    private fun overloaded(): MockResponse = MockResponse()
        .setResponseCode(529)
        .setBody(errorBody("overloaded_error", "Overloaded", "req_over_1"))

    private fun dropped(): MockResponse =
        MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

    private fun runScenario(first: MockResponse) = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(first)
            server.enqueue(toolAnswer())
            // An unexpected third request must be counted, not left hanging.
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val mutation = FakeMutation("add_item", StepResult("added", false, "tok", emptyMap()))
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(request()) as ModelResult.Success
                assertEquals(1, result.response.message.toolCalls.size)
                session.submit(ToolStep.Mutation(mutation))
                StrategyOutcome.Completed(null)
            }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(
                    AnthropicProvider {
                        baseUrl = server.url("/")
                        sleep = { waits.add(it) }
                    },
                )
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
                credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("add milk", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(2, server.requestCount)
            assertEquals(1, mutation.applyCount)
            assertEquals(1, sink.actions.size)
            assertEquals(1, outcome.trace.attempts.single().turns.size)
            assertEquals(listOf(500L), waits.toList())
        }
    }

    @Test(timeout = 30_000)
    fun anOverloadedAnswerIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(overloaded())
    }

    @Test(timeout = 30_000)
    fun aConnectionLostAfterTheRequestWasSentIsRetriedOnceAndTheChangeIsAppliedAndCommittedOnce() {
        runScenario(dropped())
    }
}
