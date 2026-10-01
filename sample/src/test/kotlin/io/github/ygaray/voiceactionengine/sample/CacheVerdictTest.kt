package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceListener
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.tools.CannedToolExecutor
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import io.github.ygaray.voiceactionengine.sample.verdict.CacheVerdict
import io.github.ygaray.voiceactionengine.sample.verdict.OutcomeSummary
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_KEY = "placeholder-not-a-real-key"
private const val PREFIX_CHARS = 21109

/** The VER-02 cache classification, from the tracer slice (a fake agentic run) to the whole matrix. */
class CacheVerdictTest {

    private val selection = ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5")
    private val policy = TierPolicy { maxIterations = 3 }

    @Test
    fun aFakeAgenticRunBecomesTurnLinesAndAPassVerdict() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.toolCall(
                    "call_1",
                    "find_items",
                    buildJsonObject { put("query", "x") },
                    Usage(inputUncached = 40, cacheRead = 0, cacheWrite = 7016, output = 20),
                ),
                FakeAiProvider.reply("done", Usage(inputUncached = 30, cacheRead = 7016, cacheWrite = 0, output = 15)),
            )
            val lines = mutableListOf<EvidenceLine>()
            val sink = EvidenceSink { lines += it }
            val listener = EvidenceListener(LegId.VER02, sink, PREFIX_CHARS)
            val engine = SampleEngine(
                listOf(fake),
                ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to TEST_KEY),
                RecordingCommitSink(),
                listener,
            )
            val tier = AgenticLoopStrategy(StrategyId("agentic")) {
                tooling = ToolSpecProvider.fixed(SyntheticTools.snapshot(null))
                executor = CannedToolExecutor(SyntheticTools.all)
            }

            val outcome = engine.pipeline(tier, selection, policy) { clock = { 0L } }
                .execute(CommandInput("find my items"))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(
                listOf(
                    "VAE_TURN leg=ver02 iteration=1 model=claude-haiku-4-5 stop_reason=tool_use tools=[find_items] " +
                        "input_tokens=40 output_tokens=20 cache_creation_input_tokens=7016 " +
                        "cache_read_input_tokens=0 prefix_chars=21109 latency_ms=0",
                    "VAE_TURN leg=ver02 iteration=2 model=claude-haiku-4-5 stop_reason=end_turn tools=[] " +
                        "input_tokens=30 output_tokens=15 cache_creation_input_tokens=0 " +
                        "cache_read_input_tokens=7016 prefix_chars=21109 latency_ms=0",
                ),
                lines.map { it.render() },
            )

            val attempts = listOf(attempt(1, "initial", 200), attempt(1, "initial", 200))
            val result = CacheVerdict.classify(listener.turns, attempts, OutcomeSummary.of(outcome))

            assertEquals(result.verdict.toString(), VerdictKind.PASS, result.verdict.kind)
            val verdictLine = EvidenceLine.verdict(LegId.VER02, result.verdict, result.extras(), null, "ui")
            assertEquals(
                "VAE_VERDICT leg=ver02 verdict=PASS turn1_write=7016 min_read=7016 calls=2 trigger=ui",
                verdictLine.render(),
            )
            assertEquals(false, verdictLine.loud)
        }
    }

    private fun attempt(number: Int, kind: String, status: Int?): AttemptRecord =
        AttemptRecord(ProviderId.ANTHROPIC, number, kind, status, null, 0)
}
