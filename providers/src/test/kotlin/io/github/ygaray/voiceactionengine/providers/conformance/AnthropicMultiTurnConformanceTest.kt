package io.github.ygaray.voiceactionengine.providers.conformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conformance suite bound to the Anthropic Messages mapper, plus checks that only these fixtures can make. */
internal class AnthropicMultiTurnConformanceTest : MultiTurnConformanceSuite() {

    override val dialect: WireDialect = AnthropicWire

    private fun row(case: String): ConversationRow = rows().single { it.case == case }

    @Test
    fun theParallelFixtureAnswersFourCallsInOneTurn() {
        val row = row("derived_parallel_tools")
        val assistants = assistantTurns(row, turnsOf(row))
        assertEquals(listOf(4, 0), assistants.map { it.toolCalls.size })
        val names = assistants[0].toolCalls.map { it.name }
        assertEquals(listOf("record_item", "record_item", "count_items", "lookup_item"), names)
        assertTrue("the zero-argument call has no arguments", assistants[0].toolCalls[2].arguments.isEmpty())
    }

    @Test
    fun aBrokenGoldenIsReportedByTurn() {
        val row = row("derived_parallel_tools")
        val broken = conversationText(row).replace("\"content\":\"ok\"", "\"content\":\"changed\"")
        assertTrue("the edit did not change the golden", broken != conversationText(row))
        val replay = replayConversation(dialect, row, parseConversation(broken))
        assertTrue("the broken golden went unnoticed", replay.violations.any { it.startsWith("turn 2") })
    }
}
