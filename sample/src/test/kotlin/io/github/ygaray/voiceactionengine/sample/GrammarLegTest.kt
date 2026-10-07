package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The offline grammar leg (VER-06, D-05), run through the real runner with no provider reachable. */
class GrammarLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val allow = Regex(ALLOW_PATTERN)

    private fun rig() = legRig(folder.newFolder()) { emptyList() }

    @Test
    fun theEnglishCommandCompletesWithZeroCallsAndAPassVerdict() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.run(LegId.GRAMMAR_OFFLINE)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val traces = rig.sink.starting("VAE_TRACE ")
            assertEquals(traces.toString(), 1, traces.size)
            val trace = traces.single()
            assertTrue(trace, trace.contains(" leg=grammar_offline case=1 kind=completed capped=none "))
            assertTrue(trace, trace.contains(" provider_turns=0 attempts=0 tripwire_calls=0 matched_lang=en "))
            assertTrue(trace, trace.contains(" sel=none "))
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=grammar_offline verdict=PASS ").size)
            for (line in rig.sink.rendered) assertTrue(line, allow.matches(line))
        }
    }
}
