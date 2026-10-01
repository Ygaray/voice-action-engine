package io.github.ygaray.voiceactionengine.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.legs.LegResult
import io.github.ygaray.voiceactionengine.sample.legs.LegRunner
import io.github.ygaray.voiceactionengine.sample.ui.Tone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The status word of a leg that has not been pressed. */
internal const val STATUS_IDLE = "IDLE"

/** The status word of the leg in progress. */
internal const val STATUS_RUNNING = "RUNNING"

/** The `trigger` of a leg started by a tap. */
internal const val TRIGGER_UI = "ui"

/** The `trigger` of a leg started by the debug-only rerun intent. */
internal const val TRIGGER_AUTORUN = "autorun"

/**
 * One row of the legs list: the status word (one of IDLE, RUNNING and the verdict kinds) and the reason code when there
 * is one.
 */
internal data class LegView(val leg: LegId, val status: String, val reason: String?) {
    /** The status word, followed by `reason=<code>` when there is one. */
    val text: String get() = if (reason == null) status else "$status reason=$reason"

    /** PASS green; FAIL and REFUSED red; WARM, OUT_OF_BAND, INCONCLUSIVE and CAPTURED amber; the rest plain. */
    val tone: Tone
        get() = when (status) {
            "PASS" -> Tone.GOOD
            "FAIL", "REFUSED" -> Tone.BAD
            "WARM", "OUT_OF_BAND", "INCONCLUSIVE", "CAPTURED" -> Tone.WARN
            else -> Tone.NEUTRAL
        }
}

/**
 * Everything the screen shows. It holds no key text; the typed keys live in their own flow so no state printout can carry one.
 *
 * @property running the leg in progress, or null; every run button is disabled while it is not null.
 * @property readout the last leg's rendered outcome, or null before the first leg.
 */
internal data class UiState(
    val okhttp: String,
    val legs: List<LegView>,
    val running: LegId?,
    val readout: String?,
) {
    /** Whether the run buttons are enabled. */
    val runEnabled: Boolean get() = running == null
}

/**
 * The screen's logic. It runs one leg at a time, ignoring a press while one is running, and turns each verdict into a
 * status word and a readout. It holds no key and logs nothing itself.
 */
internal class SampleViewModel(
    private val runner: LegRunner,
    @Suppress("unused") private val vault: KeyVault,
    @Suppress("unused") private val keyImport: KeyImport?,
    @Suppress("unused") private val fixture: FixtureState,
    @Suppress("unused") private val budget: RequestBudget,
    okhttpVersion: String,
    @Suppress("unused") private val sink: EvidenceSink,
    @Suppress("unused") private val providers: List<ProviderId>,
    @Suppress("unused") private val nowSeconds: () -> Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        UiState(
            okhttp = okhttpVersion,
            legs = LegId.entries.map { LegView(it, STATUS_IDLE, null) },
            running = null,
            readout = null,
        ),
    )

    /** What the screen shows now. */
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    /**
     * Starts [leg]. A press while another leg is running is ignored, so a double tap can never start two paid legs.
     * [trigger] is `ui` for a tap and `autorun` for the debug rerun intent.
     */
    fun runLeg(leg: LegId, trigger: String = TRIGGER_UI) {
        if (mutableState.value.running != null) return
        mutableState.update { it.withStatus(leg, STATUS_RUNNING, null).copy(running = leg) }
        viewModelScope.launch { finish(runner.run(leg, trigger)) }
    }

    private fun finish(result: LegResult) {
        val verdict = result.verdict
        mutableState.update {
            it.withStatus(result.leg, verdict.kind.name, verdict.reason)
                .copy(running = null, readout = result.verdict.toString())
        }
    }

    private fun UiState.withStatus(leg: LegId, status: String, reason: String?): UiState =
        copy(legs = legs.map { if (it.leg == leg) LegView(leg, status, reason) else it })

    override fun toString(): String = "SampleViewModel"
}
