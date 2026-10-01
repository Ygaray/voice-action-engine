package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The neutral transcript types: a single-shot request and its response, built from public constructors only. */
class TranscriptTypesTest {

    private fun logItemTool(): ToolSpec = ToolSpec(
        "log_item",
        "Log one item",
        buildJsonObject { put("type", "object") },
    )

    @Test
    fun singleShotRequestReadsBackFieldByField() {
        val user = UserMessage("log a coffee")
        val tool = logItemTool()
        val choice = ToolChoice.Required("log_item")
        val cache = CacheDirective(staticPrefix = true)

        val request = ModelRequest("be brief", listOf(user), listOf(tool), choice, 512, cache)

        assertEquals("be brief", request.system)
        assertEquals(1, request.messages.size)
        assertSame(user, request.messages[0])
        assertEquals("log a coffee", user.text)
        assertEquals(1, request.tools.size)
        assertSame(tool, request.tools[0])
        assertEquals(choice, request.toolChoice)
        assertEquals(512, request.maxTokens)
        assertEquals(CacheDirective(true, false), request.cache)
        assertTrue(request.cache.staticPrefix)
        assertEquals(false, request.cache.conversationTail)
    }

    @Test
    fun singleShotResponseReadsBackFieldByField() {
        val text = AssistantPart.Text("done")
        val message = AssistantMessage(listOf(text))
        val usage = Usage(10, 0, 0, 5)

        val response = ModelResponse(message, StopReason.END_TURN, usage, "req_1")

        assertSame(message, response.message)
        assertEquals(1, response.message.parts.size)
        assertSame(text, response.message.parts[0])
        assertEquals("done", text.text)
        assertEquals(StopReason.END_TURN, response.stopReason)
        assertSame(usage, response.usage)
        assertEquals("req_1", response.requestId)
        assertTrue(response.message.toolCalls.isEmpty())
    }

    @Test
    fun responseWithoutRequestIdHasNullId() {
        val response = ModelResponse(AssistantMessage(emptyList()), StopReason.OTHER, Usage.ZERO)

        assertEquals(null, response.requestId)
        assertTrue(response.message.parts.isEmpty())
    }

    @Test
    fun shortRequestConstructorsDefaultToAutoChoiceAndStaticPrefixCache() {
        val user = UserMessage("hi")

        val noTools = ModelRequest("sys", listOf(user), 64)
        val withTools = ModelRequest("sys", listOf(user), listOf(logItemTool()), 64)

        assertEquals(ToolChoice.Auto(), noTools.toolChoice)
        assertTrue(noTools.tools.isEmpty())
        assertEquals(CacheDirective(true), noTools.cache)
        assertEquals(64, noTools.maxTokens)
        assertEquals(ToolChoice.Auto(), withTools.toolChoice)
        assertEquals(1, withTools.tools.size)
        assertEquals(CacheDirective(true), withTools.cache)
    }
}
