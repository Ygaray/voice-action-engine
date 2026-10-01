package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LONG_SYSTEM_MIN_CHARS = 32_000

private fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

private fun array(text: String): JsonArray = parse(text) as JsonArray

private fun obj(text: String): JsonObject = parse(text) as JsonObject

private fun turn(messages: String, response: String): RecordedTurn = RecordedTurn(array(messages), obj(response))

private fun key(): String = "sk-" + "a1B2".repeat(5)

private val ANTHROPIC_CALLS = """
    [{"type":"tool_use","id":"toolu_01X","name":"record_item","input":{"item":"apple"}},
     {"type":"tool_use","id":"toolu_01Y","name":"count_items","input":{}}]
""".trimIndent()

private fun anthropicConversation(): List<RecordedTurn> = listOf(
    turn(
        """[{"role":"user","content":"go"}]""",
        """{"id":"msg_01Ab","type":"message","role":"assistant","content":$ANTHROPIC_CALLS,"stop_reason":"tool_use"}""",
    ),
    turn(
        """
        [{"role":"user","content":"go"},
         {"role":"assistant","content":$ANTHROPIC_CALLS},
         {"role":"user","content":[
           {"type":"tool_result","tool_use_id":"toolu_01X","content":"ok"},
           {"type":"tool_result","tool_use_id":"toolu_01Y","content":"2"}]}]
        """.trimIndent(),
        """{"id":"msg_01Cd","type":"message","role":"assistant","content":[{"type":"text","text":"done"}]}""",
    ),
)

private fun chatConversation(): List<RecordedTurn> = listOf(
    turn(
        """[{"role":"user","content":"go"}]""",
        """
        {"id":"chatcmpl-abc","object":"chat.completion","created":1700000000,"system_fingerprint":"fp_1",
         "choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[
           {"id":"call_aa","type":"function","function":{"name":"record_item","arguments":"{}"}},
           {"id":"call_bb","type":"function","function":{"name":"count_items","arguments":"{}"}}]}}],
         "usage":{"prompt_tokens":3,"service_tier":"default","cost":0.5,"cost_details":{"a":1}},
         "service_tier":"default","cost":0.1,"user_id":"u1"}
        """.trimIndent(),
    ),
    turn(
        """
        [{"role":"user","content":"go"},
         {"role":"assistant","content":null,"tool_calls":[
           {"id":"call_aa","type":"function","function":{"name":"record_item","arguments":"{}"}},
           {"id":"call_bb","type":"function","function":{"name":"count_items","arguments":"{}"}}]},
         {"role":"tool","tool_call_id":"call_aa","content":"ok"},
         {"role":"tool","tool_call_id":"call_bb","content":"2"}]
        """.trimIndent(),
        """{"id":"gen-xyz","choices":[{"message":{"role":"assistant","content":"done"}}]}""",
    ),
)

private fun sanitized(turns: List<RecordedTurn>): JsonArray =
    (parse(ConversationSanitizer().conversation(turns)) as JsonObject)["turns"]!!.jsonArray

private fun JsonElement.at(vararg path: Any): JsonElement {
    var node: JsonElement = this
    for (step in path) {
        node = if (step is Int) node.jsonArray[step] else node.jsonObject.getValue(step as String)
    }
    return node
}

private fun JsonElement.text(vararg path: Any): String = at(*path).jsonPrimitive.content

class ConversationSanitizerTest {

    @Test
    fun theScriptHoldsThreeNonStrictDomainFreeTools() {
        val tools = ConversationScript.tools()
        assertEquals(listOf("record_item", "count_items", "lookup_item"), tools.map { it.name })
        tools.forEach { assertEquals(it.name, false, it.strict) }
        assertEquals(
            """{"type":"object","properties":{},"additionalProperties":false}""",
            tools.single { it.name == "count_items" }.inputSchema.toString(),
        )
        assertEquals(
            """{"type":"object","properties":{"item":{"type":"string"}},"required":["item"],""" +
                """"additionalProperties":false}""",
            tools.single { it.name == "record_item" }.inputSchema.toString(),
        )
        assertEquals(
            """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"],""" +
                """"additionalProperties":false}""",
            tools.single { it.name == "lookup_item" }.inputSchema.toString(),
        )
        assertTrue(tools.none { it.mutating || it.terminal })
    }

    @Test
    fun theScriptAnswersEachToolWithItsFixedResult() {
        val record = ConversationScript.resultFor(AssistantPart.ToolCall("c1", "record_item", JsonObject(emptyMap())))
        val count = ConversationScript.resultFor(AssistantPart.ToolCall("c2", "count_items", JsonObject(emptyMap())))
        val lookup = ConversationScript.resultFor(AssistantPart.ToolCall("c3", "lookup_item", JsonObject(emptyMap())))
        val other = ConversationScript.resultFor(AssistantPart.ToolCall("c4", "mystery", JsonObject(emptyMap())))
        assertEquals(Triple("c1", "ok", false), Triple(record.callId, record.content, record.isError))
        assertEquals(Triple("c2", "2", false), Triple(count.callId, count.content, count.isError))
        assertEquals(
            Triple("c3", "no item with that name", true),
            Triple(lookup.callId, lookup.content, lookup.isError),
        )
        assertEquals(Triple("c4", "unknown tool", true), Triple(other.callId, other.content, other.isError))
    }

    @Test
    fun resultsAnswerEveryCallOfATurnInOrder() {
        val turn = AssistantMessage(
            listOf(
                AssistantPart.Text("working"),
                AssistantPart.ToolCall("c1", "record_item", JsonObject(emptyMap())),
                AssistantPart.ToolCall("c2", "lookup_item", JsonObject(emptyMap())),
            ),
        )
        val batch = ConversationScript.results(turn)
        assertEquals(listOf("c1", "c2"), batch.results.map { it.callId })
        assertEquals(listOf(false, true), batch.results.map { it.isError })
    }

    @Test
    fun aRequestUsesAutoChoiceCachedPrefixAndTheGivenMaxTokens() {
        val short = ConversationScript.request(listOf(UserMessage(ConversationScript.USER_PROMPT)), false, 77)
        assertEquals(ConversationScript.SHORT_SYSTEM, short.system)
        assertEquals(ToolChoice.Auto(), short.toolChoice)
        assertEquals(CacheDirective(true), short.cache)
        assertEquals(77, short.maxTokens)
        assertFalse(short.singleToolCall)
        assertEquals(3, short.tools.size)
        val long = ConversationScript.request(listOf(UserMessage("x")), true, ConversationScript.MAX_TOKENS)
        assertTrue(long.system.length >= LONG_SYSTEM_MIN_CHARS)
        assertTrue(long.system.startsWith(ConversationScript.SHORT_SYSTEM))
        assertEquals(ConversationScript.longSystem(), long.system)
        assertEquals(1024, ConversationScript.MAX_TOKENS)
        assertEquals(2048, ConversationScript.THINKING_MAX_TOKENS)
    }

    @Test
    fun theScriptNamesNoAppDomain() {
        val everything = ConversationScript.SHORT_SYSTEM + ConversationScript.USER_PROMPT +
            ConversationScript.tools().joinToString { it.name + it.description + it.inputSchema }
        listOf("food", "note", "card").forEach {
            assertFalse(it, everything.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun anAnthropicConversationKeepsOneIdMapAcrossTurns() {
        val turns = sanitized(anthropicConversation())
        assertEquals("msg_GOLDEN1", turns.at(0, "response").text("id"))
        assertEquals("toolu_GOLDEN1", turns.at(0, "response", "content", 0).text("id"))
        assertEquals("toolu_GOLDEN2", turns.at(0, "response", "content", 1).text("id"))
        assertEquals("toolu_GOLDEN1", turns.at(1, "messages", 1, "content", 0).text("id"))
        assertEquals("toolu_GOLDEN2", turns.at(1, "messages", 1, "content", 1).text("id"))
        assertEquals("toolu_GOLDEN1", turns.at(1, "messages", 2, "content", 0).text("tool_use_id"))
        assertEquals("toolu_GOLDEN2", turns.at(1, "messages", 2, "content", 1).text("tool_use_id"))
        assertEquals("msg_GOLDEN2", turns.at(1, "response").text("id"))
    }

    @Test
    fun aChatConversationKeepsCallNumbersAndCleansTheEnvelope() {
        val turns = sanitized(chatConversation())
        assertEquals("chatcmpl-GOLDEN1", turns.at(0, "response").text("id"))
        assertEquals("gen-GOLDEN1", turns.at(1, "response").text("id"))
        val calls = turns.at(0, "response", "choices", 0, "message", "tool_calls")
        assertEquals("call_GOLDEN1", calls.text(0, "id"))
        assertEquals("call_GOLDEN2", calls.text(1, "id"))
        assertEquals("call_GOLDEN1", turns.at(1, "messages", 2).text("tool_call_id"))
        assertEquals("call_GOLDEN2", turns.at(1, "messages", 3).text("tool_call_id"))
        assertEquals("call_GOLDEN1", turns.at(1, "messages", 1, "tool_calls").text(0, "id"))
        val response = turns.at(0, "response").jsonObject
        assertEquals(JsonPrimitive(0), response["created"])
        assertEquals(JsonNull, response["system_fingerprint"])
        assertNull(response["service_tier"])
        assertNull(response["cost"])
        assertNull(response["user_id"])
        assertNull(response.getValue("usage").jsonObject["service_tier"])
        assertNull(response.getValue("usage").jsonObject["cost"])
        assertNull(response.getValue("usage").jsonObject["cost_details"])
        assertEquals(JsonPrimitive(3), response.getValue("usage").jsonObject["prompt_tokens"])
    }

    @Test
    fun reasoningValuesComeOutByteIdentical() {
        val thinking = "I will call_abc then toolu_x"
        val signature = "EqQBCkgIARABGAIiQ+/Z"
        val opaque = "Zm9v+/YmFy=="
        val reasoning = "plan: gen-abc and msg_q"
        val details = array("""[{"type":"reasoning.encrypted","data":"toolu_zz==","id":"rs_GOLDEN1","index":0}]""")
        val response = buildJsonObject {
            put("id", "msg_01Ab")
            put(
                "content",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "thinking")
                            put("thinking", thinking)
                            put("signature", signature)
                        },
                        buildJsonObject {
                            put("type", "redacted_thinking")
                            put("data", opaque)
                        },
                    ),
                ),
            )
            put("reasoning", reasoning)
            put("reasoning_details", details)
        }
        val turns = sanitized(listOf(RecordedTurn(array("""[{"role":"user","content":"go"}]"""), response)))
        val out = turns.at(0, "response")
        assertEquals("msg_GOLDEN1", out.text("id"))
        assertEquals(thinking, out.text("content", 0, "thinking"))
        assertEquals(signature, out.text("content", 0, "signature"))
        assertEquals(opaque, out.text("content", 1, "data"))
        assertEquals(reasoning, out.text("reasoning"))
        assertEquals(details, out.at("reasoning_details"))
    }

    @Test
    fun anIdInsideReasoningDetailsIsScrubbedWhileItsSignedValuesStayExact() {
        val details = array(
            """[{"type":"reasoning.text","text":"call_x","signature":"toolu_y==","id":"rs_real1","index":0},
               {"type":"reasoning.encrypted","data":"fc_zz==","id":"rs_real1","summary":"resp_q"}]""",
        )
        val response = buildJsonObject {
            put("id", "resp_real")
            put("reasoning_details", details)
        }
        val turns = sanitized(listOf(RecordedTurn(array("""[{"role":"user","content":"go"}]"""), response)))
        val out = turns.at(0, "response")
        assertEquals("resp_GOLDEN1", out.text("id"))
        assertEquals("rs_GOLDEN1", out.text("reasoning_details", 0, "id"))
        assertEquals("rs_GOLDEN1", out.text("reasoning_details", 1, "id"))
        assertEquals("call_x", out.text("reasoning_details", 0, "text"))
        assertEquals("toolu_y==", out.text("reasoning_details", 0, "signature"))
        assertEquals("fc_zz==", out.text("reasoning_details", 1, "data"))
        assertEquals("resp_q", out.text("reasoning_details", 1, "summary"))
    }

    @Test
    fun aStructuredValueUnderAnExemptKeyIsCleanedLikeAnyOther() {
        val body = obj("""{"data":{"id":"call_real9"},"content":[{"id":"toolu_real9"}]}""")
        val clean = ConversationSanitizer().sanitize(body)
        assertEquals("call_GOLDEN1", clean.text("data", "id"))
        assertEquals("toolu_GOLDEN1", clean.text("content", 0, "id"))
    }

    @Test
    fun aKeyOutsideReasoningIsRedactedButInsideItRefusesTheConversation() {
        val text = buildJsonObject { put("text", "the key is ${key()} ok and Bearer abcdef.ghi") }
        val clean = ConversationSanitizer().sanitize(text)
        assertEquals("the key is redacted ok and redacted", clean.text("text"))

        listOf("thinking", "signature", "data", "reasoning").forEach { name ->
            listOf(key(), "Bearer abcdef.ghi").forEach { secret ->
                val body = buildJsonObject { put(name, "before $secret after") }
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    ConversationSanitizer().sanitize(body)
                }
                assertFalse(failure.message, failure.message.orEmpty().contains(secret))
                assertFalse(failure.message, failure.message.orEmpty().contains("abcdef"))
            }
        }
        val nested = obj("""{"reasoning_details":[{"text":"x"}]}""")
        assertEquals(nested, ConversationSanitizer().sanitize(nested))
    }

    @Test
    fun aResponseHoldingAnErrorObjectIsRefused() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ConversationSanitizer().conversation(
                listOf(turn("""[{"role":"user","content":"go"}]""", """{"error":{"message":"secret text"}}""")),
            )
        }
        assertFalse(failure.message, failure.message.orEmpty().contains("secret text"))
        assertThrows(IllegalArgumentException::class.java) {
            ConversationSanitizer().sanitize(obj("""{"type":"error","error":{"type":"x","message":"y"}}"""))
        }
    }

    @Test
    fun outputIsCanonicalHygienicAndStableUnderASecondPass() {
        val first = ConversationSanitizer().conversation(anthropicConversation())
        assertEquals(canonicalJson(first), first)
        assertEquals(emptyList<String>(), conversationHygieneViolations(first))
        val again = ConversationSanitizer().conversation(
            parseConversation(first).map { RecordedTurn(it.messages, it.response) },
        )
        assertEquals(first, again)

        val chat = ConversationSanitizer().conversation(chatConversation())
        assertEquals(emptyList<String>(), conversationHygieneViolations(chat))
        val chatAgain = ConversationSanitizer().conversation(
            parseConversation(chat).map { RecordedTurn(it.messages, it.response) },
        )
        assertEquals(chat, chatAgain)
    }

    @Test
    fun numberingFollowsFirstAppearanceAndGoldenIdsAreKeptNotCounted() {
        val turns = sanitized(
            listOf(
                turn(
                    """[{"role":"user","content":"x"}]""",
                    """
                    {"id":"msg_GOLDEN7","content":[{"id":"toolu_real1"},{"id":"toolu_real2"},{"id":"toolu_real1"}]}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals("msg_GOLDEN7", turns.at(0, "response").text("id"))
        assertEquals("toolu_GOLDEN1", turns.at(0, "response", "content", 0).text("id"))
        assertEquals("toolu_GOLDEN2", turns.at(0, "response", "content", 1).text("id"))
        assertEquals("toolu_GOLDEN1", turns.at(0, "response", "content", 2).text("id"))
    }

    @Test
    fun anEmptyConversationIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { ConversationSanitizer().conversation(emptyList()) }
    }

    @Test
    fun aRecordedTurnPrintsOnlyItsMessageCount() {
        assertEquals("RecordedTurn(messages=1)", turn("""[{"role":"user","content":"secret"}]""", "{}").toString())
    }
}
