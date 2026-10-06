package io.github.ygaray.voiceactionengine.core.strategy.grammar

import java.math.BigDecimal

// The self-check that refuses overlapping rules at build reads each rule's own example through the real matcher. The
// examples are made from the rule alone: no transcript data is involved, so the messages stay authoring-only.

// The word that fills an open slot in an example; made longer until no declared word equals it.
private const val SENTINEL_START = "zqx"

/** The folded words everything in a pack declares in one language, so an example's sentinel can avoid them all. */
internal fun declaredWords(
    rules: List<FlatRule>,
    slots: Map<String, Map<String, SlotSpec>>,
    language: String,
    fillers: List<List<String>>,
): Set<String> {
    val words = HashSet<String>()
    for (rule in rules) {
        rule.elements.filterIsInstance<RuleElement.Word>().forEach { words.add(it.key) }
    }
    for (spec in slots.values.flatMap { it.values }) {
        if (spec is SlotSpec.ChoiceSlot) {
            spec.options.flatMap { it.synonyms(language) }.forEach { words.addAll(it.keys) }
        }
    }
    fillers.forEach { words.addAll(it) }
    return words
}

/** A word no declared word equals and no number parser reads, made from [declared]. */
internal fun sentinelWord(declared: Set<String>): String {
    var word = SENTINEL_START
    while (word in declared) word += "x"
    return word
}

/**
 * The example transcripts of one flat [rule]: its literal words with each slot filled, once at the smallest value the
 * number slots accept and once at the largest. A choice slot says the first synonym of its first option and a text
 * slot says [sentinel]. Equal examples are kept once.
 */
internal fun examplesOf(
    rule: FlatRule,
    specs: Map<String, SlotSpec>,
    sentinel: String,
): List<String> = listOf(false, true).map { atMax ->
    rule.elements.joinToString(" ") { element ->
        val spec = (element as? RuleElement.Slot)?.let { specs[it.name] }
        exampleWord(element, spec, rule.language, sentinel, atMax)
    }
}.distinct()

private fun exampleWord(
    element: RuleElement,
    spec: SlotSpec?,
    language: String,
    sentinel: String,
    atMax: Boolean,
): String = when {
    element is RuleElement.Word -> element.key
    spec is SlotSpec.IntegerSlot -> (if (atMax) spec.max else spec.min).toString()
    spec is SlotSpec.DecimalSlot -> decimalWord(if (atMax) spec.max else spec.min, language)
    spec is SlotSpec.ChoiceSlot -> choiceWord(spec, language) ?: sentinel
    else -> sentinel
}

// A whole value reads the same in both languages; a fraction uses the language's decimal separator.
private fun decimalWord(value: Double, language: String): String {
    val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    return if (language == "es") plain.replace('.', ',') else plain
}

private fun choiceWord(spec: SlotSpec.ChoiceSlot, language: String): String? =
    spec.options.firstNotNullOfOrNull { option -> option.synonyms(language).firstOrNull() }?.keys?.joinToString(" ")

/** The build-time message for two rules that can read one example with different results. */
internal fun overlapFault(rule: FlatRule, other: FlatRule): String =
    "GrammarPack: ${rule.language} phrasings overlap with different results: tool ${rule.toolName} template " +
        "\"${rule.template}\" (${rule.ruleId}) and tool ${other.toolName} template \"${other.template}\" " +
        "(${other.ruleId}) can read the same words; reword one so a transcript has only one reading"
