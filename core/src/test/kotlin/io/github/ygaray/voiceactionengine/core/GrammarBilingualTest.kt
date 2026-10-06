package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarMatch
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val SET_COUNTER = "set_counter"
private const val SET_LEVEL = "set_level"
private const val SET_TIMER = "set_timer"
private const val LARGEST = 999_999L

/**
 * ROADMAP SC-1: one intent declares its slots once and its English and Spanish phrasings; a transcript and its
 * counterpart in the other language, each with a spoken number, give the same tool call with equal typed arguments.
 */
class GrammarBilingualTest {

    private val pack = GrammarPack {
        intent(SET_COUNTER) {
            integer("count", 0, LARGEST)
            en("set [the] counter to {count}")
            es("pon el contador en {count}")
        }
        intent(SET_LEVEL) {
            decimal("level", 0.0, 100.0)
            en("set level to {level}")
            es("pon el nivel en {level}")
        }
        intent(SET_TIMER) {
            integer("minutes", 1, 90)
            en("set timer [for {minutes}]")
            es("pon temporizador [por {minutes}]")
        }
    }

    private fun matched(text: String, language: String?): GrammarMatch =
        requireNotNull(pack.match(text, language)) { "no match for \"$text\"" }

    private fun countOf(value: Long): JsonObject = JsonObject(mapOf("count" to JsonPrimitive(value)))

    private fun levelOf(value: Double): JsonObject = JsonObject(mapOf("level" to JsonPrimitive(value)))

    @Test
    fun spokenIntegersGiveTheSameToolCallInBothLanguages() {
        val pairs = listOf(
            Triple("set the counter to twenty one", "pon el contador en veintiuno", 21L),
            Triple("set the counter to two thousand five hundred", "pon el contador en dos mil quinientos", 2500L),
            Triple("set the counter to 21", "pon el contador en 21", 21L),
            Triple("set counter to twenty-one", "pon el contador en veintiuno", 21L),
        )
        for ((english, spanish, value) in pairs) {
            val en = matched(english, "en")
            val es = matched(spanish, "es")
            assertEquals(SET_COUNTER, en.toolName)
            assertEquals(en.toolName, es.toolName)
            assertEquals(countOf(value), en.arguments)
            assertEquals(en.arguments, es.arguments)
            assertEquals("en", en.matchedLanguage)
            assertEquals("es", es.matchedLanguage)
        }
    }

    @Test
    fun aGroupedDigitNumberReadsByThePackLanguage() {
        assertEquals(countOf(1000L), matched("set the counter to 1,000", "en").arguments)
        assertNull(pack.match("pon el contador en 1,000", "es"))
    }

    @Test
    fun spokenDecimalsGiveEqualDoublesInBothLanguages() {
        val en = matched("set level to two and a half", "en")
        val es = matched("pon el nivel en dos y medio", "es")

        assertEquals(SET_LEVEL, en.toolName)
        assertEquals(en.toolName, es.toolName)
        assertEquals(levelOf(2.5), en.arguments)
        assertEquals(en.arguments, es.arguments)
    }

    @Test
    fun aWholeDecimalIsAlwaysADouble() {
        val en = matched("set level to two", "en")
        val es = matched("pon el nivel en 2", "es")

        assertEquals(JsonPrimitive(2.0), en.arguments["level"])
        assertEquals(en.arguments, es.arguments)
    }

    @Test
    fun spanishDecimalSeparatorsFollowTheSpanishRules() {
        assertEquals(levelOf(2.5), matched("pon el nivel en 2,5", "es").arguments)
        assertNull(pack.match("pon el nivel en 1.000", "es"))
        assertEquals(levelOf(2.5), matched("set level to 2.5", "en").arguments)
    }

    @Test
    fun aValueOutsideTheRangeIsNotACandidate() {
        val narrow = GrammarPack {
            intent(SET_COUNTER) {
                integer("count", 1, 10)
                en("set the counter to {count}")
            }
        }

        assertNotNull(narrow.match("set the counter to ten", "en"))
        assertNull(narrow.match("set the counter to twenty", "en"))
        assertNull(narrow.match("set the counter to zero", "en"))
        assertNull(narrow.match("set the counter to 11", "en"))
    }

    @Test
    fun anAbsentLabelTriesBothLanguagesAndKeepsTheLanguageThatMatched() {
        val en = matched("set the counter to twenty one", null)
        val es = matched("pon el contador en veintiuno", null)

        assertEquals(countOf(21L), en.arguments)
        assertEquals("en", en.matchedLanguage)
        assertEquals(countOf(21L), es.arguments)
        assertEquals("es", es.matchedLanguage)
        assertNull(pack.match("pon el contador en twenty one", null))
    }

    @Test
    fun anOptionalSlotIsOmittedWhenNotSpoken() {
        val bare = matched("set timer", "en")
        val spoken = matched("set timer for five", "en")
        val spanish = matched("pon temporizador por cinco", "es")

        assertEquals(0, bare.arguments.size)
        assertEquals(JsonObject(mapOf("minutes" to JsonPrimitive(5L))), spoken.arguments)
        assertEquals(spoken.arguments, spanish.arguments)
        assertEquals(0, matched("pon temporizador", "es").arguments.size)
    }

    @Test
    fun aNonNumberOrAPartialNumberDoesNotMatch() {
        assertNull(pack.match("set the counter to banana", "en"))
        assertNull(pack.match("set the counter to twenty one extra", "en"))
        assertNull(pack.match("set the counter to", "en"))
        assertNull(pack.match("set the counter to 1.5", "en"))
    }
}
