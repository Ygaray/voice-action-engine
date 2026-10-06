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

/** A blocker with the sequence of the action it names, so blockers can be put in a deterministic order. */
internal class Finding(val sequence: Long, val blocker: Blocker)

/** One write the verified plan will make. */
internal class Step(
    val entry: Entry,
    val key: EntityKey,
    val adapter: EntityAdapter,
    val expectedFingerprint: String?,
    val snapshot: Any?,
) {
    fun failure(reason: UndoReason, errorClass: String?): NotRestored =
        NotRestored(entry.ref, key, null, reason, errorClass)
}

/** What a verification found: the blockers, or the writes to make and the entities that need none. */
internal class Plan(val findings: List<Finding>, val steps: List<Step>, val satisfied: Set<EntityKey>)

/** The journal's undo of one group: verify the whole scope, then restore. */
internal class UndoPass(
    private val state: JournalState,
    private val adapters: Map<String, EntityAdapter>,
) {
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
        val plan = verify(pending)
        if (plan.findings.isNotEmpty()) return UndoResult.Refused(ordered(plan.findings))
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

    private suspend fun verify(pending: List<PendingEntry>): Plan {
        val findings = ArrayList<Finding>()
        val steps = ArrayList<Step>()
        val satisfied = HashSet<EntityKey>()
        val chains = pending.flatMap { p -> p.entry.data.captures.map { it.key to p.entry } }
            .groupBy({ it.first }, { it.second })
        for ((key, entries) in chains) {
            val ordered = entries.sortedBy { it.sequence }
            val latest = ordered.last()
            val earliest = ordered.first()
            val adapter = adapters[key.type]
            if (adapter == null) {
                findings.add(finding(latest, key, UndoReason.NO_ADAPTER))
                continue
            }
            val live = guardedCall(onFault = { LIVE_FAULT }) { adapter.fingerprint(key.id) }
            val before = earliest.data.captures.first { it.key == key }
            val after = latest.data.captures.first { it.key == key }
            when {
                live == LIVE_FAULT -> findings.add(finding(latest, key, UndoReason.UNVERIFIABLE))
                live == after.afterFingerprint -> steps.add(Step(latest, key, adapter, live, before.snapshot))
                live == before.beforeFingerprint -> satisfied.add(key)
                else -> findings.add(finding(latest, key, UndoReason.CHANGED_SINCE))
            }
        }
        return Plan(findings, steps.sortedWith(stepOrder), satisfied)
    }

    private fun finding(entry: Entry, key: EntityKey, reason: UndoReason): Finding =
        Finding(entry.sequence, Blocker(entry.ref, key, reason))

    private fun ordered(findings: List<Finding>): List<Blocker> =
        findings.sortedWith(
            compareByDescending<Finding> { it.sequence }
                .thenBy { it.blocker.entity?.type.orEmpty() }
                .thenBy { it.blocker.entity?.id.orEmpty() },
        ).map { it.blocker }

    private fun refusal(reason: UndoReason): UndoResult =
        UndoResult.Refused(listOf(Blocker(null, null, reason)))
}

// A fingerprint read that threw; it cannot collide with a real fingerprint, which an adapter returns as an opaque hash.
private const val LIVE_FAULT = "\u0000fault"

private val stepOrder: Comparator<Step> =
    compareByDescending<Step> { it.entry.sequence }.thenBy { it.key.type }.thenBy { it.key.id }
