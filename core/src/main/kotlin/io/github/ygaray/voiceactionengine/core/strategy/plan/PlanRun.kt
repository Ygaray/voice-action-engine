package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.prepareGuarded
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject

private const val STEP_HELD_CODE = "plan_step_held"
private const val STEP_FAILED_CODE = "plan_step_failed"
private const val BINDING_UNRESOLVED_CODE = "plan_binding_unresolved"

/** Why a plan run stopped. */
internal sealed class RunStop {
    /** Every step ran and committed. */
    object Done : RunStop()

    /** A step was held by the gate; no later step is prepared. */
    object Held : RunStop()

    /** The step at [index] did not commit and was not held. */
    class StepFailed(val index: Int) : RunStop()

    /** A step referred to an earlier step's result that did not provide the key; that step was not prepared. */
    object BindingUnresolved : RunStop()
}

/**
 * The outcome a stop gives the tier, with [remaining] the ids of the planned steps the run never handed to the
 * executor. Every stop after a step ran is an escalation, never a failure, so the engine suppresses it once a step
 * applied or was held; [carry] is the incoming carry, forwarded unchanged. The one exception is a hold after at least
 * one committed step: a later tier would run the committed steps again, so that stop is a terminal partial completion
 * that keeps the commits, and the held proposal stays in the outcome for confirmation. The choice reads the run's
 * committed steps, never the held step's position in the plan.
 */
internal fun outcomeOf(stop: RunStop, carry: Any?, run: PlanRun, remaining: List<String>): StrategyOutcome =
    when (stop) {
        is RunStop.Done -> StrategyOutcome.Completed(null)
        is RunStop.Held ->
            if (run.committedSteps > 0) {
                StrategyOutcome.Completed(null, null, true, remaining)
            } else {
                StrategyOutcome.Escalate(EscalationReason.Other(STEP_HELD_CODE), carry, remaining)
            }
        is RunStop.StepFailed -> StrategyOutcome.Escalate(EscalationReason.Other(STEP_FAILED_CODE), carry, remaining)
        is RunStop.BindingUnresolved ->
            StrategyOutcome.Escalate(EscalationReason.Other(BINDING_UNRESOLVED_CODE), carry, remaining)
    }

/**
 * Runs a plan's steps strictly one after another, in plan order. Each step is prepared by the app's executor and
 * submitted alone through the session, so the gate sees every write as its own proposal; steps are never combined.
 * Every action a step records carries [callId], the provider's id for the planning call.
 */
internal class PlanRun(
    private val session: CommandSession,
    private val input: CommandInput,
    private val executor: ToolExecutor,
    private val snapshot: ToolingSnapshot,
    private val callId: String,
) {
    /** True once any step was applied or held, so a later stop must not let another tier run the command again. */
    var worked: Boolean = false
        private set

    /** How many steps were handed to the executor; a step stopped by an unresolved reference is never counted. */
    var preparedSteps: Int = 0
        private set

    /** How many steps committed: their dispatch recorded at least one action and every action was committed. */
    var committedSteps: Int = 0
        private set

    /** Runs [plan]'s steps and says why the run stopped. */
    suspend fun run(plan: ParsedPlan): RunStop {
        for (index in plan.steps.indices) {
            val stop = runStep(plan, index)
            if (stop != null) return stop
        }
        return RunStop.Done
    }

    // What each committed step returned, by plan step id. Only a step whose dispatch recorded committed actions only is
    // written, so a held, previewed or errored step is never bindable.
    private val results = mutableMapOf<String, Map<String, String>>()

    // A cancelled caller stops the walk here, so the app is never asked to prepare a step of a plan nobody awaits.
    private suspend fun runStep(plan: ParsedPlan, index: Int): RunStop? {
        currentCoroutineContext().ensureActive()
        val step = plan.steps[index]
        val bound = bindArguments(step.arguments, results)
        return if (bound == null) unresolved() else dispatch(index, step, bound)
    }

    private suspend fun unresolved(): RunStop {
        session.recordCode(TraceCode.PLAN_BINDING_UNRESOLVED)
        return RunStop.BindingUnresolved
    }

    private suspend fun dispatch(index: Int, step: PlanStep, arguments: JsonObject): RunStop? {
        val spec = snapshot.tools.first { it.name == step.tool }
        preparedSteps++
        val prepared = prepareGuarded(session, spec, executor, input, Extraction(step.tool, arguments, callId))
        val dispatch = session.submit(prepared, callId)
        worked = worked || dispatch.held || dispatch.actions.any { it.applied }
        val committed = dispatch.actions.isNotEmpty() && dispatch.actions.all { it.kind == ActionKind.COMMITTED }
        if (committed) {
            committedSteps++
            results[step.id] = mergeTargets(dispatch.actions)
        }
        return when {
            dispatch.held -> RunStop.Held
            committed -> null
            else -> RunStop.StepFailed(index)
        }
    }
}
