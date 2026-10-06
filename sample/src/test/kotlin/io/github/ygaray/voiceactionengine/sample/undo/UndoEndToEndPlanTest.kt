package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.undo.UndoResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val CREATE = "create_item"
private const val RENAME = "rename_item"
private const val RUN = "run-1"

private fun createSpec(): ToolSpec = ToolSpec(
    CREATE,
    "Creates one item.",
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("name") { put("type", "string") } }
    },
    mutating = true,
)

private fun renameSpec(): ToolSpec = ToolSpec(
    RENAME,
    "Renames one item.",
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("item_id") { put("type", "string") }
            putJsonObject("title") { put("type", "string") }
        }
    },
    mutating = true,
)

private fun planStep(id: String, tool: String, arguments: JsonObject): JsonObject = buildJsonObject {
    put("id", id)
    put("tool", tool)
    put("arguments", arguments)
}

private fun named(name: String): JsonObject = buildJsonObject { put("name", name) }

private fun renaming(id: String, title: String): JsonObject = buildJsonObject {
    put("item_id", id)
    put("title", title)
}

/** S7: a real PlanThenExecute run that commits two steps and holds the third is undoable inside the run. */
class UndoEndToEndPlanTest {

    private class Plan(val rig: UndoRig, val provider: FakeAiProvider, val pipeline: CommandPipeline)

    private fun planOf(rig: UndoRig): Plan {
        val steps = buildJsonObject {
            put(
                "steps",
                buildJsonArray {
                    add(planStep("s1", CREATE, named("n1")))
                    add(planStep("s2", RENAME, renaming("a", "v1")))
                    add(planStep("s3", CREATE, named("n2")))
                    add(planStep("s4", RENAME, renaming("a", "v2")))
                },
            )
        }
        val provider = FakeAiProvider(
            ProviderId.ANTHROPIC,
            FakeAiProvider.toolCall("plan-1", "submit_plan", steps, Usage(1, 0, 0, 1)),
        )
        val plan = PlanThenExecuteStrategy(StrategyId("plan")) {
            tooling = ToolSpecProvider.fixed(ToolingSnapshot("plan system", listOf(createSpec(), renameSpec()), null))
            executor = ToolExecutor { call, _ ->
                val mutation: PendingMutation = when (call.toolName) {
                    CREATE -> rig.create(call.arguments.getValue("name").jsonPrimitive.content)
                    else -> rig.rename(
                        call.arguments.getValue("item_id").jsonPrimitive.content,
                        call.arguments.getValue("title").jsonPrimitive.content,
                    )
                }
                ToolStep.Mutation(mutation)
            }
        }
        val ids = AtomicInteger()
        val pipeline = commandPipeline {
            tier(plan)
            provider(provider)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model"))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "k")
            gate = ScriptedGate.sequence(
                null,
                GateDecision.Admit(),
                GateDecision.Admit(),
                GateDecision.Hold("confirm", null),
            )
            commitSink = compositeSink(rig.bridge, rig.recording)
            runIds = { "run-${ids.incrementAndGet()}" }
        }
        return Plan(rig, provider, pipeline)
    }

    private suspend fun Plan.runIt(): CommandOutcome.Completed {
        val outcome = pipeline.execute(CommandInput("make, rename, make, rename"))
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        return outcome as CommandOutcome.Completed
    }

    @Test
    fun s7aAPlanPartialIsUndoableWithinTheRun() = runTest {
        NoNetworkGuard.during {
            val plan = planOf(UndoRig())
            val rig = plan.rig

            val outcome = plan.runIt()

            assertTrue(outcome.partial)
            val kinds = listOf(ActionKind.COMMITTED, ActionKind.COMMITTED, ActionKind.HELD)
            assertEquals(kinds, outcome.executed.map { it.kind })
            assertEquals(listOf(0, 1, 2), outcome.executed.map { it.position })
            assertEquals(listOf("s4"), outcome.remainingStepIds)
            assertEquals(1, plan.provider.callCount)
            val group = rig.journal.group(RUN)!!
            assertEquals(2, group.count)
            assertFalse(group.withheld)
            assertEquals(1, rig.bridge.pendingHeld(RUN))

            val result = rig.journal.undoAll(RUN)

            assertTrue(result.toString(), result is UndoResult.Complete)
            assertEquals(2, (result as UndoResult.Complete).restored.size)
            assertEquals(rig.seeded, rig.store.snapshot())
        }
    }

    @Test
    fun s7bConfirmingTheHeldStepJoinsTheSameGroup() = runTest {
        NoNetworkGuard.during {
            val plan = planOf(UndoRig())
            val rig = plan.rig
            val outcome = plan.runIt()

            plan.pipeline.commitHeld(outcome.held.single())

            assertEquals("run-1", rig.recording.actions.last().heldRunId)
            val group = rig.journal.group(RUN)!!
            assertEquals(3, group.count)
            assertEquals(0, rig.bridge.pendingHeld(RUN))
            assertEquals(setOf("a", "item-1", "item-2"), rig.store.snapshot().keys)

            val result = rig.journal.undoAll(RUN)

            assertTrue(result.toString(), result is UndoResult.Complete)
            assertEquals(3, (result as UndoResult.Complete).restored.size)
            assertEquals(rig.seeded, rig.store.snapshot())
        }
    }
}
