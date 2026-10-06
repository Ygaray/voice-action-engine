package io.github.ygaray.voiceactionengine.spike.verdict

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.regex.PatternSyntaxException

/** The outcome of validating a value against a schema of the supported subset. */
internal sealed interface SchemaResult {
    data object Valid : SchemaResult

    /** The value breaks the schema; [code] is one of the stable codes below. */
    data class Invalid(val code: String) : SchemaResult
}

/**
 * A schema keyword (or type name) outside the supported subset. Callers fail loudly on it rather than validate partially:
 * accepting a schema we only half understand would count an unenforced constraint as satisfied.
 */
internal data class UnknownKeyword(val name: String) : SchemaResult

/** The keywords a schema uses, and the ones outside the supported set. */
internal data class KeywordScan(val used: Set<String>, val unknown: List<UnknownKeyword>)

/**
 * A subset JSON-Schema validator over `kotlinx.serialization.json`, limited to the keywords the spike's tool schemas use:
 * type (object, string, number, integer, boolean, array), required, properties, enum, items and additionalProperties,
 * the bounds minLength, maxLength, minimum, maximum and maxItems, pattern, and format uuid, plus the annotations
 * description, title and default (the SB fixture uses all of these, found by enumerating its keywords; any other keyword is
 * still reported unknown). This is the justified exception to "don't hand-roll": a general validator
 * would add a dependency and a legitimacy audit to a throwaway harness. Pure Kotlin, no Android API.
 */
internal object SchemaSubset {
    const val TYPE_MISMATCH = "type_mismatch"
    const val MISSING_REQUIRED = "missing_required"
    const val NOT_IN_ENUM = "not_in_enum"
    const val EXTRA_PROPERTY = "extra_property"
    const val ITEM_MISMATCH = "item_mismatch"
    const val LENGTH = "length"
    const val OUT_OF_RANGE = "out_of_range"
    const val TOO_MANY_ITEMS = "too_many_items"
    const val PATTERN_MISMATCH = "pattern_mismatch"
    const val FORMAT_MISMATCH = "format_mismatch"

    private val supported = setOf(
        "type", "required", "properties", "enum", "items", "additionalProperties",
        "minLength", "maxLength", "minimum", "maximum", "maxItems", "pattern", "format",
    )
    private val annotations = setOf("description", "title", "default")
    private const val UUID_FORMAT = "uuid"
    private val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val supportedTypes = setOf("object", "string", "number", "integer", "boolean", "array")

    /** Walks [schema] (properties, items and a schema-valued additionalProperties included) and reports its keywords. */
    fun keywordsIn(schema: JsonObject): KeywordScan {
        val used = linkedSetOf<String>()
        val unknown = ArrayList<UnknownKeyword>()
        scan(schema, used, unknown)
        return KeywordScan(used, unknown)
    }

    /** Validates [value] against [schema]; an unsupported keyword anywhere in the schema gives [UnknownKeyword]. */
    fun validate(schema: JsonObject, value: JsonElement): SchemaResult {
        keywordsIn(schema).unknown.firstOrNull()?.let { return it }
        return check(schema, value)
    }

    private fun flag(unknown: MutableList<UnknownKeyword>, name: String) {
        val keyword = UnknownKeyword(name)
        if (keyword !in unknown) unknown += keyword
    }

    private fun scan(schema: JsonObject, used: MutableSet<String>, unknown: MutableList<UnknownKeyword>) {
        for ((key, value) in schema) {
            if (key in annotations) continue
            if (key !in supported) {
                flag(unknown, key)
                continue
            }
            used += key
            when (key) {
                "type" -> {
                    val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (name == null) flag(unknown, "type:non_string") else if (name !in supportedTypes) flag(unknown, "type:$name")
                }
                "properties" -> (value as? JsonObject)?.values?.forEach { (it as? JsonObject)?.let { sub -> scan(sub, used, unknown) } }
                "items", "additionalProperties" -> (value as? JsonObject)?.let { scan(it, used, unknown) }
                "format" -> scanFormat(value, unknown)
                "pattern" -> scanPattern(value, unknown)
                "minLength", "maxLength", "maxItems" -> if (asLong(value) == null) flag(unknown, "$key:non_integer")
                "minimum", "maximum" -> if (asDouble(value) == null) flag(unknown, "$key:non_number")
            }
        }
    }

    private fun asLong(value: JsonElement?): Long? = (value as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun asDouble(value: JsonElement?): Double? = (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun scanFormat(value: JsonElement, unknown: MutableList<UnknownKeyword>) {
        val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (name != UUID_FORMAT) flag(unknown, "format:${name ?: "non_string"}")
    }

    private fun scanPattern(value: JsonElement, unknown: MutableList<UnknownKeyword>) {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        val compiles = text != null && try {
            Regex(text)
            true
        } catch (@Suppress("SwallowedException") e: PatternSyntaxException) {
            false
        }
        if (!compiles) flag(unknown, "pattern:invalid")
    }

    private fun typeMatches(type: String, value: JsonElement): Boolean = when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "string" -> value is JsonPrimitive && value.isString
        "number" -> value is JsonPrimitive && !value.isString && value.doubleOrNull != null
        "integer" -> value is JsonPrimitive && !value.isString && value.longOrNull != null
        "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
        else -> false
    }

    private fun check(schema: JsonObject, value: JsonElement): SchemaResult {
        val type = (schema["type"] as? JsonPrimitive)?.content
        if (type != null && !typeMatches(type, value)) return SchemaResult.Invalid(TYPE_MISMATCH)
        val allowed = schema["enum"] as? JsonArray
        if (allowed != null && value !in allowed) return SchemaResult.Invalid(NOT_IN_ENUM)
        if (value is JsonObject) {
            val failure = checkObject(schema, value)
            if (failure != SchemaResult.Valid) return failure
        }
        val bound = checkBounds(schema, value)
        if (bound != SchemaResult.Valid) return bound
        val items = schema["items"] as? JsonObject
        if (value is JsonArray && items != null && value.any { check(items, it) != SchemaResult.Valid }) {
            return SchemaResult.Invalid(ITEM_MISMATCH)
        }
        return SchemaResult.Valid
    }

    // Bounds, pattern and format apply to the value kinds they name; on any other kind they are no-ops (JSON Schema rule).
    private fun checkBounds(schema: JsonObject, value: JsonElement): SchemaResult {
        if (value !is JsonPrimitive) {
            val max = asLong(schema["maxItems"])
            return if (value is JsonArray && max != null && value.size > max) SchemaResult.Invalid(TOO_MANY_ITEMS) else SchemaResult.Valid
        }
        if (value.isString) return checkString(schema, value.content)
        val number = value.doubleOrNull ?: return SchemaResult.Valid
        val below = asDouble(schema["minimum"])?.let { number < it } ?: false
        val above = asDouble(schema["maximum"])?.let { number > it } ?: false
        return if (below || above) SchemaResult.Invalid(OUT_OF_RANGE) else SchemaResult.Valid
    }

    private fun checkString(schema: JsonObject, text: String): SchemaResult {
        val tooShort = asLong(schema["minLength"])?.let { text.length < it } ?: false
        val tooLong = asLong(schema["maxLength"])?.let { text.length > it } ?: false
        val pattern = (schema["pattern"] as? JsonPrimitive)?.content
        return when {
            tooShort || tooLong -> SchemaResult.Invalid(LENGTH)
            pattern != null && !Regex(pattern).containsMatchIn(text) -> SchemaResult.Invalid(PATTERN_MISMATCH)
            (schema["format"] as? JsonPrimitive)?.content == UUID_FORMAT && !uuidRegex.matches(text) ->
                SchemaResult.Invalid(FORMAT_MISMATCH)
            else -> SchemaResult.Valid
        }
    }

    private fun checkObject(schema: JsonObject, value: JsonObject): SchemaResult {
        val required = schema["required"] as? JsonArray
        if (required != null && required.any { (it as? JsonPrimitive)?.content !in value }) {
            return SchemaResult.Invalid(MISSING_REQUIRED)
        }
        val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        for ((name, sub) in properties) {
            val member = value[name] ?: continue
            val result = (sub as? JsonObject)?.let { check(it, member) } ?: SchemaResult.Valid
            if (result != SchemaResult.Valid) return result
        }
        val extras = value.filterKeys { it !in properties }
        return when (val additional = schema["additionalProperties"]) {
            is JsonPrimitive -> if (additional.booleanOrNull == false && extras.isNotEmpty()) {
                SchemaResult.Invalid(EXTRA_PROPERTY)
            } else {
                SchemaResult.Valid
            }
            is JsonObject -> extras.values.map { check(additional, it) }.firstOrNull { it != SchemaResult.Valid } ?: SchemaResult.Valid
            else -> SchemaResult.Valid
        }
    }
}
