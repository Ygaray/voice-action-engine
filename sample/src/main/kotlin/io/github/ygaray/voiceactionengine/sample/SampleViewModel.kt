package io.github.ygaray.voiceactionengine.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.legs.DEMO_PROVIDER
import io.github.ygaray.voiceactionengine.sample.legs.LegCatalog
import io.github.ygaray.voiceactionengine.sample.legs.LegResult
import io.github.ygaray.voiceactionengine.sample.legs.LegRunner
import io.github.ygaray.voiceactionengine.sample.ui.HeaderText
import io.github.ygaray.voiceactionengine.sample.ui.OutcomeText
import io.github.ygaray.voiceactionengine.sample.ui.OutcomeView
import io.github.ygaray.voiceactionengine.sample.ui.Tone
import io.github.ygaray.voiceactionengine.sample.ui.ToneText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.coroutines.cancellation.CancellationException

/** The status word of a leg that has not been pressed. */
internal const val STATUS_IDLE = "IDLE"

/** The status word of the leg in progress. */
internal const val STATUS_RUNNING = "RUNNING"

/** The status word of the undo leg between its two presses: it counted N and waits for "Undo all". */
internal const val STATUS_AWAITING_UNDO = "awaiting_undo"

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
            "WARM", "OUT_OF_BAND", "INCONCLUSIVE", "CAPTURED", STATUS_AWAITING_UNDO -> Tone.WARN
            else -> Tone.NEUTRAL
        }
}

/** One provider's key row: the state wording and its tone. */
internal data class KeyView(val provider: ProviderId, val text: String, val tone: Tone) {
    /** Prints no wording: it can include the last four characters of a key. */
    override fun toString(): String = "KeyView(provider=${provider.value}, tone=$tone)"
}

/**
 * Everything the screen shows. It holds no key text; the typed keys live in their own flow so no state printout can carry one.
 *
 * @property running the leg in progress, or null; every run button is disabled while it is not null.
 * @property readout the last leg's rendered outcome, or null before the first leg.
 * @property undoLabel the text "Undo all (N)" while the undo leg waits for its second press, else null.
 * @property undoNote the held-proposal note shown apart from the label, or null.
 * @property fixture the fixture banner.
 * @property budgetText the request counts and cost estimate.
 * @property warmWindow the warm-window notice, or null when the window is closed.
 * @property keys one row per provider.
 * @property importAvailable whether the debug-only import button exists (never in a release build).
 * @property importStatus what the last import did, or null before the first one.
 */
internal data class UiState(
    val okhttp: String,
    val fixture: ToneText,
    val budgetText: String,
    val warmWindow: String?,
    val keys: List<KeyView>,
    val importAvailable: Boolean,
    val importStatus: ToneText?,
    val legs: List<LegView>,
    val running: LegId?,
    val readout: OutcomeView?,
    val undoLabel: String? = null,
    val undoNote: String? = null,
) {
    /** Whether the run buttons are enabled. */
    val runEnabled: Boolean get() = running == null

    /** Prints no key wording. */
    override fun toString(): String = "UiState(running=$running, legs=${legs.size}, keys=${keys.size})"
}

/**
 * The screen's logic. It runs one leg at a time, ignoring a press while one is running, turns each verdict into a
 * status word and a readout, and holds the key actions. It holds no key beyond the text being typed, and it logs only
 * through the evidence sink, whose lines cannot carry a key.
 */
internal class SampleViewModel(
    private val runner: LegRunner,
    private val vault: KeyVault,
    private val keyImport: KeyImport?,
    fixture: FixtureState,
    private val budget: RequestBudget,
    okhttpVersion: String,
    private val sink: EvidenceSink,
    private val providers: List<ProviderId>,
    private val estUsd: () -> String = { UNKNOWN_ESTIMATE },
    private val nowSeconds: () -> Long,
) : ViewModel() {
    // The last result, kept so a pressed clarification option can name the run it answers.
    private var lastResult: LegResult? = null

    private val mutableState = MutableStateFlow(
        UiState(
            okhttp = okhttpVersion,
            fixture = HeaderText.fixtureBanner(fixture),
            budgetText = HeaderText.budget(budget.snapshot(), estUsd()),
            warmWindow = HeaderText.warmWindow(budget.warmWindowRemaining(nowSeconds())),
            keys = providers.map { KeyView(it, CHECKING_KEY, Tone.NEUTRAL) },
            importAvailable = keyImport != null,
            importStatus = null,
            legs = LegId.entries.map { LegView(it, STATUS_IDLE, null) },
            running = null,
            readout = null,
        ),
    )

    private val mutableFields = MutableStateFlow<Map<ProviderId, String>>(emptyMap())

    /** What the screen shows now. */
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    /** The text typed so far into each provider's key field. Cleared the moment Save is pressed. */
    val keyFields: StateFlow<Map<ProviderId, String>> = mutableFields.asStateFlow()

    init {
        // The runtime facts and the fixture state, once, at startup. A fixture that is not usable is a loud line.
        val loaded = fixture as? FixtureState.Loaded
        sink.emit(EvidenceLine.env(okhttpVersion, loaded?.sha256, loaded?.tools?.size, null, null, null))
        sink.emit(EvidenceLine.fixture(fixture))
        viewModelScope.launch { refreshKeys() }
    }

    /**
     * Starts [leg]. A press while another leg is running is ignored, so a double tap can never start two paid legs.
     * [trigger] is `ui` for a tap and `autorun` for the debug rerun intent.
     */
    fun runLeg(leg: LegId, trigger: String = TRIGGER_UI) {
        if (mutableState.value.running != null) return
        if (trigger == TRIGGER_AUTORUN) sink.emit(EvidenceLine.autorun(leg))
        mutableState.update { it.withStatus(leg, STATUS_RUNNING, null).copy(running = leg) }
        viewModelScope.launch { finish(runner.run(leg, trigger)) }
    }

    /**
     * Answers the clarification the last demo run ended on with the option [optionId]. This starts a NEW command linked
     * to the first by `parentRunId` (D-14); nothing is resumed. Ignored while a leg runs or when there is no such option.
     */
    fun chooseOption(optionId: String) {
        val previous = lastResult
        val completed = previous?.outcome as? CommandOutcome.Completed
        val option = completed?.terminalCall?.asClarification()?.options?.firstOrNull { it.id == optionId }
        if (previous == null || completed == null || option == null) return
        if (previous.leg != LegId.DEMO_CLARIFY || mutableState.value.running != null) return
        mutableState.update { it.withStatus(LegId.DEMO_CLARIFY, STATUS_RUNNING, null).copy(running = LegId.DEMO_CLARIFY) }
        viewModelScope.launch { finish(runner.followUp(completed, option)) }
    }

    /**
     * The second press of the undo leg: calls the journal's undo for the command the first press counted. Ignored while a
     * leg runs, and when nothing waits (no "Undo all" label is showing), so a stale or double press changes nothing.
     */
    fun undoAll() {
        if (mutableState.value.running != null || mutableState.value.undoLabel == null) return
        mutableState.update {
            it.withStatus(LegId.UNDO_ALL, STATUS_RUNNING, null).copy(running = LegId.UNDO_ALL, undoLabel = null, undoNote = null)
        }
        viewModelScope.launch { finish(runner.undoAll()) }
    }

    /** Records what is typed into [provider]'s key field. */
    fun onKeyFieldChange(provider: ProviderId, text: String) {
        mutableFields.update { it + (provider to text) }
    }

    /**
     * Saves [text] (by default what is typed in the field) as the key of [provider]. The field is cleared at once, whether
     * or not the save works, and the key is never logged. A failed save is a red row naming only the exception type.
     */
    fun saveKey(provider: ProviderId, text: String = mutableFields.value[provider].orEmpty()) {
        mutableFields.update { it + (provider to "") }
        val key = text.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            val failure = guarded { vault.save(provider, key) }
            refreshKeys(provider, failure?.let { "Save failed ($it) - re-enter key" })
        }
    }

    /** Deletes the stored key of [provider]. */
    fun deleteKey(provider: ProviderId) {
        mutableFields.update { it + (provider to "") }
        viewModelScope.launch {
            val failure = guarded { vault.delete(provider) }
            refreshKeys(provider, failure?.let { "Delete failed ($it)" })
        }
    }

    /**
     * Runs the debug-only importer over the pushed test keys, writes one `VAE_KEY` line per provider and shows each
     * provider's state word and the two plaintext flags. Nothing happens in a build that has no importer.
     */
    fun importTestKeys() {
        val importer = keyImport ?: return
        viewModelScope.launch {
            val reports = try {
                importer.importAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The importer destroys the pushed files itself; the screen says the import failed, by type only.
                val text = "Import failed (${failure.javaClass.simpleName})"
                mutableState.update { it.copy(importStatus = ToneText(text, Tone.BAD)) }
                return@launch
            }
            val lines = reports.map { EvidenceLine.key(it) }
            lines.forEach { sink.emit(it) }
            val text = reports.joinToString("; ") { HeaderText.importLine(it) }
            val tone = if (lines.any { it.loud }) Tone.BAD else Tone.GOOD
            mutableState.update { it.copy(importStatus = ToneText(text, tone)) }
            refreshKeys()
        }
    }

    /**
     * Refreshes the request counts and the warm-window countdown. The screen calls this once a second while a window is
     * open, and it runs after every leg.
     */
    fun tick() {
        mutableState.update {
            it.copy(
                budgetText = HeaderText.budget(budget.snapshot(), estUsd()),
                warmWindow = HeaderText.warmWindow(budget.warmWindowRemaining(nowSeconds())),
            )
        }
    }

    private fun finish(result: LegResult) {
        lastResult = result
        val verdict = result.verdict
        val live = LegCatalog.spec(result.leg).provider != DEMO_PROVIDER
        val prompt = result.undo
        mutableState.update {
            val shown = if (prompt == null) {
                it.withStatus(result.leg, verdict.kind.name, verdict.reason)
            } else {
                it.withStatus(result.leg, STATUS_AWAITING_UNDO, null)
            }
            val common = shown.copy(running = null, readout = OutcomeText.render(result, live))
            // Only the undo leg decides the control; another leg's result leaves a waiting "Undo all" in place.
            if (result.leg != LegId.UNDO_ALL) {
                common
            } else {
                common.copy(
                    undoLabel = prompt?.let { p -> HeaderText.undoLabel(p.n) },
                    undoNote = prompt?.let { p -> HeaderText.undoPending(p.pending) },
                )
            }
        }
        tick()
    }

    // Reads every provider's key state again; [note] replaces the row of [noted] with a red failure line.
    private suspend fun refreshKeys(noted: ProviderId? = null, note: String? = null) {
        val rows = providers.map { provider ->
            if (provider == noted && note != null) {
                KeyView(provider, note, Tone.BAD)
            } else {
                val row = HeaderText.keyRow(vault.read(provider))
                KeyView(provider, row.text, row.tone)
            }
        }
        mutableState.update { it.copy(keys = rows) }
    }

    // The exception type when [action] fails the way the keystore can, else null. The message is never kept.
    private suspend fun guarded(action: suspend () -> Unit): String? = try {
        action()
        null
    } catch (failure: GeneralSecurityException) {
        failure.javaClass.simpleName
    } catch (failure: ProviderException) {
        failure.javaClass.simpleName
    } catch (failure: IOException) {
        failure.javaClass.simpleName
    }

    private fun UiState.withStatus(leg: LegId, status: String, reason: String?): UiState =
        copy(legs = legs.map { if (it.leg == leg) LegView(leg, status, reason) else it })

    override fun toString(): String = "SampleViewModel"

    private companion object {
        const val CHECKING_KEY = "Checking key..."
        const val UNKNOWN_ESTIMATE = "unknown"
    }
}
