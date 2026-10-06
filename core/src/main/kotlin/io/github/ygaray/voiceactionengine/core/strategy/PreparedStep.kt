package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

private const val TOOL_ERROR_CONTENT = """{"status":"error","reason":"tool_error"}"""

/**
 * Asks the app's [executor] to prepare [extraction] for the tool [spec], and never lets a fault escape.
 *
 * An executor fault is answered with a fixed notice, never the exception text. A failed attempt on a mutating tool is
 * recorded as an error action; a failed read leaves no record. A cancellation always propagates. Shared by the tiers
 * that prepare model-chosen calls, so the fault path exists once.
 */
internal suspend fun prepareGuarded(
    session: CommandSession,
    spec: ToolSpec,
    executor: ToolExecutor,
    input: CommandInput,
    extraction: Extraction,
): ToolStep =
    guarded(onFault = {
        session.recordCode(TraceCode.TOOL_PREPARE_ERROR)
        faultStep(spec)
    }) { executor.prepare(extraction, input) }

private fun faultStep(spec: ToolSpec): ToolStep {
    val kind = if (spec.mutating) FinishedKind.ERROR else FinishedKind.READ
    return ToolStep.Finished(spec.name, kind, StepResult(TOOL_ERROR_CONTENT, true))
}
