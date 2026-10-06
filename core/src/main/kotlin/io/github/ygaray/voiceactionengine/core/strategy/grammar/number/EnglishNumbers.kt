package io.github.ygaray.voiceactionengine.core.strategy.grammar.number

import java.math.BigDecimal

private const val TEN = 10
private const val HUNDRED = 100
private const val THOUSAND = 1000
private const val AND = "and"
private const val A = "a"
private const val POINT = "point"
private const val HUNDRED_WORD = "hundred"
private const val THOUSAND_WORD = "thousand"

/**
 * English number words, whole-span and strict: `and` only directly before a trailing 1..99 group, `a` only as the
 * first word of `a hundred`, `a thousand`, `a half` and `a quarter`, `fifteen hundred` only with no thousand group, no
 * homophones and no digit/word mixing in the integer part.
 */
internal object EnglishNumbers : WordNumbers {
    override val conjunction: String = AND

    private val units = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val unitValue: Map<String, Int> = units.withIndex().associate { it.value to it.index }
    private val tensValue: Map<String, Int> = tens.withIndex().associate { it.value to (it.index + 2) * TEN }
    private val digitWords = units.subList(0, TEN)

    private val half = BigDecimal("0.5")
    private val quarter = BigDecimal("0.25")
    private val threeQuarters = BigDecimal("0.75")
    private val bare: Map<List<String>, BigDecimal> = mapOf(
        listOf("half") to half,
        listOf("a", "half") to half,
        listOf("one", "half") to half,
        listOf("quarter") to quarter,
        listOf("a", "quarter") to quarter,
        listOf("one", "quarter") to quarter,
        listOf("three", "quarters") to threeQuarters,
    )
    private val tails: Map<List<String>, BigDecimal> = mapOf(
        listOf("a", "half") to half,
        listOf("a", "quarter") to quarter,
        listOf("three", "quarters") to threeQuarters,
    )

    override fun integer(keys: List<String>): Int? = when {
        keys == listOf("zero") -> 0
        else -> keys.indexOf(THOUSAND_WORD).let { at ->
            if (at < 0) belowThousand(keys, allowA = true) ?: colloquialHundreds(keys) else withThousands(keys, at)
        }
    }

    override fun bareFraction(keys: List<String>): BigDecimal? = bare[keys]

    override fun fractionTail(keys: List<String>): BigDecimal? = tails[keys]

    override fun pointDecimal(keys: List<String>): BigDecimal? =
        keys.indexOf(POINT).takeIf { it >= 0 }?.let { at ->
            val whole = if (at == 0) 0 else integer(keys.subList(0, at))
            val digits = digitString(keys.subList(at + 1, keys.size), digitWords)
            if (whole != null && digits != null) pointValue(whole, digits) else null
        }

    override fun longestPhrase(): Int {
        val belowHundred = 2 // a tens word and a unit word
        val belowThousand = 1 + 1 + 1 + belowHundred // unit, "hundred", "and", then a below-hundred group
        val whole = belowThousand + 1 + belowThousand // the thousands group, "thousand", the rest
        val andTail = 1 + 2 // "and", then "three quarters"
        val pointTail = 1 + DigitForms.largestDigits() // "point", then the digit words
        return whole + maxOf(andTail, pointTail)
    }

    /** `<group> thousand [and <1..99> | <group>]`; the head is 1..999 and may be `a`. */
    private fun withThousands(keys: List<String>, at: Int): Int? {
        val headWords = keys.subList(0, at)
        val head = if (headWords == listOf(A)) 1 else belowThousand(headWords, allowA = true)
        val tail = keys.subList(at + 1, keys.size)
        val rest = when {
            tail.isEmpty() -> 0
            tail[0] == AND -> belowHundred(tail.drop(1))
            else -> belowThousand(tail, allowA = false)
        }
        return if (head != null && rest != null) head * THOUSAND + rest else null
    }

    /** `fifteen hundred`: an eleven..ninety-nine multiplier, only when the span holds nothing else. */
    private fun colloquialHundreds(keys: List<String>): Int? =
        if (keys.size > 1 && keys.last() == HUNDRED_WORD) {
            belowHundred(keys.dropLast(1))?.takeIf { it > TEN }?.times(HUNDRED)
        } else {
            null
        }

    private fun belowThousand(keys: List<String>, allowA: Boolean): Int? =
        keys.indexOf(HUNDRED_WORD).let { at ->
            if (at < 0) belowHundred(keys) else hundredsGroup(keys, at, allowA)
        }

    /** `<one..nine | a> hundred [and] [<1..99>]`. */
    private fun hundredsGroup(keys: List<String>, at: Int, allowA: Boolean): Int? {
        val headWords = keys.subList(0, at)
        val multiplier = if (allowA && headWords == listOf(A)) {
            1
        } else {
            headWords.singleOrNull()?.let { unitValue[it] }?.takeIf { it in 1 until TEN }
        }
        val rest = keys.subList(at + 1, keys.size)
        val remainder = when {
            rest.isEmpty() -> 0
            rest[0] == AND -> belowHundred(rest.drop(1))
            else -> belowHundred(rest)
        }
        return if (multiplier != null && remainder != null) multiplier * HUNDRED + remainder else null
    }

    /** A 1..99 group: one word, or a tens word and a one..nine unit word. */
    private fun belowHundred(keys: List<String>): Int? = when (keys.size) {
        1 -> (unitValue[keys[0]] ?: tensValue[keys[0]])?.takeIf { it > 0 }
        2 -> tensValue[keys[0]]?.let { tensPart ->
            unitValue[keys[1]]?.takeIf { it in 1 until TEN }?.plus(tensPart)
        }
        else -> null
    }
}
