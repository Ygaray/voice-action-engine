package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

private const val KEY_BODY = "sk-CANARY-KEY-BODY"

/**
 * A key OkHttp refuses as a header value (a stray newline from a paste, a non-ASCII character) must read as an
 * authentication failure, must not throw, and must never reach any printed value: OkHttp's own refusal quotes the whole
 * header value in its message.
 */
class AnthropicMalformedKeyTest {

    private val model = "claude-haiku-4-5"

    private fun call(key: String): ProviderRequest = anthropicRequest(
        model,
        ModelRequest(
            "You are a test system.",
            listOf(UserMessage("add milk")),
            listOf(ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })),
            256,
        ),
        key,
    )

    private fun assertAuthWithoutTheKey(key: String) = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = AnthropicProvider { baseUrl = server.url("/") }

            val result = provider.complete(call(key))

            val failure = result as ModelResult.Failure
            assertEquals(FailureReason.Auth().code, failure.reason.code)
            assertNull(failure.details)
            assertFalse(failure.toString().contains(KEY_BODY))
            assertFalse(failure.reason.toString().contains(KEY_BODY))
            // Nothing was sent: a key the server could never accept is not worth a round trip.
            assertEquals(0, server.requestCount)
        }
    }

    @Test(timeout = 30_000)
    fun aKeyWithATrailingNewlineIsAnAuthFailureAndNeverThrows() {
        assertAuthWithoutTheKey("$KEY_BODY\n")
    }

    @Test(timeout = 30_000)
    fun aKeyWithACarriageReturnInTheMiddleIsAnAuthFailure() {
        assertAuthWithoutTheKey("$KEY_BODY\rmore")
    }

    @Test(timeout = 30_000)
    fun aKeyWithANonAsciiCharacterIsAnAuthFailure() {
        assertAuthWithoutTheKey("${KEY_BODY}é")
    }
}
