package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
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
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnthropicTransportTest {

    private val model = "claude-haiku-4-5"

    private val addItem = ToolSpec(
        "add_item",
        "Adds one item to the list.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("item") { put("type", "string") } }
        },
    )

    private fun milkArguments(): JsonObject = buildJsonObject { put("item", "milk") }

    private fun answer(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("request-id", "req_test_1")
        .setBody(
            successBody(
                listOf(textBlock("Adding it."), toolUseBlock("toolu_1", "add_item", milkArguments())),
                "tool_use",
                usageJson(11, 7, cacheCreation = 0, cacheRead = 0),
            ),
        )

    private fun addMilkRequest(): ModelRequest =
        ModelRequest("You are a test system.", listOf(UserMessage("add milk")), listOf(addItem), 256)

    private fun bareRequest(): ProviderRequest = ProviderRequest(
        model,
        addMilkRequest(),
        Credential(ProviderId.OPENAI, "sk-other"),
        ModelCapabilities.UNKNOWN,
    )

    @Test
    fun aRoutedCommandReachesTheServerAsOneMessagesCallAndTheToolCallComesBackTyped() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(answer())
            server.start()
            val results = RecordingSink<ModelResult>()
            val step: StrategyStep = { _, session ->
                results.record(session.model().complete(addMilkRequest()))
                StrategyOutcome.Completed(null)
            }
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(AnthropicProvider { baseUrl = server.url("/") })
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
                credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("add milk", "en", null))

            val result = results.events.single() as ModelResult.Success
            val response = result.response
            assertEquals(listOf("add_item"), response.message.toolCalls.map { it.name })
            assertEquals("toolu_1", response.message.toolCalls.single().id)
            assertEquals(milkArguments(), response.message.toolCalls.single().arguments)
            assertEquals(StopReason.TOOL_USE, response.stopReason)
            assertEquals(Usage(11, 0, 0, 7), response.usage)
            assertEquals("req_test_1", response.requestId)

            assertEquals(1, server.requestCount)
            val seen = server.takeRequest()
            assertEquals("POST", seen.method)
            assertEquals("/v1/messages", seen.path)
            assertEquals("sk-test-key", seen.getHeader("x-api-key"))
            assertEquals("2023-06-01", seen.getHeader("anthropic-version"))
            assertTrue(seen.getHeader("content-type")!!.startsWith("application/json"))
            val text = seen.body.readUtf8()
            val body = Json.parseToJsonElement(text).jsonObject
            assertEquals(model, body["model"]!!.jsonPrimitive.content)
            assertEquals(256, body["max_tokens"]!!.jsonPrimitive.int)
            assertEquals(1, Regex("cache_control").findAll(text).count())
            assertNotNull(body["system"]!!.jsonArray.single().jsonObject["cache_control"])

            val turn = outcome.trace.attempts.single().turns.single()
            assertEquals(ProviderId.ANTHROPIC, turn.provider)
            assertEquals(model, turn.model)
            assertEquals("tool_use", turn.stopReason)
        }
    }

    @Test
    fun theDefaultProviderTargetsTheFixedHttpsHostWithSixtySecondTimeouts() {
        val provider = AnthropicProvider { }

        assertEquals("https://api.anthropic.com/", provider.transport.baseUrl.toString())
        assertTrue(provider.transport.baseUrl.isHttps)
        assertEquals(60_000L, provider.transport.client.callTimeoutMillis.toLong())
        assertEquals(60_000L, provider.transport.client.readTimeoutMillis.toLong())
    }

    @Test
    fun configuredTimeoutsAreHonoredAndNonPositiveOnesAreRejectedByName() {
        val provider = AnthropicProvider {
            callTimeoutMillis = 90_000
            readTimeoutMillis = 75_000
        }
        assertEquals(90_000L, provider.transport.client.callTimeoutMillis.toLong())
        assertEquals(75_000L, provider.transport.client.readTimeoutMillis.toLong())

        val call = runCatchingIllegal { AnthropicProvider { callTimeoutMillis = 0 } }
        assertTrue(call.contains("callTimeoutMillis"))
        val read = runCatchingIllegal { AnthropicProvider { readTimeoutMillis = -1 } }
        assertTrue(read.contains("readTimeoutMillis"))
    }

    @Test
    fun aCleartextBaseUrlIsRejectedUnlessItIsLoopback() {
        runCatchingIllegal { AnthropicProvider { baseUrl = "http://example.com/".toHttpUrl() } }
        AnthropicProvider { baseUrl = "http://localhost:8080/".toHttpUrl() }
        AnthropicProvider { baseUrl = "http://127.0.0.1:8080/".toHttpUrl() }
        AnthropicProvider { baseUrl = "http://[::1]:8080/".toHttpUrl() }
        MockWebServer().use { server ->
            server.start()
            AnthropicProvider { baseUrl = server.url("/") }
        }
    }

    @Test
    fun toStringShowsBothTimeoutsAndNothingElse() {
        val provider = AnthropicProvider {
            callTimeoutMillis = 90_000
            readTimeoutMillis = 75_000
        }

        assertEquals("AnthropicProvider(callTimeoutMillis=90000, readTimeoutMillis=75000)", provider.toString())
    }

    @Test
    fun aCredentialForAnotherProviderOrNoCredentialNeverReachesTheNetwork() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = AnthropicProvider { baseUrl = server.url("/") }
            val foreign = bareRequest()
            val none = ProviderRequest(model, foreign.request, null, ModelCapabilities.UNKNOWN)

            for (call in listOf(foreign, none)) {
                val result = provider.complete(call) as ModelResult.Failure
                assertEquals("not_configured", result.reason.code)
            }

            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun theProviderIdAndTableCapabilitiesAreAnthropics() {
        val provider = AnthropicProvider { }

        assertEquals(ProviderId.ANTHROPIC, provider.id)
        assertTrue(provider.requiresCredential)
        assertEquals(AnthropicModels.capabilities("claude-opus-5-5"), provider.capabilities("claude-opus-5-5"))
    }

    /** What one routed command saw: its result, how many requests reached the server, and the assistant turn sent. */
    private class Routed(val result: ModelResult, val requestCount: Int, val assistantContent: JsonElement?)

    private fun replayContent(): JsonArray = buildJsonArray {
        add(thinkingBlock("pondering"))
        add(toolUseBlock("toolu_1", "add_item", milkArguments()))
    }

    private fun history(replay: NativeReplay?): List<Message> = listOf(
        UserMessage("add milk"),
        AssistantMessage(listOf(AssistantPart.ToolCall("toolu_1", "add_item", milkArguments())), replay),
        ToolResultsMessage(listOf(ToolResult("toolu_1", "added"))),
    )

    /** Routes one command carrying [messages] through the pipeline to a fresh local server with one answer ready. */
    private fun routeMessages(messages: List<Message>): Routed = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(answer())
            server.start()
            val results = RecordingSink<ModelResult>()
            val request = ModelRequest("You are a test system.", messages, listOf(addItem), 256)
            val step: StrategyStep = { _, session ->
                results.record(session.model().complete(request))
                StrategyOutcome.Completed(null)
            }
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(AnthropicProvider { baseUrl = server.url("/") })
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
                credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }
            pipeline.execute(CommandInput("add milk", "en", null))
            val count = server.requestCount
            val sent = if (count == 0) {
                null
            } else {
                val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
                body.getValue("messages").jsonArray[1].jsonObject["content"]
            }
            Routed(results.events.single(), count, sent)
        }
    }

    @Test
    fun aTurnStampedForAnotherModelOrProviderIsRefusedBeforeAnyRequest() {
        val mismatched = listOf(
            NativeReplay(ProviderId.ANTHROPIC, "claude-opus-5-5", replayContent()),
            NativeReplay(ProviderId.OPENAI, model, replayContent()),
            NativeReplay(ProviderId.ANTHROPIC, model, milkArguments()),
        )

        mismatched.forEach { stamp ->
            val routed = routeMessages(history(stamp))

            assertEquals(FailureReason.Other("replay_mismatch"), (routed.result as ModelResult.Failure).reason)
            assertEquals(0, routed.requestCount)
        }

        val matching = routeMessages(history(NativeReplay(ProviderId.ANTHROPIC, model, replayContent())))
        assertTrue(matching.result is ModelResult.Success)
        assertEquals(1, matching.requestCount)
        assertEquals(replayContent(), matching.assistantContent)

        val unstamped = routeMessages(history(null))
        assertTrue(unstamped.result is ModelResult.Success)
        assertEquals(1, unstamped.requestCount)
    }

    private fun runCatchingIllegal(block: () -> Unit): String {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("expected IllegalArgumentException")
        return ""
    }
}
