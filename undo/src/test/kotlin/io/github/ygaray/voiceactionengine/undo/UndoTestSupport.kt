package io.github.ygaray.voiceactionengine.undo

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    /** Becomes [snapshot], version included, when the live fingerprint is [expected]; false and untouched otherwise. */
    fun restoreIf(id: String, expected: String?, snapshot: TestSnapshot?): Boolean = synchronized(lock) {
        if (fingerprintLocked(id) != expected) return@synchronized false
        remove(id)
        if (snapshot != null) {
            // The snapshot carries the version too, so a restored entity is the very state that was read.
            records[id] = snapshot.record
            records.putAll(snapshot.children)
        }
        true
    }

    /** Every id with its value and label, for byte-for-byte comparisons of the whole store. */
    fun dump(): List<String> = synchronized(lock) {
        records.entries.map { (id, r) -> "$id=${r.value}/${r.label}/${r.children}/${r.stamp}" }
    }

    private fun fingerprintLocked(id: String): String? = records[id]?.let { "s${it.stamp}" }
}

/** A fault message that must never reach a result, a string or a field. */
internal const val CANARY = "canary secret text"

/** An adapter over a [TestEntityStore]. Every call is logged as `<op>:<id>` in [log], which adapters may share. */
internal class TestAdapter(
    private val store: TestEntityStore,
    override val entityType: String = "item",
    val log: MutableList<String> = Collections.synchronizedList(ArrayList()),
) : EntityAdapter {
    /** Ids whose restore throws an [IllegalStateException] carrying [CANARY]. Mutable, so a retry can clear it. */
    val throwOnRestore: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ids whose restore loses a race: another writer wins first, and the call reports false. */
    val raceOnRestore: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ids whose restore throws a cancellation before writing. */
    val cancelOnRestore: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ids whose restore writes and then throws a cancellation, like a cancel that lands after the commit. */
    val cancelAfterWrite: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ids whose restore suspends first; the stored continuation is in [gates] until the test resumes it. */
    val suspendOnRestore: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val gates: MutableMap<String, Continuation<Unit>> = ConcurrentHashMap()

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
        if (id in suspendOnRestore) suspendCoroutine<Unit> { gates[id] = it }
        check(id !in throwOnRestore) { CANARY }
        if (id in cancelOnRestore) throw CancellationException(CANARY)
        if (id in raceOnRestore) {
            store.put(id, "raced")
            return false
        }
        val done = store.restoreIf(id, expectedFingerprint, snapshot as TestSnapshot?)
        if (id in cancelAfterWrite) throw CancellationException(CANARY)
        return done
    }

    fun restoreCalls(): Int = log.count { it.startsWith("restoreIf:") }

    fun restoreCalls(id: String): Int = log.count { it == "restoreIf:$id" }
}

/**
 * A compensator that applies each payload at most once (a set), so calling it twice leaves the same state. It logs
 * `compensate:<payload>` into [log], which it shares with the adapter so the two orders can be compared.
 */
internal class RecordingCompensator(private val log: MutableList<String>) : Compensator {
    val applied: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())
    val throwOn: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val cancelOn: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun compensate(payload: String) {
        log.add("compensate:$payload")
        check(payload !in throwOn) { CANARY }
        if (payload in cancelOn) throw CancellationException(CANARY)
        applied.add(payload)
    }

    fun calls(payload: String): Int = log.count { it == "compensate:$payload" }
}

/** The writes of an undo (restores and compensations) out of a shared log, in the order they happened. */
internal fun List<String>.writes(): List<String> =
    filter { it.startsWith("restoreIf:") || it.startsWith("compensate:") }

/** An undo started without waiting for it: [outcome] stays null while it is suspended. */
internal class Launched<T> {
    @Volatile
    var outcome: Result<T>? = null

    val finished: Boolean get() = outcome != null

    fun value(): T = checkNotNull(outcome) { "the undo is still suspended" }.getOrThrow()
}

/** Starts [block] on the calling thread and returns at its first suspension that is not resumed. */
internal fun <T> launch(block: suspend () -> T): Launched<T> {
    val launched = Launched<T>()
    block.startCoroutine(Continuation(EmptyCoroutineContext) { launched.outcome = it })
    return launched
}

/** One journal over one store, with a helper that records an edit the way an app's apply would. */
internal class Rig(
    val store: TestEntityStore = TestEntityStore(),
    val adapter: TestAdapter = TestAdapter(store),
    configure: UndoJournal.Builder.() -> Unit = {},
) {
    val compensator: RecordingCompensator = RecordingCompensator(adapter.log)
    val journal: UndoJournal = UndoJournal {
        adapter(adapter)
        compensator("alarm", compensator)
        configure()
    }

    /** Captures [id], writes [value], settles, and records the action as position [position] of [group]. */
    fun edit(group: String, position: Int, id: String, value: String, failed: Boolean = false) = runSuspending {
        val ticket = journal.newTicket()
        ticket.capture("item", id)
        store.put(id, value)
        ticket.settle("item", id)
        journal.record(group, null, EntryRef(group, position, "edit"), failed, ticket)
    }

    /**
     * One action that edits every id of [ids] to [value] (captured in that order) and declares each of
     * [compensations] as a (kind, payload) pair.
     */
    fun editAll(
        group: String,
        position: Int,
        ids: List<String>,
        value: String,
        compensations: List<Pair<String, String>> = emptyList(),
    ) = runSuspending {
        val ticket = journal.newTicket()
        ids.forEach { ticket.capture("item", it) }
        ids.forEach { store.put(it, value) }
        ids.forEach { ticket.settle("item", it) }
        compensations.forEach { (kind, payload) -> ticket.compensate(kind, payload) }
        journal.record(group, null, EntryRef(group, position, "edit"), false, ticket)
    }

    fun undoAll(group: String): UndoResult = runSuspending { journal.undoAll(group) }

    fun undoEntry(group: String, entry: EntryRef): UndoResult = runSuspending { journal.undoEntry(group, entry) }

    /** Lets a restore of [id] that was suspended with [TestAdapter.suspendOnRestore] carry on. */
    fun resume(id: String) {
        adapter.suspendOnRestore.remove(id)
        checkNotNull(adapter.gates.remove(id)) { "the restore of $id is not suspended" }.resume(Unit)
    }
}

/** The group view of [groupKey], or null. */
internal fun Rig.group(groupKey: String): UndoGroup? = runSuspending { journal.group(groupKey) }

/** The group view of [groupKey], which must exist. */
internal fun Rig.groupOf(groupKey: String): UndoGroup = checkNotNull(group(groupKey)) { "no such group" }
