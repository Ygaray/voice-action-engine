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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun aReplayFromAnotherVendorProviderOrModelIsRebuilt() {
        val others = listOf(
            NativeReplay(ProviderId.OPENROUTER, model, rawReplay),
            NativeReplay(ProviderId.ANTHROPIC, model, rawReplay),
            NativeReplay(ProviderId.OPENAI, "gpt-4o-mini", rawReplay),
            null,
        )

        others.forEach { replay ->
            assertEquals("[$rebuilt]", encode(assistant(replay)))
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
    fun eachToolResultIsItsOwnToolMessageInOrderAndErrorsAreSentUnchanged() {
        val results = ToolResultsMessage(
            listOf(ToolResult("call_1", "done"), ToolResult("call_2", "failed", true)),
        )

        val encoded = encode(results)

        assertEquals(
            """[{"role":"tool","tool_call_id":"call_1","content":"done"},""" +
                """{"role":"tool","tool_call_id":"call_2","content":"failed"}]""",
            encoded,
        )
        assertFalse(encoded.contains("is_error"))
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
