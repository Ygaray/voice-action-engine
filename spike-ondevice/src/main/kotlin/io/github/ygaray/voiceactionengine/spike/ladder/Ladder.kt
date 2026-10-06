package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.backend.BackendConfig
import io.github.ygaray.voiceactionengine.spike.backend.BackendFailure
import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend
import io.github.ygaray.voiceactionengine.spike.envelope.SbEnvelope
import io.github.ygaray.voiceactionengine.spike.envelope.SbState
import io.github.ygaray.voiceactionengine.spike.envelope.SmallEnvelope
import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.gate.SpikeOnDeviceCapability
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.trial.TrialRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val NANOS_PER_MILLI = 1_000_000L
private const val BYTES_PER_MB = 1024L * 1024L
private const val MAX_CODE_LENGTH = 60
private val NOT_CODE_CHAR = Regex("[^a-z0-9]+")

/**
 * What a stage needs from outside, bundled so the stage classes share one value.
 *
 * @property backend makes a fresh, uninitialized backend (the real one in the app, a fake in a test).
 * @property smallGold the committed small gold set; @property sbState the private SB envelope state (loaded on demand).
 * @property dispatcher the dispatcher blocking native calls run on.
 * @property screenN items per screen cell; tests lower it.
 */
internal class LadderEnv(
    val sink: EvidenceSink,
    val state: StateStore,
    val probes: Probes,
    val backend: () -> LlmBackend,
    val files: AppFiles,
    val smallGold: () -> GoldSet,
    val sbState: () -> SbState,
    val dispatcher: CoroutineDispatcher,
    val screenN: Int = LadderRules.SCREEN_N,
) {
    /** A monotonic nanosecond clock for [TrialRunner], read from the probes so a fake clock drives every latency. */
    val nanoClock: () -> Long = { probes.monotonicMs() * NANOS_PER_MILLI }

    override fun toString(): String = "LadderEnv"
}

/** The trial counts a running stage has reached, so a failure still reports them on its STAGE line. */
internal class StageProgress {
    var trials: Int = 0
    var planned: Int = 0
}

/** How a stage ended; it renders to the one `VAE_SPIKE_STAGE` line every stage writes last. */
internal class StageOutcome private constructor(
    private val result: String,
    private val trials: Int,
    private val planned: Int,
    private val extra: List<Pair<String, String>>,
) {
    /** The `VAE_SPIKE_STAGE` line of [stage]. */
    fun line(stage: Stage): SpikeLine = SpikeLine.of(
        SpikeKind.STAGE,
        "stage" to stage.wire,
        "result" to result,
        "trials" to trials.toString(),
        "planned" to planned.toString(),
        *extra.toTypedArray(),
    )

    companion object {
        /** The stage ran everything it planned. */
        fun done(trials: Int = 0, planned: Int = trials, vararg extra: Pair<String, String>): StageOutcome =
            StageOutcome("done", trials, planned, extra.toList())

        /** The stage was not run, for a stated stable [reason]. */
        fun skipped(reason: String): StageOutcome = StageOutcome("skipped", 0, 0, listOf("reason" to reason))

        /** The ladder stopped here, cheaply, for a stated stable [reason] (the envelope goes red with `early_exit:<reason>`). */
        fun earlyExit(reason: String): StageOutcome = StageOutcome("early_exit", 0, 0, listOf("reason" to reason))

        /** The stage failed for a stable [reason] after [trials] of [planned] trials. */
        fun error(reason: String, trials: Int = 0, planned: Int = 0): StageOutcome =
            StageOutcome("error", trials, planned, listOf("reason" to reason))
    }
}

/**
 * The on-device measurement ladder. One process start runs one stage ([run]); the stages are in early-exit order (cheap
 * gating rows first, 13-RESEARCH "Measurement ladder"). Whatever a stage does, [run] ends with exactly one
 * `VAE_SPIKE_STAGE` line, and a stage that throws becomes `result=error` with a stable code, never a message. Between
 * process starts the ladder remembers only what [StateStore] and the evidence files hold.
 */
internal class Ladder(private val env: LadderEnv) {
    /** Runs [stage] and writes its STAGE line last. */
    suspend fun run(stage: Stage) {
        val progress = StageProgress()
        val outcome = try {
            dispatch(stage, progress)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { env.sink.emit(stage, StageOutcome.error("cancelled", progress.trials, progress.planned).line(stage)) }
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            StageOutcome.error(failureCode(e), progress.trials, progress.planned)
        }
        env.sink.emit(stage, outcome.line(stage))
    }

    private suspend fun dispatch(stage: Stage, progress: StageProgress): StageOutcome = when (stage) {
        Stage.PREPARE -> prepare()
        Stage.PREFLIGHT -> preflight()
        Stage.SCREEN_SMALL -> screenMinimal(stage, progress)
        else -> StageOutcome.skipped("not_implemented")
    }

    // The failure code is a stable word: a backend code as is, otherwise the exception class name only (never its message).
    private fun failureCode(e: Exception): String =
        if (e is BackendFailure) {
            e.code
        } else {
            "harness_" + (e::class.simpleName ?: "error").lowercase().replace(NOT_CODE_CHAR, "_").trim('_').take(MAX_CODE_LENGTH)
        }

    private fun prepare(): StageOutcome {
        env.files.ensure()
        env.state.clear()
        env.state.put(StateStore.PREPARE_EPOCH_MS, env.probes.epochMs().toString())
        return StageOutcome.done()
    }

    private fun preflight(): StageOutcome {
        val facts = env.probes.deviceFacts()
        val sb = env.sbState()
        env.sink.emit(
            Stage.PREFLIGHT,
            SpikeLine.of(
                SpikeKind.PREFLIGHT,
                "source" to "app",
                "mem_total_mb" to facts.memTotalMb.toString(),
                "mem_avail_mb" to facts.memAvailMb.toString(),
                "storage_free_mb" to facts.storageFreeMb.toString(),
                "abi" to facts.abis.joinToString(","),
                "page_size" to facts.pageSize.toString(),
                "gpu_libs" to if (facts.openClPresent) "1" else "0",
                "thermal" to env.probes.thermalStatus(),
                "battery_pct" to facts.batteryPct.toString(),
                *sbFacts(sb),
            ),
        )
        for (file in ModelFile.entries) {
            val size = env.files.modelSize(file)
            env.sink.emit(
                Stage.PREFLIGHT,
                SpikeLine.of(
                    SpikeKind.MODEL,
                    "model" to file.model.wire,
                    "file" to if (file.gpuVariant) "gpu" else "generic",
                    "present" to if (env.files.modelPath(file) != null) "1" else "0",
                    "size_mb" to (size / BYTES_PER_MB).toString(),
                ),
            )
        }
        return StageOutcome.done()
    }

    // The SB tool count is a reported dimension (13-THRESHOLDS 1). The key is `tool_count`, never `tools=`, which the host filter bans.
    private fun sbFacts(sb: SbState): Array<Pair<String, String>> = when (sb) {
        is SbState.Loaded -> arrayOf("sb_state" to "loaded", "sb_tool_count" to sb.toolCount.toString())
        SbState.Absent -> arrayOf("sb_state" to "absent")
        is SbState.Invalid -> arrayOf("sb_state" to "invalid", "sb_code" to sb.code)
    }

    // The tracer's minimal screen: the first N small-gold items on one cell. The full per-cell screen replaces it (Task 3).
    private suspend fun screenMinimal(stage: Stage, progress: StageProgress): StageOutcome {
        val gold = env.smallGold()
        val planned = minOf(env.screenN, gold.items.size)
        progress.planned = planned
        val cell = Cell(ModelKey.E2B, BackendKind.CPU, Route.A, Shape.AUTO)
        val path = env.files.modelPath(ModelFile.E2B_GENERIC) ?: return StageOutcome.error("model_missing", 0, planned)
        val backend = env.backend()
        val init = backend.initialize(BackendConfig(path, gpu = false, maxNumTokens = LadderRules.SMALL_MAX_TOKENS, cacheDir = null))
        if (!init.ok) {
            backend.close()
            return StageOutcome.error(init.failureCode ?: "init_failed", 0, planned)
        }
        val capability = SpikeOnDeviceCapability({ true }, { env.probes.deviceFacts().abis }, { init })
        TrialRunner({ backend }, capability, env.dispatcher, env.nanoClock).use { runner ->
            gold.items.take(planned).forEachIndexed { index, item ->
                env.sink.emit(stage, runner.run(cell, SmallEnvelope.envelope, item, firstInProcess = index == 0, stage = stage).toLine())
                progress.trials++
            }
        }
        return StageOutcome.done(progress.trials, planned)
    }

    override fun toString(): String = "Ladder"
}

/** The SB envelope loaded from the app's private directory (the activity's `sbState`). */
internal fun loadSbState(files: AppFiles): SbState = SbEnvelope.fromPrivateDir(files.privateDir)
