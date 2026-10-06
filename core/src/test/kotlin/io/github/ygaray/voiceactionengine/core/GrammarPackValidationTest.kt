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
}
