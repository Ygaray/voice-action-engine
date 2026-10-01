package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicEncoderTest {

    private val model = "claude-haiku-4-5"
    private val caching = ModelCapabilities { caching = CachingMode.EXPLICIT_BREAKPOINTS }

    private fun objectSchema(vararg properties: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { putJsonObject(it) { put("type", "string") } } }
    }

    private fun tool(name: String, strict: Boolean? = null): ToolSpec =
        ToolSpec(name, "Does $name.", objectSchema("value"), strict = strict)

    private fun call(
        system: String = "You are a test system.",
        messages: List<Message> = listOf(UserMessage("add milk")),
        tools: List<ToolSpec> = listOf(tool("add_item")),
        toolChoice: ToolChoice = ToolChoice.Auto(),
        cache: CacheDirective = CacheDirective(true),
        capabilities: ModelCapabilities = caching,
        singleToolCall: Boolean = false,
    ): ProviderRequest = ProviderRequest(
        model,
        ModelRequest(system, messages, tools, toolChoice, 256, cache, singleToolCall),
        Credential(ProviderId.ANTHROPIC, "sk-test-key"),
        capabilities,
    )

    private fun encode(call: ProviderRequest): String = String(encodeAnthropicRequest(call), Charsets.UTF_8)

    private fun parse(call: ProviderRequest): JsonObject = Json.parseToJsonElement(encode(call)).jsonObject

    private fun count(text: String, needle: String): Int = Regex(Regex.escape(needle)).findAll(text).count()

    @Test
    fun theOnlyBreakpointSitsOnTheLastSystemBlock() {
        val text = encode(call(messages = listOf(UserMessage("a"), UserMessage("b"))))
        val body = Json.parseToJsonElement(text).jsonObject

        assertEquals(1, count(text, "cache_control"))
        val system = body.getValue("system").jsonArray
        assertEquals(buildJsonObject { put("type", "ephemeral") }, system.last().jsonObject["cache_control"])
        body.getValue("tools").jsonArray.forEach { assertNull(it.jsonObject["cache_control"]) }
        body.getValue("messages").jsonArray.forEach { assertFalse(it.toString().contains("cache_control")) }
    }

    @Test
    fun withoutCachingSupportOrAStaticPrefixThereIsNoBreakpoint() {
        assertEquals(0, count(encode(call(capabilities = ModelCapabilities.UNKNOWN)), "cache_control"))
        assertEquals(0, count(encode(call(cache = CacheDirective(false))), "cache_control"))
        assertEquals(0, count(encode(call(cache = CacheDirective(false, true))), "cache_control"))
    }

    @Test
    fun aMovingConversationTailAddsNoSecondBreakpoint() {
        val text = encode(call(cache = CacheDirective(true, true)))

        assertEquals(1, count(text, "cache_control"))
        val onlyMessage = Json.parseToJsonElement(text).jsonObject.getValue("messages").jsonArray.single().jsonObject
        assertNull(onlyMessage["cache_control"])
    }

    @Test
    fun aBlankSystemMovesTheBreakpointToTheLastSortedToolOrDropsItWithoutTools() {
        val withTools = parse(call(system = "  ", tools = listOf(tool("zeta"), tool("alpha"))))
        val tools = withTools.getValue("tools").jsonArray

        assertNull(withTools["system"])
        assertNull(tools[0].jsonObject["cache_control"])
        assertEquals("zeta", tools[1].jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(buildJsonObject { put("type", "ephemeral") }, tools[1].jsonObject["cache_control"])

        val bare = encode(call(system = "", tools = emptyList()))
        assertEquals(0, count(bare, "cache_control"))
        assertFalse(bare.contains("\"system\""))
    }

    @Test
    fun toolsAreSortedByNameAndSchemasKeepTheirKeyOrderByteForByte() {
        val schema = buildJsonObject {
            putJsonArray("required") { add(JsonPrimitive("value")) }
            put("type", "object")
            putJsonObject("properties") { putJsonObject("value") { put("type", "string") } }
        }
        val tools = listOf(
            ToolSpec("zeta", "z", objectSchema("a")),
            ToolSpec("alpha", "a", schema),
            ToolSpec("mid", "m", objectSchema("b")),
        )

        val text = encode(call(tools = tools))
        val names = Json.parseToJsonElement(text).jsonObject.getValue("tools").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }

        assertEquals(listOf("alpha", "mid", "zeta"), names)
        assertTrue(text.contains("\"input_schema\":" + schema.toString()))
        assertTrue(schema.toString().startsWith("{\"required\":"))
    }

    @Test
    fun encodingTheSameRequestTwiceOrAnEqualOneGivesIdenticalBytes() {
        val first = call()

        assertArrayEquals(encodeAnthropicRequest(first), encodeAnthropicRequest(first))
        assertArrayEquals(encodeAnthropicRequest(first), encodeAnthropicRequest(call()))
    }

    @Test
    fun languageDateAndTranscriptNeverTouchTheBytesBeforeTheMessages() {
        val spanish = encode(call(messages = listOf(UserMessage("Responde en espanol. Fecha 2026-09-30. pon leche"))))
        val english = encode(call(messages = listOf(UserMessage("Answer in English. Date 2026-10-01. add milk"))))

        val spanishPrefix = spanish.substringBefore("\"messages\":")
        val englishPrefix = english.substringBefore("\"messages\":")

        assertTrue(spanish.contains("\"messages\":"))
        assertArrayEquals(spanishPrefix.toByteArray(Charsets.UTF_8), englishPrefix.toByteArray(Charsets.UTF_8))
        assertNotEquals(spanish, english)
    }

    @Test
    fun topLevelKeysAppearInTheFixedOrderAndNothingElseIsSent() {
        val keys = parse(call()).keys.toList()
        assertEquals(listOf("model", "max_tokens", "tools", "tool_choice", "system", "messages"), keys)

        val noTools = parse(call(tools = emptyList())).keys.toList()
        assertEquals(listOf("model", "max_tokens", "system", "messages"), noTools)

        val forbidden = listOf("thinking", "temperature", "top_p", "top_k", "stream", "metadata", "stop_sequences")
        assertTrue(keys.none { it in forbidden })
    }

    @Test
    fun toolChoiceAndStrictFollowTheRequestAndTheCapabilities() {
        val auto = parse(call()).getValue("tool_choice")
        assertEquals(buildJsonObject { put("type", "auto") }, auto)

        val forced = parse(call(toolChoice = ToolChoice.Required("add_item"))).getValue("tool_choice")
        assertEquals(buildJsonObject { put("type", "tool"); put("name", "add_item") }, forced)

        val noForcing = ModelCapabilities { supportsForcedToolChoice = false }
        val downgraded = parse(call(toolChoice = ToolChoice.Required("add_item"), capabilities = noForcing))
        assertEquals(buildJsonObject { put("type", "auto") }, downgraded.getValue("tool_choice"))

        val specs = listOf(tool("alpha", strict = true), tool("beta", strict = false), tool("gamma"))
        val tools = parse(call(tools = specs))
            .getValue("tools").jsonArray.map { it.jsonObject }
        assertTrue(tools[0].getValue("strict").jsonPrimitive.boolean)
        assertNull(tools[1]["strict"])
        assertNull(tools[2]["strict"])
    }

    @Test
    fun aSingleToolCallPutsTheParallelOffSwitchInsideToolChoiceInEveryShape() {
        val forced = parse(call(toolChoice = ToolChoice.Required("add_item"), singleToolCall = true))
        assertEquals(
            buildJsonObject { put("type", "tool"); put("name", "add_item"); put("disable_parallel_tool_use", true) },
            forced.getValue("tool_choice"),
        )

        val noForcing = ModelCapabilities { supportsForcedToolChoice = false }
        val reshaped = parse(
            call(toolChoice = ToolChoice.Required("add_item"), capabilities = noForcing, singleToolCall = true),
        )
        assertEquals(
            buildJsonObject { put("type", "auto"); put("disable_parallel_tool_use", true) },
            reshaped.getValue("tool_choice"),
        )

        val auto = parse(call(singleToolCall = true))
        assertEquals(
            buildJsonObject { put("type", "auto"); put("disable_parallel_tool_use", true) },
            auto.getValue("tool_choice"),
        )
    }

    @Test
    fun withoutTheFlagNoToolChoiceByteChanges() {
        val text = encode(call(toolChoice = ToolChoice.Required("add_item")))

        assertTrue(text.contains("\"tool_choice\":{\"type\":\"tool\",\"name\":\"add_item\"}"))
        assertFalse(text.contains("disable_parallel_tool_use"))
        assertTrue(encode(call()).contains("\"tool_choice\":{\"type\":\"auto\"}"))
    }

    @Test
    fun aSingleToolCallRequestWithoutToolsSendsNoToolChoiceAndNoSwitch() {
        val text = encode(call(tools = emptyList(), singleToolCall = true))

        assertNull(parse(call(tools = emptyList(), singleToolCall = true))["tool_choice"])
        assertFalse(text.contains("disable_parallel_tool_use"))
    }

    @Test
    fun theSingleToolCallFlagKeepsTheKeyOrderAndTheCachedPrefixUntouched() {
        val flagged = parse(call(toolChoice = ToolChoice.Required("add_item"), singleToolCall = true))
        val plain = parse(call(toolChoice = ToolChoice.Required("add_item")))

        assertEquals(
            listOf("model", "max_tokens", "tools", "tool_choice", "system", "messages"),
            flagged.keys.toList(),
        )
        assertEquals(plain.getValue("tools"), flagged.getValue("tools"))
        assertEquals(plain.getValue("system"), flagged.getValue("system"))
        assertEquals(plain.getValue("messages"), flagged.getValue("messages"))
    }

    @Test
    fun aToolResultBatchIsOneUserMessageOfToolResultBlocksInOrder() {
        val results = ToolResultsMessage(
            listOf(ToolResult("toolu_1", "added", false), ToolResult("toolu_2", "no such list", true)),
        )

        val messages = parse(call(messages = listOf(UserMessage("go"), results))).getValue("messages").jsonArray
        val batch = messages[1].jsonObject
        val blocks = batch.getValue("content").jsonArray.map { it.jsonObject }

        assertEquals(2, messages.size)
        assertEquals("user", batch.getValue("role").jsonPrimitive.content)
        assertEquals(listOf("toolu_1", "toolu_2"), blocks.map { it.getValue("tool_use_id").jsonPrimitive.content })
        assertTrue(blocks.all { it.getValue("type").jsonPrimitive.content == "tool_result" })
        assertEquals(listOf("added", "no such list"), blocks.map { it.getValue("content").jsonPrimitive.content })
        assertNull(blocks[0]["is_error"])
        assertTrue(blocks[1].getValue("is_error").jsonPrimitive.boolean)
    }

    private fun callsOf(vararg ids: String): AssistantMessage =
        AssistantMessage(ids.map { AssistantPart.ToolCall(it, "add_item", buildJsonObject { }) })

    private fun resultBlocks(vararg results: ToolResult): List<JsonObject> {
        val calls = callsOf("toolu_1", "toolu_2", "toolu_3")
        val request = call(messages = listOf(UserMessage("go"), calls, ToolResultsMessage(results.toList())))
        val messages = parse(request).getValue("messages").jsonArray
        return messages.last().jsonObject.getValue("content").jsonArray.map { it.jsonObject }
    }

    @Test
    fun resultsGoOutInTheOrderOfTheCallsTheyAnswerInOneUserMessage() {
        val request = call(
            messages = listOf(
                UserMessage("go"),
                callsOf("toolu_1", "toolu_2", "toolu_3"),
                ToolResultsMessage(
                    listOf(ToolResult("toolu_3", "c"), ToolResult("toolu_1", "a"), ToolResult("toolu_2", "b")),
                ),
            ),
        )

        val messages = parse(request).getValue("messages").jsonArray
        val blocks = messages.last().jsonObject.getValue("content").jsonArray.map { it.jsonObject }

        assertEquals(3, messages.size)
        assertEquals("user", messages.last().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(
            listOf("toolu_1", "toolu_2", "toolu_3"),
            blocks.map { it.getValue("tool_use_id").jsonPrimitive.content },
        )
        assertEquals(listOf("a", "b", "c"), blocks.map { it.getValue("content").jsonPrimitive.content })
    }

    @Test
    fun isErrorAppearsOnlyOnAnErrorResult() {
        val blocks = resultBlocks(ToolResult("toolu_1", "ok"), ToolResult("toolu_2", "bad", true))

        assertNull(blocks[0]["is_error"])
        assertTrue(blocks[1].getValue("is_error").jsonPrimitive.boolean)
    }

    @Test
    fun anEmptyResultCarriesNoContentKeyAndAnEmptyErrorKeepsIsError() {
        val blocks = resultBlocks(
            ToolResult("toolu_1", ""),
            ToolResult("toolu_2", "", true),
            ToolResult("toolu_3", "text"),
        )

        assertNull(blocks[0]["content"])
        assertNull(blocks[0]["is_error"])
        assertNull(blocks[1]["content"])
        assertTrue(blocks[1].getValue("is_error").jsonPrimitive.boolean)
        assertEquals("text", blocks[2].getValue("content").jsonPrimitive.content)
    }

    @Test
    fun theReshapeInstructionStaysAfterTheOrderedResults() {
        val request = call(
            messages = listOf(
                UserMessage("go"),
                callsOf("toolu_1", "toolu_2"),
                ToolResultsMessage(listOf(ToolResult("toolu_2", "b"), ToolResult("toolu_1", "a"))),
            ),
            toolChoice = ToolChoice.Required("add_item"),
        )

        val body = Json.parseToJsonElement(String(encodeAnthropicRequest(request, reshape = true), Charsets.UTF_8))
        val blocks = body.jsonObject.getValue("messages").jsonArray.last().jsonObject
            .getValue("content").jsonArray.map { it.jsonObject }

        assertEquals(3, blocks.size)
        assertEquals(
            listOf("toolu_1", "toolu_2"),
            blocks.take(2).map { it.getValue("tool_use_id").jsonPrimitive.content },
        )
        assertEquals("text", blocks.last().getValue("type").jsonPrimitive.content)
        assertEquals("Call the add_item tool with your result.", blocks.last().getValue("text").jsonPrimitive.content)
    }

    @Test
    fun aMatchingNativeReplayIsSentVerbatimANullOneIsRebuiltAndAnyOtherIsRefused() {
        val raw: JsonArray = buildJsonArray {
            add(thinkingBlock("pondering"))
            add(toolUseBlock("toolu_9", "add_item", buildJsonObject { put("item", "milk") }))
        }
        val parts = listOf(
            AssistantPart.Text("Adding it."),
            AssistantPart.Text(""),
            AssistantPart.ToolCall("toolu_9", "add_item", buildJsonObject { put("item", "milk") }),
        )
        fun assistantContent(replay: NativeReplay?): JsonArray =
            parse(call(messages = listOf(UserMessage("go"), AssistantMessage(parts, replay))))
                .getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonArray

        assertEquals(raw, assistantContent(NativeReplay(ProviderId.ANTHROPIC, model, raw)))

        val rebuilt = buildJsonArray {
            add(textBlock("Adding it."))
            add(toolUseBlock("toolu_9", "add_item", buildJsonObject { put("item", "milk") }))
        }
        assertEquals(rebuilt, assistantContent(null))
        listOf(
            NativeReplay(ProviderId.ANTHROPIC, "claude-opus-5-5", raw),
            NativeReplay(ProviderId.OPENAI, model, raw),
        ).forEach { other ->
            val refused = assertThrows(IllegalStateException::class.java) { assistantContent(other) }

            assertFalse(refused.message.orEmpty().contains(model))
            assertFalse(refused.message.orEmpty().contains("claude-opus-5-5"))
            assertFalse(refused.message.orEmpty().contains(ProviderId.OPENAI.toString()))
            assertFalse(refused.message.orEmpty().contains(ProviderId.ANTHROPIC.toString()))
        }
    }

    @Test
    fun aReplayedToolUseWithoutAnObjectInputGoesBackWithAnEmptyOneAndNothingElseChanges() {
        val noInput = buildJsonObject {
            put("type", "tool_use")
            put("id", "toolu_1")
            put("name", "list_items")
        }
        val nullInput = toolUseBlock("toolu_2", "list_items", JsonNull)
        val valid = toolUseBlock("toolu_3", "add_item", buildJsonObject { put("item", "milk") })
        val raw = JsonArray(listOf(thinkingBlock("pondering"), noInput, nullInput, valid))
        val parts = listOf(
            AssistantPart.ToolCall("toolu_1", "list_items", buildJsonObject { }),
            AssistantPart.ToolCall("toolu_2", "list_items", buildJsonObject { }),
            AssistantPart.ToolCall("toolu_3", "add_item", buildJsonObject { put("item", "milk") }),
        )
        val results = ToolResultsMessage(listOf("toolu_1", "toolu_2", "toolu_3").map { ToolResult(it, "ok") })
        val replayed = AssistantMessage(parts, NativeReplay(ProviderId.ANTHROPIC, model, raw))
        val request = call(messages = listOf(UserMessage("go"), replayed, results))

        val sent = parse(request).getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonArray
        val empty = JsonObject(emptyMap())
        val repairedNone = buildJsonObject {
            put("type", "tool_use")
            put("id", "toolu_1")
            put("name", "list_items")
            put("input", empty)
        }
        assertEquals(
            JsonArray(listOf(raw[0], repairedNone, toolUseBlock("toolu_2", "list_items", empty), valid)),
            sent,
        )
        assertEquals(encode(request), encode(request))
    }

    @Test
    fun theReplayShapeThePreflightAcceptsIsExactlyTheOneTheEncoderAccepts() {
        val shapes = listOf(JsonArray(emptyList()), JsonObject(emptyMap()), JsonNull, JsonPrimitive("x"))
        for (raw in shapes) {
            val message = AssistantMessage(emptyList(), NativeReplay(ProviderId.ANTHROPIC, model, raw))
            val request = call(messages = listOf(UserMessage("go"), message))
            val accepted = anthropicReplayContent(raw) != null
            val encodes = runCatching { encode(request) }.isSuccess
            assertEquals(raw.toString(), accepted, encodes)
        }
    }
}
