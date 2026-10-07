---
phase: 19-sample-gate-1-docs
plan: 14
subsystem: testing
tags: [wiring-test, isolated-agent, release-record, quiet-window, doc-02]

requires:
  - phase: 19-sample-gate-1-docs
    provides: 19-13 (heavy gates green, kept dry-run publication dryrun-ec24a19786, open quiet window)
provides:
  - .planning/releases/v1.1.0/WIRING-RERUN.md (status pass, tested_sha 090fd8ec76, consulted_only_workspace true), accepted by release-cut gate wiring
  - evidence/wiring-stumbles.txt and wiring-consulted.txt (the agent's two reports, verbatim)
  - 19-QUIET-WINDOW.md closed (grant consumed, closed line, observations, green verdict)
  - carry register C9 (five doc stumbles as Phase 20 input)
affects: [phase-20, 19-security-audit]

status: complete
gate_result: "WIRING TEST: PASS checks=13; scripts/release-cut.sh gate wiring 090fd8ec761178d5922523faaf24dc3ffb7b686b printed GATE OK wiring"
actuals:
  tokens: 6000
  tasks: 2
  commits: 1
plan_head_before: 97ccede8e566b6a9fd3461828cae751c4e28c709
commits: 1

key-files:
  created:
    - .planning/releases/v1.1.0/WIRING-RERUN.md
    - .planning/releases/v1.1.0/evidence/wiring-stumbles.txt
    - .planning/releases/v1.1.0/evidence/wiring-consulted.txt
  modified:
    - .planning/phases/19-sample-gate-1-docs/19-QUIET-WINDOW.md
    - .planning/phases/19-sample-gate-1-docs/evidence/gate2-carry-register.txt

key-decisions:
  - "Stumbles recorded as Phase 20 input (C9), no doc edited: any README/INTEGRATION/API/ECOSYSTEM edit after the pass would void it"
  - "Workspace placed under /tmp (WIRING_DIR) so no ancestor holds a CLAUDE.md or .claude (ancestors_clean=yes)"

requirements-completed: [DOC-02]

duration: 8min (Task 2; agent dispatch 18:14:19Z-18:18:25Z by the orchestrator)
completed: 2026-10-07
---

# Phase 19 Plan 14: Isolated wiring test and window close Summary

A fresh, isolated sonnet agent wired grammar, plan, the router, undo-all and the keystore from the four docs alone, and `agent-wiring-test.sh verify` printed `WIRING TEST: PASS checks=13` on the Phase 19 wiring SHA; the v1.1.0 wiring record is committed and release-cut's wiring gate accepts it; the one quiet window is closed.

## Task 1 (done before this executor, by the orchestrator)

`prepare-local` printed `WIRING PREPARED dir=/tmp/vae-wiring-19/ws version=dryrun-ec24a19786`. The orchestrator ran the headless agent per DISPATCH.md (throwaway `CLAUDE_CONFIG_DIR` with credentials only, removed): dispatch 2026-10-07T18:14:19Z, finish 18:18:25Z, exit 0, model sonnet. Recorded in 19-QUIET-WINDOW.md "Plan 14 Task 1" (commit 97ccede).

## Task 2 (tracer): judge, record, close

- Verify, empty Gradle cache, low-memory recipe, 18:19:15Z to 18:20:44Z, exit 0: `WIRING TEST: PASS checks=13`. MemAvailable 7.9 GiB before; no other Gradle process; the mempalace mine ran beside and was not touched.
- Isolation audit clean: no CLAUDE.md or .claude in the workspace or any ancestor; no `.credentials.json` in the workspace; `CONSULTED.md` has only workspace-relative paths (consulted_only_workspace true); no URL, home path or key-shaped string in the agent's reports or final message; cfg_removed yes.
- Record `.planning/releases/v1.1.0/WIRING-RERUN.md`: status pass, tested_sha `090fd8ec761178d5922523faaf24dc3ffb7b686b` (HEAD ec24a19 differs from it only under `.planning`; the dry-run version name derives from ec24a19), verbatim PASS line, audit, dispatch facts, stumbles, rerun rule.
- Gate after the commit (86a13aa): `scripts/release-cut.sh gate wiring 090fd8ec761178d5922523faaf24dc3ffb7b686b` printed `GATE OK wiring` (exit 0).
- Window closed: `grant: consumed`, `closed: 2026-10-07T18:21:30Z`. Gates started 17:17:43Z, elapsed about 64 min of the 9000 s timebox. MemAvailable stayed 7.2 to 12.2 GiB; no earlyoom kill, no retry, nothing killed. Kept dirs `/tmp/tmp.iRiTqYvCWA`, `/tmp/tmp.FnrVBq4MHu` and `/tmp/vae-wiring-19` removed and confirmed absent.
- The master sends `quiet done` (the executor cannot).

## Stumbles for Phase 20 (doc input, not fixed)

1. Router answer tool name and argument shape (`pick_start_tier`, `{"tier":...}`) undocumented; the agent had to guess.
2. `submit_plan` argument schema undocumented (steps, id, tool, arguments, needs_lookup).
3. INTEGRATION.md section 11 `undo-bridge` block has no imports (`EntryRef`, `ActionEvent`, `ActionKind`, `CommitSink`, `RunTermination`, `ConcurrentHashMap`).
4. SingleShot with a read tool in the snapshot: docs say reads are not served; the resolver's intent for a read call is unclear.
5. A variable named `store` inside the `UndoJournal { }` builder is shadowed by the builder's `store` property (confusing compile error); worth a doc warning.

Carried as register item C9. Fixing any of these voids the pass and needs the wiring test re-run (Phase 20 re-runs it on its final SHA anyway, C4).

## Deviations from Plan

None. No code, script, doc or test file changed (`git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd'` empty). Task 1's checkpoint was satisfied by the orchestrator's dispatch rather than a master resume signal; the plan's intent (isolated agent, recorded facts) holds.

## Self-Check: PASSED

- WIRING-RERUN.md, both evidence files, 19-QUIET-WINDOW.md and the carry register exist and carry the claimed content; plan verify command exited 0 (`VERIFY_OK`).
- Commit 86a13aa exists; `GATE OK wiring` observed.
