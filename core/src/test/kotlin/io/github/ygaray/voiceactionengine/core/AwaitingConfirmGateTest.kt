package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.AwaitingConfirmGate
import io.github.ygaray.voiceactionengine.core.commit.ConfirmationPolicy
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The suspend-mode confirm helper: the user's answer arrives through resolve, and only a confirm writes. */
class AwaitingConfirmGateTest {
    private class Subject

    private val tierId = StrategyId("only")

    private fun ok(name: String) = FakeMutation(name, StepResult("saved"))

    @Test
    fun aConfirmPublishesPendingThenAppliesOnceAndCompletes() = runTest {
        NoNetworkGuard.during {
            val subject = Subject()
            val gate = AwaitingConfirmGate(ConfirmationPolicy { subject })
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed("done")
                    }),
                )
                this.gate = gate
                commitSink = sink
            }
            var outcome: CommandOutcome? = null
            launch { outcome = pipeline.execute(CommandInput("save it")) }
            runCurrent()

            val pending = gate.pending.value!!
            assertSame(subject, pending.subject)
            assertEquals(listOf("write"), pending.proposal.mutations.map { it.toolName })
            assertEquals(0, write.applyCount)
            assertTrue(gate.resolve(pending.id, true))
            runCurrent()

            val completed = outcome as CommandOutcome.Completed
            assertEquals(1, write.applyCount)
            assertEquals(ActionKind.COMMITTED, sink.actions.single().action.kind)
            assertEquals(listOf("write"), completed.commits.map { it.toolName })
            assertTrue(completed.held.isEmpty())
            assertNull(gate.pending.value)
        }
    }

    @Test
    fun aDeclineAppliesNothingAndHoldsWithTheSubjectAsTheReason() = runTest {
        NoNetworkGuard.during {
            val subject = Subject()
            val gate = AwaitingConfirmGate(ConfirmationPolicy { subject }, 120_000L, null, "held")
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed("done")
                    }),
                )
                this.gate = gate
                commitSink = sink
            }
            var outcome: CommandOutcome? = null
            launch { outcome = pipeline.execute(CommandInput("save it")) }
            runCurrent()

            assertTrue(gate.resolve(gate.pending.value!!.id, false))
            runCurrent()

            val completed = outcome as CommandOutcome.Completed
            assertEquals(0, write.applyCount)
            val event = sink.actions.single().action
            assertEquals(ActionKind.HELD, event.kind)
            assertFalse(event.applied)
            assertEquals("held", event.appOutcomeToken)
            assertSame(subject, completed.held.single().reason)
            assertTrue(completed.commits.isEmpty())
            assertNull(gate.pending.value)
        }
    }
}
