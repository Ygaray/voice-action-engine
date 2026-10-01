package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.atomic.AtomicInteger

internal const val LOOP_SYSTEM = "loop system"
internal const val SAVE_TOOL = "save_entry"
internal const val FIND_TOOL = "find_entries"

/** A mutating tool with a small object schema. */
internal fun writeTool(name: String = SAVE_TOOL): ToolSpec =
    ToolSpec(name, "Writes an entry.", smallSchema(), true)

/** A read tool with a small object schema. */
internal fun readTool(name: String = FIND_TOOL): ToolSpec =
    ToolSpec(name, "Reads entries.", smallSchema(), false)

private fun smallSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("text") { put("type", "string") }
    }
}

/** Arguments for a call to any of the small tools. */
internal fun loopArguments(marker: String = "m"): JsonObject = buildJsonObject { put("text", marker) }

/** A snapshot offering [tools] with the loop's system text and no single-shot tool. */
internal fun loopSnapshotOf(vararg tools: ToolSpec): ToolingSnapshot =
    ToolingSnapshot(LOOP_SYSTEM, tools.toList(), null)

/** An agentic tier with id [id], the fixed clock and [configure] applied last. */
internal fun agenticLoop(
    executor: ToolExecutor,
    snapshot: ToolingSnapshot,
    id: String = "agentic",
    configure: AgenticLoopStrategy.Builder.() -> Unit = {},
): AgenticLoopStrategy =
    AgenticLoopStrategy(StrategyId(id)) {
        tooling = ToolSpecProvider.fixed(snapshot)
        this.executor = executor
        clock = fixedClock
        configure()
    }

/** A pipeline over [tiers] with [providerId] selected and a test key for it. */
internal fun loopPipeline(
    tiers: List<CommandStrategy>,
    fake: FakeAiProvider,
    gate: ScriptedGate,
    sink: RecordingCommitSink,
    listener: RecordingEventListener? = null,
    policy: TierPolicy = TierPolicy.DEFAULT,
    providerId: ProviderId = ProviderId.ANTHROPIC,
): CommandPipeline {
    val ids = AtomicInteger()
    return commandPipeline {
        tiers.forEach { tier(it) }
        provider(fake)
        providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(providerId, "test-model"))
        credentials = ScriptedCredentialSource.keys(providerId to "test-key")
        this.policy = TierPolicySource.fixed(policy)
        this.gate = gate
        commitSink = sink
        this.listener = listener
        runIds = { "run-${ids.incrementAndGet()}" }
    }
}

/** Usage that counts [total] tokens toward the ceiling. */
internal fun usage(total: Long): Usage = Usage(0, 0, 0, total)

/** A tool turn that calls [calls] in order. */
internal fun toolTurn(total: Long, vararg calls: AssistantPart.ToolCall): ModelResult =
    FakeAiProvider.toolCalls(usage(total), *calls)
