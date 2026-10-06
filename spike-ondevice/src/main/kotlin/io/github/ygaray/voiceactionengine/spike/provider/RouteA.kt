package io.github.ygaray.voiceactionengine.spike.provider

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Route A: constrained JSON through `ResponseFormat.json`. */
internal object RouteA {
    /** The forced shape: the schema is [tool]'s own input schema and the answer is that tool's arguments object. */
    fun forcedRequest(system: String, user: String, tool: ToolSpec, maxOutputTokens: Int): BackendRequest =
        BackendRequest(system, user, BackendMode.Constrained(tool.inputSchema), true, maxOutputTokens)

    /** Reads the answer text as the forced tool's arguments; anything that is not a JSON object is `malformed_output`. */
    fun parseForced(answer: BackendAnswer, toolName: String): ModelResult {
        val arguments = parseObject(answer.text) ?: return failure(MALFORMED_OUTPUT)
        return success(listOf(toolCall(toolName, arguments)), StopReason.TOOL_USE, answer.bench)
    }

    internal fun parseObject(text: String): JsonObject? =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (@Suppress("SwallowedException") e: SerializationException) {
            // The malformed text is dropped on purpose: it must never reach a failure or a log.
            null
        }
}
