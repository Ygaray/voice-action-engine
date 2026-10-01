package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.providers.chat.BEARER_IN_TEXT
import io.github.ygaray.voiceactionengine.providers.chat.GOLDEN_TAIL
import io.github.ygaray.voiceactionengine.providers.chat.KEY_IN_TEXT
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val GOLDEN = "GOLDEN"
private const val REDACTED = "redacted"
private const val KEY_ERROR = "error"
private const val KEY_CREATED = "created"
private const val KEY_FINGERPRINT = "system_fingerprint"
private const val KEY_TURNS = "turns"
private const val KEY_MESSAGES = "messages"
private const val KEY_RESPONSE = "response"

// Keys that never reach a committed golden: pricing, tier and user identity.
private val DROPPED_KEYS = setOf("service_tier", "cost", "cost_details", "user_id")

/** One request/response exchange as recorded: the messages array as sent and the response body that came back. */
internal class RecordedTurn(val messages: JsonArray, val response: JsonObject) {
    override fun toString(): String = "RecordedTurn(messages=${messages.size})"
}

/**
 * Turns the bodies of one whole conversation into a copy that is safe to commit. Use one instance per conversation.
 *
 * It keeps a single id map for request messages and response bodies alike, so an id in a turn-1 response and the same
 * id in the turn-2 messages become the same `<prefix>GOLDEN<n>`, numbered per prefix (call_, toolu_, msg_, chatcmpl-,
 * gen-, req_) in the order the ids first appear. An id that already ends in GOLDEN is kept and not counted.
 *
 * Values under [EXEMPT_KEYS] (thinking, signature, data, reasoning, reasoning_details) are copied untouched, because a
 * provider checks them byte for byte; a key-shaped or Bearer string inside one refuses the whole conversation.
 * Elsewhere such strings become `redacted`, `created` becomes 0, `system_fingerprint` becomes null, and `service_tier`,
 * `cost`, `cost_details` and `user_id` are dropped at any depth. A response that holds an error is refused, because
 * conversation goldens are success-only.
 *
 * Every refusal is an IllegalArgumentException with a fixed message; no message ever contains body text.
 */
internal class ConversationSanitizer {

    private val ids = HashMap<String, String>()
    private val counters = HashMap<String, Int>()

    /** Cleans one element; an object that holds an `error` value is refused. */
    fun sanitize(element: JsonElement): JsonElement {
        require(element !is JsonObject || element[KEY_ERROR] == null || element[KEY_ERROR] is JsonNull) {
            "a body that holds an error is not a golden"
        }
        return clean(element)
    }

    /**
     * Sanitizes [turns] in order (each turn's messages, then its response), prints the result as canonical compact
     * `{"turns":[...]}` text and requires it to pass [conversationHygieneViolations].
     */
    fun conversation(turns: List<RecordedTurn>): String {
        require(turns.isNotEmpty()) { "a conversation needs at least one turn" }
        val cleaned = turns.map { turn ->
            val messages = sanitize(turn.messages)
            val response = sanitize(turn.response)
            JsonObject(mapOf(KEY_MESSAGES to messages, KEY_RESPONSE to response))
        }
        val text = canonicalJson(JsonObject(mapOf(KEY_TURNS to JsonArray(cleaned))).toString())
        val violations = conversationHygieneViolations(text)
        require(violations.isEmpty()) { "the sanitized conversation still breaks hygiene rules: $violations" }
        return text
    }

    private fun clean(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> cleanObject(element)
        is JsonArray -> JsonArray(element.map(::clean))
        is JsonPrimitive -> if (element.isString) JsonPrimitive(scrub(element.content)) else element
    }

    private fun cleanObject(source: JsonObject): JsonObject {
        val result = LinkedHashMap<String, JsonElement>()
        source.forEach { (key, value) ->
            when {
                key in EXEMPT_KEYS -> result[key] = keepUntouched(value)
                key in DROPPED_KEYS -> Unit
                key == KEY_FINGERPRINT -> result[key] = JsonNull
                key == KEY_CREATED -> result[key] = JsonPrimitive(0)
                else -> result[key] = clean(value)
            }
        }
        return JsonObject(result)
    }

    // Reasoning values are copied as they are; they are only checked for a secret that must never be committed.
    private fun keepUntouched(element: JsonElement): JsonElement {
        require(!holdsSecret(element)) { "a key-shaped or Bearer value sits inside a reasoning value" }
        return element
    }

    private fun holdsSecret(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.values.any(::holdsSecret)
        is JsonArray -> element.any(::holdsSecret)
        is JsonPrimitive -> element.isString &&
            (KEY_IN_TEXT.containsMatchIn(element.content) || BEARER_IN_TEXT.containsMatchIn(element.content))
    }

    private fun scrub(text: String): String {
        val withoutSecrets = BEARER_IN_TEXT.replace(KEY_IN_TEXT.replace(text, REDACTED), REDACTED)
        return CONVERSATION_ID_IN_TEXT.replace(withoutSecrets) { match ->
            val prefix = match.groupValues[1]
            if (GOLDEN_TAIL.matches(match.groupValues[2])) {
                match.value
            } else {
                ids.getOrPut(match.value) { prefix + GOLDEN + nextNumber(prefix) }
            }
        }
    }

    private fun nextNumber(prefix: String): Int {
        val next = (counters[prefix] ?: 0) + 1
        counters[prefix] = next
        return next
    }
}
