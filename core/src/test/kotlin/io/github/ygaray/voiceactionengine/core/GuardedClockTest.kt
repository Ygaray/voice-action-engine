package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.internal.GuardedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class GuardedClockTest {
    @Test
    fun theFirstReadingPassesThroughAndALaterFailureAnswersWithTheLastGoodReading() {
        var next: () -> Long = { READING }
        val clock = GuardedClock { next() }

        assertEquals(READING, clock.read())
        next = { error("clock broke") }
        assertEquals(READING, clock.read())
        next = { LATER }
        assertEquals(LATER, clock.read())
    }

    @Test
    fun aClockThatNeverReadsSuccessfullyStillThrows() {
        val failure = IllegalStateException("clock broke")
        val clock = GuardedClock { throw failure }
        try {
            clock.read()
            fail("expected the first failure to propagate")
        } catch (expected: IllegalStateException) {
            assertSame(failure, expected)
        }
    }

    @Test
    fun aLinkageErrorAfterAGoodReadingIsAbsorbed() {
        var next: () -> Long = { READING }
        val clock = GuardedClock { next() }
        clock.read()
        next = { throw NoSuchMethodError("x") }
        assertEquals(READING, clock.read())
    }

    @Test
    fun anOrdinaryJvmErrorStillPropagates() {
        var next: () -> Long = { READING }
        val clock = GuardedClock { next() }
        clock.read()
        next = { throw AssertionError("boom") }
        try {
            clock.read()
            fail("expected AssertionError")
        } catch (expected: AssertionError) {
            assertEquals("boom", expected.message)
        }
    }

    private companion object {
        const val READING = 5L
        const val LATER = 9L
    }
}
