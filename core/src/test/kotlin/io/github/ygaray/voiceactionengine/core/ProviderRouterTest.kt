package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
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
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The router wired into the pipeline: strategies reach a model only through `session.model()`. */
class ProviderRouterTest {
    private val key = "sk-canary-key-1"
    private val clock = FakeClock()
    private val listener = RecordingEventListener()

    private fun requestOf(input: CommandInput, session: CommandSession) =
        ModelRequest("sys", listOf(UserMessage(input.transcript)), session.policy.maxTokensPerTurn)

    private fun textOf(result: ModelResult): String =
        (result as ModelResult.Success).response.message.parts.filterIsInstance<AssistantPart.Text>().single().text

    /** A step that makes [turns] model calls and completes with the last reply, or fails with the first refusal. */
    private fun modelStep(turns: Int = 1): StrategyStep = { input, session -> runTurns(turns, input, session) }

    private suspend fun runTurns(turns: Int, input: CommandInput, session: CommandSession): StrategyOutcome {
        var outcome: StrategyOutcome = StrategyOutcome.Completed(null)
        repeat(turns) {
            val result = session.model().complete(requestOf(input, session))
            if (result is ModelResult.Failure) return StrategyOutcome.Failed(result.reason)
            outcome = StrategyOutcome.Completed(textOf(result))
        }
        return outcome
    }

    private fun tierOf(id: String, step: StrategyStep = modelStep()): ScriptedStrategy =
        ScriptedStrategy(StrategyId(id), step)

    private fun anthropic(model: String = "model-a") = ProviderSelection(ProviderId.ANTHROPIC, model)

    private fun pipelineOf(
        tiers: List<CommandStrategy>,
        providers: List<AiProvider>,
        selection: ProviderSelectionSource? = ScriptedSelectionSource.fixed(anthropic()),
        credentials: CredentialSource? = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key),
        extra: PipelineBuilder.() -> Unit = {},
    ): CommandPipeline = commandPipeline {
        tiers.forEach { tier(it) }
        providers.forEach { provider(it) }
        providerSelection = selection
        this.credentials = credentials
        clock = this@ProviderRouterTest.clock
        listener = this@ProviderRouterTest.listener
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
        extra()
    }

    @Test
    fun aStrategyCallsTheSelectedProviderThroughTheSessionAndTheTurnLandsInTheTrace() = runTest {
        NoNetworkGuard.during {
            val usage = Usage(20, 0, 0, 4)
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                ModelCapabilities.UNKNOWN,
                { _ ->
                    clock.advanceBy(7)
                    FakeAiProvider.reply("ok", usage)
                },
            )
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            var seenTokens = -1L
            val tier = tierOf("t") { input, session ->
                val result = session.model().complete(requestOf(input, session))
                seenTokens = session.tokensUsed
                StrategyOutcome.Completed(textOf(result))
            }

            val outcome = pipelineOf(listOf(tier), listOf(fake), credentials = credentials).execute(CommandInput("hi"))

            assertEquals("ok", (outcome as CommandOutcome.Completed).reply)
            val call = fake.calls.single()
            assertEquals("model-a", call.model)
            assertEquals(ProviderId.ANTHROPIC, call.credential?.provider)
            assertEquals(key, call.credential?.apiKey)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, call.request.maxTokens)
            val attempt = outcome.trace.attempts.single()
            assertEquals(ProviderId.ANTHROPIC, attempt.provider)
            assertEquals("model-a", attempt.model)
            assertEquals(usage.total, attempt.usage.total)
            assertEquals(7L, attempt.turns.single().latencyMillis)
            assertEquals(1, listener.events.filterIsInstance<PipelineEvent.ProviderCall>().size)
            assertEquals(usage.total, seenTokens)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
            assertNull(outcome.trace.codes.firstOrNull())
        }
    }

    @Test
    fun buildRejectsADuplicateProviderId() {
        val first = FakeAiProvider(ProviderId.ANTHROPIC, emptyList())
        val second = FakeAiProvider(ProviderId.ANTHROPIC, emptyList())
        val failure = assertThrows(IllegalArgumentException::class.java) {
            pipelineOf(listOf(tierOf("t")), listOf(first, second))
        }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("duplicate provider"))
    }

    // ---- capability overrides and the public table ----

    private val declared = ModelCapabilities {
        supportsTools = true
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = MIN_PREFIX
    }

    private fun declaredFake() =
        FakeAiProvider(ProviderId.ANTHROPIC, declared, { FakeAiProvider.reply("ok", Usage.ZERO) })

    @Test
    fun withoutOverridesTheTableReportsTheProviderDefaultAndUnknownForAnUnregisteredId() {
        val pipeline = pipelineOf(listOf(tierOf("t")), listOf(declaredFake()))

        assertEquals(declared, pipeline.capabilityTable.lookup(ProviderId.ANTHROPIC, "model-a"))
        assertEquals(ModelCapabilities.UNKNOWN, pipeline.capabilityTable.lookup(ProviderId.OPENAI, "model-a"))
    }

    @Test
    fun anOverridePatchesOnlyItsExactPairAndKeepsTheOtherFields() {
        val pipeline = pipelineOf(listOf(tierOf("t")), listOf(declaredFake())) {
            capabilities(ProviderId.ANTHROPIC, "model-a") { supportsTools = false }
        }

        val patched = pipeline.capabilityTable.lookup(ProviderId.ANTHROPIC, "model-a")
        assertEquals(false, patched.supportsTools)
        assertEquals(CachingMode.EXPLICIT_BREAKPOINTS, patched.caching)
        assertEquals(MIN_PREFIX, patched.minCacheablePrefixTokens)
        assertEquals(declared, pipeline.capabilityTable.lookup(ProviderId.ANTHROPIC, "model-b"))
    }

    @Test
    fun aRoutedRequestWithToolsOnAnOverriddenToolIncapableModelIsRefusedBeforeAnyCall() = runTest {
        NoNetworkGuard.during {
            val fake = declaredFake()
            val tools = listOf(ToolSpec("lookup", "finds a thing", JsonObject(emptyMap())))
            val tier = tierOf("t") { input, session ->
                val request = ModelRequest("sys", listOf(UserMessage(input.transcript)), tools, 100)
                when (val result = session.model().complete(request)) {
                    is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
                    else -> StrategyOutcome.Completed(null)
                }
            }
            val pipeline = pipelineOf(listOf(tier), listOf(fake)) {
                capabilities(ProviderId.ANTHROPIC, "model-a") { supportsTools = false }
            }

            val outcome = pipeline.execute(CommandInput("hi"))

            assertEquals(FailureReason.ModelUnsupported(), (outcome as CommandOutcome.Failed).reason)
            assertTrue(outcome.trace.codes.contains(TraceCode.CAPABILITY_REFUSED))
            assertEquals(0, fake.callCount)
        }
    }

    @Test
    fun buildRejectsADuplicateOverrideForTheSamePair() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            pipelineOf(listOf(tierOf("t")), listOf(declaredFake())) {
                capabilities(ProviderId.ANTHROPIC, "model-a") { supportsTools = false }
                capabilities(ProviderId.ANTHROPIC, "model-a") { supportsTools = true }
            }
        }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("duplicate capability override"))
    }

    @Test
    fun buildRejectsABlankOverrideModel() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            pipelineOf(listOf(tierOf("t")), listOf(declaredFake())) {
                capabilities(ProviderId.ANTHROPIC, " ") { supportsTools = false }
            }
        }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("blank"))
    }

    @Test
    fun buildRejectsAnOverrideBlockProducingAnInvalidValueBeforeAnyCommandRuns() {
        val fake = declaredFake()
        assertThrows(IllegalArgumentException::class.java) {
            pipelineOf(listOf(tierOf("t")), listOf(fake)) {
                capabilities(ProviderId.ANTHROPIC, "model-a") { charsPerToken = 0.0 }
            }
        }
        assertEquals(0, fake.callCount)
    }

    @Test
    fun thePreCheckAndTheRouterReadOneOnDeviceProbeInstanceTwicePerCommand() = runTest {
        NoNetworkGuard.during {
            val reads = AtomicInteger()
            val fake = declaredFake()
            val both = StrategyCapabilities(setOf(ProviderId.ON_DEVICE, ProviderId.ANTHROPIC))
            val tier = ScriptedStrategy(StrategyId("t"), both, modelStep())
            val selection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ON_DEVICE, "local"))
            val pipeline = pipelineOf(listOf(tier), listOf(fake), selection) {
                onDeviceAvailability = {
                    reads.incrementAndGet()
                    false
                }
            }

            val outcome = pipeline.execute(CommandInput("hi"))

            val expected = FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "on_device_unavailable")
            assertEquals(expected, (outcome as CommandOutcome.Failed).reason)
            assertEquals(2, reads.get())
            assertEquals(0, fake.callCount)
        }
    }

    private companion object {
        const val MIN_PREFIX = 1024
    }
}
