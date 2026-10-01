package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest

/**
 * A model a strategy can call, resolved once for one command and one tier and frozen for the rest of that command.
 *
 * The strategy never sees the API key or the app's selection: the engine resolved both before handing this out, sends
 * the key only to the provider it belongs to, and records every call in the run's trace.
 *
 * [complete] never throws, except for the caller's own cancellation or a JVM `Error`. When the engine could not bind a
 * model (nothing selected, a key missing, the provider not allowed, on-device inference not available) this handle is
 * refused: [refusal] says why, [provider], [model] and [capabilities] are null, and every [complete] answers
 * `ModelResult.Failure(refusal)` without any provider call. A strategy should turn a refusal into a failed outcome. In
 * particular an on-device refusal ends the command loudly instead of escalating, so the command never climbs to a tier
 * the app did not declare as its fallback.
 */
public abstract class BoundModel internal constructor() {
    /** The provider that will answer, or null when the handle is refused. */
    public abstract val provider: ProviderId?

    /** The model id that will be called, or null when the handle is refused. */
    public abstract val model: String?

    /** What the bound model can do after the app's overrides, or null when the handle is refused. */
    public abstract val capabilities: ModelCapabilities?

    /** The provider this handle replaced because the app's declared fallback was used, or null. */
    public abstract val fallbackFrom: ProviderId?

    /** Why no model was bound, or null when the handle is usable. */
    public abstract val refusal: FailureReason?

    /**
     * Sends [request] to the bound model and returns the answer or a typed failure, after checking that the model can
     * take the request (a request that carries tools is refused when the model does not support tools).
     */
    public abstract suspend fun complete(request: ModelRequest): ModelResult

    /** Prints the provider and model ids and the refusal code only; never a key, prompt, argument or result. */
    final override fun toString(): String =
        "BoundModel(provider=$provider, model=$model, fallbackFrom=$fallbackFrom, refusal=${refusal?.code})"
}

/** Everything the router resolved for a usable handle; held privately by [RoutedModel]. */
internal class Binding(
    val provider: AiProvider,
    val model: String,
    val credential: Credential?,
    val capabilities: ModelCapabilities,
    val fallbackFrom: ProviderId?,
)

/** A handle that answers every call with the reason it could not be bound, and calls nothing. */
internal class RefusedModel(override val refusal: FailureReason) : BoundModel() {
    override val provider: ProviderId? get() = null
    override val model: String? get() = null
    override val capabilities: ModelCapabilities? get() = null
    override val fallbackFrom: ProviderId? get() = null

    override suspend fun complete(request: ModelRequest): ModelResult = ModelResult.Failure(refusal)
}

/**
 * A usable handle. It holds no mutable state, so concurrent [complete] calls are independent; each records its own turn
 * through [recorder] on [clock] (the same path a strategy's own `recordTurn` takes).
 */
internal class RoutedModel(
    private val binding: Binding,
    private val strategy: StrategyId,
    private val recorder: RunRecorder,
    private val clock: () -> Long,
) : BoundModel() {
    override val provider: ProviderId? get() = binding.provider.id
    override val model: String? get() = binding.model
    override val capabilities: ModelCapabilities? get() = binding.capabilities
    override val fallbackFrom: ProviderId? get() = binding.fallbackFrom
    override val refusal: FailureReason? get() = null

    override suspend fun complete(request: ModelRequest): ModelResult {
        if (request.tools.isNotEmpty() && !binding.capabilities.supportsTools) {
            recorder.recordCode(TraceCode.CAPABILITY_REFUSED)
            return ModelResult.Failure(FailureReason.ModelUnsupported())
        }
        val call = ProviderCall(binding.model, request, binding.credential, binding.capabilities)
        val started = clock()
        val result = guarded(onFault = { fault ->
            recorder.recordCode(TraceCode.PROVIDER_ERROR)
            val reason = if (fault.timeoutLeak) {
                FailureReason.Timeout()
            } else {
                FailureReason.Unexpected(fault.errorClass)
            }
            ModelResult.Failure(reason)
        }) { binding.provider.complete(call) }
        recorder.turnRecorded(strategy, turnOf(result, clock() - started))
        return result
    }

    private fun turnOf(result: ModelResult, latencyMillis: Long): TurnRecord {
        val response = (result as? ModelResult.Success)?.response
        return TurnRecord(
            provider = binding.provider.id,
            model = binding.model,
            stopReason = response?.stopReason?.value,
            toolNames = response?.message?.toolCalls?.map { it.name }.orEmpty(),
            usage = response?.usage ?: Usage.ZERO,
            latencyMillis = latencyMillis,
            fallbackFrom = binding.fallbackFrom,
        )
    }
}
