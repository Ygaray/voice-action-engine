package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val NEUTRAL_LOG_TOOL = "log_entry"
private const val NEUTRAL_REPLY = "done"
private const val NEUTRAL_REQUESTS = 3
private const val AGENTIC_SOURCES = "src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic"
private const val CORE_MODULE = "core"
private const val MIN_AGENTIC_SOURCES = 4

/** The loop behaves the same on every cloud provider id and its sources cannot branch on a provider. */
class AgenticLoopProviderNeutralityTest {

    // Everything about a run that must not depend on which provider id served it.
    private data class Digest(
        val outcome: String,
        val reply: String?,
        val kinds: List<ActionKind>,
        val tools: List<String>,
        val held: Int,
        val providerCalls: Int,
        val messageKinds: List<List<String>>,
    )

    private suspend fun digestOn(providerId: ProviderId): Digest {
        val fake = FakeAiProvider(
            providerId,
            toolTurn(1, callOf("c1", SAVE_TOOL, loopArguments()), callOf("c2", FIND_TOOL, loopArguments())),
            toolTurn(1, callOf("c3", NEUTRAL_LOG_TOOL, loopArguments())),
            FakeAiProvider.reply(NEUTRAL_REPLY, usage(1)),
        )
        val read = ToolStep.Finished(FIND_TOOL, FinishedKind.READ, StepResult("found"))
        val executor = ScriptedToolExecutor.sequence(
            null,
            ToolStep.Mutation(FakeMutation(SAVE_TOOL, StepResult("saved"))),
            read,
            ToolStep.Mutation(FakeMutation(NEUTRAL_LOG_TOOL, StepResult("saved"))),
        )
        val snapshot = loopSnapshotOf(writeTool(), readTool(), writeTool(NEUTRAL_LOG_TOOL))
        val gate = ScriptedGate.sequence(null, GateDecision.Admit(), GateDecision.Hold())

        val outcome = loopPipeline(
            listOf(agenticLoop(executor, snapshot)),
            fake,
            gate,
            RecordingCommitSink(),
            providerId = providerId,
        ).execute(CommandInput("add two things", "en", null))

        return Digest(
            outcome = outcome::class.simpleName.orEmpty(),
            reply = (outcome as? CommandOutcome.Completed)?.reply,
            kinds = outcome.executed.map { it.kind },
            tools = outcome.executed.map { it.toolName },
            held = outcome.held.size,
            providerCalls = fake.callCount,
            messageKinds = fake.calls.map { call -> call.request.messages.map { it::class.simpleName.orEmpty() } },
        )
    }

    @Test
    fun theSameScriptGivesTheSameOutcomeOnAnthropicOpenAiAndOpenRouter() = runTest {
        NoNetworkGuard.during {
            val anthropic = digestOn(ProviderId.ANTHROPIC)
            val openAi = digestOn(ProviderId.OPENAI)
            val openRouter = digestOn(ProviderId.OPENROUTER)

            assertEquals("Completed", anthropic.outcome)
            assertEquals(NEUTRAL_REPLY, anthropic.reply)
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.HELD), anthropic.kinds)
            assertEquals(listOf(SAVE_TOOL, NEUTRAL_LOG_TOOL), anthropic.tools)
            assertEquals(1, anthropic.held)
            assertEquals(NEUTRAL_REQUESTS, anthropic.providerCalls)
            assertEquals(anthropic, openAi)
            assertEquals(anthropic, openRouter)
        }
    }

    // The upper-case constant names the provider id type declares, read from the type itself so the test cannot drift.
    private fun providerConstantNames(): Set<String> =
        ProviderId.Companion::class.java.declaredMethods
            .map { it.name.removePrefix("get").substringBefore('-') }
            .filter { name -> name.isNotEmpty() && name.all { it.isUpperCase() || it == '_' } }
            .toSet()

    // Found from wherever the tests run: the module directory, the repository root, or any directory below either.
    private fun agenticSourceRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, AGENTIC_SOURCES), File(dir, "$CORE_MODULE/$AGENTIC_SOURCES"))
                .firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        throw AssertionError("the agentic sources were not found from ${File("").absolutePath}")
    }

    @Test
    fun theAgenticSourcesNeverNameAProvider() {
        val names = providerConstantNames()
        assertTrue(names.toString(), names.containsAll(setOf("ANTHROPIC", "OPENAI", "OPENROUTER")))
        val root = agenticSourceRoot()
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("scanned only ${files.size} agentic sources", files.size >= MIN_AGENTIC_SOURCES)
        val forbidden = (names + ProviderId::class.java.simpleName).map { Regex("\\b${Regex.escape(it)}\\b") }

        val hits = files.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> forbidden.any { it.containsMatchIn(line) } }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }

        assertTrue("a provider is named in the agentic sources:\n${hits.joinToString("\n")}", hits.isEmpty())
    }
}
