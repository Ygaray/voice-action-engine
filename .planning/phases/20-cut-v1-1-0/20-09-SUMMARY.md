---
phase: 20-cut-v1-1-0
plan: 09
subsystem: release-cut
tags: [ver-07, c4, c10, wiring-rerun, gate-6, jitpack, isolation]
status: complete

requires:
  - phase: 20-08
    provides: W = 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1 pushed and JitPack-built (LIVE PROBE PASS ref=4bdb663b4c), D-02 waived (RT-12)
provides:
  - WIRING-RERUN.md rewritten for the full W (status pass, WIRING TEST: PASS checks=13), GATE OK wiring
  - wiring-stumbles-p20.txt and wiring-consulted-p20.txt (verbatim), three clarity-only stumbles triaged
  - gate1-delta-final.txt (C10: sample delta since Gate-1 build head 1869950dca is JVM-proven only)
  - window 20-09 (20-QUIET-WINDOW-02b.md) closed green, window 20-02 header reconciled
  - planning record pushed (origin/main ec0e2a7), GATE OK pushed, eight cheap release gates green on HEAD
affects: [20-10 (window 20-03 request, preflight and cut), 20-11 (LEDGER-ROW notes carry S1-S3), 20-12]

actuals:
  tokens: 15000
  tasks: 3
  commits: 7
plan_head_before: 1d09681c6fdad52a863171913ecf83dcea76e42b

key-files:
  created:
    - .planning/releases/v1.1.0/evidence/wiring-stumbles-p20.txt
    - .planning/releases/v1.1.0/evidence/wiring-consulted-p20.txt
    - .planning/releases/v1.1.0/evidence/gate1-delta-final.txt
    - .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-02b.md
  modified:
    - .planning/releases/v1.1.0/WIRING-RERUN.md
    - .planning/releases/v1.1.0/evidence/relay-log.md
    - .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-02.md
    - .planning/phases/20-cut-v1-1-0/20-11-PLAN.md

key-decisions:
  - "The three new wiring stumbles S1-S3 are clarity-only (every name and shape matched W's source), so none blocks the tag; they are a known follow-up for a later docs patch and are listed in the 20-11 LEDGER-ROW notes (not part of this cut: a docs edit would void the pass on W)."
  - "Plan 20-09 ran in window 20-09 (file 02b), not in window 20-02, which stayed closed red (D-02, since waived by RT-12); the plan's 'window 20-02 is consumed' wording is satisfied by 02b plus the reconciled 02 header."

metrics:
  duration: 20min (01:09Z window request to 01:29Z close-out)
  completed: 2026-10-08
---

# Phase 20 Plan 09: Isolated wiring re-run on W (C4, gate 6) Summary

**A separate headless agent wired the engine from the four documents alone against the JitPack build of W (`4bdb663b4c`), and the empty-cache judge printed `WIRING TEST: PASS checks=13`; WIRING-RERUN.md is rewritten for the full 40-hex W, `gate wiring` is green, none of the five Phase 19 stumbles recurred, and the planning record is on origin/main (`ec0e2a7`).**

## Outcome lines (verbatim)

- Verify (empty Gradle cache, JitPack, one run, exit 0, 2026-10-08T01:19:34Z to 01:21:03Z, command `scripts/agent-wiring-test.sh verify /tmp/vae-wiring-20 4bdb663b4c`):
  `WIRING TEST: PASS checks=13`
- Prepare: `WIRING PREPARED dir=/tmp/vae-wiring-20 version=4bdb663b4c` (selftest-source exit 0, `WIRING SOURCE SELFTEST OK`; ancestors_clean yes).
- Record gate: `scripts/release-cut.sh gate wiring 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1` -> `GATE OK wiring`.
- Push (orchestrating layer, 2026-10-08T01:27Z, plain): `git push origin main` -> `26dcd10..ec0e2a7  main -> main`; right after, `scripts/release-cut.sh gate pushed` -> `GATE OK pushed`;
  `git ls-remote origin refs/heads/main` -> `ec0e2a7cb45d78ff74e039dd6367179120155504`. Origin tags: v1.0.0 and v1.0.1 only.

## Window times

| Window | Opened | gates_started | Closed | Result |
|--------|--------|---------------|--------|--------|
| 20-09 (`20-QUIET-WINDOW-02b.md`) | 2026-10-08T01:12:35Z | 01:16:18Z (agent dispatch) | 01:23:20Z | heavy_gates green, `quiet done` |

Readings at open: MemAvailable 14445976 kB (13.78 GiB), SwapFree 231336 kB of 2097148 kB (about 11%, accepted under the standing swap ruling), no Gradle process. Agent run
01:16:18Z to 01:18:42Z (exit 0, cfg_removed yes), verify 01:19:34Z to 01:21:03Z, workspace removed 01:21Z. No earlyoom kill, no retry. Window 20-02 stays closed red as recorded; only
its header was reconciled (`heavy_gates: green`, `first_attempt: red (D-02, waived RT-12)`).

## Isolation audit (T-20-24, T-20-25)

ancestors_clean yes; `consulted_only_workspace: true` (workspace-relative paths only); no URL, no engine or sample source path, no `.credentials.json` in the workspace; key/URL/home-path scan of
STUMBLES.md, CONSULTED.md and the final message found no hit; the throwaway `CLAUDE_CONFIG_DIR` was removed with a plain `rm` (never the real `~/.claude`); the engine came from JitPack (`prepare`, not
`prepare-local`; version equals the 20-08 probe ref). The four docs in the workspace were byte-identical to `git show 4bdb663b4c:<doc>`.

## Triage of the agent's stumbles

None of the five Phase 19 stumbles (router answer shape, `submit_plan` schema, bridge imports, SingleShot read tool, `UndoJournal { }` `store` shadowing) recurred. Three new ones, all
**clarity-only** (information missing from a reference table; each name and shape in the docs matched W's source), none blocks the tag:

| # | Stumble | Patch later in |
|---|---------|----------------|
| S1 | `StepResult` constructors and the third argument (`appOutcomeToken`) not listed | API.md "Shapes you construct or read" |
| S2 | `Resolution.Escalate(reason, carry)` and `EscalationReason.Other(code)` constructors not listed | API.md "Shapes you construct or read" |
| S3 | `UndoCommitSink` is copy-in app code, not a published type; `PendingMutation.context` and `ExecutedAction.position` not named in the reference tables | INTEGRATION.md section 11; API.md `ExecutedAction` / `PendingMutation` rows |

Per the orchestrator ("The 3 new doc stumbles are fine as later patches; list them as a known follow-up in the ledger evidence"), S1-S3 are now written into the `notes:` follow-ups paragraph of the
LEDGER-ROW requirement in `20-11-PLAN.md` as a KNOWN FOLLOW-UP that is not part of this cut. They were NOT fixed: a docs edit would void the pass on W.

## C10: Gate-1 delta

`evidence/gate1-delta-final.txt` records `git diff --stat` and `--name-only` from the Gate-1 build head `1869950dca` to W: the sample delta (10 files, +609/-15, including the WR-04 `ItemToolExecutor`
change and the RT-07 fingerprint change) is JVM-proven only; no further TESTER window is planned in v1.1.0; the sample must be rebuilt and reinstalled from the final SHA before any later live window.

## Cheap release gates on HEAD ec0e2a7 (each its own plain command, real exit 0, 2026-10-08T01:28Z)

`GATE OK wiring`, `GATE OK diff`, `GATE OK waiver`, `GATE OK tags-absent`, `GATE OK create-tag`, `GATE OK clean`, `GATE OK leak` (`content_check=skipped(no local fixture)`), `GATE OK hygiene`.
`git diff --name-only 4bdb663b4c HEAD` lists nothing outside `.planning/` (gate 7 holds); W is unchanged.

## Tasks and commits

| Task | What | Commit |
|------|------|--------|
| (setup) | Request window 20-09 (file 02b), record the grant | 01e55d3, 39cb71d |
| 1 | Prepare on the pushed W (JitPack path), ancestors_clean; agent dispatched by the orchestrating layer (checkpoint) | dabbb9a |
| 2 (tracer) | Empty-cache verify PASS, isolation audit, WIRING-RERUN.md for W, stumbles/consulted, C10 delta, `gate wiring` OK | 3501508 (35015085c8a36143b2eb14d46321de336810a509) |
| 3 | Close window 20-09 green, reconcile window 20-02 header, relay 4; pre-push cheap gates | 6db9092, ec0e2a7 |
| 3 (post-push) | Relay 5 (push relay verbatim), push section in 02b, S1-S3 in 20-11 notes, this summary | the close-out commit after this file |

`commits: 7` counts `git rev-list 1d09681..ec0e2a7` (it includes 0fca3b3, the 20-11 plan clause added while 20-09 ran); the post-push close-out commit is not in that count. The per-plan HEAD ledger
file did not exist (the plan began before the ledger protocol was applied to this plan), so `plan_head_before` is the 20-08 completion commit `1d09681`.

## Deviations from Plan

1. **Window file.** The plan's verify reads `20-QUIET-WINDOW-02.md`; the plan ran in window 20-09 (`20-QUIET-WINDOW-02b.md`) because window 20-02 was closed red after D-02 (waived RT-12). 02 carries
   the reconciled header (`heavy_gates: green`); its body is the unchanged red record. Same 01/01b pattern as before.
2. **Executor did not run the agent or the push.** Task 1 (headless dispatch) and the push of Task 3 were done by the orchestrating layer, as the plan models them (checkpoints); the executor prepared,
   verified, audited, wrote the record and re-verified the push read-only.
3. **No rule 1-3 fixes were needed**; no source, script or document under W changed.

## Known follow-ups (not in this cut)

- Docs patch for S1-S3 (above).
- An idle Gradle daemon (pid 1039601, left by the wiring agent's own build) was still alive after the window closed; it was never touched. Plan 20-10's pre-check must note it (foreign/idle daemon,
  never stop it, no `--stop`).

## Self-Check: PASSED

- FOUND: .planning/releases/v1.1.0/WIRING-RERUN.md (`status: pass`, `WIRING TEST: PASS checks=13`), evidence/wiring-stumbles-p20.txt, wiring-consulted-p20.txt, gate1-delta-final.txt, relay-log.md (Relay 5)
- FOUND: .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-02b.md (grant consumed, closed, quiet done, Push section), 20-QUIET-WINDOW-02.md (heavy_gates green)
- FOUND commits: 01e55d3, 39cb71d, dabbb9a, 3501508, 6db9092, ec0e2a7 (all on origin/main)
- `git diff --name-only 4bdb663b4c HEAD` outside `.planning/`: none; origin tags: v1.0.0, v1.0.1 only
