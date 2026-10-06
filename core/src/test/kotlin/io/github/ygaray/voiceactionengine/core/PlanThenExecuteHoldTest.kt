package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NEXT_TIER = "next"
private const val SUPPRESSED = "escalation_suppressed"

private fun step(id: String, tool: String): JsonObject =
    planStep(
        id,
        tool,
        buildJsonObject {
            put("name", id)
        },
    )

private fun stepsOf(count: Int): Array<JsonObject> =
    Array(count) { index -> step("s${index + 1}", if (index == 0) PLAN_CREATE_TOOL else PLAN_TAG_TOOL) }

private class HoldRig(count: Int, val gate: ScriptedGate) {
    val mutations: List<FakeMutation> = List(count) { index ->
        val tool = if (index == 0) PLAN_CREATE_TOOL else PLAN_TAG_TOOL
        FakeMutation(tool, { StepResult("ok", false, "tok", emptyMap()) })
    }
    val executor = ScriptedToolExecutor.sequence(null, *mutations.map { ToolStep.Mutation(it) }.toTypedArray())
    val fake = FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(planArguments(*stepsOf(count))))
    val next = ScriptedStrategy(StrategyId(NEXT_TIER), { _, _ -> StrategyOutcome.Completed("next") })
    private val plan = planStrategy(executor, planSnapshot(createTool(), tagTool()))
    val pipeline: CommandPipeline = pipelineOf(listOf(plan, next), fake, gate, RecordingCommitSink())

    suspend fun run(): CommandOutcome.Completed {
        val outcome = pipeline.execute(CommandInput("make and tag", "en", null))
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        return outcome as CommandOutcome.Completed
    }
}

private fun gateOf(vararg decisions: GateDecision): ScriptedGate = ScriptedGate.sequence(null, *decisions)

private fun admit(): GateDecision = GateDecision.Admit()

private fun hold(): GateDecision = GateDecision.Hold("confirm", null)

/** RT-01: a hold stops the plan; with nothing committed the engine suppresses it, after a commit it ends it. */
class PlanThenExecuteHoldTest {

    private fun assertOneHeld(outcome: CommandOutcome.Completed) {
        assertEquals(1, outcome.held.size)
        assertEquals(PLAN_CALL_ID, outcome.held.single().providerCallId)
        assertNull(outcome.reply)
        assertNull(outcome.terminalCall)
        assertTrue(outcome.partial)
    }

    @Test
    fun aHoldAfterACommitMidPlanEndsTheCommandWithoutEscalating() = runTest {
        NoNetworkGuard.during {
            val rig = HoldRig(3, gateOf(admit(), hold()))
            val outcome = rig.run()
            assertOneHeld(outcome)
            assertEquals(2, rig.executor.callCount)
            assertEquals(1, outcome.commits.size)
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.HELD), outcome.executed.map { it.kind })
            assertEquals(listOf("s3"), outcome.remainingStepIds)
            val attempt = outcome.trace.attempts.first()
            assertEquals("completed", attempt.outcome)
            assertNull(attempt.suppressedEscalation)
            assertFalse(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
            assertEquals(1, rig.fake.callCount)
        }
    }

    @Test
    fun aHoldOnTheLastStepAfterACommitEndsTheCommandWithNothingRemaining() = runTest {
        NoNetworkGuard.during {
            val rig = HoldRig(2, gateOf(admit(), hold()))
            val outcome = rig.run()
            assertOneHeld(outcome)
            assertEquals(1, outcome.commits.size)
            assertEquals(emptyList<String>(), outcome.remainingStepIds)
            assertEquals("completed", outcome.trace.attempts.first().outcome)
            assertFalse(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
            assertEquals(1, rig.fake.callCount)
        }
    }

    @Test
    fun aHoldWithNothingCommittedEscalatesAndTheEngineSuppressesIt() = runTest {
        NoNetworkGuard.during {
            val rig = HoldRig(2, ScriptedGate.holdAll("confirm"))
            val outcome = rig.run()
            assertOneHeld(outcome)
            assertEquals(1, rig.executor.callCount)
            assertTrue(outcome.commits.isEmpty())
            assertEquals(listOf("s2"), outcome.remainingStepIds)
            val attempt = outcome.trace.attempts.first()
            assertEquals(SUPPRESSED, attempt.outcome)
            assertEquals(EscalationReason.Other("plan_step_held"), attempt.suppressedEscalation)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
            assertEquals(1, rig.fake.callCount)
        }
    }

    @Test
    fun aSingleStepPlanThatIsHeldEndsPartialWithASuppressedEscalation() = runTest {
        NoNetworkGuard.during {
            val rig = HoldRig(1, ScriptedGate.holdAll("confirm"))
            val outcome = rig.run()
            assertOneHeld(outcome)
            assertTrue(outcome.commits.isEmpty())
            assertEquals(emptyList<String>(), outcome.remainingStepIds)
            val attempt = outcome.trace.attempts.first()
            assertEquals(SUPPRESSED, attempt.outcome)
            assertEquals(EscalationReason.Other("plan_step_held"), attempt.suppressedEscalation)
            assertEquals(0, rig.next.executions)
            assertEquals(1, rig.fake.callCount)
        }
    }

    @Test
    fun theHeldStepIsNeverInTheRemainingList() = runTest {
        NoNetworkGuard.during {
            val rig = HoldRig(3, gateOf(admit(), hold()))
            val outcome = rig.run()
            assertFalse("s2" in outcome.remainingStepIds)
            assertFalse("s1" in outcome.remainingStepIds)
        }
    }

    @Test
    fun commitHeldAppliesOnlyTheHeldProposalAndNeverResumesThePlan() = runTest {
        NoNetworkGuard.during {
            val rig = HoldRig(3, gateOf(admit(), hold()))
            val outcome = rig.run()
            val committed = rig.pipeline.commitHeld(outcome.held.single())
            val completed = committed as CommandOutcome.Completed
            assertFalse(completed.partial)
            assertEquals(1, completed.commits.size)
            assertEquals(PLAN_TAG_TOOL, completed.commits.single().toolName)
            assertEquals(emptyList<String>(), completed.remainingStepIds)
            assertEquals(2, rig.executor.callCount)
            assertEquals(1, rig.mutations[1].applyCount)
            assertEquals(0, rig.mutations[2].applyCount)
        }
    }
}
