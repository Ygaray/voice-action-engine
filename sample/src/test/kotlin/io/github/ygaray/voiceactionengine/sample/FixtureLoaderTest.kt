package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.sample.fixture.FIXTURE_SHA256
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureLoader
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureSource
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.fixture.NamedFixtureSource
import io.github.ygaray.voiceactionengine.sample.fixture.ToolClassifier
import io.github.ygaray.voiceactionengine.sample.tools.CANNED_READ
import io.github.ygaray.voiceactionengine.sample.tools.CannedToolExecutor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

private const val SYNTHETIC_RESOURCE = "synthetic-fixture.json"
private const val FILES_LABEL = "files"
private const val ASSET_LABEL = "asset"
private const val FINAL_REPLY = "two items"

/** Loading a fixture and driving the engine with it, on the host, using the committed synthetic fixture only. */
class FixtureLoaderTest {

    private val selection = ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5")
    private val policy = TierPolicy { maxIterations = 3 }

    private fun syntheticBytes(): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(SYNTHETIC_RESOURCE)) { "missing test resource" }
            .use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun source(label: String, bytes: ByteArray?): NamedFixtureSource =
        NamedFixtureSource(label, FixtureSource { bytes })

    private fun loadSynthetic(): FixtureState.Loaded {
        val bytes = syntheticBytes()
        val state = FixtureLoader(listOf(source(FILES_LABEL, bytes)), sha256(bytes)).load()
        assertTrue(state.toString(), state is FixtureState.Loaded)
        return state as FixtureState.Loaded
    }

    @Test
    fun aLoadedFixtureDrivesTheAgenticTierEndToEnd() = runTest {
        NoNetworkGuard.during {
            val state = loadSynthetic()
            val snapshot = ToolingSnapshot(state.system, state.tools, null)
            val executor = CannedToolExecutor(state.tools)
            val usage = Usage(0, 0, 0, 1)
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.toolCall("call_1", "find_items", buildJsonObject { put("query", "x") }, usage),
                FakeAiProvider.reply(FINAL_REPLY, usage),
            )
            val engine = SampleEngine(
                listOf(fake),
                ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "sk-canary-key-do-not-print"),
                RecordingCommitSink(),
                null,
            )
            val tier = AgenticLoopStrategy(StrategyId("agentic")) {
                tooling = ToolSpecProvider.fixed(snapshot)
                this.executor = executor
            }

            val outcome = engine.pipeline(tier, selection, policy).execute(CommandInput("find my items"))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, executor.readCalls)
            assertEquals(2, fake.callCount)
            val replayed = fake.calls[1].request.messages.last()
            assertTrue(replayed.toString(), replayed is ToolResultsMessage)
            val results = (replayed as ToolResultsMessage).results
            assertEquals(1, results.size)
            assertEquals(CANNED_READ, results.single().content)
        }
    }

    @Test
    fun theLoadedStateCountsWhatTheFileHolds() {
        val state = loadSynthetic()
        val raw = Json.parseToJsonElement(syntheticBytes().toString(Charsets.UTF_8)).jsonObject
        val tools: JsonArray = raw.getValue("tools").jsonArray
        assertEquals(tools.size, state.tools.size)
        assertEquals(tools.toString().length + raw.getValue("system").jsonPrimitive.content.length, state.prefixChars)
        assertEquals(syntheticBytes().size, state.byteCount)
        assertEquals(FILES_LABEL, state.source)
        assertTrue(state.tools.all { it.inputSchema is JsonObject && it.strict == null && !it.terminal })
    }

    @Test
    fun theClassifierFollowsThePrefixRule() {
        listOf("get_a", "list_a", "find_a", "search_a").forEach { assertFalse(it, ToolClassifier.isMutating(it)) }
        listOf("create_a", "edit_a", "delete_a", "getaway", "x_get_a").forEach {
            assertTrue(it, ToolClassifier.isMutating(it))
        }
    }

    private fun loaderFor(files: ByteArray?, asset: ByteArray?, expected: String): FixtureLoader =
        FixtureLoader(listOf(source(FILES_LABEL, files), source(ASSET_LABEL, asset)), expected)

    private fun text(json: String): ByteArray = json.toByteArray(Charsets.UTF_8)

    @Test
    fun absentWhenNoSourceHasBytes() {
        val state = loaderFor(null, null, sha256(syntheticBytes())).load()
        assertTrue(state.toString(), state is FixtureState.Absent)
        assertEquals(listOf(FILES_LABEL, ASSET_LABEL), (state as FixtureState.Absent).searched)
    }

    @Test
    fun aWrongDigestIsLoudAndDoesNotFallThrough() {
        val good = syntheticBytes()
        val state = loaderFor(text("not the fixture"), good, sha256(good)).load()
        assertTrue(state.toString(), state is FixtureState.ShaMismatch)
        val mismatch = state as FixtureState.ShaMismatch
        assertEquals(FILES_LABEL, mismatch.source)
        assertEquals(sha256(text("not the fixture")).take(8), mismatch.actualPrefix)
        assertTrue(mismatch.actualPrefix.matches(Regex("[0-9a-f]{8}")))
    }

    @Test
    fun theFilesSourceWinsOverTheAsset() {
        val good = syntheticBytes()
        val state = loaderFor(good, good, sha256(good)).load()
        assertTrue(state.toString(), state is FixtureState.Loaded)
        assertEquals(FILES_LABEL, (state as FixtureState.Loaded).source)
    }

    @Test
    fun malformedJsonIsTyped() = assertMalformed("{ this is not json", "not_json")

    @Test
    fun aJsonRootThatIsNotAnObjectIsNotJson() = assertMalformed("[1, 2]", "not_json")

    @Test
    fun missingSystemIsTyped() = assertMalformed("""{"tools": []}""", "missing_system")

    @Test
    fun missingToolsIsTyped() = assertMalformed("""{"system": "s"}""", "missing_tools")

    @Test
    fun aToolWithoutAnObjectSchemaIsTyped() = assertMalformed(
        """{"system": "s", "tools": [{"name": "find_x", "description": "d", "input_schema": "nope"}]}""",
        "bad_tool",
    )

    private fun assertMalformed(json: String, code: String) {
        val bytes = text(json)
        val state = loaderFor(bytes, null, sha256(bytes)).load()
        assertTrue(state.toString(), state is FixtureState.Malformed)
        assertEquals(code, (state as FixtureState.Malformed).code)
        assertEquals(FILES_LABEL, state.source)
    }

    @Test
    fun theRealConstantIsTheFullDigest() {
        assertEquals(64, FIXTURE_SHA256.length)
        assertTrue(FIXTURE_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(FIXTURE_SHA256.startsWith("ebd3ef4a"))
        assertTrue(FIXTURE_SHA256.endsWith("af4ed3e"))
        val state = FixtureLoader(listOf(source(FILES_LABEL, syntheticBytes()))).load()
        assertTrue(state.toString(), state is FixtureState.ShaMismatch)
    }

    @Test
    fun noStateCarriesFixtureContent() {
        val canarySystem = "CANARY_SYSTEM_TEXT"
        val canaryTool = "CANARY_TOOL_TEXT"
        val good = buildJsonObject {
            put("system", canarySystem)
            put(
                "tools",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("name", "find_x")
                            put("description", canaryTool)
                            put("input_schema", buildJsonObject { put("type", "object") })
                        },
                    ),
                ),
            )
        }.toString().toByteArray(Charsets.UTF_8)
        val broken = text("CANARY_SYSTEM_TEXT CANARY_TOOL_TEXT { not json")
        val states = listOf(
            loaderFor(good, null, sha256(good)).load(),
            loaderFor(good, null, "0".repeat(64)).load(),
            loaderFor(broken, null, sha256(broken)).load(),
            loaderFor(null, null, sha256(good)).load(),
        )
        assertTrue(states[0] is FixtureState.Loaded)
        states.forEach { state ->
            assertFalse(state.toString(), state.toString().contains(canarySystem))
            assertFalse(state.toString(), state.toString().contains(canaryTool))
        }
        (states[0] as FixtureState.Loaded).tools.forEach { assertFalse(it.toString().contains(canaryTool)) }
    }
}
