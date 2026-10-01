package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse

/**
 * What a provider answers for one round trip: a [Success] or a [Failure]. A provider returns a [Failure] for every
 * expected failure instead of throwing. The set is open (later versions may add results), so keep an `else` branch
 * when switching on one.
 */
public abstract class ModelResult internal constructor() {
    /**
     * The model answered.
     *
     * @property response the neutral response.
     */
    public class Success(public val response: ModelResponse) : ModelResult() {
        /** Prints the response summary, which carries counts and names but never text or arguments. */
        override fun toString(): String = "ModelResult.Success(response=$response)"
    }

    /**
     * The call failed in a way the provider expected: a status, a provider error type, a timeout.
     *
     * @property reason why the call failed.
     * @property details transport facts such as the status and request id, or null when there are none. Never a
     * response body.
     */
    public class Failure(
        public val reason: FailureReason,
        public val details: FailureDetails?,
    ) : ModelResult() {
        /** A failure with no transport details. */
        public constructor(reason: FailureReason) : this(reason, null)

        /** Prints the reason and the transport facts only. */
        override fun toString(): String = "ModelResult.Failure(reason=$reason, details=$details)"
    }
}
