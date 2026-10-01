package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What every tier of one command shares: the run's identity, the limits in force, its write path and trace, and the
 * router that binds a tier's model. Built once per command.
 */
internal class RunScope(
    val runId: String,
    val parentRunId: String?,
    val policy: TierPolicy,
    val coordinator: CommitCoordinator,
    val recorder: RunRecorder,
    val router: ModelRouter,
)

/**
 * The session a tier sees: identity and limits, a submit that goes straight to the run's coordinator, the hook that
 * reports model turns to the run's recorder, and the model handle, bound lazily and frozen for this tier's run.
 *
 * @param declared the providers the tier declared, read once when the session is created.
 */
internal class RunSession(
    private val scope: RunScope,
    override val strategy: StrategyId,
    private val declared: Set<ProviderId>,
    override val carry: Any?,
) : CommandSession() {
    private val bindLock = Mutex()

    @Volatile
    private var bound: BoundModel? = null

    override val runId: String
        get() = scope.runId

    override val parentRunId: String?
        get() = scope.parentRunId

    override val policy: TierPolicy
        get() = scope.policy

    override val tokensUsed: Long
        get() = scope.recorder.tokensUsed

    override suspend fun submit(step: ToolStep): DispatchResult = scope.coordinator.submit(step)

    override suspend fun recordTurn(turn: TurnRecord) {
        scope.recorder.turnRecorded(strategy, turn)
    }

    /**
     * Binds on the first call and keeps that handle. The slot is assigned only after the bind returns, so a call
     * cancelled while the selection source is suspended freezes nothing and the next call binds afresh.
     */
    override suspend fun model(): BoundModel = bound ?: bindLock.withLock {
        bound ?: scope.router.bind(strategy, declared, scope.policy, scope.recorder).also { bound = it }
    }
}
