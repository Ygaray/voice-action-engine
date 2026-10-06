package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendConfig
import io.github.ygaray.voiceactionengine.spike.backend.BackendFailure
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import io.github.ygaray.voiceactionengine.spike.backend.BenchFacts
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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


/** Everything the recording backends saw. */
internal class BackendLog {
    val configs = ArrayList<BackendConfig>()
    val requests = ArrayList<Pair<BackendConfig, BackendRequest>>()
    var closed = 0
}

/** A backend that records every initialize and generate, fails an init for chosen configs and answers from a function. */
internal class RecordingBackend(
    private val log: BackendLog,
    private val initCode: (BackendConfig) -> String? = { null },
    private val answer: (BackendRequest, BackendConfig) -> BackendAnswer,
) : LlmBackend {
    private var config: BackendConfig? = null

    override suspend fun initialize(config: BackendConfig): InitOutcome {
        log.configs += config
        val code = initCode(config)
        if (code == null) this.config = config
        return InitOutcome(code, 5L)
    }

    override suspend fun generate(request: BackendRequest): BackendAnswer {
        val current = config ?: throw BackendFailure("init_failed")
        log.requests += current to request
        return answer(request, current)
    }

    override fun close() {
        log.closed++
    }
}

internal fun answerOf(text: String, prefillTokens: Int? = 100, ttftMs: Double? = 50.0, tps: Double? = 1000.0): BackendAnswer =
    BackendAnswer(text, emptyList(), BenchFacts(prefillTokens, 3, ttftMs, tps, 20.0))

/** A value that satisfies [schema] (first enum value, the minimum, the first anyOf branch), for a backend that obeys it. */
internal fun sampleFor(schema: JsonObject): JsonElement {
    schema["enum"]?.let { return it.jsonArray.first() }
    schema["anyOf"]?.let { return sampleFor(it.jsonArray.first().jsonObject) }
    return when ((schema["type"] as? JsonPrimitive)?.content) {
        "integer" -> JsonPrimitive((schema["minimum"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 3L)
        "array" -> JsonArray(listOf(JsonPrimitive("a")))
        "object" -> JsonObject(
            (schema["properties"] as? JsonObject).orEmpty().mapValues { sampleFor(it.value.jsonObject) },
        )
        else -> JsonPrimitive("a")
    }
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

/** The validity-obeying text a constrained-decoding backend would return for [request]. */
internal fun obeying(request: BackendRequest): String =
    Json.encodeToString(JsonElement.serializer(), sampleFor((request.mode as BackendMode.Constrained).schema))

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

    // ---- engine stages (Task 2) ------------------------------------------------------------------------------------

    private fun engineRig(
        log: BackendLog,
        initCode: (BackendConfig) -> String? = { null },
        answer: (BackendRequest, BackendConfig) -> BackendAnswer = { _, _ -> answerOf("ok") },
        sbState: () -> SbState = { SbState.Absent },
    ): LadderRig {
        val rig = LadderRig(tmp.root, backend = { RecordingBackend(log, initCode, answer) }, sbState = sbState)
        rig.placeModel(ModelFile.E2B_GENERIC)
        rig.placeModel(ModelFile.E2B_GPU)
        return rig
    }

    @Test
    fun initEmitsOneInitLinePerModelBackendAndAGpuLineAndMemAndThermal() = runTest {
        val log = BackendLog()
        val rig = engineRig(log)

        rig.ladder.run(Stage.INIT)

        val inits = rig.sink.of(Stage.INIT, SpikeKind.INIT)
        assertEquals(listOf("cpu:generic:process", "gpu:gpu:first_gpu", "gpu:gpu:warm_cache"), inits.map { "${it["backend"]}:${it["file"]}:${it["cold"]}" })
        assertTrue(inits.all { it["model"] == "e2b" && it["result"] == "ok" && it["code"] == "none" })
        assertEquals("gpu_ok", rig.sink.of(Stage.INIT, SpikeKind.GPU).single()["disposition"])
        val mem = rig.sink.of(Stage.INIT, SpikeKind.MEM).single()
        assertEquals("250", mem["interval_ms"])
        assertEquals("1500", mem["peak_pss_mb"])
        assertEquals(listOf("start", "end"), rig.sink.of(Stage.INIT, SpikeKind.THERMAL).map { it["point"] })
        assertEquals("done", rig.sink.stageLine(Stage.INIT)["result"])
        assertEquals("ok", rig.state.get("init_e2b_cpu"))
        assertEquals("ok", rig.state.get("init_e2b_gpu"))
        assertTrue(log.configs.filter { it.gpu }.all { it.cacheDir != null && it.maxNumTokens == 4096 })
        assertAllParse(rig.sink)
    }

    @Test
    fun aFailedGpuInitIsRecordedWithItsCodeAndTheGenericFileIsTriedOnTheGpu() = runTest {
        val log = BackendLog()
        val rig = engineRig(log, initCode = { if (it.gpu) "gpu_init_failed" else null })

        rig.ladder.run(Stage.INIT)

        val gpu = rig.sink.of(Stage.INIT, SpikeKind.INIT).filter { it["backend"] == "gpu" }
        assertEquals(listOf("gpu", "generic"), gpu.map { it["file"] })
        assertTrue(gpu.all { it["result"] == "failed" && it["code"] == "gpu_init_failed" })
        val line = rig.sink.of(Stage.INIT, SpikeKind.GPU).single()
        assertEquals("gpu_init_failed", line["disposition"])
        assertEquals("gpu_init_failed", line["code"])
        assertEquals("done", rig.sink.stageLine(Stage.INIT)["result"])
        assertEquals("gpu_init_failed", rig.state.get("init_e2b_gpu"))
    }

    @Test
    fun e2bFailingOnEveryBackendEndsInitWithAnEarlyExit() = runTest {
        val rig = engineRig(BackendLog(), initCode = { "init_failed" })

        rig.ladder.run(Stage.INIT)

        val line = rig.sink.stageLine(Stage.INIT)
        assertEquals("early_exit", line["result"])
        assertEquals("init_failed_all", line["reason"])
    }

    @Test
    fun aPlacedGemma3OneBillionIsInitializedOnTheCpuAndItsAbsenceIsNotAnError() = runTest {
        val log = BackendLog()
        val rig = engineRig(log)
        rig.placeModel(ModelFile.G3_1B)

        rig.ladder.run(Stage.INIT)

        val inits = rig.sink.of(Stage.INIT, SpikeKind.INIT)
        assertEquals(1, inits.count { it["model"] == "g3_1b" && it["backend"] == "cpu" })
        assertEquals("ok", rig.state.get("init_g3_1b_cpu"))
    }

    @Test
    fun prefillMeasuresThreeSizesPerInitializedBackendWithTheSbContext() = runTest {
        val log = BackendLog()
        val rig = engineRig(log, answer = { request, config ->
            val tokens = request.system.length / 4
            val tps = if (config.gpu) 2000.0 else 500.0
            answerOf("ok", tokens, tokens / tps * 1000.0, tps)
        })
        rig.state.put("init_e2b_cpu", "ok")
        rig.state.put("init_e2b_gpu", "ok")

        rig.ladder.run(Stage.PREFILL)

        val lines = rig.sink.of(Stage.PREFILL, SpikeKind.PREFILL)
        assertEquals(6, lines.size)
        assertEquals(listOf("1000", "4000", "7000"), lines.filter { it["backend"] == "cpu" }.map { it["target_tokens"] })
        assertEquals(listOf("1000", "4000", "7000"), lines.filter { it["backend"] == "gpu" }.map { it["target_tokens"] })
        val seven = lines.first { it["backend"] == "cpu" && it["target_tokens"] == "7000" }
        assertEquals("500.0", seven["prefill_tps"])
        assertEquals("14000", seven["ttft_ms"])
        assertTrue(log.requests.all { (config, request) -> config.maxNumTokens == 8192 && !request.constraintOn && request.maxOutputTokens == 8 })
        assertTrue(log.requests.none { (_, request) -> request.system.contains(CANARY) })
        assertTrue(rig.sink.of(Stage.PREFILL, SpikeKind.GPU).isEmpty())
        assertEquals("250", rig.sink.of(Stage.PREFILL, SpikeKind.MEM).single()["interval_ms"])
        assertEquals("done", rig.sink.stageLine(Stage.PREFILL)["result"])
    }

    @Test
    fun aGpuThatPrefillsSlowerThanTheCpuIsRefinedToGpuSlower() = runTest {
        val rig = engineRig(BackendLog(), answer = { request, config ->
            val tokens = request.system.length / 4
            val tps = if (config.gpu) 300.0 else 500.0
            answerOf("ok", tokens, tokens / tps * 1000.0, tps)
        })
        rig.state.put("init_e2b_cpu", "ok")
        rig.state.put("init_e2b_gpu", "ok")

        rig.ladder.run(Stage.PREFILL)

        assertEquals("gpu_slower", rig.sink.of(Stage.PREFILL, SpikeKind.GPU).single()["disposition"])
    }

    @Test
    fun prefillSkipsABackendWhoseInitFailed() = runTest {
        val log = BackendLog()
        val rig = engineRig(log)
        rig.state.put("init_e2b_cpu", "ok")
        rig.state.put("init_e2b_gpu", "gpu_init_failed")

        rig.ladder.run(Stage.PREFILL)

        assertEquals(3, rig.sink.of(Stage.PREFILL, SpikeKind.PREFILL).size)
        assertTrue(log.configs.none { it.gpu })
    }

    @Test
    fun everyEngineStageAfterInitExitsEarlyWhenE2bFailedEverywhere() = runTest {
        val rig = engineRig(BackendLog())
        rig.state.put("init_e2b_cpu", "init_failed")
        rig.state.put("init_e2b_gpu", "gpu_init_failed")

        for (stage in listOf(Stage.PREFILL, Stage.KV_REUSE, Stage.RF_MATRIX)) {
            rig.ladder.run(stage)
            val line = rig.sink.stageLine(stage)
            assertEquals("early_exit", line["result"])
            assertEquals("init_failed_all", line["reason"])
        }
    }

    private fun kvAnswer(reused: Boolean): (BackendRequest, BackendConfig) -> BackendAnswer {
        val seen = HashMap<Boolean, Int>()
        return { _, config ->
            val n = seen.merge(config.prefillPrefaceOnInit, 1, Int::plus)!!
            if (n == 1 || !reused) answerOf("ok", 7000, 7800.0, 900.0) else answerOf("ok", 300, 900.0, 900.0)
        }
    }

    @Test
    fun kvReuseComparesASecondConversationWithAndWithoutPrefillOnInit() = runTest {
        val log = BackendLog()
        val rig = engineRig(log, answer = kvAnswer(reused = true))
        rig.state.put("init_e2b_cpu", "ok")

        rig.ladder.run(Stage.KV_REUSE)

        val lines = rig.sink.of(Stage.KV_REUSE, SpikeKind.KVREUSE)
        assertEquals(listOf("0", "1"), lines.map { it["preface_on_init"] })
        assertTrue(lines.all { it["preface"] == "padded_small" && it["disposition"] == "reused" })
        assertEquals("7000", lines.first()["first_prefill_tokens"])
        assertEquals("300", lines.first()["second_prefill_tokens"])
        assertEquals(listOf(false, true), log.configs.map { it.prefillPrefaceOnInit })
        assertEquals(4, log.requests.size)
        assertEquals("done", rig.sink.stageLine(Stage.KV_REUSE)["result"])
    }

    @Test
    fun kvReuseReportsNotReusedWhenTheSecondCallCostsTheSame() = runTest {
        val rig = engineRig(BackendLog(), answer = kvAnswer(reused = false))
        rig.state.put("init_e2b_cpu", "ok")

        rig.ladder.run(Stage.KV_REUSE)

        assertTrue(rig.sink.of(Stage.KV_REUSE, SpikeKind.KVREUSE).all { it["disposition"] == "not_reused" })
    }

    // ---- rf_matrix ------------------------------------------------------------------------------------------------

    private fun rfAnswer(onValid: Boolean, offValid: Boolean, flagNeeded: Boolean = false): (BackendRequest, BackendConfig) -> BackendAnswer =
        { request, _ ->
            val valid = when {
                !request.constraintOn -> offValid
                // A constraint passed without the engine flag is ignored when the flag is needed.
                request.engineFlag == false && flagNeeded -> false
                else -> onValid
            }
            answerOf(if (valid) obeying(request) else "no json here")
        }

    private fun probeLines(rig: LadderRig) = rig.sink.of(Stage.RF_MATRIX, SpikeKind.SCHEMAPROBE)

    @Test
    fun rfMatrixProbesEightFeaturesOnAndOffAndMarksEnforcement() = runTest {
        val log = BackendLog()
        val rig = engineRig(log, answer = rfAnswer(onValid = true, offValid = false))
        rig.state.put("init_e2b_cpu", "ok")

        rig.ladder.run(Stage.RF_MATRIX)

        val features = listOf("flat", "required", "enum", "nested", "array", "anyof", "bounds", "addl_false")
        val lines = probeLines(rig)
        for (feature in features) {
            val on = lines.single { it["feature"] == feature && it["constraint"] == "on" }
            val off = lines.single { it["feature"] == feature && it["constraint"] == "off" }
            assertEquals(feature, "5", on["trials"])
            assertEquals(feature, "0", on["invalid"])
            assertEquals(feature, "enforced", on["disposition"])
            assertEquals(feature, "5", off["invalid"])
            assertNull(feature, off["disposition"])
        }
        // 8 features x (ON + OFF) x 5 prompts, plus the 5-prompt flat arm without the engine flag.
        assertEquals(85, log.requests.size)
        assertEquals(5, log.requests.count { (_, request) -> request.engineFlag == false })
        assertEquals("done", rig.sink.stageLine(Stage.RF_MATRIX)["result"])
        assertAllParse(rig.sink)
    }

    @Test
    fun rfMatrixCallsAnIgnoredConstraintIgnoredAndAnUnprovenOneUnproven() = runTest {
        val ignored = engineRig(BackendLog(), answer = rfAnswer(onValid = false, offValid = false))
        ignored.state.put("init_e2b_cpu", "ok")
        ignored.ladder.run(Stage.RF_MATRIX)
        assertTrue(probeLines(ignored).filter { it["constraint"] == "on" }.all { it["disposition"] == "ignored" })

        val unproven = engineRig(BackendLog(), answer = rfAnswer(onValid = true, offValid = true))
        unproven.state.put("init_e2b_cpu", "ok")
        unproven.ladder.run(Stage.RF_MATRIX)
        assertTrue(probeLines(unproven).filter { it["constraint"] == "on" }.all { it["disposition"] == "unproven" })
    }

    @Test
    fun aNativeExceptionOnTheOnArmIsNativeErrorAndTheStageStillFinishes() = runTest {
        val rig = engineRig(BackendLog(), answer = { request, _ ->
            if (request.constraintOn) throw BackendFailure("native_error")
            answerOf("no json here")
        })
        rig.state.put("init_e2b_cpu", "ok")

        rig.ladder.run(Stage.RF_MATRIX)

        val on = probeLines(rig).filter { it["constraint"] == "on" && it["disposition"] != null }
        assertEquals(8, on.size)
        assertTrue(on.all { it["disposition"] == "native_error" })
        assertEquals("done", rig.sink.stageLine(Stage.RF_MATRIX)["result"])
    }

    @Test
    fun rfMatrixRecordsWhetherTheEngineFlagWasNeeded() = runTest {
        val needed = engineRig(BackendLog(), answer = rfAnswer(onValid = true, offValid = false, flagNeeded = true))
        needed.state.put("init_e2b_cpu", "ok")
        needed.ladder.run(Stage.RF_MATRIX)
        assertEquals("1", probeLines(needed).single { it["feature"] == "flat" && it["constraint"] == "on" }["constrained_flag"])

        val notNeeded = engineRig(BackendLog(), answer = rfAnswer(onValid = true, offValid = false, flagNeeded = false))
        notNeeded.state.put("init_e2b_cpu", "ok")
        notNeeded.ladder.run(Stage.RF_MATRIX)
        assertEquals("0", probeLines(notNeeded).single { it["feature"] == "flat" && it["constraint"] == "on" }["constrained_flag"])
    }
}
