package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app resolver's verdicts on a grammar match pass through unchanged; a rejection never guesses or carries. */
class LocalGrammarVerdictsTest {

    private val pack = GrammarPack {
        intent("light_on") {
            en("turn on the light")
            es("enciende la luz")
        }
    }

    private class Run(val outcome: CommandOutcome, val next: ScriptedStrategy, val fake: FakeAiProvider)

    private suspend fun run(answer: Resolution): Run {
        val resolver = RecordingResolver { _, _ -> answer }
        val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
            pack = this@LocalGrammarVerdictsTest.pack
            this.resolver = resolver
        }
        val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)
        val pipeline = pipelineOf(listOf(grammar, next), fake, ScriptedGate.admitAll(), RecordingCommitSink())
        return Run(pipeline.execute(CommandInput("turn on the light", "en", null)), next, fake)
    }

    @Test
    fun aResolverRejectionEndsTheTierNoMatchWithACodeAndAClearedCarry() = runTest {
        NoNetworkGuard.during {
            val run = run(Resolution.NoMatch())

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertTrue(TraceCode.GRAMMAR_RESOLVER_REJECTED in run.outcome.trace.codes)
            assertEquals("no_match", run.outcome.trace.attempts.first().outcome)
            assertEquals(1, run.next.executions)
            assertEquals(listOf<Any?>(null), run.next.receivedCarries)
            assertEquals(0, run.fake.callCount)
        }
    }

    @Test
    fun aResolverEscalationPassesItsCarryToTheNextTier() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val run = run(Resolution.Escalate(EscalationReason.NoToolCall(), carry))

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(listOf<Any?>(carry), run.next.receivedCarries)
            assertTrue(TraceCode.GRAMMAR_RESOLVER_REJECTED !in run.outcome.trace.codes)
        }
    }
}
