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
