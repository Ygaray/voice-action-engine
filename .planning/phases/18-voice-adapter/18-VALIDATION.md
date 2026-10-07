---
phase: "18"
slug: voice-adapter
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 18-xx | TBD | TBD | ADPT-01 | transcript leak | text verbatim, segment never stringified | unit | `:voice-adapter:testDebugUnitTest --tests '*FinalSegmentMappingTest*'` | ❌ W0 | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | mislabeled language | closed set en/es/null, never a default | unit | `--tests '*LanguageLabelsTest*'` | ❌ W0 | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | transcript leak | no sentinel in `CommandInput.toString()` | unit | `--tests '*RedactionTest*'` | ❌ W0 | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | API freeze | public surface exactly intended | gate | `scripts/verify-api-seed.sh voice-adapter` | ❌ W0 | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | hub leakage | POM/module.json name no `voice-engine-android`; runtime classpath has no :stt | gate | `:voice-adapter:verifyAdapterSttCompileOnly` | ❌ W0 | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | hub leakage | no other module resolves the :stt group | gate | `verifySttConfined` on core/providers/keystore/undo | ❌ W0 | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | hub leakage | :core still depends on no hub | gate | `:core:verifyCoreDependencyAllowlist :voice-adapter:verifyModuleGraph` | ✅ | ⬜ pending |
| 18-xx | TBD | TBD | ADPT-01 | manifest drift | manifest consistent incl. new module | bash | `scripts/verify-module-manifest.sh` | ✅ | ⬜ pending |
| 18-xx | TBD | TBD | RT-01 | toString leak | sentinel never in `ActionEvent.toString()` | unit | `:core:test --tests '*ActionEventTest*'` | ✅ add test | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `voice-adapter/` module (build file, api.txt seed, source + test dirs)
- [ ] `scripts/modules.list` row, `settings.gradle.kts` include + exclusiveContent, `allowedEdges`, `jitpack.yml` line
- [ ] `verifyAdapterSttCompileOnly` and `verifySttConfined` gates and their negative controls
- [ ] `scripts/verify-api-seed.sh` aar task-name branch
- [ ] ML-denial scope list gains `voice-adapter`
- [ ] `ActionEventTest` sentinel test

---

## Manual-Only Verifications

All phase behaviors have automated verification. Heavy jitpack dry-run and negative-controls runs need an orchestrator quiet window (non-autonomous checkpoint). No device step.

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 600s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` — leave `false` at plan time

**Approval:** pending
