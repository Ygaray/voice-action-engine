package io.github.ygaray.voiceactionengine.core.strategy.grammar.number

import java.math.BigDecimal

private const val TEN = 10
private const val HUNDRED = 100
private const val THOUSAND = 1000
private const val TENS_OFFSET = 3 // the first tens word, treinta, is thirty
private const val AND = "y"
private const val MIL = "mil"
private const val CIEN = "cien"
private const val CIENTO = "ciento"
private const val COMPOUND_WORDS = 3 // a tens word, "y", a unit word

/**
 * Spanish number words over folded keys (accents removed), whole-span and strict. `y` joins tens and units from thirty
 * on, `cien` is the whole group only when nothing follows it, `ciento` takes a 1..99 group, `mil` is never preceded by
 * `un` or `uno`, and the apocopated and feminine forms (`veintiun`, `una`, `doscientas`) are accepted. The archaic
 * `diez y seis`, `veinte y uno` and the one-word `treintaicinco` are accepted as recognizer aliases; `treinticinco`,
 * `trenta` and `trentaicinco` are not. A bare `un` or `una` is never a number.
 */
internal object SpanishNumbers : WordNumbers {
    override val conjunction: String = AND

    private val underThirty = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
        "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
        "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete",
        "veintiocho", "veintinueve",
    )
    private val tens = listOf("treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
    private val hundreds = listOf(
        "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos",
        "novecientos",
    )

    private val twenty = underThirty.indexOf("veinte")
    private val twentyOne = underThirty.indexOf("veintiuno")
    private val firstArchaicUnit = underThirty.indexOf("dieciseis") - TEN
    private val digitWords = underThirty.subList(0, TEN)
    private val apocoped = setOf("un", "una")
    private val markers = setOf("coma", "punto")

    private val plainUnits: Map<String, Int> = (1 until TEN).associateBy { underThirty[it] }
    private val compoundUnits: Map<String, Int> = plainUnits + mapOf("un" to 1, "una" to 1)
    private val tensValue: Map<String, Int> = tens.withIndex().associate { it.value to (it.index + TENS_OFFSET) * TEN }
    private val hundredValue: Map<String, Int> = hundreds.withIndex().flatMap { (at, word) ->
        listOf(word to (at + 2) * HUNDRED, word.removeSuffix("os") + "as" to (at + 2) * HUNDRED)
    }.toMap()

    /** Every one-word number from `uno` to `noventa`, plus the apocopated 21 and the `treintaicinco` forms. */
    private val single: Map<String, Int> =
        (1 until underThirty.size).associateBy { underThirty[it] } +
            mapOf("veintiun" to twentyOne, "veintiuna" to twentyOne) +
            tensValue +
            tensValue.flatMap { (tensWord, tensPart) ->
                plainUnits.map { (unit, digit) -> tensWord + "i" + unit to tensPart + digit }
            }

    private val half = BigDecimal("0.5")
    private val quarter = BigDecimal("0.25")
    private val bare: Map<List<String>, BigDecimal> = mapOf(
        listOf("medio") to half,
        listOf("media") to half,
        listOf("un", "cuarto") to quarter,
        listOf("tres", "cuartos") to BigDecimal("0.75"),
    )
    private val tails: Map<List<String>, BigDecimal> = bare + mapOf(listOf("cuarto") to quarter)

    override fun integer(keys: List<String>): Int? = when {
        keys == listOf("cero") -> 0
        else -> keys.indexOf(MIL).let { at -> if (at < 0) belowThousand(keys) else withThousands(keys, at) }
    }

    override fun bareFraction(keys: List<String>): BigDecimal? = bare[keys]

    override fun fractionTail(keys: List<String>): BigDecimal? = tails[keys]

    override fun pointDecimal(keys: List<String>): BigDecimal? =
        keys.indexOfFirst { it in markers }.takeIf { it > 0 }?.let { at ->
            val whole = integer(keys.subList(0, at))
            val words = keys.subList(at + 1, keys.size)
            // 1+ digit words (`cero cinco`), or one 1..99 cardinal read as n / 10^digits (`veinticinco`)
            val digits = digitString(words, digitWords) ?: integer(words)?.takeIf { it in 1 until HUNDRED }?.toString()
            if (whole != null && digits != null) pointValue(whole, digits) else null
        }

    override fun longestPhrase(): Int {
        val belowHundred = 1 + 1 + 1 // a tens word, "y", a unit word
        val belowThousand = 1 + belowHundred // a hundreds word, then a below-hundred group
        val whole = belowThousand + 1 + belowThousand // the thousands group, "mil", the rest
        val andTail = 1 + 2 // "y", then "tres cuartos"
        val pointTail = 1 + DigitForms.largestDigits() // "coma" or "punto", then the digit words
        return whole + maxOf(andTail, pointTail)
    }

    /** `[<group>] mil [<group>]`; before `mil` the group is 1..999 and never ends in `uno`, and no group means 1. */
    private fun withThousands(keys: List<String>, at: Int): Int? {
        val head = keys.subList(0, at)
        val multiplier = if (head.isEmpty()) 1 else head.takeUnless { it.last().endsWith("uno") }?.let(::belowThousand)
        val tail = keys.subList(at + 1, keys.size)
        val rest = if (tail.isEmpty()) 0 else belowThousand(tail)
        return if (multiplier != null && rest != null) multiplier * THOUSAND + rest else null
    }

    /** A 1..999 group: `cien`, `ciento` plus 1..99, a hundreds word alone or plus 1..99, or a 1..99 group. */
    private fun belowThousand(keys: List<String>): Int? {
        val base = keys.firstOrNull()?.let { hundredValue[it] }
        return when {
            keys.isEmpty() -> null
            keys == listOf(CIEN) -> HUNDRED
            keys[0] == CIENTO -> afterHundreds(keys.drop(1))?.plus(HUNDRED)
            base != null -> if (keys.size == 1) base else afterHundreds(keys.drop(1))?.plus(base)
            else -> belowHundred(keys)
        }
    }

    /** What may follow a hundreds word: 1..99, where `un` and `una` stand for 1. */
    private fun afterHundreds(rest: List<String>): Int? =
        if (rest.size == 1 && rest[0] in apocoped) 1 else belowHundred(rest)

    /** A 1..99 group: one word, or tens/`diez`/`veinte`, `y`, and a unit word. */
    private fun belowHundred(keys: List<String>): Int? = when (keys.size) {
        1 -> single[keys[0]]
        COMPOUND_WORDS -> if (keys[1] == AND) compound(keys[0], keys[2]) else null
        else -> null
    }

    private fun compound(first: String, unit: String): Int? = when (first) {
        "diez" -> plainUnits[unit]?.takeIf { it >= firstArchaicUnit }?.plus(TEN)
        "veinte" -> plainUnits[unit]?.plus(twenty)
        else -> tensValue[first]?.let { tensPart -> compoundUnits[unit]?.plus(tensPart) }
    }
}
