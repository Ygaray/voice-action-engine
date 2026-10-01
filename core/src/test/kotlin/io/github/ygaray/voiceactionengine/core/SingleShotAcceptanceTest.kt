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
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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

    @Test
    fun s2WeakSingleIsHeldThenCommittedLaterAsALinkedRun() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(answerWith(entry("alpha", 0.5)))

            val original = rig.say()

            assertTrue(original.toString(), original is CommandOutcome.Completed)
            val held = original.held.single()
            val mutation = rig.resolver.mutations.single()
            assertEquals(0, mutation.applyCount)
            assertEquals(ActionKind.HELD, rig.sink.actions.single().action.kind)
            assertTrue(original.commits.isEmpty())

            val child = rig.pipeline.commitHeld(held)

            assertTrue(child.toString(), child is CommandOutcome.Completed)
            assertEquals(original.runId, child.parentRunId)
            assertNotEquals(original.runId, child.runId)
            assertEquals(1, mutation.applyCount)
            assertEquals(listOf(original.runId, child.runId), rig.sink.closedRunIds)
            assertEquals(1, rig.gate.calls)
            val actionsAfterFirst = rig.sink.actions.size

            val again = rig.pipeline.commitHeld(held)

            assertSame(child, again)
            assertEquals(actionsAfterFirst, rig.sink.actions.size)
            assertEquals(1, mutation.applyCount)
        }
    }

    @Test
    fun s3AllStrongBatchIsStillHeldAndTheGateIsAskedOnce() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(answerWith(entry("alpha", 0.95), entry("beta", 0.95), entry("gamma", 0.95)))

            val outcome = rig.say()

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, rig.gate.calls)
            assertEquals(3, rig.gate.proposals.single().mutations.size)
            assertEquals(3, outcome.held.single().mutations.size)
            assertEquals(List(3) { ActionKind.HELD }, rig.sink.actions.map { it.action.kind })
            assertTrue(outcome.commits.isEmpty())
            assertEquals(listOf(0, 0, 0), rig.resolver.mutations.map { it.applyCount })
        }
    }

    @Test
    fun s4AmendedConfirmAppliesOnlyTheReplacementList() = runTest {
        NoNetworkGuard.during {
            val rig = Rig(answerWith(entry("alpha", 0.95), entry("beta", 0.95), entry("gamma", 0.95)))
            val held = rig.say().held.single()
            val newDate = "2026-10-02"
            val amended = listOf(
                EntryMutation("alpha", 3.0, "unit", newDate, "item-a", strongVerdict(), rig.log),
                EntryMutation("beta", 1.0, "unit", newDate, "item-c", strongVerdict(), rig.log),
            )

            val child = rig.pipeline.commitHeld(held, amended)

            assertTrue(child.toString(), child is CommandOutcome.Completed)
            assertEquals(listOf(0, 0, 0), rig.resolver.mutations.map { it.applyCount })
            assertEquals(listOf(1, 1), amended.map { it.applyCount })
            assertEquals(
                listOf("applied:alpha:3.0:item-a:$newDate", "applied:beta:1.0:item-c:$newDate"),
                rig.appliedLines,
            )
            assertEquals(1, rig.gate.calls)
            assertEquals(listOf(ENTRIES_TOOL, ENTRIES_TOOL), child.commits.map { it.toolName })
            assertEquals(listOf("item-a", "item-c"), child.commits.map { it.targetIds.getValue("item") })
        }
    }

    @Test
    fun s5AFailingRowDoesNotPoisonItsSiblings() = runTest {
        NoNetworkGuard.during {
            val answer = answerWith(entry("alpha", 0.95), entry("beta", 0.95), entry("gamma", 0.95))
            for (mode in FailureMode.values()) {
                val rig = Rig(answer, ScriptedGate.admitAll(), failures = mapOf("beta" to mode))

                val outcome = rig.say()

                assertTrue("$mode: $outcome", outcome is CommandOutcome.Completed)
                assertEquals(
                    "$mode",
                    listOf(ActionKind.COMMITTED, ActionKind.IS_ERROR, ActionKind.COMMITTED),
                    outcome.executed.map { it.kind },
                )
                assertEquals("$mode", listOf(1, 1, 1), rig.resolver.mutations.map { it.applyCount })
                assertEquals(
                    "$mode",
                    listOf("applied:alpha:1.0:item-a:$TARGET_DATE", "applied:gamma:1.0:item-c:$TARGET_DATE"),
                    rig.appliedLines,
                )
            }
        }
    }

    @Test
    fun s6UnmatchedRowIsProposedThenRecoveredAtAmend() = runTest {
        NoNetworkGuard.during {
            val recovered = Rig(answerWith(entry("zeta", 0.95)))
            val proposed = recovered.say()
            val proposal = proposed.held.single().mutations.single()
            assertTrue(proposal.targetIds.isEmpty())
            assertEquals(0, recovered.resolver.mutations.single().applyCount)
            val fixed = EntryMutation("zeta", 1.0, "unit", TARGET_DATE, "item-d", strongVerdict(), recovered.log)

            recovered.pipeline.commitHeld(proposed.held.single(), listOf(fixed))

            assertEquals(listOf("applied:zeta:1.0:item-d:$TARGET_DATE"), recovered.appliedLines)

            val partial = Rig(answerWith(entry("zeta", 0.95), entry("alpha", 0.95)))
            val partialHeld = partial.say().held.single()
            val unresolved = EntryMutation("zeta", 1.0, "unit", TARGET_DATE, null, strongVerdict(), partial.log)
            val sibling = EntryMutation("alpha", 1.0, "unit", TARGET_DATE, "item-a", strongVerdict(), partial.log)

            val child = partial.pipeline.commitHeld(partialHeld, listOf(unresolved, sibling))

            assertEquals(listOf("skipped", "ok"), child.executed.map { it.appOutcomeToken })
            assertEquals(listOf("applied:alpha:1.0:item-a:$TARGET_DATE"), partial.appliedLines)

            val nothing = answerWith()
            val oneTier = Rig(nothing)
            val unhandled = oneTier.say()
            assertTrue(unhandled.toString(), unhandled is CommandOutcome.Unhandled)
            assertEquals(0, oneTier.gate.calls)
            assertTrue(unhandled.executed.isEmpty())

            val second = ScriptedStrategy(StrategyId("second"), { _, _ -> StrategyOutcome.Completed("next tier") })
            val twoResolver = FixtureResolver(fixtureCatalog(), RecordingSink())
            val twoTiers = acceptancePipeline(
                listOf(fixtureTier(twoResolver), second),
                FakeAiProvider(ProviderId.ANTHROPIC, nothing),
                deferModeGate(),
                RecordingCommitSink(),
            )
            val escalated = twoTiers.execute(CommandInput("record nothing", "en", null))
            assertEquals(1, second.executions)
            assertEquals("next tier", (escalated as CommandOutcome.Completed).reply)
        }
    }
}
