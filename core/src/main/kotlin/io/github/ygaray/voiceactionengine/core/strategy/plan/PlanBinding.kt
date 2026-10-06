package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.commit.ExecutedAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// A step id starts with a letter, so a dictated amount such as "$5.00" can never name a step.
private const val ID_FRAGMENT = "[A-Za-z][A-Za-z0-9_-]*"

// A literal dollar sign, a step id, a literal dot, then one or more characters that are not whitespace.
private val REFERENCE = Regex("[\$]($ID_FRAGMENT)\\.(\\S+)")
private val STEP_ID = Regex(ID_FRAGMENT)

// The longest step id a plan may use; a longer one is a malformed plan, not a longer name.
private const val STEP_ID_CAP = 64

// A parsed reference: the step id and the result key it names. The grammar is described on [bindArguments].
private class Reference(val stepId: String, val key: String)

private fun referenceOf(element: JsonElement): Reference? =
    (element as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.let { REFERENCE.matchEntire(it.content) }
        ?.let { Reference(it.groupValues[1], it.groupValues[2]) }

/**
 * True when [text] is a valid step id: it starts with a letter, uses letters, digits, `_` or `-`, and has at most 64
 * characters.
 */
internal fun isStepId(text: String): Boolean = text.length <= STEP_ID_CAP && STEP_ID.matches(text)

/** Every step id named by a reference in [arguments]'s values, depth first, in document order, duplicates kept. */
internal fun referencedStepIds(arguments: JsonObject): List<String> {
    val found = mutableListOf<String>()
    collect(arguments, found)
    return found
}

private fun collect(element: JsonElement, found: MutableList<String>) {
    when (element) {
        is JsonObject -> element.values.forEach { collect(it, found) }
        is JsonArray -> element.forEach { collect(it, found) }
        else -> referenceOf(element)?.let { found.add(it.stepId) }
    }
}

/**
 * Binds one step's arguments to the results of earlier steps.
 *
 * A reference is a JSON string whose ENTIRE content is `$<stepId>.<key>`. The step id starts with a letter and uses
 * letters, digits, `_` and `-`; the key is one or more characters that are not whitespace, so it may hold dots (the
 * key names belong to the app). Anything else is a literal and is never touched: `$5.00`, `$s1`, `$s1.`, a reference
 * inside a longer string, and `$$s1.x` (there is no escape syntax in this version). Object keys and values that are
 * not strings (numbers, booleans, null) are never read or rewritten. Arrays and nested objects are walked.
 *
 * A bound value is always a JSON string. Binding works on whole string primitives of the parsed tree only, never on
 * serialized text.
 *
 * Replaces every whole-value reference in [arguments] with the string that [results] holds for it. Returns null as
 * soon as a reference names a step with no entry or a key the entry lacks: nothing is guessed and nothing is
 * partially substituted.
 */
internal fun bindArguments(arguments: JsonObject, results: Map<String, Map<String, String>>): JsonObject? =
    bindObject(arguments, results)

private fun bind(element: JsonElement, results: Map<String, Map<String, String>>): JsonElement? =
    when (element) {
        is JsonObject -> bindObject(element, results)
        is JsonArray -> bindArray(element, results)
        else -> referenceOf(element)?.let { ref -> results[ref.stepId]?.get(ref.key)?.let { JsonPrimitive(it) } }
            ?: element.takeIf { referenceOf(it) == null }
    }

private fun bindObject(element: JsonObject, results: Map<String, Map<String, String>>): JsonObject? {
    val bound = LinkedHashMap<String, JsonElement>()
    for ((key, value) in element) {
        bound[key] = bind(value, results) ?: return null
    }
    return JsonObject(bound)
}

private fun bindArray(element: JsonArray, results: Map<String, Map<String, String>>): JsonArray? {
    val bound = ArrayList<JsonElement>(element.size)
    for (value in element) {
        bound.add(bind(value, results) ?: return null)
    }
    return JsonArray(bound)
}

/**
 * The union of the actions' target ids. A key two actions disagree on is dropped and stays dropped, even if a later
 * action repeats one of the values, so a reference to it is unresolved rather than a guess.
 */
internal fun mergeTargets(actions: List<ExecutedAction>): Map<String, String> {
    val merged = LinkedHashMap<String, String>()
    val conflicted = HashSet<String>()
    for (action in actions) {
        for ((key, value) in action.targetIds) {
            val earlier = merged[key]
            when {
                key in conflicted -> Unit
                earlier != null && earlier != value -> {
                    merged.remove(key)
                    conflicted.add(key)
                }
                else -> merged[key] = value
            }
        }
    }
    return merged
}
