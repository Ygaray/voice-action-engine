package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicDecoderTest {

    private val model = "claude-haiku-4-5"

    private val textOnly = listOf(textBlock("hello"))

    private fun decode(body: String?, requestId: String? = null): ModelResult =
        decodeAnthropicResponse(body, requestId, model)

    private fun success(body: String, requestId: String? = null): ModelResponse =
        (decode(body, requestId) as ModelResult.Success).response

    private fun failureCode(body: String?): String = (decode(body) as ModelResult.Failure).reason.code

    private fun stopOf(value: String?): StopReason = success(successBody(textOnly, value)).stopReason

    @Test
    fun usageMapsToTheFourBucketsAndMissingCountsAreZero() {
        val full = success(successBody(textOnly, "end_turn", usageJson(10, 5, cacheCreation = 100, cacheRead = 200)))
        assertEquals(Usage(10, 200, 100, 5), full.usage)

        val noCacheCounts = usageJson(10, 5, cacheCreation = null, cacheRead = null)
        val sparse = success(successBody(textOnly, "end_turn", noCacheCounts))
        assertEquals(Usage(10, 0, 0, 5), sparse.usage)

        val nulls = success(successBody(textOnly, "end_turn", usageJson(null, 7, cacheCreation = null, cacheRead = 3)))
        assertEquals(Usage(0, 3, 0, 7), nulls.usage)

        assertEquals(Usage.ZERO, success(successBody(textOnly, "end_turn", usage = null)).usage)
    }

    @Test
    fun stopReasonsMapByName() {
        assertEquals(StopReason.END_TURN, stopOf("end_turn"))
        assertEquals(StopReason.TOOL_USE, stopOf("tool_use"))
        assertEquals(StopReason.MAX_TOKENS, stopOf("max_tokens"))
        assertEquals(StopReason.REFUSAL, stopOf("refusal"))
        assertEquals(StopReason.PAUSE_TURN, stopOf("pause_turn"))
        assertEquals(StopReason.CONTEXT_WINDOW_EXCEEDED, stopOf("model_context_window_exceeded"))
    }

    @Test
    fun otherUnknownOrMissingStopReasonsMapToOther() {
        assertEquals(StopReason.OTHER, stopOf("stop_sequence"))
        assertEquals(StopReason.OTHER, stopOf("something_new"))
        assertEquals(StopReason.OTHER, stopOf(null))
    }

    @Test
    fun partsKeepTextAndToolCallsInOrderAndNativeReplayKeepsTheWholeContentArray() {
        val args = buildJsonObject { put("item", "milk") }
        val content = listOf(textBlock("Adding."), thinkingBlock("hmm"), toolUseBlock("toolu_1", "add_item", args))

        val response = success(successBody(content, "tool_use"))

        val parts = response.message.parts
        assertEquals(2, parts.size)
        assertEquals("Adding.", (parts[0] as AssistantPart.Text).text)
        val call = parts[1] as AssistantPart.ToolCall
        assertEquals("toolu_1", call.id)
        assertEquals("add_item", call.name)
        assertEquals(args, call.arguments)
        val replay = response.message.nativeReplay
        assertNotNull(replay)
        assertEquals(ProviderId.ANTHROPIC, replay!!.provider)
        assertEquals(model, replay.model)
        assertEquals(buildJsonArray { content.forEach { add(it) } }, replay.raw)
        assertEquals(JsonArray(content), replay.raw)
    }

    @Test
    fun toolInputArrivesExactlyAsTheModelSentIt() {
        val sent = buildJsonObject { put("title", "x") }

        val response = success(successBody(listOf(toolUseBlock("toolu_2", "add_note", sent)), "tool_use"))

        val arguments = response.message.toolCalls.single().arguments
        assertEquals(setOf("title"), arguments.keys)
        assertEquals(sent, arguments)
    }

    @Test
    fun aToolInputThatIsNotAnObjectIsMalformedToolArgs() {
        val notObjects: List<JsonElement> = listOf(JsonArray(emptyList()), JsonPrimitive("text"), JsonNull)

        for (input in notObjects) {
            val body = successBody(listOf(toolUseBlock("toolu_3", "add_item", input)), "tool_use")
            assertEquals(input.toString(), "malformed_tool_args", failureCode(body))
        }
    }

    @Test
    fun theRequestIdHeaderIsKeptOnlyWhenItIsAShortSafeToken() {
        val body = successBody(textOnly, "end_turn")

        assertEquals("req_abc", success(body, "req_abc").requestId)
        assertNull(success(body, "req abc").requestId)
        assertNull(success(body, "req\"abc").requestId)
        assertNull(success(body, "r".repeat(129)).requestId)
        assertEquals("r".repeat(128), success(body, "r".repeat(128)).requestId)
        assertNull(success(body, null).requestId)
    }

    @Test
    fun anUnusableSuccessBodyIsMalformedResponseAndNeverAnException() {
        val blankToolId = successBody(listOf(toolUseBlock(" ", "add_item", buildJsonObject { })), "tool_use")
        val blankToolName = successBody(listOf(toolUseBlock("toolu_4", "", buildJsonObject { })), "tool_use")
        val bodies = listOf(
            null,
            "",
            "   ",
            "not json at all",
            "[]",
            "\"text\"",
            "{}",
            "{\"content\": \"nope\"}",
            "{\"content\": {\"type\": \"text\"}}",
            "{\"content\": [\"loose string\"]}",
            blankToolId,
            blankToolName,
        )

        for (body in bodies) {
            assertEquals(body, "malformed_response", failureCode(body))
        }
    }

    @Test
    fun aMalformedBodyNeverLeaksItsTextIntoTheFailure() {
        val bodies = listOf(
            "CANARY-BODY is not json {",
            "{\"content\": \"CANARY-BODY\"}",
            successBody(listOf(toolUseBlock("toolu_5", "CANARY-BODY", JsonPrimitive("CANARY-BODY"))), "tool_use"),
            successBody(listOf(toolUseBlock("", "CANARY-BODY", buildJsonObject { })), "tool_use"),
        )

        for (body in bodies) {
            val result = decode(body, "CANARY-BODY") as ModelResult.Failure
            assertFalse(result.toString(), result.toString().contains("CANARY-BODY"))
            assertFalse(result.reason.toString().contains("CANARY-BODY"))
            assertTrue(result.details?.requestId != "CANARY-BODY")
        }
    }
}
