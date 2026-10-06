package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.verdict.percentileNearestRank
import io.github.ygaray.voiceactionengine.spike.verdict.round2
import io.github.ygaray.voiceactionengine.spike.verdict.round3
import io.github.ygaray.voiceactionengine.spike.verdict.wilsonLowerBound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScoringTest {
    @Test
    fun englishBoundaryIs48Of50() {
        assertTrue(wilsonLowerBound(48, 50) >= 0.85)
        assertFalse(wilsonLowerBound(47, 50) >= 0.85)
    }

    @Test
    fun spanishBoundaryIs46Of50() {
        assertTrue(wilsonLowerBound(46, 50) >= 0.80)
        assertFalse(wilsonLowerBound(45, 50) >= 0.80)
    }

    @Test
    fun perfectScoreAtHundredRoundsTo0963() {
        assertEquals("0.963", round3(wilsonLowerBound(100, 100)))
    }

    @Test
    fun zeroOfThirtyNegativesLeavesRoomAbove() {
        // The Wilson lower bound of 0 successes is 0; the point is that the function accepts the edge.
        assertEquals(0.0, wilsonLowerBound(0, 30), 1e-12)
    }

    @Test
    fun wilsonRejectsAnEmptySample() {
        try {
            wilsonLowerBound(0, 0)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun percentilesOfOneToHundredAreNearestRank() {
        val values = (1L..100L).toList()
        assertEquals(50L, percentileNearestRank(values, 0.50))
        assertEquals(95L, percentileNearestRank(values, 0.95))
    }

    @Test
    fun percentileIgnoresInputOrder() {
        assertEquals(3L, percentileNearestRank(listOf(5L, 1L, 3L, 2L, 4L), 0.50))
    }

    @Test
    fun percentileOfASingleValueIsThatValue() {
        assertEquals(42L, percentileNearestRank(listOf(42L), 0.50))
        assertEquals(42L, percentileNearestRank(listOf(42L), 0.95))
    }

    @Test
    fun percentileOfAnEmptyListThrows() {
        try {
            percentileNearestRank(emptyList(), 0.5)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun roundingIsFixedAndHalfUp() {
        assertEquals("0.865", round3(0.8649999))
        assertEquals("0.500", round3(0.5))
        assertEquals("1.10", round2(1.1))
        assertEquals("1.46", round2(1.456))
    }
}
