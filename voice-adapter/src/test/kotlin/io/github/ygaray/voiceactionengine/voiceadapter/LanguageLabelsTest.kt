package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageLabelsTest {
    @Test
    fun theClosedSetLabelsMapToThemselves() {
        assertEquals("en", normalizeSttLanguageLabel("en"))
        assertEquals("es", normalizeSttLanguageLabel("es"))
    }

    @Test
    fun caseAndSurroundingWhitespaceAreIgnored() {
        assertEquals("es", normalizeSttLanguageLabel(" ES "))
        assertEquals("en", normalizeSttLanguageLabel("En"))
        assertEquals("es", normalizeSttLanguageLabel("eS\n"))
        assertEquals("en", normalizeSttLanguageLabel("\tEN"))
    }

    @Test
    fun labelsOutsideTheSetMapToNull() {
        val outside = listOf("en-US", "en_US", "es-419", "auto", "", "   ", "fr", "zz", "english", "ENG")
        for (label in outside) {
            assertNull(normalizeSttLanguageLabel(label))
        }
    }

    @Test
    fun aNullLabelMapsToNullNeverADefault() {
        assertNull(normalizeSttLanguageLabel(null))
    }

    @Test
    fun theResultIsAlwaysEnEsOrNull() {
        val mixed = listOf(null, "en", "es", "EN", " es ", "en-US", "auto", "", "fr", "x", "\u0000", "és")
        for (label in mixed) {
            val result = normalizeSttLanguageLabel(label)
            assertTrue(result == null || result == "en" || result == "es")
        }
    }

    @Test
    fun normalisingANormalisedValueIsIdempotent() {
        for (label in listOf(null, "en", "es", " ES ", "auto", "fr")) {
            val once = normalizeSttLanguageLabel(label)
            assertEquals(once, normalizeSttLanguageLabel(once))
        }
    }

    @Test
    fun commandInputOfNormalisesItsLabel() {
        assertEquals("es", commandInputOf("hola", " ES ").language)
        assertNull(commandInputOf("hola", "es-419").language)
        assertNull(commandInputOf("hola", null).language)
    }

    @Test
    fun commandInputOfPassesContextAndParentRunIdThroughByIdentity() {
        val context = Any()
        val parentRunId = "run-7"

        val withContext = commandInputOf("hello", "en", context)
        val withBoth = commandInputOf("hello", "en", context, parentRunId)

        assertSame(context, withContext.context)
        assertNull(withContext.parentRunId)
        assertSame(context, withBoth.context)
        assertSame(parentRunId, withBoth.parentRunId)
    }

    @Test
    fun commandInputOfKeepsTheTextVerbatim() {
        val padded = String(" padded text \n".toCharArray())

        assertSame(padded, commandInputOf(padded, "en").transcript)
    }

    @Test
    fun commandInputOfAgreesWithTheSegmentPathForTheSameTextAndLabel() {
        for (label in listOf(null, "en", "es", "ES", "en-US", "auto", "fr")) {
            val fromText = commandInputOf("same words", label)
            val fromSegment = FinalSegment("same words", 0, label).toCommandInput()

            assertEquals(fromSegment.transcript, fromText.transcript)
            assertEquals(fromSegment.language, fromText.language)
        }
    }
}
