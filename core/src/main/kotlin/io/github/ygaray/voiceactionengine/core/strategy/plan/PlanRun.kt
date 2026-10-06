package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.prepareGuarded
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Why a plan run stopped. */
internal sealed class RunStop {
    /** Every step ran and committed. */
    object Done : RunStop()

    /** A step was held by the gate; [last] is true when it was the plan's last step. */
    class Held(val last: Boolean) : RunStop()

    /** The step at [index] did not commit and was not held. */
    class StepFailed(val index: Int) : RunStop()
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

    /** Runs [plan]'s steps and says why the run stopped. */
    suspend fun run(plan: ParsedPlan): RunStop {
        for (index in plan.steps.indices) {
            val stop = runStep(plan, index)
            if (stop != null) return stop
        }
        return RunStop.Done
    }

    // A cancelled caller stops the walk here, so the app is never asked to prepare a step of a plan nobody awaits.
    private suspend fun runStep(plan: ParsedPlan, index: Int): RunStop? {
        currentCoroutineContext().ensureActive()
        val step = plan.steps[index]
        val spec = snapshot.tools.first { it.name == step.tool }
        val prepared = prepareGuarded(session, spec, executor, input, Extraction(step.tool, step.arguments, callId))
        val dispatch = session.submit(prepared, callId)
        worked = worked || dispatch.held || dispatch.actions.any { it.applied }
        return when {
            dispatch.held -> RunStop.Held(index == plan.steps.lastIndex)
            dispatch.actions.isNotEmpty() && dispatch.actions.all { it.kind == ActionKind.COMMITTED } -> null
            else -> RunStop.StepFailed(index)
        }
    }
}
