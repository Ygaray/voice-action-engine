package io.github.ygaray.voiceactionengine.core.strategy.grammar.number

import java.math.BigDecimal

/** Strict whole-span number parsing over folded token keys; the one entry point the slot code uses. */
internal object NumberWords {
    fun integer(keys: List<String>, language: String): Long? = null

    fun decimal(keys: List<String>, language: String): BigDecimal? = null

    fun longestPhrase(language: String): Int = 0
}
