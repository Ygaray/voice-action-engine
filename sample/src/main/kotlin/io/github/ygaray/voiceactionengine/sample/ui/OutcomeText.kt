package io.github.ygaray.voiceactionengine.sample.ui

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ClarificationOption
import io.github.ygaray.voiceactionengine.sample.keys.KeyAction
import io.github.ygaray.voiceactionengine.sample.keys.KeyUx
import io.github.ygaray.voiceactionengine.sample.legs.LegResult
import io.github.ygaray.voiceactionengine.sample.verdict.OutcomeSummary

/** How a line should be coloured: green, amber, red, or plain. */
internal enum class Tone {
    /** The thing worked. */
    GOOD,

    /** Something needs a look: not a pass and not a failure. */
    WARN,

    /** A failure or a refusal. */
    BAD,

    /** Nothing to judge. */
    NEUTRAL,
}

/**
 * What the readout shows for one leg result.
 *
 * @property headline the one-line result.
 * @property banner the red failure line (`FAILED: <code>`), or null when the leg did not fail.
 * @property question the clarification question to show, or null.
 * @property options one pressable button per entry, in the model's order; empty when there is no clarification.
 */
internal class OutcomeView(
    val headline: String,
    val tone: Tone,
    val banner: String?,
    val question: String?,
    val options: List<ClarificationOption>,
) {
    override fun toString(): String = "OutcomeView(tone=$tone, banner=${banner != null}, options=${options.size})"
}

/**
 * The sample's rendering rules for a command outcome, the same rules the consumer docs prescribe. The outcome set is
 * closed, so it is matched exhaustively; failure and escalation reasons are an open taxonomy, so they are shown by their
 * `code` and any check of one ends in an `else`.
 */
internal object OutcomeText {
    private const val REPLY_LENGTH_FIELD = "reply_len="

    /**
     * Renders [result]. [live] is true for a real provider: its reply is shown as a length only, because model text
     * must not end up in screenshots. A demo reply is scripted, so it is shown in full.
     */
    fun render(result: LegResult, live: Boolean): OutcomeView {
        val outcome = result.outcome
        val summary = result.summary
        if (outcome == null || summary == null) {
            return OutcomeView("Refused: ${result.verdict.reason ?: "no_reason"}", Tone.BAD, null, null, emptyList())
        }
        return when (outcome) {
            is CommandOutcome.Completed -> completed(outcome, summary, live)
            is CommandOutcome.Failed -> failure(outcome.reason, outcome.commits.size)
            is CommandOutcome.Unhandled ->
                OutcomeView("Not handled: ${outcome.lastReason?.code ?: "none"}", Tone.WARN, null, null, emptyList())
        }
    }

    /**
     * The loud rendering of a failure: a red `FAILED: <code>` banner that, for a key that is stored but unreadable, also
     * names the action the user should take. [committed] is how many changes were applied before the failure.
     */
    fun failure(reason: FailureReason, committed: Int = 0): OutcomeView {
        val action = keystoreAction(reason)
        val headline = if (committed > 0) "Failed after $committed committed" else "Failed"
        val banner = "FAILED: ${reason.code}" + if (action == null) "" else " - $action"
        return OutcomeView(headline, Tone.BAD, banner, null, emptyList())
    }

    // The keystore action for a key that cannot be read; every other reason has none.
    private fun keystoreAction(reason: FailureReason): String? = when (reason) {
        is FailureReason.CredentialUnreadable -> when (KeyUx.action(reason.cause)) {
            KeyAction.REENTER_KEY -> "re-enter key (${reason.cause})"
            KeyAction.TRANSIENT_RETRY -> "transient, retry (${reason.cause})"
        }
        else -> null
    }

    private fun completed(outcome: CommandOutcome.Completed, summary: OutcomeSummary, live: Boolean): OutcomeView {
        val call = outcome.terminalCall
        val clarification = call?.asClarification()
        val question = clarification?.question
        val options = clarification?.options.orEmpty()
        return when {
            // Some work was done and the rest was not: never "Done", whatever else the outcome carries.
            outcome.partial -> {
                val why = summary.reason?.let { " ($it)" }.orEmpty()
                OutcomeView(
                    "Did ${outcome.executed.size} action(s), couldn't finish$why",
                    Tone.WARN,
                    null,
                    question,
                    options,
                )
            }
            clarification != null -> OutcomeView("Needs your answer", Tone.NEUTRAL, null, question, options)
            call != null -> OutcomeView("Ended on ${call.toolName}", Tone.NEUTRAL, null, null, emptyList())
            else -> OutcomeView(done(outcome, live), Tone.GOOD, null, null, emptyList())
        }
    }

    private fun done(outcome: CommandOutcome.Completed, live: Boolean): String {
        val base = "Done: ${outcome.commits.size} committed"
        val reply = outcome.reply
        return when {
            live -> "$base $REPLY_LENGTH_FIELD${reply?.length ?: 0}"
            reply != null -> "$base - $reply"
            else -> base
        }
    }
}
