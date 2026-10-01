package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val KEY_TOOL_CALLS = "tool_calls"

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
