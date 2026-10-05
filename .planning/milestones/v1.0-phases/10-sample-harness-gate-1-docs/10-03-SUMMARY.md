---
phase: 10-sample-harness-gate-1-docs
plan: 03
subsystem: sample
tags: [keystore, key-vault, debug-importer, plaintext-scan, ver-01, key-02]
status: complete
requires: [10-02]
provides:
  - "SampleKeys: app-owned DataStore (vae_sample_keys), three-slot KeySlot table, process-wide ApiKeyStore and KeystoreCredentialSource factories"
  - "KeyVault seam (ApiKeyStoreVault), KeyImport/ImportReport, KeyUx cause-code mapping and state labels"
  - "PlaintextScan: yes/no search of the DataStore directory for leaked plaintext"
  - "DebugTools.keyImport: debug-only TestKeyImporter; release variant returns null"
affects: [10-06, 10-07]
tech-stack:
  added: []
  patterns: ["debug/release source-set split so the importer class does not exist in release", "open-set cause codes with an else branch", "reports carry state words and booleans only"]
key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeySlots.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/PlaintextScan.kt
    - sample/src/debug/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
    - sample/src/release/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
    - sample/src/testDebug/kotlin/io/github/ygaray/voiceactionengine/sample/TestKeyImporterTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/KeyVaultTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/PlaintextScanTest.kt
  modified: []
key-decisions:
  - "A save that throws (security, provider or I/O failure) is reported as state save_failed with the exception type name as cause, and the plaintext file is still destroyed"
  - "The importer scans filesDir/datastore (where preferencesDataStore writes) for the trimmed plaintext; the scan result is null when no save was attempted"
requirements-completed: [VER-01]
commits: 3
plan_head_before: c0358b5d1b3a233ade5d83feb29ec3e7fad5cc0a
actuals:
  tokens: 9000
  tasks: 3
  commits: 3
duration: 25 min
completed: 2026-10-01
---

# Phase 10 Plan 03: Key Vault, Slots and Debug Test-Key Importer Summary

Every key the sample uses now goes through `:keystore` on an app-owned DataStore with an explicit three-slot table, a debug-only importer moves pushed test keys through the vault and destroys their plaintext, and keystore failures map to a clear user action.

## What was built

- **Task 1 (tracer, 543135e):** `KeyVault`/`ApiKeyStoreVault`, `KeyImport`, `ImportReport` (state words and booleans only), `PlaintextScan`, debug `DebugTools` + `TestKeyImporter`, release `DebugTools` returning null, and `TestKeyImporterTest` (6 tests in the debug-variant source set).
- **Task 2 (feat, d6e8be9):** `SampleKeys` (`PROVIDERS`, `SLOTS`, `store(context)` memoized, `credentials(store)`), `KeyAction`, `KeyUx.action`/`label`, and `KeyVaultTest` (4 tests).
- **Task 3 (test, f7454fc):** `PlaintextScanTest` (5 tests) and the release/debug dex check.

Slot names: provider `anthropic|openai|openrouter`, alias `vae_sample_<provider>`, ciphertext key `vae_sample_<provider>_ct`, IV key `vae_sample_<provider>_iv`; DataStore file `vae_sample_keys`.

## Gate results

| Gate | Result |
|------|--------|
| `TestKeyImporterTest` / `KeyVaultTest` / `PlaintextScanTest` | 6 / 4 / 5 tests, 0 failures |
| `:sample:compileReleaseKotlin` (substitute for the non-existent release unit-test task) | green |
| `./gradlew check --offline` | green |
| `scripts/verify-repo-hygiene.sh` | HYGIENE OK |
| `git diff --stat PLAN_BASE -- core providers keystore` | empty |
| `grep -rl 'test-keys' sample/src/main sample/src/release` | prints nothing |
| `vault.save` in the debug `DebugTools.kt` | present |
| `grep -c 'else ->'` in `KeyVault.kt` | 3 |

## Release/debug dex check (V8, V14)

| APK | `TestKeyImporter` in `classes*.dex` | `test-keys` string | Non-vacuity probe (`KeyUx`) |
|-----|-----------------------------------|--------------------|-----------------------------|
| `sample-release-unsigned.apk` | absent (0 matching lines) | absent | present |
| `sample-debug.apk` | present (7 matching lines) | present | n/a |

The release APK holds no importer and cannot name the plaintext key directory; the debug APK has both, so the check is not vacuous.

## Deviations from Plan

**1. [Rule 3 - Blocking] `:sample:testReleaseUnitTest` does not exist** (known from 10-02). Used `:sample:compileReleaseKotlin` plus `:sample:assembleRelease` for the release-variant proof. No build file changed.

**2. [Rule 2 - Missing critical functionality] `save_failed` import state.** The plan lists six report states; `ApiKeyStore.save` documents it can throw `GeneralSecurityException`, `ProviderException` and `IOException`. Without handling, one provider's failure would abort the import and could leave its plaintext file behind. The importer now reports `save_failed` (cause = exception type name, never a message) and still destroys the plaintext. Not covered by a dedicated test; the fake vault in the host test does not throw.

**Total deviations:** 2 (Rule 3, Rule 2). **Impact:** none on the contract; `save_failed` is an additive word in the report vocabulary for 10-06 to render.

## Authentication Gates

None. No device, adb, push-test-key, key file or live call was touched (D-01, D-13); all canaries are synthetic and not key-shaped.

## Next Phase Readiness

10-06 can call `SampleKeys.store(context)`, wrap it in `ApiKeyStoreVault`, pass `SampleKeys.credentials(store)` to `SampleEngine`, and call `DebugTools.keyImport(filesDir, vault, SampleKeys.PROVIDERS)` (null in release). The real AndroidKeyStore round trip is still unproven on a device; the Gate-1 tester owns it (G1-02).

## Self-Check: PASSED

- All eight created files exist on disk.
- Commits 543135e, d6e8be9, f7454fc present.
- Acceptance criteria re-run; no change under core/, providers/, keystore/.
