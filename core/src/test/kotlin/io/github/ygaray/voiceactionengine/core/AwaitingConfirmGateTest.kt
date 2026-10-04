package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.AwaitingConfirmGate
import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.ConfirmAmendHook
import io.github.ygaray.voiceactionengine.core.commit.ConfirmationPolicy
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The suspend-mode confirm helper: the user's answer arrives through resolve, and only a confirm writes. */
class AwaitingConfirmGateTest {
    private class Subject

    private val tierId = StrategyId("only")

    private fun ok(name: String) = FakeMutation(name, StepResult("saved"))

    private fun proposalOf(vararg writes: PendingMutation) = CommitProposal("run-1", null, writes.toList())

    /** A one-tier pipeline that submits [write] and finishes; the sink records what happened. */
    private fun pipelineFor(
        gate: AwaitingConfirmGate,
        sink: RecordingCommitSink,
        write: PendingMutation,
    ) = commandPipeline {
        tier(
            ScriptedStrategy(tierId, { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("done")
            }),
        )
        this.gate = gate
        commitSink = sink
        policy = TierPolicySource.fixed(TierPolicy.DEFAULT)
    }

    private fun CoroutineScope.runIn(pipeline: CommandPipeline): () -> CommandOutcome? {
        var outcome: CommandOutcome? = null
        launch { outcome = pipeline.execute(CommandInput("save it")) }
        return { outcome }
    }

    @Test
    fun aConfirmPublishesPendingThenAppliesOnceAndCompletes() = runTest {
        NoNetworkGuard.during {
            val subject = Subject()
            val gate = AwaitingConfirmGate(ConfirmationPolicy { subject })
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed("done")
                    }),
                )
                this.gate = gate
                commitSink = sink
            }
            var outcome: CommandOutcome? = null
            launch { outcome = pipeline.execute(CommandInput("save it")) }
            runCurrent()

            val pending = gate.pending.value!!
            assertSame(subject, pending.subject)
            assertEquals(listOf("write"), pending.proposal.mutations.map { it.toolName })
            assertEquals(0, write.applyCount)
            assertTrue(gate.resolve(pending.id, true))
            runCurrent()

            val completed = outcome as CommandOutcome.Completed
            assertEquals(1, write.applyCount)
            assertEquals(ActionKind.COMMITTED, sink.actions.single().action.kind)
            assertEquals(listOf("write"), completed.commits.map { it.toolName })
            assertTrue(completed.held.isEmpty())
            assertNull(gate.pending.value)
        }
    }

    @Test
    fun aDeclineAppliesNothingAndHoldsWithTheSubjectAsTheReason() = runTest {
        NoNetworkGuard.during {
            val subject = Subject()
            val gate = AwaitingConfirmGate(ConfirmationPolicy { subject }, 120_000L, null, "held")
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed("done")
                    }),
                )
                this.gate = gate
                commitSink = sink
            }
            var outcome: CommandOutcome? = null
            launch { outcome = pipeline.execute(CommandInput("save it")) }
            runCurrent()

            assertTrue(gate.resolve(gate.pending.value!!.id, false))
            runCurrent()

            val completed = outcome as CommandOutcome.Completed
            assertEquals(0, write.applyCount)
            val event = sink.actions.single().action
            assertEquals(ActionKind.HELD, event.kind)
            assertFalse(event.applied)
            assertEquals("held", event.appOutcomeToken)
            assertSame(subject, completed.held.single().reason)
            assertTrue(completed.commits.isEmpty())
            assertNull(gate.pending.value)
        }
    }

    @Test
    fun aPolicyReturningNullAdmitsAtOnceAndNeverPublishesPending() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { null })

        val decision = gate.admit(proposalOf(ok("write")))

        assertTrue(decision is GateDecision.Admit)
        assertNull((decision as GateDecision.Admit).amended)
        assertNull(gate.pending.value)
    }

    @Test
    fun noAnswerHoldsOnlyAfterTheDefaultWindowAndCarriesTheSubjectAndToken() = runTest {
        val subject = Subject()
        val gate = AwaitingConfirmGate(ConfirmationPolicy { subject }, 120_000L, null, "held")
        val decision = async { gate.admit(proposalOf(ok("write"))) }
        runCurrent()

        advanceTimeBy(119_999L)
        runCurrent()
        assertFalse(decision.isCompleted)
        advanceTimeBy(2L)
        runCurrent()

        val hold = decision.await() as GateDecision.Hold
        assertSame(subject, hold.reason)
        assertEquals("held", hold.appOutcomeToken)
        assertNull(gate.pending.value)
    }

    @Test
    fun aConfiguredWindowIsHonoured() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() }, 5_000L)
        val decision = async { gate.admit(proposalOf(ok("write"))) }
        runCurrent()

        advanceTimeBy(4_999L)
        runCurrent()
        assertFalse(decision.isCompleted)
        advanceTimeBy(2L)
        runCurrent()

        assertTrue(decision.await() is GateDecision.Hold)
    }

    @Test
    fun aThrowingPolicyPropagatesAtUnitLevelAndFailsClosedThroughThePipeline() = runTest {
        NoNetworkGuard.during {
            val gate = AwaitingConfirmGate(ConfirmationPolicy { error("policy broke") })
            try {
                gate.admit(proposalOf(ok("write")))
                fail("a policy error must propagate out of admit")
            } catch (expected: IllegalStateException) {
                assertNull(gate.pending.value)
            }
            val sink = RecordingCommitSink()
            val write = ok("write")

            val outcome = pipelineFor(gate, sink, write).execute(CommandInput("save it"))

            assertEquals(0, write.applyCount)
            assertEquals(ActionKind.IS_ERROR, sink.actions.single().action.kind)
            assertTrue(outcome.held.isEmpty())
            assertTrue(outcome.trace.codes.map { it.value }.contains("gate_error"))
        }
    }

    @Test
    fun theAmendHookListIsWhatAdmitCarriesAndWhatThePipelineApplies() = runTest {
        NoNetworkGuard.during {
            val subject = Subject()
            val original = ok("original")
            val amended = ok("amended")
            var seenProposal: CommitProposal? = null
            var seenSubject: Any? = null
            val gate = AwaitingConfirmGate(
                ConfirmationPolicy { subject },
                120_000L,
                ConfirmAmendHook { proposal, s ->
                    seenProposal = proposal
                    seenSubject = s
                    listOf(amended)
                },
                null,
            )
            val sink = RecordingCommitSink()
            val outcomeOf = runIn(pipelineFor(gate, sink, original))
            runCurrent()

            assertTrue(gate.resolve(gate.pending.value!!.id, true))
            runCurrent()

            assertEquals(0, original.applyCount)
            assertEquals(1, amended.applyCount)
            assertEquals(listOf("amended"), outcomeOf()!!.commits.map { it.toolName })
            assertSame(subject, seenSubject)
            assertEquals(listOf("original"), seenProposal!!.mutations.map { it.toolName })
        }
    }

    @Test
    fun theAmendHookListIsCarriedOnTheAdmitDecision() = runTest {
        val amended = ok("amended")
        val gate = AwaitingConfirmGate(
            ConfirmationPolicy { Subject() },
            120_000L,
            ConfirmAmendHook { _, _ -> listOf(amended) },
            null,
        )
        val decision = async { gate.admit(proposalOf(ok("original"))) }
        runCurrent()
        gate.resolve(gate.pending.value!!.id, true)
        runCurrent()

        val admit = decision.await() as GateDecision.Admit
        assertSame(amended, admit.amended!!.single())
    }

    @Test
    fun aHookThatReturnsNullAppliesTheProposalUnchanged() = runTest {
        val gate = AwaitingConfirmGate(
            ConfirmationPolicy { Subject() },
            120_000L,
            ConfirmAmendHook { _, _ -> null },
            null,
        )
        val decision = async { gate.admit(proposalOf(ok("original"))) }
        runCurrent()
        gate.resolve(gate.pending.value!!.id, true)
        runCurrent()

        assertNull((decision.await() as GateDecision.Admit).amended)
    }

    @Test
    fun aThrowingAmendHookFailsClosedThroughThePipeline() = runTest {
        NoNetworkGuard.during {
            val gate = AwaitingConfirmGate(
                ConfirmationPolicy { Subject() },
                120_000L,
                ConfirmAmendHook { _, _ -> error("hook broke") },
                null,
            )
            val sink = RecordingCommitSink()
            val write = ok("write")
            val outcomeOf = runIn(pipelineFor(gate, sink, write))
            runCurrent()

            gate.resolve(gate.pending.value!!.id, true)
            runCurrent()

            val outcome = outcomeOf()!!
            assertEquals(0, write.applyCount)
            assertTrue(outcome.held.isEmpty())
            assertEquals(ActionKind.IS_ERROR, sink.actions.single().action.kind)
            assertTrue(outcome.trace.codes.map { it.value }.contains("gate_error"))
            assertNull(gate.pending.value)
        }
    }

    @Test
    fun cancellingWhilePendingPropagatesClearsPendingAndRejectsTheStaleId() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() })
        val job = launch { gate.admit(proposalOf(ok("write"))) }
        runCurrent()
        val id = gate.pending.value!!.id

        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(id, true))
    }

    @Test
    fun cancellingTheRunWhilePendingRecordsNothingAndAppliesNothing() = runTest {
        NoNetworkGuard.during {
            val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() })
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = pipelineFor(gate, sink, write)
            val job = launch { pipeline.execute(CommandInput("save it")) }
            runCurrent()
            assertNotNull(gate.pending.value)

            job.cancel()
            runCurrent()

            assertTrue(job.isCancelled)
            assertEquals(0, write.applyCount)
            assertTrue(sink.actions.isEmpty())
            assertTrue(sink.closes.single() is RunTermination.Cancelled)
            assertNull(gate.pending.value)
        }
    }

    @Test
    fun anUnknownIdAndASecondResolveAreNoOps() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() })
        val decision = async { gate.admit(proposalOf(ok("write"))) }
        runCurrent()
        val id = gate.pending.value!!.id

        assertFalse(gate.resolve(id + 1, true))
        assertFalse(gate.resolve(-1L, true))
        assertEquals(id, gate.pending.value!!.id)
        assertFalse(decision.isCompleted)
        assertTrue(gate.resolve(id, false))
        assertFalse(gate.resolve(id, true))
        runCurrent()

        assertTrue("the first answer wins", decision.await() is GateDecision.Hold)
        assertFalse(gate.resolve(id, true))
    }

    @Test
    fun concurrentAdmitsSerializeWithIncreasingIdsAndResolveIndependently() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() })
        val first = async { gate.admit(proposalOf(ok("first"))) }
        runCurrent()
        val second = async { gate.admit(proposalOf(ok("second"))) }
        runCurrent()
        val firstId = gate.pending.value!!.id
        assertEquals(listOf("first"), gate.pending.value!!.proposal.mutations.map { it.toolName })
        assertFalse(second.isCompleted)

        assertTrue(gate.resolve(firstId, true))
        runCurrent()

        val secondId = gate.pending.value!!.id
        assertTrue(secondId > firstId)
        assertEquals(listOf("second"), gate.pending.value!!.proposal.mutations.map { it.toolName })
        assertFalse(gate.resolve(firstId, true))
        assertTrue(gate.resolve(secondId, false))
        runCurrent()

        assertTrue(first.await() is GateDecision.Admit)
        assertTrue(second.await() is GateDecision.Hold)
        assertNotEquals(firstId, secondId)
        assertNull(gate.pending.value)
    }

    @Test
    fun nothingPrintedByEitherToStringContainsTheSubject() = runTest {
        val canary = "CANARY-NOTE-TITLE"
        val gate = AwaitingConfirmGate(ConfirmationPolicy { canary })
        val decision = async { gate.admit(proposalOf(ok("write"))) }
        runCurrent()

        val pending = gate.pending.value!!
        assertFalse(pending.toString().contains(canary))
        assertFalse(gate.toString().contains(canary))
        assertEquals("PendingConfirmation(id=${pending.id}, subject=String)", pending.toString())
        assertEquals("AwaitingConfirmGate(timeoutMillis=120000, pending=${pending.id})", gate.toString())
        gate.resolve(pending.id, false)
        runCurrent()
        decision.await()
        assertEquals("AwaitingConfirmGate(timeoutMillis=120000, pending=none)", gate.toString())
    }

    @Test
    fun aConfirmationResolvedAfter119SecondsStillAdmitsBecauseTheEngineAddsNoTimeout() = runTest {
        NoNetworkGuard.during {
            val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() })
            val sink = RecordingCommitSink()
            val write = ok("write")
            val outcomeOf = runIn(pipelineFor(gate, sink, write))
            runCurrent()

            advanceTimeBy(119_000L)
            runCurrent()
            assertTrue(gate.resolve(gate.pending.value!!.id, true))
            runCurrent()

            assertEquals(1, write.applyCount)
            assertTrue(outcomeOf() is CommandOutcome.Completed)
            assertEquals(listOf("write"), outcomeOf()!!.commits.map { it.toolName })
        }
    }

    @Test
    fun aNonPositiveWindowIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { AwaitingConfirmGate(ConfirmationPolicy { null }, 0L) }
        assertThrows(IllegalArgumentException::class.java) { AwaitingConfirmGate(ConfirmationPolicy { null }, -1L) }
    }

    @Test
    fun anAnswerThatWinsTheRaceAgainstTheTimeoutIsHonouredNotDiscarded() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() }, RACE_WINDOW_MILLIS)
        var reported: Boolean? = null
        // Started first, so its timer fires before the gate's own at the same virtual instant.
        val answerer = launch(start = CoroutineStart.UNDISPATCHED) {
            delay(RACE_WINDOW_MILLIS)
            reported = gate.resolve(checkNotNull(gate.pending.value).id, true)
        }
        val decision = gate.admit(proposalOf(ok("write")))
        answerer.join()

        // resolve said it answered the confirmation, so the confirmation must be admitted.
        assertEquals(true, reported)
        assertTrue(decision is GateDecision.Admit)
        assertNull(gate.pending.value)
    }

    @Test
    fun anAnswerAfterTheTimeoutReturnsFalseAndTheGateHolds() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() }, RACE_WINDOW_MILLIS)
        val decision = async { gate.admit(proposalOf(ok("write"))) }
        runCurrent()
        val id = checkNotNull(gate.pending.value).id
        advanceTimeBy(RACE_WINDOW_MILLIS + 1)
        runCurrent()

        assertFalse(gate.resolve(id, true))
        assertTrue(decision.await() is GateDecision.Hold)
    }

    @Test
    fun cancellingTheWaiterSettlesTheConfirmationSoALateAnswerReturnsFalse() = runTest {
        val gate = AwaitingConfirmGate(ConfirmationPolicy { Subject() })
        val waiter = launch { gate.admit(proposalOf(ok("write"))) }
        runCurrent()
        val id = checkNotNull(gate.pending.value).id
        waiter.cancel()
        waiter.join()

        assertFalse(gate.resolve(id, true))
        assertNull(gate.pending.value)
    }

    private companion object {
        const val RACE_WINDOW_MILLIS = 100L
    }
}
