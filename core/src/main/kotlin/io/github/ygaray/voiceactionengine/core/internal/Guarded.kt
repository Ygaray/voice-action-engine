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
 * `withTimeout` in app code leaked out); any other cancellation is rethrown; linkage errors and exceptions are faults.
 * Other errors (out of memory, assertion failures) are not caught.
 */
// The engine's single never-throw collapse point; every app callback is routed through here.
@Suppress("TooGenericExceptionCaught")
internal suspend inline fun <T> guarded(onFault: (EngineFault) -> T, block: () -> T): T = try {
    block()
} catch (e: TimeoutCancellationException) {
    if (!currentCoroutineContext().isActive) throw e
    onFault(EngineFault(errorClassOf(e), timeoutLeak = true))
} catch (e: CancellationException) {
    throw e
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
