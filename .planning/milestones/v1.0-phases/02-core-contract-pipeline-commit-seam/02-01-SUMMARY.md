---
phase: 02-core-contract-pipeline-commit-seam
plan: 01
subsystem: core
status: complete
tags: [kotlin, contract-types, explicit-api, metalava, detekt, api-surface-lint]

requires: []
provides:
  - "CommandInput(transcript, language, context: Any?, parentRunId) with redacted toString"
  - "ProviderId / StrategyId value classes, Credential (key-free toString)"
  - "Open FailureReason (25 leaves incl. Other) and EscalationReason (6 leaves) interfaces, FailureDetails, BudgetBound"
  - "TierPolicy (builder, validated 6 / 60,000 / 4,096 defaults, no default command deadline) and TierPolicySource"
  - "ApiShapeTest in-JVM surface lint and scripts/review-api-surface.sh isolated-copy Metalava review"
affects: [02-02, 02-03, 02-04, 02-05, 02-06, 02-07, 02-08, 02-09, Phase 3, Phase 6, Phase 11]

plan_head_before: 54188f9825f1634e89dd00c3244d3afdafe52aa5

actuals:
  tokens: 13400   # chars/4 over the realized diff of core/ and scripts/ (53,651 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Open taxonomies: non-sealed interface + nested final leaves + Other(code); equals by class and fields"
    - "Redaction-safe hand-written toString on every input/credential/failure type"
    - "Growth-safe policy: internal constructor, Builder with public vars, companion invoke(block)"
    - "Constants as top-level private const val, never in a companion (no leaked public static fields)"
    - "Surface review reads the real Metalava dump in an isolated copy; the real tree never gets an api.txt"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CommandInput.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ProviderId.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/StrategyId.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/Credential.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureDetails.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/EscalationReason.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/BudgetBound.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicySource.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/IdentityTypesTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
    - scripts/review-api-surface.sh
  modified: []
  deleted:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CoreModule.kt

key-decisions:
  - "CommandInput.context is an opaque Any? (locked decision), not the research's Map<String, Any?>"
  - "TierPolicy validates in its internal constructor (init require), so every construction path is checked, not just the builder"
  - "TierPolicy.allowedProviders is defensively copied in Builder.build()"
  - "ApiShapeTest resolves the main classes from either a directory or a jar code source (Gradle puts the :core jar on the test classpath through the testFixtures edge)"

patterns-established:
  - "Surface script negative-tested by planting a sealed type, enum, data class and @JvmField in the isolated copy"

requirements-completed: [CORE-06, CORE-07]
requirements-partial: [CORE-04, CORE-09]   # type halves only: policy type / parentRunId field; behavior lands in later plans

duration: ~35min
completed: 2026-09-30
---

# Phase 2 Plan 01: Contract value layer and live API-surface lint Summary

**Identity/input/failure/policy value types as frozen-shape public API, with an in-JVM and a Metalava-dump-based surface lint live before any bulk type lands.**

## Performance

- **Tasks:** 3 of 3 (Task 1 tracer, Tasks 2 and 3 TDD-style)
- **Commits:** 1304aaf (task 1), 2533a82 (task 2), d152e08 (task 3)
- **Files:** 15 created, 1 deleted (CoreModule.kt)

## Accomplishments

- Tracer path: `CommandInput`, `ProviderId`, `StrategyId`, `FailureDetails` and the first `FailureReason` leaves compile under explicitApi, pass detekt and the scanner, and are swept by `ApiShapeTest` and `scripts/review-api-surface.sh`; the Phase 1 placeholder is gone.
- Full open taxonomies: 25 `FailureReason` leaves with unique codes (incl. `NoEligibleTier`, `PolicyUnavailable`, `Unexpected`, `Other`), 6 `EscalationReason` leaves, `BudgetBound`, `Credential`. None is sealed; a test shows consumers need an `else`.
- `TierPolicy` with builder, defaults 6 / 60,000 / 4,096 / no deadline, rejection of `maxIterations < 2`, and `TierPolicySource.fixed`.
- Redaction: canary tests prove neither `CommandInput`, `Credential` nor any failure/escalation `toString` leaks a transcript, context content or key.

## Verification evidence

- `./gradlew :core:check :providers:check` : BUILD SUCCESSFUL (detekt zero issues, scanner, structural gates, fixtures-not-published all green).
- Tests ran: ApiShapeTest 6, IdentityTypesTest 11, FailureTaxonomyTest 10, TierPolicyTest 8 (plus the existing ScriptedHarnessTest 7), 0 failures, 0 skipped.
- `scripts/review-api-surface.sh` : `API SURFACE OK sealed=none classes=46`.
- No `api.txt` in core/, providers/ or keystore/ of the real tree; `CoreModule.kt` deleted; mode of the script in git is 100755.
- Script negative controls (run in the isolated copy, then removed): a non-allow-listed sealed class, an enum, a data class and a `@JvmField` companion field each produced `API SURFACE FAIL`; an allow-listed sealed type reported `sealed=StrategyOutcome`.

## Deviations from Plan

**1. [Rule 1 - Bug] Script missed enums**
- Found during: Task 1 negative control. Metalava prints enums as `public enum Name {` (no `class` keyword), so the declaration matcher skipped them and the enum check passed silently.
- Fix: declaration matcher now recognises `enum`; also counted in `classes=`. Re-verified FAIL on a planted enum.

**2. [Rule 1 - Bug] ApiShapeTest found no classes**
- Found during: Task 1. The `:core` main output reaches the test classpath as a jar (via the testFixtures dependency edge), not a directory, so a directory walk found nothing.
- Fix: the test lists class names from either a directory or a jar.

**3. [Rule 1 - Bug] `--out` could trip the real-tree guard**
- The copy to `--out` now happens after the before/after tree-status guard, so an `--out` path inside the repo cannot fail it.

**4. Validation placement (design choice within the plan)**
- The plan asked for `require` checks at build time; they live in the `TierPolicy` constructor `init`, which the builder calls, so they cannot be bypassed. Behavior is identical for callers.

**Detekt:** no default rule forced a structural change. Only MaxLineLength findings (two in tests, two in main) were fixed by reflowing; no config tuning, no baseline, no `@Suppress` added in main.

## Resolutions of the plan's Assumptions list (carried forward for the orchestrator)

Nothing in this plan changed any assumption; for reference the planner-resolved list stands as written in 02-01-PLAN.md:
O1 (minimal ToolSpec in plan 02), O2 (NonCancellable `onAction`; cancel during `apply()` records an `is_error`, `applied = true` action), O3 (leaked `TimeoutCancellationException` becomes `Failed(Timeout)`), O4 (internal `CommandOutcome` leaf constructors), O5 (gate amends via `Admit(amended)`), A2 (held-then-escalate ends `Completed(partial = true)`), A3 (throwing sink recorded as `sink_error`), A4 (second `commitHeld` returns first outcome), A5 (`maxIterations < 2` throws `IllegalArgumentException`; throwing `TierPolicySource` becomes `Failed(PolicyUnavailable)`), A6 (`Hold` carries optional `appOutcomeToken`), A7 (`asClarification()` returns null on non-conforming args), A8 (public `clock` and `runIds` DSL properties), A9 (no cap on `CommitSink` delay). Items 14 to 21 (Credential here, `Fixed` tier misconfiguration, `maxTier` unknown, ON_DEVICE-only tier, lowercase wire strings, no default command deadline) are consistent with what was built.

## Requirements bookkeeping

CORE-06 and CORE-07 are marked complete in REQUIREMENTS.md. CORE-04 (policy enforcement in the pipeline, plan 04) and CORE-09 (`parentRunId` carried into the trace and `CommitSink`) are only half done here, so they were left Pending after `requirements.mark-complete` had flipped them. The state tooling also reset `completed_phases`/`percent` to 0 after Phase 1 was complete; STATE.md was corrected by hand to 1 phase / 9%.

## Known stubs / deferred

None. Pre-existing unrelated working-tree changes (.planning/graphs, config.json, milestone files, graphify-out) were left untouched and unstaged.

## Self-Check: PASSED

- All 15 created files exist; `CoreModule.kt` absent.
- Commits 1304aaf, 2533a82, d152e08 present in `git log`; `git rev-list --count 54188f9..HEAD` = 3 at the time of the last task commit.
