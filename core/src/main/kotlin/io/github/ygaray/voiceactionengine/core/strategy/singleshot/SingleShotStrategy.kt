package io.github.ygaray.voiceactionengine.core.strategy.singleshot

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.CurrentZoneClock
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.strategy.ceilingCrossed
import io.github.ygaray.voiceactionengine.core.strategy.ceilingReached
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import java.time.Clock
import java.time.ZonedDateTime

private const val TOOL_MISSING_CODE = "single_shot_tool_missing"

/**
 * A tier that turns one spoken command into one provider call and one local resolution.
 *
 * The tier asks the app's [ToolSpecProvider] for the system text and tools, sends one request that forces the model to
 * call the snapshot's single-shot tool, hands the model's first tool call to the app's [OutcomeResolver], and submits
 * whatever steps the resolver prepared. It never writes by itself: every change goes through the session, so the gate
 * decides. Finished steps are submitted first, in list order; all mutations are then combined, in list order, into one
 * step, so the gate sees one proposal. The tier makes at most one provider call per command and never retries.
 *
 * Only the model's first tool call is acted on. When the answer holds more, the extra calls are dropped, never run,
 * and not silently: the trace records `extra_tool_calls_dropped` and a completed outcome is marked partial
 * ([StrategyOutcome.Completed.partial]), because the user may have asked for more than was done. A run that ends
 * failed or handed up keeps that outcome.
 *
 * The tier reads its limits from the session policy. It sends the policy's per-turn token limit with the request,
 * refuses before calling when the run has already reached the token ceiling, fails before resolving or
 * writing anything when its one call took the run past the ceiling, and makes exactly one model call, so the iteration
 * limit is never reached.
 *
 * Build one with `SingleShotStrategy(id) { ... }`; the builder requires [Builder.tooling] and [Builder.resolver].
 */
public class SingleShotStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    override val capabilities: StrategyCapabilities = settings.capabilities
    private val tooling: ToolSpecProvider = requireNotNull(settings.tooling) {
        "SingleShotStrategy: tooling is required"
    }
    private val resolver: OutcomeResolver = requireNotNull(settings.resolver) {
        "SingleShotStrategy: resolver is required"
    }
    private val userTurn: UserTurnRenderer = settings.userTurn
    private val clock: Clock = settings.clock
    private val forceTool: Boolean = settings.forceTool
    private val reasoning: ReasoningMode = settings.reasoning
    private val hooks = OutcomeHooks(settings.onNoToolCall, settings.onRefusal, settings.onFailed)

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        ceilingReached(session) ?: withTooling(input, session)

    private suspend fun withTooling(input: CommandInput, session: CommandSession): StrategyOutcome {
        val snapshot = tooling.tooling(input)
        // The tool's name is needed only to force it; with forceTool false the model chooses among the offered tools.
        val choice = if (forceTool) {
            snapshot.singleShotTool?.let { ToolChoice.Required(it) }
                ?: return StrategyOutcome.Failed(FailureReason.Other(TOOL_MISSING_CODE))
        } else {
            ToolChoice.Auto()
        }
        return withModel(Attempt(input, session, snapshot, choice))
    }

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
            attempt.snapshot.tools,
            attempt.choice,
            attempt.session.policy.maxTokensPerTurn,
            CacheDirective(true),
            true,
            reasoning,
        )
    }

    private suspend fun answer(attempt: Attempt, result: ModelResult): StrategyOutcome {
        val calls = (result as? ModelResult.Success)?.response?.message?.toolCalls.orEmpty()
        return decideResult(result, hooks) ?: route(attempt, calls)
    }

    // decideResult returned null only for a successful answer that holds at least one tool call, so calls is not empty.
    // Only the first call is ever acted on; a call to a tool the snapshot never offered is not trusted. Dropped extras
    // make a Completed outcome partial (whether the first call committed, was held or was rejected); Failed and
    // Escalate outcomes are left as they are.
    private suspend fun route(attempt: Attempt, calls: List<AssistantPart.ToolCall>): StrategyOutcome {
        val call = calls.first()
        val dropped = calls.size > 1
        if (dropped) attempt.session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
        val outcome = ceilingCrossed(attempt.session) ?: dispatch(attempt, call)
        return if (dropped && outcome is StrategyOutcome.Completed) {
            StrategyOutcome.Completed(outcome.reply, outcome.terminalCall, true)
        } else {
            outcome
        }
    }

    private suspend fun dispatch(attempt: Attempt, call: AssistantPart.ToolCall): StrategyOutcome {
        val tool = attempt.snapshot.tools.firstOrNull { it.name == call.name }
        return when {
            tool == null -> StrategyOutcome.Escalate(EscalationReason.MalformedExtraction())
            tool.terminal -> StrategyOutcome.Completed(null, TerminalCall(call.name, call.arguments))
            else -> resolve(attempt, call)
        }
    }

    private suspend fun resolve(attempt: Attempt, call: AssistantPart.ToolCall): StrategyOutcome {
        val resolution = resolver.resolve(Extraction(call.name, call.arguments), attempt.input)
        return resolutionOutcome(resolution) { submitAll(attempt.session, it) }
    }

    // Finished steps first, in list order; then every mutation, in order, as one step so the gate decides once.
    // The resolver prepared the reply before the gate ran, so it cannot know whether the apply succeeded: when an
    // apply reported an error the reply is withheld and the caller reads the outcome's executed list instead. A held
    // proposal is a normal pending state that the outcome carries, so the reply is kept for it.
    private suspend fun submitAll(session: CommandSession, steps: Resolution.Steps): StrategyOutcome {
        steps.steps.filterIsInstance<ToolStep.Finished>().forEach { session.submit(it) }
        val mutations = steps.steps.filterIsInstance<ToolStep.Mutation>().flatMap { it.mutations }
        val applied = if (mutations.isEmpty()) null else session.submit(ToolStep.Mutation(mutations))
        return StrategyOutcome.Completed(if (applied?.isError == true) null else steps.reply)
    }

    /** Prints the id and the force-tool flag only. */
    override fun toString(): String = "SingleShotStrategy(id=$id, forceTool=$forceTool)"

    private class Attempt(
        val input: CommandInput,
        val session: CommandSession,
        val snapshot: ToolingSnapshot,
        val choice: ToolChoice,
    )

    /** Collects the settings of one [SingleShotStrategy]. */
    public class Builder internal constructor() {
        /** Supplies the system text and tools for each command. Required. */
        public var tooling: ToolSpecProvider? = null

        /** Turns what the model extracted into prepared steps or a verdict. Required. */
        public var resolver: OutcomeResolver? = null

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
         * True (the default) forces the model to call the snapshot's single-shot tool; a snapshot with no single-shot
         * tool then fails the command before any provider call. False lets the model choose among the offered tools,
         * for example a terminal clarification tool, and does not need the snapshot to name a single-shot tool.
         */
        public var forceTool: Boolean = true

        /**
         * The reasoning request every call of this tier carries. Defaults to [ReasoningMode.OFF]: the engine adds no
         * reasoning request of its own.
         */
        public var reasoning: ReasoningMode = ReasoningMode.OFF

        /**
         * Decides the outcome when the model answered without a tool call. It receives the response, or null when
         * the provider reported the condition as a failure. Defaults to escalating with `NoToolCall`.
         */
        public var onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome =
            { StrategyOutcome.Escalate(EscalationReason.NoToolCall()) }

        /**
         * Decides the outcome when the model refused. It receives the response, or null when the provider reported the
         * refusal as a failure. Defaults to failing with `Refusal`.
         */
        public var onRefusal: suspend (ModelResponse?) -> StrategyOutcome =
            { StrategyOutcome.Failed(FailureReason.Refusal()) }

        /**
         * Decides the outcome when the provider call failed for a reason other than a missing tool call or a refusal,
         * for example an HTTP 400, a transport fault, or a request the bound model refused before any call because it
         * cannot take it. It receives the reason and the transport details, or null. It is not called when no model
         * could be bound, when a token or iteration limit stops the tier, for a gate decision, or when the tier
         * throws. Defaults to failing with the same reason and details. A hook that throws ends the tier as a
         * strategy error.
         */
        public var onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome =
            { reason, details -> StrategyOutcome.Failed(reason, details) }
    }

    /** Ways to create a tier. */
    public companion object {
        /**
         * Builds a single-shot tier with [id] from [block].
         *
         * @throws IllegalArgumentException naming the setting when [Builder.tooling] or [Builder.resolver] is missing.
         */
        public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): SingleShotStrategy =
            SingleShotStrategy(id, Builder().apply(block))
    }
}
