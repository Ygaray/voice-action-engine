package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.CurrentZoneClock
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.strategy.ceilingCrossed
import io.github.ygaray.voiceactionengine.core.strategy.ceilingReached
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.OutcomeHooks
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.decideResult
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import java.time.Clock
import java.time.ZonedDateTime

private const val PLAN_STEP_LIMIT = 8
private const val NAME_TAKEN_CODE = "plan_tool_name_taken"
private const val NO_TOOLS_CODE = "plan_no_tools"
private const val LOOKUP_CODE = "plan_needs_lookup"
private const val STEP_FAILED_REASON = "step_failed"

// The replan is not a setting: a plan tier asks the model for a plan at most twice per command, never a third time.
private const val REPLAN_LIMIT = 1

/**
 * A tier that turns one spoken command into one planning call and then runs the plan's steps in order.
 *
 * The tier asks the app's [ToolSpecProvider] for the system text and tools, then sends one request that forces the
 * model to call the engine's `submit_plan` tool. The model answers with ordered steps, each naming one of the app's
 * tools and its arguments. Every step is prepared by the app's [ToolExecutor] and goes through the session as its own
 * proposal, in plan order, so the gate sees every write. The tier never writes by itself and never combines steps.
 *
 * It is for commands that need no lookup: the model plans from the transcript alone. A step whose tool the snapshot
 * never offered, or that is terminal, is not run. The tier makes one provider call when the plan runs. When the plan
 * is rejected before any step runs, or the first step fails before anything was applied or held, the tier asks once
 * more in the same conversation and runs the second plan instead; it never asks a third time, and never asks again
 * after a change was applied or held. A snapshot that already offers a tool named `submit_plan`, or that offers no
 * non-terminal tool, fails the command before any call.
 *
 * Every action a step records carries the provider id of the planning call, at a distinct, rising position.
 *
 * The tier is gate-per-step with no resume: steps after a hold are not run. A hold with nothing committed hands the
 * command up and the engine stops the ladder there; a hold after a commit ends the command as a partial completion
 * that keeps the commits. Either way the ids of the steps that never ran are on the outcome, and committing the held
 * proposal later applies that proposal only.
 *
 * Build one with `PlanThenExecuteStrategy(id) { ... }`; the builder requires [Builder.tooling] and [Builder.executor].
 */
public class PlanThenExecuteStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    override val capabilities: StrategyCapabilities = settings.capabilities
    private val tooling: ToolSpecProvider = requireNotNull(settings.tooling) {
        "PlanThenExecuteStrategy: tooling is required"
    }
    private val executor: ToolExecutor = requireNotNull(settings.executor) {
        "PlanThenExecuteStrategy: executor is required"
    }
    private val userTurn: UserTurnRenderer = settings.userTurn
    private val clock: Clock = settings.clock
    private val reasoning: ReasoningMode = settings.reasoning
    private val maxSteps: Int = settings.maxSteps.also {
        require(it >= 1) { "PlanThenExecuteStrategy: maxSteps must be at least 1" }
    }
    private val onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome = settings.onFailed

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        ceilingReached(session) ?: withTooling(input, session)

    private suspend fun withTooling(input: CommandInput, session: CommandSession): StrategyOutcome {
        val snapshot = tooling.tooling(input)
        return unusableTooling(snapshot) ?: withModel(Attempt(input, session, snapshot))
    }

    // Both stop before any provider call: a duplicate tool name would make the request itself invalid, and a plan
    // with no tool to call could never hold a step.
    private fun unusableTooling(snapshot: ToolingSnapshot): StrategyOutcome? =
        when {
            snapshot.tools.any { it.name == planToolName() } -> unusable(NAME_TAKEN_CODE)
            stepToolNames(snapshot.tools).isEmpty() -> unusable(NO_TOOLS_CODE)
            else -> null
        }

    private fun unusable(code: String): StrategyOutcome = StrategyOutcome.Failed(FailureReason.Other(code))

    private suspend fun withModel(attempt: Attempt): StrategyOutcome {
        val model = attempt.session.model()
        return model.refusal?.let { StrategyOutcome.Failed(it) } ?: ask(attempt, model)
    }

    private suspend fun ask(attempt: Attempt, model: BoundModel): StrategyOutcome =
        PlanFlow(attempt, model, executor, onFailed, maxSteps, request(attempt)).start()

    private suspend fun request(attempt: Attempt): ModelRequest {
        val context = UserTurnContext(attempt.input, ZonedDateTime.now(clock), attempt.session.carry)
        return ModelRequest(
            attempt.snapshot.system,
            listOf(UserMessage(userTurn.render(context))),
            listOf(submitPlanSpec(attempt.snapshot.tools, maxSteps)) + attempt.snapshot.tools,
            ToolChoice.Required(planToolName()),
            attempt.session.policy.maxTokensPerTurn,
            CacheDirective(true),
            true,
            reasoning,
        )
    }

    /** Prints the id and the step limit only. */
    override fun toString(): String = "PlanThenExecuteStrategy(id=$id, maxSteps=$maxSteps)"

    /** Collects the settings of one [PlanThenExecuteStrategy]. */
    public class Builder internal constructor() {
        /** Supplies the system text and tools for each command. Required. */
        public var tooling: ToolSpecProvider? = null

        /** Prepares each planned step: the app's step for one tool call. Required. */
        public var executor: ToolExecutor? = null

        /**
         * The providers this tier may use, checked against the command's policy before the tier runs (for example an
         * offline-only command runs only a tier declared on-device only). Defaults to
         * [StrategyCapabilities.ANY_PROVIDER].
         */
        public var capabilities: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER

        /** Renders the one user message of the request. Defaults to [UserTurnRenderer.standard]. */
        public var userTurn: UserTurnRenderer = UserTurnRenderer.standard()

        /**
         * The clock that gives the date-time shown to the renderer. Defaults to the system time in the device's
         * current zone, read again for every command, so a change of zone applies from the next command.
         */
        public var clock: Clock = CurrentZoneClock

        /**
         * The reasoning request every call of this tier carries. Defaults to [ReasoningMode.OFF]: the engine adds no
         * reasoning request of its own.
         */
        public var reasoning: ReasoningMode = ReasoningMode.OFF

        /**
         * The most steps a plan may hold; a longer plan is not run. Defaults to 8. Must be at least 1.
         */
        public var maxSteps: Int = PLAN_STEP_LIMIT

        /**
         * Decides the outcome when the provider call failed for a reason other than a missing tool call or a refusal,
         * for example an HTTP 400, a transport fault, or a request the bound model refused before any call because it
         * cannot take it. It receives the reason and the transport details, or null. It is not called when no model
         * could be bound, when a token or iteration limit stops the tier, for a gate decision, or when the tier
         * throws. Defaults to failing with the same reason and details. A hook that throws ends the tier as a
         * strategy error.
         *
         * It also receives a runtime failure of an on-device provider that was bound successfully, for example a
         * `FailureReason.ProviderUnavailable` for `ProviderId.ON_DEVICE`. An on-device failure that ends the command
         * loudly by default would climb to the next tier, carrying the same transcript, if the hook answers every
         * reason with an escalation. Branch on the reason before escalating.
         */
        public var onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome =
            { reason, details -> StrategyOutcome.Failed(reason, details) }
    }

    /** Ways to create a tier. */
    public companion object {
        /**
         * Builds a plan-then-execute tier with [id] from [block].
         *
         * @throws IllegalArgumentException naming the setting when [Builder.tooling] or [Builder.executor] is missing,
         * or when [Builder.maxSteps] is below 1.
         */
        public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): PlanThenExecuteStrategy =
            PlanThenExecuteStrategy(id, Builder().apply(block))
    }
}

private class Attempt(
    val input: CommandInput,
    val session: CommandSession,
    val snapshot: ToolingSnapshot,
)

/**
 * One command's conversation with the model: the first request, the answer's verdict, and at most one replan.
 *
 * A replan is allowed once, and only while nothing was applied or held and the token ceiling is not reached. It
 * continues the same conversation (see [replanRequest]) and its answer goes through the same checks as the first, with
 * a fresh [PlanRun] so the binding map starts empty: the second plan replaces the first, whole.
 */
private class PlanFlow(
    private val attempt: Attempt,
    private val model: BoundModel,
    private val executor: ToolExecutor,
    onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome,
    private val maxSteps: Int,
    private val first: ModelRequest,
) {
    private val session: CommandSession = attempt.session

    // Built per command so a prose answer to the plan call escalates with the incoming carry, like every other plan
    // escalation: the hook cannot be built at strategy construction, where no session exists.
    private val hooks = OutcomeHooks(
        { StrategyOutcome.Escalate(EscalationReason.NoToolCall(), session.carry) },
        { StrategyOutcome.Failed(FailureReason.Refusal()) },
        onFailed,
    )
    private var replans = 0

    suspend fun start(): StrategyOutcome = handle(model.complete(first))

    private suspend fun handle(result: ModelResult): StrategyOutcome {
        val message = (result as? ModelResult.Success)?.response?.message
        val early = truncated(result) ?: decideResult(result, hooks) ?: ceilingCrossed(session)
        // decideResult returned null only for a successful answer that holds at least one tool call.
        return early ?: message?.let { verdictOutcome(it) } ?: malformed()
    }

    // A plan cut off by the token limit is never read, whatever it holds: a partial plan must not run, and asking again
    // at the same size would truncate again. Another tier may take the command, so it escalates, unlike SingleShot,
    // which fails a truncated answer. Pause and context-window stops stay on decideResult's shared failure path.
    private fun truncated(result: ModelResult): StrategyOutcome? =
        if (result is ModelResult.Success && result.response.stopReason == StopReason.MAX_TOKENS) malformed() else null

    private suspend fun verdictOutcome(message: AssistantMessage): StrategyOutcome =
        when (val verdict = parsePlan(message.toolCalls, attempt.snapshot, maxSteps)) {
            is PlanVerdict.Valid -> runPlan(message, verdict.plan)
            is PlanVerdict.Rejected -> rejected(message, verdict)
            is PlanVerdict.NeedsLookup -> StrategyOutcome.Escalate(EscalationReason.Other(LOOKUP_CODE), session.carry)
        }

    // A plan that failed whole-plan validation ran nothing, so another tier may take the command with the same carry.
    private suspend fun rejected(message: AssistantMessage, verdict: PlanVerdict.Rejected): StrategyOutcome {
        session.recordCode(TraceCode.PLAN_REJECTED)
        if (verdict.unknownTool) session.recordCode(TraceCode.UNKNOWN_TOOL)
        return if (canReplan(false)) replan(message, verdict.code, verdict.stepIndex) else malformed()
    }

    // Only the first call is ever acted on. Dropped extras make a Completed outcome partial; an Escalate is unchanged.
    private suspend fun runPlan(message: AssistantMessage, plan: ParsedPlan): StrategyOutcome {
        val calls = message.toolCalls
        val dropped = calls.size > 1
        if (dropped) session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
        val run = PlanRun(session, attempt.input, executor, attempt.snapshot, calls.first().id)
        val stop = run.run(plan)
        if (stop is RunStop.StepFailed && canReplan(run.worked)) return replan(message, STEP_FAILED_REASON, stop.index)
        val remaining = plan.steps.drop(run.preparedSteps).map { it.id }
        val outcome = outcomeOf(stop, session.carry, run, remaining)
        return if (dropped && outcome is StrategyOutcome.Completed) {
            StrategyOutcome.Completed(outcome.reply, outcome.terminalCall, true, outcome.remainingStepIds)
        } else {
            outcome
        }
    }

    // The one predicate: a replan is left, nothing was applied or held, and the run is still under its token ceiling.
    private fun canReplan(worked: Boolean): Boolean =
        replans < REPLAN_LIMIT && !worked && ceilingReached(session) == null

    private suspend fun replan(message: AssistantMessage, reason: String, stepIndex: Int?): StrategyOutcome {
        replans++
        val request = replanRequest(first, message, rejectionDigest(reason, stepIndex)) ?: return malformed()
        session.recordCode(TraceCode.PLAN_REPLANNED)
        return handle(model.complete(request))
    }

    private fun malformed(): StrategyOutcome =
        StrategyOutcome.Escalate(EscalationReason.MalformedExtraction(), session.carry)
}
