---
phase: 20-cut-v1-1-0
plan: 07
subsystem: release-cut
tags: [ver-07, rt-02, d-05, d-07, quiet-window, selftest, gate-10, gate-12]
status: red

requires:
  - phase: 20-05
    provides: the v1.1.0 waiver packet (answered in Task 1)
  - phase: 20-06
    provides: release tooling (gate api-baseline, new-module baseline, binary-diff helper)
provides:
  - waiver packet accepted on Yahir's relayed answers (gate waiver GATE OK)
  - real-tree proof of gate 10 (api-dump) and gate 12 (api-check) on HEAD, plus api-baseline
  - quiet window 20-01 opened, used and closed (heavy_gates red)
  - root cause of the selftest red, the stale contract-ledger-only fixture
  - H1 hand-off block (outcome red) in evidence/cut-handoff.txt
affects: [20-08, 20-12, gap plan for scripts/release-cut.sh]

actuals:
  tokens: 2900
  tasks: 3
  commits: 4
plan_head_before: 36aff49e40469bc86c6a17c379ba5457475ea3cf

tech-stack:
  added: []
  patterns: []

key-files:
  created:
    - .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-01.md
    - .planning/releases/v1.1.0/evidence/cut-handoff.txt
  modified:
    - .planning/releases/v1.1.0/WAIVER-PACKET.md

key-decisions:
  - "Quiet window 20-01 was closed red after the single allowed retry of selftest all failed on a genuine control (not an earlyoom kill). No wiring_sha was written."
  - "The red is in the selftest fixture, not the diff gate. contract_append_row appends at EOF, but since eef5cb9 the contract ends with an erratum bullet after the section 11 table. The fix is a scripts/ change, which R3 forbids in 20-07, so it routes to a gap plan."

metrics:
  duration: 31min (window 23:34:36Z to 00:05:43Z; Task 1 earlier)
  completed: 2026-10-08
---

# Phase 20 Plan 07: Waiver answers and quiet window 20-01 Summary

**Waiver packet accepted on Yahir's relayed answers. Gates 10 and 12 and api-baseline are green on the real tree. `selftest all` went RED on the positive control `contract-ledger-only`, whose fixture was made stale by the §11 erratum commit eef5cb9. The window is closed red, no W is fixed, and the hand-off to 20-12 is written.**

## Tasks

| Task | Name | Commit | Result |
|------|------|--------|--------|
| 1 | Record Yahir's relayed waiver answers | a77dc6c | green (packet accepted, `GATE OK waiver`) |
| 2 | Record the quiet window 20-01 grant | 86a3304 (request), 9cab5e2 (grant) | open, relayed_by yahir-gsd-control-plane-3b, date 2026-10-07T23:34:36Z |
| 3 | Heavy gates, W, close | 4914d35 | RED at step 4, window closed, no W |

## Relay

- Who and when: yahir-gsd-control-plane-3b. Resume signal `quiet window 20-01 open 2026-10-07 (UTC now)`, recorded at 2026-10-07T23:34:36Z. The build lock is a1b5723.
- R2 ruling, relayed verbatim: swap-full is accepted if MemAvailable is at least 8 GiB at open. At open: MemAvailable 9528908 kB (9.09 GiB, MET), SwapTotal 2097148 kB, SwapFree 226956 kB.

## Gate results (verbatim)

| Step | Command | UTC | Exit | Final line |
|------|---------|-----|------|------------|
| 1 | `gate api-baseline v1.1.0` | 23:34:53 to 23:34:54 | 0 | `GATE OK api-baseline` |
| 2 | `gate api-dump` (gate 10) | 23:34:59 to 23:35:52 | 0 | `GATE OK api-dump` |
| 3 | `gate api-check v1.1.0` (gate 12) | 23:36:07 to 23:39:32 | 0 | `GATE OK api-check` |
| 4a | `selftest all` | 23:39:49 to 23:41:27 | 1 | `RELEASE SELFTEST FAIL: apiDump failed in the sandbox clone` (earlyoom SIGTERM to java pid 712612) |
| 4b | `selftest all` (the one retry) | 23:42:03 to 00:03:58 | 1 | `RELEASE SELFTEST FAIL: 1 control(s) failed (see the FAIL lines above)` |
| 5 | bash gates | n/a | n/a | not run (the window closes on the first red step) |

The FAIL line:
`FAIL  [contract-ledger-only] expected GREEN but exited 1: 'RELEASE GATE FAIL diff: 1 path(s) changed since the wiring SHA 95b0901ff4 outside the module api.txt files (scripts/modules.list) and .planning/ (the wiring pas' no run recorded a result`

In 4b the happy path was green (`PREFLIGHT OK` and `CUT OK tag=v1.0.1` in the sandbox), 44 controls passed, 1 failed, and the real-repo guard reported unchanged.

## Deviations from Plan

- **Step 4 earlyoom kill (attempt 1).** The swap was below earlyoom's 10% SIGTERM limit, so when MemAvailable dropped below about 15%, earlyoom killed the sandbox Gradle java process. The relayed rule allows one retry, and it was used. The retry ran with no kill.
- **Step 5 not run.** Per Task 3, a red step closes the window. A gap plan changes scripts/, which moves W, so every step has to run again in a new window anyway.

## Root cause of the red (for the gap plan)

The control `ctl_contract-ledger-only` builds its planted row with `contract_append_row`, which uses `printf ... >>` to append to the end of the contract. Its comment states the assumption: "appended at the end of the section 11 table (the last line of the contract)". Commit eef5cb9 (2026-10-04, the §11 erratum for voice-action-engine v1.0.1) added a blank line and a `- **Erratum** ...` bullet after the table's last row (line 288). The synthetic row now lands outside the table, and `contract_change_is_ledger_only` rule (c) rejects it, which is the correct behavior for the gate.

Proposed fix: insert the synthetic row right after the last table row, as computed by `ledger_range` in the clone, instead of appending at EOF. After that fix, all of Task 3 has to run again in a new quiet window, and W will move to the fix commit.

A related check for the real cut: gate 7 accepts orchestrator §11 rows only if they sit inside the table. A ledger tool that appended at EOF would trip gate 7 the same way. It doesn't seem to today, since 43768ea came after eef5cb9 and landed in-table.

## Host readings

| Point | MemAvailable kB | SwapFree kB (of 2097148) |
|-------|-----------------|---------------------------|
| open | 9528908 | 226956 |
| pre step 2 | 9655792 | 226956 |
| pre step 3 | 9658648 | 227196 |
| pre step 4a | 7638088 | 227236 |
| after the kill | 10833848 | 195004 |
| pre step 4b | 14182052 | 200748 |
| close | 14677192 | 225248 |

## Window close

closed 2026-10-08T00:05:43Z, `grant: consumed`, `heavy_gates: red`, no `wiring_sha`. The last non-.planning commit is 7f11d0ecb87f5400202a76d24de56b475f5385b1, not fixed as W. The tree is clean outside .planning. "quiet done" is in the window file for the master to relay.

## Next

The master relays "quiet done" (red) to the orchestrator, then routes a gap plan for `scripts/release-cut.sh` `contract_append_row`. Per H1, plan 20-12 follows on `outcome: red` unless the master reroutes.

## Self-Check: PASSED

- FOUND: .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-01.md
- FOUND: .planning/releases/v1.1.0/evidence/cut-handoff.txt
- FOUND commits: a77dc6c, 86a3304, 9cab5e2, 4914d35
