package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The default clock: the system time with the device's zone read again on every call. `Clock.systemDefaultZone()` would
 * freeze the zone when the strategy is built, and a strategy lives as long as the app, so a traveller's relative
 * phrases ("tomorrow at 9") would resolve against the old zone.
 */
internal object CurrentZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}

/** Fails the tier before it calls when the run has already reached the token ceiling; a call costs at least a token. */
internal fun ceilingReached(session: CommandSession): StrategyOutcome? =
    if (session.tokensUsed >= session.policy.tokenCeiling) tokenBudgetFailure() else null

/** Fails the tier after its call, before any resolution or write, when the turn just counted passed the ceiling. */
internal fun ceilingCrossed(session: CommandSession): StrategyOutcome? =
    if (session.tokensUsed > session.policy.tokenCeiling) tokenBudgetFailure() else null

/** The failure for a run that used up its iteration limit without finishing. */
internal fun iterationBudgetFailure(): StrategyOutcome =
    StrategyOutcome.Failed(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS))

/**
 * The failure for a stop that needs no tier hook, or null for any other stop. A truncated, paused or over-long answer
 * is never acted on, so it can never be turned into a write.
 */
internal fun stopFailure(stopReason: StopReason): StrategyOutcome? =
    when (stopReason) {
        StopReason.MAX_TOKENS -> StrategyOutcome.Failed(FailureReason.MaxTokens())
        StopReason.PAUSE_TURN -> StrategyOutcome.Failed(FailureReason.PauseTurn())
        StopReason.CONTEXT_WINDOW_EXCEEDED -> StrategyOutcome.Failed(FailureReason.ContextWindowExceeded())
        else -> null
    }

private fun tokenBudgetFailure(): StrategyOutcome =
    StrategyOutcome.Failed(FailureReason.BudgetExceeded(BudgetBound.TOKENS))
