package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicModelsTest {

    private val rejectingIds = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-mythos-5-1")

    /** A provider whose model facts come from the table under test and whose answers are irrelevant. */
    private object TableBackedProvider : AiProvider {
        override val id: ProviderId = ProviderId.ANTHROPIC

        override fun capabilities(model: String): ModelCapabilities = AnthropicModels.capabilities(model)

        override suspend fun complete(call: ProviderRequest): ModelResult =
            FakeAiProvider.reply("ok", Usage.ZERO)
    }

    /** Runs one command on [model] and returns the capabilities the strategy's bound model reported. */
    private fun boundCapabilities(
        model: String,
        override: (ModelCapabilities.Builder.() -> Unit)? = null,
    ): ModelCapabilities? {
        val seen = RecordingSink<ModelCapabilities?>()
        val step: StrategyStep = { _, session ->
            seen.record(session.model().capabilities)
            StrategyOutcome.Completed(null)
        }
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("probe"), step))
            provider(TableBackedProvider)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test")
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
            if (override != null) capabilities(ProviderId.ANTHROPIC, model, override)
        }
        runBlocking { pipeline.execute(CommandInput("hello", "en", null)) }
        return seen.events.single()
    }

    @Test
    fun anAppOverrideReachesTheBoundModelAndPatchesOnlyItsField() {
        val table = boundCapabilities("claude-opus-5-5")
        assertNotNull(table)
        assertFalse(table!!.supportsForcedToolChoice)
        assertEquals(CachingMode.EXPLICIT_BREAKPOINTS, table.caching)
        assertEquals(512, table.minCacheablePrefixTokens)

        val patched = boundCapabilities("claude-opus-5-5") { supportsForcedToolChoice = true }
        assertNotNull(patched)
        assertTrue(patched!!.supportsForcedToolChoice)
        assertEquals(CachingMode.EXPLICIT_BREAKPOINTS, patched.caching)
        assertEquals(512, patched.minCacheablePrefixTokens)
    }

    @Test
    fun theFourRejectingModelsDisallowForcedToolsAndCacheFromFiveTwelveTokens() {
        for (id in rejectingIds) {
            val caps = AnthropicModels.capabilities(id)
            assertFalse(id, caps.supportsForcedToolChoice)
            assertEquals(id, CachingMode.EXPLICIT_BREAKPOINTS, caps.caching)
            assertEquals(id, 512, caps.minCacheablePrefixTokens)
            assertTrue(id, caps.supportsTools)
            assertEquals(id, 4.0, caps.charsPerToken, 0.0)
        }
    }

    @Test
    fun theSmallModelAllowsForcedToolsAndCachesFromFourThousandNinetySixTokens() {
        val caps = AnthropicModels.capabilities("claude-haiku-4-5")

        assertTrue(caps.supportsForcedToolChoice)
        assertEquals(CachingMode.EXPLICIT_BREAKPOINTS, caps.caching)
        assertEquals(4096, caps.minCacheablePrefixTokens)
    }

    @Test
    fun idsAreMatchedExactlyAndAnythingElseGetsTheUnknownDefault() {
        val unknownShaped = listOf("claude-opus-5-5-20260901", "claude-opus-5", "CLAUDE-OPUS-5-5", "some-new-model")
        for (id in unknownShaped) {
            val caps = AnthropicModels.capabilities(id)
            assertTrue(id, caps.supportsForcedToolChoice)
            assertEquals(id, CachingMode.EXPLICIT_BREAKPOINTS, caps.caching)
            assertNull(id, caps.minCacheablePrefixTokens)
        }
    }
}
