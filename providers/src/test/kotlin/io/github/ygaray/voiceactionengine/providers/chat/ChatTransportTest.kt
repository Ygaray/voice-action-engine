package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** The Chat Completions provider end to end against a local server, for both vendors. */
class ChatTransportTest {

    private val firstTurn = "add two eggs and a coffee"

    private val toolArguments =
        """{"items":[{"name":"egg","quantity":2,"unit":null,"confidence":0.9}],"target_date":null,"source":null}"""

    private fun forcedRequest(): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage(firstTurn)),
        listOf(logFoodTool()),
        ToolChoice.Required("log_food"),
        1024,
        CacheDirective(true),
    )

    private fun toolAnswer(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("x-request-id", "req_test_1")
        .setBody(
            chatBody(
                chatMessage(null, listOf(chatToolCall("call_1", "log_food", toolArguments))),
                "tool_calls",
                chatUsage(1920, 55, cached = 1800),
            ),
        )

    private fun routerAnswer(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            chatBody(
                chatMessage(null, listOf(chatToolCall("call_1", "log_food", toolArguments))),
                "tool_calls",
                chatUsage(1920, 55, cached = 1800),
                id = "gen-GOLDEN",
            ),
        )

    private class Run(val results: List<ModelResult>, val outcome: CommandOutcome, val requestCount: Int)

    /** Routes one command to [provider] on [vendor]/[model] with [key] and runs [request] through the pipeline. */
    private fun route(
        server: MockWebServer,
        provider: ChatCompletionsProvider,
        vendor: ChatVendor,
        model: String,
        request: ModelRequest,
        key: String? = "sk-test-key",
        keyedTo: ProviderId = vendor.providerId,
    ): Run = runBlocking {
        val results = RecordingSink<ModelResult>()
        val step: StrategyStep = { _, session ->
            results.record(session.model().complete(request))
            StrategyOutcome.Completed(null)
        }
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("probe"), step))
            provider(provider)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
            credentials = if (key == null) {
                ScriptedCredentialSource.keys()
            } else {
                ScriptedCredentialSource.keys(keyedTo to key)
            }
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }
        val outcome = pipeline.execute(CommandInput(firstTurn, "en", null))
        Run(results.events, outcome, server.requestCount)
    }

    private fun assertToolCallComesBackTyped(result: ModelResult, requestId: String) {
        val success = result as ModelResult.Success
        val call = success.response.message.toolCalls.single()
        assertEquals("log_food", call.name)
        assertEquals("call_1", call.id)
        assertEquals(Json.parseToJsonElement(toolArguments).jsonObject, call.arguments)
        assertEquals(StopReason.TOOL_USE, success.response.stopReason)
        assertEquals(Usage(120, 1800, 0, 55), success.response.usage)
        assertEquals(requestId, success.response.requestId)
    }

    @Test
    fun aRoutedCommandReachesOpenAiAsTheGoldenBodyAndTheToolCallComesBackTyped() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }

            val run = route(server, provider, ChatVendor.OPENAI, "gpt-5.4-mini", forcedRequest())

            assertToolCallComesBackTyped(run.results.single(), "req_test_1")
            assertEquals(1, run.requestCount)
            val seen = server.takeRequest()
            assertEquals("POST", seen.method)
            assertEquals("/chat/completions", seen.path)
            assertEquals("Bearer sk-test-key", seen.getHeader("Authorization"))
            assertTrue(seen.getHeader("content-type")!!.startsWith("application/json"))
            assertEquals(goldenRequest("openai", "forced_log_food_strict"), seen.body.readUtf8())
            val turn = run.outcome.trace.attempts.single().turns.single()
            assertEquals(ProviderId.OPENAI, turn.provider)
            assertEquals("tool_use", turn.stopReason)
        }
    }

    @Test
    fun aRoutedCommandReachesOpenRouterAsTheGoldenBodyAndTheBodyIdIsTheRequestId() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(routerAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openRouter { baseUrl = server.url("/") }

            val run = route(server, provider, ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", forcedRequest())

            assertToolCallComesBackTyped(run.results.single(), "gen-GOLDEN")
            assertEquals(1, run.requestCount)
            val seen = server.takeRequest()
            assertEquals("POST", seen.method)
            assertEquals("/chat/completions", seen.path)
            assertEquals("Bearer sk-test-key", seen.getHeader("Authorization"))
            assertEquals(goldenRequest("openrouter", "forced_log_food_strict"), seen.body.readUtf8())
            val turn = run.outcome.trace.attempts.single().turns.single()
            assertEquals(ProviderId.OPENROUTER, turn.provider)
        }
    }

    @Test
    fun aCredentialForAnotherProviderOrNoCredentialNeverReachesTheNetwork() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }

            val foreign = route(
                server, provider, ChatVendor.OPENAI, "gpt-5.4-mini", forcedRequest(), "sk-other", ProviderId.OPENROUTER,
            )
            val none = route(server, provider, ChatVendor.OPENAI, "gpt-5.4-mini", forcedRequest(), key = null)

            for (run in listOf(foreign, none)) {
                val failure = run.results.single() as ModelResult.Failure
                assertEquals("not_configured", failure.reason.code)
                assertEquals(0, run.requestCount)
            }
        }
    }

    @Test
    fun theProviderRefusesACredentialForTheOtherVendorBeforeAnyRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = ChatCompletionsProvider.openRouter { baseUrl = server.url("/") }
            val request = forcedRequest()
            val foreign = ProviderRequest(
                "openai/gpt-5.4-mini", request, Credential(ProviderId.OPENAI, "sk-other"), ModelCapabilities.UNKNOWN,
            )
            val none = ProviderRequest("openai/gpt-5.4-mini", request, null, ModelCapabilities.UNKNOWN)

            for (call in listOf(foreign, none)) {
                val failure = provider.complete(call) as ModelResult.Failure
                assertEquals("not_configured", failure.reason.code)
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun aToolsCommandOnAResponsesOnlyModelIsRefusedWithZeroRequests() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }

            val run = route(server, provider, ChatVendor.OPENAI, "gpt-6-astra", forcedRequest())

            val failure = run.results.single() as ModelResult.Failure
            assertEquals("model_unsupported", failure.reason.code)
            assertTrue(run.outcome.trace.codes.contains(TraceCode.CAPABILITY_REFUSED))
            assertEquals(0, run.requestCount)
        }
    }

    @Test
    fun thePublicCapabilitiesSeparateTheDirectModelFromTheRoutedOne() {
        assertTrue(!ChatCompletionsProvider.openAi { }.capabilities("gpt-6-astra").supportsTools)
        assertTrue(ChatCompletionsProvider.openRouter { }.capabilities("openai/gpt-6-astra").supportsTools)
    }

    @Test
    fun theProviderIdIsTheVendorsAndAKeyIsRequired() {
        assertEquals(ProviderId.OPENAI, ChatCompletionsProvider.openAi { }.id)
        assertEquals(ProviderId.OPENROUTER, ChatCompletionsProvider.openRouter { }.id)
        assertTrue(ChatCompletionsProvider.openAi { }.requiresCredential)
    }

    // ---- retry and observer

    private fun directCall(key: String = "sk-test-key"): ProviderRequest =
        chatCall(ChatVendor.OPENAI, "gpt-5.4-mini", forcedRequest(), key)

    private fun failure(status: Int, vararg headers: Pair<String, String>): MockResponse {
        val response = MockResponse().setResponseCode(status)
            .setBody(openAiErrorBody("server_error", "internal_error", "boom"))
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        return response
    }

    private class Retried(
        val result: ModelResult,
        val requestCount: Int,
        val waits: List<Long>,
        val seen: List<ChatCompletionsAttempt>,
    )

    /** Serves [responses] in order, then one sentinel 200 so an unexpected extra request is counted, not hung. */
    private fun retried(vararg responses: MockResponse, openRouter: Boolean = false): Retried = runBlocking {
        MockWebServer().use { server ->
            responses.forEach { server.enqueue(it) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val waits = CopyOnWriteArrayList<Long>()
            val seen = CopyOnWriteArrayList<ChatCompletionsAttempt>()
            val configure: ChatCompletionsProvider.Builder.() -> Unit = {
                baseUrl = server.url("/")
                callTimeoutMillis = 2_000
                sleep = { waits.add(it) }
                attemptObserver = ChatCompletionsAttemptObserver { seen.add(it) }
            }
            val provider = if (openRouter) {
                ChatCompletionsProvider.openRouter(configure)
            } else {
                ChatCompletionsProvider.openAi(configure)
            }
            val call = if (openRouter) {
                chatCall(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", forcedRequest())
            } else {
                directCall()
            }
            val result = provider.complete(call)
            Retried(result, server.requestCount, waits.toList(), seen.toList())
        }
    }

    private fun Retried.code(): String = (result as ModelResult.Failure).reason.code

    private fun attempt(
        number: Int,
        kind: ChatCompletionsAttemptKind,
        status: Int?,
        finish: String? = null,
        toolCalls: Int = 0,
    ): ChatCompletionsAttempt = ChatCompletionsAttempt(number, kind, status, finish, toolCalls)

    @Test(timeout = 30_000)
    fun aTransientStatusIsRetriedOnceAfterTheBackoffAndEachAttemptIsObserved() {
        val run = retried(failure(503), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(500L), run.waits)
        assertEquals(
            listOf(
                attempt(1, ChatCompletionsAttemptKind.INITIAL, 503),
                attempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, 200, "tool_calls", 1),
            ),
            run.seen,
        )
    }

    @Test(timeout = 30_000)
    fun aTransientEnvelopeInsideA200IsRetriedOnceOnOpenRouter() {
        val envelope = MockResponse().setResponseCode(200).setBody(chatErrorEnvelope(429, "slow down"))

        val run = retried(envelope, routerAnswer(), openRouter = true)

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(200, run.seen.first().httpStatus)
        assertNull(run.seen.first().finishReason)
        assertEquals(listOf(500L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aRetryAfterWithinTheCapSetsTheWaitAndOneBeyondItEndsTheCall() {
        val within = retried(failure(429, "retry-after" to "2"), toolAnswer())
        assertTrue(within.result is ModelResult.Success)
        assertEquals(listOf(2_000L), within.waits)

        val beyond = retried(failure(429, "retry-after" to "30"), toolAnswer())
        assertEquals("rate_limited", beyond.code())
        assertEquals(1, beyond.requestCount)
        assertTrue(beyond.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun authQuotaAndOtherClientErrorsAreNeverRetried() {
        val auth = retried(failure(401), toolAnswer())
        assertEquals("auth", auth.code())
        assertEquals(1, auth.requestCount)

        val quota = MockResponse().setResponseCode(429)
            .setBody(openAiErrorBody("insufficient_quota", "insufficient_quota", "You exceeded your quota"))
        val billing = retried(quota, toolAnswer())
        assertEquals("billing", billing.code())
        assertEquals(1, billing.requestCount)

        for (status in listOf(400, 403, 404, 413)) {
            val run = retried(failure(status), toolAnswer())
            assertEquals("status $status", 1, run.requestCount)
            assertTrue("status $status", run.waits.isEmpty())
        }
    }

    @Test(timeout = 30_000)
    fun aMalformedSuccessIsNeverRetried() {
        val run = retried(MockResponse().setResponseCode(200).setBody("this is not json"), toolAnswer())

        assertEquals("malformed_response", run.code())
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun aSecondTransientFailureIsFinalAndNoThirdRequestIsSent() {
        val run = retried(failure(503), failure(503), toolAnswer())

        assertEquals("overloaded", run.code())
        assertEquals(2, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun aConnectionLostOnBothAttemptsIsNetworkAfterTwoRequestsWithNoStatus() {
        val dropped = MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        val run = retried(dropped, dropped, toolAnswer())

        assertEquals("network", run.code())
        assertEquals(2, run.requestCount)
        assertEquals(
            listOf(
                attempt(1, ChatCompletionsAttemptKind.INITIAL, null),
                attempt(2, ChatCompletionsAttemptKind.TRANSIENT_RETRY, null),
            ),
            run.seen,
        )
    }

    @Test(timeout = 30_000)
    fun aConnectionLostOnceIsRetriedAndSucceeds() {
        val dropped = MockResponse().setResponseCode(200).setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        val run = retried(dropped, toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertEquals(listOf(500L), run.waits)
    }

    @Test(timeout = 30_000)
    fun aToolCallWithFinishReasonStopIsReportedAsTheDisagreementAndStaysAToolTurn() {
        val disagreeing = MockResponse().setResponseCode(200).setBody(
            chatBody(chatMessage(null, listOf(chatToolCall("call_1", "log_food", toolArguments))), "stop"),
        )

        val run = retried(disagreeing)

        val success = run.result as ModelResult.Success
        assertEquals(StopReason.TOOL_USE, success.response.stopReason)
        assertEquals(listOf(attempt(1, ChatCompletionsAttemptKind.INITIAL, 200, "stop", 1)), run.seen)
    }

    @Test(timeout = 30_000)
    fun anObserverThatThrowsNeitherLosesTheAnswerNorStopsTheRetry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(failure(503))
            server.enqueue(toolAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openAi {
                baseUrl = server.url("/")
                callTimeoutMillis = 2_000
                sleep = { }
                attemptObserver = ChatCompletionsAttemptObserver { error("observer bug carrying text") }
            }

            val result = provider.complete(directCall())

            assertTrue(result is ModelResult.Success)
            assertEquals(2, server.requestCount)
        }
    }

    private fun assertAuthWithoutTheKey(key: String) = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }

            val failure = provider.complete(directCall("sk-CANARY$key")) as ModelResult.Failure

            assertEquals("auth", failure.reason.code)
            assertNull(failure.details)
            assertFalse(failure.toString().contains("sk-CANARY"))
            assertEquals(0, server.requestCount)
        }
    }

    @Test(timeout = 30_000)
    fun aKeyOkHttpWouldRefuseAsAHeaderValueIsAuthWithZeroRequests() {
        assertAuthWithoutTheKey("\n")
        assertAuthWithoutTheKey("\rmore")
        assertAuthWithoutTheKey("\u00eb")
    }

    // ---- configuration

    @Test
    fun theDefaultProvidersTargetTheFixedHttpsHostsWithSixtySecondTimeouts() {
        val openAi = ChatCompletionsProvider.openAi { }
        val openRouter = ChatCompletionsProvider.openRouter { }

        assertEquals("https://api.openai.com/v1/", openAi.transport.baseUrl.toString())
        assertEquals("https://openrouter.ai/api/v1/", openRouter.transport.baseUrl.toString())
        for (provider in listOf(openAi, openRouter)) {
            assertTrue(provider.transport.baseUrl.isHttps)
            assertEquals(60_000L, provider.transport.client.callTimeoutMillis.toLong())
            assertEquals(60_000L, provider.transport.client.readTimeoutMillis.toLong())
        }
    }

    @Test
    fun configuredTimeoutsAreHonoredAndNonPositiveOnesAreRejectedByName() {
        val provider = ChatCompletionsProvider.openAi {
            callTimeoutMillis = 90_000
            readTimeoutMillis = 75_000
        }
        assertEquals(90_000L, provider.transport.client.callTimeoutMillis.toLong())
        assertEquals(75_000L, provider.transport.client.readTimeoutMillis.toLong())

        val call = rejection { ChatCompletionsProvider.openAi { callTimeoutMillis = 0 } }
        assertTrue(call.contains("callTimeoutMillis"))
        val read = rejection { ChatCompletionsProvider.openRouter { readTimeoutMillis = -1 } }
        assertTrue(read.contains("readTimeoutMillis"))
    }

    @Test
    fun aCleartextBaseUrlIsRejectedUnlessItIsLoopback() {
        rejection { ChatCompletionsProvider.openAi { baseUrl = "http://example.com/".toHttpUrl() } }
        ChatCompletionsProvider.openAi { baseUrl = "http://localhost:8080/".toHttpUrl() }
        ChatCompletionsProvider.openRouter { baseUrl = "http://127.0.0.1:8080/".toHttpUrl() }
        ChatCompletionsProvider.openRouter { baseUrl = "http://[::1]:8080/".toHttpUrl() }
        MockWebServer().use { server ->
            server.start()
            ChatCompletionsProvider.openAi { baseUrl = server.url("/") }
        }
    }

    @Test
    fun theDerivedClientDropsTheAppsInterceptorsSoTheyNeverSeeTheRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.start()
            val applicationCalls = AtomicInteger()
            val networkCalls = AtomicInteger()
            val shared = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    applicationCalls.incrementAndGet()
                    chain.proceed(chain.request())
                }
                .addNetworkInterceptor { chain ->
                    networkCalls.incrementAndGet()
                    chain.proceed(chain.request())
                }
                .build()
            val provider = ChatCompletionsProvider.openAi {
                httpClient = shared
                baseUrl = server.url("/")
            }

            val result = provider.complete(directCall())

            assertTrue(result is ModelResult.Success)
            assertEquals(1, server.requestCount)
            assertEquals(0, applicationCalls.get())
            assertEquals(0, networkCalls.get())
            assertTrue(provider.transport.client.interceptors.isEmpty())
            assertTrue(provider.transport.client.networkInterceptors.isEmpty())
        }
    }

    @Test
    fun toStringShowsTheProviderIdAndBothTimeoutsAndNothingElse() {
        val openAi = ChatCompletionsProvider.openAi {
            callTimeoutMillis = 90_000
            readTimeoutMillis = 75_000
        }
        val openRouter = ChatCompletionsProvider.openRouter { }

        assertEquals(
            "ChatCompletionsProvider(provider=openai, callTimeoutMillis=90000, readTimeoutMillis=75000)",
            openAi.toString(),
        )
        assertEquals(
            "ChatCompletionsProvider(provider=openrouter, callTimeoutMillis=60000, readTimeoutMillis=60000)",
            openRouter.toString(),
        )
    }

    private fun replayTurn(): JsonObject = Json.parseToJsonElement(
        """{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function",""" +
            """"function":{"name":"log_food","arguments":"{}"}}]}""",
    ).jsonObject

    private fun history(replay: NativeReplay?): List<Message> = listOf(
        UserMessage(firstTurn),
        AssistantMessage(listOf(AssistantPart.ToolCall("call_1", "log_food", JsonObject(emptyMap()))), replay),
        ToolResultsMessage(listOf(ToolResult("call_1", "logged"))),
    )

    private fun historyRequest(replay: NativeReplay?): ModelRequest =
        ModelRequest(FIXED_SYSTEM, history(replay), listOf(logFoodTool()), 1024)

    @Test
    fun aTurnStampedForAnotherVendorOrModelIsRefusedBeforeAnyRequest() = runBlocking {
        val mismatched = listOf(
            NativeReplay(ProviderId.OPENROUTER, "gpt-5.4-mini", replayTurn()),
            NativeReplay(ProviderId.OPENAI, "gpt-4o-mini", replayTurn()),
            NativeReplay(ProviderId.OPENAI, "gpt-5.4-mini", JsonArray(emptyList())),
        )

        mismatched.forEach { stamp ->
            MockWebServer().use { server ->
                server.enqueue(toolAnswer())
                server.start()
                val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }

                val run = route(server, provider, ChatVendor.OPENAI, "gpt-5.4-mini", historyRequest(stamp))

                val failure = run.results.single() as ModelResult.Failure
                assertEquals(FailureReason.Other("replay_mismatch"), failure.reason)
                assertEquals(0, run.requestCount)
            }
        }

        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }
            val stamp = NativeReplay(ProviderId.OPENAI, "gpt-5.4-mini", replayTurn())

            val run = route(server, provider, ChatVendor.OPENAI, "gpt-5.4-mini", historyRequest(stamp))

            assertTrue(run.results.single() is ModelResult.Success)
            assertEquals(1, run.requestCount)
            val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val assistant = sent.getValue("messages").jsonArray.map { it.jsonObject }
                .single { it.getValue("role").jsonPrimitive.content == "assistant" }
            assertEquals(replayTurn(), assistant)
        }

        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openAi { baseUrl = server.url("/") }

            val run = route(server, provider, ChatVendor.OPENAI, "gpt-5.4-mini", historyRequest(null))

            assertTrue(run.results.single() is ModelResult.Success)
            assertEquals(1, run.requestCount)
        }

        MockWebServer().use { server ->
            server.enqueue(routerAnswer())
            server.start()
            val provider = ChatCompletionsProvider.openRouter { baseUrl = server.url("/") }
            val stamp = NativeReplay(ProviderId.OPENAI, "openai/gpt-5.4-mini", replayTurn())

            val run = route(server, provider, ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", historyRequest(stamp))

            val failure = run.results.single() as ModelResult.Failure
            assertEquals(FailureReason.Other("replay_mismatch"), failure.reason)
            assertEquals(0, run.requestCount)
        }
    }

    private fun rejection(block: () -> Unit): String {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("expected IllegalArgumentException")
        return ""
    }
}
