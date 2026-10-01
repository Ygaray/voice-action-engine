package io.github.ygaray.voiceactionengine.providers.http

/**
 * Runs an app-supplied diagnostics callback ([block]) so that nothing it throws can change a call's outcome (a billed,
 * decoded answer would be lost). The exception, which could carry any text, is dropped unread. This is the one place in
 * the transports that catches broadly, for that one reason; it is not a suspend function, so no cancellation signal can
 * pass through it.
 */
internal fun notifyQuietly(block: () -> Unit) {
    try {
        block()
    } catch (ignored: Exception) {
        // Intentionally empty: see above.
    }
}
