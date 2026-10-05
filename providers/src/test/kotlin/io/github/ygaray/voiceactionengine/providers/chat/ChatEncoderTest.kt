package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEncoderTest {

    private val firstTurn = "add two eggs and a coffee"

    private fun request(
        tools: List<ToolSpec> = listOf(logFoodTool()),
        choice: ToolChoice = ToolChoice.Required("log_food"),
        user: String = firstTurn,
        maxTokens: Int = 1024,
        singleToolCall: Boolean = false,
    ): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage(user)),
        tools,
        choice,
        maxTokens,
        CacheDirective(true),
        singleToolCall,
    )

    private fun bytes(call: ProviderRequest, vendor: ChatVendor): String =
        String(encodeChatRequest(call, vendor), Charsets.UTF_8)

    private fun encoded(vendor: ChatVendor, model: String, request: ModelRequest): String =
        bytes(chatCall(vendor, model, request), vendor)

    private fun decoded(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

    private fun firstTool(body: JsonObject): JsonObject = body.getValue("tools").jsonArray.first().jsonObject

    private fun functionOf(tool: JsonObject): JsonObject = tool.getValue("function").jsonObject

    private fun userContent(body: JsonObject): String = body.getValue("messages").jsonArray
        .map { it.jsonObject }
        .last { it.getValue("role").jsonPrimitive.content == "user" }
        .getValue("content").jsonPrimitive.content

    // ---- golden bodies

    @Test
    fun forcedOpenAiCallEncodesToTheGoldenBodyByteForByte() {
        assertEquals(
            goldenRequest("openai", "forced_log_food_strict"),
            encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request()),
        )
    }

    @Test
    fun autoCallWithTwoToolsSortsThemAndStrictsOnlyTheEligibleOne() {
        val tools = listOf(logFoodTool(), editListCardTool())

        assertEquals(
            goldenRequest("openai", "auto_two_tools"),
            encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request(tools, ToolChoice.Auto(), "add milk")),
        )
    }

    @Test
    fun legacyModelGetsMaxTokensAndNoReasoningEffort() {
        assertEquals(
            goldenRequest("openai", "legacy_forced"),
            encoded(ChatVendor.OPENAI, "gpt-4o-mini", request()),
        )
    }

    @Test
    fun aCallWithoutToolsCarriesOnlyModelMessagesAndTheLimit() {
        val noTools = request(emptyList(), ToolChoice.Auto(), "what is on my list")

        assertEquals(goldenRequest("openai", "no_tools"), encoded(ChatVendor.OPENAI, "gpt-5.4-mini", noTools))
    }

    @Test
    fun forcedOpenRouterCallToAnOpenAiModelAsksTheRouterToHonourEveryParameter() {
        assertEquals(
            goldenRequest("openrouter", "forced_log_food_strict"),
            encoded(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", request()),
        )
    }

    @Test
    fun routedAnthropicModelIsReshapedWithoutStrictOrRequireParameters() {
        assertEquals(
            goldenRequest("openrouter", "anthropic_reshaped"),
            encoded(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5", request()),
        )
    }

    @Test
    fun routedUnknownVendorWithASmallBudgetIsRaisedToTheRouterFloor() {
        val auto = request(choice = ToolChoice.Auto(), user = "add milk", maxTokens = 8)

        assertEquals(
            goldenRequest("openrouter", "other_vendor_small_budget"),
            encoded(ChatVendor.OPENROUTER, "example/tool-model", auto),
        )
    }

    @Test
    fun routedAstraModelTakesTheLowEffortAndANamedChoice() {
        assertEquals(
            goldenRequest("openrouter", "astra_forced"),
            encoded(ChatVendor.OPENROUTER, "openai/gpt-6-astra", request()),
        )
    }

    @Test
    fun aDirectResponsesOnlyModelEncodesToTheGoldenBodyWithNoReasoningEffort() {
        val body = encoded(ChatVendor.OPENAI, "gpt-6-astra", request(singleToolCall = true))

        assertEquals(goldenRequest("openai", "astra_direct"), body)
        val parsed = decoded(body)
        assertFalse(parsed.containsKey("reasoning_effort"))
        val choice = parsed.getValue("tool_choice").jsonObject.getValue("function").jsonObject
        assertEquals(JsonPrimitive("log_food"), choice["name"])
        assertEquals(JsonPrimitive(false), parsed["parallel_tool_calls"])
    }

    @Test
    fun aDirectProModelSendsNoReasoningEffort() {
        val body = decoded(encoded(ChatVendor.OPENAI, "gpt-5.5-pro", request(singleToolCall = true)))

        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test
    fun goldenFilesHoldExactlyTheNamedCases() {
        assertEquals(
            listOf("forced_log_food_strict", "auto_two_tools", "legacy_forced", "no_tools", "astra_direct"),
            goldenCaseNames("openai"),
        )
        assertEquals(
            listOf("forced_log_food_strict", "anthropic_reshaped", "other_vendor_small_budget", "astra_forced"),
            goldenCaseNames("openrouter"),
        )
    }

    // ---- the app's schema and the strict decision

    @Test
    fun theAppsSchemaIsNotChangedByEncoding() {
        val tool = logFoodTool()
        val before = tool.inputSchema.toString()

        encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request(listOf(tool)))

        assertEquals(before, tool.inputSchema.toString())
        assertTrue(tool.inputSchema.toString().contains("uniqueItems"))
    }

    @Test
    fun anAppAskingForStrictOnAnOptionalSchemaStillGetsNoStrictKeyAndItsOwnSchema() {
        val tool = editListCardTool(strict = true)
        val body = decoded(encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request(listOf(tool), ToolChoice.Auto())))

        assertNull(functionOf(firstTool(body)).get("strict"))
        assertEquals(tool.inputSchema, functionOf(firstTool(body)).getValue("parameters"))
    }

    @Test
    fun anAppOptingOutOfStrictKeepsItsSchemaByteIdentical() {
        val tool = logFoodTool(strict = false)
        val raw = encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request(listOf(tool)))
        val function = functionOf(firstTool(decoded(raw)))

        assertNull(function["strict"])
        assertEquals(tool.inputSchema, function.getValue("parameters"))
        assertTrue(raw.contains(tool.inputSchema.toString()))
    }

    @Test
    fun strictIsNeverSentThroughARouterToAnotherVendorsModel() {
        val body = decoded(encoded(ChatVendor.OPENROUTER, "example/tool-model", request(choice = ToolChoice.Auto())))

        assertNull(functionOf(firstTool(body))["strict"])
        assertNull(body["parallel_tool_calls"])
    }

    // ---- the parallel tool-call switch

    private fun parallelOf(vendor: ChatVendor, model: String, request: ModelRequest): JsonElement? =
        decoded(encoded(vendor, model, request))["parallel_tool_calls"]

    @Test
    fun aForcedOpenAiRequestStillTurnsTheParallelSwitchOffWithoutTheFlag() {
        assertEquals(JsonPrimitive(false), parallelOf(ChatVendor.OPENAI, "gpt-5.4-mini", request()))
    }

    @Test
    fun aSingleToolCallRequestTurnsTheSwitchOffEvenWithAutomaticChoiceAndANonStrictTool() {
        val auto = request(listOf(editListCardTool()), ToolChoice.Auto(), "add milk", singleToolCall = true)
        val plain = request(listOf(editListCardTool()), ToolChoice.Auto(), "add milk")

        assertEquals(JsonPrimitive(false), parallelOf(ChatVendor.OPENAI, "gpt-5.4-mini", auto))
        assertNull(parallelOf(ChatVendor.OPENAI, "gpt-5.4-mini", plain))
    }

    @Test
    fun theOSeriesNeverGetsTheParallelSwitch() {
        val flagged = request(listOf(editListCardTool()), ToolChoice.Auto(), singleToolCall = true)

        assertNull(parallelOf(ChatVendor.OPENAI, "o3", request()))
        assertNull(parallelOf(ChatVendor.OPENAI, "o3-mini", request()))
        assertNull(parallelOf(ChatVendor.OPENAI, "o4-mini", request(choice = ToolChoice.Auto(), singleToolCall = true)))
        assertNull(parallelOf(ChatVendor.OPENAI, "o1", flagged))
        assertEquals(JsonPrimitive(false), parallelOf(ChatVendor.OPENAI, "gpt-5.4-mini", request()))
    }

    @Test
    fun openRouterNeverCarriesTheParallelSwitchAndKeepsRequireParametersWhenForced() {
        val forced = decoded(encoded(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", request(singleToolCall = true)))
        val auto = request(choice = ToolChoice.Auto(), singleToolCall = true)

        assertNull(forced["parallel_tool_calls"])
        assertEquals(JsonPrimitive(true), forced.getValue("provider").jsonObject["require_parameters"])
        assertNull(parallelOf(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", auto))
        assertNull(parallelOf(ChatVendor.OPENROUTER, "example/tool-model", auto))
        assertNull(parallelOf(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5", request(singleToolCall = true)))
    }

    @Test
    fun aSingleToolCallRequestWithoutToolsCarriesNoParallelSwitch() {
        val bare = request(emptyList(), ToolChoice.Auto(), singleToolCall = true)

        assertNull(parallelOf(ChatVendor.OPENAI, "gpt-5.4-mini", bare))
    }

    @Test
    fun theKeyOrderHoldsWhenTheParallelSwitchIsPresent() {
        val keys = decoded(encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request(singleToolCall = true))).keys.toList()

        assertEquals(
            listOf("model", "messages", "tools", "tool_choice", "parallel_tool_calls", "reasoning_effort"),
            keys.take(6),
        )
    }

    // ---- the tool choice

    @Test
    fun anAppOverrideThatForbidsForcingReshapesAnOpenAiCallAndKeepsStrict() {
        val caps = ModelCapabilities { supportsForcedToolChoice = false }
        val credential = chatCall(ChatVendor.OPENAI, "gpt-5.4-mini", request()).credential
        val call = ProviderRequest("gpt-5.4-mini", request(), credential, caps)
        val body = decoded(bytes(call, ChatVendor.OPENAI))

        assertEquals(JsonPrimitive("auto"), body["tool_choice"])
        assertEquals(JsonPrimitive(true), functionOf(firstTool(body))["strict"])
        assertTrue(userContent(body).endsWith("\n\nCall the log_food tool with your result."))
        assertNull(body["provider"])
    }

    @Test
    fun anAppOverrideThatAllowsForcingGivesARoutedAnthropicModelTheNamedChoice() {
        val caps = ModelCapabilities { supportsForcedToolChoice = true }
        val model = "anthropic/claude-sonnet-5.5"
        val credential = chatCall(ChatVendor.OPENROUTER, model, request()).credential
        val body = decoded(bytes(ProviderRequest(model, request(), credential, caps), ChatVendor.OPENROUTER))

        val choice = body.getValue("tool_choice").jsonObject.getValue("function").jsonObject
        assertEquals(JsonPrimitive("log_food"), choice["name"])
        assertEquals(JsonPrimitive(true), body.getValue("provider").jsonObject["require_parameters"])
        assertNull(functionOf(firstTool(body))["strict"])
        assertEquals(firstTurn, userContent(body))
    }

    @Test
    fun theInstructionNeverTouchesTheSystemMessage() {
        val body = decoded(encoded(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5", request()))
        val system = body.getValue("messages").jsonArray.first().jsonObject

        assertEquals("system", system.getValue("role").jsonPrimitive.content)
        assertEquals(FIXED_SYSTEM, system.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun aBlankSystemPromptIsLeftOut() {
        val bare = ModelRequest(
            "  ",
            listOf(UserMessage("add milk")),
            listOf(logFoodTool()),
            ToolChoice.Auto(),
            64,
            CacheDirective(true),
        )
        val messages = decoded(encoded(ChatVendor.OPENAI, "gpt-5.4-mini", bare)).getValue("messages").jsonArray

        assertEquals(1, messages.size)
        assertEquals("user", messages.first().jsonObject.getValue("role").jsonPrimitive.content)
    }

    // ---- purity and the key allow-list

    @Test
    fun encodingTheSameRequestTwiceGivesIdenticalBytes() {
        val call = chatCall(ChatVendor.OPENAI, "gpt-5.4-mini", request())

        assertEquals(bytes(call, ChatVendor.OPENAI), bytes(call, ChatVendor.OPENAI))
    }

    @Test
    fun requestsDifferingOnlyInTheUserTextShareEverythingBeforeAndAfterIt() {
        val model = "gpt-5.4-mini"
        val english = encoded(ChatVendor.OPENAI, model, request(user = "Answer in English. Date 2026-10-01. add milk"))
        val spanish = encoded(
            ChatVendor.OPENAI,
            model,
            request(user = "Responde en espanol. Fecha 2026-09-30. pon leche"),
        )
        val marker = "\"role\":\"user\""

        assertNotEquals(english, spanish)
        assertEquals(english.substringBefore(marker), spanish.substringBefore(marker))
        assertEquals(english.substringAfter("\"tools\":"), spanish.substringAfter("\"tools\":"))
    }

    @Test
    fun noBodyCarriesAKeyOutsideTheAllowList() {
        val allowed = setOf(
            "model", "messages", "tools", "tool_choice", "parallel_tool_calls", "reasoning_effort",
            "max_completion_tokens", "max_tokens", "provider",
        )
        val scenarios = listOf(
            encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request()),
            encoded(ChatVendor.OPENAI, "gpt-4o-mini", request()),
            encoded(ChatVendor.OPENAI, "gpt-5.4-mini", request(emptyList(), ToolChoice.Auto())),
            encoded(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", request()),
            encoded(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5", request()),
            encoded(ChatVendor.OPENROUTER, "example/tool-model", request(choice = ToolChoice.Auto())),
            encoded(ChatVendor.OPENROUTER, "openai/gpt-6-astra", request()),
        ) + listOf("openai", "openrouter").flatMap { file ->
            goldenCaseNames(file).map { goldenRequest(file, it) }
        }

        scenarios.forEach { raw ->
            val keys = decoded(raw).keys
            assertTrue("unexpected keys in $keys", keys.all { it in allowed })
            val limits = keys.count { it == "max_tokens" || it == "max_completion_tokens" }
            assertEquals("exactly one limit key in $keys", 1, limits)
        }
    }

    @Test
    fun toolsAreSentInTheNestedShapeSortedByName() {
        val both = request(listOf(logFoodTool(), editListCardTool()), ToolChoice.Auto())
        val body = decoded(encoded(ChatVendor.OPENAI, "gpt-5.4-mini", both))
        val tools: JsonArray = body.getValue("tools").jsonArray
        val names = tools.map { functionOf(it.jsonObject).getValue("name").jsonPrimitive.content }

        assertEquals(listOf("edit_list_card", "log_food"), names)
        tools.forEach { assertEquals("function", it.jsonObject.getValue("type").jsonPrimitive.content) }
        assertFalse(body.containsKey("stream"))
    }
}
