package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SHORT_KEY = "k" // secret-scan: allow (fake canary)
private const val FOUR_KEY = "abcd" // secret-scan: allow (fake canary)
private const val FIVE_KEY = "abcde" // secret-scan: allow (fake canary)
private const val LONG_LENGTH = 40

/** The four states: the last-four rule, what their text form reveals, and their equality. */
class KeyStateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    private val store: ApiKeyStore by lazy {
        val temp = TempPreferences(folder).also { prefs = it }
        ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.IO, SoftwareKeyAccess())
    }

    private suspend fun readBack(apiKey: String): KeyState {
        store.save(ProviderId.ANTHROPIC, apiKey)
        return store.read(ProviderId.ANTHROPIC)
    }

    @Test
    fun shortKeysShowNoFingerprintAndLongerKeysShowTheirLastFour() = runTest {
        val longKey = "z".repeat(LONG_LENGTH - FOUR_KEY.length) + "wxyz" // secret-scan: allow (fake canary)

        assertEquals(KeyState.Ready(""), readBack(SHORT_KEY))
        assertEquals(KeyState.Ready(""), readBack(FOUR_KEY))
        assertEquals(KeyState.Ready("bcde"), readBack(FIVE_KEY))
        assertEquals(KeyState.Ready("wxyz"), readBack(longKey))
        assertEquals("", KeyState.Ready("").last4)
    }

    @Test
    fun readyNeverPrintsItsFingerprint() {
        assertFalse(KeyState.Ready("abcd").toString().contains("abcd"))
    }

    @Test
    fun unreadablePrintsItsCodeAndTheOtherLeavesNameThemselves() {
        assertTrue(KeyState.Unreadable("decrypt_failed").toString().contains("decrypt_failed"))
        assertTrue(KeyState.NotConfigured().toString().contains("NotConfigured"))
        assertTrue(KeyState.KeyMissing().toString().contains("KeyMissing"))
    }

    @Test
    fun aFingerprintLongerThanFourAndANonCodeCauseAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { KeyState.Ready(FIVE_KEY) }
        listOf("Key was wiped", "", "UPPER").forEach { cause ->
            assertThrows(IllegalArgumentException::class.java) { KeyState.Unreadable(cause) }
        }
    }

    @Test
    fun leavesAreEqualByValueAndNeverEqualToAnotherLeaf() {
        assertEquals(KeyState.NotConfigured(), KeyState.NotConfigured())
        assertEquals(KeyState.NotConfigured().hashCode(), KeyState.NotConfigured().hashCode())
        assertEquals(KeyState.KeyMissing(), KeyState.KeyMissing())
        assertEquals(KeyState.KeyMissing().hashCode(), KeyState.KeyMissing().hashCode())
        assertEquals(KeyState.Ready("abcd"), KeyState.Ready("abcd"))
        assertEquals(KeyState.Ready("abcd").hashCode(), KeyState.Ready("abcd").hashCode())
        assertNotEquals(KeyState.Ready("abcd"), KeyState.Ready("wxyz"))
        assertEquals(KeyState.Unreadable("decrypt_failed"), KeyState.Unreadable("decrypt_failed"))
        assertEquals(KeyState.Unreadable("decrypt_failed").hashCode(), KeyState.Unreadable("decrypt_failed").hashCode())
        assertNotEquals(KeyState.Unreadable("decrypt_failed"), KeyState.Unreadable("key_missing"))

        val leaves = listOf(
            KeyState.NotConfigured(),
            KeyState.Ready(""),
            KeyState.KeyMissing(),
            KeyState.Unreadable("decrypt_failed"),
        )
        leaves.forEachIndexed { i, left ->
            leaves.forEachIndexed { j, right -> if (i != j) assertNotEquals(left, right) }
        }
    }
}
