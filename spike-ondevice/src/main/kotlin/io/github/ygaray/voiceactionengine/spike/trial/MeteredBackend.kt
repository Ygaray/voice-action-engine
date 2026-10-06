package io.github.ygaray.voiceactionengine.spike.trial

import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendConfig
import io.github.ygaray.voiceactionengine.spike.backend.BackendFailure
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import io.github.ygaray.voiceactionengine.spike.backend.BenchFacts
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend

/**
 * Decorates an [LlmBackend] and keeps the last generation's [BenchFacts] and failure code, so a trial can report the
 * runtime's own token counts and time to first token without the provider or the strategy knowing about measurement.
 * It changes nothing about the call and never logs. [toString] shows presence flags only.
 */
internal class MeteredBackend(private val delegate: LlmBackend) : LlmBackend {
    /** The facts of the last successful generation since [reset], or null. */
    var lastBench: BenchFacts? = null
        private set

    /** The stable code of the last failed generation since [reset], or null. */
    var lastFailureCode: String? = null
        private set

    /** Forgets the last call; a trial starts from here. */
    fun reset() {
        lastBench = null
        lastFailureCode = null
    }

    override suspend fun initialize(config: BackendConfig): InitOutcome = delegate.initialize(config)

    override suspend fun generate(request: BackendRequest): BackendAnswer {
        try {
            val answer = delegate.generate(request)
            lastBench = answer.bench
            lastFailureCode = null
            return answer
        } catch (e: BackendFailure) {
            lastBench = null
            lastFailureCode = e.code
            throw e
        }
    }

    override fun close() {
        delegate.close()
    }

    override fun toString(): String = "MeteredBackend(bench=${lastBench != null}, failure=${lastFailureCode != null})"
}
