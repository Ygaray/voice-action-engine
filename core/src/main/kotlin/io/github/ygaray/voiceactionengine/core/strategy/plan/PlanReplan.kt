package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val IGNORED_CALL_NOTICE = """{"status":"error","reason":"ignored_call"}"""

/**
 * The request for the one replan: the same conversation, one turn longer. Everything that forms the cached prefix
 * (the system text, the tools, the tool choice) and every setting is copied from [first] unchanged, and the first
 * user message is reused, not rendered again. The model then sees its own answer and the engine's [digest] as the
 * result of its planning call; every other call of that answer is answered with a fixed notice, so no call id is left
 * without a result.
 *
 * Returns null when the continuation cannot be built: the answer has no call, a blank call id or a repeated call id.
 * Never throws. Neither the digest nor the notice carries app content or model text.
 */
internal fun replanRequest(first: ModelRequest, answer: AssistantMessage, digest: String): ModelRequest? {
    val calls = answer.toolCalls
    val ids = calls.map { it.id }
    val buildable = calls.isNotEmpty() && ids.none { it.isBlank() } && ids.toSet().size == ids.size
    if (!buildable) return null
    val results = calls.mapIndexed { index, call ->
        ToolResult(call.id, if (index == 0) digest else IGNORED_CALL_NOTICE, true)
    }
    return ModelRequest(
        first.system,
        listOf(first.messages.first(), answer, ToolResultsMessage(results)),
        first.tools,
        first.toolChoice,
        first.maxTokens,
        first.cache,
        first.singleToolCall,
        first.reasoning,
    )
}

/**
 * What the engine tells the model about a rejected plan: compact JSON in a fixed vocabulary. [reason] is one of the
 * engine's own codes and [stepIndex] the engine's 0-based index of the step at fault, or null when the whole plan is.
 */
internal fun rejectionDigest(reason: String, stepIndex: Int?): String =
    buildJsonObject {
        put("status", "plan_rejected")
        put("reason", reason)
        put("step_index", stepIndex?.let { JsonPrimitive(it) } ?: JsonNull)
    }.toString()
