package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
import io.github.ygaray.voiceactionengine.spike.gate.SpikeOnDeviceCapability
import io.github.ygaray.voiceactionengine.spike.provider.ProviderRoute
import io.github.ygaray.voiceactionengine.spike.provider.SpikeOnDeviceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CREATE = "create_item"
private const val FIND = "find_item"
private const val CANARY_PROMPT = "canary-prompt-utterance-zq91"
private const val CANARY_ANSWER = "canary-model-output-7fk2 this is not json"

/** The real engine path (SingleShot, router, on-device gate) driving the spike provider with a fake backend. */
class SpikeProviderTest {
    private val onDeviceOnly = StrategyCapabilities(setOf(ProviderId.ON_DEVICE))
    private val selection = ProviderSelection(ProviderId.ON_DEVICE, "e2b")

    private fun schema(field: String, type: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject(field) { put("type", type) } }
        putJsonArray("required") { add(JsonPrimitive(field)) }
    }

    private val create = ToolSpec(CREATE, "Creates an item.", schema("title", "string"), mutating = true)
    private val find = ToolSpec(FIND, "Finds an item.", schema("query", "string"))

    private val extractions = mutableListOf<String>()

    private fun available(): OnDeviceCapability =
        SpikeOnDeviceCapability({ true }, { listOf("arm64-v8a") }, { InitOutcome(null, 1L) })

    private fun tier(): SingleShotStrategy = SingleShotStrategy(StrategyId("single")) {
        tooling = ToolSpecProvider.fixed(ToolingSnapshot("system text", listOf(create, find), CREATE))
        resolver = OutcomeResolver { extraction, _ ->
            extractions.add("${extraction.toolName}:${extraction.arguments}")
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(extraction.toolName, StepResult("ok")))))
        }
        capabilities = onDeviceOnly
    }

    private class Rig(
        val pipeline: CommandPipeline,
        val gate: ScriptedGate,
        val sink: RecordingCommitSink,
        val credentials: ScriptedCredentialSource,
    )

    private fun rig(backend: FakeLlmBackend, capability: OnDeviceCapability): Rig {
        val provider = SpikeOnDeviceProvider(backend, ProviderRoute.CONSTRAINED_JSON, Dispatchers.Unconfined)
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val credentials = ScriptedCredentialSource.keys()
        val pipeline = commandPipeline {
            tier(tier())
            provider(provider)
            onDevice = capability
            providerSelection = ScriptedSelectionSource.fixed(selection)
            this.credentials = credentials
            this.gate = gate
            commitSink = sink
        }
        return Rig(pipeline, gate, sink, credentials)
    }

    @Test
    fun aTranscriptRunsThroughSingleShotTheRouterAndTheGateAsTheExpectedToolCall() = runTest {
        NoNetworkGuard.during {
            val backend = FakeLlmBackend(FakeLlmBackend.text("""{"title":"milk"}"""))
            val rig = rig(backend, available())

            val outcome = rig.pipeline.execute(CommandInput(CANARY_PROMPT))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(listOf(CREATE), rig.gate.proposals.single().mutations.map { it.toolName })
            assertEquals(1, rig.sink.actions.size)
            assertEquals(listOf("""$CREATE:{"title":"milk"}"""), extractions)
            assertEquals(1, backend.calls)
            val request = backend.requests.single()
            assertTrue(request.mode is BackendMode.Constrained)
            assertEquals(create.inputSchema, (request.mode as BackendMode.Constrained).schema)
            assertTrue(request.constraintOn)
            assertTrue(rig.credentials.requested.isEmpty())
        }
    }

    @Test
    fun anUnavailableModelFailsTheOnDeviceOnlyTierLoudlyWithNoBackendCall() = runTest {
        NoNetworkGuard.during {
            val backend = FakeLlmBackend()
            val missing = SpikeOnDeviceCapability({ false }, { listOf("arm64-v8a") }, { null })
            val rig = rig(backend, missing)

            val outcome = rig.pipeline.execute(CommandInput(CANARY_PROMPT))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            val reason = (outcome as CommandOutcome.Failed).reason
            assertTrue(reason is FailureReason.ProviderUnavailable)
            assertEquals(ProviderId.ON_DEVICE, (reason as FailureReason.ProviderUnavailable).provider)
            assertEquals(0, backend.calls)
            assertEquals(0, rig.gate.calls)
            assertTrue(rig.credentials.requested.isEmpty())
        }
    }

    @Test
    fun theGateNamesTheFirstFailedFactInOrder() = runTest {
        val abis = SpikeOnDeviceCapability({ true }, { listOf("x86_64") }, { null })
        val failedInit = SpikeOnDeviceCapability({ true }, { listOf("arm64-v8a") }, { InitOutcome("gpu_init_failed", 5L) })
        assertEquals("OnDeviceAvailability.Unavailable(code=abi_unsupported)", abis.availability().toString())
        assertEquals("OnDeviceAvailability.Unavailable(code=init_failed)", failedInit.availability().toString())
        assertEquals("OnDeviceAvailability.Available", available().availability().toString())
    }

    @Test
    fun aMalformedAnswerIsTheTypedFailureAndNoAnswerTextLeaksIntoIt() = runTest {
        NoNetworkGuard.during {
            val backend = FakeLlmBackend(FakeLlmBackend.text(CANARY_ANSWER))
            val rig = rig(backend, available())

            val outcome = rig.pipeline.execute(CommandInput(CANARY_PROMPT))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            val reason = (outcome as CommandOutcome.Failed).reason
            assertEquals(FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "malformed_output"), reason)
            assertFalse(outcome.toString().contains("canary"))
            assertFalse(reason.toString().contains("canary"))
            assertTrue(rig.gate.proposals.isEmpty())
        }
    }

    @Test
    fun theProviderShowsItsRouteOnlyAndNeedsNoCredential() {
        val provider = SpikeOnDeviceProvider(FakeLlmBackend(), ProviderRoute.NATIVE_TOOLS, Dispatchers.Unconfined)
        assertEquals("SpikeOnDeviceProvider(route=NATIVE_TOOLS)", provider.toString())
        assertEquals(ProviderId.ON_DEVICE, provider.id)
        assertFalse(provider.requiresCredential)
        assertTrue(provider.capabilities("e2b").supportsTools)
        assertTrue(provider.capabilities("e2b").supportsForcedToolChoice)
    }
}
