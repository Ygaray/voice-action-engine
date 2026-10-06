package io.github.ygaray.voiceactionengine.core.strategy.grammar

import io.github.ygaray.voiceactionengine.core.strategy.grammar.number.NumberWords
import kotlinx.serialization.json.JsonPrimitive

/** One way a slot can bind at a token position: the span ends before token [end] and yields [value]. */
internal class SlotCandidate(val end: Int, val value: JsonPrimitive)

/**
 * The kinds of slot an intent can declare, as the matcher sees them. The public surface is the four builder methods on
 * `GrammarPack.IntentBuilder`; this class is how they are remembered. Each kind lists the candidate spans that start at
 * a token position, in the language of the pack being matched, and the matcher tries every one of them.
 */
internal sealed class SlotSpec {
    /** True for a kind that takes any words of its own (numbers and text), false for a closed list. */
    abstract val openSpan: Boolean

    /** Every span starting at token [position] of [tokens] that this slot can bind, read as [language]. */
    abstract fun candidates(language: String, tokens: GrammarTokens, position: Int): List<SlotCandidate>

    /** A whole number within `min..max`, as a `JsonPrimitive(Long)`. */
    class IntegerSlot(val min: Long, val max: Long) : SlotSpec() {
        override val openSpan: Boolean = true

        override fun candidates(language: String, tokens: GrammarTokens, position: Int): List<SlotCandidate> =
            numberSpans(language, tokens, position) { keys ->
                NumberWords.integer(keys, language)?.takeIf { it in min..max }?.let { JsonPrimitive(it) }
            }
    }

    /** A closed list: the transcript says one synonym (in the matched language) of an option; the value is its id. */
    class ChoiceSlot(val options: List<ChoiceOption>) : SlotSpec() {
        override val openSpan: Boolean = false

        override fun candidates(language: String, tokens: GrammarTokens, position: Int): List<SlotCandidate> =
            options.flatMap { option ->
                option.synonyms(language)
                    .filter { it.keys.isNotEmpty() && spoken(tokens, position, it.keys) }
                    .map { SlotCandidate(position + it.keys.size, JsonPrimitive(option.id)) }
            }

        private fun spoken(tokens: GrammarTokens, position: Int, keys: List<String>): Boolean =
            position + keys.size <= tokens.tokens.size && tokens.keys(position, position + keys.size) == keys
    }

    /** Free text of one to [maxWords] words; the value is the words as spoken, case and accents kept. */
    class TextSlot(val maxWords: Int) : SlotSpec() {
        override val openSpan: Boolean = true

        override fun candidates(language: String, tokens: GrammarTokens, position: Int): List<SlotCandidate> =
            (position + 1..minOf(tokens.tokens.size, position + maxWords)).map { end ->
                SlotCandidate(end, JsonPrimitive(tokens.surface(position, end)))
            }
    }

    /** A decimal number within `min..max`, always a `JsonPrimitive(Double)` so `2` and `2.0` never differ. */
    class DecimalSlot(val min: Double, val max: Double) : SlotSpec() {
        override val openSpan: Boolean = true

        override fun candidates(language: String, tokens: GrammarTokens, position: Int): List<SlotCandidate> =
            numberSpans(language, tokens, position) { keys ->
                NumberWords.decimal(keys, language)?.toDouble()?.takeIf { it in min..max }?.let { JsonPrimitive(it) }
            }
    }
}

/** One spoken form of a choice option: its declared [text] and the folded [keys] a transcript must equal. */
internal class Synonym(val text: String, val keys: List<String>)

/** One option of a choice slot: the [id] a match carries and its synonyms per language, folded like transcripts. */
internal class ChoiceOption(val id: String, enSynonyms: List<String>, esSynonyms: List<String>) {
    val en: List<Synonym> = enSynonyms.map { synonymOf(it) }
    val es: List<Synonym> = esSynonyms.map { synonymOf(it) }

    /** The synonyms this option has in [language]; none for a language the library does not read. */
    fun synonyms(language: String): List<Synonym> = when (language) {
        "en" -> en
        "es" -> es
        else -> emptyList()
    }

    private fun synonymOf(text: String): Synonym = Synonym(text, tokenize(text).tokens.map { it.key })
}

/** A slot as declared on an intent: its name and its kind. */
internal class SlotDecl(val name: String, val spec: SlotSpec)

// Every end from the next token up to the longest phrase the language writes a number in; whole-span parsing decides.
private fun numberSpans(
    language: String,
    tokens: GrammarTokens,
    position: Int,
    parse: (List<String>) -> JsonPrimitive?,
): List<SlotCandidate> {
    val last = minOf(tokens.tokens.size, position + NumberWords.longestPhrase(language))
    return (position + 1..last).mapNotNull { end ->
        parse(tokens.keys(position, end))?.let { SlotCandidate(end, it) }
    }
}
