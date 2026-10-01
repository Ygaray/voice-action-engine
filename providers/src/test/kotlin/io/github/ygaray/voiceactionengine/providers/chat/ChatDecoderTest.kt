package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.transcript.conversationViolation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
        val message = chatMessage(null)
        val body = chatBody(message, "error", nativeFinishReason = "MALFORMED_FUNCTION_CALL", id = "gen-GOLDEN")
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
        for (arguments in listOf("not json CANARY-BODY", "[1]", "42", "\"text\"")) {
            val failure = failureOf(decode(toolBody(arguments)))
            assertEquals(arguments, "malformed_tool_args", failure.reason.code)
            assertFalse(failure.toString().contains("CANARY-BODY"))
        }
        for (nonString in listOf("42", "[1]", "true")) {
            val call = rawToolCalls(
                """{"id":"call_GOLDEN2","type":"function","function":{"name":"edit_card","arguments":$nonString}}""",
            )
            assertEquals(nonString, "malformed_tool_args", codeOf(decode(call)))
        }
    }

    @Test
    fun emptyArgumentFormsDecodeAsAnEmptyObject() {
        val empty = JsonObject(emptyMap())
        for (arguments in listOf("", " ", "\n\t", "null", " null ", "{}")) {
            val decoded = success(decode(toolBody(arguments))).message.toolCalls.single().arguments
            assertEquals(arguments, empty, decoded)
        }
        val rawForms = listOf(
            """"arguments":null""",
            """"arguments":{}""",
            "",
        )
        for (form in rawForms) {
            val function = if (form.isEmpty()) """"name":"edit_card"""" else """"name":"edit_card",$form"""
            val call = rawToolCalls("""{"id":"call_GOLDEN2","type":"function","function":{$function}}""")
            val decoded = success(decode(call)).message.toolCalls.single().arguments
            assertEquals(form, empty, decoded)
        }
    }

    @Test
    fun theReplayRawKeepsEmptyArgumentsExactlyAsReceived() {
        val message = chatMessage(null, listOf(editCall("")))
        val response = success(decode(chatBody(message, "tool_calls")))
        assertEquals(empty(), response.message.toolCalls.single().arguments)
        assertEquals(message, response.message.nativeFor(ProviderId.OPENAI, model))
    }

    private fun empty(): JsonObject = JsonObject(emptyMap())

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

    // ---- the stored turn agrees with the neutral parts ----------------------------------------------------------

    private fun storedCalls(decoded: ChatDecoded): JsonArray? =
        ((success(decoded).message.nativeReplay!!.raw as JsonObject)["tool_calls"] as? JsonArray)

    private fun continuation(decoded: ChatDecoded): String? {
        val request = ModelRequest("", listOf(UserMessage("go"), success(decoded).message, UserMessage("again")), 64)
        val call = chatCall(ChatVendor.OPENAI, model, request)
        return conversationViolation(call, ProviderId.OPENAI) { it is JsonObject }
    }

    @Test
    fun aLengthStoppedTurnIsStoredWithoutTheTruncatedCallsSoTheConversationCanContinue() {
        val decoded = decode(toolBody("""{"card_id":"c-""", finish = "length"))
        assertNull(storedCalls(decoded))
        assertNull(continuation(decoded))
    }

    @Test
    fun aRefusedOrFilteredTurnIsStoredWithoutItsUndecodedCalls() {
        val refused = decode(chatBody(chatMessage(null, listOf(editCall("{}")), refusal = "no"), "tool_calls"))
        assertNull(storedCalls(refused))
        assertNull(continuation(refused))

        val filtered = decode(chatBody(chatMessage("part", listOf(editCall("{}"))), "content_filter"))
        assertNull(storedCalls(filtered))
        assertNull(continuation(filtered))
    }

    @Test
    fun aSkippedNonFunctionCallIsLeftOutOfTheStoredTurnAndTheKeptCallIsUntouched() {
        val custom = """{"id":"call_GOLDEN3","type":"custom","custom":{"name":"x","input":"y"}}"""
        val function = """{"id":"call_GOLDEN2","type":"function","function":{"name":"edit_card","arguments":"{}"}}"""
        val decoded = decode(rawToolCalls(custom, function))
        assertEquals(Json.parseToJsonElement("[$function]"), storedCalls(decoded))
    }

    @Test
    fun aTurnWhoseCallsAllDecodedIsStoredExactlyAsReceived() {
        val message = chatMessage(null, listOf(editCall("""{"card_id":"c-7"}""")))
        val decoded = decode(chatBody(message, "tool_calls"))
        assertEquals(message, success(decoded).message.nativeReplay!!.raw)
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

    // ---- usage -----------------------------------------------------------------------------------------------

    private fun usageOf(usage: JsonObject?): Usage =
        success(decode(chatBody(chatMessage("hi"), "stop", usage), toolRequired = false)).usage

    private fun rawUsage(usageJson: String): Usage {
        val body = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"hi"}}],""" +
            """"usage":$usageJson}"""
        return success(decode(body, toolRequired = false)).usage
    }

    @Test
    fun cachedTokensAreCountedOnceAndNotAsUncachedInput() {
        val usage = usageOf(chatUsage(1920, 55, cached = 1800))
        assertEquals(Usage(120, 1800, 0, 55), usage)
        assertEquals(1975L, usage.total)
    }

    @Test
    fun cacheWriteTokensLeaveTheUncachedBucketToo() {
        val usage = usageOf(chatUsage(2420, 55, cached = 1800, cacheWrite = 500))
        assertEquals(Usage(120, 1800, 500, 55), usage)
        assertEquals(2475L, usage.total)
    }

    @Test
    fun cacheCountsLargerThanThePromptAreClamped() {
        val usage = usageOf(chatUsage(1000, 20, cached = 3000, cacheWrite = 400))
        assertEquals(Usage(0, 1000, 0, 20), usage)
        assertTrue(usage.total <= 1000 + 20)
    }

    @Test
    fun missingUsageIsZeroAndBadCountsAreZero() {
        assertEquals(Usage.ZERO, usageOf(null))
        assertEquals(Usage.ZERO, rawUsage("""{"prompt_tokens":null,"completion_tokens":null}"""))
        assertEquals(Usage.ZERO, rawUsage("""{"prompt_tokens":-3,"completion_tokens":-5}"""))
        assertEquals(Usage.ZERO, rawUsage("""{"prompt_tokens":"12","completion_tokens":"1.5"}"""))
        assertEquals(Usage.ZERO, rawUsage("""{"prompt_tokens":7.5,"completion_tokens":true}"""))
        assertEquals(Usage.ZERO, rawUsage("[1,2]"))
        val noDetails = usageOf(chatUsage(100, 5))
        assertEquals(Usage(100, 0, 0, 5), noDetails)
        val nullDetails = """{"prompt_tokens":100,"completion_tokens":5,"prompt_tokens_details":null}"""
        assertEquals(Usage(100, 0, 0, 5), rawUsage(nullDetails))
    }

    @Test
    fun anOpenRouterUsageWithCostAndReasoningFieldsMapsToTheSameFourNumbers() {
        val plain = """{"prompt_tokens":2420,"completion_tokens":55,"total_tokens":2475,""" +
            """"prompt_tokens_details":{"cached_tokens":1800,"cache_write_tokens":500}}"""
        val rich = """{"prompt_tokens":2420,"completion_tokens":55,"total_tokens":2475,"cost":0.0123,""" +
            """"cost_details":{"upstream_inference_cost":0.01},"is_byok":false,""" +
            """"prompt_tokens_details":{"cached_tokens":1800,"cache_write_tokens":500,"audio_tokens":0},""" +
            """"completion_tokens_details":{"reasoning_tokens":30,"image_tokens":0}}"""
        assertEquals(Usage(120, 1800, 500, 55), rawUsage(plain))
        assertEquals(rawUsage(plain), rawUsage(rich))
    }

    // ---- absent optionals and no text in failures ------------------------------------------------------------

    @Test
    fun anOmittedOptionalStaysAbsentInTheDecodedArguments() {
        val sent = """{"card_id":"c-7","items":[{"text":"milk"}]}"""
        val arguments = success(decode(toolBody(sent))).message.toolCalls.single().arguments
        assertEquals(Json.parseToJsonElement(sent), arguments)
        assertEquals(setOf("card_id", "items"), arguments.keys)
        assertEquals(setOf("text"), (arguments["items"] as JsonArray).map { (it as JsonObject).keys }.single())
    }

    private fun assertNoCanary(decoded: ChatDecoded) {
        val texts = listOf(
            decoded.toString(),
            decoded.result.toString(),
            (decoded.result as? ModelResult.Failure)?.details.toString(),
            (decoded.result as? ModelResult.Failure)?.reason.toString(),
        )
        for (text in texts) {
            assertFalse(text, text.contains("CANARY"))
        }
    }

    @Test
    fun noAnswerTextReachesAFailureOrAToString() {
        val envelope = chatErrorEnvelope(502, "upstream said CANARY-ECHO", raw = "CANARY-ECHO raw")
        val failures = listOf(
            decode("CANARY-BODY not json"),
            decode("""["CANARY-BODY"]"""),
            decode("""{"choices":[{"message":"CANARY-BODY"}]}"""),
            decode(envelope, vendor = ChatVendor.OPENROUTER),
            decode(toolBody("""["CANARY-BODY"]""")),
            decode(toolBody("CANARY-BODY")),
            decode(chatBody(chatMessage("CANARY-BODY"), "stop")),
            decode(chatBody(chatMessage(null), "error", nativeFinishReason = "CANARY BODY")),
        )
        for (decoded in failures) {
            assertTrue(decoded.result is ModelResult.Failure)
            assertNoCanary(decoded)
        }
        val refused = decode(chatBody(chatMessage(null, refusal = "CANARY-BODY"), "stop"))
        assertNoCanary(refused)
        assertEquals(0, success(refused).message.parts.size)
    }
}
