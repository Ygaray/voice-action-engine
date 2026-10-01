package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ChatModelsTest {

    /** A provider whose model facts come from the table under test; [calls] counts what reaches `complete`. */
    private class TableBackedChatProvider(private val vendor: ChatVendor) : AiProvider {
        val calls = AtomicInteger()

        override val id: ProviderId = vendor.providerId

        override fun capabilities(model: String): ModelCapabilities = ChatModels.capabilities(vendor, model)

        override suspend fun complete(call: ProviderRequest): ModelResult {
            calls.incrementAndGet()
            return FakeAiProvider.reply("ok", Usage.ZERO)
        }
    }

    /** What one routed command produced. */
    private class Run(
        val provider: TableBackedChatProvider,
        val pipeline: CommandPipeline,
        val result: ModelResult?,
        val outcome: CommandOutcome,
        val bound: ModelCapabilities?,
    )

    private val tool = ToolSpec("lookup", "finds a thing", JsonObject(emptyMap()))

    private fun run(
        vendor: ChatVendor,
        model: String,
        withTools: Boolean,
        override: (ModelCapabilities.Builder.() -> Unit)? = null,
    ): Run {
        val provider = TableBackedChatProvider(vendor)
        val results = RecordingSink<ModelResult>()
        val bounds = RecordingSink<ModelCapabilities?>()
        val step: StrategyStep = { input, session ->
            val handle = session.model()
            bounds.record(handle.capabilities)
            val tools = if (withTools) listOf(tool) else emptyList()
            val result = handle.complete(ModelRequest("sys", listOf(UserMessage(input.transcript)), tools, 100))
            results.record(result)
            when (result) {
                is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
                else -> StrategyOutcome.Completed(null)
            }
        }
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("probe"), step))
            provider(provider)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
            credentials = ScriptedCredentialSource.keys(vendor.providerId to "sk-test")
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
            if (override != null) capabilities(vendor.providerId, model, override)
        }
        val outcome = runBlocking { pipeline.execute(CommandInput("hello", "en", null)) }
        return Run(provider, pipeline, results.events.singleOrNull(), outcome, bounds.events.singleOrNull())
    }

    @Test
    fun aToolsRequestToAResponsesOnlyModelOnOpenAiIsRefusedBeforeAnyCall() {
        val run = run(ChatVendor.OPENAI, "gpt-6-astra", withTools = true)

        val failure = run.result as ModelResult.Failure
        assertEquals("model_unsupported", failure.reason.code)
        assertEquals(FailureReason.ModelUnsupported(), (run.outcome as CommandOutcome.Failed).reason)
        assertTrue(run.outcome.trace.codes.contains(TraceCode.CAPABILITY_REFUSED))
        assertEquals(0, run.provider.calls.get())
    }

    @Test
    fun theSameModelWithoutToolsReachesTheProvider() {
        val run = run(ChatVendor.OPENAI, "gpt-6-astra", withTools = false)

        assertTrue(run.result is ModelResult.Success)
        assertEquals(1, run.provider.calls.get())
    }

    @Test
    fun theBothModelsAndTheirDatedIdsReadAsToolIncapableFromThePublicTable() {
        val table = run(ChatVendor.OPENAI, "gpt-5.4-mini", withTools = false).pipeline.capabilityTable
        for (id in listOf("gpt-6-astra", "gpt-6.1-sol", "gpt-6-astra-2026-09-01", "gpt-6.1-sol-2026-09-01")) {
            assertFalse(id, table.lookup(ProviderId.OPENAI, id).supportsTools)
        }

        val mini = table.lookup(ProviderId.OPENAI, "gpt-5.4-mini")
        assertTrue(mini.supportsTools)
        assertEquals(CachingMode.AUTOMATIC, mini.caching)
        assertEquals(1_024, mini.minCacheablePrefixTokens)
    }

    @Test
    fun aNeighbouringIdIsNotTheResponsesOnlyFamily() {
        for (id in listOf("gpt-6-astral", "xgpt-6-astra", "gpt-6.1-solar", "gpt-6", "gpt-6-sol")) {
            assertTrue(id, ChatModels.capabilities(ChatVendor.OPENAI, id).supportsTools)
        }
    }

    @Test
    fun anAppOverrideForTheExactIdWinsAndTheCallReachesTheProvider() {
        val run = run(ChatVendor.OPENAI, "gpt-6-astra", withTools = true) { supportsTools = true }

        assertNotNull(run.bound)
        assertTrue(run.bound!!.supportsTools)
        assertTrue(run.result is ModelResult.Success)
        assertEquals(1, run.provider.calls.get())
    }

    @Test
    fun theTwoVendorInstancesCarryTheirDifferencesAsData() {
        val openAi = ChatVendor.OPENAI
        assertEquals(ProviderId.OPENAI, openAi.providerId)
        assertEquals("https://api.openai.com/v1/", openAi.productionBaseUrl)
        assertEquals("x-request-id", openAi.requestIdHeader)
        assertFalse(openAi.requestIdInBody)
        assertFalse(openAi.routedModelIds)
        assertFalse(openAi.requireParametersOnForced)
        assertTrue(openAi.parallelToolCallsFalseOnForced)

        val router = ChatVendor.OPENROUTER
        assertEquals(ProviderId.OPENROUTER, router.providerId)
        assertEquals("https://openrouter.ai/api/v1/", router.productionBaseUrl)
        assertEquals(null, router.requestIdHeader)
        assertTrue(router.requestIdInBody)
        assertTrue(router.routedModelIds)
        assertTrue(router.requireParametersOnForced)
        assertFalse(router.parallelToolCallsFalseOnForced)

        assertEquals("ChatVendor(openai)", openAi.toString())
        assertEquals("ChatVendor(openrouter)", router.toString())
    }
}
