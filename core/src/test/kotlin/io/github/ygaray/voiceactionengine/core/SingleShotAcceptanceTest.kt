package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TARGET_DATE = "2026-10-01"

/**
 * CT's confirm flows through SingleShot, end to end with neutral fixtures: the model extracts, the app's resolver
 * applies its thresholds and stamps them into each mutation's context, the app's gate reads only that context, and
 * held changes are committed later through commitHeld.
 */
class SingleShotAcceptanceTest {

    /** One scripted command: the fake answer, the app resolver, the gate, the sink and the pipeline over them. */
    private class Rig(
        answer: ModelResult,
        val gate: ScriptedGate = deferModeGate(),
        catalog: FixtureCatalog = fixtureCatalog(),
        failures: Map<String, FailureMode> = emptyMap(),
        val log: RecordingSink<String> = RecordingSink(),
    ) {
        val sink = RecordingCommitSink()
        val resolver = FixtureResolver(catalog, log, failures)
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, answer)
        val pipeline: CommandPipeline = acceptancePipeline(listOf(fixtureTier(resolver)), fake, gate, sink)

        suspend fun say(): CommandOutcome = pipeline.execute(CommandInput("record the entries", "en", null))

        val appliedLines: List<String> get() = log.events.filter { it.startsWith("applied:") }
    }

    private fun entry(label: String, confidence: Double, quantity: Double = 1.0) =
        FixtureEntry(label, quantity, "unit", confidence)

    private fun answerWith(vararg entries: FixtureEntry): ModelResult =
        answerOf(StopReason.TOOL_USE, callOf("call-1", ENTRIES_TOOL, entriesArgs(TARGET_DATE, *entries)))

    @Test
    fun s1StrongSingleAutoCommitsInTheOriginalRun() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(answerWith(entry("alpha", 0.95)))

            val outcome = rig.say()

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, outcome.commits.size)
            assertTrue(outcome.held.isEmpty())
            assertEquals(1, rig.gate.calls)
            assertEquals(ActionKind.COMMITTED, rig.sink.actions.single().action.kind)
            assertEquals(listOf(outcome.runId), rig.sink.closedRunIds)
            assertTrue(rig.sink.closes.single() is RunTermination.Done)
            assertEquals(1, rig.resolver.mutations.single().applyCount)
            assertEquals(listOf("applied:alpha:1.0:item-a:$TARGET_DATE"), rig.appliedLines)
        }
    }
}
