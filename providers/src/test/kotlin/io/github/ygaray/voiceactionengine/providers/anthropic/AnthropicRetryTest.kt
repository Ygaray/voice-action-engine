package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Which failures are retried, how long the retry waits, and that nothing else ever re-sends the request. */
class AnthropicRetryTest {

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

    private fun toolAnswer(): MockResponse = MockResponse().setResponseCode(200).setBody(
        successBody(
            listOf(toolUseBlock("toolu_1", "add_item", buildJsonObject { put("item", "milk") })),
            "tool_use",
        ),
    )

    private fun failure(status: Int, vararg headers: Pair<String, String>): MockResponse {
        val response = MockResponse().setResponseCode(status).setBody(errorBody("api_error", "boom", "req_f_1"))
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        return response
    }

    private class Run(val result: ModelResult, val requestCount: Int, val waits: List<Long>)

    /** Serves [responses] in order, then one sentinel 200 so an unexpected extra request is counted, not hung. */
    private fun run(vararg responses: MockResponse): Run = runBlocking {
        MockWebServer().use { server ->
            responses.forEach { server.enqueue(it) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val provider = AnthropicProvider {
                baseUrl = server.url("/")
                callTimeoutMillis = 2_000
                sleep = { waits.add(it) }
            }
            val result = provider.complete(call())
            Run(result, server.requestCount, waits.toList())
        }
    }

    private fun Run.code(): String = (result as ModelResult.Failure).reason.code

    @Test(timeout = 30_000)
    fun everyTransientStatusIsRetriedOnceAfterTheBackoff() {
        for (status in listOf(408, 429, 500, 502, 503, 504, 529)) {
            val run = run(failure(status), toolAnswer())
            assertTrue("status $status", run.result is ModelResult.Success)
            assertEquals("status $status", 2, run.requestCount)
            assertEquals("status $status", listOf(500L), run.waits)
        }
    }

    @Test(timeout = 30_000)
    fun aRetryAfterWithinTheCapSetsTheWait() {
        val run = run(failure(429, "retry-after" to "2"), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(2_000L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aRetryAfterBeyondTheCapEndsTheCallWithoutWaiting() {
        val run = run(failure(429, "retry-after" to "30"), toolAnswer())

        assertEquals("rate_limited", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun aRetryAfterThatIsADateFallsBackToTheBackoff() {
        val run = run(failure(429, "retry-after" to "Wed, 21 Oct 2026 07:28:00 GMT"), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(500L), run.waits)
    }

    private fun spendCap(): MockResponse = MockResponse().setResponseCode(429).setBody(
        buildJsonObject {
            put("type", "error")
            putJsonObject("error") {
                put("type", "rate_limit_error")
                put("message", "Spend cap")
                putJsonObject("details") { put("error_code", "enforced_spend_limit_reached") }
            }
        }.toString(),
    )

    @Test(timeout = 30_000)
    fun aSpendCapAnswerIsBillingAndNeverRetried() {
        val run = run(spendCap(), toolAnswer())

        assertEquals("billing", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun aUserSpendLimitAnswerIsBillingAndNeverRetried() {
        val limit = MockResponse().setResponseCode(400).setBody(
            errorBody("invalid_request_error", "You have reached your specified API usage limits.", "req_l_1"),
        )

        val run = run(limit, toolAnswer())

        assertEquals("billing", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun otherClientErrorsAreNeverRetried() {
        for (status in listOf(400, 401, 403, 404, 413)) {
            val run = run(failure(status), toolAnswer())
            assertEquals("status $status", 1, run.requestCount)
            assertTrue("status $status", run.waits.isEmpty())
            assertTrue("status $status", run.result is ModelResult.Failure)
        }
    }

    @Test(timeout = 30_000)
    fun aSecondTransientFailureIsFinalAndNoThirdRequestIsSent() {
        val run = run(failure(529), failure(529), toolAnswer())

        assertEquals("overloaded", run.code())
        assertEquals(2, run.requestCount)
        assertEquals(listOf(500L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aMalformedSuccessIsNeverRetried() {
        val run = run(MockResponse().setResponseCode(200).setBody("this is not json"), toolAnswer())

        assertEquals("malformed_response", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun aConnectionLostAfterTheRequestWasSentIsRetriedByTheTransportExactlyOnce() {
        val dropped = MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        val run = run(dropped, toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(500L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aRetryAfterOfZeroOn503IsFollowedByTheTransportNotByOkHttp() {
        val run = run(failure(503, "Retry-After" to "0"), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(0L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedOnAWarmPooledConnectionIsStillRetriedOnlyByTheTransport() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.enqueue(MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(toolAnswer())
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val provider = AnthropicProvider {
                baseUrl = server.url("/")
                sleep = { waits.add(it) }
            }

            val warmUp = provider.complete(call())
            val retried = provider.complete(call())

            assertTrue(warmUp is ModelResult.Success)
            assertTrue(retried is ModelResult.Success)
            // Warm-up, the dropped request, and the transport's single retry; OkHttp's own replay would have hidden the
            // drop from the transport and left no recorded wait.
            assertEquals(3, server.requestCount)
            assertEquals(listOf(500L), waits.toList())
        }
    }

    @Test(timeout = 30_000)
    fun positiveControlAReplayableBodyIsSilentlyResentByOkHttpOnAWarmConnectionAfterADisconnect() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val client = OkHttpClient()
            val request = Request.Builder().url(server.url("/")).post("{}".toRequestBody()).build()

            client.newCall(request).execute().use { assertEquals(200, it.code) }
            client.newCall(request).execute().use { assertEquals(200, it.code) }

            // This is the replay the one-shot body exists to prevent: two executes, three requests on the wire.
            assertEquals(3, server.requestCount)
        }
    }
}
