package io.github.ygaray.voiceactionengine.spike.gold

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.util.Locale

private val WHITESPACE = Regex("\\s+")

/**
 * Compares what the model proposed with a gold label (D-06).
 *
 * Strings are normalized (trim, Unicode NFC, lower case with the root locale, runs of whitespace collapsed). Numbers compare
 * by value, so `2` equals `2.0`, and a number never equals its string form (a model that quotes a number has not produced
 * the schema's type). Arrays compare as sets of normalized elements. At the top level every expected key must be present
 * and equal, and an extra predicted key is ignored (an extra optional argument is not an error). A nested object compares
 * exactly.
 */
internal object GoldMatcher {
    /** Trim, Unicode NFC, lower case with the root locale, and every run of whitespace collapsed to one space. */
    fun normalize(text: String): String =
        WHITESPACE.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT), " ")

    /** True when a tool was expected and the model called exactly that tool. */
    fun toolMatch(expectedTool: String?, actualTool: String?): Boolean =
        expectedTool != null && expectedTool == actualTool

    /** True when every expected argument is present in [actual] with an equal value after normalization. */
    fun argsMatch(expected: JsonObject, actual: JsonObject): Boolean =
        expected.all { (key, value) -> actual[key]?.let { canon(value) == canon(it) } ?: false }

    // A canonical text of a value, so equal values (after normalization) have the same text. The prefixes keep a string
    // "2" apart from the number 2.
    private fun canon(element: JsonElement): String = when (element) {
        is JsonNull -> "null"
        is JsonPrimitive -> canonPrimitive(element)
        is JsonArray -> element.map(::canon).toSortedSet().joinToString(",", "[", "]")
        is JsonObject -> element.entries.sortedBy { it.key }
            .joinToString(",", "{", "}") { (key, value) -> "$key=${canon(value)}" }
    }

    private fun canonPrimitive(primitive: JsonPrimitive): String {
        if (primitive.isString) return "s:" + normalize(primitive.content)
        val number = primitive.content.toBigDecimalOrNull()
        return if (number != null) "n:" + number.stripTrailingZeros().toPlainString() else "b:" + primitive.content
    }
}
