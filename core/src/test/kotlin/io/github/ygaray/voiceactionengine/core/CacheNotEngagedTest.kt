package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeClock
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cache diagnostic: when a caching model should have used its prompt cache and did not. */
class CacheNotEngagedTest {
    private val clock = FakeClock()
    private val listener = RecordingEventListener()
    private val key = "sk-canary-key-1"
    private val tierId = StrategyId("tier-one")

    private val explicitCaps = ModelCapabilities {
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = MINIMUM
    }

    private fun textOf(count: Int): String = "x".repeat(count)

    /** A tier that sends [system] with [directive] once and completes with whatever came back. */
    private fun sendOnce(system: String, directive: CacheDirective = CacheDirective(true)): StrategyStep =
        { input, session ->
            val request = ModelRequest(
                system,
                listOf(UserMessage(input.transcript)),
                emptyList(),
                ToolChoice.Auto(),
                session.policy.maxTokensPerTurn,
                directive,
            )
            val result = session.model().complete(request)
            when (result) {
                is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
                else -> StrategyOutcome.Completed("ok")
            }
        }

    private fun pipelineOf(fake: FakeAiProvider, step: StrategyStep): CommandPipeline = commandPipeline {
        tier(ScriptedStrategy(tierId, step))
        provider(fake)
        providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))
        credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
        clock = this@CacheNotEngagedTest.clock
        listener = this@CacheNotEngagedTest.listener
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

    private fun anthropicFake(capabilities: ModelCapabilities, usage: Usage) =
        FakeAiProvider(ProviderId.ANTHROPIC, capabilities, { _ -> FakeAiProvider.reply("ok", usage) })

    @Test
    fun aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse() = runTest {
        NoNetworkGuard.during {
            val fake = anthropicFake(explicitCaps, Usage(UNCACHED_PROMPT, 0, 0, 10))

            val outcome = pipelineOf(fake, sendOnce(textOf(LARGE_SYSTEM))).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            val events = listener.events.filterIsInstance<PipelineEvent.CacheNotEngaged>()
            val event = events.single()
            assertEquals(tierId, event.strategy)
            assertEquals(ProviderId.ANTHROPIC, event.provider)
            assertEquals("model-a", event.model)
            assertEquals(0, listener.events.filterIsInstance<PipelineEvent.EngineCode>().size)
            assertEquals(emptyList<Any>(), outcome.trace.codes)
        }
    }

    @Test
    fun aPrefixBelowTheMinimumStaysSilent() = runTest {
        NoNetworkGuard.during {
            val fake = anthropicFake(explicitCaps, Usage(UNCACHED_PROMPT, 0, 0, 10))

            pipelineOf(fake, sendOnce(textOf(SMALL_SYSTEM))).execute(CommandInput("hi"))

            assertEquals(0, listener.events.filterIsInstance<PipelineEvent.CacheNotEngaged>().size)
        }
    }

    private companion object {
        const val MINIMUM = 1024
        const val LARGE_SYSTEM = 8000
        const val SMALL_SYSTEM = 2000
        const val UNCACHED_PROMPT = 2100L
    }
}
