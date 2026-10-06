package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceGrammarTest {
    private fun golden(): List<String> {
        val text = checkNotNull(javaClass.getResourceAsStream("/evidence-golden.txt")) { "golden file missing" }
            .bufferedReader().readText()
        return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
    }

    @Test
    fun theFilterScriptCopiesTheAllowPatternByteForByte() {
        // Gradle runs unit tests in the module directory; the script lives at the repository root.
        val script = File("../scripts/spike-evidence-filter.sh")
        assertTrue("the host filter is missing: ${script.absolutePath}", script.isFile)
        val line = script.readLines().firstOrNull { it.startsWith("ALLOW_RE='") }
        assertNotNull("ALLOW_RE line not found in the filter", line)
        assertEquals(ALLOW_PATTERN, line!!.removePrefix("ALLOW_RE='").removeSuffix("'"))
    }

    @Test
    fun theKindAlternationListsExactlyTheKindEnum() {
        val alternation = Regex("""\^VAE_SPIKE_\(([A-Z|]+)\)""").find(ALLOW_PATTERN)!!.groupValues[1]
        assertEquals(SpikeKind.entries.map { it.name }, alternation.split('|'))
    }

    @Test
    fun everyGoldenLineParsesAndRendersByteIdentically() {
        val lines = golden()
        assertTrue(lines.size >= 14)
        for (text in lines) {
            val parsed = SpikeLine.parse(text)
            assertNotNull("golden line does not parse: $text", parsed)
            assertEquals(text, parsed!!.render())
        }
    }

    @Test
    fun theGoldenFileHasEveryKind() {
        val kinds = golden().map { SpikeLine.parse(it)!!.kind }.toSet()
        assertEquals(SpikeKind.entries.toSet(), kinds)
    }

    @Test
    fun goldenTrialLinesRoundTripThroughTrialRecord() {
        val trials = golden().filter { it.startsWith("VAE_SPIKE_TRIAL ") }
        assertEquals(2, trials.size)
        for (text in trials) {
            val record = TrialRecord.fromLine(SpikeLine.parse(text)!!)
            assertNotNull("not a complete trial: $text", record)
            assertEquals(text, record!!.toLine().render())
        }
    }

    @Test
    fun valuesOutsideTheAlphabetRenderAsInvalidToken() {
        assertEquals("invalid_token", SpikeLine.token("has space"))
        assertEquals("invalid_token", SpikeLine.token("say \"hi\""))
        assertEquals("invalid_token", SpikeLine.token("a".repeat(97)))
        assertEquals("invalid_token", SpikeLine.token("line\nbreak"))
        assertEquals("a".repeat(96), SpikeLine.token("a".repeat(96)))
        val line = SpikeLine.of(SpikeKind.ENV, "note" to "turn on the light")
        assertEquals("VAE_SPIKE_ENV note=invalid_token", line.render())
        assertEquals("VAE_SPIKE_ENV invalid_key=x", SpikeLine.of(SpikeKind.ENV, "Bad Key" to "x").render())
    }

    @Test
    fun everyRenderedLineMatchesTheGrammar() {
        val line = SpikeLine.of(SpikeKind.STAGE, "stage" to "init", "result" to "done", "note" to "free text here")
        assertNotNull(SpikeLine.parse(line.render()))
    }

    @Test
    fun parseRejectsCommentsFreeTextAndSpacedValues() {
        assertNull(SpikeLine.parse("# VAE_SPIKE_ENV a=b"))
        assertNull(SpikeLine.parse("turn on the lights"))
        assertNull(SpikeLine.parse("VAE_SPIKE_ENV note=two words"))
        assertNull(SpikeLine.parse("VAE_SPIKE_NOPE a=b"))
        assertNull(SpikeLine.parse("VAE_SPIKE_ENV"))
        assertNull(SpikeLine.parse("VAE_SPIKE_ENV a=" + "x".repeat(97)))
        assertNotNull(SpikeLine.parse("VAE_SPIKE_ENV a=b\r"))
    }

    @Test
    fun theCommittedToolchainEvidenceParsesUnchanged() {
        val file = File("../.planning/phases/13-on-device-model-spike/evidence/toolchain.txt")
        assertTrue("toolchain evidence missing: ${file.absolutePath}", file.isFile)
        val lines = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.isNotEmpty())
        for (text in lines) assertEquals(text, SpikeLine.parse(text)!!.render())
    }
}
