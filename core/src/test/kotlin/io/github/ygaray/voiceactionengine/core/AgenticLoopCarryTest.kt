package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CARRY_REPLY = "done"
private const val CARRY_MODEL = "test-model"
private const val CARRY_CEILING = 100L
private const val CARRY_BIG_TURN = 60L
private const val CARRY_TWO_ITERATIONS = 2
private const val MIN_SWEEP_PATHS = 12
private const val CARRY_REQUESTS = 3

/**
 * The Phase 8 carries for the loop: automatic tool choice on every request, the assistant turn replayed untouched to
 * the one model of the run, no conversation ever handed up to another tier, and a partial completion only for dropped
 * calls.
 */
class AgenticLoopCarryTest {

    // One scripted path: what the model says, what the app's executor answers, and how the run must end.
    private class Path(
        val name: String,
        val results: List<ModelResult>,
        val steps: List<ToolStep> = emptyList(),
        val gate: ScriptedGate = ScriptedGate.admitAll(),
        val policy: TierPolicy = TierPolicy.DEFAULT,
        val partial: Boolean = false,
    )

    private class Ran(
        val path: Path,
        val outcome: CommandOutcome,
        val fake: FakeAiProvider,
        val behind: ScriptedStrategy,
    )

    private fun save(id: String): AssistantPart.ToolCall = callOf(id, SAVE_TOOL, loopArguments())

    private fun ask(id: String): AssistantPart.ToolCall = callOf(id, ASK_TOOL, loopArguments())

    private fun turn(tokens: Long, vararg calls: AssistantPart.ToolCall): ModelResult = toolTurn(tokens, *calls)

    private fun prose(): ModelResult = FakeAiProvider.reply(CARRY_REPLY, usage(1))

    private fun words(): AssistantPart = AssistantPart.Text("words")

    private fun committed(): ToolStep = ToolStep.Mutation(FakeMutation(SAVE_TOOL, StepResult("saved")))

    private fun rejected(): ToolStep = ToolStep.Finished(SAVE_TOOL, FinishedKind.ERROR, StepResult("rejected", true))

    private fun snapshot() = loopSnapshotOf(writeTool(), readTool(), ToolSpec.clarification(ASK_TOOL))

    // The model-side ends: a prose answer, a tool run that finishes, and the budget, strike and malformed stops.
    private fun runPaths(): List<Path> =
        listOf(
            Path("prose", listOf(prose())),
            Path("done after tools", listOf(turn(1, save("a")), prose()), listOf(committed())),
            Path("held then prose", listOf(turn(1, save("a")), prose()), listOf(committed()), ScriptedGate.holdAll()),
            Path(
                "iteration budget",
                listOf(turn(1, save("a")), turn(1, save("b"))),
                listOf(committed()),
                policy = TierPolicy { maxIterations = CARRY_TWO_ITERATIONS },
            ),
            Path(
                "token budget",
                listOf(turn(CARRY_BIG_TURN, save("a")), turn(CARRY_BIG_TURN, save("b"))),
                listOf(committed()),
                policy = TierPolicy { tokenCeiling = CARRY_CEILING },
            ),
            Path("strike abort", listOf(turn(1, save("a")), turn(1, save("b"))), listOf(rejected(), rejected())),
            Path("malformed duplicate ids", listOf(turn(1, save("same"), save("same")))),
            Path("tool use without calls", listOf(answerOf(StopReason.TOOL_USE, words()))),
        )

    // The provider-side ends: every stop leaf, a transport failure after a commit, and the terminal exits.
    private fun endPaths(): List<Path> =
        listOf(
            Path("max tokens", listOf(answerOf(StopReason.MAX_TOKENS, words()))),
            Path("refusal", listOf(answerOf(StopReason.REFUSAL, words()))),
            Path("pause turn", listOf(answerOf(StopReason.PAUSE_TURN, words()))),
            Path("context window", listOf(answerOf(StopReason.CONTEXT_WINDOW_EXCEEDED, words()))),
            Path("unknown stop", listOf(answerOf(StopReason.OTHER, words()))),
            Path(
                "provider failure after a commit",
                listOf(turn(1, save("a")), ModelResult.Failure(FailureReason.Network())),
                listOf(committed()),
            ),
            Path("terminal", listOf(turn(1, ask("ask")))),
            Path("terminal after a commit", listOf(turn(1, save("a"), ask("ask"))), listOf(committed())),
            Path("terminal with dropped calls", listOf(turn(1, ask("ask"), save("a"))), partial = true),
        )

    private suspend fun runAll(): List<Ran> =
        (runPaths() + endPaths()).map { path ->
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, *path.results.toTypedArray())
            val behind = ScriptedStrategy(StrategyId("behind"))
            val executor = ScriptedToolExecutor.sequence(null, *path.steps.toTypedArray())
            val pipeline = loopPipeline(
                listOf(agenticLoop(executor, snapshot()), behind),
                fake,
                path.gate,
                RecordingCommitSink(),
                policy = path.policy,
            )
            Ran(path, pipeline.execute(CommandInput("add two things", "en", null)), fake, behind)
        }

    @Test
    fun everyRequestOnEveryPathUsesAutomaticChoice() = runTest {
        NoNetworkGuard.during {
            val ran = runAll()

            ran.forEach { run ->
                assertTrue(run.path.name, run.fake.calls.isNotEmpty())
                run.fake.calls.forEach { call ->
                    assertTrue(run.path.name, call.request.toolChoice is ToolChoice.Auto)
                    assertFalse(run.path.name, call.request.singleToolCall)
                }
            }
        }
    }

    @Test
    fun theAssistantTurnIsAppendedWithItsNativeReplayUntouched() = runTest {
        NoNetworkGuard.during {
            val raw = buildJsonObject { put("role", "assistant") }
            val replay = NativeReplay(ProviderId.ANTHROPIC, CARRY_MODEL, raw)
            val message = AssistantMessage(listOf(save("a")), replay)
            val first = ModelResult.Success(ModelResponse(message, StopReason.TOOL_USE, usage(1)))
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, first, prose())
            val executor = ScriptedToolExecutor.sequence(null, committed())

            loopPipeline(
                listOf(agenticLoop(executor, snapshot())),
                fake,
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
            ).execute(CommandInput("add two things", "en", null))

            val replayed = fake.calls[1].request.messages.filterIsInstance<AssistantMessage>().single()
            assertSame(message, replayed)
            assertSame(replay, replayed.nativeReplay)
            assertSame(raw, replayed.nativeFor(ProviderId.ANTHROPIC, CARRY_MODEL))
        }
    }

    @Test
    fun theRunTalksToOneProviderAndModel() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.OPENAI,
                turn(1, save("a")),
                turn(1, save("b")),
                prose(),
            )
            val executor = ScriptedToolExecutor.sequence(null, committed(), committed())

            loopPipeline(
                listOf(agenticLoop(executor, snapshot())),
                fake,
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
                providerId = ProviderId.OPENAI,
            ).execute(CommandInput("add two things", "en", null))

            assertEquals(CARRY_REQUESTS, fake.callCount)
            assertEquals(setOf(CARRY_MODEL), fake.calls.map { it.model }.toSet())
            assertEquals(setOf(ProviderId.OPENAI), fake.calls.map { checkNotNull(it.credential).provider }.toSet())
        }
    }

    @Test
    fun theLoopNeverHandsUpAConversation() = runTest {
        NoNetworkGuard.during {
            val ran = runAll()

            assertTrue("the sweep names at least $MIN_SWEEP_PATHS paths", ran.size >= MIN_SWEEP_PATHS)
            ran.forEach { run ->
                val outcome = run.outcome
                val kind = outcome.toString()
                val handedBack = outcome is CommandOutcome.Completed || outcome is CommandOutcome.Failed
                assertTrue(run.path.name + ": " + kind, handedBack)
                assertEquals(run.path.name, 0, run.behind.executions)
                assertFalse(run.path.name, outcome.trace.codes.contains(TraceCode.ESCALATION_SUPPRESSED))
            }
        }
    }

    @Test
    fun partialIsSetOnlyWhenCallsAfterATerminalCallWereDropped() = runTest {
        NoNetworkGuard.during {
            val ran = runAll()

            ran.forEach { run ->
                val outcome = run.outcome
                if (outcome is CommandOutcome.Completed) assertEquals(run.path.name, run.path.partial, outcome.partial)
            }
            val dropped = ran.single { it.path.partial }.outcome
            assertTrue(dropped.toString(), dropped is CommandOutcome.Completed && dropped.partial)
            assertTrue(dropped.trace.codes.contains(TraceCode.EXTRA_TOOL_CALLS_DROPPED))
            val completedPartials = ran.filter { (it.outcome as? CommandOutcome.Completed)?.partial == true }
            assertEquals(1, completedPartials.size)
            listOf("iteration budget", "token budget", "provider failure after a commit").forEach { name ->
                val stopped = ran.single { it.path.name == name }.outcome
                assertTrue(name + ": " + stopped, stopped is CommandOutcome.Failed)
                assertEquals(name, 1, stopped.commits.size)
            }
        }
    }
}
