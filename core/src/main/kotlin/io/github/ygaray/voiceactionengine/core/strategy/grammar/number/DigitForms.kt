package io.github.ygaray.voiceactionengine.core.strategy.grammar.number

import java.math.BigDecimal

private const val ENGLISH = "en"
private const val SPANISH = "es"
private const val GROUP_SIZE = 3
private const val LARGEST_INTEGER = 999_999L

/**
 * Number tokens written with ASCII digits, read according to the language of the pack being tried. A grouping that
 * could mean two different values is not a number: the caller gets null, never a guess. Everything is plain-integer or
 * [BigDecimal] arithmetic, so a value reads the same in both languages.
 */
internal object DigitForms {
    private val plain = Regex("0|[1-9][0-9]*")
    private val grouped = Regex("[1-9][0-9]{0,2}(,[0-9]{3})+")
    private val fractionDigits = Regex("[0-9]+")
    private val largestDigitCount = LARGEST_INTEGER.toString().length

    /** How many digits the largest accepted integer has; the word parsers bound their fraction digits by it. */
    fun largestDigits(): Int = largestDigitCount

    fun isDigitKey(key: String): Boolean = key.isNotEmpty() && key[0] in '0'..'9'

    /** A whole number 0..999,999: EN allows `,` groups of exactly three, ES allows none. */
    fun integer(key: String, language: String): Long? = when (language) {
        ENGLISH -> if (grouped.matches(key)) bounded(key.replace(",", "")) else boundedPlain(key)
        SPANISH -> boundedPlain(key)
        else -> null
    }

    /** A decimal value: an integer, a separator form, or a simple `n/d` fraction that has an exact decimal value. */
    fun decimal(key: String, language: String): BigDecimal? = when {
        '/' in key -> if (language == ENGLISH || language == SPANISH) fraction(key, ::boundedPlain) else null
        language == ENGLISH -> decimalEnglish(key)
        language == SPANISH -> decimalSpanish(key)
        else -> null
    }

    private fun decimalEnglish(key: String): BigDecimal? =
        if ('.' in key) {
            separated(key, '.', ambiguousGroup = false) { integer(it, ENGLISH) }
        } else {
            integer(key, ENGLISH)?.let { BigDecimal.valueOf(it) }
        }

    private fun decimalSpanish(key: String): BigDecimal? = when {
        ',' in key -> separated(key, ',', ambiguousGroup = false, ::boundedPlain)
        '.' in key -> separated(key, '.', ambiguousGroup = true, ::boundedPlain)
        else -> boundedPlain(key)?.let { BigDecimal.valueOf(it) }
    }

    /** `whole<sep>digits`; a digit group of exactly three after the separator is refused when [ambiguousGroup]. */
    private fun separated(
        key: String,
        separator: Char,
        ambiguousGroup: Boolean,
        whole: (String) -> Long?,
    ): BigDecimal? {
        val parts = key.split(separator)
        val head = whole(parts[0])
        val digits = parts.getOrNull(1)
        return when {
            parts.size != 2 || head == null || digits == null -> null
            ambiguousGroup && digits.length == GROUP_SIZE -> null
            else -> fractionPart(digits)?.let { BigDecimal.valueOf(head).add(it) }
        }
    }

    private fun fractionPart(digits: String): BigDecimal? =
        digits.takeIf { fractionDigits.matches(it) }?.let { BigDecimal("0.$it") }

    private fun boundedPlain(text: String): Long? = text.takeIf { plain.matches(it) }?.let(::bounded)

    private fun bounded(digits: String): Long? = digits.takeIf { it.length <= largestDigitCount }?.toLong()
}

private fun fraction(key: String, whole: (String) -> Long?): BigDecimal? {
    val parts = key.split('/')
    val numerator = parts.getOrNull(0)?.let(whole)
    val denominator = parts.getOrNull(1)?.let(whole)
    return when {
        parts.size != 2 || numerator == null || denominator == null -> null
        denominator == 0L -> null
        else -> exact(numerator, denominator)
    }
}

/** The quotient only when it terminates (1/2, 3/4); 1/3 has no exact decimal value and is not a number. */
private fun exact(numerator: Long, denominator: Long): BigDecimal? =
    try {
        BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator))
    } catch (ignored: ArithmeticException) {
        null
    }
