package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import io.github.ygaray.voiceactionengine.undo.UndoResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The undo bridge proven through the real pipeline: every grouping rule and every failure the consumers rely on. */
class UndoEndToEndTest {

    @Test
    fun s1OneCommandsThreeWritesAreUndoneTogether() = runTest {
        NoNetworkGuard.during {
            val store = ItemStore().also { it.seed("a", "v0") }
            val seeded = store.snapshot()
            val journal = UndoJournal { adapter(ItemAdapter(store)) }
            val bridge = UndoCommitSink(journal)
            val recording = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier"), { _, session ->
                        session.submit(ToolStep.Mutation(CreateItem(store, journal.newTicket(), "n1", null)))
                        session.submit(ToolStep.Mutation(RenameItem(store, journal.newTicket(), "a", "v1")))
                        session.submit(ToolStep.Mutation(CreateItem(store, journal.newTicket(), "n2", "item-1")))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = compositeSink(bridge, recording)
                runIds = { "run-1" }
            }

            val outcome = pipeline.execute(CommandInput("create, rename, create"))

            assertEquals(3, outcome.commits.size)
            assertEquals(List(3) { ActionKind.COMMITTED }, recording.actions.map { it.action.kind })
            val group = journal.group("run-1")!!
            assertEquals(3, group.count)
            assertFalse(group.withheld)
            assertEquals(3, store.snapshot().size)
            assertEquals("v1", store.get("a")?.title)

            val result = journal.undoAll("run-1")

            assertTrue(result.toString(), result is UndoResult.Complete)
            assertEquals(3, (result as UndoResult.Complete).restored.size)
            assertEquals(seeded, store.snapshot())
            assertEquals(seeded["a"], store.get("a"))
            val again = journal.undoAll("run-1")
            assertTrue(again.toString(), again is UndoResult.AlreadyUndone)
            assertEquals(seeded, store.snapshot())
        }
    }
}
