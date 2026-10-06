package io.github.ygaray.voiceactionengine.core.strategy.grammar

// Build-time bound on how many flat sequences one template may expand to. Not a runtime limit: it only stops an
// authoring mistake (a long chain of alternatives) from exploding while the pack is built.
private const val EXPANSION_LIMIT = 256

/** One element of a flat rule: a folded literal word, or a slot to fill. */
internal sealed class RuleElement {
    /** A literal word, already folded by [foldKey]. */
    class Word(val key: String) : RuleElement()

    /** A slot reference. */
    class Slot(val name: String) : RuleElement()
}

/** One flat phrasing: a tool, a language and the elements a transcript must equal, with an opaque [ruleId]. */
internal class FlatRule(
    val toolName: String,
    val language: String,
    val elements: List<RuleElement>,
    val ruleId: String,
) {
    /** The elements as one comparable string; two rules with equal signatures read the same phrasing. */
    val signature: String
        get() = elements.joinToString(" ") {
            when (it) {
                is RuleElement.Word -> it.key
                is RuleElement.Slot -> "{${it.name}}"
            }
        }
}

/** A named sub-rule as the app declared it: one or more templates that are alternatives of each other. */
internal class RuleSpec(val name: String, val templates: List<String>)

/**
 * Turns parsed templates into flat sequences of folded words and slot references: optionals and groups are expanded,
 * `<rule>` references are replaced by the same language's sub-rule. A template that expands past [EXPANSION_LIMIT]
 * sequences is refused.
 */
internal class TemplateExpander(private val language: String, private val rules: Map<String, List<TemplateNode>>) {

    /** The flat rules of one template of tool [toolName]; sequences that read the same are kept once. */
    fun expand(toolName: String, index: Int, template: String): List<FlatRule> {
        val sequences = Walk(template, rules).sequences(parseTemplate(template), false)
        return sequences.map { FlatRule(toolName, language, it, "$toolName:$language:$index") }
            .distinctBy { it.signature }
    }

    /** Expands one template of a declared sub-rule, so a fault in a rule nobody references still fails the build. */
    fun checkRuleTemplate(template: String) {
        Walk(template, rules).sequences(parseTemplate(template), true)
    }
}

// The one sequence with no elements: what an absent optional contributes, and where a product starts.
private val NOTHING: List<List<RuleElement>> = listOf(emptyList())

private class Walk(private val template: String, private val rules: Map<String, List<TemplateNode>>) {
    private val visiting = ArrayList<String>()

    fun sequences(node: TemplateNode, inRule: Boolean): List<List<RuleElement>> = when (node) {
        is TemplateNode.Word -> listOf(tokenize(node.text).tokens.map { RuleElement.Word(it.key) })
        is TemplateNode.Optional -> union(NOTHING, alternatives(node.alternatives, inRule))
        is TemplateNode.Group -> alternatives(node.alternatives, inRule)
        is TemplateNode.SlotRef -> slot(node, inRule)
        is TemplateNode.RuleRef -> reference(node.name)
        is TemplateNode.Sequence -> node.items.fold(NOTHING) { sofar, item ->
            product(sofar, sequences(item, inRule))
        }
    }

    private fun slot(node: TemplateNode.SlotRef, inRule: Boolean): List<List<RuleElement>> {
        require(!inRule) { fault("a sub-rule cannot hold the slot {${node.name}}; keep slots in the intent") }
        return listOf(listOf(RuleElement.Slot(node.name)))
    }

    private fun reference(name: String): List<List<RuleElement>> {
        val templates = requireNotNull(rules[name]) { fault("unknown rule <$name>") }
        require(name !in visiting) {
            fault("rule <$name> refers back to itself (cycle: ${visiting.joinToString(" > ")})")
        }
        visiting.add(name)
        val expanded = alternatives(templates, true)
        visiting.removeAt(visiting.size - 1)
        return expanded
    }

    private fun alternatives(nodes: List<TemplateNode>, inRule: Boolean): List<List<RuleElement>> =
        nodes.fold(emptyList<List<RuleElement>>()) { sofar, node -> union(sofar, sequences(node, inRule)) }

    private fun union(left: List<List<RuleElement>>, right: List<List<RuleElement>>): List<List<RuleElement>> {
        require(left.size + right.size <= EXPANSION_LIMIT) { tooBig() }
        return left + right
    }

    private fun product(left: List<List<RuleElement>>, right: List<List<RuleElement>>): List<List<RuleElement>> {
        require(left.size.toLong() * right.size <= EXPANSION_LIMIT) { tooBig() }
        return left.flatMap { head -> right.map { tail -> head + tail } }
    }

    private fun tooBig(): String = fault("it expands to more than $EXPANSION_LIMIT sequences; split it with sub-rules")

    private fun fault(reason: String): String = "GrammarPack: template \"$template\": $reason"
}

/** Parses the declared sub-rules of one language into templates by name; a bad or repeated name is refused. */
internal fun parseRules(specs: List<RuleSpec>): Map<String, List<TemplateNode>> {
    val parsed = LinkedHashMap<String, List<TemplateNode>>()
    for (spec in specs) {
        require(isTemplateName(spec.name)) {
            "GrammarPack: rule name \"${spec.name}\" is not valid (lower case letters, digits, underscore)"
        }
        require(spec.templates.isNotEmpty()) { "GrammarPack: rule <${spec.name}> declares no template" }
        require(!parsed.containsKey(spec.name)) { "GrammarPack: rule <${spec.name}> is declared twice" }
        parsed[spec.name] = spec.templates.map { parseTemplate(it) }
    }
    return parsed
}

/**
 * Compiles every template of one language into flat rules. The same flat sequence declared twice by one tool is kept
 * once; declared by two tools it is refused, because the transcript could not choose between them.
 */
internal fun compileLanguage(
    language: String,
    intents: List<IntentSpec>,
    specs: List<RuleSpec>,
    templatesOf: (IntentSpec) -> List<String>,
): List<FlatRule> {
    val expander = TemplateExpander(language, parseRules(specs))
    for (spec in specs) {
        for (template in spec.templates) expander.checkRuleTemplate(template)
    }
    val compiled = LinkedHashMap<String, FlatRule>()
    for (intent in intents) {
        for ((index, template) in templatesOf(intent).withIndex()) {
            require(template.isNotBlank()) {
                "GrammarPack: tool ${intent.toolName} declares a blank $language phrasing"
            }
            expander.expand(intent.toolName, index, template).forEach { admit(compiled, it, template) }
        }
    }
    return compiled.values.toList()
}

private fun admit(compiled: MutableMap<String, FlatRule>, flat: FlatRule, template: String) {
    require(flat.elements.none { it is RuleElement.Slot }) {
        "GrammarPack: tool ${flat.toolName} template \"$template\" uses a slot, and no slot kind is available yet"
    }
    require(flat.elements.any { it is RuleElement.Word }) {
        "GrammarPack: tool ${flat.toolName} template \"$template\" can match with no literal word"
    }
    val existing = compiled[flat.signature]
    if (existing == null) {
        compiled[flat.signature] = flat
    } else {
        require(existing.toolName == flat.toolName) {
            "GrammarPack: tools ${existing.toolName} and ${flat.toolName} declare the same ${flat.language} " +
                "phrasing: $template"
        }
    }
}
