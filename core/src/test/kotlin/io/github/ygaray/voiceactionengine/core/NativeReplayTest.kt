package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The verbatim replay contract: the very same raw turn comes back only for the exact provider and model. */
class NativeReplayTest {

    private fun thinkingTurn(): JsonElement = buildJsonObject {
        put("role", "assistant")
        put(
            "content",
            buildJsonArray {
                add(buildJsonObject { put("type", "thinking"); put("thinking", CANARY) })
                add(buildJsonObject { put("type", "text"); put("text", "visible") })
            },
        )
    }

    private fun stamped(raw: JsonElement, parts: List<AssistantPart> = listOf(AssistantPart.Text("visible"))) =
        AssistantMessage(parts, NativeReplay(ProviderId.ANTHROPIC, "model-a", raw))

    @Test
    fun exactProviderAndModelStampReturnsTheSameRawInstance() {
        val raw = thinkingTurn()

        val message = stamped(raw)

        assertSame(raw, message.nativeFor(ProviderId.ANTHROPIC, "model-a"))
    }

    @Test
    fun anythingElseReturnsNullSoTheMapperRebuildsFromNeutralParts() {
        val message = stamped(thinkingTurn())
        val unstamped = AssistantMessage(listOf(AssistantPart.Text("visible")))

        assertNull(unstamped.nativeFor(ProviderId.ANTHROPIC, "model-a"))
        assertNull(message.nativeFor(ProviderId.OPENAI, "model-a"))
        assertNull(message.nativeFor(ProviderId.ANTHROPIC, "model-b"))
    }

    @Test
    fun rawIsStoredByReferenceAndPartsStayTextAndToolCallOnly() {
        val raw = thinkingTurn()
        val call = AssistantPart.ToolCall("call_1", "log_item", buildJsonObject { put("q", "x") })

        val message = stamped(raw, listOf(AssistantPart.Text("visible"), call))

        assertSame(raw, message.nativeReplay?.raw)
        assertEquals(2, message.parts.size)
        assertTrue(message.parts.all { it is AssistantPart.Text || it is AssistantPart.ToolCall })
        assertEquals(listOf(call), message.toolCalls)
    }

    @Test
    fun blankModelIsRejected() {
        try {
            NativeReplay(ProviderId.ANTHROPIC, " ", thinkingTurn())
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertFalse((expected.message ?: "").contains(CANARY))
        }
    }

    @Test
    fun printingNeverRevealsTheRawTurn() {
        val message = stamped(thinkingTurn())
        val replay = requireNotNull(message.nativeReplay)

        assertEquals("NativeReplay(provider=anthropic, model=model-a)", replay.toString())
        assertTrue(message.toString().contains("anthropic/model-a"))
        assertFalse(replay.toString().contains(CANARY))
        assertFalse(message.toString().contains(CANARY))
    }

    private companion object {
        const val CANARY = "CANARY-THINKING-TEXT"
    }
}
