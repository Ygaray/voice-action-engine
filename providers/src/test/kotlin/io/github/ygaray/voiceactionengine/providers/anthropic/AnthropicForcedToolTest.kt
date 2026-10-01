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
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

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

    private fun requiredRequest(singleToolCall: Boolean = false): ModelRequest = ModelRequest(
        system,
        listOf(UserMessage("add milk")),
        listOf(addItem),
        ToolChoice.Required("add_item"),
        256,
        CacheDirective(true),
        singleToolCall,
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
    private fun throughPipeline(model: String, override: Boolean, singleToolCall: Boolean = false): Sent = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.start()
            val results = RecordingSink<ModelResult>()
            val step: StrategyStep = { _, session ->
                results.record(session.model().complete(requiredRequest(singleToolCall)))
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

    @Test(timeout = 30_000)
    fun aSingleToolCallForcedRequestCarriesTheParallelOffSwitchInsideToolChoice() {
        val sent = throughPipeline("claude-opus-5-5", override = true, singleToolCall = true)

        assertEquals(1, sent.requestCount)
        assertEquals(
            buildJsonObject {
                put("type", "tool")
                put("name", "add_item")
                put("disable_parallel_tool_use", true)
            },
            sent.body["tool_choice"],
        )
        assertEquals(1, Regex("disable_parallel_tool_use").findAll(sent.bodyText).count())
        assertToolCallReturned(sent.result)
    }

    @Test(timeout = 30_000)
    fun aSingleToolCallReshapedRequestKeepsTheSwitchAndTheInstructionLine() {
        val sent = throughPipeline("claude-opus-5-5", override = false, singleToolCall = true)

        assertEquals(1, sent.requestCount)
        assertEquals(
            buildJsonObject {
                put("type", "auto")
                put("disable_parallel_tool_use", true)
            },
            sent.body["tool_choice"],
        )
        val last = sent.lastContentBlock()
        assertEquals("Call the add_item tool with your result.", last["text"]!!.jsonPrimitive.content)
        assertToolCallReturned(sent.result)
    }

    private val unknownModel = "claude-new-model"

    private fun toolChoiceRejection(): MockResponse = MockResponse().setResponseCode(400).setBody(
        errorBody(
            "invalid_request_error",
            "tool_choice: type \"tool\" and \"any\" are not supported for this model.",
            "req_tc_1",
        ),
    )

    private fun otherBadRequest(): MockResponse = MockResponse().setResponseCode(400).setBody(
        errorBody("invalid_request_error", "messages: text content blocks must be non-empty", "req_bad_1"),
    )

    private fun status(code: Int): MockResponse =
        MockResponse().setResponseCode(code).setBody(errorBody("api_error", "boom", "req_s_1"))

    private class Run(
        val result: ModelResult,
        val requestCount: Int,
        val bodies: List<JsonObject>,
        val attempts: List<AnthropicAttempt>,
        val waits: List<Long>,
    )

    /** Calls the provider directly; [responses] are served in order, then a sentinel 200 so an extra request counts. */
    private fun run(model: String, request: ModelRequest, vararg responses: MockResponse): Run = runBlocking {
        MockWebServer().use { server ->
            responses.forEach { server.enqueue(it) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.start()
            val attempts = CopyOnWriteArrayList<AnthropicAttempt>()
            val waits = CopyOnWriteArrayList<Long>()
            val provider = AnthropicProvider {
                baseUrl = server.url("/")
                callTimeoutMillis = 2_000
                sleep = { waits.add(it) }
                attemptObserver = AnthropicAttemptObserver { attempts.add(it) }
            }
            val result = provider.complete(anthropicRequest(model, request, "sk-test-key"))
            val count = server.requestCount
            val bodies = List(count) { Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject }
            Run(result, count, bodies, attempts.toList(), waits.toList())
        }
    }

    private fun Run.code(): String = (result as ModelResult.Failure).reason.code

    private fun Run.kinds(): List<String> = attempts.map { it.kind.value }

    private fun JsonObject.isForced(): Boolean =
        this["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content == "tool"

    private fun JsonObject.hasInstruction(): Boolean = toString().contains("Call the add_item tool with your result.")

    @Test(timeout = 30_000)
    fun anUnknownModelThatRejectsForcingIsResentOnceReshapedAndSucceeds() {
        val run = run(unknownModel, requiredRequest(), toolChoiceRejection(), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(2, run.requestCount)
        assertTrue(run.bodies[0].isForced())
        assertFalse(run.bodies[0].hasInstruction())
        assertEquals("auto", run.bodies[1]["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(run.bodies[1].hasInstruction())
        assertEquals(
            listOf(
                AnthropicAttempt(1, AnthropicAttemptKind.INITIAL, 400),
                AnthropicAttempt(2, AnthropicAttemptKind.FORCED_TOOL_RESHAPE, 200),
            ),
            run.attempts,
        )
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun aForcedRequestRejectedForAnotherReasonIsAFinalHttpError() {
        val run = run(unknownModel, requiredRequest(), otherBadRequest(), toolAnswer())

        assertEquals("http_error", run.code())
        assertEquals(1, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun anAutoRequestWhoseRejectionMentionsToolChoiceIsAFinalHttpError() {
        val auto = ModelRequest(system, listOf(UserMessage("add milk")), listOf(addItem), 256)

        val run = run(unknownModel, auto, toolChoiceRejection(), toolAnswer())

        assertEquals("http_error", run.code())
        assertEquals(1, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun aRequestTheTableAlreadyReshapedIsNotReshapedAgain() {
        val run = run("claude-opus-5-5", requiredRequest(), toolChoiceRejection(), toolAnswer())

        assertEquals("http_error", run.code())
        assertEquals(1, run.requestCount)
        assertEquals(listOf("initial"), run.kinds())
    }

    @Test(timeout = 30_000)
    fun aToolChoiceMessageOnAnotherStatusIsNotReshaped() {
        val unauthorized = MockResponse().setResponseCode(401).setBody(
            errorBody("authentication_error", "tool_choice and a bad key", "req_u_1"),
        )

        val run = run(unknownModel, requiredRequest(), unauthorized, toolAnswer())

        assertEquals("auth", run.code())
        assertEquals(1, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun aReshapeThenATransientRetrySucceedsWithinThreeRequests() {
        val run = run(unknownModel, requiredRequest(), toolChoiceRejection(), status(529), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(3, run.requestCount)
        assertEquals(listOf("initial", "forced_tool_reshape", "transient_retry"), run.kinds())
        assertEquals(listOf(500L), run.waits)
        assertTrue(run.bodies[2].hasInstruction())
    }

    @Test(timeout = 30_000)
    fun aTransientRetryThenAReshapeSucceedsWithinThreeRequests() {
        val run = run(unknownModel, requiredRequest(), status(529), toolChoiceRejection(), toolAnswer())

        assertTrue(run.result is ModelResult.Success)
        assertEquals(3, run.requestCount)
        assertEquals(listOf("initial", "transient_retry", "forced_tool_reshape"), run.kinds())
        assertEquals(listOf(500L), run.waits)
        assertTrue(run.bodies[1].isForced())
        assertTrue(run.bodies[2].hasInstruction())
    }

    @Test(timeout = 30_000)
    fun aSecondRejectionAfterTheReshapeIsFinalAndNeverReshapedAgain() {
        val run = run(unknownModel, requiredRequest(), toolChoiceRejection(), toolChoiceRejection(), toolAnswer())

        assertEquals("http_error", run.code())
        assertEquals(2, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun noCombinationOfRetryAndReshapeSendsAFourthRequest() {
        val run = run(unknownModel, requiredRequest(), status(529), toolChoiceRejection(), status(529), toolAnswer())

        assertEquals("overloaded", run.code())
        assertEquals(3, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun nothingIsRememberedBetweenCalls() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) {
                server.enqueue(toolChoiceRejection())
                server.enqueue(toolAnswer())
            }
            server.start()
            val provider = AnthropicProvider { baseUrl = server.url("/") }
            val call = anthropicRequest(unknownModel, requiredRequest(), "sk-test-key")

            assertTrue(provider.complete(call) is ModelResult.Success)
            assertTrue(provider.complete(call) is ModelResult.Success)

            assertEquals(4, server.requestCount)
        }
    }

    private fun textAnswer(stopReason: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(successBody(listOf(textBlock("Sure.")), stopReason))

    private fun autoRequest(): ModelRequest =
        ModelRequest(system, listOf(UserMessage("add milk")), listOf(addItem), 256)

    @Test(timeout = 30_000)
    fun aReshapedRequiredRequestAnsweredWithTextOnlyIsNoToolCallAndNeverRetried() {
        val run = run("claude-opus-5-5", requiredRequest(), textAnswer("end_turn"), toolAnswer())

        assertEquals("no_tool_call", run.code())
        assertNull((run.result as ModelResult.Failure).details)
        assertEquals(1, run.requestCount)
        assertTrue(run.waits.isEmpty())
    }

    @Test(timeout = 30_000)
    fun aNativelyForcedRequestAnsweredWithTextOnlyIsNoToolCall() {
        val run = run("claude-haiku-4-5", requiredRequest(), textAnswer("end_turn"), toolAnswer())

        assertEquals("no_tool_call", run.code())
        assertEquals(1, run.requestCount)
    }

    @Test(timeout = 30_000)
    fun anAutoRequestAnsweredWithTextIsASuccess() {
        val run = run("claude-haiku-4-5", autoRequest(), textAnswer("end_turn"))

        assertEquals(StopReason.END_TURN, (run.result as ModelResult.Success).response.stopReason)
    }

    @Test(timeout = 30_000)
    fun aRefusalOrATruncationOfARequiredRequestStaysASuccessWithItsStopReason() {
        val refused = run("claude-opus-5-5", requiredRequest(), textAnswer("refusal"))
        val truncated = run("claude-haiku-4-5", requiredRequest(), textAnswer("max_tokens"))

        assertEquals(StopReason.REFUSAL, (refused.result as ModelResult.Success).response.stopReason)
        assertEquals(StopReason.MAX_TOKENS, (truncated.result as ModelResult.Success).response.stopReason)
    }

    @Test(timeout = 30_000)
    fun aReshapeFollowedByATextOnlyAnswerIsNoToolCallAfterTwoRequests() {
        val run = run(unknownModel, requiredRequest(), toolChoiceRejection(), textAnswer("end_turn"))

        assertEquals("no_tool_call", run.code())
        assertEquals(2, run.requestCount)
    }
}
