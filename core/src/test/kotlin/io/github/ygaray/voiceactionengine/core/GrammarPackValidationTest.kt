package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOOL_A = "tool_a"
private const val TOOL_B = "tool_b"

/** The template-level authoring faults a pack refuses at build, and the declarations it must accept. */
class GrammarPackValidationTest {

    private fun refused(build: () -> Unit): String {
        val fault = assertThrows(IllegalArgumentException::class.java) { build() }
        return fault.message.orEmpty()
    }

    @Test
    fun aTemplateThatBeginsWithADeclaredFillerIsRefused() {
        val message = refused {
            GrammarPack {
                enFillers("please")
                intent(TOOL_A) { en("please stop") }
            }
        }
        assertTrue(message, "please stop" in message)
        assertTrue(message, "please" in message)
    }

    @Test
    fun aTemplateThatEndsWithADeclaredFillerIsRefused() {
        val message = refused {
            GrammarPack {
                esFillers("por favor")
                intent(TOOL_A) { es("detente por favor") }
            }
        }
        assertTrue(message, "detente por favor" in message)
    }

    @Test
    fun aFillerAtTheEdgeOfAnyExpansionIsRefused() {
        refused {
            GrammarPack {
                enFillers("please")
                intent(TOOL_A) { en("(stop|please go)") }
            }
        }
        refused {
            GrammarPack {
                enFillers("could you")
                intent(TOOL_A) { en("stop [could you]") }
            }
        }
    }

    @Test
    fun aFillerOfAnotherLanguageDoesNotRestrictTemplates() {
        val pack = GrammarPack {
            esFillers("eh")
            intent(TOOL_A) { en("eh stop") }
        }

        assertEquals(TOOL_A, pack.match("eh stop", "en")?.toolName)
    }

    @Test
    fun aBlankOrWordlessFillerIsRefused() {
        refused { GrammarPack { enFillers("  "); intent(TOOL_A) { en("stop") } } }
        refused { GrammarPack { esFillers(""); intent(TOOL_A) { en("stop") } } }
        refused { GrammarPack { enFillers("..."); intent(TOOL_A) { en("stop") } } }
    }

    @Test
    fun twoToolsReadingTheSameWordsInOneLanguageAreRefusedNamingBoth() {
        val message = refused {
            GrammarPack {
                intent(TOOL_A) { en("turn on") }
                intent(TOOL_B) { en("Turn   ON!") }
            }
        }
        assertTrue(message, TOOL_A in message && TOOL_B in message)
    }

    @Test
    fun twoToolsThatDifferOnlyByAnAccentAreRefused() {
        val message = refused {
            GrammarPack {
                intent(TOOL_A) { es("sí") }
                intent(TOOL_B) { es("si") }
            }
        }
        assertTrue(message, TOOL_A in message && TOOL_B in message)
    }

    @Test
    fun twoToolsReachingTheSameWordsThroughDifferentExpansionsAreRefused() {
        refused {
            GrammarPack {
                intent(TOOL_A) { en("go (now|later)") }
                intent(TOOL_B) { en("go [now]", "go later") }
            }
        }
    }

    @Test
    fun aDuplicateToolNameAndAnIntentWithNoTemplateAreRefused() {
        val twice = refused {
            GrammarPack {
                intent(TOOL_A) { en("go") }
                intent(TOOL_A) { en("stop") }
            }
        }
        assertTrue(twice, TOOL_A in twice)
        val empty = refused { GrammarPack { intent(TOOL_A) { } } }
        assertTrue(empty, TOOL_A in empty)
        refused { GrammarPack { intent(" ") { en("go") } } }
    }

    @Test
    fun theSameSequenceTwiceInsideOneIntentIsKeptOnce() {
        val pack = GrammarPack { intent(TOOL_A) { en("go now", "Go Now", "(go|go) now") } }

        assertEquals(TOOL_A, pack.match("go now", "en")?.toolName)
        assertEquals("GrammarPack(intents=1, enRules=1, esRules=0)", pack.toString())
    }

    @Test
    fun theSameWordsInBothLanguagesForDifferentToolsAreAccepted() {
        val pack = GrammarPack {
            intent(TOOL_A) { en("no") }
            intent(TOOL_B) { es("no") }
        }

        assertEquals(TOOL_A, pack.match("no", "en")?.toolName)
        assertEquals(TOOL_B, pack.match("no", "es")?.toolName)
        assertNull(pack.match("no", null))
    }

    @Test
    fun aMatchNeverThrowsForAnyTranscript() {
        val pack = GrammarPack { intent(TOOL_A) { en("(turn|switch) on [the] light") } }
        val odd = listOf(
            "", " ", "\u0000", "\uD800", "[]{}<>|", "((((", "....", "a".repeat(10_000), "turn on ".repeat(2_000),
            "‮", "😀 turn on the light",
        )

        odd.forEach { pack.match(it, "en"); pack.match(it, null); pack.match(it, "es") }
        assertNotNull(pack.match("turn on the light", "en"))
    }

    private fun assertNamed(fault: IllegalArgumentException, vararg parts: String) {
        val message = fault.message.orEmpty()
        parts.forEach { assertTrue("message lacks \"$it\": $message", it in message) }
    }

    @Test
    fun aSlotNameThatIsNotLowerCaseIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("Count", 0, 9); en("go {count}") } }
        }
        assertNamed(fault, TOOL_A, "Count")
    }

    @Test
    fun aSlotDeclaredTwiceIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 9); text("count", 2); en("go {count}") } }
        }
        assertNamed(fault, TOOL_A, "count")
    }

    @Test
    fun anIntegerWithMinAboveMaxIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 9, 1); en("go {count}") } }
        }
        assertNamed(fault, TOOL_A, "count")
    }

    @Test
    fun anIntegerBelowZeroOrAboveTheNumberRangeIsRefused() {
        val below = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", -1, 9); en("go {count}") } }
        }
        assertNamed(below, TOOL_A, "count")
        val above = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 1_000_000); en("go {count}") } }
        }
        assertNamed(above, TOOL_A, "count")
    }

    @Test
    fun aDecimalWithMinAboveMaxIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { decimal("level", 2.0, 1.0); en("go {level}") } }
        }
        assertNamed(fault, TOOL_A, "level")
    }

    @Test
    fun aDecimalWithABoundThatIsNotFiniteIsRefused() {
        val notANumber = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { decimal("level", Double.NaN, 1.0); en("go {level}") } }
        }
        assertNamed(notANumber, TOOL_A, "level")
        val infinite = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { decimal("level", 0.0, Double.POSITIVE_INFINITY); en("go {level}") } }
        }
        assertNamed(infinite, TOOL_A, "level")
    }

    @Test
    fun aDecimalOutsideTheNumberRangeIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { decimal("level", -0.5, 1.0); en("go {level}") } }
        }
        assertNamed(fault, TOOL_A, "level")
        val above = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { decimal("level", 0.0, 1_000_000.0); en("go {level}") } }
        }
        assertNamed(above, TOOL_A, "level")
    }

    @Test
    fun aTextSlotWithNoWordsAllowedIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { text("title", 0); en("open {title}") } }
        }
        assertNamed(fault, TOOL_A, "title")
    }

    @Test
    fun aTextSlotWithAnUnboundedWordCountIsRefusedAtBuild() {
        // Int.MAX_VALUE as an "unbounded" idiom would overflow the span sums and silently stop every match.
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { text("title", Int.MAX_VALUE); en("open {title}") } }
        }
        assertNamed(fault, TOOL_A, "title")
        val above = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { text("title", 65); en("open {title}") } }
        }
        assertNamed(above, TOOL_A, "title")
    }

    @Test
    fun aTextSlotAtTheCeilingStillMatchesAfterALeadingWord() {
        val pack = GrammarPack { intent(TOOL_A) { text("title", 64); en("open {title} now") } }
        assertNotNull(pack.match("open one two three now", "en"))
    }

    @Test
    fun aDeclaredSlotNoTemplateUsesIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 9); en("go") } }
        }
        assertNamed(fault, TOOL_A, "count")
    }

    @Test
    fun aRequiredSlotMissingFromASpanishPhrasingIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 9); en("go {count}"); es("ve") } }
        }
        assertNamed(fault, TOOL_A, "count", "ve")
    }

    @Test
    fun aRequiredSlotMissingFromOneEnglishPhrasingIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 9); en("go {count}", "stop") } }
        }
        assertNamed(fault, TOOL_A, "count", "stop")
    }

    @Test
    fun aRequiredSlotMissingFromOneAlternativeOfAGroupIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 9); en("(go {count}|stop)") } }
        }
        assertNamed(fault, TOOL_A, "count", "(go {count}|stop)")
    }

    @Test
    fun aSlotRepeatedInOneReadingIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("count", 0, 9); en("go {count} then {count}") } }
        }
        assertNamed(fault, TOOL_A, "count", "go {count} then {count}")
    }

    @Test
    fun twoAdjacentNumberSlotsAreRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("a", 0, 9); integer("b", 0, 9); en("add {a} {b}") } }
        }
        assertNamed(fault, TOOL_A, "add {a} {b}")
    }

    @Test
    fun aNumberSlotNextToATextSlotIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { integer("a", 0, 9); text("t", 2); en("add {a} {t}") } }
        }
        assertNamed(fault, TOOL_A, "add {a} {t}")
    }

    @Test
    fun twoTextSlotsSeparatedOnlyByAnOptionalWordAreRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { text("a", 2); text("b", 2); en("move {a} [to] {b}") } }
        }
        assertNamed(fault, TOOL_A, "move {a} [to] {b}")
    }

    @Test
    fun moreThanFourSlotsInOneTemplateIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent(TOOL_A) {
                    listOf("a", "b", "c", "d", "e").forEach { integer(it, 0, 9) }
                    en("x {a} y {b} z {c} u {d} v {e}")
                }
            }
        }
        assertNamed(fault, TOOL_A, "x {a} y {b} z {c} u {d} v {e}")
    }

    @Test
    fun aChoiceWithNoOptionIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { choice("mode") { }; en("set {mode}") } }
        }
        assertNamed(fault, TOOL_A, "mode")
    }

    @Test
    fun aChoiceOptionWithABlankIdIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack { intent(TOOL_A) { choice("mode") { option(" ") { en("quiet") } }; en("set {mode}") } }
        }
        assertNamed(fault, TOOL_A, "mode")
    }

    @Test
    fun aChoiceOptionIdDeclaredTwiceIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent(TOOL_A) {
                    choice("mode") {
                        option("quiet") { en("quiet") }
                        option("quiet") { en("silent") }
                    }
                    en("set {mode}")
                }
            }
        }
        assertNamed(fault, TOOL_A, "mode", "quiet")
    }

    @Test
    fun aChoiceOptionWithNoSynonymInALanguageTheIntentUsesIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent(TOOL_A) {
                    choice("mode") { option("quiet") { en("quiet") } }
                    en("set {mode}")
                    es("modo {mode}")
                }
            }
        }
        assertNamed(fault, TOOL_A, "mode", "quiet", "es")
    }

    @Test
    fun aFoldedSynonymUnderTwoOptionIdsIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent(TOOL_A) {
                    choice("answer") {
                        option("yes") { es("sí") }
                        option("maybe") { es("SI") }
                    }
                    es("responde {answer}")
                }
            }
        }
        assertNamed(fault, TOOL_A, "answer", "yes", "maybe")
    }

    @Test
    fun aBlankChoiceSynonymIsRefused() {
        val fault = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent(TOOL_A) { choice("mode") { option("quiet") { en("quiet", "  ") } }; en("set {mode}") }
            }
        }
        assertNamed(fault, TOOL_A, "mode", "quiet")
    }

    @Test
    fun aSlotOnlyInsideBracketsInSomeTemplatesIsOptionalAndItsKeyIsOmitted() {
        val pack = GrammarPack {
            intent(TOOL_A) {
                integer("count", 0, 9)
                en("go", "go [{count}]")
                es("ve [{count}]")
            }
        }

        assertEquals(0, pack.match("go", "en")?.arguments?.size)
        assertEquals(1, pack.match("go 5", "en")?.arguments?.size)
        assertEquals(0, pack.match("ve", "es")?.arguments?.size)
    }

    @Test
    fun aChoiceNextToANumberSlotAndFourSlotsAreAccepted() {
        val pack = GrammarPack {
            intent(TOOL_A) {
                choice("mode") { option("quiet") { en("quiet") } }
                integer("count", 0, 9)
                integer("other", 0, 9)
                text("title", 2)
                en("set {mode} {count} then {other} name {title}")
            }
        }

        val match = pack.match("set quiet 5 then 6 name Release Plan", "en")

        assertNotNull(match)
        assertEquals(4, match?.arguments?.size)
    }

    @Test
    fun aChoiceSynonymMayEqualAWordOfAnotherPhrasing() {
        val pack = GrammarPack {
            intent(TOOL_A) { choice("mode") { option("off") { en("stop") } }; en("set {mode}") }
            intent(TOOL_B) { en("stop") }
        }

        assertEquals(TOOL_B, pack.match("stop", "en")?.toolName)
        assertEquals(TOOL_A, pack.match("set stop", "en")?.toolName)
    }

    @Test
    fun anOptionWithNoSynonymInAnUnusedLanguageIsAccepted() {
        val pack = GrammarPack {
            intent(TOOL_A) { choice("mode") { option("quiet") { en("quiet") } }; en("set {mode}") }
        }

        assertEquals(1, pack.match("set quiet", "en")?.arguments?.size)
    }

    @Test
    fun aLiteralPhrasingThatAnotherRulesTextSlotAlsoReadsIsRefusedNamingBoth() {
        val message = refused {
            GrammarPack {
                intent("clear_all") { en("remove all") }
                intent("remove_item") {
                    text("item", 3)
                    en("remove {item}")
                }
            }
        }

        assertTrue(message, "remove all" in message && "remove {item}" in message)
        assertTrue(message, "clear_all" in message && "remove_item" in message)
    }

    @Test
    fun theOverlapIsRefusedInSpanishToo() {
        val message = refused {
            GrammarPack {
                intent(TOOL_A) { es("borra todo") }
                intent(TOOL_B) {
                    text("item", 3)
                    es("borra {item}")
                }
            }
        }

        assertTrue(message, "borra todo" in message && "borra {item}" in message)
    }

    @Test
    fun aChoiceSynonymEqualToAnotherIntentsLiteralInTheSameFrameIsRefused() {
        val message = refused {
            GrammarPack {
                intent(TOOL_A) { choice("mode") { option("calm") { en("quiet") } }; en("set mode {mode}") }
                intent(TOOL_B) { en("set mode quiet") }
            }
        }

        assertTrue(message, "set mode {mode}" in message && "set mode quiet" in message)
        assertTrue(message, TOOL_A in message && TOOL_B in message)
    }

    @Test
    fun aNumberSlotRuleAndALiteralRuleThatCannotReadEachOthersWordsAreAccepted() {
        val pack = GrammarPack {
            intent(TOOL_A) { integer("count", 1, 9); en("take {count}") }
            intent(TOOL_B) { en("take all") }
        }

        assertEquals(TOOL_B, pack.match("take all", "en")?.toolName)
        assertEquals(TOOL_A, pack.match("take 3", "en")?.toolName)
    }

    @Test
    fun aLiteralRuleThatANumberAndTextRuleAlsoReadsIsRefused() {
        refused {
            GrammarPack {
                intent(TOOL_A) {
                    decimal("count", 0.0, 99.0)
                    text("label", 5)
                    en("add {count} and {label}")
                }
                intent(TOOL_B) { en("add 0 and zqx") }
            }
        }
    }
}
