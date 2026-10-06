package io.github.ygaray.voiceactionengine.core.strategy.grammar

private const val OPEN_OPTIONAL = '['
private const val CLOSE_OPTIONAL = ']'
private const val OPEN_GROUP = '('
private const val CLOSE_GROUP = ')'
private const val OPEN_SLOT = '{'
private const val CLOSE_SLOT = '}'
private const val OPEN_RULE = '<'
private const val CLOSE_RULE = '>'
private const val ALTERNATIVE = '|'
private const val METACHARACTERS = "[](){}<>|"
private const val SEQUENCE_ENDS = "|])"

/** One node of a parsed template. */
internal sealed class TemplateNode {
    /** A run of literal text; it may hold several words once folded. */
    class Word(val text: String) : TemplateNode()

    /** Zero or one occurrence of any alternative. */
    class Optional(val alternatives: List<TemplateNode>) : TemplateNode()

    /** Exactly one of the alternatives. */
    class Group(val alternatives: List<TemplateNode>) : TemplateNode()

    /** A reference to a typed slot declared on the intent. */
    class SlotRef(val name: String) : TemplateNode()

    /** A reference to a named sub-rule of the same language. */
    class RuleRef(val name: String) : TemplateNode()

    /** Items one after the other. */
    class Sequence(val items: List<TemplateNode>) : TemplateNode()
}

/** True for a slot or rule name: a lower-case letter, then lower-case letters, digits and underscores. */
internal fun isTemplateName(name: String): Boolean =
    name.isNotEmpty() && name[0] in 'a'..'z' && name.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }

/**
 * Parses [text] by this grammar. No regular expression is involved: the parser is one pass over the characters.
 *
 *     template := item+                     items may be separated by any whitespace
 *     item     := word | '[' alt ']' | '(' alt ')' | '{' name '}' | '<' name '>'
 *     alt      := seq ('|' seq)*            an empty alternative is refused
 *     word     := characters other than whitespace and [ ] ( ) { } < > |
 *
 * Every fault throws [IllegalArgumentException] naming the template text.
 */
internal fun parseTemplate(text: String): TemplateNode.Sequence = TemplateParser(text).parse()

private class TemplateParser(private val text: String) {
    private var position = 0

    fun parse(): TemplateNode.Sequence {
        val sequence = sequence()
        require(position == text.length) { fault("unexpected '${text[position]}' at position $position") }
        require(sequence.items.isNotEmpty()) { fault("it has no content") }
        return sequence
    }

    private fun fault(reason: String): String = "GrammarPack: template \"$text\": $reason"

    private fun sequence(): TemplateNode.Sequence {
        val items = ArrayList<TemplateNode>()
        while (true) {
            skipWhitespace()
            val c = text.getOrNull(position)
            if (c == null || c in SEQUENCE_ENDS) break
            items.add(item(c))
        }
        return TemplateNode.Sequence(items)
    }

    private fun item(c: Char): TemplateNode = when (c) {
        OPEN_OPTIONAL -> TemplateNode.Optional(alternatives(CLOSE_OPTIONAL))
        OPEN_GROUP -> TemplateNode.Group(alternatives(CLOSE_GROUP))
        OPEN_SLOT -> TemplateNode.SlotRef(name(CLOSE_SLOT))
        OPEN_RULE -> TemplateNode.RuleRef(name(CLOSE_RULE))
        CLOSE_SLOT, CLOSE_RULE -> throw IllegalArgumentException(fault("unexpected '$c' at position $position"))
        else -> word()
    }

    private fun alternatives(close: Char): List<TemplateNode> {
        position++
        val found = ArrayList<TemplateNode>()
        do {
            val one = sequence()
            require(one.items.isNotEmpty()) { fault("an alternative is empty at position $position") }
            found.add(one)
            val more = text.getOrNull(position) == ALTERNATIVE
            if (more) position++
        } while (more)
        require(text.getOrNull(position) == close) { fault("expected '$close' to close the group opened before it") }
        position++
        return found
    }

    private fun name(close: Char): String {
        val start = ++position
        while (position < text.length && text[position] != close && text[position] !in METACHARACTERS) position++
        require(text.getOrNull(position) == close) { fault("a name is not closed by '$close'") }
        val name = text.substring(start, position)
        require(isTemplateName(name)) { fault("'$name' is not a valid name (lower case letters, digits, underscore)") }
        position++
        return name
    }

    private fun word(): TemplateNode.Word {
        val start = position
        while (position < text.length && !text[position].isWhitespace() && text[position] !in METACHARACTERS) position++
        return TemplateNode.Word(text.substring(start, position))
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) position++
    }
}
