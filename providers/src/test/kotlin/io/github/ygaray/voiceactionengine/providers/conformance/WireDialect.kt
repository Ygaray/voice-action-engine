package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.anthropic.anthropicRequest
import io.github.ygaray.voiceactionengine.providers.anthropic.decodeAnthropicResponse
import io.github.ygaray.voiceactionengine.providers.anthropic.encodeAnthropicRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The credential every conformance request carries; it is not a real key. */
internal const val FAKE_KEY = "sk-test-key"

private const val KEY_MESSAGES = "messages"
private const val KEY_ROLE = "role"
private const val KEY_CONTENT = "content"
private const val ROLE_ASSISTANT = "assistant"
private const val MAX_TOKENS = ConversationScript.MAX_TOKENS
private const val THINKING_MAX_TOKENS = ConversationScript.THINKING_MAX_TOKENS
private const val TAG_LONG_SYSTEM = "long_system"
private val THINKING_TAGS = setOf("thinking", "reasoning_details")

/** One tool result as it appears on the wire of a request. [content] is null when the wire carried no text. */
internal class WireToolResult(val callId: String, val content: String?, val isError: Boolean) {
    override fun toString(): String = "WireToolResult(contentPresent=${content != null}, isError=$isError)"
}

/**
 * The seam between the one conformance suite and one wire dialect. A binding calls only the production encode and
 * decode functions and the real provider class; it holds no copy of their logic.
 */
internal interface WireDialect {
    /** The manifest dialect this binding serves. */
    val name: String

    /** The provider id a decoded turn of this dialect is stamped with. */
    val providerId: ProviderId

    /** The request the production code is handed for [request] and [model]. */
    fun call(model: String, request: ModelRequest): ProviderRequest

    /** The body the production encoder writes for [request], as UTF-8 text. */
    fun encode(model: String, request: ModelRequest): String

    /** Decodes a response [body] the way the transport does for the requested [model]. */
    fun decode(body: String, model: String): ModelResult

    /** What the decoder keeps as the raw turn of [response]. */
    fun storedReplay(response: JsonObject): JsonElement

    /** The wire form of that raw turn, or null when the golden itself is the only source. */
    fun expectedReplayWire(response: JsonObject): JsonElement?

    /** The positions of the assistant messages in a request's messages array, in order. */
    fun assistantWireIndices(messages: JsonArray): List<Int>

    /** The part of an assistant wire message that carries the raw turn. */
    fun assistantWire(message: JsonObject): JsonElement

    /** The tool results the wire carries for the assistant message at [assistantIndex], in wire order. */
    fun toolResultWires(messages: JsonArray, assistantIndex: Int): List<WireToolResult>

    /** How many wire messages carry the results of a turn with [callCount] calls. */
    fun resultMessageCount(callCount: Int): Int
}

/** What replaying one conversation produced: every request body in order, and the rule names that failed. */
internal class ConversationReplay(val bodies: List<String>, val violations: List<String>) {
    override fun toString(): String = "ConversationReplay(bodies=${bodies.size}, violations=${violations.size})"
}

/**
 * Replays [turns] the way an agent loop would: encode turn k, decode the golden response of turn k, append the
 * assistant turn and the script's results, encode turn k+1. Every turn's encoded messages must equal the golden's.
 * Violations are fixed phrases that name the turn only, never any body text.
 */
internal fun replayConversation(
    dialect: WireDialect,
    row: ConversationRow,
    turns: List<ConversationTurn>,
): ConversationReplay = Replayer(dialect, row).run(turns)

private class Replayer(private val dialect: WireDialect, private val row: ConversationRow) {
    private val bodies = mutableListOf<String>()
    private val violations = mutableListOf<String>()
    private val messages = mutableListOf<Message>(UserMessage(ConversationScript.USER_PROMPT))
    private val longSystem = TAG_LONG_SYSTEM in row.tags
    private val maxTokens = if (row.tags.any { it in THINKING_TAGS }) THINKING_MAX_TOKENS else MAX_TOKENS

    fun run(turns: List<ConversationTurn>): ConversationReplay {
        for ((index, turn) in turns.withIndex()) {
            if (!replayTurn(index + 1, turn, index == turns.lastIndex)) break
        }
        return ConversationReplay(bodies.toList(), violations.toList())
    }

    // True while the conversation can go on to a next turn.
    private fun replayTurn(number: Int, turn: ConversationTurn, last: Boolean): Boolean {
        val body = dialect.encode(row.model, ConversationScript.request(messages.toList(), longSystem, maxTokens))
        bodies += body
        if (!sameMessages(sentMessages(body), turn.messages)) {
            violations += "turn $number: messages differ from the golden"
        }
        val assistant = decoded(number, turn) ?: return false
        messages += assistant
        return when {
            assistant.toolCalls.isEmpty() -> finishedWithoutCalls(number, last)
            last -> fail("turn $number: the last turn still asks for tool calls")
            else -> {
                messages += ConversationScript.results(assistant)
                true
            }
        }
    }

    private fun finishedWithoutCalls(number: Int, last: Boolean): Boolean {
        if (!last) violations += "turn $number: a turn without tool calls is not the last"
        return false
    }

    private fun fail(violation: String): Boolean {
        violations += violation
        return false
    }

    private fun decoded(number: Int, turn: ConversationTurn): AssistantMessage? {
        val result = dialect.decode(canonicalJson(turn.response.toString()), row.model)
        val message = (result as? ModelResult.Success)?.response?.message
        val stamp = message?.nativeReplay
        val ok = stamp != null && stamp.provider == dialect.providerId && stamp.model == row.model &&
            stamp.raw == dialect.storedReplay(turn.response)
        if (!ok) violations += "turn $number: the response did not decode to a stamped turn"
        return message.takeIf { ok }
    }
}

/** The messages array of a request body. */
internal fun sentMessages(body: String): JsonArray =
    (Json.parseToJsonElement(body) as JsonObject)[KEY_MESSAGES] as JsonArray

private fun sameMessages(sent: JsonArray, golden: JsonArray): Boolean =
    sent == golden && compact(sent) == compact(golden)

private fun compact(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

/** The Anthropic Messages binding: the production encoder and decoder, and the real [AnthropicProvider]. */
internal object AnthropicWire : WireDialect {
    override val name: String = "anthropic"
    override val providerId: ProviderId = ProviderId.ANTHROPIC

    override fun call(model: String, request: ModelRequest): ProviderRequest =
        anthropicRequest(model, request, FAKE_KEY)

    override fun encode(model: String, request: ModelRequest): String =
        encodeAnthropicRequest(call(model, request)).toString(Charsets.UTF_8)

    override fun decode(body: String, model: String): ModelResult = decodeAnthropicResponse(body, null, model)

    override fun storedReplay(response: JsonObject): JsonElement = checkNotNull(response["content"])

    override fun expectedReplayWire(response: JsonObject): JsonElement? = response["content"]

    override fun assistantWireIndices(messages: JsonArray): List<Int> =
        messages.withIndex().filter { (_, message) -> roleOf(message) == ROLE_ASSISTANT }.map { it.index }

    override fun assistantWire(message: JsonObject): JsonElement = checkNotNull(message[KEY_CONTENT])

    override fun toolResultWires(messages: JsonArray, assistantIndex: Int): List<WireToolResult> {
        val results = (messages.getOrNull(assistantIndex + 1) as? JsonObject)?.get(KEY_CONTENT) as? JsonArray
        return results.orEmpty().map { block ->
            val fields = block as JsonObject
            WireToolResult(
                text(fields["tool_use_id"]).orEmpty(),
                text(fields[KEY_CONTENT]),
                (fields["is_error"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        }
    }

    override fun resultMessageCount(callCount: Int): Int = 1

    private fun roleOf(message: JsonElement): String? = text((message as? JsonObject)?.get(KEY_ROLE))

    private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}
