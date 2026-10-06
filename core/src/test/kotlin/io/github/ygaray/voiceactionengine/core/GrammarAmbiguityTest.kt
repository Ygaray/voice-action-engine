package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.FlatRule
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarResult
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleElement
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleMatcher
import io.github.ygaray.voiceactionengine.core.strategy.grammar.RuleVerdict
import io.github.ygaray.voiceactionengine.core.strategy.grammar.SlotSpec
import io.github.ygaray.voiceactionengine.core.strategy.grammar.tokenize
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ADD = "add"
private const val LABEL_WORDS = 5
private const val AMBIGUOUS_TEXT = "add two and a half and more"

/** GRAM-03: two readings of one transcript are never resolved by picking one; they end the tier with a code. */
class GrammarAmbiguityTest {

    // "two and a half" is one decimal, or "two" then "and" then text: two different calls for the same words.
    private val pack = GrammarPack {
        intent(ADD) {
            decimal("count", 0.0, 99.0)
            text("label", LABEL_WORDS)
            en("add {count} and {label}")
        }
    }

    @Test
    fun twoDistinctReadingsAreRejectedAsAmbiguousAndNeverMatched() {
        val result = pack.matchDetailed(AMBIGUOUS_TEXT, "en")

        assertTrue(result.toString(), result is GrammarResult.Rejected)
        assertEquals(TraceCode.GRAMMAR_AMBIGUOUS, (result as GrammarResult.Rejected).code)
        assertNull(pack.match(AMBIGUOUS_TEXT, "en"))
    }

    @Test
    fun oneReadingMatches() {
        val match = pack.match("add two and more", "en")

        assertEquals(ADD, match?.toolName)
        assertEquals("more", match?.arguments?.get("label")?.toString()?.trim('"'))
    }

    @Test
    fun theSameResultReachedByTwoRulesIsOneMatchNotAmbiguity() {
        val slots = mapOf(ADD to mapOf("count" to SlotSpec.IntegerSlot(0, 99)))
        val elements = listOf(RuleElement.Slot("count"), RuleElement.Word("units"))
        val first = FlatRule(ADD, "en", elements, "add:en:0", "{count} units")
        val second = FlatRule(ADD, "en", elements, "add:en:1", "{count} units")

        val matcher = RuleMatcher(listOf(first, second), emptyList(), slots)
        val verdict = matcher.match(tokenize("5 units"), matcher.span)

        assertTrue(verdict.toString(), verdict is RuleVerdict.One)
    }

    @Test
    fun twoExpansionsOfOneTemplateReadingTheSameWordsAreOneMatch() {
        val pack = GrammarPack {
            intent("go") { en("go [now]", "go now") }
        }

        assertEquals("go", pack.match("go now", "en")?.toolName)
        assertEquals("go", pack.match("go", "en")?.toolName)
    }

    @Test
    fun anAmbiguousTranscriptEndsTheTierNoMatchWithTheCodeAndTheNextTierRunsFresh() = runTest {
        NoNetworkGuard.during {
            val resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
            val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
                pack = this@GrammarAmbiguityTest.pack
                this.resolver = resolver
            }
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val pipeline = pipelineOf(listOf(grammar, next), fake, ScriptedGate.admitAll(), RecordingCommitSink())

            val outcome = pipeline.execute(CommandInput(AMBIGUOUS_TEXT, "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertTrue(TraceCode.GRAMMAR_AMBIGUOUS in outcome.trace.codes)
            assertTrue("grammar_ambiguous" in outcome.trace.codes.map { it.value })
            assertEquals(0, resolver.invocations)
            assertEquals(1, next.executions)
            assertEquals(listOf<Any?>(null), next.receivedCarries)
            assertEquals(0, fake.callCount)
        }
    }
}
