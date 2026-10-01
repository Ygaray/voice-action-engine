package io.github.ygaray.voiceactionengine.providers.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val PROPERTIES = "properties"
private const val REQUIRED = "required"
private const val ITEMS = "items"

/**
 * True when any object in [schema] has a property that its `required` list does not name, so a model may leave that
 * property out. The walk covers the root, every value under `properties` and every `items` schema, at any depth.
 *
 * It is the one place that decides what "optional" means for a tool schema, so every provider that restricts strict
 * mode to schemas without optional properties asks the same question.
 */
internal fun hasOptionalProperties(schema: JsonObject): Boolean {
    val properties = schema[PROPERTIES] as? JsonObject
    return (properties != null && !requiredNames(schema).containsAll(properties.keys)) ||
        properties.orEmpty().values.any { (it as? JsonObject)?.let(::hasOptionalProperties) == true } ||
        (schema[ITEMS] as? JsonObject)?.let(::hasOptionalProperties) == true
}

private fun requiredNames(schema: JsonObject): Set<String> =
    (schema[REQUIRED] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
