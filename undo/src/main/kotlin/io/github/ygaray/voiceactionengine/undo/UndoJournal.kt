package io.github.ygaray.voiceactionengine.undo

import io.github.ygaray.voiceactionengine.undo.internal.JournalState
import io.github.ygaray.voiceactionengine.undo.internal.UndoPass
import io.github.ygaray.voiceactionengine.undo.internal.requireToken

/**
 * A journal of what each command changed, so one call can undo a whole command.
 *
 * An app records each action it applies through an [UndoTicket], grouped under a key of its choosing (a pipeline
 * app uses the command's run id). [undoAll] then restores every entity the group touched, newest action first, or
 * refuses and writes nothing when anything moved on since.
 *
 * The journal lives in memory and does not survive process death. It depends on nothing but the Kotlin standard
 * library, so it works in an app that has no voice engine at all.
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
    private val state = JournalState()
    private val pass = UndoPass(state, adapters, compensators)

    /** Collects the adapters and compensators of a journal. */
    public class Builder internal constructor() {
        internal val adapters: MutableMap<String, EntityAdapter> = LinkedHashMap()
        internal val compensators: MutableMap<String, Compensator> = LinkedHashMap()

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
     * @throws IllegalArgumentException only for an invalid key. Every other anomaly withholds the whole group (so
     *   [undoAll] refuses with [UndoReason.JOURNAL_WITHHELD] and never offers a partial undo): a null ticket, a ticket
     *   from another journal or one already recorded, a repeated run and position, or a ticket left unsealed by an
     *   action that did not fail.
     */
    // One line on purpose: the plan's contract check greps the exact signature, which is wider than 120 columns.
    @Suppress("MaxLineLength")
    public suspend fun record(groupKey: String, parentGroupKey: String?, entry: EntryRef, failed: Boolean, ticket: UndoTicket?) {
        requireToken("groupKey", groupKey)
        if (parentGroupKey != null) requireToken("parentGroupKey", parentGroupKey)
        val data = ticket?.takeIf { it.owner === this }?.freeze()
        state.append(groupKey, parentGroupKey, entry, failed, data)
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
        state.runClosed(groupKey, runId, appliedPositions.toSet())
    }

    /**
     * Withholds the group because the app knows an action of it was not recorded: "undo all" is refused with
     * [UndoReason.JOURNAL_WITHHELD] from now on, and the group is created when the journal does not know it.
     *
     * @throws IllegalArgumentException only for an invalid key.
     */
    public suspend fun withhold(groupKey: String) {
        requireToken("groupKey", groupKey)
        state.withhold(groupKey)
    }

    /**
     * The view of the group, for showing "Undo all (N)", or null when the journal does not know it.
     *
     * @throws IllegalArgumentException only for an invalid key.
     */
    public suspend fun group(groupKey: String): UndoGroup? {
        requireToken("groupKey", groupKey)
        return state.view(groupKey)
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
        return pass.run(groupKey)
    }

    override fun toString(): String = "UndoJournal(groups=${state.groupCount()})"
}
