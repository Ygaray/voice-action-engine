package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Debug-variant unit test: the importer class does not exist in the release variant. */
class TestKeyImporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Deliberately not key-shaped: no provider prefix, nothing a scanner would mistake for a real key.
    private val canary = "test-canary-value-0000WXYZ"
    private val canaryLast4 = "WXYZ"

    private class FakeVault : KeyVault {
        val saves = mutableListOf<Pair<ProviderId, String>>()
        private val stored = mutableMapOf<ProviderId, String>()

        override suspend fun save(provider: ProviderId, key: String) {
            saves += provider to key
            stored[provider] = key
        }

        override suspend fun read(provider: ProviderId): KeyState =
            stored[provider]?.let { KeyState.Ready(it.takeLast(LAST4)) } ?: KeyState.NotConfigured()

        override suspend fun delete(provider: ProviderId) {
            stored.remove(provider)
        }

        private companion object {
            const val LAST4 = 4
        }
    }

    private val providers = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)

    private fun importer(vault: FakeVault, filesDir: File) =
        requireNotNull(DebugTools.keyImport(filesDir, vault, providers))

    private fun pushKey(filesDir: File, provider: ProviderId, text: String): File {
        val dir = File(filesDir, "test-keys").apply { mkdirs() }
        return File(dir, "$provider.key").apply { writeText(text) }
    }

    @Test
    fun aPushedKeyIsSavedThroughTheVaultAndThePlaintextIsDestroyed() = runTest {
        val filesDir = tmp.newFolder("files")
        val file = pushKey(filesDir, ProviderId.ANTHROPIC, "$canary\n")
        val vault = FakeVault()

        val reports = importer(vault, filesDir).importAll()

        assertEquals(listOf(ProviderId.ANTHROPIC to canary), vault.saves)
        val report = reports.first { it.provider == ProviderId.ANTHROPIC }
        assertEquals(ImportReport.READY, report.state)
        assertTrue(report.plaintextFileDeleted)
        assertFalse(file.exists())
        assertFalse("test-keys directory is removed when empty", File(filesDir, "test-keys").exists())
    }

    @Test
    fun theDatastoreScanReportsFalseWhenOnlyCiphertextIsStored() = runTest {
        val filesDir = tmp.newFolder("files")
        pushKey(filesDir, ProviderId.ANTHROPIC, canary)
        File(filesDir, "datastore").apply { mkdirs() }.resolve("vae_sample_keys.preferences_pb")
            .writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        val report = importer(FakeVault(), filesDir).importAll().first { it.provider == ProviderId.ANTHROPIC }

        assertEquals(false, report.plaintextInDatastore)
    }

    @Test
    fun theDatastoreScanReportsTrueWhenThePlaintextIsThere() = runTest {
        val filesDir = tmp.newFolder("files")
        pushKey(filesDir, ProviderId.ANTHROPIC, canary)
        File(filesDir, "datastore").apply { mkdirs() }.resolve("vae_sample_keys.preferences_pb")
            .writeBytes("xx$canary".toByteArray())

        val report = importer(FakeVault(), filesDir).importAll().first { it.provider == ProviderId.ANTHROPIC }

        assertEquals(true, report.plaintextInDatastore)
    }

    @Test
    fun absentFilesAreReportedNotInvented() = runTest {
        val filesDir = tmp.newFolder("files")
        pushKey(filesDir, ProviderId.ANTHROPIC, canary)
        val vault = FakeVault()

        val reports = importer(vault, filesDir).importAll()

        assertEquals(providers, reports.map { it.provider })
        for (absent in reports.filter { it.provider != ProviderId.ANTHROPIC }) {
            assertEquals(ImportReport.ABSENT_FILE, absent.state)
            assertNull(absent.plaintextInDatastore)
        }
        assertEquals(1, vault.saves.size)
    }

    @Test
    fun aBlankFileIsRejectedWithoutASaveAndStillDestroyed() = runTest {
        val filesDir = tmp.newFolder("files")
        val file = pushKey(filesDir, ProviderId.OPENAI, "  \n")
        val vault = FakeVault()

        val report = importer(vault, filesDir).importAll().first { it.provider == ProviderId.OPENAI }

        assertEquals(ImportReport.REJECTED, report.state)
        assertTrue(report.plaintextFileDeleted)
        assertFalse(file.exists())
        assertTrue(vault.saves.isEmpty())
    }

    // A vault whose save fails with [failure] for one provider and works for the rest.
    private class FailingVault(private val failing: ProviderId, private val failure: Throwable) : KeyVault {
        private val stored = mutableMapOf<ProviderId, String>()

        override suspend fun save(provider: ProviderId, key: String) {
            if (provider == failing) throw failure
            stored[provider] = key
        }

        override suspend fun read(provider: ProviderId): KeyState =
            stored[provider]?.let { KeyState.Ready("ok") } ?: KeyState.NotConfigured()

        override suspend fun delete(provider: ProviderId) {
            stored.remove(provider)
        }
    }

    @Test
    fun anUnexpectedExceptionStillDestroysTheFileAndDoesNotStopTheOthers() = runTest {
        val filesDir = tmp.newFolder("files")
        val first = pushKey(filesDir, ProviderId.ANTHROPIC, canary)
        val second = pushKey(filesDir, ProviderId.OPENAI, canary)
        val vault = FailingVault(ProviderId.ANTHROPIC, IllegalStateException(canary))

        val reports = requireNotNull(DebugTools.keyImport(filesDir, vault, providers)).importAll()

        val failed = reports.first { it.provider == ProviderId.ANTHROPIC }
        assertEquals(ImportReport.SAVE_FAILED, failed.state)
        assertEquals("IllegalStateException", failed.cause)
        assertTrue(failed.plaintextFileDeleted)
        assertEquals(ImportReport.READY, reports.first { it.provider == ProviderId.OPENAI }.state)
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertFalse(failed.toString().contains(canary))
    }

    @Test
    fun aCancelledImportDestroysEveryPushedFile() = runTest {
        val filesDir = tmp.newFolder("files")
        val files = providers.map { pushKey(filesDir, it, canary) }
        val vault = FailingVault(ProviderId.OPENAI, CancellationException("cancelled"))

        var cancelled = false
        try {
            requireNotNull(DebugTools.keyImport(filesDir, vault, providers)).importAll()
        } catch (expected: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        for (file in files) assertFalse(file.path, file.exists())
        assertFalse(File(filesDir, "test-keys").exists())
    }

    @Test
    fun reportsNeverCarryTheKey() = runTest {
        val filesDir = tmp.newFolder("files")
        pushKey(filesDir, ProviderId.ANTHROPIC, canary)
        File(filesDir, "datastore").apply { mkdirs() }.resolve("leak.bin").writeBytes(canary.toByteArray())

        val reports = importer(FakeVault(), filesDir).importAll()

        assertNotNull(reports.firstOrNull())
        for (report in reports) {
            val fields = listOf(
                report.toString(),
                report.provider.toString(),
                report.state,
                report.cause.orEmpty(),
            )
            for (text in fields) {
                assertFalse(text.contains(canary))
                assertFalse(text.contains(canaryLast4))
            }
        }
    }
}
