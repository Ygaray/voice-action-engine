package io.github.ygaray.voiceactionengine.undo

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

private const val AWAIT_SECONDS = 10L

/** Runs a suspending block to completion with the standard library only, so the tests need no coroutines library. */
internal fun <T> runSuspending(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    val outcome = arrayOfNulls<Result<T>>(1)
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result ->
            outcome[0] = result
            latch.countDown()
        },
    )
    if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) throw AssertionError("the suspending block did not finish")
    return checkNotNull(outcome[0]).getOrThrow()
}

/** One stored record: a value, a label, the ids of its children, and the stamp of its last write. */
internal class TestRecord(val value: String, val label: String, val children: List<String>, val stamp: Long)

/** What a [TestAdapter] reads: the record and the child records that a delete would take with it. */
internal class TestSnapshot(val record: TestRecord, val children: Map<String, TestRecord>)

/**
 * An in-memory store. Every write takes a fresh stamp from one counter, so every write changes the fingerprint, and
 * [restoreIf] checks and writes atomically like an app's own transaction would.
 */
internal class TestEntityStore {
    private val lock = Any()
    private val records = LinkedHashMap<String, TestRecord>()
    private var stamp = 0L

    fun put(id: String, value: String, label: String = "", children: List<String> = emptyList()) {
        synchronized(lock) { records[id] = TestRecord(value, label, children, ++stamp) }
    }

    fun get(id: String): TestRecord? = synchronized(lock) { records[id] }

    /** Removes the record and, like a cascading delete, its children. */
    fun remove(id: String) {
        synchronized(lock) {
            records.remove(id)?.children?.forEach { records.remove(it) }
        }
    }

    fun fingerprint(id: String): String? = synchronized(lock) { fingerprintLocked(id) }

    fun snapshot(id: String): TestSnapshot? = synchronized(lock) {
        val record = records[id] ?: return@synchronized null
        TestSnapshot(record, record.children.mapNotNull { child -> records[child]?.let { child to it } }.toMap())
    }

    /** Equal to [snapshot] when the live fingerprint is [expected]; false and untouched otherwise. */
    fun restoreIf(id: String, expected: String?, snapshot: TestSnapshot?): Boolean = synchronized(lock) {
        if (fingerprintLocked(id) != expected) return@synchronized false
        remove(id)
        if (snapshot != null) {
            records[id] = TestRecord(snapshot.record.value, snapshot.record.label, snapshot.record.children, ++stamp)
            snapshot.children.forEach { (childId, child) ->
                records[childId] = TestRecord(child.value, child.label, child.children, ++stamp)
            }
        }
        true
    }

    /** Every id with its value and label, for byte-for-byte comparisons of the whole store. */
    fun dump(): List<String> = synchronized(lock) {
        records.entries.map { (id, r) -> "$id=${r.value}/${r.label}/${r.children}/${r.stamp}" }
    }

    private fun fingerprintLocked(id: String): String? = records[id]?.let { "s${it.stamp}" }
}

/** An adapter over a [TestEntityStore]. Every call is logged as `<op>:<id>` in [log], which adapters may share. */
internal class TestAdapter(
    private val store: TestEntityStore,
    override val entityType: String = "item",
    val log: MutableList<String> = Collections.synchronizedList(ArrayList()),
) : EntityAdapter {
    override suspend fun read(id: String): Any? {
        log.add("read:$id")
        return store.snapshot(id)
    }

    override suspend fun fingerprint(id: String): String? {
        log.add("fingerprint:$id")
        return store.fingerprint(id)
    }

    override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean {
        log.add("restoreIf:$id")
        return store.restoreIf(id, expectedFingerprint, snapshot as TestSnapshot?)
    }

    fun restoreCalls(): Int = log.count { it.startsWith("restoreIf:") }
}

/** One journal over one store, with a helper that records an edit the way an app's apply would. */
internal class Rig(
    val store: TestEntityStore = TestEntityStore(),
    val adapter: TestAdapter = TestAdapter(store),
) {
    val journal: UndoJournal = UndoJournal { adapter(adapter) }

    /** Captures [id], writes [value], settles, and records the action as position [position] of [group]. */
    fun edit(group: String, position: Int, id: String, value: String, failed: Boolean = false) = runSuspending {
        val ticket = journal.newTicket()
        ticket.capture("item", id)
        store.put(id, value)
        ticket.settle("item", id)
        journal.record(group, null, EntryRef(group, position, "edit"), failed, ticket)
    }

    fun undoAll(group: String): UndoResult = runSuspending { journal.undoAll(group) }
}
