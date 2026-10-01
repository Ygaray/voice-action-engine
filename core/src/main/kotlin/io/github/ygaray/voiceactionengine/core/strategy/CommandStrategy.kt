package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId

/**
 * One tier of an app's ladder: a way of turning a spoken command into an outcome.
 *
 * Strategies never write. Every write is a [io.github.ygaray.voiceactionengine.core.commit.ToolStep] passed to
 * [CommandSession.submit], so the pipeline's gate and commit sink always see it.
 */
public interface CommandStrategy {
    /** The stable identity of this tier. Ids must be unique within one pipeline. */
    public val id: StrategyId

    /** Handles [input] using [session] for every write, and says how it went. */
    public suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome
}
