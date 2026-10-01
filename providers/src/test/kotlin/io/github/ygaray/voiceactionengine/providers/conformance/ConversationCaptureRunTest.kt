package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.providers.anthropic.successBody
import io.github.ygaray.voiceactionengine.providers.anthropic.textBlock
import io.github.ygaray.voiceactionengine.providers.anthropic.toolUseBlock
import io.github.ygaray.voiceactionengine.providers.anthropic.usageJson
import io.github.ygaray.voiceactionengine.providers.chat.ChatVendor
import io.github.ygaray.voiceactionengine.providers.chat.chatBody
import io.github.ygaray.voiceactionengine.providers.chat.chatMessage
import io.github.ygaray.voiceactionengine.providers.chat.chatToolCall
import io.github.ygaray.voiceactionengine.providers.chat.chatUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

private const val TEST_TIMEOUT_MILLIS = 60_000L
private const val LOOPBACK_KEY = "loopback-credential-for-recorder"
private const val HTTP_OK = 200
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val PLAN_COUNT_ANTHROPIC = 6
private const val PLAN_COUNT_CHAT = 13
private const val PLAN_COUNT_OPENAI = 4
private const val PLAN_COUNT_OPENROUTER = 9
private const val PLAN_COUNT_ALL = 19
private const val THREE = 3
private const val SIX = 6
private const val THINKING_TEXT = "first call_zz then the lookup"
private const val SIGNATURE = "SIG+/abc123=="
private const val MANIFEST_HEADER = "case\tdialect\tprovenance\tmodel\tfile\ttags\tnote"
private const val MANIFEST_PREFIX = "MANIFEST_ROW\t"

private val ALL_KEYS = mapOf("anthropic" to LOOPBACK_KEY, "openai" to LOOPBACK_KEY, "openrouter" to LOOPBACK_KEY)

private fun plan(code: String): ConversationPlan = (ConversationPlans.ANTHROPIC + ConversationPlans.CHAT).single {
    it.code == code
}

private fun thinkingBlock(text: String): JsonObject = buildJsonObject {
    put("type", "thinking")
    put("thinking", text)
    put("signature", SIGNATURE)
}

private fun anthropicToolTurn(withThinking: Boolean, cacheRead: Long = 0, stop: String = "tool_use"): MockResponse {
    val blocks = buildList<JsonElement> {
        if (withThinking) add(thinkingBlock(THINKING_TEXT))
        add(textBlock("Starting."))
        add(toolUseBlock("toolu_01RealA", "record_item", buildJsonObject { put("item", "apple") }))
        add(toolUseBlock("toolu_01RealB", "lookup_item", buildJsonObject { put("name", "kiwi") }))
    }
    val usage = usageJson(1530, 140, 0, cacheRead)
    return MockResponse().setResponseCode(HTTP_OK).setBody(successBody(blocks, stop, usage))
}

private fun anthropicEnd(cacheRead: Long = 0): MockResponse = MockResponse().setResponseCode(HTTP_OK)
    .setBody(successBody(listOf(textBlock("All done.")), "end_turn", usageJson(1800, 20, 0, cacheRead)))

private fun chatToolMessage(number: Int): JsonObject = chatMessage(
    null,
    listOf(
        chatToolCall("call_Real${number}A", "record_item", "{\"item\":\"apple\"}"),
        chatToolCall("call_Real${number}B", "lookup_item", "{\"name\":\"kiwi\"}"),
    ),
)

private fun chatToolTurn(number: Int, idPrefix: String = "chatcmpl-"): MockResponse = MockResponse()
    .setResponseCode(HTTP_OK)
    .setBody(chatBody(chatToolMessage(number), "tool_calls", chatUsage(1200, 30), "${idPrefix}REAL$number"))

private fun chatEnd(idPrefix: String = "chatcmpl-"): MockResponse = MockResponse().setResponseCode(HTTP_OK)
    .setBody(chatBody(chatMessage("All done."), "stop", chatUsage(1800, 20, 1500), "${idPrefix}REALEND"))

private fun assistantOf(body: String): JsonObject {
    val messages = (Json.parseToJsonElement(body) as JsonObject)["messages"] as JsonArray
    return messages.map { it as JsonObject }.first { (it["role"] as? JsonPrimitive)?.contentOrNull == "assistant" }
}

// Runs [block] with standard output captured, and returns what it printed.
private fun printed(block: () -> Unit): String {
    val original = System.out
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer, true, Charsets.UTF_8.name()))
    try {
        block()
    } finally {
        System.setOut(original)
    }
    return buffer.toString(Charsets.UTF_8.name())
}

private fun manifestRowOf(output: String): ConversationRow {
    val line = output.lines().single { it.startsWith(MANIFEST_PREFIX) }
    return parseConversationManifest(MANIFEST_HEADER + "\n" + line.removePrefix(MANIFEST_PREFIX)).single()
}

/**
 * Key-free proof of the conversation recorder. It is pointed at a loopback server instead of the providers, so the
 * ceilings, sending, counting, decoding, file writing, sanitizing and the replay re-check all run before any key is
 * ever used.
 */
class ConversationCaptureRunTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun recorder(server: MockWebServer, keys: Map<String, String> = ALL_KEYS): ConversationRecorder =
        ConversationRecorder(keys, File(folder.root, "raw"), File(folder.root, "golden")) { server.url("/").toString() }

    private fun golden(path: String): File = File(File(folder.root, "golden"), path)

    private fun raw(path: String): File = File(File(folder.root, "raw"), path)

    private fun serve(vararg answers: MockResponse, block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            answers.forEach { server.enqueue(it) }
            server.start()
            block(server)
        }
    }

    @Test
    fun theStatedPlansSumToTheStatedCeilings() {
        assertEquals(PLAN_COUNT_ANTHROPIC, ConversationPlans.ANTHROPIC.sumOf { it.maxRequests })
        assertEquals(PLAN_COUNT_CHAT, ConversationPlans.CHAT.sumOf { it.maxRequests })
        val direct = ConversationPlans.CHAT.filter { it.dialect == "openai" }
        assertEquals(PLAN_COUNT_OPENAI, direct.sumOf { it.maxRequests })
        val routed = ConversationPlans.CHAT.filter { it.dialect == "openrouter" }
        assertEquals(PLAN_COUNT_OPENROUTER, routed.sumOf { it.maxRequests })
        assertEquals(PLAN_COUNT_ALL, ConversationPlans.MAX_PHASE_REQUESTS)
        assertEquals(PLAN_COUNT_ANTHROPIC, ConversationPlans.MAX_ANTHROPIC_REQUESTS)
        assertEquals(PLAN_COUNT_CHAT, ConversationPlans.MAX_CHAT_REQUESTS)
        assertEquals(PLAN_COUNT_OPENAI, ConversationPlans.MAX_OPENAI_REQUESTS)
        assertEquals(PLAN_COUNT_OPENROUTER, ConversationPlans.MAX_OPENROUTER_REQUESTS)
        val all = ConversationPlans.ANTHROPIC + ConversationPlans.CHAT
        assertEquals(PLAN_COUNT_ALL, all.sumOf { it.maxRequests })
        assertEquals(listOf("A1", "A2"), ConversationPlans.ANTHROPIC.map { it.code })
        assertEquals(listOf("O1", "OP", "R1", "R2", "R3"), ConversationPlans.CHAT.map { it.code })
    }

    @Test
    fun onlyTheSonnetThinkingConversationRequiresAThinkingBlock() {
        val all = ConversationPlans.ANTHROPIC + ConversationPlans.CHAT
        assertEquals(listOf("A2"), all.filter { it.requiresThinking }.map { it.code })
    }

    @Test
    fun everyPlannedModelIsOnTheAllowedList() {
        assertEquals(
            setOf(
                "claude-haiku-4-5",
                "claude-sonnet-5-5",
                "gpt-5.4-mini",
                "openai/gpt-5.4-mini",
                "openai/gpt-oss-120b",
                "anthropic/claude-sonnet-5.5",
            ),
            ConversationPlans.ALLOWED_MODELS,
        )
        (ConversationPlans.ANTHROPIC + ConversationPlans.CHAT).forEach {
            assertTrue("${it.code} model is not allowed", it.model in ConversationPlans.ALLOWED_MODELS)
        }
        assertEquals(emptyList<String>(), ConversationPlans.violations(ConversationPlans.ANTHROPIC))
        assertEquals(emptyList<String>(), ConversationPlans.violations(ConversationPlans.CHAT))
        assertEquals("A1", plan("A1").toString())
    }

    @Test
    fun selectionKeepsPlanOrderAndRefusesUnknownCodes() {
        assertEquals(listOf("A2"), ConversationPlans.selected("A2", ConversationPlans.ANTHROPIC).map { it.code })
        val both = ConversationPlans.selected(" a2 , a1", ConversationPlans.ANTHROPIC)
        assertEquals(listOf("A1", "A2"), both.map { it.code })
        assertEquals(ConversationPlans.CHAT, ConversationPlans.selected(null, ConversationPlans.CHAT))
        assertEquals(ConversationPlans.CHAT, ConversationPlans.selected(" ", ConversationPlans.CHAT))
        assertThrows(IllegalArgumentException::class.java) { ConversationPlans.selected("Z9", ConversationPlans.CHAT) }
    }

    @Test
    fun aPlanThatBreaksARuleIsAViolation() {
        val echoWithoutSource = ConversationPlans.selected("OP", ConversationPlans.CHAT)
        assertTrue(ConversationPlans.violations(echoWithoutSource).isNotEmpty())
        val sourceFirst = ConversationPlans.selected("O1,OP", ConversationPlans.CHAT)
        assertEquals(emptyList<String>(), ConversationPlans.violations(sourceFirst))
        val unlisted = ConversationPlan("X1", "openai", "gpt-5-pro", false, 1024, 1)
        assertTrue(ConversationPlans.violations(listOf(unlisted)).isNotEmpty())
        val twice = listOf(plan("A1"), plan("A1"))
        assertTrue(ConversationPlans.violations(twice).isNotEmpty())
        val extra = ConversationPlan("A3", "anthropic", "claude-haiku-4-5", true, 1024, 1)
        assertTrue(ConversationPlans.violations(ConversationPlans.ANTHROPIC + extra).isNotEmpty())
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anAnthropicConversationIsRecordedSanitizedAndReplayed() {
        serve(anthropicToolTurn(withThinking = true), anthropicEnd(cacheRead = 1500)) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("A1"))) }

            assertEquals(emptyList<String>(), recorder.unmet)
            assertEquals(2, recorder.requests)
            assertEquals(2, server.requestCount)
            val first = server.takeRequest()
            assertEquals("/v1/messages", first.path)
            assertEquals(LOOPBACK_KEY, first.getHeader("x-api-key"))
            assertEquals("2023-06-01", first.getHeader("anthropic-version"))
            assertEquals(LOOPBACK_KEY, server.takeRequest().getHeader("x-api-key"))

            val text = golden("anthropic/captured_a1.json").readText()
            assertTrue(text.endsWith("\n"))
            assertEquals(canonicalJson(text), text.trim())
            assertEquals(emptyList<String>(), conversationHygieneViolations(text))
            assertFalse(text.contains(LOOPBACK_KEY))
            assertFalse(text.contains("toolu_01Real"))
            assertTrue(text.contains("\"thinking\":\"$THINKING_TEXT\""))
            assertTrue(text.contains("\"signature\":\"$SIGNATURE\""))

            val turns = parseConversation(text)
            assertEquals(2, turns.size)
            val issued = (turns[0].response["content"] as JsonArray).map { it as JsonObject }
                .filter { (it["type"] as JsonPrimitive).content == "tool_use" }
                .map { (it["id"] as JsonPrimitive).content }
            assertEquals(listOf("toolu_GOLDEN1", "toolu_GOLDEN2"), issued)
            val answered = ((turns[1].messages.last() as JsonObject)["content"] as JsonArray).map { it as JsonObject }
                .map { (it["tool_use_id"] as JsonPrimitive).content }
            assertEquals(issued, answered)

            val row = manifestRowOf(output)
            assertEquals("captured_a1", row.case)
            assertEquals("captured", row.provenance)
            assertEquals("claude-haiku-4-5", row.model)
            assertEquals("anthropic/captured_a1.json", row.file)
            assertEquals(setOf("parallel", "error_result", "interleaved_text", "thinking", "long_system"), row.tags)
            assertEquals(emptyList<String>(), replayConversation(AnthropicWire, row, turns).violations)

            assertTrue(raw("anthropic/A1/turn-1.response.json").readText().contains("toolu_01RealA"))
            assertTrue(raw("anthropic/A1/turn-2.messages.json").readText().contains("toolu_01RealA"))
            assertFalse(raw("anthropic/A1/turn-1.messages.json").readText().contains(LOOPBACK_KEY))
            assertTrue(recorder.findings.any { it.contains("parallel") })
            assertTrue(recorder.findings.any { it.contains("cache") })
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anOpenAiConversationIsRecordedSanitizedAndReplayed() {
        serve(chatToolTurn(1), chatEnd()) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("O1"))) }

            assertEquals(emptyList<String>(), recorder.unmet)
            assertEquals(2, recorder.requests)
            assertEquals(2, recorder.perVendor("openai"))
            val first = server.takeRequest()
            assertEquals("/chat/completions", first.path)
            assertEquals("Bearer $LOOPBACK_KEY", first.getHeader("Authorization"))

            val text = golden("openai/captured_o1.json").readText()
            assertEquals(canonicalJson(text), text.trim())
            assertEquals(emptyList<String>(), conversationHygieneViolations(text))
            assertFalse(text.contains(LOOPBACK_KEY))
            assertFalse(text.contains("call_Real"))
            assertTrue(text.contains("chatcmpl-GOLDEN1"))
            val turns = parseConversation(text)
            assertEquals(THREE, Regex("call_GOLDEN1").findAll(text).count())
            val row = manifestRowOf(output)
            assertEquals("captured_o1", row.case)
            assertEquals("gpt-5.4-mini", row.model)
            assertEquals(setOf("parallel", "error_result"), row.tags)
            val wire = ChatWire(ChatVendor.OPENAI, "openai")
            assertEquals(emptyList<String>(), replayConversation(wire, row, turns).violations)
            assertTrue(raw("openai/O1/turn-1.response.json").readText().contains("chatcmpl-REAL1"))
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anOpenRouterConversationUsesItsOwnVendorAndIds() {
        serve(chatToolTurn(1, "gen-"), chatEnd("gen-")) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("R1"))) }

            assertEquals(emptyList<String>(), recorder.unmet)
            assertEquals(2, recorder.perVendor("openrouter"))
            assertEquals(0, recorder.perVendor("openai"))
            val text = golden("openrouter/captured_r1.json").readText()
            assertTrue(text.contains("gen-GOLDEN1"))
            assertFalse(text.contains("gen-REAL"))
            assertEquals("openai/gpt-5.4-mini", manifestRowOf(output).model)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aConversationThatKeepsAskingForCallsStopsAtItsRequestLimitAndWritesNoGolden() {
        serve(chatToolTurn(1), chatToolTurn(2), chatToolTurn(THREE), chatToolTurn(4)) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("O1")))

            assertEquals(THREE, recorder.requests)
            assertEquals(THREE, server.requestCount)
            assertEquals(1, recorder.unmet.size)
            assertTrue(recorder.unmet.toString(), recorder.unmet.single().contains("incomplete"))
            assertFalse(golden("openai/captured_o1.json").exists())
            assertTrue(raw("openai/O1/turn-3.response.json").isFile)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aRefusedFirstTurnKeepsTheRawBodyOnlyAndSendsNothingMore() {
        val refusal = MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("{\"error\":\"nope\"}")
        serve(refusal, anthropicEnd()) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("A1")))

            assertEquals(1, recorder.requests)
            assertEquals(1, server.requestCount)
            assertEquals(1, recorder.unmet.size)
            assertEquals("{\"error\":\"nope\"}", raw("anthropic/A1/turn-1.response.json").readText())
            assertFalse(golden("anthropic/captured_a1.json").exists())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aTruncatedTurnIsUnmetAndWritesNoGolden() {
        serve(anthropicToolTurn(withThinking = false, stop = "max_tokens")) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("A1")))

            assertEquals(1, server.requestCount)
            assertTrue(recorder.unmet.toString(), recorder.unmet.single().contains("truncated"))
            assertFalse(golden("anthropic/captured_a1.json").exists())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aConversationThatWouldPassTheDialectCeilingIsSkippedWithoutSending() {
        val answers = Array(SIX) { anthropicToolTurn(withThinking = false) }
        serve(*answers) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("A1"), plan("A2"), plan("A1"))) }

            assertEquals(SIX, recorder.requests)
            assertEquals(SIX, server.requestCount)
            assertTrue(recorder.unmet.toString(), recorder.unmet.contains("A1 skipped by the request ceiling"))
            assertEquals(THREE, recorder.unmet.size)
            assertEquals(SIX, output.lines().count { it.contains(" conv=") })
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aConversationThatWouldPassTheVendorCeilingIsSkippedWithoutSending() {
        serve(chatToolTurn(1), chatToolTurn(2), chatToolTurn(THREE), chatEnd()) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("O1"), plan("O1")))

            assertEquals(THREE, server.requestCount)
            assertTrue(recorder.unmet.toString(), recorder.unmet.contains("O1 skipped by the request ceiling"))
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun theEchoProbeSendsTheUnfilteredStoredMessageOnceAndWritesNoGolden() {
        val annotated = buildJsonObject {
            chatToolMessage(1).forEach { (key, value) -> put(key, value) }
            put("annotations", JsonArray(listOf(buildJsonObject { put("type", "url_citation") })))
        }
        val first = MockResponse().setResponseCode(HTTP_OK)
            .setBody(chatBody(annotated, "tool_calls", chatUsage(1200, 30), "chatcmpl-REAL1"))
        serve(first, chatEnd(), chatEnd()) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("O1"), plan("OP"))) }

            assertEquals(emptyList<String>(), recorder.unmet)
            assertEquals(THREE, server.requestCount)
            assertEquals(THREE, recorder.perVendor("openai"))
            server.takeRequest()
            server.takeRequest()
            val probe = server.takeRequest()
            val sentAssistant = assistantOf(probe.body.readUtf8())
            assertNotNull(sentAssistant["annotations"])
            assertEquals(annotated, sentAssistant)
            val verdict = recorder.findings.any { it.contains("accepted") && it.contains("200") }
            assertTrue(recorder.findings.toString(), verdict)
            assertTrue(golden("openai/captured_o1.json").isFile)
            assertFalse(golden("openai/captured_op.json").exists())
            assertTrue(raw("openai/OP/turn-1.response.json").isFile)
            assertEquals(1, output.lines().count { it.contains(" conv=OP ") })
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aRejectedEchoIsAFindingNotAnUnmetExpectation() {
        val rejection = MockResponse().setResponseCode(HTTP_BAD_REQUEST).setBody("{\"error\":\"bad field\"}")
        serve(chatToolTurn(1), chatEnd(), rejection) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("O1"), plan("OP")))

            assertEquals(emptyList<String>(), recorder.unmet)
            val verdict = recorder.findings.any { it.contains("rejected") && it.contains("400") }
            assertTrue(recorder.findings.toString(), verdict)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun theEchoProbeNeedsItsSourceConversation() {
        serve(chatEnd()) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("OP")))

            assertEquals(0, server.requestCount)
            assertEquals(1, recorder.unmet.size)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aThinkingConversationWithoutAThinkingBlockIsUnmetAndWritesNoGolden() {
        serve(anthropicToolTurn(withThinking = false), anthropicEnd()) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("A2")))

            assertEquals(2, server.requestCount)
            assertTrue(recorder.unmet.toString(), recorder.unmet.single().contains("no thinking block"))
            assertFalse(golden("anthropic/captured_a2.json").exists())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aThinkingConversationWithAThinkingBlockIsMet() {
        serve(anthropicToolTurn(withThinking = true), anthropicEnd()) { server ->
            val recorder = recorder(server)
            recorder.run(listOf(plan("A2")))

            assertEquals(emptyList<String>(), recorder.unmet)
            assertTrue(golden("anthropic/captured_a2.json").isFile)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aKeyShapedStringInsideThinkingRefusesTheWholeConversation() {
        val secret = "sk-" + "a1B2".repeat(5)
        val turn = MockResponse().setResponseCode(HTTP_OK).setBody(
            successBody(
                listOf(
                    thinkingBlock("careful: $secret"),
                    toolUseBlock("toolu_01RealA", "record_item", buildJsonObject { put("item", "apple") }),
                ),
                "tool_use",
            ),
        )
        serve(turn, anthropicEnd()) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("A2"))) }

            assertTrue(recorder.unmet.toString(), recorder.unmet.any { it.contains("could not be sanitized") })
            assertFalse(golden("anthropic/captured_a2.json").exists())
            assertFalse(output.contains(secret))
            assertTrue(raw("anthropic/A2/turn-1.response.json").isFile)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aMissingKeyIsUnmetAndNothingIsSent() {
        serve(anthropicEnd()) { server ->
            val recorder = recorder(server, keys = emptyMap())
            recorder.run(listOf(plan("A1")))

            assertEquals(0, server.requestCount)
            assertEquals(0, recorder.requests)
            assertEquals(1, recorder.unmet.size)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun theRecorderPrintsOnlyCountsStatusesAndFlags() {
        serve(anthropicToolTurn(withThinking = true), anthropicEnd(cacheRead = 1500)) { server ->
            val recorder = recorder(server)
            val output = printed { recorder.run(listOf(plan("A1"))) }

            val lines = output.lines().filter { it.isNotBlank() }
            assertTrue(output, lines.all { it.startsWith("LIVE_CAPTURE ") || it.startsWith(MANIFEST_PREFIX) })
            assertEquals(recorder.requests, lines.count { it.contains(" conv=") })
            val forbidden = listOf(
                LOOPBACK_KEY,
                "toolu_01Real",
                "Starting.",
                "All done.",
                THINKING_TEXT,
                SIGNATURE,
                "apple",
                "kiwi",
                "x-api-key",
                "Bearer",
            )
            forbidden.forEach { assertFalse("printed $it", output.contains(it)) }
            val line = lines.first { it.contains(" conv=A1 turn=1 ") }
            assertTrue(line, line.contains("status=200"))
            assertTrue(line, line.contains("stop=tool_use"))
            assertTrue(line, line.contains("tool_calls=2"))
            assertTrue(line, line.contains("thinking=present"))
            assertTrue(line, line.contains("reasoning_details=absent"))
            assertTrue(line, line.contains("usage=in:1530,cache_read:0,cache_write:0,out:140"))
            assertNull(lines.firstOrNull { it.startsWith("LIVE_CAPTURE requests=") })
        }
    }
}
