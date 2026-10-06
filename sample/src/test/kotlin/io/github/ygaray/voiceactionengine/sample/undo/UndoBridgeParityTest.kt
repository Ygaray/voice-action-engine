package io.github.ygaray.voiceactionengine.sample.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bridge INTEGRATION.md teaches (through the compiled doc region) and the bridge the sample proves end to end are
 * the same text, so the documentation cannot drift from the proof. scripts/verify-docs-coverage.sh already compares
 * the doc block with the region; this test compares the region with the sample's main class.
 */
class UndoBridgeParityTest {
    private val main = File("src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoCommitSink.kt")
    private val docs = File("src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt")

    @Test
    fun theDocumentedBridgeIsTheBridgeTheSampleProves() {
        val proven = dedent(between(main.readLines(), "// undo-bridge:start", "// undo-bridge:end"))
        val documented = dedent(between(docs.readLines(), "// doc-snippet:start undo-bridge", "// doc-snippet:end undo-bridge"))

        assertTrue("the sample bridge region is empty", proven.isNotEmpty())
        assertTrue("the documented bridge region is empty", documented.isNotEmpty())
        assertTrue(proven.any { it.contains("class UndoCommitSink") })
        assertTrue(documented.any { it.contains("class UndoCommitSink") })
        val firstDifference = proven.zip(documented).indexOfFirst { (a, b) -> a != b }
        val line = when {
            firstDifference >= 0 -> firstDifference + 1
            proven.size != documented.size -> minOf(proven.size, documented.size) + 1
            else -> 0
        }
        assertEquals("the regions first differ at line $line", proven, documented)
    }

    private fun between(lines: List<String>, start: String, end: String): List<String> {
        val from = lines.indexOfFirst { it.trim() == start }
        val to = lines.indexOfFirst { it.trim() == end }
        assertTrue("markers '$start' and '$end' must both exist, in order", from >= 0 && to > from)
        return lines.subList(from + 1, to)
    }

    private fun dedent(lines: List<String>): List<String> {
        val indent = lines.filter { it.isNotBlank() }.minOfOrNull { line -> line.takeWhile { it == ' ' || it == '\t' }.length } ?: 0
        return lines.map { if (it.isBlank()) "" else it.substring(indent) }
    }
}
