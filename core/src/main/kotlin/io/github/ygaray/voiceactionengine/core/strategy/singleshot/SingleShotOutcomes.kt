package io.github.ygaray.voiceactionengine.core.strategy.singleshot

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason

private const val UNKNOWN_MODEL_RESULT_CODE = "unknown_model_result"
private const val UNKNOWN_RESOLUTION_CODE = "unknown_resolution"

/** The two outcomes a tier may override; each receives the response, or null when the provider reported a failure. */
internal class OutcomeHooks(
    val onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome,
    val onRefusal: suspend (ModelResponse?) -> StrategyOutcome,
)

/**
 * Decides a provider result before any tool call is read. Returns the final outcome, or null when the result is a
 * usable answer that holds at least one tool call (the caller goes on to the first call).
 */
internal suspend fun decideResult(result: ModelResult, hooks: OutcomeHooks): StrategyOutcome? =
    when (result) {
        is ModelResult.Failure -> failureOutcome(result, hooks)
        is ModelResult.Success -> stopOutcome(result.response, hooks)
        else -> StrategyOutcome.Failed(FailureReason.Other(UNKNOWN_MODEL_RESULT_CODE))
    }

private suspend fun failureOutcome(failure: ModelResult.Failure, hooks: OutcomeHooks): StrategyOutcome =
    when (failure.reason) {
        is FailureReason.NoToolCall -> hooks.onNoToolCall(null)
        is FailureReason.Refusal -> hooks.onRefusal(null)
        else -> StrategyOutcome.Failed(failure.reason, failure.details)
    }

// The stop reason is decided first so a refused or truncated answer can never be resolved into a write.
private suspend fun stopOutcome(response: ModelResponse, hooks: OutcomeHooks): StrategyOutcome? =
    when (response.stopReason) {
        StopReason.REFUSAL -> hooks.onRefusal(response)
        StopReason.MAX_TOKENS -> StrategyOutcome.Failed(FailureReason.MaxTokens())
        StopReason.PAUSE_TURN -> StrategyOutcome.Failed(FailureReason.PauseTurn())
        StopReason.CONTEXT_WINDOW_EXCEEDED -> StrategyOutcome.Failed(FailureReason.ContextWindowExceeded())
        else -> if (response.message.toolCalls.isEmpty()) noToolCallOutcome(response, hooks) else null
    }

private suspend fun noToolCallOutcome(response: ModelResponse, hooks: OutcomeHooks): StrategyOutcome =
    when (response.stopReason) {
        StopReason.END_TURN -> hooks.onNoToolCall(response)
        StopReason.TOOL_USE -> StrategyOutcome.Failed(FailureReason.MalformedResponse())
        else -> StrategyOutcome.Failed(FailureReason.UnknownStop())
    }

/** Maps every resolution except steps, which [onSteps] handles. The set is open, so anything else fails loudly. */
internal suspend fun resolutionOutcome(
    resolution: Resolution,
    onSteps: suspend (Resolution.Steps) -> StrategyOutcome,
): StrategyOutcome =
    when (resolution) {
        is Resolution.Steps -> onSteps(resolution)
        is Resolution.NoMatch -> StrategyOutcome.NoMatch()
        is Resolution.Escalate -> StrategyOutcome.Escalate(resolution.reason, resolution.carry)
        is Resolution.Failed -> StrategyOutcome.Failed(resolution.reason, resolution.details)
        else -> StrategyOutcome.Failed(FailureReason.Other(UNKNOWN_RESOLUTION_CODE))
    }
