---
phase: "20"
slug: "cut-v1-1-0"
status: complete
nyquist_compliant: true
wave_0_complete: true
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

Finalized post-execution (2026-10-07, validate-phase --auto, evidence audit only; no Gradle or network re-run). Phase 20 is a release cut: validation is the bash gate scripts with selftests plus recorded quiet-window evidence. 20-VERIFICATION.md: passed, 3/3, 1 override (RT-12).

| Item | Requirement | Test Type | Automated Command | Evidence | Status |
|------|-------------|-----------|-------------------|----------|--------|
| RT-01 | VER-07 | unit | `./gradlew --offline -q :core:test --tests '*PlanSchemaTest'` | `evidence/rt-outcomes.txt` (PlanSchemaTest 10 tests, 0 failures, drift guard `theParsersFieldNamesAreTheSchemasOwnKeys`) | green |
| RT-02/05 | VER-07 | script/selftest | `scripts/release-cut.sh selftest all` | `20-QUIET-WINDOW-01b.md` (selftest all green, exit 0), gates 10/12 OK | green |
| RT-03/06 | VER-07 | unit | `./gradlew --offline -q :core:test --tests '*ActionEventTest' --tests '*HeldRunIdTest'` | `evidence/rt-outcomes.txt` (sentinel tests green, unedited) | green |
| RT-04 | VER-07 | script | `scripts/release-cut.sh gate api-dump` | `evidence/api-dump-final.txt`, `cut-v1.1.0.txt` (GATE OK api-dump, api-check); 474 insertions, 0 deletions vs v1.0.1 (20-VERIFICATION.md) | green |
| RT-07 | VER-07 | unit | `./gradlew --offline -q :sample:testDebugUnitTest --tests '*KeyVaultTest' --tests '*SampleViewModelTest' --tests '*UiTagsTest'` | `20-03-SUMMARY.md` (fingerprint vector, no-echo test) | green |
| RT-08 | VER-07 | grep | `grep -n "PD-04" .planning/STATE.md` | STATE.md line 235 (PD-04 recorded) | green |
| RT-12 / D-02 | VER-07 | script + waiver | `scripts/verify-binary-diff.sh`; gate 8 waiver | `evidence/binary-diff.txt`, `binary-diff-waiver.txt`; override in 20-VERIFICATION.md (removed=12 false positive, verified in source) | green (waived) |
| C9/C11 | VER-07 | script | `VAE_DOCS_REQUIRE_PINNED_TAG=1 bash scripts/verify-docs-coverage.sh` | `20-QUIET-WINDOW-03.md`: `DOC COVERAGE OK checks=32 types=119`, exit 0 | green |
| C2 | VER-07 | live | `scripts/jitpack-live-probe.sh v1.1.0` | `evidence/live-probe-v1.1.0.txt`: `LIVE PROBE PASS ref=v1.1.0`, 5 coords | green |
| C4 | VER-07 | agent wiring | `scripts/agent-wiring-test.sh verify` | `WIRING-RERUN.md`, `20-QUIET-WINDOW-02b.md`: `WIRING TEST: PASS checks=13` on W 4bdb663 | green |
| Cut | VER-07 | script | `scripts/release-cut.sh cut v1.1.0` (15 gates) | `evidence/cut-v1.1.0.txt`: all GATE OK, `CUT OK tag=v1.1.0`, exit 0 | green |

------|-------------|-----------|-------------------|--------|
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

- [x] `.planning/releases/v1.1.0/WAIVER-PACKET.md` (selftest and gate 8 need it)
- [x] New selftest controls for the gate-12 new-module branch and the RT-05 hygiene plant
- [x] RT-07 tests (flip `KeyVaultTest` last-4 assertion; add fingerprint + no-echo tests)
- [x] Final `<m>/api.txt` commits (five files)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Instructions |
|----------|-------------|------------|--------------|
| Push main, tag push, ledger relay | VER-07 | Orchestrator handshakes (RT-09) | Master relays "pushing main <sha>", "tag ready v1.1.0 <sha>"; never commit section 11 |
| Waiver packet answers | VER-07 | Yahir decision (D-05) | Ask via orchestrator BEFORE the quiet window |
| Isolated-agent wiring test (C4) | VER-07 | Needs orchestrator dispatch of an isolated agent | `agent-wiring-test.sh prepare W` then `verify` |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] `nyquist_compliant: true` set by validate-phase after execution

**Approval:** approved 2026-10-07 (validate-phase --auto). Gaps: none automatable. Recorded WARNING: RT-01..RT-07 Gradle unit tests were not re-run during this audit (host memory constraint); they are backed by recorded green runs in plan SUMMARYs and rt-outcomes.txt. Relay 8 / REQUIREMENTS bookkeeping is an orchestrator hand-off, not a validation gap.
