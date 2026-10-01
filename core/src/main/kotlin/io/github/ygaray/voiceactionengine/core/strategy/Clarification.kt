package io.github.ygaray.voiceactionengine.core.strategy

/**
 * A question the model wants the user to answer instead of guessing, with the choices it offers.
 *
 * Options may be empty (an open question). The engine never interprets the option ids; they are the app's own.
 *
 * @property question the question to show the user.
 * @property options the offered choices, in the order the model listed them.
 */
public class Clarification(
    public val question: String,
    public val options: List<ClarificationOption>,
) {
    /** Prints lengths and counts only, so neither the question nor any option can reach a log. */
    override fun toString(): String = "Clarification(questionLength=${question.length}, options=${options.size})"
}

/**
 * One choice offered by a [Clarification].
 *
 * @property id opaque to the engine: an app row id, a list id, anything the app can map back to its own data.
 * @property label the text to show the user for this choice.
 */
public class ClarificationOption(
    public val id: String,
    public val label: String,
) {
    /** Prints lengths only, so neither the id nor the label can reach a log. */
    override fun toString(): String = "ClarificationOption(idLength=${id.length}, labelLength=${label.length})"
}
