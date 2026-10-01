package io.github.ygaray.voiceactionengine.sample.net

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptObserver
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptObserver
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetedProvider
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import okhttp3.OkHttpClient
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The leg in progress: which leg it is, whether it spends from the optional pool, and the HTTP attempts seen so far.
 * One leg runs at a time, so the tap holds at most one of these.
 */
internal class LegContext(val leg: LegId, val optional: Boolean) {
    private val seen = CopyOnWriteArrayList<AttemptRecord>()

    /** The attempts recorded so far, in order; a snapshot. */
    val attempts: List<AttemptRecord> get() = seen.toList()

    internal fun add(attempt: AttemptRecord) {
        seen.add(attempt)
    }

    override fun toString(): String = "LegContext(leg=${leg.wire}, optional=$optional, attempts=${seen.size})"
}

/**
 * Receives every HTTP attempt a transport reports. Each one is counted against the budget (the only place that sees real
 * requests), kept with the current leg for the verdict, and written as a `VAE_ATTEMPT` line.
 */
internal class AttemptTap(private val budget: RequestBudget, private val sink: EvidenceSink) {
    /** The leg in progress, or null between legs. */
    @Volatile
    var current: LegContext? = null

    /**
     * Keeps [attempt] with the current leg, emits its evidence line, then counts it. The count goes last so a store that
     * fails cannot hide the attempt; if it fails the budget refuses every further call and a loud line says so.
     */
    fun record(attempt: AttemptRecord) {
        val context = current
        if (context != null) {
            context.add(attempt)
            sink.emit(EvidenceLine.attempt(context.leg, attempt))
        }
        if (!budget.record(attempt.provider, context?.optional ?: false)) sink.emit(EvidenceLine.budgetFault())
    }

    /** The observer for the Anthropic transport. */
    fun anthropicObserver(): AnthropicAttemptObserver = AnthropicAttemptObserver { attempt ->
        record(AttemptRecord(ProviderId.ANTHROPIC, attempt.number, attempt.kind.value, attempt.httpStatus, null, 0))
    }

    /** The observer for a Chat Completions transport of [provider]. */
    fun chatObserver(provider: ProviderId): ChatCompletionsAttemptObserver = ChatCompletionsAttemptObserver { attempt ->
        record(
            AttemptRecord(
                provider,
                attempt.number,
                attempt.kind.value,
                attempt.httpStatus,
                attempt.finishReason,
                attempt.toolCalls,
            ),
        )
    }

    override fun toString(): String = "AttemptTap"
}

/**
 * Builds the three real providers on one OkHttp client. The client has no interceptor of any kind, so no key or body can
 * be logged by it. Each provider reports its attempts to the [AttemptTap] and is wrapped so a call that could break the
 * request ceiling is refused before it is sent.
 */
internal object ProviderFactory {
    /** The providers, in the order Anthropic, OpenAI, OpenRouter. Builds nothing that touches the network. */
    fun create(tap: AttemptTap, budget: RequestBudget, client: OkHttpClient = OkHttpClient()): List<AiProvider> {
        val optional = { tap.current?.optional == true }
        val anthropic = AnthropicProvider {
            httpClient = client
            attemptObserver = tap.anthropicObserver()
        }
        val openAi = ChatCompletionsProvider.openAi {
            httpClient = client
            attemptObserver = tap.chatObserver(ProviderId.OPENAI)
        }
        val openRouter = ChatCompletionsProvider.openRouter {
            httpClient = client
            attemptObserver = tap.chatObserver(ProviderId.OPENROUTER)
        }
        return listOf(anthropic, openAi, openRouter).map { BudgetedProvider(it, budget, optional) }
    }
}
