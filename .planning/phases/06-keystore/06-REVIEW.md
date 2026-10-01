---
phase: 06-keystore
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 34
files_reviewed_list:
  - build.gradle.kts
  - gradle/libs.versions.toml
  - keystore/build.gradle.kts
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcm.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlot.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyState.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauses.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSource.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt
  - keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreDeviceTest.kt
  - keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyDeviceCrypto.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcmTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreAtomicityTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreObserveTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/GoldenVectorTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlotValidationTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyStateTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreApiShapeTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCanaryTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSourceTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystorePipelineTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreTestSupport.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyCompatJvmTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyWriters.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/PlatformKeyAccessJvmTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ReadNeverCreatesKeyTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt
  - scripts/run-keystore-instrumented.sh
  - scripts/verify-keystore-device-guard.sh
  - scripts/verify-negative-controls.sh
findings:
  critical: 0
  warning: 6
  info: 5
  total: 11
status: resolved
---

# Phase 6: Code Review Report

**Reviewed:** 2026-10-01
**Depth:** standard
**Files Reviewed:** 34
**Status:** issues_found

## Summary

The `:keystore` module is generally well built. The wire format matches SecondBrain's and CalTracker's `KeystoreCrypto.kt` byte for byte (AES/GCM/NoPadding, 128-bit tag, provider-generated 12-byte IV, standard padded Base64). The preference names match the apps' real `stringPreferencesKey` literals. Reads never create keys. Secrets are kept out of `toString()` and exception messages, and the canary tests cover this. I found no BLOCKER-level defects.

The issues are in the failure-classification and observation layers, plus a few overclaiming comments and script details:

- Transient platform-keystore failures are reported as `decrypt_failed`.
- `observe` and `read` handle storage errors inconsistently.
- `observe` re-emits and re-decrypts on every unrelated write to the app's shared DataStore.
- Three catch sites catch `RuntimeException` and rely on a variable name to get past detekt.
- A KDoc makes a locking guarantee the code cannot provide.
- The TESTER runner can falsely report that its cleanup succeeded.

## Warnings

### WR-01: Transient keystore failures during cipher init or `doFinal` are classified as `decrypt_failed`

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt:64-72`
**Issue:** The design (D-10, the `AndroidKeyStoreKeyAccess` KDoc, `KeystoreCauses`) separates "the platform key store is temporarily unreachable" (`keystore_unavailable`, stored value may be fine) from "the value is bad" (`decrypt_failed`). `getKey` on AndroidKeyStore returns an opaque handle, so the real key store call happens at `Cipher.init` and `doFinal`. `decrypt()` maps every `GeneralSecurityException` and every `RuntimeException` to `decrypt_failed`. That includes `InvalidKeyException`, `KeyStoreException`, `ProviderException` and the `ServiceSpecificException`-derived runtime exceptions that Keystore2 raises when it is busy or hit a system error. The `lookUp` stage's careful transient handling is therefore bypassed. A user with a perfectly good key is told "the value was altered or sealed under another key", and the UI says "re-enter your key". CalTracker's planned app-side auto-clear banner (D-07) would then destroy a good key. `UnreadableMappingTest` only exercises transient failures at lookup time, never at cipher time.
**Fix:** Classify by exception type. Only an authentication failure is a bad value:
```kotlin
private fun decrypt(key: SecretKey, sealed: Sealed): Stage<ByteArray> = try {
    proceed(AesGcm.open(key, sealed.iv, sealed.ciphertext))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (ignored: AEADBadTagException) {
    failed(KeystoreCauses.decryptFailed)
} catch (ignored: BadPaddingException) {
    failed(KeystoreCauses.decryptFailed)
} catch (ignored: IllegalBlockSizeException) {
    failed(KeystoreCauses.decryptFailed)
} catch (ignored: GeneralSecurityException) {   // InvalidKey, KeyStore, NoSuchAlgorithm...
    failed(KeystoreCauses.keystoreUnavailable)
} catch (ignored: ProviderException) {
    failed(KeystoreCauses.keystoreUnavailable)
}
```
Add a test with a `KeyAccess` that returns a key whose cipher init throws `ProviderException` and `InvalidKeyException`, and assert `keystore_unavailable`.

### WR-02: `observe` and `read` disagree on which storage failures are mapped, and `observe` can crash its collector

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt:109-112` vs `:123-131`
**Issue:** `storedPreferences()` (used by `read` and the credential source) maps `IOException` and any `RuntimeException` to `storage_unreadable`. `observeSlot` maps only `IOException` and rethrows everything else. The `observe` KDoc promises that only a failure to read the preferences "ends it, with one final `KeyState.Unreadable`". A `RuntimeException` from the DataStore (for example `IllegalStateException`) therefore kills the collector in `observe`, while the same failure is a quiet `storage_unreadable` in `read`. Separately, catching `RuntimeException` in `read` hides integrator programming errors. The well-known case is "There are multiple DataStores active for the same file", which D-02 and `verifyNoDataStoreCreation` exist to prevent. Such a bug is reported as a generic unreadable state with no trace.
**Fix:** Choose one policy and apply it in both paths. The safest one is to map only `IOException` (DataStore documents that `CorruptionException` extends it) and let programming errors propagate:
```kotlin
private suspend fun storedPreferences(): Preferences? = try {
    dataStore.data.first()
} catch (ignored: IOException) {
    null
}
```
If `RuntimeException` mapping is deliberately kept so `KeystoreCredentialSource` never throws, add the same clause to `observeSlot` (after rethrowing `CancellationException`). Update `UnreadableMappingTest.aDataStoreThatFailsToReadReadsStorageUnreadable` and `ApiKeyStoreObserveTest` to cover the chosen behaviour in both APIs.

### WR-03: `observe` re-emits and re-decrypts on every unrelated write to the app's shared DataStore

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt:109-112`
**Issue:** The injected `DataStore<Preferences>` is the app's whole preferences file (SecondBrain's hoisted `app_preferences`, CalTracker's `caltracker_prefs`). `dataStore.data` emits on any write, including theme, counters and so on. Each emission runs `reader.open`, which means a `KeyStore.getInstance`, a key lookup and an AES-GCM decrypt, and then emits an equal `KeyState`. The KDoc says it "emits again after every save and delete", but it also emits after every unrelated write. A consumer UI cannot tell an unrelated write from a real replacement. The `distinctUntilChanged` was probably left out so that a same-last-4 replacement still emits (`aReplacedKeyWithTheSameLastFourStillEmits`). CalTracker's existing `hasApiKey` uses `distinctUntilChanged` for the same reason.
**Fix:** Dedupe on the stored pair, not on the resulting state. A fresh IV per save guarantees that a real replacement changes the pair:
```kotlin
private fun observeSlot(slot: KeySlot): Flow<KeyState> = dataStore.data
    .map { prefs ->
        prefs[stringPreferencesKey(slot.ciphertextKey)] to prefs[stringPreferencesKey(slot.ivKey)]
    }
    .distinctUntilChanged()
    .map { reader.open(slot, it.first, it.second).state }   // or keep passing prefs
    .catch { ... }
    .flowOn(ioDispatcher)
```
Add a test that an unrelated `edit` produces no emission and no `existingKey` lookup.

### WR-04: `RuntimeException` is caught in three places, and the variable name `ignored` is what gets it past detekt

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt:46` and `:70`; `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt:129`
**Issue:** The project rule is that the never-throw collapse lives in one internal helper and carries the repo's only justified `@Suppress`, and everywhere else "must catch specific types". Here `catch (ignored: RuntimeException)` appears at three sites with no suppression and no tuned rule. detekt's default `allowedExceptionNameRegex` (`_|(ignore|expected).*`) exempts any exception variable named `ignored` from `TooGenericExceptionCaught` and `SwallowedException`. This bypasses the zero-baseline policy by naming. These blocks also swallow NPE, ClassCast and similar bugs as legitimate unreadable causes with no diagnostic (see WR-01 and WR-02). Note that the `:keystore` module has no tuned detekt config for this.
**Fix:** Catch the specific platform types (`GeneralSecurityException`, `ProviderException`, `IOException`, `IllegalArgumentException`) and drop the blanket `RuntimeException` clauses. If one blanket collapse is genuinely required for the "never throws" contract of `KeystoreCredentialSource`, route it through the single sanctioned helper with the documented `@Suppress`. Do not rely on the variable name.

### WR-05: `AndroidKeyStoreKeyAccess` KDoc claims a cross-code locking guarantee it cannot provide

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt:19-21` (also `KeyAccess.kt:17-19`, "atomic across the whole process")
**Issue:** The KDoc says the creation lock "is shared by every store in the process: two stores, or a store and older code in the same process, cannot both generate a key for one alias." The lock is a private `Any()` inside this object. Older SecondBrain and CalTracker code serialises on its own `@Synchronized` monitor (the `KeystoreCrypto` instance, verified in both repos), and neither takes this lock. During the migration window, where old `KeystoreCrypto` and the library coexist, a concurrent first save under the same alias from both sides can still generate two keys. The second key orphans the first side's ciphertext, which is exactly the failure the comment says is prevented. Creation is also not atomic across processes. Overstated safety claims in a security-sensitive KDoc mislead the migration plans.
**Fix:** Reword to what is true ("serialises key creation among stores of this library in one process; code outside the library, and other processes, are not covered"). In the Wave-1 migration guidance, require that an alias is owned by exactly one writer, either the old code or the library, at any time.

### WR-06: The TESTER runner can report "test package removed" when the device is unreachable

**File:** `scripts/run-keystore-instrumented.sh:47-56`
**Issue:** `cleanup` runs `adb_t ... uninstall ... || true` and then checks `adb ... shell pm list packages | strip_cr | grep -qxF "package:$TEST_PKG"`. If the device dropped off the bus (wireless fallback, USB reset), `pm list packages` prints nothing, `grep -q` fails, and the script prints `test package removed` even though the uninstall never happened. The test APK stays on the shared Gate-1 device, and the log asserts the opposite. This is the one place the script asserts post-run device state.
**Fix:** Check the device state and the uninstall result explicitly:
```bash
if ! adb_t 60 -s "$TARGET" uninstall "$TEST_PKG" >/dev/null 2>&1; then
  echo "WARNING: could not uninstall $TEST_PKG from $TARGET (device unreachable or package absent)"
elif adb_t 30 -s "$TARGET" shell pm list packages 2>/dev/null | strip_cr | grep -qxF "package:$TEST_PKG"; then
  echo "WARNING: test package $TEST_PKG is still installed on $TARGET"
else
  echo "test package removed"
fi
```
Only print "removed" after a positive confirmation (for example require `pm list packages` to succeed with non-empty output).

## Info

### IN-01: `save` KDoc documents only `IllegalArgumentException`, but callers must expect much more

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt:54-60`
**Issue:** By design (class doc, `PlatformKeyAccessJvmTest`) `save` fails loudly with `GeneralSecurityException` subclasses, `ProviderException`, or an `IOException` from the DataStore edit. None of this is in the contract. A ViewModel that catches only `IllegalArgumentException` would crash. Since the API is frozen additively after v1.0.0, the documented contract matters.
**Fix:** Add `@throws java.security.GeneralSecurityException`, `@throws java.io.IOException` and a note about `ProviderException` to the KDoc, so the app knows to surface "could not store key" to the user.

### IN-02: `Stage` monad and an unreachable branch add complexity

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt:84-99`; `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSource.kt:31-37`
**Issue:** The `Stage`/`proceed`/`halt` machinery, with `checkNotNull(value)` on a nullable that is invariant by construction, exists only to avoid detekt's `ReturnCount`/`ThrowsCount`. The same logic as sequential `?: return` steps is shorter and easier to audit. In `KeystoreCredentialSource.readyLookup`, `plaintext == null` is documented as unreachable ("only keeps the mapping total"). `SecretRead(state, plaintext)` does not enforce the Ready-implies-plaintext invariant in its type.
**Fix:** Make `SecretRead` a small sealed hierarchy (`Plain(text)` / `Failed(state)`), which removes the null branch and the `Stage` wrapper.

### IN-03: Device serial and tailnet addresses are hard-coded in committed scripts of a public library repo

**File:** `scripts/run-keystore-instrumented.sh:18-21`; `scripts/verify-keystore-device-guard.sh:9,37-38,96-97,107`
**Issue:** The TESTER's USB serial (`R5CT10XNKQN`), its tailnet address and the personal phone's tailnet address are committed to a repository that is published through JitPack. This is infrastructure disclosure of personal devices, and it makes a reusable-library repo machine-specific. The runner's safety property ("never the personal phone") is also a literal substring check. The script only prints the foreground activity and does not stop if a person is driving the device, so the "one device-tester at a time" rule relies on the host lock alone.
**Fix:** Move the identifiers to an untracked, host-local file or env (for example `~/.config/vae/tester.env`) loaded by both scripts, and keep only the guard logic in the repo. Consider aborting (INFRA) when the screen is on with a non-launcher foreground app.

### IN-04: `LegacyDeviceCrypto.kt` duplicates ~50 lines between the two stand-ins

**File:** `keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyDeviceCrypto.kt:28-65` vs `:79-116`
**Issue:** `SbLegacyCrypto` and `CtLegacyCrypto` differ only in the Base64 implementation, but the whole cipher and key-creation block is copy-pasted. Stated intent is "verbatim copies", and the two apps' sources do differ cosmetically, so this is defensible. A shared private helper parameterised by an encoder would still keep the copies honest and cut the maintenance surface.
**Fix:** Extract one private `LegacyCrypto` core and keep thin `Sb`/`Ct` wrappers that only choose the Base64 flavour.

### IN-05: Runner hides Gradle diagnostics and has no EXIT cleanup trap

**File:** `scripts/run-keystore-instrumented.sh:58,122`
**Issue:** `./gradlew ... -q` swallows the build error, so `reason=build_failed` leaves the operator with nothing to diagnose. The `trap` covers only `INT` and `TERM`. A `SIGHUP` (SSH drop, which is the normal way this host is used) or a `set -u` unbound-variable exit after install leaves the APK on the TESTER with no cleanup.
**Fix:** Drop `-q` or tee the Gradle output to `$LOG_DIR`. Extend the trap to `HUP` and add an `EXIT` trap that calls `cleanup` when `INSTALLED=1`.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
