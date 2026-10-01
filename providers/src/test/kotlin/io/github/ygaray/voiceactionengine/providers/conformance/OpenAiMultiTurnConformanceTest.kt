package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.providers.chat.ChatVendor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EMPTY_ARGS_CASE = "derived_empty_args_forms"

/** The conformance suite bound to OpenAI Chat Completions; no assertion differs from the Anthropic binding's. */
internal class OpenAiMultiTurnConformanceTest : MultiTurnConformanceSuite() {

    private val chat = ChatWire(ChatVendor.OPENAI, "openai")

    override val dialect: WireDialect = chat

    private fun row(case: String): ConversationRow = rows().single { it.case == case }

    private fun calls(message: JsonObject): List<JsonObject> =
        (message["tool_calls"] as JsonArray).map { (it as JsonObject)["function"] as JsonObject }

    @Test
    fun theParallelFixtureAnswersFourCallsAndReplaysTheAllowlistProjection() {
        val row = row("derived_parallel_tools")
        val turns = turnsOf(row)
        val assistants = assistantTurns(row, turns)
        assertEquals(listOf(4, 0), assistants.map { it.toolCalls.size })
        assertTrue("the zero-argument call has no arguments", assistants[0].toolCalls[2].arguments.isEmpty())
        val stored = chat.storedReplay(turns[0].response) as JsonObject
        val replayed = chat.assistantWire(turns[1].messages[2] as JsonObject) as JsonObject
        assertTrue("the response-only annotations key was not stored", "annotations" in stored)
        assertEquals(listOf("role", "content", "tool_calls", "refusal"), replayed.keys.toList())
        assertEquals(chat.projection(stored), replayed)
    }

    @Test
    fun emptyArgumentsFormsDecodeAsEmptyObjectsAndStayAsReceivedInTheStoredTurn() {
        val row = row(EMPTY_ARGS_CASE)
        val turns = turnsOf(row)
        val assistants = assistantTurns(row, turns)
        assertEquals(listOf("count_items", "record_item", "count_items"), assistants[0].toolCalls.map { it.name })
        assertTrue(assistants[0].toolCalls[0].arguments.isEmpty())
        assertTrue(assistants[0].toolCalls[2].arguments.isEmpty())
        val stored = calls(chat.storedReplay(turns[0].response) as JsonObject)
        assertEquals(JsonPrimitive(""), stored[0]["arguments"])
        assertTrue("the absent arguments key was rewritten", "arguments" !in stored[2])
    }

    @Test
    fun theRepairedGoldenDiffersFromThePlainProjectionOnlyInTheEmptyArguments() {
        val row = row(EMPTY_ARGS_CASE)
        val turns = turnsOf(row)
        val stored = chat.storedReplay(turns[0].response) as JsonObject
        val replayed = chat.assistantWire(turns[1].messages[2] as JsonObject) as JsonObject
        val plain = chat.projection(stored)
        assertEquals(plain.keys, replayed.keys)
        for (key in plain.keys - "tool_calls") assertEquals("field $key", plain[key], replayed[key])
        val before = calls(plain)
        val after = calls(replayed)
        assertEquals(before.size, after.size)
        before.zip(after).forEachIndexed { index, (was, now) ->
            assertEquals("call ${index + 1} name", was["name"], now["name"])
            val expected = if (index == 1) was["arguments"] else JsonPrimitive("{}")
            assertEquals("call ${index + 1} arguments", expected, now["arguments"])
        }
        assertEquals("the absent key is appended last", listOf("name", "arguments"), after[2].keys.toList())
    }

    @Test
    fun aGoldenThatDoesNotRepairTheEmptyArgumentsIsReportedForThatTurn() {
        val row = row(EMPTY_ARGS_CASE)
        val text = conversationText(row)
        val bodies = replayConversation(dialect, row, parseConversation(text)).bodies
        val broken = text.replace("\"arguments\":\"{}\"", "\"arguments\":\"\"")
        assertTrue("the edit did not change the golden", broken != text)
        val found = verbatimViolations(dialect, parseConversation(broken), bodies, broken)
        assertTrue("the unrepaired golden went unnoticed", found.any { it.startsWith("turn 1") })
    }
}
