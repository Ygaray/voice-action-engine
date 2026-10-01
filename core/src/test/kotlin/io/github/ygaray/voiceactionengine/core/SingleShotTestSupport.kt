package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal const val ENTRIES_TOOL = "record_entries"
internal const val ASK_TOOL = "ask_user"
internal const val SINGLE_SHOT_SYSTEM = "fixed system"

/** 2026-10-01 16:30:12 UTC, so a rendered user turn is a known string. */
internal val fixedClock: Clock = Clock.fixed(Instant.parse("2026-10-01T16:30:12Z"), ZoneId.of("UTC"))

/** A neutral mutating tool that records one or more entries. */
internal fun entriesTool(): ToolSpec =
    ToolSpec(
        ENTRIES_TOOL,
        "Records one or more entries.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("entries") { put("type", "array") }
                putJsonObject("target_date") { put("type", "string") }
            }
        },
        true,
    )

/** The terminal clarification tool. */
internal fun askTool(): ToolSpec = ToolSpec.clarification(ASK_TOOL)

/** Arguments for a call to [entriesTool]. */
internal fun entriesArguments(marker: String): JsonObject = buildJsonObject {
    put("entries", buildJsonArray { })
    put("target_date", marker)
}

/** A snapshot offering [tools], forcing [forced] (null for none). */
internal fun snapshotOf(vararg tools: ToolSpec, forced: String? = ENTRIES_TOOL): ToolingSnapshot =
    ToolingSnapshot(SINGLE_SHOT_SYSTEM, tools.toList(), forced)

/** A resolver that records everything it is asked and answers with [answer]. */
internal class RecordingResolver(
    private val answer: suspend (Extraction, CommandInput) -> Resolution,
) : OutcomeResolver {
    private val seenExtractions = CopyOnWriteArrayList<Extraction>()
    private val seenInputs = CopyOnWriteArrayList<CommandInput>()
    private val count = AtomicInteger()

    /** Every extraction received, in order. */
    val extractions: List<Extraction> get() = seenExtractions.toList()

    /** Every input received, in order. */
    val inputs: List<CommandInput> get() = seenInputs.toList()

    /** How many times the resolver ran. */
    val invocations: Int get() = count.get()

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        count.incrementAndGet()
        seenExtractions.add(extraction)
        seenInputs.add(input)
        return answer(extraction, input)
    }
}

/** A single-shot tier with id `single_shot`, the fixed clock and [configure] applied last. */
internal fun singleShot(
    resolver: OutcomeResolver,
    snapshot: ToolingSnapshot,
    configure: SingleShotStrategy.Builder.() -> Unit = {},
): SingleShotStrategy =
    SingleShotStrategy(StrategyId("single_shot")) {
        tooling = ToolSpecProvider.fixed(snapshot)
        this.resolver = resolver
        clock = fixedClock
        configure()
    }

/** A pipeline over [tiers] wired like the routed tests: Anthropic selected, a test key, a fixed policy. */
internal fun pipelineOf(
    tiers: List<CommandStrategy>,
    fake: FakeAiProvider,
    gate: ScriptedGate,
    sink: RecordingCommitSink,
    listener: RecordingEventListener? = null,
    policy: TierPolicy = TierPolicy.DEFAULT,
    credentials: ScriptedCredentialSource = testKey(),
): CommandPipeline {
    val ids = AtomicInteger()
    return commandPipeline {
        tiers.forEach { tier(it) }
        provider(fake)
        providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model"))
        this.credentials = credentials
        this.policy = TierPolicySource.fixed(policy)
        this.gate = gate
        commitSink = sink
        this.listener = listener
        runIds = { "run-${ids.incrementAndGet()}" }
    }
}

/** The usual credentials: a key for Anthropic. */
internal fun testKey(): ScriptedCredentialSource = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "test-key")
