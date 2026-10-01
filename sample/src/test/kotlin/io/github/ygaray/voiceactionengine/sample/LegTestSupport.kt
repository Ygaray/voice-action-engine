package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetedProvider
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.FileBudgetStore
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureLoader
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureSource
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.fixture.NamedFixtureSource
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.legs.DemoProvider
import io.github.ygaray.voiceactionengine.sample.legs.LegRunner
import io.github.ygaray.voiceactionengine.sample.net.AttemptTap
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

private const val SYNTHETIC_RESOURCE = "synthetic-fixture.json"
private const val FIRST_CLOCK_SECONDS = 1_000L

/** Obviously fake placeholder; never key-shaped. */
internal const val FAKE_KEY = "placeholder-not-a-key"

/** A vault in memory: every provider is Ready unless a test says otherwise. */
internal class MemoryVault : KeyVault {
    private val states = HashMap<ProviderId, KeyState>()

    /** Makes [provider] answer [state]. */
    fun set(provider: ProviderId, state: KeyState) {
        states[provider] = state
    }

    override suspend fun save(provider: ProviderId, key: String) {
        states[provider] = KeyState.Ready("fake")
    }

    override suspend fun read(provider: ProviderId): KeyState = states[provider] ?: KeyState.Ready("fake")

    override suspend fun delete(provider: ProviderId) {
        states.remove(provider)
    }

    override fun toString(): String = "MemoryVault"
}

/** An evidence sink that keeps every line. */
internal class ListSink : EvidenceSink {
    private val kept = CopyOnWriteArrayList<EvidenceLine>()

    /** The lines emitted so far, rendered. */
    val rendered: List<String> get() = kept.map { it.render() }

    override fun emit(line: EvidenceLine) {
        kept.add(line)
    }

    /** The rendered lines that start with [prefix]. */
    fun starting(prefix: String): List<String> = rendered.filter { it.startsWith(prefix) }

    override fun toString(): String = "ListSink(${kept.size})"
}

/** One scripted answer: the HTTP attempts to report (kind, status) and then the result to return. */
internal class AttemptStep(val attempts: List<Pair<String, Int?>>, val result: ModelResult)

/** A step that reports one initial attempt answering 200. */
internal fun ok(result: ModelResult): AttemptStep = AttemptStep(listOf("initial" to 200), result)

/**
 * A fake provider that behaves like a transport: for each scripted answer it first reports its HTTP attempts to the
 * [AttemptTap], exactly as the real observers do, and then returns the scripted result. It records every request.
 */
internal class AttemptingFake(
    override val id: ProviderId,
    private val tap: AttemptTap,
    steps: List<AttemptStep>,
    private val caps: ModelCapabilities = ModelCapabilities.UNKNOWN,
) : AiProvider {
    private val script = ArrayDeque(steps)
    private val seen = CopyOnWriteArrayList<ProviderRequest>()

    /** The requests received, in order. */
    val calls: List<ProviderRequest> get() = seen.toList()

    override fun capabilities(model: String): ModelCapabilities = caps

    override suspend fun complete(call: ProviderRequest): ModelResult {
        seen.add(call)
        val step = script.removeFirstOrNull() ?: throw AssertionError("AttemptingFake ${id.value}: script exhausted")
        step.attempts.forEachIndexed { index, (kind, status) ->
            tap.record(AttemptRecord(id, index + 1, kind, status, null, 0))
        }
        return step.result
    }

    override fun toString(): String = "AttemptingFake(${id.value})"
}

/** A clock a test moves by hand. */
internal class ManualClock(var seconds: Long = FIRST_CLOCK_SECONDS) {
    override fun toString(): String = "ManualClock($seconds)"
}

/** Everything a leg test needs. */
internal class LegRig(
    val runner: LegRunner,
    val sink: ListSink,
    val budget: RequestBudget,
    val vault: MemoryVault,
    val commits: RecordingCommitSink,
    val demoCommits: RecordingCommitSink,
    val demo: DemoProvider,
    val clock: ManualClock,
    val fakes: List<AttemptingFake>,
    val tap: AttemptTap,
) {
    /** The fake of [provider]. */
    fun fake(provider: ProviderId): AttemptingFake = fakes.first { it.id == provider }

    override fun toString(): String = "LegRig"
}

/**
 * Wires a [LegRunner] over fakes built by [makeFakes], the one composition root, a temp-dir budget file and an
 * in-memory vault. Every fake is wrapped in a [BudgetedProvider], like the production providers.
 */
internal fun legRig(
    folder: File,
    fixture: () -> FixtureState = { FixtureState.Absent(listOf("none")) },
    makeFakes: (AttemptTap) -> List<AttemptingFake>,
): LegRig {
    val sink = ListSink()
    val budget = RequestBudget(FileBudgetStore(File(folder, "budget.txt")))
    val tap = AttemptTap(budget, sink)
    val fakes = makeFakes(tap)
    val commits = RecordingCommitSink()
    val engine = SampleEngine(
        providers = fakes.map { BudgetedProvider(it, budget) { tap.current?.optional == true } },
        credentials = ScriptedCredentialSource.keys(
            ProviderId.ANTHROPIC to FAKE_KEY,
            ProviderId.OPENAI to FAKE_KEY,
            ProviderId.OPENROUTER to FAKE_KEY,
        ),
        sink = commits,
        listener = null,
    )
    val vault = MemoryVault()
    val clock = ManualClock()
    val demoCommits = RecordingCommitSink()
    val demo = DemoProvider()
    val runner = LegRunner(engine, fixture, vault, budget, tap, sink, demoCommits, demo) { clock.seconds }
    return LegRig(runner, sink, budget, vault, commits, demoCommits, demo, clock, fakes, tap)
}

/**
 * The committed synthetic fixture, loaded with its own digest. [rename] rewrites the fixture text first (for example to
 * give its tools names that must never reach evidence); the digest is taken over the rewritten bytes.
 */
internal fun loadedSyntheticFixture(rename: (String) -> String = { it }): FixtureState.Loaded {
    val original = checkNotNull(LegRig::class.java.classLoader?.getResourceAsStream(SYNTHETIC_RESOURCE)) {
        "missing test resource"
    }.use { it.readBytes() }
    val bytes = rename(original.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val state = FixtureLoader(listOf(NamedFixtureSource("files", FixtureSource { bytes })), digest).load()
    check(state is FixtureState.Loaded) { state.toString() }
    return state
}
