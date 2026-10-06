package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.FlatRule
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleElement
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleMatcher
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleVerdict
import io.github.ygaray.voiceactionengine.core.strategy.grammar.SlotSpec
import io.github.ygaray.voiceactionengine.core.strategy.grammar.tokenize
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SET_MODE = "set_mode"
private const val OPEN_PAGE = "open_page"
private const val MAX_TITLE_WORDS = 3

/** Choice slots (per-language synonyms, the option id as value) and bounded text slots (the spoken surface text). */
class GrammarSlotsTest {

    private val modes = GrammarPack {
        intent(SET_MODE) {
            choice("mode") {
                option("quiet") {
                    en("quiet", "silent", "very quiet")
                    es("silencio", "silencioso")
                }
                option("loud") {
                    en("loud")
                    es("alto")
                }
            }
            en("set mode to {mode}")
            es("modo {mode}")
        }
    }

    private val pages = GrammarPack {
        intent(OPEN_PAGE) {
            text("title", MAX_TITLE_WORDS)
            en("open page {title}")
            es("abre la página {title}")
        }
    }

    private fun modeOf(id: String): JsonObject = JsonObject(mapOf("mode" to JsonPrimitive(id)))

    private fun titleOf(text: String): JsonObject = JsonObject(mapOf("title" to JsonPrimitive(text)))

    @Test
    fun aChoiceBindsItsOptionIdFromEitherLanguage() {
        assertEquals(modeOf("quiet"), modes.match("set mode to Silent", "en")?.arguments)
        assertEquals(modeOf("quiet"), modes.match("modo silencioso", "es")?.arguments)
        assertEquals(modeOf("loud"), modes.match("set mode to loud", "en")?.arguments)
        assertEquals(modeOf("loud"), modes.match("modo alto", "es")?.arguments)
    }

    @Test
    fun aChoiceSynonymFromTheOtherLanguageIsNotACandidate() {
        assertNull(modes.match("set mode to silencio", "en"))
        assertNull(modes.match("modo loud", "es"))
        assertNull(modes.match("set mode to banana", "en"))
    }

    @Test
    fun aMultiWordSynonymBindsAsOneSpan() {
        assertEquals(modeOf("quiet"), modes.match("set mode to very quiet", "en")?.arguments)
        assertNull(modes.match("set mode to very", "en"))
    }

    @Test
    fun synonymsFoldLikeTranscripts() {
        val accented = GrammarPack {
            intent(SET_MODE) {
                choice("mode") {
                    option("night") {
                        en("night")
                        es("Nocturnó", "de noche")
                    }
                }
                en("set mode to {mode}")
                es("modo {mode}")
            }
        }

        assertEquals(modeOf("night"), accented.match("modo nocturno", "es")?.arguments)
        assertEquals(modeOf("night"), accented.match("MODO DE NOCHE", "es")?.arguments)
        assertEquals(modeOf("night"), accented.match("set mode to NIGHT", "en")?.arguments)
    }

    @Test
    fun aTextSlotKeepsTheSpokenSurfaceText() {
        assertEquals(titleOf("Release Plan"), pages.match("open page Release Plan", "en")?.arguments)
        assertEquals(titleOf("Año Nuevo"), pages.match("abre la página Año Nuevo", "es")?.arguments)
        assertEquals(titleOf("Año Nuevo"), pages.match("abre la pagina Año Nuevo.", "es")?.arguments)
        assertEquals(titleOf("Plan"), pages.match("Open page Plan", "en")?.arguments)
    }

    @Test
    fun aTextSlotIsBoundedByMaxWords() {
        assertNotNull(pages.match("open page one two three", "en"))
        assertNull(pages.match("open page one two three four", "en"))
        assertNull(pages.match("open page", "en"))
    }

    @Test
    fun aTextSlotNeverSwallowsAClause() {
        assertNull(pages.match("open page Plan. delete everything", "en"))
        assertNull(pages.match("open page Plan! open page Other", "en"))
    }

    @Test
    fun aTextSlotIsDelimitedByTheNextLiteral() {
        val titled = GrammarPack {
            intent(OPEN_PAGE) {
                text("title", MAX_TITLE_WORDS)
                en("open {title} page")
            }
        }

        assertEquals(titleOf("Release Plan"), titled.match("open Release Plan page", "en")?.arguments)
        assertNull(titled.match("open Release Plan", "en"))
    }

    @Test
    fun aBoundSlotRecordsItsSpokenSpanForTheNormalizeHook() {
        val rule = FlatRule(
            OPEN_PAGE,
            "en",
            listOf(RuleElement.Word("open"), RuleElement.Word("page"), RuleElement.Slot("title")),
            "$OPEN_PAGE:en:0",
            "open page {title}",
        )
        val matcher = RuleMatcher(listOf(rule), emptyList(), mapOf(OPEN_PAGE to mapOf("title" to SlotSpec.TextSlot(3))))

        val verdict = matcher.match(tokenize("open page  Release,  Plan."))

        assertTrue(verdict is RuleVerdict.One)
        val binding = (verdict as RuleVerdict.One).bindings.single()
        assertEquals("title", binding.name)
        assertEquals("Release,  Plan", binding.raw)
        assertEquals(JsonPrimitive("Release,  Plan"), binding.value)
    }
}
