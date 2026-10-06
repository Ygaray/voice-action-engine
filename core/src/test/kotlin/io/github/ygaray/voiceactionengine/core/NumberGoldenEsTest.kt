package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.number.NumberWords
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Keys are folded: dieciseis, veintiun, veintidos (accents already removed).
private val spanishIntegers: List<Pair<String, Long>> = listOf(
    "cero" to 0L, "uno" to 1L, "quince" to 15L, "dieciseis" to 16L, "diecinueve" to 19L,
    "diez y seis" to 16L, "diez y nueve" to 19L,
    "veinte" to 20L, "veintiuno" to 21L, "veintiun" to 21L, "veintiuna" to 21L, "veintidos" to 22L,
    "veintinueve" to 29L, "veinte y uno" to 21L, "veinte y nueve" to 29L,
    "treinta" to 30L, "treinta y uno" to 31L, "treinta y un" to 31L, "treinta y una" to 31L,
    "cuarenta y cinco" to 45L, "noventa y nueve" to 99L, "treintaicinco" to 35L, "cuarentaidos" to 42L,
    "cien" to 100L, "ciento uno" to 101L, "ciento veinte" to 120L, "ciento treinta y cuatro" to 134L,
    "ciento diez y seis" to 116L, "ciento un" to 101L,
    "doscientos" to 200L, "doscientas" to 200L, "quinientos" to 500L, "quinientas" to 500L,
    "setecientos" to 700L, "novecientos" to 900L, "novecientas" to 900L,
    "mil" to 1000L, "dos mil" to 2000L, "quince mil" to 15_000L, "cien mil" to 100_000L,
    "ciento un mil" to 101_000L, "doscientas mil" to 200_000L, "mil quinientos" to 1500L,
    "dos mil veinte" to 2020L, "veintiun mil" to 21_000L, "veintiuna mil" to 21_000L,
    "treinta y un mil" to 31_000L, "treinta y una mil" to 31_000L,
    "mil cien" to 1100L, "mil ciento dieciseis" to 1116L, "dos mil ciento treinta y cuatro" to 2134L,
    "cien mil uno" to 100_001L, "doscientos mil cien" to 200_100L,
    "novecientos noventa y nueve mil novecientos noventa y nueve" to 999_999L,
)

private val spanishRejects: List<String> = listOf(
    "un", "una", "veinte uno", "veintiuno mil", "treinta uno", "treinta y cero", "treinta y", "veinte y",
    "diez y", "diez y diez", "diez y cinco", "veinte y un", "ciento y cinco", "cien cinco", "ciento", "dos cientos",
    "un mil", "uno mil", "millon", "millones", "docena", "treinta y uno mil", "ciento uno mil", "treintaiuno mil",
    "mil mil", "dos mil mil", "mil y uno", "mil ciento", "cienmil", "sietecientos", "nuevecientos", "treinticinco",
    "trenta", "trentaicinco", "cero uno", "cero cero", "quinientos y uno", "cien mil y uno", "doscientos y",
    "veinte y veinte", "treinta y treinta", "cien mil cien mil", "5 mil", "dos 1",
)

private val spanishDecimals: List<Pair<String, String>> = listOf(
    "medio" to "0.5", "media" to "0.5", "un cuarto" to "0.25", "tres cuartos" to "0.75",
    "dos y medio" to "2.5", "dos y media" to "2.5", "dos y cuarto" to "2.25", "dos y un cuarto" to "2.25",
    "dos y tres cuartos" to "2.75", "veinte y medio" to "20.5", "uno y medio" to "1.5",
    "dos coma cinco" to "2.5", "dos punto cinco" to "2.5", "dos coma cero cinco" to "2.05",
    "cero coma veinticinco" to "0.25", "tres coma cinco" to "3.5", "dos coma treinta y cinco" to "2.35",
    "uno coma dos cinco" to "1.25", "treinta y cinco coma dos" to "35.2", "dos coma diez" to "2.10",
    "dos coma cero" to "2.0", "dos" to "2", "ciento cinco" to "105", "2 y medio" to "2.5",
    "1000 y un cuarto" to "1000.25", "cien mil y tres cuartos" to "100000.75",
)

private val spanishDecimalRejects: List<String> = listOf(
    "dos con medio", "dos con", "coma", "tres coma ciento cinco", "tres coma 5", "cuarto", "coma cinco", "dos coma",
    "dos y", "dos y cuarto y", "un cuarto y medio", "tres cuarto", "medio medio", "dos medio", "dos coma coma cinco",
    "dos coma uno dos tres cuatro cinco seis siete", "dos coma cien", "dos coma cero cero cero cero cero cero cero",
    "dos punto", "punto cinco", "un medio", "dos y un medio", "dos y cuartos", "una y media",
)

class NumberGoldenEsTest {

    private fun keys(phrase: String): List<String> = phrase.split(" ")

    @Test
    fun acceptedIntegerPhrasesParseToTheirValues() {
        for ((phrase, value) in spanishIntegers) {
            assertEquals(phrase, value, NumberWords.integer(keys(phrase), "es"))
            assertEquals(phrase, 0, BigDecimal.valueOf(value).compareTo(NumberWords.decimal(keys(phrase), "es")))
        }
    }

    @Test
    fun rejectedPhrasesAreNotNumbers() {
        for (phrase in spanishRejects) {
            assertNull("integer of '$phrase'", NumberWords.integer(keys(phrase), "es"))
            assertNull("decimal of '$phrase'", NumberWords.decimal(keys(phrase), "es"))
        }
    }

    @Test
    fun fractionsAndComaOrPuntoDecimalsAreExact() {
        for ((phrase, value) in spanishDecimals) {
            val actual = NumberWords.decimal(keys(phrase), "es")
            assertNotNull("decimal of '$phrase'", actual)
            assertEquals("decimal of '$phrase': got $actual", 0, BigDecimal(value).compareTo(actual))
        }
    }

    @Test
    fun aFractionPhraseIsNeverAnInteger() {
        for (phrase in listOf("medio", "media", "un cuarto", "tres cuartos", "dos y medio", "dos coma cinco")) {
            assertNull("integer of '$phrase'", NumberWords.integer(keys(phrase), "es"))
        }
    }

    @Test
    fun malformedDecimalPhrasesAreNotNumbers() {
        for (phrase in spanishDecimalRejects) {
            assertNull("decimal of '$phrase'", NumberWords.decimal(keys(phrase), "es"))
        }
    }

    @Test
    fun theEnglishSideOfTheLexiconIsNotReadAsSpanish() {
        for (phrase in listOf("twenty one", "one hundred", "half", "two and a half", "two point five")) {
            assertNull("integer of '$phrase'", NumberWords.integer(keys(phrase), "es"))
            assertNull("decimal of '$phrase'", NumberWords.decimal(keys(phrase), "es"))
        }
        for (phrase in listOf("veinte", "cien", "medio", "dos y medio", "dos coma cinco")) {
            assertNull("integer of '$phrase'", NumberWords.integer(keys(phrase), "en"))
            assertNull("decimal of '$phrase'", NumberWords.decimal(keys(phrase), "en"))
        }
    }

    @Test
    fun aPrefixOrSuffixNeverReadsAsTheWholePhrase() {
        val sameValueAsPrefix = setOf("dos coma cero")
        val phrases = (spanishIntegers.map { it.first } + spanishDecimals.map { it.first })
            .filterNot { it in sameValueAsPrefix }
        var checked = 0
        for (phrase in phrases) {
            val words = keys(phrase)
            val whole = NumberWords.decimal(words, "es")
            for (cut in 1 until words.size) {
                for (part in listOf(words.subList(0, cut), words.subList(cut, words.size))) {
                    val value = NumberWords.decimal(part, "es")
                    assertTrue(
                        "'${part.joinToString(" ")}' of '$phrase' must not read as the whole value $whole",
                        value == null || value.compareTo(whole) != 0,
                    )
                    checked++
                }
            }
        }
        assertTrue("the sweep was vacuous: $checked parts", checked > spanishIntegers.size)
    }

    @Test
    fun everyPhraseTableHasRowsInEachClass() {
        assertTrue(spanishIntegers.size >= 40)
        assertTrue(spanishRejects.size >= 30)
        assertTrue(spanishDecimals.size >= 20)
        assertTrue(spanishDecimalRejects.size >= 15)
    }
}
