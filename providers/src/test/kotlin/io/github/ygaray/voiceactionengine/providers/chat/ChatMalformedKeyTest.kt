package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
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
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val KEY_BODY = "sk-CANARY-KEY-BODY"

/**
 * A key OkHttp refuses as a header value (a stray newline from a paste, a non-ASCII character) must read as an
 * authentication failure, must not throw, and must never reach any printed value: OkHttp's own refusal quotes the whole
 * header value in its message. Both vendors, through the pipeline.
 */
class ChatMalformedKeyTest {

    private fun request(): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage("add two eggs")),
        listOf(logFoodTool()),
        ToolChoice.Required("log_food"),
        1024,
        CacheDirective(true),
    )

    private fun assertAuthWithoutTheKey(vendor: ChatVendor, model: String, key: String) = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val configure: ChatCompletionsProvider.Builder.() -> Unit = { baseUrl = server.url("/") }
            val chat = if (vendor == ChatVendor.OPENROUTER) {
                ChatCompletionsProvider.openRouter(configure)
            } else {
                ChatCompletionsProvider.openAi(configure)
            }
            val results = mutableListOf<ModelResult>()
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(request())
                results.add(result)
                if (result is ModelResult.Failure) {
                    StrategyOutcome.Failed(result.reason, result.details)
                } else {
                    StrategyOutcome.Completed(null)
                }
            }
            val listener = RecordingEventListener()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(chat)
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
                credentials = ScriptedCredentialSource.keys(vendor.providerId to key)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                this.listener = listener
            }

            val outcome = pipeline.execute(CommandInput("add two eggs", "en", null))

            val failure = results.single() as ModelResult.Failure
            assertEquals("auth", failure.reason.code)
            assertNull(failure.details)
            val failed = outcome as CommandOutcome.Failed
            assertEquals("auth", failed.reason.code)
            val printed = listOf(
                failure.toString(),
                failure.reason.toString(),
                outcome.toString(),
                failed.trace.toString(),
                listener.events.joinToString { it.toString() },
            )
            for (text in printed) assertFalse(text, text.contains(KEY_BODY))
            assertTrue(listener.events.isNotEmpty())
            // Nothing was sent: a key the server could never accept is not worth a round trip.
            assertEquals(0, server.requestCount)
        }
    }

    private fun bothVendors(key: String) {
        assertAuthWithoutTheKey(ChatVendor.OPENAI, "gpt-5.4-mini", key)
        assertAuthWithoutTheKey(ChatVendor.OPENROUTER, "openai/gpt-5.4-mini", key)
    }

    @Test(timeout = 30_000)
    fun aKeyWithATrailingNewlineIsAnAuthFailureAndNeverThrows() {
        bothVendors("$KEY_BODY\n")
    }

    @Test(timeout = 30_000)
    fun aKeyWithACarriageReturnInTheMiddleIsAnAuthFailure() {
        bothVendors("$KEY_BODY\rmore")
    }

    @Test(timeout = 30_000)
    fun aKeyWithANonAsciiCharacterIsAnAuthFailure() {
        bothVendors("${KEY_BODY}ë")
    }
}
