package io.github.ygaray.voiceactionengine.spike.provider

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The wrapper value with which the model declines: no offered tool applies to the command. */
internal const val NONE_TOOL = "none"

private const val TOOL_FIELD = "tool"
private const val ARGUMENTS_FIELD = "arguments"

/**
 * Route A: constrained JSON through `ResponseFormat.json`.
 *
 * With a forced tool the schema is that tool's own input schema and the answer is its arguments object. When the model
 * chooses, the schema is the wrapper `{tool: enum of the offered names plus none, arguments: object}`, so the model can
 * decline with `none`. A tool actually named `none` would be indistinguishable from a decline; the spike's tool sets
 * never use that name.
 */
internal object RouteA {
    /** The forced shape: the schema is [tool]'s own input schema and the answer is that tool's arguments object. */
    fun forcedRequest(system: String, user: String, tool: ToolSpec, maxOutputTokens: Int): BackendRequest =
        BackendRequest(system, user, BackendMode.Constrained(tool.inputSchema), true, maxOutputTokens)

    /** The model-chooses shape: the wrapper schema over [tools], in order, plus `none`. */
    fun autoRequest(system: String, user: String, tools: List<ToolSpec>, maxOutputTokens: Int): BackendRequest =
        BackendRequest(system, user, BackendMode.Constrained(wrapperSchema(tools)), true, maxOutputTokens)

    internal fun wrapperSchema(tools: List<ToolSpec>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject(TOOL_FIELD) {
                put("type", "string")
                putJsonArray("enum") {
                    tools.forEach { add(JsonPrimitive(it.name)) }
                    add(JsonPrimitive(NONE_TOOL))
                }
            }
            putJsonObject(ARGUMENTS_FIELD) { put("type", "object") }
        }
        putJsonArray("required") {
            add(JsonPrimitive(TOOL_FIELD))
            add(JsonPrimitive(ARGUMENTS_FIELD))
        }
    }

    /** Reads the answer text as the forced tool's arguments; anything that is not a JSON object is `malformed_output`. */
    fun parseForced(answer: BackendAnswer, toolName: String): ModelResult {
        val arguments = parseObject(answer.text) ?: return failure(MALFORMED_OUTPUT)
        return success(listOf(toolCall(toolName, arguments)), StopReason.TOOL_USE, answer.bench)
    }

    /**
     * Reads the answer text as the wrapper. `none` is a text-only answer with `END_TURN` (the decline); a tool the
     * request never offered, a missing field or a non-object is `malformed_output`.
     */
    fun parseAuto(answer: BackendAnswer, tools: List<ToolSpec>): ModelResult {
        val wrapper = parseObject(answer.text) ?: return failure(MALFORMED_OUTPUT)
        val name = (wrapper[TOOL_FIELD] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return failure(MALFORMED_OUTPUT)
        if (name == NONE_TOOL) return success(listOf(AssistantPart.Text("")), StopReason.END_TURN, answer.bench)
        val arguments = wrapper[ARGUMENTS_FIELD] as? JsonObject
        return if (arguments == null || tools.none { it.name == name }) {
            failure(MALFORMED_OUTPUT)
        } else {
            success(listOf(toolCall(name, arguments)), StopReason.TOOL_USE, answer.bench)
        }
    }

    internal fun parseObject(text: String): JsonObject? =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (@Suppress("SwallowedException") e: SerializationException) {
            // The malformed text is dropped on purpose: it must never reach a failure or a log.
            null
        }
}
