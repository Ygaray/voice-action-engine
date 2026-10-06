package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val LIGHT_ON = "light_on"

/** Fillers are declared per language, strip only at the edges, and the engine ships none. */
class GrammarFillersTest {

    private val enPack = GrammarPack {
        enFillers("please", "uh", "could you")
        intent(LIGHT_ON) { en("turn on the light") }
    }

    @Test
    fun leadingAndTrailingFillersAreStrippedLongestFirstAndRepeatedly() {
        assertEquals(LIGHT_ON, enPack.match("uh please turn on the light please", "en")?.toolName)
        assertEquals(LIGHT_ON, enPack.match("could you turn on the light", "en")?.toolName)
        assertEquals(LIGHT_ON, enPack.match("Uh, uh, UH turn on the light, please!", "en")?.toolName)
    }

    @Test
    fun aTerminatorNextToAStrippedFillerDoesNotVetoTheMatch() {
        val pack = GrammarPack {
            enFillers("okay", "thanks")
            intent(LIGHT_ON) { en("turn on the light") }
        }

        assertEquals(LIGHT_ON, pack.match("Okay. Turn on the light", "en")?.toolName)
        assertEquals(LIGHT_ON, pack.match("Turn on the light. Thanks.", "en")?.toolName)
        assertEquals(LIGHT_ON, pack.match("Okay! Turn on the light? Thanks!", "en")?.toolName)
    }

    @Test
    fun aTerminatorBetweenKeptWordsStillVetoesTheMatch() {
        val pack = GrammarPack {
            enFillers("okay", "thanks")
            intent(LIGHT_ON) { en("turn on the light") }
        }

        assertNull(pack.match("Okay. Turn on. The light", "en"))
        assertNull(pack.match("Turn on the light. Turn on the light", "en"))
        assertNull(pack.match("Turn on the light. Okay turn on the light", "en"))
    }

    @Test
    fun anInteriorFillerIsNotStripped() {
        assertNull(enPack.match("turn please on the light", "en"))
        assertNull(enPack.match("turn on the please light", "en"))
    }

    @Test
    fun anInteriorOptionalWordIsWrittenInTheTemplate() {
        val pack = GrammarPack {
            enFillers("please")
            intent(LIGHT_ON) { en("turn [please] on the light") }
        }

        assertEquals(LIGHT_ON, pack.match("turn please on the light", "en")?.toolName)
        assertEquals(LIGHT_ON, pack.match("please turn on the light", "en")?.toolName)
    }

    @Test
    fun spanishFillersApplyOnlyToSpanishAndEnglishOnesNeverToSpanish() {
        val pack = GrammarPack {
            enFillers("please")
            esFillers("por favor", "eh")
            intent(LIGHT_ON) {
                en("turn on the light")
                es("enciende la luz")
            }
        }

        assertEquals(LIGHT_ON, pack.match("eh enciende la luz por favor", "es")?.toolName)
        assertNull(pack.match("please enciende la luz", "es"))
        assertNull(pack.match("eh turn on the light", "en"))
        assertEquals(LIGHT_ON, pack.match("please turn on the light", "en")?.toolName)
    }

    @Test
    fun aTranscriptOfOnlyFillersDoesNotMatch() {
        assertNull(enPack.match("uh please", "en"))
        assertNull(enPack.match("could you", "en"))
        assertNull(enPack.match("", "en"))
    }

    @Test
    fun aPackWithNoFillersStripsNothing() {
        val pack = GrammarPack { intent(LIGHT_ON) { en("turn on the light") } }

        assertNull(pack.match("please turn on the light", "en"))
        assertNull(pack.match("turn on the light please", "en"))
        assertEquals(LIGHT_ON, pack.match("turn on the light", "en")?.toolName)
    }

    @Test
    fun fillersFoldLikeTranscripts() {
        val pack = GrammarPack {
            esFillers("Por Favor", "¡Oye!")
            intent(LIGHT_ON) { es("enciende la luz") }
        }

        assertEquals(LIGHT_ON, pack.match("oye enciende la luz por favor", "es")?.toolName)
    }

    @Test
    fun anOverlappingFillerPairStripsWithoutCrossingTheMiddle() {
        val pack = GrammarPack {
            enFillers("ok")
            intent(LIGHT_ON) { en("go") }
        }

        assertEquals(LIGHT_ON, pack.match("ok go ok", "en")?.toolName)
        assertNull(pack.match("ok ok", "en"))
    }
}
