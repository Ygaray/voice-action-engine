package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind

/**
 * Something that happened during a run, delivered live to the [PipelineEventListener].
 *
 * Events carry ids, codes, counts and tool names only. They never hold transcript text, replies, tool arguments or
 * results, the app's context objects or an API key, so it is safe to log them.
 *
 * This is an open set: later versions may add events, so always keep an `else` branch when switching over it.
 */
public interface PipelineEvent {
    /** The id of the run the event belongs to. */
    public val runId: String

    /**
     * The run started.
     *
     * @property parentRunId the id of the earlier run this command answers, or null.
     */
    public class CommandStarted internal constructor(
        override val runId: String,
        public val parentRunId: String?,
    ) : PipelineEvent {
        override fun toString(): String = "CommandStarted(runId=$runId, parentRunId=$parentRunId)"
    }

    /**
     * A tier started.
     *
     * @property strategy the tier.
     */
    public class TierStarted internal constructor(
        override val runId: String,
        public val strategy: StrategyId,
    ) : PipelineEvent {
        override fun toString(): String = "TierStarted(runId=$runId, strategy=$strategy)"
    }

    /**
     * A tier did not run.
     *
     * @property strategy the tier.
     * @property code why it was skipped.
     */
    public class TierSkipped internal constructor(
        override val runId: String,
        public val strategy: StrategyId,
        public val code: TraceCode,
    ) : PipelineEvent {
        override fun toString(): String = "TierSkipped(runId=$runId, strategy=$strategy, code=$code)"
    }

    /**
     * A tier reported one model round trip.
     *
     * @property strategy the tier.
     * @property turn the round trip.
     */
    public class ProviderCall internal constructor(
        override val runId: String,
        public val strategy: StrategyId,
        public val turn: TurnRecord,
    ) : PipelineEvent {
        override fun toString(): String = "ProviderCall(runId=$runId, strategy=$strategy, turn=$turn)"
    }

    /**
     * An action was recorded: a commit, a hold, a preview or a rejected change.
     *
     * @property position the action's position in the run's executed list.
     * @property kind what happened to the change.
     * @property toolName the tool's name.
     * @property applied true when the app's apply ran.
     */
    public class ActionRecorded internal constructor(
        override val runId: String,
        public val position: Int,
        public val kind: ActionKind,
        public val toolName: String,
        public val applied: Boolean,
    ) : PipelineEvent {
        override fun toString(): String =
            "ActionRecorded(runId=$runId, position=$position, kind=$kind, toolName=$toolName, applied=$applied)"
    }

    /**
     * The engine recorded a code in the trace.
     *
     * @property code the code.
     */
    public class EngineCode internal constructor(
        override val runId: String,
        public val code: TraceCode,
    ) : PipelineEvent {
        override fun toString(): String = "EngineCode(runId=$runId, code=$code)"
    }

    /**
     * A provider response showed the prompt cache was not used when it should have been.
     *
     * @property strategy the tier.
     * @property provider the provider.
     * @property model the model id, or null.
     */
    public class CacheNotEngaged internal constructor(
        override val runId: String,
        public val strategy: StrategyId,
        public val provider: ProviderId,
        public val model: String?,
    ) : PipelineEvent {
        override fun toString(): String =
            "CacheNotEngaged(runId=$runId, strategy=$strategy, provider=$provider, model=$model)"
    }

    /**
     * A tier ended.
     *
     * @property attempt the trace entry for the tier.
     */
    public class TierFinished internal constructor(
        override val runId: String,
        public val attempt: TierAttempt,
    ) : PipelineEvent {
        override fun toString(): String = "TierFinished(runId=$runId, attempt=$attempt)"
    }

    /**
     * The run ended; this is the last event of a run.
     *
     * @property terminationCode the ending's stable code: `done`, `failed`, `exhausted` or `cancelled`.
     * @property executedCount how many actions the run recorded.
     * @property committedCount how many of them were committed.
     */
    public class RunClosed internal constructor(
        override val runId: String,
        public val terminationCode: String,
        public val executedCount: Int,
        public val committedCount: Int,
    ) : PipelineEvent {
        override fun toString(): String =
            "RunClosed(runId=$runId, terminationCode=$terminationCode, executedCount=$executedCount, " +
                "committedCount=$committedCount)"
    }
}
