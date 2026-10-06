package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.Blocker
import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.EntityKey
import io.github.ygaray.voiceactionengine.undo.EntryRef
import io.github.ygaray.voiceactionengine.undo.NotRestored
import io.github.ygaray.voiceactionengine.undo.UndoReason
import io.github.ygaray.voiceactionengine.undo.UndoResult

/**
 * One recorded action. [sequence] is journal-wide and monotonic, so a held change applied by a later run still orders
 * after the actions of the run that proposed it. [undone] and [restoredKeys] change only under the journal's lock.
 */
internal class Entry(
    val ref: EntryRef,
    val sequence: Long,
    val failed: Boolean,
    val data: TicketData,
) {
    var undone: Boolean = false
    val restoredKeys: MutableSet<EntityKey> = HashSet()

    val keys: Set<EntityKey>
        get() = data.captures.map { it.key }.toSet()

    override fun toString(): String = "Entry(sequence=$sequence, failed=$failed, undone=$undone)"
}

/** An entry together with the entities of it already restored by an earlier pass. */
internal class PendingEntry(val entry: Entry, val restored: Set<EntityKey>)

/** One command's actions. Withheld when the journal missed or rejected any of them. */
internal class Group(val key: String, val parentGroupKey: String?) {
    var withheld: Boolean = false
    val entries: MutableList<Entry> = ArrayList()
}

/** What an undo pass may work on: the actions still to undo, or the reason the whole group cannot be undone. */
internal class Scope(val reason: UndoReason?, val pending: List<PendingEntry>)

/**
 * The journal's records. Every access is inside a short `synchronized` block that never calls an adapter, a
 * compensator or any app code.
 */
internal class JournalState {
    private val lock = Any()
    private val groups = LinkedHashMap<String, Group>()
    private var nextSequence = 0L

    fun groupCount(): Int = synchronized(lock) { groups.size }

    /**
     * Adds an action to its group. An anomaly (no usable ticket, a duplicate position, an unsealed ticket on an action
     * that did not fail) records nothing and withholds the whole group, so "undo all" is never offered for part of it.
     */
    fun append(groupKey: String, parentGroupKey: String?, ref: EntryRef, failed: Boolean, data: TicketData?) {
        synchronized(lock) {
            val group = groups.getOrPut(groupKey) { Group(groupKey, parentGroupKey) }
            val usable = data?.takeIf { (failed || it.sealed) && group.entries.none { e -> e.ref == ref } }
            if (usable == null) {
                group.withheld = true
            } else {
                group.entries.add(Entry(ref, nextSequence++, failed, usable))
            }
        }
    }

    fun scopeOf(groupKey: String): Scope = synchronized(lock) {
        val group = groups[groupKey]
        when {
            group == null -> Scope(UndoReason.UNKNOWN_GROUP, emptyList())
            group.withheld -> Scope(UndoReason.JOURNAL_WITHHELD, emptyList())
            else -> Scope(null, group.entries.filter { !it.undone }.map { PendingEntry(it, it.restoredKeys.toSet()) })
        }
    }

    /** Marks [done] restored on each of [entries], and returns those now fully undone. */
    fun markRestored(entries: List<Entry>, done: Set<EntityKey>): List<Entry> = synchronized(lock) {
        for (entry in entries) {
            entry.restoredKeys.addAll(entry.keys.filter { it in done })
            if (entry.restoredKeys.containsAll(entry.keys)) entry.undone = true
        }
        entries.filter { it.undone }
    }
}

/** The journal's undo of one group: verify the whole scope, then restore. */
internal class UndoPass(
    private val state: JournalState,
    adapters: Map<String, EntityAdapter>,
) {
    private val verifier = Verifier(adapters)

    suspend fun run(groupKey: String): UndoResult {
        val scope = state.scopeOf(groupKey)
        val reason = scope.reason
        return when {
            reason != null -> refusal(reason)
            scope.pending.isEmpty() -> UndoResult.AlreadyUndone()
            else -> restore(scope.pending)
        }
    }

    private suspend fun restore(pending: List<PendingEntry>): UndoResult {
        val plan = verifier.verify(pending)
        if (plan.blockers.isNotEmpty()) return UndoResult.Refused(plan.blockers)
        val done = HashSet(plan.satisfied)
        val notRestored = ArrayList<NotRestored>()
        for (step in plan.steps) {
            val failure =
                if (notRestored.isEmpty()) attempt(step) else step.failure(UndoReason.SKIPPED_AFTER_FAILURE, null)
            if (failure == null) done.add(step.key) else notRestored.add(failure)
        }
        val undone = state.markRestored(pending.map { it.entry }, done)
        val restored = undone.sortedByDescending { it.sequence }.map { it.ref }
        return if (notRestored.isEmpty()) UndoResult.Complete(restored) else UndoResult.Partial(restored, notRestored)
    }

    private suspend fun attempt(step: Step): NotRestored? =
        guardedCall(onFault = { step.failure(UndoReason.RESTORE_FAILED, it) }) {
            if (step.adapter.restoreIf(step.key.id, step.expectedFingerprint, step.snapshot)) {
                null
            } else {
                step.failure(UndoReason.CHANGED_SINCE, null)
            }
        }

    private fun refusal(reason: UndoReason): UndoResult =
        UndoResult.Refused(listOf(Blocker(null, null, reason)))
}
