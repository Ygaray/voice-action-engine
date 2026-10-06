package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tracer: one planning call, two steps, both through the executor, the gate, apply and the sink in order. */
class PlanThenExecuteRunTest {

    @Test
    fun oneForcedPlanCallRunsBothStepsInOrderEachAsItsOwnProposal() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val executor = committingExecutor(log)
            val createArguments = buildJsonObject { put("name", "a") }
            val tagArguments = buildJsonObject {
                put("item_id", "x")
                put("tag", "red")
            }
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                planAnswer(
                    planArguments(
                        planStep("s1", PLAN_CREATE_TOOL, createArguments),
                        planStep("s2", PLAN_TAG_TOOL, tagArguments),
                    ),
                ),
            )
            val gate = ScriptedGate.admitAll(log)
            val sink = RecordingCommitSink(log)
            val second = ScriptedStrategy(StrategyId("second"), { _, _ -> StrategyOutcome.Completed(null) })
            val plan = planStrategy(executor, planSnapshot(createTool(), tagTool()))
            val pipeline = pipelineOf(listOf(plan, second), fake, gate, sink)

            val outcome = pipeline.execute(CommandInput("make a and tag it red", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            outcome as CommandOutcome.Completed
            assertEquals(null, outcome.reply)
            assertEquals(false, outcome.partial)
            assertEquals(1, fake.callCount)
            assertEquals(1, outcome.trace.attempts.first().turns.size)
            assertEquals(0, second.executions)
            assertEquals(
                listOf("prepare:$PLAN_CREATE_TOOL", "gate", "apply:$PLAN_CREATE_TOOL"),
                log.events.take(3),
            )
            assertTrue(log.events[3].startsWith("sink:action:0:"))
            assertEquals(
                listOf("prepare:$PLAN_TAG_TOOL", "gate", "apply:$PLAN_TAG_TOOL"),
                log.events.subList(4, 7),
            )
            assertTrue(log.events[7].startsWith("sink:action:1:"))
            assertEquals(2, gate.calls)
            assertTrue(gate.proposals.all { it.mutations.size == 1 })
            assertEquals(listOf(0, 1), outcome.executed.map { it.position })
            assertTrue(outcome.executed.all { it.kind == ActionKind.COMMITTED })
            assertTrue(outcome.executed.all { it.providerCallId == PLAN_CALL_ID })
            assertEquals(listOf(PLAN_CREATE_TOOL, PLAN_TAG_TOOL), executor.calls.map { it.toolName })
            assertTrue(executor.calls.all { it.callId == PLAN_CALL_ID })
            assertEquals(listOf(createArguments, tagArguments), executor.calls.map { it.arguments })
        }
    }
}
