package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val RECORD_ITEM = "record_item"
private const val COUNT_ITEMS = "count_items"
private const val LOOKUP_ITEM = "lookup_item"
private const val LONG_SYSTEM_CHARS = 32_000
private const val FILLER = " The assistant treats every item as plain text and never reorders the list."

/**
 * The one synthetic script every conversation golden shares: derived fixtures, the conformance suite and the live
 * recorder all take their system text, prompt, tools and tool results from here. It names no app domain.
 */
internal object ConversationScript {

    const val MAX_TOKENS = 1024
    const val THINKING_MAX_TOKENS = 2048

    const val SHORT_SYSTEM = "You manage a small list of items with the given tools and keep your replies short."

    const val USER_PROMPT = "Record apple and pear, count the items, and look up kiwi. Do all of it in one step."

    /** The short system text followed by one fixed sentence, repeated until the text is long enough to be cached. */
    fun longSystem(): String {
        val builder = StringBuilder(SHORT_SYSTEM)
        while (builder.length < LONG_SYSTEM_CHARS) builder.append(FILLER)
        return builder.toString()
    }

    /**
     * Two tools with one required string argument and one with none. None is strict: a strict-eligible tool would
     * switch parallel calls off on OpenAI, and the script wants the model free to call several tools in one turn.
     */
    fun tools(): List<ToolSpec> = listOf(
        ToolSpec(RECORD_ITEM, "Records one item.", stringSchema("item"), strict = false),
        ToolSpec(COUNT_ITEMS, "Counts the recorded items.", emptySchema(), strict = false),
        ToolSpec(LOOKUP_ITEM, "Looks up one item by name.", stringSchema("name"), strict = false),
    )

    /** The fixed answer to a call: record_item ok, count_items 2, lookup_item an error, any other tool unknown. */
    fun resultFor(call: AssistantPart.ToolCall): ToolResult = when (call.name) {
        RECORD_ITEM -> ToolResult(call.id, "ok", false)
        COUNT_ITEMS -> ToolResult(call.id, "2", false)
        LOOKUP_ITEM -> ToolResult(call.id, "no item with that name", true)
        else -> ToolResult(call.id, "unknown tool", true)
    }

    /** The answers to every tool call of [turn], in the order the model made them. */
    fun results(turn: AssistantMessage): ToolResultsMessage = ToolResultsMessage(turn.toolCalls.map(::resultFor))

    /** A request with automatic tool choice and the static prefix cached. */
    fun request(messages: List<Message>, longSystem: Boolean, maxTokens: Int): ModelRequest = ModelRequest(
        if (longSystem) longSystem() else SHORT_SYSTEM,
        messages,
        tools(),
        ToolChoice.Auto(),
        maxTokens,
        CacheDirective(true),
        false,
    )

    private fun stringSchema(argument: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject(argument) { put("type", "string") } }
        putJsonArray("required") { add(argument) }
        put("additionalProperties", false)
    }

    private fun emptySchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        put("additionalProperties", false)
    }
}
