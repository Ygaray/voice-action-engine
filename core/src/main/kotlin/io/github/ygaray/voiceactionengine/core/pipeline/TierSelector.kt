package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities

private const val CUSTOM_PICKER_ID = "start_tier_picker"

/**
 * Decides which tier of the ladder a command starts at. Use [Linear] to start at the first tier that may run, [Fixed]
 * to start at one named tier and climb from there, or [Custom] to let your own [StartTierPicker] choose among the
 * tiers that call a model. The tiers that need no model at the head of the ladder always run first, whatever the
 * selector.
 *
 * This class is deliberately not sealed: new selectors can be added in later versions, so keep an `else` branch when
 * you switch on one.
 */
public abstract class TierSelector internal constructor() {
    /** The index in [eligible] to start at, or null when no eligible tier can be the start. */
    internal abstract fun startIndex(eligible: List<StrategyId>): Int?

    /**
     * The picking hook for selectors that ask a picker where the model walk starts; null keeps the plain start given
     * by [startIndex].
     */
    internal open val picking: PickingSpec? get() = null

    /** Starts at the first tier that may run. This is the default. */
    public object Linear : TierSelector() {
        override fun startIndex(eligible: List<StrategyId>): Int? = if (eligible.isEmpty()) null else 0

        override fun toString(): String = "Linear"
    }

    /** Starts at [tier] and climbs upward from it; the tiers below it never run. */
    public class Fixed(public val tier: StrategyId) : TierSelector() {
        override fun startIndex(eligible: List<StrategyId>): Int? = eligible.indexOf(tier).takeIf { it >= 0 }

        override fun toString(): String = "Fixed($tier)"
    }

    /**
     * Asks [picker] which model tier to start at, after the tiers that need no model have had their turn. The picker
     * sees only the eligible tiers that call a model.
     *
     * Use `Custom(picker)` for the defaults, or `Custom(picker) { id = ...; capabilities = ... }` to name the picker and
     * the providers it may call.
     *
     * @property picker the app's picker.
     */
    public class Custom internal constructor(
        public val picker: StartTierPicker,
        settings: Builder,
    ) : TierSelector() {
        /** A selector with the default picker id and any provider allowed. */
        public constructor(picker: StartTierPicker) : this(picker, Builder())

        /** The id the picker's own model call is routed and recorded under. */
        public val id: StrategyId = settings.id

        override val picking: PickingSpec = PickingSpec(picker, id, settings.capabilities, false)

        override fun startIndex(eligible: List<StrategyId>): Int? = if (eligible.isEmpty()) null else 0

        override fun toString(): String = "Custom(id=$id)"

        /** Mutable collector for a [Custom] selector; every field starts at its default. */
        public class Builder internal constructor() {
            /**
             * The picker's own id. The engine asks your selection source for it, so map it to the model the picker
             * should use. It must differ from every tier id.
             */
            public var id: StrategyId = StrategyId(CUSTOM_PICKER_ID)

            /**
             * The providers the picker may call. A picker whose providers the command's policy forbids is not called,
             * and the walk starts Linear.
             */
            public var capabilities: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER
        }

        /** Entry points for creating selectors. */
        public companion object {
            /** Builds a selector for [picker] with the settings given in [block]. */
            public operator fun invoke(picker: StartTierPicker, block: Builder.() -> Unit): Custom =
                Custom(picker, Builder().apply(block))
        }
    }
}
