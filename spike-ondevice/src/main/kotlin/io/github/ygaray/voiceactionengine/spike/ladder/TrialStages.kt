package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
import io.github.ygaray.voiceactionengine.spike.envelope.EnvelopeSnapshot
import io.github.ygaray.voiceactionengine.spike.envelope.SbState
import io.github.ygaray.voiceactionengine.spike.envelope.SmallEnvelope
import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord
import io.github.ygaray.voiceactionengine.spike.gate.SpikeOnDeviceCapability
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.trial.TrialRunner
import io.github.ygaray.voiceactionengine.spike.verdict.CellSelector
import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds

private const val OK = "ok"
private const val FAILED = "failed"
private const val NONE = "none"
private const val PROCESS = "process"
private const val WARM = "warm"
private const val SB_FIXTURE_ABSENT = "sb_fixture_absent"
private const val NO_WINNER = "no_winner"
private const val LIGHT = "light"
private const val MILLIS_PER_SECOND = 1000L
private const val SB_PREFIX_TARGET = "7000"
private val THERMAL_ORDER = listOf("none", "light", "moderate", "severe", "critical", "emergency", "shutdown")

/**
 * The trial stages of the ladder: screen (N=20 per cell, to pick the winner), confirm (every distinct gold item once on the
 * stored winner, then the forced subset), sustained (at least 60 trials over at least 120 s on the winner) for both envelopes,
 * and the exit-reason report. Every trial goes through [TrialRunner], so it is the real engine path, scored against the gold
 * label. A failed trial is a counted failure and is never re-run; forced-subset and sustained trials sit on their own cell or
 * stage, so the verdict never counts them toward the accuracy N. Only closed-grammar lines leave this class.
 */
internal class TrialStages(private val env: LadderEnv, private val opener: EngineOpener) {
    private class Inputs(val snapshot: EnvelopeSnapshot, val gold: GoldSet, val maxTokens: Int)

    private sealed interface Resolved
    private class Ready(val inputs: Inputs) : Resolved
    private class Stop(val outcome: StageOutcome) : Resolved

    // ---- envelope inputs and the sb early exits ---------------------------------------------------------------------

    private fun resolve(envelope: Envelope): Resolved = when (envelope) {
        Envelope.SMALL -> Ready(Inputs(SmallEnvelope.envelope, env.smallGold(), LadderRules.SMALL_MAX_TOKENS))
        Envelope.SB -> when (val sb = env.sbState()) {
            SbState.Absent -> Stop(StageOutcome.earlyExit(SB_FIXTURE_ABSENT))
            is SbState.Invalid -> Stop(StageOutcome.earlyExit(sb.code))
            is SbState.Loaded -> sbBound()?.let { Stop(StageOutcome.earlyExit(it)) } ?: Ready(Inputs(sb.envelope, sb.gold, LadderRules.SB_MAX_TOKENS))
        }
    }

    // The SB prefix at the best measured prefill speed, from this run's prefill and kv_reuse evidence; null when either is missing.
    private fun sbBound(): String? {
        val kv = env.sink.lines(Stage.KV_REUSE).filter { it.kind == SpikeKind.KVREUSE }
        val dispositions = kv.mapNotNull { it["disposition"] }
        val kvReuse = when {
            LadderRules.REUSED in dispositions -> LadderRules.REUSED
            LadderRules.NOT_REUSED in dispositions -> LadderRules.NOT_REUSED
            else -> LadderRules.UNOBSERVABLE
        }
        val prefill = env.sink.lines(Stage.PREFILL).filter {
            it.kind == SpikeKind.PREFILL && it["target_tokens"] == SB_PREFIX_TARGET && it["result"] != FAILED
        }
        val tps = prefill.mapNotNull { it["prefill_tps"]?.toDoubleOrNull() }.maxOrNull() ?: return null
        val prefix = kv.filter { it["preface"] == "sb" }.mapNotNull { it["first_prefill_tokens"]?.toIntOrNull() }.maxOrNull()
            ?: prefill.mapNotNull { it["prefill_tokens"]?.toIntOrNull() }.maxOrNull()
            ?: return null
        return LadderRules.sbPrefillBound(prefix, tps, kvReuse)
    }

    // ---- shared pieces ----------------------------------------------------------------------------------------------

    private fun capability(init: InitOutcome) = SpikeOnDeviceCapability({ true }, { env.probes.deviceFacts().abis }, { init })

    private fun thermalRank(status: String): Int = THERMAL_ORDER.indexOf(status).coerceAtLeast(0)

    private fun thermal(stage: Stage, status: String = env.probes.thermalStatus(), waitedS: Long? = null) {
        val fields = mutableListOf("stage" to stage.wire, "status" to status)
        if (waitedS != null) fields += "waited_s" to waitedS.toString()
        env.sink.emit(stage, SpikeLine.of(SpikeKind.THERMAL, *fields.toTypedArray()))
    }

    // Waits in the app for the device to cool to `light` (capped), so a hot cell does not corrupt the next one (Pitfall 5).
    private suspend fun cooldown(stage: Stage) {
        var waitedMs = 0L
        var status = env.probes.thermalStatus()
        while (thermalRank(status) > thermalRank(LIGHT) && waitedMs < LadderRules.COOLDOWN_CAP_MS) {
            env.probes.pause(LadderRules.COOLDOWN_POLL_MS)
            waitedMs += LadderRules.COOLDOWN_POLL_MS
            status = env.probes.thermalStatus()
        }
        thermal(stage, status, waitedMs / MILLIS_PER_SECOND)
    }

    private fun emitInit(stage: Stage, cell: Cell, cold: String, opened: OpenedEngine) {
        env.sink.emit(
            stage,
            SpikeLine.of(
                SpikeKind.INIT,
                "stage" to stage.wire,
                "cell" to cell.wire,
                "cold" to cold,
                "init_ms" to opened.outcome.initMs.toString(),
                "pss_mb" to opened.peakPssMb.toString(),
                "result" to if (opened.outcome.ok) OK else FAILED,
                "code" to (opened.outcome.failureCode ?: NONE),
            ),
        )
    }

    private fun memLine(stage: Stage, cell: Cell, reading: PssReading): SpikeLine = SpikeLine.of(
        SpikeKind.MEM,
        "stage" to stage.wire,
        "cell" to cell.wire,
        "peak_pss_mb" to reading.peakMb.toString(),
        "interval_ms" to Thresholds.pssIntervalMs.toString(),
        "samples" to reading.samples.toString(),
    )

    // The cell the screen stored for [envelope], or null (a Gemma 3 1B cell is never a winner, whatever was stored).
    private fun storedWinner(envelope: Envelope): Cell? =
        Cell.fromWire(env.state.get(StateStore.winnerKey(envelope)))?.takeIf { it.model == ModelKey.E2B && it.shape == Shape.AUTO }

    // ---- screen -----------------------------------------------------------------------------------------------------

    /** Runs the seeded N items on every screen cell, then selects and stores the E2B winner of the envelope. */
    suspend fun screen(stage: Stage, progress: StageProgress): StageOutcome {
        val envelope = checkNotNull(stage.envelope) { "a screen stage has an envelope" }
        val inputs = when (val resolved = resolve(envelope)) {
            is Stop -> return resolved.outcome
            is Ready -> resolved.inputs
        }
        val gpuState = env.state.get(StateStore.initKey(ModelKey.E2B, BackendKind.GPU))
        val cells = LadderRules.screenCells(envelope, env.files.modelPath(ModelFile.G3_1B) != null, gpuState == null || gpuState == OK)
        val items = LadderRules.seededScreenItems(inputs.gold.items, env.screenN)
        val plannedAll = cells.size * items.size
        progress.planned = plannedAll
        var lastInitCode: String? = null
        val trials = ArrayList<TrialRecord>()
        val mems = ArrayList<SpikeLine>()
        val ran = ArrayList<Cell>()
        var firstInStage = true
        for (cell in cells) {
            cooldown(stage)
            val opened = opener.open(opener.fileFor(cell.model, cell.backend), cell.backend == BackendKind.GPU, inputs.maxTokens)
            emitInit(stage, cell, if (firstInStage) PROCESS else WARM, opened)
            val backend = opened.backend
            if (backend == null) {
                lastInitCode = opened.outcome.failureCode
                progress.planned -= items.size
                continue
            }
            ran += cell
            TrialRunner({ backend }, capability(opened.outcome), env.dispatcher, env.nanoClock).use { runner ->
                val sampler = PssSampler(env.probes)
                try {
                    sampler.around {
                        items.forEachIndexed { index, item ->
                            val record = runner.run(cell, inputs.snapshot, item, firstInProcess = firstInStage, stage = stage)
                            firstInStage = false
                            env.sink.emit(stage, record.toLine())
                            trials += record
                            progress.trials++
                            if ((index + 1) % LadderRules.THERMAL_BLOCK_TRIALS == 0) thermal(stage)
                        }
                    }
                } finally {
                    val line = memLine(stage, cell, sampler.reading)
                    env.sink.emit(stage, line)
                    mems += line
                }
            }
        }
        // No cell could start an engine: that is a failed stage (planned stays whole), never a quiet empty screen.
        if (ran.isEmpty()) return StageOutcome.error(lastInitCode ?: "init_failed", 0, plannedAll)
        val winner = CellSelector.select(trials, mems)?.takeIf { it.model == ModelKey.E2B }
        env.state.put(StateStore.winnerKey(envelope), winner?.wire ?: NONE)
        val routes = ran.map { it.route.wire }.distinct().sorted().joinToString(",")
        return StageOutcome.done(progress.trials, progress.planned, "winner" to (winner?.wire ?: NONE), "routes" to routes)
    }

    // ---- confirm ----------------------------------------------------------------------------------------------------

    /**
     * Every distinct gold item once on the stored winner (auto shape), then the forced subset on the same engine in the forced
     * shape. This is a fresh process, so the engine init line is the cold row (`cold=process`) and the first trial the cold trial.
     */
    suspend fun confirm(stage: Stage, progress: StageProgress): StageOutcome {
        val envelope = checkNotNull(stage.envelope) { "a confirm stage has an envelope" }
        val inputs = when (val resolved = resolve(envelope)) {
            is Stop -> return resolved.outcome
            is Ready -> resolved.inputs
        }
        val cell = storedWinner(envelope) ?: return StageOutcome.earlyExit(NO_WINNER)
        val plan = LadderRules.confirmPlan(inputs.gold)
        progress.planned = plan.planned
        cooldown(stage)
        val opened = opener.open(opener.fileFor(cell.model, cell.backend), cell.backend == BackendKind.GPU, inputs.maxTokens)
        emitInit(stage, cell, PROCESS, opened)
        val backend = opened.backend ?: return StageOutcome.error(opened.outcome.failureCode ?: "init_failed", 0, plan.planned)
        TrialRunner({ backend }, capability(opened.outcome), env.dispatcher, env.nanoClock).use { runner ->
            val sampler = PssSampler(env.probes)
            try {
                sampler.around {
                    val forcedCell = cell.copy(shape = Shape.FORCED)
                    var index = 0
                    for ((item, trialCell) in plan.auto.map { it to cell } + plan.forced.map { it to forcedCell }) {
                        val record = runner.run(trialCell, inputs.snapshot, item, firstInProcess = index == 0, stage = stage)
                        env.sink.emit(stage, record.toLine())
                        progress.trials++
                        index++
                        if (index % LadderRules.THERMAL_BLOCK_TRIALS == 0) thermal(stage)
                    }
                }
            } finally {
                env.sink.emit(stage, memLine(stage, cell, sampler.reading))
            }
        }
        return StageOutcome.done(progress.trials, plan.planned)
    }

    // ---- sustained --------------------------------------------------------------------------------------------------

    /**
     * Cycles the auto items on the winner until [LadderRules.sustainedDone], with a THERMAL line every
     * [Thresholds.thermalIntervalS] seconds. Sustained trials sit on their own stage, so they never count toward the accuracy N.
     */
    suspend fun sustained(stage: Stage, progress: StageProgress): StageOutcome {
        val envelope = checkNotNull(stage.envelope) { "a sustained stage has an envelope" }
        val inputs = when (val resolved = resolve(envelope)) {
            is Stop -> return resolved.outcome
            is Ready -> resolved.inputs
        }
        val cell = storedWinner(envelope) ?: return StageOutcome.earlyExit(NO_WINNER)
        val items = inputs.gold.items
        progress.planned = Thresholds.sustainedMinTrials
        cooldown(stage)
        val opened = opener.open(opener.fileFor(cell.model, cell.backend), cell.backend == BackendKind.GPU, inputs.maxTokens)
        emitInit(stage, cell, PROCESS, opened)
        val backend = opened.backend ?: return StageOutcome.error(opened.outcome.failureCode ?: "init_failed", 0, progress.planned)
        val start = env.probes.monotonicMs()
        TrialRunner({ backend }, capability(opened.outcome), env.dispatcher, env.nanoClock).use { runner ->
            val sampler = PssSampler(env.probes)
            try {
                sampler.around {
                    var lastThermal = start
                    var index = 0
                    while (!LadderRules.sustainedStop(progress.trials, env.probes.monotonicMs() - start)) {
                        val item = items[index % items.size]
                        index++
                        val record = runner.run(cell, inputs.snapshot, item, firstInProcess = progress.trials == 0, stage = stage)
                        env.sink.emit(stage, record.toLine())
                        progress.trials++
                        val now = env.probes.monotonicMs()
                        if (now - lastThermal >= Thresholds.thermalIntervalS * MILLIS_PER_SECOND) {
                            thermal(stage)
                            lastThermal = now
                        }
                    }
                }
            } finally {
                env.sink.emit(stage, memLine(stage, cell, sampler.reading))
            }
        }
        val seconds = (env.probes.monotonicMs() - start) / MILLIS_PER_SECOND
        return StageOutcome.done(progress.trials, progress.planned, "seconds" to seconds.toString())
    }

    // ---- exit reasons -----------------------------------------------------------------------------------------------

    /** One EXIT line per exit reason of this package since the prepare stage, with its count (proof of zero process deaths). */
    fun exitReasons(): StageOutcome {
        val since = env.state.get(StateStore.PREPARE_EPOCH_MS)?.toLongOrNull() ?: 0L
        env.probes.exitReasons(since).groupingBy { it }.eachCount().toSortedMap().forEach { (reason, count) ->
            env.sink.emit(Stage.EXIT_REASONS, SpikeLine.of(SpikeKind.EXIT, "reason" to reason, "count" to count.toString()))
        }
        return StageOutcome.done()
    }

    override fun toString(): String = "TrialStages"
}
