package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
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
import java.util.concurrent.atomic.AtomicInteger

private const val NEXT_ID = "next"
private const val FAILED_CODE = "plan_step_failed"
private const val UNRESOLVED_CODE = "plan_binding_unresolved"

private fun planStepOf(id: String, tool: String, reference: String? = null): JsonObject =
    planStep(
        id,
        tool,
        buildJsonObject {
            put("name", id)
            if (reference != null) put("item_id", reference)
        },
    )

private fun plan(count: Int, referenceInStepTwo: String? = null): JsonObject =
    planArguments(
        *Array(count) { index ->
            val tool = if (index == 0) PLAN_CREATE_TOOL else PLAN_TAG_TOOL
            planStepOf("s${index + 1}", tool, if (index == 1) referenceInStepTwo else null)
        },
    )

private fun committed(tool: String): () -> ToolStep = {
    ToolStep.Mutation(FakeMutation(tool, { StepResult("ok", false, "tok", emptyMap()) }))
}

private fun appliedAsError(): () -> ToolStep = {
    ToolStep.Mutation(FakeMutation(PLAN_TAG_TOOL, { StepResult("bad", true, "tok", emptyMap()) }))
}

private fun finished(kind: FinishedKind, isError: Boolean): () -> ToolStep = {
    ToolStep.Finished(PLAN_TAG_TOOL, kind, StepResult("x", isError, null, emptyMap()))
}

private fun throwing(): () -> ToolStep = { error("executor broke") }

private class SuppressionRig(
    steps: List<() -> ToolStep>,
    gate: ScriptedGate,
    vararg answers: ModelResult,
) {
    private val position = AtomicInteger()
    val executor = ScriptedToolExecutor(null) { _, _ -> steps[position.getAndIncrement()]() }
    val fake = FakeAiProvider(ProviderId.ANTHROPIC, *answers)
    val next = ScriptedStrategy(StrategyId(NEXT_ID), { _, _ -> StrategyOutcome.Completed(null) })
    private val tier = planStrategy(executor, planSnapshot(createTool(), tagTool()))
    private val pipeline = pipelineOf(listOf(tier, next), fake, gate, RecordingCommitSink())

    suspend fun run(): CommandOutcome = pipeline.execute(CommandInput("make and tag", "en", null))
}

/** The plan tier never lets a later tier run once a step committed: every later stop ends as a partial completion. */
class PlanThenExecuteSuppressionTest {

    private fun rig(steps: List<() -> ToolStep>, plan: JsonObject, gate: ScriptedGate = ScriptedGate.admitAll()) =
        SuppressionRig(steps, gate, planAnswer(plan))

    private fun assertSuppressed(rig: SuppressionRig, outcome: CommandOutcome, code: String, remaining: List<String>) {
        val completed = outcome as CommandOutcome.Completed
        assertTrue(completed.partial)
        assertNull(completed.reply)
        assertNull(completed.terminalCall)
        val attempt = outcome.trace.attempts.first()
        assertEquals("escalation_suppressed", attempt.outcome)
        assertEquals(EscalationReason.Other(code), attempt.suppressedEscalation)
        assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
        assertEquals(0, rig.next.executions)
        assertEquals(1, rig.fake.callCount)
        assertEquals(1, attempt.turns.size)
        assertEquals(1, outcome.commits.size)
        assertEquals(ActionKind.COMMITTED, outcome.commits.single().kind)
        assertEquals(PLAN_CREATE_TOOL, outcome.commits.single().toolName)
        assertEquals(remaining, completed.remainingStepIds)
    }

    @Test
    fun escalateAfterStepOneCommittedIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val errored = finished(FinishedKind.ERROR, true)
            val steps = listOf(committed(PLAN_CREATE_TOOL), errored, committed(PLAN_TAG_TOOL))
            val rig = rig(steps, plan(3))
            val outcome = rig.run()
            assertSuppressed(rig, outcome, FAILED_CODE, listOf("s3"))
            assertEquals(2, rig.executor.callCount)
        }
    }

    @Test
    fun anAppliedErrorAfterStepOneIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val rig = rig(listOf(committed(PLAN_CREATE_TOOL), appliedAsError()), plan(2))
            val outcome = rig.run()
            assertSuppressed(rig, outcome, FAILED_CODE, emptyList())
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.IS_ERROR), outcome.executed.map { it.kind })
        }
    }

    @Test
    fun anExecutorThatThrowsAfterStepOneIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val rig = rig(listOf(committed(PLAN_CREATE_TOOL), throwing()), plan(2))
            val outcome = rig.run()
            assertSuppressed(rig, outcome, FAILED_CODE, emptyList())
            assertTrue(TraceCode.TOOL_PREPARE_ERROR in outcome.trace.codes)
        }
    }

    @Test
    fun aGateFaultAfterStepOneIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val asked = AtomicInteger()
            val gate = ScriptedGate(null) {
                if (asked.getAndIncrement() == 1) error("gate broke")
                GateDecision.Admit()
            }
            val rig = rig(listOf(committed(PLAN_CREATE_TOOL), committed(PLAN_TAG_TOOL)), plan(2), gate)
            val outcome = rig.run()
            assertSuppressed(rig, outcome, FAILED_CODE, emptyList())
            assertTrue(TraceCode.GATE_ERROR in outcome.trace.codes)
            assertEquals(ActionKind.IS_ERROR, outcome.executed.last().kind)
        }
    }

    @Test
    fun aPreviewAfterStepOneIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val rig = rig(listOf(committed(PLAN_CREATE_TOOL), finished(FinishedKind.PREVIEW, false)), plan(2))
            val outcome = rig.run()
            assertSuppressed(rig, outcome, FAILED_CODE, emptyList())
        }
    }

    @Test
    fun anUnresolvedReferenceAfterStepOneIsSuppressedAndListsTheStepsThatNeverRan() = runTest {
        NoNetworkGuard.during {
            val steps = listOf(committed(PLAN_CREATE_TOOL), committed(PLAN_TAG_TOOL), committed(PLAN_TAG_TOOL))
            val rig = rig(steps, plan(3, referenceInStepTwo = "\$s1.item_id"))
            val outcome = rig.run()
            assertSuppressed(rig, outcome, UNRESOLVED_CODE, listOf("s2", "s3"))
            assertEquals(1, rig.executor.callCount)
            assertTrue(TraceCode.PLAN_BINDING_UNRESOLVED in outcome.trace.codes)
        }
    }

    @Test
    fun aHoldAfterStepOneCommittedEndsTheCommandWithoutEscalating() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate.sequence(null, GateDecision.Admit(), GateDecision.Hold("confirm", null))
            val steps = listOf(committed(PLAN_CREATE_TOOL), committed(PLAN_TAG_TOOL), committed(PLAN_TAG_TOOL))
            val rig = rig(steps, plan(3), gate)
            val outcome = rig.run()
            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            val attempt = outcome.trace.attempts.first()
            assertEquals("completed", attempt.outcome)
            assertNull(attempt.suppressedEscalation)
            assertFalse(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
            assertEquals(1, rig.fake.callCount)
            assertEquals(1, outcome.commits.size)
            assertEquals(1, outcome.held.size)
            assertEquals(listOf("s3"), completed.remainingStepIds)
        }
    }

    @Test
    fun theSameFailureWithNothingAppliedIsHandedUpAndTheNextTierRuns() = runTest {
        NoNetworkGuard.during {
            val failing = finished(FinishedKind.ERROR, true)
            val rig = SuppressionRig(
                listOf(failing, failing),
                ScriptedGate.admitAll(),
                planAnswer(plan(2)),
                planAnswer(plan(2), "plan-2"),
            )
            val outcome = rig.run()
            val completed = outcome as CommandOutcome.Completed
            val attempt = outcome.trace.attempts.first()
            assertEquals("escalated", attempt.outcome)
            assertEquals(EscalationReason.Other(FAILED_CODE), attempt.escalationReason)
            assertNull(attempt.suppressedEscalation)
            assertFalse(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(1, rig.next.executions)
            assertEquals(2, rig.fake.callCount)
            assertFalse(completed.partial)
            assertEquals(emptyList<String>(), completed.remainingStepIds)
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun aPreviewOrReadAsTheFirstStepIsAFailedStepSoItReplansOnceThenHandsUp() = runTest {
        NoNetworkGuard.during {
            listOf(FinishedKind.PREVIEW, FinishedKind.READ).forEach { kind ->
                val step = finished(kind, false)
                val rig = SuppressionRig(
                    listOf(step, step),
                    ScriptedGate.admitAll(),
                    planAnswer(plan(2)),
                    planAnswer(plan(2), "plan-2"),
                )
                val outcome = rig.run()
                val attempt = outcome.trace.attempts.first()
                assertEquals("escalated", attempt.outcome)
                assertEquals(EscalationReason.Other(FAILED_CODE), attempt.escalationReason)
                assertTrue(TraceCode.PLAN_REPLANNED in outcome.trace.codes)
                assertEquals(2, rig.fake.callCount)
                assertEquals(1, rig.next.executions)
                assertTrue(outcome.commits.isEmpty())
            }
        }
    }
}
