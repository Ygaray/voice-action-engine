package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HTTP_BAD_REQUEST = 400
private const val CEILING = 100L
private const val NEXT_ID = "next"

private fun validPlan(): JsonObject =
    planArguments(planStep("s1", PLAN_CREATE_TOOL, buildJsonObject { put("name", "a") }))

// Step 2 refers to a step nobody declared, so whole-plan validation rejects the plan before any step runs.
private fun rejectedPlan(): JsonObject =
    planArguments(
        planStep("s1", PLAN_CREATE_TOOL, buildJsonObject { put("name", "a") }),
        planStep("s2", PLAN_TAG_TOOL, buildJsonObject { put("item_id", "\$s9.item_id") }),
    )

private fun httpError(details: FailureDetails? = FailureDetails(HTTP_BAD_REQUEST, "invalid_request", null)) =
    ModelResult.Failure(FailureReason.HttpError(), details)

private fun usage() = Usage(1, 0, 0, 1)

/** Counts every call of the onFailed hook and answers the way the default does. */
private class FailedHookCounter {
    var calls = 0
    val reasons = mutableListOf<FailureReason>()
    val details = mutableListOf<FailureDetails?>()
    val hook: suspend (FailureReason, FailureDetails?) -> StrategyOutcome = { reason, detail ->
        calls++
        reasons += reason
        details += detail
        StrategyOutcome.Failed(reason, detail)
    }
}

private class OutcomeRig(
    val fake: FakeAiProvider,
    val executor: ScriptedToolExecutor = committingExecutor(null),
    private val gate: ScriptedGate = ScriptedGate.admitAll(),
    private val policy: TierPolicy = TierPolicy.DEFAULT,
    private val credentials: ScriptedCredentialSource = testKey(),
    configure: PlanThenExecuteStrategy.Builder.() -> Unit = {},
) {
    val next = ScriptedStrategy(StrategyId(NEXT_ID), { _, _ -> StrategyOutcome.Completed("next") })
    private val plan = planStrategy(executor, planSnapshot(createTool(), tagTool()), configure = configure)

    suspend fun run(vararg before: CommandStrategy): CommandOutcome =
        pipelineOf(before.toList() + plan + next, fake, gate, RecordingCommitSink(), null, policy, credentials)
            .execute(CommandInput("make and tag", "en", null))
}

private fun rigOf(
    vararg results: ModelResult,
    configure: PlanThenExecuteStrategy.Builder.() -> Unit = {},
): OutcomeRig = OutcomeRig(FakeAiProvider(ProviderId.ANTHROPIC, *results), configure = configure)

private fun failedReason(outcome: CommandOutcome): FailureReason = (outcome as CommandOutcome.Failed).reason

private fun planAttempt(outcome: CommandOutcome) = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }

/** The provider-failure hook of the plan tier is SingleShot's, and a truncated plan escalates instead of failing. */
class PlanThenExecuteOutcomeMappingTest {

    @Test
    fun anHttpFailureEndsFailedWithTheExactReasonAndDetailsByDefault() = runTest {
        NoNetworkGuard.during {
            val details = FailureDetails(HTTP_BAD_REQUEST, "invalid_request", "req-1")
            val rig = rigOf(httpError(details))

            val outcome = rig.run()

            assertEquals(FailureReason.HttpError(), failedReason(outcome))
            assertSame(details, (outcome as CommandOutcome.Failed).details)
            assertEquals(0, rig.executor.callCount)
            assertEquals(0, rig.next.executions)
        }
    }

    @Test
    fun anOnFailedHookReturningEscalateSendsTheCommandToTheNextTierOnce() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(httpError()) {
                onFailed = { _, _ -> StrategyOutcome.Escalate(EscalationReason.ModelDeclined()) }
            }

            val outcome = rig.run()

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, rig.next.executions)
            assertEquals(1, rig.fake.callCount)
        }
    }

    @Test
    fun theOnFailedHookReceivesTheExactReasonAndDetailsOncePerFailingCall() = runTest {
        NoNetworkGuard.during {
            val reason = FailureReason.HttpError()
            val details = FailureDetails(HTTP_BAD_REQUEST, "invalid_request", "req-9")
            val counting = FailedHookCounter()
            val rig = rigOf(ModelResult.Failure(reason, details)) { onFailed = counting.hook }

            rig.run()

            assertEquals(1, counting.calls)
            assertSame(reason, counting.reasons.single())
            assertSame(details, counting.details.single())
        }
    }

    @Test
    fun modelUnsupportedATransportFaultAndAnOnDeviceFailureAllReachTheHookUnchanged() = runTest {
        NoNetworkGuard.during {
            val failures = listOf(
                FailureReason.ModelUnsupported(),
                FailureReason.Network(),
                FailureReason.Timeout(),
                FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "aicore_down"),
            )

            failures.forEach { reason ->
                val details = FailureDetails(null, "fault", null)
                val counting = FailedHookCounter()
                val rig = rigOf(ModelResult.Failure(reason, details)) { onFailed = counting.hook }

                val outcome = rig.run()

                assertEquals(reason.toString(), 1, counting.calls)
                assertSame(reason, counting.reasons.single())
                assertSame(details, counting.details.single())
                assertSame(reason, (outcome as CommandOutcome.Failed).reason)
                assertEquals(0, rig.next.executions)
            }
        }
    }

    @Test
    fun aReplanCallThatFailsReachesTheHookOnceAndTheDefaultEndsFailedWithNothingWritten() = runTest {
        NoNetworkGuard.during {
            val counting = FailedHookCounter()
            val rig = rigOf(planAnswer(rejectedPlan()), httpError()) { onFailed = counting.hook }

            val outcome = rig.run()

            assertEquals(1, counting.calls)
            assertEquals(FailureReason.HttpError(), failedReason(outcome))
            assertEquals(2, rig.fake.callCount)
            assertEquals(0, rig.executor.callCount)
            assertEquals(0, rig.next.executions)
            assertTrue(TraceCode.PLAN_REPLANNED in outcome.trace.codes)
        }
    }

    @Test
    fun aNoToolCallFailureOrAProseAnswerEscalatesAndTheHookIsNotCalled() = runTest {
        NoNetworkGuard.during {
            val answers = listOf(
                ModelResult.Failure(FailureReason.NoToolCall()),
                FakeAiProvider.reply("prose", usage()),
            )

            answers.forEach { answer ->
                val counting = FailedHookCounter()
                val rig = rigOf(answer) { onFailed = counting.hook }

                val outcome = rig.run()

                assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
                assertEquals(1, rig.next.executions)
                assertEquals(EscalationReason.NoToolCall(), planAttempt(outcome).escalationReason)
                assertEquals(0, counting.calls)
            }
        }
    }

    @Test
    fun aNoToolCallFailureOrAProseAnswerEscalatesWithTheIncomingCarry() = runTest {
        NoNetworkGuard.during {
            val answers = listOf(
                ModelResult.Failure(FailureReason.NoToolCall()),
                FakeAiProvider.reply("prose", usage()),
            )

            answers.forEach { answer ->
                val carried = Any()
                val earlier = ScriptedStrategy(
                    StrategyId("earlier"),
                    { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carried) },
                )
                val rig = rigOf(answer) {}

                val outcome = rig.run(earlier)

                assertEquals(EscalationReason.NoToolCall(), planAttempt(outcome).escalationReason)
                assertEquals(listOf(carried), rig.next.receivedCarries)
            }
        }
    }

    @Test
    fun aRefusalFailureOrAStopFailsRefusalWithoutEscalatingAndTheHookIsNotCalled() = runTest {
        NoNetworkGuard.during {
            val answers = listOf(
                ModelResult.Failure(FailureReason.Refusal()),
                FakeAiProvider.refusal(usage()),
                answerOf(StopReason.REFUSAL, AssistantPart.ToolCall(PLAN_CALL_ID, "submit_plan", validPlan())),
            )

            answers.forEach { answer ->
                val counting = FailedHookCounter()
                val rig = rigOf(answer) { onFailed = counting.hook }

                val outcome = rig.run()

                assertEquals(FailureReason.Refusal(), failedReason(outcome))
                assertEquals(0, rig.next.executions)
                assertEquals(0, rig.executor.callCount)
                assertEquals(0, counting.calls)
            }
        }
    }

    @Test
    fun aMissingCredentialFailsAndNeitherTheHookNorTheNextTierRuns() = runTest {
        NoNetworkGuard.during {
            val counting = FailedHookCounter()
            val rig = OutcomeRig(
                FakeAiProvider(ProviderId.ANTHROPIC),
                credentials = ScriptedCredentialSource.keys(),
                configure = { onFailed = counting.hook },
            )

            val outcome = rig.run()

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(0, counting.calls)
            assertEquals(0, rig.next.executions)
            assertEquals(0, rig.fake.callCount)
        }
    }

    @Test
    fun aTokenCeilingReachedBeforeTheCallFailsBudgetExceededWithoutTheHookOrAProviderCall() = runTest {
        NoNetworkGuard.during {
            val counting = FailedHookCounter()
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val policy = TierPolicy { tokenCeiling = CEILING }
            val rig = OutcomeRig(fake, policy = policy, configure = { onFailed = counting.hook })
            val earlier = ScriptedStrategy(
                StrategyId("earlier"),
                { _, session ->
                    session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, CEILING), 1L))
                    StrategyOutcome.Escalate(EscalationReason.NoToolCall())
                },
            )

            val outcome = rig.run(earlier)

            assertEquals(FailureReason.BudgetExceeded(BudgetBound.TOKENS), failedReason(outcome))
            assertEquals(0, fake.callCount)
            assertEquals(0, counting.calls)
        }
    }

    @Test
    fun aGateHoldAndAThrowingExecutorNeverReachTheHook() = runTest {
        NoNetworkGuard.during {
            val held = FailedHookCounter()
            val holding = OutcomeRig(
                FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(validPlan())),
                gate = ScriptedGate.sequence(null, GateDecision.Hold("confirm", null)),
                configure = { onFailed = held.hook },
            )
            holding.run()

            val thrown = FailedHookCounter()
            val throwing = OutcomeRig(
                FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(validPlan()), planAnswer(validPlan(), "plan-2")),
                executor = ScriptedToolExecutor(null) { _, _ -> error("executor blew up") },
                configure = { onFailed = thrown.hook },
            )
            val outcome = throwing.run()

            assertEquals(0, held.calls)
            assertEquals(0, thrown.calls)
            assertEquals(2, throwing.executor.callCount)
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun anOnFailedHookThatThrowsEndsTheTierAsAStrategyError() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(httpError()) { onFailed = { _, _ -> error("hook blew up") } }

            val outcome = rig.run()

            assertTrue(failedReason(outcome).toString(), failedReason(outcome) is FailureReason.Unexpected)
            assertTrue(TraceCode.STRATEGY_ERROR in outcome.trace.codes)
            assertEquals(0, rig.next.executions)
        }
    }

    @Test
    fun aTruncatedPlanEscalatesMalformedWithTheIncomingCarryAndRunsNothing() = runTest {
        NoNetworkGuard.during {
            val carried = Any()
            val earlier = ScriptedStrategy(
                StrategyId("earlier"),
                { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carried) },
            )
            val call = AssistantPart.ToolCall(PLAN_CALL_ID, "submit_plan", validPlan())
            val counting = FailedHookCounter()
            val rig = rigOf(answerOf(StopReason.MAX_TOKENS, call)) { onFailed = counting.hook }

            val outcome = rig.run(earlier)

            val attempt = planAttempt(outcome)
            assertEquals("escalated", attempt.outcome)
            assertTrue(attempt.escalationReason is EscalationReason.MalformedExtraction)
            assertEquals(1, rig.fake.callCount)
            assertEquals(1, attempt.turns.size)
            assertEquals(0, rig.executor.callCount)
            assertEquals(1, rig.next.executions)
            assertEquals(listOf(carried), rig.next.receivedCarries)
            assertEquals(0, counting.calls)
            assertTrue(TraceCode.PLAN_REPLANNED !in outcome.trace.codes)
        }
    }

    @Test
    fun aTruncatedReplanAnswerEscalatesMalformedWithTheCarryAndNeverAsksAThirdTime() = runTest {
        NoNetworkGuard.during {
            val carried = Any()
            val earlier = ScriptedStrategy(
                StrategyId("earlier"),
                { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carried) },
            )
            val call = AssistantPart.ToolCall("plan-2", "submit_plan", validPlan())
            val rig = rigOf(planAnswer(rejectedPlan()), answerOf(StopReason.MAX_TOKENS, call))

            val outcome = rig.run(earlier)

            val attempt = planAttempt(outcome)
            assertTrue(attempt.escalationReason is EscalationReason.MalformedExtraction)
            assertEquals(2, rig.fake.callCount)
            assertEquals(0, rig.executor.callCount)
            assertEquals(listOf(carried), rig.next.receivedCarries)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.PLAN_REPLANNED })
        }
    }

    @Test
    fun pauseAndContextWindowStopsFailAndAToolUseStopWithNoCallIsMalformed() = runTest {
        NoNetworkGuard.during {
            val call = AssistantPart.ToolCall(PLAN_CALL_ID, "submit_plan", validPlan())
            val expected = listOf(
                answerOf(StopReason.PAUSE_TURN, call) to FailureReason.PauseTurn(),
                answerOf(StopReason.CONTEXT_WINDOW_EXCEEDED, call) to FailureReason.ContextWindowExceeded(),
                answerOf(StopReason.TOOL_USE) to FailureReason.MalformedResponse(),
            )

            expected.forEach { (answer, reason) ->
                val rig = rigOf(answer)

                val outcome = rig.run()

                assertEquals(reason, failedReason(outcome))
                assertEquals(0, rig.executor.callCount)
                assertEquals(0, rig.next.executions)
            }
        }
    }
}
