package io.github.ygaray.voiceactionengine.sample.keys

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.KeySlot
import io.github.ygaray.voiceactionengine.keystore.KeystoreCredentialSource

// One DataStore per file per process, and the app (not the keystore library) owns it (KEY-02). The file name is the
// sample's own, so it can never share storage with another app's keys.
private val Context.keyDataStore: DataStore<Preferences> by preferencesDataStore(name = "vae_sample_keys")

/**
 * The sample's key storage names, spelled out in one place: one slot per provider, every alias and preference name
 * prefixed `vae_sample_` so they cannot collide with the aliases of any other app on the device.
 */
internal object SampleKeys {
    /** The providers the sample holds a key for, in display order. */
    val PROVIDERS: List<ProviderId> = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)

    /** One slot per provider: alias `vae_sample_<p>`, ciphertext `vae_sample_<p>_ct`, IV `vae_sample_<p>_iv`. */
    val SLOTS: List<KeySlot> = PROVIDERS.map { provider ->
        KeySlot(
            provider = provider,
            alias = "vae_sample_$provider",
            ciphertextKey = "vae_sample_${provider}_ct",
            ivKey = "vae_sample_${provider}_iv",
        )
    }

    @Volatile
    private var instance: ApiKeyStore? = null

    /** The process-wide store: the DataStore allows one instance per file, so there is exactly one store too. */
    fun store(context: Context): ApiKeyStore = instance ?: synchronized(this) {
        instance ?: ApiKeyStore(context.applicationContext.keyDataStore, SLOTS).also { instance = it }
    }

    /** The only way the engine reads a key: through the keystore library's credential source. */
    fun credentials(store: ApiKeyStore): CredentialSource = KeystoreCredentialSource(store)
}
