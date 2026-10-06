package io.github.ygaray.voiceactionengine.spike.gold

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
    /**
     * RT-03 grading tolerance (a tolerance on how an answer is graded, never a relabel: the gold labels are unchanged).
     * `find_tags` does not fold plurals, so for the five find_tags items in [PLURAL_QUERY_ITEMS] a singular-stem tag query
     * scores as equally correct as the plural. List-item articles ("a scarf" vs "scarf") are acceptable either way for the
     * SB-envelope items (ids starting `b_`). Both sides are folded the same way, then compared by the strict rules above.
     * An item outside these sets (every small-envelope item) is graded strictly, exactly as before.
     */
    private val PLURAL_QUERY_ITEMS = setOf("b_en_041", "b_en_044", "b_es_041", "b_es_042", "b_es_044")
    private const val SB_ITEM_PREFIX = "b_"
    private const val PLURAL_MIN_LENGTH = 4
    private val LEADING_ARTICLE = Regex("^(a|an|the|el|la|los|las|un|una|unos|unas)\\s+")

    /**
     * True when every expected argument is present in [actual] with an equal value after normalization, under the RT-03
     * tolerance for [itemId] (null or an item outside the tolerance sets grades strictly, like the two-argument form).
     */
    fun argsMatch(expected: JsonObject, actual: JsonObject, itemId: String?): Boolean {
        if (itemId == null) return argsMatch(expected, actual)
        val plural = itemId in PLURAL_QUERY_ITEMS
        val articles = itemId.startsWith(SB_ITEM_PREFIX)
        if (!plural && !articles) return argsMatch(expected, actual)
        return argsMatch(fold(expected, plural, articles), fold(actual, plural, articles))
    }

    // Folds only the two places the tolerance covers: a `query` string (plural to singular stem) and the `text` of list
    // items inside an `items` array (a leading article dropped). Everything else passes through unchanged.
    private fun fold(obj: JsonObject, plural: Boolean, articles: Boolean): JsonObject = buildJsonObject {
        for ((key, value) in obj) {
            put(
                key,
                when {
                    plural && key == "query" && value is JsonPrimitive && value.isString ->
                        JsonPrimitive(singularStem(value.content))
                    articles && key == "items" && value is JsonArray -> foldItems(value)
                    else -> value
                },
            )
        }
    }

    private fun foldItems(items: JsonArray): JsonArray = buildJsonArray {
        for (item in items) {
            add(
                when {
                    item is JsonObject -> foldItemText(item)
                    item is JsonPrimitive && item.isString -> JsonPrimitive(dropArticle(item.content))
                    else -> item
                },
            )
        }
    }

    private fun foldItemText(item: JsonObject): JsonObject = buildJsonObject {
        for ((key, value) in item) {
            val isText = key == "text" && value is JsonPrimitive && value.isString
            put(key, if (isText) JsonPrimitive(dropArticle((value as JsonPrimitive).content)) else value)
        }
    }

    private fun dropArticle(text: String): String = LEADING_ARTICLE.replace(normalize(text), "")

    // One trailing "s" is the whole plural rule (movies/movie, recipes/recipe, películas/película, viajes/viaje); a word of
    // three letters or fewer is left alone.
    private fun singularStem(text: String): String {
        val normalized = normalize(text)
        return if (normalized.length >= PLURAL_MIN_LENGTH && normalized.endsWith("s")) normalized.dropLast(1) else normalized
    }

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
