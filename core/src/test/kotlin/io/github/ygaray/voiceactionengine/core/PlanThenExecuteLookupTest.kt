package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Rig(snapshot: ToolingSnapshot, arguments: JsonObject) {
    val executor: ScriptedToolExecutor = committingExecutor(null)
    val fake = FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(arguments))
    val gate: ScriptedGate = ScriptedGate.admitAll()
    val sink = RecordingCommitSink()
    val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed(null) })
    val plan = planStrategy(executor, snapshot)

    suspend fun run(vararg before: ScriptedStrategy): CommandOutcome {
        val tiers = before.toList() + plan + next
        return pipelineOf(tiers, fake, gate, sink).execute(CommandInput("make and find", "en", null))
    }
}

private fun stepOf(id: String, tool: String): JsonObject =
    planStep(id, tool, buildJsonObject { put("name", "a") })

/** A plan that needs a lookup hands the command up after one provider call with nothing run. */
class PlanThenExecuteLookupTest {
    private val withFind = planSnapshot(createTool(), tagTool(), findTool())

    private fun assertNothingRan(rig: Rig, outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        val attempt = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }
        assertEquals("escalated", attempt.outcome)
        assertEquals(EscalationReason.Other("plan_needs_lookup"), attempt.escalationReason)
        assertEquals(1, attempt.turns.size)
        assertEquals(1, rig.next.executions)
        assertEquals(0, rig.executor.callCount)
        assertEquals(0, rig.gate.calls)
        assertTrue(rig.sink.actions.isEmpty())
        assertEquals(1, rig.fake.callCount)
    }

    @Test
    fun theNeedsLookupFlagEscalatesWithNothingRun() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(withFind, planArguments(needsLookup = true))
            assertNothingRan(rig, rig.run())
        }
    }

    @Test
    fun aStepNamingAReadToolEscalatesWithNothingPrepared() = runTest {
        NoNetworkGuard.during {
            val steps = planArguments(stepOf("s1", PLAN_CREATE_TOOL), stepOf("s2", PLAN_FIND_TOOL))
            val rig = Rig(withFind, steps)
            assertNothingRan(rig, rig.run())
        }
    }

    @Test
    fun theFlagWithListedWritingStepsStillRunsNothing() = runTest {
        NoNetworkGuard.during {
            val steps = planArguments(stepOf("s1", PLAN_CREATE_TOOL), needsLookup = true)
            val rig = Rig(withFind, steps)
            assertNothingRan(rig, rig.run())
        }
    }

    @Test
    fun theIncomingCarryIsForwardedUnchangedToTheNextTier() = runTest {
        NoNetworkGuard.during {
            val carried = Any()
            val first = ScriptedStrategy(
                StrategyId("first"),
                { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carried) },
            )
            val rig = Rig(withFind, planArguments(needsLookup = true))
            val outcome = rig.run(first)
            val attempt = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }
            assertTrue(attempt.carryIn)
            assertEquals(listOf(carried), rig.next.receivedCarries)
            assertEquals(0, rig.executor.callCount)
        }
    }
}
