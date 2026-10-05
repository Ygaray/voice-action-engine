---
phase: "13"
slug: "on-device-model-spike"
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-05"
---

# Phase 13 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `13-RESEARCH.md` section "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (repo standard); hand-written fakes |
| **Config file** | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts`; new `scripts/*spike*` |
| **Quick run command** | `./gradlew :spike-ondevice:testDebugUnitTest --tests '<Class>' --offline -q` (low-memory GRADLE_OPTS; one Gradle at a time) |
| **Full suite command** | `./gradlew :core:test :core:detekt :core:scanBannedConstructs :providers:detekt :providers:scanBannedConstructs :keystore:detekt :keystore:scanBannedConstructs :spike-ondevice:testDebugUnitTest --offline`, then `scripts/verify-repo-hygiene.sh`, `scripts/verify-spike-device-guard.sh`, `scripts/verify-spike-verdict.sh`, `scripts/verify-negative-controls.sh` |
| **Estimated runtime** | ~300-600 seconds (host is memory-tight; max 2 Gradle-running plans per wave) |

---

## Sampling Rate

- **After every task commit:** Run the single touched test class (quick command)
- **After every plan wave:** `./gradlew :core:test :spike-ondevice:testDebugUnitTest` plus `scripts/verify-spike-device-guard.sh` and `scripts/verify-repo-hygiene.sh`
- **Before `/gsd-verify-work`:** Full suite must be green; `13-VERDICT.md` reproduced by `verify-spike-verdict.sh`
- **Max feedback latency:** 600 seconds

---

## Per-Task Verification Map

Requirement-level map (task rows are bound to concrete plan tasks by the plan files; each plan task carries its own `<automated>` command).

| Req | Behavior | Test Type | Automated Command | File Exists | Status |
|-----|----------|-----------|-------------------|-------------|--------|
| SPIKE-01 | Evidence complete and well formed; absent gating row yields red `unmeasured:<metric>` | unit | `./gradlew :spike-ondevice:testDebugUnitTest --tests '*VerdictRulesTest*' --tests '*EvidenceLineTest*'` | W0 | pending |
| SPIKE-01 | Wilson / percentile / schema-valid scoring match oracle table | unit | `... --tests '*ScoringTest*'` | W0 | pending |
| SPIKE-01 | Route A / Route B mappers over a fake `LlmBackend` | unit | `... --tests '*SpikeProviderTest*'` | W0 | pending |
| SPIKE-01 | Toolchain: assemble, D8, stdlib resolution, 16 KB zipalign, APK delta | build | `./gradlew :spike-ondevice:assembleDebug` plus `zipalign -v -c -P 16 4 <apk>` | W0 | pending |
| SPIKE-01 | Runner never targets another device | script | `scripts/verify-spike-device-guard.sh` | W0 | pending |
| SPIKE-01 | Evidence filter rejects prompts, outputs, key shapes | script | `scripts/spike-evidence-filter.sh` with negative samples | W0 | pending |
| SPIKE-01 | Measurement on the TESTER (latency, PSS, thermal, accuracy) | manual-only (device window) | `scripts/run-spike-ondevice.sh preflight/run/capture-save/cleanup` | W0 | pending |
| SPIKE-02 | `13-VERDICT.md` equals verdict recomputed from committed evidence under committed thresholds | script | `scripts/verify-spike-verdict.sh` | W0 | pending |
| SPIKE-02 | Message file has verdict, per-envelope numbers, three D-10 rows; relay recorded | doc check | `grep` checks on `13-VERDICT-MESSAGE.md` | W0 | pending |
| SPIKE-03 (red) | No spike code / ML reference ships | script | `scripts/verify-repo-hygiene.sh` | W0 | pending |
| SPIKE-03 (green) | `:ondevice` gates: check, api.txt, opt-in negative compile, gate fallback unchanged | build+unit | `./gradlew :ondevice:check apiCheck` | only if green | pending |
| SC4 | `:core` test flags LiteRT/MediaPipe tokens | unit | `./gradlew :core:test --tests '*NoHardCodedConstantsTest*'` | extend | pending |
| SC4 | ML artifact on `:core`/`:providers`/`:keystore` classpath fails the build | gate | `./gradlew :providers:verifyNoMlArtifacts` plus negative-control plant | W0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `spike-ondevice/` module scaffold + catalog pin (13-01)
- [ ] `13-THRESHOLDS.md` committed before any device step (13-01)
- [ ] `Scoring` / `EvidenceLine` / `VerdictRules` tests with Wilson oracle table
- [ ] fake `LlmBackend` + provider tests
- [ ] `run-spike-ondevice.sh`, `verify-spike-device-guard.sh`, `spike-evidence-filter.sh`, `verify-spike-verdict.sh`
- [ ] `verifyNoMlArtifacts` task + negative-control plants + hygiene patterns + `.gitignore` entries
- [ ] Gold sets: small committed synthetic (EN/ES/negatives) and private SB loader (gitignored)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| On-device measurement run | SPIKE-01 | Only the real S22 TESTER produces the numbers; needs a separately granted TESTER window | Non-autonomous checkpoint returning `needs_human` to the orchestrator; runner scripts with TESTER-only guard |
| Verdict relay to the orchestrator | SPIKE-02 | Message is relayed by the orchestrator/human | Produce `13-VERDICT-MESSAGE.md`; note relay in SUMMARY |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> Finalized ONLY post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 600s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` - leave `false` at plan time

**Approval:** pending (finalizer-owned, not set at plan time)
