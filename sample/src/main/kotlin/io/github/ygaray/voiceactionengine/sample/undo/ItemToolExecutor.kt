package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The tool that creates an item. The name is also what a plan step and a single-shot call use. */
internal const val ITEM_TOOL_CREATE = "create_item"

/** The tool that renames an item. */
internal const val ITEM_TOOL_RENAME = "rename_item"

/** The result key both write tools return: the id of the item they touched. A plan step refers to it as `$<step>.id`. */
internal const val ITEM_RESULT_KEY = "id"

private const val ARG_TITLE = "title"
private const val ARG_PARENT = "parent_id"
private const val ARG_ID = "id"
private const val TYPE_STRING = "string"
private const val INVALID_ARGUMENTS = "invalid_arguments"
private const val UNKNOWN_TOOL = "unknown_tool"

/** A short, domain-free system line for the stateful item legs. Sent to the provider: synthetic words only. */
internal const val ITEM_SYSTEM =
    "You manage a plain list of items. Each item has a title and may sit under a parent item. " +
        "A tool that creates an item returns the key id, the new item's id."

/**
 * The tool specs the stateful legs and the undo leg share. Both are mutating, and each description states the key it
 * returns (`id`), so a plan can pass it on to a later step as `$<step>.id`. `strict` stays null because `parent_id` is
 * optional.
 */
internal object ItemTools {
    /** Creates an item; `title` is required and `parent_id` is optional. Returns the key `id`, the new item's id. */
    val createItem: ToolSpec = ToolSpec(
        ITEM_TOOL_CREATE,
        "Creates one item with a title, optionally under the parent item named by parent_id. " +
            "Returns the key id: the id of the new item.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject(ARG_TITLE) { put("type", TYPE_STRING) }
                putJsonObject(ARG_PARENT) { put("type", TYPE_STRING) }
            }
            putJsonArray("required") { add(ARG_TITLE) }
            put("additionalProperties", false)
        },
        mutating = true,
    )

    /** Renames an item; `id` and `title` are required. Returns the key `id`, the renamed item's id. */
    val renameItem: ToolSpec = ToolSpec(
        ITEM_TOOL_RENAME,
        "Renames one item by id. Returns the key id: the id of the renamed item.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject(ARG_ID) { put("type", TYPE_STRING) }
                putJsonObject(ARG_TITLE) { put("type", TYPE_STRING) }
            }
            putJsonArray("required") {
                add(ARG_ID)
                add(ARG_TITLE)
            }
            put("additionalProperties", false)
        },
        mutating = true,
    )

    /** The two tools in a fixed order. */
    val all: List<ToolSpec> = listOf(createItem, renameItem)

    /** A snapshot of [all]; [singleShotTool] names the tool a single-shot tier forces, or null. */
    fun snapshot(singleShotTool: String?): ToolingSnapshot = ToolingSnapshot(ITEM_SYSTEM, all, singleShotTool)
}

/**
 * The stateful [ToolExecutor] over an [ItemStore]: `create_item` becomes a [CreateItem] mutation and `rename_item` a
 * [RenameItem] mutation, each with a ticket from [journal]. Applying a mutation returns the key `id` in its step result,
 * so a later plan step can use it. Arguments are validated here; a bad or unknown call is a finished error step with a
 * fixed text that never echoes an argument, and nothing is thrown.
 */
internal class ItemToolExecutor(private val store: ItemStore, private val journal: UndoJournal) : ToolExecutor {
    override suspend fun prepare(call: Extraction, input: CommandInput): ToolStep = when (call.toolName) {
        ITEM_TOOL_CREATE -> create(call.arguments)
        ITEM_TOOL_RENAME -> rename(call.arguments)
        else -> failed(call.toolName, UNKNOWN_TOOL)
    }

    private fun create(arguments: JsonObject): ToolStep {
        val title = text(arguments[ARG_TITLE])
        val parent = arguments[ARG_PARENT]
        val parentId = text(parent)
        return when {
            title == null -> failed(ITEM_TOOL_CREATE, INVALID_ARGUMENTS)
            parent != null && parentId == null -> failed(ITEM_TOOL_CREATE, INVALID_ARGUMENTS)
            else -> ToolStep.Mutation(CreateItem(store, journal.newTicket(), title, parentId))
        }
    }

    private fun rename(arguments: JsonObject): ToolStep {
        val id = text(arguments[ARG_ID])
        val title = text(arguments[ARG_TITLE])
        return if (id == null || title == null) {
            failed(ITEM_TOOL_RENAME, INVALID_ARGUMENTS)
        } else {
            ToolStep.Mutation(RenameItem(store, journal.newTicket(), id, title))
        }
    }

    // A non-blank string value, or null for anything else (absent, a number, an object, a blank string).
    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun failed(tool: String, code: String): ToolStep =
        ToolStep.Finished(tool, FinishedKind.ERROR, StepResult(code, true, code, emptyMap()))

    /** Never the store or the journal. */
    override fun toString(): String = "ItemToolExecutor"
}

/** The resolver of a single-shot tier over [executor]: the model's one call becomes the executor's one step. */
internal class ItemResolver(private val executor: ItemToolExecutor) : OutcomeResolver {
    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution =
        Resolution.Steps(listOf(executor.prepare(extraction, input)))

    override fun toString(): String = "ItemResolver"
}
