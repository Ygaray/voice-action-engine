package io.github.ygaray.voiceactionengine.core.telemetry

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.internal.GuardedClock

/**
 * The start-tier pick of one run: open while the picker runs, closed into a [StartTierSelection] when it ends. It
 * shares the recorder's [lock], so the callers read the clock before taking it, as the recorder does.
 */
internal class SelectionBook(private val lock: Any, private val clock: GuardedClock) {
    private var picker: StrategyId? = null
    private var eligible: List<StrategyId> = emptyList()
    private var startedAt = 0L
    private val turns = mutableListOf<TurnRecord>()
    private var closed: StartTierSelection? = null

    /** The closed selection, or null while none was recorded or the pick is still open. */
    val current: StartTierSelection?
        get() = synchronized(lock) { closed }

    /** Opens the selection for [picker], which was offered [eligible]. */
    suspend fun started(picker: StrategyId, eligible: List<StrategyId>) {
        val now = clock.read()
        synchronized(lock) {
            this.picker = picker
            this.eligible = eligible.toList()
            startedAt = now
            turns.clear()
            closed = null
        }
    }

    /** Keeps [turn] and returns true only while a selection is open and [strategy] is its picker. */
    fun take(strategy: StrategyId, turn: TurnRecord): Boolean = synchronized(lock) {
        val mine = picker != null && closed == null && strategy == picker
        if (mine) turns.add(turn)
        mine
    }

    /** Closes the open selection as [outcome]; does nothing when none is open. */
    suspend fun finished(outcome: String, picked: StrategyId?, tiersBypassed: Int) {
        val now = clock.read()
        synchronized(lock) {
            val id = picker
            if (id != null && closed == null) {
                val latency = now - startedAt
                closed = StartTierSelection(id, outcome, picked, eligible, tiersBypassed, latency, turns.toList())
            }
        }
    }
}
