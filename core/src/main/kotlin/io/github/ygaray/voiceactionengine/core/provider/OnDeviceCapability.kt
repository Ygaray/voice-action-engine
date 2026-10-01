package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.failure.isStableCode

/**
 * Tells the engine whether on-device inference can be used right now. The app implements it over whatever the device
 * offers.
 *
 * Version 1.0 ships no on-device implementation, so the engine's default reports
 * [OnDeviceAvailability.Unavailable] with the code `not_implemented`. The same gate is consulted by the policy
 * pre-check and by the router, so there is one answer to "can the device run it" and no second path.
 */
public fun interface OnDeviceCapability {
    /** Reports the device's current on-device status. */
    public suspend fun availability(): OnDeviceAvailability
}

/**
 * The on-device status, mirroring the statuses of Google's on-device generative runtime. Only [Available] is usable in
 * version 1.0. The set is open by design: later engine versions and adapters can add leaves, so a consumer must
 * always keep an `else` branch when it switches on a status.
 */
public abstract class OnDeviceAvailability internal constructor() {
    /** The model is on the device and ready to run. */
    public class Available : OnDeviceAvailability() {
        override fun equals(other: Any?): Boolean = other is Available
        override fun hashCode(): Int = Available::class.java.name.hashCode()
        override fun toString(): String = "OnDeviceAvailability.Available"
    }

    /** The device supports the model but it has not been downloaded yet. */
    public class Downloadable : OnDeviceAvailability() {
        override fun equals(other: Any?): Boolean = other is Downloadable
        override fun hashCode(): Int = Downloadable::class.java.name.hashCode()
        override fun toString(): String = "OnDeviceAvailability.Downloadable"
    }

    /** The model is being downloaded right now. */
    public class Downloading : OnDeviceAvailability() {
        override fun equals(other: Any?): Boolean = other is Downloading
        override fun hashCode(): Int = Downloading::class.java.name.hashCode()
        override fun toString(): String = "OnDeviceAvailability.Downloading"
    }

    /**
     * On-device inference cannot be used on this device or in this build.
     *
     * @property code a stable lower snake case code (`[a-z0-9_]+`, never a message) saying why; anything else is
     * refused with [IllegalArgumentException].
     */
    public class Unavailable(public val code: String) : OnDeviceAvailability() {
        init {
            require(isStableCode(code)) { "Unavailable code must be a stable code" }
        }

        override fun equals(other: Any?): Boolean = other is Unavailable && code == other.code
        override fun hashCode(): Int = code.hashCode()
        override fun toString(): String = "OnDeviceAvailability.Unavailable(code=$code)"
    }
}
