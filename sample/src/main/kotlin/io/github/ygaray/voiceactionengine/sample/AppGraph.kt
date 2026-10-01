package io.github.ygaray.voiceactionengine.sample

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.FileBudgetStore
import io.github.ygaray.voiceactionengine.sample.evidence.LogcatEvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureLoader
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.fixture.androidFixtureSources
import io.github.ygaray.voiceactionengine.sample.keys.ApiKeyStoreVault
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.keys.SampleKeys
import io.github.ygaray.voiceactionengine.sample.legs.LegRunner
import io.github.ygaray.voiceactionengine.sample.net.AttemptTap
import io.github.ygaray.voiceactionengine.sample.net.OkHttpRuntime
import io.github.ygaray.voiceactionengine.sample.net.ProviderFactory
import java.io.File
import java.util.Locale

private const val BUDGET_FILE = "gate1-budget.txt"
private const val MILLIS_PER_SECOND = 1000L
private const val EST_PREFIX = "est_usd="
private const val UNKNOWN = "unknown"

/**
 * A commit sink that keeps the action kinds of the run in progress and, once the run closes, of the last finished run.
 * It holds counts and kind words only, and it logs nothing.
 */
internal class InMemoryCommitSink : CommitSink {
    private val lock = Any()
    private val current = ArrayList<String>()
    private var last: List<String> = emptyList()

    /** The action kinds (`committed`, `held`, ...) of the last run that closed, in order. */
    val lastRunKinds: List<String> get() = synchronized(lock) { last }

    override suspend fun onAction(event: ActionEvent) {
        synchronized(lock) { current.add(event.action.kind.value) }
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) {
        synchronized(lock) {
            last = current.toList()
            current.clear()
        }
    }

    override fun toString(): String = "InMemoryCommitSink"
}

/**
 * Passes every evidence line on to [delegate] and adds up the cost estimate of the `VAE_BUDGET` lines it sees, so the
 * header can show a running total. Any leg whose estimate is `unknown` makes the total `unknown`.
 */
internal class CostTallySink(private val delegate: EvidenceSink) : EvidenceSink {
    private val lock = Any()
    private var sum = 0.0
    private var unknown = false

    /** The total estimate since launch as five decimals, or `unknown`. */
    fun total(): String = synchronized(lock) {
        if (unknown) UNKNOWN else String.format(Locale.ROOT, "%.5f", sum)
    }

    override fun emit(line: EvidenceLine) {
        delegate.emit(line)
        if (line.type != "BUDGET") return
        val text = line.render()
        val at = text.indexOf(EST_PREFIX)
        if (at < 0) return
        val value = text.substring(at + EST_PREFIX.length).substringBefore(' ')
        synchronized(lock) {
            val parsed = value.toDoubleOrNull()
            if (parsed == null) unknown = true else sum += parsed
        }
    }

    override fun toString(): String = "CostTallySink"
}

/**
 * The one place that wires the sample for the device: the fixture, the key vault, the spend guard and request tap, the
 * three real providers, the engine, the leg runner and (in debug builds) the test-key importer. Built once per process
 * from the application context.
 */
internal class AppGraph private constructor(context: Context) {
    private val filesDir: File = context.filesDir
    private val fixtureState: FixtureState = FixtureLoader(androidFixtureSources(context)).load()
    private val store = SampleKeys.store(context)
    private val vault: KeyVault = ApiKeyStoreVault(store)
    private val budget = RequestBudget(FileBudgetStore(File(filesDir, BUDGET_FILE)))
    private val costs = CostTallySink(LogcatEvidenceSink)
    private val tap = AttemptTap(budget, costs)
    private val commits = InMemoryCommitSink()
    private val engine = SampleEngine(
        providers = ProviderFactory.create(tap, budget),
        credentials = SampleKeys.credentials(store),
        sink = commits,
        listener = null,
    )
    private val runner = LegRunner(
        engine = engine,
        fixture = { fixtureState },
        vault = vault,
        budget = budget,
        tap = tap,
        sink = costs,
        demoSink = commits,
        nowSeconds = { System.currentTimeMillis() / MILLIS_PER_SECOND },
    )

    /** The factory that builds the screen's view model from this graph. */
    fun viewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SampleViewModel(
                runner = runner,
                vault = vault,
                keyImport = DebugTools.keyImport(filesDir, vault, SampleKeys.PROVIDERS),
                fixture = fixtureState,
                budget = budget,
                okhttpVersion = OkHttpRuntime.version(),
                sink = costs,
                providers = SampleKeys.PROVIDERS,
                estUsd = costs::total,
                nowSeconds = { System.currentTimeMillis() / MILLIS_PER_SECOND },
            )
        }
    }

    override fun toString(): String = "AppGraph"

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        /** The process-wide graph, built on first use. */
        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance = it }
        }
    }
}
