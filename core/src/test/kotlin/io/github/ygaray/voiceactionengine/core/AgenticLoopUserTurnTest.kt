package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val TRANSCRIPT = "add two things"
private const val SB_EXPECTED =
    "Current local date-time: 2026-10-01T16:30:12 (UTC)\n\nVoice command: add two things"

/** The loop takes the same user-turn seam as the single-shot tier, and its cached prefix never changes. */
class AgenticLoopUserTurnTest {

    private fun threeTurns(): FakeAiProvider =
        FakeAiProvider(
            ProviderId.ANTHROPIC,
            toolTurn(1, callOf("a", SAVE_TOOL, loopArguments())),
            toolTurn(1, callOf("b", SAVE_TOOL, loopArguments())),
            FakeAiProvider.reply("done", usage(1)),
        )

    private fun twoTurns(): FakeAiProvider =
        FakeAiProvider(
            ProviderId.ANTHROPIC,
            toolTurn(1, callOf("a", SAVE_TOOL, loopArguments())),
            FakeAiProvider.reply("done", usage(1)),
        )

    private fun savingExecutor(times: Int): ScriptedToolExecutor =
        ScriptedToolExecutor.sequence(
            null,
            *Array(times) { ToolStep.Mutation(FakeMutation(SAVE_TOOL, StepResult("saved"))) },
        )

    private fun snapshot(): ToolingSnapshot = loopSnapshotOf(writeTool(), readTool())

    private fun pipeline(tiers: List<CommandStrategy>, fake: FakeAiProvider): CommandPipeline =
        loopPipeline(tiers, fake, ScriptedGate.admitAll(), RecordingCommitSink())

    private fun loop(times: Int, configure: AgenticLoopStrategy.Builder.() -> Unit = {}): AgenticLoopStrategy =
        agenticLoop(savingExecutor(times), snapshot(), configure = configure)

    private fun firstText(fake: FakeAiProvider, index: Int): String =
        (fake.calls[index].request.messages.first() as UserMessage).text

    @Test
    fun theUserMessageIsExactlyTheRenderersOutput() = runTest {
        NoNetworkGuard.during {
            val fake = twoTurns()
            val renderer = UserTurnRenderer { "FRAMED[" + it.input.transcript + "]" }

            pipeline(listOf(loop(1) { userTurn = renderer }), fake).execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals("FRAMED[add two things]", firstText(fake, 0))
        }
    }

    @Test
    fun anSbShapedRendererReachesTheRequestByteForByte() = runTest {
        NoNetworkGuard.during {
            val fake = twoTurns()
            val sbShaped = UserTurnRenderer { ctx ->
                "Current local date-time: ${ctx.dateTime.toLocalDateTime()} (${ctx.dateTime.zone.id})\n\n" +
                    "Voice command: ${ctx.input.transcript}"
            }

            pipeline(listOf(loop(1) { userTurn = sbShaped }), fake).execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals(2, fake.callCount)
            assertEquals(SB_EXPECTED, firstText(fake, 0))
            assertEquals(SB_EXPECTED, firstText(fake, 1))
        }
    }

    @Test
    fun theStandardRendererIsTheDefault() = runTest {
        NoNetworkGuard.during {
            val fake = twoTurns()

            pipeline(listOf(loop(1)), fake).execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals(
                "Current local date-time: 2026-10-01T16:30:12Z (UTC)\n\nadd two things",
                firstText(fake, 0),
            )
        }
    }

    @Test
    fun theRendererSeesTheEscalatingTiersCarryAndTheInput() = runTest {
        NoNetworkGuard.during {
            val marker = Any()
            val seen = CopyOnWriteArrayList<UserTurnContext>()
            val renderer = UserTurnRenderer { context ->
                seen.add(context)
                "rendered"
            }
            val earlier = ScriptedStrategy(
                StrategyId("earlier"),
                { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), marker) },
            )
            val input = CommandInput(TRANSCRIPT, "en", null)

            pipeline(listOf(earlier, loop(1) { userTurn = renderer }), twoTurns()).execute(input)

            val context = seen.single()
            assertSame(marker, context.carry)
            assertSame(input, context.input)
            assertEquals(ZonedDateTime.now(fixedClock), context.dateTime)
        }
    }

    @Test
    fun theUserTurnIsRenderedOncePerCommand() = runTest {
        NoNetworkGuard.during {
            val renders = AtomicInteger()
            val renderer = UserTurnRenderer {
                renders.incrementAndGet()
                "rendered"
            }

            pipeline(listOf(loop(2) { userTurn = renderer }), threeTurns())
                .execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals(1, renders.get())
        }
    }

    @Test
    fun theToolingIsReadOncePerCommand() = runTest {
        NoNetworkGuard.during {
            val reads = AtomicInteger()
            val counting = ToolSpecProvider { input ->
                reads.incrementAndGet()
                ToolSpecProvider.fixed(snapshot()).tooling(input)
            }

            pipeline(listOf(loop(2) { tooling = counting }), threeTurns())
                .execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals(1, reads.get())
        }
    }

    @Test
    fun everyIterationSendsTheSameSystemAndTools() = runTest {
        NoNetworkGuard.during {
            val fake = threeTurns()

            pipeline(listOf(loop(2)), fake).execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals(3, fake.callCount)
            val first = fake.calls.first().request
            fake.calls.forEach { call ->
                val request = call.request
                assertEquals(LOOP_SYSTEM, request.system)
                assertEquals(first.tools.size, request.tools.size)
                first.tools.indices.forEach { index -> assertSame(first.tools[index], request.tools[index]) }
                assertTrue(request.cache.staticPrefix)
            }
        }
    }

    @Test
    fun everyRequestAsksForAutomaticChoiceWithParallelCallsAllowed() = runTest {
        NoNetworkGuard.during {
            val fake = threeTurns()

            pipeline(listOf(loop(2)), fake).execute(CommandInput(TRANSCRIPT, "en", null))

            assertEquals(3, fake.callCount)
            fake.calls.forEach { call ->
                assertTrue(call.request.toolChoice is ToolChoice.Auto)
                assertEquals(false, call.request.singleToolCall)
                assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, call.request.maxTokens)
            }
        }
    }

    @Test
    fun aSnapshotNamingASingleShotToolIsStillAutomatic() = runTest {
        NoNetworkGuard.during {
            val fake = twoTurns()
            val named = ToolingSnapshot(LOOP_SYSTEM, listOf(writeTool(), readTool()), SAVE_TOOL)
            val strategy = agenticLoop(savingExecutor(1), named)

            pipeline(listOf(strategy), fake).execute(CommandInput(TRANSCRIPT, "en", null))

            fake.calls.forEach { call ->
                assertTrue(call.request.toolChoice is ToolChoice.Auto)
                assertEquals(false, call.request.singleToolCall)
            }
        }
    }
}
