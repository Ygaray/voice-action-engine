package io.github.ygaray.voiceactionengine.core.strategy.grammar

import kotlinx.serialization.json.JsonObject

/**
 * What a [GrammarPack] matched: the tool a declared phrasing names, the arguments its slots filled, and which language
 * pack matched.
 *
 * @property toolName the app's tool the matched phrasing belongs to.
 * @property arguments the arguments the phrasing's slots filled; empty for a phrasing with no slots.
 * @property matchedLanguage "en" or "es" for the pack that matched, or null when both packs matched the same tool and
 * the command carried no language label (a cross-pack agreement). It can be null, so a caller that keys a reply
 * template off it must fall back to its own locale.
 * @property terminal true when the matched intent is declared terminal, so the command ends with a call to the app and
 * no tool runs.
 * @property ruleId an opaque identifier of the matched phrasing, for correlating a match with its declaration in logs
 * and tests; do not parse it. Null when both packs matched and agreed.
 */
public class GrammarMatch internal constructor(
    public val toolName: String,
    public val arguments: JsonObject,
    public val matchedLanguage: String?,
    public val terminal: Boolean,
    public val ruleId: String?,
) {
    /** Prints the tool name, language, argument count, terminal flag and rule id; never argument values. */
    override fun toString(): String =
        "GrammarMatch(toolName=$toolName, matchedLanguage=$matchedLanguage, argumentCount=${arguments.size}, " +
            "terminal=$terminal, ruleId=$ruleId)"
}
