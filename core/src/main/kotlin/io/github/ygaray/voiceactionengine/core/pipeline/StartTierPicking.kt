package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How a selector asks a picker: the [picker], the id its model call is routed and recorded under, the providers it
 * may use, and whether the single-shot tier is skipped. Prints the id only.
 */
internal class PickingSpec(
    val picker: StartTierPicker,
    val id: StrategyId,
    val capabilities: StrategyCapabilities,
    val skipsSingleTier: Boolean,
) {
    override fun toString(): String = "PickingSpec(id=$id)"
}

/**
 * The context a picker sees. Its model is bound through the router under the picker's own id and declared providers,
 * so the selection, credential and policy gates apply exactly as for a tier.
 */
internal class RunPickContext(
    private val scope: RunScope,
    private val picker: StrategyId,
    private val declared: Set<ProviderId>,
) : PickContext() {
    private val bindLock = Mutex()

    @Volatile
    private var bound: BoundModel? = null

    override val runId: String
        get() = scope.runId

    override val policy: TierPolicy
        get() = scope.policy

    override val tokensUsed: Long
        get() = scope.recorder.tokensUsed

    override suspend fun recordTurn(turn: TurnRecord) {
        scope.recorder.turnRecorded(picker, turn)
    }

    override suspend fun model(): BoundModel = bound ?: bindLock.withLock {
        bound ?: scope.router.bind(picker, declared, scope.policy, scope.recorder).also { bound = it }
    }
}

private const val SELECTION_PICKED = "picked"
private const val SELECTION_FALLBACK = "router_fallback"

/**
 * Asks a picker where the model walk starts. The answer is accepted only when it names one of the eligible model
 * tiers; anything else starts at the first of them and is recorded as `router_fallback`. A picker the policy would not
 * let run as a tier (by the same static rule) is not called either.
 */
internal class StartTierPicking(private val scope: RunScope, private val ladder: Ladder) {
    /** The index in [rest] the walk starts at. [rest] is the ladder after the tiers that make no model call. */
    suspend fun startIn(rest: List<CommandStrategy>, spec: PickingSpec, input: CommandInput): Int {
        val llm = rest.filter { it.capabilities.providers.isNotEmpty() }.map { it.id }
        if (llm.isEmpty() || !tierPermitted(spec.capabilities, scope.policy, ladder.onDeviceAvailable)) {
            scope.recorder.recordCode(TraceCode.ROUTER_FALLBACK)
            return 0
        }
        scope.recorder.selectionBook.started(spec.id, llm)
        val choice = guarded(onFault = { null }) {
            withTimeoutOrNull(scope.policy.pickerTimeoutMillis) {
                spec.picker.pick(input, llm, RunPickContext(scope, spec.id, spec.capabilities.providers))
            }
        }
        val picked = choice?.takeIf { it in llm }
        if (picked == null) scope.recorder.recordCode(TraceCode.ROUTER_FALLBACK)
        scope.recorder.selectionBook.finished(
            if (picked != null) SELECTION_PICKED else SELECTION_FALLBACK,
            picked,
            picked?.let { llm.indexOf(it) } ?: 0,
        )
        return picked?.let { id -> rest.indexOfFirst { it.id == id } } ?: 0
    }
}
