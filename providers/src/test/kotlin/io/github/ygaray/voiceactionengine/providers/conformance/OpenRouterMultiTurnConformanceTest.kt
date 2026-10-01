package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.providers.chat.ChatVendor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conformance suite bound to OpenRouter Chat Completions; no assertion differs from the other bindings'. */
internal class OpenRouterMultiTurnConformanceTest : MultiTurnConformanceSuite() {

    private val chat = ChatWire(ChatVendor.OPENROUTER, "openrouter")

    override val dialect: WireDialect = chat

    private fun row(case: String): ConversationRow = rows().single { it.case == case }

    private fun firstReplay(row: ConversationRow): Pair<JsonObject, JsonObject> {
        val turns = turnsOf(row)
        val stored = chat.storedReplay(turns[0].response) as JsonObject
        val replayed = chat.assistantWire(turns[1].messages[2] as JsonObject) as JsonObject
        return stored to replayed
    }

    @Test
    fun theParallelFixtureKeepsEachCallsIndexAndKeyOrderAndDropsTheReasoningField() {
        val (stored, replayed) = firstReplay(row("derived_parallel_tools"))
        assertEquals(listOf("role", "content", "refusal", "reasoning", "tool_calls"), stored.keys.toList())
        assertEquals(listOf("role", "content", "refusal", "tool_calls"), replayed.keys.toList())
        val calls = (replayed["tool_calls"] as JsonArray).map { it as JsonObject }
        assertEquals(listOf(0, 1, 2, 3), calls.map { (it["index"] as JsonPrimitive).content.toInt() })
        calls.forEach { assertEquals(listOf("type", "index", "id", "function"), it.keys.toList()) }
        assertEquals(chat.projection(stored), replayed)
    }

    @Test
    fun theReasoningDetailsAreKeptWholeAndInOrderWhileTheReasoningStringIsDropped() {
        val row = row("derived_reasoning_details")
        val turns = turnsOf(row)
        val (stored, replayed) = firstReplay(row)
        val storedKeys = listOf("role", "content", "refusal", "reasoning", "reasoning_details", "tool_calls")
        assertEquals(storedKeys, stored.keys.toList())
        assertEquals(storedKeys - "reasoning", replayed.keys.toList())
        assertEquals(stored["reasoning_details"], replayed["reasoning_details"])
        val details = canonicalJson(checkNotNull(stored["reasoning_details"]).toString())
        val bodies = replayConversation(dialect, row, turns).bodies
        assertTrue("the details are missing from request 2", details in bodies[1])
        assertFalse("the reasoning string was echoed", "\"reasoning\":" in bodies[1])
    }
}
