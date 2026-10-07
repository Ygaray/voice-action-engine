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

    /** Lets go of the snapshots: an undone action (or a dropped group) needs them no more. */
    fun dropSnapshots() {
        data.captures.forEach { it.snapshot = null }
    }

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

    /** When the group last changed, by the journal's clock. */
    var lastActive: Long = 0

    /** Orders groups by their last change, whatever the clock says. */
    var activity: Long = 0
    val entries: MutableList<Entry> = ArrayList()

    fun bump() {
        revision++
    }

    /** A change at [now]; [tick] is the next value of the journal's activity counter. */
    fun touch(now: Long, tick: Long) {
        revision++
        lastActive = now
        activity = tick
    }

    /** Lets go of every snapshot, for a group that is leaving the journal. */
    fun dropSnapshots() {
        entries.forEach { it.dropSnapshots() }
    }

    /**
     * Withholds the group. A withheld group is never undone, so the snapshots its earlier actions already hold are
     * dead weight of user data and are released now.
     */
    fun withhold() {
        withheld = true
        dropSnapshots()
    }

    /** The positions recorded for [runId]. */
    fun positionsOf(runId: String): Set<Int> = entries.filter { it.ref.runId == runId }.map { it.ref.position }.toSet()
}

/** What a recorded action holds when its ticket was missing, rejected or not sealed: nothing to restore. */
private val UNUSABLE = TicketData(emptyList(), emptySet(), emptyList(), false, false)

/** One action as the app reported it: which, whether it reported an error, and its frozen ticket if it had one. */
internal class Recorded(val ref: EntryRef, val failed: Boolean, val data: TicketData?)

/**
 * What an undo pass may work on: the actions still to undo, or the reason it cannot go ahead. [entry] is the action a
 * refusal is about, or null when it is about the whole group.
 */
internal class Scope(val reason: UndoReason?, val pending: List<PendingEntry>, val entry: EntryRef?)

/**
 * The journal's records. Every access is inside a short `synchronized` block that never calls an adapter, a
 * compensator, the store or any other app code, so the clock is read by the caller and passed in as `now`.
 *
 * With [mirrored], the keys of dropped groups are kept until [drainDropped] hands them on, so the store can be told
 * outside the lock.
 */
internal class JournalState(private val retention: Retention, private val mirrored: Boolean) {
    private val lock = Any()
    private val groups = LinkedHashMap<String, Group>()
    private val dropped = ArrayList<String>()
    private var nextSequence = 0L
    private var nextActivity = 0L

    /** The marks an undo pass leaves on actions as it goes. */
    val marks = Marks(lock)

    fun groupCount(): Int = synchronized(lock) { groups.size }

    /**
     * Adds an action to its group. An anomaly (no usable ticket, a duplicate position, an unsealed ticket on an action
     * that did not fail) withholds the whole group, so "undo all" is never offered for part of it. An action that is
     * not usable is still listed, with nothing to restore, so the group's view shows every action that was reported.
     */
    fun append(groupKey: String, parentGroupKey: String?, recorded: Recorded, now: Long) {
        val ref = recorded.ref
        val failed = recorded.failed
        val data = recorded.data
        synchronized(lock) {
            val group = open(groupKey, now)
            if (group.parentGroupKey == null) group.parentGroupKey = parentGroupKey
            val duplicate = group.entries.any { it.ref == ref }
            val usable = data != null && (failed || data.sealed) && !duplicate
            if (!usable) group.withhold()
            if (!duplicate) {
                val kept = if (usable && !group.withheld) data else null
                group.entries.add(Entry(group, ref, nextSequence++, failed, kept ?: UNUSABLE))
            }
            group.touch(now, nextActivity++)
        }
    }

    /** Checks that every position a run applied was recorded: a missing one withholds the group. */
    fun runClosed(groupKey: String, runId: String, appliedPositions: Set<Int>, now: Long) {
        synchronized(lock) {
            sweep(now, null)
            if (appliedPositions.isEmpty() && groupKey !in groups) return
            val group = open(groupKey, now)
            if (!group.positionsOf(runId).containsAll(appliedPositions)) group.withhold()
            group.touch(now, nextActivity++)
        }
    }

    /** Withholds the group, creating it when it is not known. */
    fun withhold(groupKey: String, now: Long) {
        synchronized(lock) {
            val group = open(groupKey, now)
            group.withhold()
            group.touch(now, nextActivity++)
        }
    }

    /** The view of the group, or null when it is not known. */
    fun view(groupKey: String, now: Long): UndoGroup? = synchronized(lock) {
        sweep(now, null)
        groups[groupKey]?.let(::viewOf)
    }

    /** The view of the group for the store, or null when there is no store or the group is gone. */
    fun mirrorView(groupKey: String): UndoGroup? = synchronized(lock) {
        if (mirrored) groups[groupKey]?.let(::viewOf) else null
    }

    /** The keys of the groups dropped since the last call. */
    fun drainDropped(): List<String> = synchronized(lock) {
        val keys = dropped.toList()
        dropped.clear()
        keys
    }

    /**
     * What an undo of the group, or of the one action [only], may work on. When it has something to undo the group is
     * claimed in the same locked step, so a second call sees [UndoReason.IN_PROGRESS] and never works on the same
     * entities. A claim is given back with [release].
     */
    fun claim(groupKey: String, only: EntryRef?, now: Long): Scope = synchronized(lock) {
        sweep(now, null)
        val group = groups[groupKey]
        when {
            group == null -> Scope(UndoReason.UNKNOWN_GROUP, emptyList(), null)
            group.undoing -> Scope(UndoReason.IN_PROGRESS, emptyList(), null)
            group.withheld -> Scope(UndoReason.JOURNAL_WITHHELD, emptyList(), null)
            else -> scopeOf(group, only).also { group.undoing = it.pending.isNotEmpty() }
        }
    }

    /** Gives back the claim of [groupKey], counting the undo as activity when it [changed] anything. */
    fun release(groupKey: String, now: Long, changed: Boolean) {
        synchronized(lock) {
            val group = groups[groupKey] ?: return
            group.undoing = false
            if (changed) group.touch(now, nextActivity++)
        }
    }

    // Drops what the limits say, and remembers the keys for the store. A group dropped by age and then used again
    // comes back through open() as a withheld group.
    private fun sweep(now: Long, keep: String?) {
        val gone = retention.sweep(groups, now, keep)
        if (mirrored) dropped.addAll(gone)
    }

    // The group for the key, created withheld when it was dropped earlier: part of its command is gone for good.
    private fun open(groupKey: String, now: Long): Group {
        sweep(now, null)
        val group = groups.getOrPut(groupKey) {
            Group(groupKey, null).also { it.withheld = retention.wasDropped(groupKey) }
        }
        sweep(now, groupKey)
        return group
    }
}

/** The marks an undo pass leaves on actions: what is restored and reversed, and so what is undone. */
internal class Marks(private val lock: Any) {
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

// The actions an undo of the group (or of the one action [only]) works on, or why it cannot go ahead.
private fun scopeOf(group: Group, only: EntryRef?): Scope {
    val open = group.entries.filter { !it.undone }
    if (only == null) return Scope(null, open.map(::pendingOf), null)
    val entry = group.entries.firstOrNull { it.ref == only }
    return when {
        entry == null -> Scope(UndoReason.UNKNOWN_ENTRY, emptyList(), only)
        entry.undone -> Scope(null, emptyList(), null)
        !Footprints(open).isolated(entry) -> Scope(UndoReason.ENTANGLED, emptyList(), entry.ref)
        else -> Scope(null, listOf(pendingOf(entry)), null)
    }
}

private fun pendingOf(entry: Entry) = PendingEntry(entry, entry.restoredKeys.toSet(), entry.compensated.toSet())

/** Marks [entry] undone when everything of it is restored and reversed. True when it just became undone. */
private fun refresh(entry: Entry): Boolean {
    val effectsDone = entry.data.compensations.indices.all { it in entry.compensated }
    val becomesUndone = !entry.undone && effectsDone && entry.restoredKeys.containsAll(entry.keys)
    if (becomesUndone) {
        entry.undone = true
        entry.dropSnapshots()
    }
    return becomesUndone
}

/** The journal's undo of one group: verify the whole scope, restore, reverse the effects, and report what happened. */
internal class UndoPass(
    private val state: JournalState,
    adapters: Map<String, EntityAdapter>,
    compensators: Map<String, Compensator>,
) {
    private val verifier = Verifier(adapters, compensators.keys)
    private val restorer = Restorer(state.marks)
    private val compensating = Compensating(compensators, state.marks)

    /** Undoes the group, or only the action [only] when it is given. */
    suspend fun run(groupKey: String, only: EntryRef?, now: Long): UndoResult {
        val scope = state.claim(groupKey, only, now)
        val reason = scope.reason
        return when {
            reason != null -> UndoResult.Refused(listOf(Blocker(scope.entry, null, reason)))
            scope.pending.isEmpty() -> UndoResult.AlreadyUndone()
            else -> {
                var changed = false
                try {
                    restore(scope.pending).also { changed = it !is UndoResult.Refused }
                } finally {
                    state.release(groupKey, now, changed)
                }
            }
        }
    }

    private suspend fun restore(pending: List<PendingEntry>): UndoResult {
        val plan = verifier.verify(pending)
        if (plan.blockers.isNotEmpty()) return UndoResult.Refused(plan.blockers)
        val entries = pending.map { it.entry }
        state.marks.markRestored(entries, plan.satisfied)
        val outcome = restorer.run(plan.steps, entries, Footprints(entries))
        val restoredKeys = HashSet<EntityKey>(plan.satisfied).apply {
            addAll(outcome.restored)
            pending.forEach { addAll(it.restored) }
        }
        val notRestored = outcome.notRestored + compensating.run(pending, restoredKeys)
        val restored = state.marks.finished(entries).sortedByDescending { it.sequence }.map { it.ref }
        return if (notRestored.isEmpty()) UndoResult.Complete(restored) else UndoResult.Partial(restored, notRestored)
    }
}
