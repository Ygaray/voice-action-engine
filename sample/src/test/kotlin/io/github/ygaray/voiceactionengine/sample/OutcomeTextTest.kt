package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.ui.OutcomeText
import io.github.ygaray.voiceactionengine.sample.ui.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The readout rules: what each kind of outcome says on screen, shown through real outcomes from the real runner. */
@OptIn(ExperimentalCoroutinesApi::class)
class OutcomeTextTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun mainIsATestDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun mainIsRestored() {
        Dispatchers.resetMain()
    }

    // No fake transports: the demos never touch a real provider, a key or the budget.
    private fun demoRig() = legRig(folder.newFolder()) { emptyList() }

    private fun openAiRig(vararg steps: AttemptStep) =
        legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(ProviderId.OPENAI, tap, steps.toList())) }

    @Test
    fun aPartialOutcomeNeverReadsAsDone() = runTest {
        NoNetworkGuard.during {
            val result = demoRig().runner.run(LegId.DEMO_PARTIAL)

            val view = OutcomeText.render(result, live = false)

            assertTrue(view.headline, view.headline.startsWith("Did 1 action(s), couldn't finish"))
            assertEquals(Tone.WARN, view.tone)
            assertFalse(view.headline, view.headline.startsWith("Done"))
            assertNull(view.banner)
        }
    }

    @Test
    fun aFailedOutcomeIsALoudBannerWithItsCode() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig(ok(ModelResult.Failure(FailureReason.Other("x_code"))))
            rig.vault.set(ProviderId.OPENAI, KeyState.Ready("fake"))

            val result = rig.runner.run(LegId.SMOKE_OPENAI)

            assertTrue(result.toString(), result.outcome is CommandOutcome.Failed)
            val view = OutcomeText.render(result, live = true)
            assertEquals(Tone.BAD, view.tone)
            assertEquals("FAILED: x_code", view.banner)
        }
    }

    @Test
    fun anUnreadableKeyNamesTheUserAction() {
        val lost = OutcomeText.failure(FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "decrypt_failed"))
        val transient = OutcomeText.failure(FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "keystore_unavailable"))
        val other = OutcomeText.failure(FailureReason.Other("something_else"))

        assertTrue(lost.banner, lost.banner!!.startsWith("FAILED: credential_unreadable") && "re-enter key" in lost.banner!!)
        assertTrue(transient.banner, "transient, retry" in transient.banner!!)
        assertEquals(Tone.BAD, transient.tone)
        // A reason that is not a keystore one carries no keystore action.
        assertFalse(other.banner, "re-enter key" in other.banner!! || "retry" in other.banner!!)
        assertEquals("FAILED: something_else", other.banner)
    }

    @Test
    fun aClarificationRendersItsOptions() = runTest {
        NoNetworkGuard.during {
            val result = demoRig().runner.run(LegId.DEMO_CLARIFY)

            val view = OutcomeText.render(result, live = false)

            assertEquals("Which list should I add it to?", view.question)
            assertEquals(listOf("list-a", "list-b"), view.options.map { it.id })
            assertEquals(listOf("List A", "List B"), view.options.map { it.label })
            assertFalse(view.headline, view.headline.startsWith("Done"))
        }
    }

    @Test
    fun choosingAnOptionRunsTheFollowUp() = runTest {
        NoNetworkGuard.during {
            val rig = demoRig()
            val viewModel = sampleViewModel(rig)
            runCurrent()
            viewModel.runLeg(LegId.DEMO_CLARIFY)
            runCurrent()
            val first = viewModel.state.value.readout
            assertNotNull(first)
            assertEquals(listOf("list-a", "list-b"), first!!.options.map { it.id })

            viewModel.chooseOption("list-b")
            runCurrent()

            val readout = viewModel.state.value.readout
            assertNotNull(readout)
            assertTrue(readout!!.headline, readout.headline.startsWith("Done: 1 committed"))
            assertTrue(readout.options.isEmpty())
            assertEquals("PASS", viewModel.state.value.legs.first { it.leg == LegId.DEMO_CLARIFY }.text)
            val closed = rig.demoCommits.closes
            assertEquals("the follow-up is linked to the first run", 2, closed.size)
            assertEquals(closed.first().runId, closed.last().parentRunId)
            // An id that is not on offer starts nothing.
            viewModel.chooseOption("list-b")
            runCurrent()
            assertEquals(2, rig.demoCommits.closes.size)
        }
    }

    @Test
    fun liveRepliesAreLengthOnly() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig(ok(FakeAiProvider.reply("hello there", Usage(10, 0, 0, 5))))

            val live = rig.runner.run(LegId.MULTI_OPENAI)
            val liveView = OutcomeText.render(live, live = true)

            assertTrue(liveView.headline, "reply_len=11" in liveView.headline)
            assertFalse(liveView.headline, "hello there" in liveView.headline)

            val demo = demoRig().runner.run(LegId.DEMO_CLARIFY)
            val first = demo.outcome as CommandOutcome.Completed
            val option = first.terminalCall!!.asClarification()!!.options.first()
            val followed = demoRig().runner.followUp(first, option)
            val demoView = OutcomeText.render(followed, live = false)

            assertTrue(demoView.headline, "Added it to the chosen list." in demoView.headline)
        }
    }
}
