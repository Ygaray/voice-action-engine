package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val CACHE_CONTROL = "cache_control"
private const val EPHEMERAL = "ephemeral"
private const val ROLE = "role"
private const val ROLE_USER = "user"
private const val CONTENT = "content"
private const val TYPE = "type"
private const val TEXT = "text"

// The most tools one request may mark strict; Anthropic rejects a request with more.
private const val MAX_STRICT_TOOLS = 20

/**
 * Encodes one request as the Messages API body.
 *
 * The bytes are a pure function of the request: top-level keys are always written as `model`, `max_tokens`, `tools`,
 * `tool_choice`, `system`, `messages`, so everything the provider caches (tools, then system) forms an identical prefix
 * for every call that shares them. Per-request content (the user's words, language, date) only ever appears under
 * `messages`. Nothing outside those keys is sent.
 *
 * With [reshape] the request asks for a tool the model may not be forced to call: the tool choice is `auto`, the tools
 * that can take strict mode get it, and one line naming the required tool closes the last user message. The system text
 * and the tool definitions are otherwise untouched.
 */
internal fun encodeAnthropicRequest(call: ProviderRequest, reshape: Boolean = false): ByteArray {
    val request = call.request
    val tools = request.tools.sortedBy { it.name }
    val breakpoint = request.cache.staticPrefix && call.capabilities.caching == CachingMode.EXPLICIT_BREAKPOINTS
    val hasSystem = request.system.isNotBlank()
    val body = buildJsonObject {
        put("model", call.model)
        put("max_tokens", request.maxTokens)
        if (tools.isNotEmpty()) {
            put("tools", encodeTools(tools, strictToolNames(tools, reshape), breakpoint && !hasSystem))
            put("tool_choice", encodeToolChoice(call, reshape))
        }
        if (hasSystem) put("system", encodeSystem(request.system, breakpoint))
        put("messages", encodeMessages(call).withInstruction(requiredToolInstruction(call).takeIf { reshape }))
    }
    return Json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
}

private fun encodeTools(
    tools: List<ToolSpec>,
    strictNames: Set<String>,
    breakpointOnLastTool: Boolean,
): JsonArray = buildJsonArray {
    tools.forEachIndexed { index, tool ->
        addJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            // The app's schema object goes out untouched: its key order is part of the cached prefix.
            put("input_schema", tool.inputSchema)
            if (tool.name in strictNames) put("strict", true)
            if (breakpointOnLastTool && index == tools.lastIndex) putEphemeralBreakpoint()
        }
    }
}

// An explicit true is always sent as asked. In a reshaped request the engine also marks every tool the app left
// undecided when its schema fits the strict subset, up to the per-request limit; false is never made strict.
private fun strictToolNames(tools: List<ToolSpec>, reshape: Boolean): Set<String> {
    val asked = tools.filter { it.strict == true }.map { it.name }
    if (!reshape) return asked.toSet()
    val room = (MAX_STRICT_TOOLS - asked.size).coerceAtLeast(0)
    val added = tools.filter { it.strict == null && isAnthropicStrictEligible(it.inputSchema) }.take(room)
    return asked.toSet() + added.map { it.name }
}

private fun encodeToolChoice(call: ProviderRequest, reshape: Boolean): JsonObject {
    val choice = call.request.toolChoice
    return buildJsonObject {
        if (choice is ToolChoice.Required && !reshape && call.capabilities.supportsForcedToolChoice) {
            put(TYPE, "tool")
            put("name", choice.toolName)
        } else {
            put(TYPE, "auto")
        }
    }
}

private fun requiredToolInstruction(call: ProviderRequest): String? =
    (call.request.toolChoice as? ToolChoice.Required)?.let { "Call the ${it.toolName} tool with your result." }

// The line is one more text block at the very end of the last user-role message, after any tool results.
private fun JsonArray.withInstruction(instruction: String?): JsonArray {
    val target = indexOfLast { ((it as? JsonObject)?.get(ROLE) as? JsonPrimitive)?.contentOrNull == ROLE_USER }
    if (instruction == null || target < 0) return this
    return buildJsonArray {
        this@withInstruction.forEachIndexed { index, message ->
            add(if (index == target) (message as JsonObject).withTrailingText(instruction) else message)
        }
    }
}

private fun JsonObject.withTrailingText(text: String): JsonObject = buildJsonObject {
    this@withTrailingText.forEach { (key, value) ->
        if (key == CONTENT && value is JsonArray) {
            put(key, buildJsonArray { value.forEach { add(it) }; addJsonObject { textBlock(text) } })
        } else {
            put(key, value)
        }
    }
}

private fun encodeSystem(system: String, breakpoint: Boolean): JsonArray = buildJsonArray {
    addJsonObject {
        put(TYPE, TEXT)
        put(TEXT, system)
        if (breakpoint) putEphemeralBreakpoint()
    }
}

private fun JsonObjectBuilder.putEphemeralBreakpoint() {
    putJsonObject(CACHE_CONTROL) { put(TYPE, EPHEMERAL) }
}
