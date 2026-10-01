package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeClock
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ProviderStep
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** The router and the handle it returns, driven directly against a real [RunRecorder] and scripted fakes. */
class ModelRouterTest {
    private val clock = FakeClock()
    private val listener = RecordingEventListener()
    private val strategy = StrategyId("a")
    private val key = "sk-canary-key-1"
    private val request = ModelRequest("sys", listOf(UserMessage("hi")), TierPolicy.DEFAULT.maxTokensPerTurn)

    private fun recorder(): RunRecorder = RunRecorder("run-1", null, null, 0, clock, listener)

    private fun routerOf(
        providers: List<AiProvider>,
        selection: ProviderSelectionSource? = anthropicSelection(),
        credentials: CredentialSource? = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key),
        probe: suspend () -> Boolean = { false },
        table: ModelCapabilityTable = tableOf(providers),
    ): ModelRouter = ModelRouter(providers.associateBy { it.id }, selection, credentials, table, probe)

    private fun anthropicSelection(): ProviderSelectionSource =
        ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))

    private fun tableOf(providers: List<AiProvider>): ModelCapabilityTable {
        val byId = providers.associateBy { it.id }
        return ModelCapabilityTable({ provider, model -> byId.getValue(provider).capabilities(model) }, emptyMap())
    }

    private fun textOf(result: ModelResult): String =
        (result as ModelResult.Success).response.message.parts.filterIsInstance<AssistantPart.Text>().single().text

    @Test
    fun bindResolvesTheModelAndACompleteCallReachesTheProviderAndTheTrace() = runTest {
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
            val selection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))
            val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
            val recorder = recorder()
            recorder.tierStarted(strategy)

            val handle = routerOf(listOf(fake), selection, credentials)
                .bind(strategy, setOf(ProviderId.ANTHROPIC), TierPolicy.DEFAULT, recorder)
            val result = handle.complete(request)

            assertEquals("ok", textOf(result))
            assertEquals(ProviderId.ANTHROPIC, handle.provider)
            assertEquals("model-a", handle.model)
            assertNull(handle.refusal)
            assertNull(handle.fallbackFrom)
            val call = fake.calls.single()
            assertEquals("model-a", call.model)
            assertEquals(ProviderId.ANTHROPIC, call.credential?.provider)
            assertEquals(key, call.credential?.apiKey)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, call.request.maxTokens)
            assertEquals(listOf(ProviderId.ANTHROPIC), credentials.requested)
            assertEquals(strategy, selection.requests.single().strategy)

            recorder.tierFinished(strategy, "completed", null, null, null)
            val turn = recorder.snapshot().attempts.single().turns.single()
            assertEquals(ProviderId.ANTHROPIC, turn.provider)
            assertEquals("model-a", turn.model)
            assertEquals("end_turn", turn.stopReason)
            assertEquals(usage.total, turn.usage.total)
            assertEquals(7L, turn.latencyMillis)
            assertEquals(1, listener.events.filterIsInstance<PipelineEvent.ProviderCall>().size)
            assertEquals(usage.total, recorder.tokensUsed)
        }
    }

    private fun anthropicFake() = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("ok", Usage.ZERO))

    private fun onDeviceFake() = FakeAiProvider(
        ProviderId.ON_DEVICE,
        emptyList(),
        ModelCapabilities.UNKNOWN,
        requiresCredential = false,
    )

    private suspend fun bindDefault(
        router: ModelRouter,
        recorder: RunRecorder,
        declared: Set<ProviderId> = setOf(ProviderId.ANTHROPIC),
        policy: TierPolicy = TierPolicy.DEFAULT,
    ): BoundModel = router.bind(strategy, declared, policy, recorder)

    /** The handle is refused with [reason]; exactly [codes] were recorded, none by a second call; no fake ran. */
    private suspend fun assertRefused(
        handle: BoundModel,
        recorder: RunRecorder,
        reason: FailureReason,
        codes: List<TraceCode>,
        vararg fakes: FakeAiProvider,
    ) {
        assertEquals(reason, handle.refusal)
        assertNull(handle.provider)
        assertNull(handle.model)
        assertNull(handle.capabilities)
        repeat(2) {
            val result = handle.complete(request) as ModelResult.Failure
            assertEquals(reason, result.reason)
        }
        assertEquals(codes, recorder.snapshot().codes)
        assertTrue(listener.events.none { it is PipelineEvent.ProviderCall })
        fakes.forEach { assertEquals(0, it.callCount) }
        assertTrue(handle.toString().contains(reason.code))
    }

    @Test
    fun theHandlePrintsIdsOnlyAndNeverAKeyOrContent() = runTest {
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("ok", Usage.ZERO))
        val handle: BoundModel = routerOf(listOf(fake))
            .bind(strategy, setOf(ProviderId.ANTHROPIC), TierPolicy.DEFAULT, recorder())

        val printed = handle.toString()

        assertTrue(printed.contains("anthropic"))
        assertTrue(printed.contains("model-a"))
        assertFalse(printed.contains(key))
        assertFalse(printed.contains("sys"))
        assertFalse(printed.contains("hi"))
    }

    @Test
    fun noSelectionSourceAndANullSelectionBothRefuseNotConfiguredWithNoProvider() = runTest {
        val fake = anthropicFake()
        val absent = recorder()
        val none = bindDefault(routerOf(listOf(fake), selection = null), absent)
        assertRefused(none, absent, FailureReason.NotConfigured(null), listOf(TraceCode.PROVIDER_NOT_SELECTED), fake)

        val nothing = recorder()
        val noneChosen = bindDefault(routerOf(listOf(fake), selection = ScriptedSelectionSource.fixed(null)), nothing)
        assertRefused(
            noneChosen,
            nothing,
            FailureReason.NotConfigured(null),
            listOf(TraceCode.PROVIDER_NOT_SELECTED),
            fake,
        )
    }

    @Test
    fun aThrowingSelectionSourceRefusesUnexpectedWithTheClassNameOnly() = runTest {
        val fake = anthropicFake()
        val recorder = recorder()
        val source = ProviderSelectionSource { throw IllegalStateException("secret detail") }

        val handle = bindDefault(routerOf(listOf(fake), selection = source), recorder)

        assertRefused(
            handle,
            recorder,
            FailureReason.Unexpected("IllegalStateException"),
            listOf(TraceCode.SELECTION_SOURCE_ERROR),
            fake,
        )
    }

    @Test
    fun aSelectionSourceThatLeaksATimeoutRefusesWithTimeout() = runTest {
        val fake = anthropicFake()
        val recorder = recorder()
        val source = ProviderSelectionSource { withTimeout(1) { delay(1_000) }; null }

        val handle = bindDefault(routerOf(listOf(fake), selection = source), recorder)

        assertRefused(handle, recorder, FailureReason.Timeout(), listOf(TraceCode.SELECTION_SOURCE_ERROR), fake)
    }

    @Test
    fun aProviderTheTierDoesNotDeclareIsRefusedAsNotDeclared() = runTest {
        val fake = anthropicFake()
        val recorder = recorder()

        val handle = bindDefault(routerOf(listOf(fake)), recorder, declared = setOf(ProviderId.OPENAI))

        val reason = FailureReason.ProviderUnavailable(ProviderId.ANTHROPIC, "provider_not_declared")
        assertRefused(handle, recorder, reason, listOf(TraceCode.PROVIDER_NOT_ALLOWED), fake)
    }

    @Test
    fun aProviderThePolicyDoesNotAllowIsRefusedAsForbidden() = runTest {
        val fake = anthropicFake()
        val recorder = recorder()
        val policy = TierPolicy { allowedProviders = setOf(ProviderId.OPENAI) }

        val handle = bindDefault(routerOf(listOf(fake)), recorder, policy = policy)

        val reason = FailureReason.ProviderUnavailable(ProviderId.ANTHROPIC, "policy_forbids_provider")
        assertRefused(handle, recorder, reason, listOf(TraceCode.PROVIDER_NOT_ALLOWED), fake)
    }

    @Test
    fun anOfflineOnlyPolicyRefusesACloudProviderEvenWhenTheTierDeclaresIt() = runTest {
        val fake = anthropicFake()
        val recorder = recorder()
        val policy = TierPolicy { offlineOnly = true }

        val handle = bindDefault(routerOf(listOf(fake)), recorder, policy = policy)

        val reason = FailureReason.ProviderUnavailable(ProviderId.ANTHROPIC, "policy_forbids_provider")
        assertRefused(handle, recorder, reason, listOf(TraceCode.PROVIDER_NOT_ALLOWED), fake)
    }

    @Test
    fun aSelectedProviderWithNoRegisteredImplementationIsNotConfigured() = runTest {
        val other = FakeAiProvider(ProviderId.OPENAI, FakeAiProvider.reply("ok", Usage.ZERO))
        val recorder = recorder()
        val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)

        val handle = bindDefault(routerOf(listOf(other), credentials = credentials), recorder)

        val reason = FailureReason.NotConfigured(ProviderId.ANTHROPIC)
        assertRefused(handle, recorder, reason, listOf(TraceCode.PROVIDER_NOT_REGISTERED), other)
        assertTrue(credentials.requested.isEmpty())
    }

    @Test
    fun noCredentialSourceAndAMissingKeyBothRefuseNotConfigured() = runTest {
        val fake = anthropicFake()
        val reason = FailureReason.NotConfigured(ProviderId.ANTHROPIC)
        val absent = recorder()
        val noSource = bindDefault(routerOf(listOf(fake), credentials = null), absent)
        assertRefused(noSource, absent, reason, listOf(TraceCode.CREDENTIAL_MISSING), fake)

        val missing = recorder()
        val empty = ScriptedCredentialSource(emptyMap())
        val noKey = bindDefault(routerOf(listOf(fake), credentials = empty), missing)
        assertRefused(noKey, missing, reason, listOf(TraceCode.CREDENTIAL_MISSING), fake)
        assertEquals(listOf(ProviderId.ANTHROPIC), empty.requested)
    }

    @Test
    fun anUnreadableKeyAndAThrowingCredentialSourceAreDistinctFromMissing() = runTest {
        val fake = anthropicFake()
        val unreadable = recorder()
        val lost = ScriptedCredentialSource(
            mapOf(ProviderId.ANTHROPIC to CredentialLookup.Unreadable("key_invalidated")),
        )
        val first = bindDefault(routerOf(listOf(fake), credentials = lost), unreadable)
        assertRefused(
            first,
            unreadable,
            FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "key_invalidated"),
            listOf(TraceCode.CREDENTIAL_UNREADABLE),
            fake,
        )

        val thrown = recorder()
        val broken = CredentialSource { throw IllegalStateException("store closed") }
        val second = bindDefault(routerOf(listOf(fake), credentials = broken), thrown)
        assertRefused(
            second,
            thrown,
            FailureReason.Unexpected("IllegalStateException"),
            listOf(TraceCode.CREDENTIAL_SOURCE_ERROR),
            fake,
        )
    }

    @Test
    fun aCredentialSourceThatLeaksATimeoutRefusesWithTimeoutNotAnUnreadableKey() = runTest {
        val fake = anthropicFake()
        val recorder = recorder()
        val source = CredentialSource { withTimeout(1) { delay(1_000) }; CredentialLookup.Missing() }

        val handle = bindDefault(routerOf(listOf(fake), credentials = source), recorder)

        assertRefused(handle, recorder, FailureReason.Timeout(), listOf(TraceCode.CREDENTIAL_SOURCE_ERROR), fake)
    }

    @Test
    fun aProviderWhoseCapabilityAnswerLeaksATimeoutRefusesWithTimeout() = runTest {
        val broken = object : AiProvider {
            override val id: ProviderId = ProviderId.ANTHROPIC

            override fun capabilities(model: String): ModelCapabilities =
                runBlocking { withTimeout(1) { delay(1_000) }; error("unreachable") }

            override suspend fun complete(call: ProviderRequest): ModelResult = FakeAiProvider.reply("ok", Usage.ZERO)
        }
        val recorder = recorder()

        val handle = bindDefault(routerOf(listOf(broken)), recorder)

        assertEquals(FailureReason.Timeout(), handle.refusal)
        assertEquals(listOf(TraceCode.CAPABILITY_LOOKUP_ERROR), recorder.snapshot().codes)
    }

    @Test
    fun aCredentialStampedForAnotherProviderIsRefusedAndNeverSent() = runTest {
        val other = "sk-other-provider-key"
        val anthropic = anthropicFake()
        val openai = FakeAiProvider(ProviderId.OPENAI, FakeAiProvider.reply("ok", Usage.ZERO))
        val recorder = recorder()
        val foreign = ScriptedCredentialSource(
            mapOf(ProviderId.ANTHROPIC to CredentialLookup.Present(Credential(ProviderId.OPENAI, other))),
        )

        val handle = bindDefault(routerOf(listOf(anthropic, openai), credentials = foreign), recorder)

        val reason = FailureReason.NotConfigured(ProviderId.ANTHROPIC)
        assertRefused(handle, recorder, reason, listOf(TraceCode.CREDENTIAL_MISMATCH), anthropic, openai)
        assertEquals(listOf(ProviderId.ANTHROPIC), foreign.requested)
        assertTrue((anthropic.calls + openai.calls).none { it.credential?.apiKey == other })
    }

    private fun onDeviceSelection() = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ON_DEVICE, "model-d"))

    private suspend fun assertOnDeviceRefused(
        router: ModelRouter,
        recorder: RunRecorder,
        codes: List<TraceCode>,
        credentials: ScriptedCredentialSource,
        vararg fakes: FakeAiProvider,
    ) {
        val handle = bindDefault(router, recorder, declared = setOf(ProviderId.ON_DEVICE))
        val reason = FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "on_device_unavailable")
        assertRefused(handle, recorder, reason, codes, *fakes)
        assertTrue(credentials.requested.isEmpty())
    }

    @Test
    fun anOnDeviceSelectionThatIsNotReadyFailsLoudlyWithoutTouchingAKey() = runTest {
        val device = onDeviceFake()
        val cloud = anthropicFake()
        val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
        val router = routerOf(
            listOf(device, cloud),
            selection = onDeviceSelection(),
            credentials = credentials,
            probe = { false },
        )

        assertOnDeviceRefused(router, recorder(), listOf(TraceCode.ON_DEVICE_UNAVAILABLE), credentials, device, cloud)
    }

    @Test
    fun anOnDeviceSelectionWithNoRegisteredOnDeviceProviderIsUnavailableEvenWhenTheProbeSaysReady() = runTest {
        val cloud = anthropicFake()
        val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
        val router = routerOf(
            listOf(cloud),
            selection = onDeviceSelection(),
            credentials = credentials,
            probe = { true },
        )

        assertOnDeviceRefused(router, recorder(), listOf(TraceCode.ON_DEVICE_UNAVAILABLE), credentials, cloud)
    }

    @Test
    fun aThrowingOnDeviceProbeIsRecordedAndCountsAsUnavailable() = runTest {
        val device = onDeviceFake()
        val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
        val router = routerOf(
            listOf(device),
            selection = onDeviceSelection(),
            credentials = credentials,
            probe = { throw IllegalStateException("probe down") },
        )

        assertOnDeviceRefused(
            router,
            recorder(),
            listOf(TraceCode.ON_DEVICE_PROBE_ERROR, TraceCode.ON_DEVICE_UNAVAILABLE),
            credentials,
            device,
        )
    }

    @Test
    fun aReadyOnDeviceSelectionBindsWithNoKeyAsked() = runTest {
        val device = FakeAiProvider(
            ProviderId.ON_DEVICE,
            listOf({ _ -> FakeAiProvider.reply("local", Usage.ZERO) }),
            ModelCapabilities.UNKNOWN,
            requiresCredential = false,
        )
        val credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
        val router = routerOf(
            listOf(device),
            selection = onDeviceSelection(),
            credentials = credentials,
            probe = { true },
        )

        val handle = bindDefault(router, recorder(), declared = setOf(ProviderId.ON_DEVICE))

        assertNull(handle.refusal)
        assertEquals("local", textOf(handle.complete(request)))
        assertNull(device.calls.single().credential)
        assertTrue(credentials.requested.isEmpty())
    }

    private val toolRequest = ModelRequest(
        "sys",
        listOf(UserMessage("hi")),
        listOf(ToolSpec("lookup", "finds a thing", JsonObject(emptyMap()))),
        TierPolicy.DEFAULT.maxTokensPerTurn,
    )

    private suspend fun turnsOf(recorder: RunRecorder) = recorder.snapshot().attempts.single().turns

    private suspend fun finish(recorder: RunRecorder) = recorder.tierFinished(strategy, "completed", null, null, null)

    @Test
    fun aToolRequestOnAModelWithoutToolsIsRefusedBeforeAnyCallAndTheHandleStaysUsable() = runTest {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities { supportsTools = false },
            { _ -> FakeAiProvider.reply("plain", Usage.ZERO) },
        )
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        val refused = handle.complete(toolRequest) as ModelResult.Failure

        assertEquals(FailureReason.ModelUnsupported(), refused.reason)
        assertEquals(0, fake.callCount)
        assertEquals(listOf(TraceCode.CAPABILITY_REFUSED), recorder.snapshot().codes)
        assertEquals("plain", textOf(handle.complete(request)))
        assertEquals(1, fake.callCount)
        finish(recorder)
        assertEquals(1, turnsOf(recorder).size)
    }

    @Test
    fun anAppOverrideForTheExactModelAlsoStopsAToolRequestBeforeTheCall() = runTest {
        val fake = anthropicFake()
        val table = ModelCapabilityTable(
            { _, _ -> ModelCapabilities.UNKNOWN },
            mapOf((ProviderId.ANTHROPIC to "model-a") to { supportsTools = false }),
        )
        val recorder = recorder()
        val handle = bindDefault(routerOf(listOf(fake), table = table), recorder)

        val refused = handle.complete(toolRequest) as ModelResult.Failure

        assertEquals(FailureReason.ModelUnsupported(), refused.reason)
        assertEquals(0, fake.callCount)
        assertFalse(handle.capabilities?.supportsTools ?: true)
    }

    @Test
    fun aProviderWhoseCapabilityAnswerThrowsRefusesTheBindWithNoCall() = runTest {
        val broken = object : AiProvider {
            override val id: ProviderId = ProviderId.ANTHROPIC
            var calls = 0

            override fun capabilities(model: String): ModelCapabilities = error("no table")

            override suspend fun complete(call: ProviderRequest): ModelResult {
                calls++
                return FakeAiProvider.reply("ok", Usage.ZERO)
            }
        }
        val recorder = recorder()

        val handle = bindDefault(routerOf(listOf(broken)), recorder)

        assertEquals(FailureReason.Unexpected("IllegalStateException"), handle.refusal)
        assertEquals(listOf(TraceCode.CAPABILITY_LOOKUP_ERROR), recorder.snapshot().codes)
        assertEquals(0, broken.calls)
    }

    @Test
    fun aThrowingProviderBecomesATypedFailureAndAZeroUsageTurnNamingTheModel() = runTest {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities.UNKNOWN,
            { _ -> throw IllegalStateException("provider exploded with ${key}") },
        )
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        val failed = handle.complete(request) as ModelResult.Failure

        assertEquals(FailureReason.Unexpected("IllegalStateException"), failed.reason)
        assertFalse(failed.toString().contains(key))
        assertEquals(listOf(TraceCode.PROVIDER_ERROR), recorder.snapshot().codes)
        finish(recorder)
        val turn = turnsOf(recorder).single()
        assertEquals(ProviderId.ANTHROPIC, turn.provider)
        assertEquals("model-a", turn.model)
        assertEquals(0L, turn.usage.total)
        assertNull(turn.stopReason)
    }

    @Test
    fun aClockThatStartsThrowingAfterTheRunBeganDoesNotBreakComplete() = runTest {
        var reads = 0
        val flaky: () -> Long = {
            reads += 1
            if (reads > 1) error("clock broke") else 0L
        }
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities.UNKNOWN,
            { _ -> FakeAiProvider.reply("ok", Usage.ZERO) },
        )
        val recorder = RunRecorder("run-1", null, null, 0, flaky, listener)
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        val result = handle.complete(request)

        assertEquals("ok", textOf(result))
        finish(recorder)
        assertEquals(0L, turnsOf(recorder).single().latencyMillis)
    }

    @Test
    fun aProviderThatLeaksATimeoutGivesATimeoutFailure() = runTest {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities.UNKNOWN,
            { _ -> withTimeout(1) { delay(1_000) }; FakeAiProvider.reply("late", Usage.ZERO) },
        )
        val handle = bindDefault(routerOf(listOf(fake)), recorder())

        val failed = handle.complete(request) as ModelResult.Failure

        assertEquals(FailureReason.Timeout(), failed.reason)
    }

    @Test
    fun aProviderFailureIsPassedThroughUnchangedWithAZeroUsageTurnAndNoErrorCode() = runTest {
        val scripted = ModelResult.Failure(FailureReason.RateLimited())
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, scripted)
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        val result = handle.complete(request)

        assertSame(scripted, result)
        assertTrue(recorder.snapshot().codes.isEmpty())
        finish(recorder)
        val turn = turnsOf(recorder).single()
        assertEquals("model-a", turn.model)
        assertEquals(0L, turn.usage.total)
    }

    @Test
    fun cancellingTheCallerPropagatesItsOwnCancellationAndRecordsNoTurn() = runTest {
        val parked = CompletableDeferred<Unit>()
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities.UNKNOWN,
            { _ -> parked.await(); FakeAiProvider.reply("never", Usage.ZERO) },
        )
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)
        var caught: Throwable? = null

        val job = launch {
            try {
                handle.complete(request)
            } catch (cancelled: CancellationException) {
                caught = cancelled
                throw cancelled
            }
        }
        runCurrent()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(caught is CancellationException)
        assertTrue(recorder.snapshot().codes.isEmpty())
        finish(recorder)
        assertTrue(turnsOf(recorder).isEmpty())
    }

    @Test
    fun aKeylessProviderIsBoundAndCalledWithNoCredentialAndNoKeyLookup() = runTest {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            listOf({ _ -> FakeAiProvider.reply("ok", Usage.ZERO) }),
            ModelCapabilities.UNKNOWN,
            requiresCredential = false,
        )
        val credentials = ScriptedCredentialSource(emptyMap())
        val handle = bindDefault(routerOf(listOf(fake), credentials = credentials), recorder())

        assertEquals("ok", textOf(handle.complete(request)))
        assertNull(fake.calls.single().credential)
        assertTrue(credentials.requested.isEmpty())
    }

    @Test
    fun twoConcurrentCompletesOnOneHandleRecordExactlyTwoTurns() = runTest {
        val gate = CompletableDeferred<Unit>()
        val step: ProviderStep = { _ -> gate.await(); FakeAiProvider.reply("ok", Usage(3, 0, 0, 1)) }
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, listOf(step, step))
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        val first = async { handle.complete(request) }
        val second = async { handle.complete(request) }
        runCurrent()
        assertEquals(2, fake.callCount)
        gate.complete(Unit)
        listOf(first, second).awaitAll().forEach { assertEquals("ok", textOf(it)) }

        finish(recorder)
        assertEquals(2, turnsOf(recorder).size)
        assertEquals(8L, recorder.tokensUsed)
    }

    @Test
    fun manyCompletesFromRealThreadsLoseNoTurn() = runTest {
        val count = 40
        val step: ProviderStep = { _ -> FakeAiProvider.reply("ok", Usage(1, 0, 0, 0)) }
        val steps = List(count) { step }
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, steps)
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        withContext(Dispatchers.Default) {
            (1..count).map { async { handle.complete(request) } }.awaitAll()
        }

        finish(recorder)
        assertEquals(count, turnsOf(recorder).size)
        assertEquals(count.toLong(), recorder.tokensUsed)
    }

    @Test
    fun eachTurnLatencyIsTheRouterClockAdvanceInsideTheCall() = runTest {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities.UNKNOWN,
            { _ -> clock.advanceBy(13); FakeAiProvider.reply("a", Usage.ZERO) },
            { _ -> clock.advanceBy(2); FakeAiProvider.reply("b", Usage.ZERO) },
        )
        val recorder = recorder()
        recorder.tierStarted(strategy)
        val handle = bindDefault(routerOf(listOf(fake)), recorder)

        handle.complete(request)
        handle.complete(request)

        finish(recorder)
        assertEquals(listOf(13L, 2L), turnsOf(recorder).map { it.latencyMillis })
    }
}
