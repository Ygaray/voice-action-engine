@file:JvmName("SttLanguageLabels")

package io.github.ygaray.voiceactionengine.voiceadapter

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
