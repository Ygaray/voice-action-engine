package io.github.ygaray.voiceactionengine.sample

import android.content.Intent
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import io.github.ygaray.voiceactionengine.sample.keys.PlaintextScan
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Debug-only tools. The release variant supplies the same object with every lookup answering null. */
internal object DebugTools {
    /**
     * The importer for keys pushed to the device for testing. Debug builds only; `preferencesDataStore` writes its file
     * under `filesDir/datastore`, so that is the directory scanned for leaked plaintext.
     */
    fun keyImport(filesDir: File, vault: KeyVault, providers: List<ProviderId>): KeyImport? =
        TestKeyImporter(filesDir, vault, providers, scanRoot = File(filesDir, "datastore"))

    /**
     * The leg a debug intent asks to rerun, from its string extra `vae_autorun` (a leg's wire name), or null when the
     * extra is absent or names no leg. This is a RERUN convenience only: the first Gate-1 run of every leg is driven
     * through the screen (D-01), so a leg's first verdict never carries `trigger=autorun`.
     */
    fun autorunLeg(intent: Intent?): LegId? {
        val wire = intent?.getStringExtra(AUTORUN_EXTRA) ?: return null
        return LegId.entries.firstOrNull { it.wire == wire }
    }

    private const val AUTORUN_EXTRA = "vae_autorun"
}

/**
 * Moves each pushed plaintext key file into the keystore library through [vault], then destroys the file.
 *
 * Per provider, in the given order: look for `filesDir/test-keys/<provider>.key`; none gives an absent report; otherwise
 * read and trim it, save through the vault, read the vault's answer, overwrite the file with zero bytes, delete it,
 * confirm it is gone, and scan [scanRoot] for the plaintext. The file is destroyed whatever the save does: success,
 * any exception, or cancellation (and a cancelled or crashed import also destroys every other pushed file). It never
 * logs, and no report carries the key.
 */
internal class TestKeyImporter(
    private val filesDir: File,
    private val vault: KeyVault,
    private val providers: List<ProviderId>,
    private val scanRoot: File,
) : KeyImport {
    override suspend fun importAll(): List<ImportReport> {
        val keysDir = File(filesDir, KEYS_DIR)
        try {
            return providers.map { provider -> importOne(provider, File(keysDir, "$provider.key")) }
        } finally {
            // Whatever ended the import (a cancellation, an error nobody expected), no pushed plaintext survives it. On
            // the normal path every file is already gone and this does nothing.
            for (provider in providers) {
                val leftover = File(keysDir, "$provider.key")
                if (leftover.exists()) destroy(leftover)
            }
            if (keysDir.isDirectory && keysDir.list().isNullOrEmpty()) keysDir.delete()
        }
    }

    private suspend fun importOne(provider: ProviderId, file: File): ImportReport {
        if (!file.isFile) return ImportReport(provider, ImportReport.ABSENT_FILE, null, true, null)
        val text = try {
            file.readText(Charsets.UTF_8).trim()
        } catch (ignored: IOException) {
            return ImportReport(provider, ImportReport.SAVE_FAILED, "unreadable_file", destroy(file), null)
        }
        if (text.isEmpty()) return ImportReport(provider, ImportReport.REJECTED, null, destroy(file), null)
        var deleted = false
        val saved = try {
            saveThenRead(provider, text)
        } finally {
            // Destroyed on success, on a failed save and on cancellation alike.
            deleted = destroy(file)
        }
        val leaked = PlaintextScan.contains(scanRoot, text.toByteArray(Charsets.UTF_8))
        return ImportReport(provider, saved.state, saved.cause, deleted, leaked)
    }

    private class Saved(val state: String, val cause: String?)

    // Any failure of the save becomes a SAVE_FAILED report with the exception type only (never its message, which could
    // quote the key); a cancellation is not a failure and is rethrown.
    private suspend fun saveThenRead(provider: ProviderId, text: String): Saved = try {
        vault.save(provider, text)
        val answer = vault.read(provider)
        Saved(ImportReport.stateWord(answer), (answer as? KeyState.Unreadable)?.cause)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Saved(ImportReport.SAVE_FAILED, failure.javaClass.simpleName)
    }

    // Overwrite with zeros of the same length, delete, and report whether the file is really gone.
    private fun destroy(file: File): Boolean {
        try {
            file.writeBytes(ByteArray(file.length().toInt()))
        } catch (ignored: IOException) {
            // Deleting is still attempted below; the report says whether the file is gone.
        }
        file.delete()
        return !file.exists()
    }

    /** Prints the type only. */
    override fun toString(): String = "TestKeyImporter"

    private companion object {
        const val KEYS_DIR = "test-keys"
    }
}
