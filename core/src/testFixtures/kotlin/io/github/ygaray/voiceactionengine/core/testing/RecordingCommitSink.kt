package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A sink that records what it receives. Each call is recorded first, then logged as `sink:action:<position>:<kind>`
 * or `sink:closed:<runId>:<code>`, then the matching hook runs, so a test can make a hook suspend or throw.
 *
 * @param log an optional shared log.
 * @param onActionHook runs after an action was recorded and logged.
 * @param onRunClosedHook runs after a run close was recorded and logged.
 */
public class RecordingCommitSink(
    private val log: RecordingSink<String>? = null,
    private val onActionHook: suspend (ActionEvent) -> Unit = {},
    private val onRunClosedHook: suspend (String, RunTermination) -> Unit = { _, _ -> },
) : CommitSink {
    private val receivedActions = CopyOnWriteArrayList<ActionEvent>()
    private val receivedCloses = CopyOnWriteArrayList<RunTermination>()
    private val receivedRunIds = CopyOnWriteArrayList<String>()

    /** Every action event received, in order. */
    public val actions: List<ActionEvent>
        get() = receivedActions.toList()

    /** Every termination received, in order. */
    public val closes: List<RunTermination>
        get() = receivedCloses.toList()

    /** The run id passed with each close, in order. */
    public val closedRunIds: List<String>
        get() = receivedRunIds.toList()

    override suspend fun onAction(event: ActionEvent) {
        receivedActions.add(event)
        log?.record("sink:action:${event.action.position}:${event.action.kind}")
        onActionHook(event)
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) {
        receivedCloses.add(termination)
        receivedRunIds.add(runId)
        log?.record("sink:closed:$runId:${termination.code}")
        onRunClosedHook(runId, termination)
    }
}
