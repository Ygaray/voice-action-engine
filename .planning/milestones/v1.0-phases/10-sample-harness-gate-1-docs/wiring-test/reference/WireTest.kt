package wire

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
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

private fun createCallAnswer(title: String): ModelResult {
    val call = AssistantPart.ToolCall("call_1", "create_item", buildJsonObject { put("title", title) })
    return ModelResult.Success(
        ModelResponse(AssistantMessage(listOf(call)), StopReason.TOOL_USE, Usage(100, 0, 0, 20)),
    )
}

class WireTest {
    @Test
    fun forcedCreateCallCompletesWithOneCommittedAction() = runTest {
        val items = mutableListOf<String>()
        val provider = WireScriptedProvider(listOf(createCallAnswer("buy paper")))
        val outcome = wirePipeline(items, provider).execute(CommandInput("add buy paper to my list", "en"))

        assertTrue(outcome is CommandOutcome.Completed)
        assertEquals(1, outcome.commits.size)
        assertEquals(listOf("buy paper"), items)
        assertEquals(1, provider.requests.size)
        assertEquals("Done: Added it.", render(outcome).headline)
    }

    @Test
    fun providerFailureEndsFailedAndRendersThroughTheElseBranch() = runTest {
        val items = mutableListOf<String>()
        val provider = WireScriptedProvider(listOf(ModelResult.Failure(FailureReason.Other("boom"))))
        val outcome = wirePipeline(items, provider).execute(CommandInput("add buy paper to my list", "en"))

        assertTrue(outcome is CommandOutcome.Failed)
        assertTrue(items.isEmpty())
        assertTrue(render(outcome).headline.startsWith("Failed: "))
    }
}
