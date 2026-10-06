package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendConfig
import io.github.ygaray.voiceactionengine.spike.backend.BackendFailure
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend
import io.github.ygaray.voiceactionengine.spike.envelope.SbState
import io.github.ygaray.voiceactionengine.spike.envelope.SmallEnvelope
import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.gate.SpikeOnDeviceCapability
import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

private const val NONE = "none"
private const val NA = "na"
private const val OK = "ok"
private const val FAILED = "failed"
private const val CHARS_PER_TOKEN = 4
private const val PREFILL_USER = "Say ok."
private const val PREFILL_MAX_OUTPUT = 8
private const val RF_MAX_OUTPUT = 128
private const val SB_PREFIX_TOKENS = 7000
private val PREFILL_TARGETS = listOf(1000, 4000, SB_PREFIX_TOKENS)

// Neutral filler: carries no instruction and no domain, sized by characters (about 4 per token).
private const val FILLER = "This sentence is neutral filler text and it carries no instruction at all. "

/** A neutral system text of about [tokens] tokens. */
internal fun fillerText(tokens: Int): String = buildString {
    val chars = tokens * CHARS_PER_TOKEN
    while (length < chars) append(FILLER)
}.take(tokens * CHARS_PER_TOKEN)

/** An engine opened for a stage; [backend] is null when the load failed (see [outcome]). */
internal class OpenedEngine(val backend: LlmBackend?, val outcome: InitOutcome, val peakPssMb: Int)

/** Opens engines for the stages: one at a time, wrapped in a PSS sampler, with the blocking load on the ladder's dispatcher. */
internal class EngineOpener(private val env: LadderEnv) {
    /** The model file of [model] on [backend]; the GPU variant is used only when the init stage found it works. */
    fun fileFor(model: ModelKey, backend: BackendKind): ModelFile = when {
        model == ModelKey.G3_1B -> ModelFile.G3_1B
        backend == BackendKind.CPU -> ModelFile.E2B_GENERIC
        env.state.get(GPU_FILE_KEY) == GENERIC_FILE -> ModelFile.E2B_GENERIC
        env.files.modelPath(ModelFile.E2B_GPU) != null -> ModelFile.E2B_GPU
        else -> ModelFile.E2B_GENERIC
    }

    /** Loads [file] with [maxTokens] of context; the GPU shares one compiled-kernel cache directory. */
    suspend fun open(file: ModelFile, gpu: Boolean, maxTokens: Int, preface: Boolean = false): OpenedEngine {
        val path = env.files.modelPath(file) ?: return OpenedEngine(null, InitOutcome(SpikeOnDeviceCapability.MODEL_MISSING, 0L), 0)
        val backend = env.backend()
        val config = BackendConfig(path, gpu, maxTokens, if (gpu) env.files.engineCache.absolutePath else null, preface)
        val sampler = PssSampler(env.probes)
        val outcome = sampler.around { withContext(env.dispatcher) { backend.initialize(config) } }
        if (!outcome.ok) {
            backend.close()
            return OpenedEngine(null, outcome, sampler.reading.peakMb)
        }
        return OpenedEngine(backend, outcome, sampler.reading.peakMb)
    }

    /** True when the init stage left no usable E2B backend: both recorded and neither `ok`. Absent state means untried. */
    fun e2bFailedEverywhere(): Boolean {
        val cpu = env.state.get(StateStore.initKey(ModelKey.E2B, BackendKind.CPU))
        val gpu = env.state.get(StateStore.initKey(ModelKey.E2B, BackendKind.GPU))
        return cpu != null && gpu != null && LadderRules.initEarlyExit(cpu == OK, gpu == OK) != null
    }

    /** The E2B backends worth measuring: not recorded as failed (CPU first). */
    fun e2bBackends(): List<BackendKind> = listOf(BackendKind.CPU, BackendKind.GPU).filter {
        val recorded = env.state.get(StateStore.initKey(ModelKey.E2B, it))
        recorded == null || recorded == OK
    }

    companion object {
        const val GPU_FILE_KEY = "gpu_file_e2b"
        const val GENERIC_FILE = "generic"
        const val GPU_FILE = "gpu"
    }
}

/**
 * The cheap, gating engine rows (13-RESEARCH ladder rows 2 to 5): init (CPU, GPU, first-ever GPU), prefill throughput,
 * KV reuse and the ResponseFormat feature matrix. Each runs one engine at a time (the model is memory-mapped, so two do not
 * fit), emits only closed-grammar lines, and is wrapped in a PSS sampler with a thermal reading at its start and end.
 */
internal class EngineStages(private val env: LadderEnv, private val opener: EngineOpener = EngineOpener(env)) {
    private suspend fun measured(stage: Stage, block: suspend () -> StageOutcome): StageOutcome {
        thermal(stage, "start")
        val sampler = PssSampler(env.probes)
        try {
            return sampler.around(block)
        } finally {
            val reading = sampler.reading
            env.sink.emit(
                stage,
                SpikeLine.of(
                    SpikeKind.MEM,
                    "stage" to stage.wire,
                    "peak_pss_mb" to reading.peakMb.toString(),
                    "interval_ms" to Thresholds.pssIntervalMs.toString(),
                    "samples" to reading.samples.toString(),
                ),
            )
            thermal(stage, "end")
        }
    }

    private fun thermal(stage: Stage, point: String) {
        env.sink.emit(
            stage,
            SpikeLine.of(SpikeKind.THERMAL, "stage" to stage.wire, "status" to env.probes.thermalStatus(), "point" to point),
        )
    }

    // ---- init -----------------------------------------------------------------------------------------------------

    private var firstInit = true
    private var lastCode: String = NONE

    /** Initializes E2B on the CPU and the GPU (first-ever and repeat) and Gemma 3 1B on the CPU when it is present. */
    suspend fun init(): StageOutcome = measured(Stage.INIT) {
        firstInit = true
        val cpuOk = attempt(ModelKey.E2B, BackendKind.CPU, ModelFile.E2B_GENERIC)
        env.state.put(StateStore.initKey(ModelKey.E2B, BackendKind.CPU), if (cpuOk) OK else lastCode)
        val gpuOk = initGpu()
        if (env.files.modelPath(ModelFile.G3_1B) != null) {
            val g3Ok = attempt(ModelKey.G3_1B, BackendKind.CPU, ModelFile.G3_1B)
            env.state.put(StateStore.initKey(ModelKey.G3_1B, BackendKind.CPU), if (g3Ok) OK else lastCode)
        }
        LadderRules.initEarlyExit(cpuOk, gpuOk)?.let { StageOutcome.earlyExit(it) } ?: StageOutcome.done()
    }

    // One engine load, recorded as an INIT line and closed at once (the next stage is a new process anyway).
    private suspend fun attempt(model: ModelKey, backend: BackendKind, file: ModelFile, coldLabel: String? = null): Boolean {
        val cold = coldLabel ?: if (firstInit) "process" else "warm"
        firstInit = false
        val opened = opener.open(file, backend == BackendKind.GPU, LadderRules.SMALL_MAX_TOKENS)
        opened.backend?.close()
        lastCode = opened.outcome.failureCode ?: NONE
        env.sink.emit(
            Stage.INIT,
            SpikeLine.of(
                SpikeKind.INIT,
                "model" to model.wire,
                "backend" to backend.wire,
                "file" to if (file.gpuVariant) EngineOpener.GPU_FILE else EngineOpener.GENERIC_FILE,
                "cold" to cold,
                "init_ms" to opened.outcome.initMs.toString(),
                "pss_mb" to opened.peakPssMb.toString(),
                "result" to if (opened.outcome.ok) OK else FAILED,
                "code" to lastCode,
            ),
        )
        return opened.outcome.ok
    }

    // The GPU path: an empty kernel cache for the first-ever init, the generic file on the GPU when the GPU file fails, and a
    // repeat init (warm cache) once one works. The GPU line is the row of the verdict, `gpu_ok` or `gpu_init_failed:<code>`.
    private suspend fun initGpu(): Boolean {
        env.files.engineCache.deleteRecursively()
        env.files.engineCache.mkdirs()
        var file = ModelFile.E2B_GPU
        var firstOk = attempt(ModelKey.E2B, BackendKind.GPU, file, "first_gpu")
        if (!firstOk) {
            file = ModelFile.E2B_GENERIC
            firstOk = attempt(ModelKey.E2B, BackendKind.GPU, file, "first_gpu")
        }
        var code = lastCode
        val ok = firstOk && attempt(ModelKey.E2B, BackendKind.GPU, file, "warm_cache").also { code = lastCode }
        env.state.put(StateStore.initKey(ModelKey.E2B, BackendKind.GPU), if (ok) OK else code)
        env.state.put(EngineOpener.GPU_FILE_KEY, if (file.gpuVariant) EngineOpener.GPU_FILE else EngineOpener.GENERIC_FILE)
        env.sink.emit(
            Stage.INIT,
            if (ok) {
                SpikeLine.of(SpikeKind.GPU, "disposition" to "gpu_ok")
            } else {
                SpikeLine.of(SpikeKind.GPU, "disposition" to "gpu_init_failed", "code" to code)
            },
        )
        return ok
    }

    // ---- prefill --------------------------------------------------------------------------------------------------

    /** Prefill throughput on every initialized E2B backend at 1000, 4000 and 7000 prompt tokens, with the SB-sized context. */
    suspend fun prefill(): StageOutcome = measured(Stage.PREFILL) {
        val tpsAtPrefix = HashMap<BackendKind, Double>()
        for (kind in opener.e2bBackends()) {
            val opened = opener.open(opener.fileFor(ModelKey.E2B, kind), kind == BackendKind.GPU, LadderRules.SB_MAX_TOKENS)
            val engine = opened.backend
            if (engine == null) {
                emitPrefillFailure(kind, opened.outcome.failureCode)
                continue
            }
            try {
                for (target in PREFILL_TARGETS) {
                    val request = BackendRequest(fillerText(target), PREFILL_USER, BackendMode.NativeTools(emptyList()), false, PREFILL_MAX_OUTPUT)
                    val answer = generateOrCode(engine, request)
                    emitPrefill(kind, target, answer)
                    if (target == SB_PREFIX_TOKENS && answer is Generated) {
                        answer.value.bench.prefillTokensPerSecond?.let { tpsAtPrefix[kind] = it }
                    }
                }
            } finally {
                engine.close()
            }
        }
        val cpu = tpsAtPrefix[BackendKind.CPU]
        val gpu = tpsAtPrefix[BackendKind.GPU]
        if (cpu != null && gpu != null && gpu < cpu) {
            env.sink.emit(Stage.PREFILL, SpikeLine.of(SpikeKind.GPU, "disposition" to "gpu_slower"))
        }
        StageOutcome.done()
    }

    private fun emitPrefill(kind: BackendKind, target: Int, outcome: Generation) {
        val line = when (outcome) {
            is Generated -> {
                val bench = outcome.value.bench
                SpikeLine.of(
                    SpikeKind.PREFILL,
                    "model" to ModelKey.E2B.wire,
                    "backend" to kind.wire,
                    "target_tokens" to target.toString(),
                    "prefill_tokens" to (bench.prefillTokens?.toString() ?: NA),
                    "prefill_tps" to (bench.prefillTokensPerSecond?.let(::oneDecimal) ?: NA),
                    "ttft_ms" to (bench.ttftMs?.roundToLong()?.toString() ?: NA),
                    "result" to OK,
                    "code" to NONE,
                )
            }
            is Refused -> prefillFailureLine(kind, target, outcome.code)
        }
        env.sink.emit(Stage.PREFILL, line)
    }

    private fun emitPrefillFailure(kind: BackendKind, code: String?) {
        env.sink.emit(Stage.PREFILL, prefillFailureLine(kind, 0, code ?: NATIVE_ERROR))
    }

    private fun prefillFailureLine(kind: BackendKind, target: Int, code: String): SpikeLine = SpikeLine.of(
        SpikeKind.PREFILL,
        "model" to ModelKey.E2B.wire,
        "backend" to kind.wire,
        "target_tokens" to target.toString(),
        "prefill_tokens" to NA,
        "prefill_tps" to NA,
        "ttft_ms" to NA,
        "result" to FAILED,
        "code" to code,
    )

    // ---- kv_reuse -------------------------------------------------------------------------------------------------

    /**
     * Two back-to-back conversations with an identical system-plus-tools preface, with and without `prefillPrefaceOnInit`,
     * on the first working E2B backend. The preface is the SB envelope when it loaded, else the small envelope padded to about
     * 7000 tokens (labeled `padded_small`). The disposition comes from [LadderRules.kvReuseDisposition].
     */
    suspend fun kvReuse(): StageOutcome = measured(Stage.KV_REUSE) {
        val kind = opener.e2bBackends().firstOrNull() ?: return@measured StageOutcome.earlyExit(LadderRules.INIT_FAILED_ALL)
        val preface = kvPreface()
        val request = BackendRequest(preface.system, PREFILL_USER, BackendMode.NativeTools(preface.tools), false, PREFILL_MAX_OUTPUT)
        for (onInit in listOf(false, true)) {
            val opened = opener.open(opener.fileFor(ModelKey.E2B, kind), kind == BackendKind.GPU, LadderRules.SB_MAX_TOKENS, onInit)
            val engine = opened.backend
            if (engine == null) {
                env.sink.emit(Stage.KV_REUSE, kvLine(preface.label, kind, onInit, null, null, opened.outcome.failureCode))
                continue
            }
            try {
                val first = generateOrCode(engine, request)
                val second = if (first is Generated) generateOrCode(engine, request) else first
                env.sink.emit(Stage.KV_REUSE, kvLine(preface.label, kind, onInit, (first as? Generated)?.value, (second as? Generated)?.value, (second as? Refused)?.code))
            } finally {
                engine.close()
            }
        }
        StageOutcome.done()
    }

    private class Preface(val label: String, val system: String, val tools: List<ToolSpec>)

    private fun kvPreface(): Preface = when (val sb = env.sbState()) {
        is SbState.Loaded -> Preface("sb", sb.envelope.system, sb.envelope.tools)
        else -> Preface("padded_small", SmallEnvelope.SYSTEM + " " + fillerText(SB_PREFIX_TOKENS), SmallEnvelope.tools)
    }

    @Suppress("LongParameterList")
    private fun kvLine(
        preface: String,
        kind: BackendKind,
        onInit: Boolean,
        first: BackendAnswer?,
        second: BackendAnswer?,
        code: String?,
    ): SpikeLine {
        val firstTokens = first?.bench?.prefillTokens
        val secondTokens = second?.bench?.prefillTokens
        val firstTtft = first?.bench?.ttftMs?.roundToLong()
        val secondTtft = second?.bench?.ttftMs?.roundToLong()
        val disposition = LadderRules.kvReuseDisposition(firstTokens, secondTokens, firstTtft, secondTtft)
        val fields = mutableListOf(
            "preface" to preface,
            "backend" to kind.wire,
            "preface_on_init" to if (onInit) "1" else "0",
            "first_prefill_tokens" to (firstTokens?.toString() ?: NA),
            "second_prefill_tokens" to (secondTokens?.toString() ?: NA),
            "first_ttft_ms" to (firstTtft?.toString() ?: NA),
            "second_ttft_ms" to (secondTtft?.toString() ?: NA),
            "disposition" to disposition,
        )
        if (code != null) fields += "code" to code
        return SpikeLine.of(SpikeKind.KVREUSE, *fields.toTypedArray())
    }

    // ---- rf_matrix ------------------------------------------------------------------------------------------------

    /**
     * Eight schema features, each with the constraint ON and OFF over five adversarial prompts, on the first working E2B
     * backend. The flat feature also runs ON without the engine's constrained-decoding flag, which tells whether that flag
     * is needed (`constrained_flag`). Dispositions come from [LadderRules.rfDisposition].
     */
    suspend fun rfMatrix(): StageOutcome = measured(Stage.RF_MATRIX) {
        val kind = opener.e2bBackends().firstOrNull() ?: return@measured StageOutcome.earlyExit(LadderRules.INIT_FAILED_ALL)
        val opened = opener.open(opener.fileFor(ModelKey.E2B, kind), kind == BackendKind.GPU, LadderRules.SMALL_MAX_TOKENS)
        val engine = opened.backend ?: return@measured StageOutcome.error(opened.outcome.failureCode ?: NATIVE_ERROR)
        try {
            for (feature in RfProbes.features) probeFeature(engine, feature)
        } finally {
            engine.close()
        }
        StageOutcome.done()
    }

    private class ArmResult(val invalid: Int, val nativeError: Boolean)

    private suspend fun probeFeature(engine: LlmBackend, feature: RfFeature) {
        val off = runArm(engine, feature, constraintOn = false, engineFlag = null)
        env.sink.emit(Stage.RF_MATRIX, probeLine(feature, "off", off, null))
        val on = runArm(engine, feature, constraintOn = true, engineFlag = null)
        val disposition = LadderRules.rfDisposition(on.nativeError, on.invalid, off.invalid)
        var flag: String? = null
        if (feature.id == FLAT) {
            val withoutFlag = runArm(engine, feature, constraintOn = true, engineFlag = false)
            env.sink.emit(Stage.RF_MATRIX, probeLine(feature, "on_noflag", withoutFlag, null))
            flag = if (disposition == LadderRules.ENFORCED) (if (withoutFlag.invalid > 0) "1" else "0") else NA
        }
        env.sink.emit(Stage.RF_MATRIX, probeLine(feature, "on", on, disposition, flag))
    }

    private suspend fun runArm(engine: LlmBackend, feature: RfFeature, constraintOn: Boolean, engineFlag: Boolean?): ArmResult {
        var invalid = 0
        var nativeError = false
        for (prompt in feature.prompts) {
            val request = BackendRequest(RfProbes.SYSTEM, prompt, BackendMode.Constrained(feature.schema), constraintOn, RF_MAX_OUTPUT, engineFlag)
            when (val outcome = generateOrCode(engine, request)) {
                is Generated -> if (!feature.valid(outcome.value.text)) invalid++
                is Refused -> {
                    invalid++
                    nativeError = true
                }
            }
        }
        return ArmResult(invalid, nativeError)
    }

    private fun probeLine(feature: RfFeature, constraint: String, arm: ArmResult, disposition: String?, flag: String? = null): SpikeLine {
        val fields = mutableListOf(
            "feature" to feature.id,
            "constraint" to constraint,
            "trials" to RfProbes.PROMPTS_PER_FEATURE.toString(),
            "invalid" to arm.invalid.toString(),
        )
        if (disposition != null) fields += "disposition" to disposition
        if (flag != null) fields += "constrained_flag" to flag
        return SpikeLine.of(SpikeKind.SCHEMAPROBE, *fields.toTypedArray())
    }

    // ---- shared ---------------------------------------------------------------------------------------------------

    private sealed interface Generation
    private class Generated(val value: BackendAnswer) : Generation
    private class Refused(val code: String) : Generation

    // A failed generation is a recorded refusal with its stable code, never an exception out of a stage.
    private suspend fun generateOrCode(engine: LlmBackend, request: BackendRequest): Generation =
        try {
            Generated(withContext(env.dispatcher) { engine.generate(request) })
        } catch (e: BackendFailure) {
            Refused(e.code)
        }

    private fun oneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    override fun toString(): String = "EngineStages"

    private companion object {
        const val FLAT = "flat"
        const val NATIVE_ERROR = "native_error"
    }
}
