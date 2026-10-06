package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.number.NumberWords
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val englishIntegers: List<Pair<String, Long>> = listOf(
    "zero" to 0L, "one" to 1L, "nine" to 9L, "ten" to 10L, "nineteen" to 19L,
    "twenty" to 20L, "twenty one" to 21L, "forty two" to 42L, "ninety nine" to 99L,
    "one hundred" to 100L, "a hundred" to 100L, "one hundred five" to 105L, "one hundred and five" to 105L,
    "two hundred twenty one" to 221L, "nine hundred ninety nine" to 999L,
    "fifteen hundred" to 1500L, "twenty five hundred" to 2500L, "eleven hundred" to 1100L,
    "a thousand" to 1000L, "one thousand" to 1000L, "two thousand five hundred" to 2500L,
    "one thousand and five" to 1005L, "one thousand five" to 1005L, "five thousand" to 5000L,
    "a hundred thousand" to 100_000L, "one hundred thousand" to 100_000L,
    "twenty one thousand twenty one" to 21_021L,
    "nine hundred and ninety nine thousand nine hundred and ninety nine" to 999_999L,
    "nine hundred ninety nine thousand nine hundred ninety nine" to 999_999L,
)

private val englishRejects: List<String> = listOf(
    "oh five", "zero five", "fourty", "a", "an", "one one", "twenty and five", "twenty 1",
    "hundred", "one hundred hundred", "one hundred and", "and five", "and one hundred", "ten hundred",
    "fifteen hundred thirty thousand", "million", "thousand", "one thousand thousand", "dozen",
    "to", "too", "for", "ate", "won", "twenty ten", "twenty zero", "one thousand and", "a thousand a hundred",
    "one a hundred", "ninety ninety", "one hundred and five hundred", "a a hundred", "twenty hundred and five",
    "one thousand and one hundred", "five and a",
)

private val englishDecimals: List<Pair<String, String>> = listOf(
    "half" to "0.5", "a half" to "0.5", "one half" to "0.5",
    "quarter" to "0.25", "a quarter" to "0.25", "one quarter" to "0.25", "three quarters" to "0.75",
    "one and a half" to "1.5", "two and a quarter" to "2.25", "two and three quarters" to "2.75",
    "two point five" to "2.5", "zero point two five" to "0.25", "point five" to "0.5",
    "twenty one point zero five" to "21.05", "two" to "2", "one hundred and five" to "105",
    "2 and a half" to "2.5", "1000 and a quarter" to "1000.25", "one hundred and five and a half" to "105.5",
)

private val englishDecimalRejects: List<String> = listOf(
    "one point", "point twenty five", "two point 5", "point", "and a half", "one and", "one and half",
    "two and one half", "a half and a half", "half a", "three quarter", "two quarter", "a quarters",
    "one and a", "point point five", "two point five and a half", "two point ten",
    "two point five five five five five five five",
)

class NumberGoldenEnTest {

    private fun keys(phrase: String): List<String> = phrase.split(" ")

    @Test
    fun acceptedIntegerPhrasesParseToTheirValues() {
        for ((phrase, value) in englishIntegers) {
            assertEquals(phrase, value, NumberWords.integer(keys(phrase), "en"))
            assertEquals(phrase, 0, BigDecimal.valueOf(value).compareTo(NumberWords.decimal(keys(phrase), "en")))
        }
    }

    @Test
    fun rejectedPhrasesAreNotNumbers() {
        for (phrase in englishRejects) {
            assertNull("integer of '$phrase'", NumberWords.integer(keys(phrase), "en"))
            assertNull("decimal of '$phrase'", NumberWords.decimal(keys(phrase), "en"))
        }
    }

    @Test
    fun fractionsAndPointDecimalsAreExact() {
        for ((phrase, value) in englishDecimals) {
            val actual = NumberWords.decimal(keys(phrase), "en")
            assertNotNull("decimal of '$phrase'", actual)
            assertEquals("decimal of '$phrase': got $actual", 0, BigDecimal(value).compareTo(actual))
        }
    }

    @Test
    fun aFractionPhraseIsNeverAnInteger() {
        for (phrase in listOf("half", "a half", "a quarter", "three quarters", "one and a half", "two point five")) {
            assertNull("integer of '$phrase'", NumberWords.integer(keys(phrase), "en"))
        }
    }

    @Test
    fun malformedDecimalPhrasesAreNotNumbers() {
        for (phrase in englishDecimalRejects) {
            assertNull("decimal of '$phrase'", NumberWords.decimal(keys(phrase), "en"))
        }
    }

    @Test
    fun aPrefixOrSuffixNeverReadsAsTheWholePhrase() {
        val redundantArticle = setOf("a half", "one half", "a quarter", "one quarter")
        val phrases = (englishIntegers.map { it.first } + englishDecimals.map { it.first })
            .filterNot { it in redundantArticle }
        var checked = 0
        for (phrase in phrases) {
            val words = keys(phrase)
            val whole = NumberWords.decimal(words, "en")
            for (cut in 1 until words.size) {
                for (part in listOf(words.subList(0, cut), words.subList(cut, words.size))) {
                    val value = NumberWords.decimal(part, "en")
                    assertTrue(
                        "'${part.joinToString(" ")}' of '$phrase' must not read as the whole value $whole",
                        value == null || value.compareTo(whole) != 0,
                    )
                    checked++
                }
            }
        }
        assertTrue("the sweep was vacuous: $checked parts", checked > englishIntegers.size)
    }

    @Test
    fun everyPhraseTableHasRowsInEachClass() {
        assertTrue(englishIntegers.size >= 25)
        assertTrue(englishRejects.size >= 25)
        assertTrue(englishDecimals.size >= 15)
        assertTrue(englishDecimalRejects.size >= 10)
    }

    @Test
    fun theLongestPhraseCoversTheLongestSpelling() {
        val longest = (0..999_999).maxOf { spellEnWithAnd(it).size }
        val phrase = NumberWords.longestPhrase("en")
        assertTrue("longest phrase $phrase < spelling $longest", phrase >= longest)
        assertEquals(0, NumberWords.longestPhrase("fr"))
    }
}
