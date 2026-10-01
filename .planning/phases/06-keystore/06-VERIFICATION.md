---
phase: 06-keystore
verified: 2026-10-01T13:00:00Z
status: passed
score: 4/4 must-haves verified
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/06-keystore/06-01-PLAN.md"
  - ".planning/phases/06-keystore/06-01-SUMMARY.md"
  - ".planning/phases/06-keystore/06-02-PLAN.md"
  - ".planning/phases/06-keystore/06-02-SUMMARY.md"
  - ".planning/phases/06-keystore/06-03-PLAN.md"
  - ".planning/phases/06-keystore/06-03-SUMMARY.md"
  - ".planning/phases/06-keystore/06-04-PLAN.md"
  - ".planning/phases/06-keystore/06-04-SUMMARY.md"
  - ".planning/phases/06-keystore/06-05-PLAN.md"
  - ".planning/phases/06-keystore/06-05-SUMMARY.md"
  - ".planning/phases/06-keystore/06-06-PLAN.md"
  - ".planning/phases/06-keystore/06-06-SUMMARY.md"
  - ".planning/phases/06-keystore/06-07-PLAN.md"
  - ".planning/phases/06-keystore/06-07-SUMMARY.md"
  - "keystore/build.gradle.kts"
  - "keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreDeviceTest.kt"
  - "keystore/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyDeviceCrypto.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcm.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlot.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyState.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauses.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSource.kt"
  - "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcmTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreAtomicityTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreObserveTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/GoldenVectorTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlotValidationTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyStateTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreApiShapeTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCanaryTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSourceTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystorePipelineTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreTestSupport.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyCompatJvmTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyWriters.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/PlatformKeyAccessJvmTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ReadNeverCreatesKeyTest.kt"
  - "keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt"
  - "scripts/run-keystore-instrumented.sh"
  - "scripts/verify-keystore-device-guard.sh"
covered_digest: "v1:sha256:5adea53616d78a2f5e4d1040db6aa1d0512940522ab7686f1c91c7febfc876e3"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 6: Keystore Verification Report

**Phase Goal:** A consumer can keep each provider's BYO API key encrypted on-device in its own DataStore, under its existing aliases so no current user's key is stranded, and feed that key straight into the provider seam.
**Verified:** 2026-10-01
**Status:** passed
**Re-verification:** No, initial verification

SUMMARY claims were not used as evidence. Every truth below was checked against the `:keystore` main sources, the test sources, the freshly re-run `:keystore` gate, and the read-only SecondBrain and CalTracker sources.

## Goal Achievement

### Observable Truths (ROADMAP success criteria SC1..SC4)

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| SC1 | A consumer can store, read and delete a key per provider; AES/GCM via AndroidKeyStore; persisted in the app's own `DataStore<Preferences>`; the engine never opens a second DataStore | VERIFIED | `ApiKeyStore.kt`: `save` (trim, reject blank, `AesGcm.seal` under `keyAccess.getOrCreateKey`, ciphertext and iv written in one `dataStore.edit`, serialized by a `Mutex`), `delete` (both prefs removed in one `edit`, device key left alone), `read` and `observe`. `AesGcm.kt` uses `AES/GCM/NoPadding`, 128-bit tag, provider-generated nonce. `AndroidKeyStoreKeyAccess` is the platform path (`KeyStore.getInstance("AndroidKeyStore")`). The constructor takes an injected `DataStore<Preferences>`. Gradle gate `verifyNoDataStoreCreation` fails the build on `preferencesDataStore`, `PreferenceDataStoreFactory` or `DataStoreFactory` in main, and it passed on my re-run. `verifyDatastoreIsApi` passed (datastore is a compile-scope POM dep). Tests: `ApiKeyStoreTest` 4, `ApiKeyStoreAtomicityTest` 8, `KeySlotValidationTest` 14. Canary test confirms the persisted file does not contain the key. |
| SC2 | Keys map via an app-supplied explicit `KeySlot` table; a key written under SB's or CT's existing alias and DataStore key reads back unchanged (legacy-format compat) | VERIFIED | `KeySlot(provider, alias, ciphertextKey, ivKey)` has no defaults, is validated at construction, and `indexValidated` rejects an empty table and any repeated provider, alias or pref name. Nothing is derived by formula. I checked the literal names against the apps' real source (read only). SB: alias `secondbrain_anthropic_api_key_v1`, prefs `anthropic_api_key_ct` and `anthropic_api_key_iv` (`KeystoreCrypto.kt:98`, `ThemePreferenceManager.kt:323,329`). CT: aliases `caltracker_api_key_v1`, `caltracker_openai_api_key_v1`, `caltracker_openrouter_api_key_v1`, with `{anthropic,openai,openrouter}_api_key_{ct,iv}` (`ApiKeyRepository.kt:199-212`). These match the slots used in `LegacyCompatJvmTest` and `KeystoreDeviceTest`. JVM leg: `LegacyCompatJvmTest` (5, independent replica writers in both directions) and `GoldenVectorTest` (4, fixed byte vector). Device leg: `KeystoreDeviceTest` `secondBrainLegacyBlobReadsBack`, `calTrackerLegacyBlobsReadBack` and `storeWritesLegacyCodeReads` use verbatim copies of the apps' crypto against the real AndroidKeyStore (device run: OK, 7 tests). |
| SC3 | Reads return `NotConfigured, Ready, KeyMissing, Unreadable`; decrypt after the Keystore key is gone is `KeyMissing` and never creates a key; synchronized get-or-create on encrypt; `java.util.Base64` NO_WRAP-compatible encoding | VERIFIED | `KeyState.kt` has the four leaves. `SecretReader.open` only calls `keyAccess.existingKey`, which returns null when the key is absent (mapped to `KeyMissing`) and throws for any other lookup problem (mapped to `Unreadable("keystore_unavailable")`). The reader has no path to `getOrCreateKey`. `getOrCreateKey` is `synchronized(creationLock)` on a process-wide object. Encoding uses `java.util.Base64.getEncoder()` and a strict `getDecoder()`. `ReadNeverCreatesKeyTest` (3) asserts no create and no write on read. `GoldenVectorTest` pins the alphabet, padding and no line break. Device: `deletedDeviceKeyReadsKeyMissingAndCreatesNothing`, `concurrentFirstUseKeepsOneKey` and `frameworkBase64MatchesJavaBase64` passed on the TESTER. |
| SC4 | `KeystoreCredentialSource` plugs into the provider seam; JVM tests prove the round trip through the crypto seam; one instrumented test passes on the TESTER | VERIFIED | `KeystoreCredentialSource : CredentialSource` is a real `fun interface` in `:core`. It maps `Plain` to `Present(Credential(provider, key))`, `KeyMissing` to `Unreadable("key_missing")`, `Unreadable` to `Unreadable(cause)`, and everything else to `Missing`. `KeystorePipelineTest` (3) runs a saved key through the real `commandPipeline` to the fake provider. `KeystoreCredentialSourceTest` has 11 tests. Device: `evidence/keystore-instrumented-run.txt` records `OK (7 tests)` on TESTER `R5CT10XNKQN` through the guarded runner, and the runner's own guard proof (`DEVICE GUARD OK`) is in the 06-07 plan record. See the Gate-1 note below on the post-review code. |

**Score:** 4/4 truths verified (0 present-but-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
| --- | --- | --- | --- |
| `keystore/.../ApiKeyStore.kt` | Public store: save, delete, read, observe, three constructors | VERIFIED | 169 lines, substantive, wired from `KeystoreCredentialSource` and the tests. No default arguments on public constructors. |
| `keystore/.../KeySlot.kt` | Explicit validated slot row | VERIFIED | Validated in `init`; `toString` names only, no secrets. |
| `keystore/.../KeyState.kt` | Four typed states, open abstract class, stable-code cause | VERIFIED | `Ready.toString` prints no key; `Unreadable` rejects non-`[a-z0-9_]+` causes. |
| `keystore/.../SecretReader.kt` | Single decode-and-decrypt path, never creates a key | VERIFIED | Explicit catch chain with cancellation rethrown; no `Exception` or `Throwable` catch; no `runCatching`. |
| `keystore/.../AndroidKeyStoreKeyAccess.kt` | `getKey`-based lookup, process-global creation lock, same spec as the apps | VERIFIED | Does not use `getEntry` or `containsAlias`, so a transient failure cannot be read as "absent". Spec is AES-256, GCM, no padding, no auth binding, no StrongBox. |
| `keystore/.../KeystoreCredentialSource.kt` | Adapter onto the existing seam | VERIFIED | Adds no parallel type. `toString` prints the type only. |
| `keystore/.../KeystoreCauses.kt` | Single home for the five cause codes | VERIFIED | `key_missing`, `keystore_unavailable`, `decrypt_failed`, `stored_value_malformed`, `storage_unreadable`. |
| `keystore/build.gradle.kts` | datastore as `api`, no-creation gate, androidTest wiring | VERIFIED | Both custom gates are wired into `check` and ran green. `assembleDebugAndroidTest` is part of `check`. |
| `androidTest/.../KeystoreDeviceTest.kt`, `LegacyDeviceCrypto.kt` | Real-Keystore instrumented class (7 tests) | VERIFIED | 7 `@Test` methods; assertions are substantive (real read-back, key deletion, 8-thread race, tamper). |
| `scripts/run-keystore-instrumented.sh`, `verify-keystore-device-guard.sh` | TESTER-only runner and offline refusal proof | VERIFIED | Present and executable; not exercised by me (no adb, per instruction). |
| `evidence/keystore-instrumented-run.txt`, `keystore-surface-review.txt` | Device and surface records | VERIFIED | Present. The run record is for commit `16c83fb`, which predates the review fixes (see below). |

### Key Link Verification

| From | To | Via | Status | Details |
| --- | --- | --- | --- | --- |
| `KeystoreCredentialSource` | `ApiKeyStore.readSecret` | internal call | WIRED | `store.readSecret(provider)`, mapped through the `SecretRead.Plain` and `Failed` branches. |
| `ApiKeyStore` | `SecretReader` | `reader.open` | WIRED | Used by `openSlot` and by `observeSlot`. |
| `ApiKeyStore.save` | `AndroidKeyStoreKeyAccess.getOrCreateKey` | `KeyAccess` seam | WIRED | The public constructors pass `AndroidKeyStoreKeyAccess`. |
| `SecretReader` | `KeyAccess.existingKey` (never `getOrCreateKey`) | `lookUp` | WIRED | Confirmed by grep. `ReadNeverCreatesKeyTest` asserts it. |
| `KeystoreCredentialSource` | `:core` `CredentialSource` and `CredentialLookup` | implements | WIRED | `KeystorePipelineTest` drives the real `commandPipeline` through it. |
| `check` | `verifyDatastoreIsApi`, `verifyNoDataStoreCreation`, `assembleDebugAndroidTest` | `dependsOn` | WIRED | Present in `build.gradle.kts`. |

### Data-Flow Trace (Level 4)

| Artifact | Data variable | Source | Produces real data | Status |
| --- | --- | --- | --- | --- |
| `KeystoreCredentialSource.credential` | `read.text` | `dataStore.data.first()` then `SecretReader.open` then `AesGcm.open` on the stored pair | Yes. `KeystorePipelineTest` shows the saved key arriving at the provider. | FLOWING |
| `ApiKeyStore.observe` | emitted `KeyState` | `dataStore.data` then `distinctUntilChangedBy` then `reader.open` | Yes. `ApiKeyStoreObserveTest` has 8 tests. | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| --- | --- | --- | --- |
| `:keystore` unit suite, detekt, both custom gates, instrumented-class assembly (forced re-run) | `./gradlew :keystore:testDebugUnitTest :keystore:detekt :keystore:verifyDatastoreIsApi :keystore:verifyNoDataStoreCreation :keystore:assembleDebugAndroidTest --offline --rerun-tasks -q` | Exit 0, no output. Result XML shows 96 tests, 0 failures, 0 errors, 0 skipped across 15 classes. | PASS |
| Whole project gate | `./gradlew check --offline -q` | Exit 0 | PASS |
| Debt markers in phase files | grep for `TBD`, `FIXME`, `XXX`, `TODO`, `HACK`, `PLACEHOLDER` in `keystore/src` and the two scripts | No matches | PASS |
| Banned constructs in main | grep for `runCatching`, `println`, `printStackTrace`, `android.util.Log`, `okhttp3`, `@Suppress`, `getEntry`, `containsAlias`, `deleteEntry` | No matches | PASS |
| Planning ids in comments | grep for `D-NN`, `KEY-0N`, `WR-0N`, `T-NN-NN`, `Phase N` in `keystore/src` and `build.gradle.kts` | No matches | PASS |
| No `core` or `providers` change since plan 06-06 | `git diff --stat 8d13043..HEAD -- core providers` | Empty | PASS |

### Probe Execution

Step 7c: SKIPPED. The phase declares no probe scripts. `scripts/verify-keystore-device-guard.sh` is an offline fake-adb proof, and I did not run it because the instruction was not to touch adb at all.

### Requirements Coverage

Plan frontmatter IDs: 06-01 [KEY-01, KEY-02]; 06-02 [KEY-01, KEY-02]; 06-03 [KEY-03]; 06-04 [KEY-02, KEY-03]; 06-05 [KEY-04]; 06-06 [KEY-02, KEY-03, KEY-04]; 06-07 [KEY-02, KEY-03, KEY-04]. Union: KEY-01..KEY-04, which equals the ROADMAP Phase 6 requirement list. REQUIREMENTS.md maps no other ID to Phase 6, so there are no orphaned requirements.

| Requirement | Source Plans | Description | Status | Evidence |
| --- | --- | --- | --- | --- |
| KEY-01 | 06-01, 06-02 | Store, read and delete a BYO key per provider; AES/GCM; the app's own DataStore | SATISFIED | See SC1. |
| KEY-02 | 06-01, 06-02, 06-04, 06-06, 06-07 | Explicit app-supplied `KeySlot` table; existing aliases and keys preserved; never derives names; never opens a second DataStore | SATISFIED | See SC2 and SC1. Alias and pref names confirmed against the real SB and CT sources. |
| KEY-03 | 06-03, 06-04, 06-06, 06-07 | Four typed states; decrypt never creates a key; synchronized get-or-create; NO_WRAP-compatible Base64 | SATISFIED | See SC3. |
| KEY-04 | 06-05, 06-06, 06-07 | `KeystoreCredentialSource` on the provider seam; JVM round trip plus one TESTER instrumented test | SATISFIED | See SC4. |

### Prohibitions (plan `must_haves.prohibitions`, judgment-tier)

I checked the code-checkable ones and found no violation:

- No logging or printing, no `runCatching`, no DI annotation, no `@Suppress`, no planning id in comments: grep clean.
- No `getEntry`, `containsAlias`, StrongBox, user-auth binding or `deleteEntry` in main: grep clean.
- No public member returns plaintext: the only public path out is `KeystoreCredentialSource`, and `readSecret` is `internal`.
- No catch of `Exception` or `Throwable`. The `RuntimeException` catches were removed in review fix WR-04.
- No default argument on a public constructor or function. The three `ApiKeyStore` constructors are overloads.
- No change under `core/` or `providers/` since 06-06. No `api.txt` is tracked (the 06-07 hygiene script reported `HYGIENE OK`).

One prohibition looks like a deviation, but it is a scope question rather than a real violation. Plans 06-01 to 06-03 and 06-05 say "No sealed class, data class or enum". Review fix IN-02 added `internal sealed class SecretRead` (`SecretReader.kt:17`). The plans attach that rule to the public-shape rules ("house shape rules; 06-05 adds the sweep that enforces them"), and `KeystoreApiShapeTest` (7 tests, green) sweeps the compiled public classes. An `internal` sealed type is invisible to consumers and has no API-evolution cost, so I do not treat it as a violation. It is recorded here so the orchestrator knows the literal wording was not followed.

### Anti-Patterns Found

None blocking. There are no debt markers, no stub returns, no empty handlers, and no hardcoded-empty data flowing to output in the phase files.

### Review Fixes (06-REVIEW / 06-REVIEW-FIX)

I confirmed in the current sources that WR-01 through WR-06 and IN-01, IN-02 and IN-05 are present:

- Decrypt maps only `BadPaddingException` and `IllegalBlockSizeException` to `decrypt_failed`. Other `GeneralSecurityException` and `ProviderException` map to `keystore_unavailable`.
- `read` and `observe` both map only `IOException` to `storage_unreadable`.
- `observe` uses `distinctUntilChangedBy` on the slot's stored pair.
- No `RuntimeException` catch remains.
- The `KeyAccess` and `AndroidKeyStoreKeyAccess` KDoc now states the process-local lock guarantee honestly.

All of these ran green on the forced `:keystore` re-run above. IN-03 and IN-04 were skipped for documented reasons and are not goal-relevant.

### Expected Pending Items (not gaps, not human_needed)

1. **Gate-1 re-run of `KeystoreDeviceTest` on the TESTER against the fixed code.** The recorded device run (commit `16c83fb`) predates the WR-01..03 changes to `SecretReader` and `ApiKeyStore`. The SC4 device half is satisfied by that run. The later changes only touch error classification and `observe` filtering. They do not touch the happy path or the `KeyMissing` and no-create lookup, and the tamper case still lands on `decrypt_failed`, because `AEADBadTagException` extends `BadPaddingException`. The JVM suite proves the new behavior, and the agentic Gate-1 self-UAT is the scheduled device re-confirmation.
2. **The five `Unreadable` cause codes** are as implemented. They are pending orchestrator confirmation and are frozen at the `v1.0.0` tag.
3. **Evidence granularity.** The runner prints only dots, so the per-test names in the run record are the class's `@Test` names plus `OK (7 tests)`. That is a sound proof that all 7 passed, but it is not a per-test device log.
4. **Bookkeeping.** `REQUIREMENTS.md` still shows KEY-01..04 as `[ ]` and "Pending" in Traceability, and the ROADMAP phase list line 30 is unchecked. The orchestrator owns these updates after this verification.

### Human Verification Required

None. Nothing in this phase needs a person to look at it. The device leg ran under automation, and its re-run is Gate-1's job.

### Gaps Summary

No gaps. All four ROADMAP success criteria hold in the code. The legacy aliases and preference names match the real SecondBrain and CalTracker sources. The read path cannot create a key by construction, and a test proves it. The adapter sits on the existing `CredentialSource` seam and works through the real pipeline. `./gradlew check --offline` passes, and the `:keystore` suite re-run with `--rerun-tasks` shows 96 passing tests, with the 7-test instrumented class recorded as passing on the TESTER.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
