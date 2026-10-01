package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    // ---- the frozen snapshot: turns, tiers, mid-command changes, concurrency, cancellation ----

    private fun echoFake(id: ProviderId, calls: Int) = FakeAiProvider(
        id,
        List(calls) { { call -> FakeAiProvider.reply(call.model, Usage.ZERO) } },
    )

    @Test
    fun threeTurnsInOneTierAskTheSelectionOnceAndAllGoToTheSameModel() = runTest {
        NoNetworkGuard.during {
            val fake = echoFake(ProviderId.ANTHROPIC, 3)
            val selection = ScriptedSelectionSource.fixed(anthropic())

            val outcome = pipelineOf(listOf(tierOf("t", modelStep(3))), listOf(fake), selection)
                .execute(CommandInput("hi"))

            assertEquals("model-a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, selection.calls)
            assertEquals(listOf("model-a", "model-a", "model-a"), fake.calls.map { it.model })
            assertEquals(3, outcome.trace.attempts.single().turns.size)
        }
    }

    @Test
    fun aSelectionThatChangesMidCommandDoesNotMoveTheCommandButTheNextCommandUsesIt() = runTest {
        NoNetworkGuard.during {
            val anthropicFake = echoFake(ProviderId.ANTHROPIC, 3)
            val openAiFake = echoFake(ProviderId.OPENAI, 1)
            val current = AtomicReference<ProviderSelection?>(anthropic())
            val asked = AtomicInteger()
            val selection = ProviderSelectionSource {
                asked.incrementAndGet()
                current.get()
            }
            val first: StrategyStep = { input, session ->
                val handle = session.model()
                handle.complete(requestOf(input, session))
                current.set(ProviderSelection(ProviderId.OPENAI, "model-z"))
                handle.complete(requestOf(input, session))
                runTurns(1, input, session)
            }
            val tier = ScriptedStrategy(StrategyId("t"), first, modelStep())
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key, ProviderId.OPENAI to key)
            val pipeline = pipelineOf(listOf(tier), listOf(anthropicFake, openAiFake), selection, credentials)

            val firstOutcome = pipeline.execute(CommandInput("one"))
            assertEquals("model-a", (firstOutcome as CommandOutcome.Completed).reply)
            assertEquals(1, asked.get())
            assertEquals(listOf("model-a", "model-a", "model-a"), anthropicFake.calls.map { it.model })
            assertEquals(0, openAiFake.callCount)

            val secondOutcome = pipeline.execute(CommandInput("two"))
            assertEquals("model-z", (secondOutcome as CommandOutcome.Completed).reply)
            assertEquals(2, asked.get())
            assertEquals(listOf("model-z"), openAiFake.calls.map { it.model })
            assertEquals(3, anthropicFake.callCount)
        }
    }

    @Test
    fun eachTierAsksOnceNamingItselfSoAppsCanPickACheapAndAStrongModel() = runTest {
        NoNetworkGuard.during {
            val fake = echoFake(ProviderId.ANTHROPIC, 2)
            val first = StrategyId("first")
            val second = StrategyId("second")
            val asked = mutableListOf<StrategyId>()
            val selection = ProviderSelectionSource { request ->
                asked.add(request.strategy)
                anthropic(if (request.strategy == first) "model-cheap" else "model-strong")
            }
            val escalating: StrategyStep = { input, session ->
                session.model().complete(requestOf(input, session))
                StrategyOutcome.Escalate(EscalationReason.NoToolCall())
            }
            val tiers = listOf(ScriptedStrategy(first, escalating), tierOf("second"))

            val outcome = pipelineOf(tiers, listOf(fake), selection).execute(CommandInput("hi"))

            assertEquals("model-strong", (outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf(first, second), asked)
            assertEquals(listOf("model-cheap", "model-strong"), fake.calls.map { it.model })
        }
    }

    @Test
    fun twoConcurrentModelCallsInOneTierResolveOnceAndShareTheSameHandle() = runTest {
        NoNetworkGuard.during {
            val gate = CompletableDeferred<Unit>()
            val asked = AtomicInteger()
            val selection = ProviderSelectionSource {
                asked.incrementAndGet()
                gate.await()
                anthropic()
            }
            var firstHandle: BoundModel? = null
            var secondHandle: BoundModel? = null
            var askedWhileSuspended = -1
            val tier = tierOf("t") { _, session ->
                coroutineScope {
                    val one = async { session.model() }
                    val two = async { session.model() }
                    yield()
                    yield()
                    askedWhileSuspended = asked.get()
                    gate.complete(Unit)
                    firstHandle = one.await()
                    secondHandle = two.await()
                }
                StrategyOutcome.Completed("done")
            }

            pipelineOf(listOf(tier), listOf(echoFake(ProviderId.ANTHROPIC, 0)), selection).execute(CommandInput("hi"))

            assertEquals(1, askedWhileSuspended)
            assertEquals(1, asked.get())
            assertSame(firstHandle, secondHandle)
            assertNotNull(firstHandle)
        }
    }

    @Test
    fun aCancelledModelCallFreezesNothingSoALaterCallResolvesAfresh() = runTest {
        NoNetworkGuard.during {
            val gate = CompletableDeferred<Unit>()
            val asked = AtomicInteger()
            val selection = ProviderSelectionSource {
                asked.incrementAndGet()
                gate.await()
                anthropic()
            }
            var cancelled = false
            var model: String? = null
            val tier = tierOf("t") { input, session ->
                coroutineScope {
                    val child = launch { session.model() }
                    yield()
                    assertEquals(1, asked.get())
                    child.cancel()
                    child.join()
                    cancelled = child.isCancelled
                    gate.complete(Unit)
                }
                val handle = session.model()
                model = handle.model
                handle.complete(requestOf(input, session))
                StrategyOutcome.Completed("done")
            }
            val fake = echoFake(ProviderId.ANTHROPIC, 1)

            val outcome = pipelineOf(listOf(tier), listOf(fake), selection).execute(CommandInput("hi"))

            assertTrue(cancelled)
            assertEquals("done", (outcome as CommandOutcome.Completed).reply)
            assertEquals(2, asked.get())
            assertEquals("model-a", model)
            assertEquals(1, fake.callCount)
        }
    }

    @Test
    fun twoCommandsRunningAtOnceOnOnePipelineResolveIndependently() = runTest {
        NoNetworkGuard.during {
            val gate = CompletableDeferred<Unit>()
            val arrivals = AtomicInteger()
            val selection = ProviderSelectionSource {
                val arrival = arrivals.incrementAndGet()
                gate.await()
                anthropic(if (arrival == 1) "model-a" else "model-b")
            }
            val step = modelStep()
            val tier = ScriptedStrategy(StrategyId("t"), step, step)
            val pipeline = pipelineOf(listOf(tier), listOf(echoFake(ProviderId.ANTHROPIC, 2)), selection)

            val one = async { pipeline.execute(CommandInput("one")) }
            val two = async { pipeline.execute(CommandInput("two")) }
            runCurrent()
            assertEquals(2, arrivals.get())
            gate.complete(Unit)

            val replies = setOf(one.await(), two.await()).map { (it as CommandOutcome.Completed).reply }.toSet()
            assertEquals(setOf("model-a", "model-b"), replies)
            assertEquals(2, arrivals.get())
        }
    }

    // ---- refusals end to end ----

    private class Refused(val outcome: CommandOutcome, val refusal: FailureReason?, val fake: FakeAiProvider)

    private suspend fun refusedRun(
        selection: ProviderSelectionSource?,
        credentials: CredentialSource?,
        capabilities: StrategyCapabilities = StrategyCapabilities(setOf(ProviderId.ANTHROPIC)),
    ): Refused {
        val fake = echoFake(ProviderId.ANTHROPIC, 0)
        var refusal: FailureReason? = null
        val tier = ScriptedStrategy(
            StrategyId("t"),
            capabilities,
            { input, session ->
                refusal = session.model().refusal
                val result = session.model().complete(requestOf(input, session))
                when (result) {
                    is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
                    else -> StrategyOutcome.Completed(null)
                }
            },
        )
        val outcome = pipelineOf(listOf(tier), listOf(fake), selection, credentials).execute(CommandInput("hi"))
        return Refused(outcome, refusal, fake)
    }

    @Test
    fun noProviderSelectionEndsNotConfiguredWithNoCall() = runTest {
        NoNetworkGuard.during {
            val run = refusedRun(null, ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key))

            assertEquals(FailureReason.NotConfigured(null), (run.outcome as CommandOutcome.Failed).reason)
            assertEquals(run.outcome.reason, run.refusal)
            assertEquals(listOf(TraceCode.PROVIDER_NOT_SELECTED), run.outcome.trace.codes)
            assertEquals(0, run.fake.callCount)
        }
    }

    @Test
    fun aMissingKeyEndsNotConfiguredForThatProviderWithNoCall() = runTest {
        NoNetworkGuard.during {
            val run = refusedRun(ScriptedSelectionSource.fixed(anthropic()), ScriptedCredentialSource.keys())

            val expected = FailureReason.NotConfigured(ProviderId.ANTHROPIC)
            assertEquals(expected, (run.outcome as CommandOutcome.Failed).reason)
            assertEquals(run.outcome.reason, run.refusal)
            assertEquals(listOf(TraceCode.CREDENTIAL_MISSING), run.outcome.trace.codes)
            assertEquals(0, run.fake.callCount)
        }
    }

    @Test
    fun anOnDeviceSelectionWithoutAFallbackEndsLoudlyAndAsksForNoKey() = runTest {
        NoNetworkGuard.during {
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val both = StrategyCapabilities(setOf(ProviderId.ON_DEVICE, ProviderId.ANTHROPIC))
            val selection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ON_DEVICE, "local"))

            val run = refusedRun(selection, credentials, both)

            val expected = FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "on_device_unavailable")
            assertEquals(expected, (run.outcome as CommandOutcome.Failed).reason)
            assertEquals(expected, run.refusal)
            assertTrue(run.outcome.trace.codes.contains(TraceCode.ON_DEVICE_UNAVAILABLE))
            assertTrue(credentials.requested.isEmpty())
            assertEquals(0, run.fake.callCount)
        }
    }

    private companion object {
        const val MIN_PREFIX = 1024
    }
}
