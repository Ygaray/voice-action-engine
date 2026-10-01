package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class AnthropicTimeoutTest {

    /** A server that answers every request the same way, so a test never depends on how many calls were made. */
    private class SameAnswer(private val answer: () -> MockResponse) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = answer()
    }

    private val model = "claude-haiku-4-5"

    private fun call(): ProviderRequest = anthropicRequest(
        model,
        ModelRequest(
            "You are a test system.",
            listOf(UserMessage("add milk")),
            listOf(ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })),
            256,
        ),
        "sk-test-key",
    )

    private fun assertReasonOnly(result: ModelResult, expected: FailureReason) {
        val failure = result as ModelResult.Failure
        assertEquals(expected.code, failure.reason.code)
        assertNull(failure.details)
        // Equal to the reason-only form: no exception class or message was copied into the failure.
        assertEquals(ModelResult.Failure(expected).toString(), failure.toString())
        assertFalse(failure.toString().contains("Exception"))
    }

    @Test(timeout = 30_000)
    fun aServerThatDelaysItsHeadersPastTheCallTimeoutIsATimeout() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer {
                MockResponse().setResponseCode(200).setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS)
            }
            server.start()
            val provider = AnthropicProvider {
                baseUrl = server.url("/")
                callTimeoutMillis = 300
                readTimeoutMillis = 10_000
            }

            assertReasonOnly(provider.complete(call()), FailureReason.Timeout())
        }
    }

    @Test(timeout = 30_000)
    fun aServerThatNeverRespondsPastTheReadTimeoutIsATimeout() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
            server.start()
            val provider = AnthropicProvider {
                baseUrl = server.url("/")
                callTimeoutMillis = 10_000
                readTimeoutMillis = 300
            }

            assertReasonOnly(provider.complete(call()), FailureReason.Timeout())
        }
    }

    @Test(timeout = 30_000)
    fun aRefusedConnectionIsANetworkFailure() = runBlocking {
        val closed: HttpUrl = MockWebServer().use { server ->
            server.start()
            server.url("/")
        }
        val provider = AnthropicProvider { baseUrl = closed }

        assertReasonOnly(provider.complete(call()), FailureReason.Network())
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedBeforeAnyAnswerIsANetworkFailure() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer {
                MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }
            server.start()
            val provider = AnthropicProvider { baseUrl = server.url("/") }

            assertReasonOnly(provider.complete(call()), FailureReason.Network())
        }
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedMidAnswerIsANetworkFailureNotAMalformedOne() = runBlocking {
        MockWebServer().use { server ->
            // The status line and headers arrive (announcing the whole body); the connection then dies partway through
            // the body, so the failure happens while the answer is being read, not while it is being awaited.
            server.dispatcher = SameAnswer {
                MockResponse()
                    .setResponseCode(200)
                    .setBody(successBody(listOf(textBlock("a long enough answer to be cut in half")), "end_turn"))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            }
            server.start()
            val seen = CopyOnWriteArrayList<AnthropicAttempt>()
            val provider = AnthropicProvider {
                baseUrl = server.url("/")
                sleep = { }
                attemptObserver = AnthropicAttemptObserver { seen.add(it) }
            }

            assertReasonOnly(provider.complete(call()), FailureReason.Network())
            // Initial request plus the one transient retry; the answered status was never read, so both are null.
            assertEquals(
                listOf(
                    AnthropicAttempt(1, AnthropicAttemptKind.INITIAL, null),
                    AnthropicAttempt(2, AnthropicAttemptKind.TRANSIENT_RETRY, null),
                ),
                seen.toList(),
            )
        }
    }
}
