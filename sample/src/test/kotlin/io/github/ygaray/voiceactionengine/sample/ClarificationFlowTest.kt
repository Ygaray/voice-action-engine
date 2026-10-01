package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The offline demos: an A19 clarification with pressable options, a linked follow-up, and a partial outcome. */
class ClarificationFlowTest {

    @get:Rule
    val folder = TemporaryFolder()

    // No fake transports: the demos must not touch a real provider, a key or the budget.
    private fun rig() = legRig(folder.newFolder()) { emptyList() }.also { rig ->
        for (provider in listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)) {
            rig.vault.set(provider, KeyState.NotConfigured())
        }
    }

    @Test
    fun theClarificationDemoEndsOnPressableOptions() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.run(LegId.DEMO_CLARIFY)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val outcome = result.outcome as CommandOutcome.Completed
            val clarification = outcome.terminalCall?.asClarification()
            assertNotNull(outcome.toString(), clarification)
            assertTrue(clarification!!.question.isNotBlank())
            assertEquals(listOf("list-a", "list-b"), clarification.options.map { it.id })
            assertEquals(listOf("List A", "List B"), clarification.options.map { it.label })
            assertEquals(0, rig.budget.snapshot().total)
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=demo_clarify verdict=PASS trigger=ui").size)
        }
    }

    @Test
    fun choosingAnOptionStartsALinkedFollowUp() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            val first = rig.runner.run(LegId.DEMO_CLARIFY).outcome as CommandOutcome.Completed
            val chosen = first.terminalCall!!.asClarification()!!.options.first { it.id == "list-b" }

            val result = rig.runner.followUp(first, chosen)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val second = result.outcome as CommandOutcome.Completed
            assertEquals(first.runId, second.parentRunId)
            assertEquals(false, second.partial)
            assertEquals(listOf("create_item"), second.commits.map { it.toolName })
            assertEquals(listOf(ActionKind.COMMITTED), second.commits.map { it.kind })
            // The sink heard the run close with the link to the first run.
            assertEquals(first.runId, rig.demoCommits.closes.last().parentRunId)
            // A new command, not a resumed one: the follow-up's first message carries everything the model needs.
            val message = rig.demo.calls.first { call -> "The user chose:" in firstText(call.request.messages.first()) }
            val text = firstText(message.request.messages.first())
            assertTrue(text, "add paper to my list" in text)
            assertTrue(text, "Which list should I add it to?" in text)
            assertTrue(text, "The user chose: List B (list-b)" in text)
            assertEquals(1, message.request.messages.size)
        }
    }

    @Test
    fun thePartialDemoIsMarkedPartial() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.run(LegId.DEMO_PARTIAL)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val outcome = result.outcome as CommandOutcome.Completed
            assertTrue(outcome.partial)
            assertEquals(1, outcome.commits.size)
            assertEquals(0, rig.budget.snapshot().total)
        }
    }

    private fun firstText(message: Message): String =
        (message as? UserMessage)?.text.orEmpty()
}
