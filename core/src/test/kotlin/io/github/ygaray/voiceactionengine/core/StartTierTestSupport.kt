package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.provider.SelectionRequest
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** A pipeline over [tiers] for the start-tier tests; [selector] and [onDevice] are set only when non-null. */
internal fun startTierPipeline(
    tiers: List<CommandStrategy>,
    selector: TierSelector?,
    fake: FakeAiProvider,
    listener: RecordingEventListener = RecordingEventListener(),
    selection: ProviderSelectionSource =
        ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model")),
    policy: TierPolicy = TierPolicy.DEFAULT,
    onDevice: OnDeviceCapability? = null,
    gate: ScriptedGate = ScriptedGate.admitAll(),
    sink: RecordingCommitSink = RecordingCommitSink(),
): CommandPipeline {
    val ids = AtomicInteger()
    return commandPipeline {
        tiers.forEach { tier(it) }
        provider(fake)
        providerSelection = selection
        credentials = testKey()
        this.policy = TierPolicySource.fixed(policy)
        if (selector != null) this.selector = selector
        if (onDevice != null) this.onDevice = onDevice
        this.gate = gate
        commitSink = sink
        this.listener = listener
        runIds = { "run-${ids.incrementAndGet()}" }
    }
}

/** A tier that makes no model call and plays one scripted outcome. */
internal fun zeroCallTier(id: String, outcome: StrategyOutcome): ScriptedStrategy =
    ScriptedStrategy(StrategyId(id), StrategyCapabilities.NO_PROVIDER, { _, _ -> outcome })

/** A tier that may call any provider and plays one scripted step. */
internal fun llmTier(id: String, step: StrategyStep): ScriptedStrategy =
    ScriptedStrategy(StrategyId(id), StrategyCapabilities.ANY_PROVIDER, step)

/** The strategy ids named by [ids], in order. */
internal fun idsOf(vararg ids: String): List<StrategyId> = ids.map { StrategyId(it) }

/** The request a picker test sends through the picker's model handle. */
internal fun pickTurnRequest(): ModelRequest = ModelRequest("pick", listOf(UserMessage("pick")), 8)

/**
 * A selection source that answers by strategy id: a key of [byId] answers its entry (null means "not configured"),
 * any other id gets [fallback]. Every asked id is kept in [requested].
 */
internal class MappedSelection(
    private val byId: Map<String, ProviderSelection?>,
    private val fallback: ProviderSelection?,
) : ProviderSelectionSource {
    private val asked = CopyOnWriteArrayList<StrategyId>()

    /** Every strategy id asked about so far, in arrival order. */
    val requested: List<StrategyId>
        get() = asked.toList()

    override suspend fun select(request: SelectionRequest): ProviderSelection? {
        asked.add(request.strategy)
        return if (byId.containsKey(request.strategy.value)) byId[request.strategy.value] else fallback
    }
}
