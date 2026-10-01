package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val USAGE_TOKENS = 1L

/** What one single-shot request looks like on the wire, and what builds it. */
class SingleShotRequestTest {

    private class Run(
        val outcome: CommandOutcome,
        val fake: FakeAiProvider,
        val sink: RecordingCommitSink,
        val resolver: RecordingResolver,
    ) {
        val sent: ModelRequest get() = fake.calls.single().request
    }

    private fun answeringWithTheTool(): FakeAiProvider =
        FakeAiProvider(
            ProviderId.ANTHROPIC,
            FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments("a"), Usage(USAGE_TOKENS, 0, 0, 1)),
        )

    private fun savingResolver(): RecordingResolver =
        RecordingResolver { _, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(ENTRIES_TOOL, StepResult("saved")))))
        }

    private suspend fun runShot(
        tools: Array<ToolSpec> = arrayOf(entriesTool(), askTool()),
        forced: String? = ENTRIES_TOOL,
        configure: SingleShotStrategy.Builder.() -> Unit = {},
    ): Run {
        val resolver = savingResolver()
        val snapshot = snapshotOf(*tools, forced = forced)
        val fake = answeringWithTheTool()
        val sink = RecordingCommitSink()
        val tier = singleShot(resolver, snapshot, configure = configure)
        val pipeline = pipelineOf(listOf(tier), fake, ScriptedGate.admitAll(), sink)
        return Run(pipeline.execute(CommandInput("add two things", "en", null)), fake, sink, resolver)
    }

    @Test
    fun oneTranscriptBecomesOneForcedSingleCallRequest() = runTest {
        NoNetworkGuard.during {
            val tools = arrayOf(entriesTool(), askTool())

            val run = runShot(tools = tools)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(1, run.fake.callCount)
            val sent = run.sent
            assertEquals(SINGLE_SHOT_SYSTEM, sent.system)
            assertEquals(2, sent.tools.size)
            assertEquals(listOf(ENTRIES_TOOL, ASK_TOOL), sent.tools.map { it.name })
            assertEquals(ToolChoice.Required(ENTRIES_TOOL), sent.toolChoice)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, sent.maxTokens)
            assertEquals(CacheDirective(true), sent.cache)
            assertTrue(sent.singleToolCall)
            assertEquals(1, sent.messages.size)
            assertEquals(
                "Current local date-time: 2026-10-01T16:30:12Z (UTC)\n\nadd two things",
                (sent.messages.single() as UserMessage).text,
            )
        }
    }

    @Test
    fun theRequestCarriesTheSnapshotsToolInstancesUnchanged() = runTest {
        NoNetworkGuard.during {
            val entries = entriesTool()
            val ask = askTool()

            val run = runShot(tools = arrayOf(entries, ask))

            assertSame(entries, run.sent.tools[0])
            assertSame(ask, run.sent.tools[1])
        }
    }

    @Test
    fun forceToolFalseSendsAutoChoiceAndStillAsksForOneCall() = runTest {
        NoNetworkGuard.during {
            val run = runShot { forceTool = false }

            assertEquals(ToolChoice.Auto(), run.sent.toolChoice)
            assertTrue(run.sent.singleToolCall)
        }
    }

    @Test
    fun forceToolFalseWorksWhenTheSnapshotNamesNoSingleShotTool() = runTest {
        NoNetworkGuard.during {
            val run = runShot(forced = null) { forceTool = false }

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(1, run.fake.callCount)
            assertEquals(1, run.resolver.invocations)
            assertEquals(ToolChoice.Auto(), run.sent.toolChoice)
            assertTrue(run.sent.singleToolCall)
        }
    }

    @Test
    fun aSnapshotWithoutAForcedToolFailsWithNoProviderCall() = runTest {
        NoNetworkGuard.during {
            val resolver = savingResolver()
            val fake = answeringWithTheTool()
            val pipeline = pipelineOf(
                listOf(singleShot(resolver, snapshotOf(entriesTool(), forced = null))),
                fake,
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
            )

            val outcome = pipeline.execute(CommandInput("add two things", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(FailureReason.Other("single_shot_tool_missing"), (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, fake.callCount)
            assertEquals(0, resolver.invocations)
        }
    }

    @Test
    fun aMissingCredentialFailsWithoutAProviderCallAndNeverRunsTheNextTier() = runTest {
        NoNetworkGuard.during {
            val resolver = savingResolver()
            val fake = answeringWithTheTool()
            val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("next") })
            val pipeline = pipelineOf(
                listOf(singleShot(resolver, snapshotOf(entriesTool())), next),
                fake,
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
                credentials = ScriptedCredentialSource.keys(),
            )

            val outcome = pipeline.execute(CommandInput("add two things", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertTrue(
                (outcome as CommandOutcome.Failed).reason.toString(),
                outcome.reason is FailureReason.NotConfigured,
            )
            assertEquals(0, fake.callCount)
            assertEquals(0, resolver.invocations)
            assertEquals(0, next.executions)
        }
    }

    @Test
    fun buildingWithoutToolingNamesTheMissingField() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            SingleShotStrategy(StrategyId("single_shot")) { resolver = savingResolver() }
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("tooling"))
    }

    @Test
    fun buildingWithoutAResolverNamesTheMissingField() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            SingleShotStrategy(StrategyId("single_shot")) {
                tooling = ToolSpecProvider.fixed(snapshotOf(entriesTool()))
            }
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("resolver"))
    }

    @Test
    fun toStringPrintsTheIdAndForceToolOnly() {
        val strategy = singleShot(savingResolver(), snapshotOf(entriesTool(), forced = ENTRIES_TOOL))

        assertEquals("SingleShotStrategy(id=single_shot, forceTool=true)", strategy.toString())
    }
}
