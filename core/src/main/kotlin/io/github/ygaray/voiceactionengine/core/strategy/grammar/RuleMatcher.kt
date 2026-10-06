package io.github.ygaray.voiceactionengine.core.strategy.grammar

/** What one language's rules decided for one transcript. */
internal sealed class RuleVerdict {
    /** No rule matches the whole transcript. */
    class None : RuleVerdict()

    /** Exactly one distinct result; [rule] is the first rule that reached it. */
    class One(val rule: FlatRule) : RuleVerdict()

    /** Two or more distinct results: the transcript is read two ways and the matcher never picks one. */
    class Ambiguous : RuleVerdict()
}

/**
 * The flat rules of one language and the anchored walk over them.
 *
 * A rule matches only when its elements account for every token of the transcript, in order, from the first token to
 * the last: never a prefix, never a part. The walk visits every rule and collects the distinct results (the same tool
 * reached by two rules or two expansions is one result); the moment a second distinct result exists it stops and
 * reports [RuleVerdict.Ambiguous]. There is no score, no threshold and no first-match-wins path.
 *
 * Cost: the transcript has a bounded number of tokens, each rule is a short flat list, and the walk compares one
 * literal word per step and stops at the first mismatch, so a rule costs at most its own length. There is no
 * backtracking over user-supplied patterns and no regular expression.
 */
internal class RuleMatcher(private val rules: List<FlatRule>) {

    /** The number of flat rules, for the pack's `toString`. */
    val size: Int get() = rules.size

    /** The verdict for the folded [keys] of one transcript. */
    fun match(keys: List<String>): RuleVerdict {
        val results = LinkedHashMap<String, FlatRule>()
        for (rule in rules) {
            if (walk(rule.elements, 0, keys, 0)) {
                results.putIfAbsent(resultKey(rule), rule)
                if (results.size > 1) return RuleVerdict.Ambiguous()
            }
        }
        return results.values.firstOrNull()?.let { RuleVerdict.One(it) } ?: RuleVerdict.None()
    }

    // The identity of a result: what the app would be asked to do. Rules reaching the same one are the same reading.
    private fun resultKey(rule: FlatRule): String = rule.toolName

    private fun walk(elements: List<RuleElement>, element: Int, keys: List<String>, position: Int): Boolean {
        if (element == elements.size) return position == keys.size
        val next = elements[element]
        return next is RuleElement.Word && position < keys.size && next.key == keys[position] &&
            walk(elements, element + 1, keys, position + 1)
    }
}
