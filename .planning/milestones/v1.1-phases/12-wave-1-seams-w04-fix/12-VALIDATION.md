---
phase: "12"
slug: "wave-1-seams-w04-fix"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-05"
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0; legacy `okhttp3.mockwebserver` in `:providers`; hand-written fakes in `core/src/testFixtures` |
| **Config file** | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts`; `scripts/verify-docs-coverage.sh` |
| **Quick run command** | `./gradlew :core:test --tests '<Class>' -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false` (per module) |
| **Full suite command** | `./gradlew check -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false` then `scripts/verify-docs-coverage.sh` |
| **Estimated runtime** | quick ~60 s; full ~10 min (host memory tight, never parallel) |

---

## Sampling Rate

- **After every task commit:** Run the single touched test class for its module
- **After every plan wave:** `./gradlew :core:test :providers:test :keystore:testDebugUnitTest` plus `scripts/verify-docs-coverage.sh` once docs exist
- **Before `/gsd-verify-work`:** `./gradlew check` green (OkHttp matrix legs included), docs gate OK, `scripts/verify-negative-controls.sh` for the new opt-in plant, `scripts/verify-sample-device-guard.sh` after the runner retarget, then the single live device run
- **Max feedback latency:** 120 seconds (per-task quick run)

---

## Per-Task Verification Map

Seeded from RESEARCH.md "Phase Requirements -> Test Map"; task IDs bound by the planner (plan-task). Every verify command runs Gradle with `--offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false`. SEAM-07's negative-compile proof is `scripts/verify-keyaccess-opt-in.sh` (called by `scripts/verify-negative-controls.sh` Part 4). PROV-16's device run (12-08-T3) is agent-driven on the TESTER after the orchestrator relay; its JVM judge test is 12-05-T1.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-02-T3 | 12-02 | 1 | SEAM-01 | — | hook never fires for ceiling/gate/strategy_error | unit | `./gradlew :core:test --tests '*SingleShotOutcomeMappingTest*'` | ✅ extend | ✅ green |
| 12-02-T1, 12-02-T2 | 12-02 | 1 | SEAM-02 | — | OFF default keeps wire bytes unchanged | unit/golden | `./gradlew :core:test --tests '*SingleShotRequestTest*' --tests '*TranscriptTypesTest*'`; `:providers:test --tests '*AnthropicEncoderTest*' --tests '*ChatEncoderTest*'` | ✅ extend | ✅ green |
| 12-01-T3 | 12-01 | 1 | SEAM-03 | — | N/A | unit | `./gradlew :providers:test --tests '*AnthropicModelsTest*' --tests '*ChatModelsTest*'` | ✅ extend | ✅ green |
| 12-03-T1, 12-03-T2 | 12-03 | 1 | SEAM-04 | — | carryIn is a boolean, never content | unit | `./gradlew :core:test --tests '*TierWalkTest*' --tests '*TraceTest*' --tests '*InFlightTierTraceTest*'` | ✅ extend | ✅ green |
| 12-03-T3 | 12-03 | 1 | SEAM-05 | — | N/A | unit | `./gradlew :core:test --tests '*TierPolicyTest*' --tests '*TierWalkTest*'` | ✅ extend | ✅ green |
| 12-06-T1..T3 | 12-06 | 2 | SEAM-06 | T: callId leaks | toString/redaction unchanged | unit | `./gradlew :core:test --tests '*CommitPathTest*' --tests '*HeldReportingTest*' --tests '*SingleShotResolveTest*' --tests '*AgenticLoopDispatchTest*' --tests '*RedactionCanaryTest*'` | ✅ extend | ✅ green |
| 12-04-T1, 12-04-T2, 12-07-T1 | 12-04, 12-07 | 1, 3 | SEAM-07 | T: key access misuse | opt-in required; compile fails without it | unit + negative-compile | `./gradlew :keystore:testDebugUnitTest`; `scripts/verify-negative-controls.sh` | ❌ W0 | ✅ green |
| 12-01-T1, 12-01-T2 | 12-01 | 1 | PROV-14 | — | Responses-only ids never get `reasoning_effort` | unit/golden | `./gradlew :providers:test --tests '*OpenAiModelRulesTest*' --tests '*ChatEncoderTest*'` | ✅ extend | ✅ green |
| 12-01-T1 | 12-01 | 1 | PROV-15 | T: body leakage | W04 400 -> `model_unsupported`, no body in details | unit + MockWebServer | `./gradlew :providers:test --tests '*ChatErrorMapTest*' --tests '*ChatTransportTest*'` then `:providers:testOkhttp521` / `:providers:testOkhttp550` | ✅ extend | ✅ green |
| 12-05-T1..T3, 12-08-T1..T3 | 12-05, 12-08 | 1, 4 | PROV-16 | T: key/spend | bounded request count, spend-capped key | JVM judge test + manual device | `./gradlew :sample:testDebugUnitTest --tests '*SmokeLegTest*'` | ✅ extend | ✅ green |
| 12-07-T1..T3 | 12-07 | 3 | DOC-01 | — | N/A | gate | `scripts/verify-docs-coverage.sh`; `./gradlew :sample:testDebugUnitTest --tests '*DocSnippetsTest*'` | ✅ extend | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Negative-compile plant for the `DelicateKeyAccess` opt-in in `scripts/verify-negative-controls.sh` (`:sample` consumer file) — SEAM-07
- [x] `DocSnippetsTest` region `keystore-fake` + JVM round-trip `@Test` — SEAM-07 / DOC-01
- [x] `ModelRequest` 7-arg-ctor-exists and `Extraction` 2-arg-ctor-exists reflection tests — SEAM-02 / SEAM-06 additivity
- [x] `12-LIVE-LEG-DECISION.md` (orchestrator relay) — blocks the PROV-16 device run only

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live `gpt-6-astra` smoke returns typed `ModelUnsupported` | PROV-16 | Only proof of the real wire; needs the wired TESTER and the spend-capped OpenAI test key | Per runbook: `scripts/run-sample-gate1.sh preflight`, then the bounded RESPONSES_PROBE leg via the test-keys workflow |

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-10-05).

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] _(finalizer-only, post-execution)_ `nyquist_compliant: true`

**Approval:** validated 2026-10-05 (automated finalizer, --auto)

---

## Validation Audit 2026-10-05

| Metric | Count |
|--------|-------|
| Requirements audited | 11 (SEAM-01..07, PROV-14..16, DOC-01) |
| Gaps found (automatable) | 0 |
| Resolved | 0 |
| Escalated | 0 |

Evidence the mapped commands ran green this phase (12-VERIFICATION.md, 12-SELF-UAT.md): core 694, providers 609 per OkHttp leg (4.12.0, 5.2.1, 5.5.0), keystore 105, sample 147 tests, 0 failures; detekt, Metalava apiCheck, verify-docs-coverage (25 checks), verify-keyaccess-opt-in (Part 4) and verify-sample-device-guard (33 scenarios) green. PROV-16's live leg is Manual-Only and was run on the TESTER (evidence/gate1-responses_probe.txt: verdict=PASS reason=model_unsupported http=400), with its JVM judge (SmokeLegTest) green.
Note: scripts/verify-negative-controls.sh Part 1 is stale since the v1.0.0 cut (not a Phase 12 gap; Part 4 passes).
