package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TierSelectorTest {

    private fun scripted(id: String, outcome: StrategyOutcome) =
        ScriptedStrategy(StrategyId(id), { _, _ -> outcome })

    @Test
    fun linearIsTheDefaultAndStartsAtTheFirstTier() = runTest {
        NoNetworkGuard.during {
            val a = scripted("a", StrategyOutcome.Completed("a"))
            val b = scripted("b", StrategyOutcome.Completed("b"))
            val c = scripted("c", StrategyOutcome.Completed("c"))
            val pipeline = commandPipeline {
                tier(a)
                tier(b)
                tier(c)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }
            val outcome = pipeline.execute(CommandInput("hi"))
            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf(1, 0, 0), listOf(a, b, c).map { it.executions })
        }
    }

    @Test
    fun fixedStartsMidLadderAndHandsTheCarryOnByIdentity() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val a = scripted("a", StrategyOutcome.Completed("a"))
            val b = scripted("b", StrategyOutcome.Escalate(EscalationReason.ModelDeclined(), carry))
            val c = scripted("c", StrategyOutcome.Completed("c"))
            val pipeline = commandPipeline {
                tier(a)
                tier(b)
                tier(c)
                selector = TierSelector.Fixed(StrategyId("b"))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }
            val outcome = pipeline.execute(CommandInput("hi"))
            assertEquals("c", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, a.executions)
            assertEquals(1, b.executions)
            assertEquals(1, c.executions)
            assertSame(carry, c.receivedCarries.single())
            assertEquals(listOf(StrategyId("b"), StrategyId("c")), outcome.trace.attempts.map { it.strategy })
        }
    }

    @Test
    fun fixedNamingAnUnknownTierIsRejectedAtBuild() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            commandPipeline {
                tier(scripted("a", StrategyOutcome.NoMatch()))
                tier(scripted("b", StrategyOutcome.NoMatch()))
                selector = TierSelector.Fixed(StrategyId("zz"))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }
        }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("zz"))
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("selector names unknown tier"))
    }

    @Test
    fun selectorsRenderReadably() {
        assertEquals("Linear", TierSelector.Linear.toString())
        assertEquals("Fixed(b)", TierSelector.Fixed(StrategyId("b")).toString())
        assertEquals(StrategyId("b"), TierSelector.Fixed(StrategyId("b")).tier)
        val picker = ScriptedPicker({ _, _, _ -> null })
        assertEquals("Custom(id=start_tier_picker)", TierSelector.Custom(picker).toString())
        assertEquals("Custom(id=app_picker)", TierSelector.Custom(picker) { id = StrategyId("app_picker") }.toString())
        assertSame(picker, TierSelector.Custom(picker).picker)
    }
}
