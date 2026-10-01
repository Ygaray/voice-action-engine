package io.github.ygaray.voiceactionengine.providers.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val FORMAT = "format"
private const val PROPERTIES = "properties"
private const val ITEMS = "items"
private const val PREFIX_ITEMS = "prefixItems"
private const val ANY_OF = "anyOf"
private const val DEFS = "\$defs"
private const val DEFINITIONS = "definitions"

// Validation-only keywords that strict mode rejects, per the vendor structured-outputs guide and the official SDK's
// strict schema transform, checked 2026-10-01. Refresh from the vendor docs when they change.
private val DROPPED_KEYWORDS: Set<String> = setOf(
    "uniqueItems",
    "minProperties",
    "maxProperties",
    "contains",
    "minContains",
    "maxContains",
    "propertyNames",
    "contentEncoding",
    "contentMediaType",
)

// Dropped from the root only: they describe the document, not a value.
private val ROOT_ONLY_KEYWORDS: Set<String> = setOf("\$schema", "\$id")

// The string formats strict mode accepts, same sources and date as above; any other format is dropped.
private val SUPPORTED_FORMATS: Set<String> = setOf(
    "date-time",
    "time",
    "date",
    "duration",
    "email",
    "hostname",
    "ipv4",
    "ipv6",
    "uuid",
)

/**
 * A copy of [schema] that strict mode accepts, for the request sent with `strict: true` only. A call that is not strict
 * sends the app's schema as it is, so every hint in it still reaches the model.
 *
 * The copy loses the validation-only keywords strict mode rejects, the root `$schema` and `$id`, and a `format` that is
 * not one of the supported names. The walk visits schema positions only, so a property that happens to be named like a
 * keyword survives, and `enum`, `const` and `default` values are copied exactly. The lists are data, refreshed from the
 * vendor docs. [schema] itself is never changed.
 */
internal fun stripForChatStrict(schema: JsonObject): JsonObject = stripNode(schema, isRoot = true)

private fun stripNode(node: JsonObject, isRoot: Boolean): JsonObject = buildJsonObject {
    for ((key, value) in node) {
        val dropped = key in DROPPED_KEYWORDS ||
            (isRoot && key in ROOT_ONLY_KEYWORDS) ||
            (key == FORMAT && !isSupportedFormat(value))
        if (!dropped) put(key, stripValue(key, value))
    }
}

private fun isSupportedFormat(value: JsonElement): Boolean =
    (value as? JsonPrimitive)?.takeIf { it.isString }?.content in SUPPORTED_FORMATS

// Recurses only where [key] holds sub-schemas; any other value is copied untouched.
private fun stripValue(key: String, value: JsonElement): JsonElement = when {
    key in setOf(PROPERTIES, DEFS, DEFINITIONS) && value is JsonObject ->
        JsonObject(value.mapValues { (_, entry) -> stripSchema(entry) })
    key == ITEMS -> stripSchema(value)
    key in setOf(PREFIX_ITEMS, ANY_OF) && value is JsonArray -> JsonArray(value.map(::stripSchema))
    else -> value
}

// A schema position holds an object schema, or a list of them for the tuple form of items.
private fun stripSchema(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> stripNode(value, isRoot = false)
    is JsonArray -> JsonArray(value.map(::stripSchema))
    else -> value
}
