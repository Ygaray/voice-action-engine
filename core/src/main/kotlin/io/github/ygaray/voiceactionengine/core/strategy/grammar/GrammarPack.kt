package io.github.ygaray.voiceactionengine.core.strategy.grammar

import io.github.ygaray.voiceactionengine.core.internal.guardedPlain
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val EN = "en"
private const val ES = "es"

internal class IntentSpec(
    val toolName: String,
    val en: List<String>,
    val es: List<String>,
    val slots: List<SlotDecl>,
    val terminal: Boolean,
    val normalizers: List<NormalizerDecl>,
)

/** The app's hook for one slot: it sees the raw words and the matched pack's language, and answers the value to use. */
internal class NormalizerDecl(val slot: String, val hook: (String, String) -> String?)

// What one hook call came to: the value to use, or the code that refuses the match. A fault never keeps the exception.
private class HookAnswer(val value: String?, val refusal: TraceCode?)

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
 * - `{name}`, a slot the intent declares once with [IntentBuilder.integer], [IntentBuilder.decimal],
 *   [IntentBuilder.choice] or [IntentBuilder.text]; the same declaration serves its English and Spanish phrasings, so
 *   both languages bind the same slots and a match carries equal typed arguments whichever language was spoken. A slot
 *   written only inside `[ ]` is optional and its key is left out when it is not spoken.
 *
 * The command's language label picks the phrasings read: "en" the English ones, "es" the Spanish ones (both with
 * [Builder.tryOtherLanguage]), no label both, anything else none. When two languages match they must agree on the tool
 * and the arguments. The pack never guesses: two readings of one transcript, in one language or across the two, match
 * nothing, and so does a transcript with more words (fillers aside) than the longest phrasing can span.
 *
 * Every phrasing needs at least one literal word on every path, so an optional-only phrasing is refused. A phrasing
 * that expands to more than 256 sequences is refused too: split it with sub-rules. Anything wrong in the declarations
 * (bad syntax, an unknown or looping rule, two tools reading the same words in one language, a slot that is invalid,
 * unused, missing from a phrasing that needs it or placed next to another free-span slot) throws when the pack is
 * built, never when a transcript is matched. So does a pack whose phrasings overlap: if one phrasing's own example
 * words can also be read by another phrasing with a different result (`remove all` beside `remove {item}`), the build
 * refuses it, naming both.
 *
 * The pack is immutable once built, so it is safe to share between commands and threads.
 */
public class GrammarPack internal constructor(settings: Builder) {
    private val matchers: Map<String, RuleMatcher>
    private val hooks: Map<String, Map<String, (String, String) -> String?>>
    private val terminalTools: Set<String>
    private val intentCount: Int
    private val tryOtherLanguage: Boolean
    private val inputCap: Int

    /** True when some intent is not terminal, so a tier over this pack needs a resolver. */
    internal val hasNonTerminalIntent: Boolean

    init {
        val intents = settings.intents.toList()
        validateIntents(intents)
        validateSlotDeclarations(intents)
        validateNormalizers(intents)
        intentCount = intents.size
        hooks = intents.associate { intent -> intent.toolName to intent.normalizers.associate { it.slot to it.hook } }
        terminalTools = intents.filter { it.terminal }.map { it.toolName }.toSet()
        hasNonTerminalIntent = intents.any { !it.terminal }
        tryOtherLanguage = settings.tryOtherLanguage
        val enFillers = foldFillers(EN, settings.enFillers.toList())
        val esFillers = foldFillers(ES, settings.esFillers.toList())
        val slots = intents.associate { intent -> intent.toolName to intent.slots.associate { it.name to it.spec } }
        val enRules = compileLanguage(EN, intents, settings.enRules.toList(), enFillers) { it.en }
        val esRules = compileLanguage(ES, intents, settings.esRules.toList(), esFillers) { it.es }
        validateSlotUse(intents, enRules + esRules)
        val en = RuleMatcher(enRules, enFillers, slots)
        val es = RuleMatcher(esRules, esFillers, slots)
        validateNoOverlap(enRules, en, slots, enFillers)
        validateNoOverlap(esRules, es, slots, esFillers)
        matchers = mapOf(EN to en, ES to es)
        inputCap = maxOf(en.span, es.span)
    }

    /**
     * Matches [transcript] against the declared phrasings, for the command's [language] label.
     *
     * - "en" tries the English phrasings only and "es" the Spanish ones only, unless [Builder.tryOtherLanguage] is set,
     *   which also tries the other language;
     * - null tries both languages, whatever [Builder.tryOtherLanguage] says;
     * - any other label, including "EN", "en-US", "" or "fr", matches nothing.
     *
     * When one language matches, its phrasing is the answer. When both match they must agree on the tool and the
     * arguments, otherwise the transcript is ambiguous and matches nothing. Two readings in one language, or a
     * transcript with more words than any phrasing can span, match nothing too: the match is never a best guess.
     *
     * Returns the match, or null when nothing matches, the match is not unambiguous, the input is too long or the label
     * is not supported. It never throws for any transcript, never logs, and has no effect.
     */
    public fun match(transcript: String, language: String?): GrammarMatch? =
        (matchDetailed(transcript, language) as? GrammarResult.Matched)?.match

    internal fun matchDetailed(transcript: String, language: String?): GrammarResult {
        val candidates = candidatesFor(language)
            ?: return GrammarResult.Rejected(TraceCode.GRAMMAR_LANGUAGE_UNSUPPORTED)
        val tokens = tokenize(transcript)
        val verdicts = candidates.map { it to (matchers[it]?.match(tokens, inputCap) ?: RuleVerdict.None()) }
        return decide(language, verdicts)
    }

    // The languages a label asks for, labeled one first; null for a label this library does not read.
    private fun candidatesFor(label: String?): List<String>? = when (label) {
        null -> listOf(EN, ES)
        EN -> if (tryOtherLanguage) listOf(EN, ES) else listOf(EN)
        ES -> if (tryOtherLanguage) listOf(ES, EN) else listOf(ES)
        else -> null
    }

    // The one place the per-language verdicts become a result: a language that reads two ways ends it, each reading
    // goes through the app's normalize hooks (labeled language first, the first refusal wins), one reading is the
    // answer, and two readings must be the same call.
    private fun decide(label: String?, verdicts: List<Pair<String, RuleVerdict>>): GrammarResult {
        val readings = verdicts.mapNotNull { (language, verdict) ->
            (verdict as? RuleVerdict.One)?.let { language to it }
        }
        val ambiguous = verdicts.any { it.second is RuleVerdict.Ambiguous }
        // An ambiguous transcript is refused before any app hook sees it.
        val results = readings.filter { !ambiguous }.map { (language, reading) -> normalized(reading, language) }
        val refusal = results.filterIsInstance<GrammarResult.Rejected>().firstOrNull()
        val hits = results.filterIsInstance<GrammarResult.Matched>().map { it.match }
        return when {
            ambiguous -> GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
            refusal != null -> refusal
            hits.size == 1 -> GrammarResult.Matched(hits.single())
            hits.size > 1 -> agreed(label, hits)
            else -> GrammarResult.Rejected(unmatchedCode(verdicts))
        }
    }

    // One reading with the app's hooks applied to its bound slots: the hook sees the words as spoken and the language
    // of the pack that matched, and its answer replaces the slot value. No answer (null or blank) refuses the match; a
    // throwing hook refuses it too and only the fault code is kept, never the exception.
    private fun normalized(verdict: RuleVerdict.One, language: String): GrammarResult {
        val tool = verdict.rule.toolName
        val toolHooks = hooks[tool].orEmpty()
        val values = LinkedHashMap<String, JsonElement>(verdict.arguments)
        var refusal: TraceCode? = null
        for (binding in verdict.bindings) {
            val hook = toolHooks[binding.name]
            if (hook != null && refusal == null) {
                val answer = ask(hook, binding.raw, language)
                refusal = answer.refusal
                answer.value?.let { values[binding.name] = JsonPrimitive(it) }
            }
        }
        val terminal = tool in terminalTools
        return if (refusal != null) {
            GrammarResult.Rejected(refusal)
        } else {
            GrammarResult.Matched(GrammarMatch(tool, JsonObject(values), language, terminal, verdict.rule.ruleId))
        }
    }

    private fun ask(hook: (String, String) -> String?, raw: String, language: String): HookAnswer =
        guardedPlain(
            onFault = { HookAnswer(null, TraceCode.GRAMMAR_NORMALIZE_ERROR) },
            block = {
                val answer = hook(raw, language)
                when {
                    answer.isNullOrBlank() -> HookAnswer(null, TraceCode.GRAMMAR_SLOT_REJECTED)
                    else -> HookAnswer(answer, null)
                }
            },
        )

    private fun unmatchedCode(verdicts: List<Pair<String, RuleVerdict>>): TraceCode? =
        if (verdicts.any { it.second is RuleVerdict.TooLong }) TraceCode.GRAMMAR_INPUT_TOO_LONG else null

    // Both languages read the transcript: the same tool, terminal flag and arguments is one reading, labeled with the
    // command's label (null without one); anything else is two readings and the pack never picks between them.
    private fun agreed(label: String?, hits: List<GrammarMatch>): GrammarResult {
        val first = hits.first()
        val same = hits.all {
            it.toolName == first.toolName && it.terminal == first.terminal && it.arguments == first.arguments
        }
        return if (same) {
            GrammarResult.Matched(GrammarMatch(first.toolName, first.arguments, label, first.terminal, null))
        } else {
            GrammarResult.Rejected(TraceCode.GRAMMAR_AMBIGUOUS)
        }
    }

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
         * Whether a command labeled "en" also tries the Spanish phrasings, and one labeled "es" the English ones.
         * False by default: a labeled command reads only its own language. A command with no label always tries both,
         * and a label other than "en" or "es" matches nothing, whatever this says. When both languages match, they
         * must agree on the tool and the arguments, and the match then carries the command's label as its language.
         */
        public var tryOtherLanguage: Boolean = false

        /**
         * Declares the phrasings that mean the app's tool [toolName], from [block].
         *
         * Checked when the pack is built: the name must not be blank or declared twice, the intent needs at least one
         * phrasing, and two tools may not declare the same phrasing in one language.
         */
        public fun intent(toolName: String, block: IntentBuilder.() -> Unit) {
            val settings = IntentBuilder().apply(block)
            intents.add(
                IntentSpec(
                    toolName,
                    settings.en.toList(),
                    settings.es.toList(),
                    settings.slots.toList(),
                    settings.terminal,
                    settings.normalizers.toList(),
                ),
            )
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
        internal val normalizers: MutableList<NormalizerDecl> = mutableListOf()
        internal var terminal: Boolean = false

        /**
         * Marks this intent terminal: a navigation or question intent that writes nothing. When it matches, the tier
         * ends handled with a call to the app carrying the tool name and the slot values (`terminalCall` on the
         * outcome), runs no resolver and asks no gate, and no action is recorded. It works under an offline-only
         * policy like every grammar match.
         */
        public fun terminal() {
            terminal = true
        }

        /**
         * Declares the app's hook for the `text` or `choice` slot [slot] of this intent. After a phrasing has matched
         * and before the tool call is built, [hook] receives the slot's words as spoken (case and accents kept) and the
         * language of the pack that matched ("en" or "es", never null), and answers the value to use in their place,
         * for example a canonical word an app synonym map maps both languages onto. Answering null or blank rejects the
         * match, so the command goes to the next tier; a hook that throws rejects it too and only a code is recorded,
         * never the message.
         *
         * The hook runs once for each language pack that matched the transcript, before the packs are compared, so a
         * match in both languages must still agree after it. One refusal refuses the command: no pack is preferred
         * over the other. It is not a suspend function and must not block.
         *
         * Checked when the pack is built: [slot] must be a declared `text` or `choice` slot of this intent, with one
         * hook at most.
         */
        public fun normalize(slot: String, hook: (String, String) -> String?) {
            normalizers.add(NormalizerDecl(slot, hook))
        }

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
         * kept. [maxWords] must be from 1 to 64; checked when the pack is built.
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
