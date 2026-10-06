package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ID_FIELD = "item_id"
private const val NEXT_ID = "next"
private const val REJECTED_DIGEST = """{"status":"plan_rejected","reason":"bad_reference","step_index":1}"""
private const val IGNORED = """{"status":"error","reason":"ignored_call"}"""

private fun createArgs(): JsonObject = buildJsonObject { put("name", "a") }

private fun createStep(): JsonObject = planStep("s1", PLAN_CREATE_TOOL, createArgs())

private fun tagStep(reference: String): JsonObject =
    planStep(
        "s2",
        PLAN_TAG_TOOL,
        buildJsonObject {
            put(ID_FIELD, reference)
            put("tag", "red")
        },
    )

// Step 2 refers to a step nobody declared, so whole-plan validation rejects it with bad_reference at index 1.
private fun rejectedPlan(): JsonObject = planArguments(createStep(), tagStep("\$s9.item_id"))

private fun validPlan(): JsonObject = planArguments(createStep(), tagStep("\$s1.item_id"))

private fun created(): ToolStep =
    ToolStep.Mutation(FakeMutation(PLAN_CREATE_TOOL, { StepResult("ok", false, "tok", mapOf(ID_FIELD to "id-1")) }))

private fun tagged(): ToolStep = ToolStep.Mutation(FakeMutation(PLAN_TAG_TOOL, { StepResult("ok") }))

private fun erroring(): ToolStep = ToolStep.Finished(PLAN_CREATE_TOOL, FinishedKind.ERROR, StepResult("bad", true))

private class ReplanRig(
    val fake: FakeAiProvider,
    val executor: ScriptedToolExecutor,
    private val policy: TierPolicy = TierPolicy.DEFAULT,
) {
    val sink = RecordingCommitSink()
    val next = ScriptedStrategy(StrategyId(NEXT_ID), { _, _ -> StrategyOutcome.Completed(null) })
    private val gate = ScriptedGate.admitAll()
    private val plan = planStrategy(executor, planSnapshot(createTool(), tagTool()))

    suspend fun run(vararg before: ScriptedStrategy): CommandOutcome.Completed {
        val tiers = before.toList() + plan + next
        val outcome = pipelineOf(tiers, fake, gate, sink, policy = policy)
            .execute(CommandInput("make and tag", "en", null))
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        return outcome as CommandOutcome.Completed
    }
}

private fun replanRig(vararg answers: ModelResult, steps: List<ToolStep> = emptyList()): ReplanRig =
    ReplanRig(
        FakeAiProvider(ProviderId.ANTHROPIC, *answers),
        ScriptedToolExecutor.sequence(null, *steps.toTypedArray()),
    )

/** The one replan: only before anything was written, same conversation, fixed digest, never a third call. */
class PlanThenExecuteReplanTest {

    @Test
    fun aRejectedPlanIsReplannedOnceAndTheSecondPlanRuns() = runTest {
        NoNetworkGuard.during {
            val answers = arrayOf(planAnswer(rejectedPlan()), planAnswer(validPlan(), "plan-2"))
            val rig = replanRig(*answers, steps = listOf(created(), tagged()))
            val outcome = rig.run()
            assertEquals(null, outcome.reply)
            assertEquals(2, rig.fake.callCount)
            assertEquals(2, outcome.trace.attempts.first().turns.size)
            assertEquals(listOf(PLAN_CREATE_TOOL, PLAN_TAG_TOOL), rig.executor.calls.map { it.toolName })
            assertEquals(listOf("plan-2", "plan-2"), rig.executor.calls.map { it.callId })
            assertEquals(listOf(0, 1), outcome.executed.map { it.position })
            assertTrue(outcome.executed.all { it.kind == ActionKind.COMMITTED })
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.PLAN_REPLANNED })
            assertTrue(TraceCode.PLAN_REJECTED in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
        }
    }

    @Test
    fun theReplanContinuesTheConversationWithAnIdenticalPrefixAndTheFixedDigest() = runTest {
        NoNetworkGuard.during {
            val firstAnswer = planAnswer(rejectedPlan())
            val rig = replanRig(firstAnswer, planAnswer(validPlan(), "plan-2"), steps = listOf(created(), tagged()))
            rig.run()
            val one = rig.fake.calls[0].request
            val two = rig.fake.calls[1].request
            assertEquals(one.system, two.system)
            assertEquals(one.tools.size, two.tools.size)
            one.tools.indices.forEach { assertSame(one.tools[it], two.tools[it]) }
            assertEquals(ToolChoice.Required("submit_plan"), two.toolChoice)
            assertEquals(one.toolChoice, two.toolChoice)
            assertEquals(one.maxTokens, two.maxTokens)
            assertEquals(one.cache, two.cache)
            assertEquals(one.singleToolCall, two.singleToolCall)
            assertEquals(one.reasoning, two.reasoning)
            assertEquals(1, one.messages.size)
            assertEquals(3, two.messages.size)
            assertSame(one.messages.first(), two.messages[0])
            assertTrue(two.messages[0] is UserMessage)
            val answered = (firstAnswer as ModelResult.Success).response.message
            assertSame(answered, two.messages[1])
            val results = (two.messages[2] as ToolResultsMessage).results
            assertEquals(listOf(PLAN_CALL_ID), results.map { it.callId })
            assertTrue(results.all { it.isError })
            assertEquals(REJECTED_DIGEST, results.single().content)
        }
    }

    @Test
    fun everyOtherCallOfTheRejectedAnswerIsAnsweredAsIgnoredInOrder() = runTest {
        NoNetworkGuard.during {
            val extra = AssistantPart.ToolCall("extra-1", PLAN_CREATE_TOOL, createArgs())
            val bad = AssistantPart.ToolCall(PLAN_CALL_ID, "submit_plan", rejectedPlan())
            val rig = replanRig(
                FakeAiProvider.toolCalls(Usage(1, 0, 0, 1), bad, extra),
                planAnswer(validPlan(), "plan-2"),
                steps = listOf(created(), tagged()),
            )
            rig.run()
            val results = (rig.fake.calls[1].request.messages[2] as ToolResultsMessage).results
            assertEquals(listOf(PLAN_CALL_ID, "extra-1"), results.map { it.callId })
            assertEquals(listOf(REJECTED_DIGEST, IGNORED), results.map { it.content })
            assertTrue(results.all { it.isError })
            assertEquals(listOf("plan-2", "plan-2"), rig.executor.calls.map { it.callId })
        }
    }

    @Test
    fun aDirectAppToolCallIsRejectedMalformedAndNeverPrepared() = runTest {
        NoNetworkGuard.during {
            val direct = FakeAiProvider.toolCall("direct-1", PLAN_CREATE_TOOL, createArgs(), Usage(1, 0, 0, 1))
            val rig = replanRig(direct, planAnswer(validPlan(), "plan-2"), steps = listOf(created(), tagged()))
            val outcome = rig.run()
            assertEquals(2, rig.fake.callCount)
            assertEquals(2, rig.executor.callCount)
            assertTrue(rig.executor.calls.all { it.callId == "plan-2" })
            assertEquals(1, outcome.executed.count { it.toolName == PLAN_CREATE_TOOL })
            val results = (rig.fake.calls[1].request.messages[2] as ToolResultsMessage).results
            assertEquals(
                """{"status":"plan_rejected","reason":"malformed","step_index":null}""",
                results.single().content,
            )
            assertEquals("direct-1", results.single().callId)
        }
    }

    @Test
    fun aSecondRejectionEscalatesMalformedWithTheIncomingCarryAndNeverCallsAThirdTime() = runTest {
        NoNetworkGuard.during {
            val carried = Any()
            val first = ScriptedStrategy(
                StrategyId("first"),
                { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carried) },
            )
            val rig = replanRig(planAnswer(rejectedPlan()), planAnswer(rejectedPlan(), "plan-2"))
            val outcome = rig.run(first)
            val attempt = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }
            assertEquals("escalated", attempt.outcome)
            assertTrue(attempt.escalationReason is EscalationReason.MalformedExtraction)
            assertEquals(2, attempt.turns.size)
            assertEquals(2, rig.fake.callCount)
            assertEquals(1, rig.next.executions)
            assertEquals(listOf(carried), rig.next.receivedCarries)
            assertEquals(0, rig.executor.callCount)
            assertEquals(2, outcome.trace.codes.count { it == TraceCode.PLAN_REJECTED })
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.PLAN_REPLANNED })
        }
    }

    @Test
    fun aFirstStepThatFailsBeforeAnythingWasWrittenIsReplannedOnce() = runTest {
        NoNetworkGuard.during {
            val sequence = listOf(erroring(), created(), tagged())
            val answers = arrayOf(planAnswer(validPlan()), planAnswer(validPlan(), "plan-2"))
            val rig = replanRig(*answers, steps = sequence)
            val outcome = rig.run()
            assertEquals(2, rig.fake.callCount)
            assertEquals(0, rig.next.executions)
            assertEquals(3, rig.executor.callCount)
            val kinds = rig.sink.actions.map { it.action.kind }
            assertEquals(listOf(ActionKind.IS_ERROR, ActionKind.COMMITTED, ActionKind.COMMITTED), kinds)
            assertEquals(0, rig.sink.actions.first().action.position)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.PLAN_REPLANNED })
            val results = (rig.fake.calls[1].request.messages[2] as ToolResultsMessage).results
            assertEquals(
                """{"status":"plan_rejected","reason":"step_failed","step_index":0}""",
                results.single().content,
            )
        }
    }

    @Test
    fun theSamePreCommitFailureTwiceEscalatesAndIsHandedUpNotSuppressed() = runTest {
        NoNetworkGuard.during {
            val answers = arrayOf(planAnswer(validPlan()), planAnswer(validPlan(), "plan-2"))
            val rig = replanRig(*answers, steps = listOf(erroring(), erroring()))
            val outcome = rig.run()
            val attempt = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }
            assertEquals("escalated", attempt.outcome)
            assertEquals(EscalationReason.Other("plan_step_failed"), attempt.escalationReason)
            assertEquals(2, rig.fake.callCount)
            assertEquals(1, rig.next.executions)
            assertEquals(2, rig.executor.callCount)
        }
    }

    @Test
    fun aFailureAfterACommitNeverReplansAndIsSuppressedPartial() = runTest {
        NoNetworkGuard.during {
            val failing = ToolStep.Finished(PLAN_TAG_TOOL, FinishedKind.ERROR, StepResult("bad", true))
            val rig = replanRig(planAnswer(validPlan()), steps = listOf(created(), failing))
            val outcome = rig.run()
            val attempt = outcome.trace.attempts.first()
            assertEquals(1, rig.fake.callCount)
            assertEquals(true, outcome.partial)
            assertEquals("escalation_suppressed", attempt.outcome)
            assertEquals(EscalationReason.Other("plan_step_failed"), attempt.suppressedEscalation)
            assertTrue(TraceCode.PLAN_REPLANNED !in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
        }
    }

    @Test
    fun aReplanAnswerThatNeedsALookupEscalatesWithNothingRun() = runTest {
        NoNetworkGuard.during {
            val rig = replanRig(planAnswer(rejectedPlan()), planAnswer(planArguments(needsLookup = true), "plan-2"))
            val outcome = rig.run()
            val attempt = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }
            assertEquals(EscalationReason.Other("plan_needs_lookup"), attempt.escalationReason)
            assertEquals(2, rig.fake.callCount)
            assertEquals(0, rig.executor.callCount)
            assertEquals(1, rig.next.executions)
        }
    }

    @Test
    fun noReplanWhenTheTokenCeilingIsAlreadyReached() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(rejectedPlan()))
            val rig = ReplanRig(fake, ScriptedToolExecutor.sequence(null), TierPolicy { tokenCeiling = 2 })
            val outcome = rig.run()
            val attempt = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }
            assertTrue(attempt.escalationReason is EscalationReason.MalformedExtraction)
            assertEquals(1, fake.callCount)
            assertTrue(TraceCode.PLAN_REPLANNED !in outcome.trace.codes)
        }
    }
}
