package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/** The ids of the planned steps a tier never ran travel on the outcome that ends the command, and only there. */
class RemainingStepIdsTest {

    private fun tier(id: String, step: StrategyStep) = ScriptedStrategy(StrategyId(id), step)

    private fun ok(name: String) = FakeMutation(name, StepResult("done", false, "ok", emptyMap()))

    private fun pipeline(gate: ScriptedGate, vararg tiers: ScriptedStrategy): CommandPipeline =
        commandPipeline {
            tiers.forEach { tier(it) }
            this.gate = gate
            commitSink = RecordingCommitSink()
        }

    private fun second(): ScriptedStrategy = tier("second") { _, _ -> StrategyOutcome.Completed("second") }

    @Test
    fun aCompletedWithIdsEndsTheCommandWithThoseIds() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, _ -> StrategyOutcome.Completed("done", null, true, listOf("a", "b")) }

            val outcome = pipeline(ScriptedGate.admitAll(), first).execute(CommandInput("go"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals(listOf("a", "b"), completed.remainingStepIds)
            assertTrue(completed.partial)
        }
    }

    @Test
    fun everyPublicCompletedConstructorLeavesTheIdsEmpty() = runTest {
        NoNetworkGuard.during {
            val steps = listOf<StrategyStep>(
                { _, _ -> StrategyOutcome.Completed("one") },
                { _, _ -> StrategyOutcome.Completed("two", null) },
                { _, _ -> StrategyOutcome.Completed(null, null, true) },
            )
            steps.forEach { step ->
                val outcome = pipeline(ScriptedGate.admitAll(), tier("only", step)).execute(CommandInput("go"))
                assertEquals(emptyList<String>(), (outcome as CommandOutcome.Completed).remainingStepIds)
            }
        }
    }

    @Test
    fun aSuppressedEscalateAfterAHoldCarriesItsIds() = runTest {
        NoNetworkGuard.during {
            val write = ok("write")
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Escalate(EscalationReason.Other("stopped"), "carry", listOf("b"))
            }
            val next = second()

            val outcome = pipeline(ScriptedGate.holdAll("confirm"), first, next).execute(CommandInput("go"))

            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            assertEquals(listOf("b"), completed.remainingStepIds)
            assertEquals("escalation_suppressed", outcome.trace.attempts.single().outcome)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(0, next.executions)
        }
    }

    @Test
    fun aHandedUpEscalateDropsItsIdsAndTheNextTierRuns() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, _ ->
                StrategyOutcome.Escalate(EscalationReason.Other("stopped"), null, listOf("b"))
            }
            val next = second()

            val outcome = pipeline(ScriptedGate.admitAll(), first, next).execute(CommandInput("go"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals(1, next.executions)
            assertFalse(completed.partial)
            assertEquals(emptyList<String>(), completed.remainingStepIds)
        }
    }

    @Test
    fun aNoMatchAfterWorkEndsSuppressedWithNoIds() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(ok("write")))
                StrategyOutcome.NoMatch()
            }

            val outcome = pipeline(ScriptedGate.admitAll(), first, second()).execute(CommandInput("go"))

            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            assertEquals(emptyList<String>(), completed.remainingStepIds)
        }
    }

    @Test
    fun aCommitHeldOutcomeCarriesNoIds() = runTest {
        NoNetworkGuard.during {
            val write = ok("write")
            val first = tier("first") { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Escalate(EscalationReason.Other("stopped"), null, listOf("b"))
            }
            val pipeline = pipeline(ScriptedGate.holdAll("confirm"), first)

            val held = pipeline.execute(CommandInput("go")).held.single()
            val committed = pipeline.commitHeld(held)

            val completed = committed as CommandOutcome.Completed
            assertFalse(completed.partial)
            assertEquals(emptyList<String>(), completed.remainingStepIds)
            assertEquals(1, write.applyCount)
        }
    }

    @Test
    fun theCompletedToStringPrintsTheCountAndNeverTheIds() = runTest {
        NoNetworkGuard.during {
            val first = tier("first") { _, _ ->
                StrategyOutcome.Completed(null, null, true, listOf("secret-step-a", "secret-step-b"))
            }

            val outcome = pipeline(ScriptedGate.admitAll(), first).execute(CommandInput("go"))

            val text = outcome.toString()
            assertTrue(text, text.contains("remainingSteps=2"))
            assertFalse(text, text.contains("secret-step"))
            val strategyCompleted = StrategyOutcome.Completed(null, null, true, listOf("secret-step-a"))
            assertFalse(strategyCompleted.toString().contains("secret"))
            assertFalse(
                StrategyOutcome.Escalate(EscalationReason.Other("x"), null, listOf("secret-step-a"))
                    .toString().contains("secret"),
            )
        }
    }

    @Test
    fun theStrategyOutcomePublicConstructorsKeepTheirSignatures() {
        val string = String::class.java
        val call = TerminalCall::class.java
        val bool = Boolean::class.javaPrimitiveType
        val completed = StrategyOutcome.Completed::class.java.declaredConstructors
            .filter { !it.isSynthetic && Modifier.isPublic(it.modifiers) }
            .map { it.parameterTypes.toList() }
        assertTrue(completed.toString(), listOf(string, call, bool) in completed)
        assertTrue(completed.toString(), listOf(string, call) in completed)
        assertTrue(completed.toString(), listOf(string) in completed)

        val reason = EscalationReason::class.java
        val any = Any::class.java
        val escalate = StrategyOutcome.Escalate::class.java.declaredConstructors
            .filter { !it.isSynthetic && Modifier.isPublic(it.modifiers) }
            .map { it.parameterTypes.toList() }
        assertTrue(escalate.toString(), listOf(reason, any) in escalate)
        assertTrue(escalate.toString(), listOf(reason) in escalate)
        // The longer primary constructors are Kotlin-internal; the Metalava check proves they are not public API.
        val every = StrategyOutcome.Completed::class.java.declaredConstructors.filter { !it.isSynthetic }
            .map { it.parameterTypes.toList() }
        assertTrue(every.toString(), listOf(string, call, bool, List::class.java) in every)
    }
}
