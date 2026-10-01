package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tier that wrote or holds a change can never hand the command to another tier. */
class EscalationSafetyTest {

    private fun tier(id: String, step: StrategyStep) = ScriptedStrategy(StrategyId(id), step)

    private fun ok(name: String) = FakeMutation(name, StepResult("done", false, "ok", emptyMap()))

    private suspend fun run(gate: ScriptedGate, first: ScriptedStrategy, second: ScriptedStrategy): CommandOutcome =
        commandPipeline {
            tier(first)
            tier(second)
            this.gate = gate
            commitSink = RecordingCommitSink()
        }.execute(CommandInput("rename it"))

    private fun untouchedSecond(): ScriptedStrategy = tier("second") { _, _ -> StrategyOutcome.Completed("second") }

    @Test
    fun aCommitThenEscalateEndsPartialAndTierTwoNeverRuns() = runTest {
        NoNetworkGuard.during {
            val write = ok("write")
            val reason = EscalationReason.ModelDeclined()
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Escalate(reason, "carry")
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            assertNull(completed.reply)
            assertNull(completed.terminalCall)
            assertEquals(0, second.executions)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(ActionKind.COMMITTED), outcome.commits.map { it.kind })
            val attempt = outcome.trace.attempts.single()
            assertEquals("escalation_suppressed", attempt.outcome)
            assertSame(reason, attempt.suppressedEscalation)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
        }
    }

    @Test
    fun aCommitThenNoMatchEndsPartialWithNoSuppressedReason() = runTest {
        NoNetworkGuard.during {
            val write = ok("write")
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.NoMatch()
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            assertEquals(0, second.executions)
            assertEquals(1, write.applyCount)
            val attempt = outcome.trace.attempts.single()
            assertEquals("escalation_suppressed", attempt.outcome)
            assertNull(attempt.suppressedEscalation)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
        }
    }

    @Test
    fun anErroredApplyCountsAsCommittedForTheGuard() = runTest {
        NoNetworkGuard.during {
            val failing = FakeMutation("write", StepResult("bad", true, "err", emptyMap()))
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(failing))
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            assertTrue((outcome as CommandOutcome.Completed).partial)
            assertEquals(0, second.executions)
            assertEquals(1, failing.applyCount)
            assertEquals(listOf(ActionKind.IS_ERROR), outcome.executed.map { it.kind })
        }
    }

    @Test
    fun anApplyThatThrowsCountsAsCommittedForTheGuard() = runTest {
        NoNetworkGuard.during {
            val throwing = FakeMutation("write", { throw IllegalStateException("disk full") })
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(throwing))
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            assertTrue((outcome as CommandOutcome.Completed).partial)
            assertEquals(0, second.executions)
            assertEquals(1, throwing.applyCount)
        }
    }

    @Test
    fun aHoldThenEscalateEndsPartialListsTheHeldProposalAndTierTwoNeverRuns() = runTest {
        NoNetworkGuard.during {
            val write = ok("write")
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.holdAll("needs confirm"), first, second)

            assertTrue((outcome as CommandOutcome.Completed).partial)
            assertEquals(0, second.executions)
            assertEquals(0, write.applyCount)
            assertEquals(1, outcome.held.size)
            assertTrue(outcome.commits.isEmpty())
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
        }
    }

    @Test
    fun aPreviewOnlyTierStillEscalates() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                val step = ToolStep.Finished("rename", FinishedKind.PREVIEW, StepResult("p", false, "pv", emptyMap()))
                session.submit(step)
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            val completed = outcome as CommandOutcome.Completed
            assertFalse(completed.partial)
            assertEquals("second", completed.reply)
            assertEquals(1, second.executions)
            assertFalse(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
        }
    }

    @Test
    fun aRejectedOnlyTierStillEscalates() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                val step = ToolStep.Finished("rename", FinishedKind.ERROR, StepResult("x", true, "rej", emptyMap()))
                session.submit(step)
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            assertFalse((outcome as CommandOutcome.Completed).partial)
            assertEquals(1, second.executions)
            assertEquals(listOf(ActionKind.IS_ERROR), outcome.executed.map { it.kind })
        }
    }

    @Test
    fun aBudgetFailureAfterACommitStaysFailedAndCarriesTheCommit() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(ok("write")))
                StrategyOutcome.Failed(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS))
            }
            val second = untouchedSecond()

            val outcome = run(ScriptedGate.admitAll(), first, second)

            val failed = outcome as CommandOutcome.Failed
            assertTrue(failed.reason is FailureReason.BudgetExceeded)
            assertEquals(listOf("write"), outcome.commits.map { it.toolName })
            assertEquals(0, second.executions)
        }
    }

    @Test
    fun aNormalCompletionIsNotPartial() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(ok("write")))
                StrategyOutcome.Completed("all done")
            }

            val outcome = run(ScriptedGate.admitAll(), first, untouchedSecond())

            assertFalse((outcome as CommandOutcome.Completed).partial)
            assertEquals("all done", outcome.reply)
        }
    }

    @Test
    fun partialIsAPublicFieldWithNoDefaultedConstructor() {
        val type = CommandOutcome.Completed::class.java
        val marker = "kotlin.jvm.internal.DefaultConstructorMarker"
        val defaulted = type.declaredConstructors.filter { c -> c.parameterTypes.any { it.name == marker } }
        assertTrue("a default on partial adds a synthetic constructor: $defaulted", defaulted.isEmpty())
        assertNotNull(type.getMethod("getPartial"))
    }
}
