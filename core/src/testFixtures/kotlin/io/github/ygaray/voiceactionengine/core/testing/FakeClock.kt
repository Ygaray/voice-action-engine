package io.github.ygaray.voiceactionengine.core.testing

import java.util.concurrent.atomic.AtomicLong

/**
 * A clock a test moves by hand. It is a `() -> Long`, so it can be set directly as a pipeline's `clock`; reading it
 * returns [now] and never changes it.
 *
 * @param start the first reading, in milliseconds.
 */
public class FakeClock(start: Long = 0L) : () -> Long {
    private val time = AtomicLong(start)

    /** The current reading, in milliseconds. */
    public val now: Long
        get() = time.get()

    /** Moves the clock forward by [millis]. */
    public fun advanceBy(millis: Long) {
        require(millis >= 0) { "a clock cannot go backwards" }
        time.addAndGet(millis)
    }

    override fun invoke(): Long = time.get()

    override fun toString(): String = "FakeClock(now=$now)"
}
