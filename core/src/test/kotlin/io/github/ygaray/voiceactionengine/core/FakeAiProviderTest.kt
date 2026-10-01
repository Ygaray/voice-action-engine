package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderCall
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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

    private fun usage(): Usage = Usage(INPUT, 0, 0, OUTPUT)

    private fun bare(): AiProvider = object : AiProvider {
        override val id: ProviderId = ProviderId.OPENAI

        override suspend fun complete(call: ProviderCall): ModelResult =
            ModelResult.Failure(FailureReason.RateLimited())
    }

    @Test
    fun aProviderImplementingOnlyIdAndCompleteGetsTheDefaults() {
        val provider = bare()
        assertEquals(ModelCapabilities.UNKNOWN, provider.capabilities("anything"))
        assertTrue(provider.requiresCredential)
    }

    @Test
    fun theFakeReturnsItsDeclaredCapabilitiesForAnyModelAndItsCredentialFlag() {
        val caching = ModelCapabilities { caching = CachingMode.AUTOMATIC }
        val cached = FakeAiProvider(ProviderId.ANTHROPIC, caching)
        assertEquals(caching, cached.capabilities("one"))
        assertEquals(caching, cached.capabilities("two"))
        val local = FakeAiProvider(ProviderId.ON_DEVICE, emptyList(), ModelCapabilities.UNKNOWN, false)
        assertFalse(local.requiresCredential)
        assertTrue(FakeAiProvider(ProviderId.ANTHROPIC).requiresCredential)
    }

    @Test
    fun scriptedResultsComeBackInOrderAndALambdaStepSeesTheCall() = runTest {
        var seenModel: String? = null
        var seenProvider: ProviderId? = null
        val provider = FakeAiProvider(
            ProviderId.ANTHROPIC,
            ModelCapabilities.UNKNOWN,
            { FakeAiProvider.reply("first", usage()) },
            { call ->
                seenModel = call.model
                seenProvider = call.credential?.provider
                FakeAiProvider.reply("second", usage())
            },
            { ModelResult.Failure(FailureReason.Timeout()) },
        )
        val replies = List(2) { texts(provider.complete(call())) }
        assertEquals(listOf("first", "second"), replies)
        assertEquals("model-a", seenModel)
        assertEquals(ProviderId.ANTHROPIC, seenProvider)
        assertTrue(provider.complete(call()) is ModelResult.Failure)
        assertEquals(THREE, provider.callCount)
    }

    private fun texts(result: ModelResult): String =
        ((result as ModelResult.Success).response.message.parts.single() as AssistantPart.Text).text

    @Test
    fun aCallPastTheScriptThrowsAnAssertionErrorNamingExhaustion() = runTest {
        val provider = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("only", usage()))
        provider.complete(call())
        val error = try {
            provider.complete(call())
            null
        } catch (expected: AssertionError) {
            expected
        }
        assertTrue(error?.message.orEmpty().contains("script exhausted"))
        assertEquals(2, provider.callCount)
    }

    @Test
    fun aBlankModelFailsAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderCall(" ", request(), null, ModelCapabilities.UNKNOWN)
        }
    }

    @Test
    fun providerCallToStringCarriesCountsAndProviderButNoSecret() {
        val secretRequest = ModelRequest(
            CANARY_SYSTEM,
            listOf(UserMessage(CANARY_TEXT)),
            listOf(ToolSpec("log_item", "d", JsonObject(emptyMap()))),
            ToolChoice.Auto(),
            MAX_TOKENS,
            CacheDirective(true),
        )
        val rendered =
            ProviderCall("model-a", secretRequest, Credential(ProviderId.ANTHROPIC, KEY), ModelCapabilities.UNKNOWN)
                .toString()
        assertEquals("ProviderCall(model=model-a, messages=1, tools=1, credential=anthropic)", rendered)
        assertFalse(rendered.contains(KEY))
        assertFalse(rendered.contains(CANARY_SYSTEM))
        assertFalse(rendered.contains(CANARY_TEXT))
    }

    @Test
    fun aNullCredentialPrintsNone() {
        val rendered = ProviderCall("model-a", request(), null, ModelCapabilities.UNKNOWN).toString()
        assertTrue(rendered, rendered.endsWith("credential=none)"))
    }

    @Test
    fun failureHasNullDetailsWhenBuiltFromAReasonAndPrintsTheCode() {
        val failure = ModelResult.Failure(FailureReason.RateLimited())
        assertNull(failure.details)
        assertTrue(failure.toString(), failure.toString().contains("rate_limited"))
    }

    @Test
    fun successToStringShowsTheSummaryWithoutTheReplyText() {
        val success = FakeAiProvider.reply(CANARY_TEXT, usage())
        val rendered = success.toString()
        assertTrue(rendered, rendered.startsWith("ModelResult.Success(response=ModelResponse("))
        assertFalse(rendered.contains(CANARY_TEXT))
    }

    @Test
    fun toolCallBuilderYieldsAToolUseTurnWithOneNamedCall() {
        val arguments = buildJsonObject { put("item", "apple") }
        val success = FakeAiProvider.toolCall("call_1", "log_item", arguments, usage()) as ModelResult.Success
        val call = success.response.message.toolCalls.single()
        assertEquals("log_item", call.name)
        assertEquals("call_1", call.id)
        assertEquals(StopReason.TOOL_USE, success.response.stopReason)
    }

    private companion object {
        const val CANARY_SYSTEM = "CANARY-SYSTEM-PROMPT-9d41"
        const val CANARY_TEXT = "CANARY-USER-TEXT-77ab"
        const val THREE = 3
        const val KEY = "sk-fake-provider-test-key"
        const val MAX_TOKENS = 256
        const val INPUT = 12L
        const val OUTPUT = 3L
    }
}
