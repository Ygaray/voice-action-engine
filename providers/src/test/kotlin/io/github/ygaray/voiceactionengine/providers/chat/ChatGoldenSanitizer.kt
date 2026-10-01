@file:OptIn(ExperimentalSerializationApi::class)

package io.github.ygaray.voiceactionengine.providers.chat

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val GOLDEN = "GOLDEN"
private const val REDACTED = "redacted"
private const val INDENT = "  "
private const val TOOL_CALL_PREFIX = "call_GOLDEN"

private const val KEY_ERROR = "error"
private const val KEY_MESSAGE = "message"
private const val KEY_METADATA = "metadata"
private const val KEY_RAW = "raw"
private const val KEY_PROVIDER_NAME = "provider_name"
private const val KEY_TOOL_CALLS = "tool_calls"
private const val KEY_ID = "id"
private const val KEY_CREATED = "created"
private const val KEY_FINGERPRINT = "system_fingerprint"

// Keys that never reach a committed golden: pricing, tier and user identity.
private val DROPPED_KEYS = setOf("service_tier", "cost", "cost_details", "user_id")

// One rule set, shared with the hygiene scan in ChatGoldenReplayTest so the two cannot drift apart.
internal val ID_IN_TEXT = Regex("(?<![A-Za-z0-9_])(chatcmpl-|gen-|call_)([A-Za-z0-9_-]*)")
internal val GOLDEN_TAIL = Regex("GOLDEN[0-9]*")
internal val KEY_IN_TEXT = Regex("(?<![A-Za-z0-9])sk-[A-Za-z0-9_-]{16,}")
internal val BEARER_IN_TEXT = Regex("(?i)Bearer\\s+\\S+")

private val PRINTER = Json {
    prettyPrint = true
    prettyPrintIndent = INDENT
}

/** The one running count a pass needs: tool calls are numbered in the order they appear. */
private class Pass {
    var toolCalls = 0
}

/**
 * Turns a raw Chat Completions body (a real answer or error) into a copy that is safe to commit.
 *
 * Every `chatcmpl-` and `gen-` id keeps its prefix and ends in GOLDEN, each tool call id becomes `call_GOLDEN<n>` in
 * the order the calls appear, `system_fingerprint` becomes null, `created` becomes 0, and `service_tier`, `cost`,
 * `cost_details` and `user_id` are dropped wherever they occur. In an `error` object the message, `metadata.raw` and
 * `metadata.provider_name` become `redacted`, because a provider can echo the prompt there. Key-shaped strings and
 * Bearer values anywhere in the text are replaced. Usage counts, finish reasons, message content, refusals, tool
 * calls and reasoning details are kept.
 *
 * The output is pretty-printed with a two-space indent and is checked against [goldenHygieneViolations] before it is
 * returned; a body that still breaks a rule is refused with an IllegalArgumentException, as is a body that is not a
 * JSON object. No message ever contains the body.
 */
internal object ChatGoldenSanitizer {

    fun sanitize(body: String): String {
        val cleaned = clean(redactError(parseObject(body)), Pass())
        val output = PRINTER.encodeToString(JsonElement.serializer(), cleaned)
        val violations = goldenHygieneViolations(output)
        require(violations.isEmpty()) { "sanitized body still breaks hygiene rules: $violations" }
        return output
    }

    // The fixed messages below are all that is ever said about a bad body: the parser's own message can quote it.
    private fun parseObject(body: String): JsonObject {
        val element = try {
            Json.parseToJsonElement(body)
        } catch (ignored: IllegalArgumentException) {
            throw IllegalArgumentException("the body is not JSON")
        }
        return element as? JsonObject ?: throw IllegalArgumentException("the body is not a JSON object")
    }

    private fun redactError(root: JsonObject): JsonObject {
        val error = root[KEY_ERROR] as? JsonObject ?: return root
        val metadata = error[KEY_METADATA] as? JsonObject
        val redactedMetadata = metadata?.let { replaceIfPresent(it, KEY_RAW, KEY_PROVIDER_NAME) }
        var redacted = replaceIfPresent(error, KEY_MESSAGE)
        if (redactedMetadata != null) redacted = JsonObject(redacted + (KEY_METADATA to redactedMetadata))
        return JsonObject(root + (KEY_ERROR to redacted))
    }

    private fun replaceIfPresent(from: JsonObject, vararg keys: String): JsonObject =
        JsonObject(from.mapValues { (key, value) -> if (key in keys) JsonPrimitive(REDACTED) else value })

    private fun clean(element: JsonElement, pass: Pass): JsonElement = when (element) {
        is JsonObject -> cleanObject(element, pass)
        is JsonArray -> JsonArray(element.map { clean(it, pass) })
        is JsonPrimitive -> if (element.isString) JsonPrimitive(scrub(element.content)) else element
    }

    private fun cleanObject(source: JsonObject, pass: Pass): JsonObject {
        val result = LinkedHashMap<String, JsonElement>()
        source.forEach { (key, value) ->
            when {
                key in DROPPED_KEYS -> Unit
                key == KEY_FINGERPRINT -> result[key] = JsonNull
                key == KEY_CREATED -> result[key] = JsonPrimitive(0)
                key == KEY_TOOL_CALLS && value is JsonArray -> result[key] = cleanToolCalls(value, pass)
                else -> result[key] = clean(value, pass)
            }
        }
        return JsonObject(result)
    }

    private fun cleanToolCalls(calls: JsonArray, pass: Pass): JsonArray = JsonArray(
        calls.map { call ->
            val cleaned = clean(call, pass)
            if (cleaned is JsonObject && cleaned.containsKey(KEY_ID)) {
                pass.toolCalls += 1
                JsonObject(cleaned + (KEY_ID to JsonPrimitive(TOOL_CALL_PREFIX + pass.toolCalls)))
            } else {
                cleaned
            }
        },
    )

    private fun scrub(text: String): String {
        val withoutKeys = BEARER_IN_TEXT.replace(KEY_IN_TEXT.replace(text, REDACTED), REDACTED)
        return ID_IN_TEXT.replace(withoutKeys) { match ->
            if (GOLDEN_TAIL.matches(match.groupValues[2])) match.value else match.groupValues[1] + GOLDEN
        }
    }
}
