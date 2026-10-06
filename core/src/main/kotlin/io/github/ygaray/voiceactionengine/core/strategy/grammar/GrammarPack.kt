package io.github.ygaray.voiceactionengine.core.strategy.grammar

import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.serialization.json.JsonObject

private const val EN = "en"
private const val ES = "es"

internal class IntentSpec(val toolName: String, val en: List<String>, val es: List<String>)

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
 * longer sentence, never a best guess.
 *
 * Text is folded the same way for the transcript and for every declared word: Unicode NFC, lower case (not tied to the
 * device locale), the vowel accents `á é í ó ú` (and their grave, circumflex and diaeresis forms) folded to the plain
 * vowel, `ñ` kept so `año` and `ano` stay different words, apostrophes ignored (`don't` is `dont`), a hyphen between
 * two letters read as a space (`twenty-one` is two words, `5-10` and `-5` stay one), and the punctuation
 * `. , ; : ! ? ¿ ¡ " “ ” ‘ ’ « » ( ) …` ignored at the edges of a word. A phrasing written with accents therefore
 * matches a transcript without them and the other way round. A sentence terminator (`. ! ? ;`) between two words
 * means the transcript holds more than one command, so it matches nothing.
 *
 * Build one with `GrammarPack { intent("tool") { en("..."); es("...") } }`. A phrasing is written from these parts, in
 * either language, and no regular expression is involved:
 *
 * - words, matched one for one after the fold above;
 * - `[the]`, an optional part: zero or one of its alternatives (`[the|a]`);
 * - `(turn|switch)`, a group: exactly one of its alternatives;
 * - `<name>`, a named sub-rule of the same language declared with [Builder.enRule] or [Builder.esRule]; sub-rules may
 *   refer to other sub-rules (never to themselves) and may not hold slots.
 *
 * Every phrasing needs at least one literal word on every path, so an optional-only phrasing is refused. A phrasing
 * that expands to more than 256 sequences is refused too: split it with sub-rules. Anything wrong in the declarations
 * (bad syntax, an unknown or looping rule, two tools reading the same words in one language) throws when the pack is
 * built, never when a transcript is matched.
 *
 * The pack is immutable once built, so it is safe to share between commands and threads.
 */
public class GrammarPack internal constructor(settings: Builder) {
    private val matchers: Map<String, RuleMatcher>
    private val intentCount: Int

    init {
        val intents = settings.intents.toList()
        validate(intents)
        intentCount = intents.size
        matchers = mapOf(
            EN to RuleMatcher(compileLanguage(EN, intents, settings.enRules.toList()) { it.en }),
            ES to RuleMatcher(compileLanguage(ES, intents, settings.esRules.toList()) { it.es }),
        )
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
        val tokens = tokenize(transcript)
        val keys = tokens.tokens.map { it.key }
        return when {
            tokens.clauseBreak -> GrammarResult.Rejected(null)
            language == null -> matchBoth(keys)
            language == EN || language == ES -> matchOne(language, keys)
            else -> GrammarResult.Rejected(null)
        }
    }

    private fun matchOne(language: String, keys: List<String>): GrammarResult =
        when (val verdict = verdictOf(language, keys)) {
            is RuleVerdict.One -> GrammarResult.Matched(matchOf(verdict.rule, language))
            is RuleVerdict.Ambiguous -> GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
            is RuleVerdict.None -> GrammarResult.Rejected(null)
        }

    private fun matchBoth(keys: List<String>): GrammarResult {
        val en = verdictOf(EN, keys)
        val es = verdictOf(ES, keys)
        return when {
            en is RuleVerdict.Ambiguous || es is RuleVerdict.Ambiguous ->
                GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
            en is RuleVerdict.One && es is RuleVerdict.One -> agreed(en.rule, es.rule)
            en is RuleVerdict.One -> GrammarResult.Matched(matchOf(en.rule, EN))
            es is RuleVerdict.One -> GrammarResult.Matched(matchOf(es.rule, ES))
            else -> GrammarResult.Rejected(null)
        }
    }

    private fun verdictOf(language: String, keys: List<String>): RuleVerdict =
        matchers[language]?.match(keys) ?: RuleVerdict.None()

    // Both languages produce the same words: the same tool is one reading, different tools are never guessed between.
    private fun agreed(en: FlatRule, es: FlatRule): GrammarResult =
        if (en.toolName == es.toolName) {
            GrammarResult.Matched(GrammarMatch(en.toolName, JsonObject(emptyMap()), null, false, null))
        } else {
            GrammarResult.Rejected(null)
        }

    private fun matchOf(rule: FlatRule, language: String): GrammarMatch =
        GrammarMatch(rule.toolName, JsonObject(emptyMap()), language, false, rule.ruleId)

    /** Prints the number of intents and phrasings per language only. */
    override fun toString(): String =
        "GrammarPack(intents=$intentCount, enRules=${matchers[EN]?.size}, esRules=${matchers[ES]?.size})"

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

    /** Collects the intents and sub-rules of one [GrammarPack]. */
    public class Builder internal constructor() {
        internal val intents: MutableList<IntentSpec> = mutableListOf()
        internal val enRules: MutableList<RuleSpec> = mutableListOf()
        internal val esRules: MutableList<RuleSpec> = mutableListOf()

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

        /**
         * Declares the English sub-rule [name], written `<name>` in the English phrasings of any intent. Each of
         * [templates] is one way to say it, so `enRule("article", "the", "a")` is the same as `(the|a)`. The name is
         * lower case letters, digits and underscores, starting with a letter, and may be declared once per language.
         * A sub-rule may refer to other sub-rules but not to itself, and may not hold slots.
         */
        public fun enRule(name: String, vararg templates: String) {
            enRules.add(RuleSpec(name, templates.toList()))
        }

        /** Declares the Spanish sub-rule [name]; the same as [enRule] for the Spanish phrasings. */
        public fun esRule(name: String, vararg templates: String) {
            esRules.add(RuleSpec(name, templates.toList()))
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
         * no phrasing, declares a blank or malformed phrasing, refers to an unknown or looping sub-rule, or when two
         * tools declare the same phrasing in one language.
         */
        public operator fun invoke(block: Builder.() -> Unit): GrammarPack = GrammarPack(Builder().apply(block))
    }
}
