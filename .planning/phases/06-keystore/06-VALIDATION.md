---
phase: "6"
slug: keystore
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| **Device command** | `ANDROID_SERIAL=R5CT10XNKQN ./gradlew :keystore:connectedDebugAndroidTest --offline --console=plain` via the guarded script (TESTER only) |
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
| KEY-01 | save/read/delete per provider, replace, isolation, blank rejected, trim | unit | `./gradlew :keystore:testDebugUnitTest --tests '*ApiKeyStoreTest' --offline -q` | W0 | pending |
| KEY-01 | distinct 12-byte IV per save, atomic ct+iv edit | unit | `... --tests '*AesGcmTest' --tests '*ApiKeyStoreAtomicityTest'` | W0 | pending |
| KEY-02 | slot-table validation | unit | `... --tests '*KeySlotValidationTest'` | W0 | pending |
| KEY-02 | legacy SB/CT layouts read back; no DataStore creation in `src/main` | unit + grep | `... --tests '*LegacyCompatJvmTest'` | W0 | pending |
| KEY-02 | real-Keystore legacy compat, literal aliases | instrumented (TESTER) | device command | W0 | pending |
| KEY-03 | four states, zero key creation on read, cancellation rethrown | unit | `... --tests '*KeyStateTest' --tests '*ReadNeverCreatesKeyTest' --tests '*UnreadableMappingTest'` | W0 | pending |
| KEY-03 | golden vector, NO_WRAP-compatible encoding | unit | `... --tests '*GoldenVectorTest'` | W0 | pending |
| KEY-04 | adapter maps four states, never throws | unit | `... --tests '*KeystoreCredentialSourceTest'` | W0 | pending |
| KEY-04 | pipeline round trip through the real `commandPipeline` | integration | `... --tests '*KeystorePipelineTest'` | W0 | pending |
| KEY-01..04 | no secret in toString/exception/persisted plaintext | unit | `... --tests '*KeystoreCanaryTest'` | W0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `keystore/build.gradle.kts`: `api(libs.datastore.prefs)`, test and androidTest deps, `testInstrumentationRunner`
- [ ] `gradle/libs.versions.toml`: androidx-test-runner 1.7.0, androidx-test-ext-junit 1.3.0
- [ ] root `build.gradle.kts` detekt source adds `src/androidTest/kotlin`
- [ ] `SoftwareKeyAccess`, independent legacy-format writers, shared temp-file DataStore helper under `keystore/src/test/kotlin`
- [ ] `KeystoreDeviceTest` and the guarded TESTER wrapper script

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Instrumented AndroidKeyStore round trip on the TESTER | KEY-02, KEY-03, KEY-04 | Needs the physical TESTER (`R5CT10XNKQN`); infra outcome if the device is down | run the guarded wrapper script; it checks availability and fails loudly rather than substituting a device |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` stays `false` at plan time

**Approval:** pending (finalizer-owned, not set at plan time)
