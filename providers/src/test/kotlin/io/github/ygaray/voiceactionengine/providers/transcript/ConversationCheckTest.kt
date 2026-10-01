package io.github.ygaray.voiceactionengine.providers.transcript

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationCheckTest {

    private val model = "claude-haiku-4-5"
    private val args = JsonObject(emptyMap())
    private val anything: (JsonElement) -> Boolean = { true }

    private fun callPart(id: String): AssistantPart = AssistantPart.ToolCall(id, "add_item", args)

    private fun assistant(vararg callIds: String, replay: NativeReplay? = null): AssistantMessage =
        AssistantMessage(callIds.map { callPart(it) }, replay)

    private fun results(vararg callIds: String): ToolResultsMessage =
        ToolResultsMessage(callIds.map { ToolResult(it, "done") })

    private fun request(vararg messages: Message): ProviderRequest = ProviderRequest(
        model,
        ModelRequest("system", messages.toList(), 256),
        null,
        ModelCapabilities.UNKNOWN,
    )

    private fun violation(vararg messages: Message): String? =
        conversationViolation(request(*messages), ProviderId.ANTHROPIC, anything)

    private fun stamp(provider: ProviderId = ProviderId.OPENAI): NativeReplay =
        NativeReplay(provider, model, JsonArray(emptyList()))

    @Test
    fun aFullyAnsweredTurnIsLegalInAnyResultOrder() {
        assertNull(violation(UserMessage("go"), assistant("a", "b"), results("a", "b")))
        assertNull(violation(UserMessage("go"), assistant("a", "b"), results("b", "a")))
    }

    @Test
    fun aValidTwoRoundHistoryIsLegal() {
        val closing = AssistantMessage(listOf(AssistantPart.Text("done")))

        assertNull(
            violation(
                UserMessage("go"),
                assistant("a"),
                results("a"),
                assistant("b", "c"),
                results("c", "b"),
                closing,
            ),
        )
    }

    @Test
    fun anAssistantTurnWithNoCallsAndNoReplayIsLegal() {
        assertNull(violation(UserMessage("go"), AssistantMessage(emptyList())))
        assertNull(violation(UserMessage("go"), AssistantMessage(listOf(AssistantPart.Text("hello")))))
    }

    @Test
    fun aTurnWithCallsThatIsLastOrFollowedByAnythingButResultsIsUnanswered() {
        assertEquals("tool_call_unanswered", violation(UserMessage("go"), assistant("a", "b")))
        assertEquals("tool_call_unanswered", violation(UserMessage("go"), assistant("a"), UserMessage("again")))
        assertEquals(
            "tool_call_unanswered",
            violation(UserMessage("go"), assistant("a"), assistant("b"), results("b")),
        )
    }

    @Test
    fun aCallWithNoResultIsMissing() {
        assertEquals("tool_result_missing", violation(UserMessage("go"), assistant("a", "b"), results("a")))
    }

    @Test
    fun aResultForNoCallIsUnexpected() {
        assertEquals("tool_result_unexpected", violation(UserMessage("go"), assistant("a"), results("a", "z")))
    }

    @Test
    fun resultsThatDoNotFollowATurnWithCallsAreUnexpected() {
        assertEquals("tool_result_unexpected", violation(UserMessage("go"), results("a")))
        assertEquals(
            "tool_result_unexpected",
            violation(UserMessage("go"), AssistantMessage(listOf(AssistantPart.Text("hi"))), results("a")),
        )
        assertEquals(
            "tool_result_unexpected",
            violation(UserMessage("go"), assistant("a"), results("a"), results("a")),
        )
    }

    @Test
    fun twoCallsSharingAnIdInOneTurnAreDuplicates() {
        assertEquals("tool_call_id_duplicate", violation(UserMessage("go"), assistant("a", "a"), results("a")))
    }

    @Test
    fun theSameIdInTwoDifferentTurnsIsLegal() {
        assertNull(
            violation(UserMessage("go"), assistant("a"), results("a"), assistant("a"), results("a")),
        )
    }

    @Test
    fun aStampForAnotherProviderOrModelOrShapeIsAReplayMismatch() {
        val otherModel = NativeReplay(ProviderId.ANTHROPIC, "claude-opus-5-5", JsonArray(emptyList()))
        val matching = stamp(ProviderId.ANTHROPIC)

        assertEquals("replay_mismatch", violation(UserMessage("go"), assistant(replay = stamp())))
        assertEquals("replay_mismatch", violation(UserMessage("go"), assistant(replay = otherModel)))
        assertNull(violation(UserMessage("go"), assistant(replay = matching)))

        val picky = conversationViolation(
            request(UserMessage("go"), assistant(replay = matching)),
            ProviderId.ANTHROPIC,
        ) { false }
        assertEquals("replay_mismatch", picky)
    }

    @Test
    fun theFirstViolationInMessageOrderWins() {
        assertEquals(
            "replay_mismatch",
            violation(UserMessage("go"), assistant(replay = stamp()), assistant("a")),
        )
        assertEquals(
            "tool_call_id_duplicate",
            violation(
                UserMessage("go"),
                assistant("a", "a"),
                results("a"),
                assistant(replay = stamp()),
            ),
        )
    }

    @Test
    fun insideOneTurnReplayBeatsDuplicateBeatsUnansweredBeatsMissingBeatsUnexpected() {
        assertEquals(
            "replay_mismatch",
            violation(UserMessage("go"), assistant("a", "a", replay = stamp())),
        )
        assertEquals("tool_call_id_duplicate", violation(UserMessage("go"), assistant("a", "a")))
        assertEquals("tool_call_unanswered", violation(UserMessage("go"), assistant("a", "b"), UserMessage("x")))
        assertEquals("tool_result_missing", violation(UserMessage("go"), assistant("a", "b"), results("z")))
    }

    @Test
    fun everyCodeBuildsASafeFailureReason() {
        val cases = listOf(
            "replay_mismatch" to arrayOf(UserMessage("go"), assistant(replay = stamp())),
            "tool_call_unanswered" to arrayOf(UserMessage("go"), assistant("a")),
            "tool_call_id_duplicate" to arrayOf(UserMessage("go"), assistant("a", "a"), results("a")),
            "tool_result_missing" to arrayOf(UserMessage("go"), assistant("a", "b"), results("a")),
            "tool_result_unexpected" to arrayOf(UserMessage("go"), assistant("a"), results("a", "z")),
        )

        cases.forEach { (code, messages) ->
            val refusal = conversationRefusal(request(*messages), ProviderId.ANTHROPIC, anything)

            assertNotNull(code, refusal)
            assertEquals(FailureReason.Other(code), refusal!!.reason)
        }
        assertNull(conversationRefusal(request(UserMessage("go")), ProviderId.ANTHROPIC, anything))
    }

    @Test
    fun noIdOrContentReachesTheReasonOrItsToString() {
        val callId = "CANARY_ID_7f3"
        val content = "CANARY-CONTENT-7f3"
        val violating = listOf(
            arrayOf(
                UserMessage("go"),
                AssistantMessage(listOf(callPart(callId), callPart("other"))),
                ToolResultsMessage(listOf(ToolResult(callId, content))),
            ),
            arrayOf(
                UserMessage("go"),
                AssistantMessage(listOf(callPart(callId))),
                ToolResultsMessage(listOf(ToolResult(callId, content), ToolResult("zzz", content))),
            ),
            arrayOf(UserMessage("go"), ToolResultsMessage(listOf(ToolResult(callId, content)))),
            arrayOf(UserMessage("go"), AssistantMessage(listOf(callPart(callId), callPart(callId)))),
        )

        violating.forEach { messages ->
            val refusal = conversationRefusal(request(*messages), ProviderId.ANTHROPIC, anything)!!

            for (text in listOf(refusal.toString(), refusal.reason.code, refusal.reason.toString())) {
                assertFalse(text, text.contains(callId))
                assertFalse(text, text.contains(content))
            }
        }
    }
}
