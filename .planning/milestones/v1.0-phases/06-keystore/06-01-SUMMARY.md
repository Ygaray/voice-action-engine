---
phase: 06-keystore
plan: 01
subsystem: keystore
tags: [aes-gcm, datastore, androidkeystore, gradle-gates]
requires: []
provides:
  - ApiKeyStore save/read over an injected DataStore and a KeySlot table
  - KeyState four-state result (NotConfigured, Ready, KeyMissing, Unreadable)
  - internal KeyAccess seam and the one AesGcm cipher path
  - verifyDatastoreIsApi and verifyNoDataStoreCreation gates under check
  - androidTest toolchain built and linted by check
affects: [06-02, 06-03, 06-04, 06-05, 06-06, 06-07]
tech-stack:
  added: [androidx.test:runner 1.7.0, androidx.test.ext:junit 1.3.0]
  patterns: [open abstract class with final leaves, internal seam with software test double, publication-file check task]
key-files:
  created:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlot.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyState.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcm.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreTestSupport.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreTest.kt
  modified:
    - keystore/build.gradle.kts
    - gradle/libs.versions.toml
    - build.gradle.kts
    - scripts/verify-negative-controls.sh
  deleted:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreModule.kt
key-decisions:
  - "KeyState leaves are final classes (not objects) with hand-written equals, matching CredentialLookup; callers write KeyState.NotConfigured()"
  - "read() splits the decrypt step into a private open() helper to satisfy detekt ReturnCount without a suppression"
requirements-completed: [KEY-01, KEY-02]
status: complete
plan_head_before: 7056c2ac5200e278b8e6f386f73ca2f5c2b279f3
commits: 2
actuals:
  tokens: 35000
  tasks: 2
  commits: 2
duration: 25m
completed: 2026-10-01
---

# Phase 6 Plan 1: Keystore tracer and build wiring Summary

A key saved for one provider is sealed through the shared AES/GCM path into an injected, real temp-file DataStore and reads back as `KeyState.Ready(last4)`, and the build now guards that DataStore stays a compile-scope dependency and is never created by the library.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: save then read through AesGcm and a real DataStore | 7f41e63 |
| 2 | Build wiring: api gate, no-DataStore-creation gate, androidTest toolchain, negative controls | 43f3a92 |

## What was built

- `AesGcm.seal` has no IV parameter (the provider generates the 12-byte nonce, read back from the cipher); `open` uses `GCMParameterSpec(128, iv)`. Stored as two standard padded unwrapped `java.util.Base64` strings under the slot's two pref keys, written in one `dataStore.edit`.
- `read` never calls `getOrCreateKey`: a null `existingKey` gives `KeyMissing`. The tracer asserts `getOrCreateCalls` stays at 1 across a read.
- Tracer test runs inside `NoNetworkGuard.during`, which proves `testFixtures(project(":core"))` resolves on the unit-test classpath. 3 tests, 0 failures.
- `verifyDatastoreIsApi` parses `pom-default.xml` (scope must be `compile`) and `module.json` (every `java-api` variant must list `datastore-preferences`). `verifyNoDataStoreCreation` scans main sources. Both plus `assembleDebugAndroidTest` run under `check`.
- `src/androidTest/kotlin` added to the root detekt source list. minSdk 35 and compileSdk 36.1 unchanged.

## module.json variants found

`releaseVariantReleaseApiPublication` (java-api), `releaseVariantReleaseRuntimePublication` (java-runtime), `releaseVariantReleaseSourcePublication` (java-runtime, no dependencies). Both the api and runtime variants list `voice-action-engine-core`, `datastore-preferences` and `kotlin-stdlib`.

## Deviations from Plan

- [Rule 1 - Style] detekt reported `ReturnCount` (3 returns) in `ApiKeyStore.read` and two `MaxLineLength` hits (an ApiKeyStore KDoc line and an AesGcm KDoc line). Fixed by extracting a private `open(slot, iv, ciphertext)` helper and rewrapping the two doc lines. No change to the public shape or behavior; no suppression used.
- No other deviations. No catch is present in `read`/`save` (06-03 owns the exception mapping, as planned).

## Verification

- `./gradlew :keystore:check :core:check --offline -q` exit 0; `scripts/verify-repo-hygiene.sh` prints HYGIENE OK.
- `scripts/verify-negative-controls.sh` reports `negative-control failures: 0`, including `ok [DataStore creation in keystore main]` and `ok [datastore demoted from api]`.
- `git log` for `core/` and `providers/` since `plan_head_before` is empty.

## Self-Check: PASSED

All seven new source files exist, `KeystoreModule.kt` is deleted, commits 7f41e63 and 43f3a92 are present.
