package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
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
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CEILING = 100L
private const val DEFAULT_STEP_CAP = 8
private const val TOO_MANY = """{"status":"plan_rejected","reason":"too_many_steps","step_index":null}"""
private const val PER_TURN_TOKENS = 777

private fun createStepOf(id: String): JsonObject = planStep(id, PLAN_CREATE_TOOL, buildJsonObject { put("name", id) })

private fun onePlan(): JsonObject = planArguments(createStepOf("s1"))

private fun plansOf(count: Int): JsonObject = planArguments(*Array(count) { createStepOf("s${it + 1}") })

// A create step followed by a tag step with a literal id, so no binding is involved.
private fun createThenTag(): JsonObject =
    planArguments(createStepOf("s1"), planStep("s2", PLAN_TAG_TOOL, buildJsonObject { put("item_id", "x") }))

private fun committed(): ToolStep = ToolStep.Mutation(FakeMutation(PLAN_CREATE_TOOL, { StepResult("ok") }))

private fun failedStep(): ToolStep = ToolStep.Finished(PLAN_TAG_TOOL, FinishedKind.ERROR, StepResult("bad", true))

private class LimitsRig(
    val fake: FakeAiProvider,
    val executor: ScriptedToolExecutor = committingExecutor(null),
    private val policy: TierPolicy = TierPolicy.DEFAULT,
    configure: PlanThenExecuteStrategy.Builder.() -> Unit = {},
) {
    val gate: ScriptedGate = ScriptedGate.admitAll()
    val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("next") })
    private val plan = planStrategy(executor, planSnapshot(createTool(), tagTool()), configure = configure)

    suspend fun run(vararg before: CommandStrategy): CommandOutcome =
        pipelineOf(before.toList() + plan + next, fake, gate, RecordingCommitSink(), null, policy)
            .execute(CommandInput("make and tag", "en", null))
}

private fun limitsRig(vararg results: ModelResult, configure: PlanThenExecuteStrategy.Builder.() -> Unit = {}) =
    LimitsRig(FakeAiProvider(ProviderId.ANTHROPIC, *results), configure = configure)

private fun planAttempt(outcome: CommandOutcome) = outcome.trace.attempts.first { it.strategy == StrategyId("plan") }

private fun maxItemsOf(request: ModelRequest): Int {
    val schema = request.tools.first { it.name == "submit_plan" }.inputSchema
    val steps = schema.getValue("properties").jsonObject.getValue("steps").jsonObject
    return (steps.getValue("maxItems") as JsonPrimitive).int
}

/** The step cap, both token-ceiling checks and the exact number of model calls per scenario. */
class PlanThenExecuteLimitsTest {

    @Test
    fun aPlanLongerThanTheDefaultCapIsRejectedTooManyStepsAndReplannedOnce() = runTest {
        NoNetworkGuard.during {
            val rig = limitsRig(planAnswer(plansOf(DEFAULT_STEP_CAP + 1)), planAnswer(onePlan(), "plan-2"))

            val outcome = rig.run()

            assertEquals(2, rig.fake.callCount)
            assertEquals(1, rig.executor.callCount)
            assertEquals(DEFAULT_STEP_CAP, maxItemsOf(rig.fake.calls[0].request))
            val results = (rig.fake.calls[1].request.messages[2] as ToolResultsMessage).results
            assertEquals(TOO_MANY, results.single().content)
            assertTrue(TraceCode.PLAN_REJECTED in outcome.trace.codes)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.PLAN_REPLANNED })
            assertEquals(0, rig.next.executions)
        }
    }

    @Test
    fun aPlanAtTheDefaultCapRunsInOneCall() = runTest {
        NoNetworkGuard.during {
            val rig = limitsRig(planAnswer(plansOf(DEFAULT_STEP_CAP)))

            rig.run()

            assertEquals(1, rig.fake.callCount)
            assertEquals(DEFAULT_STEP_CAP, rig.executor.callCount)
        }
    }

    @Test
    fun maxStepsTwoPutsMaxItemsTwoInTheSchemaAndRejectsAThreeStepPlan() = runTest {
        NoNetworkGuard.during {
            val rig = limitsRig(planAnswer(plansOf(3)), planAnswer(plansOf(2), "plan-2")) { maxSteps = 2 }

            rig.run()

            assertEquals(2, maxItemsOf(rig.fake.calls[0].request))
            assertEquals(2, maxItemsOf(rig.fake.calls[1].request))
            assertEquals(2, rig.fake.callCount)
            assertEquals(2, rig.executor.callCount)
            val results = (rig.fake.calls[1].request.messages[2] as ToolResultsMessage).results
            assertEquals(TOO_MANY, results.single().content)
        }
    }

    @Test
    fun aTokenCeilingAlreadyReachedFailsBudgetExceededBeforeAnyProviderCall() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val rig = LimitsRig(fake, policy = TierPolicy { tokenCeiling = CEILING })
            val earlier = ScriptedStrategy(
                StrategyId("earlier"),
                { _, session ->
                    session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, CEILING), 1L))
                    StrategyOutcome.Escalate(EscalationReason.NoToolCall())
                },
            )

            val outcome = rig.run(earlier)

            assertEquals(FailureReason.BudgetExceeded(BudgetBound.TOKENS), (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, fake.callCount)
            assertEquals(0, rig.executor.callCount)
        }
    }

    @Test
    fun aPlanAnswerThatCrossesTheCeilingFailsBeforeAnyStepRuns() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                planAnswer(onePlan(), usage = Usage(0, 0, 0, CEILING + 1)),
            )
            val rig = LimitsRig(fake, policy = TierPolicy { tokenCeiling = CEILING })

            val outcome = rig.run()

            assertEquals(FailureReason.BudgetExceeded(BudgetBound.TOKENS), (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, rig.executor.callCount)
            assertEquals(0, rig.gate.calls)
            assertEquals(0, rig.next.executions)
        }
    }

    @Test
    fun aRejectedPlanThatBringsTheRunExactlyToTheCeilingIsNotReplanned() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                planAnswer(plansOf(DEFAULT_STEP_CAP + 1), usage = Usage(0, 0, 0, CEILING)),
            )
            val rig = LimitsRig(fake, policy = TierPolicy { tokenCeiling = CEILING })

            val outcome = rig.run()

            val attempt = planAttempt(outcome)
            assertTrue(attempt.escalationReason is EscalationReason.MalformedExtraction)
            assertEquals(1, fake.callCount)
            assertEquals(1, attempt.turns.size)
            assertTrue(TraceCode.PLAN_REPLANNED !in outcome.trace.codes)
            assertEquals(0, rig.executor.callCount)
        }
    }

    @Test
    fun bothRequestsCarryTheTurnTokenLimitAndTheBuildersReasoning() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                planAnswer(plansOf(DEFAULT_STEP_CAP + 1)),
                planAnswer(onePlan(), "plan-2"),
            )
            val rig = LimitsRig(
                fake,
                policy = TierPolicy { maxTokensPerTurn = PER_TURN_TOKENS },
                configure = { reasoning = ReasoningMode.PROVIDER_DEFAULT },
            )

            rig.run()

            assertEquals(2, fake.callCount)
            fake.calls.forEach {
                assertEquals(PER_TURN_TOKENS, it.request.maxTokens)
                assertEquals(ReasoningMode.PROVIDER_DEFAULT, it.request.reasoning)
            }
        }
    }

    @Test
    fun theMinimumIterationLimitStillAllowsTheReplanAndALongPlan() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { maxIterations = 2 }
            val replanned = LimitsRig(
                FakeAiProvider(
                    ProviderId.ANTHROPIC,
                    planAnswer(plansOf(DEFAULT_STEP_CAP + 1)),
                    planAnswer(onePlan(), "plan-2"),
                ),
                policy = policy,
            )
            val long = LimitsRig(FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(plansOf(5))), policy = policy)

            val replannedOutcome = replanned.run()
            val longOutcome = long.run()

            assertEquals(2, replanned.fake.callCount)
            assertEquals(1, replanned.executor.callCount)
            assertTrue(replannedOutcome.toString(), replannedOutcome is CommandOutcome.Completed)
            assertEquals(1, long.fake.callCount)
            assertEquals(5, long.executor.callCount)
            assertTrue(longOutcome.toString(), longOutcome is CommandOutcome.Completed)
            assertEquals(0, long.next.executions)
        }
    }

    @Test
    fun aCleanPlanIsOneCallWithTheScriptedUsage() = runTest {
        NoNetworkGuard.during {
            val usage = Usage(10, 2, 3, 5)
            val rig = limitsRig(planAnswer(plansOf(2), usage = usage))

            val outcome = rig.run()

            assertEquals(1, planAttempt(outcome).turns.size)
            assertEquals(1, rig.fake.callCount)
            assertEquals(usage, outcome.trace.usage)
        }
    }

    @Test
    fun aNeedsLookupPlanIsOneCallAndHandsTheCommandUp() = runTest {
        NoNetworkGuard.during {
            val usage = Usage(9, 0, 1, 4)
            val rig = limitsRig(planAnswer(planArguments(needsLookup = true), usage = usage))

            val outcome = rig.run()

            val attempt = planAttempt(outcome)
            assertEquals(1, attempt.turns.size)
            assertEquals(EscalationReason.Other("plan_needs_lookup"), attempt.escalationReason)
            assertEquals(usage, outcome.trace.usage)
            assertEquals(1, rig.next.executions)
            assertEquals(0, rig.executor.callCount)
        }
    }

    @Test
    fun aFailureAfterACommitIsStillOneCallAndNeverReplans() = runTest {
        NoNetworkGuard.during {
            val usage = Usage(6, 0, 0, 8)
            val rig = LimitsRig(
                FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(createThenTag(), usage = usage)),
                ScriptedToolExecutor.sequence(null, committed(), failedStep()),
            )

            val outcome = rig.run()

            assertEquals(1, planAttempt(outcome).turns.size)
            assertEquals(1, rig.fake.callCount)
            assertEquals(usage, outcome.trace.usage)
            assertEquals(0, rig.next.executions)
            assertTrue(TraceCode.PLAN_REPLANNED !in outcome.trace.codes)
        }
    }

    @Test
    fun aReplannedPlanIsTwoCallsAndTheTraceUsageIsTheSumOfBoth() = runTest {
        NoNetworkGuard.during {
            val first = Usage(10, 2, 3, 5)
            val second = Usage(7, 0, 1, 4)
            val rig = limitsRig(
                planAnswer(plansOf(DEFAULT_STEP_CAP + 1), usage = first),
                planAnswer(onePlan(), "plan-2", second),
            )

            val outcome = rig.run()

            assertEquals(2, planAttempt(outcome).turns.size)
            assertEquals(2, rig.fake.callCount)
            assertEquals(first + second, outcome.trace.usage)
            assertEquals(first + second, planAttempt(outcome).usage)
        }
    }

    @Test
    fun theSentSchemaListsTheStepsArrayAsAnArrayWithTheCap() = runTest {
        NoNetworkGuard.during {
            val rig = limitsRig(planAnswer(onePlan())) { maxSteps = 3 }

            rig.run()

            val schema = rig.fake.calls.single().request.tools.first { it.name == "submit_plan" }.inputSchema
            val steps = schema.getValue("properties").jsonObject.getValue("steps").jsonObject
            assertEquals("array", steps.getValue("type").jsonPrimitive.content)
            assertEquals(3, maxItemsOf(rig.fake.calls.single().request))
            assertTrue(steps.getValue("items") is JsonObject)
            assertTrue(schema.getValue("required") is JsonArray)
        }
    }
}
