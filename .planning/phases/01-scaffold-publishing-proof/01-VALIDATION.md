---
phase: "1"
slug: "scaffold-publishing-proof"
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-09-30"
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: 01-RESEARCH.md "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 (+ kotlinx-coroutines-test 1.11.0, legacy okhttp mockwebserver in `:providers`); Gradle verification tasks as structural tests |
| **Config file** | none beyond Gradle build files; `config/detekt/detekt.yml` |
| **Quick run command** | `./gradlew :core:check :providers:check -q` |
| **Full suite command** | `./gradlew check` |
| **Estimated runtime** | ~45 seconds warm |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :<touched module>:check -q`
- **After every plan wave:** Run `./gradlew check`
- **Before `/gsd-verify-work`:** Full suite green + `scripts/verify-negative-controls.sh` green + live JitPack probe evidence saved
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| BLD-01 | one-way module graph; `:core` has no HTTP/Android/DI/hub | structural | `./gradlew verifyModuleGraph :core:verifyCoreDependencyAllowlist` | ✅ | ✅ green |
| BLD-02 | toolchain pins + JVM 11 class files | structural | `./gradlew verifyBytecodeLevel` | ✅ | ✅ green |
| BLD-03 | per-module resolve from JitPack by SHA, `:sample` absent | live probe | `VERSION=$SHA scripts/jitpack-consumer-probe.sh` | ✅ | ✅ green |
| BLD-04 | detekt clean + invariant rules; planted constructs fail | unit+integration | `./gradlew detekt scanBannedConstructs` ; `scripts/verify-negative-controls.sh` | ✅ | ✅ green |
| BLD-05 | explicit API strict; Metalava dump + guarded check | structural | `./gradlew verifyExplicitApiStrict` | ✅ | ✅ green |
| BLD-07 | package root `io.github.ygaray.voiceactionengine` | structural | `git ls-files` path assertions | ✅ | ✅ green |
| BLD-08 | ECOSYSTEM/README coordinates; gitignore | unit | `grep` + `git check-ignore` | ✅ | ✅ green |
| BLD-09 | test-fixture harness runs with zero network; fixtures unpublished | unit | `./gradlew :core:test :core:verifyNoTestFixturesPublished` | ✅ | ✅ green |
| CLN-01 | no DI imports/annotations/artifacts | structural | scanner + detekt controls | ✅ | ✅ green |
| CLN-05 | no planning ids in comments | unit | detekt controls + scanner control | ✅ | ✅ green |
| (A1 plumbing) | matrix legs + reflective guard + compile floor | integration | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 :providers:verifyOkHttpCompileFloor` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Greenfield: every file is a gap. `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper, `jitpack.yml`, `config/detekt/detekt.yml`, `config/negative-controls/**`, module build files, `:core` test + testFixtures, `:providers` guard test, `scripts/*.sh`, `.gitignore` additions, ECOSYSTEM/README edits. No framework install needed (Gradle resolves everything).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live JitPack build of a pushed commit SHA | BLD-03 | Needs a pushed commit and JitPack's servers | Follow "JitPack Probe Procedure" in 01-RESEARCH.md; save script output + build.log excerpt as evidence |

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-09-30).

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] _(finalizer-only)_ `nyquist_compliant: true` set post-execution

**Approval:** validated 2026-09-30

---

## Validation Audit 2026-09-30

| Metric | Count |
|--------|-------|
| Requirements audited | 11 (10 requirement IDs plus the A1 plumbing row) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Every row maps to an automated command that ran green at HEAD: `./gradlew check` (137 tasks, 01-VERIFICATION.md and the auditor rerun), `scripts/verify-negative-controls.sh` (69 ok, 0 failures), `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh`, and the live JitPack probe (LIVE PROBE PASS ref=a40f8319ca, evidence/jitpack-probe.txt). The BLD-03 live probe stays listed under Manual-Only only because it needs the network and a pushed SHA; it is scripted and was executed. Auditor not spawned (auto mode, zero automatable gaps).
