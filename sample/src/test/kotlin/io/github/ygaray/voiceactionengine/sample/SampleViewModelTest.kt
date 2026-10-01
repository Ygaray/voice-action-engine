package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.ui.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

private const val EDIT_TOOL = "edit_item"
private val ALL_PROVIDERS = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)

/** A view model over [rig]'s runner, vault, budget and clock; its evidence goes to [sink]. */
internal fun sampleViewModel(
    rig: LegRig,
    fixture: FixtureState = FixtureState.Absent(listOf("none")),
    keyImport: KeyImport? = null,
    sink: EvidenceSink = rig.sink,
) = SampleViewModel(
    runner = rig.runner,
    vault = rig.vault,
    keyImport = keyImport,
    fixture = fixture,
    budget = rig.budget,
    okhttpVersion = "5.2.1",
    sink = sink,
    providers = ALL_PROVIDERS,
    nowSeconds = { rig.clock.seconds },
)

/** The screen's logic: one leg at a time, verdict to status word, keys, import and the follow-up, over fake transports. */
@OptIn(ExperimentalCoroutinesApi::class)
class SampleViewModelTest {

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

    private fun editCall() = ok(
        FakeAiProvider.toolCall(
            "call_1",
            EDIT_TOOL,
            buildJsonObject {
                put("id", "c-42")
                put("body", "buy more paper")
            },
            Usage(10, 0, 0, 5),
        ),
    )

    private fun openAiRig(vararg steps: AttemptStep) =
        legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(ProviderId.OPENAI, tap, steps.toList())) }

    private fun SampleViewModel.row(leg: LegId) = state.value.legs.first { it.leg == leg }

    private fun TestScope.settle() {
        runCurrent()
    }

    @Test
    fun aLegPressShowsRunningThenItsVerdict() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig(editCall())
            val viewModel = sampleViewModel(rig)
            settle()

            viewModel.runLeg(LegId.SMOKE_OPENAI)

            assertEquals("RUNNING", viewModel.row(LegId.SMOKE_OPENAI).text)
            assertFalse("other legs must be disabled while one runs", viewModel.state.value.runEnabled)
            assertEquals(LegId.SMOKE_OPENAI, viewModel.state.value.running)

            settle()

            assertEquals("PASS", viewModel.row(LegId.SMOKE_OPENAI).text)
            assertEquals(Tone.GOOD, viewModel.row(LegId.SMOKE_OPENAI).tone)
            assertNotNull(viewModel.state.value.readout)
            assertTrue(viewModel.state.value.runEnabled)
            assertNull(viewModel.state.value.running)
            assertEquals("IDLE", viewModel.row(LegId.SMOKE_ANTHROPIC).text)
        }
    }

    @Test
    fun aSecondPressWhileRunningIsIgnored() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig(editCall())
            val viewModel = sampleViewModel(rig)
            settle()

            viewModel.runLeg(LegId.SMOKE_OPENAI)
            viewModel.runLeg(LegId.SMOKE_OPENAI)
            viewModel.runLeg(LegId.MULTI_OPENAI)
            settle()

            assertEquals(1, rig.fake(ProviderId.OPENAI).calls.size)
            assertEquals("PASS", viewModel.row(LegId.SMOKE_OPENAI).text)
            assertEquals("IDLE", viewModel.row(LegId.MULTI_OPENAI).text)
        }
    }

    @Test
    fun aRefusedLegIsRedWithItsReason() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig()
            rig.vault.set(ProviderId.OPENAI, KeyState.NotConfigured())
            val viewModel = sampleViewModel(rig)
            settle()

            viewModel.runLeg(LegId.SMOKE_OPENAI)
            settle()

            assertEquals("REFUSED reason=key_NotConfigured", viewModel.row(LegId.SMOKE_OPENAI).text)
            assertEquals(Tone.BAD, viewModel.row(LegId.SMOKE_OPENAI).tone)
            assertTrue(rig.fake(ProviderId.OPENAI).calls.isEmpty())
        }
    }
}
