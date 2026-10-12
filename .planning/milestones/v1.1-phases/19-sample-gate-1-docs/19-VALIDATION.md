---
phase: "19"
slug: "sample-gate-1-docs"
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-07"
---

# Phase 19 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`:sample`, `:core`, `:voice-adapter`, `:undo`); bash gates for `scripts/` |
| **Config file** | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; scripts under `scripts/` |
| **Quick run command** | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false :sample:testDebugUnitTest --tests '<pattern>'` |
| **Full suite command** | same flags: `./gradlew --offline :sample:testDebugUnitTest :voice-adapter:check :undo:check :core:check`, then `scripts/verify-sample-device-guard.sh`, `scripts/verify-docs-coverage.sh`, `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-stt-confinement.sh --selftest` |
| **Estimated runtime** | quick ~3 min; full suite ~25 min; heavy quiet-window gates 2.5 h or more |

---

## Sampling Rate

- **After every task commit:** the one relevant Gradle test filter (flags above) or the one relevant bash gate; `bash -n` on every touched script.
- **After every plan wave:** `:sample:testDebugUnitTest` in full plus the four bash verifiers.
- **Before `/gsd-verify-work`:** full suite green; heavy gates only inside the granted quiet window.
- **Max feedback latency:** ~180 seconds for the quick command.

---

## Per-Task Verification Map

Populated from the PLAN.md files by the planner / finalizer. Requirement-level map (from RESEARCH.md):

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| VER-06 | grammar leg: EN/ES complete, near-miss `Unhandled(cappedByPolicy=true)`, zero turns/attempts/tripwire calls | unit (JVM, fakes) | `:sample:testDebugUnitTest --tests '*GrammarLeg*'` | yes | green |
| VER-06 | plan leg binding and store state; verdict classifier | unit | `--tests '*PlanLeg*'` | yes | green |
| VER-06 | router leg: at least 2 tiers, picked selection, tokens in `trace.usage`; one-tier ladder FAILs | unit | `--tests '*RouterLeg*'` | yes | green |
| VER-06 | undo-all: complete, refused, plan partial; N counts applied actions | unit | `--tests '*UndoLeg*' --tests '*UndoEndToEnd*'` | yes | green |
| VER-06 | new evidence types fit `ALLOW_PATTERN`, golden lockstep, no free text | unit + bash | `--tests '*EvidenceLine*'`; `scripts/verify-sample-device-guard.sh` | yes | green |
| VER-06 | decision-file env/derivation, `consumed` refuses key push, LEGS parity | bash (fake adb) | `scripts/verify-sample-device-guard.sh` | yes | green |
| VER-06 | live TESTER evidence per leg plus D-13 line | device (non-autonomous) | runner `capture-save` then `scripts/sample-evidence-filter.sh < file` | yes | green |
| DOC-02 | every doc Kotlin block equals a compiled region | unit + bash | `:sample:testDebugUnitTest --tests '*DocSnippets*'`; `scripts/verify-docs-coverage.sh` | yes | green |
| DOC-02 | five-module coordinate/type coverage, new-tier content, RT-01/RT-04 wording | bash | `scripts/verify-docs-coverage.sh` (+ `--selftest`) | yes | green |
| DOC-02 | RT-04 overloads gone | unit (reflection) | `:voice-adapter:testDebugUnitTest --tests '*AdapterApiShape*'` | yes | green |
| DOC-02 | fresh-agent wiring PASS | isolated agent + judge | `scripts/agent-wiring-test.sh selftest`, then `prepare-local` and `verify` | yes | green |
| RT-02/RT-03 | dry run (five artifacts, `:undoalone`, `:adapteralone`), negative controls, `:voice-adapter:check` | heavy bash (quiet window) | `scripts/jitpack-dry-run.sh`, `scripts/verify-negative-controls.sh` | yes | green |

*Status: pending / green / red / flaky*

Per-plan map (planner, 2026-10-07; every task's `<verify>` carries an `<automated>` command and a `<fails_when>`):

| Plan | Wave | Requirement | Automated gate of the plan | Gradle? |
|------|------|-------------|----------------------------|---------|
| 19-01 | 1 | VER-06 | `scripts/verify-sample-device-guard.sh` (>= 38 scenarios) | no |
| 19-02 | 1 | DOC-02 (RT-03a) | `bash -n` + `scripts/verify-stt-confinement.sh` + `scripts/verify-release-manifest.sh` + wiring-gate diagnostic | no |
| 19-03 | 1 | DOC-02 (RT-04) | `:voice-adapter:check` + `scripts/verify-docs-coverage.sh` | yes |
| 19-04 | 2 | VER-06 | `:sample:testDebugUnitTest` (`*GrammarLegTest*`, `*EvidenceLineTest*`) + guard | yes |
| 19-05 | 3 | VER-06 | `:sample:testDebugUnitTest` (`*PlanLegTest*`, `*RouterLegTest*`, full suite) + guard | yes |
| 19-06 | 4 | VER-06 | `:sample:testDebugUnitTest` (`*UndoLegTest*`, full suite) + guard + hygiene | yes |
| 19-07 | 5 | VER-06 (D-13) | evidence greps + `scripts/sample-evidence-filter.sh` re-scan (device, checkpoint) | build-install |
| 19-08 | 6 | DOC-02 (D-12) | `scripts/api-dump-isolated.sh` + `scripts/review-api-surface.sh --module` x5 + planted enum | yes |
| 19-09 | 7 | DOC-02 | `:sample:testDebugUnitTest --tests '*DocSnippetsTest*'` + `scripts/verify-docs-coverage.sh` | yes |
| 19-10 | 8 | DOC-02 (RT-01) | `scripts/verify-docs-coverage.sh` (checks=32) + `--selftest` | no |
| 19-11 | 9 | DOC-02 (D-11) | memory guard, then `scripts/agent-wiring-test.sh selftest --local` (checks=13) + `prepare-local` | yes (memory-guarded exception, hard stop) |
| 19-12 | 10 | VER-06, DOC-02 | full autonomous gate (memory guard before each of the `check` x2, every bash gate and selftest) + Gate-1 build delta recompute (07 capture head to the candidate) | yes (memory-guarded exception, hard stop) |
| 19-13 | 11 | DOC-02, VER-06 (RT-02, RT-03) | quiet window opened ("quiet window 19-13"): dry run with `:adapteralone`, clean-clone selftest, `:voice-adapter:check`, API dump proof, negative controls, live-probe exercise; handoff `heavy_gates: green` + `kept_m2` (closes the window itself on red) | yes (heavy) |
| 19-14 | 12 | DOC-02 | same window, no second request: `prepare-local`, master-dispatched isolated agent, `agent-wiring-test.sh verify` (PASS checks=13), record + `release-cut.sh gate wiring`, window close | yes (agent verify build, in the window) |

---

## Wave 0 Requirements

- [x] `GrammarLegTest`, `PlanLegTest`, `RouterLegTest`, `UndoLegTest` under `sample/src/test/kotlin/.../sample/` - VER-06
- [x] `EvidenceLineTest` plus golden-file extensions and guard counts - VER-06
- [x] guard scenarios for the decision-file override / derived name / `consumed` - VER-06
- [x] docs-coverage selftest and new checks - DOC-02
- [x] wiring assets relocation, new prompt/reference/judge checks, `prepare-local` - DOC-02
- [x] `AdapterApiShapeTest` update and reflection proof for RT-04

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live TESTER legs (grammar offline, plan, router, undo-all, D-13 W04 smoke) | VER-06 | needs the wired TESTER R5CT10XNKQN and spend-capped live keys; master-relayed checkpoints | run via `scripts/run-sample-gate1.sh` after master GO; capture evidence file; filter with `scripts/sample-evidence-filter.sh` |
| Heavy host gates (jitpack dry-run, `:undoalone`/`:adapteralone` probes, negative controls) and the isolated agent run | RT-02, RT-03, DOC-02 | host memory (earlyoom); needs orchestrator quiet window | one handshake "quiet window 19-13" covering plans 13 and 14; MemAvailable >= 5 GiB |

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-10-07): `status: validated`, `nyquist_compliant: true`.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 180s for the quick command
- [x] _(finalizer-only, post-execution)_ `nyquist_compliant` set `true`: zero automatable gaps

**Approval:** approved by the Nyquist finalizer 2026-10-07 (zero automatable gaps)

---

## Validation Audit 2026-10-07

Auto-mode audit (no auditor spawned, no tests generated). Every requirement row maps to an automated command or committed device evidence that ran green in this phase: `:sample:check`, `:voice-adapter:check`, `:core/:undo/:providers/:keystore :check` (exit 0 on HEAD ac62054), `verify-sample-device-guard.sh` (scenarios=43), `verify-docs-coverage.sh` (checks=32, selftest plants=13), `agent-wiring-test.sh selftest` and `selftest-source` OK, `scripts/jitpack-dry-run.sh` (DRY RUN OK, PROBE OK incl. :adapteralone), `verify-api-dump.sh` (API DUMP PROOF OK), `verify-negative-controls.sh` (failures: 0), isolated wiring PASS checks=13 on 090fd8ec76, and five TESTER Gate-1 evidence files (all PASS). The live TESTER legs and the heavy host gates remain Manual-Only by design (device and quiet window) and are evidenced, not automatable in CI. Review fixes WR-01..WR-04 have targeted coverage (guard scenarios 43, docs selftest 13 plants, `selftest-source`, `PlanLegTest.anExplicitNullParentIsTheSameAsNoParent`).

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Caveat carried to Phase 20: the negative-control suite and API dump proof were last run on the wiring SHA candidate 090fd8ec76; the later review fixes touched only scripts and one sample file (re-checked by the targeted gates above).

