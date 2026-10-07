package wire

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.KeySlot
import io.github.ygaray.voiceactionengine.keystore.KeystoreCredentialSource

/** One key slot per provider: the alias and the two preference names are the app's own stable choices. */
val WireKeySlots: List<KeySlot> = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)
    .map { provider ->
        KeySlot(
            provider = provider,
            alias = "wire_app_$provider",
            ciphertextKey = "wire_app_${provider}_ct",
            ivKey = "wire_app_${provider}_iv",
        )
    }

// One DataStore per file per process, and the app owns it.
private val Context.wireKeyDataStore: DataStore<Preferences> by preferencesDataStore(name = "wire_app_keys")

fun wireCredentials(context: Context): CredentialSource =
    KeystoreCredentialSource(ApiKeyStore(context.applicationContext.wireKeyDataStore, WireKeySlots))
