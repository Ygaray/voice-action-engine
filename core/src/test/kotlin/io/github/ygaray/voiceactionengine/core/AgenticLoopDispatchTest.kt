package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CALL_ONE = "call_1"
private const val COORDINATOR_CONTENT = """{"saved":1}"""
private const val FINAL_REPLY = "done"
private const val MANY_TOOLS = 25

/** The loop's dispatch path: a tool turn runs through the app's executor, the gate and the sink, and the run ends. */
class AgenticLoopDispatchTest {

    private fun mutation(name: String, log: RecordingSink<String>? = null): ToolStep.Mutation =
        ToolStep.Mutation(FakeMutation(name, StepResult("saved:$name"), log = log))

    private fun readStep(): ToolStep = ToolStep.Finished(FIND_TOOL, FinishedKind.READ, StepResult("[]"))

    private fun resultsAt(fake: FakeAiProvider, request: Int): ToolResultsMessage =
        fake.calls[request].request.messages.last() as ToolResultsMessage

    private suspend fun run(
        fake: FakeAiProvider,
        strategy: AgenticLoopStrategy,
        gate: ScriptedGate = ScriptedGate.admitAll(),
        sink: RecordingCommitSink = RecordingCommitSink(),
    ): CommandOutcome =
        loopPipeline(listOf(strategy), fake, gate, sink).execute(CommandInput("add two things", "en", null))

    @Test
    fun aToolTurnThenProseCommitsThroughTheGateAndCompletes() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val write = FakeMutation(SAVE_TOOL, StepResult(COORDINATOR_CONTENT), log = log)
            val executor = ScriptedToolExecutor.sequence(log, ToolStep.Mutation(write))
            val gate = ScriptedGate.admitAll(log)
            val sink = RecordingCommitSink(log)
            val firstTurn = toolTurn(1, callOf(CALL_ONE, SAVE_TOOL, loopArguments()))
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                firstTurn,
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val strategy = agenticLoop(executor, loopSnapshotOf(writeTool(), readTool()))

            val outcome = loopPipeline(listOf(strategy), fake, gate, sink)
                .execute(CommandInput("add two things", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            outcome as CommandOutcome.Completed
            assertEquals(FINAL_REPLY, outcome.reply)
            assertFalse(outcome.partial)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(ActionKind.COMMITTED), sink.actions.map { it.action.kind })
            val committedAt = log.events.indexOfFirst { it.startsWith("sink:action:") }
            val closedAt = log.events.indexOfFirst { it.startsWith("sink:closed:") }
            assertTrue(log.events.toString(), committedAt in 0 until closedAt)
            assertEquals(2, fake.callCount)

            val first = fake.calls[0].request.messages
            val second = fake.calls[1].request.messages
            assertEquals(3, second.size)
            assertSame(first[0], second[0])
            assertTrue(second[0] is UserMessage)
            assertSame((firstTurn as ModelResult.Success).response.message, second[1])
            val results = (second[2] as ToolResultsMessage).results.single()
            assertEquals(CALL_ONE, results.callId)
            assertEquals(COORDINATOR_CONTENT, results.content)
            assertFalse(results.isError)
        }
    }

    @Test
    fun aMultiToolTurnSendsOneResultsMessageInCallOrder() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(
                    1,
                    callOf("a", SAVE_TOOL, loopArguments()),
                    callOf("b", FIND_TOOL, loopArguments()),
                ),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val executor = ScriptedToolExecutor.sequence(null, mutation(SAVE_TOOL), readStep())

            run(fake, agenticLoop(executor, loopSnapshotOf(writeTool(), readTool())))

            val results = resultsAt(fake, 1)
            assertEquals(listOf("a", "b"), results.results.map { it.callId })
            assertEquals(1, fake.calls[1].request.messages.count { it is ToolResultsMessage })
        }
    }

    @Test
    fun eachCallStampsItsOwnIdOnItsActionsAndTheExecutorSeesTheId() = runTest {
        NoNetworkGuard.during {
            val second = "update_entry"
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(
                    1,
                    callOf("a1", SAVE_TOOL, loopArguments()),
                    callOf("r1", FIND_TOOL, loopArguments()),
                    callOf("u1", "never_offered", loopArguments()),
                    callOf("a2", second, loopArguments()),
                ),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val executor = ScriptedToolExecutor.sequence(null, mutation(SAVE_TOOL), readStep(), mutation(second))
            val snapshot = loopSnapshotOf(writeTool(), readTool(), writeTool(second))

            val outcome = run(fake, agenticLoop(executor, snapshot))

            assertEquals(listOf("a1", "a2"), outcome.executed.map { it.providerCallId })
            assertEquals(listOf(SAVE_TOOL, second), outcome.executed.map { it.toolName })
            assertEquals(listOf("a1", "r1", "a2"), executor.calls.map { it.callId })
        }
    }

    @Test
    fun callsAreDispatchedOneAtATimeInCallOrder() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val second = "update_entry"
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(
                    1,
                    callOf("a", SAVE_TOOL, loopArguments()),
                    callOf("b", second, loopArguments()),
                ),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val executor = ScriptedToolExecutor.sequence(log, mutation(SAVE_TOOL, log), mutation(second, log))
            val snapshot = loopSnapshotOf(writeTool(), writeTool(second))

            run(fake, agenticLoop(executor, snapshot), ScriptedGate.admitAll(log), RecordingCommitSink(log))

            val events = log.events
            assertEquals("prepare:$SAVE_TOOL", events[0])
            assertEquals("gate", events[1])
            assertEquals("apply:$SAVE_TOOL", events[2])
            assertTrue(events.toString(), events[3].startsWith("sink:action:0:"))
            assertEquals("prepare:$second", events[4])
            assertEquals("gate", events[5])
            assertEquals("apply:$second", events[6])
            assertTrue(events.toString(), events[7].startsWith("sink:action:1:"))
        }
    }

    @Test
    fun theHistoryIsAppendOnlyAcrossTurns() = runTest {
        NoNetworkGuard.during {
            val turnOne = toolTurn(1, callOf("a", SAVE_TOOL, loopArguments()))
            val turnTwo = toolTurn(1, callOf("b", SAVE_TOOL, loopArguments()))
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turnOne,
                turnTwo,
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val executor = ScriptedToolExecutor.sequence(null, mutation(SAVE_TOOL), mutation(SAVE_TOOL))

            run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())))

            val responses = listOf(turnOne, turnTwo).map { (it as ModelResult.Success).response.message }
            for (index in 1..2) {
                val previous = fake.calls[index - 1].request.messages
                val current = fake.calls[index].request.messages
                assertEquals(previous.size + 2, current.size)
                previous.indices.forEach { assertSame(previous[it], current[it]) }
                assertSame(responses[index - 1], current[previous.size])
                assertTrue(current.last() is ToolResultsMessage)
            }
        }
    }

    @Test
    fun theReplyIsTheFirstTextBlockOfTheFinalTurn() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf("a", SAVE_TOOL, loopArguments())),
                toolTurn(1, callOf("b", SAVE_TOOL, loopArguments())),
                answerOf(StopReason.END_TURN, AssistantPart.Text("first"), AssistantPart.Text("second")),
            )
            val executor = ScriptedToolExecutor.sequence(null, mutation(SAVE_TOOL), mutation(SAVE_TOOL))

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals("first", (outcome as CommandOutcome.Completed).reply)
        }
    }

    @Test
    fun aProseOnlyRunCompletesWithItsReply() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("just words", usage(1)))
            val executor = ScriptedToolExecutor.sequence(null)
            val gate = ScriptedGate.admitAll()

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), gate)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals("just words", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, fake.callCount)
            assertEquals(0, executor.callCount)
            assertEquals(0, gate.calls)
        }
    }

    @Test
    fun readsAreAbsentFromTheExecutedListButNamedInTheTurnTrace() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(
                    1,
                    callOf("a", FIND_TOOL, loopArguments()),
                    callOf("b", SAVE_TOOL, loopArguments()),
                ),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val executor = ScriptedToolExecutor.sequence(null, readStep(), mutation(SAVE_TOOL))
            val sink = RecordingCommitSink()

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool(), readTool())), sink = sink)

            assertEquals(1, outcome.executed.size)
            assertEquals(1, outcome.commits.size)
            assertEquals(1, sink.actions.size)
            val turn = outcome.trace.attempts.single().turns[0]
            assertEquals(listOf(FIND_TOOL, SAVE_TOOL), turn.toolNames)
        }
    }

    @Test
    fun anUnknownToolIsAnsweredWithAnErrorAndTheExecutorIsNotCalled() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf("u1", "ghost_tool", loopArguments())),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val executor = ScriptedToolExecutor.sequence(null)

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())))

            val result = resultsAt(fake, 1).results.single()
            assertEquals("u1", result.callId)
            assertTrue(result.isError)
            assertEquals("""{"status":"error","reason":"unknown_tool"}""", result.content)
            assertEquals(0, executor.callCount)
            assertTrue(outcome.trace.codes.contains(TraceCode.UNKNOWN_TOOL))
        }
    }

    @Test
    fun theLoopRunsWithOneTwoOrTwentyFiveTools() = runTest {
        NoNetworkGuard.during {
            listOf(1, 2, MANY_TOOLS).forEach { count ->
                val tools = List(count) { writeTool("tool_$it") }
                val last = tools.last().name
                val fake = FakeAiProvider(
                    ProviderId.ANTHROPIC,
                    toolTurn(1, callOf("a", last, loopArguments())),
                    FakeAiProvider.reply(FINAL_REPLY, usage(1)),
                )
                val executor = ScriptedToolExecutor.sequence(null, mutation(last))
                val strategy = agenticLoop(executor, loopSnapshotOf(*tools.toTypedArray()))

                val outcome = run(fake, strategy)

                assertTrue("$count: $outcome", outcome is CommandOutcome.Completed)
                assertEquals(count.toString(), 1, outcome.commits.size)
                fake.calls.forEach { call ->
                    assertEquals(tools.map { it.name }, call.request.tools.map { it.name })
                }
            }
        }
    }

    @Test
    fun theBuilderRequiresToolingAndExecutor() {
        val executor = ScriptedToolExecutor.sequence(null)
        val missingTooling = assertThrows(IllegalArgumentException::class.java) {
            AgenticLoopStrategy(StrategyId("x")) { this.executor = executor }
        }
        assertTrue(missingTooling.message, missingTooling.message.orEmpty().contains("tooling"))
        val missingExecutor = assertThrows(IllegalArgumentException::class.java) {
            AgenticLoopStrategy(StrategyId("x")) {
                tooling = ToolSpecProvider.fixed(loopSnapshotOf(writeTool()))
            }
        }
        assertTrue(missingExecutor.message, missingExecutor.message.orEmpty().contains("executor"))
    }

    @Test
    fun toStringPrintsTheIdOnly() {
        val strategy = agenticLoop(ScriptedToolExecutor.sequence(null), loopSnapshotOf(writeTool()))

        assertEquals("AgenticLoopStrategy(id=agentic)", strategy.toString())
    }
}
