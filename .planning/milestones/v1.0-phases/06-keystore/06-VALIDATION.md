---
phase: "6"
slug: keystore
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-01"
---

# Phase 6 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (JVM, `testDebugUnitTest`); AndroidX Test runner 1.7.0 + ext-junit 1.3.0 (device) |
| **Config file** | `keystore/build.gradle.kts` (Wave 0 adds test + androidTest deps and the runner), `config/detekt/detekt.yml` |
| **Quick run command** | `./gradlew :keystore:testDebugUnitTest --tests '*<Class>' --offline -q` |
| **Full suite command** | `./gradlew :keystore:check :core:check --offline` |
| **Device command** | `bash scripts/run-keystore-instrumented.sh` (guarded runner, TESTER only; INFRA exit if the device is down) |
| **Estimated runtime** | ~240 seconds full suite (Phase 5 precedent) |

---

## Sampling Rate

- **After every task commit:** the quick command for the touched class plus `./gradlew :keystore:detekt :keystore:scanBannedConstructs --offline -q`
- **After every plan wave:** `./gradlew :keystore:check :core:check --offline`
- **Before `/gsd-verify-work`:** `./gradlew check --offline` green, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`, Gate-1 TESTER run recorded
- **Max feedback latency:** ~240 seconds

---

## Per-Task Verification Map

Seeded from 06-RESEARCH.md "Validation Architecture"; the per-task rows are bound to task ids by the plans' `<automated>` blocks.

| Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|-----------------|-----------|-------------------|-------------|--------|
| KEY-01 | save/read/delete per provider, replace, isolation, blank rejected, trim | unit | `./gradlew :keystore:testDebugUnitTest --tests '*ApiKeyStoreTest' --offline -q` | yes | green |
| KEY-01 | distinct 12-byte IV per save, atomic ct+iv edit | unit | `... --tests '*AesGcmTest' --tests '*ApiKeyStoreAtomicityTest'` | yes | green |
| KEY-02 | slot-table validation | unit | `... --tests '*KeySlotValidationTest'` | yes | green |
| KEY-02 | legacy SB/CT layouts read back; no DataStore creation in `src/main` | unit + grep | `... --tests '*LegacyCompatJvmTest'` | yes | green |
| KEY-02 | real-Keystore legacy compat, literal aliases | instrumented (TESTER) | `bash scripts/run-keystore-instrumented.sh` | yes | green |
| KEY-03 | four states, zero key creation on read, cancellation rethrown | unit | `... --tests '*KeyStateTest' --tests '*ReadNeverCreatesKeyTest' --tests '*UnreadableMappingTest'` | yes | green |
| KEY-03 | golden vector, NO_WRAP-compatible encoding | unit | `... --tests '*GoldenVectorTest'` | yes | green |
| KEY-04 | adapter maps four states, never throws | unit | `... --tests '*KeystoreCredentialSourceTest'` | yes | green |
| KEY-04 | pipeline round trip through the real `commandPipeline` | integration | `... --tests '*KeystorePipelineTest'` | yes | green |
| KEY-01..04 | no secret in toString/exception/persisted plaintext | unit | `... --tests '*KeystoreCanaryTest'` | yes | green |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [x] `keystore/build.gradle.kts`: `api(libs.datastore.prefs)`, test and androidTest deps, `testInstrumentationRunner`
- [x] `gradle/libs.versions.toml`: androidx-test-runner 1.7.0, androidx-test-ext-junit 1.3.0
- [x] root `build.gradle.kts` detekt source adds `src/androidTest/kotlin`
- [x] `SoftwareKeyAccess`, independent legacy-format writers, shared temp-file DataStore helper under `keystore/src/test/kotlin`
- [x] `KeystoreDeviceTest` and the guarded TESTER wrapper script

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Instrumented AndroidKeyStore round trip on the TESTER | KEY-02, KEY-03, KEY-04 | Needs the physical TESTER (`R5CT10XNKQN`); infra outcome if the device is down. Automated by the guarded runner; ran PASS (7 tests) at 16c83fb and again at HEAD in Gate-1 | `bash scripts/run-keystore-instrumented.sh`; it checks availability and fails loudly rather than substituting a device |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 240s
- [x] `nyquist_compliant: true` set by the post-execution finalizer

**Approval:** validated 2026-10-01

---

## Validation Audit 2026-10-01

| Metric | Count |
|--------|-------|
| Requirements audited (KEY-01..KEY-04) | 4 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Every Per-Task row maps to an existing test class (96 JVM tests green at HEAD, `./gradlew check --offline` green) and the device row is covered by `KeystoreDeviceTest` (7 tests, PASS on the TESTER twice, the second time at HEAD in Gate-1). Evidence: `evidence/keystore-instrumented-run.txt`, `evidence/keystore-instrumented-run-gate1.txt`, `06-07-SELF-UAT.md`.
