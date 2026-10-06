package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.Blocker
import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.EntityKey
import io.github.ygaray.voiceactionengine.undo.NotRestored
import io.github.ygaray.voiceactionengine.undo.UndoReason

/** One write the verified plan will make. */
internal class Step(
    val entry: Entry,
    val key: EntityKey,
    val adapter: EntityAdapter,
    val expectedFingerprint: String?,
    val snapshot: Any?,
    val order: Int,
) {
    fun failure(reason: UndoReason, errorClass: String?): NotRestored =
        NotRestored(entry.ref, key, null, reason, errorClass)
}

/**
 * What a verification found. [blockers] is non-empty when the undo must refuse, in a deterministic order. Otherwise
 * [steps] are the writes to make (newest action first) and [satisfied] are the entities that are already as they were.
 */
internal class Plan(val blockers: List<Blocker>, val steps: List<Step>, val satisfied: Set<EntityKey>)

/** One action's capture of one entity: a link in that entity's chain of writes. */
private class Link(val entry: Entry, val capture: Capture)

/** A blocker with the sequence of the action it names, so blockers can be put in a deterministic order. */
private class Finding(val sequence: Long, val blocker: Blocker)

/** The result of reading an entity's live fingerprint: its value, or that the read threw. */
private class LiveRead(val fingerprint: String?, val failed: Boolean)

private class PlanBuilder {
    val findings = ArrayList<Finding>()
    val steps = ArrayList<Step>()
    val satisfied = HashSet<EntityKey>()

    fun block(entry: Entry, key: EntityKey?, reason: UndoReason) {
        findings.add(Finding(entry.sequence, Blocker(entry.ref, key, reason)))
    }

    fun build(): Plan {
        val blockers = findings
            .distinctBy { listOf(it.sequence, it.blocker.entity?.type, it.blocker.entity?.id, it.blocker.reason.value) }
            .sortedWith(
                compareByDescending<Finding> { it.sequence }
                    .thenBy { it.blocker.entity?.type.orEmpty() }
                    .thenBy { it.blocker.entity?.id.orEmpty() }
                    .thenBy { it.blocker.reason.value },
            )
            .map { it.blocker }
        // Newest action first and, inside one action, the entity captured last first: the reverse of the writes.
        val ordered = steps.sortedWith(compareByDescending<Step> { it.entry.sequence }.thenByDescending { it.order })
        return Plan(blockers, ordered, satisfied)
    }
}

/**
 * Reads, and never writes. For the whole scope it decides, entity by entity, whether the undo can proceed:
 * - live equals the earliest before-fingerprint: already as it was, nothing to write;
 * - otherwise every write must be settled and each must have started from where the previous one left the entity,
 *   and the live state must equal the newest after-fingerprint, which is then restored;
 * - anything else blocks, and one blocker is enough to refuse the whole undo.
 *
 * An effect outside the database with no registered compensator blocks too.
 */
internal class Verifier(
    private val adapters: Map<String, EntityAdapter>,
    private val compensatorKinds: Set<String>,
) {

    suspend fun verify(pending: List<PendingEntry>): Plan {
        val plan = PlanBuilder()
        for (p in pending) {
            val entry = p.entry
            // A failed action may have written; with nothing captured and no word that it wrote nothing, it is unknown.
            if (entry.failed && !entry.data.nothingWritten && entry.data.captures.isEmpty()) {
                plan.block(entry, null, UndoReason.UNVERIFIABLE)
            }
            // An effect outside the database that nothing is registered to reverse: refuse before anything is written.
            if (p.pendingCompensations().any { (_, compensation) -> compensation.kind !in compensatorKinds }) {
                plan.block(entry, null, UndoReason.NO_ADAPTER)
            }
        }
        for ((key, links) in chainsOf(pending)) checkKey(key, links, plan)
        return plan.build()
    }

    private fun chainsOf(pending: List<PendingEntry>): Map<EntityKey, List<Link>> =
        pending
            .flatMap { p -> p.entry.data.captures.filter { it.key !in p.restored }.map { Link(p.entry, it) } }
            .groupBy { it.capture.key }
            .mapValues { (_, links) -> links.sortedBy { it.entry.sequence } }

    private suspend fun checkKey(key: EntityKey, links: List<Link>, plan: PlanBuilder) {
        val problems = links.mapNotNull { link -> link.capture.problem?.let { link to it } }
        val adapter = adapters[key.type]
        when {
            problems.isNotEmpty() -> problems.forEach { (link, reason) -> plan.block(link.entry, key, reason) }
            adapter == null -> plan.block(links.last().entry, key, UndoReason.NO_ADAPTER)
            else -> checkLive(key, links, adapter, plan)
        }
    }

    private suspend fun checkLive(key: EntityKey, links: List<Link>, adapter: EntityAdapter, plan: PlanBuilder) {
        val live = guardedCall(onFault = { LiveRead(null, true) }) { LiveRead(adapter.fingerprint(key.id), false) }
        when {
            live.failed -> plan.block(links.last().entry, key, UndoReason.UNVERIFIABLE)
            live.fingerprint == links.first().capture.beforeFingerprint -> plan.satisfied.add(key)
            else -> checkChain(key, links, adapter, live.fingerprint, plan)
        }
    }

    private fun checkChain(
        key: EntityKey,
        links: List<Link>,
        adapter: EntityAdapter,
        live: String?,
        plan: PlanBuilder,
    ) {
        val latest = links.last()
        val unsettled = links.lastOrNull { !it.capture.settled }
        val broken = links.zipWithNext().filter { (prev, next) ->
            next.capture.beforeFingerprint != prev.capture.afterFingerprint
        }
        when {
            unsettled != null -> plan.block(unsettled.entry, key, UndoReason.UNVERIFIABLE)
            broken.isNotEmpty() -> broken.forEach { (_, next) -> plan.block(next.entry, key, UndoReason.CHAIN_BROKEN) }
            live == latest.capture.afterFingerprint ->
                plan.steps.add(
                    Step(
                        latest.entry,
                        key,
                        adapter,
                        live,
                        links.first().capture.snapshot,
                        latest.entry.data.captures.indexOf(latest.capture),
                    ),
                )
            else -> plan.block(latest.entry, key, UndoReason.CHANGED_SINCE)
        }
    }
}
