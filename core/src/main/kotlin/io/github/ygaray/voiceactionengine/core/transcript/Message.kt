package io.github.ygaray.voiceactionengine.core.transcript

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
 * @property nativeReplay the provider's own version of this turn, or null when there is none.
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

    /** Prints counts, tool names and the replay stamp only, never text, arguments or the raw turn. */
    override fun toString(): String =
        "AssistantMessage(parts=${parts.size}, toolCalls=${toolCalls.map { it.name }}, " +
            "nativeReplay=${nativeReplay?.let { "${it.provider}/${it.model}" } ?: "none"})"
}
