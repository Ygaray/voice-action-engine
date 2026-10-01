package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageEncoderTest {

    private val model = "gpt-5.4-mini"
    private val vendor = ChatVendor.OPENAI

    private val rawReplay = Json.parseToJsonElement(
        """
        {"role":"assistant","content":"Sure","refusal":null,"annotations":[],"reasoning":"r",
         "reasoning_details":[{"type":"x"}],
         "tool_calls":[{"id":"call_1","type":"function","function":{"name":"log_food","arguments":"{\"a\":1}"}}]}
        """.trimIndent(),
    ) as JsonObject

    private val parts = listOf(
        AssistantPart.Text("Sure"),
        AssistantPart.ToolCall("call_1", "log_food", Json.parseToJsonElement("""{"b":1,"a":"x"}""") as JsonObject),
    )

    private val rebuilt = """
        {"role":"assistant","content":"Sure","tool_calls":[{"id":"call_1","type":"function",
        "function":{"name":"log_food","arguments":"{\"b\":1,\"a\":\"x\"}"}}]}
    """.trimIndent().replace("\n", "")

    private fun encode(vararg messages: Message): String {
        val request = ModelRequest("", messages.toList(), 64)
        return Json.encodeToString(
            JsonElement.serializer(),
            encodeChatMessages(chatCall(vendor, model, request), vendor),
        )
    }

    private fun assistant(replay: NativeReplay?): AssistantMessage = AssistantMessage(parts, replay)

    @Test
    fun aUserMessageIsRoleAndContent() {
        assertEquals("""[{"role":"user","content":"add milk"}]""", encode(UserMessage("add milk")))
    }

    @Test
    fun aMatchingReplayKeepsOnlyTheAllowedFieldsInStoredOrder() {
        val replay = NativeReplay(ProviderId.OPENAI, model, rawReplay)
        val expected = """
            [{"role":"assistant","content":"Sure","refusal":null,"reasoning_details":[{"type":"x"}],
            "tool_calls":[{"id":"call_1","type":"function","function":{"name":"log_food","arguments":"{\"a\":1}"}}]}]
        """.trimIndent().replace("\n", "")

        assertEquals(expected, encode(assistant(replay)))
    }

    private fun replayOf(json: String): String =
        encode(assistant(NativeReplay(ProviderId.OPENAI, model, Json.parseToJsonElement(json) as JsonObject)))

    @Test
    fun aReplayIsRepairedOnlyWhereItWouldBeInvalidInput() {
        val valid = """
            {"role":"assistant","content":"Sure","refusal":null,"annotations":[{"u":1}],
            "tool_calls":[{"id":"call_1","type":"function","function":{"name":"log_food","arguments":"{\"a\":1}"}}]}
        """.trimIndent().replace("\n", "")
        val validOut = """
            [{"role":"assistant","content":"Sure","refusal":null,
            "tool_calls":[{"id":"call_1","type":"function","function":{"name":"log_food","arguments":"{\"a\":1}"}}]}]
        """.trimIndent().replace("\n", "")
        assertEquals(validOut, replayOf(valid))

        val routed = """
            {"role":"assistant","content":null,"refusal":null,"reasoning":"r",
            "tool_calls":[{"type":"function","index":0,"id":"call_2","function":{"arguments":"{}","name":"x"}}]}
        """.trimIndent().replace("\n", "")
        val routedOut = """
            [{"role":"assistant","content":null,"refusal":null,
            "tool_calls":[{"type":"function","index":0,"id":"call_2","function":{"arguments":"{}","name":"x"}}]}]
        """.trimIndent().replace("\n", "")
        assertEquals(routedOut, replayOf(routed))

        val details = """[{"type":"t","text":"a","signature":"s","format":"f","index":0},{"type":"e","data":"d"}]"""
        val withDetails = """{"role":"assistant","content":"x","reasoning_details":$details}"""
        assertEquals("""[{"role":"assistant","content":"x","reasoning_details":$details}]""", replayOf(withDetails))

        assertEquals("""[{"role":"assistant","content":"x"}]""", replayOf("""{"content":"x","annotations":[]}"""))

        for (empty in listOf("\"\"", "\" \"", "\"null\"", "null")) {
            val call = """{"id":"c","type":"function","function":{"name":"n","arguments":$empty}}"""
            val out = replayOf("""{"role":"assistant","content":null,"tool_calls":[$call]}""")
            val fixed = """{"id":"c","type":"function","function":{"name":"n","arguments":"{}"}}"""
            assertEquals(empty, """[{"role":"assistant","content":null,"tool_calls":[$fixed]}]""", out)
        }

        val absent = """{"id":"c","type":"function","function":{"name":"n"},"index":3}"""
        val mixed = """{"id":"d","type":"function","function":{"name":"m","arguments":"{\"k\":2}"}}"""
        val absentOut = """{"id":"c","type":"function","function":{"name":"n","arguments":"{}"},"index":3}"""
        assertEquals(
            """[{"role":"assistant","content":null,"tool_calls":[$absentOut,$mixed]}]""",
            replayOf("""{"role":"assistant","content":null,"tool_calls":[$absent,$mixed]}"""),
        )
    }

    @Test
    fun anObjectFormArgumentsValueGoesBackAsItsCompactTextInKeyOrder() {
        val objectCall =
            """{"id":"c","type":"function","function":{"name":"n","arguments":{"b": 1, "a": {"z":[1, 2]}}}}"""
        val stringCall =
            """{"id":"c","type":"function","function":{"name":"n","arguments":"{\"b\":1,\"a\":{\"z\":[1,2]}}"}}"""
        val out = replayOf("""{"role":"assistant","content":null,"tool_calls":[$objectCall]}""")
        assertEquals("""[{"role":"assistant","content":null,"tool_calls":[$stringCall]}]""", out)

        val emptyObject = """{"id":"c","type":"function","function":{"name":"n","arguments":{}}}"""
        val emptyOut = """{"id":"c","type":"function","function":{"name":"n","arguments":"{}"}}"""
        assertEquals(
            """[{"role":"assistant","content":null,"tool_calls":[$emptyOut]}]""",
            replayOf("""{"role":"assistant","content":null,"tool_calls":[$emptyObject]}"""),
        )
        assertEquals(out, replayOf("""{"role":"assistant","content":null,"tool_calls":[$objectCall]}"""))
    }

    @Test
    fun theRepairIsDeterministic() {
        val call = """{"id":"c","type":"function","function":{"name":"n","arguments":""}}"""
        val raw = """{"content":null,"tool_calls":[$call]}"""
        assertEquals(replayOf(raw), replayOf(raw))
        assertTrue(replayOf(raw).startsWith("""[{"role":"assistant","content":null"""))
    }

    @Test
    fun aReplayFromAnotherVendorProviderOrModelIsRefusedAndANullOneIsRebuilt() {
        val others = listOf(
            NativeReplay(ProviderId.OPENROUTER, model, rawReplay),
            NativeReplay(ProviderId.ANTHROPIC, model, rawReplay),
            NativeReplay(ProviderId.OPENAI, "gpt-4o-mini", rawReplay),
            NativeReplay(ProviderId.OPENAI, model, JsonArray(emptyList())),
        )

        assertEquals("[$rebuilt]", encode(assistant(null)))
        others.forEach { replay ->
            val refused = assertThrows(IllegalStateException::class.java) { encode(assistant(replay)) }

            assertFalse(refused.message.orEmpty().contains(model))
            assertFalse(refused.message.orEmpty().contains("gpt-4o-mini"))
        }
    }

    @Test
    fun rebuiltToolCallArgumentsKeepTheModelsKeyOrderAsACompactString() {
        assertTrue(encode(assistant(null)).contains("""\"b\":1,\"a\":\"x\""""))
    }

    @Test
    fun aTurnOfOnlyToolCallsHasNullContentAndEmptyTextIsSkipped() {
        val call = parts.last()
        val message = AssistantMessage(listOf(AssistantPart.Text(""), call))

        val encoded = encode(message)

        assertTrue(encoded.startsWith("""[{"role":"assistant","content":null,"tool_calls":["""))
    }

    @Test
    fun aTurnOfOnlyTextOmitsToolCallsAndJoinsTextPartsWithANewline() {
        val message = AssistantMessage(listOf(AssistantPart.Text("one"), AssistantPart.Text("two")))

        assertEquals("""[{"role":"assistant","content":"one\ntwo"}]""", encode(message))
    }

    @Test
    fun anEmptyTurnHasEmptyContentAndNoToolCalls() {
        assertEquals("""[{"role":"assistant","content":""}]""", encode(AssistantMessage(emptyList())))
    }

    @Test
    fun eachToolResultIsItsOwnToolMessageInCallOrderAndAnErrorIsWrapped() {
        // Deliberate reversal: the earlier pinned behaviour sent an error's text unchanged. The dialect has no error
        // flag, so an error result is wrapped as an object and every other result goes out as the app produced it.
        val results = ToolResultsMessage(
            listOf(ToolResult("call_2", "failed", true), ToolResult("call_1", "done")),
        )

        val encoded = encode(callsOf("call_1", "call_2"), results)

        assertTrue(
            encoded.endsWith(
                """{"role":"tool","tool_call_id":"call_1","content":"done"},""" +
                    """{"role":"tool","tool_call_id":"call_2","content":"{\"error\":\"failed\"}"}]""",
            ),
        )
        assertFalse(encoded.contains("is_error"))
    }

    private fun callsOf(vararg ids: String): AssistantMessage =
        AssistantMessage(ids.map { AssistantPart.ToolCall(it, "log_food", JsonObject(emptyMap())) })

    private fun toolMessages(vararg messages: Message): List<JsonObject> =
        (Json.parseToJsonElement(encode(*messages)) as JsonArray).map { it as JsonObject }
            .filter { it.getValue("role").jsonPrimitive.content == "tool" }

    private fun contentOf(message: JsonObject): String = message.getValue("content").jsonPrimitive.content

    @Test
    fun resultsAnsweredOutOfOrderAreSentInTheOrderOfTheCalls() {
        val answered = ToolResultsMessage(
            listOf(ToolResult("call_3", "c"), ToolResult("call_1", "a"), ToolResult("call_2", "b")),
        )

        val tools = toolMessages(callsOf("call_1", "call_2", "call_3"), answered)

        assertEquals(
            listOf("call_1", "call_2", "call_3"),
            tools.map { it.getValue("tool_call_id").jsonPrimitive.content },
        )
        assertEquals(listOf("a", "b", "c"), tools.map { contentOf(it) })
    }

    @Test
    fun aHeldForConfirmationResultIsSentByteForByteUnchanged() {
        val held = """{"applied":false,"status":"held_for_confirmation"}"""

        val tools = toolMessages(callsOf("call_1"), ToolResultsMessage(listOf(ToolResult("call_1", held))))

        assertEquals(held, contentOf(tools.single()))
    }

    @Test
    fun anErrorTextWithQuoteBackslashAndNewlineParsesBackToTheOriginal() {
        val text = "he said \"no\" at C:\\tmp\nline two"

        val tools = toolMessages(callsOf("call_1"), ToolResultsMessage(listOf(ToolResult("call_1", text, true))))

        val wrapper = Json.parseToJsonElement(contentOf(tools.single())) as JsonObject
        assertEquals(setOf("error"), wrapper.keys)
        assertEquals(text, wrapper.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun anEmptyResultIsSentAsAnEmptyStringAndAnEmptyErrorIsStillWrapped() {
        val results = ToolResultsMessage(listOf(ToolResult("call_1", ""), ToolResult("call_2", "", true)))

        val tools = toolMessages(callsOf("call_1", "call_2"), results)

        assertEquals("", contentOf(tools[0]))
        assertEquals("""{"error":""}""", contentOf(tools[1]))
    }

    @Test
    fun aConversationKeepsItsOrderWithToolMessagesBetweenTheTurns() {
        val calls = AssistantMessage(
            listOf(
                AssistantPart.ToolCall("c1", "a", JsonObject(emptyMap())),
                AssistantPart.ToolCall("c2", "b", JsonObject(emptyMap())),
            ),
        )
        val encoded = encode(
            UserMessage("first"),
            calls,
            ToolResultsMessage(listOf(ToolResult("c1", "r1"), ToolResult("c2", "r2"))),
            UserMessage("second"),
        )
        val roles = (Json.parseToJsonElement(encoded) as JsonArray)
            .map { (it as JsonObject).getValue("role").toString() }

        assertEquals(listOf("\"user\"", "\"assistant\"", "\"tool\"", "\"tool\"", "\"user\""), roles)
    }
}
