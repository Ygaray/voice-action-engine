package io.github.ygaray.voiceactionengine.core.strategy.grammar.number

import java.math.BigDecimal

/** One language's number words over folded token keys; every parser consumes its whole span or returns null. */
internal interface WordNumbers {
    /** The word that joins a whole number to its fraction tail (`and`, `y`). */
    val conjunction: String

    /** A whole number 0..999,999 written in words. */
    fun integer(keys: List<String>): Int?

    /** A fraction written on its own (`half`, `un cuarto`), never joined to a whole number. */
    fun bareFraction(keys: List<String>): BigDecimal?

    /** The fraction that follows [conjunction] after a whole number (`a half`, `medio`). */
    fun fractionTail(keys: List<String>): BigDecimal?

    /** A whole number, a point or coma marker and its digits, as an exact decimal. */
    fun pointDecimal(keys: List<String>): BigDecimal?

    /** The most tokens any accepted decimal phrase spans, worked out from the lexicon structure. */
    fun longestPhrase(): Int
}

/** Strict whole-span number parsing over folded token keys; the one entry point the slot code uses. */
internal object NumberWords {
    fun integer(keys: List<String>, language: String): Long? = when {
        keys.isEmpty() -> null
        DigitForms.isDigitKey(keys[0]) -> keys.singleOrNull()?.let { DigitForms.integer(it, language) }
        else -> lexicon(language)?.integer(keys)?.toLong()
    }

    fun decimal(keys: List<String>, language: String): BigDecimal? = when {
        keys.isEmpty() -> null
        DigitForms.isDigitKey(keys[0]) -> digitDecimal(keys, language)
        else -> lexicon(language)?.let { wordDecimal(it, keys) }
    }

    fun longestPhrase(language: String): Int = lexicon(language)?.longestPhrase() ?: 0

    private val lexicons: Map<String, WordNumbers> = emptyMap()

    private fun lexicon(language: String): WordNumbers? = lexicons[language]

    private fun digitDecimal(keys: List<String>, language: String): BigDecimal? = when (keys.size) {
        1 -> DigitForms.decimal(keys[0], language)
        2 -> mixedFraction(keys[0], keys[1], language)
        else -> conjoinedFraction(keys, language)
    }

    /** `2 1/2`: a digit whole number followed by a proper `n/d` fraction. */
    private fun mixedFraction(whole: String, fraction: String, language: String): BigDecimal? {
        val head = DigitForms.integer(whole, language)
        val tail = if ('/' in fraction) DigitForms.decimal(fraction, language) else null
        return if (head != null && tail != null && tail < BigDecimal.ONE) BigDecimal.valueOf(head).add(tail) else null
    }

    /** `2 and a half`, `2 y medio`: a digit whole number, the language's conjunction and a fraction word tail. */
    private fun conjoinedFraction(keys: List<String>, language: String): BigDecimal? =
        lexicon(language)?.takeIf { it.conjunction == keys[1] }?.let { words ->
            val head = DigitForms.integer(keys[0], language)
            val tail = words.fractionTail(keys.drop(2))
            if (head != null && tail != null) BigDecimal.valueOf(head).add(tail) else null
        }

    /** An integer phrase is also a decimal; otherwise a bare fraction, a whole number plus a tail, or a point form. */
    private fun wordDecimal(words: WordNumbers, keys: List<String>): BigDecimal? =
        words.integer(keys)?.let { BigDecimal.valueOf(it.toLong()) }
            ?: words.bareFraction(keys)
            ?: wholeWithTail(words, keys)
            ?: words.pointDecimal(keys)

    private fun wholeWithTail(words: WordNumbers, keys: List<String>): BigDecimal? =
        keys.indices.filter { keys[it] == words.conjunction }.firstNotNullOfOrNull { at ->
            val head = words.integer(keys.subList(0, at))
            val tail = words.fractionTail(keys.subList(at + 1, keys.size))
            if (head != null && tail != null) BigDecimal.valueOf(head.toLong()).add(tail) else null
        }
}
