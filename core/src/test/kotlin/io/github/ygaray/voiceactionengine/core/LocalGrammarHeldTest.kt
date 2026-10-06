package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val REPLY = "Okay, turning it on"

/** A grammar action the gate holds is reported held, never as success, and an errored apply withholds the reply. */
class LocalGrammarHeldTest {

    private val pack = GrammarPack {
        intent("light_on") {
            en("turn on the light")
            es("enciende la luz")
        }
    }

    private class Rig(pack: GrammarPack, val write: FakeMutation, val gate: ScriptedGate) {
        val sink = RecordingCommitSink()
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)
        private val resolver = RecordingResolver { _, _ -> Resolution.Steps(listOf(ToolStep.Mutation(write)), REPLY) }
        private val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
            this.pack = pack
            resolver = this@Rig.resolver
        }
        val pipeline = pipelineOf(listOf(grammar), fake, gate, sink)
    }

    @Test
    fun aHeldGrammarActionIsReportedHeldWithNoProviderCallIdAndKeepsTheReply() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("light_on", StepResult("saved"))
            val rig = Rig(pack, write, ScriptedGate.holdAll("confirm", "held"))

            val outcome = rig.pipeline.execute(CommandInput("turn on the light", "en", null))

            val completed = outcome as CommandOutcome.Completed
            val action = rig.sink.actions.single().action
            assertEquals(ActionKind.HELD, action.kind)
            assertFalse(action.applied)
            assertNull(action.providerCallId)
            assertEquals(listOf(ActionKind.HELD), completed.executed.map { it.kind })
            assertNull(completed.executed.single().providerCallId)
            assertEquals(0, write.applyCount)
            assertTrue(completed.commits.isEmpty())
            assertEquals(1, completed.held.size)
            assertEquals(REPLY, completed.reply)
            assertEquals(0, rig.fake.callCount)
        }
    }

    @Test
    fun anAppliedWriteThatReportsAnErrorWithholdsTheReplyAndIsNeverASuccess() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("light_on", StepResult("bad", true, "err", emptyMap()))
            val rig = Rig(pack, write, ScriptedGate.admitAll())

            val outcome = rig.pipeline.execute(CommandInput("turn on the light", "en", null))

            val completed = outcome as CommandOutcome.Completed
            assertNull(completed.reply)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(ActionKind.IS_ERROR), completed.executed.map { it.kind })
            assertNull(completed.executed.single().providerCallId)
            assertTrue(completed.commits.none { it.kind == ActionKind.COMMITTED })
        }
    }

    @Test
    fun anAdmittedGrammarWriteIsCommittedWithNoProviderCallIdAndKeepsTheReply() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("light_on", StepResult("saved"))
            val rig = Rig(pack, write, ScriptedGate.admitAll())

            val outcome = rig.pipeline.execute(CommandInput("enciende la luz", "es", null))

            val completed = outcome as CommandOutcome.Completed
            assertEquals(REPLY, completed.reply)
            assertEquals(listOf(ActionKind.COMMITTED), completed.executed.map { it.kind })
            assertNull(completed.executed.single().providerCallId)
        }
    }
}
