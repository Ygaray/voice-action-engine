package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ExecutedAction
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.GuardedClock

/**
 * Collects what one run did, hands out immutable [CommandTrace] snapshots, and delivers each happening to the app's
 * [listener] as it occurs. Safe to call from several threads.
 *
 * State changes happen under a lock; the listener is called after the lock is released, and the app's clock is read
 * before the lock is taken, so app code never runs while the lock is held. A listener that throws is recorded once as
 * [TraceCode.LISTENER_ERROR]; the failure itself produces no event.
 *
 * @param clock milliseconds on a monotonic clock; the first reading is the run's start and may throw, later readings
 * that throw answer with the last good one.
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
    /** The run's clock: its first reading may throw, later readings never do. */
    val runClock = GuardedClock(clock)
    private val startedAt = runClock.read()
    private val codes = mutableListOf<TraceCode>()
    private val book = TierBook(startedAt)
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

    /**
     * Marks the moment [strategy] starts, for its latency, and starts collecting its turns. [carryIn] is whether the
     * tier was handed the previous tier's carry; only that fact is kept, never the carry.
     */
    suspend fun tierStarted(strategy: StrategyId, carryIn: Boolean) {
        val now = runClock.read()
        synchronized(lock) {
            check(strategy !in skipped) { "tier $strategy was skipped and cannot start" }
            book.start(strategy, now, carryIn)
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
            book.turns.add(turn)
            tokenTotal = saturatedAdd(tokenTotal, turn.usage.total)
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
        val now = runClock.read()
        val attempt = synchronized(lock) {
            book.close(now) { latency, carry, turns ->
                TierAttempt(strategy, outcome, escalation, suppressed, failure, latency, carry, turns)
            }
        }
        dispatch.send(PipelineEvent.TierFinished(runId, attempt))
    }

    /**
     * Records the tier that started and never finished, because the engine deadline cut it off, the caller cancelled,
     * or an error ended it. Without this its turns, and the tokens they cost, would be missing from the trace. It does
     * nothing when no tier is in flight, so it is safe to call on every exit path.
     */
    suspend fun flushInFlight(outcome: String, failure: FailureReason?) {
        if (synchronized(lock) { book.inFlight } == null) return
        val now = runClock.read()
        val attempt = synchronized(lock) {
            book.inFlight?.let { tier ->
                book.close(now) { latency, carry, turns ->
                    TierAttempt(tier, outcome, null, null, failure, latency, carry, turns)
                }
            }
        }
        if (attempt != null) dispatch.send(PipelineEvent.TierFinished(runId, attempt))
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

    /**
     * Announces that a provider response showed the prompt cache was not used. The engine calls this after a routed
     * response whose cache should have engaged; the event carries ids only.
     */
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
    fun snapshot(): CommandTrace {
        val now = runClock.read()
        return synchronized(lock) {
            CommandTrace(
                runId = runId,
                parentRunId = parentRunId,
                language = language,
                transcriptLength = transcriptLength,
                startedAtMillis = startedAt,
                durationMillis = now - startedAt,
                attempts = book.attempts.toList(),
                codes = codes.toList(),
            )
        }
    }
}

/** The attempts a run has made and the tier now running. Callers hold the recorder's lock. */
private class TierBook(startedAt: Long) {
    val attempts = mutableListOf<TierAttempt>()
    val turns = mutableListOf<TurnRecord>()
    var inFlight: StrategyId? = null
    private var tierStartedAt = startedAt
    private var tierCarryIn = false

    /** Begins [strategy] at [now], with no turns yet; [carryIn] is whether it received a carry. */
    fun start(strategy: StrategyId, now: Long, carryIn: Boolean) {
        tierStartedAt = now
        tierCarryIn = carryIn
        turns.clear()
        inFlight = strategy
    }

    /**
     * Ends the tier running now at [now]; [build] makes its attempt from the latency, whether it received a carry and
     * its turns, which is then kept.
     */
    fun close(
        now: Long,
        build: (latency: Long, carryIn: Boolean, turns: List<TurnRecord>) -> TierAttempt,
    ): TierAttempt {
        val made = build(now - tierStartedAt, tierCarryIn, turns.toList())
        turns.clear()
        attempts.add(made)
        inFlight = null
        return made
    }
}
