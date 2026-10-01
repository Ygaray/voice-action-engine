package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason

/**
 * Collects what one run did and hands out immutable [CommandTrace] snapshots. Safe to call from several threads.
 *
 * @param clock milliseconds on a monotonic clock; the first reading is the run's start.
 */
internal class RunRecorder(
    private val runId: String,
    private val parentRunId: String?,
    private val language: String?,
    private val transcriptLength: Int,
    private val clock: () -> Long,
) {
    private val lock = Any()
    private val startedAt = clock()
    private val attempts = mutableListOf<TierAttempt>()
    private val codes = mutableListOf<TraceCode>()
    private var tierStartedAt = startedAt
    private val skipped = mutableListOf<StrategyId>()

    /** Marks the moment [strategy] starts, for its latency. */
    fun tierStarted(strategy: StrategyId) {
        synchronized(lock) {
            check(strategy !in skipped) { "tier $strategy was skipped and cannot start" }
            tierStartedAt = clock()
        }
    }

    /** Records that [strategy] did not run, as [code]. */
    fun tierSkipped(strategy: StrategyId, code: TraceCode) {
        synchronized(lock) {
            skipped.add(strategy)
            codes.add(code)
        }
    }

    /** Records how the tier that started last ended. */
    fun tierFinished(
        strategy: StrategyId,
        outcome: String,
        escalation: EscalationReason?,
        failure: FailureReason?,
        suppressed: EscalationReason?,
    ) {
        synchronized(lock) {
            attempts.add(TierAttempt(strategy, outcome, escalation, suppressed, failure, clock() - tierStartedAt))
        }
    }

    /** Records an engine code. */
    fun recordCode(code: TraceCode) {
        synchronized(lock) { codes.add(code) }
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
