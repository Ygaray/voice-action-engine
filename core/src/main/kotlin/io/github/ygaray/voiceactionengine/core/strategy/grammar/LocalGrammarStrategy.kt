package io.github.ygaray.voiceactionengine.core.strategy.grammar

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.resolutionOutcome
import io.github.ygaray.voiceactionengine.core.strategy.submitSteps
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

/**
 * A tier that resolves a spoken command from phrasings the app declared, with no provider call.
 *
 * The tier asks its [GrammarPack] whether the transcript is one of the declared phrasings. On a match it hands the
 * tool name and arguments to the app's [OutcomeResolver], exactly as a single-shot tier does with a model's tool call
 * (the extraction's `callId` is null and its `matchedLanguage` says which language matched), and submits whatever steps
 * the resolver prepared. It never writes by itself: every change goes through the session, so the gate decides, and
 * the commit sink sees the action with a null provider call id. When nothing matches, or the resolver answers
 * [Resolution.NoMatch], the tier ends with no match and the next tier
 * starts fresh with the original transcript.
 *
 * The tier uses no provider and no network, so it is eligible for an offline-only command and under any provider
 * policy: [capabilities] is always [StrategyCapabilities.NO_PROVIDER].
 *
 * A match on an intent declared terminal ends the tier handled with a `TerminalCall` and calls neither the resolver nor
 * the gate. A pack whose intents are all terminal needs no resolver.
 *
 * Build one with `LocalGrammarStrategy(id) { ... }`; the builder requires [Builder.pack], and [Builder.resolver] unless
 * every intent of the pack is terminal.
 */
public class LocalGrammarStrategy internal constructor(
    override val id: StrategyId,
    settings: Builder,
) : CommandStrategy {
    override val capabilities: StrategyCapabilities = StrategyCapabilities.NO_PROVIDER
    private val pack: GrammarPack = requireNotNull(settings.pack) { "LocalGrammarStrategy: pack is required" }
    private val resolver: OutcomeResolver? = settings.resolver

    init {
        require(resolver != null || !pack.hasNonTerminalIntent) { "LocalGrammarStrategy: resolver is required" }
    }

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
        when (val result = pack.matchDetailed(input.transcript, input.language)) {
            is GrammarResult.Matched -> resolve(result.match, input, session)
            is GrammarResult.Rejected -> {
                result.code?.let { session.recordCode(it) }
                StrategyOutcome.NoMatch()
            }
        }

    private suspend fun resolve(match: GrammarMatch, input: CommandInput, session: CommandSession): StrategyOutcome =
        when {
            // A terminal intent writes nothing: the app gets the tool name and slots as the outcome, with no resolver,
            // no gate and no recorded action.
            match.terminal -> StrategyOutcome.Completed(null, TerminalCall(match.toolName, match.arguments))
            else -> resolver?.let { resolveWith(it, match, input, session) } ?: StrategyOutcome.NoMatch()
        }

    private suspend fun resolveWith(
        resolver: OutcomeResolver,
        match: GrammarMatch,
        input: CommandInput,
        session: CommandSession,
    ): StrategyOutcome {
        val extraction = Extraction(match.toolName, match.arguments, null, match.matchedLanguage)
        val resolution = resolver.resolve(extraction, input)
        if (resolution is Resolution.NoMatch) session.recordCode(TraceCode.GRAMMAR_RESOLVER_REJECTED)
        return resolutionOutcome(resolution) { submitSteps(session, it, null) }
    }

    /** Prints the id only. */
    override fun toString(): String = "LocalGrammarStrategy(id=$id)"

    /** Collects the settings of one [LocalGrammarStrategy]. */
    public class Builder internal constructor() {
        /** The phrasings this tier matches. Required. */
        public var pack: GrammarPack? = null

        /**
         * Turns a match into prepared steps or a verdict, as for a single-shot tier. Required unless every intent of
         * the pack is terminal.
         */
        public var resolver: OutcomeResolver? = null
    }

    /** Ways to create a tier. */
    public companion object {
        /**
         * Builds a grammar tier with [id] from [block].
         *
         * @throws IllegalArgumentException naming the setting when [Builder.pack] is missing, or [Builder.resolver] is
         * missing and some intent of the pack is not terminal.
         */
        public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): LocalGrammarStrategy =
            LocalGrammarStrategy(id, Builder().apply(block))
    }
}
