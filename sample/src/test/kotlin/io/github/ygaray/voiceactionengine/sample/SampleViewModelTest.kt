package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceSink
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.keys.KeyUx
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.ui.HeaderText
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
    vault: KeyVault = rig.vault,
) = SampleViewModel(
    runner = rig.runner,
    vault = vault,
    keyImport = keyImport,
    fixture = fixture,
    budget = rig.budget,
    okhttpVersion = "5.2.1",
    sink = sink,
    providers = ALL_PROVIDERS,
    nowSeconds = { rig.clock.seconds },
)

/** An evidence sink that keeps the lines themselves, so a test can ask whether one is loud. */
private class LineSink : EvidenceSink {
    val lines = ArrayList<EvidenceLine>()

    override fun emit(line: EvidenceLine) {
        lines.add(line)
    }

    override fun toString(): String = "LineSink(${lines.size})"
}

/** A vault that counts saves, answers Ready with the last four characters of what was saved, and fingerprints the key. */
private class CountingVault : KeyVault {
    var saves = 0
    private val keys = HashMap<ProviderId, String>()

    override suspend fun save(provider: ProviderId, key: String) {
        saves++
        keys[provider] = key
    }

    override suspend fun read(provider: ProviderId): KeyState =
        keys[provider]?.let { KeyState.Ready(it.takeLast(LAST_CHARS)) } ?: KeyState.NotConfigured()

    override suspend fun delete(provider: ProviderId) {
        keys.remove(provider)
    }

    override suspend fun fingerprint(provider: ProviderId): String? = keys[provider]?.let { KeyUx.fingerprint(it) }

    override fun toString(): String = "CountingVault"

    private companion object {
        const val LAST_CHARS = 4
    }
}

/** An importer that reports a fixed list. */
private class FixedImport(private val reports: List<ImportReport>) : KeyImport {
    override suspend fun importAll(): List<ImportReport> = reports

    override fun toString(): String = "FixedImport"
}

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
    fun theUndoLegWaitsWithItsLabelThenTheUndoPressPassesAndClearsIt() = runTest {
        NoNetworkGuard.during {
            val rig = legRig(folder.newFolder()) { emptyList() }
            val viewModel = sampleViewModel(rig)
            settle()
            assertNull(viewModel.state.value.undoLabel)

            viewModel.runLeg(LegId.UNDO_ALL)
            settle()

            assertEquals("awaiting_undo", viewModel.row(LegId.UNDO_ALL).text)
            assertEquals(Tone.WARN, viewModel.row(LegId.UNDO_ALL).tone)
            assertEquals("Undo all (3)", viewModel.state.value.undoLabel)
            assertNull(viewModel.state.value.undoNote)
            assertTrue(viewModel.state.value.runEnabled)
            assertTrue(rig.sink.starting("VAE_VERDICT ").isEmpty())

            viewModel.undoAll()

            assertEquals("RUNNING", viewModel.row(LegId.UNDO_ALL).text)
            settle()

            assertEquals("PASS", viewModel.row(LegId.UNDO_ALL).text)
            assertEquals(Tone.GOOD, viewModel.row(LegId.UNDO_ALL).tone)
            assertNull(viewModel.state.value.undoLabel)
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=undo_all verdict=PASS").size)
        }
    }

    @Test
    fun theUndoPressWhileNothingWaitsChangesNothing() = runTest {
        NoNetworkGuard.during {
            val rig = legRig(folder.newFolder()) { emptyList() }
            val viewModel = sampleViewModel(rig)
            settle()
            val before = viewModel.state.value
            val linesBefore = rig.sink.rendered.size

            viewModel.undoAll()
            settle()

            assertEquals(before, viewModel.state.value)
            assertEquals(linesBefore, rig.sink.rendered.size)
            assertEquals("IDLE", viewModel.row(LegId.UNDO_ALL).text)
        }
    }

    @Test
    fun anotherLegsResultLeavesAWaitingUndoLabelInPlace() = runTest {
        NoNetworkGuard.during {
            val rig = legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(ProviderId.OPENAI, tap, listOf(editCall()))) }
            val viewModel = sampleViewModel(rig)
            settle()
            viewModel.runLeg(LegId.UNDO_ALL)
            settle()

            viewModel.runLeg(LegId.SMOKE_OPENAI)
            settle()

            assertEquals("PASS", viewModel.row(LegId.SMOKE_OPENAI).text)
            assertEquals("Undo all (3)", viewModel.state.value.undoLabel)
            assertEquals("awaiting_undo", viewModel.row(LegId.UNDO_ALL).text)
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

    @Test
    fun startupEmitsEnvAndALoudFixtureLineWhenAbsent() = runTest {
        NoNetworkGuard.during {
            val absentSink = LineSink()
            sampleViewModel(openAiRig(), sink = absentSink)
            runCurrent()

            assertEquals(1, absentSink.lines.count { it.type == "ENV" })
            val fixtureLines = absentSink.lines.filter { it.type == "FIXTURE" }
            assertEquals(1, fixtureLines.size)
            assertTrue(fixtureLines.single().render(), fixtureLines.single().render().startsWith("VAE_FIXTURE kind=absent"))
            assertTrue(fixtureLines.single().loud)

            val loadedSink = LineSink()
            sampleViewModel(openAiRig(), fixture = loadedSyntheticFixture(), sink = loadedSink)
            runCurrent()

            assertEquals(1, loadedSink.lines.count { it.type == "ENV" })
            assertFalse(loadedSink.lines.single { it.type == "FIXTURE" }.loud)
        }
    }

    @Test
    fun theFixtureBannerNamesEveryStateLoudly() {
        assertEquals(
            "FIXTURE ABSENT - push the LE-1 fixture (GATE1-RUNBOOK)",
            HeaderText.fixtureBanner(FixtureState.Absent(listOf("files"))).text,
        )
        val mismatch = HeaderText.fixtureBanner(FixtureState.ShaMismatch("files", "0badc0de"))
        assertEquals("FIXTURE SHA MISMATCH 0badc0de - do not use; ask the orchestrator to regenerate", mismatch.text)
        assertEquals(Tone.BAD, mismatch.tone)
        assertEquals("FIXTURE MALFORMED bad_tool", HeaderText.fixtureBanner(FixtureState.Malformed("files", "bad_tool")).text)
        val fixture = loadedSyntheticFixture()
        val loaded = HeaderText.fixtureBanner(fixture)
        assertEquals(Tone.GOOD, loaded.tone)
        val digest = fixture.sha256
        assertEquals("Fixture OK sha=${digest.take(8)} tools=${fixture.tools.size} source=files", loaded.text)
        assertTrue(loaded.text, digest.takeLast(7) !in loaded.text)
        assertTrue(loaded.text, "source=files" in loaded.text)
    }

    @Test
    fun savingAKeyNeverLogsIt() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig()
            val vault = CountingVault()
            val own = LineSink()
            val viewModel = sampleViewModel(rig, sink = own, vault = vault)
            runCurrent()
            viewModel.onKeyFieldChange(ProviderId.OPENAI, "dummy-value-1234")

            viewModel.saveKey(ProviderId.OPENAI, "dummy-value-1234")
            runCurrent()

            assertEquals(1, vault.saves)
            val row = viewModel.state.value.keys.first { it.provider == ProviderId.OPENAI }
            val fingerprint = KeyUx.fingerprint("dummy-value-1234")
            assertEquals("Ready - fp $fingerprint", row.text)
            assertFalse(row.text, "1234" in row.text || "dummy" in row.text)
            assertEquals(Tone.GOOD, row.tone)
            assertEquals("", viewModel.keyFields.value[ProviderId.OPENAI])
            val printed = own.lines.map { it.render() } + rig.sink.rendered + viewModel.state.value.toString()
            for (line in printed) {
                assertFalse(line, "dummy-value" in line || "1234" in line || fingerprint in line)
            }
        }
    }

    @Test
    fun savingTheTypedFieldUsesAndClearsIt() = runTest {
        NoNetworkGuard.during {
            val vault = CountingVault()
            val viewModel = sampleViewModel(openAiRig(), vault = vault)
            runCurrent()
            viewModel.onKeyFieldChange(ProviderId.ANTHROPIC, "  placeholder-abcd  ")

            viewModel.saveKey(ProviderId.ANTHROPIC)
            runCurrent()

            assertEquals(1, vault.saves)
            val anthropicRow = viewModel.state.value.keys.first { it.provider == ProviderId.ANTHROPIC }.text
            assertEquals("Ready - fp ${KeyUx.fingerprint("placeholder-abcd")}", anthropicRow)
            assertFalse(anthropicRow, "abcd" in anthropicRow)
            assertEquals("", viewModel.keyFields.value[ProviderId.ANTHROPIC])

            viewModel.deleteKey(ProviderId.ANTHROPIC)
            runCurrent()

            assertEquals("Not configured", viewModel.state.value.keys.first { it.provider == ProviderId.ANTHROPIC }.text)
        }
    }

    @Test
    fun importShowsFlagsWithoutLast4() = runTest {
        NoNetworkGuard.during {
            val reports = listOf(
                ImportReport(ProviderId.ANTHROPIC, ImportReport.READY, null, true, false),
                ImportReport(ProviderId.OPENAI, ImportReport.ABSENT_FILE, null, true, null),
                ImportReport(ProviderId.OPENROUTER, ImportReport.READY, null, true, false),
            )
            val own = LineSink()
            val viewModel = sampleViewModel(openAiRig(), keyImport = FixedImport(reports), sink = own)
            runCurrent()

            viewModel.importTestKeys()
            runCurrent()

            assertEquals(3, own.lines.count { it.type == "KEY" })
            val status = viewModel.state.value.importStatus
            assertNotNull(status)
            assertEquals(
                "anthropic=Ready deleted=true in_datastore=false; openai=absent_file deleted=true in_datastore=none; " +
                    "openrouter=Ready deleted=true in_datastore=false",
                status!!.text,
            )
            assertEquals(Tone.GOOD, status.tone)
            assertTrue(viewModel.state.value.importAvailable)
            assertFalse(sampleViewModel(openAiRig()).state.value.importAvailable)
        }
    }

    @Test
    fun anImporterThatThrowsIsShownAsAFailureNotACrash() = runTest {
        NoNetworkGuard.during {
            val throwing = object : KeyImport {
                override suspend fun importAll(): List<ImportReport> = throw IllegalStateException("zz-not-shown")
            }
            val viewModel = sampleViewModel(openAiRig(), keyImport = throwing)
            runCurrent()

            viewModel.importTestKeys()
            runCurrent()

            val status = viewModel.state.value.importStatus!!
            assertEquals(Tone.BAD, status.tone)
            assertEquals("Import failed (IllegalStateException)", status.text)
        }
    }

    @Test
    fun aLeakedPlaintextMakesTheImportRed() = runTest {
        NoNetworkGuard.during {
            val leaked = listOf(ImportReport(ProviderId.ANTHROPIC, ImportReport.READY, null, true, true))
            val viewModel = sampleViewModel(openAiRig(), keyImport = FixedImport(leaked))
            runCurrent()

            viewModel.importTestKeys()
            runCurrent()

            assertEquals(Tone.BAD, viewModel.state.value.importStatus!!.tone)
        }
    }

    @Test
    fun theWarmWindowCountsDown() = runTest {
        NoNetworkGuard.during {
            val capabilities = ModelCapabilities {
                caching = CachingMode.EXPLICIT_BREAKPOINTS
                minCacheablePrefixTokens = 4096
            }
            val lookup = ok(
                FakeAiProvider.toolCall(
                    "call_1",
                    "find_items",
                    buildJsonObject { put("query", "paper") },
                    Usage(40, 0, 7016, 20),
                ),
            )
            val answer = ok(FakeAiProvider.reply("You have two items.", Usage(30, 7016, 0, 15)))
            val fixture = loadedSyntheticFixture()
            val rig = legRig(folder.newFolder(), { fixture }) { tap ->
                listOf(AttemptingFake(ProviderId.ANTHROPIC, tap, listOf(lookup, answer), capabilities))
            }
            val viewModel = sampleViewModel(rig, fixture = fixture)
            runCurrent()
            assertNull(viewModel.state.value.warmWindow)

            viewModel.runLeg(LegId.VER02)
            runCurrent()

            assertEquals("PASS", viewModel.row(LegId.VER02).text)
            assertEquals("Anthropic warm window: wait 360 s", viewModel.state.value.warmWindow)
            rig.clock.seconds += 100
            viewModel.tick()
            assertEquals("Anthropic warm window: wait 260 s", viewModel.state.value.warmWindow)
            rig.clock.seconds += 260
            viewModel.tick()
            assertNull(viewModel.state.value.warmWindow)
            assertTrue(viewModel.state.value.budgetText, viewModel.state.value.budgetText.startsWith("requests 2/15"))
        }
    }

    @Test
    fun anAutorunRerunSaysSoOnTheVerdictLine() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig(editCall(), editCall())
            val viewModel = sampleViewModel(rig)
            runCurrent()

            viewModel.runLeg(LegId.SMOKE_OPENAI)
            runCurrent()
            viewModel.runLeg(LegId.SMOKE_OPENAI, TRIGGER_AUTORUN)
            runCurrent()

            assertEquals(1, rig.sink.starting("VAE_AUTORUN leg=smoke_openai").size)
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=smoke_openai verdict=PASS key_charset=ok trigger=ui").size)
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=smoke_openai verdict=PASS key_charset=ok trigger=autorun").size)
        }
    }

    @Test
    fun anAutorunCannotBeALegsFirstRun() = runTest {
        NoNetworkGuard.during {
            val rig = openAiRig(editCall())
            val viewModel = sampleViewModel(rig)
            runCurrent()

            viewModel.runLeg(LegId.SMOKE_OPENAI, TRIGGER_AUTORUN)
            runCurrent()

            assertEquals(0, rig.fake(ProviderId.OPENAI).calls.size)
            assertEquals(0, rig.budget.snapshot().total)
            assertEquals(
                1,
                rig.sink.starting("VAE_VERDICT leg=smoke_openai verdict=REFUSED reason=autorun_before_ui trigger=autorun").size,
            )
            // A demo leg costs nothing but follows the same rule.
            viewModel.runLeg(LegId.DEMO_PARTIAL, TRIGGER_AUTORUN)
            runCurrent()
            assertEquals(
                1,
                rig.sink.starting("VAE_VERDICT leg=demo_partial verdict=REFUSED reason=autorun_before_ui trigger=autorun").size,
            )
            viewModel.runLeg(LegId.DEMO_PARTIAL)
            runCurrent()
            viewModel.runLeg(LegId.DEMO_PARTIAL, TRIGGER_AUTORUN)
            runCurrent()
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=demo_partial verdict=PASS trigger=autorun").size)
        }
    }
}
