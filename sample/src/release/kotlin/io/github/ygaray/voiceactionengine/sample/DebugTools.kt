package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.sample.keys.KeyImport
import io.github.ygaray.voiceactionengine.sample.keys.KeyVault
import java.io.File

/** Release builds have no debug tools: they cannot import plaintext keys, so every lookup answers null. */
internal object DebugTools {
    @Suppress("UNUSED_PARAMETER")
    fun keyImport(filesDir: File, vault: KeyVault, providers: List<ProviderId>): KeyImport? = null
}
