package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEncoderTest {

    private fun forcedLogFood(userText: String = "add two eggs and a coffee"): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage(userText)),
        listOf(logFoodTool()),
        ToolChoice.Required("log_food"),
        1024,
        CacheDirective(true),
    )

    private fun encoded(vendor: ChatVendor, model: String, request: ModelRequest): String =
        String(encodeChatRequest(chatCall(vendor, model, request), vendor), Charsets.UTF_8)

    @Test
    fun forcedOpenAiCallEncodesToTheGoldenBodyByteForByte() {
        val body = encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forcedLogFood())

        assertEquals(goldenRequest("openai", "forced_log_food_strict"), body)
    }

    @Test
    fun theAppsSchemaIsNotChangedByEncoding() {
        val tool = logFoodTool()
        val request = ModelRequest(
            FIXED_SYSTEM,
            listOf(UserMessage("add milk")),
            listOf(tool),
            ToolChoice.Required("log_food"),
            1024,
            CacheDirective(true),
        )

        encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request)

        assertTrue(tool.inputSchema.toString().contains("uniqueItems"))
        assertTrue(tool.inputSchema.toString().contains("\"format\":\"uri\""))
    }
}
