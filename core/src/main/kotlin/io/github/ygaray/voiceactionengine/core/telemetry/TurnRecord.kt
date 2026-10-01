package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.ProviderId

/**
 * One model round trip, as a strategy reports it through `CommandSession.recordTurn`.
 *
 * It carries ids, codes, counts and tool names only: never prompt or reply text, tool arguments or tool results.
 *
 * @property provider the provider that answered, or null when the tier has none.
 * @property model the model id the provider reported, or null.
 * @property stopReason the provider's stop reason code, or null.
 * @property toolNames the names of the tools the model called in this turn, in order.
 * @property usage the tokens this turn used, normalized to the four [Usage] buckets.
 * @property latencyMillis how long the round trip took.
 * @property fallbackFrom the provider this turn fell back from (on-device when the declared fallback answered), or
 * null.
 */
public class TurnRecord(
    public val provider: ProviderId?,
    public val model: String?,
    public val stopReason: String?,
    toolNames: List<String>,
    public val usage: Usage,
    public val latencyMillis: Long,
    public val fallbackFrom: ProviderId?,
) {
    /** A turn that did not fall back from another provider. */
    public constructor(
        provider: ProviderId?,
        model: String?,
        stopReason: String?,
        toolNames: List<String>,
        usage: Usage,
        latencyMillis: Long,
    ) : this(provider, model, stopReason, toolNames, usage, latencyMillis, null)

    /** A copy of the tool names. */
    public val toolNames: List<String> = toolNames.toList()

    override fun toString(): String =
        "TurnRecord(provider=$provider, model=$model, stopReason=$stopReason, toolNames=$toolNames, " +
            "usage=$usage, latencyMillis=$latencyMillis, fallbackFrom=$fallbackFrom)"
}
