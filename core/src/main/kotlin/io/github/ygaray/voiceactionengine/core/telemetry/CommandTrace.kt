package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason

/**
 * What one run did, in ids, codes and counts only. It never holds transcript text, replies or tool arguments.
 *
 * @property runId the command's id.
 * @property parentRunId the id of the earlier run this command answers, or null.
 * @property language the input's language, or null.
 * @property transcriptLength how many characters the transcript had.
 * @property attempts one entry per tier that ran, in order.
 * @property codes the engine codes recorded during the run, in order.
 * @property selection the start-tier pick, when a Custom or Router selector asked its picker, else null. Its turns are
 * not in [attempts], which still means one entry per tier that ran.
 * @property usage the tokens used across all attempts' reported turns and the selection's, summed bucket by bucket.
 * @property startedAtMillis when the run started, on the pipeline's clock.
 * @property durationMillis how long the run had been going when this trace was taken.
 */
public class CommandTrace internal constructor(
    public val runId: String,
    public val parentRunId: String?,
    public val language: String?,
    public val transcriptLength: Int,
    public val startedAtMillis: Long,
    public val durationMillis: Long,
    public val attempts: List<TierAttempt> = emptyList(),
    public val codes: List<TraceCode> = emptyList(),
    public val selection: StartTierSelection? = null,
) {
    /** The tokens used across all attempts' reported turns and the start-tier selection's. */
    public val usage: Usage =
        attempts.fold(Usage.ZERO) { sum, attempt -> sum + attempt.usage } + (selection?.usage ?: Usage.ZERO)

    override fun toString(): String =
        "CommandTrace(runId=$runId, parentRunId=$parentRunId, language=$language, " +
            "transcriptLength=$transcriptLength, attempts=${attempts.size}, codes=$codes, usage=$usage, " +
            "startedAtMillis=$startedAtMillis, durationMillis=$durationMillis" +
            (selection?.let { ", selection=$it" } ?: "") + ")"
}

/**
 * One tier's turn in a run.
 *
 * @property strategy the tier.
 * @property outcome how it ended: `completed`, `escalated`, `no_match`, `failed` or `escalation_suppressed`; or,
 * when the tier never returned, `timeout` (the engine deadline) or `cancelled` (the caller). An open set: keep an
 * `else` branch.
 * @property escalationReason why it handed up, when it did.
 * @property suppressedEscalation the escalation that was blocked because earlier work had been done, when one was.
 * @property failure why it failed, when it did.
 * @property latencyMillis how long the tier took, on the pipeline's clock.
 * @property carryIn true when the tier started with the previous tier's carry (the opaque object an escalation
 * handed up); only its presence is reported, never its content.
 * @property turns the model round trips the tier reported, in order.
 */
public class TierAttempt internal constructor(
    public val strategy: StrategyId,
    public val outcome: String,
    public val escalationReason: EscalationReason?,
    public val suppressedEscalation: EscalationReason?,
    public val failure: FailureReason?,
    public val latencyMillis: Long,
    public val carryIn: Boolean,
    turns: List<TurnRecord> = emptyList(),
) {
    /** A copy of the round trips the tier reported. */
    public val turns: List<TurnRecord> = turns.toList()

    /** The provider of the tier's last reported turn, or null when it reported none. */
    public val provider: ProviderId? get() = this.turns.lastOrNull()?.provider

    /** The model of the tier's last reported turn, or null when it reported none. */
    public val model: String? get() = this.turns.lastOrNull()?.model

    /** The provider the tier's last reported turn fell back from, or null when it reported none or had no fallback. */
    public val fallbackFrom: ProviderId? get() = this.turns.lastOrNull()?.fallbackFrom

    /** The tokens used by the tier's turns, summed bucket by bucket. */
    public val usage: Usage = this.turns.fold(Usage.ZERO) { sum, turn -> sum + turn.usage }

    override fun toString(): String =
        "TierAttempt(strategy=$strategy, outcome=$outcome, escalationReason=$escalationReason, " +
            "suppressedEscalation=$suppressedEscalation, failure=$failure, latencyMillis=$latencyMillis, " +
            "carryIn=$carryIn, " +
            "turns=${this.turns.size}, provider=$provider, model=$model, fallbackFrom=$fallbackFrom, usage=$usage)"
}
