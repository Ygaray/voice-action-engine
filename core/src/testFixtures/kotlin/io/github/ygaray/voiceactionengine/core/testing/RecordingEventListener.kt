package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A listener that remembers every event in arrival order. With [throwing] set it also throws on every event, after
 * recording it, to prove the engine survives a misbehaving listener.
 *
 * @param throwing whether to throw from every call.
 */
public class RecordingEventListener(private val throwing: Boolean = false) : PipelineEventListener {
    private val received = CopyOnWriteArrayList<PipelineEvent>()

    /** Every event received so far, in order. */
    public val events: List<PipelineEvent>
        get() = received.toList()

    override fun onEvent(event: PipelineEvent) {
        received.add(event)
        check(!throwing) { "listener failure" }
    }

    override fun toString(): String = "RecordingEventListener(events=${received.size}, throwing=$throwing)"
}
