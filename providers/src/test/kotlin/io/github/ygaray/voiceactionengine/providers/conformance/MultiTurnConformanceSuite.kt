package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    @Test
    fun everyAssistantTurnIsReplayedByteForByte() {
        for (row in rows()) {
            val turns = turnsOf(row)
            val replay = replayConversation(dialect, row, turns)
            assertTrue("${row.case}: the replay failed", replay.violations.isEmpty())
            val found = verbatimViolations(dialect, turns, replay.bodies, conversationText(row))
            assertEquals("${row.case}: $found", emptyList<String>(), found)
        }
    }

    @Test
    fun aStampForAnotherProviderOrModelFailsBeforeAnyRequest() {
        val (row, turn) = firstToolTurn()
        val stamp = checkNotNull(turn.nativeReplay)
        val refused = listOf(
            "other provider" to NativeReplay(dialect.otherProviderId, stamp.model, stamp.raw),
            "other model" to NativeReplay(stamp.provider, stamp.model + "-other", stamp.raw),
            "other shape" to NativeReplay(stamp.provider, stamp.model, dialect.rawOfOtherShape()),
        )
        for ((variant, replay) in refused) {
            val sent = send(dialect, row, afterTurn(AssistantMessage(turn.parts, replay), turn))
            assertEquals("$variant: reason", FailureReason.Other("replay_mismatch"), sent.reason)
            assertEquals("$variant: requests", 0, sent.requests)
        }
        for ((variant, replay) in listOf("original stamp" to stamp, "unstamped copy" to null)) {
            val sent = send(dialect, row, afterTurn(AssistantMessage(turn.parts, replay), turn))
            assertTrue("$variant: not a success", sent.result is ModelResult.Success)
            assertEquals("$variant: requests", 1, sent.requests)
        }
    }

    @Test
    fun toolCallCoverageIsEnforcedBeforeAnyRequest() {
        val (row, turn) = firstToolTurn()
        val results = ConversationScript.results(turn).results
        assertTrue("${row.case}: the first turn needs two calls", results.size > 1)
        val first = turn.toolCalls.first()
        val twice = AssistantMessage(listOf(first, AssistantPart.ToolCall(first.id, first.name, first.arguments)))
        val variants = listOf(
            Triple("a missing result", "tool_result_missing", listOf(turn, ToolResultsMessage(results.dropLast(1)))),
            Triple("an extra result", "tool_result_unexpected", listOf(turn, extraResult(results))),
            Triple("a dangling turn", "tool_call_unanswered", listOf(turn)),
            Triple("results after the prompt", "tool_result_unexpected", listOf(ToolResultsMessage(results))),
            Triple("duplicate call ids", "tool_call_id_duplicate", listOf(twice, ToolResultsMessage(results.take(1)))),
        )
        for ((variant, code, tail) in variants) {
            val sent = send(dialect, row, listOf<Message>(UserMessage(ConversationScript.USER_PROMPT)) + tail)
            assertEquals("$variant: reason", FailureReason.Other(code), sent.reason)
            assertEquals("$variant: requests", 0, sent.requests)
        }
    }

    /** Turn 1 of the dialect's first fixture row, decoded; it must make tool calls. */
    private fun firstToolTurn(): Pair<ConversationRow, AssistantMessage> {
        val row = rows().first()
        val turn = assistantTurns(row, turnsOf(row)).first()
        check(turn.toolCalls.isNotEmpty()) { "${row.case}: turn 1 makes no tool calls" }
        return row to turn
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

private const val HTTP_OK = 200

/** What the real provider answered to a request and how many HTTP requests reached the server meanwhile. */
private class Sent(val result: ModelResult, val requests: Int) {
    val reason: FailureReason? get() = (result as? ModelResult.Failure)?.reason

    override fun toString(): String = "Sent(requests=$requests)"
}

// The prompt, the given assistant turn, and the script's results for the original turn's calls.
private fun afterTurn(assistant: AssistantMessage, original: AssistantMessage): List<Message> =
    listOf(UserMessage(ConversationScript.USER_PROMPT), assistant, ConversationScript.results(original))

private fun extraResult(results: List<ToolResult>): ToolResultsMessage =
    ToolResultsMessage(results + ToolResult("toolu_UNKNOWN", "ok"))

// One call through the real provider against a local server that answers every request with a plain end of turn.
private fun send(dialect: WireDialect, row: ConversationRow, messages: List<Message>): Sent = runBlocking {
    MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(dialect.okAnswer()))
        server.start()
        val request = ConversationScript.request(messages, false, ConversationScript.MAX_TOKENS)
        val result = dialect.provider(server.url("/")).complete(dialect.call(row.model, request))
        Sent(result, server.requestCount)
    }
}
