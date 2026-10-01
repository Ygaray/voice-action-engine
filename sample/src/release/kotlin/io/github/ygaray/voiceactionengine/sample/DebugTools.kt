package io.github.ygaray.voiceactionengine.sample

import android.content.Intent
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import java.io.File

/** Release builds have no debug tools: they cannot import plaintext keys, so every lookup answers null. */
internal object DebugTools {
    @Suppress("UNUSED_PARAMETER")
    fun keyImport(filesDir: File, vault: KeyVault, providers: List<ProviderId>): KeyImport? = null

    /** A release build ignores any rerun request from an intent: it never starts a leg by itself. */
    @Suppress("UNUSED_PARAMETER")
    fun autorunLeg(intent: Intent?): LegId? = null
}
