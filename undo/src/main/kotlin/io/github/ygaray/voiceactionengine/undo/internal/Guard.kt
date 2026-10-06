@file:Suppress("TooGenericExceptionCaught")

package io.github.ygaray.voiceactionengine.undo.internal

import kotlin.coroutines.cancellation.CancellationException

private const val MAX_TOKEN_LENGTH = 256
private const val CLASS_NAME_LENGTH = 128
private const val UNKNOWN_CLASS = "Throwable"
private val UNSAFE_CLASS_CHARS = Regex("[^A-Za-z0-9_$.-]")

// This module's one never-throw collapse point: every adapter, compensator and store call goes through here, so an
// app fault becomes a typed value carrying a class name and never an exception message. A cancellation is always
// rethrown, because the standard library cannot tell a foreign cancellation from the caller's own.
internal inline fun <T> guardedCall(onFault: (String) -> T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: LinkageError) {
    onFault(errorClassOf(e))
} catch (e: Exception) {
    onFault(errorClassOf(e))
}

/**
 * The simple class name of [error], or the last segment of the JVM name for an anonymous class. Anything outside
 * letters, digits and `_ $ . -` becomes `_` and the name is cut to 128 characters.
 */
internal fun errorClassOf(error: Throwable): String {
    val type = error::class.java
    val name = type.simpleName.ifEmpty { type.name.substringAfterLast('.') }
    val clean = name.take(CLASS_NAME_LENGTH).replace(UNSAFE_CLASS_CHARS, "_")
    return clean.ifEmpty { UNKNOWN_CLASS }
}

/** Rejects a blank or over-long identifier. The message never carries the value. */
internal fun requireToken(name: String, value: String) {
    require(value.isNotBlank() && value.length <= MAX_TOKEN_LENGTH) {
        "$name must be non-blank and at most $MAX_TOKEN_LENGTH characters"
    }
}
