---
status: complete
result: all_pass
gate: 1
phase: 06-keystore
source: [06-ROADMAP success criteria 1-4]
device: SM-S908U Galaxy S22 Ultra, the TESTER yahirs-s22-ultra-2 (USB serial R5CT10XNKQN, Android 15 / sdk 35)
apk: keystore-debug-androidTest.apk (md5 6ba7a5a27c1bb3edc7b274197095390e @ 8d9382d)
run: 2026-10-01T12:05:32Z
---

<!--
Gate-1 self-UAT for a library phase with no UI. The target surface is the real AndroidKeyStore + a real file-backed
DataStore on the TESTER, driven ONLY through the guarded runner scripts/run-keystore-instrumented.sh. No project
AGENT-*-TESTING.md exists; the global AGENT-DEVICE-TESTING.md template's device rules plus ~/.claude/context/devices/
{common,test-android}.md were followed (explicit adb -s, TESTER only, personal phone never touched).
-->

# Self-UAT Log: Phase 6 (Keystore), plan 06-07 device half

**Device:** SM-S908U, TESTER (R5CT10XNKQN, Android 15 / sdk 35)   **APK:** keystore-debug-androidTest.apk (md5 `6ba7a5a27c1bb3edc7b274197095390e` @ `8d9382d`)   **Run:** 2026-10-01T12:05:32Z
**Schema:** n/a (Preferences DataStore, no migration)   **Pre-flight:** `adb devices` listed the TESTER on USB and wireless; runner confirmed ro.serialno == R5CT10XNKQN, model SM-S908U, sdk 35 >= 35. No other runner or agent holding `/run/user/1000/vae-keystore-tester.lock`; the foreground app was the idle yahirandroidtasteharness Explorer (same as the earlier run), nobody driving.
**Unit suite:** `./gradlew :keystore:testDebugUnitTest --offline` -> 96 tests, 0 failures, 0 errors, 0 skipped (UP-TO-DATE vs HEAD).   **Coverage/Nyquist:** 06-VALIDATION.md / COVERAGE.md exist from the phase; not re-derived here (not the Gate-1 question).
**Seed/fixture integrity:** the test package `io.github.ygaray.voiceactionengine.keystore.test` has its own AndroidKeyStore namespace, so the literal SB/CT alias strings never touch SecondBrain or CalTracker keys; `cleanBefore`/`cleanAfter` delete every alias used. Each test builds its own temp file DataStore under the test cache dir. Seeding is programmatic and in-test (legacy writers write straight into the DataStore); there is no UI. Post-run `pm list packages | grep -c voiceactionengine` -> 0, runner printed `test package removed`.

**Build identity:** HEAD `8d9382d0b612`; androidTest APK built 05:56:10 -0600, after the last `keystore`/`scripts` commit (d0fcdc7, 05:54:19 -0600); `:keystore:assembleDebugAndroidTest` is UP-TO-DATE against HEAD. This run therefore exercises WR-01..06 and IN-01/02/05, which the earlier record (`evidence/keystore-instrumented-run.txt`, 11:44Z @ 16c83fb) predates.

**Command:** `bash scripts/run-keystore-instrumented.sh` -> exit 0, `KEYSTORE_INSTRUMENTED: PASS tests=7 target=R5CT10XNKQN model=SM-S908U sdk=35`. Verbatim in `evidence/keystore-instrumented-run-gate1.txt`.

**Limitation on per-test attribution (honest):** the sanctioned runner prints one dot per test (`.......`) and `OK (7 tests)`; it does not print per-test names. `KeystoreDeviceTest` has exactly 7 `@Test` methods (read from source), `OK (7 tests)` means all 7 passed, and the runner requires N >= 7. The mapping below is therefore by source reading plus the all-7-passed count, not by named result lines. Same limitation as the earlier record.

## Criteria

### 1. SC1 - store, read, delete a key per provider; AES/GCM; persisted in the app's own DataStore<Preferences>; no second DataStore
result: passed
- **Rung:** 3 (instrumented run on the real AndroidKeyStore + real file DataStore), backed by rung 1 for the delete half.
- **Target:** device (TESTER) for store + read with real AES/GCM; headless JVM for delete and the no-second-DataStore gate.
- **Expected:** a consumer can save a key, read it back, and delete it per provider; ciphertext is produced by AndroidKeyStore AES/GCM and persisted in a caller-owned `DataStore<Preferences>`.
- **Arranged (seeded):** a fresh `PreferenceDataStoreFactory.create` file DataStore per test (caller-owned, passed into `ApiKeyStore`); device keys cleared before each test.
- **Did (drove):** `storeWritesLegacyCodeReads` saves via `ApiKeyStore.save` for SB (1 slot) and CT (3 slots); `concurrentFirstUseKeepsOneKey` saves with 8 stores and reads each back as `KeyState.Ready(last4)`; `tamperedCiphertextReadsDecryptFailed` saves, flips one ciphertext byte in the DataStore, and expects `Unreadable("decrypt_failed")` (falsifier: proves the stored blob is really GCM-authenticated, not plaintext/base64). `storeWritesLegacyCodeReads` additionally pulls the stored ciphertext+iv pair out of the DataStore and decrypts it with the verbatim legacy code on the real Keystore key, which is only possible if the store persisted real AES/GCM ciphertext under the slot's DataStore keys.
- **Observed:** all 7 passed. The delete leg is NOT exercised by `KeystoreDeviceTest` (its `deleteEntry` calls remove the Keystore key, not a saved API key); it is settled at rung 1 by `ApiKeyStoreTest.aThreeProviderStoreKeepsEachKeySeparateAcrossSaveReplaceAndDelete` (JVM suite 96/0 failures this run). The no-second-DataStore property is the JVM build gate from 06-01, not a device matter.
- **Evidence:** `.planning/phases/06-keystore/evidence/keystore-instrumented-run-gate1.txt`; `keystore/src/androidTest/.../KeystoreDeviceTest.kt` (storeWritesLegacyCodeReads, concurrentFirstUseKeepsOneKey, tamperedCiphertextReadsDecryptFailed); JVM `ApiKeyStoreTest.kt:97`.

### 2. SC2 - SB/CT legacy aliases and DataStore keys: values written by the legacy writers read back unchanged, and vice versa
result: passed
- **Rung:** 3 (device).
- **Target:** device (TESTER), real AndroidKeyStore with the literal legacy aliases (`secondbrain_anthropic_api_key_v1`, `caltracker_api_key_v1`, `caltracker_openai_api_key_v1`, `caltracker_openrouter_api_key_v1`) in the test package's namespace.
- **Expected:** a blob written by SecondBrain's or CalTracker's existing crypto under its existing alias and DataStore key reads back unchanged via the engine, and a blob the engine writes is decryptable by the legacy code.
- **Arranged (seeded):** verbatim copies of SB's and CT's crypto (`SbLegacyCrypto`, `CtLegacyCrypto` in `LegacyDeviceCrypto.kt`) write ciphertext+iv straight into the DataStore under the legacy slot keys.
- **Did (drove):** `secondBrainLegacyBlobReadsBack` (SB legacy write -> `store.read` == `Ready("AB12")` and `credential()` yields the exact full key); `calTrackerLegacyBlobsReadBack` (three CT providers, same assertions); `storeWritesLegacyCodeReads` (engine `save` -> `SbLegacyCrypto.decryptPair` / `CtLegacyCrypto.decryptPair` returns the exact original strings, four aliases); `frameworkBase64MatchesJavaBase64` (0..40-byte sweep plus the `+`/`/` edge bytes: `android.util.Base64 NO_WRAP` equals `java.util.Base64` encoding and round-trips, the format-compat claim the legacy blobs depend on).
- **Observed:** all 7 passed, covering both directions for SB and CT plus the Base64 equivalence. Full-key equality (not just last4) is asserted through the credential seam.
- **Evidence:** run log above; `KeystoreDeviceTest.kt` and `LegacyDeviceCrypto.kt`.

### 3. SC3 - read states; deleted device key reads KeyMissing and creates no key; one key under concurrent first use
result: passed
- **Rung:** 3 (device).
- **Target:** device (TESTER), real AndroidKeyStore (`KeyStore.getKey(alias, null)` as the oracle).
- **Expected:** after the Keystore key is gone (restored-backup shape) read reports `KeyMissing` and NEVER creates a key; eight concurrent first-use saves end with exactly one working key.
- **Arranged (seeded):** `store.save` creates the key and blob; then `deleteEntry(SB_ALIAS)` removes only the device key, leaving the blob (the restored-backup state, which has no UI path).
- **Did (drove):** `deletedDeviceKeyReadsKeyMissingAndCreatesNothing`: `store.read` == `KeyState.KeyMissing()`, `getKey(SB_ALIAS)` is null, `KeystoreCredentialSource.credential` == `CredentialLookup.Unreadable("key_missing")`, `getKey` still null (no key created by either read path). `concurrentFirstUseKeepsOneKey`: 8 separate `ApiKeyStore` instances on 8 DataStores, same fresh alias, released together by a `CyclicBarrier`; afterwards every store's own blob decrypts to its own key. If two keys had been created, an earlier racer's blob would fail to decrypt, so the all-eight-Ready assertion is a real falsifier. `tamperedCiphertextReadsDecryptFailed` gives the `Unreadable("decrypt_failed")` state. `NotConfigured` (no blob) and the remaining state matrix are JVM-covered (`KeyStateTest`, `ReadNeverCreatesKeyTest`, `UnreadableMappingTest`), including the post-review WR-01..03 error mapping on the JVM leg.
- **Observed:** all 7 passed on the post-review HEAD. The earlier record predates WR-01 (cipher/provider failures now map to `keystore_unavailable`, not `decrypt_failed`); the tamper test still yields `decrypt_failed` after that change, as expected for a genuine GCM tag failure.
- **Evidence:** run log above; `KeystoreDeviceTest.kt` (deletedDeviceKeyReadsKeyMissingAndCreatesNothing, concurrentFirstUseKeepsOneKey, tamperedCiphertextReadsDecryptFailed).

### 4. SC4 - KeystoreCredentialSource plugs into the provider seam; JVM round trip through the crypto seam; one instrumented test passes on the TESTER
result: passed
- **Rung:** 3 (device) + 1 (JVM).
- **Target:** device (TESTER) for the device half; headless JVM for the crypto-seam round trip.
- **Expected:** a saved key reaches the provider seam as a `CredentialLookup.Present` with the real key; the instrumented suite passes on the TESTER.
- **Arranged (seeded):** as SC2/SC3.
- **Did (drove):** device: the guarded runner ran the full `KeystoreDeviceTest` class on the TESTER (`OK (7 tests)`). `KeystoreCredentialSource(store).credential(provider)` is exercised on hardware in `secondBrainLegacyBlobReadsBack` and `calTrackerLegacyBlobsReadBack` (Present with the exact full key, stamped with the right provider) and in `deletedDeviceKeyReadsKeyMissingAndCreatesNothing` (Unreadable `key_missing`). JVM: `KeystoreCredentialSourceTest.aSavedKeyIsPresentAndStampedWithTheAskedProvider` and `KeystorePipelineTest` cover save -> source -> pipeline through the crypto seam (96/0 this run).
- **Observed:** PASS. One observed gap, recorded for honesty and not a defect: no single device test performs `ApiKeyStore.save` immediately followed by `KeystoreCredentialSource.credential` == Present. The two halves are each proven on hardware (save -> Ready/legacy-decrypt in `storeWritesLegacyCodeReads` and `concurrentFirstUseKeepsOneKey`; legacy-written store -> Present in tests 1 and 2) on the same classes, and the composite is JVM-proven. The ROADMAP wording ("one instrumented test passes on the TESTER") is satisfied. Optional hardening for gap-closure-free backlog: add a one-line `save -> presentKey` assertion to a device test.
- **Evidence:** run log above; `KeystoreCredentialSourceTest.kt:61`.

## Summary

total: 4
passed: 4
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- The run is a re-confirmation of the earlier 06-07 device record on a newer HEAD: same 7 tests, `OK (7 tests)`, 0.86 s vs 0.905 s. The earlier record was audited as a claim; its own attached output (`OK (7 tests)`, `test package removed`, runner PASS) contains no contradicting defect. Its per-test "names" were inferred from the dot count, the same inference made here (see limitation above).
- Device state: runner installs the test APK and removes it again; `pm list packages | grep -c voiceactionengine` is 0 afterward. No SecondBrain / CalTracker app or key was touched (separate Keystore namespace by package). Airplane mode was not used. The personal phone (100.126.94.47) was never addressed.
- `adb shell pm list packages` prints a non-fatal `SecurityException ... user 150` (Secure Folder quirk noted in test-android.md); harmless, and the runner's own positive listing check still produced `test package removed`.
- No physical or human step exists for this phase: it ships a library with no UI, and the live-provider key use belongs to Phase 10. Gate-2 is registered as a sign-off-only fragment.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 4 criteria PASS on the real AndroidKeyStore on the TESTER at HEAD 8d9382d. Gate-1 complete; human Gate-2 deferred to milestone completion (registered as `.planning/uat-pending/06-keystore.md`).
