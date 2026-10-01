package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** JSON builders for Anthropic answers, shared by the transport, decoder and error-mapping tests. */

fun textBlock(text: String): JsonObject = buildJsonObject {
    put("type", "text")
    put("text", text)
}

fun thinkingBlock(thinking: String): JsonObject = buildJsonObject {
    put("type", "thinking")
    put("thinking", thinking)
    put("signature", "sig-1")
}

fun toolUseBlock(id: String, name: String, input: JsonElement): JsonObject = buildJsonObject {
    put("type", "tool_use")
    put("id", id)
    put("name", name)
    put("input", input)
}

fun usageJson(
    inputTokens: Long?,
    outputTokens: Long?,
    cacheCreation: Long? = 0,
    cacheRead: Long? = 0,
): JsonObject = buildJsonObject {
    putCount("input_tokens", inputTokens)
    putCount("output_tokens", outputTokens)
    if (cacheCreation != null) putCount("cache_creation_input_tokens", cacheCreation)
    if (cacheRead != null) putCount("cache_read_input_tokens", cacheRead)
}

private fun JsonObjectBuilder.putCount(key: String, value: Long?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun successBody(
    content: List<JsonElement>,
    stopReason: String?,
    usage: JsonObject? = usageJson(1, 1),
): String = buildJsonObject {
    put("id", "msg_1")
    put("type", "message")
    put("role", "assistant")
    put("model", "claude-haiku-4-5")
    put("content", buildJsonArray { content.forEach { add(it) } })
    if (stopReason != null) put("stop_reason", stopReason)
    if (usage != null) put("usage", usage)
}.toString()

fun errorBody(type: String, message: String, requestId: String?): String = buildJsonObject {
    put("type", "error")
    putJsonObject("error") {
        put("type", type)
        put("message", message)
    }
    if (requestId != null) put("request_id", requestId)
}.toString()

/** A request for [model] carrying the table capabilities the real provider would report for it. */
fun anthropicRequest(model: String, request: ModelRequest, key: String?): ProviderRequest = ProviderRequest(
    model,
    request,
    key?.let { Credential(ProviderId.ANTHROPIC, it) },
    AnthropicModels.capabilities(model),
)
