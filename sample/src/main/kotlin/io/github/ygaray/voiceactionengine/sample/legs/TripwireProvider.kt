package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import java.util.concurrent.atomic.AtomicInteger

/** The id of the tripwire. It is not a real provider: no key, no network, no budget. */
internal val TRIPWIRE_PROVIDER = ProviderId("tripwire")

/** The model name the tripwire is selected under. */
internal const val TRIPWIRE_MODEL = "tripwire-model"

/** The stable failure code the tripwire answers with. */
internal const val TRIPWIRE_CODE = "tripwire_called"

/**
 * A provider that exists to be NEVER called. It counts every call and answers a typed failure, so a leg that must make
 * zero provider calls can prove it: if a policy leak ever let a tier reach a provider, the only one it could reach is
 * this one, the count goes above zero, and the leg fails instead of spending anything.
 */
internal class TripwireProvider : AiProvider {
    private val counter = AtomicInteger()

    override val id: ProviderId get() = TRIPWIRE_PROVIDER

    override val requiresCredential: Boolean get() = false

    /** How many times [complete] was called. */
    val calls: Int get() = counter.get()

    override fun capabilities(model: String): ModelCapabilities = ModelCapabilities {
        supportsTools = true
        supportsForcedToolChoice = true
    }

    override suspend fun complete(call: ProviderRequest): ModelResult {
        counter.incrementAndGet()
        return ModelResult.Failure(FailureReason.Other(TRIPWIRE_CODE))
    }

    /** The class name and the count only. */
    override fun toString(): String = "TripwireProvider(calls=$calls)"
}
