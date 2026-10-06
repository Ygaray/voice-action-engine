---
phase: "15"
slug: planthenexecute-strategy
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-06"
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: `15-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0; hand-written fakes in `core/src/testFixtures` |
| **Config file** | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts` |
| **Quick run command** | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q :core:test --tests '*PlanThenExecute*'` |
| **Full suite command** | same GRADLE_OPTS, `./gradlew check --offline`, then `scripts/verify-docs-coverage.sh` and `scripts/review-api-surface.sh` |
| **Estimated runtime** | quick ~60-120 s; full ~10+ min (host-memory constrained: one Gradle run at a time) |

---

## Sampling Rate

- **After every task commit:** Run the single touched test class (quick command)
- **After every plan wave:** `:core:test :core:detekt :core:scanBannedConstructs` in ONE invocation
- **Before `/gsd-verify-work`:** Full `check` green, docs-coverage and api-surface scripts, wire test
- **Max feedback latency:** ~120 seconds per task

---

## Per-Task Verification Map

Filled by the planner from the plan task list; the requirement-to-test map is in `15-RESEARCH.md` § Phase Requirements → Test Map.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | PLAN-01 (tracer) | T-15-01, T-15-02 | one gate proposal per step, planning call id on every action | pipeline | `:core:test --tests '*PlanThenExecuteRunTest*' --tests '*ApiShapeTest*' --tests '*NoHardCodedConstantsTest*'` | ❌ W0 (created by the task) | ⬜ pending |
| 15-01-02 | 01 | 1 | (move) | T-15-05 | fault bytes exactly once, agentic unchanged | unit | `:core:test --tests '*AgenticLoop*' --tests '*ToolExecutorSeamTest*' --tests '*RedactionCanaryTest*' --tests '*PlanThenExecuteRunTest*'` | ✅ | ⬜ pending |
| 15-01-03 | 01 | 1 | PLAN-01, PLAN-05 | T-15-03, T-15-04 | guards before any call, byte-stable schema | unit + pipeline + docs | `:core:test --tests '*PlanSchemaTest*' --tests '*PlanThenExecuteRunTest*'`; `scripts/verify-docs-coverage.sh --only C20,C21` | ❌ W0 | ⬜ pending |
| 15-02-01 | 02 | 2 | PLAN-02 (D-04) | T-15-08 | literals never rewritten, no guessed value | unit (TDD) | `:core:test --tests '*PlanBindingTest*' --tests '*ApiShapeTest*'` | ❌ W0 | ⬜ pending |
| 15-02-02 | 02 | 2 | PLAN-02, PLAN-03 | T-15-07, T-15-09, T-15-11 | whole-plan validation before step 1, fixed codes | unit (TDD) | `:core:test --tests '*PlanParseTest*' --tests '*TraceTest*' --tests '*PlanThenExecuteRunTest*' --tests '*PlanSchemaTest*'` | ❌ W0 | ⬜ pending |
| 15-02-03 | 02 | 2 | PLAN-02, PLAN-03 | T-15-07, T-15-10 | committed-only binding, zero-side-effect lookup | pipeline + docs | `:core:test --tests '*PlanThenExecute*' --tests '*PlanBindingTest*' --tests '*PlanParseTest*'`; `scripts/verify-docs-coverage.sh --only C20,C21` | ❌ W0 | ⬜ pending |
| 15-03-01 | 03 | 3 | PLAN-03 (D-08) | T-15-13, T-15-14, T-15-15 | one replan, only before any write; fixed digest | pipeline (TDD) | `:core:test --tests '*PlanThenExecute*' --tests '*PlanParseTest*' --tests '*PlanBindingTest*' --tests '*NoHardCodedConstantsTest*'` | ❌ W0 | ⬜ pending |
| 15-03-02 | 03 | 3 | PLAN-03 (D-08 byte test) | T-15-16 | every call id answered | wire | `:providers:test --tests '*PlanThenExecuteWireTest*' --tests '*AgenticLoopWireTest*'` | ❌ W0 | ⬜ pending |
| 15-03-03 | 03 | 3 | PLAN-02 (D-04 probe harness) | T-15-17, T-15-18 | opt-in, outside check, ≤ 8 requests, counts only | build + skip proof | `:providers:compileTestKotlin :providers:livePlanProbe` (skipped without VAE_LIVE_PLAN) + check dry-run excludes it | ❌ W0 | ⬜ pending |
| 15-04-01 | 04 | 4 | PLAN-01, PLAN-04 (RT-01 point 4) | T-15-35, T-15-36 | never-run step ids only on the outcome that ends the command, count-only toString, public constructors and api.txt unchanged | unit + compat (TDD) | `:core:test --tests '*RemainingStepIdsTest*' --tests '*ApiShapeTest*' --tests '*EscalationSafetyTest*' --tests '*HeldCommitGuardsTest*' --tests '*HeldReportingTest*' --tests '*PlanThenExecute*' :core:apiCheck` | ❌ W0 | ⬜ pending |
| 15-04-02 | 04 | 4 | PLAN-01, PLAN-04 (D-07 amended by RT-01) | T-15-20, T-15-21 | stop at first hold, held never success; zero-commit hold escalates (suppressed), a hold after a commit is a terminal partial Completed | pipeline (TDD) | `:core:test --tests '*PlanThenExecuteHoldTest*' --tests '*PlanThenExecuteBindingTest*' --tests '*PlanThenExecuteReplanTest*' --tests '*HeldReportingTest*' --tests '*RemainingStepIdsTest*'`; `scripts/verify-docs-coverage.sh --only C20,C21` | ❌ W0 | ⬜ pending |
| 15-04-03 | 04 | 4 | PLAN-04 | T-15-19 | no later tier after a commit (failures suppressed, a hold never escalates) | pipeline | `:core:test --tests '*PlanThenExecuteSuppressionTest*' --tests '*EscalationSafetyTest*'` | ❌ W0 | ⬜ pending |
| 15-05-01 | 05 | 5 | PLAN-05, D-09 | T-15-23, T-15-26 | truncated plan never acted on | pipeline (TDD) | `:core:test --tests '*PlanThenExecuteOutcomeMappingTest*' --tests '*SingleShotOutcomeMappingTest*' --tests '*PlanThenExecuteReplanTest*'` | ❌ W0 | ⬜ pending |
| 15-05-02 | 05 | 5 | PLAN-03 (call count), D-12 | T-15-24 | ceilings before and after each call | pipeline | `:core:test --tests '*PlanThenExecuteLimitsTest*' --tests '*PlanThenExecute*'` | ❌ W0 | ⬜ pending |
| 15-05-03 | 05 | 5 | security (V7) | T-15-25 | no canary in any sink or digest (step ids reach the app only through remainingStepIds) | pipeline | `:core:test --tests '*PlanThenExecuteRedactionTest*' --tests '*RedactionCanaryTest*'` | ❌ W0 | ⬜ pending |
| 15-06-01 | 06 | 6 | PLAN-01..05 (surface), RT-01 point 4 | T-15-27, T-15-28 | only intended public members; remainingStepIds +-only against core/api.txt | dump review | `scripts/review-api-surface.sh --out <scratch>` (API SURFACE OK) plus the outcome-block diff in the plan's verify | ✅ | ⬜ pending |
| 15-06-02 | 06 | 6 | PLAN-01..05 (phase gate) | T-15-28 | api.txt unchanged, invariants intact | gate | `./gradlew check`; `scripts/verify-docs-coverage.sh`; `scripts/verify-repo-hygiene.sh`; git invariants | ✅ | ⬜ pending |
| 15-07-01 | 07 | 7 | PLAN-02 (D-04) | T-15-32 | no live call without a relayed approval | checkpoint (relay) | n/a (blocking relay) | n/a | ⬜ pending |
| 15-07-02 | 07 | 7 | PLAN-02 (D-04) | T-15-31, T-15-33, T-15-34 | ≤ 8 requests, keys only via with-test-keys | opt-in live / deferral | `with-test-keys --only anthropic,openai -- env VAE_LIVE_PLAN=1 ... :providers:livePlanProbe` (or recorded deferral) | ✅ (from 15-03) | ⬜ pending |

All Gradle commands use the host-safe recipe (`GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false ...`); the exact commands are in each plan's `<verify>`.

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `core/src/test/.../PlanTestSupport.kt` — neutral tools, plan-args builder, strategy helper
- [ ] New `PlanThenExecute*Test` / `PlanBindingTest` / `PlanSchemaTest` classes (PLAN-01..05)
- [ ] `providers/.../PlanThenExecuteWireTest.kt` and the opt-in `PlanBindingLiveProbeTest.kt` plus `livePlanProbe` task

*No framework install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live binding syntax on cheap models (D-04) | PLAN-02 | needs real provider keys, opt-in, host-only | `with-test-keys --only anthropic,openai -- env VAE_LIVE_PLAN=1 ... ./gradlew --offline :providers:livePlanProbe`; record in `15-LIVE-PROBE.md` or defer to Phase 19 |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` — leave `false` at plan time

**Approval:** pending
