package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIGHT_ON = "light_on"
private const val LIGHT_OFF = "light_off"

/**
 * The grammar tier end to end with neutral fixtures: a declared phrasing reaches the app's tool through the resolver,
 * the gate, the commit and the sink, with no provider call and no provider call id.
 */
class LocalGrammarPipelineTest {

    private val pack = GrammarPack {
        intent(LIGHT_ON) {
            en("turn on the light")
            es("enciende la luz")
        }
        intent(LIGHT_OFF) {
            en("turn off the light")
            es("apaga la luz")
        }
    }

    private class Rig(pack: GrammarPack, next: ScriptedStrategy? = null) {
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)
        val resolver = RecordingResolver { extraction, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(extraction.toolName, StepResult("saved")))))
        }
        val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
            this.pack = pack
            resolver = this@Rig.resolver
        }
        private val second = next ?: singleShot(resolver, snapshotOf(entriesTool()))
        val pipeline = pipelineOf(listOf(grammar, second), fake, gate, sink)
    }

    @Test
    fun anEnglishPhraseResolvesWithNoProviderCallAndNoProviderCallId() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(pack)

            val outcome = rig.pipeline.execute(CommandInput("turn on the light", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(0, rig.fake.callCount)
            val attempt = outcome.trace.attempts.first()
            assertTrue(attempt.turns.isEmpty())
            assertNull(attempt.provider)
            assertEquals(1, rig.resolver.invocations)
            val seen = rig.resolver.extractions.single()
            assertEquals(LIGHT_ON, seen.toolName)
            assertNull(seen.callId)
            assertEquals("en", seen.matchedLanguage)
            assertEquals(ActionKind.COMMITTED, rig.sink.actions.single().action.kind)
            assertNull(outcome.executed.single().providerCallId)
            assertNull(rig.sink.actions.single().action.providerCallId)
            assertEquals(1, rig.gate.calls)
        }
    }

    @Test
    fun aSpanishPhraseResolvesTheSameToolInSpanish() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(pack)

            val outcome = rig.pipeline.execute(CommandInput("Enciende la luz", "es", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(0, rig.fake.callCount)
            val seen = rig.resolver.extractions.single()
            assertEquals(LIGHT_ON, seen.toolName)
            assertEquals("es", seen.matchedLanguage)
            assertNull(outcome.executed.single().providerCallId)
        }
    }

    @Test
    fun caseDoesNotMatter() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(pack)

            val outcome = rig.pipeline.execute(CommandInput("Turn ON the light", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(LIGHT_ON, rig.resolver.extractions.single().toolName)
        }
    }

    @Test
    fun aMissingLanguageLabelMatchesWhenOnePackMatches() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(pack)

            val outcome = rig.pipeline.execute(CommandInput("apaga la luz", null, null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            val seen = rig.resolver.extractions.single()
            assertEquals(LIGHT_OFF, seen.toolName)
            assertEquals("es", seen.matchedLanguage)
        }
    }

    @Test
    fun aPhraseNoRuleMatchesLeavesTheTierNoMatchAndTheNextTierRunsFresh() = runTest {
        NoNetworkGuard.during {
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })
            val rig = Rig(pack, next)

            val outcome = rig.pipeline.execute(CommandInput("turn on the light please", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(0, rig.resolver.invocations)
            assertEquals(0, rig.fake.callCount)
            assertEquals(1, next.executions)
            assertEquals(listOf<Any?>(null), next.receivedCarries)
            assertTrue(rig.sink.actions.isEmpty())
        }
    }

    @Test
    fun aGrammarTierNeedsAPackAndAResolver() {
        val noPack = assertThrows(IllegalArgumentException::class.java) {
            LocalGrammarStrategy(StrategyId("grammar")) {
                resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
            }
        }
        assertTrue(noPack.message.orEmpty(), "pack" in noPack.message.orEmpty())
        val noResolver = assertThrows(IllegalArgumentException::class.java) {
            LocalGrammarStrategy(StrategyId("grammar")) { pack = this@LocalGrammarPipelineTest.pack }
        }
        assertTrue(noResolver.message.orEmpty(), "resolver" in noResolver.message.orEmpty())
    }

    @Test
    fun theTierDeclaresNoProvider() {
        val rig = Rig(pack)

        assertEquals(StrategyCapabilities.NO_PROVIDER, rig.grammar.capabilities)
        assertEquals("LocalGrammarStrategy(id=grammar)", rig.grammar.toString())
    }

    @Test
    fun theMatchIsPureAndNeverGuesses() {
        assertEquals(LIGHT_ON, pack.match("turn on the light", "en")?.toolName)
        assertNull(pack.match("turn on the light", "es"))
        assertNull(pack.match("turn on the", "en"))
        assertNull(pack.match("", null))
        assertNull(pack.match("turn on the light", "fr"))
        assertEquals("GrammarPack(intents=2, enRules=2, esRules=2)", pack.toString())
    }

    @Test
    fun aPackRefusesAmbiguousOrUnsupportedDeclarationsWhenBuilt() {
        assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent("a") { en("go") }
                intent("b") { en("GO") }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { GrammarPack { intent("a") { en("go [now]") } } }
        assertThrows(IllegalArgumentException::class.java) { GrammarPack { intent("a") { } } }
        assertThrows(IllegalArgumentException::class.java) {
            GrammarPack {
                intent("a") { en("go") }
                intent("a") { en("stop") }
            }
        }
    }
}
