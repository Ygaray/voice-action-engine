package io.github.ygaray.voiceactionengine.core.strategy.singleshot

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
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
 * decides. The tier makes at most one provider call per command and never retries.
 *
 * Build one with `SingleShotStrategy(id) { ... }`; the builder requires [Builder.tooling] and [Builder.resolver].
 */
public class SingleShotStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    private val tooling: ToolSpecProvider = requireNotNull(settings.tooling) {
        "SingleShotStrategy: tooling is required"
    }
    private val resolver: OutcomeResolver = requireNotNull(settings.resolver) {
        "SingleShotStrategy: resolver is required"
    }
    private val userTurn: UserTurnRenderer = settings.userTurn
    private val clock: Clock = settings.clock
    private val forceTool: Boolean = settings.forceTool
    private val hooks = OutcomeHooks(settings.onNoToolCall, settings.onRefusal)

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome {
        val snapshot = tooling.tooling(input)
        val tool = snapshot.singleShotTool ?: return StrategyOutcome.Failed(FailureReason.Other(TOOL_MISSING_CODE))
        return withModel(Attempt(input, session, snapshot), tool)
    }

    private suspend fun withModel(attempt: Attempt, tool: String): StrategyOutcome {
        val model = attempt.session.model()
        return model.refusal?.let { StrategyOutcome.Failed(it) } ?: ask(attempt, model, tool)
    }

    private suspend fun ask(attempt: Attempt, model: BoundModel, tool: String): StrategyOutcome =
        answer(attempt, model.complete(request(attempt, tool)))

    private suspend fun request(attempt: Attempt, tool: String): ModelRequest {
        val context = UserTurnContext(attempt.input, ZonedDateTime.now(clock), attempt.session.carry)
        return ModelRequest(
            attempt.snapshot.system,
            listOf(UserMessage(userTurn.render(context))),
            attempt.snapshot.tools,
            if (forceTool) ToolChoice.Required(tool) else ToolChoice.Auto(),
            attempt.session.policy.maxTokensPerTurn,
            CacheDirective(true),
            true,
        )
    }

    private suspend fun answer(attempt: Attempt, result: ModelResult): StrategyOutcome {
        val calls = (result as? ModelResult.Success)?.response?.message?.toolCalls.orEmpty()
        return decideResult(result, hooks) ?: route(attempt, calls)
    }

    // Only the first call is ever acted on; a call to a tool the snapshot never offered is not trusted.
    private suspend fun route(attempt: Attempt, calls: List<AssistantPart.ToolCall>): StrategyOutcome {
        val call = calls.firstOrNull() ?: return StrategyOutcome.Failed(FailureReason.MalformedResponse())
        if (calls.size > 1) attempt.session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
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
    private suspend fun submitAll(session: CommandSession, steps: Resolution.Steps): StrategyOutcome {
        steps.steps.filterIsInstance<ToolStep.Finished>().forEach { session.submit(it) }
        val mutations = steps.steps.filterIsInstance<ToolStep.Mutation>().flatMap { it.mutations }
        if (mutations.isNotEmpty()) session.submit(ToolStep.Mutation(mutations))
        return StrategyOutcome.Completed(steps.reply)
    }

    /** Prints the id and the force-tool flag only. */
    override fun toString(): String = "SingleShotStrategy(id=$id, forceTool=$forceTool)"

    private class Attempt(val input: CommandInput, val session: CommandSession, val snapshot: ToolingSnapshot)

    /** Collects the settings of one [SingleShotStrategy]. */
    public class Builder internal constructor() {
        /** Supplies the system text and tools for each command. Required. */
        public var tooling: ToolSpecProvider? = null

        /** Turns what the model extracted into prepared steps or a verdict. Required. */
        public var resolver: OutcomeResolver? = null

        /** Renders the one user message of the request. Defaults to [UserTurnRenderer.standard]. */
        public var userTurn: UserTurnRenderer = UserTurnRenderer.standard()

        /** The clock that gives the date-time shown to the renderer. Defaults to the system clock and zone. */
        public var clock: Clock = Clock.systemDefaultZone()

        /**
         * True (the default) forces the model to call the snapshot's single-shot tool. False lets the model choose
         * among the offered tools, for example a terminal clarification tool.
         */
        public var forceTool: Boolean = true

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
