package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RESOLVER_CANARY = "canary-resolver-91c2"

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

    @Test
    fun aResolverFailureEndsTheRunFailedWithItsReasonAndNoNextTier() = runTest {
        NoNetworkGuard.during {
            val run = run(Resolution.Failed(FailureReason.Auth(), null))

            val failed = run.outcome as CommandOutcome.Failed
            assertEquals("auth", failed.reason.code)
            assertEquals(0, run.next.executions)
            assertEquals(0, run.fake.callCount)
        }
    }

    @Test
    fun aResolverThatThrowsEndsFailedUnexpectedWithAStrategyErrorAndNoMessage() = runTest {
        NoNetworkGuard.during {
            val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
                pack = this@LocalGrammarVerdictsTest.pack
                resolver = RecordingResolver { _, _ -> throw IllegalStateException(RESOLVER_CANARY) }
            }
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("done") })
            val pipeline = pipelineOf(
                listOf(grammar, next),
                FakeAiProvider(ProviderId.ANTHROPIC),
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
            )

            val outcome = pipeline.execute(CommandInput("turn on the light", "en", null))

            val failed = outcome as CommandOutcome.Failed
            assertEquals(FailureReason.Unexpected("IllegalStateException"), failed.reason)
            assertTrue(TraceCode.STRATEGY_ERROR in outcome.trace.codes)
            assertEquals(0, next.executions)
            assertFalse(outcome.toString().contains(RESOLVER_CANARY))
        }
    }

    @Test
    fun aGrammarTierAfterATierThatAlreadyCommittedNeverRunsAndTheEscalationIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("write", StepResult("done", false, "ok", emptyMap()))
            val first = ScriptedStrategy(
                StrategyId("first"),
                { _, session ->
                    session.submit(ToolStep.Mutation(write))
                    StrategyOutcome.Escalate(EscalationReason.ModelDeclined(), null)
                },
            )
            val resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
            val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
                pack = this@LocalGrammarVerdictsTest.pack
                this.resolver = resolver
            }
            val pipeline = pipelineOf(
                listOf(first, grammar),
                FakeAiProvider(ProviderId.ANTHROPIC),
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
            )

            val outcome = pipeline.execute(CommandInput("turn on the light", "en", null))

            assertTrue((outcome as CommandOutcome.Completed).partial)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals("escalation_suppressed", outcome.trace.attempts.single().outcome)
            assertEquals(0, resolver.invocations)
        }
    }

    private val terminalPack = GrammarPack {
        intent("open_page") {
            text("title", 3)
            terminal()
            en("open page {title}")
            es("abre la página {title}")
        }
        intent("light_on") {
            en("turn on the light")
            es("enciende la luz")
        }
    }

    private class TerminalRun(
        val outcome: CommandOutcome,
        val resolver: RecordingResolver,
        val gate: ScriptedGate,
        val sink: RecordingCommitSink,
        val next: ScriptedStrategy,
    )

    private suspend fun terminalRun(transcript: String, language: String?, policy: TierPolicy): TerminalRun {
        val resolver = RecordingResolver { _, _ -> Resolution.NoMatch() }
        val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
            pack = terminalPack
            this.resolver = resolver
        }
        val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("next") })
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)
        val pipeline = pipelineOf(listOf(grammar, next), fake, gate, sink, policy = policy)
        return TerminalRun(pipeline.execute(CommandInput(transcript, language, null)), resolver, gate, sink, next)
    }

    private fun assertEndedWithTheNavigation(run: TerminalRun, title: String) {
        val completed = run.outcome as CommandOutcome.Completed
        assertNull(completed.reply)
        val call = checkNotNull(completed.terminalCall)
        assertEquals("open_page", call.toolName)
        assertEquals(JsonObject(mapOf("title" to JsonPrimitive(title))), call.arguments)
        assertTrue(completed.executed.isEmpty())
        assertTrue(run.sink.actions.isEmpty())
        assertEquals(0, run.resolver.invocations)
        assertEquals(0, run.gate.calls)
        assertEquals(0, run.next.executions)
    }

    @Test
    fun aTerminalIntentEndsHandledWithATerminalCallAndTouchesNeitherResolverNorGate() = runTest {
        NoNetworkGuard.during {
            val english = terminalRun("open page Release Plan", "en", TierPolicy.DEFAULT)
            assertEndedWithTheNavigation(english, "Release Plan")
            assertEndedWithTheNavigation(
                terminalRun("abre la página Release Plan", "es", TierPolicy.DEFAULT),
                "Release Plan",
            )
        }
    }

    @Test
    fun aTerminalIntentIsHandledUnderOfflineOnly() = runTest {
        NoNetworkGuard.during {
            val run = terminalRun("open page Release Plan", "en", TierPolicy { offlineOnly = true })

            assertEndedWithTheNavigation(run, "Release Plan")
        }
    }

    @Test
    fun aNonTerminalIntentOfTheSamePackStillGoesThroughTheResolver() = runTest {
        NoNetworkGuard.during {
            val run = terminalRun("turn on the light", "en", TierPolicy.DEFAULT)

            assertEquals(1, run.resolver.invocations)
            assertNull((run.outcome as CommandOutcome.Completed).terminalCall)
        }
    }

    @Test
    fun aPackOfOnlyTerminalIntentsNeedsNoResolverButAMixedPackDoes() {
        val onlyTerminal = GrammarPack {
            intent("open_page") {
                text("title", 3)
                terminal()
                en("open page {title}")
            }
        }
        val tier = LocalGrammarStrategy(StrategyId("nav")) { pack = onlyTerminal }
        assertEquals("LocalGrammarStrategy(id=nav)", tier.toString())

        val failure = assertThrows(IllegalArgumentException::class.java) {
            LocalGrammarStrategy(StrategyId("nav")) { pack = terminalPack }
        }
        assertEquals("LocalGrammarStrategy: resolver is required", failure.message)
    }

    @Test
    fun theTerminalFlagIsPartOfTheMatchAndSurvivesCrossPackAgreement() {
        val match = terminalPack.match("open page Release Plan", "en")

        assertTrue(match?.terminal == true)
        assertFalse(terminalPack.match("turn on the light", "en")?.terminal ?: true)
    }
}
