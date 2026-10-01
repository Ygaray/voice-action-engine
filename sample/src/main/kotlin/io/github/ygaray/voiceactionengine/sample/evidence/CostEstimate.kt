package io.github.ygaray.voiceactionengine.sample.evidence

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import java.util.Locale

private const val TOKENS_PER_MILLION = 1_000_000.0
private const val ROUNDING_SCALE = 100_000.0
private const val UNKNOWN = "unknown"

/** Dollars per million tokens for each of the four usage buckets. */
private class Price(val input: Double, val cacheWrite: Double, val cacheRead: Double, val output: Double)

/**
 * An estimate of what a run cost, from the usage the providers reported. It is an estimate for the report-back line,
 * not a bill: a model without a price here reports `unknown`.
 *
 * Prices, dollars per million tokens, as read on 2026-10-01 from platform.claude.com (pricing) and the
 * developers.openai.com model page:
 * - `claude-haiku-4-5`: 1.00 input, 1.25 cache write, 0.10 cache read, 5.00 output.
 * - `gpt-5.4-mini`: 0.75 input, 0.075 cached input, 4.50 output (OpenAI has no cache-write charge).
 * - `openai/gpt-5.4-mini` on OpenRouter: assumed equal to the upstream price (research A4).
 */
internal object CostEstimate {
    private val haiku = Price(input = 1.00, cacheWrite = 1.25, cacheRead = 0.10, output = 5.00)
    private val gptMini = Price(input = 0.75, cacheWrite = 0.0, cacheRead = 0.075, output = 4.50)

    private val prices: Map<Pair<String, String>, Price> = mapOf(
        (ProviderId.ANTHROPIC.value to "claude-haiku-4-5") to haiku,
        (ProviderId.OPENAI.value to "gpt-5.4-mini") to gptMini,
        (ProviderId.OPENROUTER.value to "openai/gpt-5.4-mini") to gptMini,
    )

    /** Dollars for one turn's [usage], rounded to 5 places, or null when the provider and model have no price here. */
    fun usd(provider: ProviderId, model: String, usage: Usage): Double? {
        val price = prices[provider.value to model] ?: return null
        val dollars = (
            usage.inputUncached * price.input +
                usage.cacheWrite * price.cacheWrite +
                usage.cacheRead * price.cacheRead +
                usage.output * price.output
            ) / TOKENS_PER_MILLION
        return Math.round(dollars * ROUNDING_SCALE) / ROUNDING_SCALE
    }

    /** Dollars for all [turns]; null when any turn has no provider, no model or no price. */
    fun usd(turns: List<TurnRecord>): Double? {
        var sum = 0.0
        for (turn in turns) {
            val provider = turn.provider ?: return null
            val model = turn.model ?: return null
            sum += usd(provider, model, turn.usage) ?: return null
        }
        return Math.round(sum * ROUNDING_SCALE) / ROUNDING_SCALE
    }

    /** The estimate as the evidence token: five decimals, or `unknown`. */
    fun format(usd: Double?): String = if (usd == null) UNKNOWN else String.format(Locale.ROOT, "%.5f", usd)
}
