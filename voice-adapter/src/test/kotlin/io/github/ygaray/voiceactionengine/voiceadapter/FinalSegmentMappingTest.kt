package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FinalSegmentMappingTest {
    @Test
    fun aFinalSegmentBecomesACommandInputWithTheNormalisedLanguage() {
        val input = FinalSegment("hola", 0, "es").toCommandInput()

        assertEquals("hola", input.transcript)
        assertEquals("es", input.language)
        assertNull(input.context)
        assertNull(input.parentRunId)
    }

    @Test
    fun theTextIsPassedVerbatimForPaddedEmptyBlankAndAccentedText() {
        for (text in listOf(" lead and trail \n", "", "   ", "¿qué hora es?")) {
            assertEquals(text, FinalSegment(text, 0, "en").toCommandInput().transcript)
        }
    }

    @Test
    fun theTranscriptIsTheSameStringInstanceAsTheSegmentText() {
        val text = String(charArrayOf('a', 'b', 'c'))

        assertSame(text, FinalSegment(text, 3, "en").toCommandInput().transcript)
    }

    @Test
    fun twoSegmentsDifferingOnlyInSegmentIdMapToTheSameTranscriptAndLanguage() {
        val first = FinalSegment("abre la luz", 1, "es").toCommandInput()
        val second = FinalSegment("abre la luz", 99, "es").toCommandInput()

        assertEquals(first.transcript, second.transcript)
        assertEquals(first.language, second.language)
    }

    @Test
    fun aNullLabelGivesANullLanguage() {
        assertNull(FinalSegment("hello", 0).toCommandInput().language)
        assertNull(FinalSegment("hello", 0, null).toCommandInput().language)
    }

    @Test
    fun labelsOutsideTheClosedSetGiveANullLanguage() {
        for (label in listOf("en-US", "auto", "fr")) {
            assertNull(FinalSegment("hello", 0, label).toCommandInput().language)
        }
    }

    @Test
    fun theTwoArgumentOverloadPassesTheContextByIdentityWithANullParentRun() {
        val context = Any()

        val input = FinalSegment("hello", 0, "en").toCommandInput(context, null)

        assertSame(context, input.context)
        assertNull(input.parentRunId)
    }

    @Test
    fun theTwoArgumentOverloadPassesContextAndParentRunIdThrough() {
        val context = Any()
        val parentRunId = "run-42"

        val input = FinalSegment("hello", 0, "en").toCommandInput(context, parentRunId)

        assertSame(context, input.context)
        assertSame(parentRunId, input.parentRunId)
    }

    @Test
    fun theNoArgumentOverloadHasNullContextAndNullParentRunId() {
        val input = FinalSegment("hello", 0, "en").toCommandInput()

        assertNull(input.context)
        assertNull(input.parentRunId)
    }

    @Test
    fun passingNullForBothArgumentsIsTotal() {
        val input = FinalSegment("hello", 0, "en").toCommandInput(null, null)

        assertNotNull(input)
        assertNull(input.context)
        assertNull(input.parentRunId)
    }
}
