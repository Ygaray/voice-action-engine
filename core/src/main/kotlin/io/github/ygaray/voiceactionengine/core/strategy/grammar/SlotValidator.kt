package io.github.ygaray.voiceactionengine.core.strategy.grammar

// Build-time checks of the slots a pack declares. Every fault is an IllegalArgumentException from `require`, and every
// message carries the tool name, the slot name and the template text: authoring data, never anything from a transcript.

// The number words and digits read by the number parsers reach this far, so a bound beyond it could never be met.
private const val LARGEST_NUMBER = 999_999L

// A text slot is bounded, so `maxWords` has a ceiling: an "unbounded" Int.MAX_VALUE would overflow the span sums and
// the window arithmetic and silently stop matching. Far above any real spoken field.
private const val MAX_TEXT_WORDS = 64

/** Checks every slot declaration of [intents]: names, bounds and choice options. */
internal fun validateSlotDeclarations(intents: List<IntentSpec>) {
    for (intent in intents) {
        val names = HashSet<String>()
        for (declared in intent.slots) {
            require(isTemplateName(declared.name)) {
                "GrammarPack: tool ${intent.toolName} slot name \"${declared.name}\" is not valid " +
                    "(lower case letters, digits, underscore, starting with a letter)"
            }
            require(names.add(declared.name)) {
                "GrammarPack: tool ${intent.toolName} declares the slot ${declared.name} twice"
            }
            validateSpec(intent, declared)
        }
    }
}

/**
 * Every normalize hook names a `text` or `choice` slot its intent declares, once. Number slots already bind a typed
 * value, so a hook there would only change its type. Messages carry the tool and slot names only.
 */
internal fun validateNormalizers(intents: List<IntentSpec>) {
    for (intent in intents) {
        val seen = HashSet<String>()
        for (normalizer in intent.normalizers) {
            val declared = intent.slots.firstOrNull { it.name == normalizer.slot }
            require(declared != null) {
                "GrammarPack: tool ${intent.toolName} normalizes the slot ${normalizer.slot}, which it does not declare"
            }
            require(declared.spec is SlotSpec.TextSlot || declared.spec is SlotSpec.ChoiceSlot) {
                "GrammarPack: tool ${intent.toolName} normalizes the slot ${normalizer.slot}, " +
                    "which is a number slot; normalize applies to text and choice slots only"
            }
            require(seen.add(normalizer.slot)) {
                "GrammarPack: tool ${intent.toolName} declares a normalize hook for the slot ${normalizer.slot} twice"
            }
        }
    }
}

private fun validateSpec(intent: IntentSpec, declared: SlotDecl) {
    val where = "tool ${intent.toolName} slot ${declared.name}"
    when (val spec = declared.spec) {
        is SlotSpec.IntegerSlot -> {
            require(spec.min <= spec.max) { "GrammarPack: $where has min above max" }
            require(spec.min >= 0 && spec.max <= LARGEST_NUMBER) {
                "GrammarPack: $where must stay within 0..$LARGEST_NUMBER"
            }
        }
        is SlotSpec.DecimalSlot -> {
            require(spec.min.isFinite() && spec.max.isFinite()) {
                "GrammarPack: $where has a bound that is not finite"
            }
            require(spec.min <= spec.max) { "GrammarPack: $where has min above max" }
            require(spec.min >= 0.0 && spec.max <= LARGEST_NUMBER) {
                "GrammarPack: $where must stay within 0..$LARGEST_NUMBER"
            }
        }
        is SlotSpec.TextSlot -> require(spec.maxWords in 1..MAX_TEXT_WORDS) {
            "GrammarPack: $where needs maxWords from 1 to $MAX_TEXT_WORDS"
        }
        is SlotSpec.ChoiceSlot -> validateChoice(intent, declared.name, spec)
    }
}

private fun validateChoice(intent: IntentSpec, slot: String, choice: SlotSpec.ChoiceSlot) {
    val where = "tool ${intent.toolName} choice slot $slot"
    require(choice.options.isNotEmpty()) { "GrammarPack: $where has no option" }
    val ids = HashSet<String>()
    for (option in choice.options) {
        require(option.id.isNotBlank()) { "GrammarPack: $where has an option with a blank id" }
        require(ids.add(option.id)) { "GrammarPack: $where declares the option ${option.id} twice" }
    }
    for ((language, used) in listOf("en" to intent.en.isNotEmpty(), "es" to intent.es.isNotEmpty())) {
        validateSynonyms(where, language, used, choice)
    }
}

private fun validateSynonyms(where: String, language: String, used: Boolean, choice: SlotSpec.ChoiceSlot) {
    val owners = HashMap<List<String>, String>()
    for (option in choice.options) {
        val synonyms = option.synonyms(language)
        require(!used || synonyms.isNotEmpty()) {
            "GrammarPack: $where option ${option.id} has no $language synonym, and the intent has $language phrasings"
        }
        for (synonym in synonyms) {
            require(synonym.keys.isNotEmpty()) {
                "GrammarPack: $where option ${option.id} has a blank $language synonym: \"${synonym.text}\""
            }
            val owner = owners.putIfAbsent(synonym.keys, option.id)
            require(owner == null || owner == option.id) {
                "GrammarPack: $where reads the $language synonym \"${synonym.text}\" under the options $owner and " +
                    option.id
            }
        }
    }
}

/**
 * Checks how [intents] use their declared slots across the compiled [rules] of both languages: every declared slot is
 * used by some template, every slot that appears outside `[ ]` in some template appears exactly once in every
 * expansion of every template of the intent (so both languages bind the same set), no expansion repeats a slot or
 * holds more than four, and no two open-span slots touch with no literal word between them.
 */
internal fun validateSlotUse(intents: List<IntentSpec>, rules: List<FlatRule>) {
    val byTool = rules.groupBy { it.toolName }
    for (intent in intents) {
        val usage = SlotUsage()
        (intent.en + intent.es).forEach { collectSlots(parseTemplate(it), false, usage) }
        for (declared in intent.slots) {
            require(declared.name in usage.used) {
                "GrammarPack: tool ${intent.toolName} declares the slot ${declared.name}, which no template uses"
            }
        }
        val specs = intent.slots.associate { it.name to it.spec }
        byTool[intent.toolName].orEmpty().forEach { validateRuleSlots(it, usage.required, specs) }
    }
}

// The slots of one intent: those any template mentions, and those a template mentions outside `[ ]`.
private class SlotUsage {
    val used = HashSet<String>()
    val required = LinkedHashSet<String>()
}

private fun collectSlots(node: TemplateNode, optional: Boolean, usage: SlotUsage) {
    when (node) {
        is TemplateNode.SlotRef -> {
            usage.used.add(node.name)
            if (!optional) usage.required.add(node.name)
        }
        is TemplateNode.Optional -> node.alternatives.forEach { collectSlots(it, true, usage) }
        is TemplateNode.Group -> node.alternatives.forEach { collectSlots(it, optional, usage) }
        is TemplateNode.Sequence -> node.items.forEach { collectSlots(it, optional, usage) }
        is TemplateNode.Word, is TemplateNode.RuleRef -> Unit
    }
}
