package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.internal.guardedUncancellable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hands events to the app's listener without letting it hurt the run. The first throw is reported through [onFault]
 * and is never turned into an event, so the listener is not re-entered for its own failure.
 *
 * @param listener the app's listener, or null for none.
 * @param onFault called once, the first time the listener throws; the recorder uses it to add the trace code.
 */
internal class EventDispatch(
    private val listener: PipelineEventListener?,
    private val onFault: () -> Unit,
) {
    private val reported = AtomicBoolean(false)

    /**
     * Delivers [event] if there is a listener. Nothing the listener throws escapes: it cannot suspend, so a
     * `CancellationException` from it is never the caller's own cancellation and counts as a fault.
     */
    suspend fun send(event: PipelineEvent) {
        val target = listener ?: return
        guardedUncancellable(onFault = { if (reported.compareAndSet(false, true)) onFault() }) { target.onEvent(event) }
    }
}
