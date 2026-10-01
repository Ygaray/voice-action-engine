package io.github.ygaray.voiceactionengine.core.failure

private const val HASH_PRIME = 31
private val STABLE_CODE = Regex("[a-z0-9_]+")
private val SAFE_TOKEN = Regex("[A-Za-z0-9_.:-]+")
private val CLASS_NAME = Regex("[A-Za-z0-9_.$-]+")

private const val CODE_LENGTH = 64
private const val ID_LENGTH = 128

/** Folds [next] into the running hash [acc], the way every value type in this package combines its fields. */
internal fun mixHash(acc: Int, next: Int): Int = acc * HASH_PRIME + next

/** Shared redaction-safe rendering: type name, leaf name, code, and any extra stable fields. */
internal fun describe(family: String, leaf: String, code: String, vararg extras: Pair<String, Any?>): String {
    val tail = extras.joinToString("") { ", ${it.first}=${it.second}" }
    return "$family.$leaf(code=$code$tail)"
}

/** True when [value] looks like a stable lower snake case code rather than a message. */
internal fun isStableCode(value: String): Boolean = STABLE_CODE.matches(value)

/**
 * True when [value] is a short identifier (letters, digits and `_ . : -`, 1 to 64 characters) rather than free text:
 * no whitespace, line breaks or quotes, so it cannot carry a sentence, a key fragment or a forged log line.
 */
internal fun isSafeToken(value: String): Boolean = value.length <= CODE_LENGTH && SAFE_TOKEN.matches(value)

/** Like [isSafeToken] with room for a provider request id (up to 128 characters). */
internal fun isRequestId(value: String): Boolean = value.length <= ID_LENGTH && SAFE_TOKEN.matches(value)

/** True when [value] is a JVM class name or simple name of at most 128 characters (also allows `$`). */
internal fun isClassName(value: String): Boolean = value.length <= ID_LENGTH && CLASS_NAME.matches(value)
