package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.anthropic.anthropicRequest
import io.github.ygaray.voiceactionengine.providers.anthropic.decodeAnthropicResponse
import io.github.ygaray.voiceactionengine.providers.anthropic.encodeAnthropicRequest
import io.github.ygaray.voiceactionengine.providers.anthropic.successBody
import io.github.ygaray.voiceactionengine.providers.anthropic.textBlock
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import io.github.ygaray.voiceactionengine.providers.chat.ChatVendor
import io.github.ygaray.voiceactionengine.providers.chat.chatBody
import io.github.ygaray.voiceactionengine.providers.chat.chatCall
import io.github.ygaray.voiceactionengine.providers.chat.chatMessage
import io.github.ygaray.voiceactionengine.providers.chat.chatUsage
import io.github.ygaray.voiceactionengine.providers.chat.decodeChatResponse
import io.github.ygaray.voiceactionengine.providers.chat.encodeChatRequest
import io.github.ygaray.voiceactionengine.providers.chat.isEmptyArgumentsForm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl

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

    /** Some other provider id, used to build a turn stamped for the wrong provider. */
    val otherProviderId: ProviderId

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

    /**
     * For a turn whose [expectedReplayWire] is null (a Chat turn with empty-arguments forms): the wire form the
     * dialect's own rules say the repaired turn takes, computed here and not by production code. Null otherwise.
     */
    fun repairedReplayWire(response: JsonObject): JsonElement? = null

    /** The positions of the assistant messages in a request's messages array, in order. */
    fun assistantWireIndices(messages: JsonArray): List<Int>

    /** The part of an assistant wire message that carries the raw turn. */
    fun assistantWire(message: JsonObject): JsonElement

    /** The tool results the wire carries for the assistant message at [assistantIndex], in wire order. */
    fun toolResultWires(messages: JsonArray, assistantIndex: Int): List<WireToolResult>

    /** How many wire messages carry the results of a turn with [callCount] calls. */
    fun resultMessageCount(callCount: Int): Int

    /** A raw turn of a shape this dialect's provider refuses to replay. */
    fun rawOfOtherShape(): JsonElement

    /** The real provider, pointed at [baseUrl]. */
    fun provider(baseUrl: HttpUrl): AiProvider

    /** A minimal successful end-of-turn answer body. */
    fun okAnswer(): String
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

/**
 * The whole-history replay rule: the raw turn of every response that is followed by a later request must appear, as the
 * compact text the golden file stores, in that golden file and in every later request body, and must be the assistant
 * element at its own position in each of them. Violations name the turn and request only.
 */
internal fun verbatimViolations(
    dialect: WireDialect,
    turns: List<ConversationTurn>,
    bodies: List<String>,
    goldenText: String,
): List<String> {
    val found = mutableListOf<String>()
    for ((index, turn) in turns.withIndex()) {
        if (index + 1 >= bodies.size) continue
        val expected = dialect.expectedReplayWire(turn.response) ?: repairedWire(dialect, turns, index, found)
        if (expected != null) found += turnViolations(dialect, index + 1, expected, bodies, goldenText)
    }
    return found
}

private fun turnViolations(
    dialect: WireDialect,
    number: Int,
    expected: JsonElement,
    bodies: List<String>,
    goldenText: String,
): List<String> {
    val found = mutableListOf<String>()
    if (canonicalJson(expected.toString()) !in goldenText) found += "turn $number: not verbatim in the golden file"
    for (request in number until bodies.size) {
        found += requestViolations(dialect, number, expected, request + 1, bodies[request])
    }
    return found
}

// A turn the dialect repairs before replaying has no wire the stored response gives directly. The golden's next request
// is then the source: its assistant element must equal the repair the dialect's own rules prescribe, and that
// prescribed form is what every later request must repeat. Null when the dialect prescribes no repair.
private fun repairedWire(
    dialect: WireDialect,
    turns: List<ConversationTurn>,
    index: Int,
    found: MutableList<String>,
): JsonElement? {
    val prescribed = dialect.repairedReplayWire(turns[index].response) ?: return null
    val messages = turns[index + 1].messages
    val at = dialect.assistantWireIndices(messages).getOrNull(index)
    val golden = at?.let { dialect.assistantWire(messages[it] as JsonObject) }
    if (golden != prescribed) found += "turn ${index + 1}: the golden's replayed turn is not the repaired stored turn"
    return prescribed
}

private fun requestViolations(
    dialect: WireDialect,
    number: Int,
    expected: JsonElement,
    request: Int,
    body: String,
): List<String> {
    val found = mutableListOf<String>()
    if (canonicalJson(expected.toString()) !in body) found += "turn $number: not verbatim in request $request"
    val messages = sentMessages(body)
    val at = dialect.assistantWireIndices(messages).getOrNull(number - 1)
    val wire = at?.let { dialect.assistantWire(messages[it] as JsonObject) }
    if (wire != expected) found += "turn $number: request $request holds a different assistant turn"
    return found
}

private fun sameMessages(sent: JsonArray, golden: JsonArray): Boolean =
    sent == golden && compact(sent) == compact(golden)

private fun compact(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

/** The Anthropic Messages binding: the production encoder and decoder, and the real [AnthropicProvider]. */
internal object AnthropicWire : WireDialect {
    override val name: String = "anthropic"
    override val providerId: ProviderId = ProviderId.ANTHROPIC
    override val otherProviderId: ProviderId = ProviderId.OPENAI

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

    // Anthropic replays a content array, so an object is the wrong shape.
    override fun rawOfOtherShape(): JsonElement = JsonObject(emptyMap())

    override fun provider(baseUrl: HttpUrl): AiProvider = AnthropicProvider { this.baseUrl = baseUrl }

    override fun okAnswer(): String = successBody(listOf(textBlock("Done.")), "end_turn")

    private fun roleOf(message: JsonElement): String? = text((message as? JsonObject)?.get(KEY_ROLE))

    private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}

private const val KEY_CHOICES = "choices"
private const val KEY_MESSAGE = "message"
private const val KEY_TOOL_CALLS = "tool_calls"
private const val KEY_FUNCTION = "function"
private const val KEY_ARGUMENTS = "arguments"
private const val KEY_ERROR = "error"
private const val ROLE_TOOL = "tool"
private const val OK_PROMPT_TOKENS = 10L
private const val OK_COMPLETION_TOKENS = 2L

// The test's own list of what a Chat replay keeps; it is deliberately not read from production code.
private val CHAT_REPLAY_KEYS = setOf(KEY_ROLE, KEY_CONTENT, KEY_TOOL_CALLS, "refusal", "reasoning_details")

/**
 * The Chat Completions binding for one [vendor]: the production encoder and decoder, and the real
 * [ChatCompletionsProvider]. The turn it stores and replays is the first choice's message.
 */
internal class ChatWire(private val vendor: ChatVendor, override val name: String) : WireDialect {
    override val providerId: ProviderId = vendor.providerId
    override val otherProviderId: ProviderId =
        if (vendor.providerId == ProviderId.OPENAI) ProviderId.OPENROUTER else ProviderId.OPENAI

    override fun call(model: String, request: ModelRequest): ProviderRequest =
        chatCall(vendor, model, request, FAKE_KEY)

    override fun encode(model: String, request: ModelRequest): String =
        encodeChatRequest(call(model, request), vendor).toString(Charsets.UTF_8)

    override fun decode(body: String, model: String): ModelResult =
        decodeChatResponse(body, null, model, vendor, false).result

    override fun storedReplay(response: JsonObject): JsonElement = storedMessage(response)

    override fun expectedReplayWire(response: JsonObject): JsonElement? =
        storedMessage(response).let { if (hasEmptyArguments(it)) null else projection(it) }

    // Each empty-form arguments value becomes "{}": in place when the key is there, else last in the function.
    override fun repairedReplayWire(response: JsonObject): JsonElement? {
        val message = storedMessage(response)
        if (!hasEmptyArguments(message)) return null
        val calls = JsonArray((message[KEY_TOOL_CALLS] as JsonArray).map { repairedCall(it as JsonObject) })
        return JsonObject(projection(message).mapValues { (key, value) -> if (key == KEY_TOOL_CALLS) calls else value })
    }

    override fun assistantWireIndices(messages: JsonArray): List<Int> =
        messages.withIndex().filter { (_, message) -> roleOf(message) == ROLE_ASSISTANT }.map { it.index }

    override fun assistantWire(message: JsonObject): JsonElement = message

    override fun toolResultWires(messages: JsonArray, assistantIndex: Int): List<WireToolResult> =
        messages.drop(assistantIndex + 1)
            .takeWhile { roleOf(it) == ROLE_TOOL }
            .map { message -> toolResult(message as JsonObject) }

    override fun resultMessageCount(callCount: Int): Int = callCount

    // Chat replays a message object, so an array is the wrong shape.
    override fun rawOfOtherShape(): JsonElement = JsonArray(emptyList())

    override fun provider(baseUrl: HttpUrl): AiProvider =
        if (vendor.providerId == ProviderId.OPENAI) {
            ChatCompletionsProvider.openAi { this.baseUrl = baseUrl }
        } else {
            ChatCompletionsProvider.openRouter { this.baseUrl = baseUrl }
        }

    override fun okAnswer(): String =
        chatBody(chatMessage("Done."), "stop", chatUsage(OK_PROMPT_TOKENS, OK_COMPLETION_TOKENS))

    /** The replayed keys of [message], in the order the stored message has them. */
    fun projection(message: JsonObject): JsonObject = JsonObject(message.filterKeys { it in CHAT_REPLAY_KEYS })

    private fun storedMessage(response: JsonObject): JsonObject {
        val choice = (response[KEY_CHOICES] as JsonArray)[0] as JsonObject
        return choice[KEY_MESSAGE] as JsonObject
    }

    private fun hasEmptyArguments(message: JsonObject): Boolean =
        (message[KEY_TOOL_CALLS] as? JsonArray).orEmpty().any { entry ->
            val function = (entry as JsonObject)[KEY_FUNCTION] as JsonObject
            isEmptyArgumentsForm(function[KEY_ARGUMENTS])
        }

    private fun repairedCall(call: JsonObject): JsonObject {
        val function = call[KEY_FUNCTION] as JsonObject
        if (!isEmptyArgumentsForm(function[KEY_ARGUMENTS])) return call
        val fixed = function.toMutableMap()
        fixed[KEY_ARGUMENTS] = JsonPrimitive("{}")
        return JsonObject(call.toMutableMap().also { it[KEY_FUNCTION] = JsonObject(fixed) })
    }

    // An error is a string inside a one-key `error` object; any other content is the app's text as it went out.
    private fun toolResult(message: JsonObject): WireToolResult {
        val id = text(message["tool_call_id"]).orEmpty()
        val content = text(message[KEY_CONTENT])
        val inner = content?.let(::errorText)
        return if (inner != null) WireToolResult(id, inner, true) else WireToolResult(id, content, false)
    }

    private fun errorText(content: String): String? {
        if (!content.startsWith("{")) return null
        val parsed = try {
            Json.parseToJsonElement(content) as? JsonObject
        } catch (ignored: IllegalArgumentException) {
            null
        }
        return parsed?.takeIf { it.keys == setOf(KEY_ERROR) }?.let { text(it[KEY_ERROR]) }
    }

    private fun roleOf(message: JsonElement): String? = text((message as? JsonObject)?.get(KEY_ROLE))

    private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}
