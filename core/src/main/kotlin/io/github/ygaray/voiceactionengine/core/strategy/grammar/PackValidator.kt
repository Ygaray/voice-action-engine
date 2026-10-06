package io.github.ygaray.voiceactionengine.core.strategy.grammar

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
 * Checks one flat rule of [toolName]'s [template]: it holds a literal word on every path, and it neither begins nor ends with a declared filler, which stripping would remove from the transcript and so
 * leave the rule unable to match.
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
