package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// Neutral, CT-shaped acceptance fixtures. Every threshold lives in this file and rides the opaque mutation context;
// nothing here is known to the engine.
private const val CONFIDENCE_FLOOR = 0.7
private const val SCORE_FLOOR = 0.8

internal const val FLAG_NON_POSITIVE_QUANTITY = "non_positive_quantity"
internal const val FLAG_UNMATCHED = "unmatched"
internal const val TARGET_KEY = "item"

/** One entry the model reports: what it is, how much, in which unit, and how sure the model is. */
internal class FixtureEntry(
    val label: String,
    val quantity: Double,
    val unit: String,
    val confidence: Double,
)

/** Arguments for a call to the `record_entries` tool: an array of entries and one shared target date. */
internal fun entriesArgs(targetDate: String, vararg entries: FixtureEntry): JsonObject = buildJsonObject {
    put(
        "entries",
        buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("label", entry.label)
                        put("quantity", entry.quantity)
                        put("unit", entry.unit)
                        put("confidence", entry.confidence)
                    },
                )
            }
        },
    )
    put("target_date", targetDate)
}

/** One row of the app's catalog. */
internal class CatalogItem(val id: String, val label: String, val score: Double)

/** The app's catalog: the best row for a label, or null when no row carries that label. */
internal class FixtureCatalog(private val items: List<CatalogItem>) {
    /** The highest scoring item whose label equals [label] (ignoring case), or null when unmatched. */
    fun match(label: String): CatalogItem? =
        items.filter { it.label.equals(label, ignoreCase = true) }.maxByOrNull { it.score }
}

/** The usual catalog: three strong rows and one weak one. */
internal fun fixtureCatalog(): FixtureCatalog =
    FixtureCatalog(
        listOf(
            CatalogItem("item-a", "alpha", 0.92),
            CatalogItem("item-b", "beta", 0.9),
            CatalogItem("item-c", "gamma", 0.88),
            CatalogItem("item-d", "delta", 0.4),
        ),
    )

/**
 * The app's per-entry verdict, carried in each mutation's opaque context. The gate reads only this.
 */
internal class EntryVerdict(
    val confidence: Double,
    val score: Double?,
    val unresolved: Boolean,
    val flags: List<String>,
) {
    /** True when the entry needs the user's confirmation under the fixture thresholds. */
    val weak: Boolean
        get() = unresolved || flags.isNotEmpty() || confidence < CONFIDENCE_FLOOR || (score ?: 0.0) < SCORE_FLOOR

    override fun toString(): String = "EntryVerdict(weak=$weak, flags=${flags.size})"
}

/** How a test makes one row fail. */
internal enum class FailureMode { ERROR_RESULT, THROW }

/** One pending entry write. It counts its applies and logs a line for each real write. */
internal class EntryMutation(
    val label: String,
    val quantity: Double,
    val unit: String,
    val targetDate: String,
    val matchId: String?,
    val verdict: EntryVerdict,
    private val log: RecordingSink<String>,
    private val failure: FailureMode? = null,
) : PendingMutation {
    private val applies = AtomicInteger()

    override val toolName: String = ENTRIES_TOOL

    override val targetIds: Map<String, String> = if (matchId == null) emptyMap() else mapOf(TARGET_KEY to matchId)

    override val context: Any = verdict

    /** How many times apply ran. */
    val applyCount: Int
        get() = applies.get()

    override suspend fun apply(): StepResult {
        applies.incrementAndGet()
        return when {
            failure == FailureMode.THROW -> error("fixture write failed")
            failure == FailureMode.ERROR_RESULT -> StepResult("write failed", true, "error", emptyMap())
            matchId == null -> StepResult("skipped_unresolved", false, "skipped", emptyMap())
            else -> {
                log.record("applied:$label:$quantity:$matchId:$targetDate")
                StepResult("saved", false, "ok", mapOf(TARGET_KEY to matchId))
            }
        }
    }
}

/** A strong verdict for tests that build replacement mutations by hand. */
internal fun strongVerdict(): EntryVerdict = EntryVerdict(0.95, 0.9, false, emptyList())

/**
 * The app-side resolver: it applies the fixture rules (confidence floor, match score floor, non-positive quantity),
 * stamps an [EntryVerdict] into each mutation's context, and never applies anything.
 *
 * @param failures rows (by label) that fail when applied.
 * @param reply the reply text of the resolved steps.
 */
internal class FixtureResolver(
    private val catalog: FixtureCatalog,
    private val log: RecordingSink<String>,
    private val failures: Map<String, FailureMode> = emptyMap(),
    private val reply: String? = null,
) : OutcomeResolver {
    private val seenExtractions = CopyOnWriteArrayList<Extraction>()
    private val produced = CopyOnWriteArrayList<EntryMutation>()
    private val count = AtomicInteger()

    /** Every extraction received, in order. */
    val extractions: List<Extraction> get() = seenExtractions.toList()

    /** How many times the resolver ran. */
    val invocations: Int get() = count.get()

    /** Every mutation the resolver made, in order. */
    val mutations: List<EntryMutation> get() = produced.toList()

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        count.incrementAndGet()
        seenExtractions.add(extraction)
        val entries = extraction.arguments["entries"] as? JsonArray ?: JsonArray(emptyList())
        val date = (extraction.arguments["target_date"] as? JsonPrimitive)?.content.orEmpty()
        val made = entries.mapNotNull { (it as? JsonObject)?.let { row -> mutationOf(row, date) } }
        produced.addAll(made)
        return if (made.isEmpty()) Resolution.NoMatch() else Resolution.Steps(listOf(ToolStep.Mutation(made)), reply)
    }

    private fun mutationOf(row: JsonObject, date: String): EntryMutation {
        val label = (row["label"] as? JsonPrimitive)?.content.orEmpty()
        val quantity = (row["quantity"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val unit = (row["unit"] as? JsonPrimitive)?.content.orEmpty()
        val confidence = (row["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val match = catalog.match(label)
        val flags = buildList {
            if (quantity <= 0.0) add(FLAG_NON_POSITIVE_QUANTITY)
            if (match == null) add(FLAG_UNMATCHED)
        }
        val verdict = EntryVerdict(confidence, match?.score, match == null, flags)
        return EntryMutation(label, quantity, unit, date, match?.id, verdict, log, failures[label])
    }
}

/**
 * A defer-mode threshold gate: it holds a batch of two or more, holds a single weak or unresolved entry, and admits
 * anything else. It reads only the opaque context the resolver stamped.
 */
internal fun deferModeGate(): ScriptedGate =
    ScriptedGate { proposal ->
        val mutations = proposal.mutations
        val verdict = mutations.singleOrNull()?.context as? EntryVerdict
        when {
            mutations.size >= 2 -> GateDecision.Hold("batch_confirm", "needs_confirm")
            verdict?.weak == true -> GateDecision.Hold("weak_match", "needs_confirm")
            else -> GateDecision.Admit()
        }
    }

/** A single-shot tier that forces `record_entries`, offers the clarification tool and resolves with [resolver]. */
internal fun fixtureTier(resolver: OutcomeResolver, id: String = "single_shot"): SingleShotStrategy =
    SingleShotStrategy(StrategyId(id)) {
        tooling = ToolSpecProvider.fixed(
            ToolingSnapshot(SINGLE_SHOT_SYSTEM, listOf(entriesTool(), askTool()), ENTRIES_TOOL),
        )
        this.resolver = resolver
        clock = fixedClock
    }

/** A pipeline over [tiers] with the standard routed wiring. */
internal fun acceptancePipeline(
    tiers: List<CommandStrategy>,
    fake: FakeAiProvider,
    gate: ScriptedGate,
    sink: RecordingCommitSink,
    listener: RecordingEventListener? = null,
): CommandPipeline = pipelineOf(tiers, fake, gate, sink, listener)
