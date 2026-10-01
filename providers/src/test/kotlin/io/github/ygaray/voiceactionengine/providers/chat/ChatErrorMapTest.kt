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

    private fun assertNoCanary(text: String) {
        assertFalse(text, text.contains("CANARY"))
    }
}
