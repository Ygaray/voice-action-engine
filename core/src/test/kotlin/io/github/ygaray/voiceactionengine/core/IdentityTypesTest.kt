package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTypesTest {

    private class AppContext(@Suppress("unused") val secret: String)

    @Test
    fun blankProviderIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProviderId("") }
        assertThrows(IllegalArgumentException::class.java) { ProviderId("   ") }
    }

    @Test
    fun blankStrategyIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { StrategyId("") }
        assertThrows(IllegalArgumentException::class.java) { StrategyId(" ") }
    }

    @Test
    fun strategyIdToStringIsItsValue() {
        assertEquals("local_grammar", StrategyId("local_grammar").toString())
        assertEquals("anthropic", ProviderId.ANTHROPIC.toString())
    }

    @Test
    fun commandInputDefaultsAreAllNull() {
        val input = CommandInput("hello")
        assertEquals("hello", input.transcript)
        assertNull(input.language)
        assertNull(input.context)
        assertNull(input.parentRunId)
    }

    @Test
    fun commandInputKeepsEveryField() {
        val context = AppContext("c")
        val input = CommandInput("hola", "es", context, "run-1")
        assertEquals("es", input.language)
        assertEquals(context, input.context)
        assertEquals("run-1", input.parentRunId)
    }

    @Test
    fun commandInputToStringShowsLengthAndContextClassOnly() {
        val canaryTranscript = "CANARY-TRANSCRIPT-xyzzy"
        val canaryInContext = "CANARY-CONTEXT-plugh"
        val text = CommandInput(canaryTranscript, "en", AppContext(canaryInContext), "run-9").toString()
        assertTrue(text, text.contains("transcriptLength=${canaryTranscript.length}"))
        assertTrue(text, text.contains("context=AppContext"))
        assertFalse(text, text.contains(canaryTranscript))
        assertFalse(text, text.contains(canaryInContext))
    }

    @Test
    fun commandInputToStringHandlesNullContext() {
        assertEquals(
            "CommandInput(transcriptLength=2, language=null, context=null, parentRunId=null)",
            CommandInput("hi").toString(),
        )
    }

    @Test
    fun rateLimitedFailureHasItsCodeAndEquality() {
        assertEquals("rate_limited", FailureReason.RateLimited().code)
        assertEquals(FailureReason.RateLimited(), FailureReason.RateLimited())
        assertEquals(FailureReason.RateLimited().hashCode(), FailureReason.RateLimited().hashCode())
    }

    @Test
    fun failureDetailsRoundTripsItsThreeFields() {
        val details = FailureDetails(HTTP_TOO_MANY, "rate_limit_error", "req_123")
        assertEquals(HTTP_TOO_MANY, details.httpStatus)
        assertEquals("rate_limit_error", details.providerErrorType)
        assertEquals("req_123", details.requestId)
        assertEquals(details, FailureDetails(HTTP_TOO_MANY, "rate_limit_error", "req_123"))
        assertTrue(details.toString().contains("req_123"))
    }

    @Test
    fun blankCredentialKeyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Credential(ProviderId.ANTHROPIC, "") }
        assertThrows(IllegalArgumentException::class.java) { Credential(ProviderId.ANTHROPIC, "  ") }
    }

    @Test
    fun credentialToStringNamesOnlyTheProvider() {
        val key = "sk-CANARY-KEY-987654321"
        val credential = Credential(ProviderId.ANTHROPIC, key)
        assertEquals("Credential(provider=anthropic)", credential.toString())
        assertEquals(key, credential.apiKey)
        assertFalse(credential.toString().contains("${key.length}"))
    }

    private companion object {
        const val HTTP_TOO_MANY = 429
    }
}
