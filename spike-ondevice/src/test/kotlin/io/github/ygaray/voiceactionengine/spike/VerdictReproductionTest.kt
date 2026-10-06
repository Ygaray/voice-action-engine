package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.verdict.VerdictRules
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The Gradle half of `scripts/verify-spike-verdict.sh`: reads every `*.txt` file of the evidence directory named by the
 * `vae.spike.evidenceDir` system property (sorted by name, lines in order), evaluates, and writes the rendered lines to the
 * file named by `vae.spike.verdictOut`. With no evidence directory it skips, so a normal test run is unaffected.
 */
class VerdictReproductionTest {
    @Test
    fun recomputesTheVerdictFromTheEvidenceDirectory() {
        val dir = System.getProperty("vae.spike.evidenceDir").orEmpty()
        assumeTrue("vae.spike.evidenceDir is not set", dir.isNotBlank())
        val out = System.getProperty("vae.spike.verdictOut").orEmpty()
        require(out.isNotBlank()) { "vae.spike.verdictOut must name the output file" }
        val files = File(dir).listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?: error("not a directory: $dir")
        val lines = files.sortedBy { it.name }.flatMap { SpikeLine.parseAll(it.readText()) }
        val report = VerdictRules.evaluate(lines)
        File(out).writeText(report.render().joinToString(separator = "\n", postfix = "\n"))
    }
}
