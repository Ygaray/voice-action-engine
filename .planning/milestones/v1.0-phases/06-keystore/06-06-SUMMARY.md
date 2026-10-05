---
phase: 06-keystore
plan: 06
subsystem: keystore
tags: [androidkeystore, key-custody, public-constructors, instrumented-test]
requires: [06-05]
provides:
  - AndroidKeyStoreKeyAccess, internal platform key custody: getKey lookup and process-global get-or-create
  - ApiKeyStore(dataStore, slots) and ApiKeyStore(dataStore, slots, ioDispatcher), the two public constructors
  - PlatformKeyAccessJvmTest, the JVM proof that a platform lookup failure is Unreadable and never a new key
  - KeystoreDeviceTest and LegacyDeviceCrypto, the instrumented class for the TESTER run in 06-07
affects: [06-07]
tech-stack:
  added: []
  patterns: [getKey-only lookup that propagates, one process-wide creation lock inside an object, secondary constructors without defaults over an internal primary]
key-files:
  created:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/PlatformKeyAccessJvmTest.kt
    - keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyDeviceCrypto.kt
    - keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreDeviceTest.kt
  modified:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
key-decisions:
  - "Helpers of the instrumented class live in a private DeviceHarness class and top-level functions so KeystoreDeviceTest stays under the repo's TooManyFunctions threshold without any suppression"
  - "encryptToPair returns (ciphertext, nonce) so it lines up with decryptPair(alias, ctB64, ivB64)"
requirements-completed: [KEY-02, KEY-03, KEY-04]
status: complete
plan_head_before: b5a1c6ab7ec14f69578fb27a701f322beb4db384
commits: 2
actuals:
  tokens: 20000
  tasks: 2
  commits: 2
duration: 30m
completed: 2026-10-01
---

# Phase 6 Plan 6: Platform key custody and the TESTER device leg Summary

`ApiKeyStore` now has its two public constructors over the real AndroidKeyStore. Keys are looked up with `getKey` and created only under one process-wide lock with the ports' exact spec. The instrumented class that proves it on hardware is authored, built and linted; 06-07 runs it.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: public constructors wire the AndroidKeyStore access; a JVM lookup failure is Unreadable, never a new key | bcb8dcc |
| 2 | TESTER instrumented class with verbatim SecondBrain and CalTracker legacy writers | 69c6301 |

## What was built

- `AndroidKeyStoreKeyAccess` (internal object, the only main file touching `android.security.keystore`). `existingKey` loads the AndroidKeyStore and returns `getKey(alias, null)`: null is the only absent signal, a non-`SecretKey` throws `UnrecoverableKeyException` with a fixed message, nothing is caught. `getOrCreateKey` is `synchronized(creationLock) { existingKey(alias) ?: generate(alias) }` with a private lock shared by every store in the process. The spec is AES, 256 bits, GCM, no padding, encrypt plus decrypt, randomized encryption left on, no authentication binding, no StrongBox. Code-line grep for the entry-based lookup, the alias-presence query, auth binding, StrongBox and key deletion over main is empty.
- `ApiKeyStore`: `public constructor(dataStore, slots)` (Dispatchers.IO) and `public constructor(dataStore, slots, ioDispatcher)`, both delegating to the internal primary with `AndroidKeyStoreKeyAccess`. No default arguments; `KeystoreApiShapeTest` (7 tests) still passes. Class KDoc gained the usage paragraph (inject the app's DataStore and a verbatim slot table; nothing is copied or migrated).
- `PlatformKeyAccessJvmTest` (3 tests): `aStoredPairReadsUnreadableBecauseTheKeyStoreCannotBeQueried`, `aSaveFailsLoudlyWithoutTheKeyAndLeavesStorageUnchanged`, `aNeverStoredProviderStillReadsNotConfigured`. On the plain JVM there is no AndroidKeyStore provider, so a seeded pair reads `Unreadable("keystore_unavailable")`, the adapter answers `CredentialLookup.Unreadable("keystore_unavailable")`, the DataStore snapshot is unchanged after the failed save, and a never-stored provider reads NotConfigured.
- Exception type the JVM save threw: a `GeneralSecurityException` (the test catches that type; on a JDK it is the `KeyStoreException` raised by `KeyStore.getInstance("AndroidKeyStore")`). No message or cause message in the chain contains the key.
- `LegacyDeviceCrypto.kt`: `SbLegacyCrypto` (verbatim SecondBrain crypto, `java.util.Base64`) and `CtLegacyCrypto` (verbatim CalTracker crypto, framework `Base64` `NO_WRAP`), each with `encryptToPair(alias, plaintext)` and `decryptPair(alias, ctB64, ivB64)`. Both keep the apps' original entry-based lookup on purpose (they stand in for the old code); DI annotations and planning comments were dropped; the file KDoc names both sources and the copy date 2026-10-01.
- `KeystoreDeviceTest` (`@RunWith(AndroidJUnit4::class)`, 7 `@Test`), exact method names: `secondBrainLegacyBlobReadsBack`, `calTrackerLegacyBlobsReadBack`, `storeWritesLegacyCodeReads`, `frameworkBase64MatchesJavaBase64`, `deletedDeviceKeyReadsKeyMissingAndCreatesNothing`, `concurrentFirstUseKeepsOneKey`, `tamperedCiphertextReadsDecryptFailed`. Each test builds its own DataStore in the target context's `cacheDir` and uses the public `ApiKeyStore(dataStore, slots)` constructor. The four literal legacy aliases and every `vae_device_test_<uuid>` alias are deleted in `@Before` and `@After`. The concurrency test uses a `CyclicBarrier(8)` over an 8-thread pool, eight stores, eight DataStore files, one fresh alias.
- androidTest APK produced: `keystore/build/outputs/apk/androidTest/debug/keystore-debug-androidTest.apk`.

## Verification (plan-level only)

- `./gradlew :keystore:testDebugUnitTest --tests '*PlatformKeyAccessJvmTest' --tests '*KeystoreApiShapeTest' --offline -q`: pass (3 + 7 tests, 0 failures).
- `./gradlew :keystore:detekt :keystore:scanBannedConstructs :keystore:verifyExplicitApiStrict --offline -q`: pass.
- `./gradlew :keystore:assembleDebugAndroidTest :keystore:detekt --offline -q`: pass.
- `./gradlew :keystore:check :core:check --offline -q`: exit 0.
- All Task 2 acceptance greps hold: 7 `@Test`, all four literal aliases present, `CyclicBarrier` and `@After` present, no planning ids or DI annotations in androidTest, APK exists. `git log b5a1c6a..HEAD -- core/ providers/` is empty.
- No adb command was run and no device was touched; the device run is 06-07.

## Deviations from Plan

None. One mid-task adjustment inside the plan's intent: the first draft of the instrumented class exceeded the repo's `TooManyFunctions` limit (12 per class), so its helpers moved into a private `DeviceHarness` class and top-level private functions rather than being suppressed.

## Deleted files

None.

## Self-Check: PASSED

- FOUND: AndroidKeyStoreKeyAccess.kt, PlatformKeyAccessJvmTest.kt, LegacyDeviceCrypto.kt, KeystoreDeviceTest.kt, the androidTest APK
- FOUND commits: bcb8dcc, 69c6301
