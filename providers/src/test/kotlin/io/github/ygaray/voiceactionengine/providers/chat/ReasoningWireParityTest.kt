package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicModels
import io.github.ygaray.voiceactionengine.providers.anthropic.encodeAnthropicRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MAX_TOKENS = 1024

/** The reasoning knob changes no wire byte in this version: OFF and PROVIDER_DEFAULT both send the v1.0 request. */
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

    private fun assertAllShapesEqual(vendor: ChatVendor, model: String, golden: String) {
        assertEquals(golden, encoded(vendor, model, forced(null)))
        assertEquals(golden, encoded(vendor, model, forced(ReasoningMode.OFF)))
        assertEquals(golden, encoded(vendor, model, forced(ReasoningMode.PROVIDER_DEFAULT)))
    }

    @Test
    fun openRouterOffAndProviderDefaultEncodeToTheV10GoldensForOpenAiAnthropicAndAstraModels() {
        assertAllShapesEqual(
            ChatVendor.OPENROUTER,
            "openai/gpt-5.4-mini",
            goldenRequest("openrouter", "forced_log_food_strict"),
        )
        assertAllShapesEqual(
            ChatVendor.OPENROUTER,
            "anthropic/claude-sonnet-5.5",
            goldenRequest("openrouter", "anthropic_reshaped"),
        )
        assertAllShapesEqual(
            ChatVendor.OPENROUTER,
            "openai/gpt-6-astra",
            goldenRequest("openrouter", "astra_forced"),
        )
    }

    @Test
    fun openAiOffAndProviderDefaultBothEncodeToTheV10Golden() {
        val golden = goldenRequest("openai", "forced_log_food_strict")

        assertEquals(golden, encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forced(null)))
        assertEquals(golden, encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forced(ReasoningMode.OFF)))
        assertEquals(golden, encoded(ChatVendor.OPENAI, "gpt-5.4-mini", forced(ReasoningMode.PROVIDER_DEFAULT)))
    }

    private fun anthropicBytes(model: String, reasoning: ReasoningMode?): String {
        val messages = listOf(UserMessage("add milk"))
        val tools = listOf(ToolSpec("add_item", "Adds an item.", buildJsonObject { put("type", "object") }))
        val choice = ToolChoice.Required("add_item")
        val cache = CacheDirective(true)
        val request = if (reasoning == null) {
            ModelRequest("You are a test system.", messages, tools, choice, MAX_TOKENS, cache, true)
        } else {
            ModelRequest("You are a test system.", messages, tools, choice, MAX_TOKENS, cache, true, reasoning)
        }
        val call = ProviderRequest(
            model,
            request,
            Credential(ProviderId.ANTHROPIC, "sk-test-key"),
            AnthropicModels.capabilities(model),
        )
        return String(encodeAnthropicRequest(call), Charsets.UTF_8)
    }

    @Test
    fun anthropicBytesAreIdenticalForEveryModeOnAForcedAndAReshapedModel() {
        listOf("claude-haiku-4-5", "claude-sonnet-5-5").forEach { model ->
            val v10 = anthropicBytes(model, null)

            assertEquals(model, v10, anthropicBytes(model, ReasoningMode.OFF))
            assertEquals(model, v10, anthropicBytes(model, ReasoningMode.PROVIDER_DEFAULT))
        }
    }
}
