@file:Suppress("TooGenericExceptionCaught")

package io.github.ygaray.voiceactionengine.core.internal

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * What went wrong inside a guarded block, reduced to what is safe to record.
 *
 * @property errorClass the exception's class name only; messages can carry URLs, keys or user text.
 * @property timeoutLeak true when a timeout from inside the block escaped while the run itself was still active.
 */
internal class EngineFault(
    val errorClass: String,
    val timeoutLeak: Boolean,
)

/**
 * Runs [block] and turns anything it throws into [onFault]'s value, except real cancellation, which propagates.
 *
 * Catch order matters and each clause is separate so no type check is needed inside a general catch:
 * a timeout is rethrown when the caller is cancelled and is a fault when the caller is still active (an inner
 * `withTimeout` in app code leaked out); any other cancellation is rethrown only when the caller is cancelled and is
 * a fault when the caller is still active (a foreign cancelled call or deferred); linkage errors and exceptions are
 * faults.
 * Other errors (out of memory, assertion failures) are not caught.
 *
 * Use [guardedUncancellable] instead for app code that runs under `NonCancellable`.
 */
internal suspend inline fun <T> guarded(onFault: (EngineFault) -> T, block: () -> T): T =
    guardedCore(cancellationIsFault = false, onFault = onFault, block = block)

/**
 * Like [guarded], for app code the engine runs under `NonCancellable` (sink delivery, run close, event listeners).
 *
 * There the caller's own cancellation cannot be what surfaces, so any [CancellationException] thrown by the app code
 * (a cancelled deferred awaited by the sink, a closed storage scope) is foreign and is a fault like any other. Letting
 * it escape would replace the run's outcome, skip the rest of the close and abort the siblings of a batch.
 */
internal suspend inline fun <T> guardedUncancellable(onFault: (EngineFault) -> T, block: () -> T): T =
    guardedCore(cancellationIsFault = true, onFault = onFault, block = block)

// The engine's never-throw collapse points (this one and guardedPlain below); the file-level suppression is the
// repository's only one, and every app callback is routed through here.
internal suspend inline fun <T> guardedCore(
    cancellationIsFault: Boolean,
    onFault: (EngineFault) -> T,
    block: () -> T,
): T = try {
    block()
} catch (e: TimeoutCancellationException) {
    if (!cancellationIsFault && !currentCoroutineContext().isActive) throw e
    onFault(EngineFault(errorClassOf(e), timeoutLeak = true))
} catch (e: CancellationException) {
    if (!cancellationIsFault && !currentCoroutineContext().isActive) throw e
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
} catch (e: LinkageError) {
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
} catch (e: Exception) {
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
}

/**
 * Like [guarded] for app code that is not a suspend call (a plain function such as the clock): exceptions and linkage
 * errors become [onFault]'s value; other JVM errors propagate. There is no coroutine cancellation to preserve.
 */
internal inline fun <T> guardedPlain(onFault: (EngineFault) -> T, block: () -> T): T = try {
    block()
} catch (e: LinkageError) {
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
} catch (e: Exception) {
    onFault(EngineFault(errorClassOf(e), timeoutLeak = false))
}

/** The simple class name, or the last segment of the JVM name when the class is anonymous. */
internal fun errorClassOf(error: Throwable): String {
    val type = error::class.java
    return type.simpleName.ifEmpty { type.name.substringAfterLast('.') }
}
