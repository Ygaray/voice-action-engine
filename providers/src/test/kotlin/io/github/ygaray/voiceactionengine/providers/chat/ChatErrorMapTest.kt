package io.github.ygaray.voiceactionengine.providers.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatErrorMapTest {

    private fun root(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun openRouterRateLimitInsideA200AnswerIsRetryableRateLimit() {
        val body = """{"id":"gen-GOLDEN","error":{"code":429,"message":"Rate limit exceeded: CANARY-ECHO",""" +
            """"metadata":{"raw":"CANARY-RAW","provider_name":"Example"}}}"""
        val info = chatEnvelopeError(root(body), null, true)!!
        assertEquals(429, info.status)
        assertEquals("rate_limited", info.reason().code)
        assertTrue(info.transient)
        assertEquals("gen-GOLDEN", info.requestId)
        assertNoCanary(info.toString())
        assertNoCanary(info.details().toString())
        assertNoCanary(info.reason().toString())
    }

    @Test
    fun openAiUnauthorizedIsAuthAndFinal() {
        val body = """{"error":{"message":"Incorrect API key provided.","type":"invalid_request_error",""" +
            """"param":null,"code":"invalid_api_key"}}"""
        val info = parseChatError(401, "req_test_1", body, false)
        assertEquals("auth", info.reason().code)
        assertEquals("invalid_api_key", info.errorType)
        assertEquals("req_test_1", info.requestId)
        assertFalse(info.transient)
    }

    @Test
    fun aRootWithoutAnErrorKeyIsNotAnEnvelopeError() {
        assertNull(chatEnvelopeError(root("""{"id":"x","choices":[]}"""), null, true))
    }

    @Test
    fun theKeptFactsAreExactlyTheFiveNamedFields() {
        val fields = ChatErrorInfo::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(setOf("status", "errorType", "requestId", "refined", "transient"), fields)
    }

    private fun errorBody(status: Int, extra: String = ""): ChatErrorInfo =
        parseChatError(status, null, """{"error":{"message":"m"$extra}}""", false)

    private fun withMessage(status: Int, message: String, extra: String = ""): ChatErrorInfo =
        parseChatError(status, null, """{"error":{"message":"$message"$extra}}""", false)

    @Test
    fun statusTableMapsEveryDocumentedStatus() {
        val expected = mapOf(
            401 to "auth", 403 to "auth", 402 to "billing", 404 to "model_not_found", 408 to "timeout",
            504 to "timeout", 429 to "rate_limited", 503 to "overloaded", 529 to "overloaded",
            500 to "http_error", 502 to "http_error", 524 to "http_error", 418 to "http_error",
        )
        expected.forEach { (status, code) -> assertEquals("status $status", code, errorBody(status).reason().code) }
    }

    @Test
    fun transientIsTrueExactlyForTheRetryableStatuses() {
        val retryable = setOf(408, 429, 500, 502, 503, 504, 524, 529)
        retryable.forEach { assertTrue("status $it", errorBody(it).transient) }
        listOf(400, 401, 402, 403, 404, 418).forEach { assertFalse("status $it", errorBody(it).transient) }
    }

    @Test
    fun exhaustedQuotaIsFinalBillingAtAnyStatus() {
        val byCode = errorBody(429, ""","code":"insufficient_quota"""")
        val byType = errorBody(429, ""","type":"insufficient_quota"""")
        val envelope = chatEnvelopeError(
            root("""{"error":{"code":"insufficient_quota","message":"m"}}"""),
            null,
            false,
        )!!
        listOf(byCode, byType, envelope).forEach {
            assertEquals("billing", it.reason().code)
            assertFalse(it.transient)
        }
    }

    @Test
    fun contextLengthExceededIsContextWindowExceeded() {
        assertEquals(
            "context_window_exceeded",
            errorBody(400, ""","code":"context_length_exceeded"""").reason().code,
        )
    }

    @Test
    fun functionToolsOnTheChatEndpointAreModelUnsupported() {
        val message = "Function tools with reasoning_effort are not supported for gpt-6-astra in " +
            "/v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'."
        assertEquals("model_unsupported", withMessage(400, message).reason().code)
        assertEquals("model_unsupported", withMessage(404, "use /v1/responses instead").reason().code)
        assertEquals("http_error", withMessage(500, "use /v1/responses instead").reason().code)
        val bare = "This model is only supported in v1/responses and not in v1/chat/completions."
        assertEquals("model_unsupported", withMessage(400, bare).reason().code)
        assertEquals("http_error", withMessage(500, bare).reason().code)
    }

    private fun effortBody(param: String?, code: String?, message: String = "m"): String {
        val paramJson = if (param == null) "null" else "\"$param\""
        val codeJson = if (code == null) "null" else "\"$code\""
        return """{"error":{"message":"$message","type":"invalid_request_error","param":$paramJson,"code":$codeJson}}"""
    }

    @Test
    fun anUnsupportedReasoningEffortValueIsModelUnsupportedOnlyForAllThreeFacts() {
        val w04 = effortBody("reasoning_effort", "unsupported_value", "Unsupported value: 'reasoning_effort'")
        assertEquals("model_unsupported", parseChatError(400, null, w04, false).reason().code)
        assertEquals("http_error", parseChatError(500, null, w04, false).reason().code)
        val otherParam = effortBody("temperature", "unsupported_value")
        assertEquals("http_error", parseChatError(400, null, otherParam, false).reason().code)
        val noCode = effortBody("reasoning_effort", null, "reasoning_effort is not accepted here")
        assertEquals("http_error", parseChatError(400, null, noCode, false).reason().code)
        val probeA = "Function tools with reasoning_effort are not supported for gpt-6-astra in " +
            "/v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'."
        val probeABody = effortBody("reasoning_effort", null, probeA)
        assertEquals("model_unsupported", parseChatError(400, null, probeABody, false).reason().code)
    }

    @Test
    fun noServerTextSurfacesFromAnUnsupportedReasoningEffortAnswer() {
        val body = effortBody("reasoning_effort", "unsupported_value", "CANARY-MSG")
        val info = parseChatError(400, null, body, false)
        assertEquals("model_unsupported", info.reason().code)
        assertNoCanary(info.toString())
        assertNoCanary(info.details().toString())
        assertNoCanary(info.reason().toString())
    }

    @Test
    fun noEndpointsForAParameterIsModelUnsupportedButOtherNotFoundIsNot() {
        val live = "No endpoints found that can handle the requested parameters."
        assertEquals("model_unsupported", withMessage(404, live).reason().code)
        val older = "No endpoints found that support the provided 'tool_choice' value."
        assertEquals("model_unsupported", withMessage(404, older).reason().code)
        assertEquals("model_not_found", withMessage(404, "No endpoints found for foo/bar.").reason().code)
        assertEquals("model_not_found", withMessage(404, "No such model").reason().code)
    }

    @Test
    fun forbiddenWithModerationReasonsIsRefusalOtherwiseAuth() {
        val reasons = ""","metadata":{"reasons":["violence"],"flagged_input":"CANARY-INPUT"}"""
        val refusal = errorBody(403, reasons)
        assertEquals("refusal", refusal.reason().code)
        assertNoCanary(refusal.toString())
        assertEquals("auth", errorBody(403).reason().code)
    }

    @Test
    fun envelopeStatusComesFromTheNumericCodeElseTwoHundred() {
        val bad = chatEnvelopeError(root("""{"error":{"code":502,"message":"m"}}"""), null, false)!!
        assertEquals("http_error", bad.reason().code)
        assertTrue(bad.transient)
        val auth = chatEnvelopeError(root("""{"error":{"code":401,"message":"m"}}"""), null, false)!!
        assertEquals("auth", auth.reason().code)
        assertFalse(auth.transient)
        val named = chatEnvelopeError(root("""{"error":{"code":"server_error","message":"m"}}"""), null, false)!!
        assertEquals(200, named.status)
        assertEquals("http_error", named.reason().code)
        assertFalse(named.transient)
        assertEquals("server_error", named.errorType)
        val empty = chatEnvelopeError(root("""{"choices":[],"error":{"code":429,"message":"m"}}"""), null, false)!!
        assertEquals(429, empty.status)
    }

    @Test
    fun theHeaderRequestIdWinsAndTheBodyIdIsUsedOnlyWhenAllowed() {
        val body = """{"id":"gen-1","error":{"code":429}}"""
        assertEquals("hdr-1", chatEnvelopeError(root(body), "hdr-1", true)!!.requestId)
        assertEquals("gen-1", chatEnvelopeError(root(body), null, true)!!.requestId)
        assertNull(chatEnvelopeError(root(body), null, false)!!.requestId)
        assertEquals("gen-1", chatEnvelopeError(root(body), "bad id", true)!!.requestId)
    }

    @Test
    fun hostileTypesAndRequestIdsAreDroppedButTheStatusSurvives() {
        listOf("has space", "quo\\\"te", "a".repeat(65)).forEach { hostile ->
            val info = parseChatError(500, null, """{"error":{"code":"$hostile"}}""", false)
            assertNull(info.errorType)
            assertEquals(500, info.status)
            info.details()
        }
        val long = parseChatError(500, "r".repeat(129), null, false)
        assertNull(long.requestId)
        assertEquals(500, long.details().httpStatus)
    }

    @Test
    fun oddBodiesGiveTheStatusTableReasonWithNullType() {
        listOf(null, "", "not json", "[1,2]", """{"error":"just a string"}""").forEach { body ->
            val info = parseChatError(429, null, body, false)
            assertEquals("rate_limited", info.reason().code)
            assertNull(info.errorType)
            assertTrue(info.transient)
        }
    }

    @Test
    fun noServerTextSurfacesInAnyRenderedFact() {
        val body = """{"error":{"code":"insufficient_quota","message":"CANARY-MSG /v1/responses",""" +
            """"metadata":{"raw":"CANARY-RAW","provider_name":"CANARY-PROVIDER","reasons":["x"]}}}"""
        listOf(400, 403, 404, 429).forEach { status ->
            val info = parseChatError(status, null, body, false)
            assertNoCanary(info.toString())
            assertNoCanary(info.details().toString())
            assertNoCanary(info.reason().toString())
        }
    }

    private fun assertNoCanary(text: String) {
        assertFalse(text, text.contains("CANARY"))
    }
}
