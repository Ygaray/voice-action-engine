package io.github.ygaray.voiceactionengine.sample.evidence

import android.util.Log
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import io.github.ygaray.voiceactionengine.sample.verdict.OutcomeSummary
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind

/**
 * The Gate-1 legs, by their wire name. 10-05 to 10-07 and the runbook use these exact strings.
 */
internal enum class LegId(val wire: String) {
    VER02("ver02"),
    SMOKE_ANTHROPIC("smoke_anthropic"),
    SMOKE_OPENAI("smoke_openai"),
    SMOKE_OPENROUTER("smoke_openrouter"),
    MULTI_OPENAI("multi_openai"),
    MULTI_OPENROUTER("multi_openrouter"),
    RESPONSES_PROBE("responses_probe"),
    DEMO_CLARIFY("demo_clarify"),
    DEMO_PARTIAL("demo_partial"),
    GRAMMAR_OFFLINE("grammar_offline"),
}

/**
 * True for a leg that runs on the private SB fixture. Its tool names (the tools the model called, the terminal tool) are
 * fixture content and must never reach a committed evidence file (LE-7), so its lines carry counts and the word
 * `redacted` instead. Decided here, from the leg alone, so no caller can forget it.
 */
internal val LegId.fixtureBacked: Boolean get() = this == LegId.VER02

private const val REDACTED = "redacted"
private const val INVALID_TOKEN = "invalid_token"
private const val INVALID_KEY = "invalid_key"
private const val NONE = "none"
private const val MAX_TOKEN_LENGTH = 96
private const val HEX_PREFIX_LENGTH = 8
private const val MAX_HEX_LENGTH = 64

// A value token: letters, digits and _ . : / , [ ] - only. No space, quote or newline can occur, so no sentence can.
private val VALUE_TOKEN = Regex("[A-Za-z0-9_.:/,\\[\\]-]{0,$MAX_TOKEN_LENGTH}")

// A list item: the value alphabet minus the list punctuation, so an item cannot split or close the list.
private val LIST_ITEM = Regex("[A-Za-z0-9_.:/-]{1,$MAX_TOKEN_LENGTH}")
private val FIELD_KEY = Regex("[a-z0-9_]+")
private val HEX = Regex("[0-9a-f]{1,$MAX_HEX_LENGTH}")

// The closed word sets of a VAE_TRACE line: outcome kinds, grammar languages and start-tier pick outcomes.
private val TRACE_KINDS = setOf("completed", "failed", "unhandled")
private val TRACE_LANGS = setOf("en", "es")
private val TRACE_SELS = setOf("picked", "router_fallback", "cancelled", "timeout", "failed")

/**
 * The full-line grammar of every evidence line. The host filter in 10-07 keeps exactly the lines matching this.
 */
internal const val ALLOW_PATTERN =
    "^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN|TRACE)" +
        "( [a-z0-9_]+=[A-Za-z0-9_.:/,\\[\\]-]{0,96})+$"

/**
 * What one traced command amounts to, for a `VAE_TRACE` line: counts, booleans, indexes and codes only. Nothing here can
 * hold a tier id, a transcript, a slot value, a tool argument or a key: the words are checked against closed sets when
 * the line is built, and the trace codes are typed (the sample cannot construct one).
 *
 * @property kind `completed`, `failed` or `unhandled` (anything else renders `invalid_token`).
 * @property capped for an unhandled outcome, whether the policy skipped a tier; null otherwise.
 * @property tiersRun how many tiers ran.
 * @property providerTurns the model round trips the event listener saw.
 * @property attempts the HTTP requests the request tap saw.
 * @property tripwireCalls the calls the tripwire provider received.
 * @property matchedLang `en` or `es` when a grammar tier matched in that language, else null.
 * @property sel the start-tier pick's outcome word (`picked`, `router_fallback`, ...), or null without a pick.
 * @property eligible how many model tiers the picker was offered, or null.
 * @property pickedIndex the picked tier's index among the eligible ones, or null.
 * @property firstModelIndex the ladder index of the first tier that calls a model, or null when not reported.
 * @property bypassed the tiers a linear walk would have tried before the picked one, or null.
 * @property selTurns the model round trips the pick itself took, or null.
 * @property codes the engine's trace codes for the run, in order.
 */
internal class TraceFacts(
    val kind: String,
    val capped: Boolean?,
    val tiersRun: Int,
    val providerTurns: Int,
    val attempts: Int,
    val tripwireCalls: Int,
    val matchedLang: String?,
    val sel: String? = null,
    val eligible: Int? = null,
    val pickedIndex: Int? = null,
    val firstModelIndex: Int? = null,
    val bypassed: Int? = null,
    val selTurns: Int? = null,
    val codes: List<TraceCode> = emptyList(),
) {
    /** Counts and words only; no list of codes. */
    override fun toString(): String = "TraceFacts(kind=$kind, tiersRun=$tiersRun, codes=${codes.size})"

    companion object {
        /**
         * The facts of a finished [outcome] with the measured counts. The start-tier pick, when there was one, is
         * reduced to its outcome word, its eligible count, the picked index and the bypass and turn counts.
         */
        fun of(
            outcome: CommandOutcome,
            providerTurns: Int,
            attempts: Int,
            tripwireCalls: Int,
            matchedLang: String?,
            firstModelIndex: Int? = null,
        ): TraceFacts {
            val trace = outcome.trace
            val selection = trace.selection
            return TraceFacts(
                kind = OutcomeSummary.of(outcome).kind,
                capped = (outcome as? CommandOutcome.Unhandled)?.cappedByPolicy,
                tiersRun = trace.attempts.size,
                providerTurns = providerTurns,
                attempts = attempts,
                tripwireCalls = tripwireCalls,
                matchedLang = matchedLang,
                sel = selection?.outcome,
                eligible = selection?.eligible?.size,
                pickedIndex = selection?.picked?.let { picked -> selection.eligible.indexOf(picked).takeIf { it >= 0 } },
                firstModelIndex = firstModelIndex,
                bypassed = selection?.tiersBypassed,
                selTurns = selection?.turns?.size,
                codes = trace.codes,
            )
        }
    }
}

/**
 * One line of Gate-1 evidence. It is a closed vocabulary by construction: the constructor is private, the factories
 * take only typed values (enums, numbers, provider ids, tool-name lists, hex fingerprints, stable codes), and every
 * value is restricted to a token alphabet of at most 96 characters. Anything else renders as `invalid_token`, so a
 * prompt with spaces, a reply, a tool argument with punctuation cannot reach a log line. The alphabet is a shape filter,
 * not a vocabulary: a value that fits it passes, so names that are private content (the fixture's tool names) are kept
 * out by redaction at the source (see [fixtureBacked]) and by the host filter, never by the alphabet alone.
 *
 * @property type the line type word, for example `TURN`; the rendered line starts with `VAE_` plus it.
 * @property loud true when the line must be logged at error level so a failure cannot hide in the noise.
 */
internal class EvidenceLine private constructor(
    val type: String,
    val loud: Boolean,
    private val fields: List<Pair<String, String>>,
) {
    /** The line: `VAE_<TYPE>` then ` key=value` pairs in insertion order. */
    fun render(): String = buildString {
        append("VAE_").append(type)
        for ((key, value) in fields) append(' ').append(key).append('=').append(value)
    }

    override fun toString(): String = render()

    companion object {
        /** The runtime facts of one run: OkHttp version, fixture digest prefix and size, cache minimum, prefix size. */
        fun env(
            okhttp: String,
            fixtureSha: String?,
            fixtureTools: Int?,
            minCacheable: Int?,
            prefixChars: Int?,
            estPrefixTokens: Int?,
        ): EvidenceLine = EvidenceLine(
            "ENV",
            false,
            listOf(
                "okhttp" to token(okhttp),
                "fixture_sha" to hexOrNone(fixtureSha),
                "fixture_tools" to numOrNone(fixtureTools),
                "min_cacheable" to numOrNone(minCacheable),
                "prefix_chars" to numOrNone(prefixChars),
                "est_prefix_tokens" to numOrNone(estPrefixTokens),
            ),
        )

        /** What loading the fixture produced. Loud unless it loaded. */
        fun fixture(state: FixtureState): EvidenceLine {
            val fields: List<Pair<String, String>> = when (state) {
                is FixtureState.Loaded -> listOf(
                    "kind" to "loaded",
                    "source" to token(state.source),
                    "sha" to hexOrNone(state.sha256),
                    "tools" to state.tools.size.toString(),
                    "bytes" to state.byteCount.toString(),
                )
                is FixtureState.Absent -> listOf("kind" to "absent", "searched" to list(state.searched))
                is FixtureState.ShaMismatch -> listOf(
                    "kind" to "sha_mismatch",
                    "source" to token(state.source),
                    "actual" to hexOrNone(state.actualPrefix),
                )
                is FixtureState.Malformed -> listOf(
                    "kind" to "malformed",
                    "source" to token(state.source),
                    "code" to token(state.code),
                )
            }
            return EvidenceLine("FIXTURE", state !is FixtureState.Loaded, fields)
        }

        /** What happened to one provider's pushed test key. State words and booleans only. */
        fun key(report: ImportReport): EvidenceLine {
            val problem = report.plaintextInDatastore == true || !report.plaintextFileDeleted ||
                report.state in LOUD_KEY_STATES
            return EvidenceLine(
                "KEY",
                problem,
                listOf(
                    "provider" to token(report.provider.value),
                    "state" to token(report.state),
                    "cause" to tokenOrNone(report.cause),
                    "deleted" to report.plaintextFileDeleted.toString(),
                    "in_datastore" to (report.plaintextInDatastore?.toString() ?: NONE),
                ),
            )
        }

        /**
         * One model round trip in the SecondBrain field order: `input_tokens` is the uncached input,
         * `cache_creation_input_tokens` the cache write and `cache_read_input_tokens` the cache read.
         */
        fun turn(leg: LegId, iteration: Int, record: TurnRecord, prefixChars: Int?): EvidenceLine = EvidenceLine(
            "TURN",
            false,
            listOf(
                "leg" to leg.wire,
                "iteration" to iteration.toString(),
                "model" to tokenOrNone(record.model),
                "stop_reason" to tokenOrNone(record.stopReason),
                "tools" to if (leg.fixtureBacked) REDACTED else list(record.toolNames),
                "tool_count" to record.toolNames.size.toString(),
                "input_tokens" to record.usage.inputUncached.toString(),
                "output_tokens" to record.usage.output.toString(),
                "cache_creation_input_tokens" to record.usage.cacheWrite.toString(),
                "cache_read_input_tokens" to record.usage.cacheRead.toString(),
                "prefix_chars" to numOrNone(prefixChars),
                "latency_ms" to record.latencyMillis.toString(),
            ),
        )

        /** One HTTP request a transport sent. */
        fun attempt(leg: LegId, record: AttemptRecord): EvidenceLine = EvidenceLine(
            "ATTEMPT",
            false,
            listOf(
                "leg" to leg.wire,
                "provider" to token(record.provider.value),
                "n" to record.number.toString(),
                "kind" to token(record.kind),
                "http" to numOrNone(record.httpStatus),
                "finish" to tokenOrNone(record.finishReason),
                "tool_calls" to record.toolCalls.toString(),
            ),
        )

        /** The engine saw a cacheable prefix that the provider did not cache. Always loud. */
        fun cache(leg: LegId, provider: ProviderId, model: String?): EvidenceLine = EvidenceLine(
            "CACHE",
            true,
            listOf(
                "leg" to leg.wire,
                "provider" to token(provider.value),
                "model" to tokenOrNone(model),
                "event" to "not_engaged",
            ),
        )

        /**
         * What a single-shot leg extracted: the tool name and the argument key names only, never a value.
         * [optionalAbsent] is `true`, `false` or `inconclusive` (see `SmokeVerdict`).
         */
        fun smoke(
            leg: LegId,
            tool: String?,
            argKeys: Set<String>,
            optionalAbsent: String,
            promptVariant: Int,
        ): EvidenceLine = EvidenceLine(
            "SMOKE",
            false,
            listOf(
                "leg" to leg.wire,
                "tool" to tokenOrNone(tool),
                "arg_keys" to list(argKeys.sorted()),
                "optional_absent" to token(optionalAbsent),
                "prompt_variant" to promptVariant.toString(),
            ),
        )

        /** What the command outcome amounts to: codes and counts, the reply only as a length. */
        fun outcome(leg: LegId, summary: OutcomeSummary): EvidenceLine = EvidenceLine(
            "OUTCOME",
            false,
            listOf(
                "leg" to leg.wire,
                "kind" to token(summary.kind),
                "partial" to summary.partial.toString(),
                "reason" to tokenOrNone(summary.reason),
                "executed" to summary.executed.toString(),
                "committed" to summary.committed.toString(),
                "held" to summary.held.toString(),
                "reply_len" to summary.replyLength.toString(),
                "terminal_tool" to terminalTool(leg, summary.terminalTool),
            ),
        )

        /**
         * The leg's verdict with its measured numbers ([extras], in the map's order) and how it was triggered
         * (`ui` or `autorun`). Loud when the leg failed or was refused.
         */
        fun verdict(
            leg: LegId,
            verdict: Verdict,
            extras: Map<String, Long>,
            keyCharsetOk: Boolean?,
            trigger: String,
        ): EvidenceLine {
            val fields = ArrayList<Pair<String, String>>()
            fields += "leg" to leg.wire
            fields += "verdict" to verdict.kind.name
            if (verdict.reason != null) fields += "reason" to token(verdict.reason)
            for ((name, value) in extras) fields += fieldKey(name) to value.toString()
            if (keyCharsetOk != null) fields += "key_charset" to if (keyCharsetOk) "ok" else "bad"
            fields += "trigger" to token(trigger)
            val loud = verdict.kind == VerdictKind.FAIL || verdict.kind == VerdictKind.REFUSED
            return EvidenceLine("VERDICT", loud, fields)
        }

        /**
         * What one traced command did: counts, booleans, indexes and codes only (see [TraceFacts]). [case] is the
         * 1-based case number inside the leg. Never loud: a failing leg says so in its verdict line.
         */
        fun trace(leg: LegId, case: Int, facts: TraceFacts): EvidenceLine = EvidenceLine(
            "TRACE",
            false,
            listOf(
                "leg" to leg.wire,
                "case" to case.toString(),
                "kind" to oneOf(facts.kind, TRACE_KINDS),
                "capped" to (facts.capped?.toString() ?: NONE),
                "tiers_run" to facts.tiersRun.toString(),
                "provider_turns" to facts.providerTurns.toString(),
                "attempts" to facts.attempts.toString(),
                "tripwire_calls" to facts.tripwireCalls.toString(),
                "matched_lang" to oneOfOrNone(facts.matchedLang, TRACE_LANGS),
                "sel" to oneOfOrNone(facts.sel, TRACE_SELS),
                "eligible" to numOrNone(facts.eligible),
                "picked_index" to numOrNone(facts.pickedIndex),
                "first_model_index" to numOrNone(facts.firstModelIndex),
                "bypassed" to numOrNone(facts.bypassed),
                "sel_turns" to numOrNone(facts.selTurns),
                "codes" to list(facts.codes.map { it.value }),
            ),
        )

        /**
         * The requests spent so far, in total and per provider, and the cost estimate ([estUsd] is the text from
         * `CostEstimate.format`).
         */
        fun budget(snapshot: BudgetSnapshot, estUsd: String): EvidenceLine = EvidenceLine(
            "BUDGET",
            false,
            listOf(
                "core" to snapshot.core.toString(),
                "optional" to snapshot.optional.toString(),
                "anthropic" to (snapshot.perProvider[ProviderId.ANTHROPIC.value] ?: 0).toString(),
                "openai" to (snapshot.perProvider[ProviderId.OPENAI.value] ?: 0).toString(),
                "openrouter" to (snapshot.perProvider[ProviderId.OPENROUTER.value] ?: 0).toString(),
                "est_usd" to token(estUsd),
            ),
        )

        /** The request count could not be saved, so the spend guard now refuses every call. Always loud. */
        fun budgetFault(): EvidenceLine = EvidenceLine("BUDGET", true, listOf("fault" to "store_write_failed"))

        /** A debug rerun was started by the autorun intent instead of a tap. */
        fun autorun(leg: LegId): EvidenceLine = EvidenceLine("AUTORUN", false, listOf("leg" to leg.wire))

        /** A free-form value as a token: itself when it fits the alphabet and length, else `invalid_token`. */
        internal fun token(value: String): String = if (VALUE_TOKEN.matches(value)) value else INVALID_TOKEN

        private fun tokenOrNone(value: String?): String = if (value == null) NONE else token(value)

        // A fixture leg reports only that a terminal tool ended the run, never which one.
        private fun terminalTool(leg: LegId, name: String?): String =
            if (leg.fixtureBacked) (if (name == null) NONE else REDACTED) else tokenOrNone(name)

        // A word from a closed set, else invalid_token: a tier id or any free word cannot pass as a trace word.
        private fun oneOf(value: String, allowed: Set<String>): String = if (value in allowed) value else INVALID_TOKEN

        private fun oneOfOrNone(value: String?, allowed: Set<String>): String =
            if (value == null) NONE else oneOf(value, allowed)

        private fun numOrNone(value: Int?): String = value?.toString() ?: NONE

        private fun hexOrNone(value: String?): String =
            if (value == null) NONE else value.take(HEX_PREFIX_LENGTH).let { if (HEX.matches(it)) it else INVALID_TOKEN }

        /** `[a,b]` of items that fit the item alphabet; the whole list is `invalid_token` if it would not fit. */
        private fun list(items: Collection<String>): String =
            token(items.joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
                if (LIST_ITEM.matches(item)) item else INVALID_TOKEN
            })

        private fun fieldKey(name: String): String = if (FIELD_KEY.matches(name)) name else INVALID_KEY

        private val LOUD_KEY_STATES = setOf(
            ImportReport.UNREADABLE,
            ImportReport.SAVE_FAILED,
            ImportReport.REJECTED,
        )
    }
}

/** Where evidence lines go. */
internal fun interface EvidenceSink {
    /** Takes [line]. Must not throw and must not block. */
    fun emit(line: EvidenceLine)
}

/** Logcat tag every evidence line is written under; the host capture filters on it. */
internal const val EVIDENCE_TAG = "VaeSample"

/**
 * The app's sink: a loud line is logged at error level, any other at info. This is the only place in the sample that
 * calls `Log`.
 */
internal object LogcatEvidenceSink : EvidenceSink {
    override fun emit(line: EvidenceLine) {
        if (line.loud) Log.e(EVIDENCE_TAG, line.render()) else Log.i(EVIDENCE_TAG, line.render())
    }

    override fun toString(): String = "LogcatEvidenceSink"
}

/**
 * Turns the engine's events into evidence: a `VAE_TURN` line for every model round trip (with a 1-based iteration
 * counter) and a loud `VAE_CACHE` line when the cache was not engaged. It also keeps the turn records, in order, for the
 * verdict. Every other event is ignored.
 */
internal class EvidenceListener(
    private val leg: LegId,
    private val sink: EvidenceSink,
    private val prefixChars: Int? = null,
) : PipelineEventListener {
    private val recorded = ArrayList<TurnRecord>()

    /** The turns seen so far, oldest first; a snapshot. */
    val turns: List<TurnRecord>
        get() = synchronized(recorded) { recorded.toList() }

    override fun onEvent(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.ProviderCall -> {
                val iteration = synchronized(recorded) {
                    recorded.add(event.turn)
                    recorded.size
                }
                sink.emit(EvidenceLine.turn(leg, iteration, event.turn, prefixChars))
            }
            is PipelineEvent.CacheNotEngaged -> sink.emit(EvidenceLine.cache(leg, event.provider, event.model))
            else -> Unit
        }
    }

    override fun toString(): String = "EvidenceListener(leg=${leg.wire})"
}
