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
import org.junit.Assert.assertNull
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

    @Test
    fun keysFollowTheVendorsIdShape() {
        val variant = ChatModels.key(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini:nitro")
        assertEquals("openai", variant.family)
        assertEquals("gpt-5.4-mini", variant.id)

        val anthropic = ChatModels.key(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5")
        assertEquals("anthropic", anthropic.family)
        assertEquals("claude-sonnet-5.5", anthropic.id)

        assertEquals("other", ChatModels.key(ChatVendor.OPENROUTER, "example/tool-model").family)
        assertEquals("other", ChatModels.key(ChatVendor.OPENROUTER, "no-slash-model").family)
        assertEquals("openai", ChatModels.key(ChatVendor.OPENROUTER, "OpenAI/gpt-5.4-mini").family)

        val direct = ChatModels.key(ChatVendor.OPENAI, "gpt-5.4-mini")
        assertEquals("openai", direct.family)
        assertEquals("gpt-5.4-mini", direct.id)
        // The direct vendor never splits: a slash in an id is just part of the id.
        assertEquals("a/b:c", ChatModels.key(ChatVendor.OPENAI, "a/b:c").id)
    }

    @Test
    fun aRoutedOpenAiIdInheritsTheOpenAiFacts() {
        val routed = ChatModels.capabilities(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini")

        assertEquals(ChatModels.capabilities(ChatVendor.OPENAI, "gpt-5.4-mini"), routed)
        assertTrue(routed.supportsTools)
        assertTrue(routed.supportsForcedToolChoice)
        assertEquals(CachingMode.AUTOMATIC, routed.caching)
        assertEquals(1_024, routed.minCacheablePrefixTokens)
        assertEquals(routed, ChatModels.capabilities(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini:nitro"))
    }

    @Test
    fun theResponsesOnlyPairTakesToolsThroughTheRouterButNotOnOpenAi() {
        assertTrue(ChatModels.capabilities(ChatVendor.OPENROUTER, "openai/gpt-6-astra").supportsTools)
        assertTrue(ChatModels.capabilities(ChatVendor.OPENROUTER, "openai/gpt-6.1-sol").supportsTools)
        assertFalse(ChatModels.capabilities(ChatVendor.OPENAI, "gpt-6-astra").supportsTools)
    }

    @Test
    fun aRoutedAnthropicIdTakesTheForcedToolFactAndNeverCaches() {
        val sonnet = ChatModels.capabilities(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5")
        assertFalse(sonnet.supportsForcedToolChoice)
        assertEquals(CachingMode.NONE, sonnet.caching)
        assertNull(sonnet.minCacheablePrefixTokens)

        val haiku = ChatModels.capabilities(ChatVendor.OPENROUTER, "anthropic/claude-haiku-4.5")
        assertTrue(haiku.supportsForcedToolChoice)
        assertEquals(CachingMode.NONE, haiku.caching)
    }

    @Test
    fun anyOtherRoutedVendorOrAnIdWithoutASlashIsUnknown() {
        assertEquals(ModelCapabilities.UNKNOWN, ChatModels.capabilities(ChatVendor.OPENROUTER, "example/tool-model"))
        assertEquals(ModelCapabilities.UNKNOWN, ChatModels.capabilities(ChatVendor.OPENROUTER, "no-slash-model"))
        assertEquals(ModelCapabilities.UNKNOWN, ChatModels.capabilities(ChatVendor.OPENROUTER, "/leading"))
        assertEquals(ModelCapabilities.UNKNOWN, ChatModels.capabilities(ChatVendor.OPENROUTER, ":only-variant"))
    }

    @Test
    fun oddIdsNeverThrow() {
        for (id in listOf("a", "/", "//", ":", "x/:", "openai/", "anthropic/", "\u00e9/\u4e2d:\u00fc", "a b/c d")) {
            ChatModels.capabilities(ChatVendor.OPENROUTER, id)
            ChatModels.wireRules(ChatVendor.OPENROUTER, id)
            ChatModels.routesToOpenAi(ChatVendor.OPENROUTER, id)
        }
    }

    @Test
    fun wireRulesFollowTheNormalizedFamily() {
        fun check(model: String, effort: String?, tokenParam: String, minTokens: Int) {
            val rules = ChatModels.wireRules(ChatVendor.OPENROUTER, model)
            assertEquals(model, effort, rules.reasoningEffortWithTools)
            assertEquals(model, tokenParam, rules.tokenParam)
            assertEquals(model, minTokens, rules.minTokens)
        }
        check("openai/gpt-5.4-mini", "none", "max_completion_tokens", 1)
        check("openai/gpt-6-astra", "low", "max_completion_tokens", 1)
        check("anthropic/claude-sonnet-5.5", null, "max_tokens", 16)
        check("example/tool-model", null, "max_tokens", 16)
        check("openai/gpt-4o-mini", null, "max_tokens", 16)

        val direct = ChatModels.wireRules(ChatVendor.OPENAI, "gpt-4o-mini")
        assertEquals("max_tokens", direct.tokenParam)
        assertEquals(1, direct.minTokens)
    }

    @Test
    fun routesToOpenAiIsTrueForEveryDirectIdAndRoutedOpenAiOnly() {
        assertTrue(ChatModels.routesToOpenAi(ChatVendor.OPENAI, "gpt-5.4-mini"))
        assertTrue(ChatModels.routesToOpenAi(ChatVendor.OPENAI, "anthropic/claude-sonnet-5.5"))
        assertTrue(ChatModels.routesToOpenAi(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini"))
        assertFalse(ChatModels.routesToOpenAi(ChatVendor.OPENROUTER, "anthropic/claude-sonnet-5.5"))
        assertFalse(ChatModels.routesToOpenAi(ChatVendor.OPENROUTER, "example/tool-model"))
    }

    @Test
    fun theRoutedPublicTableKeepsTheAstraFactAndAnOverrideKeysOnTheExactId() {
        val table = run(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", withTools = false).pipeline.capabilityTable
        assertTrue(table.lookup(ProviderId.OPENROUTER, "openai/gpt-6-astra").supportsTools)

        val model = "anthropic/claude-sonnet-5.5"
        val untouched = run(ChatVendor.OPENROUTER, model, withTools = false)
        assertFalse(untouched.bound!!.supportsForcedToolChoice)

        val patched = run(ChatVendor.OPENROUTER, model, withTools = false) { supportsForcedToolChoice = true }
        assertTrue(patched.bound!!.supportsForcedToolChoice)
        assertEquals(CachingMode.NONE, patched.bound!!.caching)
    }
}
