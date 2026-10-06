---
phase: "16"
slug: start-tier-selection
status: complete
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-06"
---

# Phase 16 — Validation Strategy

> Per-phase validation contract. Source: `16-RESEARCH.md` § Validation Architecture. Plan-time DRAFT: finalized post-execution by the Nyquist finalizer.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0; hand-written fakes in `core/src/testFixtures` |
| **Config file** | `core/build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts` |
| **Quick run command** | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q :core:test --tests '*StartTier*'` |
| **Full suite command** | same GRADLE_OPTS, `./gradlew check --offline`, then `scripts/verify-docs-coverage.sh` and `scripts/review-api-surface.sh` |
| **Estimated runtime** | quick ~60-120 s; full ~10+ min (host-memory constrained: one Gradle run at a time) |

---

## Sampling Rate

- **After every task commit:** the single touched test class (quick command).
- **After every plan wave:** `:core:test :core:detekt :core:scanBannedConstructs` in ONE invocation.
- **Before `/gsd-verify-work`:** full `check`, `verify-docs-coverage.sh`, `review-api-surface.sh`, `verify-repo-hygiene.sh` green.
- **Max feedback latency:** ~120 s per task.

---

## Per-Task Verification Map

Task rows are filled by the planner/executor from the PLAN.md files; requirement-level map:

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| guard | v1.0.1 Linear/Fixed walk unchanged | characterization | `:core:test --tests '*TierWalkLinearCharacterizationTest*'` | yes | green |
| ROUT-01 | Custom picker, eligible LLM ids, trace/budget accounting | pipeline | `:core:test --tests '*StartTierPickerTest*'` | yes | green |
| ROUT-02 | zero-call head free pre-pass | pipeline | `:core:test --tests '*StartTierPrePassTest*'` | yes | green |
| ROUT-03 | null/ineligible/throw/timeout -> Linear + router_fallback; cancellation propagates | pipeline | `:core:test --tests '*StartTierFallbackTest*'` | yes | green |
| ROUT-04 | no eligible LLM tier -> picker never called | pipeline | `:core:test --tests '*StartTierPolicyTest*'` | yes | green |
| ROUT-05 | Router off by default, request shape pinned, tiersBypassed | pipeline + golden | `:core:test --tests '*RouterSelectorTest*'` | yes | green |
| RT-01 | step id cap 64 | unit | `:core:test --tests '*PlanParseTest*'` | yes | green |
| WR-01 | late picker turn (recorded after pick closed) is in neither attempts nor selection turns; still counted in ctx.tokensUsed | pipeline | `:core:test --tests '*StartTierFallbackTest*'` | yes (added at finalize) | green |
| docs | API.md names new public types | doc gate | `scripts/verify-docs-coverage.sh --only C20,C21` | yes | green (Gate-1 doc gate) |

---

## Wave 0 Requirements

- [x] `TierWalkLinearCharacterizationTest` — green on the unmodified TierWalk, committed first
- [x] `ScriptedPicker` testFixture
- [x] `StartTierPickerTest`, `StartTierPrePassTest`, `StartTierFallbackTest`, `StartTierPolicyTest`, `RouterSelectorTest`, `StartTierRedactionTest`

---

## Manual-Only Verifications

All phase behaviors have automated JVM verification. Any device step needs a separately granted TESTER window; any live provider spend needs a relayed orchestrator GO with a request/USD ceiling (both non-autonomous).

---

## Validation Sign-Off

> Plan-time state is a DRAFT. `nyquist_compliant` is finalizer-owned.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] _(finalizer-only)_ `nyquist_compliant: true`

**Approval:** 2026-10-06 finalized. Audit: 1 gap found (WR-01, c94bd9d, untested), 1 filled (`StartTierFallbackTest.aPickerTurnRecordedAfterThePickClosed...`). 76 targeted tests + `:core:detekt` green. Note: the late turn is excluded from `trace.usage` (derived from attempts/selection turns) but counted in the run token total (`PickContext.tokensUsed`).
