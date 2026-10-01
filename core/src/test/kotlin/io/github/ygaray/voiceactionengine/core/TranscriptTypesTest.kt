package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Modifier

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

    private fun searchTool(): ToolSpec = ToolSpec(
        "search_items",
        "Search items",
        buildJsonObject { put("type", "object") },
    )

    private fun args(): JsonObject = buildJsonObject { put("q", CANARY_ARGS) }

    private fun assertRejects(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException for $what")
        } catch (expected: IllegalArgumentException) {
            assertFalse(what, (expected.message ?: "").contains(CANARY))
        }
    }

    @Test
    fun multiTurnToolConversationIsExpressibleAndReadsBack() {
        val first = AssistantPart.ToolCall("call_1", "log_item", args())
        val second = AssistantPart.ToolCall("call_2", "search_items", args())
        val assistant = AssistantMessage(
            listOf(AssistantPart.Text("on it"), first, second),
            NativeReplay(ProviderId.ANTHROPIC, "model-a", JsonNull),
        )
        val results = ToolResultsMessage(
            listOf(ToolResult("call_1", "ok", false), ToolResult("call_2", "boom", true)),
        )
        val finalTurn = AssistantMessage(listOf(AssistantPart.Text("all done")))

        val request = ModelRequest(
            "system",
            listOf(UserMessage("log and search"), assistant, results, finalTurn),
            listOf(logItemTool(), searchTool()),
            ToolChoice.Auto(),
            256,
            CacheDirective(staticPrefix = true, conversationTail = true),
        )

        assertEquals(4, request.messages.size)
        assertSame(results, request.messages[2])
        assertEquals(2, request.tools.size)
        assertEquals(ToolChoice.Auto(), request.toolChoice)
        assertEquals(listOf(first, second), assistant.toolCalls)
        assertEquals(3, assistant.parts.size)
        assertEquals(2, results.results.size)
        assertEquals("call_1", results.results[0].callId)
        assertEquals("ok", results.results[0].content)
        assertFalse(results.results[0].isError)
        assertEquals("call_2", results.results[1].callId)
        assertTrue(results.results[1].isError)
        assertTrue(request.cache.conversationTail)
    }

    @Test
    fun toolResultShortConstructorIsNotAnError() {
        assertFalse(ToolResult("call_1", "fine").isError)
    }

    @Test
    fun listsAreCopiedOnConstruction() {
        val parts = mutableListOf<AssistantPart>(AssistantPart.Text("a"))
        val results = mutableListOf(ToolResult("c1", "r"))
        val messages = mutableListOf<Message>(UserMessage("u"))
        val tools = mutableListOf(logItemTool())

        val assistant = AssistantMessage(parts)
        val batch = ToolResultsMessage(results)
        val request = ModelRequest("s", messages, tools, 8)
        parts.add(AssistantPart.ToolCall("c2", "x", args()))
        results.add(ToolResult("c3", "r"))
        messages.add(UserMessage("later"))
        tools.add(searchTool())

        assertEquals(1, assistant.parts.size)
        assertTrue(assistant.toolCalls.isEmpty())
        assertEquals(1, batch.results.size)
        assertEquals(1, request.messages.size)
        assertEquals(1, request.tools.size)
    }

    @Test
    fun requestValidationRejectsBadShapes() {
        val user = listOf<Message>(UserMessage("u"))
        assertRejects("no messages") { ModelRequest("s", emptyList(), 8) }
        assertRejects("maxTokens 0") { ModelRequest("s", user, 0) }
        assertRejects("negative maxTokens") { ModelRequest("s", user, -1) }
        assertRejects("duplicate tool names") { ModelRequest("s", user, listOf(logItemTool(), logItemTool()), 8) }
        assertRejects("required tool absent") {
            ModelRequest("s", user, listOf(logItemTool()), ToolChoice.Required("missing"), 8, CacheDirective(true))
        }
        assertRejects("required tool with no tools") {
            ModelRequest("s", user, emptyList(), ToolChoice.Required("log_item"), 8, CacheDirective(true))
        }
        assertRejects("blank required name") { ToolChoice.Required(" ") }
    }

    @Test
    fun batchAndIdValidationRejectsBadShapes() {
        assertRejects("empty result batch") { ToolResultsMessage(emptyList()) }
        assertRejects("duplicate call id") {
            ToolResultsMessage(listOf(ToolResult("c1", "a"), ToolResult("c1", "b")))
        }
        assertRejects("blank call id") { AssistantPart.ToolCall(" ", "log_item", args()) }
        assertRejects("blank call name") { AssistantPart.ToolCall("c1", "", args()) }
        assertRejects("blank result call id") { ToolResult("", "x") }
    }

    @Test
    fun stopReasonIsAnOpenSnakeCaseVocabulary() {
        assertRejects("spaced stop reason") { StopReason("End Turn") }
        assertRejects("empty stop reason") { StopReason("") }
        assertEquals("tool_use", StopReason.TOOL_USE.value)
        val all = setOf(
            StopReason.END_TURN, StopReason.TOOL_USE, StopReason.MAX_TOKENS, StopReason.REFUSAL,
            StopReason.PAUSE_TURN, StopReason.CONTEXT_WINDOW_EXCEEDED, StopReason.OTHER,
        )
        assertEquals(SEVEN, all.size)
        assertEquals("end_turn", StopReason.END_TURN.toString())
        assertEquals(StopReason("pause_turn"), StopReason.PAUSE_TURN)
    }

    @Test
    fun toolChoiceAndCacheDirectiveHaveValueEquality() {
        assertEquals(ToolChoice.Required("a"), ToolChoice.Required("a"))
        assertEquals(ToolChoice.Required("a").hashCode(), ToolChoice.Required("a").hashCode())
        assertNotEquals(ToolChoice.Required("a"), ToolChoice.Required("b"))
        assertFalse(ToolChoice.Required("a") == ToolChoice.Auto())
        assertEquals(ToolChoice.Auto(), ToolChoice.Auto())
        assertEquals(CacheDirective(true), CacheDirective(true, false))
        assertEquals(CacheDirective(true).hashCode(), CacheDirective(true, false).hashCode())
        assertNotEquals(CacheDirective(true), CacheDirective(true, true))
        assertNotEquals(CacheDirective(true), CacheDirective(false))
    }

    @Test
    fun toStringNeverPrintsContent() {
        val replay = NativeReplay(ProviderId.ANTHROPIC, "model-a", buildJsonObject { put("thinking", CANARY) })
        val call = AssistantPart.ToolCall("call_1", "log_item", args())
        val text = AssistantPart.Text(CANARY)
        val assistant = AssistantMessage(listOf(text, call), replay)
        val result = ToolResult("call_1", CANARY, true)
        val batch = ToolResultsMessage(listOf(result))
        val user = UserMessage(CANARY)
        val request = ModelRequest(CANARY, listOf(user, assistant, batch), listOf(logItemTool()), 8)
        val response = ModelResponse(assistant, StopReason.TOOL_USE, Usage.ZERO, "req_9")

        val printed = listOf(user, assistant, batch, result, text, call, request, response, replay)
        for (item in printed) {
            assertFalse("${item::class.simpleName} leaked", item.toString().contains(CANARY))
        }
        assertEquals("ToolCall(id=call_1, name=log_item, argumentCount=1)", call.toString())
        assertEquals("ToolResult(callId=call_1, contentLength=${CANARY.length}, isError=true)", result.toString())
        assertEquals("ToolResultsMessage(results=1, errors=1)", batch.toString())
        assertEquals("UserMessage(textLength=${CANARY.length})", user.toString())
        assertTrue(assistant.toString().contains("log_item"))
        assertTrue(assistant.toString().contains("anthropic/model-a"))
        assertTrue(request.toString().contains("tools=[log_item]"))
        assertTrue(response.toString().contains("requestId=req_9"))
    }

    @Test
    fun sevenArgumentFormReadsSingleToolCallBackAsTrue() {
        val tool = logItemTool()
        val request = ModelRequest(
            "be brief",
            listOf(UserMessage("log a coffee")),
            listOf(tool),
            ToolChoice.Required("log_item"),
            512,
            CacheDirective(true),
            true,
        )

        assertTrue(request.singleToolCall)
        assertEquals(512, request.maxTokens)
    }

    @Test
    fun sixFourAndThreeArgumentFormsReadSingleToolCallAsFalse() {
        val user = UserMessage("hi")
        val tool = logItemTool()

        val six = ModelRequest("sys", listOf(user), listOf(tool), ToolChoice.Auto(), 64, CacheDirective(true))
        val four = ModelRequest("sys", listOf(user), listOf(tool), 64)
        val three = ModelRequest("sys", listOf(user), 64)

        assertFalse(six.singleToolCall)
        assertFalse(four.singleToolCall)
        assertFalse(three.singleToolCall)
    }

    @Test
    fun toStringShowsTheFlagAndStillHidesTheSystemText() {
        val request = ModelRequest(
            CANARY,
            listOf(UserMessage(CANARY)),
            listOf(logItemTool()),
            ToolChoice.Auto(),
            8,
            CacheDirective(true),
            true,
        )

        val printed = request.toString()

        assertTrue(printed, printed.endsWith("singleToolCall=true)"))
        assertFalse(printed, printed.contains(CANARY))
    }

    @Test
    fun modelRequestKeepsItsPublicConstructorShapes() {
        val cls = ModelRequest::class.java
        val string = String::class.java
        val list = List::class.java
        val choice = ToolChoice::class.java
        val cache = CacheDirective::class.java
        val int = Int::class.javaPrimitiveType
        val bool = Boolean::class.javaPrimitiveType
        val shapes = listOf(
            cls.getConstructor(string, list, int),
            cls.getConstructor(string, list, list, int),
            cls.getConstructor(string, list, list, choice, int, cache),
            cls.getConstructor(string, list, list, choice, int, cache, bool),
        )
        for (constructor in shapes) {
            assertTrue(constructor.toString(), Modifier.isPublic(constructor.modifiers))
            assertEquals(cls, constructor.declaringClass)
        }
    }

    private companion object {
        const val CANARY = "CANARY-TEXT"
        const val CANARY_ARGS = "CANARY-ARGS-VALUE"
        const val SEVEN = 7
    }
}
