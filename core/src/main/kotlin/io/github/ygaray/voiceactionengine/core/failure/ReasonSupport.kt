package io.github.ygaray.voiceactionengine.core.failure

private const val HASH_PRIME = 31
private val STABLE_CODE = Regex("[a-z0-9_]+")

/** Folds [next] into the running hash [acc], the way every value type in this package combines its fields. */
internal fun mixHash(acc: Int, next: Int): Int = acc * HASH_PRIME + next

/** Shared redaction-safe rendering: type name, leaf name, code, and any extra stable fields. */
internal fun describe(family: String, leaf: String, code: String, vararg extras: Pair<String, Any?>): String {
    val tail = extras.joinToString("") { ", ${it.first}=${it.second}" }
    return "$family.$leaf(code=$code$tail)"
}

/** True when [value] looks like a stable lower snake case code rather than a message. */
internal fun isStableCode(value: String): Boolean = STABLE_CODE.matches(value)
