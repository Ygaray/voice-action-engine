package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.undo.UndoTicket

private const val ITEM = "item"

private fun missing(ticket: UndoTicket): StepResult {
    ticket.nothingWritten()
    return StepResult("item not found", true, "NOT_FOUND", emptyMap())
}

/** Creates an item, optionally under [parentId]. The ticket is the action's context, so the bridge can journal it. */
class CreateItem(
    private val store: ItemStore,
    ticket: UndoTicket,
    private val title: String,
    private val parentId: String?,
) : PendingMutation {
    override val toolName: String = "create_item"
    override val context: UndoTicket = ticket

    override suspend fun apply(): StepResult {
        if (parentId != null && store.get(parentId) == null) return missing(context)
        val item = store.create(title, parentId)
        context.created(ITEM, item.id)
        if (parentId != null) context.touches(ITEM, parentId)
        return StepResult("created", false, null, mapOf("id" to item.id))
    }
}

/** Renames an item. Whatever state the item is in when this is finally applied is what the undo restores. */
class RenameItem(
    private val store: ItemStore,
    ticket: UndoTicket,
    private val id: String,
    private val title: String,
) : PendingMutation {
    override val toolName: String = "rename_item"
    override val context: UndoTicket = ticket

    override suspend fun apply(): StepResult {
        if (store.get(id) == null) return missing(context)
        context.capture(ITEM, id)
        store.rename(id, title)
        context.settle(ITEM, id)
        return StepResult("renamed", false, null, mapOf("id" to id))
    }
}

/** Deletes an item. */
class DeleteItem(
    private val store: ItemStore,
    ticket: UndoTicket,
    private val id: String,
) : PendingMutation {
    override val toolName: String = "delete_item"
    override val context: UndoTicket = ticket

    override suspend fun apply(): StepResult {
        if (store.get(id) == null) return missing(context)
        context.capture(ITEM, id)
        store.delete(id)
        context.settle(ITEM, id)
        return StepResult("deleted", false, null, mapOf("id" to id))
    }
}
