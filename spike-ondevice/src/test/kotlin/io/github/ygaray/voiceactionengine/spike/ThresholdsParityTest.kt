package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ties [Thresholds] to the committed 13-THRESHOLDS.md `THRESHOLD key=value` lines, in both directions. */
class ThresholdsParityTest {
    private fun fileLines(): Map<String, String> {
        val path = System.getProperty("vae.spike.thresholdsFile").orEmpty()
        assertTrue("vae.spike.thresholdsFile is not set", path.isNotBlank())
        val file = File(path)
        assertTrue("thresholds file missing: $path", file.isFile)
        return file.readLines()
            .mapNotNull { Regex("^THRESHOLD ([a-z0-9_]+)=(\\S+)$").matchEntire(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun constants(): Map<String, Any> =
        Thresholds::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.name != "INSTANCE" }
            .associate { field -> field.name.replace(Regex("([A-Z])"), "_$1").lowercase() to field.get(null) }

    @Test
    fun everyThresholdLineHasAnEqualConstant() {
        val constants = constants()
        val lines = fileLines()
        assertTrue("no THRESHOLD lines found", lines.isNotEmpty())
        for ((key, text) in lines) {
            val constant = constants[key]
            assertTrue("no Thresholds constant for $key", constant != null)
            when (constant) {
                is Number -> assertEquals("value of $key", text.toDouble(), constant.toDouble(), 0.0)
                else -> assertEquals("value of $key", text, constant.toString())
            }
        }
    }

    @Test
    fun everyConstantHasAThresholdLine() {
        val lines = fileLines()
        for (key in constants().keys) assertTrue("no THRESHOLD line for the constant $key", key in lines)
    }

    @Test
    fun theLockedFileHasAtLeast24Entries() {
        assertTrue(fileLines().size >= 24)
        assertEquals(fileLines().size, constants().size)
    }
}
