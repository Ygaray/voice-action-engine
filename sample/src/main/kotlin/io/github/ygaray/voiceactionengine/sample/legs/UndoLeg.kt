package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.sample.SampleEngine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.UndoFacts
import io.github.ygaray.voiceactionengine.sample.undo.ITEM_TOOL_CREATE
import io.github.ygaray.voiceactionengine.sample.undo.ITEM_TOOL_RENAME
import io.github.ygaray.voiceactionengine.sample.undo.Item
import io.github.ygaray.voiceactionengine.sample.undo.ItemStore
import io.github.ygaray.voiceactionengine.sample.undo.UndoCommitSink
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict
import io.github.ygaray.voiceactionengine.undo.UndoResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger

private const val CASE_WHOLE = 1

private const val PLAN_TOOL = "submit_plan"
private const val UNDO_ITERATIONS = 4

// A tier must declare the providers it may use; the undo leg declares its scripted provider and nothing else.
private val UNDO_ONLY = StrategyCapabilities(setOf(DEMO_PROVIDER))

// Synthetic words only. The ids stay inside the sample's own store; none of this reaches an evidence line.
private const val SEED_ID = "seed"
private const val SEED_TITLE = "seeded"
private const val LATER_TITLE = "edited later"

/** The expected shape of the whole-command case: two creates and one rename, so N is three. */
internal const val EXPECTED_WHOLE_N = 3


/** The transcripts of the three sub-cases. Plain words: the scripted provider ignores them and answers its fixed plan. */
internal const val UNDO_TRANSCRIPT_WHOLE = "Make a list, make a second one under it, and rename the seeded one."

/** Why the undo leg failed: the stable codes of its verdict, in the order they are reported. */
internal const val REASON_UNDO_INCOMPLETE = "undo_incomplete"
internal const val REASON_WRONG_COUNT = "wrong_count"

/**
 * An offline provider that answers a plan tier's `submit_plan` call with one fixed [plan]. It needs no key, opens no
 * socket and is not behind the request budget: the undo leg makes zero provider calls over any network.
 */
internal class UndoScriptProvider(private val plan: JsonObject) : AiProvider {
    private val counter = AtomicInteger()

    override val id: ProviderId get() = DEMO_PROVIDER

    override val requiresCredential: Boolean get() = false

    /** How many planning calls were answered. */
    val calls: Int get() = counter.get()

    override fun capabilities(model: String): ModelCapabilities = ModelCapabilities {
        supportsTools = true
        supportsForcedToolChoice = true
    }

    override suspend fun complete(call: ProviderRequest): ModelResult {
        val part = AssistantPart.ToolCall("undo_call_${counter.incrementAndGet()}", PLAN_TOOL, plan)
        return ModelResult.Success(ModelResponse(AssistantMessage(listOf(part)), StopReason.TOOL_USE, Usage.ZERO))
    }

    override fun toString(): String = "UndoScriptProvider"
}

/** A sample [PreApplyGate] that admits every proposal. */
internal object AdmitAllGate : PreApplyGate {
    override suspend fun admit(proposal: CommitProposal): GateDecision = GateDecision.Admit()

    override fun toString(): String = "AdmitAllGate"
}

/** What the screen needs to show "Undo all (N)": N, and the held proposals that are shown apart and not counted in N. */
internal class UndoPrompt(val n: Int, val pending: Int) {
    override fun toString(): String = "UndoPrompt(n=$n, pending=$pending)"
}

/**
 * What one command left behind. [n] is read from `journal.group(key).count` (applied actions: committed plus errored
 * applies); [pending] is the bridge's count of held proposals, which are never part of [n].
 */
internal class CommandRun(
    val outcome: CommandOutcome,
    val groupKey: String,
    val n: Int,
    val pending: Int,
    val withheld: Boolean,
) {
    /** The command's own counts. */
    val committed: Int get() = outcome.commits.size

    /** Held proposals the outcome carries. */
    val held: Int get() = outcome.held.size

    /** Whether the command ended as a partial completion. */
    val partial: Boolean get() = (outcome as? CommandOutcome.Completed)?.partial ?: false

    /** Plan steps that never ran. */
    val remaining: Int get() = (outcome as? CommandOutcome.Completed)?.remainingStepIds?.size ?: 0

    /** The `VAE_UNDO` facts of this command at [phase]. */
    fun facts(phase: String): UndoFacts = UndoFacts(phase, n, committed, pending, withheld, partial, remaining)

    override fun toString(): String = "CommandRun(n=$n, pending=$pending)"
}

/** What one `undoAll` did to a store, reduced to codes and counts. */
internal class UndoStep(
    val result: String,
    val restored: Int?,
    val blockers: Int?,
    val reason: String?,
    val storeOk: Boolean,
) {
    /** The `VAE_UNDO` facts of this step, on top of the command's [run] counts. */
    fun facts(phase: String, run: CommandRun): UndoFacts = UndoFacts(
        phase = phase,
        n = run.n,
        committed = run.committed,
        pending = run.pending,
        withheld = run.withheld,
        partial = run.partial,
        remaining = run.remaining,
        result = result,
        restored = restored,
        blockers = blockers,
        reason = reason,
        storeOk = storeOk,
    )

    override fun toString(): String = "UndoStep(result=$result, storeOk=$storeOk)"
}

/**
 * One command's world: a store with one seeded item, the journal over it, the reference bridge and the item executor.
 * A fresh one per sub-case, so one sub-case cannot mask another.
 */
internal class UndoWorld {
    /** The stateful world the plan tier runs over. */
    val world: ItemWorld = ItemWorld(ItemStore().also { it.seed(SEED_ID, SEED_TITLE) })

    /** The reference bridge: it feeds the journal from the pipeline and is listed first in the composite sink. */
    val bridge: UndoCommitSink = UndoCommitSink(world.journal)

    /** The store as it was before any command. */
    val seeded: Map<String, Item> = world.store.snapshot()

    /** A write to the seeded item that the command did not make, so a later undo must refuse (T-19-18). */
    fun editSeededItem() {
        world.store.edit(SEED_ID, LATER_TITLE)
    }

    /**
     * Runs one command: the scripted [plan] through the plan tier, over this world, with the engine's commit sink
     * `compositeSink(UndoCommitSink(journal), app sink)`, journal first. [decide] is the pre-apply gate.
     */
    suspend fun run(plan: JsonObject, transcript: String, decide: PreApplyGate): CommandRun {
        val engine = SampleEngine(
            providers = listOf(UndoScriptProvider(plan)),
            credentials = CredentialSource { CredentialLookup.Missing() },
            sink = compositeSink(bridge, NoOpCommitSink),
            listener = null,
        )
        val pipeline = engine.pipeline(
            tier = PlanLegs.planTier(world, UNDO_ONLY),
            selection = ProviderSelection(DEMO_PROVIDER, DEMO_MODEL),
            policy = TierPolicy { maxIterations = UNDO_ITERATIONS },
        ) {
            gate = decide
        }
        val outcome = pipeline.execute(CommandInput(transcript))
        val key = bridge.groupOf(outcome.runId)
        val group = world.journal.group(key)
        return CommandRun(outcome, key, group?.count ?: 0, bridge.pendingHeld(key), group?.withheld ?: false)
    }

    /**
     * Calls `journal.undoAll` for [run]'s group and reads the result. `storeOk` compares the store with [expected]: the
     * pre-command snapshot after an undo that should restore, or the store as it was before the call after one that
     * should refuse (untouched by the undo).
     */
    suspend fun undoAll(run: CommandRun, expected: Map<String, Item>): UndoStep {
        val result = world.journal.undoAll(run.groupKey)
        val storeOk = world.store.snapshot() == expected
        return when (result) {
            is UndoResult.Complete -> UndoStep(result.code, result.restored.size, null, null, storeOk)
            is UndoResult.Refused ->
                UndoStep(result.code, null, result.blockers.size, result.blockers.first().reason.value, storeOk)
            is UndoResult.Partial ->
                UndoStep(result.code, result.restored.size, null, result.notRestored.first().reason.value, storeOk)
            is UndoResult.AlreadyUndone -> UndoStep(result.code, null, null, null, storeOk)
        }
    }

    override fun toString(): String = "UndoWorld"
}

/** The three scripted plans. Each names the seeded item by its id, which is never written to evidence. */
internal object UndoPlans {
    private fun step(id: String, tool: String, arguments: JsonObject): JsonObject = buildJsonObject {
        put("id", id)
        put("tool", tool)
        put("arguments", arguments)
    }

    private fun create(id: String, title: String, parentRef: String? = null): JsonObject = step(
        id,
        ITEM_TOOL_CREATE,
        buildJsonObject {
            put("title", title)
            if (parentRef != null) put("parent_id", parentRef)
        },
    )

    private fun rename(id: String, title: String): JsonObject = step(
        id,
        ITEM_TOOL_RENAME,
        buildJsonObject {
            put("id", SEED_ID)
            put("title", title)
        },
    )

    private fun plan(vararg steps: JsonObject): JsonObject = buildJsonObject {
        put("steps", buildJsonArray { steps.forEach { add(it) } })
    }

    /** Two creates (the second under the first through the step reference) and one rename of the seeded item. */
    fun whole(): JsonObject = plan(
        create("stepone", "first"),
        create("steptwo", "second", "\$stepone.id"),
        rename("stepthree", "renamed"),
    )
}

/** The pure verdict rules of the undo leg. */
internal object UndoVerdicts {
    /** PASS only when the whole-command undo restored every action and left the store as it was before the command. */
    fun judge(whole: CommandRun, wholeUndo: UndoStep): Verdict {
        val done = wholeUndo.result == UNDONE && wholeUndo.storeOk && wholeUndo.restored == EXPECTED_WHOLE_N
        val code = when {
            !done -> REASON_UNDO_INCOMPLETE
            whole.n != EXPECTED_WHOLE_N || whole.pending != 0 -> REASON_WRONG_COUNT
            else -> null
        }
        return if (code == null) Verdict.pass() else Verdict.fail(code)
    }

    private const val UNDONE = "complete"
}

/** What the second press produced. */
internal class UndoFinish(val verdict: Verdict, val extras: Map<String, Long>) {
    override fun toString(): String = "UndoFinish(verdict=$verdict)"
}

/**
 * The undo-all leg between its two presses. The first press ran the whole-command case and counted N; this holds that
 * world until the second press undoes it (and runs the refusal and partial cases in the same press).
 */
internal class UndoSession(private val leg: LegId, val whole: UndoWorld, private val wholeRun: CommandRun) {
    /** The whole-command outcome, for the readout. */
    val outcome: CommandOutcome get() = wholeRun.outcome

    /** The number the control shows and the held proposals shown apart. */
    val prompt: UndoPrompt = UndoPrompt(wholeRun.n, wholeRun.pending)

    /** The second press: undo the whole command through the journal and judge it. */
    suspend fun finish(sink: EvidenceSink): UndoFinish {
        val wholeUndo = whole.undoAll(wholeRun, whole.seeded)
        sink.emit(EvidenceLine.undo(leg, CASE_WHOLE, wholeUndo.facts("undone", wholeRun)))
        val extras = linkedMapOf("n" to wholeRun.n.toLong(), "restored" to (wholeUndo.restored ?: 0).toLong())
        return UndoFinish(UndoVerdicts.judge(wholeRun, wholeUndo), extras)
    }

    override fun toString(): String = "UndoSession(n=${wholeRun.n})"
}

/** The first press of the undo-all leg: runs the whole-command case offline and counts N. */
internal object UndoLeg {
    /** Runs the multi-action command, emits `VAE_UNDO phase=counted` and returns the session the second press finishes. */
    suspend fun start(sink: EvidenceSink, leg: LegId): UndoSession {
        val world = UndoWorld()
        val run = world.run(UndoPlans.whole(), UNDO_TRANSCRIPT_WHOLE, AdmitAllGate)
        sink.emit(EvidenceLine.undo(leg, CASE_WHOLE, run.facts("counted")))
        return UndoSession(leg, world, run)
    }
}
