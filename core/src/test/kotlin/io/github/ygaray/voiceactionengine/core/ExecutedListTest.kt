package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every outcome variant carries the ordered executed list of all four kinds and its committed subset. */
class ExecutedListTest {
    private val runId = "run-list"

    private fun preview() = ToolStep.Finished("rename", FinishedKind.PREVIEW, StepResult("p", false, "pv", emptyMap()))

    private fun rejection() = ToolStep.Finished("rename", FinishedKind.ERROR, StepResult("x", true, "rej", emptyMap()))

    private fun good() = ToolStep.Mutation(FakeMutation("good", StepResult("g", false, "ok", emptyMap())))

    private fun tier(id: String, step: StrategyStep) = ScriptedStrategy(StrategyId(id), step)

    private suspend fun run(gate: ScriptedGate, vararg tiers: ScriptedStrategy): CommandOutcome =
        commandPipeline {
            tiers.forEach { tier(it) }
            this.gate = gate
            commitSink = RecordingCommitSink()
            runIds = { runId }
        }.execute(CommandInput("do things"))

    @Test
    fun completedListsAllFourKindsInDispatchOrderAndTheCommittedSubset() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate.sequence(
                null,
                GateDecision.Admit(),
                GateDecision.Admit(),
                GateDecision.Hold("later", "held-tok"),
            )
            val only = tier("only") { _, session ->
                session.submit(preview())
                session.submit(good())
                session.submit(ToolStep.Mutation(FakeMutation("bad", StepResult("b", true, "err", emptyMap()))))
                session.submit(ToolStep.Mutation(FakeMutation("later", StepResult("l"))))
                StrategyOutcome.Completed("done")
            }

            val outcome = run(gate, only)

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(0, 1, 2, 3), outcome.executed.map { it.position })
            assertEquals(
                listOf(ActionKind.PREVIEW, ActionKind.COMMITTED, ActionKind.IS_ERROR, ActionKind.HELD),
                outcome.executed.map { it.kind },
            )
            assertEquals(listOf(false, true, true, false), outcome.executed.map { it.applied })
            assertEquals(listOf("pv", "ok", "err", "held-tok"), outcome.executed.map { it.appOutcomeToken })
            assertEquals(listOf("good"), outcome.commits.map { it.toolName })
            assertEquals(1, outcome.held.size)
        }
    }

    @Test
    fun failedAfterACommitStillCarriesTheCommit() = runTest {
        NoNetworkGuard.during {
            val only = tier("only") { _, session ->
                session.submit(good())
                StrategyOutcome.Failed(FailureReason.Auth())
            }

            val outcome = run(ScriptedGate.admitAll(), only)

            assertTrue(outcome is CommandOutcome.Failed)
            assertEquals(listOf(ActionKind.COMMITTED), outcome.executed.map { it.kind })
            assertEquals(listOf("good"), outcome.commits.map { it.toolName })
        }
    }

    @Test
    fun unhandledCarriesTheUnappliedActionsOfEveryTierInOrder() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                session.submit(preview())
                session.submit(rejection())
                StrategyOutcome.Escalate(EscalationReason.NoToolCall())
            }
            val second = tier("second") { _, _ -> StrategyOutcome.NoMatch() }

            val outcome = run(ScriptedGate.admitAll(), first, second)

            assertTrue(outcome is CommandOutcome.Unhandled)
            assertEquals(listOf(ActionKind.PREVIEW, ActionKind.IS_ERROR), outcome.executed.map { it.kind })
            assertEquals(listOf(false, false), outcome.executed.map { it.applied })
            assertEquals(listOf(0, 1), outcome.executed.map { it.position })
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun theListIsCumulativeAcrossTiersWithMonotonicPositions() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                session.submit(preview())
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
            }
            val second = tier("second") { _, session ->
                session.submit(good())
                StrategyOutcome.Completed("done")
            }

            val outcome = run(ScriptedGate.admitAll(), first, second)

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(0, 1), outcome.executed.map { it.position })
            assertEquals(listOf(ActionKind.PREVIEW, ActionKind.COMMITTED), outcome.executed.map { it.kind })
            assertEquals(listOf("pv", "ok"), outcome.executed.map { it.appOutcomeToken })
            assertEquals(listOf(1), outcome.commits.map { it.position })
        }
    }
}
