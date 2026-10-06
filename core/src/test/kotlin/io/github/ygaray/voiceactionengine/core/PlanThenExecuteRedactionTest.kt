package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANARY = "CANARY"
private const val ITEM_KEY = "item_id"
private const val MIN_DISTINCT = 12

// Letters and digits only, so a canary still matches the step-id grammar.
private const val ID_ONE = "${CANARY}one"
private const val ID_TWO = "${CANARY}two"
private const val ID_THREE = "${CANARY}three"
private const val BOUND = "$CANARY-BOUND"

/** An app object whose own text is a canary, standing in for the context and hold reason an app hands the engine. */
private class PlanCanary(private val label: String) {
    override fun toString(): String = "$CANARY-$label"
}

private fun createWith(id: String): JsonObject =
    planStep(id, PLAN_CREATE_TOOL, buildJsonObject { put("name", "$CANARY-NAME") })

private fun tagWith(id: String, reference: String): JsonObject =
    planStep(
        id,
        PLAN_TAG_TOOL,
        buildJsonObject {
            put(ITEM_KEY, reference)
            put("tag", "$CANARY-TAG")
        },
    )

private fun result(ids: Map<String, String> = mapOf(ITEM_KEY to BOUND)): StepResult =
    StepResult("$CANARY-RESULT", false, "$CANARY-TOKEN", ids)

private fun committing(ids: Map<String, String> = mapOf(ITEM_KEY to BOUND)): ToolStep =
    ToolStep.Mutation(FakeMutation("any_tool", { result(ids) }, context = PlanCanary("APP")))

private fun erroring(): ToolStep {
    val bad = StepResult("$CANARY-BAD", true, "$CANARY-TOKEN", emptyMap())
    return ToolStep.Finished(PLAN_CREATE_TOOL, FinishedKind.ERROR, bad)
}

private class CanaryRun(
    answers: List<ModelResult>,
    steps: List<ToolStep>,
    val gate: ScriptedGate = ScriptedGate.admitAll(),
) {
    val executor = ScriptedToolExecutor.sequence(null, *steps.toTypedArray())
    val fake = FakeAiProvider(ProviderId.ANTHROPIC, *answers.toTypedArray())
    val sink = RecordingCommitSink()
    val listener = RecordingEventListener()
    val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("next") })
    var builder: PlanThenExecuteStrategy.Builder? = null
    val plan = planStrategy(executor, planSnapshot(createTool(), tagTool(), findTool())) { builder = this }

    suspend fun run(): CommandOutcome {
        val input = CommandInput("$CANARY-TRANSCRIPT", "en", PlanCanary("CONTEXT"))
        return pipelineOf(listOf(plan, next), fake, gate, sink, listener).execute(input)
    }
}

/** Collects the printed form of everything the plan tier returns, delivers or prints. */
private class Sweep {
    val printed = mutableListOf<String>()

    fun see(value: Any?) {
        printed += value.toString()
    }

    fun outcome(outcome: CommandOutcome) {
        see(outcome)
        see(outcome.trace)
        outcome.trace.codes.forEach { see(it) }
        outcome.trace.attempts.forEach { attempt ->
            see(attempt)
            see(attempt.escalationReason)
            see(attempt.suppressedEscalation)
            see(attempt.failure)
            see(attempt.usage)
            attempt.turns.forEach { see(it) }
        }
        outcome.executed.forEach { see(it) }
        outcome.commits.forEach { see(it) }
        outcome.held.forEach { see(it) }
    }

    fun delivered(run: CanaryRun) {
        run.gate.proposals.forEach { see(it) }
        run.sink.actions.forEach {
            see(it)
            see(it.action)
        }
        run.sink.closes.forEach { see(it) }
        run.listener.events.forEach { see(it) }
        see(run.plan)
        see(run.builder)
        see(run.listener)
    }

    fun assertClean() {
        val leaks = printed.filter { CANARY in it }
        assertTrue("leaked: $leaks", leaks.isEmpty())
        assertTrue("swept only ${printed.toSet().size} distinct values", printed.toSet().size >= MIN_DISTINCT)
    }
}

// Codes and tool names in events match a plain identifier shape, so no free text can hide in them.
private fun checkEventFields(events: List<PipelineEvent>) {
    val identifier = Regex("[a-z0-9_.-]+")
    events.forEach { event ->
        when (event) {
            is PipelineEvent.ActionRecorded -> assertTrue(identifier.matches(event.toolName))
            is PipelineEvent.EngineCode -> assertTrue(identifier.matches(event.code.value))
            is PipelineEvent.TierSkipped -> assertTrue(identifier.matches(event.code.value))
            is PipelineEvent.RunClosed -> assertTrue(identifier.matches(event.terminationCode))
            else -> Unit
        }
    }
}

private suspend fun sweepRun(run: CanaryRun): CommandOutcome {
    val outcome = run.run()
    val sweep = Sweep()
    sweep.outcome(outcome)
    sweep.delivered(run)
    sweep.assertClean()
    checkEventFields(run.listener.events)
    outcome.trace.codes.forEach { assertTrue(it.value, Regex("[a-z_]+").matches(it.value)) }
    return outcome
}

/** No step id, argument, bound id, transcript or app result of a plan reaches a sink, a reason or the replan digest. */
class PlanThenExecuteRedactionTest {

    @Test
    fun aCleanCreateThenTagPlanLeaksNothingAndTheCanaryReachesTheExecutor() = runTest {
        NoNetworkGuard.during {
            val plan = planArguments(createWith(ID_ONE), tagWith(ID_TWO, "\$$ID_ONE.$ITEM_KEY"))
            val run = CanaryRun(listOf(planAnswer(plan)), listOf(committing(), committing()))

            val outcome = sweepRun(run)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            // The values are really in the typed data the app receives, so the sweep above is not vacuous.
            assertEquals("$CANARY-NAME", run.executor.calls[0].arguments.getValue("name").jsonPrimitive.content)
            assertEquals(BOUND, run.executor.calls[1].arguments.getValue(ITEM_KEY).jsonPrimitive.content)
            assertEquals(listOf(BOUND, BOUND), outcome.executed.map { it.targetIds[ITEM_KEY] })
            assertTrue(outcome.executed.all { it.appOutcomeToken == "$CANARY-TOKEN" })
        }
    }

    @Test
    fun aReplannedPreCommitFailureLeaksNothingAndTheDigestCarriesCodesOnly() = runTest {
        NoNetworkGuard.during {
            val plan = planArguments(createWith(ID_ONE), tagWith(ID_TWO, "\$$ID_ONE.$ITEM_KEY"))
            val run = CanaryRun(
                listOf(planAnswer(plan), planAnswer(plan, "plan-2")),
                listOf(erroring(), committing(), committing()),
            )

            val outcome = sweepRun(run)

            assertEquals(2, run.fake.callCount)
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            val replan = run.fake.calls[1].request.messages
            val digests = replan.filterIsInstance<ToolResultsMessage>().flatMap { message -> message.results }
            assertTrue(digests.isNotEmpty())
            digests.forEach {
                assertTrue(it.content, CANARY !in it.content)
                assertTrue(it.toString(), CANARY !in it.toString())
            }
            replan.filterIsInstance<ToolResultsMessage>().forEach { assertTrue(CANARY !in it.toString()) }
        }
    }

    @Test
    fun aPostCommitUnresolvedBindingLeaksNothing() = runTest {
        NoNetworkGuard.during {
            val plan = planArguments(createWith(ID_ONE), tagWith(ID_TWO, "\$$ID_ONE.$ITEM_KEY"))
            val run = CanaryRun(listOf(planAnswer(plan)), listOf(committing(mapOf("other" to BOUND))))

            val outcome = sweepRun(run)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(true, (outcome as CommandOutcome.Completed).partial)
            assertEquals(1, run.executor.callCount)
            assertEquals(listOf(ID_TWO), outcome.remainingStepIds)
            assertEquals(0, run.next.executions)
        }
    }

    @Test
    fun aNeedsLookupPlanLeaksNothing() = runTest {
        NoNetworkGuard.during {
            val lookup = planStep(ID_ONE, PLAN_FIND_TOOL, buildJsonObject { put("name", "$CANARY-ARG") })
            val run = CanaryRun(listOf(planAnswer(planArguments(lookup, createWith(ID_TWO)))), emptyList())

            val outcome = sweepRun(run)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, run.next.executions)
            assertEquals(0, run.executor.callCount)
        }
    }

    @Test
    fun aHoldAfterACommitLeaksNothingAndOnlyRemainingStepIdsHandTheNeverRunIdToTheApp() = runTest {
        NoNetworkGuard.during {
            val plan = planArguments(
                createWith(ID_ONE),
                tagWith(ID_TWO, "\$$ID_ONE.$ITEM_KEY"),
                tagWith(ID_THREE, "\$$ID_ONE.$ITEM_KEY"),
            )
            val gate = ScriptedGate.sequence(
                null,
                GateDecision.Admit(),
                GateDecision.Hold(PlanCanary("HOLD"), "$CANARY-HOLD-TOKEN"),
            )
            val run = CanaryRun(listOf(planAnswer(plan)), listOf(committing(), committing()), gate)

            val outcome = sweepRun(run)

            val completed = outcome as CommandOutcome.Completed
            assertNotNull(completed.held.singleOrNull())
            assertEquals(1, completed.commits.size)
            // The one sanctioned exception: the never-run step id is consumer data on the outcome...
            assertEquals(listOf(ID_THREE), completed.remainingStepIds)
            // ...and it never prints: only a count does.
            assertTrue(completed.toString(), "remainingSteps=1" in completed.toString())
            assertTrue(completed.toString(), CANARY !in completed.toString())
            assertEquals(0, run.next.executions)
        }
    }
}
