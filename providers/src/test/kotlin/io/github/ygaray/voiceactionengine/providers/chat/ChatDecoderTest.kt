package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun failureOf(decoded: ChatDecoded): ModelResult.Failure = decoded.result as ModelResult.Failure

    private fun codeOf(decoded: ChatDecoded): String = failureOf(decoded).reason.code

    private fun forcedAnswer(): JsonObject =
        chatMessage(null, listOf(chatToolCall("call_GOLDEN1", "log_food", logFoodArguments)))

    private fun editCall(arguments: String): JsonObject = chatToolCall("call_GOLDEN2", "edit_card", arguments)

    private fun toolBody(arguments: String, finish: String? = "tool_calls"): String =
        chatBody(chatMessage(null, listOf(editCall(arguments))), finish)

    private fun rawToolCalls(vararg calls: String): String =
        """{"id":"chatcmpl-GOLDEN","choices":[{"index":0,"finish_reason":"tool_calls","message":""" +
            """{"role":"assistant","content":null,"tool_calls":[${calls.joinToString(",")}]}}]}"""

    // ---- tracer ----------------------------------------------------------------------------------------------

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

    // ---- unusable bodies -------------------------------------------------------------------------------------

    @Test
    fun anUnusableBodyIsMalformedAndNeverThrows() {
        val bodies = listOf(null, "", "   ", "not json", "[1,2]", """{"choices":"x"}""", """{"choices":[1]}""")
        for (body in bodies) {
            val decoded = decode(body)
            assertEquals("malformed_response", codeOf(decoded))
            assertFalse(decoded.transient)
            assertEquals(0, decoded.toolCalls)
        }
    }

    @Test
    fun emptyChoicesOrAChoiceWithoutAMessageAreMalformed() {
        assertEquals("malformed_response", codeOf(decode("""{"choices":[]}""")))
        assertEquals("malformed_response", codeOf(decode("""{"choices":[{"finish_reason":"stop"}]}""")))
        assertEquals("malformed_response", codeOf(decode("""{"choices":[{"message":"hi","finish_reason":"stop"}]}""")))
    }

    // ---- the 200 error envelope ------------------------------------------------------------------------------

    @Test
    fun anOpenRouterRateLimitInsideA200IsRateLimitedAndTransient() {
        val decoded = decode(chatErrorEnvelope(429, "Rate limit CANARY-ECHO"), vendor = ChatVendor.OPENROUTER)
        assertEquals("rate_limited", codeOf(decoded))
        assertEquals(429, failureOf(decoded).details?.httpStatus)
        assertTrue(decoded.transient)
    }

    @Test
    fun anErrorBesideEmptyChoicesIsHttpErrorWithTheEnvelopeStatus() {
        val body = """{"id":"gen-GOLDEN","choices":[],"error":{"code":502,"message":"upstream CANARY-ECHO"}}"""
        val decoded = decode(body, vendor = ChatVendor.OPENROUTER)
        assertEquals("http_error", codeOf(decoded))
        assertEquals(502, failureOf(decoded).details?.httpStatus)
        assertEquals("gen-GOLDEN", failureOf(decoded).details?.requestId)
        assertTrue(decoded.transient)
    }

    @Test
    fun anOpenAiShapedErrorOnA200IsFinalHttpError() {
        val decoded = decode(openAiErrorBody("server_error", "internal_error", "boom CANARY-ECHO"))
        assertEquals("http_error", codeOf(decoded))
        assertEquals(200, failureOf(decoded).details?.httpStatus)
        assertEquals("internal_error", failureOf(decoded).details?.providerErrorType)
        assertFalse(decoded.transient)
    }

    // ---- finish reasons --------------------------------------------------------------------------------------

    @Test
    fun finishReasonErrorIsHttpErrorCarryingTheNativeFinishReason() {
        val body = chatBody(chatMessage(null), "error", nativeFinishReason = "MALFORMED_FUNCTION_CALL", id = "gen-GOLDEN")
        val decoded = decode(body, vendor = ChatVendor.OPENROUTER)
        val details = failureOf(decoded).details
        assertEquals("http_error", codeOf(decoded))
        assertEquals(200, details?.httpStatus)
        assertEquals("MALFORMED_FUNCTION_CALL", details?.providerErrorType)
        assertEquals("gen-GOLDEN", details?.requestId)
    }

    @Test
    fun aMissingOrHostileNativeFinishReasonIsDropped() {
        val plain = chatBody(chatMessage(null), "error")
        assertNull(failureOf(decode(plain)).details?.providerErrorType)
        val hostile = chatBody(chatMessage(null), "error", nativeFinishReason = "bad value CANARY-ECHO")
        val details = failureOf(decode(hostile)).details
        assertNull(details?.providerErrorType)
        assertEquals(200, details?.httpStatus)
    }

    @Test
    fun aRefusalIsASuccessWithTheRefusalStopReasonAndNoTextInTheParts() {
        val message = chatMessage(null, refusal = "I can't help with that")
        val decoded = decode(chatBody(message, "stop"))
        val response = success(decoded)
        assertEquals(StopReason.REFUSAL, response.stopReason)
        assertEquals(0, response.message.parts.size)
        assertEquals(0, decoded.toolCalls)
        assertFalse(decoded.toString().contains("help with that"))
    }

    @Test
    fun contentFilterIsARefusalAndARefusalBeatsToolCalls() {
        val filtered = success(decode(chatBody(chatMessage("partial CANARY-BODY"), "content_filter")))
        assertEquals(StopReason.REFUSAL, filtered.stopReason)
        assertEquals(0, filtered.message.parts.size)

        val both = chatMessage(null, listOf(editCall("{}")), refusal = "no")
        val decoded = decode(chatBody(both, "tool_calls"))
        assertEquals(StopReason.REFUSAL, success(decoded).stopReason)
        assertEquals(0, decoded.toolCalls)
    }

    @Test
    fun lengthIsMaxTokensAndTruncatedArgumentsAreNeverDecoded() {
        val decoded = decode(toolBody("""{"card_id":"c-""", finish = "length"))
        val response = success(decoded)
        assertEquals(StopReason.MAX_TOKENS, response.stopReason)
        assertEquals(0, response.message.toolCalls.size)
        assertEquals(0, decoded.toolCalls)
    }

    @Test
    fun toolCallsDecideTheTurnWhateverTheFinishReasonSays() {
        for (finish in listOf("stop", "tool_calls", null, "eos")) {
            val decoded = decode(toolBody("""{"card_id":"c-7"}""", finish))
            assertEquals(StopReason.TOOL_USE, success(decoded).stopReason)
            assertEquals(finish, decoded.finishReason)
            assertEquals(1, decoded.toolCalls)
        }
    }

    // ---- tool-call arguments ---------------------------------------------------------------------------------

    @Test
    fun argumentsDecodeStrictly() {
        val empty = success(decode(toolBody(""))).message.toolCalls.single().arguments
        assertEquals(JsonObject(emptyMap()), empty)

        val obj = success(decode(toolBody("""{"card_id":"c-7"}"""))).message.toolCalls.single().arguments
        assertEquals(Json.parseToJsonElement("""{"card_id":"c-7"}"""), obj)

        val asObject = rawToolCalls(
            """{"id":"call_GOLDEN2","type":"function","function":{"name":"edit_card","arguments":{"card_id":"c-9"}}}""",
        )
        val accepted = success(decode(asObject)).message.toolCalls.single().arguments
        assertEquals(Json.parseToJsonElement("""{"card_id":"c-9"}"""), accepted)
    }

    @Test
    fun badArgumentsAreMalformedToolArgs() {
        for (arguments in listOf("not json CANARY-BODY", "[1]", "42", "null", "\"text\"", " ")) {
            assertEquals(arguments, "malformed_tool_args", codeOf(decode(toolBody(arguments))))
        }
        val jsonNull = rawToolCalls(
            """{"id":"call_GOLDEN2","type":"function","function":{"name":"edit_card","arguments":null}}""",
        )
        assertEquals("malformed_tool_args", codeOf(decode(jsonNull)))
        val missing = rawToolCalls("""{"id":"call_GOLDEN2","type":"function","function":{"name":"edit_card"}}""")
        assertEquals("malformed_tool_args", codeOf(decode(missing)))
    }

    @Test
    fun aBlankIdOrNameIsMalformed() {
        val blankId = chatBody(chatMessage(null, listOf(chatToolCall(" ", "edit_card", "{}"))), "tool_calls")
        val blankName = chatBody(chatMessage(null, listOf(chatToolCall("call_GOLDEN2", "", "{}"))), "tool_calls")
        assertEquals("malformed_response", codeOf(decode(blankId)))
        assertEquals("malformed_response", codeOf(decode(blankName)))
    }

    @Test
    fun aNonFunctionCallIsSkipped() {
        val custom = """{"id":"call_GOLDEN3","type":"custom","custom":{"name":"x","input":"y"}}"""
        val function = """{"id":"call_GOLDEN2","type":"function","function":{"name":"edit_card","arguments":"{}"}}"""
        val mixed = decode(rawToolCalls(custom, function))
        assertEquals(1, mixed.toolCalls)
        assertEquals("call_GOLDEN2", success(mixed).message.toolCalls.single().id)

        assertEquals("malformed_response", codeOf(decode(rawToolCalls(custom))))
    }

    // ---- no tool call ----------------------------------------------------------------------------------------

    @Test
    fun noToolCallDependsOnWhetherOneWasRequired() {
        val body = chatBody(chatMessage("Sure, done."), "stop")
        assertEquals("no_tool_call", codeOf(decode(body, toolRequired = true)))

        val response = success(decode(body, toolRequired = false))
        assertEquals(StopReason.END_TURN, response.stopReason)
        assertEquals("Sure, done.", (response.message.parts.single() as AssistantPart.Text).text)
    }

    @Test
    fun aToolCallsFinishWithoutACallIsMalformedAndUnknownEndingsAreOther() {
        assertEquals("malformed_response", codeOf(decode(chatBody(chatMessage(null), "tool_calls"))))
        assertEquals(StopReason.OTHER, success(decode(chatBody(chatMessage("hi"), "eos"))).stopReason)
        assertEquals(StopReason.OTHER, success(decode(chatBody(chatMessage("hi"), null))).stopReason)
        val missingFinish = """{"choices":[{"message":{"role":"assistant","content":"hi"}}]}"""
        assertEquals(StopReason.OTHER, success(decode(missingFinish)).stopReason)
    }
}
