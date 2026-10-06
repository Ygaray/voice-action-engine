package io.github.ygaray.voiceactionengine.core.strategy.grammar

// A template with more slots than this is refused; relaxing it later only accepts more packs.
private const val SLOTS_PER_TEMPLATE = 4

// Build-time checks of a pack's declarations. Every fault is an IllegalArgumentException from `require`, and every
// message carries tool names, template text and filler text: authoring data, never anything from a transcript.

/** Each intent needs a distinct non-blank tool name and at least one phrasing in some language. */
internal fun validateIntents(intents: List<IntentSpec>) {
    val names = HashSet<String>()
    for (intent in intents) {
        require(intent.toolName.isNotBlank()) { "GrammarPack: an intent's tool name must not be blank" }
        require(names.add(intent.toolName)) { "GrammarPack: tool ${intent.toolName} is declared twice" }
        require(intent.en.isNotEmpty() || intent.es.isNotEmpty()) {
            "GrammarPack: tool ${intent.toolName} declares no phrasing"
        }
    }
}

/** One declared phrasing must say something. */
internal fun validateTemplateText(toolName: String, language: String, template: String) {
    require(template.isNotBlank()) { "GrammarPack: tool $toolName declares a blank $language phrasing" }
}

/**
 * The folded word sequences of the declared fillers of one language, longest first. A blank filler, or one that holds
 * no word once folded, is refused: it could never be told apart from nothing.
 */
internal fun foldFillers(language: String, phrases: List<String>): List<List<String>> =
    phrases.map { phrase ->
        val keys = tokenize(phrase).tokens.map { it.key }
        require(keys.isNotEmpty()) { "GrammarPack: a $language filler is blank or holds no word: \"$phrase\"" }
        keys
    }.distinct().sortedByDescending { it.size }

/**
 * Checks one flat rule of [template]: it holds a literal word on every path, and it neither begins nor ends with a
 * declared filler, which stripping would remove from the transcript and so leave the rule unable to match.
 */
internal fun validateFlat(flat: FlatRule, template: String, fillers: List<List<String>>) {
    require(flat.elements.any { it is RuleElement.Word }) {
        "GrammarPack: tool ${flat.toolName} template \"$template\" can match with no literal word"
    }
    val leading = flat.elements.takeWhile { it is RuleElement.Word }.map { (it as RuleElement.Word).key }
    val trailing = flat.elements.takeLastWhile { it is RuleElement.Word }.map { (it as RuleElement.Word).key }
    for (filler in fillers) {
        require(leading.take(filler.size) != filler) { fillerFault(flat, template, filler, "begins") }
        require(trailing.takeLast(filler.size) != filler) { fillerFault(flat, template, filler, "ends") }
    }
}

/** Every `{slot}` of [flat] must be one [intent] declares. */
internal fun validateSlotRefs(flat: FlatRule, template: String, intent: IntentSpec) {
    for (element in flat.elements) {
        if (element is RuleElement.Slot) {
            require(intent.slots.any { it.name == element.name }) {
                "GrammarPack: tool ${flat.toolName} template \"$template\" uses the slot {${element.name}}, " +
                    "which the intent does not declare"
            }
        }
    }
}

private fun fillerFault(flat: FlatRule, template: String, filler: List<String>, edge: String): String =
    "GrammarPack: tool ${flat.toolName} ${flat.language} template \"$template\" $edge with the declared filler " +
        "\"${filler.joinToString(" ")}\"; stripping would remove it, so write it inside [..] or drop the filler"

/**
 * Adds [flat] to [compiled] by its signature. The same words declared again by the same tool are one rule; declared
 * by two tools they are refused, because a transcript could not choose between them.
 */
internal fun admitFlat(compiled: MutableMap<String, FlatRule>, flat: FlatRule, template: String) {
    val existing = compiled.putIfAbsent(flat.signature, flat) ?: return
    require(existing.toolName == flat.toolName) {
        "GrammarPack: tools ${existing.toolName} and ${flat.toolName} declare the same ${flat.language} " +
            "phrasing: $template"
    }
}

/**
 * Checks one compiled phrasing against its intent's slot use: no more than [SLOTS_PER_TEMPLATE] slots, none twice,
 * every [required] slot present (a slot only inside `[ ]` in some template is optional, its key omitted when
 * absent), and no two open-span slots (numbers and text) side by side without a literal word between them.
 */
internal fun validateRuleSlots(rule: FlatRule, required: Set<String>, specs: Map<String, SlotSpec>) {
    val where = "tool ${rule.toolName} ${rule.language} template \"${rule.template}\""
    val names = rule.elements.filterIsInstance<RuleElement.Slot>().map { it.name }
    require(names.size <= SLOTS_PER_TEMPLATE) {
        "GrammarPack: $where holds ${names.size} slots, and a template may hold at most $SLOTS_PER_TEMPLATE"
    }
    val repeated = names.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()
    require(repeated == null) { "GrammarPack: $where uses the slot {$repeated} more than once in one reading" }
    val missing = required.firstOrNull { it !in names }
    require(missing == null) {
        "GrammarPack: $where lacks the required slot {$missing}; put it inside [ ] to make it optional"
    }
    for ((left, right) in rule.elements.zipWithNext()) {
        require(!(isOpenSpan(left, specs) && isOpenSpan(right, specs))) {
            "GrammarPack: $where has two open-span slots next to each other; put a literal word between them"
        }
    }
}

private fun isOpenSpan(element: RuleElement, specs: Map<String, SlotSpec>): Boolean =
    element is RuleElement.Slot && specs[element.name]?.openSpan == true

/**
 * The ambiguity self-check of one language: every flat rule's own example goes through the real [matcher], and the
 * example must be read as that rule's own result and no other. A rule another rule can also read, with a different
 * tool or different arguments, makes the pack refuse to build, naming both. Examples are made from the rules alone.
 */
internal fun validateNoOverlap(
    rules: List<FlatRule>,
    matcher: RuleMatcher,
    slots: Map<String, Map<String, SlotSpec>>,
    fillers: List<List<String>>,
) {
    val language = rules.firstOrNull()?.language ?: return
    val sentinel = sentinelWord(declaredWords(rules, slots, language, fillers))
    for (rule in rules) {
        for (example in examplesOf(rule, slots[rule.toolName].orEmpty(), sentinel)) {
            val readings = matcher.readings(tokenize(example))
            val own = readings.firstOrNull { it.rule === rule }
            val clash = readings.firstOrNull {
                own == null || it.rule.toolName != own.rule.toolName || it.arguments != own.arguments
            }
            if (clash != null) throw IllegalArgumentException(overlapFault(rule, clash.rule))
        }
    }
}
