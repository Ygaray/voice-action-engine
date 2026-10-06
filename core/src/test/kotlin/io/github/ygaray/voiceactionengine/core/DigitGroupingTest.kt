package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.number.NumberWords
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Digit forms and the language-resolved grouping matrix: an ambiguous grouping is "not a number", never a guess. */
class DigitGroupingTest {

    private fun integer(language: String, vararg keys: String): Long? = NumberWords.integer(keys.toList(), language)

    private fun decimal(language: String, vararg keys: String): BigDecimal? =
        NumberWords.decimal(keys.toList(), language)

    private fun assertInteger(expected: Long, language: String, vararg keys: String) {
        assertEquals("integer ${keys.toList()} in $language", expected, integer(language, *keys))
    }

    private fun assertDecimal(expected: String, language: String, vararg keys: String) {
        val actual = decimal(language, *keys)
        assertNotNull("decimal ${keys.toList()} in $language", actual)
        assertEquals(
            "decimal ${keys.toList()} in $language: expected $expected, got $actual",
            0,
            BigDecimal(expected).compareTo(actual),
        )
    }

    @Test
    fun englishIntegersAcceptPlainDigitsAndExactlyThreeDigitGroups() {
        assertInteger(1000, "en", "1000")
        assertInteger(1000, "en", "1,000")
        assertInteger(12345, "en", "12,345")
        assertInteger(999_999, "en", "999,999")
        assertInteger(0, "en", "0")
        assertNull(integer("en", "1,00"))
        assertNull(integer("en", "1,5"))
        assertNull(integer("en", "1,0000"))
        assertNull(integer("en", "1,000,0"))
    }

    @Test
    fun anIntegerTokenNeverCarriesADecimalPoint() {
        assertNull(integer("en", "1.000"))
        assertNull(integer("en", "2.5"))
        assertNull(integer("es", "1.000"))
        assertNull(integer("es", "12.345"))
        assertNull(integer("es", "2,5"))
    }

    @Test
    fun englishDecimalsReadThePointAsTheSeparatorAndTheCommaAsGrouping() {
        assertDecimal("1.0", "en", "1.000")
        assertDecimal("1000.5", "en", "1,000.5")
        assertDecimal("1.5", "en", "1.5")
        assertDecimal("0.5", "en", "0.5")
        assertDecimal("0.25", "en", "0.25")
        assertDecimal("12345", "en", "12,345")
        assertNull(decimal("en", "1,5"))
        assertNull(decimal("en", "1,00"))
        assertNull(decimal("en", ".5"))
        assertNull(decimal("en", "1."))
    }

    @Test
    fun spanishDecimalsReadTheCommaAsTheSeparator() {
        assertDecimal("2.5", "es", "2,5")
        assertDecimal("12.345", "es", "12,345")
        assertDecimal("1.0", "es", "1,000")
        assertDecimal("1.5", "es", "1.5")
        assertDecimal("0.25", "es", "0,25")
        assertDecimal("1.2345", "es", "1.2345")
        assertNull(decimal("es", "1,"))
        assertNull(decimal("es", ",5"))
        assertNull(decimal("es", "1,000.5"))
        assertNull(decimal("es", "1.000,5"))
    }

    @Test
    fun aSingleSpanishDotFollowedByExactlyThreeDigitsIsAmbiguous() {
        assertNull(decimal("es", "1.000"))
        assertNull(decimal("es", "12.345"))
        assertNull(decimal("es", "0.250"))
        assertNull(decimal("es", "1.000.000"))
    }

    @Test
    fun leadingZerosSignsRangesPercentsAndNonAsciiDigitsAreNotNumbers() {
        for (language in listOf("en", "es")) {
            assertNull(integer(language, "007"))
            assertNull(integer(language, "-5"))
            assertNull(integer(language, "+5"))
            assertNull(integer(language, "5-10"))
            assertNull(integer(language, "5%"))
            assertNull(integer(language, "٥"))
            assertNull(integer(language, "５"))
            assertNull(decimal(language, "007"))
            assertNull(decimal(language, "00.5"))
            assertNull(decimal(language, "-5"))
            assertNull(decimal(language, "5%"))
            assertNull(decimal(language, "٥"))
        }
        assertInteger(0, "en", "0")
        assertInteger(0, "es", "0")
    }

    @Test
    fun integersStopAtNineHundredNinetyNineThousandNineHundredNinetyNine() {
        assertNull(integer("en", "1000000"))
        assertNull(integer("es", "1000000"))
        assertNull(integer("en", "1,000,000"))
        assertInteger(999_999, "es", "999999")
        assertNull(decimal("en", "1000000"))
    }

    @Test
    fun simpleFractionsAreExactDecimals() {
        assertDecimal("0.5", "en", "1/2")
        assertDecimal("0.5", "es", "1/2")
        assertDecimal("0.75", "en", "3/4")
        assertDecimal("2.5", "en", "2", "1/2")
        assertDecimal("2.5", "es", "2", "1/2")
        assertNull(decimal("en", "1/0"))
        assertNull(decimal("en", "1/3"))
        assertNull(decimal("en", "1/2/3"))
        assertNull(decimal("en", "/2"))
        assertNull(decimal("en", "1/"))
        assertNull(decimal("en", "2", "5/2"))
        assertNull(integer("en", "1/2"))
    }

    @Test
    fun aNumberPhraseIsAllDigitsOrAllWordsInItsIntegerPart() {
        assertNull(integer("en", "twenty", "1"))
        assertNull(integer("en", "1", "twenty"))
        assertNull(integer("en", "1", "2"))
        assertNull(integer("es", "veinte", "1"))
    }

    @Test
    fun anUnsupportedLanguageAndAnEmptyKeyListReturnNullWithoutThrowing() {
        for (language in listOf("fr", "EN", "en-US", "")) {
            assertNull(integer(language, "5"))
            assertNull(decimal(language, "5"))
            assertNull(decimal(language, "1/2"))
            assertNull(integer(language, "five"))
        }
        assertNull(NumberWords.integer(emptyList(), "en"))
        assertNull(NumberWords.decimal(emptyList(), "es"))
        assertNull(NumberWords.integer(listOf(""), "en"))
        assertNull(NumberWords.decimal(listOf(""), "en"))
    }
}
