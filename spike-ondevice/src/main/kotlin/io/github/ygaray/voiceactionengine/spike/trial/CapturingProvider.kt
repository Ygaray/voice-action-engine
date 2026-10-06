package io.github.ygaray.voiceactionengine.spike.trial

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason

/**
 * Decorates an [AiProvider] and keeps what the model answered: the first tool call, the stop reason and the failure code.
 * The strategy sees only the first call anyway, and a decline (no call) never reaches the pipeline outcome as such, so
 * scoring reads the answer here. Everything else is delegated unchanged. [toString] never shows arguments.
 */
internal class CapturingProvider(private val delegate: AiProvider) : AiProvider {
    override val id: ProviderId get() = delegate.id

    override val requiresCredential: Boolean get() = delegate.requiresCredential

    /** The first tool call of the last answer since [reset], or null (a decline or a failure). */
    var firstCall: AssistantPart.ToolCall? = null
        private set

    /** The stop reason of the last successful answer since [reset], or null. */
    var stopReason: StopReason? = null
        private set

    /** The stable code of the last failure since [reset], or null. */
    var failureCode: String? = null
        private set

    /** Forgets the last answer; a trial starts from here. */
    fun reset() {
        firstCall = null
        stopReason = null
        failureCode = null
    }

    override fun capabilities(model: String): ModelCapabilities = delegate.capabilities(model)

    override suspend fun complete(call: ProviderRequest): ModelResult {
        val result = delegate.complete(call)
        when (result) {
            is ModelResult.Success -> {
                firstCall = result.response.message.toolCalls.firstOrNull()
                stopReason = result.response.stopReason
                failureCode = null
            }
            is ModelResult.Failure -> {
                firstCall = null
                stopReason = null
                failureCode = codeOf(result.reason)
            }
        }
        return result
    }

    override fun toString(): String =
        "CapturingProvider(call=${firstCall?.name}, stop=$stopReason, failureCode=$failureCode)"

    private fun codeOf(reason: FailureReason): String =
        (reason as? FailureReason.ProviderUnavailable)?.cause ?: reason.code
}
