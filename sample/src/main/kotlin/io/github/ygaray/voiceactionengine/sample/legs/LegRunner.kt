package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.ClarificationOption
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.SampleEngine
import io.github.ygaray.voiceactionengine.sample.TRIGGER_AUTORUN
import io.github.ygaray.voiceactionengine.sample.evidence.CostEstimate
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceListener
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.net.AttemptTap
import io.github.ygaray.voiceactionengine.sample.net.LegContext
import io.github.ygaray.voiceactionengine.sample.net.OkHttpRuntime
import io.github.ygaray.voiceactionengine.sample.tools.CannedToolExecutor
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import io.github.ygaray.voiceactionengine.sample.verdict.CacheVerdict
import io.github.ygaray.voiceactionengine.sample.verdict.MultiTurnVerdict
import io.github.ygaray.voiceactionengine.sample.verdict.OutcomeSummary
import io.github.ygaray.voiceactionengine.sample.verdict.SmokeVerdict
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.sync.Mutex

private const val HTTP_OK = 200
private const val REASON_MODEL_UNSUPPORTED = "model_unsupported"
private const val REASON_NO_PROVIDER_CALL = "no_provider_call"

// A tier must declare the providers it may use; the demos declare the demo provider and nothing else.
private val DEMO_ONLY = StrategyCapabilities(setOf(DEMO_PROVIDER))

/**
 * What one leg produced.
 *
 * @property outcome the engine's outcome, or null when the leg was refused and nothing ran.
 * @property summary [outcome] reduced to codes and counts, or null when nothing ran.
 */
internal class LegResult(
    val leg: LegId,
    val verdict: Verdict,
    val outcome: CommandOutcome?,
    val summary: OutcomeSummary?,
) {
    override fun toString(): String = "LegResult(leg=${leg.wire}, verdict=$verdict)"
}

/**
 * A resolver that never reads a value: it records the tool the model called and the NAMES of its arguments, then prepares
 * the canned step for that tool. The names are all a smoke verdict needs, and no argument value can reach evidence.
 */
internal class SmokeResolver(tools: List<ToolSpec>) : OutcomeResolver {
    private val executor = CannedToolExecutor(tools)

    /** The tool of the call that was resolved, or null when none was. */
    @Volatile
    var toolName: String? = null
        private set

    /** The argument key names of that call. */
    @Volatile
    var argKeys: Set<String> = emptySet()
        private set

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        toolName = extraction.toolName
        argKeys = extraction.arguments.keys.toSet()
        return Resolution.Steps(listOf(executor.prepare(extraction, input)))
    }

    override fun toString(): String = "SmokeResolver"
}

/** A verdict with the measured numbers its line carries. */
private class Judged(val verdict: Verdict, val extras: Map<String, Long> = emptyMap(), val smokeWord: String? = null)

/** Everything one finished run leaves for judging. */
private class RunFacts(
    val spec: LegSpec,
    val variant: Int,
    val outcome: CommandOutcome,
    val summary: OutcomeSummary,
    val attempts: List<AttemptRecord>,
    val turns: List<TurnRecord>,
    val smoke: SmokeResolver,
)

/**
 * Runs one leg: checks every precondition, builds the pipeline through the one composition root, executes the prompt,
 * judges the result and writes the evidence. A refused leg makes no provider call and says so in one loud verdict line.
 * Only one leg runs at a time.
 */
internal class LegRunner(
    private val engine: SampleEngine,
    private val fixture: () -> FixtureState,
    private val vault: KeyVault,
    private val budget: RequestBudget,
    private val tap: AttemptTap,
    private val sink: EvidenceSink,
    demoSink: CommitSink = NoOpCommitSink,
    demo: DemoProvider = DemoProvider(),
    private val grammar: GrammarLegRig = GrammarLegRig.standalone(),
    private val nowSeconds: () -> Long,
) {
    private val running = Mutex()

    // The demos run on their own engine: the demo provider only, and a credential source that is never consulted.
    private val demoEngine = SampleEngine(listOf(demo), CredentialSource { CredentialLookup.Missing() }, demoSink, null)

    // The legs that have been started from the screen in this process. A live leg's earlier runs are also in the budget file.
    private val startedFromUi = HashSet<LegId>()

    /**
     * Runs [leg] and returns its result. [trigger] is `ui` for a tap or `autorun` for a debug intent. An autorun is a
     * rerun convenience only (D-01): it is refused with `autorun_before_ui` for a leg that has not been run from the
     * screen first, so an intent can never make a leg's first run.
     */
    suspend fun run(leg: LegId, trigger: String = TRIGGER_UI): LegResult {
        if (!running.tryLock()) return refuse(leg, "another_leg_running", emptyMap(), trigger)
        try {
            if (trigger == TRIGGER_AUTORUN) {
                if (!startedFromUi.contains(leg) && budget.runsOf(leg.wire) == 0) {
                    return refuse(leg, "autorun_before_ui", emptyMap(), trigger)
                }
            } else {
                startedFromUi.add(leg)
            }
            return runLocked(LegCatalog.spec(leg), trigger)
        } finally {
            running.unlock()
        }
    }

    private suspend fun runLocked(spec: LegSpec, trigger: String): LegResult {
        // The grammar leg needs no key, fixture or budget, so it is routed before any of those preconditions.
        if (spec.kind == LegKind.GRAMMAR_OFFLINE) return runGrammar(spec, trigger)
        val loaded = fixture() as? FixtureState.Loaded
        val refusal = precondition(spec, loaded)
        if (refusal != null) return refuse(spec.id, refusal.first, refusal.second, trigger)
        if (spec.kind == LegKind.DEMO_CLARIFY || spec.kind == LegKind.DEMO_PARTIAL) {
            return runDemo(spec, CommandInput(spec.prompts.first()), trigger, null)
        }

        val variant = minOf(budget.runsOf(spec.id.wire), spec.prompts.size - 1)
        budget.recordRun(spec.id.wire)
        val context = LegContext(spec.id, spec.optional)
        val fixtureForLeg = if (spec.needsFixture) loaded else null
        val legListener = EvidenceListener(spec.id, sink, fixtureForLeg?.prefixChars)
        val smoke = SmokeResolver(LegCatalog.liveTools)
        val pipeline = engine.pipeline(
            tier = tierFor(spec, smoke, fixtureForLeg),
            selection = ProviderSelection(spec.provider, spec.model),
            policy = TierPolicy { maxIterations = spec.maxIterations },
        ) {
            listener = legListener
            // The engine would refuse a Responses-only model before sending; the probe wants the transport's own answer.
            if (spec.kind == LegKind.RESPONSES_PROBE) capabilities(spec.provider, spec.model) { supportsTools = true }
        }

        if (spec.kind == LegKind.AGENTIC_FIXTURE && fixtureForLeg != null) {
            budget.markAgenticStart(nowSeconds())
            emitEnv(spec, fixtureForLeg, pipeline)
        }

        tap.current = context
        val outcome = try {
            pipeline.execute(CommandInput(spec.prompts[variant]))
        } finally {
            tap.current = null
        }
        val summary = OutcomeSummary.of(outcome)
        val facts = RunFacts(spec, variant, outcome, summary, context.attempts, legListener.turns, smoke)
        return finish(facts, trigger)
    }

    // The first failed precondition as (reason, extras), or null when the leg may start.
    private suspend fun precondition(spec: LegSpec, loaded: FixtureState.Loaded?): Pair<String, Map<String, Long>>? {
        var refusal: Pair<String, Map<String, Long>>? = null
        if (spec.needsFixture && loaded == null) {
            refusal = "fixture_${fixtureWord(fixture())}" to emptyMap()
        } else if (spec.needsKey) {
            val state = vault.read(spec.provider)
            if (state !is KeyState.Ready) {
                refusal = "key_${ImportReport.stateWord(state)}" to emptyMap()
            }
        }
        if (refusal == null && spec.reservation > 0 && !budget.canStart(spec.reservation, spec.optional)) {
            refusal = "budget" to emptyMap()
        }
        if (refusal == null && spec.kind == LegKind.AGENTIC_FIXTURE) {
            // A second Anthropic agentic start inside the cache TTL would read the first run's cache and prove nothing.
            val remaining = budget.warmWindowRemaining(nowSeconds())
            if (remaining > 0L) refusal = "warm_window" to mapOf("remaining" to remaining)
        }
        return refusal
    }

    // The runtime facts the cold-run verdict is read against: OkHttp, fixture digest, cache minimum and prefix size.
    private fun emitEnv(spec: LegSpec, loaded: FixtureState.Loaded, pipeline: CommandPipeline) {
        val capabilities = pipeline.capabilityTable.lookup(spec.provider, spec.model)
        sink.emit(
            EvidenceLine.env(
                okhttp = OkHttpRuntime.version(),
                fixtureSha = loaded.sha256,
                fixtureTools = loaded.tools.size,
                minCacheable = capabilities.minCacheablePrefixTokens,
                prefixChars = loaded.prefixChars,
                estPrefixTokens = (loaded.prefixChars / capabilities.charsPerToken).toInt(),
            ),
        )
    }

    private fun fixtureWord(state: FixtureState): String = when (state) {
        is FixtureState.Loaded -> "loaded"
        is FixtureState.Absent -> "absent"
        is FixtureState.ShaMismatch -> "sha_mismatch"
        is FixtureState.Malformed -> "malformed"
    }

    private fun tierFor(spec: LegSpec, smoke: SmokeResolver, loaded: FixtureState.Loaded?): CommandStrategy =
        when (spec.kind) {
            LegKind.SINGLE_SHOT, LegKind.RESPONSES_PROBE -> SingleShotStrategy(StrategyId("single")) {
                tooling = ToolSpecProvider.fixed(LegCatalog.liveSnapshot(spec.forcedTool))
                resolver = smoke
                forceTool = true
            }
            LegKind.AGENTIC_FIXTURE -> {
                val state = checkNotNull(loaded) { "the fixture leg needs a loaded fixture" }
                AgenticLoopStrategy(StrategyId("agentic")) {
                    tooling = ToolSpecProvider.fixed(ToolingSnapshot(state.system, state.tools, null))
                    executor = CannedToolExecutor(state.tools)
                }
            }
            LegKind.AGENTIC_SYNTHETIC -> AgenticLoopStrategy(StrategyId("agentic")) {
                tooling = ToolSpecProvider.fixed(LegCatalog.liveSnapshot(null))
                executor = CannedToolExecutor(LegCatalog.liveTools)
            }
            else -> error("leg kind ${spec.kind} is not a live leg")
        }

    private fun judge(facts: RunFacts): Judged = when (facts.spec.kind) {
        LegKind.SINGLE_SHOT -> {
            val spec = facts.spec
            val schema = LegCatalog.liveTools.first { it.name == spec.forcedTool }
            val result = SmokeVerdict.classify(
                expectedTool = requireNotNull(spec.forcedTool),
                schema = schema,
                toolName = facts.smoke.toolName,
                argKeys = facts.smoke.argKeys,
                requestedOptionals = spec.requestedOptionals,
                attempts = facts.attempts,
                outcome = facts.summary,
                anthropicStrict = spec.provider == ProviderId.ANTHROPIC,
            )
            Judged(result.verdict, emptyMap(), result.optionalAbsent)
        }
        LegKind.AGENTIC_FIXTURE -> {
            val result = CacheVerdict.classify(facts.turns, facts.attempts, facts.summary)
            Judged(result.verdict, result.extras())
        }
        LegKind.AGENTIC_SYNTHETIC -> {
            val result = MultiTurnVerdict.classify(
                requireNotNull(facts.spec.readTool),
                facts.turns,
                facts.attempts,
                facts.summary,
            )
            Judged(result.verdict, result.extras(facts.turns.size))
        }
        LegKind.RESPONSES_PROBE -> {
            // PASS only on the typed model_unsupported AFTER a real provider answer (an HTTP status is present).
            val status = facts.attempts.lastOrNull()?.httpStatus
            val reason = facts.summary.reason
            val extras = if (status == null) emptyMap() else mapOf("http" to status.toLong())
            val verdict = when {
                reason == REASON_MODEL_UNSUPPORTED && status != null ->
                    Verdict(VerdictKind.PASS, REASON_MODEL_UNSUPPORTED)
                reason == REASON_MODEL_UNSUPPORTED -> Verdict(VerdictKind.FAIL, REASON_NO_PROVIDER_CALL)
                else -> Verdict(VerdictKind.FAIL, reason ?: facts.summary.kind)
            }
            Judged(verdict, extras)
        }
        else -> error("leg kind ${facts.spec.kind} is not a live leg")
    }

    private fun finish(facts: RunFacts, trigger: String): LegResult {
        val spec = facts.spec
        val judged = judge(facts)
        sink.emit(EvidenceLine.outcome(spec.id, facts.summary))
        if (judged.smokeWord != null) {
            sink.emit(
                EvidenceLine.smoke(
                    spec.id,
                    facts.smoke.toolName,
                    facts.smoke.argKeys,
                    judged.smokeWord,
                    facts.variant,
                ),
            )
        }
        // key_charset=ok is claimed only for a provider that answered HTTP 200 in this very run.
        val charsetOk = if (facts.attempts.any { it.provider == spec.provider && it.httpStatus == HTTP_OK }) true else null
        sink.emit(EvidenceLine.verdict(spec.id, judged.verdict, judged.extras, charsetOk, trigger))
        sink.emit(EvidenceLine.budget(budget.snapshot(), CostEstimate.format(CostEstimate.usd(facts.turns))))
        return LegResult(spec.id, judged.verdict, facts.outcome, facts.summary)
    }

    /**
     * Starts a NEW command that answers the clarification [previous] ended on. The chosen [option] reaches the model
     * through the user-turn renderer, and the command is linked to the first by `parentRunId`; no transcript is resumed
     * (A19, D-14).
     */
    suspend fun followUp(
        previous: CommandOutcome.Completed,
        option: ClarificationOption,
        trigger: String = TRIGGER_UI,
    ): LegResult {
        val spec = LegCatalog.spec(LegId.DEMO_CLARIFY)
        if (!running.tryLock()) return refuse(spec.id, "another_leg_running", emptyMap(), trigger)
        try {
            val clarification = previous.terminalCall?.asClarification()
            if (clarification == null) return refuse(spec.id, "not_a_clarification", emptyMap(), trigger)
            if (clarification.options.none { it.id == option.id }) {
                return refuse(spec.id, "unknown_option", emptyMap(), trigger)
            }
            val input = CommandInput(
                spec.prompts.first(),
                null,
                FollowUpContext(clarification.question, option),
                previous.runId,
            )
            return runDemo(spec, input, trigger, previous.runId)
        } finally {
            running.unlock()
        }
    }

    private suspend fun runDemo(spec: LegSpec, input: CommandInput, trigger: String, parentRunId: String?): LegResult {
        val legListener = EvidenceListener(spec.id, sink)
        val pipeline = demoEngine.pipeline(
            tier = demoTier(spec),
            selection = ProviderSelection(DEMO_PROVIDER, DEMO_MODEL),
            policy = TierPolicy { maxIterations = spec.maxIterations },
        ) {
            listener = legListener
        }
        val outcome = pipeline.execute(input)
        val summary = OutcomeSummary.of(outcome)
        val verdict = demoVerdict(spec, outcome, summary, parentRunId)
        sink.emit(EvidenceLine.outcome(spec.id, summary))
        sink.emit(EvidenceLine.verdict(spec.id, verdict, emptyMap(), null, trigger))
        return LegResult(spec.id, verdict, outcome, summary)
    }

    // The grammar leg: its own engine (real providers behind the tap plus the tripwire), one VAE_TRACE per case, then the
    // outcome of the last case and one verdict. It sends nothing to a provider; any non-zero count fails it.
    private suspend fun runGrammar(spec: LegSpec, trigger: String): LegResult {
        val run = GrammarLeg.run(grammar, tap, sink, spec.id)
        val last = run.results.last()
        val summary = OutcomeSummary.of(last.outcome)
        sink.emit(EvidenceLine.outcome(spec.id, summary))
        sink.emit(EvidenceLine.verdict(spec.id, run.verdict, run.extras, null, trigger))
        return LegResult(spec.id, run.verdict, last.outcome, summary)
    }

    private fun demoTier(spec: LegSpec): CommandStrategy = when (spec.kind) {
        LegKind.DEMO_PARTIAL -> SingleShotStrategy(StrategyId("single")) {
            capabilities = DEMO_ONLY
            tooling = ToolSpecProvider.fixed(SyntheticTools.snapshot(spec.forcedTool))
            resolver = SmokeResolver(SyntheticTools.all)
            forceTool = true
        }
        else -> AgenticLoopStrategy(StrategyId("agentic")) {
            capabilities = DEMO_ONLY
            tooling = ToolSpecProvider.fixed(SyntheticTools.snapshot(null))
            executor = CannedToolExecutor(SyntheticTools.all)
            userTurn = FollowUpTurnRenderer
        }
    }

    // PASS when the outcome has the scripted shape, otherwise FAIL naming the shape that was observed.
    private fun demoVerdict(
        spec: LegSpec,
        outcome: CommandOutcome,
        summary: OutcomeSummary,
        parentRunId: String?,
    ): Verdict {
        val completed = outcome as? CommandOutcome.Completed
            ?: return Verdict.fail(summary.failureCode() ?: "not_completed")
        val code = when {
            spec.kind == LegKind.DEMO_PARTIAL -> partialShape(completed)
            parentRunId != null -> followUpShape(completed, parentRunId)
            else -> clarifyShape(completed)
        }
        return if (code == null) Verdict.pass() else Verdict.fail(code)
    }

    private fun partialShape(outcome: CommandOutcome.Completed): String? = when {
        !outcome.partial -> "not_partial"
        outcome.commits.size != 1 -> "commit_count_${outcome.commits.size}"
        else -> null
    }

    private fun followUpShape(outcome: CommandOutcome.Completed, parentRunId: String): String? = when {
        outcome.parentRunId != parentRunId -> "parent_mismatch"
        outcome.partial -> "partial"
        outcome.commits.size != 1 -> "commit_count_${outcome.commits.size}"
        else -> null
    }

    private fun clarifyShape(outcome: CommandOutcome.Completed): String? {
        val clarification = outcome.terminalCall?.asClarification()
        return when {
            outcome.terminalCall == null -> "no_terminal_call"
            clarification == null -> "not_a_clarification"
            clarification.question.isBlank() -> "blank_question"
            clarification.options.isEmpty() -> "no_options"
            else -> null
        }
    }

    private fun refuse(leg: LegId, reason: String, extras: Map<String, Long>, trigger: String): LegResult {
        val verdict = Verdict(VerdictKind.REFUSED, reason)
        sink.emit(EvidenceLine.verdict(leg, verdict, extras, null, trigger))
        return LegResult(leg, verdict, null, null)
    }

    override fun toString(): String = "LegRunner"

    private companion object {
        const val TRIGGER_UI = "ui"
    }
}
