package io.github.ygaray.voiceactionengine.core.strategy.grammar

import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

private const val EN = "en"
private const val ES = "es"

internal class IntentSpec(
    val toolName: String,
    val en: List<String>,
    val es: List<String>,
    val slots: List<SlotDecl>,
)

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
        validateIntents(intents)
        intentCount = intents.size
        val enFillers = foldFillers(EN, settings.enFillers.toList())
        val esFillers = foldFillers(ES, settings.esFillers.toList())
        val slots = intents.associate { intent -> intent.toolName to intent.slots.associate { it.name to it.spec } }
        matchers = mapOf(
            EN to RuleMatcher(
                compileLanguage(EN, intents, settings.enRules.toList(), enFillers) { it.en }, enFillers, slots,
            ),
            ES to RuleMatcher(
                compileLanguage(ES, intents, settings.esRules.toList(), esFillers) { it.es }, esFillers, slots,
            ),
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
        return when {
            tokens.clauseBreak -> GrammarResult.Rejected(null)
            language == null -> matchBoth(tokens)
            language == EN || language == ES -> matchOne(language, tokens)
            else -> GrammarResult.Rejected(null)
        }
    }

    private fun matchOne(language: String, tokens: GrammarTokens): GrammarResult =
        when (val verdict = verdictOf(language, tokens)) {
            is RuleVerdict.One -> GrammarResult.Matched(matchOf(verdict, language))
            is RuleVerdict.Ambiguous -> GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
            is RuleVerdict.None -> GrammarResult.Rejected(null)
        }

    private fun matchBoth(tokens: GrammarTokens): GrammarResult {
        val en = verdictOf(EN, tokens)
        val es = verdictOf(ES, tokens)
        return when {
            en is RuleVerdict.Ambiguous || es is RuleVerdict.Ambiguous ->
                GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
            en is RuleVerdict.One && es is RuleVerdict.One -> agreed(en, es)
            en is RuleVerdict.One -> GrammarResult.Matched(matchOf(en, EN))
            es is RuleVerdict.One -> GrammarResult.Matched(matchOf(es, ES))
            else -> GrammarResult.Rejected(null)
        }
    }

    private fun verdictOf(language: String, tokens: GrammarTokens): RuleVerdict =
        matchers[language]?.match(tokens) ?: RuleVerdict.None()

    // Both languages read the transcript: the same tool with equal arguments is one reading, a different tool is never
    // guessed between, and the same tool with different arguments is two readings.
    private fun agreed(en: RuleVerdict.One, es: RuleVerdict.One): GrammarResult = when {
        en.rule.toolName != es.rule.toolName -> GrammarResult.Rejected(null)
        en.arguments != es.arguments -> GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
        else -> GrammarResult.Matched(GrammarMatch(en.rule.toolName, en.arguments, null, false, null))
    }

    private fun matchOf(verdict: RuleVerdict.One, language: String): GrammarMatch =
        GrammarMatch(verdict.rule.toolName, verdict.arguments, language, false, verdict.rule.ruleId)

    /** Prints the number of intents and phrasings per language only. */
    override fun toString(): String =
        "GrammarPack(intents=$intentCount, enRules=${matchers[EN]?.size}, esRules=${matchers[ES]?.size})"

    /** Collects the intents and sub-rules of one [GrammarPack]. */
    public class Builder internal constructor() {
        internal val intents: MutableList<IntentSpec> = mutableListOf()
        internal val enRules: MutableList<RuleSpec> = mutableListOf()
        internal val esRules: MutableList<RuleSpec> = mutableListOf()
        internal val enFillers: MutableList<String> = mutableListOf()
        internal val esFillers: MutableList<String> = mutableListOf()

        /**
         * Declares the phrasings that mean the app's tool [toolName], from [block].
         *
         * Checked when the pack is built: the name must not be blank or declared twice, the intent needs at least one
         * phrasing, and two tools may not declare the same phrasing in one language.
         */
        public fun intent(toolName: String, block: IntentBuilder.() -> Unit) {
            val settings = IntentBuilder().apply(block)
            intents.add(IntentSpec(toolName, settings.en.toList(), settings.es.toList(), settings.slots.toList()))
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

        /**
         * Declares English filler [phrases] such as "please" or "could you". A transcript may carry them at its start
         * and its end, and they are stripped before matching: longest first, and repeatedly, so "please could you turn
         * on the light please" loses all three. Only the edges are touched; a filler in the middle of a transcript
         * stays and stops the match, so write an interior optional word as `[..]` in the phrasing instead.
         *
         * The engine ships no filler: a pack with none strips nothing. A filler is folded like any other text, and a
         * blank one is refused when the pack is built, as is a phrasing that begins or ends with one of the fillers
         * (stripping would remove a word the phrasing needs).
         */
        public fun enFillers(vararg phrases: String) {
            enFillers.addAll(phrases)
        }

        /** Declares Spanish filler [phrases] such as "por favor"; the same as [enFillers] for Spanish. */
        public fun esFillers(vararg phrases: String) {
            esFillers.addAll(phrases)
        }
    }

    /** Collects the phrasings of one intent. */
    public class IntentBuilder internal constructor() {
        internal val en: MutableList<String> = mutableListOf()
        internal val es: MutableList<String> = mutableListOf()
        internal val slots: MutableList<SlotDecl> = mutableListOf()

        /**
         * Declares the whole-number slot [name], written `{name}` in this intent's phrasings in either language, with
         * a value from [min] to [max]. The speaker may say it in digits or in words, read by the rules of the language
         * of the phrasing that matched (`twenty one` or `1,000` in English, `veintiuno` in Spanish, where a comma
         * group is not a number), and the match carries it as a JSON number with a whole value. A value outside the
         * range is not a candidate, so the phrasing does not match. Declared once per intent and shared by its
         * English and Spanish phrasings.
         *
         * The bounds must satisfy `0 <= min <= max <= 999,999`; checked when the pack is built.
         */
        public fun integer(name: String, min: Long, max: Long) {
            slots.add(SlotDecl(name, SlotSpec.IntegerSlot(min, max)))
        }

        /**
         * Declares the decimal slot [name] with a value from [min] to [max]. Whole numbers, spoken fractions (`two and
         * a half`, `dos y medio`) and digits with the language's decimal separator are read in the language of the
         * phrasing that matched, and the match always carries a JSON number with a fractional type, so `2` and `2.0`
         * never differ between languages. A digit group that could mean a thousands separator in Spanish (`1.000`) is
         * not a number there.
         *
         * The bounds must be finite and satisfy `0 <= min <= max <= 999,999`; checked when the pack is built.
         */
        public fun decimal(name: String, min: Double, max: Double) {
            slots.add(SlotDecl(name, SlotSpec.DecimalSlot(min, max)))
        }

        /**
         * Declares the closed-list slot [name]: [block] lists the options, each with the ways to say it in English and
         * in Spanish. The transcript must say one of the synonyms of the language of the phrasing that matched
         * (folded like any transcript: case and accents ignored, a synonym may be several words), and the match
         * carries the option's id as a JSON string. Two options may not share a synonym in one language.
         *
         * Every option needs at least one synonym in each language the intent has phrasings for; checked when the
         * pack is built.
         */
        public fun choice(name: String, block: ChoiceBuilder.() -> Unit) {
            slots.add(SlotDecl(name, SlotSpec.ChoiceSlot(ChoiceBuilder().apply(block).options.toList())))
        }

        /**
         * Declares the bounded free-text slot [name] of one to [maxWords] words. The words are whatever the speaker
         * said up to the next literal word of the phrasing or the end of the transcript, never across a sentence
         * break, and the match carries them as a JSON string exactly as spoken: case, accents and inner punctuation
         * kept. [maxWords] must be at least 1; checked when the pack is built.
         */
        public fun text(name: String, maxWords: Int) {
            slots.add(SlotDecl(name, SlotSpec.TextSlot(maxWords)))
        }

        /** Adds English phrasings. */
        public fun en(vararg templates: String) {
            en.addAll(templates)
        }

        /** Adds Spanish phrasings. */
        public fun es(vararg templates: String) {
            es.addAll(templates)
        }
    }

    /** Collects the options of one choice slot. */
    public class ChoiceBuilder internal constructor() {
        internal val options: MutableList<ChoiceOption> = mutableListOf()

        /** Adds the option [id], the value a match carries, with the synonyms [block] declares. */
        public fun option(id: String, block: OptionBuilder.() -> Unit) {
            val settings = OptionBuilder().apply(block)
            options.add(ChoiceOption(id, settings.en.toList(), settings.es.toList()))
        }
    }

    /** Collects the synonyms of one choice option. */
    public class OptionBuilder internal constructor() {
        internal val en: MutableList<String> = mutableListOf()
        internal val es: MutableList<String> = mutableListOf()

        /** Adds English synonyms. */
        public fun en(vararg synonyms: String) {
            en.addAll(synonyms)
        }

        /** Adds Spanish synonyms. */
        public fun es(vararg synonyms: String) {
            es.addAll(synonyms)
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
