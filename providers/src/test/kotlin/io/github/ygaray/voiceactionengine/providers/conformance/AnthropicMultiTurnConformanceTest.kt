package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.anthropic.encodeAnthropicRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conformance suite bound to the Anthropic Messages mapper, plus checks that only these fixtures can make. */
internal class AnthropicMultiTurnConformanceTest : MultiTurnConformanceSuite() {

    override val dialect: WireDialect = AnthropicWire

    private fun row(case: String): ConversationRow = rows().single { it.case == case }

    @Test
    fun reshapeIsSingleTurnOnly() {
        val row = row("derived_parallel_tools")
        val first = assistantTurns(row, turnsOf(row)).first()
        val prompt = UserMessage(ConversationScript.USER_PROMPT)
        val history = listOf<Message>(prompt, first, ConversationScript.results(first))
        val cannotForce = ModelCapabilities {
            caching = CachingMode.EXPLICIT_BREAKPOINTS
            supportsForcedToolChoice = false
        }
        fun body(messages: List<Message>, choice: ToolChoice): String {
            val base = rowRequest(row, messages)
            val request = ModelRequest(
                base.system,
                messages,
                base.tools,
                choice,
                base.maxTokens,
                base.cache,
                base.singleToolCall,
            )
            val call = ProviderRequest(row.model, request, Credential(ProviderId.ANTHROPIC, FAKE_KEY), cannotForce)
            return encodeAnthropicRequest(call, true).toString(Charsets.UTF_8)
        }
        val required = ToolChoice.Required("record_item")
        // The instruction line moves from the first user message to the last one, so the cached prefix is rewritten.
        val rewritten = appendOnlyViolation(body(history.take(1), required), body(history, required))
        assertEquals("a forced tool on a model that cannot be forced", "prefix changed", rewritten)
        val auto = ToolChoice.Auto()
        val appended = appendOnlyViolation(body(history.take(1), auto), body(history, auto))
        assertEquals("the same pair with automatic choice", null, appended)
    }

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
    fun theThinkingFixtureKeepsItsBlocksInOrderAndEveryLaterRequestRepeatsThem() {
        val row = row("derived_thinking")
        val turns = turnsOf(row)
        val bodies = replayConversation(dialect, row, turns).bodies
        val first = dialect.expectedReplayWire(turns[0].response) as JsonArray
        val types = first.map { ((it as JsonObject)["type"] as JsonPrimitive).content }
        assertEquals(
            listOf("thinking", "redacted_thinking", "text", "tool_use", "thinking", "tool_use"),
            types,
        )
        val firstText = canonicalJson(first.toString())
        val secondText = canonicalJson(checkNotNull(dialect.expectedReplayWire(turns[1].response)).toString())
        assertTrue("turn 1 is missing from request 2", firstText in bodies[1])
        assertTrue("turn 1 is missing from request 3", firstText in bodies[2])
        assertTrue("turn 2 is missing from request 3", secondText in bodies[2])
        assertTrue("the fixture holds no signature", firstText.contains("\"signature\""))
    }

    @Test
    fun aChangedSignatureFailsTheByteForByteCheckForThatTurn() {
        val row = row("derived_thinking")
        val text = conversationText(row)
        val bodies = replayConversation(dialect, row, parseConversation(text)).bodies
        val tampered = text.replace("derived-placeholder-signature-1", "derived-placeholder-signature-X")
        assertTrue("the edit did not change the golden", tampered != text)
        val found = verbatimViolations(dialect, parseConversation(tampered), bodies, text)
        assertTrue("the changed signature went unnoticed", found.any { it.startsWith("turn 1") })
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
