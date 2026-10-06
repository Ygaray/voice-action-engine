package io.github.ygaray.voiceactionengine.spike.provider

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest

/**
 * Route B: the runtime's native tool calls. Every offered tool is handed over, the model decides, and the runtime
 * returns structured calls (it never executes them; the backend turns automatic tool calling off).
 */
internal object RouteB {
    /** Offers every tool in [tools], in order. The same system, user and token limit as Route A. */
    fun nativeRequest(system: String, user: String, tools: List<ToolSpec>, maxOutputTokens: Int): BackendRequest =
        BackendRequest(system, user, BackendMode.NativeTools(tools), true, maxOutputTokens)

    /**
     * Reads the runtime's answer. No tool call is a text-only `END_TURN`. Otherwise only the first call is taken (the
     * single-shot tier drops and traces extras); a name the request never offered is `malformed_output`, and arguments
     * that do not fit the tool's schema are `schema_type_mismatch` (the runtime decodes every number to a double, so an
     * integral double for an integer field is coerced, never rejected).
     */
    fun parse(answer: BackendAnswer, tools: List<ToolSpec>): ModelResult {
        val raw = answer.rawToolCalls.firstOrNull()
            ?: return success(listOf(AssistantPart.Text(answer.text)), StopReason.END_TURN, answer.bench)
        val tool = tools.firstOrNull { it.name == raw.name } ?: return failure(MALFORMED_OUTPUT)
        return try {
            val arguments = mapToJson(raw.arguments, tool.inputSchema)
            success(listOf(toolCall(tool.name, arguments)), StopReason.TOOL_USE, answer.bench)
        } catch (@Suppress("SwallowedException") e: SchemaTypeMismatch) {
            // The offending value is dropped on purpose: a model's argument text must never reach a failure.
            failure(SCHEMA_TYPE_MISMATCH)
        }
    }
}
