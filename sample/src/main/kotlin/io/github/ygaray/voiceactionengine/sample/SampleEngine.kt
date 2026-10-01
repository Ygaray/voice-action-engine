package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener

/** Gate-1 runs the confirm gate in canned-admit mode: every proposed change is applied. */
private val CANNED_ADMIT = PreApplyGate { GateDecision.Admit() }

/**
 * The sample's one composition root. Host tests (with fake providers) and the app (with real providers) both build their
 * pipelines here, so the tests exercise the same wiring the device runs.
 *
 * It registers every given provider, always admits (canned admit), and reports to the given sink and listener.
 * Credentials never leave the [CredentialSource]; this class holds it but never reads a key.
 */
internal class SampleEngine(
    private val providers: List<AiProvider>,
    private val credentials: CredentialSource,
    private val sink: CommitSink,
    private val listener: PipelineEventListener?,
) {
    /**
     * A pipeline with one tier, [tier], every registered provider, the canned-admit gate, the given [selection] and
     * [policy]. [configure] runs last, so a caller may override anything.
     */
    fun pipeline(
        tier: CommandStrategy,
        selection: ProviderSelection,
        policy: TierPolicy,
        configure: PipelineBuilder.() -> Unit = {},
    ): CommandPipeline {
        val creds = credentials
        val events = listener
        val out = sink
        val registered = providers
        return commandPipeline {
            tier(tier)
            registered.forEach { provider(it) }
            gate = CANNED_ADMIT
            commitSink = out
            providerSelection = ProviderSelectionSource { selection }
            credentials = creds
            this.policy = TierPolicySource.fixed(policy)
            listener = events
            configure()
        }
    }

    /** Provider ids only: never the credential source or anything it holds. */
    override fun toString(): String = "SampleEngine(providers=${providers.map { it.id.value }})"
}
