package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ExecutedAction
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason

/**
 * Collects what one run did, hands out immutable [CommandTrace] snapshots, and delivers each happening to the app's
 * [listener] as it occurs. Safe to call from several threads.
 *
 * State changes happen under a lock; the listener is called after the lock is released, so app code never runs while
 * the lock is held. A listener that throws is recorded once as [TraceCode.LISTENER_ERROR]; the failure itself
 * produces no event.
 *
 * @param clock milliseconds on a monotonic clock; the first reading is the run's start.
 * @param listener where events go, or null when the app set none.
 */
internal class RunRecorder(
    private val runId: String,
    private val parentRunId: String?,
    private val language: String?,
    private val transcriptLength: Int,
    private val clock: () -> Long,
    listener: PipelineEventListener? = null,
) {
    private val lock = Any()
    private val startedAt = clock()
    private val attempts = mutableListOf<TierAttempt>()
    private val codes = mutableListOf<TraceCode>()
    private val currentTurns = mutableListOf<TurnRecord>()
    private var tierStartedAt = startedAt
    private var tokenTotal = 0L
    private val dispatch = EventDispatch(listener) { synchronized(lock) { codes.add(TraceCode.LISTENER_ERROR) } }
    private val skipped = mutableListOf<StrategyId>()

    /** The tokens used by every turn reported so far in this run, across tiers. */
    val tokensUsed: Long
        get() = synchronized(lock) { tokenTotal }

    /** Announces that the run began. */
    suspend fun commandStarted() {
        dispatch.send(PipelineEvent.CommandStarted(runId, parentRunId))
    }

    /** Marks the moment [strategy] starts, for its latency, and starts collecting its turns. */
    suspend fun tierStarted(strategy: StrategyId) {
        synchronized(lock) {
            check(strategy !in skipped) { "tier $strategy was skipped and cannot start" }
            tierStartedAt = clock()
            currentTurns.clear()
        }
        dispatch.send(PipelineEvent.TierStarted(runId, strategy))
    }

    /** Records that [strategy] did not run, as [code]. */
    suspend fun tierSkipped(strategy: StrategyId, code: TraceCode) {
        synchronized(lock) {
            skipped.add(strategy)
            codes.add(code)
        }
        dispatch.send(PipelineEvent.TierSkipped(runId, strategy, code))
    }

    /** Attaches [turn], reported by [strategy], to the tier now running, and adds its tokens to the run total. */
    suspend fun turnRecorded(strategy: StrategyId, turn: TurnRecord) {
        synchronized(lock) {
            currentTurns.add(turn)
            tokenTotal += turn.usage.total
        }
        dispatch.send(PipelineEvent.ProviderCall(runId, strategy, turn))
    }

    /** Records how the tier that started last ended. */
    suspend fun tierFinished(
        strategy: StrategyId,
        outcome: String,
        escalation: EscalationReason?,
        failure: FailureReason?,
        suppressed: EscalationReason?,
    ) {
        val attempt = synchronized(lock) {
            val made = TierAttempt(
                strategy,
                outcome,
                escalation,
                suppressed,
                failure,
                clock() - tierStartedAt,
                currentTurns.toList(),
            )
            currentTurns.clear()
            attempts.add(made)
            made
        }
        dispatch.send(PipelineEvent.TierFinished(runId, attempt))
    }

    /** Records an engine code. */
    suspend fun recordCode(code: TraceCode) {
        synchronized(lock) { codes.add(code) }
        dispatch.send(PipelineEvent.EngineCode(runId, code))
    }

    /** Announces that [action] was recorded, by id, position and tool name only; the action object never leaves. */
    suspend fun actionRecorded(action: ExecutedAction) {
        val event = PipelineEvent.ActionRecorded(runId, action.position, action.kind, action.toolName, action.applied)
        dispatch.send(event)
    }

    /** Announces that a provider response showed the prompt cache was not used. */
    suspend fun cacheNotEngaged(strategy: StrategyId, provider: ProviderId, model: String?) {
        dispatch.send(PipelineEvent.CacheNotEngaged(runId, strategy, provider, model))
    }

    /** Announces that the run ended as [termination]; the last event of the run. */
    suspend fun runClosed(termination: RunTermination) {
        val event = PipelineEvent.RunClosed(
            runId,
            termination.code,
            termination.executed.size,
            termination.commits.size,
        )
        dispatch.send(event)
    }

    /** An immutable trace of everything recorded so far. */
    fun snapshot(): CommandTrace = synchronized(lock) {
        CommandTrace(
            runId = runId,
            parentRunId = parentRunId,
            language = language,
            transcriptLength = transcriptLength,
            startedAtMillis = startedAt,
            durationMillis = clock() - startedAt,
            attempts = attempts.toList(),
            codes = codes.toList(),
        )
    }
}
