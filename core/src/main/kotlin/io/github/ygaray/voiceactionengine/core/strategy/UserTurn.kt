package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.CommandInput
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val DATE_TIME_PREFIX = "Current local date-time: "

/**
 * Renders the one user message of a request from a command. The returned text becomes that user message and never
 * enters the system prompt, so it cannot disturb the cached prefix. An app that needs byte-exact framing supplies its
 * own renderer; [standard] is the engine default.
 */
public fun interface UserTurnRenderer {
    /** Returns the user message text for [context]. */
    public suspend fun render(context: UserTurnContext): String

    /** Ways to create a renderer. */
    public companion object {
        /**
         * The engine default: the current local date-time truncated to seconds with its offset and zone id, a blank
         * line, then the transcript.
         */
        public fun standard(): UserTurnRenderer = StandardUserTurnRenderer
    }
}

private object StandardUserTurnRenderer : UserTurnRenderer {
    override suspend fun render(context: UserTurnContext): String {
        val dateTime = context.dateTime.truncatedTo(ChronoUnit.SECONDS)
        return DATE_TIME_PREFIX + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime) +
            " (" + dateTime.zone.id + ")\n\n" + context.input.transcript
    }
}

/**
 * What a [UserTurnRenderer] sees. The engine builds it; an app only reads it.
 *
 * @property input the command.
 * @property dateTime the current date-time from the tier's clock, in the clock's zone.
 * @property carry the opaque object an earlier tier escalated with, or null.
 */
public class UserTurnContext internal constructor(
    public val input: CommandInput,
    public val dateTime: ZonedDateTime,
    public val carry: Any?,
) {
    /** Prints lengths, the language, the date-time and the carry's class name only, never the transcript. */
    override fun toString(): String =
        "UserTurnContext(transcriptLength=${input.transcript.length}, language=${input.language}, " +
            "dateTime=$dateTime, carry=${carry?.let { it::class.simpleName }})"
}
