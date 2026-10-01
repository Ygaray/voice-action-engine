package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A required tool: forced where the model accepts it, reshaped where it does not. */
class AnthropicForcedToolTest {

    private val system = "You are a test system."

    private fun eligibleSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("item") { put("type", "string") } }
        putJsonArray("required") { add("item") }
        put("additionalProperties", false)
    }

    private val addItem = ToolSpec("add_item", "Adds one item.", eligibleSchema())

    private fun requiredRequest(): ModelRequest = ModelRequest(
        system,
        listOf(UserMessage("add milk")),
        listOf(addItem),
        ToolChoice.Required("add_item"),
        256,
        CacheDirective(true),
    )

    private fun toolAnswer(): MockResponse = MockResponse().setResponseCode(200).setBody(
        successBody(
            listOf(toolUseBlock("toolu_1", "add_item", buildJsonObject { put("item", "milk") })),
            "tool_use",
        ),
    )

    private class Sent(val result: ModelResult, val requestCount: Int, val bodyText: String) {
        val body: JsonObject = Json.parseToJsonElement(bodyText).jsonObject
    }

    /** Runs the request through a real pipeline against one tool answer; [override] patches the capabilities. */
    private fun throughPipeline(model: String, override: Boolean): Sent = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.start()
            val results = RecordingSink<ModelResult>()
            val step: StrategyStep = { _, session ->
                results.record(session.model().complete(requiredRequest()))
                StrategyOutcome.Completed(null)
            }
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(AnthropicProvider { baseUrl = server.url("/") })
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
                credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                if (override) capabilities(ProviderId.ANTHROPIC, model) { supportsForcedToolChoice = true }
            }

            pipeline.execute(CommandInput("add milk", "en", null))

            Sent(results.events.single(), server.requestCount, server.takeRequest().body.readUtf8())
        }
    }

    private fun Sent.lastContentBlock(): JsonObject =
        body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.last().jsonObject

    private fun assertToolCallReturned(result: ModelResult) {
        val calls = (result as ModelResult.Success).response.message.toolCalls
        assertEquals(listOf("add_item"), calls.map { it.name })
    }

    @Test(timeout = 30_000)
    fun aModelThatRejectsForcedToolChoiceGetsAutoStrictAndTheInstructionOnTheFirstRequest() {
        val sent = throughPipeline("claude-opus-5-5", override = false)

        assertEquals(1, sent.requestCount)
        assertEquals(buildJsonObject { put("type", "auto") }, sent.body["tool_choice"])
        val tool = sent.body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("true", tool["strict"]!!.jsonPrimitive.content)
        assertEquals(eligibleSchema(), tool["input_schema"])
        val last = sent.lastContentBlock()
        assertEquals("text", last["type"]!!.jsonPrimitive.content)
        assertEquals("Call the add_item tool with your result.", last["text"]!!.jsonPrimitive.content)
        val unshaped = encodeAnthropicRequest(anthropicRequest("claude-opus-5-5", requiredRequest(), "k"), false)
        assertEquals(
            Json.parseToJsonElement(unshaped.toString(Charsets.UTF_8)).jsonObject["system"].toString(),
            sent.body["system"].toString(),
        )
        assertFalse(sent.body["system"].toString().contains("Call the add_item"))
        assertEquals(1, Regex("cache_control").findAll(sent.bodyText).count())
        assertToolCallReturned(sent.result)
    }

    @Test(timeout = 30_000)
    fun anAppOverrideThatAllowsForcingSendsTheForcedChoiceWithNoInstructionAndNoStrict() {
        val sent = throughPipeline("claude-opus-5-5", override = true)

        assertEquals(1, sent.requestCount)
        assertEquals(
            buildJsonObject {
                put("type", "tool")
                put("name", "add_item")
            },
            sent.body["tool_choice"],
        )
        assertFalse(sent.bodyText.contains("Call the add_item tool"))
        assertFalse(sent.bodyText.contains("\"strict\""))
        assertToolCallReturned(sent.result)
    }

    @Test(timeout = 30_000)
    fun aModelTheTableSaysAcceptsForcingIsForcedWithoutOverride() {
        val sent = throughPipeline("claude-haiku-4-5", override = false)

        assertEquals(1, sent.requestCount)
        assertEquals("tool", sent.body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(sent.bodyText.contains("Call the add_item tool"))
        assertFalse(sent.bodyText.contains("\"strict\""))
        assertTrue(sent.result is ModelResult.Success)
    }
}
