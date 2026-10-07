package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import io.github.ygaray.voiceactionengine.core.CommandInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// INTEGRATION.md section 12 quotes the region below byte for byte after removing its common indentation;
// scripts/verify-docs-coverage.sh compares them. It lives in this module's own tests, so the snippet is compiled and run
// against the real adapter and :sample needs no dependency on :voice-adapter or :stt. The region holds only what a
// consumer would write; the assertions stay outside it.

// doc-snippet:start adapter-wiring
// One final segment from :stt becomes one command. The language is "en", "es" or null (unknown): the adapter never
// guesses. The transcript is passed verbatim, so guard a blank one yourself before you call execute.
fun commandFor(segment: FinalSegment, replyingTo: String? = null): CommandInput? =
    if (segment.text.isBlank()) null else segment.toCommandInput(context = null, parentRunId = replyingTo)

// Without the :stt types on your classpath (another capture path, or a test), build the same input from plain text and
// the label you were given.
fun commandForText(text: String, label: String?): CommandInput = commandInputOf(text, label)

// The label rule on its own, for example to ask the user for a language when the speech engine reported none.
fun languageIsKnown(segment: FinalSegment): Boolean = normalizeSttLanguageLabel(segment.language) != null
// doc-snippet:end adapter-wiring

/** Runs the adapter-wiring region against the real adapter. */
class DocSnippetAdapterTest {
    @Test
    fun aFinalSegmentBecomesACommandWithTheNormalisedLanguageAndTheParentRun() {
        val input = commandFor(FinalSegment("add paper to my list", 7, " ES "), replyingTo = "run-1")

        assertNotNull(input)
        assertEquals("add paper to my list", input!!.transcript)
        assertEquals("es", input.language)
        assertNull(input.context)
        assertEquals("run-1", input.parentRunId)
    }

    @Test
    fun noParentRunIsTheDefaultAndABlankTranscriptIsGuarded() {
        val input = commandFor(FinalSegment("hello", 0, "en"))

        assertNull(input!!.parentRunId)
        assertEquals("en", input.language)
        assertNull(commandFor(FinalSegment("   ", 0, "en")))
        assertNull(commandFor(FinalSegment("", 0, "en")))
    }

    @Test
    fun anUnknownLabelIsNullAndNeverGuessed() {
        for (label in listOf("en-US", "auto", "fr", "", null)) {
            val segment = FinalSegment("hello", 0, label)

            assertNull(commandFor(segment)!!.language)
            assertFalse(languageIsKnown(segment))
        }
        assertTrue(languageIsKnown(FinalSegment("hello", 0, "en")))
    }

    @Test
    fun theTextFormMatchesTheSegmentFormWithoutAnySttType() {
        val fromText = commandForText("add paper to my list", " EN ")
        val fromSegment = commandFor(FinalSegment("add paper to my list", 3, " EN "))!!

        assertEquals(fromSegment.transcript, fromText.transcript)
        assertEquals("en", fromText.language)
        assertEquals(fromSegment.language, fromText.language)
        assertNull(commandForText("hello", null).language)
        val text = String(charArrayOf('a', 'b'))
        assertSame(text, commandForText(text, "en").transcript)
    }
}
