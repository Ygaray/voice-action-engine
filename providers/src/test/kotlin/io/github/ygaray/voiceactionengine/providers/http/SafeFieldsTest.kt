package io.github.ygaray.voiceactionengine.providers.http

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFieldsTest {

    private val rejected = listOf(
        null,
        "",
        "a".repeat(129),
        "has space",
        "quo\"te",
        "new\nline",
        "naïve",
    )

    @Test
    fun safeTokenAcceptsShortProviderIdentifiers() {
        assertEquals("invalid_request_error", safeToken("invalid_request_error"))
        assertEquals("rate_limit_error", safeToken("rate_limit_error"))
        assertEquals("a.b:c-d_1", safeToken("a.b:c-d_1"))
        val sixtyFour = "a".repeat(64)
        assertEquals(sixtyFour, safeToken(sixtyFour))
    }

    @Test
    fun safeTokenRejectsEverythingElse() {
        assertNull(safeToken("a".repeat(65)))
        rejected.forEach { assertNull("token input: $it", safeToken(it)) }
    }

    @Test
    fun safeRequestIdAcceptsUpTo128Characters() {
        assertEquals("req_011CSHoEeqs5C35K2UUqR7Fy", safeRequestId("req_011CSHoEeqs5C35K2UUqR7Fy"))
        val max = "r".repeat(128)
        assertEquals(max, safeRequestId(max))
    }

    @Test
    fun safeRequestIdRejectsEverythingElse() {
        assertNull(safeRequestId("r".repeat(129)))
        rejected.forEach { assertNull("request id input: $it", safeRequestId(it)) }
    }

    @Test
    fun headerSafeAcceptsPrintableAsciiSpaceAndTab() {
        assertTrue(isHeaderSafe("sk-test-key"))
        assertTrue(isHeaderSafe("inner space"))
        assertTrue(isHeaderSafe("with\ttab"))
        assertTrue(isHeaderSafe(""))
    }

    @Test
    fun headerSafeRejectsControlAndNonAsciiCharacters() {
        listOf("key\n", "in\rner", "nul\u0000", "del\u007f", "naïve").forEach {
            assertFalse("header input of length ${it.length}", isHeaderSafe(it))
        }
    }

    @Test
    fun failureDetailsNeverThrowsForAnyValidatedServerString() {
        val inputs = rejected + listOf(
            "invalid_request_error",
            "rate_limit_error",
            "a".repeat(64),
            "a".repeat(65),
            "req_011CSHoEeqs5C35K2UUqR7Fy",
            "r".repeat(128),
        )
        inputs.forEach { input ->
            val details = FailureDetails(400, safeToken(input), safeRequestId(input))
            assertEquals(400, details.httpStatus)
        }
    }
}
