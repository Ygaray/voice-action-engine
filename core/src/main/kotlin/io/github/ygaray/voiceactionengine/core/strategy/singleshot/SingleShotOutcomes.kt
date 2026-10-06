package io.github.ygaray.voiceactionengine.core.strategy.singleshot

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.stopFailure
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason

private const val UNKNOWN_MODEL_RESULT_CODE = "unknown_model_result"

/**
 * The outcomes a tier may override. [onNoToolCall] and [onRefusal] receive the response, or null when the provider
 * reported the condition as a failure; [onFailed] receives the reason and details of any other provider failure.
 */
internal class OutcomeHooks(
    val onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome,
    val onRefusal: suspend (ModelResponse?) -> StrategyOutcome,
    val onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome,
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
        else -> hooks.onFailed(failure.reason, failure.details)
    }

// The stop reason is decided first so a refused or truncated answer can never be resolved into a write.
private suspend fun stopOutcome(response: ModelResponse, hooks: OutcomeHooks): StrategyOutcome? =
    when (response.stopReason) {
        StopReason.REFUSAL -> hooks.onRefusal(response)
        else -> stopFailure(response.stopReason)
            ?: if (response.message.toolCalls.isEmpty()) noToolCallOutcome(response, hooks) else null
    }

private suspend fun noToolCallOutcome(response: ModelResponse, hooks: OutcomeHooks): StrategyOutcome =
    when (response.stopReason) {
        StopReason.END_TURN -> hooks.onNoToolCall(response)
        StopReason.TOOL_USE -> StrategyOutcome.Failed(FailureReason.MalformedResponse())
        else -> StrategyOutcome.Failed(FailureReason.UnknownStop())
    }
