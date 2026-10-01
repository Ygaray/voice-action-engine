package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Real sockets and real time: the timeouts are OkHttp's own, so a virtual clock would prove nothing. */
class ChatTimeoutTest {

    /** A server that answers every request the same way, so a test never depends on how many calls were made. */
    private class SameAnswer(private val answer: () -> MockResponse) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = answer()
    }

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

    private fun call(openRouter: Boolean): ProviderRequest = if (openRouter) {
        chatCall(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", request())
    } else {
        chatCall(ChatVendor.OPENAI, "gpt-5.4-mini", request())
    }

    private fun provider(openRouter: Boolean, configure: ChatCompletionsProvider.Builder.() -> Unit) =
        if (openRouter) ChatCompletionsProvider.openRouter(configure) else ChatCompletionsProvider.openAi(configure)

    private fun assertReasonOnly(result: ModelResult, expected: FailureReason) {
        val failure = result as ModelResult.Failure
        assertEquals(expected.code, failure.reason.code)
        assertNull(failure.details)
        // Equal to the reason-only form: no exception class or message was copied into the failure.
        assertEquals(ModelResult.Failure(expected).toString(), failure.toString())
        assertFalse(failure.toString().contains("Exception"))
    }

    private fun headersDelayedPastTheCallTimeout(openRouter: Boolean) = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer {
                MockResponse().setResponseCode(200).setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS)
            }
            server.start()
            val provider = provider(openRouter) {
                baseUrl = server.url("/")
                callTimeoutMillis = 300
                readTimeoutMillis = 10_000
                sleep = { }
            }

            assertReasonOnly(provider.complete(call(openRouter)), FailureReason.Timeout())
            // A timeout may clear on its own, so it gets the one retry and no more.
            assertEquals(2, server.requestCount)
        }
    }

    private fun silenceBeyondTheReadTimeout(openRouter: Boolean) = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
            server.start()
            val provider = provider(openRouter) {
                baseUrl = server.url("/")
                callTimeoutMillis = 10_000
                readTimeoutMillis = 300
                sleep = { }
            }

            assertReasonOnly(provider.complete(call(openRouter)), FailureReason.Timeout())
            assertEquals(2, server.requestCount)
        }
    }

    @Test(timeout = 30_000)
    fun aServerThatDelaysItsHeadersPastTheCallTimeoutIsATimeoutAfterTheSingleRetry() {
        headersDelayedPastTheCallTimeout(openRouter = false)
    }

    @Test(timeout = 30_000)
    fun aServerThatDelaysItsHeadersPastTheCallTimeoutIsATimeoutOnOpenRouterToo() {
        headersDelayedPastTheCallTimeout(openRouter = true)
    }

    @Test(timeout = 30_000)
    fun aServerThatStopsSendingPastTheReadTimeoutIsATimeoutAfterTheSingleRetry() {
        silenceBeyondTheReadTimeout(openRouter = false)
    }

    @Test(timeout = 30_000)
    fun aServerThatStopsSendingPastTheReadTimeoutIsATimeoutOnOpenRouterToo() {
        silenceBeyondTheReadTimeout(openRouter = true)
    }

    @Test(timeout = 30_000)
    fun aRefusedConnectionIsANetworkFailure() = runBlocking {
        val closed: HttpUrl = MockWebServer().use { server ->
            server.start()
            server.url("/")
        }
        for (openRouter in listOf(false, true)) {
            val provider = provider(openRouter) {
                baseUrl = closed
                sleep = { }
            }

            assertReasonOnly(provider.complete(call(openRouter)), FailureReason.Network())
        }
    }

    private fun droppedBeforeAnyAnswer(policy: SocketPolicy, openRouter: Boolean) = runBlocking {
        MockWebServer().use { server ->
            // The default queue, not a custom dispatcher: only the queue's peek() lets AT_START act before a read.
            repeat(2) { server.enqueue(MockResponse().setResponseCode(200).setSocketPolicy(policy)) }
            server.start()
            val provider = provider(openRouter) {
                baseUrl = server.url("/")
                sleep = { }
            }

            assertReasonOnly(provider.complete(call(openRouter)), FailureReason.Network())
            assertEquals(2, server.requestCount)
        }
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedAfterTheRequestIsANetworkFailure() {
        droppedBeforeAnyAnswer(SocketPolicy.DISCONNECT_AFTER_REQUEST, openRouter = false)
        droppedBeforeAnyAnswer(SocketPolicy.DISCONNECT_AFTER_REQUEST, openRouter = true)
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedAtTheStartIsANetworkFailure() {
        droppedBeforeAnyAnswer(SocketPolicy.DISCONNECT_AT_START, openRouter = false)
        droppedBeforeAnyAnswer(SocketPolicy.DISCONNECT_AT_START, openRouter = true)
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedMidAnswerIsANetworkFailureNotAMalformedOne() = runBlocking {
        for (openRouter in listOf(false, true)) {
            MockWebServer().use { server ->
                // The status line and headers arrive (announcing the whole body); the connection then dies partway
                // through the body, so the failure happens while the answer is being read, not while it is awaited.
                server.dispatcher = SameAnswer {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            chatBody(
                                chatMessage(null, listOf(chatToolCall("call_1", "log_food", toolArguments))),
                                "tool_calls",
                                chatUsage(1920, 55, cached = 1800),
                            ),
                        )
                        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                }
                server.start()
                val seen = CopyOnWriteArrayList<ChatCompletionsAttempt>()
                val provider = provider(openRouter) {
                    baseUrl = server.url("/")
                    sleep = { }
                    attemptObserver = ChatCompletionsAttemptObserver { seen.add(it) }
                }

                assertReasonOnly(provider.complete(call(openRouter)), FailureReason.Network())
                // Initial request plus the one transient retry; the answered status was never read, so both are null.
                assertEquals(
                    listOf(
                        ChatCompletionsAttempt(1, ChatCompletionsAttemptKind.INITIAL, null, null, 0),
                        ChatCompletionsAttempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, null, null, 0),
                    ),
                    seen.toList(),
                )
            }
        }
    }
}
