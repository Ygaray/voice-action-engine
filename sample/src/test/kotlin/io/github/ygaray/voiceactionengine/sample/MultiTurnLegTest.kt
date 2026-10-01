package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.tools.CANNED_READ
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SECOND_TURN_CACHE_READ = 512L

/** The extended multi-turn legs on OpenAI Chat and OpenRouter: read, replay the tool result, answer. */
class MultiTurnLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun lookup() = ok(
        FakeAiProvider.toolCall("call_1", "find_items", buildJsonObject { put("query", "paper") }, Usage(40, 0, 0, 20)),
    )

    private fun answer(cacheRead: Long) = ok(FakeAiProvider.reply("There are two items.", Usage(30, cacheRead, 0, 15)))

    private fun rig(provider: ProviderId, vararg steps: AttemptStep) =
        legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(provider, tap, steps.toList())) }

    @Test
    fun theMultiTurnLegReplaysTheToolResult() = runTest {
        NoNetworkGuard.during {
            val rig = rig(ProviderId.OPENAI, lookup(), answer(SECOND_TURN_CACHE_READ))

            val result = rig.runner.run(LegId.MULTI_OPENAI)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val calls = rig.fake(ProviderId.OPENAI).calls
            assertEquals(2, calls.size)
            assertEquals(listOf("find_items", "create_item", "edit_item"), calls[0].request.tools.map { it.name })
            val replay = calls[1].request.messages.last() as ToolResultsMessage
            assertEquals(CANNED_READ, replay.results.single().content)
            assertTrue(
                rig.sink.rendered.toString(),
                "VAE_VERDICT leg=multi_openai verdict=PASS calls=2 turn2_cache_read=512 key_charset=ok trigger=ui" in
                    rig.sink.rendered,
            )
        }
    }

    @Test
    fun theOpenRouterMultiTurnLegPassesAndOnlyObservesTheCacheRead() = runTest {
        NoNetworkGuard.during {
            val rig = rig(ProviderId.OPENROUTER, lookup(), answer(0L))

            val result = rig.runner.run(LegId.MULTI_OPENROUTER)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            assertEquals("openai/gpt-5.4-mini", rig.fake(ProviderId.OPENROUTER).calls.first().model)
            assertTrue(
                rig.sink.rendered.toString(),
                "VAE_VERDICT leg=multi_openrouter verdict=PASS calls=2 turn2_cache_read=0 key_charset=ok trigger=ui" in
                    rig.sink.rendered,
            )
        }
    }

    @Test
    fun aSingleTurnAnswerFailsAndTheRerunIsStronger() = runTest {
        NoNetworkGuard.during {
            val proseOnly = ok(FakeAiProvider.reply("Probably about two.", Usage(30, 0, 0, 15)))
            val rig = rig(ProviderId.OPENAI, proseOnly, lookup(), answer(SECOND_TURN_CACHE_READ))

            val first = rig.runner.run(LegId.MULTI_OPENAI)
            val second = rig.runner.run(LegId.MULTI_OPENAI)

            assertEquals(VerdictKind.FAIL, first.verdict.kind)
            assertEquals("single_turn", first.verdict.reason)
            assertEquals(VerdictKind.PASS, second.verdict.kind)
            val texts = rig.fake(ProviderId.OPENAI).calls.map { (it.request.messages.first() as UserMessage).text }
            assertFalse(texts[0], "First call find_items" in texts[0])
            assertTrue(texts[1], "First call find_items with the query paper" in texts[1])
        }
    }
}
