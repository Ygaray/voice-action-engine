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
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import java.time.Clock
import java.time.ZonedDateTime

private const val PLAN_STEP_LIMIT = 8
private const val NAME_TAKEN_CODE = "plan_tool_name_taken"
private const val NO_TOOLS_CODE = "plan_no_tools"
private const val STEP_HELD_CODE = "plan_step_held"
private const val STEP_FAILED_CODE = "plan_step_failed"

/**
 * A tier that turns one spoken command into one planning call and then runs the plan's steps in order.
 *
 * The tier asks the app's [ToolSpecProvider] for the system text and tools, then sends one request that forces the
 * model to call the engine's `submit_plan` tool. The model answers with ordered steps, each naming one of the app's
 * tools and its arguments. Every step is prepared by the app's [ToolExecutor] and goes through the session as its own
 * proposal, in plan order, so the gate sees every write. The tier never writes by itself and never combines steps.
 *
 * It is for commands that need no lookup: the model plans from the transcript alone. A step whose tool the snapshot
 * never offered, or that is terminal, is not run. The tier makes one provider call when the plan runs. A snapshot that
 * already offers a tool named `submit_plan`, or that offers no non-terminal tool, fails the command before any call.
 *
 * Every action a step records carries the provider id of the planning call, at a distinct, rising position.
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
    private val hooks = OutcomeHooks(
        { StrategyOutcome.Escalate(EscalationReason.NoToolCall()) },
        { StrategyOutcome.Failed(FailureReason.Refusal()) },
        settings.onFailed,
    )

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
        answer(attempt, model.complete(request(attempt)))

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

    private suspend fun answer(attempt: Attempt, result: ModelResult): StrategyOutcome {
        val calls = (result as? ModelResult.Success)?.response?.message?.toolCalls.orEmpty()
        return decideResult(result, hooks) ?: ceilingCrossed(attempt.session) ?: verdictOutcome(attempt, calls)
    }

    // decideResult returned null only for a successful answer that holds at least one tool call, so calls is not empty.
    private suspend fun verdictOutcome(attempt: Attempt, calls: List<AssistantPart.ToolCall>): StrategyOutcome =
        when (val verdict = parsePlan(calls, attempt.snapshot, maxSteps)) {
            is PlanVerdict.Valid -> runPlan(attempt, verdict.plan, calls.first().id)
            is PlanVerdict.Rejected ->
                StrategyOutcome.Escalate(EscalationReason.MalformedExtraction(), attempt.session.carry)
        }

    // Every stop after a step ran is an escalation, never a failure, so the engine suppresses it once a step applied.
    private suspend fun runPlan(attempt: Attempt, plan: ParsedPlan, callId: String): StrategyOutcome =
        when (PlanRun(attempt.session, attempt.input, executor, attempt.snapshot, callId).run(plan)) {
            is RunStop.Done -> StrategyOutcome.Completed(null)
            is RunStop.Held -> StrategyOutcome.Escalate(EscalationReason.Other(STEP_HELD_CODE), attempt.session.carry)
            is RunStop.StepFailed ->
                StrategyOutcome.Escalate(EscalationReason.Other(STEP_FAILED_CODE), attempt.session.carry)
        }

    /** Prints the id and the step limit only. */
    override fun toString(): String = "PlanThenExecuteStrategy(id=$id, maxSteps=$maxSteps)"

    private class Attempt(
        val input: CommandInput,
        val session: CommandSession,
        val snapshot: ToolingSnapshot,
    )

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
