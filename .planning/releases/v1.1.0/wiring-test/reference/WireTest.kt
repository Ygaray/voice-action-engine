package wire

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The consumer's own scripted provider: plays canned answers and records every request. */
class WireScriptedProvider(
    answers: List<ModelResult>,
    override val id: ProviderId = ProviderId.ANTHROPIC,
) : AiProvider {
    private val remaining = ArrayDeque(answers)
    val requests: MutableList<ProviderRequest> = mutableListOf()

    override val requiresCredential: Boolean = false

    override suspend fun complete(call: ProviderRequest): ModelResult {
        requests.add(call)
        return remaining.removeFirstOrNull() ?: ModelResult.Failure(FailureReason.Other("script_exhausted"))
    }
}

private fun toolCallAnswer(name: String, arguments: JsonObject): ModelResult {
    val call = AssistantPart.ToolCall("call_1", name, arguments)
    return ModelResult.Success(
        ModelResponse(AssistantMessage(listOf(call)), StopReason.TOOL_USE, Usage(100, 0, 0, 20)),
    )
}

private fun stepOf(id: String, title: String, parentRef: String? = null): JsonObject = buildJsonObject {
    put("id", id)
    put("tool", "create_item")
    putJsonObject("arguments") {
        put("title", title)
        if (parentRef != null) put("parent_id", parentRef)
    }
}

private fun planAnswer(vararg steps: JsonObject): ModelResult =
    toolCallAnswer("submit_plan", buildJsonObject { putJsonArray("steps") { steps.forEach { add(it) } } })

private class Rig(answers: List<ModelResult>) {
    val store = WireStore()
    val journal = wireJournal(store)
    val provider = WireScriptedProvider(answers)
    val resolver = WireResolver(store, journal, "en")
}

class WireTest {
    @Test
    fun anEnglishGrammarCommandCompletesWithZeroProviderCalls() = runTest {
        val rig = Rig(emptyList())
        val pipeline = wirePipeline(wireTiers(rig.store, rig.journal, rig.resolver), rig.journal, rig.provider)

        val outcome = pipeline.execute(CommandInput("please add buy paper to my list", "en"))

        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        assertEquals(listOf("buy paper"), rig.store.titles())
        assertEquals(emptyList<ProviderRequest>(), rig.provider.requests)
        assertEquals(listOf("grammar"), outcome.trace.attempts.map { it.strategy.value })
        assertEquals(listOf("en"), rig.resolver.spoken)
    }

    @Test
    fun aSpanishGrammarCommandCompletes() = runTest {
        val rig = Rig(emptyList())
        val pipeline = wirePipeline(wireTiers(rig.store, rig.journal, rig.resolver), rig.journal, rig.provider)

        val outcome = pipeline.execute(CommandInput("por favor pon comprar papel en mi lista", "es"))

        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        assertEquals(listOf("comprar papel"), rig.store.titles())
        assertEquals(emptyList<ProviderRequest>(), rig.provider.requests)
        assertEquals(listOf("es"), rig.resolver.spoken)
    }

    @Test
    fun theSecondPlanStepReceivesTheFirstStepsId() = runTest {
        val rig = Rig(listOf(planAnswer(stepOf("first", "Groceries"), stepOf("second", "Milk", "\$first.id"))))
        val pipeline = wirePipeline(listOf(wirePlanTier(rig.store, rig.journal)), rig.journal, rig.provider)

        val outcome = pipeline.execute(CommandInput("make a list called groceries with milk under it", "en"))

        val completed = outcome as CommandOutcome.Completed
        assertEquals(outcome.toString(), 2, completed.commits.size)
        assertFalse(completed.partial)
        assertEquals(1, rig.provider.requests.size)
        val rows = rig.store.all()
        assertEquals(listOf("Groceries", "Milk"), rows.map { it.title })
        assertNull(rows[0].parentId)
        assertEquals(rows[0].id, rows[1].parentId)
    }

    @Test
    fun aScriptedRouterAnswerStartsTheWalkAtThePlanTier() = runTest {
        val rig = Rig(
            listOf(
                toolCallAnswer("pick_start_tier", buildJsonObject { put("tier", "plan") }),
                planAnswer(stepOf("only", "Paper")),
            ),
        )
        val pipeline = wireRoutedPipeline(rig.store, rig.journal, rig.provider, rig.resolver)

        val outcome = pipeline.execute(CommandInput("set up a list for paper", "en"))

        val completed = outcome as CommandOutcome.Completed
        val selection = completed.trace.selection!!
        assertEquals("picked", selection.outcome)
        assertEquals(StrategyId("plan"), selection.picked)
        assertEquals(1, selection.tiersBypassed)
        val ran = completed.trace.attempts.map { it.strategy.value }
        assertFalse(ran.toString(), "single_shot" in ran)
        assertEquals("plan", ran.last())
        assertEquals(listOf("my-small-model", "my-model"), rig.provider.requests.map { it.model })
        assertEquals(listOf("Paper"), rig.store.titles())
    }

    @Test
    fun undoAllRestoresTheStore() = runTest {
        val rig = Rig(listOf(planAnswer(stepOf("first", "Groceries"), stepOf("second", "Milk", "\$first.id"))))
        val pipeline = wirePipeline(listOf(wirePlanTier(rig.store, rig.journal)), rig.journal, rig.provider) {
            runIds = { "run-1" }
        }

        pipeline.execute(CommandInput("make a list called groceries with milk under it", "en"))

        assertEquals(listOf("Groceries", "Milk"), rig.store.titles())
        assertEquals("Undo all (2)", undoLabel(rig.journal, "run-1"))
        assertEquals("Undone (2)", undoAllText(rig.journal, "run-1"))
        assertEquals(emptyList<String>(), rig.store.titles())
        assertEquals("Nothing to undo", undoAllText(rig.journal, "run-1"))
    }

    @Test
    fun aProviderFailureEndsFailedAndRendersThroughTheElseBranch() = runTest {
        val rig = Rig(listOf(ModelResult.Failure(FailureReason.Other("boom"))))
        val tiers = listOf(wireGrammarTier(rig.resolver), wireSingleShotTier(rig.resolver))
        val pipeline = wirePipeline(tiers, rig.journal, rig.provider)

        val outcome = pipeline.execute(CommandInput("write something the grammar does not know", "en"))

        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertTrue(rig.store.titles().isEmpty())
        assertTrue(render(outcome).headline.startsWith("Failed: "))
    }
}
