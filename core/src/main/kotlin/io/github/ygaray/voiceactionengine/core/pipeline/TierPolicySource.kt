package io.github.ygaray.voiceactionengine.core.pipeline

/** Supplies the [TierPolicy] for each command, so an app can change limits (for example a setting) between commands. */
public fun interface TierPolicySource {
    /** Returns the policy to apply to the command about to run. */
    public suspend fun current(): TierPolicy

    /** Ways to create a source. */
    public companion object {
        /** A source that returns [policy] for every command. */
        public fun fixed(policy: TierPolicy): TierPolicySource = TierPolicySource { policy }
    }
}
