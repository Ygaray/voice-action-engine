package io.github.ygaray.voiceactionengine.core.transcript

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec

/**
 * One request to a model, in neutral terms.
 *
 * It carries no model id: the model is bound per command by the router, so the same request goes to whichever model
 * the app selected. [maxTokens] comes from the command's policy, which is why no constructor has a default for it.
 *
 * @property system the system prompt. It is never printed by [toString].
 * @property messages the conversation so far, oldest first; a copy, never empty.
 * @property tools the tools the model may call; a copy, with distinct names.
 * @property toolChoice whether the model decides to call a tool or must call a named one.
 * @property maxTokens the most tokens the model may generate, at least 1.
 * @property cache which parts of the request the provider is asked to cache.
 * @property singleToolCall true to ask the model for at most one tool call in its answer (exactly one when
 * [toolChoice] requires a tool). A provider whose wire format cannot express this sends the request without it, and
 * the strategy still uses only the first call. It has no effect when [tools] is empty.
 * @throws IllegalArgumentException when [messages] is empty, [maxTokens] is below 1, two tools share a name, or
 * [toolChoice] requires a tool that is not in [tools].
 */
public class ModelRequest(
    public val system: String,
    messages: List<Message>,
    tools: List<ToolSpec>,
    public val toolChoice: ToolChoice,
    public val maxTokens: Int,
    public val cache: CacheDirective,
    public val singleToolCall: Boolean,
) {
    /** A request that leaves the number of tool calls to the model. */
    public constructor(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
        toolChoice: ToolChoice,
        maxTokens: Int,
        cache: CacheDirective,
    ) : this(system, messages, tools, toolChoice, maxTokens, cache, false)

    /** A request with no tools, automatic tool choice and the static prefix cached. */
    public constructor(system: String, messages: List<Message>, maxTokens: Int) :
        this(system, messages, emptyList(), ToolChoice.Auto(), maxTokens, CacheDirective(true), false)

    /** A request with [tools], automatic tool choice and the static prefix cached. */
    public constructor(system: String, messages: List<Message>, tools: List<ToolSpec>, maxTokens: Int) :
        this(system, messages, tools, ToolChoice.Auto(), maxTokens, CacheDirective(true), false)

    /** A copy of the messages. */
    public val messages: List<Message> = messages.toList()

    /** A copy of the tools. */
    public val tools: List<ToolSpec> = tools.toList()

    init {
        require(this.messages.isNotEmpty()) { "a request must carry at least one message" }
        require(maxTokens >= 1) { "maxTokens must be at least 1" }
        val names = this.tools.map { it.name }
        require(names.toSet().size == names.size) { "tool names must be distinct" }
        val choice = toolChoice
        require(choice !is ToolChoice.Required || choice.toolName in names) {
            "a required tool choice must name a tool in the request"
        }
    }

    /** Prints the system prompt length, counts and tool names only, never prompt or message content. */
    override fun toString(): String =
        "ModelRequest(systemLength=${system.length}, messages=${messages.size}, tools=${tools.map { it.name }}, " +
            "toolChoice=$toolChoice, maxTokens=$maxTokens, cache=$cache, singleToolCall=$singleToolCall)"
}

/**
 * Whether the model may or must call a tool. The set is open (later versions may add choices), so keep an `else`
 * branch when switching on one.
 */
public abstract class ToolChoice internal constructor() {
    /** The model decides whether to call a tool. */
    public class Auto : ToolChoice() {
        override fun equals(other: Any?): Boolean = other is Auto

        override fun hashCode(): Int = AUTO_HASH

        override fun toString(): String = "ToolChoice.Auto"
    }

    /**
     * The model must call the tool named [toolName].
     *
     * @property toolName the tool the model must call.
     * @throws IllegalArgumentException when [toolName] is blank.
     */
    public class Required(public val toolName: String) : ToolChoice() {
        init {
            require(toolName.isNotBlank()) { "a required tool name must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is Required && other.toolName == toolName

        override fun hashCode(): Int = toolName.hashCode()

        override fun toString(): String = "ToolChoice.Required(toolName=$toolName)"
    }
}

private const val AUTO_HASH = 1

/**
 * Which parts of a request the provider is asked to cache.
 *
 * @property staticPrefix true to cache the frozen tools plus system prefix.
 * @property conversationTail true to also cache the moving tail of the conversation. A provider may ignore it: the
 * Anthropic provider in v1.0 does (it places a single breakpoint after the static prefix), and honouring the tail for
 * growing agentic transcripts is deferred to a later version.
 */
public class CacheDirective(
    public val staticPrefix: Boolean,
    public val conversationTail: Boolean,
) {
    /** Cache the static prefix and not the conversation tail. */
    public constructor(staticPrefix: Boolean) : this(staticPrefix, false)

    override fun equals(other: Any?): Boolean =
        other is CacheDirective && other.staticPrefix == staticPrefix && other.conversationTail == conversationTail

    override fun hashCode(): Int = 2 * staticPrefix.hashCode() + conversationTail.hashCode()

    override fun toString(): String = "CacheDirective(staticPrefix=$staticPrefix, conversationTail=$conversationTail)"
}
