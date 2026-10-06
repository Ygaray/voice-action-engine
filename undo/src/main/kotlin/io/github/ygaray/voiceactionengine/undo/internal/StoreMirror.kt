package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.JournalStore
import io.github.ygaray.voiceactionengine.undo.UndoGroup
import java.util.concurrent.atomic.AtomicInteger

/**
 * Passes the journal's changes to the optional store, outside the journal's lock. A fault of the store is counted and
 * never reaches the journal's caller, and a cancellation always does.
 */
internal class StoreMirror(private val store: JournalStore?) {
    private val faults = AtomicInteger()

    val enabled: Boolean
        get() = store != null

    /** How many store calls threw. */
    val faultCount: Int
        get() = faults.get()

    /** Tells the store which groups were dropped, then the current view of the one that changed. */
    suspend fun deliver(dropped: List<String>, changed: UndoGroup?) {
        val target = store ?: return
        for (key in dropped) guardedCall(onFault = { countFault() }) { target.delete(key) }
        if (changed != null) guardedCall(onFault = { countFault() }) { target.save(changed) }
    }

    private fun countFault() {
        faults.incrementAndGet()
    }
}
