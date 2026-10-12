---
phase: "17"
slug: "run-level-undo"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: validated
nyquist_compliant: true
wave_0_complete: true
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
| 17-XX-XX | — | — | UNDO-01 | — | no dependency beyond stdlib; standalone JVM app journals/undoes | unit + gate | `:undo:test --tests '*UndoStandaloneTest*'`, `:undo:verifyUndoZeroDeps :undo:verifyModuleGraph` | ✅ | ✅ green |
| 17-XX-XX | — | — | UNDO-02 | — | adapter round trip; compensators after DB restore | unit | `:undo:test --tests '*AdapterRoundTrip*' --tests '*Compensator*'` | ✅ | ✅ green |
| 17-XX-XX | — | — | UNDO-03 | T-17 refuse-loudly | edit-since → Refused, store untouched; Partial exact lists; withheld never N-1; redaction | unit | `:undo:test --tests '*RefuseLoudly*' --tests '*Partial*' --tests '*Withheld*' --tests '*Redaction*'` | ✅ | ✅ green |
| 17-XX-XX | — | — | UNDO-04 | — | heldRunId + compositeSink isolation; end-to-end through real pipeline | unit | `:core:test --tests '*HeldRunId*' --tests '*CompositeSink*'`; `:sample:testDebugUnitTest --tests '*UndoEndToEnd*'` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `undo/` module scaffold (`build.gradle.kts`, header-only `api.txt` seed, test helper, in-memory fake store)
- [x] `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh` + planted-module control
- [x] `verifyUndoZeroDeps` + negative controls
- [x] `core` tests: `HeldRunIdTest`, `CompositeSinkTest`
- [x] `sample`: `UndoEndToEndTest`, bridge region + docs parity test

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Clean-cache `:undoalone` consumer probe (`jitpack-dry-run.sh`) and `verify-negative-controls.sh` | UNDO-01 | heavy; host memory (earlyoom) needs a quiet window | DONE in the 17-10 relayed quiet window (negative-control failures 0, API DUMP PROOF OK, DRY RUN OK / PROBE OK; see 17-QUIET-WINDOW.md). A re-run of the dry run and live probe on the final tree (post-window script edits) is a deferred obligation owned by Phase 19's gate run (17-VERIFICATION.md). |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] _(finalizer-only, post-execution)_ `nyquist_compliant: true`

**Approval:** 2026-10-07 finalized. Audit: 0 gaps found. Every requirement (UNDO-01..04) maps to named, passing tests; post-fix fresh runs: `:undo` 113, `:core` 1110, `:sample` 160 tests, 0 failures/errors/skipped (JUnit XML, Gate-1 run), `:undo:detekt`/`verifyUndoZeroDeps`/`verifyModuleGraph`/`metalavaCheckCompatibility` green, full `./gradlew check` green, heavy gates green in the 17-10 quiet window. No tests added by the finalizer.

## Validation Audit 2026-10-07
| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
