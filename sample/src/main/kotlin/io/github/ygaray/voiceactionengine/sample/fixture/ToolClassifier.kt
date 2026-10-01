package io.github.ygaray.voiceactionengine.sample.fixture

private val READ_PREFIXES = listOf("get_", "list_", "find_", "search_")

/**
 * Decides read versus mutating from a tool name alone, so the sample never hard-codes any app's tool names: a name that
 * starts with get_, list_, find_ or search_ is a read; anything else is treated as mutating (the safe default, since a
 * mutating tool goes through the gate).
 */
internal object ToolClassifier {
    /** True unless [name] starts with one of the four read prefixes. */
    fun isMutating(name: String): Boolean = READ_PREFIXES.none { name.startsWith(it) }
}
