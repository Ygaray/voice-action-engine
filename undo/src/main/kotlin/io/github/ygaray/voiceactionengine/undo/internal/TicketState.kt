package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.EntityKey
import io.github.ygaray.voiceactionengine.undo.UndoReason

/**
 * What a ticket learned about one entity. The fields change only while the ticket is open; once the ticket is frozen
 * into [TicketData] they are read-only by convention.
 *
 * [snapshot] and [beforeFingerprint] describe the entity before the write (null: it did not exist). [afterFingerprint]
 * is meaningful only when [settled]. A non-null [problem] means the journal cannot reason about this entity, and
 * [errorClass] names the fault, never its message.
 */
internal class Capture(
    val key: EntityKey,
    var snapshot: Any?,
    val beforeFingerprint: String?,
    var afterFingerprint: String?,
    var settled: Boolean,
    var problem: UndoReason?,
    var errorClass: String?,
) {
    override fun toString(): String = "Capture(settled=$settled, problem=$problem)"
}

/** One out-of-database effect the app declared. The payload is user data and is never printed. */
internal class Compensation(val kind: String, val payload: String) {
    override fun toString(): String = "Compensation(kind=$kind, payloadLength=${payload.length})"
}

/** The frozen content of a recorded ticket. */
internal class TicketData(
    val captures: List<Capture>,
    val touches: Set<EntityKey>,
    val compensations: List<Compensation>,
    val nothingWritten: Boolean,
    val sealed: Boolean,
) {
    override fun toString(): String = "TicketData(captures=${captures.size}, sealed=$sealed)"
}

/**
 * The open state of one ticket. All access is under one private lock, and no adapter is ever called while holding it.
 */
internal class TicketState {
    private val lock = Any()
    private val captures = LinkedHashMap<EntityKey, Capture>()
    private val touched = LinkedHashSet<EntityKey>()
    private val compensations = ArrayList<Compensation>()
    private var nothingWritten = false
    private var frozen = false

    fun captureOf(key: EntityKey): Capture? = synchronized(lock) {
        checkOpen()
        captures[key]
    }

    /** Adds [capture] unless its entity already has one: the first capture of an entity wins. */
    fun putIfAbsent(capture: Capture) {
        synchronized(lock) {
            checkOpen()
            if (capture.key !in captures) {
                captures[capture.key] = capture
                nothingWritten = false
            }
        }
    }

    fun settle(key: EntityKey, afterFingerprint: String?) {
        synchronized(lock) {
            checkOpen()
            captures[key]?.let {
                it.afterFingerprint = afterFingerprint
                it.settled = true
            }
        }
    }

    fun fail(key: EntityKey, errorClass: String) {
        synchronized(lock) {
            checkOpen()
            captures[key]?.let {
                it.problem = UndoReason.UNVERIFIABLE
                it.errorClass = errorClass
            }
        }
    }

    fun touch(key: EntityKey) {
        synchronized(lock) {
            checkOpen()
            touched.add(key)
        }
    }

    fun addCompensation(compensation: Compensation) {
        synchronized(lock) {
            checkOpen()
            compensations.add(compensation)
        }
    }

    /** The action wrote nothing: what was captured and declared so far is dropped. */
    fun clearForNothingWritten() {
        synchronized(lock) {
            checkOpen()
            captures.clear()
            touched.clear()
            compensations.clear()
            nothingWritten = true
        }
    }

    /** The data so far, frozen: further calls on the ticket fail. Null when it was already frozen. */
    fun freeze(): TicketData? = synchronized(lock) {
        if (frozen) {
            null
        } else {
            frozen = true
            val sealed = sealedLocked()
            TicketData(captures.values.toList(), touched.toSet(), compensations.toList(), nothingWritten, sealed)
        }
    }

    fun describe(): String = synchronized(lock) {
        "UndoTicket(entities=${captures.size}, touches=${touched.size}, compensations=${compensations.size}, " +
            "sealed=${sealedLocked()})"
    }

    // Sealed: nothing written, or every captured entity is settled or already known to be a problem for the journal.
    private fun sealedLocked(): Boolean = nothingWritten || captures.values.all { it.settled || it.problem != null }

    private fun checkOpen() = check(!frozen) { "the ticket was already recorded" }
}

private const val MISSING_ADAPTER = "NoAdapter"

/** The outcome of reading a fingerprint after a write: the value, or the class name of the fault. */
internal class FingerprintRead(val fingerprint: String?, val errorClass: String?)

/** Reads entities through the registered adapters on behalf of a ticket. Every adapter call is guarded. */
internal class TicketReader(private val adapters: Map<String, EntityAdapter>) {
    /** The entity's state before the write. */
    suspend fun before(key: EntityKey): Capture {
        val adapter = adapters[key.type] ?: return problem(key, UndoReason.NO_ADAPTER, null)
        return guardedCall(onFault = { problem(key, UndoReason.UNVERIFIABLE, it) }) {
            Capture(key, adapter.read(key.id), adapter.fingerprint(key.id), null, false, null, null)
        }
    }

    /** The entity's fingerprint after the write. */
    suspend fun after(key: EntityKey): FingerprintRead {
        val adapter = adapters[key.type] ?: return FingerprintRead(null, MISSING_ADAPTER)
        return guardedCall(onFault = { FingerprintRead(null, it) }) {
            FingerprintRead(adapter.fingerprint(key.id), null)
        }
    }

    /** An entity that did not exist before the write and exists now. */
    suspend fun created(key: EntityKey): Capture {
        val adapter = adapters[key.type] ?: return problem(key, UndoReason.NO_ADAPTER, null)
        return guardedCall(onFault = { problem(key, UndoReason.UNVERIFIABLE, it) }) {
            Capture(key, null, null, adapter.fingerprint(key.id), true, null, null)
        }
    }

    /** An entity settled without ever being captured: its before state is unknown. */
    fun unknown(key: EntityKey): Capture =
        problem(key, if (adapters.containsKey(key.type)) UndoReason.UNVERIFIABLE else UndoReason.NO_ADAPTER, null)

    private fun problem(key: EntityKey, reason: UndoReason, errorClass: String?): Capture =
        Capture(key, null, null, null, false, reason, errorClass)
}
