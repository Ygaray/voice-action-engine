package io.github.ygaray.voiceactionengine.core.telemetry

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
) {
    override fun toString(): String =
        "CommandTrace(runId=$runId, parentRunId=$parentRunId, language=$language, " +
            "transcriptLength=$transcriptLength, attempts=${attempts.size}, codes=$codes, " +
            "startedAtMillis=$startedAtMillis, durationMillis=$durationMillis)"
}

/**
 * One tier's turn in a run.
 *
 * @property strategy the tier.
 * @property outcome how it ended: `completed`, `escalated`, `no_match`, `failed` or `escalation_suppressed`.
 * @property escalationReason why it handed up, when it did.
 * @property suppressedEscalation the escalation that was blocked because earlier work had been done, when one was.
 * @property failure why it failed, when it did.
 * @property latencyMillis how long the tier took, on the pipeline's clock.
 */
public class TierAttempt internal constructor(
    public val strategy: StrategyId,
    public val outcome: String,
    public val escalationReason: EscalationReason?,
    public val suppressedEscalation: EscalationReason?,
    public val failure: FailureReason?,
    public val latencyMillis: Long,
) {
    override fun toString(): String =
        "TierAttempt(strategy=$strategy, outcome=$outcome, escalationReason=$escalationReason, " +
            "suppressedEscalation=$suppressedEscalation, failure=$failure, latencyMillis=$latencyMillis)"
}
