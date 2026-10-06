package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend
import io.github.ygaray.voiceactionengine.spike.envelope.SbState
import io.github.ygaray.voiceactionengine.spike.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.spike.evidence.FileEvidenceSink
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.gold.GoldItem
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.ladder.AppFiles
import io.github.ygaray.voiceactionengine.spike.ladder.DeviceFacts
import io.github.ygaray.voiceactionengine.spike.ladder.Ladder
import io.github.ygaray.voiceactionengine.spike.ladder.LadderEnv
import io.github.ygaray.voiceactionengine.spike.ladder.ModelFile
import io.github.ygaray.voiceactionengine.spike.ladder.Probes
import io.github.ygaray.voiceactionengine.spike.ladder.StateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val CANARY = "canary-ladder-text-q7x2"

/** An in-memory [EvidenceSink]: every emitted line per stage, in order. */
internal class MemorySink : EvidenceSink {
    val emitted = LinkedHashMap<Stage, MutableList<SpikeLine>>()

    override fun emit(stage: Stage, line: SpikeLine) {
        emitted.getOrPut(stage) { ArrayList() }.add(line)
    }

    override fun lines(stage: Stage): List<SpikeLine> = emitted[stage].orEmpty().toList()

    /** Every line of [stage] of [kind]. */
    fun of(stage: Stage, kind: SpikeKind): List<SpikeLine> = lines(stage).filter { it.kind == kind }

    /** The single STAGE line of [stage]; fails the test when there is not exactly one. */
    fun stageLine(stage: Stage): SpikeLine {
        val stages = of(stage, SpikeKind.STAGE)
        assertEquals("exactly one STAGE line for ${stage.wire}", 1, stages.size)
        return stages.single()
    }
}

/** Fake [Probes]: a controllable clock that [pause] advances, a thermal status and a PSS reading. */
internal class FakeProbes(var thermal: String = "none", var pss: Int = 1500, private val tickMs: Long = 0L) : Probes {
    var clockMs: Long = 0L
    val exits = ArrayList<String>()
    val thermalScript = ArrayDeque<String>()

    override fun pssMb(): Int = pss

    override fun thermalStatus(): String = thermalScript.removeFirstOrNull() ?: thermal

    override fun exitReasons(sinceEpochMs: Long): List<String> = exits.toList()

    override fun deviceFacts(): DeviceFacts = DeviceFacts(7500, 3000, 20000, listOf("arm64-v8a"), 4096, true, 90)

    override fun monotonicMs(): Long = clockMs.also { clockMs += tickMs }

    override fun epochMs(): Long = 1_700_000_000_000L

    override suspend fun pause(ms: Long) {
        clockMs += ms
        yield()
    }
}

/** A small gold set built in memory (the loader's minima are not under test here). */
internal fun smallGoldOf(count: Int): GoldSet = GoldSet(
    Envelope.SMALL,
    null,
    List(count) { i ->
        GoldItem(
            "s_en_%03d".format(i + 1),
            Lang.EN,
            ItemKind.POS,
            "$CANARY $i",
            "create_item",
            buildJsonObject { put("title", "milk") },
            false,
        )
    },
)

private fun createAnswer(): FakeLlmBackend.Step =
    FakeLlmBackend.text("""{"tool":"create_item","arguments":{"title":"Milk"}}""")

/** Builds a ladder over fakes in a temporary directory. */
internal class LadderRig(
    private val root: File,
    val sink: MemorySink = MemorySink(),
    val probes: FakeProbes = FakeProbes(),
    backend: () -> LlmBackend = { FakeLlmBackend(createAnswer(), createAnswer(), createAnswer()) },
    smallGold: () -> GoldSet = { smallGoldOf(3) },
    sbState: () -> SbState = { SbState.Absent },
    screenN: Int = 3,
) {
    val files = AppFiles(File(root, "files"), File(root, "ext"))
    val state = StateStore(files.state)
    val ladder = Ladder(
        LadderEnv(sink, state, probes, backend, files, smallGold, sbState, Dispatchers.Unconfined, screenN),
    )

    /** Puts a (dummy) model file where the ladder looks for it. */
    fun placeModel(file: ModelFile) {
        files.ensure()
        File(File(root, "ext"), file.fileName).writeText("x")
    }
}

class LadderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun assertAllParse(sink: MemorySink) {
        for ((_, lines) in sink.emitted) {
            for (line in lines) assertNotNull("not a grammar line: ${line.render()}", SpikeLine.parse(line.render()))
        }
    }

    @Test
    fun prepareThenPreflightThenMinimalScreenEndsEachStageWithOneStageLine() = runTest {
        val rig = LadderRig(tmp.root)

        rig.ladder.run(Stage.PREPARE)
        rig.placeModel(ModelFile.E2B_GENERIC)
        rig.ladder.run(Stage.PREFLIGHT)
        rig.ladder.run(Stage.SCREEN_SMALL)

        val prepare = rig.sink.stageLine(Stage.PREPARE)
        assertEquals("done", prepare["result"])
        assertEquals("1700000000000", rig.state.get(StateStore.PREPARE_EPOCH_MS))
        assertTrue(rig.files.evidence.isDirectory && rig.files.privateDir.isDirectory && rig.files.state.isDirectory)

        assertEquals("done", rig.sink.stageLine(Stage.PREFLIGHT)["result"])
        val facts = rig.sink.of(Stage.PREFLIGHT, SpikeKind.PREFLIGHT).single()
        assertEquals("app", facts["source"])
        assertEquals("arm64-v8a", facts["abi"])
        assertEquals("absent", facts["sb_state"])
        val models = rig.sink.of(Stage.PREFLIGHT, SpikeKind.MODEL)
        assertEquals(ModelFile.entries.size, models.size)
        assertEquals("1", models.first { it["model"] == "e2b" && it["file"] == "generic" }["present"])
        assertEquals("0", models.first { it["model"] == "g3_1b" }["present"])

        val trials = rig.sink.of(Stage.SCREEN_SMALL, SpikeKind.TRIAL)
        assertEquals(3, trials.size)
        assertEquals(listOf("1", "0", "0"), trials.map { it["first_in_process"] })
        val screen = rig.sink.stageLine(Stage.SCREEN_SMALL)
        assertEquals("done", screen["result"])
        assertEquals("3", screen["trials"])
        assertEquals("3", screen["planned"])
        assertAllParse(rig.sink)
    }

    @Test
    fun aStageThatThrowsEndsWithOneErrorLineAndNoMessageText() = runTest {
        val rig = LadderRig(tmp.root, smallGold = { throw IllegalStateException(CANARY) })
        rig.ladder.run(Stage.PREPARE)
        rig.placeModel(ModelFile.E2B_GENERIC)

        rig.ladder.run(Stage.SCREEN_SMALL)

        val line = rig.sink.stageLine(Stage.SCREEN_SMALL)
        assertEquals("error", line["result"])
        assertEquals("harness_illegalstateexception", line["reason"])
        assertFalse(rig.sink.emitted.values.flatten().any { CANARY in it.render() })
        assertAllParse(rig.sink)
    }

    @Test
    fun aMissingModelIsAStableErrorCodeNotAThrow() = runTest {
        val rig = LadderRig(tmp.root)
        rig.ladder.run(Stage.PREPARE)

        rig.ladder.run(Stage.SCREEN_SMALL)

        val line = rig.sink.stageLine(Stage.SCREEN_SMALL)
        assertEquals("error", line["result"])
        assertEquals("model_missing", line["reason"])
        assertEquals("3", line["planned"])
    }

    @Test
    fun theFileSinkAppendsOneLinePerEmitAndReadsThemBack() {
        val dir = File(tmp.root, "evidence")
        val echoed = ArrayList<String>()
        val first = FileEvidenceSink(dir) { echoed += it }
        val line = SpikeLine.of(SpikeKind.STAGE, "stage" to "init", "result" to "done", "trials" to "0", "planned" to "0")

        first.emit(Stage.INIT, line)
        first.close()
        val second = FileEvidenceSink(dir) { echoed += it }
        second.emit(Stage.INIT, line)

        val text = File(dir, "init.txt").readText()
        assertEquals(2, text.lines().filter { it.isNotEmpty() }.size)
        assertTrue(text.lines().first().startsWith("VAE_SPIKE_STAGE stage=init result=done"))
        assertEquals(2, second.lines(Stage.INIT).size)
        assertEquals(2, echoed.size)
        assertTrue(second.lines(Stage.PREFILL).isEmpty())
    }

    @Test
    fun theStateStoreRoundTripsAndNeverStoresAFreeSentence() {
        val store = StateStore(File(tmp.root, "state"))

        assertNull(store.get("winner_small"))
        store.put("winner_small", "e2b.gpu.a.auto")
        store.put("note", "free text with spaces and $CANARY")

        assertEquals("e2b.gpu.a.auto", store.get("winner_small"))
        assertEquals("invalid_token", store.get("note"))
        store.clear()
        assertNull(store.get("winner_small"))
    }
}
