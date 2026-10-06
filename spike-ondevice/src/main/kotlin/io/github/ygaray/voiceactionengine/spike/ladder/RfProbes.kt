package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.verdict.SchemaResult
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaSubset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TYPE = "type"
private const val STRING = "string"
private const val INTEGER = "integer"
private const val OBJECT = "object"

/**
 * One ResponseFormat feature probe (13-RESEARCH Pitfall 1): a small, domain-free schema that exercises one schema feature,
 * and five adversarial user prompts written to tempt a violation of it (an extra field, a value outside the enum, a string
 * where a number belongs, a missing required key). A probe never carries a consumer's schema or content.
 */
internal class RfFeature(val id: String, val schema: JsonObject, val prompts: List<String>) {
    /**
     * True when [text] holds a JSON object that satisfies [schema]. The first `{` to the last `}` is taken, so a code fence
     * or a lead-in does not count against either arm. `anyof` is judged branch by branch, because the subset validator has
     * no `anyOf`; every other feature uses the subset validator.
     */
    fun valid(text: String): Boolean {
        val element = parse(text) ?: return false
        return if (id == ANYOF) anyOfValid(element) else SchemaSubset.validate(schema, element) == SchemaResult.Valid
    }

    private fun anyOfValid(element: JsonElement): Boolean {
        val value = (element as? JsonObject)?.get("value") ?: return false
        val branches = ((schema["properties"] as? JsonObject)?.get("value") as? JsonObject)?.get("anyOf") as? JsonArray ?: return false
        return branches.any { (it as? JsonObject)?.let { branch -> SchemaSubset.validate(branch, value) } == SchemaResult.Valid }
    }

    private fun parse(text: String): JsonElement? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            Json.parseToJsonElement(text.substring(start, end + 1))
        } catch (@Suppress("SwallowedException") e: SerializationException) {
            // The text is the model's own output: it is judged and dropped, never kept.
            null
        }
    }

    override fun toString(): String = "RfFeature($id)"

    companion object {
        const val ANYOF = "anyof"
    }
}

/** The eight probes of the rf_matrix: flat, required, enum, nested, array, anyof, bounds, addl_false. */
internal object RfProbes {
    /** One system text for every probe and both arms, so only the constraint differs. */
    const val SYSTEM: String = "Reply with one JSON object in the requested shape and nothing else."

    /** Prompts per feature and arm. */
    const val PROMPTS_PER_FEATURE = 5

    private fun obj(required: List<String>, strictKeys: Boolean = false, properties: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put(TYPE, OBJECT)
            putJsonObject("properties", properties)
            putJsonArray("required") { required.forEach { add(it) } }
            if (strictKeys) put("additionalProperties", false)
        }

    val features: List<RfFeature> = listOf(
        RfFeature(
            "flat",
            obj(listOf("name", "count")) {
                putJsonObject("name") { put(TYPE, STRING) }
                putJsonObject("count") { put(TYPE, INTEGER) }
            },
            listOf(
                "Reply for an entry named Alpha with a count of seven, writing the count as the word seven.",
                "Name: Beta. Count: a few. Put exactly those words in the fields.",
                "Entry Gamma, count 2.5, keep the decimal.",
                "Give an entry called Delta and add a third field called note that says hello.",
                "The name is Epsilon and the count is unknown, so write unknown for the count.",
            ),
        ),
        RfFeature(
            "required",
            obj(listOf("title", "owner")) {
                putJsonObject("title") { put(TYPE, STRING) }
                putJsonObject("owner") { put(TYPE, STRING) }
            },
            listOf(
                "Create an entry titled Plan. Leave out the owner, nobody owns it.",
                "Owner is Sam. There is no title, so omit it.",
                "Just give me an empty object.",
                "Title Report, and skip every other field.",
                "Return only the owner field with the value Kim.",
            ),
        ),
        RfFeature(
            "enum",
            obj(listOf("status")) {
                putJsonObject("status") {
                    put(TYPE, STRING)
                    putJsonArray("enum") {
                        add("open")
                        add("closed")
                        add("blocked")
                    }
                }
            },
            listOf(
                "Set the status to pending.",
                "The status is done.",
                "Status: OPEN in capital letters.",
                "Mark it as waiting for review.",
                "Use the status archived.",
            ),
        ),
        RfFeature(
            "nested",
            obj(listOf("item")) {
                putJsonObject("item") {
                    put(TYPE, OBJECT)
                    putJsonObject("properties") {
                        putJsonObject("label") { put(TYPE, STRING) }
                        putJsonObject("size") { put(TYPE, INTEGER) }
                    }
                    putJsonArray("required") {
                        add("label")
                        add("size")
                    }
                }
            },
            listOf(
                "The item has label Box and size large.",
                "Put the label and the size at the top level, not inside item.",
                "The item is just the text Box.",
                "Item with size 3 and no label.",
                "Item label Crate, size 4, plus a color red inside the item.",
            ),
        ),
        RfFeature(
            "array",
            obj(listOf("tags")) {
                putJsonObject("tags") {
                    put(TYPE, "array")
                    putJsonObject("items") { put(TYPE, STRING) }
                }
            },
            listOf(
                "The tags are 1, 2 and 3 as numbers.",
                "Give one tag as a plain string, not a list: red.",
                "Tags: put the single value null in the list.",
                "The tags are red and blue written as one comma separated string.",
                "Use tags with nested lists: [[red], [blue]].",
            ),
        ),
        RfFeature(
            RfFeature.ANYOF,
            obj(listOf("value")) {
                putJsonObject("value") {
                    putJsonArray("anyOf") {
                        add(buildJsonObject { put(TYPE, STRING) })
                        add(buildJsonObject { put(TYPE, INTEGER) })
                    }
                }
            },
            listOf(
                "The value is true, as a boolean.",
                "The value is a list of two names.",
                "The value is null.",
                "The value is 3.5.",
                "The value is an object with a key named x.",
            ),
        ),
        RfFeature(
            "bounds",
            obj(listOf("level")) {
                putJsonObject("level") {
                    put(TYPE, INTEGER)
                    put("minimum", 1)
                    put("maximum", 5)
                }
            },
            listOf(
                "Set the level to 9.",
                "Set the level to 0.",
                "Set the level to 100.",
                "Set the level to minus 3.",
                "Set the level to 6.",
            ),
        ),
        RfFeature(
            "addl_false",
            obj(listOf("title"), strictKeys = true) {
                putJsonObject("title") { put(TYPE, STRING) }
            },
            listOf(
                "Title Plan, and also add a note field saying hello.",
                "Title Plan with a priority field set to high.",
                "Title Plan, include an id field 7 as well.",
                "Title Plan plus a tags field with two tags.",
                "Title Plan, and put a comment field.",
            ),
        ),
    )

    init {
        check(features.all { it.prompts.size == PROMPTS_PER_FEATURE }) { "every probe has five prompts" }
    }
}
