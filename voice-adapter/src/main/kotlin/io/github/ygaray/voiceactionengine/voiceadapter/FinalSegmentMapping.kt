@file:JvmName("FinalSegmentCommandInput")

package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import io.github.ygaray.voiceactionengine.core.CommandInput

/**
 * Turns one finished speech segment into the command the engine runs.
 *
 * The text is passed verbatim (no trimming, no validation) and the segment id is dropped. The language is the segment's
 * label through [normalizeSttLanguageLabel], so an unknown label gives null rather than a guess.
 *
 * The segment is never stringified or logged here: its `toString` prints the raw transcript.
 */
public fun FinalSegment.toCommandInput(): CommandInput =
    CommandInput(transcript = text, language = normalizeSttLanguageLabel(language))
