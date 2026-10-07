package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.legs.ItemWorld
import io.github.ygaray.voiceactionengine.sample.undo.ITEM_TOOL_CREATE
import io.github.ygaray.voiceactionengine.sample.undo.ItemToolExecutor
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIRST_STEP = "stepone"
private const val SECOND_STEP = "steptwo"
private const val FIRST_TITLE = "zzalphacanary"
private const val SECOND_TITLE = "zzbetacanary"

/** One plan step as the model writes it into `submit_plan`. */
private fun step(id: String, title: String, parentRef: String?): JsonObject = buildJsonObject {
    put("id", id)
    put("tool", "create_item")
    put(
        "arguments",
        buildJsonObject {
            put("title", title)
            if (parentRef != null) put("parent_id", parentRef)
        },
    )
}

private fun plan(vararg steps: JsonObject): JsonObject = buildJsonObject {
    put("steps", buildJsonArray { steps.forEach { add(it) } })
}

/** The `plan_live` leg run through the real runner, tap and budget wrapper over a scripted provider (no live call). */
class PlanLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val usage = Usage(10, 0, 0, 5)
    private val allow = Regex(ALLOW_PATTERN)

    private fun planCall(vararg steps: JsonObject) =
        ok(FakeAiProvider.toolCall("call_plan", "submit_plan", plan(*steps), usage))

    private fun rigOf(vararg steps: AttemptStep) =
        legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(ProviderId.ANTHROPIC, tap, steps.toList())) }

    @Test
    fun theSecondStepUsesTheFirstStepsNewIdAndTheLegPasses() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(
                planCall(
                    step(FIRST_STEP, FIRST_TITLE, null),
                    step(SECOND_STEP, SECOND_TITLE, "\$$FIRST_STEP.id"),
                ),
            )

            val result = rig.runner.run(LegId.PLAN_LIVE)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val outcome = result.outcome as CommandOutcome.Completed
            assertFalse(outcome.partial)
            assertTrue(outcome.remainingStepIds.isEmpty())
            assertEquals(2, outcome.commits.size)
            val verdicts = rig.sink.starting("VAE_VERDICT ")
            assertEquals(verdicts.toString(), 1, verdicts.size)
            assertTrue(
                verdicts.single(),
                verdicts.single().startsWith(
                    "VAE_VERDICT leg=plan_live verdict=PASS committed=2 bound=1 remaining=0 replanned=0 key_charset=ok",
                ),
            )
            val request = rig.fake(ProviderId.ANTHROPIC).calls.single().request
            assertEquals(ToolChoice.Required("submit_plan"), request.toolChoice)
            val traces = rig.sink.starting("VAE_TRACE ")
            assertEquals(traces.toString(), 1, traces.size)
            assertTrue(traces.single(), traces.single().startsWith("VAE_TRACE leg=plan_live case=1 kind=completed capped=none "))
            assertTrue(traces.single(), traces.single().contains(" sel=none eligible=none "))
            assertEquals(1, rig.sink.starting("VAE_BUDGET core=1 ").size)
        }
    }

    @Test
    fun noLineCarriesATitleAStepIdOrTheReference() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(
                planCall(
                    step(FIRST_STEP, FIRST_TITLE, null),
                    step(SECOND_STEP, SECOND_TITLE, "\$$FIRST_STEP.id"),
                ),
            )

            rig.runner.run(LegId.PLAN_LIVE)

            val text = rig.sink.rendered.joinToString("\n").lowercase()
            for (leaked in listOf(FIRST_TITLE, SECOND_TITLE, FIRST_STEP, SECOND_STEP, "\$", "item-1", "item-2")) {
                assertFalse("$leaked in $text", text.contains(leaked))
            }
            for (line in rig.sink.rendered) assertTrue(line, allow.matches(line))
        }
    }

    @Test
    fun aReferenceTheToolDoesNotReturnStopsThePlanAndFailsNotBound() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(
                planCall(
                    step(FIRST_STEP, FIRST_TITLE, null),
                    step(SECOND_STEP, SECOND_TITLE, "\$$FIRST_STEP.nothing"),
                ),
            )

            val result = rig.runner.run(LegId.PLAN_LIVE)

            assertEquals(result.toString(), VerdictKind.FAIL, result.verdict.kind)
            assertEquals("not_bound", result.verdict.reason)
            val verdict = rig.sink.starting("VAE_VERDICT ").single()
            assertTrue(verdict, verdict.startsWith("VAE_VERDICT leg=plan_live verdict=FAIL reason=not_bound committed=1 bound=0 remaining=1 "))
            assertTrue(rig.sink.starting("VAE_TRACE ").single().contains("plan_binding_unresolved"))
            // The command did not replan after a change was applied: only one planning call went out.
            assertEquals(1, rig.fake(ProviderId.ANTHROPIC).calls.size)
        }
    }

    @Test
    fun aSecondStepThatIgnoresTheFirstIdCompletesButIsNotBound() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(
                planCall(
                    step(FIRST_STEP, FIRST_TITLE, null),
                    step(SECOND_STEP, SECOND_TITLE, null),
                ),
            )

            val result = rig.runner.run(LegId.PLAN_LIVE)

            // Two commits and nothing remaining, yet the store shows no parent: a count alone would have passed this.
            assertEquals(2, (result.outcome as CommandOutcome.Completed).commits.size)
            assertEquals(VerdictKind.FAIL, result.verdict.kind)
            assertEquals("not_bound", result.verdict.reason)
            assertTrue(rig.sink.starting("VAE_VERDICT ").single().contains(" committed=2 bound=0 remaining=0 "))
        }
    }

    @Test
    fun aPlanOfOneStepFailsOnItsCommitCount() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(planCall(step(FIRST_STEP, FIRST_TITLE, null)))

            val result = rig.runner.run(LegId.PLAN_LIVE)

            assertEquals(VerdictKind.FAIL, result.verdict.kind)
            assertEquals("commit_count_1", result.verdict.reason)
        }
    }

    @Test
    fun aRefusedPlanIsNotCompleted() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(ok(FakeAiProvider.reply("no plan", usage)), ok(FakeAiProvider.reply("still none", usage)))

            val result = rig.runner.run(LegId.PLAN_LIVE)

            assertEquals(VerdictKind.FAIL, result.verdict.kind)
            assertFalse(result.verdict.toString(), result.verdict.reason == null)
            assertNotNull(result.verdict.reason)
        }
    }

    @Test
    fun theToolDescriptionsNameTheKeyTheyReturnAndAreSyntheticWords() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(
                planCall(
                    step(FIRST_STEP, FIRST_TITLE, null),
                    step(SECOND_STEP, SECOND_TITLE, "\$$FIRST_STEP.id"),
                ),
            )

            rig.runner.run(LegId.PLAN_LIVE)

            val tools = rig.fake(ProviderId.ANTHROPIC).calls.single().request.tools
            val create = tools.first { it.name == "create_item" }
            val rename = tools.first { it.name == "rename_item" }
            assertTrue(create.description, "key id" in create.description)
            assertTrue(rename.description, "key id" in rename.description)
            assertTrue(create.mutating && rename.mutating)
        }
    }

    @Test
    fun theExecutorGivesAFinishedErrorForAnUnknownToolAndForBadArgumentsAndNeverThrows() = runTest {
        val world = ItemWorld()
        val executor: ItemToolExecutor = world.executor

        val unknown = executor.prepare(Extraction("delete_everything", buildJsonObject { put("title", "x") }), CommandInput("x"))
        val blank = executor.prepare(Extraction(ITEM_TOOL_CREATE, buildJsonObject { put("title", " ") }), CommandInput("x"))
        val numeric = executor.prepare(
            Extraction(ITEM_TOOL_CREATE, buildJsonObject { put("title", "ok"); put("parent_id", 7) }),
            CommandInput("x"),
        )

        for (step in listOf(unknown, blank, numeric)) {
            assertTrue(step.toString(), step is ToolStep.Finished && step.kind == FinishedKind.ERROR)
        }
        assertTrue(world.store.snapshot().isEmpty())
    }
}
