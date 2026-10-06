package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import io.github.ygaray.voiceactionengine.undo.UndoReason
import io.github.ygaray.voiceactionengine.undo.UndoResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun s2AnUnrelatedEditRefusesTheWholeUndoAndWritesNothing() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val pipeline = rig.pipeline(
                ScriptedGate.admitAll(),
                rig.submitting(rig.create("n1"), rig.rename("a", "v1"), rig.create("n2", "item-1"), rig.alarm("al-1")),
            )
            pipeline.execute(CommandInput("three writes and an alarm"))
            assertEquals(4, rig.journal.group("run-1")!!.count)
            rig.store.edit("a", "x")
            val afterEdit = rig.store.snapshot()

            val result = rig.journal.undoAll("run-1")

            assertTrue(result.toString(), result is UndoResult.Refused)
            val blocker = (result as UndoResult.Refused).blockers.first { it.entity?.id == "a" }
            assertEquals("item", blocker.entity?.type)
            assertEquals(UndoReason.CHANGED_SINCE, blocker.reason)
            assertEquals(afterEdit, rig.store.snapshot())
            assertEquals(emptyList<String>(), rig.log.events)
            assertEquals(setOf("al-1"), rig.board.ids)
            assertEquals(4, rig.journal.group("run-1")!!.count)
        }
    }

    @Test
    fun s3AHeldRenameConfirmedLaterRestoresTheMovedStateNotTheProposedOne() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val pipeline = rig.pipeline(
                ScriptedGate.sequence(null, GateDecision.Hold("confirm", null)),
                rig.submitting(rig.rename("a", "v2")),
            )

            val first = pipeline.execute(CommandInput("rename a"))

            val proposed = rig.recording.actions.single()
            assertEquals(ActionKind.HELD, proposed.action.kind)
            assertFalse(proposed.action.applied)
            assertEquals(1, rig.bridge.pendingHeld("run-1"))
            assertTrue(rig.journal.group("run-1")?.count ?: 0 == 0)
            assertEquals("v0", rig.store.get("a")?.title)

            rig.store.edit("a", "v1")
            val moved = rig.store.get("a")
            pipeline.commitHeld(first.held.single())

            val confirmed = rig.recording.actions.last()
            assertEquals("run-1", confirmed.heldRunId)
            assertEquals("run-2", confirmed.runId)
            assertEquals(1, rig.journal.group("run-1")!!.count)
            assertEquals(0, rig.bridge.pendingHeld("run-1"))
            assertEquals("v2", rig.store.get("a")?.title)

            val result = rig.journal.undoAll("run-1")

            assertTrue(result.toString(), result is UndoResult.Complete)
            assertEquals("v1", rig.store.get("a")?.title)
            assertEquals(moved, rig.store.get("a"))
        }
    }

    @Test
    fun s4AClarificationReplyIsItsOwnGroupAndKeepsItsParentGroupKey() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val ids = ArrayDeque(listOf("run-p", "run-r"))
            val pipeline = rig.pipeline(
                ScriptedGate.admitAll(),
                rig.submitting(rig.create("n1")),
                rig.submitting(rig.rename("a", "v1")),
                runIds = { ids.removeFirst() },
            )
            pipeline.execute(CommandInput("first"))
            val before = rig.journal.group("run-p")!!

            pipeline.execute(CommandInput("reply", parentRunId = "run-p"))

            val reply = rig.recording.actions.last()
            assertEquals("run-r", reply.runId)
            assertEquals("run-p", reply.parentRunId)
            assertNull(reply.heldRunId)
            val group = rig.journal.group("run-r")!!
            assertEquals("run-p", group.parentGroupKey)
            assertEquals(1, group.count)
            val parent = rig.journal.group("run-p")!!
            assertEquals(1, parent.count)
            assertEquals(before.revision, parent.revision)
            assertNull(parent.parentGroupKey)
        }
    }

    @Test
    fun s5AThrowingFirstSinkNeverStopsTheBridgeOrChangesTheOutcome() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val thrower = object : CommitSink {
                override suspend fun onAction(event: ActionEvent) = throw IllegalStateException("sink down")

                override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit
            }
            val pipeline = rig.pipeline(
                ScriptedGate.admitAll(),
                rig.submitting(rig.create("n1"), rig.rename("a", "v1")),
                sink = compositeSink(thrower, rig.bridge, rig.recording),
            )

            val outcome = pipeline.execute(CommandInput("two writes"))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(2, outcome.commits.size)
            assertEquals(2, rig.recording.actions.size)
            assertTrue(TraceCode.SINK_ERROR in outcome.trace.codes)
            assertEquals(setOf("a", "item-1"), rig.store.snapshot().keys)
            val group = rig.journal.group("run-1")!!
            assertEquals(2, group.count)
            assertFalse(group.withheld)
        }
    }

    @Test
    fun s6aAnAppliedActionWithoutATicketWithholdsTheGroup() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val noTicket = FakeMutation("create_item", StepResult("ok"))
            val pipeline = rig.pipeline(ScriptedGate.admitAll(), rig.submitting(rig.create("n1"), noTicket))
            pipeline.execute(CommandInput("one with a ticket, one without"))
            val written = rig.store.snapshot()

            val group = rig.journal.group("run-1")!!
            val result = rig.journal.undoAll("run-1")

            assertTrue(group.withheld)
            assertTrue(result.toString(), result is UndoResult.Refused)
            assertEquals(UndoReason.JOURNAL_WITHHELD, (result as UndoResult.Refused).blockers.first().reason)
            assertEquals(written, rig.store.snapshot())
            assertEquals(emptyList<String>(), rig.log.events)
        }
    }

    @Test
    fun s6bAnActionTheBridgeNeverSawWithholdsTheGroupAtRunClose() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val pipeline = rig.pipeline(
                ScriptedGate.admitAll(),
                rig.submitting(rig.create("n1"), rig.create("n2"), rig.create("n3")),
                sink = compositeSink(DroppingSink(rig.bridge, 1), rig.recording),
            )
            pipeline.execute(CommandInput("three creates"))
            val written = rig.store.snapshot()

            val group = rig.journal.group("run-1")!!
            val result = rig.journal.undoAll("run-1")

            assertTrue(group.withheld)
            assertEquals(3, rig.recording.actions.size)
            assertTrue(result.toString(), result is UndoResult.Refused)
            assertEquals(UndoReason.JOURNAL_WITHHELD, (result as UndoResult.Refused).blockers.first().reason)
            assertEquals(written, rig.store.snapshot())
        }
    }

    /** An action that creates an item and arms an alarm for it, declaring both on its ticket. */
    private fun UndoRig.createAndArm(title: String): FakeMutation {
        val ticket = journal.newTicket()
        return FakeMutation("create_item", {
            val item = store.create(title, null)
            ticket.created("item", item.id)
            board.arm(item.id)
            ticket.compensate("alarm", item.id)
            StepResult("ok", false, null, mapOf("id" to item.id))
        }, context = ticket)
    }

    @Test
    fun s8CompensatorsRunAfterTheRestoresInReverseOnce() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val pipeline = rig.pipeline(ScriptedGate.admitAll(), rig.submitting(rig.createAndArm("n1"), rig.createAndArm("n2")))
            pipeline.execute(CommandInput("two items with alarms"))
            assertEquals(setOf("item-1", "item-2"), rig.board.ids)

            val result = rig.journal.undoAll("run-1")

            assertTrue(result.toString(), result is UndoResult.Complete)
            assertEquals(
                listOf("restore:item-2", "restore:item-1", "disarm:item-2", "disarm:item-1"),
                rig.log.events,
            )
            assertEquals(emptySet<String>(), rig.board.ids)
            assertEquals(rig.seeded, rig.store.snapshot())
        }
    }

    @Test
    fun s8AFailedCompensatorIsRetriedAlone() = runTest {
        NoNetworkGuard.during {
            val rig = UndoRig()
            val pipeline = rig.pipeline(ScriptedGate.admitAll(), rig.submitting(rig.createAndArm("n1"), rig.createAndArm("n2")))
            pipeline.execute(CommandInput("two items with alarms"))
            rig.board.failOnce.add("item-2")

            val partial = rig.journal.undoAll("run-1")

            assertTrue(partial.toString(), partial is UndoResult.Partial)
            val failed = (partial as UndoResult.Partial).notRestored.single()
            assertEquals(1, failed.entry.position)
            assertEquals("alarm", failed.compensator)
            assertEquals(UndoReason.COMPENSATOR_FAILED, failed.reason)
            assertEquals(listOf(0), partial.restored.map { it.position })
            assertEquals(rig.seeded, rig.store.snapshot())
            assertEquals(setOf("item-2"), rig.board.ids)
            val firstPass = rig.log.events.size

            val retry = rig.journal.undoAll("run-1")

            assertTrue(retry.toString(), retry is UndoResult.Complete)
            assertEquals(listOf(1), (retry as UndoResult.Complete).restored.map { it.position })
            assertEquals(listOf("disarm:item-2"), rig.log.events.drop(firstPass))
            assertEquals(emptySet<String>(), rig.board.ids)
            val third = rig.journal.undoAll("run-1")
            assertTrue(third.toString(), third is UndoResult.AlreadyUndone)
            assertEquals(firstPass + 1, rig.log.events.size)
        }
    }
}
