package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.CommandInput

/**
 * Supplies the system text and the tools for one command. A strategy calls it once per command.
 *
 * The system text and the tool list must not depend on the command: together they form the prefix a provider caches,
 * so anything that varies per command would break the cache. Per-command text (the date, the transcript) belongs in
 * the user turn, which the user-turn renderer produces.
 */
public fun interface ToolSpecProvider {
    /** Returns the tooling for the command described by [input]. */
    public suspend fun tooling(input: CommandInput): ToolingSnapshot

    /** Ways to create a provider. */
    public companion object {
        /** A provider that returns [snapshot] for every command. */
        public fun fixed(snapshot: ToolingSnapshot): ToolSpecProvider = ToolSpecProvider { snapshot }
    }
}

/**
 * The system text and the tools a strategy sends with a command.
 *
 * @property system the system prompt. It is never printed by [toString].
 * @property tools the tools the model may call; a copy.
 * @property singleShotTool the name of the tool a single-shot tier forces the model to call, or null. A multi-turn tier
 * leaves it null.
 * @throws IllegalArgumentException when [singleShotTool] is not null and does not name a tool in [tools].
 */
public class ToolingSnapshot(
    public val system: String,
    tools: List<ToolSpec>,
    public val singleShotTool: String?,
) {
    /** A copy of the tools. */
    public val tools: List<ToolSpec> = tools.toList()

    init {
        require(singleShotTool == null || this.tools.any { it.name == singleShotTool }) {
            "singleShotTool must name a tool in tools"
        }
    }

    /** Prints the system text length, the tool names and the forced tool name only, never the text. */
    override fun toString(): String =
        "ToolingSnapshot(systemLength=${system.length}, tools=${tools.map { it.name }}, " +
            "singleShotTool=$singleShotTool)"
}
