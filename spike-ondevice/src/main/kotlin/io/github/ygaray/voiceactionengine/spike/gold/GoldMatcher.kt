package io.github.ygaray.voiceactionengine.spike.gold

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.text.Normalizer
import java.util.Locale

private val WHITESPACE = Regex("\\s+")

/** Compares what the model proposed with a gold label. */
internal object GoldMatcher {
    /** Trim, Unicode NFC, lower case with the root locale, and every run of whitespace collapsed to one space. */
    fun normalize(text: String): String =
        WHITESPACE.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT), " ")

    /** True when a tool was expected and the model called exactly that tool. */
    fun toolMatch(expectedTool: String?, actualTool: String?): Boolean =
        expectedTool != null && expectedTool == actualTool

    /** True when every expected argument is present in [actual] with a normalized-equal value. */
    fun argsMatch(expected: JsonObject, actual: JsonObject): Boolean =
        expected.all { (key, value) -> actual[key]?.let { valueMatch(value, it) } ?: false }

    private fun valueMatch(expected: JsonElement, actual: JsonElement): Boolean =
        normalize(expected.toString()) == normalize(actual.toString())
}
