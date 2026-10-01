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
 * @property legRuns how many times each leg (by wire name) has started, which selects the prompt variant of a rerun.
 */
internal class BudgetState(
    val core: Int,
    val optional: Int,
    val perProvider: Map<String, Int>,
    val agenticStartEpochSeconds: Long?,
    val legRuns: Map<String, Int> = emptyMap(),
) {
    override fun toString(): String =
        "BudgetState(core=$core, optional=$optional, perProvider=$perProvider, " +
            "agenticStartEpochSeconds=$agenticStartEpochSeconds, legRuns=$legRuns)"

    companion object {
        /** Nothing spent, nothing started. */
        val EMPTY = BudgetState(0, 0, emptyMap(), null)

        /**
         * What an unreadable or corrupt store reads as: far above every ceiling, so the guard refuses every call (fail
         * closed) and the header shows the absurd count instead of a comforting zero.
         */
        const val UNREADABLE_COUNT = 1_000

        /** The state of a store that exists but cannot be trusted. */
        val UNREADABLE = BudgetState(UNREADABLE_COUNT, UNREADABLE_COUNT, emptyMap(), null)
    }
}

/** Where the budget keeps its [BudgetState]. */
internal interface BudgetStore {
    /**
     * The saved state, [BudgetState.EMPTY] only when nothing was ever saved, and [BudgetState.UNREADABLE] when something
     * was saved and cannot be read back: a spend guard never resets to "nothing spent" on corruption.
     */
    fun read(): BudgetState

    /** Saves [state], replacing the previous one. */
    fun write(state: BudgetState)
}

private const val FIELD_CORE = "core"
private const val FIELD_OPTIONAL = "optional"
private const val FIELD_AGENTIC_START = "agentic_start"
private const val PROVIDER_PREFIX = "provider."
private const val RUN_PREFIX = "run."

/**
 * A [BudgetStore] in one small text file, one `key=value` line per field. A write goes to a temp file first and is then
 * moved over the real one, so a force-stop mid-write leaves the old state, never half of a new one. A file that exists
 * but cannot be read, or has a line or a number it cannot read, a missing count or a negative count, reads as
 * [BudgetState.UNREADABLE] (fail closed): it never silently resets to zero spent.
 */
internal class FileBudgetStore(private val file: File) : BudgetStore {
    override fun read(): BudgetState {
        if (!file.exists()) return BudgetState.EMPTY
        val lines = try {
            file.readLines(Charsets.UTF_8)
        } catch (unreadable: IOException) {
            return BudgetState.UNREADABLE
        } catch (denied: SecurityException) {
            return BudgetState.UNREADABLE
        }
        return parse(lines) ?: BudgetState.UNREADABLE
    }

    // The parsed state, or null when any line is not one this store wrote.
    private fun parse(lines: List<String>): BudgetState? {
        var core: Int? = null
        var optional: Int? = null
        var start: Long? = null
        val providers = LinkedHashMap<String, Int>()
        val runs = LinkedHashMap<String, Int>()
        for (line in lines) {
            if (line.isEmpty()) continue
            val at = line.indexOf('=')
            if (at <= 0) return null
            val key = line.substring(0, at)
            val value = line.substring(at + 1)
            when {
                key == FIELD_CORE -> core = count(value) ?: return null
                key == FIELD_OPTIONAL -> optional = count(value) ?: return null
                key == FIELD_AGENTIC_START -> start = value.toLongOrNull() ?: return null
                key.startsWith(PROVIDER_PREFIX) ->
                    providers[key.removePrefix(PROVIDER_PREFIX)] = count(value) ?: return null
                key.startsWith(RUN_PREFIX) -> runs[key.removePrefix(RUN_PREFIX)] = count(value) ?: return null
            }
        }
        return if (core == null || optional == null) null else BudgetState(core, optional, providers, start, runs)
    }

    // A non-negative whole number, or null.
    private fun count(text: String): Int? = text.toIntOrNull()?.takeIf { it >= 0 }

    override fun write(state: BudgetState) {
        val text = buildString {
            append(FIELD_CORE).append('=').append(state.core).append('\n')
            append(FIELD_OPTIONAL).append('=').append(state.optional).append('\n')
            for ((provider, count) in state.perProvider) {
                append(PROVIDER_PREFIX).append(provider).append('=').append(count).append('\n')
            }
            for ((leg, count) in state.legRuns) {
                append(RUN_PREFIX).append(leg).append('=').append(count).append('\n')
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

    // Set when a count could not be saved: a request was sent that the store never saw, so nothing more may be sent.
    @Volatile
    private var writeFailed = false

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

    /**
     * Counts one HTTP request to [provider], against the core or the optional budget. Returns false when the count could
     * not be saved; the guard then refuses every further call until the app is restarted (fail closed), and the caller
     * must say so loudly.
     */
    fun record(provider: ProviderId, optional: Boolean): Boolean {
        synchronized(lock) {
            val state = store.read()
            val perProvider = LinkedHashMap(state.perProvider)
            perProvider[provider.value] = (perProvider[provider.value] ?: 0) + 1
            val next = BudgetState(
                core = if (optional) state.core else state.core + 1,
                optional = if (optional) state.optional + 1 else state.optional,
                perProvider = perProvider,
                agenticStartEpochSeconds = state.agenticStartEpochSeconds,
                legRuns = state.legRuns,
            )
            try {
                store.write(next)
            } catch (unwritable: IOException) {
                writeFailed = true
            } catch (denied: SecurityException) {
                writeFailed = true
            }
            return !writeFailed
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
            store.write(BudgetState(state.core, state.optional, state.perProvider, nowSeconds, state.legRuns))
        }
    }

    /** How many times [leg] (a wire name) has started. */
    fun runsOf(leg: String): Int = synchronized(lock) { store.read().legRuns[leg] ?: 0 }

    /** Counts one start of [leg] (a wire name); a rerun then picks the next prompt variant. */
    fun recordRun(leg: String) {
        synchronized(lock) {
            val state = store.read()
            val runs = LinkedHashMap(state.legRuns)
            runs[leg] = (runs[leg] ?: 0) + 1
            store.write(
                BudgetState(state.core, state.optional, state.perProvider, state.agenticStartEpochSeconds, runs),
            )
        }
    }

    /** Seconds left before another Anthropic agentic leg may start; 0 when none is waiting. */
    fun warmWindowRemaining(nowSeconds: Long): Long = synchronized(lock) {
        val start = store.read().agenticStartEpochSeconds ?: return@synchronized 0L
        (start + warmWindowSeconds - nowSeconds).coerceAtLeast(0L)
    }

    private fun fits(state: BudgetState, requests: Int, optional: Boolean): Boolean =
        if (writeFailed) {
            false
        } else if (optional) {
            state.optional == 0 && state.core + state.optional + requests <= totalCeiling
        } else {
            state.core + requests <= coreCeiling && state.core + state.optional + requests <= totalCeiling
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
