package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicErrorMapTest {

    private val canary = "CANARY-ECHO"

    /** A server that answers every request the same way, so a test never depends on how many calls were made. */
    private class SameAnswer(private val answer: () -> MockResponse) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = answer()
    }

    private val model = "claude-haiku-4-5"

    private fun request(): ModelRequest = ModelRequest(
        "You are a test system.",
        listOf(UserMessage("add milk")),
        listOf(ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })),
        256,
    )

    @Test
    fun aProviderErrorReachesTheAppAsATypedFailureWithStatusTypeAndRequestIdOnly() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = SameAnswer {
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("request-id", "req_err_1")
                    .setBody(errorBody("authentication_error", "invalid x-api-key $canary", "req_body_1"))
            }
            server.start()
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(request())
                if (result is ModelResult.Failure) {
                    StrategyOutcome.Failed(result.reason, result.details)
                } else {
                    StrategyOutcome.Completed(null)
                }
            }
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(AnthropicProvider { baseUrl = server.url("/") })
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
                credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("add milk", "en", null)) as CommandOutcome.Failed

            assertEquals("auth", outcome.reason.code)
            assertEquals(FailureDetails(401, "authentication_error", "req_err_1"), outcome.details)
            assertFalse(outcome.toString().contains(canary))
            assertFalse(outcome.trace.toString().contains(canary))
            assertFalse(outcome.details.toString().contains(canary))
        }
    }

    private fun parse(
        status: Int,
        type: String? = "invalid_request_error",
        message: String? = "bad $canary",
        header: String? = null,
        bodyRequestId: String? = "req_b",
        errorCode: String? = null,
    ): AnthropicErrorInfo = parseAnthropicError(status, header, errorJson(type, message, bodyRequestId, errorCode))

    private fun errorJson(type: String?, message: String?, requestId: String?, errorCode: String?): String =
        buildJsonObject {
            put("type", "error")
            putJsonObject("error") {
                if (type != null) put("type", type)
                if (message != null) put("message", message)
                if (errorCode != null) putJsonObject("details") { put("error_code", errorCode) }
            }
            if (requestId != null) put("request_id", requestId)
        }.toString()

    private fun assertNoCanary(info: AnthropicErrorInfo) {
        assertFalse(info.toString().contains("CANARY"))
        assertFalse(info.details().toString().contains("CANARY"))
        assertFalse(info.reason().toString().contains("CANARY"))
    }

    @Test
    fun everyStatusRowMapsToItsReasonAndKeepsTheStatus() {
        val rows = mapOf(
            401 to "auth", 403 to "auth", 402 to "billing", 429 to "rate_limited", 404 to "model_not_found",
            408 to "timeout", 504 to "timeout", 503 to "overloaded", 529 to "overloaded",
            400 to "http_error", 413 to "http_error", 500 to "http_error", 502 to "http_error", 307 to "http_error",
        )
        assertEquals(14, rows.size)
        for ((status, code) in rows) {
            val info = parse(status)
            assertEquals("status $status", code, info.reason().code)
            assertEquals(status, info.details().httpStatus)
            assertEquals("invalid_request_error", info.details().providerErrorType)
            assertNoCanary(info)
        }
    }

    @Test
    fun anEnforcedSpendCapOn429IsBillingAndAnyOtherErrorCodeIsARateLimit() {
        val cap = parse(429, "rate_limit_error", errorCode = "enforced_spend_limit_reached")
        assertEquals("billing", cap.reason().code)
        assertTrue(cap.spendCapReached)
        assertFalse(cap.userSpendLimit)
        assertNoCanary(cap)

        val other = parse(429, "rate_limit_error", errorCode = "something_else")
        assertEquals("rate_limited", other.reason().code)
        assertFalse(other.spendCapReached)

        val plain = parse(429, "rate_limit_error")
        assertEquals("rate_limited", plain.reason().code)
        assertFalse(plain.spendCapReached)
    }

    @Test
    fun aUserSpendLimitOn400IsBillingAndAnyOtherBadRequestIsAnHttpError() {
        val limit = parse(400, message = "You have reached your specified API usage limits. $canary")
        assertEquals("billing", limit.reason().code)
        assertTrue(limit.userSpendLimit)
        assertFalse(limit.spendCapReached)
        assertNoCanary(limit)

        val other = parse(400, message = "messages: text content blocks must be non-empty $canary")
        assertEquals("http_error", other.reason().code)
        assertFalse(other.userSpendLimit)
        assertNoCanary(other)
    }

    @Test
    fun theToolChoiceFlagFollowsTheMessageOnly() {
        val named = parse(400, message = "tool_choice: type \"tool\" and \"any\" are not supported for this model.")
        assertTrue(named.mentionsToolChoice)
        assertFalse(parse(400, message = "messages: roles must alternate $canary").mentionsToolChoice)
        assertFalse(parse(400, message = null).mentionsToolChoice)
    }

    @Test
    fun theHeaderRequestIdBeatsTheBodyOneAndAHostileHeaderFallsBack() {
        assertEquals("req_h", parse(500, header = "req_h", bodyRequestId = "req_b").requestId)
        assertEquals("req_b", parse(500, header = null, bodyRequestId = "req_b").requestId)
        assertEquals("req_b", parse(500, header = "has space", bodyRequestId = "req_b").requestId)
        assertEquals("req_b", parse(500, header = "x".repeat(129), bodyRequestId = "req_b").requestId)
        assertNull(parse(500, header = "has space", bodyRequestId = "also bad").requestId)
        assertNull(parse(500, header = "x".repeat(129), bodyRequestId = "y".repeat(129)).requestId)
        assertNull(parse(500, header = null, bodyRequestId = null).requestId)
    }

    @Test
    fun aHostileErrorTypeIsDroppedButTheReasonStillComesFromTheStatus() {
        val hostile = listOf("has space", "has\"quote", "has\nnewline", "t".repeat(65))
        for (type in hostile) {
            val info = parse(401, type = type)
            assertNull(type, info.errorType)
            assertNull(info.details().providerErrorType)
            assertEquals("auth", info.reason().code)
            assertEquals(401, info.details().httpStatus)
        }
        assertEquals("t".repeat(64), parse(401, type = "t".repeat(64)).errorType)
    }

    @Test
    fun aMissingErrorObjectANullBodyAndANonJsonBodyAllKeepTheStatusReason() {
        val bodies = listOf(
            null,
            "",
            "<html>$canary bad gateway</html>",
            "[\"$canary\"]",
            "\"$canary\"",
            "{\"type\":\"error\",\"request_id\":\"req_b\"}",
            "{\"error\":\"$canary\"}",
            "{\"error\":{\"type\":42,\"message\":[\"$canary\"],\"details\":\"$canary\"}}",
        )
        for (body in bodies) {
            val info = parseAnthropicError(503, null, body)
            assertNull(body, info.errorType)
            assertEquals("overloaded", info.reason().code)
            assertEquals(503, info.details().httpStatus)
            assertFalse(info.spendCapReached)
            assertFalse(info.userSpendLimit)
            assertFalse(info.mentionsToolChoice)
            assertNoCanary(info)
        }
        assertEquals("req_b", parseAnthropicError(503, null, bodies[5]).requestId)
    }
}
