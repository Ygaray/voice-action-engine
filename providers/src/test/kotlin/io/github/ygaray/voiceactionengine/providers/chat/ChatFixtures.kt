package io.github.ygaray.voiceactionengine.providers.chat

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** JSON builders for Chat Completions answers and error bodies, shared by the decoder, transport and parity tests. */

internal const val CHAT_GOLDEN_MODEL = "gpt-5.4-mini"

/** One `tool_calls` entry as the API sends it: arguments are a JSON-encoded string. */
internal fun chatToolCall(id: String, name: String, arguments: String): JsonObject = buildJsonObject {
    put("id", id)
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("arguments", arguments)
    }
}

/** An assistant message; a null content or refusal is written as JSON null, as the API does. */
internal fun chatMessage(
    content: String?,
    toolCalls: List<JsonObject> = emptyList(),
    refusal: String? = null,
): JsonObject = buildJsonObject {
    put("role", "assistant")
    if (content == null) put("content", JsonNull) else put("content", content)
    if (refusal == null) put("refusal", JsonNull) else put("refusal", refusal)
    if (toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray { toolCalls.forEach { add(it) } })
}

/** A `usage` object; a null count is written as JSON null and a null cache count leaves that detail out. */
internal fun chatUsage(prompt: Long?, completion: Long?, cached: Long? = null, cacheWrite: Long? = null): JsonObject =
    buildJsonObject {
        putCount("prompt_tokens", prompt)
        putCount("completion_tokens", completion)
        if (prompt != null && prompt >= 0) put("total_tokens", prompt + (completion ?: 0))
        if (cached != null || cacheWrite != null) {
            putJsonObject("prompt_tokens_details") {
                if (cached != null) put("cached_tokens", cached)
                if (cacheWrite != null) put("cache_write_tokens", cacheWrite)
            }
        }
    }

private fun JsonObjectBuilder.putCount(key: String, value: Long?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

/** A 200 answer body with one choice. A null finish reason is written as JSON null. */
internal fun chatBody(
    message: JsonObject,
    finishReason: String?,
    usage: JsonObject? = null,
    id: String = "chatcmpl-GOLDEN",
    nativeFinishReason: String? = null,
): String = buildJsonObject {
    put("id", id)
    put("object", "chat.completion")
    put("created", 0)
    put("model", CHAT_GOLDEN_MODEL)
    putJsonArray("choices") {
        add(
            buildJsonObject {
                put("index", 0)
                put("message", message)
                if (finishReason == null) put("finish_reason", JsonNull) else put("finish_reason", finishReason)
                if (nativeFinishReason != null) put("native_finish_reason", nativeFinishReason)
            },
        )
    }
    if (usage != null) put("usage", usage)
}.toString()

/** OpenRouter's error object inside a 200 answer: a numeric HTTP-like code, with the upstream's raw text optional. */
internal fun chatErrorEnvelope(code: Int, message: String, raw: String? = null): String = buildJsonObject {
    putJsonObject("error") {
        put("code", code)
        put("message", message)
        if (raw != null) {
            putJsonObject("metadata") {
                put("raw", raw)
                put("provider_name", "Example")
            }
        }
    }
}.toString()

/** OpenAI's error body: a string code (or null), a type and a message. */
internal fun openAiErrorBody(type: String, code: String?, message: String): String = buildJsonObject {
    putJsonObject("error") {
        put("message", message)
        put("type", type)
        put("param", JsonNull)
        if (code == null) put("code", JsonNull) else put("code", code)
    }
}.toString()
