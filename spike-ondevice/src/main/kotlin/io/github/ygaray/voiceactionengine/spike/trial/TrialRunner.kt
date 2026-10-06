package io.github.ygaray.voiceactionengine.spike.trial

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend
import io.github.ygaray.voiceactionengine.spike.envelope.EnvelopeSnapshot
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.evidence.StagePhase
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord
import io.github.ygaray.voiceactionengine.spike.gold.GoldItem
import io.github.ygaray.voiceactionengine.spike.gold.GoldMatcher
import io.github.ygaray.voiceactionengine.spike.provider.ProviderRoute
import io.github.ygaray.voiceactionengine.spike.provider.SpikeOnDeviceProvider
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaResult
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaSubset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher

private const val OUTCOME_OK = "ok"
private const val OUTCOME_HARNESS_ERROR = "harness_error"
private const val OUTCOME_FAILED = "failed"
private const val NANOS_PER_MILLI = 1_000_000L
private val STABLE_OUTCOME = Regex("[a-z0-9_]+")

/**
 * The measured unit of the spike (D-04): one gold item through the real engine path (SingleShotStrategy, the router, the
 * on-device gate and [SpikeOnDeviceProvider]) for one cell, scored against the item's gold label (D-06).
 *
 * One pipeline is built per (cell, envelope) and reused. A write is never applied: the resolver's mutation is a no-op and
 * the gate only records the proposal, so a false write is counted (a negative item that reaches the gate with a mutating
 * proposal) and nothing changes. Scoring reads the model's own first call from a [CapturingProvider], because a decline
 * (no call) is a normal answer the pipeline outcome cannot tell apart from other escalations.
 *
 * [run] never throws for a model-side problem: any exception other than cancellation is a counted `harness_error`.
 * [toString] shows counts only.
 *
 * @property backendFor the backend of a cell; called once per cell and wrapped in a [MeteredBackend].
 * @property onDevice the real on-device gate the router consults before it binds the on-device provider.
 * @property dispatcher the dispatcher blocking native calls run on.
 * @property nanoClock a monotonic clock in nanoseconds; latency is read from it around the pipeline call (THRESHOLDS (d)).
 */
internal class TrialRunner(
    private val backendFor: (Cell) -> LlmBackend,
    private val onDevice: OnDeviceCapability,
    private val dispatcher: CoroutineDispatcher,
    private val nanoClock: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val backends = HashMap<Cell, MeteredBackend>()
    private val rigs = HashMap<Pair<Cell, Envelope>, Rig>()

    /** Everything one (cell, envelope) pipeline needs, kept so a trial can read what the model and the gate saw. */
    private inner class Rig(cell: Cell, private val envelope: EnvelopeSnapshot) {
        val metered: MeteredBackend = backends.getOrPut(cell) { MeteredBackend(backendFor(cell)) }
        val provider = CapturingProvider(SpikeOnDeviceProvider(metered, routeOf(cell.route), dispatcher))
        val gate = RecordingGate()

        /** The tool a forced single-shot tier calls for the current item. */
        var forcedTool: String? = null

        val pipeline: CommandPipeline = commandPipeline {
            tier(
                SingleShotStrategy(StrategyId("spike_single")) {
                    tooling = ToolSpecProvider { envelope.toTooling(forcedTool) }
                    resolver = SpikeResolver { envelope.tools }
                    capabilities = StrategyCapabilities(setOf(ProviderId.ON_DEVICE))
                    forceTool = cell.shape == Shape.FORCED
                },
            )
            provider(provider)
            onDevice = this@TrialRunner.onDevice
            providerSelection = ProviderSelectionSource { ProviderSelection(ProviderId.ON_DEVICE, cell.model.wire) }
            gate = this@Rig.gate
            commitSink = DiscardingCommitSink
        }
    }

    /**
     * Runs [item] once for [cell] in [envelope] and returns the scored record. [firstInProcess] marks the first trial of a
     * process (the cold row); [stage] defaults to the screen stage of the envelope.
     */
    @Suppress("LongParameterList")
    suspend fun run(
        cell: Cell,
        envelope: EnvelopeSnapshot,
        item: GoldItem,
        firstInProcess: Boolean,
        stage: Stage = Stage.of(envelope.env, StagePhase.SCREEN),
    ): TrialRecord {
        val rig = rigs.getOrPut(cell to envelope.env) { Rig(cell, envelope) }
        rig.provider.reset()
        rig.gate.reset()
        rig.metered.reset()
        rig.forcedTool = item.expectTool
        val input = CommandInput(item.transcript, item.lang.wire.takeIf { item.lang != Lang.NONE })
        val started = nanoClock()
        val outcome = try {
            rig.pipeline.execute(input)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // A fault in the harness is a counted failure, never a re-run and never an exception out of a trial.
            null
        }
        val latencyMs = (nanoClock() - started) / NANOS_PER_MILLI
        return score(rig, envelope, item, cell, stage, latencyMs, firstInProcess, outcome)
    }

    @Suppress("LongParameterList")
    private fun score(
        rig: Rig,
        envelope: EnvelopeSnapshot,
        item: GoldItem,
        cell: Cell,
        stage: Stage,
        latencyMs: Long,
        firstInProcess: Boolean,
        outcome: CommandOutcome?,
    ): TrialRecord {
        val bench = rig.metered.lastBench
        val failureCode = rig.provider.failureCode
        val call = rig.provider.firstCall
        val harnessError = outcome == null
        val declined = !harnessError && call == null && failureCode == null && rig.provider.stopReason == StopReason.END_TURN
        val tool = call?.let { c -> envelope.tools.firstOrNull { it.name == c.name } }
        val schemaValid = !harnessError && (
            declined ||
                (call != null && tool != null && SchemaSubset.validate(tool.inputSchema, call.arguments) == SchemaResult.Valid)
            )
        val toolMatch: Boolean
        val argsMatch: Boolean
        when {
            harnessError -> {
                toolMatch = false
                argsMatch = false
            }
            item.kind == ItemKind.POS -> {
                toolMatch = GoldMatcher.toolMatch(item.expectTool, call?.name)
                argsMatch = toolMatch && call != null && GoldMatcher.argsMatch(item.expectArgs, call.arguments)
            }
            else -> {
                // A negative is right when the model declines or calls a tool that cannot write.
                toolMatch = declined || (call != null && tool != null && !tool.mutating)
                argsMatch = toolMatch
            }
        }
        val falseWrite = item.kind == ItemKind.NEG && rig.gate.proposedTools.isNotEmpty()
        return TrialRecord(
            env = envelope.env,
            stage = stage,
            cell = cell,
            item = item.id,
            lang = item.lang,
            kind = item.kind,
            schemaValid = schemaValid,
            toolMatch = toolMatch,
            argsMatch = argsMatch,
            falseWrite = falseWrite,
            latencyMs = latencyMs,
            ttftMs = bench?.ttftMs?.toLong(),
            prefillTokens = bench?.prefillTokens ?: 0,
            decodeTokens = bench?.decodeTokens ?: 0,
            firstInProcess = firstInProcess,
            outcome = outcomeCode(outcome, failureCode),
        )
    }

    private fun outcomeCode(outcome: CommandOutcome?, failureCode: String?): String = when {
        outcome == null -> OUTCOME_HARNESS_ERROR
        failureCode != null -> failureCode
        outcome is CommandOutcome.Failed -> outcome.reason.code.takeIf { STABLE_OUTCOME.matches(it) } ?: OUTCOME_FAILED
        else -> OUTCOME_OK
    }

    private fun routeOf(route: Route): ProviderRoute = when (route) {
        Route.A -> ProviderRoute.CONSTRAINED_JSON
        Route.B -> ProviderRoute.NATIVE_TOOLS
    }

    /** Closes every backend this runner opened. */
    override fun close() {
        backends.values.forEach { it.close() }
        backends.clear()
        rigs.clear()
    }

    override fun toString(): String = "TrialRunner(cells=${backends.size}, pipelines=${rigs.size})"
}
