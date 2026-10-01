---
phase: 02-core-contract-pipeline-commit-seam
plan: 02
subsystem: core
status: complete
tags: [kotlin, clarification, terminal-tools, value-classes, usage, trace-codes, guarded, cancellation, detekt]

requires: []
provides:
  - "ToolSpec(name, description, inputSchema, mutating, terminal) with terminal && mutating rejected at construction; ToolSpec.clarification(name[, description])"
  - "TerminalCall(toolName, arguments) with asClarification(): Clarification? (null, never a throw); Clarification / ClarificationOption with redacted toString"
  - "ActionKind (committed, held, preview, is_error) and FinishedKind (read, preview, error) value classes"
  - "Usage {inputUncached, cacheRead, cacheWrite, output} with total (four-way sum), plus, ZERO"
  - "TraceCode value class with all 14 engine codes"
  - "internal guarded(onFault, block) and EngineFault(errorClass, timeoutLeak): the only broad catch in :core main"
affects: [02-03, 02-04, 02-05, 02-06, 02-07, 02-08, Phase 3]

plan_head_before: 2f8f1ed97d76df7971e771820b66ab409314429a

actuals:
  tokens: 9400    # chars/4 over the realized diff of core/ and config/ (38,276 chars incl. diff headers)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Open vocabularies as @JvmInline value classes with internal constructors and companion constants (no enums)"
    - "Field-name constants as file-private const vals, never internal (internal const compiles to a public static field and trips ApiShapeTest)"
    - "One inline suspend collapse helper with separate catch clauses in a fixed order (no type checks inside a catch)"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/TerminalCall.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/Clarification.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionKind.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/FinishedKind.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/Usage.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolSpecClarificationTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ValueTypesTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GuardedTest.kt
  modified:
    - config/detekt/detekt.yml

key-decisions:
  - "Clarification field names are mirrored as private consts in ToolSpec.kt and TerminalCall.kt; the round-trip test keeps them in step"
  - "detekt MatchingDeclarationName excluded for internal/Guarded.kt only (file is named for the helper function, which holds one small carrier class)"

requirements-completed: []
requirements-partial: [CORE-05, CORE-08, TEL-01]   # CORE-05 mechanism only; CORE-08 type half; TEL-01 Usage half. CORE-07 was completed in 02-01.

duration: ~25min
completed: 2026-09-30
---

# Phase 2 Plan 02: Terminal/clarification types, commit kinds, Usage, TraceCode and the guarded helper Summary

**The A19 clarification types, open commit-kind and trace-code vocabularies, normalized `Usage`, and the engine's single cancellation-correct broad-catch helper, all proven by 28 new JVM tests.**

## Performance

- **Tasks:** 3 of 3 (Task 1 tracer, Tasks 2 and 3 TDD-style)
- **Commits:** f137b59 (task 1), 112baab (task 2), dccc7d1 (task 3)
- **Files:** 11 created, 1 modified (`config/detekt/detekt.yml`)

## Accomplishments

- Tracer: `ToolSpec.clarification("ask_clarification")` is terminal, non-mutating, and its schema equals the golden string byte for byte; a model-shaped `TerminalCall` converts to a typed `Clarification` in order, returns null on 8 malformed shapes, and allows empty options. No `toString` leaks a canary.
- `ActionKind`, `FinishedKind` and `TraceCode` are value classes with internal constructors (zero `enum class`), so a later kind cannot break a consumer's exhaustive `when`.
- `Usage` totals the four-way sum; the Anthropic-style (100/900/0/50) and OpenAI-style (prompt 1000, cached 900, completion 50) mappings of the same work are equal with total 1050.
- `guarded` is the only broad catch and the only `@Suppress` in `core/src/main` (verified by count and location).

## Golden clarification schema

```
{"type":"object","properties":{"question":{"type":"string"},"options":{"type":"array","items":{"type":"object","properties":{"id":{"type":"string"},"label":{"type":"string"}},"required":["id","label"],"additionalProperties":false}}},"required":["question","options"],"additionalProperties":false}
```

Default description: `Ask the user to choose one of the listed options instead of guessing.`

## GuardedTest methods (9)

1. `ordinaryExceptionBecomesAClassNamedFault`
2. `linkageErrorBecomesAFault`
3. `leakedInnerTimeoutWhileActiveIsATimeoutFault`
4. `outerTimeoutPropagatesAndNeverReachesOnFault`
5. `cancellingTheJobPropagatesAndNeverReachesOnFault`
6. `plainCancellationExceptionWhileActivePropagates`
7. `assertionErrorIsNotCaught`
8. `normalReturnPassesTheValueThroughWithoutFault`
9. `anonymousExceptionClassFallsBackToTheJvmNameSegment`

## Verification evidence

- `./gradlew :core:check :providers:check` : BUILD SUCCESSFUL (detekt zero issues, scanBannedConstructs, structural gates, fixtures-not-published, detekt negative controls).
- Tests ran in `:core`: 70 total, 0 failures, 0 skipped (new: ToolSpecClarificationTest 11, ValueTypesTest 8, GuardedTest 9; existing ApiShapeTest 6, IdentityTypesTest 11, FailureTaxonomyTest 10, TierPolicyTest 8, ScriptedHarnessTest 7). ApiShapeTest's surface sweep covers the new classes.
- Suppression check: exactly 1 `@Suppress` in `core/src/main`, in `internal/Guarded.kt`.
- `grep -c 'enum class'` on ActionKind.kt and FinishedKind.kt: 0 and 0.

## Deviations from Plan

**1. [Rule 1 - Bug] `internal const val` field names would trip the surface lint, and an `internal object` of plain vals tripped detekt MayBeConst**
- Found during: Task 1. `internal const` compiles to a public static field (ApiShapeTest forbids those) and a non-const object val is flagged by `MayBeConst`.
- Fix: the four field names are file-private consts duplicated in `ToolSpec.kt` and `TerminalCall.kt`, each with a comment pointing at the other; the golden-schema and round-trip tests catch drift.

**2. [Rule 3 - Blocking] detekt `MatchingDeclarationName` on Guarded.kt**
- The plan fixes the file name `Guarded.kt` (and its acceptance greps), but the file's only top-level class is `EngineFault`, so the rule failed. `@Suppress` is forbidden (only one allowed in main).
- Fix: a targeted `excludes: ['**/internal/Guarded.kt']` for that one rule in `config/detekt/detekt.yml` with a one-line justification. This is the only file touched outside the plan's `files_modified`. No baseline, no other tuning.

**3. Detekt MaxLineLength (test and Usage)** : reflowed; no config change.

**Detekt rules that shaped Guarded.kt:** `TooGenericExceptionCaught` (the one suppression), `InstanceOfCheckForException` (hence separate catch clauses, no `is` inside a catch), `MatchingDeclarationName` (deviation 2).

## Requirements bookkeeping

`requirements mark-complete` was intentionally NOT run: this plan delivers only part of each of its requirements. CORE-05 (mechanism only; the pipeline-level collapse lands in later plans), CORE-08 (type half; `Completed.terminalCall` and the dispatch behavior come later), TEL-01 (`Usage` half; `CommandTrace` comes later) stay Pending. CORE-07 was already completed by 02-01.

## Known stubs / deferred

None. Pre-existing unrelated working-tree changes (.planning/graphs, config.json, milestone files, graphify-out) were left untouched and unstaged.

## Self-Check: PASSED

- All 11 created files exist on disk; `git log` shows f137b59, 112baab, dccc7d1; `git rev-list --count 2f8f1ed..HEAD` = 3 at the last task commit.
