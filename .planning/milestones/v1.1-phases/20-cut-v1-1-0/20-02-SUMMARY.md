---
phase: 20-cut-v1-1-0
plan: 02
subsystem: release
tags: [api-freeze, metalava, tostring-redaction, release-cut, undo]

requires:
  - phase: 20-01
    provides: main pushed level with origin/main
provides:
  - ActionEvent.toString KDoc states why run id and parent run id print verbatim (join keys) and the held run id prints as set or null
  - PlanSchemaTest.theParsersFieldNamesAreTheSchemasOwnKeys drift guard (RT-01 left)
  - JournalStoreTest.withNoStoreSuppliedTheEngineWritesNothingToDisk (P17 OI-1 pinned)
  - five committed final api.txt baselines (core, providers, keystore, undo, voice-adapter) from one isolated dump
  - evidence rt-outcomes.txt, api-dump-final.txt, api-surface-final.txt
affects: [20-03, 20-06, 20-07, 20-08]

tech-stack:
  added: []
  patterns:
    - "dump only through scripts/api-dump-isolated.sh --out, never apiDump in the real tree"
    - "drift guard test instead of a shared constant when a shared const would leak a public static field"

key-files:
  created:
    - .planning/releases/v1.1.0/evidence/rt-outcomes.txt
    - .planning/releases/v1.1.0/evidence/api-dump-final.txt
    - .planning/releases/v1.1.0/evidence/api-surface-final.txt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanSchemaTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStoreTest.kt
    - core/api.txt
    - keystore/api.txt
    - undo/api.txt
    - voice-adapter/api.txt

key-decisions:
  - "RT-01 stays LEFT (public static field / MayBeConst); guarded by a schema-to-parser agreement test"
  - "RT-03 redact-by-default already implemented; only the rationale was added to the KDoc"
  - "P17 OI-1: no existing test pinned 'no store, no disk write', so one was added in :undo"

requirements-completed: []  # VER-07 (cut v1.1.0) spans all 12 plans; not complete after 20-02

actuals:
  tokens: 9000
  tasks: 2
  commits: 3
plan_head_before: 43c7f237b23fa4888747500782c2a07051efc5df

duration: n/a
completed: 2026-10-07
status: complete
---

# Phase 20 Plan 02: Final API baselines and toString rationale Summary

**The five api.txt files are now the reviewed, committed released-API baseline (v1.0.1 is a strict subset for core, providers and keystore; undo and voice-adapter have real dumps), ActionEvent.toString has its stated rationale, and the P17 OI-1 "no store, no disk write" rule is pinned by a test.**

## Performance

- **Tasks:** 2 (Task 1 tracer, Task 2 auto) plus one master-directed extra test
- **Files modified:** 7 tracked source/baseline files, 3 evidence files created
- **Host readings (R2, outside a quiet window so warning only):**
  - before Task 1 tests: MemAvailable 11155860 kB, SwapTotal 2097148 kB, SwapFree 204 kB
  - before :undo tests: MemAvailable 10378676 kB, SwapFree 484 kB
  - before the isolated dump: MemAvailable 8862124 kB, SwapFree 820 kB
  - Swap was effectively full on every run (about 0.04% free). Master: an operator swap reset would help before the quiet windows. No earlyoom kill occurred, no retry was needed, no wrapper process was running, 5 GiB floor held.

## Accomplishments

- **RT-03 / RT-06:** KDoc on `ActionEvent.toString` now says run id and parent run id are the join keys a sink needs, so they print verbatim, and the held run id prints as set or null because its value equals an id already printed. Bodies untouched; ActionEventTest sentinel tests and HeldRunIdTest green and unedited. WR-01 confirmed fixed in Phase 18 ("may carry user text" is gone).
- **RT-01:** recorded LEFT with the reason (top-level `internal const val` becomes a public static field that `ApiShapeTest` rejects; plain `internal val` trips detekt MayBeConst). PlanParse.kt and PlanSchema.kt untouched. New `theParsersFieldNamesAreTheSchemasOwnKeys` builds a plan answer from the keys read out of `submitPlanSpec(...).inputSchema` and checks `parsePlan` returns Valid, and NeedsLookup for the lookup key.
- **P17-OI-1:** none of the existing undo tests pinned it (`withoutAStoreNothingIsCalledAndThereAreNoFaults` only checks the fault counter). Added `withNoStoreSuppliedTheEngineWritesNothingToDisk`: runs record, runClosed, undoAll and withhold with no store, asserts the file tree under user.dir (excluding build and .gradle) and a fresh temp dir is unchanged, `storeFaults == 0`, and no `:undo` main class names `java/io/File`, `java/nio/file/` or RandomAccessFile in its constant pool. Test only, api.txt unaffected. Recorded as `P17-OI-1:` in rt-outcomes.txt.
- **RT-02 / RT-04 / D-01:** one isolated dump: `API DUMP ISOLATED OK core=1956 providers=121 keystore=76 undo=191 voice-adapter=16`. Against v1.0.1: core 0 removed (+120 lines), providers byte-equal, keystore 0 removed (67 to 76, +9, the DelicateKeyAccess / KeyAccess lines). undo and voice-adapter carry their own package line; voice-adapter has exactly 2 `toCommandInput(` and 2 `commandInputOf(`. `:undo` has exactly one `@Suppress` (Guard.kt:1); the second one in 19-API-REVIEW OI-5 was removed by e1b5653, recorded without editing the closed review.
- **D-02:** `review-api-surface.sh` printed `API SURFACE OK` for all five modules against the final dump; sealed sets are core's unchanged seven (AssistantPart, CommandOutcome, GateDecision, Message, RunTermination, StrategyOutcome, ToolStep), `UndoResult` for undo, none elsewhere; the script is unchanged since ce7e457.

## Task Commits

1. **Task 1 (tracer): toString rationale, agreement test, rt-outcomes** - `cc703ce`
2. **Extra (master instruction 1): OI-1 no-store test** - `ae4a955`
3. **Task 2: five api.txt baselines + evidence (RT-02, RT-04)** - `7066836`

`commits: 3` measured from the plan-head ledger (`43c7f23..HEAD`) before this SUMMARY commit.

## Deviations from Plan

### Master-directed addition

**1. [Instruction] P17 OI-1 no-store test**
- Directed by the orchestrator (relay-log P17 OI-1 ruling), not in the plan. Added one test in `:undo`, committed separately as `test(20-02)`, recorded as `P17-OI-1:` in rt-outcomes.txt. The test ran before the final api dump; api.txt is unaffected.

### Notes

- The KDoc addition is one sentence long in spirit but spans three wrapped lines; all earlier "never prints" statements are kept.
- The sorted-unique comm view of keystore shows 5 distinct added lines; the raw line count delta is the 9 lines 19-API-REVIEW names (duplicate closing braces collapse under `sort -u`). Both are in api-dump-final.txt.
- `scripts/verify-api-seed.sh` left alone as the plan said.

## Issues Encountered

None. Swap nearly full (see readings above) but no OOM kill.

## Next Phase Readiness

- Library sources and api.txt are final; after this plan only docs, scripts and `.planning` change. Gate 10 and selftest step 4 reds are removed by the committed dumps; gate 12 awaits plan 20-06.
- Worktree is clean outside `.planning`.

## Self-Check: PASSED

- Files present: the three evidence files, 20-02 api.txt files (core, keystore, undo, voice-adapter changed; providers unchanged by design).
- Commits cc703ce, ae4a955, 7066836 exist in git log; Task 2 automated verify exited 0; `git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd'` printed nothing after the commit.
