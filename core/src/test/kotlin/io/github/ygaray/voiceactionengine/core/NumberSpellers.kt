package io.github.ygaray.voiceactionengine.core

/**
 * Test-only spellers, written as plain arithmetic over their own word arrays so a table slip in the parsers cannot
 * hide behind a matching slip here. Each returns the folded token keys of one spelling of `n` in 0..999,999.
 */

private const val SPELLED_LARGEST = 999_999

private val enOnes = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
)
private val enTens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

private fun enBelowHundred(n: Int): String =
    if (n < 20) enOnes[n] else enTens[n / 10] + (if (n % 10 == 0) "" else " " + enOnes[n % 10])

private fun enBelowThousand(n: Int, withAnd: Boolean): String {
    val hundreds = n / 100
    val rest = n % 100
    return when {
        hundreds == 0 -> enBelowHundred(rest)
        rest == 0 -> "${enOnes[hundreds]} hundred"
        withAnd -> "${enOnes[hundreds]} hundred and ${enBelowHundred(rest)}"
        else -> "${enOnes[hundreds]} hundred ${enBelowHundred(rest)}"
    }
}

private fun enWords(n: Int, withAnd: Boolean): List<String> {
    require(n in 0..SPELLED_LARGEST) { "out of the spelled range: $n" }
    val thousands = n / 1000
    val rest = n % 1000
    val text = when {
        n == 0 -> "zero"
        thousands == 0 -> enBelowThousand(rest, withAnd)
        rest == 0 -> enBelowThousand(thousands, withAnd) + " thousand"
        withAnd && rest < 100 -> enBelowThousand(thousands, true) + " thousand and " + enBelowHundred(rest)
        else -> enBelowThousand(thousands, withAnd) + " thousand " + enBelowThousand(rest, withAnd)
    }
    return text.split(" ")
}

/** The canonical English spelling: `one hundred five`, no `and`. */
fun spellEn(n: Int): List<String> = enWords(n, withAnd = false)

/** The same number with the British `and` before the last one or two digits. */
fun spellEnWithAnd(n: Int): List<String> = enWords(n, withAnd = true)

private val esUnits = listOf(
    "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
    "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
    "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete",
    "veintiocho", "veintinueve",
)
private val esTens = listOf("", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
private val esHundreds = listOf(
    "", "", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos",
    "ochocientos", "novecientos",
)

/** 1..99; style 1 writes the split archaic forms (diez y seis, veinte y uno), style 2 the one-word tens forms. */
private fun esBelowHundred(n: Int, style: Int): String = when {
    n < 16 -> esUnits[n]
    style == 1 && n in 16..19 -> "diez y " + esUnits[n - 10]
    style == 1 && n in 21..29 -> "veinte y " + esUnits[n - 20]
    n < 30 -> esUnits[n]
    n % 10 == 0 -> esTens[n / 10]
    style == 2 -> esTens[n / 10] + "i" + esUnits[n % 10]
    else -> esTens[n / 10] + " y " + esUnits[n % 10]
}

private fun esFeminine(hundred: String): String = hundred.removeSuffix("os") + "as"

/** 1..999 spelled whole: `cien` for exactly 100, `ciento` before anything else, the -os or -as hundreds otherwise. */
private fun esBelowThousand(n: Int, feminine: Boolean, style: Int): String {
    val hundreds = n / 100
    val rest = n % 100
    val head = when {
        n == 100 -> "cien"
        hundreds == 1 -> "ciento"
        hundreds >= 2 -> esHundreds[hundreds].let { if (feminine) esFeminine(it) else it }
        else -> ""
    }
    val tail = if (rest == 0) "" else esBelowHundred(rest, style)
    return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
}

/** `uno` at the end of a group written before `mil` loses its o (veintiun mil) or turns feminine (veintiuna mil). */
private fun esApocope(group: String, feminine: Boolean): String =
    if (group.endsWith("uno")) group.removeSuffix("uno") + (if (feminine) "una" else "un") else group

private fun esWords(n: Int, variant: Boolean): List<String> {
    require(n in 0..SPELLED_LARGEST) { "out of the spelled range: $n" }
    val thousands = n / 1000
    val rest = n % 1000
    val style = if (variant) n % 3 else 0
    val head = when {
        thousands == 0 -> ""
        thousands == 1 -> "mil"
        else -> esApocope(esBelowThousand(thousands, variant, 0), variant && n % 2 == 0) + " mil"
    }
    val tail = if (rest == 0) "" else esBelowThousand(rest, false, style)
    return (if (n == 0) "cero" else listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")).split(" ")
}

/** The canonical Spanish spelling: one-word 16..29, `y` from thirty, apocope before `mil`. */
fun spellEs(n: Int): List<String> = esWords(n, variant = false)

/** Feminine hundreds and `una` before `mil`, plus the split and one-word forms in the last group. */
fun spellEsVariant(n: Int): List<String> = esWords(n, variant = true)
