package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.tools.CANNED_READ
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val MIN_CACHEABLE = 4096
private const val ANCHOR = 7016L
private const val WARM_WINDOW = 360L

/** The VER-02 cold agentic run on the fixture, judged by the cache verdict, with its warm-window and fixture refusals. */
class AgenticLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fixture = loadedSyntheticFixture()

    private val capabilities = ModelCapabilities {
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = MIN_CACHEABLE
    }

    private fun lookup() = ok(
        FakeAiProvider.toolCall("call_1", "find_items", buildJsonObject { put("query", "paper") }, Usage(40, 0, ANCHOR, 20)),
    )

    private fun answer(cacheRead: Long = ANCHOR) = ok(FakeAiProvider.reply("You have two items.", Usage(30, cacheRead, 0, 15)))

    private fun rig(vararg steps: AttemptStep, state: FixtureState = fixture) =
        legRig(folder.newFolder(), { state }) { tap ->
            listOf(AttemptingFake(ProviderId.ANTHROPIC, tap, steps.toList(), capabilities))
        }

    @Test
    fun theColdAgenticRunPassesAndLogsPrefixAndMinimum() = runTest {
        NoNetworkGuard.during {
            val rig = rig(lookup(), answer())

            val result = rig.runner.run(LegId.VER02)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val lines = rig.sink.rendered
            val env = rig.sink.starting("VAE_ENV ").single()
            assertTrue(env, "min_cacheable=$MIN_CACHEABLE" in env)
            assertTrue(env, "prefix_chars=${fixture.prefixChars}" in env)
            assertTrue(env, "est_prefix_tokens=${fixture.prefixChars / 4}" in env)
            assertEquals(lines.toString(), 2, rig.sink.starting("VAE_TURN ").size)
            assertTrue(
                lines.toString(),
                "VAE_VERDICT leg=ver02 verdict=PASS turn1_write=7016 min_read=7016 calls=2 key_charset=ok trigger=ui" in lines,
            )
            // The executor answered the model's lookup with the canned read, and the model saw it on turn 2.
            val second = rig.fake(ProviderId.ANTHROPIC).calls[1].request.messages.last() as ToolResultsMessage
            assertEquals(CANNED_READ, second.results.single().content)
            assertEquals(1, rig.sink.starting("VAE_BUDGET core=2 ").size)
        }
    }

    @Test
    fun aWarmRunIsReportedAsWarm() = runTest {
        NoNetworkGuard.during {
            val warmFirst = ok(
                FakeAiProvider.toolCall(
                    "call_1",
                    "find_items",
                    buildJsonObject { put("query", "paper") },
                    Usage(40, ANCHOR, 0, 20),
                ),
            )
            val rig = rig(warmFirst, answer())

            val result = rig.runner.run(LegId.VER02)

            assertEquals(VerdictKind.WARM, result.verdict.kind)
            assertEquals("turn1_cache_read", result.verdict.reason)
        }
    }

    @Test
    fun aSecondStartInsideTheWarmWindowIsRefused() = runTest {
        NoNetworkGuard.during {
            val rig = rig(lookup(), answer(), lookup(), answer())

            rig.runner.run(LegId.VER02)
            val callsAfterFirst = rig.fake(ProviderId.ANTHROPIC).calls.size
            rig.clock.seconds += 100L
            val refused = rig.runner.run(LegId.VER02)

            assertEquals(VerdictKind.REFUSED, refused.verdict.kind)
            assertEquals("warm_window", refused.verdict.reason)
            assertEquals(callsAfterFirst, rig.fake(ProviderId.ANTHROPIC).calls.size)
            assertEquals(
                1,
                rig.sink.starting("VAE_VERDICT leg=ver02 verdict=REFUSED reason=warm_window remaining=260 trigger=ui").size,
            )

            rig.clock.seconds += WARM_WINDOW + 1L - 100L
            val later = rig.runner.run(LegId.VER02)

            assertEquals(VerdictKind.PASS, later.verdict.kind)
            val texts = rig.fake(ProviderId.ANTHROPIC).calls.map { (it.request.messages.first() as UserMessage).text }
            assertTrue(texts[2], "Do not answer from memory" in texts[2])
        }
    }

    @Test
    fun anAbsentFixtureIsRefused() = runTest {
        NoNetworkGuard.during {
            val rig = rig(lookup(), answer(), state = FixtureState.Absent(listOf("files", "asset")))

            val result = rig.runner.run(LegId.VER02)

            assertEquals(VerdictKind.REFUSED, result.verdict.kind)
            assertEquals("fixture_absent", result.verdict.reason)
            assertEquals(0, rig.fake(ProviderId.ANTHROPIC).calls.size)
            assertEquals(0, rig.budget.snapshot().core)
        }
    }
}
