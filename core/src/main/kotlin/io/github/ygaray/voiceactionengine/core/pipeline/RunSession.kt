package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord

/**
 * The session a tier sees: identity and limits, a submit that goes straight to the run's coordinator, and the hook
 * that reports model turns to the run's recorder.
 */
internal class RunSession(
    override val runId: String,
    override val parentRunId: String?,
    override val strategy: StrategyId,
    override val policy: TierPolicy,
    override val carry: Any?,
    private val coordinator: CommitCoordinator,
    private val recorder: RunRecorder,
) : CommandSession() {
    override val tokensUsed: Long
        get() = recorder.tokensUsed

    override suspend fun submit(step: ToolStep): DispatchResult = coordinator.submit(step)

    override suspend fun recordTurn(turn: TurnRecord) {
        recorder.turnRecorded(strategy, turn)
    }
}
