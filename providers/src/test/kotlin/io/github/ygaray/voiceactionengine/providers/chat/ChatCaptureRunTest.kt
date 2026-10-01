package io.github.ygaray.voiceactionengine.providers.chat

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val TEST_TIMEOUT_MILLIS = 60_000L
private const val FAKE_KEY = "fake-openai-credential"
private const val REAL_ID = "chatcmpl-REAL123"
private const val TWO = 2
private const val SIX = 6
private const val SEVEN = 7

/**
 * Key-free proof of the capture runner: it is pointed at a loopback server instead of the vendors, so the sending,
 * counting, decoding, file writing and sanitizing all run once before any key is ever used.
 */
class ChatCaptureRunTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun call(code: String): PlannedCall = CapturePlan.all.single { it.code == code }

    private fun toolAnswer(): MockResponse {
        val arguments = """{"items":[{"name":"egg","quantity":2,"unit":null,"confidence":0.9}],"target_date":null}"""
        val message = chatMessage(null, listOf(chatToolCall("call_real_1", "log_food", arguments)))
        val body = chatBody(message, "tool_calls", chatUsage(1200, 30), REAL_ID)
        return MockResponse().setResponseCode(200).setBody(body)
    }

    private fun proseAnswer(): MockResponse {
        val body = chatBody(chatMessage("Sure thing."), "stop", chatUsage(10, 3), REAL_ID)
        return MockResponse().setResponseCode(200).setBody(body)
    }

    private fun keyProbeAnswer(): MockResponse = MockResponse()
        .setResponseCode(401)
        .setHeader("x-request-id", "req_abc123")
        .setBody(openAiErrorBody("invalid_request_error", "invalid_api_key", "Incorrect API key provided"))

    private fun runner(server: MockWebServer, raw: File, golden: File): CaptureRun =
        CaptureRun(mapOf(VENDOR_OPENAI to FAKE_KEY, VENDOR_OPENROUTER to FAKE_KEY), raw, golden) {
            server.url("/").toString()
        }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aMetIntentWritesTheRawAndTheSanitizedCopyAndSendsTheRightCredential() {
        val raw = folder.newFolder("raw")
        val golden = folder.newFolder("golden")
        MockWebServer().use { server ->
            server.enqueue(toolAnswer())
            server.enqueue(keyProbeAnswer())
            server.start()
            val run = runner(server, raw, golden)
            run.run(listOf(call("C1"), call("C5")))

            assertEquals(emptyList<String>(), run.unmet)
            assertEquals(TWO, run.requests)
            assertEquals(TWO, run.perVendor(VENDOR_OPENAI))
            assertEquals("Bearer $FAKE_KEY", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer invalid-credential-for-capture", server.takeRequest().getHeader("Authorization"))
        }
        assertTrue(File(raw, "openai-forced_log_food.json").readText().contains(REAL_ID))
        val clean = File(golden, "openai-forced_log_food.json").readText()
        assertFalse(clean.contains(REAL_ID))
        assertTrue(clean.contains("chatcmpl-GOLDEN"))
        assertTrue(clean.endsWith("\n"))
        assertEquals(emptyList<String>(), goldenHygieneViolations(clean))
        assertTrue(File(golden, "openai-invalid_key.json").isFile)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anUnmetIntentIsCollectedNotThrown() {
        MockWebServer().use { server ->
            server.enqueue(proseAnswer())
            server.start()
            val run = runner(server, folder.newFolder("raw"), folder.newFolder("golden"))
            run.run(listOf(call("C1")))

            assertEquals(1, run.unmet.size)
            assertTrue(run.unmet.toString(), run.unmet.single().startsWith("C1 expected success:tool_use:log_food"))
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aBodyThatIsNotJsonKeepsOnlyTheRawCopyAndIsReported() {
        val golden = folder.newFolder("golden")
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
            server.start()
            val run = runner(server, folder.newFolder("raw"), golden)
            run.run(listOf(call("C5")))

            assertTrue(run.unmet.toString(), run.unmet.any { it.contains("could not be sanitized") })
        }
        assertEquals(0, golden.listFiles().orEmpty().size)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun theVendorCeilingStopsTheSeventhRequestBeforeItIsSent() {
        MockWebServer().use { server ->
            repeat(SIX) { server.enqueue(toolAnswer()) }
            server.start()
            val run = runner(server, folder.newFolder("raw"), folder.newFolder("golden"))
            run.run(List(SEVEN) { call("C1") })

            assertEquals(SIX, run.requests)
            assertEquals(SIX, server.requestCount)
            assertEquals(listOf("C1 skipped by the request ceiling"), run.unmet)
        }
    }
}
