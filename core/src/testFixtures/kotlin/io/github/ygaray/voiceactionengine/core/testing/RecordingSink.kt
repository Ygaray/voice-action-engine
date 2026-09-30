package io.github.ygaray.voiceactionengine.core.testing

import java.util.concurrent.CopyOnWriteArrayList

/** Thread-safe, arrival-ordered recorder of events, for asserting exactly what a pipeline did and in which order. */
public class RecordingSink<T> {
    private val recorded = CopyOnWriteArrayList<T>()

    /** Immutable snapshot of the events recorded so far; later recordings do not change a snapshot already taken. */
    public val events: List<T>
        get() = recorded.toList()

    /** Appends [event] after everything already recorded. */
    public fun record(event: T) {
        recorded.add(event)
    }

    /** Forgets every recorded event. */
    public fun clear() {
        recorded.clear()
    }
}
