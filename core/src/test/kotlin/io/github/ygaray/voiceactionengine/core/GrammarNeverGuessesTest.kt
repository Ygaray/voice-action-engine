package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarMatch
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private const val LARGEST = 999_999L
private const val MUTATIONS = 3_000
private const val MINIMUM_MUTATIONS = 2_000

// Words that appear in no phrasing, synonym, filler or number word of the pack below.
private val foreignWords = listOf("zebra", "quokka", "nimbus", "fjord", "gizmo", "plinth")

private val forbiddenName = Regex("score|confidence|threshold|best|closest|fuzzy", RegexOption.IGNORE_CASE)

// One utterance per row, with its language label. Numbers are single digit tokens: dropping a word of a spoken number
// can leave another valid number, which is a different, still correct, reading and not a guess. There is no text slot
// here for the same reason (a free-text slot takes any words by design).
private val corpus = listOf(
    "turn on the light" to "en",
    "turn off the light" to "en",
    "delete everything" to "en",
    "set the counter to 21" to "en",
    "set counter to 21" to "en",
    "set the level to 2.5" to "en",
    "set mode to economy" to "en",
    "set mode to eco mode" to "en",
    "set mode to turbo" to "en",
    "enciende la luz" to "es",
    "apaga la luz" to "es",
    "borra todo" to "es",
    "pon el contador en 21" to "es",
    "pon el nivel en 2,5" to "es",
    "pon modo economia" to "es",
    "pon modo eco" to "es",
    "pon modo turbo" to "es",
)

private val pack = GrammarPack {
    intent("light_on") {
        en("turn on the light")
        es("enciende la luz")
    }
    intent("light_off") {
        en("turn off the light")
        es("apaga la luz")
    }
    intent("delete_all") {
        en("delete everything")
        es("borra todo")
    }
    intent("set_counter") {
        integer("count", 0, LARGEST)
        en("set [the] counter to {count}")
        es("pon el contador en {count}")
    }
    intent("set_level") {
        decimal("level", 0.0, 100.0)
        en("set the level to {level}")
        es("pon el nivel en {level}")
    }
    intent("set_mode") {
        choice("mode") {
            option("eco") {
                en("economy", "eco mode")
                es("economia", "eco")
            }
            option("turbo") {
                en("turbo")
                es("turbo")
            }
        }
        en("set mode to {mode}")
        es("pon modo {mode}")
    }
}

/**
 * GRAM-03: whatever is done to a matching utterance, the tier either matches a declared phrasing exactly or says
 * nothing. There is no nearest phrasing to fall back on.
 */
class GrammarNeverGuessesTest {

    // Utterances the pack accepts, folded as the pack folds them (these are already plain lower case).
    private val accepted: Set<String> = corpus.map { it.first }.toSet()

    @Test
    fun theCorpusItselfMatches() {
        for ((text, label) in corpus) {
            assertNotNull(text, pack.match(text, label))
        }
    }

    @Test
    fun anInsertedForeignWordADeletedWordOrASwappedPairNeverMatchesUnlessItIsAnotherDeclaredUtterance() {
        val random = Random(20_261_006)
        val kinds = IntArray(3)
        var checked = 0
        repeat(MUTATIONS) {
            val (text, label) = corpus[random.nextInt(corpus.size)]
            val words = text.split(' ')
            val kind = random.nextInt(kinds.size)
            val mutant = mutate(words, kind, random).joinToString(" ")
            kinds[kind]++
            if (mutant !in accepted) {
                assertNull("\"$mutant\" ($label) from \"$text\"", pack.match(mutant, label))
                assertNull("\"$mutant\" (null) from \"$text\"", pack.match(mutant, null))
                checked++
            }
        }
        assertTrue("only $checked mutants checked", checked >= MINIMUM_MUTATIONS)
        assertTrue("every kind used: ${kinds.toList()}", kinds.all { it > 0 })
    }

    private fun mutate(words: List<String>, kind: Int, random: Random): List<String> = when (kind) {
        0 -> words.toMutableList().also { it.add(random.nextInt(words.size + 1), foreignWords.random(random)) }
        1 -> words.toMutableList().also { it.removeAt(random.nextInt(words.size)) }
        else -> words.toMutableList().also {
            val at = random.nextInt(words.size - 1)
            val held = it[at]
            it[at] = it[at + 1]
            it[at + 1] = held
        }
    }

    @Test
    fun noPublicMethodOfTheTierOrItsPartsIsNamedLikeAGuess() {
        for (type in listOf(GrammarPack::class.java, GrammarMatch::class.java, LocalGrammarStrategy::class.java)) {
            for (method in type.methods) {
                assertTrue("${type.simpleName}.${method.name}", !forbiddenName.containsMatchIn(method.name))
            }
        }
    }

    @Test
    fun theMatchDoesNotChangeBetweenCalls() {
        for ((text, label) in corpus) {
            assertEquals(text, pack.match(text, label)?.toString(), pack.match(text, label)?.toString())
        }
    }
}
