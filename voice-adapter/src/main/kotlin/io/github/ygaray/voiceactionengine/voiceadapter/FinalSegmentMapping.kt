@file:JvmName("FinalSegmentCommandInput")

package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import io.github.ygaray.voiceactionengine.core.CommandInput

/**
 * Turns one finished speech segment into the command the engine runs, with no context and no parent run.
 *
 * The text is passed verbatim (no trimming, no validation) and the segment id is dropped. The language is the segment's
 * label through [normalizeSttLanguageLabel], so an unknown label gives null rather than a guess. Never throws.
 *
 * The segment is never stringified or logged here: its `toString` prints the raw transcript.
 */
public fun FinalSegment.toCommandInput(): CommandInput = toCommandInput(null, null)

/**
 * Turns one finished speech segment into the command the engine runs, carrying an app [context] object.
 *
 * The text is passed verbatim, the segment id is dropped, the label is normalised to `"en"`, `"es"` or null, and
 * [context] is passed through unchanged. Never throws.
 *
 * A [String] passed here is a context object, not a run id: the parent run stays null. To answer an earlier run, use
 * the two-argument form `toCommandInput(context, parentRunId)`.
 */
public fun FinalSegment.toCommandInput(context: Any?): CommandInput = toCommandInput(context, null)

/**
 * Turns one finished speech segment into the command the engine runs, carrying an app [context] object and the id of
 * the earlier run this command answers.
 *
 * The text is passed verbatim, the segment id is dropped, the label is normalised to `"en"`, `"es"` or null, and
 * [context] and [parentRunId] are passed through unchanged. Never throws.
 */
public fun FinalSegment.toCommandInput(context: Any?, parentRunId: String?): CommandInput =
    CommandInput(
        transcript = text,
        language = normalizeSttLanguageLabel(language),
        context = context,
        parentRunId = parentRunId,
    )
