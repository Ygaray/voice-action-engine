package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Which Chat failures are retried, how long the retry waits, and that nothing else ever re-sends the request. */
class ChatRetryTest {

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

    private fun toolAnswer(): MockResponse = MockResponse().setResponseCode(200).setBody(
        chatBody(
            chatMessage(null, listOf(chatToolCall("call_1", "log_food", toolArguments))),
            "tool_calls",
            chatUsage(1920, 55, cached = 1800),
        ),
    )

    private fun failure(status: Int, vararg headers: Pair<String, String>): MockResponse {
        val response = MockResponse().setResponseCode(status)
            .setBody(openAiErrorBody("server_error", "internal_error", "boom"))
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        return response
    }

    private fun envelope(code: Int): MockResponse =
        MockResponse().setResponseCode(200).setBody(chatErrorEnvelope(code, "upstream said no"))

    private fun dropped(): MockResponse =
        MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

    private class Run(val result: ModelResult, val requestCount: Int, val waits: List<Long>)

    private fun configured(
        server: MockWebServer,
        openRouter: Boolean,
        waits: MutableList<Long>,
        extra: ChatCompletionsProvider.Builder.() -> Unit = {},
    ): ChatCompletionsProvider {
        val configure: ChatCompletionsProvider.Builder.() -> Unit = {
            baseUrl = server.url("/")
            callTimeoutMillis = 2_000
            retryAfterCapMillis = 5_000
            transientBackoffMillis = 500
            sleep = { waits.add(it) }
            extra()
        }
        return if (openRouter) {
            ChatCompletionsProvider.openRouter(configure)
        } else {
            ChatCompletionsProvider.openAi(configure)
        }
    }

    /** Serves [responses] in order, then one sentinel 200 so an unexpected extra request is counted, not hung. */
    private fun run(vararg responses: MockResponse, openRouter: Boolean = false): Run = runBlocking {
        MockWebServer().use { server ->
            responses.forEach { server.enqueue(it) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val result = configured(server, openRouter, waits).complete(call(openRouter))
            Run(result, server.requestCount, waits.toList())
        }
    }

    private fun Run.code(): String = (result as ModelResult.Failure).reason.code

    // ---- the retry matrix

    @Test(timeout = 30_000)
    fun everyTransientStatusIsRetriedOnceAfterTheBackoff() {
        for (status in listOf(408, 429, 500, 502, 503, 504, 524, 529)) {
            val run = run(failure(status), toolAnswer())
            assertTrue("status $status", run.result is ModelResult.Success)
            assertEquals("status $status", 2, run.requestCount)
            assertEquals("status $status", listOf(500L), run.waits)
        }
    }

    @Test(timeout = 30_000)
    fun everyTransientStatusIsRetriedOnceAfterTheBackoffOnOpenRouterToo() {
        for (status in listOf(408, 429, 500, 502, 503, 504, 524, 529)) {
            val run = run(failure(status), toolAnswer(), openRouter = true)
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
        val run = run(failure(429, "retry-after" to "60"), toolAnswer())

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

    @Test(timeout = 30_000)
    fun aRetryAfterOfZeroOn503IsFollowedByTheTransportNotByOkHttp() {
        val run = run(failure(503, "Retry-After" to "0"), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(0L), run.waits)
    }

    @Test(timeout = 30_000)
    fun anInsufficientQuota429IsBillingAndNeverRetried() {
        val quota = MockResponse().setResponseCode(429)
            .setBody(openAiErrorBody("insufficient_quota", "insufficient_quota", "You exceeded your quota"))

        val run = run(quota, toolAnswer())

        assertEquals("billing", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun clientErrorsAreNeverRetriedAndEachKeepsItsReason() {
        val expected = listOf(
            402 to "billing",
            400 to "http_error",
            401 to "auth",
            403 to "auth",
            404 to "model_not_found",
        )
        for ((status, code) in expected) {
            val run = run(failure(status), toolAnswer(), openRouter = status == 402)
            assertEquals("status $status", code, run.code())
            assertEquals("status $status", 1, run.requestCount)
            assertTrue("status $status", run.waits.isEmpty())
        }
    }

    @Test(timeout = 30_000)
    fun aSecondTransientFailureIsFinalAndNoThirdRequestIsSent() {
        val run = run(failure(503), failure(503), toolAnswer())

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

    // ---- errors wrapped in a 200

    @Test(timeout = 30_000)
    fun aTransientEnvelopeInsideA200IsRetriedOnceOnOpenRouter() {
        for (code in listOf(429, 502)) {
            val run = run(envelope(code), toolAnswer(), openRouter = true)
            assertTrue("code $code", run.result is ModelResult.Success)
            assertEquals("code $code", 2, run.requestCount)
            assertEquals("code $code", listOf(500L), run.waits)
        }
    }

    @Test(timeout = 30_000)
    fun aNonTransientEnvelopeInsideA200IsFinalAfterOneRequest() {
        val run = run(envelope(401), toolAnswer(), openRouter = true)

        assertEquals("auth", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    // ---- who re-sends

    @Test(timeout = 30_000)
    fun aConnectionLostAfterTheRequestWasSentIsRetriedByTheTransportExactlyOnce() {
        val run = run(dropped(), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(500L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aConnectionDroppedOnAWarmPooledConnectionIsStillRetriedOnlyByTheTransport() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.enqueue(dropped())
            server.enqueue(toolAnswer())
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val seen = CopyOnWriteArrayList<ChatCompletionsAttempt>()
            val provider = configured(server, false, waits) {
                attemptObserver = ChatCompletionsAttemptObserver { seen.add(it) }
            }

            val warmUp = provider.complete(call(false))
            val retried = provider.complete(call(false))

            assertTrue(warmUp is ModelResult.Success)
            assertTrue(retried is ModelResult.Success)
            // Warm-up, the dropped request and the transport's single retry. OkHttp's own replay would have hidden the
            // drop from the transport: fewer observed attempts than requests on the wire.
            assertEquals(3, server.requestCount)
            assertEquals(3, seen.size)
            assertEquals(listOf(500L), waits.toList())
        }
    }

    @Test(timeout = 30_000)
    fun positiveControlAReplayableBodyIsSilentlyResentByOkHttpOnAWarmConnectionAfterADisconnect() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.enqueue(dropped())
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val client = OkHttpClient()
            val replayable = Request.Builder().url(server.url("/")).post("{}".toRequestBody()).build()

            client.newCall(replayable).execute().use { assertEquals(200, it.code) }
            client.newCall(replayable).execute().use { assertEquals(200, it.code) }

            // This is the replay the one-shot body exists to prevent: two executes, three requests on the wire.
            assertEquals(3, server.requestCount)
        }
    }

    // ---- the observer

    private fun observed(
        vararg responses: MockResponse,
        request: ProviderRequest = call(false),
    ): List<ChatCompletionsAttempt> = runBlocking {
        MockWebServer().use { server ->
            responses.forEach { server.enqueue(it) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val seen = CopyOnWriteArrayList<ChatCompletionsAttempt>()
            val provider = configured(server, false, CopyOnWriteArrayList()) {
                sleep = { }
                attemptObserver = ChatCompletionsAttemptObserver { seen.add(it) }
            }
            provider.complete(request)
            seen.toList()
        }
    }

    private fun attempt(
        number: Int,
        kind: ChatCompletionsAttemptKind,
        status: Int?,
        finish: String? = null,
        toolCalls: Int = 0,
    ): ChatCompletionsAttempt = ChatCompletionsAttempt(number, kind, status, finish, toolCalls)

    @Test(timeout = 30_000)
    fun theObserverSeesTheFailedFirstAttemptAndTheRetryThatFollowedIt() {
        val seen = observed(failure(529), toolAnswer())

        assertEquals(
            listOf(
                attempt(1, ChatCompletionsAttemptKind.INITIAL, 529),
                attempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, 200, "tool_calls", 1),
            ),
            seen,
        )
    }

    @Test(timeout = 30_000)
    fun anAttemptWithNoHttpAnswerIsReportedWithANullStatus() {
        val seen = observed(dropped(), toolAnswer())

        assertEquals(
            listOf(
                attempt(1, ChatCompletionsAttemptKind.INITIAL, null),
                attempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, 200, "tool_calls", 1),
            ),
            seen,
        )
    }

    @Test(timeout = 30_000)
    fun aFinalFailureAndAFirstTrySuccessAreEachOneAttempt() {
        assertEquals(
            listOf(attempt(1, ChatCompletionsAttemptKind.INITIAL, 401)),
            observed(failure(401), toolAnswer()),
        )
        assertEquals(
            listOf(attempt(1, ChatCompletionsAttemptKind.INITIAL, 200, "tool_calls", 1)),
            observed(toolAnswer()),
        )
    }

    @Test(timeout = 30_000)
    fun anObserverThatThrowsNeitherLosesTheAnswerNorStopsTheRetry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(failure(529))
            server.enqueue(toolAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openAi {
                baseUrl = server.url("/")
                callTimeoutMillis = 2_000
                sleep = { }
                attemptObserver = ChatCompletionsAttemptObserver { error("observer bug carrying text") }
            }

            val result = provider.complete(call(false))

            assertTrue(result is ModelResult.Success)
            assertEquals(2, server.requestCount)
        }
    }

    @Test(timeout = 30_000)
    fun aRequestRefusedBeforeTheNetworkIsNotAnAttempt() {
        val otherProvider = ProviderRequest(
            "gpt-5.4-mini",
            request(),
            Credential(ProviderId.OPENROUTER, "sk-other"),
            ModelCapabilities.UNKNOWN,
        )

        assertTrue(observed(toolAnswer(), request = otherProvider).isEmpty())
    }

    @Test
    fun anAttemptIsComparedByValueOverAllFiveFactsAndPrintsOnlyThem() {
        val first = attempt(1, ChatCompletionsAttemptKind.INITIAL, 529, "stop", 2)

        assertEquals(first, attempt(1, ChatCompletionsAttemptKind.INITIAL, 529, "stop", 2))
        assertEquals(first.hashCode(), attempt(1, ChatCompletionsAttemptKind.INITIAL, 529, "stop", 2).hashCode())
        assertNotEquals(first, attempt(2, ChatCompletionsAttemptKind.INITIAL, 529, "stop", 2))
        assertNotEquals(first, attempt(1, ChatCompletionsAttemptKind.TRANSIENT_RETRY, 529, "stop", 2))
        assertNotEquals(first, attempt(1, ChatCompletionsAttemptKind.INITIAL, null, "stop", 2))
        assertNotEquals(first, attempt(1, ChatCompletionsAttemptKind.INITIAL, 529, "length", 2))
        assertNotEquals(first, attempt(1, ChatCompletionsAttemptKind.INITIAL, 529, "stop", 1))
        assertEquals(
            "ChatCompletionsAttempt(number=1, kind=initial, httpStatus=529, finishReason=stop, toolCalls=2)",
            first.toString(),
        )
        assertEquals(
            "ChatCompletionsAttempt(number=2, kind=transient_retry, httpStatus=null, finishReason=null, toolCalls=0)",
            attempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, null).toString(),
        )
        assertNull(attempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, null).httpStatus)
    }

    @Test
    fun theAttemptKindsHaveTheirWireValuesAndAreDistinct() {
        val kinds = listOf(ChatCompletionsAttemptKind.INITIAL, ChatCompletionsAttemptKind.TRANSIENT_RETRY)

        assertEquals(listOf("initial", "transient_retry"), kinds.map { it.value })
        assertEquals(listOf("initial", "transient_retry"), kinds.map { it.toString() })
        assertEquals(2, kinds.toSet().size)
    }

    @Test
    fun aProviderBuiltWithAnObserverStillPrintsOnlyItsIdAndTimeouts() {
        val provider = ChatCompletionsProvider.openAi {
            callTimeoutMillis = 1_000
            readTimeoutMillis = 2_000
            attemptObserver = ChatCompletionsAttemptObserver { }
        }

        assertEquals(
            "ChatCompletionsProvider(provider=openai, callTimeoutMillis=1000, readTimeoutMillis=2000)",
            provider.toString(),
        )
    }
}
