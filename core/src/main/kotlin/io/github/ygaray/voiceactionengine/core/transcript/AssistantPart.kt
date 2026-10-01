package io.github.ygaray.voiceactionengine.core.transcript

import kotlinx.serialization.json.JsonObject

/**
 * One piece of an assistant turn in the neutral transcript.
 *
 * The two kinds, [Text] and [ToolCall], are the whole universe: a provider mapper switches over them exhaustively.
 * Thinking and any other provider-specific block lives only in [NativeReplay.raw], never as a part.
 */
public sealed class AssistantPart {
    /**
     * Plain text the model wrote.
     *
     * @property text the text. It is never printed by [toString].
     */
    public class Text(public val text: String) : AssistantPart() {
        /** Prints the length only, never the text. */
        override fun toString(): String = "Text(length=${text.length})"
    }

    /**
     * A tool the model asked to call.
     *
     * @property id the provider's id for this call; the matching [ToolResult] carries the same id.
     * @property name the name of the tool to call.
     * @property arguments the arguments the model chose. They are never printed by [toString].
     * @throws IllegalArgumentException when [id] or [name] is blank.
     */
    public class ToolCall(
        public val id: String,
        public val name: String,
        public val arguments: JsonObject,
    ) : AssistantPart() {
        init {
            require(id.isNotBlank()) { "a tool call id must not be blank" }
            require(name.isNotBlank()) { "a tool call name must not be blank" }
        }

        /** Prints the id, the name and the number of arguments, never the argument values. */
        override fun toString(): String = "ToolCall(id=$id, name=$name, argumentCount=${arguments.size})"
    }
}
