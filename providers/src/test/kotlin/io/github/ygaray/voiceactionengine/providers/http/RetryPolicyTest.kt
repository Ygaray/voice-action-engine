package io.github.ygaray.voiceactionengine.providers.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun retryAfterIsReadAsWholeSecondsAroundOptionalSpaces() {
        assertEquals(2L, retryAfterSeconds("2"))
        assertEquals(0L, retryAfterSeconds("0"))
        assertEquals(3L, retryAfterSeconds(" 3 "))
    }

    @Test
    fun anythingThatIsNotAPlainNonNegativeIntegerIsIgnored() {
        for (header in listOf("-1", "1.5", "abc", "", "  ", "1 2", "+4", "Wed, 21 Oct 2026 07:28:00 GMT", null)) {
            assertNull("header $header", retryAfterSeconds(header))
        }
    }

    @Test
    fun aNumberTooLargeForALongIsFarTooLongRatherThanIgnored() {
        assertEquals(Long.MAX_VALUE, retryAfterSeconds("99999999999999999999999"))
        assertNull(transientWaitMillis(retryAfterSeconds("99999999999999999999999"), 5_000, 500))
    }

    @Test
    fun theWaitIsTheServersAskWithinTheCapElseTheBackoffElseNoRetry() {
        assertEquals(500L, transientWaitMillis(null, 5_000, 500))
        assertEquals(2_000L, transientWaitMillis(2, 5_000, 500))
        assertEquals(0L, transientWaitMillis(0, 5_000, 500))
        assertEquals(5_000L, transientWaitMillis(5, 5_000, 500))
        assertNull(transientWaitMillis(6, 5_000, 500))
    }

    @Test
    fun onlyTheSevenTransientStatusesAreTransient() {
        val transient = setOf(408, 429, 500, 502, 503, 504, 529)
        for (code in 100..599) {
            assertEquals("status $code", code in transient, isTransientStatus(code))
        }
        assertTrue(isTransientStatus(529))
        assertFalse(isTransientStatus(200))
    }
}
