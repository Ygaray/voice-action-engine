---
phase: 10-sample-harness-gate-1-docs
plan: 02
subsystem: sample
tags: [fixture, sha256, canned-executor, synthetic-tools, ver-01]
status: complete
requires: [10-01]
provides:
  - "FixtureLoader: run-time-only LE-1 fixture load with full SHA-256 check and typed states (Loaded, Absent, ShaMismatch, Malformed)"
  - "ToolClassifier: name-prefix read/mutating rule (get_, list_, find_, search_)"
  - "AndroidFixtureSources: app-private files first, then debug asset"
  - "CannedToolExecutor: fake ToolExecutor with fixed canned JSON for reads, mutations and unknown tools"
  - "SyntheticTools: committed domain-free tool set (find_items, create_item, edit_item, ask_user) plus snapshot()"
affects: [10-03, 10-04, 10-05, 10-07, 10-08]
tech-stack:
  added: []
  patterns: ["closed typed failure states with content-free toString", "no fall-through on digest mismatch", "strict left null so optional fields stay optional"]
key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/FixtureLoader.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/ToolClassifier.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/AndroidFixtureSources.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/CannedToolExecutor.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/SyntheticTools.kt
    - sample/src/test/resources/synthetic-fixture.json
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/FixtureLoaderTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/CannedToolExecutorTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SyntheticToolsTest.kt
  modified: []
key-decisions:
  - "An IOException from a fixture source counts as that source holding nothing; Absent still names every source, so the failure stays visible"
  - "A JSON root that is not an object maps to not_json; missing_system is added to the closed Malformed codes (tested)"
requirements-completed: [VER-01]
commits: 3
plan_head_before: 19da97a5139e9c961e83ccbd590bd1f0d4e01b41
actuals:
  tokens: 14000
  tasks: 3
  commits: 3
duration: 25 min
completed: 2026-10-01
---

# Phase 10 Plan 02: Fixture Loader, Canned Executor and Synthetic Tools Summary

The sample now loads the LE-1 fixture only at run time with a full SHA-256 check and loud typed failures, answers tool calls from a fake executor with fixed canned JSON, and owns a committed domain-free synthetic tool set whose optional fields stay optional.

## What was built

- **Task 1 (tracer, 05c5d2b):** `FixtureLoader` (constant `FIXTURE_SHA256` = full 64-hex digest, `FixtureSource`, `NamedFixtureSource`, sealed `FixtureState`), `ToolClassifier`, `CannedToolExecutor`, the synthetic test fixture and the first three tests. The tracer loads the synthetic fixture, runs an agentic command through `SampleEngine` with the canned executor, and asserts the canned read JSON comes back to the model as the `ToolResultsMessage` content on the second request.
- **Task 2 (feat, bbbdd7a):** `AndroidFixtureSources` (files/fixture/sb-a10-fixture.json first, then the asset; only `FileNotFoundException` maps to null) and ten more loader tests: absent, wrong digest with no fall-through, files-before-asset, four malformed codes, the real digest constant, and a canary test that no state's `toString` carries system or tool text.
- **Task 3 (feat, 0f87a5c):** `SyntheticTools` (four tools, `strict` null, closed object schemas, `snapshot(singleShotTool)`), `SyntheticToolsTest` (6) and `CannedToolExecutorTest` (4, including the argument-echo canary).

## Gate results

| Gate | Result |
|------|--------|
| `:sample:testDebugUnitTest` | 29 tests total (FixtureLoaderTest 13, SyntheticToolsTest 6, CannedToolExecutorTest 4, SampleEngineTracerTest 3, OkHttpPinTest 3), 0 failures |
| `:sample:compileReleaseKotlin` | green (see deviation) |
| `./gradlew check --offline` | green |
| `scripts/verify-repo-hygiene.sh` | HYGIENE OK |
| build scripts name the fixture / sample source names the SB repo | no / no |
| `git diff --stat PLAN_BASE -- core providers keystore` | empty |
| `grep -c 'strict = true'` in SyntheticTools.kt | 0 |
| fixture-shaped files in the tree | none |

## Deviations from Plan

**1. [Rule 3 - Blocking] `:sample:testReleaseUnitTest` does not exist.** The plan's Task 3 verify and the plan verification section name this task; the AGP 9 sample module registers only `testDebugUnitTest` (no release unit-test variant). The intent was "main code compiles for the release variant", so I ran `:sample:testDebugUnitTest :sample:compileReleaseKotlin` instead; both are green. No build file was changed. Later plans that copy this verify line should use the same substitute.

**2. Task ordering note.** The loader was written complete in Task 1 (so the tracer could use it) and Task 2 added the Android sources plus the failure-state tests, which passed on first run. The Task 2 tests were therefore not observed red first.

**Total deviations:** 1 rule-based (Rule 3), 1 process note. **Impact:** none on behavior.

## Authentication Gates

None. No device, adb, key or live provider call was touched (D-01, D-13). The LE-1 fixture was never read, copied or committed; all tests use the committed synthetic fixture.

## Next Phase Readiness

`androidFixtureSources(context)`, `FixtureLoader`, `CannedToolExecutor` and `SyntheticTools.snapshot()` are ready for the screen and smokes in later plans. The 10-07 `push-fixture` step must place the file at `files/fixture/sb-a10-fixture.json`.

## Self-Check: PASSED

- All nine created files exist on disk.
- Commits 05c5d2b, bbbdd7a, 0f87a5c present.
- Acceptance criteria re-run: digest constant grep, no fixture files, filesDir grep, hygiene OK, no core/providers/keystore diff.
