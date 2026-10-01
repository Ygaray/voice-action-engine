package io.github.ygaray.voiceactionengine.core.strategy.agentic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult

private const val UNKNOWN_TOOL_CONTENT = """{"status":"error","reason":"unknown_tool"}"""
private const val TOOL_ERROR_CONTENT = """{"status":"error","reason":"tool_error"}"""
private const val STRIKES_TO_ABORT = 2
private const val NOT_A_MUTATING_TOOL_CONTENT = """{"status":"error","reason":"not_a_mutating_tool"}"""

/** What dispatching a turn's calls needs: the run's session, the command, the offered tools and the app's executor. */
internal class DispatchContext(
    val session: CommandSession,
    val input: CommandInput,
    val snapshot: ToolingSnapshot,
    val executor: ToolExecutor,
) {
    // Errors per tool name for the whole command. A held result is not an error and never counts.
    private val strikes = mutableMapOf<String, Int>()

    /** Counts one error result for the tool called [toolName]. */
    fun strike(toolName: String) {
        strikes[toolName] = (strikes[toolName] ?: 0) + 1
    }

    /** True once some tool has returned errors often enough to end the run. */
    fun struckOut(): Boolean = strikes.values.any { it >= STRIKES_TO_ABORT }
}

/** What a turn's dispatch produced: one result per call in call order, and whether the run must end after the turn. */
internal class TurnDispatch(
    val results: List<ToolResult>,
    val struckOut: Boolean,
)

/**
 * Runs a turn's [calls] one at a time, in the order the model emitted them, never concurrently, and returns one result
 * per call in the same order. Every change goes through the session, so the gate decides. A tool that has returned
 * errors twice in the command ends the run, but only after the rest of the turn ran.
 */
internal suspend fun dispatchCalls(
    context: DispatchContext,
    calls: List<AssistantPart.ToolCall>,
): TurnDispatch {
    val results = calls.map { dispatchCall(context, it) }
    return TurnDispatch(results, context.struckOut())
}

private suspend fun dispatchCall(context: DispatchContext, call: AssistantPart.ToolCall): ToolResult {
    val spec = context.snapshot.tools.firstOrNull { it.name == call.name }
    val result = if (spec == null) unknownTool(context, call) else settle(context, spec, call)
    if (result.isError) context.strike(call.name)
    return result
}

// A call to a tool the tier never offered is not trusted: the app is not asked and nothing is recorded.
private suspend fun unknownTool(context: DispatchContext, call: AssistantPart.ToolCall): ToolResult {
    context.session.recordCode(TraceCode.UNKNOWN_TOOL)
    return ToolResult(call.id, UNKNOWN_TOOL_CONTENT, true)
}

// The one place a prepared step becomes a result for the model; every step is submitted, so the write path is single.
private suspend fun settle(context: DispatchContext, spec: ToolSpec, call: AssistantPart.ToolCall): ToolResult {
    val step = guardWrites(context, spec, prepare(context, spec, call))
    val dispatch = context.session.submit(step)
    return ToolResult(call.id, dispatch.contentForModel, dispatch.isError)
}

// A tool not declared mutating can never reach the gate: its change is dropped and the model gets an error. A read
// step is never recorded, so nothing reaches the ledger or the sink.
private suspend fun guardWrites(context: DispatchContext, spec: ToolSpec, step: ToolStep): ToolStep {
    if (step !is ToolStep.Mutation || spec.mutating) return step
    context.session.recordCode(TraceCode.READ_TOOL_MUTATION_REJECTED)
    return ToolStep.Finished(spec.name, FinishedKind.READ, StepResult(NOT_A_MUTATING_TOOL_CONTENT, true))
}

// An executor fault is answered with a fixed notice, never the exception text. A failed attempt on a mutating tool is
// recorded as an error action; a failed read leaves no record.
private suspend fun prepare(context: DispatchContext, spec: ToolSpec, call: AssistantPart.ToolCall): ToolStep =
    guarded(onFault = {
        context.session.recordCode(TraceCode.TOOL_PREPARE_ERROR)
        faultStep(spec)
    }) { context.executor.prepare(Extraction(call.name, call.arguments), context.input) }

private fun faultStep(spec: ToolSpec): ToolStep {
    val kind = if (spec.mutating) FinishedKind.ERROR else FinishedKind.READ
    return ToolStep.Finished(spec.name, kind, StepResult(TOOL_ERROR_CONTENT, true))
}
