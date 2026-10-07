package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sentinel tests: neither the words nor an unrecognised label may appear in a mapped input's string form. */
class RedactionTest {
    private val sentinelText = "zq-secret-words-7f3a"
    private val sentinelLabel = "zz-secret-label"

    private val oddInputs = listOf("", "   ", "x".repeat(LONG_LENGTH), "\uD800", "\uDC00a", "\u0000")

    @Test
    fun theSegmentPathStringFormHasNoTranscriptButKeepsItsLength() {
        val input = FinalSegment(sentinelText, 1, "es").toCommandInput(Any(), "p")

        assertTrue(input.transcript.contains(sentinelText))
        val form = input.toString()
        assertFalse(form.contains(sentinelText))
        assertTrue(form.contains("transcriptLength=${sentinelText.length}"))
    }

    @Test
    fun anUnrecognisedLabelOnTheSegmentPathGivesNullAndIsNotInTheStringForm() {
        val input = FinalSegment(sentinelText, 1, sentinelLabel).toCommandInput()

        assertNull(input.language)
        assertFalse(input.toString().contains(sentinelLabel))
    }

    @Test
    fun theTextAndLabelEntryPointKeepsTheSameGuarantees() {
        val input = commandInputOf(sentinelText, sentinelLabel, Any(), "p")

        assertTrue(input.transcript.contains(sentinelText))
        assertNull(input.language)
        val form = input.toString()
        assertFalse(form.contains(sentinelText))
        assertFalse(form.contains(sentinelLabel))
        assertTrue(form.contains("transcriptLength=${sentinelText.length}"))
    }

    @Test
    fun mappingNeverThrowsForEmptyHugeOrLoneSurrogateText() {
        for (text in oddInputs) {
            val fromSegment = FinalSegment(text, 0, "en").toCommandInput()
            val fromText = commandInputOf(text, "en")

            assertEquals(text, fromSegment.transcript)
            assertEquals(text, fromText.transcript)
        }
    }

    @Test
    fun normalizingNeverThrowsForTheSameOddInputsUsedAsLabels() {
        for (label in oddInputs) {
            assertNull(normalizeSttLanguageLabel(label))
            assertNotNull(FinalSegment("hello", 0, label).toCommandInput())
        }
    }

    private companion object {
        const val LONG_LENGTH = 10_000
    }
}
