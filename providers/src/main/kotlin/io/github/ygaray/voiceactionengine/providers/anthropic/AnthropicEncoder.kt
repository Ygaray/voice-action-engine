package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val CACHE_CONTROL = "cache_control"
private const val EPHEMERAL = "ephemeral"

/**
 * Encodes one request as the Messages API body.
 *
 * The bytes are a pure function of the request: top-level keys are always written as `model`, `max_tokens`, `tools`,
 * `tool_choice`, `system`, `messages`, so everything the provider caches (tools, then system) forms an identical prefix
 * for every call that shares them. Per-request content (the user's words, language, date) only ever appears under
 * `messages`. Nothing outside those keys is sent.
 */
internal fun encodeAnthropicRequest(call: ProviderRequest): ByteArray {
    val request = call.request
    val tools = request.tools.sortedBy { it.name }
    val breakpoint = request.cache.staticPrefix && call.capabilities.caching == CachingMode.EXPLICIT_BREAKPOINTS
    val hasSystem = request.system.isNotBlank()
    val body = buildJsonObject {
        put("model", call.model)
        put("max_tokens", request.maxTokens)
        if (tools.isNotEmpty()) {
            put("tools", encodeTools(tools, breakpointOnLastTool = breakpoint && !hasSystem))
            put("tool_choice", encodeToolChoice(call))
        }
        if (hasSystem) put("system", encodeSystem(request.system, breakpoint))
        put("messages", encodeMessages(call))
    }
    return Json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
}

private fun encodeTools(tools: List<ToolSpec>, breakpointOnLastTool: Boolean): JsonArray = buildJsonArray {
    tools.forEachIndexed { index, tool ->
        addJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            // The app's schema object goes out untouched: its key order is part of the cached prefix.
            put("input_schema", tool.inputSchema)
            if (tool.strict == true) put("strict", true)
            if (breakpointOnLastTool && index == tools.lastIndex) putEphemeralBreakpoint()
        }
    }
}

private fun encodeToolChoice(call: ProviderRequest): JsonObject {
    val choice = call.request.toolChoice
    return buildJsonObject {
        if (choice is ToolChoice.Required && call.capabilities.supportsForcedToolChoice) {
            put(TYPE, "tool")
            put("name", choice.toolName)
        } else {
            put(TYPE, "auto")
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
