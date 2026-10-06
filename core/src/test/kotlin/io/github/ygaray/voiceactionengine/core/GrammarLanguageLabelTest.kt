package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarResult
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIGHT_ON = "light_on"
private const val ONLY_EN = "only_en"
private const val ONLY_ES = "only_es"
private const val SHARED = "shared"
private const val SET_LEVEL = "set_level"
private const val FALSE_EN = "false_en"
private const val FALSE_ES = "false_es"
private const val LARGEST = 99_999.0
private val BAD_LABELS = listOf("EN", "en-US", "", " es", "fr", "ES", "en ", "spanish")

/**
 * The language-label table: a labeled command reads its own pack, the other one only when asked, a command without a
 * label reads both and needs them to agree, and any label this library does not read matches nothing.
 */
class GrammarLanguageLabelTest {

    private fun packOf(tryOther: Boolean): GrammarPack = GrammarPack {
        tryOtherLanguage = tryOther
        intent(LIGHT_ON) {
            en("turn on the light")
            es("enciende la luz")
        }
        intent(ONLY_EN) { en("hello there") }
        intent(ONLY_ES) { es("hola amigo") }
        intent(SHARED) {
            en("okay")
            es("okay")
        }
        intent(SET_LEVEL) {
            decimal("level", 0.0, LARGEST)
            en("level {level}")
            es("level {level}")
        }
        intent(FALSE_EN) { en("sale") }
        intent(FALSE_ES) { es("sale") }
    }

    private val strict = packOf(false)
    private val open = packOf(true)

    private fun codeOf(pack: GrammarPack, text: String, label: String?): TraceCode? {
        val result = pack.matchDetailed(text, label)
        assertTrue(result.toString(), result is GrammarResult.Rejected)
        return (result as GrammarResult.Rejected).code
    }

    private fun levelOf(value: Double): JsonObject = JsonObject(mapOf("level" to JsonPrimitive(value)))

    @Test
    fun aLabeledCommandReadsOnlyItsOwnLanguageByDefault() {
        assertEquals("en", strict.match("turn on the light", "en")?.matchedLanguage)
        assertEquals("es", strict.match("enciende la luz", "es")?.matchedLanguage)
        assertNull(strict.match("hola amigo", "en"))
        assertNull(strict.match("hello there", "es"))
        assertNull(strict.match("enciende la luz", "en"))
    }

    @Test
    fun aLabeledCommandWithTryOtherLanguageAlsoReadsTheOtherPack() {
        val spanish = open.match("hola amigo", "en")
        assertEquals(ONLY_ES, spanish?.toolName)
        assertEquals("es", spanish?.matchedLanguage)
        assertNotNull(spanish?.ruleId)
        val english = open.match("hello there", "es")
        assertEquals(ONLY_EN, english?.toolName)
        assertEquals("en", english?.matchedLanguage)
    }

    @Test
    fun whenBothPacksAgreeALabeledCommandKeepsItsLabelAndHasNoRuleId() {
        val en = open.match("okay", "en")
        assertEquals(SHARED, en?.toolName)
        assertEquals("en", en?.matchedLanguage)
        assertNull(en?.ruleId)
        val es = open.match("okay", "es")
        assertEquals("es", es?.matchedLanguage)
        assertNull(es?.ruleId)
    }

    @Test
    fun whenBothPacksMatchDifferentToolsTheTranscriptIsAmbiguous() {
        assertEquals(TraceCode.GRAMMAR_AMBIGUOUS, codeOf(open, "sale", "en"))
        assertEquals(TraceCode.GRAMMAR_AMBIGUOUS, codeOf(open, "sale", "es"))
        assertEquals(TraceCode.GRAMMAR_AMBIGUOUS, codeOf(strict, "sale", null))
        assertEquals(TraceCode.GRAMMAR_AMBIGUOUS, codeOf(open, "sale", null))
        assertNull(open.match("sale", null))
    }

    @Test
    fun aMissingLabelTriesBothPacksWhateverTheFlagSays() {
        for (pack in listOf(strict, open)) {
            val english = pack.match("turn on the light", null)
            assertEquals(LIGHT_ON, english?.toolName)
            assertEquals("en", english?.matchedLanguage)
            assertNotNull(english?.ruleId)
            val spanish = pack.match("hola amigo", null)
            assertEquals(ONLY_ES, spanish?.toolName)
            assertEquals("es", spanish?.matchedLanguage)
            val both = pack.match("okay", null)
            assertEquals(SHARED, both?.toolName)
            assertNull(both?.matchedLanguage)
            assertNull(both?.ruleId)
        }
    }

    @Test
    fun anyLabelOtherThanEnEsOrNullMatchesNothingWithTheUnsupportedCode() {
        for (pack in listOf(strict, open)) {
            for (label in BAD_LABELS) {
                assertNull("label \"$label\"", pack.match("turn on the light", label))
                assertEquals(
                    "label \"$label\"",
                    TraceCode.GRAMMAR_LANGUAGE_UNSUPPORTED,
                    codeOf(pack, "turn on the light", label),
                )
            }
        }
    }

    @Test
    fun aGroupedDigitNumberThatTwoLanguagesReadDifferentlyUnderANullLabelIsAmbiguous() {
        assertEquals(TraceCode.GRAMMAR_AMBIGUOUS, codeOf(strict, "level 1,000", null))
        assertEquals(levelOf(1000.0), strict.match("level 1,000", "en")?.arguments)
        assertEquals(levelOf(1.0), strict.match("level 1,000", "es")?.arguments)
    }

    @Test
    fun aCommaDecimalOnlyTheSpanishPackReadsMatchesInSpanishUnderANullLabel() {
        val match = strict.match("level 1,5", null)

        assertEquals(levelOf(1.5), match?.arguments)
        assertEquals("es", match?.matchedLanguage)
    }

    @Test
    fun aPointDecimalBothPacksReadTheSameHasNoLanguageUnderANullLabel() {
        val match = strict.match("level 1.5", null)

        assertEquals(levelOf(1.5), match?.arguments)
        assertNull(match?.matchedLanguage)
        assertNull(match?.ruleId)
    }

    @Test
    fun aFalseFriendThatMeansDifferentToolsInTwoLanguagesIsNeverTheFirstPacksResult() {
        assertNull(strict.match("sale", null))
        assertEquals(FALSE_EN, strict.match("sale", "en")?.toolName)
        assertEquals(FALSE_ES, strict.match("sale", "es")?.toolName)
    }

    @Test
    fun thePureMatchIsTheDecisionTheTierActsOn() {
        val rows = listOf(
            "turn on the light" to "en", "hola amigo" to "en", "okay" to null, "sale" to null,
            "level 1,000" to null, "turn on the light" to "fr",
        )
        for (pack in listOf(strict, open)) {
            for ((text, label) in rows) {
                val decided = (pack.matchDetailed(text, label) as? GrammarResult.Matched)?.match
                assertEquals("$text/$label", decided?.toString(), pack.match(text, label)?.toString())
            }
        }
    }

    @Test
    fun theTierPassesTheMatchedLanguageOfAnOtherLanguageMatchToTheResolver() = runTest {
        NoNetworkGuard.during {
            val resolver = RecordingResolver { extraction, _ ->
                Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(extraction.toolName, StepResult("saved")))))
            }
            val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
                pack = open
                this.resolver = resolver
            }
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> error("next tier must not run") })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val pipeline = pipelineOf(listOf(grammar, next), fake, ScriptedGate.admitAll(), RecordingCommitSink())

            val outcome = pipeline.execute(CommandInput("hola amigo", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals("es", resolver.extractions.single().matchedLanguage)
            assertEquals(ONLY_ES, resolver.extractions.single().toolName)
        }
    }

    @Test
    fun anUnsupportedLabelRecordsTheCodeAndTheNextTierRunsFresh() = runTest {
        NoNetworkGuard.during {
            val resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
            val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
                pack = strict
                this.resolver = resolver
            }
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val pipeline = pipelineOf(listOf(grammar, next), fake, ScriptedGate.admitAll(), RecordingCommitSink())

            val outcome = pipeline.execute(CommandInput("turn on the light", "fr", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertTrue(TraceCode.GRAMMAR_LANGUAGE_UNSUPPORTED in outcome.trace.codes)
            assertEquals(0, resolver.invocations)
            assertEquals(1, next.executions)
        }
    }
}
