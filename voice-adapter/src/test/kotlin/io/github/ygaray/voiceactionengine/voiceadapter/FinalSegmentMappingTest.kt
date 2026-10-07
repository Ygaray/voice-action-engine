package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
