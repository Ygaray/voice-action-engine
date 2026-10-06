package io.github.ygaray.voiceactionengine.core.strategy.grammar

import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.serialization.json.JsonObject
import java.text.Normalizer

private const val EN = "en"
private const val ES = "es"
private const val SYNTAX_CHARACTERS = "[](){}<>|"

private val whitespaceRun = Regex("""\s+""")

/** NFC, lower case, split on whitespace; the one fold both declared phrasings and transcripts go through. */
private fun foldWords(text: String): List<String> =
    Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().split(whitespaceRun).filter { it.isNotEmpty() }

internal class IntentSpec(val toolName: String, val en: List<String>, val es: List<String>)

private class Rule(val toolName: String, val language: String, val templateIndex: Int) {
    val id: String get() = "$toolName:$language:$templateIndex"
}

/** What a pack decided for one transcript. Internal: the public surface is [GrammarPack.match]. */
internal sealed class GrammarResult {
    /** A phrasing matched. */
    class Matched(val match: GrammarMatch) : GrammarResult()

    /** Nothing matched, or the match was refused; [code] says why when there is a trace code for it. */
    class Rejected(val code: TraceCode?) : GrammarResult()
}

/**
 * The phrasings an app declares for its tools, in English and Spanish, and a pure matcher over them.
 *
 * A pack holds one intent per tool. An intent lists the phrasings (templates) that mean the tool, per language. A
 * transcript matches only when its whole word sequence equals a declared phrasing: never a prefix, never a part of a
 * longer sentence, never a best guess. Text is compared after Unicode normalization and lower-casing.
 *
 * Build one with `GrammarPack { intent("tool") { en("..."); es("...") } }`. A phrasing here is a plain sequence of
 * words; a phrasing that uses any of the characters `[ ] ( ) { } < > |` is refused when the pack is built.
 *
 * The pack is immutable once built, so it is safe to share between commands and threads.
 */
public class GrammarPack internal constructor(settings: Builder) {
    private val index: Map<String, Map<List<String>, Rule>>
    private val intentCount: Int

    init {
        val intents = settings.intents.toList()
        validate(intents)
        intentCount = intents.size
        index = mapOf(EN to rulesFor(EN, intents) { it.en }, ES to rulesFor(ES, intents) { it.es })
    }

    /**
     * Matches [transcript] against the declared phrasings, for the command's [language] label: "en" tries the English
     * phrasings, "es" the Spanish ones, and null tries both and matches only when exactly one language matches or both
     * match the same tool. Any other label matches nothing.
     *
     * Returns the match, or null when nothing matches, the match is not unambiguous or the label is not supported. It
     * never throws for any transcript, never logs, and has no effect.
     */
    public fun match(transcript: String, language: String?): GrammarMatch? =
        (matchDetailed(transcript, language) as? GrammarResult.Matched)?.match

    internal fun matchDetailed(transcript: String, language: String?): GrammarResult {
        val words = foldWords(transcript)
        return when {
            language == null -> matchBoth(words)
            language == EN || language == ES -> matchOne(language, words)
            else -> GrammarResult.Rejected(null)
        }
    }

    private fun matchOne(language: String, words: List<String>): GrammarResult =
        index[language]?.get(words)?.let { GrammarResult.Matched(matchOf(it, language)) }
            ?: GrammarResult.Rejected(null)

    private fun matchBoth(words: List<String>): GrammarResult {
        val en = index[EN]?.get(words)
        val es = index[ES]?.get(words)
        return when {
            en != null && es != null -> agreed(en, es)
            en != null -> GrammarResult.Matched(matchOf(en, EN))
            es != null -> GrammarResult.Matched(matchOf(es, ES))
            else -> GrammarResult.Rejected(null)
        }
    }

    // Both languages produce the same words: the same tool is one reading, different tools are never guessed between.
    private fun agreed(en: Rule, es: Rule): GrammarResult =
        if (en.toolName == es.toolName) {
            GrammarResult.Matched(GrammarMatch(en.toolName, JsonObject(emptyMap()), null, false, null))
        } else {
            GrammarResult.Rejected(null)
        }

    private fun matchOf(rule: Rule, language: String): GrammarMatch =
        GrammarMatch(rule.toolName, JsonObject(emptyMap()), language, false, rule.id)

    /** Prints the number of intents and phrasings per language only. */
    override fun toString(): String =
        "GrammarPack(intents=$intentCount, enRules=${index[EN]?.size}, esRules=${index[ES]?.size})"

    private fun validate(intents: List<IntentSpec>) {
        val names = HashSet<String>()
        for (intent in intents) {
            require(intent.toolName.isNotBlank()) { "GrammarPack: an intent's tool name must not be blank" }
            require(names.add(intent.toolName)) { "GrammarPack: tool ${intent.toolName} is declared twice" }
            require(intent.en.isNotEmpty() || intent.es.isNotEmpty()) {
                "GrammarPack: tool ${intent.toolName} declares no phrasing"
            }
        }
    }

    private fun rulesFor(
        language: String,
        intents: List<IntentSpec>,
        templatesOf: (IntentSpec) -> List<String>,
    ): Map<List<String>, Rule> {
        val rules = LinkedHashMap<List<String>, Rule>()
        for (intent in intents) {
            templatesOf(intent).forEachIndexed { position, template ->
                val words = wordsOf(intent.toolName, language, template)
                val existing = rules[words]
                if (existing == null) {
                    rules[words] = Rule(intent.toolName, language, position)
                } else {
                    // The same phrasing twice for one tool collapses to one rule; for two tools it would be a guess.
                    require(existing.toolName == intent.toolName) {
                        "GrammarPack: tools ${existing.toolName} and ${intent.toolName} declare the same " +
                            "$language phrasing: $template"
                    }
                }
            }
        }
        return rules
    }

    private fun wordsOf(toolName: String, language: String, template: String): List<String> {
        require(template.isNotBlank()) { "GrammarPack: tool $toolName declares a blank $language phrasing" }
        require(template.none { it in SYNTAX_CHARACTERS }) {
            "GrammarPack: tool $toolName $language phrasing uses template syntax that is not supported yet: $template"
        }
        return foldWords(template)
    }

    /** Collects the intents of one [GrammarPack]. */
    public class Builder internal constructor() {
        internal val intents: MutableList<IntentSpec> = mutableListOf()

        /**
         * Declares the phrasings that mean the app's tool [toolName], from [block].
         *
         * Checked when the pack is built: the name must not be blank or declared twice, the intent needs at least one
         * phrasing, and two tools may not declare the same phrasing in one language.
         */
        public fun intent(toolName: String, block: IntentBuilder.() -> Unit) {
            val settings = IntentBuilder().apply(block)
            intents.add(IntentSpec(toolName, settings.en.toList(), settings.es.toList()))
        }
    }

    /** Collects the phrasings of one intent. */
    public class IntentBuilder internal constructor() {
        internal val en: MutableList<String> = mutableListOf()
        internal val es: MutableList<String> = mutableListOf()

        /** Adds English phrasings. */
        public fun en(vararg templates: String) {
            en.addAll(templates)
        }

        /** Adds Spanish phrasings. */
        public fun es(vararg templates: String) {
            es.addAll(templates)
        }
    }

    /** Ways to create a pack. */
    public companion object {
        /**
         * Builds a pack from [block].
         *
         * @throws IllegalArgumentException naming the tool and phrasing when an intent is blank, declared twice, has
         * no phrasing, declares a blank or unsupported phrasing, or when two tools declare the same phrasing in one
         * language.
         */
        public operator fun invoke(block: Builder.() -> Unit): GrammarPack = GrammarPack(Builder().apply(block))
    }
}
