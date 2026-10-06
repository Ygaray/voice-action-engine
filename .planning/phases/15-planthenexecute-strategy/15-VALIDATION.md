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
| 15-xx-xx | xx | x | PLAN-01..05 | see plans | per-step gate, bounded plan, no leaks | unit / pipeline / wire | see plans | ❌ W0 | ⬜ pending |

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
