package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession

/** The session a tier sees: identity and limits, and a submit that goes straight to the run's coordinator. */
internal class RunSession(
    override val runId: String,
    override val parentRunId: String?,
    override val strategy: StrategyId,
    override val policy: TierPolicy,
    override val carry: Any?,
    private val coordinator: CommitCoordinator,
) : CommandSession() {
    override suspend fun submit(step: ToolStep): DispatchResult = coordinator.submit(step)
}
