package io.github.ygaray.voiceactionengine.core.telemetry

/**
 * Receives [PipelineEvent]s as a run happens. Set it with the `listener` property of the pipeline builder.
 *
 * It is called on the pipeline's own coroutine, in order, and must return quickly: it cannot suspend, so it cannot
 * stall the run for long, but it can still slow it if it blocks. If it throws, the engine records the trace code
 * `listener_error` and carries on; the command is never aborted or changed, and the listener is not called again for
 * that failure.
 */
public fun interface PipelineEventListener {
    /** Called once per [event], in the order the events happened. */
    public fun onEvent(event: PipelineEvent)
}
