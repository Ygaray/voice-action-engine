package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.providers.schema.hasOptionalProperties
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** The most tools one request may mark strict; Anthropic rejects a request with more. */
internal const val MAX_STRICT_TOOLS = 20

private const val TYPE_KEY = "type"
private const val TYPE_OBJECT = "object"
private const val PROPERTIES = "properties"
private const val ITEMS = "items"
private const val ADDITIONAL_PROPERTIES = "additionalProperties"
private const val MIN_ITEMS = "minItems"
private const val MIN_ITEMS_LIMIT = 1L

// Keywords outside the subset Anthropic's strict mode accepts: references, combinators and numeric or length bounds.
private val UNSUPPORTED_KEYWORDS: Set<String> = setOf(
    "\$ref",
    "\$defs",
    "definitions",
    "anyOf",
    "oneOf",
    "allOf",
    "minimum",
    "maximum",
    "exclusiveMinimum",
    "exclusiveMaximum",
    "multipleOf",
    "minLength",
    "maxLength",
)

/**
 * True when [schema] can be sent with `strict: true` without changing what the model may return.
 *
 * Strict mode makes every property required, so a schema with an optional property is never eligible: the model would
 * fill the omitted value with an empty one that an app may read as "clear this field". Beyond that the schema must stay
 * inside Anthropic's strict subset: the root is an object, every object lists all its properties in `required` and sets
 * `additionalProperties` to false, there is no reference, combinator or numeric or length bound, and `minItems` is
 * absent, 0 or 1.
 */
internal fun isAnthropicStrictEligible(schema: JsonObject): Boolean =
    typeNames(schema) == listOf(TYPE_OBJECT) && !hasOptionalProperties(schema) && withinSubset(schema)

private fun withinSubset(node: JsonObject): Boolean =
    node.keys.none { it in UNSUPPORTED_KEYWORDS } &&
        minItemsAllowed(node) &&
        (!isObjectNode(node) || isClosed(node)) &&
        childSchemas(node).all(::withinSubset)

private fun minItemsAllowed(node: JsonObject): Boolean {
    val declared = node[MIN_ITEMS] ?: return true
    val count = (declared as? JsonPrimitive)?.longOrNull
    return count != null && count in 0L..MIN_ITEMS_LIMIT
}

private fun isClosed(node: JsonObject): Boolean =
    (node[ADDITIONAL_PROPERTIES] as? JsonPrimitive)?.booleanOrNull == false

private fun isObjectNode(node: JsonObject): Boolean = PROPERTIES in node || TYPE_OBJECT in typeNames(node)

private fun childSchemas(node: JsonObject): List<JsonObject> {
    val fromProperties = (node[PROPERTIES] as? JsonObject)?.values.orEmpty().mapNotNull { it as? JsonObject }
    return fromProperties + listOfNotNull(node[ITEMS] as? JsonObject)
}

// `type` is a single name or an array of names; anything else reads as no type at all.
private fun typeNames(node: JsonObject): List<String> = when (val type = node[TYPE_KEY]) {
    is JsonPrimitive -> listOfNotNull(type.takeIf { it.isString }?.contentOrNull)
    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.takeIf { name -> name.isString }?.contentOrNull }
    else -> emptyList()
}
