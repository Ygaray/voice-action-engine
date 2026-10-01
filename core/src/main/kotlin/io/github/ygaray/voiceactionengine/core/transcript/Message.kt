package io.github.ygaray.voiceactionengine.core.transcript

import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.serialization.json.JsonElement

/**
 * One message in a neutral, provider-independent conversation.
 *
 * The three kinds, [UserMessage], [AssistantMessage] and [ToolResultsMessage], are the whole universe of a
 * conversation: a provider mapper switches over them exhaustively. A single-shot command is a one-message
 * conversation; an agentic run is a longer one.
 */
public sealed class Message

/**
 * What the user said.
 *
 * Voice commands are text, so a user message is text only. Constructing it never throws.
 *
 * @property text the user's words. They are never printed by [toString].
 */
public class UserMessage(public val text: String) : Message() {
    /** Prints the length only, never the text. */
    override fun toString(): String = "UserMessage(textLength=${text.length})"
}

/**
 * One turn the model produced.
 *
 * @property parts the turn's text and tool calls, in order; a copy. It may be empty, because a provider can return an
 * empty turn.
 * @property nativeReplay the provider's own version of this turn, or null when there is none. A mapper refuses a
 * request whose history holds a replay stamped for another provider or model, with the reason `replay_mismatch`; it
 * never rebuilds such a turn quietly. A tier ladder that escalates to another model or provider in the middle of a
 * conversation therefore sends a copy of the history whose assistant turns are rebuilt as `AssistantMessage(parts)`
 * (no replay). A rebuilt turn carries no thinking blocks or signatures, which is the cost of switching.
 */
public class AssistantMessage(
    parts: List<AssistantPart>,
    public val nativeReplay: NativeReplay?,
) : Message() {
    /** A turn with no native replay. */
    public constructor(parts: List<AssistantPart>) : this(parts, null)

    /** A copy of the parts. */
    public val parts: List<AssistantPart> = parts.toList()

    /** The tool calls among [parts], in order. */
    public val toolCalls: List<AssistantPart.ToolCall> = this.parts.filterIsInstance<AssistantPart.ToolCall>()

    /**
     * The provider's own version of this turn, to send back unchanged, or null.
     *
     * It is the very same raw instance, returned only when [provider] and [model] both match the replay's stamp
     * exactly. A null result means this turn has no replay for that provider and model: a mapper rebuilds a turn from
     * the neutral [parts] only when [nativeReplay] is null, and refuses a turn stamped for another provider or model.
     */
    public fun nativeFor(provider: ProviderId, model: String): JsonElement? =
        nativeReplay?.takeIf { it.provider == provider && it.model == model }?.raw

    /** Prints counts, tool names and the replay stamp only, never text, arguments or the raw turn. */
    override fun toString(): String =
        "AssistantMessage(parts=${parts.size}, toolCalls=${toolCalls.map { it.name }}, " +
            "nativeReplay=${nativeReplay?.let { "${it.provider}/${it.model}" } ?: "none"})"
}

/**
 * The results of the tool calls from one assistant turn, sent back together in one message.
 *
 * @property results one result per call, in the order the app produced them; a copy, never empty, with distinct call
 * ids.
 * @throws IllegalArgumentException when [results] is empty or two results share a call id.
 */
public class ToolResultsMessage(results: List<ToolResult>) : Message() {
    /** A copy of the results. */
    public val results: List<ToolResult> = results.toList()

    init {
        require(this.results.isNotEmpty()) { "a tool result batch must not be empty" }
        val ids = this.results.map { it.callId }
        require(ids.toSet().size == ids.size) { "a tool result batch must not repeat a call id" }
    }

    /** Prints the number of results and of errors only, never the result content. */
    override fun toString(): String =
        "ToolResultsMessage(results=${results.size}, errors=${results.count { it.isError }})"
}

/**
 * What the app answered to one tool call.
 *
 * @property callId the id of the [AssistantPart.ToolCall] this answers.
 * @property content the result text. It is never printed by [toString].
 * @property isError true when the tool failed and [content] describes the failure.
 * @throws IllegalArgumentException when [callId] is blank.
 */
public class ToolResult(
    public val callId: String,
    public val content: String,
    public val isError: Boolean,
) {
    /** A successful result. */
    public constructor(callId: String, content: String) : this(callId, content, false)

    init {
        require(callId.isNotBlank()) { "a tool result call id must not be blank" }
    }

    /** Prints the call id, the content length and the error flag only, never the content. */
    override fun toString(): String = "ToolResult(callId=$callId, contentLength=${content.length}, isError=$isError)"
}
