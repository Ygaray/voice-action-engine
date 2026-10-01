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
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
