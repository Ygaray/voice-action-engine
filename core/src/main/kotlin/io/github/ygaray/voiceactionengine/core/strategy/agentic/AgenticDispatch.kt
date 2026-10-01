package io.github.ygaray.voiceactionengine.core.strategy.agentic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

    /** The offered tool called [name], or null when the tier never offered it. */
    fun specOf(name: String): ToolSpec? = snapshot.tools.firstOrNull { it.name == name }
}

/**
 * What a turn's dispatch produced.
 *
 * @property results one result per dispatched call, in call order; the terminal call and the calls after it get none.
 * @property struckOut true when some tool has returned errors often enough to end the run.
 * @property terminal the terminal call that ends the run, or null.
 * @property dropped true when calls after the terminal call were never prepared.
 */
internal class TurnDispatch(
    val results: List<ToolResult>,
    val struckOut: Boolean,
    val terminal: TerminalCall?,
    val dropped: Boolean,
)

/**
 * Runs a turn's [calls] one at a time, in the order the model emitted them, never concurrently, and returns one result
 * per call in the same order. Every change goes through the session, so the gate decides. A tool that has returned
 * errors twice in the command ends the run, but only after the rest of the turn ran. The first call to a terminal tool
 * stops the walk: the app is not asked about it, and the calls after it are dropped, never prepared.
 */
internal suspend fun dispatchCalls(
    context: DispatchContext,
    calls: List<AssistantPart.ToolCall>,
): TurnDispatch {
    val endAt = calls.indexOfFirst { context.specOf(it.name)?.terminal == true }
    val active = if (endAt < 0) calls else calls.subList(0, endAt)
    val results = active.map { dispatchCall(context, it) }
    val ender = calls.getOrNull(endAt)
    val dropped = endAt in 0 until calls.lastIndex
    if (dropped) context.session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
    return TurnDispatch(results, context.struckOut(), ender?.let { TerminalCall(it.name, it.arguments) }, dropped)
}

// A cancelled caller stops the walk here, so the app is never asked to prepare a call of a turn nobody awaits.
private suspend fun dispatchCall(context: DispatchContext, call: AssistantPart.ToolCall): ToolResult {
    currentCoroutineContext().ensureActive()
    val spec = context.specOf(call.name)
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
// step is never recorded, so nothing reaches the ledger or the sink. A read tool that reports a preview or an error
// is treated as a plain read: the model still gets its content and error flag, but no action is recorded.
private suspend fun guardWrites(context: DispatchContext, spec: ToolSpec, step: ToolStep): ToolStep = when {
    spec.mutating -> step
    step is ToolStep.Mutation -> {
        context.session.recordCode(TraceCode.READ_TOOL_MUTATION_REJECTED)
        ToolStep.Finished(spec.name, FinishedKind.READ, StepResult(NOT_A_MUTATING_TOOL_CONTENT, true))
    }
    step is ToolStep.Finished && step.kind != FinishedKind.READ -> asRead(step)
    else -> step
}

// An error kind keeps its error flag even when the app left it unset, as the coordinator would have reported it.
private fun asRead(step: ToolStep.Finished): ToolStep {
    val result = step.result
    val isError = result.isError || step.kind == FinishedKind.ERROR
    val kept = StepResult(result.contentForModel, isError, result.appOutcomeToken, result.targetIds)
    return ToolStep.Finished(step.toolName, FinishedKind.READ, kept, step.context)
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
