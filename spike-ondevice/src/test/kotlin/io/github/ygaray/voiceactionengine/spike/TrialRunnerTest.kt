package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
import io.github.ygaray.voiceactionengine.spike.envelope.EnvelopeSnapshot
import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord
import io.github.ygaray.voiceactionengine.spike.gate.SpikeOnDeviceCapability
import io.github.ygaray.voiceactionengine.spike.gold.GoldItem
import io.github.ygaray.voiceactionengine.spike.trial.TrialRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val CREATE = "create_item"
private const val FIND = "find_items"
private const val ITEM_POS = "s_en_001"
private const val ITEM_NEG = "s_neg_001"
private const val CANARY = "canary-transcript-text-zq91"

/** One gold item through the real engine path (SingleShot, router, gate, spike provider) over a scripted backend. */
class TrialRunnerTest {
    private val cell = Cell(ModelKey.E2B, BackendKind.CPU, Route.A, Shape.AUTO)

    private fun schema(required: String, vararg more: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject(required) { put("type", "string") }
            more.forEach { putJsonObject(it) { put("type", "string") } }
        }
        putJsonArray("required") { add(JsonPrimitive(required)) }
        put("additionalProperties", false)
    }

    private val envelope = EnvelopeSnapshot(
        Envelope.SMALL,
        "You manage a plain list of items.",
        listOf(
            ToolSpec(CREATE, "Creates an item.", schema("title", "body"), mutating = true),
            ToolSpec(FIND, "Finds items.", schema("query")),
        ),
    )

    private val capability = SpikeOnDeviceCapability({ true }, { listOf("arm64-v8a") }, { InitOutcome(null, 1L) })

    private fun positive() = GoldItem(
        ITEM_POS, Lang.EN, ItemKind.POS, CANARY, CREATE, buildJsonObject { put("title", "milk") }, false,
    )

    private fun negative() = GoldItem(ITEM_NEG, Lang.EN, ItemKind.NEG, CANARY, null, JsonObject(emptyMap()), false)

    private fun wrapper(tool: String, arguments: String): FakeLlmBackend.Step =
        FakeLlmBackend.text("""{"tool":"$tool","arguments":$arguments}""")

    private fun runner(backend: FakeLlmBackend): TrialRunner =
        TrialRunner({ backend }, capability, Dispatchers.Unconfined, fakeClock())

    // Advances 7 ms per read, so latency is a deterministic 7 ms.
    private fun fakeClock(): () -> Long {
        var now = 0L
        return { now.also { now += 7_000_000L } }
    }

    @Test
    fun aCorrectPositiveItemScoresAllOnesAndNoFalseWrite() = runTest {
        val backend = FakeLlmBackend(wrapper(CREATE, """{"title":"Milk"}"""))

        val record = runner(backend).run(cell, envelope, positive(), firstInProcess = true)

        assertTrue(record.schemaValid)
        assertTrue(record.toolMatch)
        assertTrue(record.argsMatch)
        assertFalse(record.falseWrite)
        assertEquals("ok", record.outcome)
        assertEquals(7L, record.latencyMs)
        assertEquals(80L, record.ttftMs)
        assertEquals(120, record.prefillTokens)
        assertEquals(9, record.decodeTokens)
        assertTrue(record.firstInProcess)
        assertEquals(1, backend.calls)
    }

    @Test
    fun aNegativeItemThatCallsAMutatingToolIsAFalseWrite() = runTest {
        val backend = FakeLlmBackend(wrapper(CREATE, """{"title":"milk"}"""))

        val record = runner(backend).run(cell, envelope, negative(), firstInProcess = false)

        assertTrue(record.falseWrite)
        assertFalse(record.toolMatch)
        assertFalse(record.argsMatch)
        assertFalse(record.firstInProcess)
    }

    @Test
    fun aNegativeItemThatDeclinesIsCorrectAndNeverAFalseWrite() = runTest {
        val backend = FakeLlmBackend(wrapper("none", "{}"))

        val record = runner(backend).run(cell, envelope, negative(), firstInProcess = false)

        assertTrue(record.schemaValid)
        assertTrue(record.toolMatch)
        assertTrue(record.argsMatch)
        assertFalse(record.falseWrite)
    }

    @Test
    fun aNegativeItemThatCallsAReadToolIsCorrect() = runTest {
        val backend = FakeLlmBackend(wrapper(FIND, """{"query":"milk"}"""))

        val record = runner(backend).run(cell, envelope, negative(), firstInProcess = false)

        assertTrue(record.toolMatch)
        assertFalse(record.falseWrite)
    }

    @Test
    fun aMalformedAnswerIsAnInvalidCountedFailureWithItsStableCode() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.text("not json at all"))

        val record = runner(backend).run(cell, envelope, positive(), firstInProcess = false)

        assertFalse(record.schemaValid)
        assertFalse(record.toolMatch)
        assertEquals("malformed_output", record.outcome)
    }

    @Test
    fun aWrongToolIsAMissOnAPositiveItem() = runTest {
        val backend = FakeLlmBackend(wrapper(FIND, """{"query":"milk"}"""))

        val record = runner(backend).run(cell, envelope, positive(), firstInProcess = false)

        assertTrue(record.schemaValid)
        assertFalse(record.toolMatch)
        assertFalse(record.argsMatch)
    }

    @Test
    fun anExceptionFromTheBackendIsANativeErrorRowNeverAThrow() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.Step.Boom(IllegalStateException(CANARY)))

        val record = runner(backend).run(cell, envelope, positive(), firstInProcess = false)

        assertFalse(record.schemaValid)
        assertEquals("native_error", record.outcome)
        assertFalse(record.toLine().render().contains("canary"))
    }

    @Test
    fun everyRenderedLineParsesBackAndKeepsTheOpaqueItemId() = runTest {
        val backend = FakeLlmBackend(wrapper(CREATE, """{"title":"milk"}"""), wrapper(CREATE, """{"title":"milk"}"""))
        val trials = runner(backend)

        val records = listOf(
            trials.run(cell, envelope, positive(), firstInProcess = true),
            trials.run(cell, envelope, negative(), firstInProcess = false),
        )

        for (record in records) {
            val line = record.toLine()
            val parsed = SpikeLine.parse(line.render())
            assertNotNull(line.render(), parsed)
            val back = TrialRecord.fromLine(parsed!!)
            assertEquals(record, back)
            assertFalse(line.render().contains("canary"))
        }
        // The host filter check in the plan reads this file: exactly the two rendered lines.
        val out = File("build/spike-trial-lines.txt")
        out.parentFile.mkdirs()
        out.writeText(records.joinToString("\n", postfix = "\n") { it.toLine().render() })
    }
}
