package io.github.ygaray.voiceactionengine.core.strategy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The model's call to a terminal tool, the call that ended the run.
 *
 * @property toolName the name of the terminal tool the model called.
 * @property arguments the model-generated arguments, exactly as received.
 */
public class TerminalCall(
    public val toolName: String,
    public val arguments: JsonObject,
) {
    /**
     * Reads these arguments as a clarification request, or returns null when they do not have that shape.
     *
     * The expected shape is a string `question` and an array `options` whose every element is an object with string
     * `id` and `label` (extra keys are ignored). An empty `options` array is valid. This never throws, whatever the
     * model sent.
     */
    public fun asClarification(): Clarification? {
        val question = stringOrNull(arguments[QUESTION_FIELD])
        val rawOptions = arguments[OPTIONS_FIELD] as? JsonArray
        val options = rawOptions?.map { optionOrNull(it) ?: return null }
        return if (question != null && options != null) Clarification(question, options) else null
    }

    /** Prints the tool name and the argument count only, never argument values. */
    override fun toString(): String = "TerminalCall(toolName=$toolName, argumentCount=${arguments.size})"
}

// Field names are mirrored in ToolSpec.clarification; the round-trip test keeps the two in step.
private const val QUESTION_FIELD = "question"
private const val OPTIONS_FIELD = "options"
private const val ID_FIELD = "id"
private const val LABEL_FIELD = "label"

private fun optionOrNull(element: JsonElement): ClarificationOption? {
    val obj = element as? JsonObject ?: return null
    val id = stringOrNull(obj[ID_FIELD])
    val label = stringOrNull(obj[LABEL_FIELD])
    return if (id != null && label != null) ClarificationOption(id, label) else null
}

private fun stringOrNull(element: JsonElement?): String? =
    if (element is JsonPrimitive && element.isString) element.content else null
