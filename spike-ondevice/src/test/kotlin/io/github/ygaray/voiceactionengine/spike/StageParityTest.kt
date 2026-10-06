package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The host runner's `STAGES` list and the app's [Stage] wires are two copies of one contract (`am start --es stage <wire>`).
 * This fails when either side changes alone, and when the runner file cannot be found (so the check can never go vacuous).
 */
class StageParityTest {
    private fun runnerFile(): File {
        val candidates = listOf(File("../scripts/run-spike-ondevice.sh"), File("scripts/run-spike-ondevice.sh"))
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("scripts/run-spike-ondevice.sh not found from ${File(".").absoluteFile.normalize()}")
    }

    @Test
    fun theRunnerStagesAreExactlyTheStageWiresInOrder() {
        val line = runnerFile().readLines().firstOrNull { it.startsWith("STAGES=\"") }
            ?: throw AssertionError("run-spike-ondevice.sh has no STAGES=\"...\" line")
        val runnerStages = line.removePrefix("STAGES=\"").substringBefore('"').trim().split(Regex("\\s+"))

        assertEquals(Stage.entries.map { it.wire }, runnerStages)
    }

    @Test
    fun everyWireIsAClosedLowerSnakeWordTheActivityCanMapBack() {
        for (stage in Stage.entries) {
            assertTrue(Regex("[a-z_]+").matches(stage.wire))
            assertEquals(stage, Stage.fromWire(stage.wire))
        }
        assertEquals(null, Stage.fromWire("rm -rf"))
        assertEquals(null, Stage.fromWire(null))
    }
}
