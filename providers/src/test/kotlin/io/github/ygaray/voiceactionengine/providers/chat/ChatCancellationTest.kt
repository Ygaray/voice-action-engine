package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Real sockets and real time: cancellation must reach the HTTP call, which only a live call can show. */
class ChatCancellationTest {

    /**
     * A server that reads every request and never answers, so the call stays in flight until the client closes it. The
     * server thread ends when the socket closes, which keeps the server's shutdown instant.
     */
    private class NeverAnswers : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
    }

    private val sink = RecordingCommitSink()

    private fun request(): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage("add two eggs")),
        listOf(logFoodTool()),
        ToolChoice.Required("log_food"),
        1024,
        CacheDirective(true),
    )

    private fun pipeline(
        server: MockWebServer,
        app: OkHttpClient,
        policy: TierPolicy,
        openRouter: Boolean,
        cancellations: MutableList<Throwable>,
    ): CommandPipeline {
        val vendor = if (openRouter) ChatVendor.OPENROUTER else ChatVendor.OPENAI
        val model = if (openRouter) "openai/gpt-5.4-mini" else "gpt-5.4-mini"
        val step: StrategyStep = { _, session ->
            val result = try {
                session.model().complete(request())
            } catch (e: CancellationException) {
                cancellations.add(e)
                throw e
            }
            if (result is ModelResult.Failure) {
                StrategyOutcome.Failed(result.reason, result.details)
            } else {
                StrategyOutcome.Completed(null)
            }
        }
        val configure: ChatCompletionsProvider.Builder.() -> Unit = {
            httpClient = app
            baseUrl = server.url("/")
        }
        val chat = if (openRouter) {
            ChatCompletionsProvider.openRouter(configure)
        } else {
            ChatCompletionsProvider.openAi(configure)
        }
        return commandPipeline {
            tier(ScriptedStrategy(StrategyId("probe"), step))
            provider(chat)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
            credentials = ScriptedCredentialSource.keys(vendor.providerId to "sk-test-key")
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

    private fun cancelWhileInFlight(openRouter: Boolean) = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = NeverAnswers()
            server.start()
            val app = OkHttpClient()
            val cancellations = CopyOnWriteArrayList<Throwable>()
            val command = pipeline(server, app, TierPolicy.DEFAULT, openRouter, cancellations)

            val job = launch(Dispatchers.Default) { command.execute(CommandInput("add two eggs", "en", null)) }
            awaitRequestAtServer(server)
            val started = System.nanoTime()
            job.cancelAndJoin()
            val joinedWithinMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            assertTrue("cancelAndJoin took $joinedWithinMillis ms", joinedWithinMillis < IDLE_WITHIN_SECONDS * MILLIS)
            // The strategy saw the cancellation, and no retry was sent after it.
            assertEquals(1, cancellations.size)
            assertEquals(1, server.requestCount)
            awaitIdle(app)
            assertTrue(sink.actions.isEmpty())
        }
    }

    private fun deadlineDuringTheCall(openRouter: Boolean) = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = NeverAnswers()
            server.start()
            val app = OkHttpClient()
            val policy = TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS }
            val command = pipeline(server, app, policy, openRouter, CopyOnWriteArrayList())

            val started = System.nanoTime()
            val outcome = command.execute(CommandInput("add two eggs", "en", null))
            val tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            val failed = outcome as CommandOutcome.Failed
            assertEquals("timeout", failed.reason.code)
            assertTrue("the deadline took $tookMillis ms", tookMillis < DEADLINE_WITHIN_SECONDS * MILLIS)
            assertTrue(failed.trace.codes.map { it.value }.contains("engine_timeout"))
            awaitIdle(app)
            assertTrue(sink.actions.isEmpty())
        }
    }

    @Test(timeout = 30_000)
    fun cancellingTheCommandWhileTheCallIsInFlightCancelsTheCallAndCommitsNothing() {
        cancelWhileInFlight(openRouter = false)
    }

    @Test(timeout = 30_000)
    fun cancellingTheCommandWhileTheCallIsInFlightCancelsTheCallOnOpenRouterToo() {
        cancelWhileInFlight(openRouter = true)
    }

    @Test(timeout = 30_000)
    fun anEngineDeadlineDuringTheCallIsATimeoutAndCancelsTheCall() {
        deadlineDuringTheCall(openRouter = false)
    }

    @Test(timeout = 30_000)
    fun anEngineDeadlineDuringTheCallIsATimeoutAndCancelsTheCallOnOpenRouterToo() {
        deadlineDuringTheCall(openRouter = true)
    }

    private companion object {
        // Generous on purpose: these are real-time bounds that must not flake on a loaded CI machine, and each test
        // also carries its own 30 s timeout. The structural assertions (one cancellation, one request) are the check.
        const val IDLE_WITHIN_SECONDS = 10L
        const val DEADLINE_WITHIN_SECONDS = 15L
        const val DEADLINE_MILLIS = 500L
        const val POLL_MILLIS = 25L
        const val MILLIS = 1_000L
    }
}
