package io.github.ygaray.voiceactionengine.undo

import io.github.ygaray.voiceactionengine.undo.internal.Compensation
import io.github.ygaray.voiceactionengine.undo.internal.TicketData
import io.github.ygaray.voiceactionengine.undo.internal.TicketReader
import io.github.ygaray.voiceactionengine.undo.internal.TicketState
import io.github.ygaray.voiceactionengine.undo.internal.requireToken

/**
 * What the app tells the journal while one action is applied: the state of each entity before the write, its
 * fingerprint after, and any effect outside the database.
 *
 * Create one with [UndoJournal.newTicket] per action, and use it inside the app's own apply, in this order:
 * 1. [capture] each entity before writing it;
 * 2. write;
 * 3. [settle] each captured entity, [created] each entity whose id exists only after the write, [touches] any
 *    entity that changed without being captured (for example a cascade child), and [compensate] each effect
 *    outside the database.
 *
 * Call [nothingWritten] when the apply failed before writing anything. Then hand the ticket to
 * [UndoJournal.record]. A held change is captured when it is finally applied, never when it was proposed. Every
 * method throws [IllegalArgumentException] for an invalid string and [IllegalStateException] after the ticket was
 * recorded. A fault in an adapter never reaches the caller: it is kept, and the undo refuses with a reason.
 */
public class UndoTicket internal constructor(
    internal val owner: Any,
    adapters: Map<String, EntityAdapter>,
) {
    private val state = TicketState()
    private val reader = TicketReader(adapters)

    /**
     * Reads the entity's state before the write. The first capture of an entity wins; later ones are ignored.
     */
    public suspend fun capture(type: String, id: String) {
        val key = keyOf(type, id)
        if (state.captureOf(key) != null) return
        state.putIfAbsent(reader.before(key))
    }

    /** Records the entity's fingerprint after the write, or that it no longer exists. */
    public suspend fun settle(type: String, id: String) {
        val key = keyOf(type, id)
        val captured = state.captureOf(key)
        if (captured == null) {
            // Settling what was never captured: the before state is unknown, so the undo will refuse for this entity.
            state.putIfAbsent(reader.unknown(key))
        } else if (captured.problem == null) {
            val read = reader.after(key)
            if (read.errorClass == null) state.settle(key, read.fingerprint) else state.fail(key, read.errorClass)
        }
    }

    /** Declares an entity whose id exists only after the write: it did not exist before, and exists now. */
    public suspend fun created(type: String, id: String) {
        val key = keyOf(type, id)
        if (state.captureOf(key) != null) {
            settle(type, id)
        } else {
            state.putIfAbsent(reader.created(key))
        }
    }

    /** Declares an entity the action changed without capturing it, such as a cascade child. */
    public fun touches(type: String, id: String) {
        state.touch(keyOf(type, id))
    }

    /** Declares an effect outside the database; [payload] is handed back to the compensator registered for [kind]. */
    public fun compensate(kind: String, payload: String) {
        requireToken("kind", kind)
        state.addCompensation(Compensation(kind, payload))
    }

    /** Declares that the apply wrote nothing: what was captured and declared so far is dropped. */
    public fun nothingWritten() {
        state.clearForNothingWritten()
    }

    override fun toString(): String = state.describe()

    internal fun freeze(): TicketData? = state.freeze()

    private fun keyOf(type: String, id: String): EntityKey {
        requireToken("type", type)
        requireToken("id", id)
        return EntityKey(type, id)
    }
}
