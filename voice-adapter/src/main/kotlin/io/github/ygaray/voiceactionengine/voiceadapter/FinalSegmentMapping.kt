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
 * Turns one finished speech segment into the command the engine runs, carrying an app [context] object and the id of
 * the earlier run this command answers.
 *
 * Either of [context] and [parentRunId] may be null. The text is passed verbatim, the segment id is dropped, the label
 * is normalised to `"en"`, `"es"` or null, and [context] and [parentRunId] are passed through unchanged. Never throws.
 */
public fun FinalSegment.toCommandInput(context: Any?, parentRunId: String?): CommandInput =
    // One mapping for both paths: the stt-free label facade owns it, so the two can never drift apart.
    commandInputOf(text, language, context, parentRunId)
