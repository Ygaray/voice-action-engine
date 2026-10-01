package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.providers.schema.hasOptionalProperties
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
private const val ENUM = "enum"
private const val CONST = "const"

// Size limits of the strict subset; a schema over any of them is sent without strict mode rather than rejected.
private const val MAX_PROPERTIES = 5_000
private const val MAX_DEPTH = 10
private const val MAX_ENUM_VALUES = 1_000
private const val MAX_SCHEMA_TEXT_CHARS = 120_000

// Keywords outside the subset that OpenAI's strict mode accepts, per the vendor structured-outputs guide and the
// official SDK's strict schema transform, checked 2026-10-01. A combinator that is supported (anyOf, $defs,
// definitions, $ref) is not listed. Refresh from the vendor docs when they change.
private val UNSUPPORTED_KEYWORDS: Set<String> = setOf(
    "allOf",
    "oneOf",
    "not",
    "if",
    "then",
    "else",
    "dependentRequired",
    "dependentSchemas",
    "dependencies",
    "prefixItems",
    "patternProperties",
    "unevaluatedProperties",
    "unevaluatedItems",
    "\$dynamicRef",
    "\$dynamicAnchor",
)

/**
 * True when [schema] can be sent with `strict: true` without changing what the model may return.
 *
 * Strict mode makes every property required, so a schema with an optional property is never eligible: the model would
 * fill the omitted value with an empty one that an app may read as "clear this field". The root must be an object,
 * every object in the schema must set `additionalProperties` to false, no schema may use a keyword outside OpenAI's
 * strict subset, and the schema must fit the documented size limits. A schema that fails any rule is sent as it is,
 * without strict mode; it is never rewritten to fit.
 */
internal fun isChatStrictEligible(schema: JsonObject): Boolean =
    typeNames(schema) == listOf(TYPE_OBJECT) &&
        !hasOptionalProperties(schema) &&
        withinSubset(schema) &&
        withinLimits(schema)

/**
 * Whether the request for [tool] carries `strict: true`.
 *
 * The engine decides. A tool's `strict` can only opt out (false); true cannot force strict mode onto a schema that has
 * an optional property, because the model would then fill that property in.
 */
internal fun effectiveChatStrict(tool: ToolSpec): Boolean =
    tool.strict != false && isChatStrictEligible(tool.inputSchema)

private fun withinSubset(node: JsonObject): Boolean =
    node.keys.none { it in UNSUPPORTED_KEYWORDS } &&
        (!isObjectNode(node) || (node[ADDITIONAL_PROPERTIES] as? JsonPrimitive)?.booleanOrNull == false) &&
        childSchemas(node).all(::withinSubset)

private fun isObjectNode(node: JsonObject): Boolean = PROPERTIES in node || TYPE_OBJECT in typeNames(node)

// Running totals for the size limits, filled in one pass over the schema positions.
private class SchemaTotals {
    var properties = 0
    var maxDepth = 0
    var enumValues = 0
    var textChars = 0
}

private fun withinLimits(root: JsonObject): Boolean {
    val totals = SchemaTotals()
    tally(root, 0, totals)
    return totals.properties <= MAX_PROPERTIES &&
        totals.maxDepth <= MAX_DEPTH &&
        totals.enumValues <= MAX_ENUM_VALUES &&
        totals.textChars <= MAX_SCHEMA_TEXT_CHARS
}

// Property names plus string enum and const values all count toward the text limit.
private fun tally(node: JsonObject, depth: Int, totals: SchemaTotals) {
    val level = if (isObjectNode(node)) depth + 1 else depth
    totals.maxDepth = maxOf(totals.maxDepth, level)
    val names = (node[PROPERTIES] as? JsonObject)?.keys.orEmpty()
    totals.properties += names.size
    totals.textChars += names.sumOf { it.length }
    val values = (node[ENUM] as? JsonArray).orEmpty()
    totals.enumValues += values.size
    totals.textChars += (values + listOfNotNull(node[CONST])).sumOf { stringLength(it) }
    childSchemas(node).forEach { tally(it, level, totals) }
}

private fun stringLength(value: JsonElement): Int =
    (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.length ?: 0

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
