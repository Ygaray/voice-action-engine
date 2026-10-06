package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarResult
import io.github.ygaray.voiceactionengine.core.strategy.grammar.foldKey
import io.github.ygaray.voiceactionengine.core.strategy.grammar.tokenize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The one text fold that transcripts and declared phrasings share: case, vowel accents, apostrophes, hyphens and edge
 * punctuation, with the near misses that must stay one token or must reject the transcript.
 */
class GrammarTextTest {

    private fun keys(text: String): List<String> = tokenize(text).tokens.map { it.key }

    @Test
    fun caseSpacingAndEdgePunctuationDoNotChangeTheKeys() {
        val plain = tokenize("Enciende   la Luz.")
        assertEquals(listOf("enciende", "la", "luz"), plain.tokens.map { it.key })
        assertFalse(plain.clauseBreak)
        assertEquals(listOf("enciende", "la", "luz"), keys("¡Enciende la luz!"))
        assertEquals(listOf("enciende", "la", "luz"), keys("“Enciende” la luz…"))
    }

    @Test
    fun vowelAccentsFoldButTheNTildeIsKept() {
        assertEquals("dieciseis", foldKey("Dieciséis"))
        assertEquals("veintiun", foldKey("veintiún"))
        assertEquals("año", foldKey("AÑO"))
        assertNotEquals(foldKey("año"), foldKey("ano"))
        assertEquals("aeiou", foldKey("àëîöü"))
    }

    @Test
    fun aDecomposedTildeComposesBeforeFolding() {
        val decomposed = "año"
        assertEquals("año", foldKey(decomposed))
        assertEquals(listOf("año"), keys(decomposed))
    }

    @Test
    fun hyphensSplitOnlyBetweenTwoLetters() {
        assertEquals(listOf("twenty", "one"), keys("twenty-one"))
        assertEquals(listOf("e", "mail"), keys("e-mail"))
        assertEquals(listOf("twenty", "one"), keys("twenty–one"))
        assertEquals(listOf("5-10"), keys("5-10"))
        assertEquals(listOf("-5"), keys("-5"))
        assertEquals(listOf("twenty-1"), keys("twenty-1"))
    }

    @Test
    fun apostrophesLeaveTheKeyButStayInTheSurface() {
        val tokens = tokenize("Don't")
        assertEquals(listOf("dont"), tokens.tokens.map { it.key })
        assertEquals("Don't", tokens.surface(0, 1))
        assertEquals(listOf("dont"), keys("don’t"))
    }

    @Test
    fun symbolsAndInteriorSeparatorsSurvive() {
        assertEquals(listOf("5%"), keys("5%"))
        assertEquals(listOf("2.5"), keys("2.5"))
        assertEquals(listOf("1,000"), keys("1,000"))
        assertEquals(listOf("1/2"), keys("1/2"))
        assertEquals(listOf("21"), keys("¿21?"))
    }

    @Test
    fun aSentenceTerminatorBetweenWordsIsAClauseBreak() {
        assertTrue(tokenize("turn on the light. delete everything").clauseBreak)
        assertTrue(tokenize("turn on the light! delete everything").clauseBreak)
        assertTrue(tokenize("turn on the light; delete everything").clauseBreak)
        assertTrue(tokenize("turn on the light ? delete everything").clauseBreak)
        assertTrue(tokenize("turn on the light . delete everything").clauseBreak)
    }

    @Test
    fun aCommaOrATrailingTerminatorIsNotAClauseBreak() {
        assertFalse(tokenize("turn on the light, please").clauseBreak)
        assertFalse(tokenize("turn on the light.").clauseBreak)
        assertFalse(tokenize("turn on the light . ").clauseBreak)
        assertFalse(tokenize("add 2.5 apples").clauseBreak)
        assertFalse(tokenize("add 1,000 apples").clauseBreak)
    }

    @Test
    fun anEmptyOrPunctuationOnlyTranscriptHasNoTokens() {
        assertTrue(tokenize("").tokens.isEmpty())
        assertTrue(tokenize("  ... ").tokens.isEmpty())
        assertFalse(tokenize("  ... ").clauseBreak)
    }

    @Test
    fun surfaceKeepsCaseAccentsAndInteriorPunctuation() {
        val tokens = tokenize("Say ¡Hola, Mundo! now")
        assertEquals(listOf("say", "hola", "mundo", "now"), tokens.tokens.map { it.key })
        assertEquals("Hola, Mundo", tokens.surface(1, 3))
        assertEquals("2.5", tokenize("Pay 2.5 now").surface(1, 2))
        assertEquals("twenty-one", tokenize("twenty-one").surface(0, 2))
        assertEquals("Café", tokenize("Café").surface(0, 1))
    }

    @Test
    fun theFoldIsLocaleInvariant() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("light", foldKey("LIGHT"))
            assertEquals(listOf("light", "ice"), keys("LIGHT ICE"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    private val pack = GrammarPack {
        intent("light_on") {
            en("turn on the light")
            es("enciende la luz")
        }
    }

    @Test
    fun aPackMatchesRegardlessOfCaseAccentsAndPunctuation() {
        assertNotNull(pack.match("¡Enciende la Luz!", "es"))
        assertNotNull(pack.match("ENCIENDE LA LUZ", "es"))
        assertNotNull(pack.match("Turn ON the light.", "en"))
    }

    @Test
    fun aPackRejectsATranscriptWithAnExtraClause() {
        assertNull(pack.match("turn on the light. delete everything", "en"))
        val result = pack.matchDetailed("turn on the light. delete everything", "en")
        assertTrue(result is GrammarResult.Rejected)
        assertNull((result as GrammarResult.Rejected).code)
    }
}
