package io.github.ygaray.voiceactionengine.sample.tools

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A short, domain-free system prompt for the synthetic item list assistant. */
internal const val SYNTHETIC_SYSTEM =
    "You manage a plain list of items. Each item has a title, an optional body and optional tags. " +
        "Find items before editing one, and ask the user when a request is ambiguous."

private const val TYPE_STRING = "string"
private const val TYPE_ARRAY = "array"

/**
 * The committed synthetic tool set the smokes, the docs and the tests share. It names no app domain. `strict` stays null
 * on every spec: the engine sends strict only when a tool has no optional field, so optional fields stay optional
 * (PROV-12).
 */
internal object SyntheticTools {
    /** A read tool; `query` is required. */
    val findItems: ToolSpec = ToolSpec(
        "find_items",
        "Finds items whose title or body matches a query.",
        objectSchema(required = listOf("query")) { putJsonObject("query") { put("type", TYPE_STRING) } },
        mutating = false,
    )

    /** A mutating tool; `title` is required, `body` and `tags` are optional. */
    val createItem: ToolSpec = ToolSpec(
        "create_item",
        "Creates an item with a title and, optionally, a body and tags.",
        objectSchema(required = listOf("title")) {
            putJsonObject("title") { put("type", TYPE_STRING) }
            bodyAndTags()
        },
        mutating = true,
    )

    /** A mutating tool; `id` is required, `title`, `body` and `tags` are optional. */
    val editItem: ToolSpec = ToolSpec(
        "edit_item",
        "Edits an item by id. Only the fields given are changed.",
        objectSchema(required = listOf("id")) {
            putJsonObject("id") { put("type", TYPE_STRING) }
            putJsonObject("title") { put("type", TYPE_STRING) }
            bodyAndTags()
        },
        mutating = true,
    )

    /** The A19 clarification tool: terminal and read-only. */
    val askUser: ToolSpec = ToolSpec.clarification("ask_user")

    /** The four tools in a fixed order. */
    val all: List<ToolSpec> = listOf(findItems, createItem, editItem, askUser)

    /** A snapshot of [all] with [SYNTHETIC_SYSTEM]; [singleShotTool] names the tool a single-shot tier forces, or null. */
    fun snapshot(singleShotTool: String?): ToolingSnapshot = ToolingSnapshot(SYNTHETIC_SYSTEM, all, singleShotTool)

    private fun JsonObjectBuilder.bodyAndTags() {
        putJsonObject("body") { put("type", TYPE_STRING) }
        putJsonObject("tags") {
            put("type", TYPE_ARRAY)
            putJsonObject("items") { put("type", TYPE_STRING) }
        }
    }

    private fun objectSchema(
        required: List<String>,
        properties: JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties", properties)
        putJsonArray("required") { required.forEach { add(it) } }
        put("additionalProperties", false)
    }
}
