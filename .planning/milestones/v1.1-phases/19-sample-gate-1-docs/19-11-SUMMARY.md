---
phase: 19-sample-gate-1-docs
plan: 11
subsystem: docs
tags: [doc-02, wiring-test, agent-wiring-test, selftest-local, prepare-local, dispatch]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 10 (final doc set and coverage gate), plan 02 (compiled doc regions and sample)
provides:
  - scripts/agent-wiring-test.sh reading its assets from .planning/releases/v1.1.0/wiring-test (VAE_WIRING_ASSET_DIR)
  - "selftest --local": working-tree publication into an isolated maven-local, --offline, host cache
  - "prepare-local <m2dir> <version>": isolated workspace against a file:// repository
  - judge W1-W13 (W2 floor 6, W10 grammar, W11 plan, W12 router, W13 undo) with checks=13
  - the v1.1 agent task, the extended reference solution (Wire.kt, WireTest.kt, AppWire.kt) and DISPATCH.md
affects: [19-12, 19-13, 19-14, phase-20]

status: complete
actuals:
  tokens: 14000
  tasks: 3
  commits: 3
plan_head_before: ee819686d72db2b95c99ad09602991eca123751d
commits: 3

tech-stack:
  added: []
  patterns:
    - "Wiring assets live under .planning/releases/<version>/wiring-test so they survive the milestone archive"
    - "selftest --local reuses the jitpack.yml install-list awk and adds --offline plus an isolated maven.repo.local"

key-files:
  created:
    - .planning/releases/v1.1.0/wiring-test/AGENT-PROMPT.md
    - .planning/releases/v1.1.0/wiring-test/DISPATCH.md
    - .planning/releases/v1.1.0/wiring-test/reference/Wire.kt
    - .planning/releases/v1.1.0/wiring-test/reference/WireTest.kt
    - .planning/releases/v1.1.0/wiring-test/reference/AppWire.kt
  modified:
    - scripts/agent-wiring-test.sh

key-decisions:
  - "The reference uses one create tool (title plus optional parent_id) for the grammar, SingleShot and plan tiers, so one undo ticket path covers every tier"
  - "The undo test runs through the plan tier with runIds = { \"run-1\" } rather than the engine's ScriptedStrategy fixture, which the judge (W7) forbids"
  - "prepare-local defaults the workspace to ~/.cache/vae-wiring-test/local-<version> and takes the docs from HEAD"

patterns-established:
  - "Tracer first: the v1.0-shaped reference passed selftest --local (checks=9) before any v1.1 content was added"

coverage:
  - id: D1
    description: "The wiring harness runs from stable paths and selftest --local proves reference PASS and planted FAIL against a working-tree publication"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "scripts/agent-wiring-test.sh selftest --local (Task 1: PASS checks=9 then WIRING SELFTEST OK)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Prompt, reference solution and judge cover grammar (EN+ES), plan, router, undo-all and the keystore; planted copy fails W4, W5, W12"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "scripts/agent-wiring-test.sh selftest --local (Task 2: WIRING TEST: PASS checks=13, planted FAIL W1-W5 + W12, WIRING SELFTEST OK)"
        status: pass
    human_judgment: false
  - id: D3
    description: "prepare-local builds a clean workspace; DISPATCH.md holds the isolation recipe and re-confirmed flags"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "Task 3 verify command (WIRING PREPARED line, file:// settings, TASK.md version, docs present, no reference/planning/snippet file in the workspace)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Whether a fresh agent can wire grammar, plan, router and undo-all from the docs alone"
    verification: []
    human_judgment: true
    rationale: "Only the isolated agent run in plan 19-14 on the wiring SHA can show it; DOC-02 stays pending"

duration: 40min
completed: 2026-10-07
---

# Phase 19 Plan 11: Wiring test rewrite for v1.1 Summary

**The isolated wiring test now runs from stable paths, judges thirteen checks (grammar, plan, router, undo-all on top of v1.0's nine), proves itself with a cheap `selftest --local`, and can prepare a workspace against the unpushed tree's local publication.**

## Performance

- **Duration:** about 40 min (three Gradle-backed selftest runs dominated by the compile of the workspace)
- **Completed:** 2026-10-07
- **Tasks:** 3 (one tracer)
- **Files:** 1 modified, 5 created

## Accomplishments

- Task 1 (tracer): copied the v1.0 prompt and reference into `.planning/releases/v1.1.0/wiring-test/`, replaced the archived Phase 10 paths with `VAE_WIRING_ASSET_DIR`, added `selftest --local`. The v1.0-shaped reference passed (`checks=9`) against a publication of the working tree, the planted copy failed.
- Task 2: new prompt (ladder with `LocalGrammarStrategy` EN+ES, SingleShot, `PlanThenExecuteStrategy`, `TierSelector.Router`, `UndoJournal` + `EntityAdapter`, undo bridge with the journal first, render, `undoAll`; six tests; keystore as before). Reference `Wire.kt` (368 lines) and `WireTest.kt` (six tests) built from the compiled doc regions grammar-tier, plan-tier, router-selector, undo-wiring, undo-bridge, scripted-provider and render-outcome. Judge W2 floor 6, W10-W13, `checks=13`; the planted copy also loses its `TierSelector.Router` line.
- Task 3: `prepare-local <m2dir> <version>`; `DISPATCH.md` with the headless recipe, throwaway `CLAUDE_CONFIG_DIR`, isolation audit, the record plan 14 writes and the rerun rule.

## Task Commits

1. **Task 1 (tracer): stable asset dir and selftest --local** - `5f8e2c6`
2. **Task 2: v1.1 prompt, reference solution, judge W10-W13** - `e951387`
3. **Task 3: prepare-local and DISPATCH.md** - `5beaf10`

**Plan metadata:** the commit that adds this SUMMARY, STATE.md and ROADMAP.md.

## Memory guard and selftest evidence

Guard command: `awk '/^MemAvailable:/{exit !($2>=5242880)}' /proc/meminfo && ! pgrep -f '[v]oice-action-engine/gradle/wrapper/gradle-wrapper.jar' >/dev/null`, run in the same shell command immediately before each `selftest --local`; low-memory GRADLE_OPTS recipe; one Gradle process; nothing killed, no `./gradlew --stop`.

| Run | MemAvailable before | `free -h` (Mem: used / available) | `pgrep -af '[G]radleDaemon'` | VAE wrapper pgrep | Guard |
|---|---|---|---|---|---|
| Task 1 | 9328960 kB (9.0 GiB) | 22Gi used / 8.9Gi available; swap 2.0Gi of 2.0Gi used | no output | none | pass |
| Task 2 | 9497788 kB (9.1 GiB) | 22Gi used / 9.1Gi available | no output | none | pass |

Verdict lines (full logs: one run per task, the second overwrote the first at the plan's log path):

- Task 1: `WIRING TEST: PASS checks=9` (reference), planted copy `FAIL W1..W5`, last line `WIRING SELFTEST OK`.
- Task 2: `WIRING TEST: PASS checks=13` (reference), planted copy `FAIL W1`, `W2`, `W3`, `W4`, `W5`, `W12`, last line `WIRING SELFTEST OK`.

Task 3 needed no Gradle: the prepare-local verify command printed all checks green (`WIRING PREPARED dir=<tmp> version=local-check`).

## Verification

- `bash -n scripts/agent-wiring-test.sh`; no `phases/10-sample-harness-gate-1-docs` string left in the script; `git diff --exit-code` on the archived Phase 10 directory is clean.
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK` (before the Task 3 commit).
- `grep -c '@Test'` on `WireTest.kt` prints 6; `{{VERSION}}` present in the prompt; the prompt names no file outside the workspace.
- `merge-base --is-ancestor` still in the script (pushed-SHA `prepare` untouched).
- No device touched, no api.txt edited, README/API/ECOSYSTEM/INTEGRATION not edited, clean-clone selftest and JitPack dry run not run, fresh agent not dispatched.

## Decisions Made

- One create tool with an optional `parent_id` serves all three tiers. This lets the plan thread `$first.id` into `parent_id` and keeps one undo-ticket code path (`createStep`).
- The undo proof drives the plan tier with `runIds = { "run-1" }` instead of `ScriptedStrategy`, because `core.testing` imports are a W7 failure.
- The failure test uses a grammar plus SingleShot ladder, so the scripted provider failure lands on a model tier and ends `Failed`.

## Deviations from Plan

None in scope. Small notes:

- The plan's Task 1 verify uses a single shared log path; both runs wrote to `$TMPDIR/vae-19-wiring-local.log`, so only the Task 2 log remains there (copied copies of both are kept in the executor scratchpad, outside the repo).
- The reference compiled and passed on its first run, so no reference or judge bug was found (and none fixed).

## Issues Encountered

None. The planted copy's `W1` failure is a resolution failure (the aggregator coordinate is unpublished), which is the intended signal; the content checks W4, W5 and W12 fail independently of it.

## Findings for later plans

- Plan 19-13 should publish the wiring SHA's tree (or reuse its clean-clone selftest `m2`) and call `prepare-local <m2dir> <version>`; the version string in TASK.md must equal the publication's VERSION.
- DISPATCH.md says the agent's `verify` runs from an empty Gradle cache against the `file://` repository in the workspace; that run is a heavy Gradle job and belongs to the plan 13-14 window.
- Any edit to README, INTEGRATION, API or ECOSYSTEM after the pass voids it; a script or reference fix is legal only before the wiring SHA.
- DOC-02 stays pending (needs the isolated agent run on the final SHA); it was not ticked.

## Next Phase Readiness

Ready for 19-12 and the window plans: the judge is proven non-vacuous for grammar, plan, router and undo-all, and the workspace can be prepared against a local publication.

## Self-Check: PASSED

Files created or modified exist; commits `5f8e2c6`, `e951387`, `5beaf10` are in history; verify commands of all three tasks were run green.
