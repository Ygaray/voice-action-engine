package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

private const val ADD_TEXT = "add two things"
private const val DELETE_TEXT = "borra eso"
private const val THIRD_TEXT = "what is on my list"
private const val STEP_HOURS = 5L

/** A clock whose instant a test moves between commands, so one strategy renders two different times. */
private class SteppingClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = SteppingClock(now, zone)

    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now = now.plus(by)
    }
}

/** Per-command text reaches only the user message; the cached tools and system prefix is the same every time. */
class SingleShotUserTurnTest {

    private fun answer(): ModelResult =
        FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments("a"), Usage(0, 0, 0, 1))

    private fun savingResolver(): RecordingResolver =
        RecordingResolver { _, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(ENTRIES_TOOL, StepResult("saved")))))
        }

    private fun fakeAnswering(times: Int): FakeAiProvider =
        FakeAiProvider(ProviderId.ANTHROPIC, *Array(times) { answer() })

    private fun pipeline(tiers: List<CommandStrategy>, fake: FakeAiProvider): CommandPipeline =
        pipelineOf(tiers, fake, ScriptedGate.admitAll(), RecordingCommitSink())

    private fun shot(configure: SingleShotStrategy.Builder.() -> Unit = {}): SingleShotStrategy =
        singleShot(savingResolver(), snapshotOf(entriesTool(), askTool()), configure = configure)

    private fun userText(fake: FakeAiProvider, index: Int): String =
        (fake.calls[index].request.messages.single() as UserMessage).text

    @Test
    fun twoCommandsShareTheSameSystemAndToolsBytes() = runTest {
        NoNetworkGuard.during {
            val clock = SteppingClock(Instant.parse("2026-10-01T16:30:12Z"), ZoneId.of("UTC"))
            val fake = fakeAnswering(2)
            val pipeline = pipeline(listOf(shot { this.clock = clock }), fake)

            pipeline.execute(CommandInput(ADD_TEXT, "en", null))
            clock.advance(Duration.ofHours(STEP_HOURS))
            pipeline.execute(CommandInput(DELETE_TEXT, "es", null))

            val first = fake.calls[0].request
            val second = fake.calls[1].request
            assertEquals(first.system, second.system)
            assertEquals(first.tools.size, second.tools.size)
            first.tools.indices.forEach { index ->
                assertSame(first.tools[index], second.tools[index])
                assertEquals(first.tools[index].name, second.tools[index].name)
                assertEquals(first.tools[index].inputSchema, second.tools[index].inputSchema)
            }
            assertNotEquals(userText(fake, 0), userText(fake, 1))
            listOf(ADD_TEXT, DELETE_TEXT, "2026-").forEach { fragment ->
                assertFalse(fragment, first.system.contains(fragment))
                assertFalse(fragment, second.system.contains(fragment))
            }
        }
    }

    @Test
    fun theUserMessageIsExactlyTheRenderersOutput() = runTest {
        NoNetworkGuard.during {
            val fake = fakeAnswering(1)
            val renderer = UserTurnRenderer { "FRAMED[" + it.input.transcript + "]" }

            pipeline(listOf(shot { userTurn = renderer }), fake).execute(CommandInput(ADD_TEXT, "en", null))

            val message = fake.calls.single().request.messages.single() as UserMessage
            assertEquals("FRAMED[add two things]", message.text)
        }
    }

    @Test
    fun theDefaultRendererFramesDateZoneAndTranscript() = runTest {
        NoNetworkGuard.during {
            val fake = fakeAnswering(1)
            val clock = Clock.fixed(Instant.parse("2026-10-01T16:30:12.345Z"), ZoneId.of("America/Los_Angeles"))

            pipeline(listOf(shot { this.clock = clock }), fake).execute(CommandInput(ADD_TEXT, "en", null))

            assertEquals(
                "Current local date-time: 2026-10-01T09:30:12-07:00 (America/Los_Angeles)\n\nadd two things",
                userText(fake, 0),
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
            val input = CommandInput(ADD_TEXT, "en", null)

            pipeline(listOf(earlier, shot { userTurn = renderer }), fakeAnswering(1)).execute(input)

            val context = seen.single()
            assertSame(marker, context.carry)
            assertSame(input, context.input)
            assertEquals(ZonedDateTime.now(fixedClock), context.dateTime)
        }
    }

    @Test
    fun perCommandTextNeverReachesTheSystemPrompt() = runTest {
        NoNetworkGuard.during {
            val fake = fakeAnswering(3)
            val pipeline = pipeline(listOf(shot()), fake)

            pipeline.execute(CommandInput(ADD_TEXT, "en", null))
            pipeline.execute(CommandInput(DELETE_TEXT, "es", null))
            pipeline.execute(CommandInput(THIRD_TEXT, null, null))

            assertEquals(3, fake.callCount)
            fake.calls.forEach { assertEquals(SINGLE_SHOT_SYSTEM, it.request.system) }
        }
    }

    @Test
    fun theDefaultClockReadsTheDeviceZoneAgainForEveryCommand() = runTest {
        NoNetworkGuard.during {
            val original = TimeZone.getDefault()
            try {
                val fake = fakeAnswering(2)
                val strategy = SingleShotStrategy(StrategyId("single_shot")) {
                    tooling = ToolSpecProvider.fixed(snapshotOf(entriesTool(), askTool()))
                    resolver = savingResolver()
                }
                val pipeline = pipeline(listOf(strategy), fake)

                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
                pipeline.execute(CommandInput(ADD_TEXT, "en", null))
                TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
                pipeline.execute(CommandInput(DELETE_TEXT, "es", null))

                assertTrue(userText(fake, 0), userText(fake, 0).contains("(Asia/Tokyo)"))
                assertTrue(userText(fake, 1), userText(fake, 1).contains("(America/New_York)"))
            } finally {
                TimeZone.setDefault(original)
            }
        }
    }
}
