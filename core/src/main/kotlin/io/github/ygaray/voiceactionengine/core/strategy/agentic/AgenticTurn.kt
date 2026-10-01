package io.github.ygaray.voiceactionengine.core.strategy.agentic

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.stopFailure
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason

private const val UNKNOWN_MODEL_RESULT_CODE = "unknown_model_result"

/**
 * Decides a provider result before any tool call is read. Returns the final outcome, or null when the result is a
 * usable answer that holds at least one tool call, which makes it a tool turn.
 *
 * An answer whose stop reason is refusal, max tokens, pause turn or context window exceeded is final even when it holds
 * tool calls, so it can never be turned into a write. Any other answer that holds tool calls is a tool turn whatever
 * its stop reason says, because the providers differ in the stop reason they report for one; that includes a stop
 * reason the engine does not map.
 */
internal fun decideTurn(result: ModelResult): StrategyOutcome? =
    when (result) {
        is ModelResult.Failure -> StrategyOutcome.Failed(result.reason, result.details)
        is ModelResult.Success -> decideResponse(result.response)
        else -> StrategyOutcome.Failed(FailureReason.Other(UNKNOWN_MODEL_RESULT_CODE))
    }

private fun decideResponse(response: ModelResponse): StrategyOutcome? =
    when (response.stopReason) {
        StopReason.REFUSAL -> StrategyOutcome.Failed(FailureReason.Refusal())
        else -> stopFailure(response.stopReason)
            ?: if (response.message.toolCalls.isEmpty()) answerWithoutCalls(response) else null
    }

private fun answerWithoutCalls(response: ModelResponse): StrategyOutcome =
    when (response.stopReason) {
        StopReason.END_TURN -> StrategyOutcome.Completed(firstText(response))
        StopReason.TOOL_USE -> StrategyOutcome.Failed(FailureReason.MalformedResponse())
        else -> StrategyOutcome.Failed(FailureReason.UnknownStop())
    }

/** The text of the answer's first text part, or null when it has none. */
private fun firstText(response: ModelResponse): String? =
    response.message.parts.filterIsInstance<AssistantPart.Text>().firstOrNull()?.text
