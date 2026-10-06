package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANARY = "canary-normalize-7f3a"

/** The per-slot normalize hook: an app synonym map changes what resolves; null rejects; a throw is contained. */
class GrammarNormalizeTest {

    private val seen = ArrayList<Pair<String, String>>()

    // CT's cross-language map, with neutral words: both languages' words map to one canonical value.
    private val synonyms = mapOf(
        "en" to mapOf("lamp" to "lamp"),
        "es" to mapOf("lámpara" to "lamp"),
    )

    private fun mapped(raw: String, language: String): String? {
        seen.add(raw to language)
        return synonyms[language]?.get(raw.lowercase())
    }

    private val pack = GrammarPack {
        intent("turn_on") {
            text("device", 2)
            normalize("device") { raw, language -> mapped(raw, language) }
            en("turn on the {device}")
            es("enciende la {device}")
        }
    }

    private fun args(device: String): JsonObject = JsonObject(mapOf("device" to JsonPrimitive(device)))

    @Test
    fun anAppMapTurnsEnglishAndSpanishWordsIntoOneCanonicalValue() {
        assertEquals(args("lamp"), pack.match("turn on the Lamp", "en")?.arguments)
        assertEquals(args("lamp"), pack.match("enciende la Lámpara", "es")?.arguments)
        // The hook saw the words as spoken, accents and case kept, and the pack's own language.
        assertEquals(listOf("Lamp" to "en", "Lámpara" to "es"), seen)
    }

    @Test
    fun theHookGetsTheLanguageOfThePackThatMatchedNeverNull() {
        val match = pack.match("enciende la Lámpara", null)

        assertNotNull(match)
        assertEquals("es", match?.matchedLanguage)
        assertEquals(listOf("Lámpara" to "es"), seen)
    }

    @Test
    fun aWordOutsideTheMapRejectsTheMatch() {
        assertNull(pack.match("turn on the heater", "en"))
        assertNull(pack.match("enciende la lámpara", "en"))
    }

    @Test
    fun aBlankAnswerRejectsLikeNull() = runTest {
        val blank = GrammarPack {
            intent("turn_on") {
                text("device", 2)
                normalize("device") { _, _ -> "  " }
                en("turn on the {device}")
            }
        }

        val outcome = run(blank, "turn on the lamp").outcome

        assertNull(blank.match("turn on the lamp", "en"))
        assertTrue(TraceCode.GRAMMAR_SLOT_REJECTED in outcome.trace.codes)
    }

    @Test
    fun aThrowingHookRejectsWithItsOwnCodeAndLeaksNothing() = runTest {
        val throwing = GrammarPack {
            intent("turn_on") {
                text("device", 2)
                normalize("device") { _, _ -> throw IllegalStateException(CANARY) }
                en("turn on the {device}")
            }
        }

        val run = run(throwing, "turn on the lamp")

        assertNull(throwing.match("turn on the lamp", "en"))
        assertTrue(run.outcome.toString(), TraceCode.GRAMMAR_NORMALIZE_ERROR in run.outcome.trace.codes)
        assertFalse(TraceCode.STRATEGY_ERROR in run.outcome.trace.codes)
        assertFalse(run.outcome.toString().contains(CANARY))
        assertFalse(run.outcome.trace.codes.joinToString { it.toString() }.contains(CANARY))
        assertTrue(run.outcome is CommandOutcome.Completed)
    }

    @Test
    fun theHookRunsOncePerCandidatePackThatParsed() {
        var calls = 0
        val both = GrammarPack {
            intent("turn_on") {
                text("device", 2)
                normalize("device") { raw, _ ->
                    calls++
                    raw.lowercase()
                }
                en("turn on the {device}")
                es("turn on the {device}", "enciende la {device}")
            }
        }

        both.match("turn on the lamp", "en")
        assertEquals(1, calls)

        calls = 0
        both.match("turn on the lamp", null)
        assertEquals(2, calls)

        calls = 0
        both.match("enciende la lampara", null)
        assertEquals(1, calls)

        calls = 0
        both.match("something else entirely", null)
        assertEquals(0, calls)
    }

    @Test
    fun oneRefusingPackRefusesTheCommandWithNoFallbackToTheOther() = runTest {
        val split = GrammarPack {
            intent("turn_on") {
                text("device", 2)
                normalize("device") { raw, language -> if (language == "en") raw.lowercase() else null }
                en("turn on the {device}")
                es("turn on the {device}")
            }
        }

        val run = run(split, "turn on the lamp", language = null)

        assertNull(split.match("turn on the lamp", null))
        assertTrue(TraceCode.GRAMMAR_SLOT_REJECTED in run.outcome.trace.codes)
        assertEquals(0, run.resolver.invocations)
    }

    @Test
    fun agreementIsComparedAfterNormalize() {
        val agree = GrammarPack {
            intent("turn_on") {
                text("device", 2)
                normalize("device") { raw, _ -> raw.lowercase() }
                en("turn on the {device}")
                es("turn on the {device}")
            }
        }

        // Raw text is the same in both packs, and so is the normalized value: one reading, labeled by the command.
        val match = agree.match("turn on the Lamp", null)

        assertEquals(args("lamp"), match?.arguments)
        assertNull(match?.matchedLanguage)
    }

    @Test
    fun differentNormalizedValuesAcrossPacksAreAmbiguousNotAGuess() = runTest {
        val differ = GrammarPack {
            intent("turn_on") {
                text("device", 2)
                normalize("device") { raw, language -> raw.lowercase() + "-" + language }
                en("turn on the {device}")
                es("turn on the {device}")
            }
        }

        val run = run(differ, "turn on the lamp", language = null)

        assertNull(differ.match("turn on the lamp", null))
        assertTrue(TraceCode.GRAMMAR_AMBIGUOUS in run.outcome.trace.codes)
    }

    @Test
    fun aChoiceHookGetsTheSpokenSynonymAndItsAnswerReplacesTheOptionId() {
        var raw: String? = null
        val modes = GrammarPack {
            intent("set_mode") {
                choice("mode") {
                    option("night") {
                        en("evening", "night")
                        es("noche")
                    }
                }
                normalize("mode") { spoken, _ ->
                    raw = spoken
                    spoken.uppercase()
                }
                en("set mode {mode}")
                es("pon modo {mode}")
            }
        }

        val match = modes.match("set mode Evening", "en")

        assertEquals("Evening", raw)
        assertEquals(JsonObject(mapOf("mode" to JsonPrimitive("EVENING"))), match?.arguments)
    }

    @Test
    fun anOutOfMapTokenFallsThroughToTheNextTierWithNoCarryAndNoProviderCall() = runTest {
        NoNetworkGuard.during {
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })

            val run = run(pack, "turn on the heater", next = next)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertTrue(TraceCode.GRAMMAR_SLOT_REJECTED in run.outcome.trace.codes)
            assertEquals(0, run.resolver.invocations)
            assertEquals(1, next.executions)
            assertEquals(listOf<Any?>(null), next.receivedCarries)
            assertEquals(0, run.fake.callCount)
        }
    }

    @Test
    fun aMappedWordReachesTheResolverAsTheCanonicalValue() = runTest {
        NoNetworkGuard.during {
            val run = run(pack, "enciende la Lámpara", language = "es")

            assertEquals(args("lamp"), run.resolver.extractions.single().arguments)
            assertEquals("es", run.resolver.extractions.single().matchedLanguage)
        }
    }

    @Test
    fun normalizeIsRefusedWhenTheBuildCannotHonorIt() {
        val onInteger = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent("set_level") {
                    integer("level", 1, 9)
                    normalize("level") { raw, _ -> raw }
                    en("set level {level}")
                }
            }
        }
        assertTrue(onInteger.message.orEmpty(), "set_level" in onInteger.message.orEmpty())
        assertTrue(onInteger.message.orEmpty(), "level" in onInteger.message.orEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent("set_level") {
                    decimal("level", 0.5, 9.0)
                    normalize("level") { raw, _ -> raw }
                    en("set level {level}")
                }
            }
        }
        val undeclared = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent("open_page") {
                    text("title", 3)
                    normalize("missing") { raw, _ -> raw }
                    en("open page {title}")
                }
            }
        }
        assertTrue(undeclared.message.orEmpty(), "missing" in undeclared.message.orEmpty())
        val twice = assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent("open_page") {
                    text("title", 3)
                    normalize("title") { raw, _ -> raw }
                    normalize("title") { raw, _ -> raw }
                    en("open page {title}")
                }
            }
        }
        assertTrue(twice.message.orEmpty(), "title" in twice.message.orEmpty())
    }

    private class Run(val outcome: CommandOutcome, val resolver: RecordingResolver, val fake: FakeAiProvider)

    private suspend fun run(
        grammarPack: GrammarPack,
        transcript: String,
        language: String? = "en",
        next: ScriptedStrategy = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") }),
    ): Run {
        val resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
        val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
            pack = grammarPack
            this.resolver = resolver
        }
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)
        val pipeline = pipelineOf(listOf(grammar, next), fake, ScriptedGate.admitAll(), RecordingCommitSink())
        return Run(pipeline.execute(CommandInput(transcript, language, null)), resolver, fake)
    }
}
