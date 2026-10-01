package io.github.ygaray.voiceactionengine.sample.verdict

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome

/**
 * How a Gate-1 leg ended. Only [PASS] is a pass. [WARM] and [OUT_OF_BAND] are escalations that carry a measured value:
 * they are never reported as a pass and never reclassified by widening a band.
 */
internal enum class VerdictKind {
    /** The leg proved what it was meant to prove. */
    PASS,

    /** The leg ran and the rule was not met. */
    FAIL,

    /** The cache was already warm at turn 1, so the cold-run proof did not happen: an infrastructure re-run. */
    WARM,

    /** A measured value fell outside the expected band: escalate with the number, never a pass. */
    OUT_OF_BAND,

    /** The run was clean but did not settle the question (for example the model filled an optional field). */
    INCONCLUSIVE,

    /** A capture-only leg (no pass/fail rule) recorded its numbers. */
    CAPTURED,

    /** The leg did not start because a precondition or the spend guard said no; nothing was sent. */
    REFUSED,
}

/**
 * The classification of one leg: a [kind] and a short stable [reason] code (`[a-z0-9_]+`), or null when the kind needs
 * none. Never free text.
 */
internal class Verdict(val kind: VerdictKind, val reason: String?) {
    override fun toString(): String = "Verdict(kind=$kind, reason=$reason)"

    companion object {
        /** A pass. */
        fun pass(): Verdict = Verdict(VerdictKind.PASS, null)

        /** A failure with its reason code. */
        fun fail(reason: String): Verdict = Verdict(VerdictKind.FAIL, reason)
    }
}

/**
 * One HTTP request a provider sent, as the transports' attempt observers report it: facts only. [kind] is the attempt
 * kind's wire value (`initial`, `transient_retry`, `forced_tool_reshape`).
 *
 * @property finishReason the vendor's finish reason, or null (Anthropic attempts carry none).
 */
internal class AttemptRecord(
    val provider: ProviderId,
    val number: Int,
    val kind: String,
    val httpStatus: Int?,
    val finishReason: String?,
    val toolCalls: Int,
) {
    override fun toString(): String =
        "AttemptRecord(provider=$provider, number=$number, kind=$kind, httpStatus=$httpStatus, " +
            "finishReason=$finishReason, toolCalls=$toolCalls)"
}

/**
 * What a command outcome amounts to for a verdict: codes and counts only. The reply is carried only as its length, and
 * a failure only as its stable code.
 *
 * @property kind `completed`, `failed` or `unhandled`.
 * @property partial true for a completed outcome that did some work and could not finish.
 * @property reason the failure reason's code or the last escalation code, or null.
 * @property terminalTool the name of the terminal tool that ended the run, or null.
 */
internal class OutcomeSummary(
    val kind: String,
    val partial: Boolean,
    val reason: String?,
    val executed: Int,
    val committed: Int,
    val held: Int,
    val replyLength: Int,
    val terminalTool: String?,
) {
    /** True for a completed outcome. */
    val isCompleted: Boolean get() = kind == COMPLETED

    /** The failure code a verdict reports when the outcome is not a completed one, or null when it is. */
    fun failureCode(): String? = if (isCompleted) null else "outcome_${reason ?: kind}"

    override fun toString(): String =
        "OutcomeSummary(kind=$kind, partial=$partial, reason=$reason, executed=$executed, committed=$committed, " +
            "held=$held, replyLength=$replyLength, terminalTool=$terminalTool)"

    companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val UNHANDLED = "unhandled"

        /**
         * The summary of [outcome]. The outcome set is closed, but failure and escalation reasons are an open
         * taxonomy, so they are reported by their `code` and never enumerated.
         */
        fun of(outcome: CommandOutcome): OutcomeSummary = when (outcome) {
            is CommandOutcome.Completed -> OutcomeSummary(
                kind = COMPLETED,
                partial = outcome.partial,
                reason = null,
                executed = outcome.executed.size,
                committed = outcome.commits.size,
                held = outcome.held.size,
                replyLength = outcome.reply?.length ?: 0,
                terminalTool = outcome.terminalCall?.toolName,
            )
            is CommandOutcome.Failed -> OutcomeSummary(
                kind = FAILED,
                partial = false,
                reason = outcome.reason.code,
                executed = outcome.executed.size,
                committed = outcome.commits.size,
                held = outcome.held.size,
                replyLength = 0,
                terminalTool = null,
            )
            is CommandOutcome.Unhandled -> OutcomeSummary(
                kind = UNHANDLED,
                partial = false,
                reason = outcome.lastReason?.code,
                executed = outcome.executed.size,
                committed = outcome.commits.size,
                held = outcome.held.size,
                replyLength = 0,
                terminalTool = null,
            )
        }
    }
}
