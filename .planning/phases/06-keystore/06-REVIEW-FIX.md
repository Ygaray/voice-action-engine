---
phase: 06-keystore
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/06-keystore/06-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 9
skipped: 2
status: resolved
---

# Phase 6: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/06-keystore/06-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (0 critical, 6 warning, 5 info; fix_scope = all)
- Fixed: 9
- Skipped: 2 (both documented acceptable-skips, see below)

**Verification:** per-fix `./gradlew :keystore:detekt :keystore:testDebugUnitTest --offline` (green each time);
final `./gradlew check --offline` in the main checkout (no worktree) is BUILD SUCCESSFUL, which includes detekt
(zero baseline, no new `@Suppress`, no config change), unit tests, Metalava compatibility and lint. The script fixes
were checked with `bash -n` and the offline fake-adb guard `scripts/verify-keystore-device-guard.sh`
(`DEVICE GUARD OK`); no adb command was run against a device. The five cause codes and the golden/legacy-compat tests
are untouched.

## Fixed Issues

### WR-01: Transient keystore failures during cipher init or `doFinal` are classified as `decrypt_failed`

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt`, `keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt`
**Commit:** b881d4e
**Applied fix:** `decrypt` now maps only `BadPaddingException` (covers `AEADBadTagException`) and `IllegalBlockSizeException` to `decrypt_failed`; any other `GeneralSecurityException` and `ProviderException` map to `keystore_unavailable`. Added a test with a wrong-length key and a key whose `getEncoded` throws `ProviderException`. Logic change, so: fixed, requires human verification of the classification on a real device (the TESTER run was not repeated by this agent).

### WR-02: `observe` and `read` disagree on which storage failures are mapped

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSource.kt`, `keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt`
**Commit:** 125906d
**Applied fix:** Chose the reviewer's safer policy: both paths map only `IOException` (incl. `CorruptionException`) to `storage_unreadable`; a misuse such as `IllegalStateException` propagates from `read` and `observe` alike (the engine's `guarded` wrapper already turns a throwing credential source into a credential-source fault). Updated the `read`/`KeystoreCredentialSource` KDoc and the unreadable-mapping test; added a test that both APIs throw for a misuse. Logic change: fixed, requires human verification.

### WR-03: `observe` re-emits and re-decrypts on every unrelated write to the shared DataStore

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt`, `keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreObserveTest.kt`
**Commit:** 957a0a3
**Applied fix:** `distinctUntilChangedBy` on the slot's stored (ciphertext, iv) pair before decrypting; a fresh IV per save keeps same-last-four replacements emitting. New test: an unrelated `edit` emits nothing and costs no extra key lookup. Logic change: fixed, requires human verification.

### WR-04: `RuntimeException` caught in three places, passing detekt only by the name `ignored`

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt` (plus the decrypt site in the WR-01 commit and the storage site in the WR-02 commit)
**Commit:** 5875026 (lookup site; decrypt site in b881d4e, `storedPreferences` site in 125906d)
**Applied fix:** No `RuntimeException` catch remains in main sources. The key lookup catches `GeneralSecurityException` and `ProviderException`; decrypt and storage were narrowed as above. No new `@Suppress`, no detekt config change. The remaining `ignored` names are on specific exception types, which is detekt's intended idiom for a deliberate swallow.

### WR-05: `AndroidKeyStoreKeyAccess` KDoc claims a cross-code locking guarantee it cannot provide

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt`
**Commit:** 267b4dc
**Applied fix:** Reworded both KDocs: the lock serialises creation among stores of this library in one process; outside code and other processes are not covered, so an alias must have exactly one writer at a time. (The Wave-1 migration guidance mentioned in the review lives in the control plane / consumer repos and was not touched here; the orchestrator should carry the "one writer per alias" rule into the migration plans.)

### WR-06: The TESTER runner can report "test package removed" when the device is unreachable

**Files modified:** `scripts/run-keystore-instrumented.sh`
**Commit:** 89b0e87
**Applied fix:** `cleanup` takes the post-uninstall package listing and prints "test package removed" only when the listing is non-empty and does not contain the package; an empty listing prints a "could not verify" warning, a listing that still has it prints the existing warning. Static edit; `bash -n` and the offline guard script pass.

### IN-01: `save` KDoc documents only `IllegalArgumentException`

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt`
**Commit:** d0fcdc7
**Applied fix:** Added `@throws` for `GeneralSecurityException`, `ProviderException` and `IOException`, plus a note that callers should surface "could not store the key". Documentation only.

### IN-02: `Stage` monad and an unreachable branch add complexity

**Files modified:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSource.kt`
**Commit:** 15cd072
**Applied fix:** (Partial, by design.) `SecretRead` is now an internal sealed type (`Plain` carries the key and a `Ready` state, `Failed` carries no key), which removes the null-plaintext branch in `KeystoreCredentialSource`. The `Stage` helper was kept: replacing it with sequential early returns would exceed detekt's default `ReturnCount` and the zero-baseline rule forbids tuning that away for style. `state`/`plaintext` accessors are unchanged, so existing tests are unaffected.

### IN-05: Runner hides Gradle diagnostics and has no EXIT cleanup trap

**Files modified:** `scripts/run-keystore-instrumented.sh`
**Commit:** c4daf60
**Applied fix:** Gradle output now goes to `keystore/build/device-run/gradle.log` and its last 40 lines are echoed on failure; the signal trap now covers `HUP` and an `EXIT` trap quietly removes the APK if the script dies after install without finishing (a no-op after a normal `finish`). Static edit; `bash -n` and the offline guard script pass.

## Skipped Issues

### IN-03: Device serial and tailnet addresses are hard-coded in committed scripts

**File:** `scripts/run-keystore-instrumented.sh:18-21`, `scripts/verify-keystore-device-guard.sh`
**Reason:** Acceptable-skip. The literal TESTER USB serial / tailnet address and the personal-phone substring are the deliberate identity guard (never substitute a device, never touch the personal phone), and the offline guard script asserts those exact literals. Moving them to a host-local env file would weaken the guard (a missing or altered file could change the target) and conflicts with the runner's stated "no option, argument or environment variable changes the target" property. The "abort when someone is driving the device" suggestion is a process-policy change beyond a code fix (the host lock plus the one-tester-at-a-time rule is the sanctioned control). Any relocation of identifiers for the public repo should be decided by the orchestrator/user.
**Original issue:** Personal device identifiers are committed to a published reusable-library repo.

### IN-04: `LegacyDeviceCrypto.kt` duplicates ~50 lines between the two stand-ins

**File:** `keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyDeviceCrypto.kt:28-65` vs `:79-116`
**Reason:** Acceptable-skip. The two objects are documented verbatim copies of each app's `KeystoreCrypto.kt` and play the old code on purpose; each carries its own `@Synchronized` monitor, which mirrors the real apps' separate instances. Merging them would alter code that was device-verified on the TESTER (7 tests passing) and this run may not touch a device to re-verify.
**Original issue:** Cipher and key-creation block copy-pasted between `SbLegacyCrypto` and `CtLegacyCrypto`.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
