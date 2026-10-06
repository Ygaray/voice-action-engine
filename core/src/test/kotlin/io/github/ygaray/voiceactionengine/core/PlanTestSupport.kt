package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal const val PLAN_CREATE_TOOL = "create_item"
internal const val PLAN_TAG_TOOL = "tag_item"
internal const val PLAN_FIND_TOOL = "find_item"
internal const val PLAN_CALL_ID = "plan-1"
internal const val PLAN_SYSTEM = "plan system"

/** A neutral mutating tool that creates one item and returns its id. */
internal fun createTool(): ToolSpec =
    ToolSpec(
        PLAN_CREATE_TOOL,
        "Creates one item. Returns item_id.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("name") { put("type", "string") } }
        },
        mutating = true,
    )

/** A neutral mutating tool that tags one existing item. */
internal fun tagTool(): ToolSpec =
    ToolSpec(
        PLAN_TAG_TOOL,
        "Tags one item.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("item_id") { put("type", "string") }
                putJsonObject("tag") { put("type", "string") }
            }
        },
        mutating = true,
    )

/** A neutral read tool that finds items. */
internal fun findTool(): ToolSpec =
    ToolSpec(
        PLAN_FIND_TOOL,
        "Finds items by name.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("name") { put("type", "string") } }
        },
        mutating = false,
    )

/** A snapshot offering [tools], with no single-shot tool (the plan tier forces its own). */
internal fun planSnapshot(vararg tools: ToolSpec): ToolingSnapshot = ToolingSnapshot(PLAN_SYSTEM, tools.toList(), null)

/** One step of a plan, as the model writes it. */
internal fun planStep(id: String, tool: String, arguments: JsonObject): JsonObject = buildJsonObject {
    put("id", id)
    put("tool", tool)
    put("arguments", arguments)
}

/** The arguments of a `submit_plan` call holding [steps], with `needs_lookup` when given. */
internal fun planArguments(vararg steps: JsonObject, needsLookup: Boolean? = null): JsonObject = buildJsonObject {
    put("steps", buildJsonArray { steps.forEach { add(it) } })
    if (needsLookup != null) put("needs_lookup", needsLookup)
}

/** A provider answer that calls `submit_plan` with [arguments]. */
internal fun planAnswer(
    arguments: JsonObject,
    callId: String = PLAN_CALL_ID,
    usage: Usage = Usage(1, 0, 0, 1),
): ModelResult = FakeAiProvider.toolCall(callId, "submit_plan", arguments, usage)

/** A plan tier with id [id], the fixed clock and [configure] applied last. */
internal fun planStrategy(
    executor: ToolExecutor,
    snapshot: ToolingSnapshot,
    id: String = "plan",
    configure: PlanThenExecuteStrategy.Builder.() -> Unit = {},
): PlanThenExecuteStrategy =
    PlanThenExecuteStrategy(StrategyId(id)) {
        tooling = ToolSpecProvider.fixed(snapshot)
        this.executor = executor
        clock = fixedClock
        configure()
    }

/**
 * An executor that answers every call with a mutation which applies as a success; [targetIdsFor] gives the ids the
 * applied call reports.
 */
internal fun committingExecutor(
    log: RecordingSink<String>?,
    targetIdsFor: (Extraction) -> Map<String, String> = { emptyMap() },
): ScriptedToolExecutor =
    ScriptedToolExecutor(log) { call, _ ->
        ToolStep.Mutation(
            FakeMutation(call.toolName, { StepResult("ok", false, "tok", targetIdsFor(call)) }, log = log),
        )
    }
