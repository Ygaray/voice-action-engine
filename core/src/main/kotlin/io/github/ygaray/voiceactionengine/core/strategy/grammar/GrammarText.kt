package io.github.ygaray.voiceactionengine.core.strategy.grammar

import java.text.Normalizer

private const val ACCENTED = "áàâäéèêëíìîïóòôöúùûü"
private const val PLAIN = "aaaaeeeeiiiioooouuuu"
private const val EDGE_PUNCTUATION = ".,;:!?¿¡\"“”‘’«»()…"
private const val TERMINATORS = ".!?;"
private const val HYPHENS = "-–—"
private const val APOSTROPHES = "'’"

/**
 * One word of a folded text: its comparison [key] and the `[start, end)` span of its surface in the NFC text.
 * [breakBefore] is true when a sentence terminator stood between the previous word and this one.
 */
internal class GrammarToken(val key: String, val start: Int, val end: Int, val breakBefore: Boolean = false)

/**
 * A text split into [tokens] over its NFC form [nfc]. [clauseBreak] is true when a sentence terminator (`.`, `!`, `?`
 * or `;`) stood between two of these words, so they are more than one clause. It is worked out from the tokens held,
 * so a slice that dropped a leading or trailing filler no longer counts a terminator that sat next to the filler.
 */
internal class GrammarTokens(val nfc: String, val tokens: List<GrammarToken>) {
    val clauseBreak: Boolean = (1 until tokens.size).any { tokens[it].breakBefore }

    /** The original text from token [from] up to but excluding token [to]: case, accents and inner punctuation kept. */
    fun surface(from: Int, to: Int): String = nfc.substring(tokens[from].start, tokens[to - 1].end)

    /** The folded keys of tokens [from] up to but excluding [to]. */
    fun keys(from: Int, to: Int): List<String> = tokens.subList(from, to).map { it.key }

    /** The same text restricted to tokens [from] up to but excluding [to]; surface indices count from the new start. */
    fun slice(from: Int, to: Int): GrammarTokens = GrammarTokens(nfc, tokens.subList(from, to))
}

/**
 * The comparison key of one word: NFC, locale-invariant lower case, vowel accents folded by an explicit map (`á` to
 * `a`, and so on), `ñ` kept (`año` is not `ano`), apostrophes dropped. Transcripts, declared words and fillers all go
 * through this one function, so they can only agree when they are the same word.
 */
internal fun foldKey(word: String): String {
    val lower = Normalizer.normalize(word, Normalizer.Form.NFC).lowercase()
    val folded = StringBuilder(lower.length)
    for (c in lower) {
        if (c !in APOSTROPHES) folded.append(foldVowel(c))
    }
    return folded.toString()
}

private fun foldVowel(c: Char): Char {
    val at = ACCENTED.indexOf(c)
    return if (at >= 0) PLAIN[at] else c
}

/**
 * Splits [text] into folded words with offsets, by these rules in order: NFC; split on whitespace; split on a hyphen
 * only between two letters (`twenty-one` is two words, `5-10`, `-5` and `twenty-1` stay one); strip the edge
 * punctuation `. , ; : ! ? ¿ ¡ " “ ” ‘ ’ « » ( ) …` from each piece and drop pieces left empty; fold each key with
 * [foldKey]. Symbols such as `%` and inner separators such as the dot in `2.5` are never stripped.
 *
 * A terminator between two words marks the later one [GrammarToken.breakBefore], which sets
 * [GrammarTokens.clauseBreak]: "turn on the light. delete everything" is two commands and must not match one
 * phrasing. A comma is not a break.
 */
internal fun tokenize(text: String): GrammarTokens = TokenScan(Normalizer.normalize(text, Normalizer.Form.NFC)).run()

private class TokenScan(private val nfc: String) {
    private val tokens = ArrayList<GrammarToken>()
    private var terminatorSeen = false

    fun run(): GrammarTokens {
        var at = 0
        while (at < nfc.length) {
            if (nfc[at].isWhitespace()) {
                at++
            } else {
                val end = runEnd(at)
                splitRun(at, end)
                at = end
            }
        }
        return GrammarTokens(nfc, tokens)
    }

    private fun runEnd(from: Int): Int {
        var end = from
        while (end < nfc.length && !nfc[end].isWhitespace()) end++
        return end
    }

    private fun splitRun(start: Int, end: Int) {
        var pieceStart = start
        for (at in start + 1 until end - 1) {
            if (nfc[at] in HYPHENS && nfc[at - 1].isLetter() && nfc[at + 1].isLetter()) {
                piece(pieceStart, at)
                pieceStart = at + 1
            }
        }
        piece(pieceStart, end)
    }

    // Trailing edge first, so a piece made only of punctuation still reports the terminator it held.
    private fun piece(from: Int, to: Int) {
        var end = to
        var terminated = false
        while (end > from && nfc[end - 1] in EDGE_PUNCTUATION) {
            terminated = terminated || nfc[end - 1] in TERMINATORS
            end--
        }
        var start = from
        while (start < end && nfc[start] in EDGE_PUNCTUATION) start++
        val key = if (start < end) foldKey(nfc.substring(start, end)) else ""
        if (key.isNotEmpty()) add(GrammarToken(key, start, end))
        if (terminated) terminatorSeen = true
    }

    private fun GrammarToken.afterBreak() = GrammarToken(key, start, end, breakBefore = true)

    private fun add(token: GrammarToken) {
        tokens.add(if (terminatorSeen && tokens.isNotEmpty()) token.afterBreak() else token)
        terminatorSeen = false
    }
}
