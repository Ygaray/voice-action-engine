package io.github.ygaray.voiceactionengine.spike.envelope

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TYPE_STRING = "string"
private const val TYPE_ARRAY = "array"

/**
 * The small envelope (D-05): four domain-free synthetic tools that stand in for CT's small tool set. It mirrors the
 * sample module's synthetic set (the spike cannot depend on `:sample`), names no consumer-app domain and holds no secret,
 * so it is committed. `strict` stays null on every spec so optional fields stay optional.
 */
internal object SmallEnvelope {
    /** A short, domain-free system text for the item list assistant. */
    const val SYSTEM: String =
        "You manage a plain list of items. Each item has a title, an optional body and optional tags. " +
            "Find items before editing one, and ask the user when a request is ambiguous."

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

    /** The clarification tool: terminal and read-only. */
    val askUser: ToolSpec = ToolSpec.clarification("ask_user")

    /** The four tools in a fixed order. */
    val tools: List<ToolSpec> = listOf(findItems, createItem, editItem, askUser)

    /** The envelope snapshot a trial offers the model. */
    val envelope: EnvelopeSnapshot = EnvelopeSnapshot(Envelope.SMALL, SYSTEM, tools)

    /** The engine's tooling snapshot of this envelope; [singleShotTool] is the tool a forced single-shot tier calls, or null. */
    fun snapshot(singleShotTool: String?): ToolingSnapshot = ToolingSnapshot(SYSTEM, tools, singleShotTool)

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
