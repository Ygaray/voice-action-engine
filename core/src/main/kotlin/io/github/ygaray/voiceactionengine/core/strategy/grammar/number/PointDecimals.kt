package io.github.ygaray.voiceactionengine.core.strategy.grammar.number

import java.math.BigDecimal

/**
 * The digit string the words spell (`two five` is "25"), or null unless every word is one of [digitWords] (zero to
 * nine, in order) and there are no more words than the largest accepted integer has digits.
 */
internal fun digitString(words: List<String>, digitWords: List<String>): String? =
    words.takeIf { it.isNotEmpty() && it.size <= DigitForms.largestDigits() }
        ?.map { digitWords.indexOf(it) }
        ?.takeIf { digits -> digits.none { it < 0 } }
        ?.joinToString("")

/** `whole` and the digits after the point as one exact decimal; no floating point is involved. */
internal fun pointValue(whole: Int, digits: String): BigDecimal = BigDecimal("$whole.$digits")
