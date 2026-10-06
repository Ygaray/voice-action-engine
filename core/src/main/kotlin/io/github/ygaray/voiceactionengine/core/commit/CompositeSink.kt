package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.internal.guarded

/**
 * Puts several sinks in the one `commitSink` slot.
 *
 * The children are called one after another in the given order, each awaited, for both [CommitSink.onAction] and
 * [CommitSink.onRunClosed]. A child that throws never stops the children after it. When any child failed, the
 * composite then throws one exception whose message names only how many failed (never a child's own text), so the
 * pipeline records `sink_error` and the run itself is unaffected. A cancellation of the caller still propagates.
 *
 * Put the sink that must see every action first (for example an undo journal).
 *
 * @param sinks the children, at least one; the array is copied.
 * @throws IllegalArgumentException when [sinks] is empty.
 */
public fun compositeSink(vararg sinks: CommitSink): CommitSink {
    require(sinks.isNotEmpty()) { "compositeSink: at least one sink is required" }
    return CompositeSink(sinks.toList())
}

private class CompositeSink(private val children: List<CommitSink>) : CommitSink {
    override suspend fun onAction(event: ActionEvent) {
        var failed = 0
        for (child in children) {
            guarded(onFault = { failed += 1 }) { child.onAction(event) }
        }
        raiseIfAny(failed)
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) {
        var failed = 0
        for (child in children) {
            guarded(onFault = { failed += 1 }) { child.onRunClosed(runId, termination) }
        }
        raiseIfAny(failed)
    }

    private fun raiseIfAny(failed: Int) {
        if (failed > 0) throw CompositeSinkFault("compositeSink: $failed of ${children.size} child sinks failed")
    }

    override fun toString(): String = "CompositeSink(children=${children.size})"
}

// A fixed message with counts only: a child's own exception text can carry user data and is never forwarded.
private class CompositeSinkFault(message: String) : IllegalStateException(message)
