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
 * the schema's type). Arrays compare as sets: the same size, and every expected element matched by a different predicted
 * element, in any order. Objects compare by their expected keys at every depth, so an extra predicted key (an optional
 * argument, or a defaulted flag inside a list item) is ignored, while a missing or different expected key is a miss.
 */
internal object GoldMatcher {
    /** Trim, Unicode NFC, lower case with the root locale, and every run of whitespace collapsed to one space. */
    fun normalize(text: String): String =
        WHITESPACE.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT), " ")

    /** True when a tool was expected and the model called exactly that tool. */
    fun toolMatch(expectedTool: String?, actualTool: String?): Boolean =
        expectedTool != null && expectedTool == actualTool

    /** True when every expected argument is present in [actual] with an equal value after normalization. */
    fun argsMatch(expected: JsonObject, actual: JsonObject): Boolean = objectMatch(expected, actual)

    private fun objectMatch(expected: JsonObject, actual: JsonObject): Boolean =
        expected.all { (key, value) -> actual[key]?.let { valueMatch(value, it) } ?: false }

    private fun valueMatch(expected: JsonElement, actual: JsonElement): Boolean = when {
        expected is JsonObject -> actual is JsonObject && objectMatch(expected, actual)
        expected is JsonArray -> actual is JsonArray && arrayMatch(expected, actual)
        expected is JsonNull -> actual is JsonNull
        expected is JsonPrimitive -> actual is JsonPrimitive && primitiveKey(expected) == primitiveKey(actual)
        else -> false
    }

    // A set match: every expected element gets a distinct predicted element that matches it. Arrays here are a handful of
    // elements, so a plain backtracking search is enough and always exact.
    private fun arrayMatch(expected: JsonArray, actual: JsonArray): Boolean =
        expected.size == actual.size && assign(expected, actual, 0, BooleanArray(actual.size))

    private fun assign(expected: JsonArray, actual: JsonArray, index: Int, used: BooleanArray): Boolean {
        if (index == expected.size) return true
        for (candidate in actual.indices) {
            if (used[candidate] || !valueMatch(expected[index], actual[candidate])) continue
            used[candidate] = true
            if (assign(expected, actual, index + 1, used)) return true
            used[candidate] = false
        }
        return false
    }

    // The prefixes keep the string "2" apart from the number 2 and from the boolean-like tokens.
    private fun primitiveKey(primitive: JsonPrimitive): String {
        if (primitive.isString) return "s:" + normalize(primitive.content)
        val number = primitive.content.toBigDecimalOrNull()
        return if (number != null) "n:" + number.stripTrailingZeros().toPlainString() else "b:" + primitive.content
    }
}
