---
phase: "02"
slug: "core-contract-pipeline-commit-seam"
status: validated
nyquist_compliant: true
wave_0_complete: true
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
| CORE-01 | TBD | TBD | - | build-time misconfig rejected | unit | `./gradlew :core:test --tests '*PipelineBuilderTest'` | yes | green |
| CORE-02 | TBD | TBD | - | carry handed to next tier | unit | `./gradlew :core:test --tests '*TierWalkTest'` | yes | green |
| CORE-03 | TBD | TBD | - | unknown/excluded tier -> NoEligibleTier | unit | `./gradlew :core:test --tests '*TierSelectorTest'` | yes | green |
| CORE-04 | TBD | TBD | - | offlineOnly -> zero provider calls | unit | `./gradlew :core:test --tests '*TierPolicyTest'` | yes | green |
| CORE-05 | TBD | TBD | - | never throws; cancellation propagates | unit | `./gradlew :core:test --tests '*NeverThrowTest'` | yes | green |
| CORE-06 | TBD | TBD | - | failures carry no body | unit | `./gradlew :core:test --tests '*FailureTaxonomyTest'` | yes | green |
| CORE-07 | TBD | TBD | - | additive-only surface shape | unit+script | `./gradlew :core:test --tests '*ApiShapeTest'` and `scripts/review-api-surface.sh` | yes | green |
| CORE-08 | TBD | TBD | - | terminal tools non-mutating | unit | `./gradlew :core:test --tests '*TerminalCallTest'` | yes | green |
| CORE-09 | TBD | TBD | - | parentRunId linkage | unit | `./gradlew :core:test --tests '*ParentRunIdTest'` | yes | green |
| GATE-01 | TBD | TBD | - | gate -> apply -> sink order | unit | `./gradlew :core:test --tests '*CommitPathTest'` | yes | green |
| GATE-02 | TBD | TBD | - | fail-closed suspend/defer modes | unit | `./gradlew :core:test --tests '*AwaitingConfirmGateTest' --tests '*DeferModeTest'` | yes | green |
| GATE-03 | TBD | TBD | - | held never reported as success | unit | `./gradlew :core:test --tests '*HeldReportingTest'` | yes | green |
| GATE-04 | TBD | TBD | - | ActionEvent payload per D-11 | unit | `./gradlew :core:test --tests '*ActionEventTest' --tests '*BatchIsolationTest'` | yes | green |
| GATE-05 | TBD | TBD | - | onRunClosed exactly once on 5 paths | unit | `./gradlew :core:test --tests '*RunClosedPathsTest'` | yes | green |
| GATE-06 | TBD | TBD | - | ordered executed list | unit | `./gradlew :core:test --tests '*ExecutedListTest'` | yes | green |
| GATE-07 | TBD | TBD | - | no duplicate write after commit+escalate | unit | `./gradlew :core:test --tests '*EscalationSafetyTest'` | yes | green |
| TEL-01 | TBD | TBD | - | trace shape, redaction | unit | `./gradlew :core:test --tests '*TraceTest'` | yes | green |
| TEL-02 | TBD | TBD | - | live events, throwing listener harmless | unit | `./gradlew :core:test --tests '*EventsTest'` | yes | green |
| (TEL-04 slice) | TBD | TBD | - | canaries never in toString/trace/events | unit | `./gradlew :core:test --tests '*RedactionCanaryTest'` | yes | green |

*Status: pending · green · red · flaky*

---

## Wave 0 Requirements

- [x] `core/src/testFixtures/.../testing/ScriptedStrategy.kt` - scripted fake `CommandStrategy`
- [x] `core/src/testFixtures/.../testing/` recording gate/sink/listener, fake clock, fixed id generator
- [x] All test classes named above
- [x] `scripts/review-api-surface.sh` - isolated-copy `apiDump` + sealed allow-list / `copy(` / stray static-final grep
- [x] Framework install: none

---

## Manual-Only Verifications

All phase behaviors have automated verification. (Human read of `api.txt` from the isolated-copy dump is a phase-gate review step, not a behavior test; no `api.txt` is committed until the v1.0.0 cut.)

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-09-30).

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant` set true by finalizer

**Approval:** approved 2026-09-30 (Nyquist finalizer)

---

## Validation Audit 2026-09-30

| Metric | Count |
|--------|-------|
| Requirements audited (CORE-01..09, GATE-01..07, TEL-01..02, TEL-04 slice) | 19 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Evidence: `./gradlew :core:test --rerun-tasks` exit 0; 233 tests, 0 failures/skipped. Every class named in the map ran green (junit XML under `core/build/test-results/test`); `scripts/review-api-surface.sh` and the other verify scripts exist. Wave 0 fixtures (`core/src/testFixtures`) present.

Manual-only: none for this phase. Real-provider, prompt-cache and on-device behaviour belong to later phases (providers/keystore/sample) and are out of Phase 2 scope.
