@file:JvmName("SttLanguageLabels")

package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.voiceactionengine.core.CommandInput

private const val LABEL_EN = "en"
private const val LABEL_ES = "es"

/**
 * Maps a language label reported by a speech engine onto the engine's closed language set.
 *
 * The result is `"en"` or `"es"` when the label, after trimming and lowercasing, equals one of them. Everything else
 * (a regional tag, an automatic-detection marker, a blank, another language, null) gives null, which means unknown.
 * A language is never guessed or defaulted, and no locale logic is involved.
 */
public fun normalizeSttLanguageLabel(raw: String?): String? {
    val label = raw?.trim()?.lowercase()
    return when (label) {
        LABEL_EN -> LABEL_EN
        LABEL_ES -> LABEL_ES
        else -> null
    }
}

/**
 * Builds a command from plain text and a language label, for callers with no speech-engine class on their classpath.
 *
 * The text is passed verbatim (no trimming, no validation) and the label goes through [normalizeSttLanguageLabel], so
 * the language is `"en"`, `"es"` or null. Never throws.
 */
public fun commandInputOf(transcript: String, languageLabel: String?): CommandInput =
    commandInputOf(transcript, languageLabel, null, null)

/**
 * Builds a command from plain text and a language label, carrying an app [context] object.
 *
 * The text is passed verbatim, the label is normalised to `"en"`, `"es"` or null, and [context] is passed through
 * unchanged. Never throws.
 */
public fun commandInputOf(transcript: String, languageLabel: String?, context: Any?): CommandInput =
    commandInputOf(transcript, languageLabel, context, null)

/**
 * Builds a command from plain text and a language label, carrying an app [context] object and the id of the earlier
 * run this command answers.
 *
 * The text is passed verbatim, the label is normalised to `"en"`, `"es"` or null, and [context] and [parentRunId] are
 * passed through unchanged. Never throws.
 */
public fun commandInputOf(
    transcript: String,
    languageLabel: String?,
    context: Any?,
    parentRunId: String?,
): CommandInput =
    CommandInput(
        transcript = transcript,
        language = normalizeSttLanguageLabel(languageLabel),
        context = context,
        parentRunId = parentRunId,
    )
