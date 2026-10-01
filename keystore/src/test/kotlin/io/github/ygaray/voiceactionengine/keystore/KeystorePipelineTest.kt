package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ALIAS = "secondbrain_anthropic_api_key_v1"
private const val SAVED_KEY = "  sk-ant-pipeline-0000wxyz  " // secret-scan: allow (fake canary)
private const val TRIMMED_KEY = "sk-ant-pipeline-0000wxyz" // secret-scan: allow (fake canary)

/** A key saved in the encrypted store reaches the provider through the real pipeline, and every failure is typed. */
class KeystorePipelineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null
    private val fake = FakeAiProvider(ProviderId.ANTHROPIC, listOf { _ -> FakeAiProvider.reply("ok", Usage.ZERO) })

    @After
    fun close() {
        prefs?.close()
    }

    private fun store(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        return ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.IO, keys)
    }

    private val callStep: StrategyStep = { input, session ->
        val request = ModelRequest("sys", listOf(UserMessage(input.transcript)), session.policy.maxTokensPerTurn)
        when (val result = session.model().complete(request)) {
            is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
            else -> StrategyOutcome.Completed("done")
        }
    }

    private fun pipelineOver(store: ApiKeyStore): CommandPipeline = commandPipeline {
        tier(ScriptedStrategy(StrategyId("only"), StrategyCapabilities(setOf(ProviderId.ANTHROPIC)), callStep))
        provider(fake)
        providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))
        this.credentials = KeystoreCredentialSource(store)
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

    private fun CommandOutcome.codes(): List<String> = trace.codes.map { it.value }

    @Test
    fun aSavedKeyReachesTheProviderThroughThePipeline() = runTest {
        NoNetworkGuard.during {
            val store = store()
            store.save(ProviderId.ANTHROPIC, SAVED_KEY)

            val outcome = pipelineOver(store).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            val call = fake.calls.single()
            assertEquals(ProviderId.ANTHROPIC, call.credential?.provider)
            assertEquals(TRIMMED_KEY, call.credential?.apiKey)
        }
    }

    @Test
    fun aLostDeviceKeyEndsAsCredentialUnreadableAndTheProviderIsNeverCalled() = runTest {
        NoNetworkGuard.during {
            val store = store()
            store.save(ProviderId.ANTHROPIC, SAVED_KEY)
            keys.lose(ALIAS)

            val outcome = pipelineOver(store).execute(CommandInput("hi"))

            assertEquals(
                FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "key_missing"),
                (outcome as CommandOutcome.Failed).reason,
            )
            assertTrue(outcome.codes().contains("credential_unreadable"))
            assertFalse(outcome.codes().contains("credential_source_error"))
            assertEquals(0, fake.callCount)
        }
    }

    @Test
    fun aNeverStoredKeyEndsAsNotConfiguredAndTheProviderIsNeverCalled() = runTest {
        NoNetworkGuard.during {
            val outcome = pipelineOver(store()).execute(CommandInput("hi"))

            assertEquals(FailureReason.NotConfigured(ProviderId.ANTHROPIC), (outcome as CommandOutcome.Failed).reason)
            assertFalse(outcome.codes().contains("credential_source_error"))
            assertEquals(0, fake.callCount)
        }
    }
}
