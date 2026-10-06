package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarResult
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIGHT_ON = "light_on"
private const val LIGHT_OFF = "light_off"
private const val DELETE_ALL = "delete_all"
private const val CLEAR_YEAR = "clear_year"
private const val SET_COUNTER = "set_counter"
private const val SET_LEVEL = "set_level"
private const val SET_MODE = "set_mode"
private const val ADD_NOTE = "add_note"
private const val NOTE_WORDS = 4
private const val BODY_WORDS = 3
private const val LARGEST = 999_999L
private const val PARAGRAPH_WORDS = 5_000
private const val ONE_SECOND_NANOS = 1_000_000_000L
private val NO_ARGS = JsonObject(emptyMap())

private val pack = GrammarPack {
    enFillers("please")
    esFillers("por favor")
    intent(LIGHT_ON) {
        en("turn on the light")
        es("enciende la luz")
    }
    intent(LIGHT_OFF) {
        en("turn off the light")
        es("apaga la luz")
    }
    intent(DELETE_ALL) {
        en("delete everything")
        es("borra todo")
    }
    intent(CLEAR_YEAR) {
        en("clear the year")
        es("borra el año")
    }
    intent(SET_COUNTER) {
        integer("count", 0, LARGEST)
        en("set [the] counter to {count}")
        es("pon el contador en {count}")
    }
    intent(SET_LEVEL) {
        decimal("level", 0.0, 100.0)
        en("set the level to {level}")
        es("pon el nivel en {level}")
    }
    intent(SET_MODE) {
        choice("mode") {
            option("eco") {
                en("economy", "eco mode")
                es("economia", "eco")
            }
            option("turbo") {
                en("turbo")
                es("turbo")
            }
        }
        en("set mode to {mode}")
        es("pon modo {mode}")
    }
    intent(ADD_NOTE) {
        text("body", NOTE_WORDS)
        en("add note {body}")
        es("agrega nota {body}")
    }
}

private class Row(val text: String, val label: String, val code: TraceCode? = null)

private class Positive(val text: String, val label: String, val tool: String, val arguments: JsonObject)

private fun args(name: String, value: Long): JsonObject = JsonObject(mapOf(name to JsonPrimitive(value)))

private fun args(name: String, value: Double): JsonObject = JsonObject(mapOf(name to JsonPrimitive(value)))

private fun args(name: String, value: String): JsonObject = JsonObject(mapOf(name to JsonPrimitive(value)))

private val nearMisses = listOf(
    Row("don't turn on the light", "en"),
    Row("turn on the light and lock the door", "en"),
    Row("turn on the light please now", "en"),
    Row("turn on the light. delete everything", "en"),
    Row("enciende la luz ahora mismo", "es"),
    Row("turn on la luz", "en"),
    Row("set the contador to veintiuno", "en"),
    Row("set the counter to 5%", "en"),
    Row("set the counter to -5", "en"),
    Row("set the counter to 5-10", "en"),
    Row("pon el contador en 1.000", "es"),
    Row("set the counter to a", "en"),
    Row("pon el contador en un", "es"),
    Row("pon el contador en ciento y cinco", "es"),
    Row("set the counter to twenty 1", "en"),
    Row("pon el nivel en dos con medio", "es"),
    Row("pon el contador en veintiuno mil", "es"),
    Row("turn on the", "en"),
    Row("on the light", "en"),
    Row("borra el ano", "es"),
    Row("clear the yeer", "en"),
    Row("set mode to economy and turbo", "en"),
    Row("turn on the light ".repeat(PARAGRAPH_WORDS / 4), "en", TraceCode.GRAMMAR_INPUT_TOO_LONG),
)

private val positives = listOf(
    Positive("turn on the light", "en", LIGHT_ON, NO_ARGS),
    Positive("Turn ON the light please", "en", LIGHT_ON, NO_ARGS),
    Positive("please turn on the light", "en", LIGHT_ON, NO_ARGS),
    Positive("enciende la luz por favor", "es", LIGHT_ON, NO_ARGS),
    Positive("turn off the light", "en", LIGHT_OFF, NO_ARGS),
    Positive("apaga la luz", "es", LIGHT_OFF, NO_ARGS),
    Positive("delete everything", "en", DELETE_ALL, NO_ARGS),
    Positive("borra todo", "es", DELETE_ALL, NO_ARGS),
    Positive("clear the year", "en", CLEAR_YEAR, NO_ARGS),
    Positive("borra el año", "es", CLEAR_YEAR, NO_ARGS),
    Positive("BORRA EL AÑO", "es", CLEAR_YEAR, NO_ARGS),
    Positive("set the counter to 21", "en", SET_COUNTER, args("count", 21L)),
    Positive("set counter to twenty one", "en", SET_COUNTER, args("count", 21L)),
    Positive("set the counter to 2,500", "en", SET_COUNTER, args("count", 2500L)),
    Positive("pon el contador en veintiuno", "es", SET_COUNTER, args("count", 21L)),
    Positive("pon el contador en dos mil quinientos", "es", SET_COUNTER, args("count", 2500L)),
    Positive("set the level to 2.5", "en", SET_LEVEL, args("level", 2.5)),
    Positive("set the level to two and a half", "en", SET_LEVEL, args("level", 2.5)),
    Positive("pon el nivel en dos y medio", "es", SET_LEVEL, args("level", 2.5)),
    Positive("pon el nivel en 2,5", "es", SET_LEVEL, args("level", 2.5)),
    Positive("set mode to economy", "en", SET_MODE, args("mode", "eco")),
    Positive("set mode to eco mode", "en", SET_MODE, args("mode", "eco")),
    Positive("set mode to turbo", "en", SET_MODE, args("mode", "turbo")),
    Positive("pon modo economia", "es", SET_MODE, args("mode", "eco")),
    Positive("pon modo turbo", "es", SET_MODE, args("mode", "turbo")),
    Positive("add note buy milk", "en", ADD_NOTE, args("body", "buy milk")),
    Positive("agrega nota comprar leche", "es", ADD_NOTE, args("body", "comprar leche")),
)

/**
 * GRAM-03 corpora: every near miss of the neutral pack above ends with no match, and the same pack matches every
 * declared phrasing with sample values, so the misses are not vacuous. Also the derived input cap.
 */
class GrammarNearMissCorpusTest {

    @Test
    fun everyNearMissEndsWithNoMatch() {
        for (row in nearMisses) {
            val label = "\"${row.text.take(40)}\" (${row.label})"
            assertNull(label, pack.match(row.text, row.label))
            val result = pack.matchDetailed(row.text, row.label)
            assertTrue(label, result is GrammarResult.Rejected)
            assertEquals(label, row.code, (result as GrammarResult.Rejected).code)
        }
    }

    @Test
    fun everyNearMissAlsoEndsWithNoMatchUnderANullLabel() {
        for (row in nearMisses) {
            assertNull("\"${row.text.take(40)}\"", pack.match(row.text, null))
        }
    }

    @Test
    fun theSamePackMatchesEveryPositiveRowWithTheExpectedToolAndArguments() {
        for (row in positives) {
            val match = pack.match(row.text, row.label)
            assertNotNull("\"${row.text}\" (${row.label})", match)
            assertEquals(row.text, row.tool, match?.toolName)
            assertEquals(row.text, row.arguments, match?.arguments)
            assertEquals(row.text, row.label, match?.matchedLanguage)
        }
    }

    @Test
    fun everyPositiveRowAlsoMatchesUnderANullLabel() {
        for (row in positives) {
            val match = pack.match(row.text, null)
            assertEquals(row.text, row.tool, match?.toolName)
            assertEquals(row.text, row.arguments, match?.arguments)
        }
    }

    private val notePack = GrammarPack {
        enFillers("please")
        intent("note") {
            text("body", BODY_WORDS)
            en("note {body}")
        }
    }

    @Test
    fun aTranscriptOneWordPastTheLongestPhrasingIsTooLongAndTheLongestIsNot() {
        assertNotNull(notePack.match("note a b c", "en"))

        val result = notePack.matchDetailed("note a b c d", "en")

        assertTrue(result.toString(), result is GrammarResult.Rejected)
        assertEquals(TraceCode.GRAMMAR_INPUT_TOO_LONG, (result as GrammarResult.Rejected).code)
        assertNull(notePack.match("note a b c d", "en"))
    }

    @Test
    fun fillersAtTheEdgesDoNotCountAgainstTheCap() {
        assertNotNull(notePack.match("please note a b c please", "en"))
        assertNotNull(notePack.match("please please note a b c", "en"))

        val result = notePack.matchDetailed("please note a b c d please", "en")

        assertEquals(TraceCode.GRAMMAR_INPUT_TOO_LONG, (result as GrammarResult.Rejected).code)
    }

    @Test
    fun aDictatedParagraphIsRejectedPromptlyWithTheSameCode() {
        val paragraph = "note " + "word ".repeat(PARAGRAPH_WORDS)
        val started = System.nanoTime()

        val result = notePack.matchDetailed(paragraph, "en")

        val elapsed = System.nanoTime() - started
        assertEquals(TraceCode.GRAMMAR_INPUT_TOO_LONG, (result as GrammarResult.Rejected).code)
        assertTrue("took $elapsed ns", elapsed < ONE_SECOND_NANOS)
    }

    @Test
    fun theCapComesFromTheLongerOfTheTwoLanguages() {
        val both = GrammarPack {
            intent("go") {
                en("go")
                es("ve a casa ahora mismo")
            }
        }

        assertNotNull(both.match("ve a casa ahora mismo", "es"))
        assertNull(both.match("go there now please friend ok yes", "en"))
        val long = both.matchDetailed("go there now please friend ok yes", "en")
        assertEquals(TraceCode.GRAMMAR_INPUT_TOO_LONG, (long as GrammarResult.Rejected).code)
        val short = both.matchDetailed("go there", "en")
        assertNull((short as GrammarResult.Rejected).code)
    }
}
