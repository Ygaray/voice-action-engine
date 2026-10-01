package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOOL = "create_item"
private const val TEST_KEY = "sk-canary-key-do-not-print"
private const val FINAL_REPLY = "done"

/** The tracer slice: the composition root runs one agentic command end to end over the public pipeline. */
class SampleEngineTracerTest {

    private val selection = ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5")
    private val policy = TierPolicy { maxIterations = 3 }

    private fun usage(): Usage = Usage(0, 0, 0, 1)

    private fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("title") { put("type", "string") } }
    }

    private fun args(): JsonObject = buildJsonObject { put("title", "a") }

    private fun tier(executor: ScriptedToolExecutor): AgenticLoopStrategy =
        AgenticLoopStrategy(StrategyId("agentic")) {
            tooling = ToolSpecProvider.fixed(
                ToolingSnapshot("sample system", listOf(ToolSpec(TOOL, "Creates an item.", schema(), true)), null),
            )
            this.executor = executor
        }

    private class Rig(
        val fake: FakeAiProvider,
        val sink: RecordingCommitSink,
        val listener: RecordingEventListener,
        val engine: SampleEngine,
    )

    private fun rig(fake: FakeAiProvider): Rig {
        val sink = RecordingCommitSink()
        val listener = RecordingEventListener()
        val engine = SampleEngine(
            listOf(fake),
            ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to TEST_KEY),
            sink,
            listener,
        )
        return Rig(fake, sink, listener, engine)
    }

    private fun pipeline(rig: Rig, executor: ScriptedToolExecutor): CommandPipeline =
        rig.engine.pipeline(tier(executor), selection, policy)

    @Test
    fun oneAgenticCommandRunsEndToEndThroughTheCompositionRoot() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.toolCall("call_1", TOOL, args(), usage()),
                FakeAiProvider.reply(FINAL_REPLY, usage()),
            )
            val rig = rig(fake)
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(FakeMutation(TOOL, StepResult("ok"))))

            val outcome = pipeline(rig, executor).execute(CommandInput("tracer command"))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            val completed = outcome as CommandOutcome.Completed
            assertEquals(FINAL_REPLY, completed.reply)
            assertFalse(completed.partial)
            assertEquals(listOf(ActionKind.COMMITTED), rig.sink.actions.map { it.action.kind })
            assertEquals(1, rig.sink.closes.size)
            assertEquals(2, fake.callCount)
            assertEquals(2, rig.listener.events.count { it is PipelineEvent.ProviderCall })
        }
    }

    @Test
    fun theGateAlwaysAdmits() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.toolCall("call_1", TOOL, args(), usage()),
                FakeAiProvider.reply(FINAL_REPLY, usage()),
            )
            val rig = rig(fake)
            val first = FakeMutation(TOOL, StepResult("one"))
            val second = FakeMutation(TOOL, StepResult("two"))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(listOf(first, second)))

            val outcome = pipeline(rig, executor).execute(CommandInput("two things"))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, first.applyCount)
            assertEquals(1, second.applyCount)
            assertEquals(
                listOf(ActionKind.COMMITTED, ActionKind.COMMITTED),
                rig.sink.actions.map { it.action.kind },
            )
        }
    }

    @Test
    fun toStringCarriesNoSecrets() {
        val engine = rig(FakeAiProvider(ProviderId.ANTHROPIC)).engine
        val text = engine.toString()
        assertTrue(text, text.contains("anthropic"))
        assertFalse(text, text.contains(TEST_KEY))
        assertFalse(text, text.contains("ScriptedCredentialSource"))
    }
}
