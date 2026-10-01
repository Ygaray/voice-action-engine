package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val KEY_TOOL_CALLS = "tool_calls"
private const val ROLE = "role"
private const val ROLE_ASSISTANT = "assistant"
private const val FUNCTION = "function"
private const val ARGUMENTS = "arguments"

// Response-only fields (annotations, reasoning text and the like) are rejected or ignored as input, so a replay keeps
// just these. The reasoning details stay because a router needs them back to continue a reasoning turn.
private val REPLAY_FIELDS = setOf("role", "content", "tool_calls", "refusal", "reasoning_details")

// The stored turn must hold exactly the tool calls the neutral parts hold, because the conversation pre-flight checks
// the parts while the encoder sends the stored turn. A call the decoder did not take (truncated by a length stop,
// dropped by a refusal, or of a type that is not `function`) is left out of the stored turn; every other field and
// every kept call stays byte for byte. The stored instance is returned itself when nothing is left out.
internal fun replayMatching(replay: NativeReplay, parts: List<AssistantPart>): NativeReplay {
    val message = replay.raw as? JsonObject
    val calls = message?.get(KEY_TOOL_CALLS) as? JsonArray
    val decodedIds = parts.filterIsInstance<AssistantPart.ToolCall>().map { it.id }.toSet()
    val kept = calls?.filter { isDecodedCall(it, decodedIds) }
    return when {
        message == null || calls == null || kept == null || kept.size == calls.size -> replay
        kept.isEmpty() -> NativeReplay(replay.provider, replay.model, JsonObject(message - KEY_TOOL_CALLS))
        else -> NativeReplay(replay.provider, replay.model, JsonObject(message + (KEY_TOOL_CALLS to JsonArray(kept))))
    }
}

private fun isDecodedCall(entry: JsonElement, decodedIds: Set<String>): Boolean {
    val call = entry as? JsonObject ?: return false
    val type = chatStringField(call, "type")
    return (type == null || type == "function") && chatStringField(call, "id") in decodedIds
}

// A replay goes back as stored, minus response-only fields. Only a missing role, an empty arguments value or an
// arguments object is fixed, because the endpoint rejects those as input; ids and every other value are never touched,
// and a valid replay is returned unchanged. The result depends only on the stored turn, so a later request repeats the
// same bytes.
internal fun repairedReplay(replay: JsonObject): JsonObject {
    val projection = JsonObject(replay.filterKeys { it in REPLAY_FIELDS })
    val withRole =
        if (ROLE in projection) projection else JsonObject(mapOf(ROLE to JsonPrimitive(ROLE_ASSISTANT)) + projection)
    val calls = withRole[KEY_TOOL_CALLS] as? JsonArray ?: return withRole
    return JsonObject(withRole + (KEY_TOOL_CALLS to JsonArray(calls.map { repairedCall(it) })))
}

private fun repairedCall(entry: JsonElement): JsonElement {
    val call = entry as? JsonObject
    val function = call?.get(FUNCTION) as? JsonObject
    val fixed = repairedArguments(function?.get(ARGUMENTS))
    return if (call != null && function != null && fixed != null) {
        JsonObject(call + (FUNCTION to JsonObject(function + (ARGUMENTS to fixed))))
    } else {
        entry
    }
}

// The wire carries the arguments as a string. An empty form becomes "{}"; an object, which some routed upstreams send
// and the decoder accepts, becomes its compact text in the key order received. Any other value is left alone.
private fun repairedArguments(arguments: JsonElement?): JsonPrimitive? = when {
    isEmptyArgumentsForm(arguments) -> JsonPrimitive("{}")
    arguments is JsonObject -> JsonPrimitive(Json.encodeToString(JsonObject.serializer(), arguments))
    else -> null
}
