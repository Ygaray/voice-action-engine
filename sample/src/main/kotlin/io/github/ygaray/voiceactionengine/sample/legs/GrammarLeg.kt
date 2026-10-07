package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.sample.SampleEngine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceListener
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.TraceFacts
import io.github.ygaray.voiceactionengine.sample.net.AttemptTap
import io.github.ygaray.voiceactionengine.sample.net.LegContext
import io.github.ygaray.voiceactionengine.sample.tools.CannedToolExecutor
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict

private const val TOOL_CREATE_ITEM = "create_item"
private const val SLOT_TITLE = "title"
private const val TITLE_MAX_WORDS = 3
private const val LANG_EN = "en"
private const val LANG_ES = "es"

/** The committed English phrasing the first case speaks: an exact grammar phrasing with a synthetic title. */
internal const val GRAMMAR_EN_TRANSCRIPT = "add paper to my list"

/** The committed Spanish phrasing the second case speaks. */
internal const val GRAMMAR_ES_TRANSCRIPT = "agrega papel a mi lista"

/** The near-miss: a whole English phrasing plus extra words. The grammar never matches partially. */
internal const val GRAMMAR_NEAR_MISS_TRANSCRIPT = "add paper to my list right now"

/**
 * One command the grammar leg sends. [expectedLang] is the language the grammar must report for a command that completes,
 * or null for a command that must be handed on and end unhandled, capped by policy.
 */
internal class GrammarCase(val transcript: String, val language: String?, val expectedLang: String?) {
    /** Never the transcript. */
    override fun toString(): String = "GrammarCase(language=$language, expectedLang=$expectedLang)"
}

/**
 * A resolver that records only the language the grammar matched and the NAMES of the arguments, then prepares the canned
 * step for the matched tool. No argument value is read into a field.
 */
internal class GrammarResolver(tools: List<ToolSpec>) : OutcomeResolver {
    private val executor = CannedToolExecutor(tools)

    /** The language of the match (`en` or `es`), or null when the tier never resolved one. */
    @Volatile
    var matchedLang: String? = null
        private set

    /** The argument key names of the matched call. */
    @Volatile
    var argKeys: Set<String> = emptySet()
        private set

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        matchedLang = extraction.matchedLanguage
        argKeys = extraction.arguments.keys.toSet()
        return Resolution.Steps(listOf(executor.prepare(extraction, input)))
    }

    override fun toString(): String = "GrammarResolver"
}

/**
 * The engine the grammar leg runs on and the tripwire inside it. The engine registers [providers] (the real ones, behind
 * the request tap, so any HTTP attempt would be counted) plus the tripwire; the leg's single-shot tier selects only the
 * tripwire, so a policy leak can never reach a real provider.
 */
internal class GrammarLegRig(val engine: SampleEngine, val tripwire: TripwireProvider) {
    override fun toString(): String = "GrammarLegRig"

    companion object {
        /** A rig whose engine registers [providers] and one new tripwire. */
        fun create(providers: List<AiProvider>, credentials: CredentialSource, sink: CommitSink): GrammarLegRig {
            val tripwire = TripwireProvider()
            return GrammarLegRig(SampleEngine(providers + tripwire, credentials, sink, null), tripwire)
        }

        /** A rig with the tripwire as its only provider, for a runner that was not given real ones. */
        fun standalone(): GrammarLegRig =
            create(emptyList(), CredentialSource { CredentialLookup.Missing() }, NoOpCommitSink)
    }
}

/** One finished case: the engine's outcome and the facts the `VAE_TRACE` line carries. */
internal class GrammarCaseResult(val case: GrammarCase, val outcome: CommandOutcome, val facts: TraceFacts) {
    override fun toString(): String = "GrammarCaseResult(case=$case, facts=$facts)"
}

/** What the leg produced: every case, the verdict and the numbers the verdict line carries. */
internal class GrammarRun(val results: List<GrammarCaseResult>, val verdict: Verdict, val extras: Map<String, Long>) {
    override fun toString(): String = "GrammarRun(cases=${results.size}, verdict=$verdict)"
}

/**
 * The `grammar_offline` leg (VER-06, D-05): the free grammar tier answers an English and a Spanish command, and a near-miss
 * is handed on and ends unhandled, capped by policy, with ZERO provider calls. Zero is proven by three counts, not by the
 * absence of HTTP: model round trips (`provider_turns`), HTTP attempts seen by the request tap (`attempts`) and calls the
 * tripwire received (`tripwire_calls`). Any non-zero count fails the leg. No airplane mode is used.
 */
internal object GrammarLeg {
    /** The three committed cases, in order: English match, Spanish match, near-miss. */
    val cases: List<GrammarCase> = listOf(
        GrammarCase(GRAMMAR_EN_TRANSCRIPT, LANG_EN, LANG_EN),
        GrammarCase(GRAMMAR_ES_TRANSCRIPT, LANG_ES, LANG_ES),
        GrammarCase(GRAMMAR_NEAR_MISS_TRANSCRIPT, LANG_EN, null),
    )

    /** The sample pack: one create-item intent with a title text slot, one English and one Spanish phrasing. */
    fun pack(tryOtherLanguage: Boolean = false): GrammarPack = GrammarPack {
        this.tryOtherLanguage = tryOtherLanguage
        intent(TOOL_CREATE_ITEM) {
            text(SLOT_TITLE, TITLE_MAX_WORDS)
            en("add {$SLOT_TITLE} to my list")
            es("agrega {$SLOT_TITLE} a mi lista")
        }
    }

    /** The policy the leg runs under. */
    fun offlinePolicy(): TierPolicy = TierPolicy { offlineOnly = true }

    // The single-shot tier names the tripwire and nothing else, so the policy is the first line and the tripwire the third.
    private fun singleShotTier(): CommandStrategy = SingleShotStrategy(StrategyId("single")) {
        capabilities = StrategyCapabilities(setOf(TRIPWIRE_PROVIDER))
        tooling = ToolSpecProvider.fixed(SyntheticTools.snapshot(TOOL_CREATE_ITEM))
        resolver = SmokeResolver(SyntheticTools.all)
        forceTool = true
    }

    /**
     * Runs [cases] in order on [rig], one `VAE_TRACE` line per case on [sink], and judges them. [policy] and [pack] are
     * seams: a test passes a policy that lets the single-shot tier run, to prove a leak reaches only the tripwire and fails.
     */
    suspend fun run(
        rig: GrammarLegRig,
        tap: AttemptTap,
        sink: EvidenceSink,
        leg: LegId,
        cases: List<GrammarCase> = GrammarLeg.cases,
        policy: TierPolicy = offlinePolicy(),
        pack: GrammarPack = pack(),
    ): GrammarRun {
        val results = ArrayList<GrammarCaseResult>()
        for ((index, case) in cases.withIndex()) {
            val result = runCase(rig, tap, sink, leg, case, policy, pack)
            sink.emit(EvidenceLine.trace(leg, index + 1, result.facts))
            results.add(result)
        }
        return judge(results)
    }

    private suspend fun runCase(
        rig: GrammarLegRig,
        tap: AttemptTap,
        sink: EvidenceSink,
        leg: LegId,
        case: GrammarCase,
        policy: TierPolicy,
        phrasings: GrammarPack,
    ): GrammarCaseResult {
        val resolver = GrammarResolver(SyntheticTools.all)
        val listener = EvidenceListener(leg, sink)
        val context = LegContext(leg, false)
        val tripwireBefore = rig.tripwire.calls
        val grammarTier = LocalGrammarStrategy(StrategyId("grammar")) {
            pack = phrasings
            this.resolver = resolver
        }
        val laddered = singleShotTier()
        val pipeline = rig.engine.pipeline(
            tier = grammarTier,
            selection = ProviderSelection(TRIPWIRE_PROVIDER, TRIPWIRE_MODEL),
            policy = policy,
        ) {
            this.listener = listener
            tier(laddered)
        }
        tap.current = context
        val outcome = try {
            pipeline.execute(CommandInput(case.transcript, case.language))
        } finally {
            tap.current = null
        }
        val facts = TraceFacts.of(
            outcome = outcome,
            providerTurns = listener.turns.size,
            attempts = context.attempts.size,
            tripwireCalls = rig.tripwire.calls - tripwireBefore,
            matchedLang = resolver.matchedLang,
        )
        return GrammarCaseResult(case, outcome, facts)
    }

    // PASS only when nothing was called and every case ended as expected; otherwise FAIL with the first failing code.
    private fun judge(results: List<GrammarCaseResult>): GrammarRun {
        val turns = results.sumOf { it.facts.providerTurns }
        val attempts = results.sumOf { it.facts.attempts }
        val tripwire = results.sumOf { it.facts.tripwireCalls }
        val expectingMatch = results.filter { it.case.expectedLang != null }
        val expectingMiss = results.filter { it.case.expectedLang == null }
        fun notCompleted(lang: String): Boolean =
            expectingMatch.any { it.case.expectedLang == lang && it.outcome !is CommandOutcome.Completed }
        val wrongLanguage = expectingMatch.any {
            it.outcome is CommandOutcome.Completed && it.facts.matchedLang != it.case.expectedLang
        }
        val notCapped = expectingMiss.any {
            val outcome = it.outcome
            !(outcome is CommandOutcome.Unhandled && outcome.cappedByPolicy)
        }
        val code = when {
            tripwire > 0 || turns > 0 -> REASON_PROVIDER_CALLED
            attempts > 0 -> REASON_HTTP_ATTEMPTED
            notCompleted(LANG_EN) -> REASON_EN_NOT_COMPLETED
            notCompleted(LANG_ES) -> REASON_ES_NOT_COMPLETED
            wrongLanguage -> REASON_WRONG_LANGUAGE
            notCapped -> REASON_NEAR_MISS_NOT_CAPPED
            else -> null
        }
        val extras = LinkedHashMap<String, Long>()
        for (lang in listOf(LANG_EN, LANG_ES)) {
            val mine = expectingMatch.filter { it.case.expectedLang == lang }
            if (mine.isNotEmpty()) {
                extras[lang] = if (mine.all { it.outcome is CommandOutcome.Completed && it.facts.matchedLang == lang }) 1L else 0L
            }
        }
        if (expectingMiss.isNotEmpty()) extras["near_miss_capped"] = if (notCapped) 0L else 1L
        extras["provider_turns"] = turns.toLong()
        extras["attempts"] = attempts.toLong()
        extras["tripwire_calls"] = tripwire.toLong()
        val verdict = if (code == null) Verdict.pass() else Verdict.fail(code)
        return GrammarRun(results, verdict, extras)
    }
}

internal const val REASON_PROVIDER_CALLED = "provider_called"
internal const val REASON_HTTP_ATTEMPTED = "http_attempted"
internal const val REASON_EN_NOT_COMPLETED = "en_not_completed"
internal const val REASON_ES_NOT_COMPLETED = "es_not_completed"
internal const val REASON_WRONG_LANGUAGE = "wrong_language"
internal const val REASON_NEAR_MISS_NOT_CAPPED = "near_miss_not_capped"
