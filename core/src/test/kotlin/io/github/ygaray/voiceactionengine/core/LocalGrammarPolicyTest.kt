package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The grammar tier uses no provider, so every provider policy lets it run and offline-only keeps it. */
class LocalGrammarPolicyTest {

    private val pack = GrammarPack {
        intent("light_on") {
            en("turn on the light")
            es("enciende la luz")
        }
        intent("open_page") {
            text("title", 3)
            terminal()
            en("open page {title}")
        }
    }

    private class Rig(pack: GrammarPack, policy: TierPolicy) {
        val sink = RecordingCommitSink()
        val fake = FakeAiProvider(ProviderId.ANTHROPIC)
        val resolver = RecordingResolver { extraction, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(extraction.toolName, StepResult("saved")))))
        }
        private val grammar = LocalGrammarStrategy(StrategyId("grammar")) {
            this.pack = pack
            resolver = this@Rig.resolver
        }
        // A real cloud tier behind the grammar tier, so a provider call would be counted if one were made.
        private val cloud = singleShot(resolver, snapshotOf(entriesTool()))
        val pipeline = pipelineOf(listOf(grammar, cloud), fake, ScriptedGate.admitAll(), sink, policy = policy)
        val grammarTier: LocalGrammarStrategy = grammar
    }

    private suspend fun commits(policy: TierPolicy) {
        val rig = Rig(pack, policy)

        val outcome = rig.pipeline.execute(CommandInput("turn on the light", "en", null))

        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        assertEquals(ActionKind.COMMITTED, rig.sink.actions.single().action.kind)
        assertNull(outcome.executed.single().providerCallId)
        assertEquals(0, rig.fake.callCount)
        assertEquals(1, rig.resolver.invocations)
    }

    @Test
    fun theTierDeclaresNoProvider() {
        assertEquals(StrategyCapabilities.NO_PROVIDER, Rig(pack, TierPolicy.DEFAULT).grammarTier.capabilities)
    }

    @Test
    fun theTierRunsUnderOfflineOnlyWithZeroProviderCalls() = runTest {
        NoNetworkGuard.during { commits(TierPolicy { offlineOnly = true }) }
    }

    @Test
    fun theTierRunsWhenOnlyAnotherProviderIsAllowed() = runTest {
        NoNetworkGuard.during { commits(TierPolicy { allowedProviders = setOf(ProviderId.OPENAI) }) }
    }

    @Test
    fun theTierRunsWhenNoProviderIsAllowed() = runTest {
        NoNetworkGuard.during { commits(TierPolicy { allowedProviders = emptySet() }) }
    }

    @Test
    fun anOfflineOnlyNoMatchEndsUnhandledCappedByPolicyAfterZeroProviderCalls() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(pack, TierPolicy { offlineOnly = true })

            val outcome = rig.pipeline.execute(CommandInput("something the pack does not know", "en", null))

            val unhandled = outcome as CommandOutcome.Unhandled
            assertTrue(unhandled.cappedByPolicy)
            assertEquals(0, rig.fake.callCount)
            assertEquals(0, rig.resolver.invocations)
            assertTrue(rig.sink.actions.isEmpty())
        }
    }

    @Test
    fun aTerminalIntentEndsHandledUnderOfflineOnlyWithItsTerminalCall() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(pack, TierPolicy { offlineOnly = true })

            val outcome = rig.pipeline.execute(CommandInput("open page Release Plan", "en", null))

            val completed = outcome as CommandOutcome.Completed
            assertEquals("open_page", completed.terminalCall?.toolName)
            assertEquals(0, rig.fake.callCount)
            assertEquals(0, rig.resolver.invocations)
            assertTrue(completed.executed.isEmpty())
        }
    }
}
