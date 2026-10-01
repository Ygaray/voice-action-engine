package io.github.ygaray.voiceactionengine.core.strategy.agentic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
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
import io.github.ygaray.voiceactionengine.core.strategy.iterationBudgetFailure
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import java.time.Clock
import java.time.ZonedDateTime

private const val EXHAUSTED_CODE = "agentic_loop_exhausted"

/**
 * A tier that turns one spoken command into a bounded conversation with the model over the app's own tools.
 *
 * The tier asks the app's [ToolSpecProvider] once per command for the system text and tools, renders the user turn
 * once, and asks the model with automatic tool choice and parallel calls allowed. Each tool call the model makes is
 * run one at a time, in the order the model emitted them, through the app's [ToolExecutor] and then the session, so the
 * gate decides every change. The results of a turn go back to the model together, in call order, and the conversation
 * only ever grows. The run ends on the first answer that holds no tool calls, whose first text part is the reply, or on
 * a typed failure. The system text and the tools are the same on every request, which keeps them a cacheable prefix.
 *
 * The tier reads its limits from the session policy. Before every model call it refuses when the run has already
 * reached the token ceiling. After a tool turn it fails, before running any call, when the run is past the ceiling,
 * and it fails the same way before running the calls of the last permitted turn. When the token ceiling and the
 * iteration limit trip on the same turn, the failure is the token ceiling's; the ceiling is checked first.
 *
 * A tool that returns an error twice in one command ends the run as a tool failure, after the rest of that turn ran.
 * The first call to a terminal tool ends the run: the calls before it in the turn ran, the calls after it are dropped
 * and make the completion partial, and the call is delivered as the outcome's terminal call. A turn whose first call
 * is terminal is not stopped by the iteration limit.
 *
 * The tier returns only completed or failed outcomes, never an escalation, and never retries. It reports no turn
 * itself: the engine records every turn made through the session's model.
 *
 * Build one with `AgenticLoopStrategy(id) { ... }`; the builder requires [Builder.tooling] and [Builder.executor].
 */
public class AgenticLoopStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    override val capabilities: StrategyCapabilities = settings.capabilities
    private val tooling: ToolSpecProvider = requireNotNull(settings.tooling) {
        "AgenticLoopStrategy: tooling is required"
    }
    private val executor: ToolExecutor = requireNotNull(settings.executor) {
        "AgenticLoopStrategy: executor is required"
    }
    private val userTurn: UserTurnRenderer = settings.userTurn
    private val clock: Clock = settings.clock

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        ceilingReached(session) ?: start(input, session)

    private suspend fun start(input: CommandInput, session: CommandSession): StrategyOutcome {
        val snapshot = tooling.tooling(input)
        val model = session.model()
        return model.refusal?.let { StrategyOutcome.Failed(it) } ?: run(input, session, snapshot, model)
    }

    private suspend fun run(
        input: CommandInput,
        session: CommandSession,
        snapshot: ToolingSnapshot,
        model: BoundModel,
    ): StrategyOutcome {
        val context = UserTurnContext(input, ZonedDateTime.now(clock), session.carry)
        val first = UserMessage(userTurn.render(context))
        return AgenticRun(DispatchContext(session, input, snapshot, executor), model, first).run()
    }

    /** Prints the id only. */
    override fun toString(): String = "AgenticLoopStrategy(id=$id)"

    /** Collects the settings of one [AgenticLoopStrategy]. */
    public class Builder internal constructor() {
        /** Supplies the system text and tools for each command. Required. */
        public var tooling: ToolSpecProvider? = null

        /** Prepares each tool call the model makes. Required. */
        public var executor: ToolExecutor? = null

        /**
         * The providers this tier may use, checked against the command's policy before the tier runs (for example an
         * offline-only command runs only a tier declared on-device only). Defaults to
         * [StrategyCapabilities.ANY_PROVIDER].
         */
        public var capabilities: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER

        /**
         * Renders the one user message of the conversation. Defaults to [UserTurnRenderer.standard]. An app that needs
         * its own framing passes its own renderer; the engine adds nothing around the text it returns.
         */
        public var userTurn: UserTurnRenderer = UserTurnRenderer.standard()

        /**
         * The clock that gives the date-time shown to the renderer. Defaults to the system time in the device's
         * current zone, read again for every command, so a change of zone applies from the next command.
         */
        public var clock: Clock = CurrentZoneClock
    }

    /** Ways to create a tier. */
    public companion object {
        /**
         * Builds an agentic tier with [id] from [block].
         *
         * @throws IllegalArgumentException naming the setting when [Builder.tooling] or [Builder.executor] is missing.
         */
        public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): AgenticLoopStrategy =
            AgenticLoopStrategy(id, Builder().apply(block))
    }
}

/**
 * One command's conversation. The history starts with the rendered user message and only grows: each tool turn adds
 * the model's message exactly as received, so its native replay stays, and then one message holding the results.
 */
internal class AgenticRun(
    private val context: DispatchContext,
    private val model: BoundModel,
    first: UserMessage,
) {
    private val history = mutableListOf<Message>(first)

    /** Runs up to the policy's iteration limit; the last permitted iteration always ends the run. */
    suspend fun run(): StrategyOutcome {
        for (iteration in 1..context.session.policy.maxIterations) {
            val outcome = iterate(iteration)
            if (outcome != null) return outcome
        }
        return StrategyOutcome.Failed(FailureReason.Other(EXHAUSTED_CODE))
    }

    // Null means the turn was a tool turn that was dispatched and the conversation goes on.
    private suspend fun iterate(iteration: Int): StrategyOutcome? =
        ceilingReached(context.session) ?: ask(iteration)

    private suspend fun ask(iteration: Int): StrategyOutcome? {
        val result = model.complete(request())
        val response = (result as? ModelResult.Success)?.response
        return decideTurn(result) ?: response?.let { toolTurn(iteration, it) }
    }

    private fun request(): ModelRequest =
        ModelRequest(
            context.snapshot.system,
            history.toList(),
            context.snapshot.tools,
            ToolChoice.Auto(),
            context.session.policy.maxTokensPerTurn,
            CacheDirective(true),
            false,
        )

    // The token ceiling is checked first, so a turn that trips both limits reports the ceiling.
    private suspend fun toolTurn(iteration: Int, response: ModelResponse): StrategyOutcome? =
        ceilingCrossed(context.session) ?: lastTurnGuard(iteration, response) ?: dispatchTurn(response)

    // The last permitted turn runs no tool, except that a turn whose first call is terminal dispatches nothing at all
    // and simply ends the run.
    private fun lastTurnGuard(iteration: Int, response: ModelResponse): StrategyOutcome? {
        val last = iteration >= context.session.policy.maxIterations
        val endsAtOnce = context.specOf(response.message.toolCalls.first().name)?.terminal == true
        return if (last && !endsAtOnce) iterationBudgetFailure() else null
    }

    // A tool that struck out ends the run after the whole turn ran, and beats a terminal call of the same turn. A
    // terminal call ends the run with no results sent and no further request. The pipeline attaches every executed
    // action to either outcome, so nothing committed or held in the turn is hidden.
    private suspend fun dispatchTurn(response: ModelResponse): StrategyOutcome? {
        val turn = dispatchCalls(context, response.message.toolCalls)
        val terminal = turn.terminal
        if (terminal == null) {
            history.add(response.message)
            history.add(ToolResultsMessage(turn.results))
        }
        return when {
            turn.struckOut -> StrategyOutcome.Failed(FailureReason.ToolFailure())
            terminal != null -> StrategyOutcome.Completed(null, terminal, turn.dropped)
            else -> null
        }
    }
}
