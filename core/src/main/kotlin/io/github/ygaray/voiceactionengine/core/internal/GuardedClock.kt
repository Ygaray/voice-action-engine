package io.github.ygaray.voiceactionengine.core.internal

import java.util.concurrent.atomic.AtomicLong

private const val NO_READING = Long.MIN_VALUE

/**
 * Reads the app's clock for one run so that a clock which starts failing mid-run cannot break the run.
 *
 * The first reading is passed through as is, so a clock that is broken from the start still stops the run before it
 * begins. After one good reading, a clock that throws an exception (or a linkage error) answers with the last good
 * reading instead, which makes later latencies read as zero rather than escaping through a call that promised not to
 * throw. Other JVM errors (out of memory, assertion failures) still propagate.
 *
 * The callers read it outside any lock.
 */
internal class GuardedClock(private val source: () -> Long) {
    private val lastGood = AtomicLong(NO_READING)

    fun read(): Long {
        if (lastGood.get() == NO_READING) return remember(source())
        return guardedPlain(onFault = { lastGood.get() }) { remember(source()) }
    }

    private fun remember(reading: Long): Long {
        lastGood.set(reading)
        return reading
    }
}
