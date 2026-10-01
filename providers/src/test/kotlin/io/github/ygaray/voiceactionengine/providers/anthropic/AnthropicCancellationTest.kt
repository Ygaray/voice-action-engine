package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Real sockets and real time: cancellation must reach the HTTP call, which only a live call can show. */
class AnthropicCancellationTest {

    /**
     * A server that reads every request and never answers, so the call stays in flight until the client closes it. The
     * server thread ends when the socket closes, which keeps the server's shutdown instant.
     */
    private class NeverAnswers : okhttp3.mockwebserver.Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
    }

    private val model = "claude-haiku-4-5"
    private val sink = RecordingCommitSink()

    private fun request(): ModelRequest = ModelRequest(
        "You are a test system.",
        listOf(UserMessage("add milk")),
        listOf(ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })),
        256,
    )

    private fun pipeline(server: MockWebServer, app: OkHttpClient, policy: TierPolicy): CommandPipeline {
        val step: StrategyStep = { _, session ->
            val result = session.model().complete(request())
            if (result is ModelResult.Failure) {
                StrategyOutcome.Failed(result.reason, result.details)
            } else {
                StrategyOutcome.Completed(null)
            }
        }
        return commandPipeline {
            tier(ScriptedStrategy(StrategyId("probe"), step))
            provider(
                AnthropicProvider {
                    httpClient = app
                    baseUrl = server.url("/")
                },
            )
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
            gate = ScriptedGate.admitAll()
            commitSink = sink
            this.policy = TierPolicySource.fixed(policy)
        }
    }

    private suspend fun awaitRequestAtServer(server: MockWebServer) {
        val seen: RecordedRequest? = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
        assertNotNull("the request never reached the server", seen)
    }

    private suspend fun awaitIdle(app: OkHttpClient) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(IDLE_WITHIN_SECONDS)
        while (app.dispatcher.runningCallsCount() > 0 && System.nanoTime() < deadline) delay(POLL_MILLIS)
        assertEquals("the HTTP call is still running", 0, app.dispatcher.runningCallsCount())
    }

    @Test(timeout = 30_000)
    fun cancellingTheCommandWhileTheCallIsInFlightCancelsTheCallAndCommitsNothing() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = NeverAnswers()
            server.start()
            val app = OkHttpClient()
            val command = pipeline(server, app, TierPolicy.DEFAULT)

            val job = launch(Dispatchers.Default) { command.execute(CommandInput("add milk", "en", null)) }
            awaitRequestAtServer(server)
            val started = System.nanoTime()
            job.cancelAndJoin()
            val joinedWithinMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            assertTrue("cancelAndJoin took $joinedWithinMillis ms", joinedWithinMillis < IDLE_WITHIN_SECONDS * MILLIS)
            awaitIdle(app)
            assertTrue(sink.actions.isEmpty())
        }
    }

    @Test(timeout = 30_000)
    fun anEngineDeadlineDuringTheCallIsATimeoutAndCancelsTheCall() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = NeverAnswers()
            server.start()
            val app = OkHttpClient()
            val command = pipeline(server, app, TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS })

            val started = System.nanoTime()
            val outcome = command.execute(CommandInput("add milk", "en", null))
            val tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            val failed = outcome as CommandOutcome.Failed
            assertEquals("timeout", failed.reason.code)
            assertTrue("the deadline took $tookMillis ms", tookMillis < DEADLINE_WITHIN_SECONDS * MILLIS)
            assertTrue(failed.trace.codes.map { it.value }.contains("engine_timeout"))
            awaitIdle(app)
            assertTrue(sink.actions.isEmpty())
        }
    }

    private companion object {
        const val IDLE_WITHIN_SECONDS = 2L
        const val DEADLINE_WITHIN_SECONDS = 5L
        const val DEADLINE_MILLIS = 500L
        const val POLL_MILLIS = 25L
        const val MILLIS = 1_000L
    }
}
