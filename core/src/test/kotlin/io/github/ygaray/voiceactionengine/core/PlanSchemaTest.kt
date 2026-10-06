package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.strategy.plan.submitPlanSpec
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PINNED_SCHEMA =
    """{"type":"object","properties":{"steps":{"type":"array","maxItems":8,"items":{"type":"object",""" +
        """"properties":{"id":{"type":"string"},"tool":{"type":"string","enum":["create_item","find_item",""" +
        """"tag_item"]},"arguments":{"type":"object"}},"required":["id","tool","arguments"]}},""" +
        """"needs_lookup":{"type":"boolean"}},"required":["steps"]}"""

// The frozen model-facing text: a change needs a recorded probe failure and a gap plan.
private const val PINNED_DESCRIPTION =
    "Submit the whole plan for the command as ordered steps, each calling one of the listed tools with its " +
        "arguments. Give every step a short id that starts with a letter and uses only letters, digits, _ or -. " +
        "To use a value that an earlier step's tool returns, write the whole argument value as the string " +
        "\$<stepId>.<key>, with the key names given in that tool's description. A reference is the entire value, " +
        "never part of a longer string, and only names a step listed earlier. Do not call any other tool. " +
        "When the command needs information you do not have, for example something that must be looked up first, " +
        "set needs_lookup to true and leave steps empty."

private fun onePlan(): FakeAiProvider =
    FakeAiProvider(
        ProviderId.ANTHROPIC,
        planAnswer(planArguments(planStep("s1", PLAN_CREATE_TOOL, buildJsonObject { put("name", "a") }))),
    )

private fun failureCode(outcome: CommandOutcome): String =
    ((outcome as CommandOutcome.Failed).reason as FailureReason.Other).code

/** The engine-owned submit_plan tool, the request that carries it, and the guards that run before any call. */
class PlanSchemaTest {

    private val offered = listOf(createTool(), findTool(), askTool(), tagTool())

    private suspend fun run(
        fake: FakeAiProvider,
        tools: List<ToolSpec>,
        configure: PlanThenExecuteStrategy.Builder.() -> Unit = {},
    ): Pair<CommandOutcome, ScriptedToolExecutor> {
        val executor = committingExecutor(null)
        val tier = planStrategy(executor, planSnapshot(*tools.toTypedArray()), configure = configure)
        val pipeline = pipelineOf(listOf(tier), fake, ScriptedGate.admitAll(), RecordingCommitSink())
        return pipeline.execute(CommandInput("add one thing", "en", null)) to executor
    }

    @Test
    fun theSchemaIsPinnedByteForByte() {
        val spec = submitPlanSpec(offered, 8)

        assertEquals(PINNED_SCHEMA, spec.inputSchema.toString())
    }

    @Test
    fun maxStepsIsTheMaxItemsOfTheStepsArray() {
        val schema = submitPlanSpec(offered, 3).inputSchema.toString()

        assertTrue(schema, schema.contains("\"maxItems\":3"))
    }

    @Test
    fun theSpecIsEngineOwnedNonMutatingNonTerminalAndNonStrict() {
        val spec = submitPlanSpec(offered, 8)

        assertEquals("submit_plan", spec.name)
        assertFalse(spec.mutating)
        assertFalse(spec.terminal)
        assertEquals(false, spec.strict)
    }

    @Test
    fun theDescriptionIsPinnedAndNamesTheReferenceGrammar() {
        val description = submitPlanSpec(offered, 8).description

        assertEquals(PINNED_DESCRIPTION, description)
        assertTrue(description.contains("\$<stepId>.<key>"))
        assertTrue(description.contains("needs_lookup"))
    }

    @Test
    fun theRequestForcesSubmitPlanAndCarriesTheSnapshotsTools() = runTest {
        NoNetworkGuard.during {
            val fake = onePlan()

            val (outcome, _) = run(fake, offered) { reasoning = ReasoningMode.PROVIDER_DEFAULT }

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, fake.callCount)
            val sent: ModelRequest = fake.calls.single().request
            val expected = listOf("submit_plan", PLAN_CREATE_TOOL, PLAN_FIND_TOOL, ASK_TOOL, PLAN_TAG_TOOL)
            assertEquals(expected, sent.tools.map { it.name })
            assertEquals(ToolChoice.Required("submit_plan"), sent.toolChoice)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, sent.maxTokens)
            assertEquals(CacheDirective(true), sent.cache)
            assertTrue(sent.singleToolCall)
            assertEquals(ReasoningMode.PROVIDER_DEFAULT, sent.reasoning)
            assertEquals(1, sent.messages.size)
            assertTrue(sent.messages.single() is UserMessage)
        }
    }

    @Test
    fun aSnapshotThatOffersTheEngineToolNameFailsBeforeAnyCall() = runTest {
        NoNetworkGuard.during {
            val schema = buildJsonObject { put("type", "object") }
            val taken = ToolSpec("submit_plan", "App tool.", schema, mutating = true)
            val fake = onePlan()

            val (outcome, executor) = run(fake, listOf(createTool(), taken))

            assertEquals("plan_tool_name_taken", failureCode(outcome))
            assertEquals(0, fake.callCount)
            assertEquals(0, executor.callCount)
        }
    }

    @Test
    fun aSnapshotWithNoNonTerminalToolFailsBeforeAnyCall() = runTest {
        NoNetworkGuard.during {
            val fake = onePlan()

            val (outcome, executor) = run(fake, listOf(askTool()))

            assertEquals("plan_no_tools", failureCode(outcome))
            assertEquals(0, fake.callCount)
            assertEquals(0, executor.callCount)
        }
    }

    @Test
    fun missingSettingsAndAZeroStepLimitAreRejectedNamingTheSetting() {
        val noTooling = assertThrows(IllegalArgumentException::class.java) {
            PlanThenExecuteStrategy(StrategyId("plan")) { executor = committingExecutor(null) }
        }
        val noExecutor = assertThrows(IllegalArgumentException::class.java) {
            PlanThenExecuteStrategy(StrategyId("plan")) {
                tooling = ToolSpecProvider.fixed(planSnapshot(createTool()))
            }
        }
        val zeroSteps = assertThrows(IllegalArgumentException::class.java) {
            planStrategy(committingExecutor(null), planSnapshot(createTool())) { maxSteps = 0 }
        }

        assertTrue(noTooling.message.orEmpty(), noTooling.message.orEmpty().contains("tooling"))
        assertTrue(noExecutor.message.orEmpty(), noExecutor.message.orEmpty().contains("executor"))
        assertTrue(zeroSteps.message.orEmpty(), zeroSteps.message.orEmpty().contains("maxSteps"))
    }

    @Test
    fun theStrategyPrintsItsIdAndStepLimitOnly() {
        val tier = planStrategy(committingExecutor(null), planSnapshot(createTool()))

        assertEquals("PlanThenExecuteStrategy(id=plan, maxSteps=8)", tier.toString())
    }
}
