---
phase: "02"
slug: "core-contract-pipeline-commit-seam"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-30"
---

# Phase 2 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source of truth for test names: `02-RESEARCH.md` `## Validation Architecture`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`, virtual time); hand-written fakes |
| **Config file** | `core/build.gradle.kts`, `config/detekt/detekt.yml` (no new config) |
| **Quick run command** | `./gradlew :core:test --tests '<class>' -q` |
| **Full suite command** | `./gradlew check` (module: `./gradlew :core:check -q`) |
| **Estimated runtime** | ~45 seconds warm (Phase 1 recorded) |

---

## Sampling Rate

- **After every task commit:** the touched test class via `./gradlew :core:test --tests '<class>' -q`, plus `./gradlew :core:detekt -q` when main sources changed
- **After every plan wave:** `./gradlew :core:check :providers:check -q`
- **Before `/gsd-verify-work`:** `./gradlew check` green; `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh` still green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

Task-level rows are owned by the PLAN.md files (each task carries an `<automated>` command). Requirement-level map:

| Requirement | Plan | Wave | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|------|------|------------|-----------------|-----------|-------------------|-------------|--------|
| CORE-01 | TBD | TBD | - | build-time misconfig rejected | unit | `./gradlew :core:test --tests '*PipelineBuilderTest'` | W0 | pending |
| CORE-02 | TBD | TBD | - | carry handed to next tier | unit | `./gradlew :core:test --tests '*TierWalkTest'` | W0 | pending |
| CORE-03 | TBD | TBD | - | unknown/excluded tier -> NoEligibleTier | unit | `./gradlew :core:test --tests '*TierSelectorTest'` | W0 | pending |
| CORE-04 | TBD | TBD | - | offlineOnly -> zero provider calls | unit | `./gradlew :core:test --tests '*TierPolicyTest'` | W0 | pending |
| CORE-05 | TBD | TBD | - | never throws; cancellation propagates | unit | `./gradlew :core:test --tests '*NeverThrowTest'` | W0 | pending |
| CORE-06 | TBD | TBD | - | failures carry no body | unit | `./gradlew :core:test --tests '*FailureTaxonomyTest'` | W0 | pending |
| CORE-07 | TBD | TBD | - | additive-only surface shape | unit+script | `./gradlew :core:test --tests '*ApiShapeTest'` and `scripts/review-api-surface.sh` | W0 | pending |
| CORE-08 | TBD | TBD | - | terminal tools non-mutating | unit | `./gradlew :core:test --tests '*TerminalCallTest'` | W0 | pending |
| CORE-09 | TBD | TBD | - | parentRunId linkage | unit | `./gradlew :core:test --tests '*ParentRunIdTest'` | W0 | pending |
| GATE-01 | TBD | TBD | - | gate -> apply -> sink order | unit | `./gradlew :core:test --tests '*CommitPathTest'` | W0 | pending |
| GATE-02 | TBD | TBD | - | fail-closed suspend/defer modes | unit | `./gradlew :core:test --tests '*AwaitingConfirmGateTest' --tests '*DeferModeTest'` | W0 | pending |
| GATE-03 | TBD | TBD | - | held never reported as success | unit | `./gradlew :core:test --tests '*HeldReportingTest'` | W0 | pending |
| GATE-04 | TBD | TBD | - | ActionEvent payload per D-11 | unit | `./gradlew :core:test --tests '*ActionEventTest' --tests '*BatchIsolationTest'` | W0 | pending |
| GATE-05 | TBD | TBD | - | onRunClosed exactly once on 5 paths | unit | `./gradlew :core:test --tests '*RunClosedPathsTest'` | W0 | pending |
| GATE-06 | TBD | TBD | - | ordered executed list | unit | `./gradlew :core:test --tests '*ExecutedListTest'` | W0 | pending |
| GATE-07 | TBD | TBD | - | no duplicate write after commit+escalate | unit | `./gradlew :core:test --tests '*EscalationSafetyTest'` | W0 | pending |
| TEL-01 | TBD | TBD | - | trace shape, redaction | unit | `./gradlew :core:test --tests '*TraceTest'` | W0 | pending |
| TEL-02 | TBD | TBD | - | live events, throwing listener harmless | unit | `./gradlew :core:test --tests '*EventsTest'` | W0 | pending |
| (TEL-04 slice) | TBD | TBD | - | canaries never in toString/trace/events | unit | `./gradlew :core:test --tests '*RedactionCanaryTest'` | W0 | pending |

*Status: pending · green · red · flaky*

---

## Wave 0 Requirements

- [ ] `core/src/testFixtures/.../testing/ScriptedStrategy.kt` - scripted fake `CommandStrategy`
- [ ] `core/src/testFixtures/.../testing/` recording gate/sink/listener, fake clock, fixed id generator
- [ ] All test classes named above
- [ ] `scripts/review-api-surface.sh` - isolated-copy `apiDump` + sealed allow-list / `copy(` / stray static-final grep
- [ ] Framework install: none

---

## Manual-Only Verifications

All phase behaviors have automated verification. (Human read of `api.txt` from the isolated-copy dump is a phase-gate review step, not a behavior test; no `api.txt` is committed until the v1.0.0 cut.)

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> Finalized ONLY post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` - leave `false` at plan time

**Approval:** pending (finalizer-owned, not set at plan time)
