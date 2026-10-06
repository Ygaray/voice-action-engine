package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.FlatRule
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleElement
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleMatcher
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleVerdict
import io.github.ygaray.voiceactionengine.core.strategy.grammar.tokenize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIGHT_ON = "light_on"

/**
 * The template mini-syntax in both languages: literals, optional words, alternatives and sub-rules, the build-time
 * faults, and the anchored matcher that refuses when two distinct results exist.
 */
class GrammarTemplateTest {

    private fun enPack(vararg templates: String): GrammarPack = GrammarPack { intent(LIGHT_ON) { en(*templates) } }

    private fun GrammarPack.hits(text: String, language: String): Boolean = match(text, language) != null

    private fun assertRefused(expectedInMessage: String, build: () -> Unit) {
        val fault = assertThrows(IllegalArgumentException::class.java) { build() }
        assertTrue(fault.message.orEmpty(), expectedInMessage in fault.message.orEmpty())
    }

    @Test
    fun alternativesAndOptionalWordsMatchTheirVariants() {
        val pack = enPack("(turn|switch) on [the] light")

        assertTrue(pack.hits("turn on the light", "en"))
        assertTrue(pack.hits("switch on light", "en"))
        assertTrue(pack.hits("Switch on THE light.", "en"))
    }

    @Test
    fun theMatchIsAnchoredAtBothEnds() {
        val pack = enPack("(turn|switch) on [the] light")

        assertFalse(pack.hits("turn on", "en"))
        assertFalse(pack.hits("turn on the light now", "en"))
        assertFalse(pack.hits("please turn on the light", "en"))
        assertFalse(pack.hits("turn the light", "en"))
    }

    @Test
    fun aSubRuleIsReferencedFromTheSameLanguage() {
        val en = GrammarPack {
            enRule("article", "(the|a)")
            intent(LIGHT_ON) { en("open <article> door") }
        }
        val es = GrammarPack {
            esRule("articulo", "(la|una)")
            intent(LIGHT_ON) { es("abre <articulo> puerta") }
        }

        assertTrue(en.hits("open a door", "en"))
        assertTrue(en.hits("open the door", "en"))
        assertFalse(en.hits("open door", "en"))
        assertTrue(es.hits("abre una puerta", "es"))
        assertTrue(es.hits("Abre LA puerta", "es"))
    }

    @Test
    fun aSubRuleMayHaveSeveralTemplatesAndNestOtherRules() {
        val pack = GrammarPack {
            enRule("polite", "please", "kindly")
            enRule("verb", "turn on", "switch on [<polite>]")
            intent(LIGHT_ON) { en("<verb> the light") }
        }

        assertTrue(pack.hits("turn on the light", "en"))
        assertTrue(pack.hits("switch on the light", "en"))
        assertTrue(pack.hits("switch on please the light", "en"))
        assertFalse(pack.hits("turn on please the light", "en"))
    }

    @Test
    fun spanishTemplatesFoldAccentsOnBothSides() {
        val pack = GrammarPack { intent(LIGHT_ON) { es("abre la página [por favor]") } }

        assertTrue(pack.hits("ABRE LA PAGINA", "es"))
        assertTrue(pack.hits("abre la página por favor", "es"))
        assertFalse(pack.hits("abre la pagina ahora", "es"))
    }

    @Test
    fun aTemplateCannotReferenceARuleOfTheOtherLanguage() {
        assertRefused("open <articulo> door") {
            GrammarPack {
                esRule("articulo", "(la|una)")
                intent(LIGHT_ON) { en("open <articulo> door") }
            }
        }
    }

    @Test
    fun syntaxFaultsFailAtBuildNamingTheTemplate() {
        val faulty = listOf(
            "(a|)", "[]", "()", "a | b", "[open", "open]", "{Count}", "<missing>", "(a|b", "go }", "go >",
        )
        (faulty + "").forEach {
            val fault = assertThrows("template: '$it'", IllegalArgumentException::class.java) { enPack(it) }
            assertTrue("template '$it' message: ${fault.message}", it in fault.message.orEmpty())
        }
    }

    @Test
    fun aTemplateWithNoLiteralWordFails() {
        assertRefused("[the]") { enPack("[the]") }
        assertRefused("[a] [b]") { enPack("[a] [b]") }
    }

    @Test
    fun aSlotNeverAppearsInASubRuleAndAnUndeclaredSlotFails() {
        assertRefused("{x}") { enPack("add {x}") }
        assertRefused("{x}") {
            GrammarPack {
                enRule("amount", "{x}")
                intent(LIGHT_ON) { en("add <amount>") }
            }
        }
    }

    @Test
    fun aRuleCycleOrAnUnknownRuleFails() {
        assertRefused("cycle") {
            GrammarPack {
                enRule("a", "go <b>")
                enRule("b", "stop <a>")
                intent(LIGHT_ON) { en("run <a>") }
            }
        }
        assertRefused("cycle") {
            GrammarPack {
                enRule("a", "go <a>")
                intent(LIGHT_ON) { en("run <a>") }
            }
        }
        assertRefused("<nowhere>") { enPack("go <nowhere>") }
    }

    @Test
    fun ruleDeclarationFaultsFail() {
        assertRefused("Bad") { GrammarPack { enRule("Bad", "go"); intent(LIGHT_ON) { en("go") } } }
        assertRefused("dup") {
            GrammarPack {
                enRule("dup", "go")
                enRule("dup", "stop")
                intent(LIGHT_ON) { en("<dup> now") }
            }
        }
        assertRefused("hollow") { GrammarPack { enRule("hollow"); intent(LIGHT_ON) { en("go") } } }
    }

    @Test
    fun anExpansionPastTheBoundFailsAndSuggestsSubRules() {
        val atBound = (1..8).joinToString(" ") { "(a$it|b$it)" }
        val pastBound = (1..9).joinToString(" ") { "(a$it|b$it)" }

        assertNotNull(enPack(atBound))
        assertRefused("sub-rule") { enPack(pastBound) }
    }

    @Test
    fun identicalExpansionsOfOneIntentAreOneMatch() {
        val pack = enPack("(go|go) now", "go now", "go [now]")

        assertEquals(LIGHT_ON, pack.match("go now", "en")?.toolName)
        assertEquals(LIGHT_ON, pack.match("go", "en")?.toolName)
    }

    private fun flat(tool: String, vararg words: String): FlatRule =
        FlatRule(tool, "en", words.map { RuleElement.Word(it) }, "$tool:en:0", words.joinToString(" "))

    private fun RuleMatcher.match(keys: List<String>): RuleVerdict = match(tokenize(keys.joinToString(" ")), span)

    private fun matcherOf(vararg rules: FlatRule): RuleMatcher = RuleMatcher(rules.toList(), emptyList(), emptyMap())

    @Test
    fun twoDistinctResultsInOneLanguageAreAmbiguous() {
        val matcher = matcherOf(flat("first_tool", "go", "now"), flat("second_tool", "go", "now"))

        assertTrue(matcher.match(listOf("go", "now")) is RuleVerdict.Ambiguous)
    }

    @Test
    fun theSameResultFromTwoRulesIsOneResult() {
        val first = flat("first_tool", "go", "now")
        val matcher = matcherOf(first, flat("first_tool", "go", "now"), flat("other_tool", "stop"))

        val verdict = matcher.match(listOf("go", "now"))

        assertTrue(verdict is RuleVerdict.One)
        assertSame(first, (verdict as RuleVerdict.One).rule)
        assertTrue(matcher.match(listOf("go")) is RuleVerdict.None)
        assertTrue(matcher.match(listOf("go", "now", "now")) is RuleVerdict.TooLong)
        assertTrue(matcher.match(emptyList()) is RuleVerdict.None)
    }
}
