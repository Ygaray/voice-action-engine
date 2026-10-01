package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** A follow-up run names the run it answers on every artifact it produces. */
class ParentRunIdTest {

    private val ids = AtomicInteger()

    private fun write(name: String) = FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))

    @Test
    fun aFollowUpRunCarriesItsParentOnTheOutcomeTraceEventsProposalHeldProposalAndTermination() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val gate = ScriptedGate.sequence(null, GateDecision.Admit(), GateDecision.Hold("later"))
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("only"), { _, session ->
                        session.submit(ToolStep.Mutation(write("a")))
                        session.submit(ToolStep.Mutation(write("b")))
                        StrategyOutcome.Completed("done")
                    }),
                )
                this.gate = gate
                commitSink = sink
                runIds = { "run-${ids.incrementAndGet()}" }
            }

            val outcome = pipeline.execute(CommandInput("and also this", parentRunId = "p"))

            assertEquals("p", outcome.parentRunId)
            assertEquals("p", outcome.trace.parentRunId)
            assertTrue(sink.actions.isNotEmpty())
            assertTrue(sink.actions.all { it.parentRunId == "p" })
            assertTrue(gate.proposals.all { it.parentRunId == "p" })
            assertEquals(listOf("p"), outcome.held.map { it.parentRunId })
            assertEquals("p", sink.closes.single().parentRunId)
            assertEquals("p", sink.closes.single().trace.parentRunId)
        }
    }

    @Test
    fun aRunWithNoParentReportsNullEverywhere() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("only"), { _, session ->
                        session.submit(ToolStep.Mutation(write("a")))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("hello"))

            assertNull(outcome.parentRunId)
            assertNull(outcome.trace.parentRunId)
            assertTrue(sink.actions.all { it.parentRunId == null })
            assertNull(sink.closes.single().parentRunId)
        }
    }

    @Test
    fun aCommitHeldChildCarriesTheHeldRunsIdAsItsParentEverywhere() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val proposals = mutableListOf<CommitProposal>()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("only"), { _, session ->
                        session.submit(ToolStep.Mutation(write("a")))
                        StrategyOutcome.Completed("asked")
                    }),
                )
                gate = ScriptedGate { proposal ->
                    proposals.add(proposal)
                    GateDecision.Hold("later")
                }
                commitSink = sink
                runIds = { "run-${ids.incrementAndGet()}" }
            }
            val original = pipeline.execute(CommandInput("save it", parentRunId = "p"))
            val held = original.held.single()
            assertEquals("p", held.parentRunId)
            assertEquals(original.runId, held.runId)

            val child = pipeline.commitHeld(held)

            assertEquals(original.runId, child.parentRunId)
            assertEquals(original.runId, child.trace.parentRunId)
            val childEvents = sink.actions.filter { it.runId == child.runId }
            assertEquals(1, childEvents.size)
            assertEquals(original.runId, childEvents.single().parentRunId)
            val termination = sink.closes.last()
            assertEquals(child.runId, termination.runId)
            assertEquals(original.runId, termination.parentRunId)
            assertEquals(1, proposals.size)
        }
    }
}
