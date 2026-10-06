package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private const val SLOT_CANARY = "canaryslot"
private const val SPEECH_CANARY = "canaryspeech"
private const val RANDOM_STRINGS = 1_000
private const val RANDOM_SEED = 20_261_006
private const val MAX_RANDOM_LENGTH = 40

/** What the grammar objects print, and what their build errors say, never holds speech or slot values. */
class GrammarRedactionTest {

    private val pack = GrammarPack {
        intent("open_page") {
            text("title", 3)
            normalize("title") { raw, _ -> raw }
            en("open page {title}")
            es("abre la página {title}")
        }
    }

    private val richPack = GrammarPack {
        intent("set_level") {
            integer("level", 1, 99)
            choice("mode") {
                option("night") {
                    en("evening", "night")
                    es("noche")
                }
            }
            en("set [the] level {level} to {mode}")
            es("pon nivel {level} en {mode}")
        }
        intent("pour") {
            decimal("amount", 0.5, 20.0)
            text("note", 3)
            en("pour {amount} for {note}")
            es("sirve {amount} para {note}")
        }
        intent("stop") {
            en("stop", "stop it now")
            es("para", "detente ya")
        }
        enFillers("please")
        esFillers("por favor")
    }

    @Test
    fun noPrintedFormHoldsTheTranscriptOrASlotValue() = runTest {
        NoNetworkGuard.during {
            val transcript = "open page $SLOT_CANARY $SPEECH_CANARY"
            val match = pack.match(transcript, "en")
            assertNotNull(match)
            val resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
            val tier = LocalGrammarStrategy(StrategyId("grammar")) {
                this.pack = this@GrammarRedactionTest.pack
                this.resolver = resolver
            }
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })
            val pipeline = pipelineOf(
                listOf(tier, next),
                FakeAiProvider(ProviderId.ANTHROPIC),
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
            )
            val outcome = pipeline.execute(CommandInput(transcript, "en", null))
            val extraction = resolver.extractions.single()

            val printed = listOf(
                pack.toString(),
                match.toString(),
                tier.toString(),
                extraction.toString(),
                outcome.toString(),
                outcome.trace.toString(),
            )

            // The values are really there in the typed objects, so the check below is not vacuous.
            assertTrue(match!!.arguments.toString().contains(SLOT_CANARY))
            assertTrue(extraction.arguments.toString().contains(SLOT_CANARY))
            printed.forEach {
                assertFalse(it, it.contains(SLOT_CANARY, ignoreCase = true))
                assertFalse(it, it.contains(SPEECH_CANARY, ignoreCase = true))
            }
        }
    }

    @Test
    fun buildErrorsNameTemplatesSlotsAndToolsOnly() {
        val faults = listOf(
            "go [now" to { GrammarPack { intent("bad_syntax") { en("go [now") } } },
            "no_slot" to { GrammarPack { intent("no_slot") { en("open {title}") } } },
            "unused" to { GrammarPack { intent("unused") { text("title", 2); en("open page") } } },
            "level" to { GrammarPack { intent("level") { integer("level", 5, 1); en("level {level}") } } },
            "twice" to {
                GrammarPack {
                    intent("twice") {
                        text("title", 2)
                        normalize("title") { raw, _ -> raw }
                        normalize("title") { raw, _ -> raw }
                        en("open {title}")
                    }
                }
            },
        )

        // Each message names the template, slot or tool at fault, and nothing else.
        faults.forEach { (named, build) ->
            val message = assertThrows(IllegalArgumentException::class.java) { build() }.message.orEmpty()
            assertTrue(message, named in message)
            assertFalse(message, message.contains(SLOT_CANARY))
        }
    }

    @Test
    fun matchNeverThrowsOnOneThousandRandomStrings() {
        val random = Random(RANDOM_SEED)
        val pool = poolOfCharacters()

        repeat(RANDOM_STRINGS) {
            val text = buildString {
                repeat(random.nextInt(MAX_RANDOM_LENGTH)) { append(pool[random.nextInt(pool.size)]) }
            }
            listOf(null, "en", "es", "fr", "").forEach { label ->
                richPack.match(text, label)
                pack.match(text, label)
            }
        }

        // The grammar still works after the noise: the pack holds no state between matches.
        assertEquals("stop", richPack.match("please stop", "en")?.toolName)
        assertEquals("stop", richPack.match("por favor, para", "es")?.toolName)
    }

    // Control characters, combining marks, accented letters, number words, joiners, an emoji pair and a lone surrogate.
    private fun poolOfCharacters(): List<String> {
        val words = listOf("open", "page", "set", "level", "one", "two", "twenty", "dos", "veinte", "and", "y", "half")
        val marks = listOf("\u0301", "\u0308", "\u0327", "\u0300", "\u036F")
        val controls = (0..0x1F).map { it.toChar().toString() } + listOf("\u007F", "\u0085", "\u200B", "\u200D")
        val punctuation = listOf(" ", ".", ",", "!", "?", ";", ":", "-", "'", "\"", "%", "[", "]", "{", "}", "0", "9")
        val odd = listOf("\uD83D\uDE00", "\uD83D", "\uDE00", "á", "é", "ñ", "ü", "İ", "ß", "\uFFFF", "\u00A0")
        return words.map { " $it " } + marks + controls + punctuation + odd
    }
}
