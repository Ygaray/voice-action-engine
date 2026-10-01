package io.github.ygaray.voiceactionengine.providers.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val PROPERTIES = "properties"
private const val REQUIRED = "required"
private const val ITEMS = "items"
private const val PREFIX_ITEMS = "prefixItems"
private const val ANY_OF = "anyOf"
private const val ONE_OF = "oneOf"
private const val ALL_OF = "allOf"
private const val DEFS = "\$defs"
private const val DEFINITIONS = "definitions"
private const val ADDITIONAL_PROPERTIES = "additionalProperties"

/**
 * True when any object in [schema] has a property that its `required` list does not name, so a model may leave that
 * property out. The walk covers the root and, at any depth, every value under `properties`, `$defs` and `definitions`,
 * every `items` schema (one schema or a list of them), every entry of `prefixItems`, `anyOf`, `oneOf` and `allOf`, and
 * an `additionalProperties` that is itself a schema. Definitions are walked whether or not anything references them,
 * which can only make the answer more cautious.
 *
 * A property named in `required` is not optional even when its type admits null, so a nullable but required property
 * keeps a schema free of optionals.
 *
 * It is the one place that decides what "optional" means for a tool schema, so every provider that restricts strict
 * mode to schemas without optional properties asks the same question.
 */
internal fun hasOptionalProperties(schema: JsonObject): Boolean {
    val properties = schema[PROPERTIES] as? JsonObject
    return (properties != null && !requiredNames(schema).containsAll(properties.keys)) ||
        childSchemas(schema).any(::hasOptionalProperties)
}

// Every schema nested directly under [schema], in a fixed order; non-object entries are skipped.
private fun childSchemas(schema: JsonObject): List<JsonObject> =
    schemaMap(schema, PROPERTIES) +
        itemSchemas(schema) +
        listOf(PREFIX_ITEMS, ANY_OF, ONE_OF, ALL_OF).flatMap { schemaList(schema, it) } +
        schemaMap(schema, DEFS) +
        schemaMap(schema, DEFINITIONS) +
        listOfNotNull(schema[ADDITIONAL_PROPERTIES] as? JsonObject)

private fun schemaMap(schema: JsonObject, keyword: String): List<JsonObject> =
    (schema[keyword] as? JsonObject).orEmpty().values.mapNotNull { it as? JsonObject }

private fun schemaList(schema: JsonObject, keyword: String): List<JsonObject> =
    (schema[keyword] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

// `items` is one schema, or a list of schemas in the older tuple form.
private fun itemSchemas(schema: JsonObject): List<JsonObject> = when (val declared = schema[ITEMS]) {
    is JsonObject -> listOf(declared)
    is JsonArray -> declared.mapNotNull { it as? JsonObject }
    else -> emptyList()
}

private fun requiredNames(schema: JsonObject): Set<String> =
    (schema[REQUIRED] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
