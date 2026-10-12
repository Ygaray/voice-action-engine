---
phase: "18"
slug: voice-adapter
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-06"
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 (AGP unit tests `testDebugUnitTest`); no Robolectric, no coroutines-test |
| **Config file** | `voice-adapter/build.gradle.kts` (Wave 0 creates); gates in `gradle/invariants.gradle.kts`; `config/detekt/detekt.yml` |
| **Quick run command** | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline :voice-adapter:testDebugUnitTest` |
| **Full suite command** | same flags: `./gradlew --offline :voice-adapter:check :core:check`, then `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-docs-coverage.sh` |
| **Estimated runtime** | ~300 seconds (Gradle, host memory tight) |

---

## Sampling Rate

- **After every task commit:** the single most relevant quick command (one Gradle task) or the bash gate for plumbing tasks
- **After every plan wave:** `:voice-adapter:check :core:check` (one Gradle invocation)
- **Before `/gsd-verify-work`:** full suite green plus `scripts/verify-api-seed.sh voice-adapter`
- **Max feedback latency:** 600 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 18-01..03 | TBD | TBD | ADPT-01 | transcript leak | text verbatim, segment never stringified | unit | `:voice-adapter:testDebugUnitTest --tests '*FinalSegmentMappingTest*'` | ✅ | ✅ green |
| 18-01..03 | TBD | TBD | ADPT-01 | mislabeled language | closed set en/es/null, never a default | unit | `--tests '*LanguageLabelsTest*'` | ✅ | ✅ green |
| 18-01..03 | TBD | TBD | ADPT-01 | transcript leak | no sentinel in `CommandInput.toString()` | unit | `--tests '*RedactionTest*'` | ✅ | ✅ green |
| 18-01/18-05 | TBD | TBD | ADPT-01 | API freeze | public surface exactly intended | gate | `scripts/verify-api-seed.sh voice-adapter` | ✅ | ✅ green |
| 18-04/18-07 | TBD | TBD | ADPT-01 | hub leakage | POM/module.json name no `voice-engine-android`; runtime classpath has no :stt | gate | `:voice-adapter:verifyAdapterSttCompileOnly` | ✅ | ✅ green |
| 18-04 | TBD | TBD | ADPT-01 | hub leakage | no other module resolves the :stt group | gate | `verifySttConfined` on core/providers/keystore/undo | ✅ | ✅ green |
| 18-04 | TBD | TBD | ADPT-01 | hub leakage | :core still depends on no hub | gate | `:core:verifyCoreDependencyAllowlist :voice-adapter:verifyModuleGraph` | ✅ | ✅ green |
| 18-02 | TBD | TBD | ADPT-01 | manifest drift | manifest consistent incl. new module | bash | `scripts/verify-module-manifest.sh` | ✅ | ✅ green |
| 18-06 | TBD | TBD | RT-01 | toString leak | sentinel never in `ActionEvent.toString()` | unit | `:core:test --tests '*ActionEventTest*'` | ✅ add test | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `voice-adapter/` module (build file, api.txt seed, source + test dirs)
- [x] `scripts/modules.list` row, `settings.gradle.kts` include + exclusiveContent, `allowedEdges`, `jitpack.yml` line
- [x] `verifyAdapterSttCompileOnly` and `verifySttConfined` gates and their negative controls
- [x] `scripts/verify-api-seed.sh` aar task-name branch
- [x] ML-denial scope list gains `voice-adapter`
- [x] `ActionEventTest` sentinel test

---

## Manual-Only Verifications

All phase behaviors have automated verification. Heavy jitpack dry-run and negative-controls runs need an orchestrator quiet window (non-autonomous checkpoint). No device step.

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-10-06).

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 600s
- [x] _(finalizer-only)_ `nyquist_compliant: true`

**Approval:** validated 2026-10-06

---

## Validation Audit 2026-10-06

Evidence: 18-VERIFICATION.md (passed, 2/2 SC, ADPT-01 satisfied), 18-SELF-UAT.md (all_pass), 18-SECURITY.md (secured),
18-QUIET-WINDOW.md (negative controls 0 failures, API DUMP PROOF OK, DRY RUN OK, PROBE OK), 18-REVIEW-FIX.md
(`:voice-adapter` 33 tests green, `:core` ActionEvent tests green). All nine map rows resolve to an existing test or gate
(`FinalSegmentMappingTest`, `LanguageLabelsTest`, `RedactionTest`, `AdapterApiShapeTest`, `SttFreeFacadeTest`,
`ActionEventTest`; gates `verifyAdapterSttCompileOnly`, `verifySttConfined`, `verify-api-seed.sh`,
`verify-module-manifest.sh`). No Gradle run by this audit (results are the recorded ones).

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Non-automatable / carried (not gaps against ADPT-01 or SC1-2): clean-cache `:adapteralone` consumer probe (owner Phase 19
gate / Phase 20 dry run); wiring `verify-stt-confinement.sh` into release-cut (Phase 20); `:voice-adapter:check` and the
negative-control suite were last run on e15bd36, not the final SHA (re-run by Phase 19 gate / Phase 20 cut).
