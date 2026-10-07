---
phase: "20"
slug: "cut-v1-1-0"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-07"
---

# Phase 20 - Validation Strategy

> Per-phase validation contract. Source of truth for commands: 20-RESEARCH.md "## Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (JVM tests); bash gate scripts with built-in selftests |
| **Config file** | `config/detekt/detekt.yml`, `gradle/invariants.gradle.kts`, `scripts/modules.list` |
| **Quick run command** | per-item Quick column in 20-RESEARCH.md (bash-only verifiers first: `bash scripts/verify-stt-confinement.sh`, `bash scripts/verify-release-manifest.sh`, `bash scripts/verify-docs-coverage.sh`) |
| **Full suite command** | `scripts/release-cut.sh selftest all` then `scripts/release-cut.sh preflight v1.1.0 <W>` (quiet windows only) |
| **Estimated runtime** | Quick: seconds to ~2 min (single low-memory Gradle process); Full: quiet-window sized (heavy) |

---

## Sampling Rate

- **After every task commit:** the Quick command for the touched item (one Gradle process at a time, low-memory recipe)
- **After every plan wave:** bash verifiers (docs-coverage, module-manifest, stt-confinement, repo-hygiene, release-manifest) plus `:core:check` / `:sample:check` as touched
- **Before the cut:** `selftest all` green (window 20-01), then `preflight` + `cut` (window 20-02)
- **Max feedback latency:** one task

---

## Per-Task Verification Map

Filled by the plans' `<verify>` blocks; the planner maps each RT/C item to a task. Status is finalized post-execution by validate-phase.

| Item | Requirement | Test Type | Automated Command | Status |
|------|-------------|-----------|-------------------|--------|
| RT-01 | VER-07 | unit/record | `./gradlew --offline -q :core:test --tests '*PlanSchemaTest' --tests '*PlanParseTest' --tests '*ApiShapeTest'` | pending |
| RT-02/05 | VER-07 | script/selftest | `scripts/release-cut.sh gate api-check v1.1.0`; `SELFTEST_ONLY=... scripts/release-cut.sh selftest negative` | pending |
| RT-03/06 | VER-07 | unit | `./gradlew --offline -q :core:test --tests '*ActionEventTest' --tests '*HeldRunIdTest'` | pending |
| RT-04 | VER-07 | script | `scripts/release-cut.sh gate api-dump` | pending |
| RT-07 | VER-07 | unit | `./gradlew --offline -q :sample:testDebugUnitTest --tests '*KeyVaultTest' ...` | pending |
| RT-08 | VER-07 | grep | `grep -n "PD-04" .planning/STATE.md` | pending |
| C9/C11 | VER-07 | script | `bash scripts/verify-docs-coverage.sh` | pending |
| C2/C4 | VER-07 | live | `scripts/jitpack-live-probe.sh W`; `scripts/agent-wiring-test.sh verify` | pending |

---

## Wave 0 Requirements

- [ ] `.planning/releases/v1.1.0/WAIVER-PACKET.md` (selftest and gate 8 need it)
- [ ] New selftest controls for the gate-12 new-module branch and the RT-05 hygiene plant
- [ ] RT-07 tests (flip `KeyVaultTest` last-4 assertion; add fingerprint + no-echo tests)
- [ ] Final `<m>/api.txt` commits (five files)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Instructions |
|----------|-------------|------------|--------------|
| Push main, tag push, ledger relay | VER-07 | Orchestrator handshakes (RT-09) | Master relays "pushing main <sha>", "tag ready v1.1.0 <sha>"; never commit section 11 |
| Waiver packet answers | VER-07 | Yahir decision (D-05) | Ask via orchestrator BEFORE the quiet window |
| Isolated-agent wiring test (C4) | VER-07 | Needs orchestrator dispatch of an isolated agent | `agent-wiring-test.sh prepare W` then `verify` |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] `nyquist_compliant: true` set by validate-phase after execution

**Approval:** pending
