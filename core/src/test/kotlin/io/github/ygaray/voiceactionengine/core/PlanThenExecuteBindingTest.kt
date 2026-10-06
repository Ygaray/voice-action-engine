package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NEXT_TIER = "next"
private const val TAG_FIELD = "item_id"

private class Run(
    val outcome: CommandOutcome.Completed,
    val fake: FakeAiProvider,
    val next: ScriptedStrategy,
    val gate: ScriptedGate,
)

private fun created(ids: Map<String, String> = mapOf(TAG_FIELD to "id-1")): ToolStep =
    ToolStep.Mutation(FakeMutation(PLAN_CREATE_TOOL, { StepResult("ok", false, "tok", ids) }))

private fun tagged(): ToolStep = ToolStep.Mutation(FakeMutation(PLAN_TAG_TOOL, { StepResult("ok") }))

private fun createStep(): JsonObject = planStep("s1", PLAN_CREATE_TOOL, buildJsonObject { put("name", "a") })

private fun tagStep(id: String, arguments: JsonObject): JsonObject = planStep(id, PLAN_TAG_TOOL, arguments)

private fun refTo(step: String): JsonObject = buildJsonObject {
    put(TAG_FIELD, "\$$step.item_id")
    put("tag", "red")
}

private suspend fun runPlan(
    executor: ScriptedToolExecutor,
    steps: List<JsonObject>,
    gate: ScriptedGate = ScriptedGate.admitAll(),
): Run {
    val fake = FakeAiProvider(ProviderId.ANTHROPIC, planAnswer(planArguments(*steps.toTypedArray())))
    val next = ScriptedStrategy(StrategyId(NEXT_TIER), { _, _ -> StrategyOutcome.Completed(null) })
    val plan = planStrategy(executor, planSnapshot(createTool(), tagTool()))
    val outcome = pipelineOf(listOf(plan, next), fake, gate, RecordingCommitSink())
        .execute(CommandInput("make and tag", "en", null))
    assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
    return Run(outcome as CommandOutcome.Completed, fake, next, gate)
}

/** A later step gets an earlier step's real committed id; every other case stops before the step. */
class PlanThenExecuteBindingTest {

    @Test
    fun aLaterStepReceivesTheRealIdOfTheStepBeforeIt() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null, created(), tagged())
            val run = runPlan(executor, listOf(createStep(), tagStep("s2", refTo("s1"))))
            val expected = buildJsonObject {
                put(TAG_FIELD, "id-1")
                put("tag", "red")
            }
            assertEquals(expected, executor.calls[1].arguments)
            assertEquals(false, run.outcome.partial)
            assertEquals(1, run.fake.callCount)
        }
    }

    @Test
    fun aKeyThePendingChangeReportsIsBindableAndTheStepResultWinsOnTheSameKey() = runTest {
        NoNetworkGuard.during {
            val both = ToolStep.Mutation(
                FakeMutation(
                    PLAN_CREATE_TOOL,
                    { StepResult("ok", false, "tok", mapOf(TAG_FIELD to "from-result")) },
                    mapOf(TAG_FIELD to "from-change", "extra" to "e1"),
                ),
            )
            val executor = ScriptedToolExecutor.sequence(null, both, tagged())
            val arguments = buildJsonObject {
                put(TAG_FIELD, "\$s1.item_id")
                put("extra", "\$s1.extra")
            }
            runPlan(executor, listOf(createStep(), tagStep("s2", arguments)))
            val expected = buildJsonObject {
                put(TAG_FIELD, "from-result")
                put("extra", "e1")
            }
            assertEquals(expected, executor.calls[1].arguments)
        }
    }

    @Test
    fun referencesInsideNestedArraysAndObjectsAreBound() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null, created(), tagged())
            val arguments = buildJsonObject {
                putJsonArray("ids") {
                    add(JsonPrimitive("\$s1.item_id"))
                    add(buildJsonArray { add(JsonPrimitive("\$s1.item_id")) })
                }
                putJsonObject("inner") { put("ref", "\$s1.item_id") }
            }
            runPlan(executor, listOf(createStep(), tagStep("s2", arguments)))
            val expected = buildJsonObject {
                putJsonArray("ids") {
                    add(JsonPrimitive("id-1"))
                    add(buildJsonArray { add(JsonPrimitive("id-1")) })
                }
                putJsonObject("inner") { put("ref", "id-1") }
            }
            assertEquals(expected, executor.calls[1].arguments)
        }
    }

    @Test
    fun literalsAndObjectKeysAndNonStringsReachTheExecutorUnchanged() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null, created(), tagged())
            val arguments = buildJsonObject {
                put("price", "\$5.00")
                put("text", "price \$s1.item_id")
                put("\$s1.item_id", "kept")
                put("count", 5)
                put("nothing", JsonNull)
            }
            runPlan(executor, listOf(createStep(), tagStep("s2", arguments)))
            assertEquals(arguments, executor.calls[1].arguments)
        }
    }

    @Test
    fun aKeyTheCommittedStepDidNotReturnStopsBeforeTheNextStepAndEndsSuppressed() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null, created(emptyMap()), tagged())
            val run = runPlan(executor, listOf(createStep(), tagStep("s2", refTo("s1"))))
            val attempt = run.outcome.trace.attempts.first()
            assertEquals(1, executor.callCount)
            assertEquals(true, run.outcome.partial)
            assertEquals("escalation_suppressed", attempt.outcome)
            assertEquals(EscalationReason.Other("plan_binding_unresolved"), attempt.suppressedEscalation)
            assertTrue(TraceCode.PLAN_BINDING_UNRESOLVED in run.outcome.trace.codes)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in run.outcome.trace.codes)
            assertEquals(0, run.next.executions)
            assertEquals(1, run.fake.callCount)
        }
    }

    @Test
    fun aKeyTwoActionsOfOneStepDisagreeOnIsUnresolved() = runTest {
        NoNetworkGuard.during {
            val disagree = ToolStep.Mutation(
                listOf(
                    FakeMutation(PLAN_CREATE_TOOL, { StepResult("ok") }, mapOf(TAG_FIELD to "a")),
                    FakeMutation(PLAN_CREATE_TOOL, { StepResult("ok") }, mapOf(TAG_FIELD to "b")),
                ),
            )
            val executor = ScriptedToolExecutor.sequence(null, disagree, tagged())
            val run = runPlan(executor, listOf(createStep(), tagStep("s2", refTo("s1"))))
            assertEquals(1, executor.callCount)
            assertTrue(TraceCode.PLAN_BINDING_UNRESOLVED in run.outcome.trace.codes)
        }
    }

    @Test
    fun aPreviewStepIsNeverBindableSoTheStepAfterItIsNotPrepared() = runTest {
        NoNetworkGuard.during {
            val preview = ToolStep.Finished(
                PLAN_TAG_TOOL,
                FinishedKind.PREVIEW,
                StepResult("p", false, null, mapOf(TAG_FIELD to "p1")),
            )
            val executor = ScriptedToolExecutor.sequence(null, created(), preview, tagged())
            val steps = listOf(createStep(), tagStep("s2", refTo("s1")), tagStep("s3", refTo("s2")))
            runPlan(executor, steps)
            assertEquals(2, executor.callCount)
        }
    }

    @Test
    fun anErroredStepIsNeverBindableSoTheStepAfterItIsNotPrepared() = runTest {
        NoNetworkGuard.during {
            val errored = ToolStep.Mutation(
                FakeMutation(PLAN_TAG_TOOL, { StepResult("bad", true, null, mapOf(TAG_FIELD to "e1")) }),
            )
            val executor = ScriptedToolExecutor.sequence(null, created(), errored, tagged())
            val steps = listOf(createStep(), tagStep("s2", refTo("s1")), tagStep("s3", refTo("s2")))
            runPlan(executor, steps)
            assertEquals(2, executor.callCount)
        }
    }

    @Test
    fun aHeldStepIsNeverBindableSoTheStepAfterItIsNotPrepared() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null, created(), tagged(), tagged())
            val gate = ScriptedGate.sequence(null, GateDecision.Admit(), GateDecision.Hold())
            val steps = listOf(createStep(), tagStep("s2", refTo("s1")), tagStep("s3", refTo("s2")))
            val run = runPlan(executor, steps, gate)
            assertEquals(2, executor.callCount)
            assertEquals(2, run.gate.calls)
        }
    }
}
