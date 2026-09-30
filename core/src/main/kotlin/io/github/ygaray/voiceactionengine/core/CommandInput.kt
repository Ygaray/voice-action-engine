package io.github.ygaray.voiceactionengine.core

/**
 * One spoken command handed to the engine.
 *
 * Constructing an input never throws and performs no validation.
 *
 * @property transcript what the user said, as text.
 * @property language `"en"`, `"es"` or null when the caller does not know.
 * @property context an opaque app object the engine never inspects; it is only handed back to the app's own seams.
 * @property parentRunId the id of the earlier run this command answers (for example a clarification reply), or null.
 */
public class CommandInput(
    public val transcript: String,
    public val language: String? = null,
    public val context: Any? = null,
    public val parentRunId: String? = null,
) {
    /** Prints the transcript length and the context class name only, so neither the words nor the context can leak. */
    override fun toString(): String =
        "CommandInput(transcriptLength=${transcript.length}, language=$language, " +
            "context=${context?.let { it::class.simpleName }}, parentRunId=$parentRunId)"
}
