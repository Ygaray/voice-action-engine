package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.Blocker
import io.github.ygaray.voiceactionengine.undo.Compensator
import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.EntityKey
import io.github.ygaray.voiceactionengine.undo.EntryRef
import io.github.ygaray.voiceactionengine.undo.NotRestored
import io.github.ygaray.voiceactionengine.undo.UndoReason
import io.github.ygaray.voiceactionengine.undo.UndoGroup
import io.github.ygaray.voiceactionengine.undo.UndoResult

/**
 * One recorded action. [sequence] is journal-wide and monotonic, so a held change applied by a later run still orders
 * after the actions of the run that proposed it. [undone], [restoredKeys] and [compensated] (the positions of the
 * declared effects already reversed) change only under the journal's lock.
 */
internal class Entry(
    val group: Group,
    val ref: EntryRef,
    val sequence: Long,
    val failed: Boolean,
    val data: TicketData,
) {
    var undone: Boolean = false
    val restoredKeys: MutableSet<EntityKey> = HashSet()
    val compensated: MutableSet<Int> = HashSet()

    val keys: Set<EntityKey>
        get() = data.captures.map { it.key }.toSet()

    /** Every entity this action captured, created or declared as touched. */
    val footprint: Set<EntityKey>
        get() = keys + data.touches

    override fun toString(): String = "Entry(sequence=$sequence, failed=$failed, undone=$undone)"
}

/** An entry together with the entities and effects of it already undone by an earlier pass. */
internal class PendingEntry(val entry: Entry, val restored: Set<EntityKey>, val compensated: Set<Int>) {
    /** The declared effects still to reverse, each with its position in the action's declarations. */
    fun pendingCompensations(): List<Pair<Int, Compensation>> =
        entry.data.compensations.withIndex().filter { it.index !in compensated }.map { it.index to it.value }
}

/**
 * One command's actions. Withheld when the journal missed or rejected any of them. Every field changes only under the
 * journal's lock.
 */
internal class Group(val key: String, var parentGroupKey: String?) {
    var withheld: Boolean = false

    /** True while an undo of this group is running. */
    var undoing: Boolean = false

    /** Rises on every change, so a copy of the group's view can tell it is stale. */
    var revision: Long = 0
    val entries: MutableList<Entry> = ArrayList()

    fun bump() {
        revision++
    }

    /** The positions recorded for [runId]. */
    fun positionsOf(runId: String): Set<Int> = entries.filter { it.ref.runId == runId }.map { it.ref.position }.toSet()
}

/** What a recorded action holds when its ticket was missing, rejected or not sealed: nothing to restore. */
private val UNUSABLE = TicketData(emptyList(), emptySet(), emptyList(), false, false)

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
     * that did not fail) withholds the whole group, so "undo all" is never offered for part of it. An action that is
     * not usable is still listed, with nothing to restore, so the group's view shows every action that was reported.
     */
    fun append(groupKey: String, parentGroupKey: String?, ref: EntryRef, failed: Boolean, data: TicketData?) {
        synchronized(lock) {
            val group = groupFor(groupKey)
            if (group.parentGroupKey == null) group.parentGroupKey = parentGroupKey
            val duplicate = group.entries.any { it.ref == ref }
            val usable = data != null && (failed || data.sealed) && !duplicate
            if (!usable) group.withheld = true
            if (!duplicate) {
                val kept = if (usable && !group.withheld) data else null
                group.entries.add(Entry(group, ref, nextSequence++, failed, kept ?: UNUSABLE))
            }
            group.bump()
        }
    }

    /** Checks that every position a run applied was recorded: a missing one withholds the group. */
    fun runClosed(groupKey: String, runId: String, appliedPositions: Set<Int>) {
        synchronized(lock) {
            if (appliedPositions.isEmpty() && groupKey !in groups) return
            val group = groupFor(groupKey)
            if (!group.positionsOf(runId).containsAll(appliedPositions)) group.withheld = true
            group.bump()
        }
    }

    /** Withholds the group, creating it when it is not known. */
    fun withhold(groupKey: String) {
        synchronized(lock) {
            val group = groupFor(groupKey)
            group.withheld = true
            group.bump()
        }
    }

    /** The view of the group, or null when it is not known. */
    fun view(groupKey: String): UndoGroup? = synchronized(lock) { groups[groupKey]?.let(::viewOf) }

    private fun groupFor(groupKey: String): Group = groups.getOrPut(groupKey) { Group(groupKey, null) }

    /**
     * What an undo of the group may work on. When it has something to undo the group is claimed in the same locked
     * step, so a second call sees [UndoReason.IN_PROGRESS] and never works on the same entities. A claim is given back
     * with [release].
     */
    fun claim(groupKey: String): Scope = synchronized(lock) {
        val group = groups[groupKey]
        when {
            group == null -> Scope(UndoReason.UNKNOWN_GROUP, emptyList())
            group.undoing -> Scope(UndoReason.IN_PROGRESS, emptyList())
            group.withheld -> Scope(UndoReason.JOURNAL_WITHHELD, emptyList())
            else -> {
                val pending = group.entries.filter { !it.undone }.map(::pendingOf)
                group.undoing = pending.isNotEmpty()
                Scope(null, pending)
            }
        }
    }

    /** Gives back the claim of [groupKey]. Safe to call when there is none. */
    fun release(groupKey: String) {
        synchronized(lock) { groups[groupKey]?.undoing = false }
    }

    /** Marks [done] restored on each of [entries]. An entry with everything restored and reversed is undone. */
    fun markRestored(entries: List<Entry>, done: Set<EntityKey>) {
        synchronized(lock) {
            for (entry in entries) {
                val added = entry.restoredKeys.addAll(entry.keys.filter { it in done })
                if (refresh(entry) || added) entry.group.bump()
            }
        }
    }

    /** Marks the effect at [index] of [entry] as reversed. */
    fun markCompensated(entry: Entry, index: Int) {
        synchronized(lock) {
            entry.compensated.add(index)
            refresh(entry)
            entry.group.bump()
        }
    }

    /** The entries of [entries] that are now fully undone. */
    fun finished(entries: List<Entry>): List<Entry> = synchronized(lock) { entries.filter { it.undone } }
}

private fun viewOf(group: Group): UndoGroup {
    val pending = group.entries.filter { !it.undone }
    val isolated = if (group.withheld) emptyList() else Footprints(pending).isolatedEntries()
    return UndoGroup(
        group.key,
        group.parentGroupKey,
        group.revision,
        group.withheld,
        group.entries.map { it.ref },
        pending.map { it.ref },
        isolated.sortedBy { it.sequence }.map { it.ref },
    )
}

private fun pendingOf(entry: Entry) = PendingEntry(entry, entry.restoredKeys.toSet(), entry.compensated.toSet())

/** Marks [entry] undone when everything of it is restored and reversed. True when it just became undone. */
private fun refresh(entry: Entry): Boolean {
    val effectsDone = entry.data.compensations.indices.all { it in entry.compensated }
    val becomesUndone = !entry.undone && effectsDone && entry.restoredKeys.containsAll(entry.keys)
    if (becomesUndone) entry.undone = true
    return becomesUndone
}

/** The journal's undo of one group: verify the whole scope, restore, reverse the effects, and report what happened. */
internal class UndoPass(
    private val state: JournalState,
    adapters: Map<String, EntityAdapter>,
    compensators: Map<String, Compensator>,
) {
    private val verifier = Verifier(adapters, compensators.keys)
    private val restorer = Restorer(state)
    private val compensating = Compensating(compensators, state)

    suspend fun run(groupKey: String): UndoResult {
        val scope = state.claim(groupKey)
        val reason = scope.reason
        return when {
            reason != null -> refusal(reason)
            scope.pending.isEmpty() -> UndoResult.AlreadyUndone()
            else -> try {
                restore(scope.pending)
            } finally {
                state.release(groupKey)
            }
        }
    }

    private suspend fun restore(pending: List<PendingEntry>): UndoResult {
        val plan = verifier.verify(pending)
        if (plan.blockers.isNotEmpty()) return UndoResult.Refused(plan.blockers)
        val entries = pending.map { it.entry }
        state.markRestored(entries, plan.satisfied)
        val outcome = restorer.run(plan.steps, entries, Footprints(entries))
        val restoredKeys = HashSet<EntityKey>(plan.satisfied).apply {
            addAll(outcome.restored)
            pending.forEach { addAll(it.restored) }
        }
        val notRestored = outcome.notRestored + compensating.run(pending, restoredKeys)
        val restored = state.finished(entries).sortedByDescending { it.sequence }.map { it.ref }
        return if (notRestored.isEmpty()) UndoResult.Complete(restored) else UndoResult.Partial(restored, notRestored)
    }

    private fun refusal(reason: UndoReason): UndoResult =
        UndoResult.Refused(listOf(Blocker(null, null, reason)))
}
