package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val OPT_IN_VAR = "VAE_LIVE_ANTHROPIC"
private const val KEY_VAR = "ANTHROPIC_API_KEY"

// The one model the capture may call: the cheapest, and one that accepts a forced tool choice.
private const val CAPTURE_MODEL = "claude-haiku-4-5"
private const val TOOL_NAME = "record_item"

// Hard ceiling on HTTP requests for the whole capture.
private const val MAX_HTTP_REQUESTS = 6

// A call may send a second request when the first is repeated or reshaped; the credential probe never does.
private const val WORST_CASE_REQUESTS = 2
private const val PROBE_WORST_CASE_REQUESTS = 1

// Haiku 4.5 caches a prefix only from 4096 tokens; this many fixed characters clear that with a wide margin.
private const val PREFIX_CHARS = 32_000
private const val HAIKU_MIN_CACHEABLE_PREFIX = 4096
private const val MAX_OUTPUT_TOKENS = 128
private const val PROBE_OUTPUT_TOKENS = 16
private const val TEST_TIMEOUT_MILLIS = 300_000L

/**
 * Opt-in capture of a few real Anthropic answers, run only by the liveAnthropicCapture task and never by check.
 *
 * It is skipped unless VAE_LIVE_ANTHROPIC is 1 and ANTHROPIC_API_KEY is set, makes at most six HTTP requests, calls
 * Haiku 4.5 only, and prints ids, statuses and counts: never the key, a body, text or tool arguments.
 */
class AnthropicLiveCaptureTest {

    private val requestCount = AtomicInteger(0)
    private val mismatches = mutableListOf<String>()

    private fun schema() = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("item") { put("type", "string") } }
        putJsonArray("required") { add("item") }
        put("additionalProperties", false)
    }

    private fun bigSystemPrompt(): String {
        val sentence = "The assistant records each shopping list item exactly as the user dictates it. "
        return sentence.repeat(PREFIX_CHARS / sentence.length + 1)
    }

    private fun forcedRequest(): ModelRequest = ModelRequest(
        bigSystemPrompt(),
        listOf(UserMessage("add milk")),
        listOf(ToolSpec(TOOL_NAME, "Records one item.", schema())),
        ToolChoice.Required(TOOL_NAME),
        MAX_OUTPUT_TOKENS,
        CacheDirective(true),
    )

    private fun probeRequest(): ModelRequest =
        ModelRequest("You are a test system.", listOf(UserMessage("add milk")), PROBE_OUTPUT_TOKENS)

    private fun presence(id: String?): String = if (id == null) "absent" else "present"

    private fun successLine(response: ModelResponse): String {
        val usage = response.usage
        return "result=success http=2xx error_type=- request_id=${presence(response.requestId)} " +
            "stop=${response.stopReason} tools=${response.message.toolCalls.map { it.name }} " +
            "usage=in:${usage.inputUncached},cache_read:${usage.cacheRead}," +
            "cache_write:${usage.cacheWrite},out:${usage.output}"
    }

    private fun failureLine(failure: ModelResult.Failure): String {
        val details = failure.details
        return "result=failure reason=${failure.reason.code} http=${details?.httpStatus ?: "-"} " +
            "error_type=${details?.providerErrorType ?: "-"} request_id=${presence(details?.requestId)} " +
            "stop=- tools=[] usage=-"
    }

    private fun lineFor(result: ModelResult): String = when (result) {
        is ModelResult.Success -> successLine(result.response)
        is ModelResult.Failure -> failureLine(result)
        else -> "result=other"
    }

    /** Sends one call unless it could push the total past the ceiling; prints one LIVE_CAPTURE line either way. */
    private fun capture(
        number: Int,
        label: String,
        worstCase: Int,
        send: suspend () -> ModelResult,
        expect: (ModelResult) -> Boolean,
    ) {
        if (requestCount.get() + worstCase > MAX_HTTP_REQUESTS) {
            println("LIVE_CAPTURE call=$number label=$label result=skipped_request_ceiling")
            mismatches.add("call $number ($label) skipped")
            return
        }
        val result = runBlocking { send() }
        println("LIVE_CAPTURE call=$number label=$label ${lineFor(result)}")
        if (!expect(result)) mismatches.add("call $number ($label) did not meet its expectation")
    }

    private fun toolNames(result: ModelResult): List<String> =
        (result as? ModelResult.Success)?.response?.message?.toolCalls?.map { it.name }.orEmpty()

    private fun failedWith(result: ModelResult): Boolean {
        val failure = result as? ModelResult.Failure ?: return false
        val details = failure.details
        return failure.reason.code == "auth" && details?.httpStatus == 401 &&
            details.providerErrorType == "authentication_error" && details.requestId != null
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun boundedCaptureAgainstHaiku() {
        Assume.assumeTrue("opt-in variable not set", System.getenv(OPT_IN_VAR) == "1")
        val key = System.getenv(KEY_VAR).orEmpty()
        Assume.assumeTrue("no API key in the environment", key.isNotBlank())

        val provider = AnthropicProvider {
            attemptObserver = AnthropicAttemptObserver { requestCount.incrementAndGet() }
        }
        val real = Credential(ProviderId.ANTHROPIC, key)
        val invalid = Credential(ProviderId.ANTHROPIC, "plainlettersnotarealkey")
        val actual = provider.capabilities(CAPTURE_MODEL)
        val reshaped = ModelCapabilities {
            caching = CachingMode.EXPLICIT_BREAKPOINTS
            minCacheablePrefixTokens = HAIKU_MIN_CACHEABLE_PREFIX
            supportsForcedToolChoice = false
        }
        val forced = forcedRequest()
        val probe = probeRequest()
        fun send(request: ModelRequest, credential: Credential, caps: ModelCapabilities): suspend () -> ModelResult =
            { provider.complete(ProviderRequest(CAPTURE_MODEL, request, credential, caps)) }

        capture(1, "forced", WORST_CASE_REQUESTS, send(forced, real, actual)) {
            TOOL_NAME in toolNames(it) && ((it as? ModelResult.Success)?.response?.requestId != null)
        }
        capture(2, "forced_repeat", WORST_CASE_REQUESTS, send(forced, real, actual)) {
            ((it as? ModelResult.Success)?.response?.usage?.cacheRead ?: 0L) > 0L
        }
        capture(3, "reshaped", WORST_CASE_REQUESTS, send(forced, real, reshaped)) { TOOL_NAME in toolNames(it) }
        capture(4, "invalid_credential", PROBE_WORST_CASE_REQUESTS, send(probe, invalid, actual), ::failedWith)

        println("LIVE_CAPTURE attempts=${requestCount.get()} ceiling=$MAX_HTTP_REQUESTS")
        assertTrue("HTTP requests over the ceiling: ${requestCount.get()}", requestCount.get() <= MAX_HTTP_REQUESTS)
        assertTrue("live expectations not met: $mismatches", mismatches.isEmpty())
    }
}
