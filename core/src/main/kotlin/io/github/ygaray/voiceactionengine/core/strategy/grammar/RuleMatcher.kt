package io.github.ygaray.voiceactionengine.core.strategy.grammar

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One bound slot: its [name], the JSON [value] it contributes and the [raw] text the speaker said for it. */
internal class SlotBinding(val name: String, val value: JsonPrimitive, val raw: String)

/** What one language's rules decided for one transcript. */
internal sealed class RuleVerdict {
    /** No rule matches the whole transcript. */
    class None : RuleVerdict()

    /**
     * Exactly one distinct result; [rule] is the first rule that reached it, [arguments] the values its slots bound and
     * [bindings] the same values in rule order with the spoken text of each span.
     */
    class One(val rule: FlatRule, val arguments: JsonObject, val bindings: List<SlotBinding>) : RuleVerdict()

    /** Two or more distinct results: the transcript is read two ways and the matcher never picks one. */
    class Ambiguous : RuleVerdict()
}

/**
 * The flat rules of one language and the anchored walk over them.
 *
 * A rule matches only when its elements account for every token of the transcript, in order, from the first token to
 * the last: never a prefix, never a part. The walk visits every rule and collects the distinct results (the same tool
 * reached by two rules or two expansions is one result); the moment a second distinct result exists it stops and
 * reports [RuleVerdict.Ambiguous]. There is no score, no threshold and no first-match-wins path. A slot tries every
 * candidate span of its kind, so two different bindings of one phrasing are two results too.
 *
 * Cost: the transcript has a bounded number of tokens, each rule is a short flat list of at most four slots, a number
 * slot has at most as many candidate spans as the language's longest number phrase and a text slot at most `maxWords`,
 * and the walk compares one literal word per step and stops at the first mismatch. There is no backtracking over
 * user-supplied patterns and no regular expression.
 *
 * [slots] holds, per tool, the declared kind of each slot name.
 */
internal class RuleMatcher(
    rules: List<FlatRule>,
    fillers: List<List<String>>,
    private val slots: Map<String, Map<String, SlotSpec>>,
) {
    private val rules: List<FlatRule> = rules.toList()

    // Longest first, so a filler that contains another is stripped whole.
    private val fillers: List<List<String>> = fillers.sortedByDescending { it.size }

    /** The number of flat rules, for the pack's `toString`. */
    val size: Int get() = rules.size

    /** The verdict for one transcript's [tokens], after its leading and trailing fillers are stripped. */
    fun match(tokens: GrammarTokens): RuleVerdict {
        val kept = strip(tokens)
        val hits = ArrayList<RuleVerdict.One>()
        for (rule in rules) {
            val walk = RuleWalk(rule, slots[rule.toolName].orEmpty(), kept) { admit(hits, it) }
            if (walk.run()) return RuleVerdict.Ambiguous()
        }
        return hits.firstOrNull() ?: RuleVerdict.None()
    }

    // True when [hit] is a second distinct result. The same tool with equal arguments is one reading however reached.
    private fun admit(hits: MutableList<RuleVerdict.One>, hit: RuleVerdict.One): Boolean {
        if (hits.none { it.rule.toolName == hit.rule.toolName && it.arguments == hit.arguments }) hits.add(hit)
        return hits.size > 1
    }

    // Leading phrases first, then trailing ones, each repeatedly and longest first; interior words are never touched.
    private fun strip(tokens: GrammarTokens): GrammarTokens {
        val keys = tokens.keys(0, tokens.tokens.size)
        var from = 0
        var to = keys.size
        var step = leadingFiller(keys, from, to)
        while (step > 0) {
            from += step
            step = leadingFiller(keys, from, to)
        }
        step = trailingFiller(keys, from, to)
        while (step > 0) {
            to -= step
            step = trailingFiller(keys, from, to)
        }
        return tokens.slice(from, to)
    }

    private fun leadingFiller(keys: List<String>, from: Int, to: Int): Int =
        fillers.firstOrNull { occursAt(keys, from, from, to, it) }?.size ?: 0

    private fun trailingFiller(keys: List<String>, from: Int, to: Int): Int =
        fillers.firstOrNull { occursAt(keys, to - it.size, from, to, it) }?.size ?: 0

    // True when [phrase] sits in keys exactly at [at], inside the window [from, to).
    private fun occursAt(keys: List<String>, at: Int, from: Int, to: Int, phrase: List<String>): Boolean =
        at >= from && at + phrase.size <= to && phrase.indices.all { keys[at + it] == phrase[it] }
}

// One bound slot while a walk is in progress: the candidate and where its span starts.
private class Bound(val name: String, val candidate: SlotCandidate, val from: Int)

/**
 * The depth-first walk of one flat rule over the kept tokens. A word must equal the next token; a slot tries each of its
 * candidate spans. Success needs every token consumed. [onHit] receives each complete parse and answers true to stop
 * the whole walk, which [run] then reports as true.
 */
private class RuleWalk(
    private val rule: FlatRule,
    private val slots: Map<String, SlotSpec>,
    private val tokens: GrammarTokens,
    private val onHit: (RuleVerdict.One) -> Boolean,
) {
    private val bound = ArrayList<Bound>()

    fun run(): Boolean = walk(0, 0)

    private fun walk(element: Int, position: Int): Boolean = when {
        element == rule.elements.size -> position == tokens.tokens.size && onHit(hit())
        else -> when (val next = rule.elements[element]) {
            is RuleElement.Word -> position < tokens.tokens.size && next.key == tokens.tokens[position].key &&
                walk(element + 1, position + 1)
            is RuleElement.Slot -> bind(next, element, position)
        }
    }

    private fun bind(slot: RuleElement.Slot, element: Int, position: Int): Boolean {
        val candidates = slots[slot.name]?.candidates(rule.language, tokens, position).orEmpty()
        return candidates.any { candidate ->
            bound.add(Bound(slot.name, candidate, position))
            val stop = walk(element + 1, candidate.end)
            bound.removeAt(bound.size - 1)
            stop
        }
    }

    private fun hit(): RuleVerdict.One {
        val bindings = bound.map { SlotBinding(it.name, it.candidate.value, tokens.surface(it.from, it.candidate.end)) }
        return RuleVerdict.One(rule, JsonObject(bindings.associate { it.name to it.value }), bindings)
    }
}
