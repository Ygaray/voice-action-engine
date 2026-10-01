package io.github.ygaray.voiceactionengine.sample.evidence

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The core legs share this many HTTP requests, in total, across restarts. */
internal const val CORE_REQUEST_CEILING = 33

/** Core plus the one optional probe may never exceed this many HTTP requests. */
internal const val TOTAL_REQUEST_CEILING = 34

/**
 * The most HTTP requests one logical provider call can send: the first, one transient retry and one forced-tool
 * reshape (the Anthropic transport's own ceiling). The spend guard assumes this worst case before it lets a call out.
 */
internal const val PER_CALL_WORST_CASE = 3

/** An Anthropic agentic start is refused for this long after the previous one: the 5-minute cache TTL plus a margin. */
internal const val WARM_WINDOW_SECONDS = 360L

/** The refusal code a [BudgetedProvider] returns instead of sending a request that could break the ceiling. */
internal const val BUDGET_EXHAUSTED = "sample_budget_exhausted"

/**
 * What the budget remembers between app restarts.
 *
 * @property core HTTP requests the core legs have sent.
 * @property optional HTTP requests the optional probe has sent.
 * @property perProvider requests per provider id, core and optional together.
 * @property agenticStartEpochSeconds when the last Anthropic agentic leg started, or null.
 */
internal class BudgetState(
    val core: Int,
    val optional: Int,
    val perProvider: Map<String, Int>,
    val agenticStartEpochSeconds: Long?,
) {
    override fun toString(): String =
        "BudgetState(core=$core, optional=$optional, perProvider=$perProvider, " +
            "agenticStartEpochSeconds=$agenticStartEpochSeconds)"

    companion object {
        /** Nothing spent, nothing started. */
        val EMPTY = BudgetState(0, 0, emptyMap(), null)
    }
}

/** Where the budget keeps its [BudgetState]. */
internal interface BudgetStore {
    /** The saved state, or [BudgetState.EMPTY] when nothing was saved. */
    fun read(): BudgetState

    /** Saves [state], replacing the previous one. */
    fun write(state: BudgetState)
}

private const val FIELD_CORE = "core"
private const val FIELD_OPTIONAL = "optional"
private const val FIELD_AGENTIC_START = "agentic_start"
private const val PROVIDER_PREFIX = "provider."

/**
 * A [BudgetStore] in one small text file, one `key=value` line per field. A write goes to a temp file first and is then
 * moved over the real one, so a force-stop mid-write leaves the old state, never half of a new one. A line it cannot
 * read counts as zero.
 */
internal class FileBudgetStore(private val file: File) : BudgetStore {
    override fun read(): BudgetState {
        if (!file.isFile) return BudgetState.EMPTY
        val lines = try {
            file.readLines(Charsets.UTF_8)
        } catch (unreadable: IOException) {
            return BudgetState.EMPTY
        }
        var core = 0
        var optional = 0
        var start: Long? = null
        val providers = LinkedHashMap<String, Int>()
        for (line in lines) {
            val at = line.indexOf('=')
            if (at <= 0) continue
            val key = line.substring(0, at)
            val value = line.substring(at + 1)
            when {
                key == FIELD_CORE -> core = value.toIntOrNull() ?: 0
                key == FIELD_OPTIONAL -> optional = value.toIntOrNull() ?: 0
                key == FIELD_AGENTIC_START -> start = value.toLongOrNull()
                key.startsWith(PROVIDER_PREFIX) -> providers[key.removePrefix(PROVIDER_PREFIX)] = value.toIntOrNull() ?: 0
            }
        }
        return BudgetState(core, optional, providers, start)
    }

    override fun write(state: BudgetState) {
        val text = buildString {
            append(FIELD_CORE).append('=').append(state.core).append('\n')
            append(FIELD_OPTIONAL).append('=').append(state.optional).append('\n')
            for ((provider, count) in state.perProvider) {
                append(PROVIDER_PREFIX).append(provider).append('=').append(count).append('\n')
            }
            val start = state.agenticStartEpochSeconds
            if (start != null) append(FIELD_AGENTIC_START).append('=').append(start).append('\n')
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text, Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun toString(): String = "FileBudgetStore"
}

/** The counts at one moment: what the report and the evidence line show. */
internal class BudgetSnapshot(val core: Int, val optional: Int, val perProvider: Map<String, Int>) {
    /** Core plus optional. */
    val total: Int get() = core + optional

    override fun toString(): String = "BudgetSnapshot(core=$core, optional=$optional, perProvider=$perProvider)"
}

/**
 * The Gate-1 spend guard. The core legs share [coreCeiling] HTTP requests; the one optional probe may add requests only
 * while the total stays within [totalCeiling] even in its worst case. Counts live in the [store], so a force-stop or a
 * restart cannot reset them.
 *
 * [perCallWorstCase] is 3 because one logical call to the Anthropic transport can send up to three requests: the
 * first, one transient retry and one forced-tool reshape. A call is therefore let out only while the headroom covers
 * all three, so the ceiling holds even under retries.
 */
internal class RequestBudget(
    private val store: BudgetStore,
    private val coreCeiling: Int = CORE_REQUEST_CEILING,
    private val totalCeiling: Int = TOTAL_REQUEST_CEILING,
    private val perCallWorstCase: Int = PER_CALL_WORST_CASE,
    private val warmWindowSeconds: Long = WARM_WINDOW_SECONDS,
) {
    private val lock = Any()

    /**
     * Whether a leg that may send up to [reservation] requests may start. The optional probe may run once only, and
     * only while core plus its worst case stays within the total ceiling.
     */
    fun canStart(reservation: Int, optional: Boolean): Boolean = synchronized(lock) {
        fits(store.read(), reservation, optional)
    }

    /** Whether one more logical call (worst case [perCallWorstCase] requests) may be sent. */
    fun headroomFor(optional: Boolean): Boolean = synchronized(lock) {
        fits(store.read(), perCallWorstCase, optional)
    }

    /** Counts one HTTP request to [provider], against the core or the optional budget. */
    fun record(provider: ProviderId, optional: Boolean) {
        synchronized(lock) {
            val state = store.read()
            val perProvider = LinkedHashMap(state.perProvider)
            perProvider[provider.value] = (perProvider[provider.value] ?: 0) + 1
            store.write(
                BudgetState(
                    core = if (optional) state.core else state.core + 1,
                    optional = if (optional) state.optional + 1 else state.optional,
                    perProvider = perProvider,
                    agenticStartEpochSeconds = state.agenticStartEpochSeconds,
                ),
            )
        }
    }

    /** The counts now. */
    fun snapshot(): BudgetSnapshot = synchronized(lock) {
        val state = store.read()
        BudgetSnapshot(state.core, state.optional, state.perProvider)
    }

    /** Remembers that an Anthropic agentic leg started at [nowSeconds] (epoch seconds). */
    fun markAgenticStart(nowSeconds: Long) {
        synchronized(lock) {
            val state = store.read()
            store.write(BudgetState(state.core, state.optional, state.perProvider, nowSeconds))
        }
    }

    /** Seconds left before another Anthropic agentic leg may start; 0 when none is waiting. */
    fun warmWindowRemaining(nowSeconds: Long): Long = synchronized(lock) {
        val start = store.read().agenticStartEpochSeconds ?: return@synchronized 0L
        (start + warmWindowSeconds - nowSeconds).coerceAtLeast(0L)
    }

    private fun fits(state: BudgetState, requests: Int, optional: Boolean): Boolean =
        if (optional) {
            state.optional == 0 && state.core + state.optional + requests <= totalCeiling
        } else {
            state.core + requests <= coreCeiling
        }
}

/**
 * Wraps a provider so a call that could break the request ceiling is refused before anything is sent. It returns the
 * typed failure `sample_budget_exhausted` and never calls [delegate]. Counting happens elsewhere, from the transports'
 * attempt observers, because only they see each real HTTP request.
 *
 * @param optional whether the call in progress belongs to the optional probe.
 */
internal class BudgetedProvider(
    private val delegate: AiProvider,
    private val budget: RequestBudget,
    private val optional: () -> Boolean,
) : AiProvider {
    override val id: ProviderId get() = delegate.id

    override val requiresCredential: Boolean get() = delegate.requiresCredential

    override fun capabilities(model: String): ModelCapabilities = delegate.capabilities(model)

    override suspend fun complete(call: ProviderRequest): ModelResult =
        if (budget.headroomFor(optional())) {
            delegate.complete(call)
        } else {
            ModelResult.Failure(FailureReason.Other(BUDGET_EXHAUSTED))
        }

    /** The provider id only. */
    override fun toString(): String = "BudgetedProvider(${delegate.id.value})"
}
