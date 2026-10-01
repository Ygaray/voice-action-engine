package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDecoderTest {

    private val model = CHAT_GOLDEN_MODEL

    private val logFoodArguments =
        """{"items":[{"name":"egg","quantity":2,"unit":null,"confidence":0.9}],"target_date":null,"source":null}"""

    private fun decode(
        body: String?,
        header: String? = null,
        vendor: ChatVendor = ChatVendor.OPENAI,
        toolRequired: Boolean = true,
    ): ChatDecoded = decodeChatResponse(body, header, model, vendor, toolRequired)

    private fun success(decoded: ChatDecoded): ModelResponse = (decoded.result as ModelResult.Success).response

    private fun forcedAnswer(): JsonObject =
        chatMessage(null, listOf(chatToolCall("call_GOLDEN1", "log_food", logFoodArguments)))

    @Test
    fun aForcedAnswerThatEndsWithStopButCarriesAToolCallDecodesToATypedToolCall() {
        val message = forcedAnswer()
        val body = chatBody(message, "stop", chatUsage(1920, 55, cached = 1800))

        val decoded = decode(body, header = "req_test_1")

        val response = success(decoded)
        assertEquals(StopReason.TOOL_USE, response.stopReason)
        val call = response.message.toolCalls.single()
        assertEquals("call_GOLDEN1", call.id)
        assertEquals("log_food", call.name)
        assertEquals(Json.parseToJsonElement(logFoodArguments), call.arguments)
        assertEquals(Usage(120, 1800, 0, 55), response.usage)
        assertEquals("req_test_1", response.requestId)
        assertEquals(message, response.message.nativeFor(ProviderId.OPENAI, model))
        assertEquals("stop", decoded.finishReason)
        assertEquals(1, decoded.toolCalls)
        assertFalse(decoded.transient)
    }

    @Test
    fun openRouterTakesTheRequestIdFromTheBodyAndOpenAiDoesNot() {
        val body = chatBody(forcedAnswer(), "stop", chatUsage(1920, 55, cached = 1800), id = "gen-GOLDEN")

        val routed = success(decode(body, header = null, vendor = ChatVendor.OPENROUTER))
        assertEquals("gen-GOLDEN", routed.requestId)
        assertEquals(forcedAnswer(), routed.message.nativeFor(ProviderId.OPENROUTER, model))

        val direct = success(decode(body, header = null, vendor = ChatVendor.OPENAI))
        assertNull(direct.requestId)
    }
}
