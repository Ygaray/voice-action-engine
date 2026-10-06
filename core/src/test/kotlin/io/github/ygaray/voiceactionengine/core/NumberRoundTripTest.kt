package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.number.NumberWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val LARGEST = 999_999
private const val EVERY_NUMBER = 1_000_000
private const val FNV_OFFSET = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L

/**
 * Parses what an independent speller writes, for every n in 0..999,999 and in both languages. The speller shares no
 * table with the parsers, so a slip in either shows up as a mismatch on the first n it touches.
 */
class NumberRoundTripTest {

    private class Sweep(val checked: Int, val distinct: Int, val longest: Int)

    private fun fingerprint(words: List<String>): Long {
        var hash = FNV_OFFSET
        for (word in words) {
            for (c in word) hash = (hash xor c.code.toLong()) * FNV_PRIME
            hash = (hash xor ' '.code.toLong()) * FNV_PRIME
        }
        return hash
    }

    /** Parses `spell(n)` for every n; returns how many were checked and how many different spellings they were. */
    private fun sweep(language: String, spell: (Int) -> List<String>): Sweep {
        val fingerprints = LongArray(LARGEST + 1)
        var longest = 0
        for (n in 0..LARGEST) {
            val words = spell(n)
            val parsed = NumberWords.integer(words, language)
            if (parsed != n.toLong()) fail("$language: parse(spell($n)) = $parsed for $words")
            fingerprints[n] = fingerprint(words)
            longest = maxOf(longest, words.size)
        }
        fingerprints.sort()
        val distinct = 1 + (1..LARGEST).count { fingerprints[it] != fingerprints[it - 1] }
        return Sweep(LARGEST + 1, distinct, longest)
    }

    private fun assertWholeRange(language: String, sweep: Sweep) {
        assertEquals("$language numbers swept", EVERY_NUMBER, sweep.checked)
        assertEquals("$language distinct spellings", EVERY_NUMBER, sweep.distinct)
        val phrase = NumberWords.longestPhrase(language)
        assertTrue("$language longestPhrase $phrase < longest spelling ${sweep.longest}", phrase >= sweep.longest)
    }

    private fun assertDigitsRoundTrip(language: String) {
        for (n in 0..LARGEST) {
            val parsed = NumberWords.integer(listOf(n.toString()), language)
            if (parsed != n.toLong()) fail("$language: parse(digits($n)) = $parsed")
        }
    }

    @Test
    fun englishWordsRoundTripAcrossTheWholeRange() {
        assertWholeRange("en", sweep("en", ::spellEn))
    }

    @Test
    fun englishWordsWithAndRoundTripAcrossTheWholeRange() {
        assertWholeRange("en", sweep("en", ::spellEnWithAnd))
    }

    @Test
    fun englishDigitsRoundTripAcrossTheWholeRange() {
        assertDigitsRoundTrip("en")
    }
}
