package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one multi-turn conformance suite. A subclass names a [WireDialect]; every test then runs over every manifest row
 * of that dialect, whatever its provenance. The suite asserts through production functions and the real provider only.
 * Assertion messages name the case, request and turn, never a body, message text or argument.
 */
internal abstract class MultiTurnConformanceSuite {

    protected abstract val dialect: WireDialect

    protected fun rows(): List<ConversationRow> = conversationRows().filter { it.dialect == dialect.name }

    protected fun turnsOf(row: ConversationRow): List<ConversationTurn> = parseConversation(conversationText(row))

    /** Each turn's response decoded the way the transport decodes it. */
    protected fun assistantTurns(row: ConversationRow, turns: List<ConversationTurn>): List<AssistantMessage> =
        turns.mapIndexed { index, turn ->
            val result = dialect.decode(canonicalJson(turn.response.toString()), row.model)
            val success = result as? ModelResult.Success
            checkNotNull(success) { "${row.case}: turn ${index + 1} did not decode" }.response.message
        }

    @Test
    fun theDialectHasAtLeastOneFixture() {
        assertTrue("${dialect.name}: the manifest has no row for this dialect", rows().isNotEmpty())
    }

    @Test
    fun everyFixtureConversationRoundTrips() {
        for (row in rows()) {
            val turns = turnsOf(row)
            val replay = replayConversation(dialect, row, turns)
            assertEquals("${row.case}: ${replay.violations}", emptyList<String>(), replay.violations)
            assertEquals("${row.case}: one request per turn", turns.size, replay.bodies.size)
            val assistants = assistantTurns(row, turns)
            assertTrue("${row.case}: the last turn asks for tool calls", assistants.last().toolCalls.isEmpty())
            if ("parallel" in row.tags) {
                assertTrue("${row.case}: no turn makes parallel calls", assistants.any { it.toolCalls.size > 1 })
            }
        }
    }

    @Test
    fun toolResultsAreEncodedPerDialect() {
        for (row in rows()) {
            val turns = turnsOf(row)
            val assistants = assistantTurns(row, turns)
            val replay = replayConversation(dialect, row, turns)
            assertTrue("${row.case}: the replay failed", replay.violations.isEmpty())
            replay.bodies.forEachIndexed { request, body ->
                val messages = sentMessages(body)
                val indices = dialect.assistantWireIndices(messages)
                assertEquals("${row.case}: request ${request + 1} assistant turns", request, indices.size)
                indices.forEachIndexed { turn, at ->
                    val label = "${row.case}: request ${request + 1}, turn ${turn + 1}"
                    checkResults(label, assistants[turn], messages, at, indices.getOrNull(turn + 1) ?: messages.size)
                }
            }
        }
    }

    private fun checkResults(label: String, assistant: AssistantMessage, messages: JsonArray, at: Int, next: Int) {
        val calls = assistant.toolCalls
        assertEquals("$label: result message count", dialect.resultMessageCount(calls.size), next - at - 1)
        val wires = dialect.toolResultWires(messages, at)
        assertEquals("$label: result ids in call order", calls.map { it.id }, wires.map { it.callId })
        calls.zip(wires).forEach { (call, wire) ->
            val expected = ConversationScript.resultFor(call)
            assertEquals("$label: error flag of ${call.name}", expected.isError, wire.isError)
            if (!expected.isError) assertEquals("$label: content of ${call.name}", expected.content, wire.content)
        }
    }
}
