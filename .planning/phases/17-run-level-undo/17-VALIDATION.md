---
phase: "17"
slug: "run-level-undo"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-06"
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: 17-RESEARCH.md `## Validation Architecture`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2; kotlinx-coroutines-test 1.11.0 only in `:core`/`:sample` tests; `:undo` uses a stdlib-only suspend helper |
| **Config file** | per-module `build.gradle.kts`; gates in `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml` |
| **Quick run command** | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline :undo:test` |
| **Full suite command** | same flags: `./gradlew --offline :undo:check :core:check :sample:testDebugUnitTest`, then `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-docs-coverage.sh` |
| **Estimated runtime** | ~300 seconds (host memory tight: one Gradle-running plan per wave) |

---

## Sampling Rate

- **After every task commit:** the single most relevant quick command (one Gradle task, flags applied) plus bash gates for plumbing tasks
- **After every plan wave:** `:undo:check :core:check` then `:sample:testDebugUnitTest --tests '*Undo*' --tests '*DocSnippets*'`
- **Before `/gsd-verify-work`:** full suite green plus `scripts/verify-negative-controls.sh` and clean-cache `jitpack-dry-run.sh` (`:undoalone` probe) in a quiet window (non-autonomous)
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 17-XX-XX | — | — | UNDO-01 | — | no dependency beyond stdlib; standalone JVM app journals/undoes | unit + gate | `:undo:test --tests '*UndoStandaloneTest*'`, `:undo:verifyUndoZeroDeps :undo:verifyModuleGraph` | ❌ W0 | ⬜ pending |
| 17-XX-XX | — | — | UNDO-02 | — | adapter round trip; compensators after DB restore | unit | `:undo:test --tests '*AdapterRoundTrip*' --tests '*Compensator*'` | ❌ W0 | ⬜ pending |
| 17-XX-XX | — | — | UNDO-03 | T-17 refuse-loudly | edit-since → Refused, store untouched; Partial exact lists; withheld never N-1; redaction | unit | `:undo:test --tests '*RefuseLoudly*' --tests '*Partial*' --tests '*Withheld*' --tests '*Redaction*'` | ❌ W0 | ⬜ pending |
| 17-XX-XX | — | — | UNDO-04 | — | heldRunId + compositeSink isolation; end-to-end through real pipeline | unit | `:core:test --tests '*HeldRunId*' --tests '*CompositeSink*'`; `:sample:testDebugUnitTest --tests '*UndoEndToEnd*'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `undo/` module scaffold (`build.gradle.kts`, header-only `api.txt` seed, test helper, in-memory fake store)
- [ ] `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh` + planted-module control
- [ ] `verifyUndoZeroDeps` + negative controls
- [ ] `core` tests: `HeldRunIdTest`, `CompositeSinkTest`
- [ ] `sample`: `UndoEndToEndTest`, bridge region + docs parity test

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Clean-cache `:undoalone` consumer probe (`jitpack-dry-run.sh`) and `verify-negative-controls.sh` | UNDO-01 | heavy; host memory (earlyoom) needs a quiet window | run in a quiet window after all waves land |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` — leave `false` at plan time

**Approval:** pending — finalizer-owned, not set at plan time
