package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.EngineFault
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

private const val NOT_DECLARED_CAUSE = "provider_not_declared"
private const val POLICY_FORBIDS_CAUSE = "policy_forbids_provider"
private const val ON_DEVICE_UNAVAILABLE_CAUSE = "on_device_unavailable"

/** One resolution step: carry on with a value, or stop with the trace code and the typed reason to refuse with. */
private sealed interface Step<out T> {
    class Go<out T>(val value: T) : Step<T>

    class Stop(val code: TraceCode, val reason: FailureReason) : Step<Nothing>
}

private inline fun <A, B> Step<A>.then(next: (A) -> Step<B>): Step<B> = when (this) {
    is Step.Go -> next(value)
    is Step.Stop -> this
}

private fun unexpectedOrTimeout(fault: EngineFault): FailureReason =
    if (fault.timeoutLeak) FailureReason.Timeout() else FailureReason.Unexpected(fault.errorClass)

private fun onDeviceStop(code: TraceCode): Step.Stop =
    Step.Stop(code, FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, ON_DEVICE_UNAVAILABLE_CAUSE))

private fun notSelected(): Step.Stop = Step.Stop(TraceCode.PROVIDER_NOT_SELECTED, FailureReason.NotConfigured(null))

private fun gate(provider: ProviderId, declared: Set<ProviderId>, policy: TierPolicy): Step<Unit> =
    providerGate(provider, declared, policy)
        ?.let { Step.Stop(TraceCode.PROVIDER_NOT_ALLOWED, it) }
        ?: Step.Go(Unit)

private fun present(id: ProviderId, credential: Credential): Step<Credential?> =
    if (credential.provider == id) {
        Step.Go(credential)
    } else {
        Step.Stop(TraceCode.CREDENTIAL_MISMATCH, FailureReason.NotConfigured(id))
    }

private fun missing(id: ProviderId): Step.Stop =
    Step.Stop(TraceCode.CREDENTIAL_MISSING, FailureReason.NotConfigured(id))

/**
 * Why [provider] may not serve a tier that declares [declared] under [policy], or null when it may: the tier must
 * declare the provider, the policy's allowed set must include it, and an offline-only policy admits on-device only.
 * Every selected provider, and any fallback, passes through here so the static policy still holds for the provider a
 * run-time selection actually picked.
 */
internal fun providerGate(provider: ProviderId, declared: Set<ProviderId>, policy: TierPolicy): FailureReason? {
    val allowed = policy.allowedProviders
    val cause = when {
        provider !in declared -> NOT_DECLARED_CAUSE
        allowed != null && provider !in allowed -> POLICY_FORBIDS_CAUSE
        policy.offlineOnly && provider != ProviderId.ON_DEVICE -> POLICY_FORBIDS_CAUSE
        else -> null
    }
    return cause?.let { FailureReason.ProviderUnavailable(provider, it) }
}

/**
 * Resolves the model a tier uses from the app's seams, in a fixed order, refusing with a typed reason and exactly one
 * trace code before any provider call: selection, the tier and policy gate, the on-device probe, provider
 * registration, the credential for that provider only, then the model's capabilities.
 *
 * @param providers the registered providers by id.
 * @param selection the app's selection source, or null when none is set.
 * @param credentials the app's credential source, or null when none is set.
 * @param table the capability lookup applied to the bound model.
 * @param clock milliseconds on the pipeline's monotonic clock, used for each call's latency.
 * @param onDeviceProbe whether on-device inference is ready right now.
 */
internal class ModelRouter(
    private val providers: Map<ProviderId, AiProvider>,
    private val selection: ProviderSelectionSource?,
    private val credentials: CredentialSource?,
    private val table: ModelCapabilityTable,
    private val clock: () -> Long,
    private val onDeviceProbe: suspend () -> Boolean,
) {
    /** Binds the model [strategy] will use; a refused binding comes back as a refused handle, never an exception. */
    suspend fun bind(
        strategy: StrategyId,
        declared: Set<ProviderId>,
        policy: TierPolicy,
        recorder: RunRecorder,
    ): BoundModel = when (val step = resolve(strategy, declared, policy, recorder)) {
        is Step.Go -> RoutedModel(step.value, strategy, recorder, clock)
        is Step.Stop -> {
            recorder.recordCode(step.code)
            RefusedModel(step.reason)
        }
    }

    private suspend fun resolve(
        strategy: StrategyId,
        declared: Set<ProviderId>,
        policy: TierPolicy,
        recorder: RunRecorder,
    ): Step<Binding> = select(strategy).then { chosen ->
        gate(chosen.provider, declared, policy).then {
            if (usable(chosen.provider, recorder)) {
                bound(chosen, null)
            } else {
                viaFallback(chosen, declared, policy, recorder)
            }
        }
    }

    /** Registration, the credential for that provider only, then the model's capabilities. */
    private suspend fun bound(chosen: ProviderSelection, fallbackFrom: ProviderId?): Step<Binding> =
        registered(chosen.provider).then { provider ->
            credential(provider).then { key ->
                capabilities(provider, chosen.model).then { caps ->
                    Step.Go(Binding(provider, chosen.model, key, caps, fallbackFrom))
                }
            }
        }

    private suspend fun select(strategy: StrategyId): Step<ProviderSelection> {
        val source = selection ?: return notSelected()
        val answer: Step<ProviderSelection?> = guarded(
            onFault = { fault -> Step.Stop(TraceCode.SELECTION_SOURCE_ERROR, unexpectedOrTimeout(fault)) },
        ) { Step.Go(source.select(SelectionRequest(strategy))) }
        return answer.then { chosen -> chosen?.let { Step.Go(it) } ?: notSelected() }
    }

    /** Whether [provider] can be called now; on-device needs a ready probe and a registered provider. */
    private suspend fun usable(provider: ProviderId, recorder: RunRecorder): Boolean {
        if (provider != ProviderId.ON_DEVICE) return true
        val ready = guarded(onFault = { recorder.recordCode(TraceCode.ON_DEVICE_PROBE_ERROR); false }) {
            onDeviceProbe()
        }
        return ready && ProviderId.ON_DEVICE in providers
    }

    /**
     * The only way past an unusable on-device selection is the fallback the app declared, and only when the tier and
     * the policy permit its provider, exactly as for any selection. Otherwise the command fails loudly, before any key
     * is asked for.
     */
    private suspend fun viaFallback(
        chosen: ProviderSelection,
        declared: Set<ProviderId>,
        policy: TierPolicy,
        recorder: RunRecorder,
    ): Step<Binding> {
        val fallback = chosen.fallback
        return when {
            fallback == null -> onDeviceStop(TraceCode.ON_DEVICE_UNAVAILABLE)
            providerGate(fallback.provider, declared, policy) != null -> {
                recorder.recordCode(TraceCode.ON_DEVICE_UNAVAILABLE)
                onDeviceStop(TraceCode.FALLBACK_REFUSED)
            }
            else -> {
                recorder.recordCode(TraceCode.PROVIDER_FALLBACK)
                bound(fallback, ProviderId.ON_DEVICE)
            }
        }
    }

    private fun registered(provider: ProviderId): Step<AiProvider> =
        providers[provider]?.let { Step.Go(it) }
            ?: Step.Stop(TraceCode.PROVIDER_NOT_REGISTERED, FailureReason.NotConfigured(provider))

    /** The key for [provider] alone, null for a provider that needs none; the source is never asked otherwise. */
    private suspend fun credential(provider: AiProvider): Step<Credential?> =
        if (provider.requiresCredential) lookUp(provider.id) else Step.Go(null)

    private suspend fun lookUp(id: ProviderId): Step<Credential?> {
        val source = credentials ?: return missing(id)
        // A throwing source is an engine fault, not an unreadable key: only `Unreadable` asks the user to re-enter it.
        val lookup: Step<CredentialLookup> = guarded(
            onFault = { fault -> Step.Stop(TraceCode.CREDENTIAL_SOURCE_ERROR, unexpectedOrTimeout(fault)) },
        ) { Step.Go(source.credential(id)) }
        return lookup.then { found ->
            when (found) {
                is CredentialLookup.Present -> present(id, found.credential)
                is CredentialLookup.Unreadable -> Step.Stop(
                    TraceCode.CREDENTIAL_UNREADABLE,
                    FailureReason.CredentialUnreadable(id, found.cause),
                )
                else -> missing(id)
            }
        }
    }

    private suspend fun capabilities(provider: AiProvider, model: String): Step<ModelCapabilities> =
        guarded(
            onFault = { fault ->
                Step.Stop(TraceCode.CAPABILITY_LOOKUP_ERROR, unexpectedOrTimeout(fault))
            },
        ) { Step.Go(table.lookup(provider.id, model)) }
}
