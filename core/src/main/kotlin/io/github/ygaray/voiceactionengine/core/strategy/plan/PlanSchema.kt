package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val PLAN_TOOL_NAME = "submit_plan"
private const val TYPE_KEY = "type"
private const val PROPERTIES_KEY = "properties"
private const val REQUIRED_KEY = "required"
private const val ITEMS_KEY = "items"
private const val ENUM_KEY = "enum"
private const val ITEM_LIMIT_KEY = "maxItems"
private const val TYPE_OBJECT = "object"
private const val TYPE_STRING = "string"
private const val TYPE_ARRAY = "array"
private const val TYPE_BOOLEAN = "boolean"
private const val STEPS_FIELD = "steps"
private const val NEEDS_LOOKUP_FIELD = "needs_lookup"
private const val ID_FIELD = "id"
private const val TOOL_FIELD = "tool"
private const val ARGUMENTS_FIELD = "arguments"

// The description is part of the cached prefix and of what consumers write planning prompts against: it is frozen at
// the tag, so a change needs a recorded probe failure and a gap plan.
private const val PLAN_DESCRIPTION =
    "Submit the whole plan for the command as ordered steps, each calling one of the listed tools with its " +
        "arguments. Give every step a short id that starts with a letter and uses only letters, digits, _ or -. " +
        "To use a value that an earlier step's tool returns, write the whole argument value as the string " +
        "\$<stepId>.<key>, with the key names given in that tool's description. A reference is the entire value, " +
        "never part of a longer string, and only names a step listed earlier. Do not call any other tool. " +
        "When the command needs information you do not have, for example something that must be looked up first, " +
        "set needs_lookup to true and leave steps empty."

/** The name of the engine-owned tool the model calls to submit its plan. */
internal fun planToolName(): String = PLAN_TOOL_NAME

/** The names of the tools a plan step may call: every non-terminal snapshot tool, reads included, in snapshot order. */
internal fun stepToolNames(tools: List<ToolSpec>): List<String> = tools.filter { !it.terminal }.map { it.name }

/**
 * The engine-owned `submit_plan` tool for [tools], with at most [maxSteps] steps. It is not mutating, not terminal and
 * not strict: the engine validates the plan itself. Key order is part of the contract: the schema string is
 * byte-stable so provider prompt caches keep hitting.
 */
internal fun submitPlanSpec(tools: List<ToolSpec>, maxSteps: Int): ToolSpec =
    ToolSpec(
        PLAN_TOOL_NAME,
        PLAN_DESCRIPTION,
        planSchema(stepToolNames(tools), maxSteps),
        mutating = false,
        terminal = false,
        strict = false,
    )

private fun planSchema(toolNames: List<String>, maxSteps: Int): JsonObject = buildJsonObject {
    put(TYPE_KEY, TYPE_OBJECT)
    putJsonObject(PROPERTIES_KEY) {
        putJsonObject(STEPS_FIELD) {
            put(TYPE_KEY, TYPE_ARRAY)
            put(ITEM_LIMIT_KEY, maxSteps)
            put(ITEMS_KEY, stepSchema(toolNames))
        }
        putJsonObject(NEEDS_LOOKUP_FIELD) { put(TYPE_KEY, TYPE_BOOLEAN) }
    }
    putJsonArray(REQUIRED_KEY) { add(STEPS_FIELD) }
}

private fun stepSchema(toolNames: List<String>): JsonObject = buildJsonObject {
    put(TYPE_KEY, TYPE_OBJECT)
    putJsonObject(PROPERTIES_KEY) {
        putJsonObject(ID_FIELD) { put(TYPE_KEY, TYPE_STRING) }
        putJsonObject(TOOL_FIELD) {
            put(TYPE_KEY, TYPE_STRING)
            putJsonArray(ENUM_KEY) { toolNames.forEach { add(it) } }
        }
        putJsonObject(ARGUMENTS_FIELD) { put(TYPE_KEY, TYPE_OBJECT) }
    }
    putJsonArray(REQUIRED_KEY) {
        add(ID_FIELD)
        add(TOOL_FIELD)
        add(ARGUMENTS_FIELD)
    }
}
