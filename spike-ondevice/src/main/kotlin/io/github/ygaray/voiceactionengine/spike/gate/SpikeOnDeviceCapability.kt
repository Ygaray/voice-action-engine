package io.github.ygaray.voiceactionengine.spike.gate

import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome

private const val SUPPORTED_ABI = "arm64-v8a"

/**
 * The engine's on-device gate over three facts the app can read without running the model: the model file is present,
 * the device supports arm64-v8a, and the last engine load did not fail. It reports [OnDeviceAvailability.Available] only
 * when all three hold, otherwise [OnDeviceAvailability.Unavailable] with `model_missing`, `abi_unsupported` or
 * `init_failed` (checked in that order), so a tier that declares only `ON_DEVICE` fails loudly with no backend call.
 *
 * Each fact is a lambda so a test injects it and the app reads it fresh at every command.
 */
internal class SpikeOnDeviceCapability(
    private val modelPresent: () -> Boolean,
    private val supportedAbis: () -> List<String>,
    private val lastInit: () -> InitOutcome?,
) : OnDeviceCapability {
    override suspend fun availability(): OnDeviceAvailability = when {
        !modelPresent() -> OnDeviceAvailability.Unavailable(MODEL_MISSING)
        SUPPORTED_ABI !in supportedAbis() -> OnDeviceAvailability.Unavailable(ABI_UNSUPPORTED)
        lastInit()?.ok == false -> OnDeviceAvailability.Unavailable(INIT_FAILED)
        else -> OnDeviceAvailability.Available()
    }

    override fun toString(): String = "SpikeOnDeviceCapability"

    companion object {
        const val MODEL_MISSING = "model_missing"
        const val ABI_UNSUPPORTED = "abi_unsupported"
        const val INIT_FAILED = "init_failed"
    }
}
