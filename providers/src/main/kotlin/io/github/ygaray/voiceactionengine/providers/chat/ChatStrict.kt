package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.providers.schema.hasOptionalProperties
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

private const val TYPE_KEY = "type"
private const val TYPE_OBJECT = "object"
private const val PROPERTIES = "properties"
private const val ITEMS = "items"
private const val ANY_OF = "anyOf"
private const val DEFS = "\$defs"
private const val DEFINITIONS = "definitions"
private const val ADDITIONAL_PROPERTIES = "additionalProperties"

/**
 * True when [schema] can be sent with `strict: true` without changing what the model may return.
 *
 * Strict mode makes every property required, so a schema with an optional property is never eligible: the model would
 * fill the omitted value with an empty one that an app may read as "clear this field". The root must be an object and
 * every object in the schema must set `additionalProperties` to false. A schema that fails any rule is sent as it is,
 * without strict mode; it is never rewritten to fit.
 */
internal fun isChatStrictEligible(schema: JsonObject): Boolean =
    typeNames(schema) == listOf(TYPE_OBJECT) && !hasOptionalProperties(schema) && objectsAreClosed(schema)

/**
 * Whether the request for [tool] carries `strict: true`.
 *
 * The engine decides. A tool's `strict` can only opt out (false); true cannot force strict mode onto a schema that has
 * an optional property, because the model would then fill that property in.
 */
internal fun effectiveChatStrict(tool: ToolSpec): Boolean =
    tool.strict != false && isChatStrictEligible(tool.inputSchema)

private fun objectsAreClosed(node: JsonObject): Boolean =
    (!isObjectNode(node) || isClosed(node)) && childSchemas(node).all(::objectsAreClosed)

private fun isClosed(node: JsonObject): Boolean =
    (node[ADDITIONAL_PROPERTIES] as? JsonPrimitive)?.booleanOrNull == false

private fun isObjectNode(node: JsonObject): Boolean = PROPERTIES in node || TYPE_OBJECT in typeNames(node)

// Schema positions only: the values under these keywords, never the property names themselves.
private fun childSchemas(node: JsonObject): List<JsonObject> {
    val named = listOf(PROPERTIES, DEFS, DEFINITIONS).flatMap { keyword ->
        (node[keyword] as? JsonObject)?.values.orEmpty().mapNotNull { it as? JsonObject }
    }
    val items = when (val declared = node[ITEMS]) {
        is JsonObject -> listOf(declared)
        is JsonArray -> declared.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }
    val branches = (node[ANY_OF] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    return named + items + branches
}

// `type` is a single name or an array of names; anything else reads as no type at all.
private fun typeNames(node: JsonObject): List<String> = when (val type = node[TYPE_KEY]) {
    is JsonPrimitive -> listOfNotNull(type.takeIf { it.isString }?.contentOrNull)
    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.takeIf { name -> name.isString }?.contentOrNull }
    else -> emptyList()
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
