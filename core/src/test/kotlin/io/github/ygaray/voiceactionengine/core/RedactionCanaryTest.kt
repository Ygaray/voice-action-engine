package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An app object whose own text is a canary: if the engine ever prints the object itself instead of its class name,
 * the canary shows up in the output.
 */
private class Canary(private val label: String) {
    override fun toString(): String = "$CANARY-$label"
}

private const val CANARY = "CANARY"
private const val KEY = "sk-CANARY-KEY"

/** Everything the engine returns, delivers or prints is swept for the canaries planted in every user-content slot. */
class RedactionCanaryTest {

    private val printed = CopyOnWriteArrayList<String>()

    private fun see(value: Any?) {
        printed.add(value.toString())
    }

    private fun canaryResult() = StepResult("$CANARY-RESULT", false, "$CANARY-TOKEN", mapOf("id" to "$CANARY-ID"))

    private fun clarification(): TerminalCall = TerminalCall(
        "ask_user",
        buildJsonObject {
            put("question", "$CANARY-QUESTION")
            val one = option("$CANARY-OPTION-ONE", "$CANARY-LABEL-ONE")
            val two = option("$CANARY-OPTION-TWO", "$CANARY-LABEL-TWO")
            put("options", JsonArray(listOf(one, two)))
        },
    )

    private fun option(id: String, label: String): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
    }

    private fun mutation(name: String) = FakeMutation(
        toolName = name,
        behavior = { canaryResult() },
        targetIds = mapOf("target" to "$CANARY-TARGET"),
        context = Canary("SNAPSHOT"),
    )

    private fun sweepOutcome(outcome: CommandOutcome) {
        see(outcome)
        see(outcome.trace)
        outcome.trace.attempts.forEach { attempt ->
            see(attempt)
            attempt.turns.forEach { see(it) }
            see(attempt.usage)
        }
        outcome.executed.forEach { see(it) }
        outcome.held.forEach { see(it) }
        when (outcome) {
            is CommandOutcome.Completed -> see(outcome.terminalCall)
            is CommandOutcome.Failed -> {
                see(outcome.reason)
                see(outcome.details)
            }
            is CommandOutcome.Unhandled -> see(outcome.lastReason)
        }
    }

    private val sessions = CopyOnWriteArrayList<CommandSession>()
    private val results = CopyOnWriteArrayList<DispatchResult>()

    /** Tier one previews, reports a turn and escalates with a carry whose own text is a canary. */
    private fun firstTier() = ScriptedStrategy(
        StrategyId("first"),
        { _, session ->
            sessions.add(session)
            val preview = ToolStep.Finished("lookup", FinishedKind.PREVIEW, canaryResult(), Canary("PREVIEW"))
            see(preview)
            results.add(session.submit(preview))
            val usage = Usage(1, 2, 3, 4)
            session.recordTurn(TurnRecord(ProviderId.ANTHROPIC, "claude-x", "tool_use", listOf("lookup"), usage, 5))
            StrategyOutcome.Escalate(EscalationReason.ModelDeclined(), Canary("CARRY")).also { see(it) }
        },
    )

    /** Tier two commits one change, has one held, and ends with a clarification as the terminal call. */
    private fun secondTier() = ScriptedStrategy(
        StrategyId("second"),
        { _, session ->
            sessions.add(session)
            val commit = ToolStep.Mutation(mutation("commit_tool"))
            val held = ToolStep.Mutation(listOf(mutation("held_tool"), mutation("held_tool_two")))
            see(commit)
            see(held)
            results.add(session.submit(commit))
            results.add(session.submit(held))
            StrategyOutcome.Completed(null, clarification()).also { see(it) }
        },
    )

    private fun sweepDelivered(gate: ScriptedGate, sink: RecordingCommitSink, events: List<PipelineEvent>) {
        gate.proposals.forEach { see(it) }
        results.forEach { see(it) }
        sessions.forEach { see(it) }
        sink.actions.forEach { see(it) }
        sink.closes.forEach { see(it) }
        events.forEach { see(it) }
        assertNotNull(gate.proposals.firstOrNull())
        checkEventFields(events)
        assertTrue(events.last() is PipelineEvent.RunClosed)
    }

    private suspend fun fullScriptedRun(): CommandOutcome {
        val listener = RecordingEventListener()
        val sink = RecordingCommitSink()
        val admit = GateDecision.Admit(listOf(mutation("amended_tool")))
        val hold = GateDecision.Hold(Canary("HOLD"), "$CANARY-HOLD-TOKEN")
        val gate = ScriptedGate.sequence(null, admit, hold)
        val pipeline = commandPipeline {
            tier(firstTier())
            tier(secondTier())
            this.gate = gate
            commitSink = sink
            this.listener = listener
        }
        val input = CommandInput("$CANARY-TRANSCRIPT", "en", Canary("CONTEXT"))
        see(input)
        see(Credential(ProviderId.ANTHROPIC, KEY))
        see(pipeline)
        see(admit)
        see(hold)
        val outcome = pipeline.execute(input)

        sweepOutcome(outcome)
        sweepDelivered(gate, sink, listener.events)
        (outcome as? CommandOutcome.Completed)?.terminalCall?.asClarification()?.let { clarification ->
            see(clarification)
            clarification.options.forEach { see(it) }
        }
        return outcome
    }

    private suspend fun throwingRun(): CommandOutcome {
        val listener = RecordingEventListener()
        val sink = RecordingCommitSink()
        val boom = FakeMutation("boom_tool", { error("$CANARY-APPLY-MESSAGE $KEY") }, context = Canary("BOOM"))
        val strategy = ScriptedStrategy(
            StrategyId("throwing"),
            { _, session ->
                see(session.submit(ToolStep.Mutation(boom)))
                error("$CANARY-EXCEPTION-MESSAGE $KEY")
            },
        )
        val outcome = commandPipeline {
            tier(strategy)
            gate = ScriptedGate.admitAll()
            commitSink = sink
            this.listener = listener
        }.execute(CommandInput("$CANARY-TRANSCRIPT", "es", Canary("CONTEXT")))

        sweepOutcome(outcome)
        sink.actions.forEach { see(it) }
        sink.closes.forEach { see(it) }
        listener.events.forEach { see(it) }
        checkEventFields(listener.events)
        return outcome
    }

    /** Codes and tool names in events match a plain identifier shape: no free text can hide in them. */
    private fun checkEventFields(events: List<PipelineEvent>) {
        val identifier = Regex("[a-z0-9_.-]+")
        events.forEach { event ->
            assertTrue(event.runId, event.runId.isNotEmpty())
            when (event) {
                is PipelineEvent.ActionRecorded -> assertTrue(identifier.matches(event.toolName))
                is PipelineEvent.EngineCode -> assertTrue(identifier.matches(event.code.value))
                is PipelineEvent.TierSkipped -> assertTrue(identifier.matches(event.code.value))
                is PipelineEvent.RunClosed -> assertTrue(identifier.matches(event.terminationCode))
                else -> Unit
            }
        }
    }

    @Test
    fun noCanaryAppearsInAnythingTheEngineReturnsDeliversOrPrints() = runTest {
        NoNetworkGuard.during {
            val full = fullScriptedRun()
            val thrown = throwingRun()

            val leaks = printed.filter { CANARY in it || KEY in it }
            assertTrue("leaked: $leaks", leaks.isEmpty())
            assertTrue("swept only ${printed.toSet().size} distinct values", printed.toSet().size >= MIN_DISTINCT)
            assertTrue(full is CommandOutcome.Completed)
            assertTrue(thrown is CommandOutcome.Failed)
            val reason = (thrown as CommandOutcome.Failed).reason
            assertEquals("IllegalStateException", (reason as FailureReason.Unexpected).errorClass)
            thrown.trace.codes.forEach { assertTrue(it.value, Regex("[a-z_]+").matches(it.value)) }
            full.trace.codes.forEach { assertTrue(it.value, Regex("[a-z_]+").matches(it.value)) }
        }
    }

    @Test
    fun theCredentialKeyIsNotInItsOwnText() {
        val credential = Credential(ProviderId.ANTHROPIC, KEY)

        assertTrue(KEY.startsWith("sk-"))
        assertTrue(KEY !in credential.toString())
        assertEquals("Credential(provider=anthropic)", credential.toString())
    }

    private companion object {
        const val MIN_DISTINCT = 25
    }
}
