package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val HELD_BYTES = """{"applied":false,"status":"held_for_confirmation"}"""
private const val READ_TOOL = "read_entries"
private const val READ_CONTENT = "{}"
private const val ARGUMENT_MARKER = "ARGUMENT-MARKER-9021"

/** The app's tool seam composes with the shipped write path before any loop exists, and its fixture behaves. */
class ToolExecutorSeamTest {

    private class Run(
        val outcome: CommandOutcome,
        val sink: RecordingCommitSink,
        val gate: ScriptedGate,
        val dispatches: List<DispatchResult>,
    )

    private fun arguments(): JsonObject = buildJsonObject { put("text", ARGUMENT_MARKER) }

    private fun answeringWithTheTool(): FakeAiProvider =
        FakeAiProvider(
            ProviderId.ANTHROPIC,
            FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, arguments(), Usage(1, 0, 0, 1)),
        )

    /** Does by hand what the loop will do: one routed call, then the executor's step through the session. */
    private suspend fun runByHand(
        gate: ScriptedGate,
        executor: ScriptedToolExecutor,
    ): Run {
        val sink = RecordingCommitSink()
        val dispatches = CopyOnWriteArrayList<DispatchResult>()
        val tooling = ToolSpecProvider.fixed(ToolingSnapshot("fixed system", listOf(entriesTool()), null))
        val step: StrategyStep = { input, session ->
            val snapshot = tooling.tooling(input)
            val request = ModelRequest(
                snapshot.system,
                listOf(UserMessage(input.transcript)),
                snapshot.tools,
                ToolChoice.Auto(),
                session.policy.maxTokensPerTurn,
                CacheDirective(true),
                false,
            )
            val result = session.model().complete(request) as ModelResult.Success
            val call = result.response.message.toolCalls.first()
            dispatches.add(session.submit(executor.prepare(Extraction(call.name, call.arguments), input)))
            StrategyOutcome.Completed(null)
        }
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("seam"), step))
            provider(answeringWithTheTool())
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model"))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "test-key")
            this.gate = gate
            commitSink = sink
        }
        return Run(pipeline.execute(CommandInput("add two things", "en", null)), sink, gate, dispatches.toList())
    }

    @Test
    fun anExecutorMutationComposesWithTheGateAndTheSink() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(ENTRIES_TOOL, StepResult("saved", false, "ok", emptyMap()))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(write))

            val run = runByHand(ScriptedGate.admitAll(), executor)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(1, run.gate.calls)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(ActionKind.COMMITTED), run.sink.actions.map { it.action.kind })
            assertEquals(1, run.outcome.commits.size)
            assertEquals(listOf(ENTRIES_TOOL), executor.calls.map { it.toolName })
        }
    }

    @Test
    fun aHoldingGateLeavesTheExecutorMutationUnapplied() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(ENTRIES_TOOL, StepResult("saved", false, "ok", emptyMap()))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(write))

            val run = runByHand(ScriptedGate.holdAll("confirm"), executor)

            assertEquals(1, run.gate.calls)
            assertEquals(0, write.applyCount)
            assertEquals(1, run.outcome.held.size)
            assertTrue(run.outcome.commits.isEmpty())
            val seen = run.dispatches.single()
            assertTrue(seen.held)
            assertEquals(false, seen.isError)
            assertEquals(HELD_BYTES, seen.contentForModel)
        }
    }

    @Test
    fun aReadStepIsNeverRecorded() = runTest {
        NoNetworkGuard.during {
            val read = ToolStep.Finished(READ_TOOL, FinishedKind.READ, StepResult(READ_CONTENT))
            val executor = ScriptedToolExecutor.sequence(null, read)

            val run = runByHand(ScriptedGate.admitAll(), executor)

            assertEquals(0, run.gate.calls)
            assertTrue(run.outcome.executed.isEmpty())
            assertTrue(run.sink.actions.isEmpty())
            val seen = run.dispatches.single()
            assertEquals(READ_CONTENT, seen.contentForModel)
            assertEquals(false, seen.isError)
            assertEquals(false, seen.held)
        }
    }

    private fun finished(tool: String): ToolStep.Finished =
        ToolStep.Finished(tool, FinishedKind.READ, StepResult(READ_CONTENT))

    @Test
    fun theScriptedExecutorPlaysRecordsAndLogsInOrder() = runTest {
        val log = RecordingSink<String>()
        val first = finished("first_tool")
        val second = finished("second_tool")
        val executor = ScriptedToolExecutor.sequence(log, first, second)
        val firstArguments = arguments()
        val secondArguments = buildJsonObject { put("other", "value") }
        val input = CommandInput("anything")

        val playedFirst = executor.prepare(Extraction("first_tool", firstArguments), input)
        val playedSecond = executor.prepare(Extraction("second_tool", secondArguments), input)

        assertSame(first, playedFirst)
        assertSame(second, playedSecond)
        assertEquals(listOf("first_tool", "second_tool"), executor.calls.map { it.toolName })
        assertSame(firstArguments, executor.calls[0].arguments)
        assertSame(secondArguments, executor.calls[1].arguments)
        assertEquals(2, executor.callCount)
        assertEquals(listOf("prepare:first_tool", "prepare:second_tool"), log.events)
    }

    @Test
    fun theScriptedExecutorFailsLoudlyWhenItsScriptRunsDry() = runTest {
        val executor = ScriptedToolExecutor.sequence(null, finished("only_tool"))
        val input = CommandInput("anything")
        executor.prepare(Extraction("only_tool", arguments()), input)

        val error = assertThrows(AssertionError::class.java) {
            runBlocking { executor.prepare(Extraction("only_tool", arguments()), input) }
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("exhausted after 1 calls"))
        assertEquals(2, executor.callCount)
    }

    @Test
    fun aScriptedExecutorWithoutALogStillRecords() = runTest {
        val executor = ScriptedToolExecutor(null) { _, _ -> finished("quiet_tool") }

        executor.prepare(Extraction("quiet_tool", arguments()), CommandInput("anything"))

        assertEquals(listOf("quiet_tool"), executor.calls.map { it.toolName })
        assertEquals(1, executor.callCount)
    }
}
