package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MAX_TOKENS = 1024

/** The reasoning knob changes no byte on the wire in this version: OFF and PROVIDER_DEFAULT both send the v1.0 request. */
class ReasoningWireParityTest {

    private val user = "add two eggs and a coffee"

    private fun forced(reasoning: ReasoningMode?): ModelRequest {
        val messages = listOf(UserMessage(user))
        val tools = listOf(logFoodTool())
        val choice = ToolChoice.Required("log_food")
        return if (reasoning == null) {
            ModelRequest(FIXED_SYSTEM, messages, tools, choice, MAX_TOKENS, CacheDirective(true), false)
        } else {
            ModelRequest(FIXED_SYSTEM, messages, tools, choice, MAX_TOKENS, CacheDirective(true), false, reasoning)
        }
    }

    private fun encoded(vendor: ChatVendor, model: String, request: ModelRequest): String =
        String(encodeChatRequest(chatCall(vendor, model, request), vendor), Charsets.UTF_8)

    @Test
    fun openAiOffAndProviderDefaultBothEncodeToTheV10Golden() {
        val golden = goldenRequest("openai", "forced_log_food_strict")

        assertEquals(golden, encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forced(null)))
        assertEquals(golden, encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forced(ReasoningMode.OFF)))
        assertEquals(golden, encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forced(ReasoningMode.PROVIDER_DEFAULT)))
    }
}
