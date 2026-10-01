package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderCall
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiProviderTest {

    private fun request(): ModelRequest = ModelRequest("system", listOf(UserMessage("hi")), MAX_TOKENS)

    private fun call(): ProviderCall =
        ProviderCall("model-a", request(), Credential(ProviderId.ANTHROPIC, KEY), ModelCapabilities.UNKNOWN)

    @Test
    fun singleShotRequestReachesTheFakeAndTheScriptedResponseComesBack() = runTest {
        val usage = Usage(INPUT, 0, 0, OUTPUT)
        val provider = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("ok", usage))
        val call = call()

        val result = provider.complete(call)

        val success = result as ModelResult.Success
        val text = success.response.message.parts.single() as AssistantPart.Text
        assertEquals("ok", text.text)
        assertEquals(StopReason.END_TURN, success.response.stopReason)
        assertEquals(usage, success.response.usage)
        assertEquals(1, provider.callCount)
        assertSame(call, provider.calls.single())
    }

    private companion object {
        const val KEY = "sk-fake-provider-test-key"
        const val MAX_TOKENS = 256
        const val INPUT = 12L
        const val OUTPUT = 3L
    }
}
