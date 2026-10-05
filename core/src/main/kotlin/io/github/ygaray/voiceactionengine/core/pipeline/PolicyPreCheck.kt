package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

private const val NOT_FOUND = -1
private const val OFFLINE_CAUSE = "offline_unavailable"
private const val ON_DEVICE_CAUSE = "on_device_unavailable"

/** The tiers one command may run, after the policy has been applied, and what the walk needs to enforce the rest. */
internal class Ladder(
    val tiers: List<CommandStrategy>,
    val selector: TierSelector,
    private val onDeviceAvailable: Boolean,
    val refusal: FailureReason?,
    val cappedByPolicy: Boolean,
) {
    /** True when [tier] can only use on-device inference and that is not available, so it must not run. */
    fun blockedOnDevice(tier: CommandStrategy): Boolean = tier.capabilities.onDeviceOnly && !onDeviceAvailable

    /** The failure for reaching a tier that [blockedOnDevice]. */
    fun onDeviceFailure(): FailureReason = FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, ON_DEVICE_CAUSE)
}

/**
 * Applies the app's [TierPolicy] to the ladder once per command, before any tier runs, using only the tiers' static
 * capability declarations. [onDeviceAvailability] is where on-device inference reports whether it can run; it
 * defaults to unavailable until an on-device tier is plugged in.
 */
internal class PolicyPreCheck(
    val strategies: List<CommandStrategy>,
    private val selector: TierSelector,
    private val onDeviceAvailability: suspend () -> Boolean,
) {
    /** Cuts the ladder to the tiers [policy] allows, recording every tier it drops. */
    suspend fun check(policy: TierPolicy, recorder: RunRecorder): Ladder {
        val onDevice = strategies.any { ProviderId.ON_DEVICE in it.capabilities.providers } &&
            guarded(onFault = { recorder.recordCode(TraceCode.ON_DEVICE_PROBE_ERROR); false }) {
                onDeviceAvailability()
            }
        val capIndex = policy.maxTier?.let { id -> strategies.indexOfFirst { it.id == id } }
        if (capIndex == NOT_FOUND) {
            recorder.recordCode(TraceCode.MAX_TIER_UNKNOWN)
            return refused(FailureReason.NoEligibleTier(), onDevice)
        }
        val within = if (capIndex == null) strategies else strategies.take(capIndex + 1)
        strategies.drop(within.size).forEach { recorder.tierSkipped(it.id, TraceCode.TIER_SKIPPED_POLICY) }
        val eligible = within.filter { tier ->
            val allowed = permits(tier, policy, onDevice)
            if (!allowed) recorder.tierSkipped(tier.id, TraceCode.TIER_SKIPPED_POLICY)
            allowed
        }
        return if (eligible.isNotEmpty()) {
            Ladder(eligible, selector, onDevice, null, cappedByPolicy = eligible.size < strategies.size)
        } else {
            refused(nothingMayRun(policy, recorder), onDevice)
        }
    }

    /** The failure for a ladder with no eligible tier; offline-only gets its own loud, specific reason. */
    private suspend fun nothingMayRun(policy: TierPolicy, recorder: RunRecorder): FailureReason =
        if (policy.offlineOnly) {
            recorder.recordCode(TraceCode.OFFLINE_UNAVAILABLE)
            FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, OFFLINE_CAUSE)
        } else {
            FailureReason.NoEligibleTier()
        }

    private fun refused(reason: FailureReason, onDevice: Boolean): Ladder =
        Ladder(emptyList(), selector, onDevice, reason, cappedByPolicy = false)

    private fun permits(tier: CommandStrategy, policy: TierPolicy, onDevice: Boolean): Boolean {
        val capabilities = tier.capabilities
        val allowed = policy.allowedProviders
        val providers = capabilities.providers
        if (allowed != null && providers.isNotEmpty() && providers.none { it in allowed }) return false
        // Offline means zero network: only a tier with no provider, or one that can use nothing but a ready on-device
        // model, may run.
        return !policy.offlineOnly || providers.isEmpty() || (capabilities.onDeviceOnly && onDevice)
    }
}
