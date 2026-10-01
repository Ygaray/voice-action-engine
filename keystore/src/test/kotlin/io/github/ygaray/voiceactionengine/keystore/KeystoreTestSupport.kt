package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val AES_256_BYTES = 32

/**
 * Software keys behind the same seam the platform key store uses, so the tests drive the production cipher code.
 * [existingKey] never creates; [getOrCreateKey] is the only creator and goes through [existingKey] first, so a lookup
 * failure propagates instead of creating a key.
 */
internal class SoftwareKeyAccess : KeyAccess {
    private val keys = ConcurrentHashMap<String, SecretKey>()
    private val random = SecureRandom()

    val getOrCreateCalls = AtomicInteger()
    val createdKeys = AtomicInteger()
    val lookups = AtomicInteger()

    @Volatile
    var failLookupWith: Throwable? = null

    @Volatile
    var failCreateWith: Throwable? = null

    override fun existingKey(alias: String): SecretKey? {
        lookups.incrementAndGet()
        failLookupWith?.let { throw it }
        return keys[alias]
    }

    override fun getOrCreateKey(alias: String): SecretKey {
        getOrCreateCalls.incrementAndGet()
        synchronized(keys) {
            existingKey(alias)?.let { return it }
            failCreateWith?.let { throw it }
            val bytes = ByteArray(AES_256_BYTES).also { random.nextBytes(it) }
            val key = SecretKeySpec(bytes, "AES")
            keys[alias] = key
            createdKeys.incrementAndGet()
            return key
        }
    }

    /** Forgets the key under [alias], as a restored backup or a wiped key store would. */
    fun lose(alias: String) {
        keys.remove(alias)
    }

    /** Puts a known key under [alias]. */
    fun install(alias: String, keyBytes: ByteArray) {
        keys[alias] = SecretKeySpec(keyBytes, "AES")
    }

    /** The key under [alias]; fails the test when there is none. */
    fun keyFor(alias: String): SecretKey = checkNotNull(keys[alias]) { "no key under $alias" }
}

/** A real preferences DataStore on a temp file, the way an app would inject its own. */
internal class TempPreferences(folder: TemporaryFolder) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val file: File = folder.newFile("t.preferences_pb").also { it.delete() }

    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    /** Every stored preference by its name. */
    suspend fun snapshot(): Map<String, Any?> = dataStore.data.first().asMap().mapKeys { it.key.name }

    fun close() {
        scope.cancel()
    }
}
