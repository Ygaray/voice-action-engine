package io.github.ygaray.voiceactionengine.undo

import io.github.ygaray.voiceactionengine.undo.internal.JournalState
import io.github.ygaray.voiceactionengine.undo.internal.NO_READING
import io.github.ygaray.voiceactionengine.undo.internal.Recorded
import io.github.ygaray.voiceactionengine.undo.internal.Retention
import io.github.ygaray.voiceactionengine.undo.internal.StoreMirror
import io.github.ygaray.voiceactionengine.undo.internal.UndoPass
import io.github.ygaray.voiceactionengine.undo.internal.guardedCall
import io.github.ygaray.voiceactionengine.undo.internal.requireToken

/** How many groups the journal keeps unless told otherwise. */
private const val GROUP_LIMIT: Int = 50

/** How long a group may be idle, in milliseconds, before the journal drops it unless told otherwise: one hour. */
private const val AGE_LIMIT_MILLIS: Long = 3_600_000

private const val NANOS_PER_MILLI: Long = 1_000_000

/**
 * A journal of what each command changed, so one call can undo a whole command.
 *
 * An app records each action it applies through an [UndoTicket], grouped under a key of its choosing (a pipeline
 * app uses the command's run id). [undoAll] then restores every entity the group touched, newest action first, or
 * refuses and writes nothing when anything moved on since.
 *
 * The journal lives in memory and does not survive process death. It keeps at most [Builder.maxGroups] groups and
 * drops a group idle for more than [Builder.maxAgeMillis], so the snapshots of user data in it do not pile up or
 * linger. It depends on nothing but the Kotlin standard library, so it works in an app that has no voice engine at
 * all.
 *
 * Build one with the DSL:
 * ```
 * val journal = UndoJournal {
 *     adapter(itemAdapter)
 *     compensator("alarm", alarmCompensator)
 * }
 * ```
 */
public class UndoJournal internal constructor(settings: Builder) {
    private val adapters: Map<String, EntityAdapter> = settings.adapters.toMap()
    private val compensators: Map<String, Compensator> = settings.compensators.toMap()
    private val clock: () -> Long = settings.clock
    private val mirror = StoreMirror(settings.store)
    private val state: JournalState
    private val pass: UndoPass

    @Volatile
    private var lastNow = NO_READING

    init {
        require(settings.maxGroups >= 1) { "maxGroups must be at least 1" }
        require(settings.maxAgeMillis >= 1) { "maxAgeMillis must be at least 1" }
        state = JournalState(Retention(settings.maxGroups, settings.maxAgeMillis), mirrored = mirror.enabled)
        pass = UndoPass(state, adapters, compensators)
    }

    /** Collects the adapters, compensators and limits of a journal. */
    public class Builder internal constructor() {
        internal val adapters: MutableMap<String, EntityAdapter> = LinkedHashMap()
        internal val compensators: MutableMap<String, Compensator> = LinkedHashMap()

        /**
         * How many groups the journal keeps; past that the least recently active group is dropped, except one being
         * undone. Default 50. Must be at least 1.
         */
        public var maxGroups: Int = GROUP_LIMIT

        /**
         * How long a group may be idle, in milliseconds since its last change, before the journal drops it. Default one
         * hour. Must be at least 1.
         */
        public var maxAgeMillis: Long = AGE_LIMIT_MILLIS

        /**
         * An optional mirror of the journal, told about every change and every dropped group. The journal still lives
         * in memory only: see [JournalStore]. Default none.
         */
        public var store: JournalStore? = null

        /**
         * The time source for [maxAgeMillis], in milliseconds. Only differences between readings matter. The default
         * is a monotonic clock, so a change of the wall clock never ages a group early; tests replace it.
         */
        public var clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }

        /**
         * Registers the adapter for its entity type.
         *
         * @throws IllegalArgumentException when the type is blank or already has an adapter.
         */
        public fun adapter(adapter: EntityAdapter) {
            val type = adapter.entityType
            requireToken("entityType", type)
            require(type !in adapters) { "an adapter is already registered for entity type $type" }
            adapters[type] = adapter
        }

        /**
         * Registers the compensator for an effect [kind].
         *
         * @throws IllegalArgumentException when the kind is blank or already has a compensator.
         */
        public fun compensator(kind: String, compensator: Compensator) {
            requireToken("kind", kind)
            require(kind !in compensators) { "a compensator is already registered for kind $kind" }
            compensators[kind] = compensator
        }
    }

    /** Creates the builder for a journal. */
    public companion object {
        /** Builds a journal from [block]. */
        public operator fun invoke(block: Builder.() -> Unit): UndoJournal = UndoJournal(Builder().apply(block))
    }

    /** A fresh ticket for one action; use it inside the app's apply, then pass it to [record]. */
    public fun newTicket(): UndoTicket = UndoTicket(this, adapters)

    /**
     * Records one applied action.
     *
     * @param groupKey the group the action belongs to; "undo all" undoes the group.
     * @param parentGroupKey the group this one continues (the first record of a group decides), or null.
     * @param entry which action this is.
     * @param failed true when the action was applied and reported an error, so it may have written.
     * @param ticket what the action captured, or null when the app has nothing to say about it.
     *
     * A record into a group the journal already dropped (a held change confirmed long after its run) makes a withheld
     * group, so the rest of the command is never offered as a partial undo. The journal remembers the keys of the most
     * recently dropped groups only, at most the larger of 1000 and 20 times [Builder.maxGroups]; a record into a key
     * older than that opens a fresh group that is not withheld.
     *
     * @throws IllegalArgumentException only for an invalid key. Every other anomaly withholds the whole group (so
     *   [undoAll] refuses with [UndoReason.JOURNAL_WITHHELD] and never offers a partial undo): a null ticket, a ticket
     *   from another journal or one already recorded, a repeated run and position, or a ticket left unsealed by an
     *   action that did not fail.
     */
    public suspend fun record(
        groupKey: String,
        parentGroupKey: String?,
        entry: EntryRef,
        failed: Boolean,
        ticket: UndoTicket?,
    ) {
        requireToken("groupKey", groupKey)
        if (parentGroupKey != null) requireToken("parentGroupKey", parentGroupKey)
        val data = ticket?.takeIf { it.owner === this }?.freeze()
        state.append(groupKey, parentGroupKey, Recorded(entry, failed, data), now())
        publish(groupKey)
    }

    /**
     * Says that [runId] is finished and applied the actions at [appliedPositions] in the group. This is an integrity
     * check for that run and never a seal: more actions, for example a held change confirmed later, may still be
     * recorded under the same group. When any applied position was not recorded the group is withheld, so "undo all"
     * is refused rather than offered for fewer actions than ran. A group the journal does not know is created
     * withheld when [appliedPositions] is not empty, and is not created when it is.
     *
     * @throws IllegalArgumentException only for an invalid key.
     */
    public suspend fun runClosed(groupKey: String, runId: String, appliedPositions: Set<Int>) {
        requireToken("groupKey", groupKey)
        requireToken("runId", runId)
        state.runClosed(groupKey, runId, appliedPositions.toSet(), now())
        publish(groupKey)
    }

    /**
     * Withholds the group because the app knows an action of it was not recorded: "undo all" is refused with
     * [UndoReason.JOURNAL_WITHHELD] from now on, and the group is created when the journal does not know it.
     *
     * @throws IllegalArgumentException only for an invalid key.
     */
    public suspend fun withhold(groupKey: String) {
        requireToken("groupKey", groupKey)
        state.withhold(groupKey, now())
        publish(groupKey)
    }

    /**
     * The view of the group, for showing "Undo all (N)", or null when the journal does not know it.
     *
     * @throws IllegalArgumentException only for an invalid key.
     */
    public suspend fun group(groupKey: String): UndoGroup? {
        requireToken("groupKey", groupKey)
        val view = state.view(groupKey, now())
        publish(null)
        return view
    }

    /**
     * Undoes every action of the group, newest first.
     *
     * Before anything is written, every entity in scope is checked: one that was changed, deleted or recreated after
     * the command makes the whole undo [UndoResult.Refused] with nothing written. An unknown or withheld group is
     * refused too, and a group with nothing left to undo gives [UndoResult.AlreadyUndone].
     *
     * @throws IllegalArgumentException when [groupKey] is blank or longer than 256 characters.
     */
    public suspend fun undoAll(groupKey: String): UndoResult {
        requireToken("groupKey", groupKey)
        return undone(groupKey, null)
    }

    /**
     * Undoes the single action [entry] of the group, for an action that shares no entity with another action not yet
     * undone (see [UndoGroup.isolated]). Only that action's own entities are checked.
     *
     * An action that shares an entity with another is refused with [UndoReason.ENTANGLED] and nothing is written: it is
     * never widened to the actions it shares with, so use [undoAll] for those. An unknown group, a withheld group, an
     * unknown action and one that is already undone are answered like [undoAll] does, and the call shares the group's
     * in-progress claim with it.
     *
     * @throws IllegalArgumentException when [groupKey] is blank or longer than 256 characters.
     */
    public suspend fun undoEntry(groupKey: String, entry: EntryRef): UndoResult {
        requireToken("groupKey", groupKey)
        return undone(groupKey, entry)
    }

    /** How many store calls threw. Always 0 without a [Builder.store]. A store fault never changes a result. */
    public val storeFaults: Int
        get() = mirror.faultCount

    // A refusal wrote nothing, so there is no change for the store to hear about. A cancelled undo may already have
    // restored entities, so the store hears about it too.
    private suspend fun undone(groupKey: String, only: EntryRef?): UndoResult {
        var result: UndoResult? = null
        try {
            result = pass.run(groupKey, only, now())
            return result
        } finally {
            publish(if (result is UndoResult.Refused) null else groupKey)
        }
    }

    // The store is app code: it is called here, after the journal's lock was given back.
    private suspend fun publish(groupKey: String?) {
        if (!mirror.enabled) return
        mirror.deliver(state.drainDropped(), groupKey?.let(state::mirrorView))
    }

    // The time source is app code, so a fault in it never reaches the caller: the last good reading stands in, and
    // NO_READING (which ages nothing) before there has been one.
    private fun now(): Long = guardedCall(onFault = { lastNow }) { clock().also { lastNow = it } }

    override fun toString(): String = "UndoJournal(groups=${state.groupCount()})"
}
