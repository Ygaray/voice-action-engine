package io.github.ygaray.voiceactionengine.sample.ui

import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetSnapshot
import io.github.ygaray.voiceactionengine.sample.evidence.CORE_REQUEST_CEILING
import io.github.ygaray.voiceactionengine.sample.evidence.TOTAL_REQUEST_CEILING
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.keys.KeyUx

private const val SHA_HEAD = 8
private const val MIDDLE_DOT = "·"

/** A line of text with the tone it is shown in. */
internal class ToneText(val text: String, val tone: Tone) {
    override fun toString(): String = "ToneText(tone=$tone)"
}

/** The header, keys and import texts of the screen as pure functions, so the wording is testable and in one place. */
internal object HeaderText {
    /** The undo control's label: the applied actions of the command, as the journal counts them. */
    fun undoLabel(n: Int): String = "Undo all ($n)"

    /** The note shown apart from the label when held proposals exist; they are not part of N. Null when there are none. */
    fun undoPending(pending: Int): String? = if (pending > 0) "$pending held, not counted" else null

    /**
     * The fixture banner: green with the 8-hex digest prefix (never the suffix: LE-7), tool count and source when it
     * loaded; red and specific for every other state. A fixture that is not usable must be impossible to miss.
     */
    fun fixtureBanner(state: FixtureState): ToneText = when (state) {
        is FixtureState.Loaded -> {
            val sha = state.sha256.take(SHA_HEAD)
            val source = state.source.substringBefore('/')
            ToneText("Fixture OK sha=$sha tools=${state.tools.size} source=$source", Tone.GOOD)
        }
        is FixtureState.Absent -> ToneText("FIXTURE ABSENT - push the LE-1 fixture (GATE1-RUNBOOK)", Tone.BAD)
        is FixtureState.ShaMismatch -> ToneText(
            "FIXTURE SHA MISMATCH ${state.actualPrefix} - do not use; ask the orchestrator to regenerate",
            Tone.BAD,
        )
        is FixtureState.Malformed -> ToneText("FIXTURE MALFORMED ${state.code}", Tone.BAD)
    }

    /** The request counts against the ceilings, and the cost estimate text. */
    fun budget(snapshot: BudgetSnapshot, estUsd: String): String =
        "requests ${snapshot.core}/$CORE_REQUEST_CEILING $MIDDLE_DOT " +
            "optional ${snapshot.optional}/${TOTAL_REQUEST_CEILING - CORE_REQUEST_CEILING} $MIDDLE_DOT est USD $estUsd"

    /** The warm-window notice, or null when the window is closed. */
    fun warmWindow(remainingSeconds: Long): String? =
        if (remainingSeconds > 0L) "Anthropic warm window: wait $remainingSeconds s" else null

    /** The key row of a provider: the library's wording, green when ready, red when the key is lost or damaged. */
    fun keyRow(state: KeyState): ToneText = ToneText(
        KeyUx.label(state),
        when (state) {
            is KeyState.Ready -> Tone.GOOD
            is KeyState.NotConfigured -> Tone.NEUTRAL
            else -> Tone.BAD
        },
    )

    /**
     * One import line per provider: the state word and the two plaintext flags. Never any part of a key.
     * `in_datastore` is `none` when no scan ran.
     */
    fun importLine(report: ImportReport): String =
        "${report.provider.value}=${report.state} deleted=${report.plaintextFileDeleted} " +
            "in_datastore=${report.plaintextInDatastore ?: "none"}"
}
