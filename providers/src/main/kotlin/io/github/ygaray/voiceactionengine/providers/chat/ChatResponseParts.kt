package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val KEY_FUNCTION = "function"
private const val KEY_TOOL_CALLS = "tool_calls"
private const val KEY_PROMPT_TOKENS = "prompt_tokens"
private const val KEY_COMPLETION_TOKENS = "completion_tokens"
private const val KEY_PROMPT_DETAILS = "prompt_tokens_details"
private const val KEY_CACHED_TOKENS = "cached_tokens"
private const val KEY_CACHE_WRITE_TOKENS = "cache_write_tokens"

/** Carries the typed reason for a 2xx answer that cannot be used; the message never holds any body text. */
internal class ChatMalformed(val reason: FailureReason) : IllegalArgumentException("unusable model answer")

/** Abandons decoding with a reason-only [ChatMalformed]; [decodeChatResponse] turns it into a failure. */
internal fun unusableAnswer(reason: FailureReason = FailureReason.MalformedResponse()): Nothing =
    throw ChatMalformed(reason)

/**
 * Reads the tool calls of an assistant [message]. A call whose `type` is present and is not `function` is skipped. The
 * call id and name must be non-blank, and the arguments must be a JSON object, sent either as a string holding one
 * (the empty string means no arguments) or, by some routed upstreams, as an object. The decoder never consults a tool
 * schema, so the arguments hold exactly the keys the model sent.
 *
 * @throws ChatMalformed when a call is unusable: [FailureReason.MalformedResponse] for a bad shape, id or name,
 * [FailureReason.MalformedToolArgs] for arguments that are not a JSON object.
 */
internal fun decodeChatToolCalls(message: JsonObject): List<AssistantPart.ToolCall> {
    val calls = message[KEY_TOOL_CALLS] as? JsonArray ?: return emptyList()
    return calls.mapNotNull { decodeToolCall(it) }
}

private fun decodeToolCall(element: JsonElement): AssistantPart.ToolCall? {
    val call = element as? JsonObject ?: unusableAnswer()
    val type = chatStringField(call, "type")
    if (type != null && type != KEY_FUNCTION) return null
    val function = call[KEY_FUNCTION] as? JsonObject ?: unusableAnswer()
    val id = chatStringField(call, "id")?.takeIf { it.isNotBlank() }
    val name = chatStringField(function, "name")?.takeIf { it.isNotBlank() }
    if (id == null || name == null) unusableAnswer()
    return AssistantPart.ToolCall(id, name, decodeArguments(function["arguments"]))
}

private fun decodeArguments(element: JsonElement?): JsonObject = when {
    element is JsonObject -> element
    element is JsonPrimitive && element.isString -> parseArguments(element.content)
    else -> unusableAnswer(FailureReason.MalformedToolArgs())
}

private fun parseArguments(text: String): JsonObject {
    if (text.isEmpty()) return JsonObject(emptyMap())
    val parsed = try {
        Json.parseToJsonElement(text)
    } catch (expected: IllegalArgumentException) {
        // Invalid JSON (SerializationException is a subclass) becomes the typed reason; the text is never kept.
        null
    }
    return parsed as? JsonObject ?: unusableAnswer(FailureReason.MalformedToolArgs())
}

/**
 * Maps a Chat Completions `usage` object to the four neutral buckets. `prompt_tokens` includes the cached tokens, and
 * is taken to include the cache-write tokens too, so
 * `inputUncached = prompt_tokens - cached_tokens - cache_write_tokens`. Each cache count is clamped to what remains of
 * the prompt, so the total never exceeds `prompt_tokens + completion_tokens`. Reasoning tokens are already inside
 * `completion_tokens`. A missing object is [Usage.ZERO]; a missing, null, negative or non-numeric count is 0.
 */
internal fun decodeChatUsage(element: JsonElement?): Usage {
    val usage = element as? JsonObject ?: return Usage.ZERO
    val details = usage[KEY_PROMPT_DETAILS] as? JsonObject
    val prompt = count(usage, KEY_PROMPT_TOKENS)
    val cacheRead = minOf(count(details, KEY_CACHED_TOKENS), prompt)
    val cacheWrite = minOf(count(details, KEY_CACHE_WRITE_TOKENS), prompt - cacheRead)
    return Usage(
        inputUncached = prompt - cacheRead - cacheWrite,
        cacheRead = cacheRead,
        cacheWrite = cacheWrite,
        output = count(usage, KEY_COMPLETION_TOKENS),
    )
}

// Only a bare JSON number counts; a string such as "12", a null or a fraction is 0, and a negative is floored at 0.
private fun count(from: JsonObject?, key: String): Long {
    val value = from?.get(key) as? JsonPrimitive ?: return 0L
    return if (value.isString) 0L else (value.longOrNull ?: 0L).coerceAtLeast(0L)
}

/** The string value of [key] in [from], or null when it is missing, null or not a string. */
internal fun chatStringField(from: JsonObject, key: String): String? =
    (from[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
