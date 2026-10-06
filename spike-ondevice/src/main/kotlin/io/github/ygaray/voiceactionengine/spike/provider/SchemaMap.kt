package io.github.ygaray.voiceactionengine.spike.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** A value the model returned does not have the type its tool schema declares. It carries no value and no message. */
internal class SchemaTypeMismatch : RuntimeException("schema_type_mismatch") {
    override fun toString(): String = "SchemaTypeMismatch"
}

private const val TYPE = "type"
private const val PROPERTIES = "properties"
private const val ITEMS = "items"

/**
 * Hands a JSON schema to LiteRT-LM as the plain maps and lists it expects: strings, booleans, `Long` or `Double`
 * numbers, nested lists and nested maps.
 */
internal fun jsonToMap(schema: JsonObject): Map<String, Any?> =
    schema.entries.associate { (key, value) -> key to jsonToAny(value) }

private fun jsonToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> jsonToMap(element)
    is JsonArray -> element.map { jsonToAny(it) }
    is JsonPrimitive -> primitiveToAny(element)
}

// A quoted value stays a string. An unquoted one is a boolean, an integral Long, or else a Double.
private fun primitiveToAny(primitive: JsonPrimitive): Any = when {
    primitive.isString -> primitive.content
    primitive.booleanOrNull != null -> primitive.booleanOrNull as Boolean
    primitive.longOrNull != null -> primitive.longOrNull as Long
    else -> primitive.doubleOrNull ?: primitive.content
}

/**
 * Rebuilds a tool's arguments from the maps the runtime decoded, coercing numbers against [schema].
 *
 * The runtime decodes every JSON number to a `Double`, so `2` arrives as `2.0`. A `Double` with an integral value is
 * rendered as an integer when the schema field is `integer`; a non-integral `Double` for an `integer` field is a type
 * mismatch. Every declared `type` is checked the same way, so a model that answers a string where a number is declared
 * is a typed failure, never a harness error.
 *
 * @throws SchemaTypeMismatch when a value does not fit its declared type.
 */
internal fun mapToJson(arguments: Map<String, Any?>, schema: JsonObject?): JsonObject =
    JsonObject(arguments.entries.associate { (key, value) -> key to anyToJson(value, propertySchema(schema, key)) })

private fun propertySchema(schema: JsonObject?, key: String): JsonObject? =
    (schema?.get(PROPERTIES) as? JsonObject)?.get(key) as? JsonObject

private fun declaredType(schema: JsonObject?): String? = (schema?.get(TYPE) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun anyToJson(value: Any?, schema: JsonObject?): JsonElement {
    val type = declaredType(schema)
    return when (value) {
        null -> JsonNull
        is Boolean -> requireType(type, "boolean").let { JsonPrimitive(value) }
        is String -> requireType(type, "string").let { JsonPrimitive(value) }
        is Number -> numberToJson(value, type)
        is Map<*, *> -> requireType(type, "object").let { mapValue(value, schema) }
        is List<*> -> requireType(type, "array").let { listValue(value, schema) }
        else -> throw SchemaTypeMismatch()
    }
}

// A null declared type means the schema says nothing about this field, so any value passes.
private fun requireType(declared: String?, actual: String) {
    if (declared != null && declared != actual) throw SchemaTypeMismatch()
}

private fun numberToJson(value: Number, type: String?): JsonElement = when (type) {
    "integer" -> JsonPrimitive(integral(value))
    "number", null -> if (value is Double || value is Float) JsonPrimitive(value.toDouble()) else JsonPrimitive(value.toLong())
    else -> throw SchemaTypeMismatch()
}

private fun integral(value: Number): Long {
    if (value !is Double && value !is Float) return value.toLong()
    val d = value.toDouble()
    if (!d.isFinite() || d != Math.rint(d)) throw SchemaTypeMismatch()
    return d.toLong()
}

private fun mapValue(map: Map<*, *>, schema: JsonObject?): JsonObject {
    val converted = LinkedHashMap<String, Any?>()
    for ((key, v) in map) converted[key as? String ?: throw SchemaTypeMismatch()] = v
    return mapToJson(converted, schema)
}

private fun listValue(list: List<*>, schema: JsonObject?): JsonArray {
    val items = schema?.get(ITEMS) as? JsonObject
    return JsonArray(list.map { anyToJson(it, items) })
}
