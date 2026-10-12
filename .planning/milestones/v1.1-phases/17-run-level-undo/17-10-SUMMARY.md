---
phase: 17-run-level-undo
plan: 10
subsystem: heavy-gates-quiet-window
status: complete
tags: [quiet-window, negative-controls, metalava, jitpack, undo, undoalone, host-memory]

requires:
  - phase: 17-03
    provides: "the three :undo zero-dependency plants and the :undo source plants in verify-negative-controls.sh"
  - phase: 17-04
    provides: "manifest-driven jitpack-dry-run.sh and the :undoalone stand-alone consumer probe"
  - phase: 17-09
    provides: "17-QUIET-WINDOW.md relayable request (grant: pending) and the green autonomous phase gate"
provides:
  - "17-QUIET-WINDOW.md: relayed grant (open, then consumed), pre-check memory readings, and the verbatim evidence lines from the three heavy gates"
  - "UNDO-01 mechanical proofs run green: the :undo dependency plants go red, the Metalava wiring proof holds with undo/api.txt, and a clean consumer resolves voice-action-engine-undo with no :core"
affects: [19, 20]

actuals:
  tokens: 2000
  tasks: 3
  commits: 3
plan_head_before: ee9dd8f373f34196daf30b75b4167bfaad6b7d0b
commits: 3

key-files:
  created: []
  modified:
    - .planning/phases/17-run-level-undo/17-QUIET-WINDOW.md

key-decisions:
  - "Task 1's checkpoint was satisfied by the relayed grant (orchestrator yahir-gsd-control-plane-3b via the milestone master, 2026-10-06), recorded verbatim; no self-authored grant"
  - "Ran all three heavy gates at the low-memory recipe (no daemon, workers.max=2, parallel=false, in-process Kotlin, Xmx1536m), one at a time, with a /proc/meminfo pre-check before each. MemAvailable stayed above 8.4 GiB, so the 5 GiB stop line was never reached"

metrics:
  duration: "~30 min"
  completed: 2026-10-07
---

# Phase 17 Plan 10: Heavy Gates in the Quiet Window Summary

All three host-heavy UNDO-01 gates ran green in the relayed quiet window, 2026-10-06T23:50:06Z to 2026-10-07T00:19:47Z. The full negative-control suite (with the :undo plants) reported `negative-control failures: 0`. The Metalava proof printed `API DUMP PROOF OK`. The clean-cache JitPack dry run published exactly four artifacts, with `voice-action-engine-undo` as a jar. The `:undoalone` consumer compiled against `:undo` with no `voice-action-engine-core` or coroutines on its runtime classpath.

## Relayed answer (Task 1)

Relayed by orchestrator yahir-gsd-control-plane-3b via the milestone master, on 2026-10-06. Verbatim:

"QUIET WINDOW CONFIRMED (17-CONTEXT.md Runtime Decisions RT-01, commit ee9dd8f): orchestrator yahir-gsd-control-plane-3b holds the VAE build lock (control-plane 65c20f2); no other repo is running Gradle. Memory bounds: a transient 4.4 GB mempalace mine is also running (MemAvailable ~8.4 GiB at grant) - run every Gradle step with --no-daemon (or at most one daemon), workers.max=2, parallel=false; check /proc/meminfo before each heavy step and STOP (needs_human, type quiet_window_memory) if MemAvailable drops below 5 GiB rather than risking an earlyoom kill."

## Accomplishments

- **verify-negative-controls.sh: exit 0.** It printed 121 `ok` lines and ended with `negative-control failures: 0`.
  - Every `:undo` plant went red for the right reason:
    - `forbidden project edge undo -> core`
    - `undo gains a library dependency`
    - `undo test classpath gains core testFixtures`
    - `undo gains an ML dependency`
    - `api.txt missing once released (undo)`
    - the source plants
  - The ML-denial part printed `ML DENIAL CONTROLS OK plants=9`.
- **verify-api-dump.sh: exit 0.** It printed `API DUMP PROOF OK`. `undo/api.txt` is 191 lines.
- **jitpack-dry-run.sh: exit 0.** It printed:
  - `DRY RUN OK version=dryrun-4cd2b5bf56`
  - `PROBE OK`
  - four `artifact:` lines: core jar, providers jar, keystore aar, undo jar
  - an `:undoalone runtimeClasspath` block that lists only `voice-action-engine-undo`
- **Host safety:**
  - Pre-check MemAvailable readings were 8815412, 12507216 and 9298428 kB (8.4 GiB minimum).
  - Swap was full throughout (2.0Gi of 2.0Gi), as warned.
  - The only Gradle daemon was a pre-existing idle one (pid 4004320).
  - No earlyoom kill. No process killed, no `--stop`, no sudo, no device, no keys.

## Task Commits

1. **Task 1: record the relayed quiet-window grant**: `d84843d` (docs)
2. **Task 2: negative-control suite and API dump proof**: `4cd2b5b` (docs)
3. **Task 3: clean-cache JitPack dry run with :undoalone; window consumed**: `3f7e239` (docs)

## Deviations from Plan

None. The plan was executed as written, on the window path. No code, script or test changed. The only file changed is `17-QUIET-WINDOW.md`.

Note on the gate greps: Task 1's acceptance grep (`^grant: (open|deferred)$`) passed at commit `d84843d`. Task 3 then moved the line to `grant: consumed`, as the plan requires, so that grep no longer matches HEAD. That is by design.

## Issues Encountered

- One sentence in my first Results draft said "no PROBE FAIL", which falsely tripped Task 3's `! grep -q 'PROBE FAIL'` check. I reworded it before committing. The run itself never printed a failure line.
- The negative-control log contains one `FAILED` token: `noOnDeviceImplementationCode FAILED`. It sits inside an `ok` line as the expected red reason for the `:core` on-device-scan plant. It is not a failure.

## Verification

- Task 2 verify: `^negative-control failures: 0$` and `^API DUMP PROOF OK` are both present (exit 0).
- Task 3 verify: `grant: consumed`, `^DRY RUN OK version=`, `^PROBE OK` and `voice-action-engine-undo` are all present, and `PROBE FAIL` is absent (exit 0).
- `ok    [forbidden project edge undo` count = 1. `opened:` is present. `voice-action-engine-undo-` count >= 1.
- No `voice-action-engine-core` line in the `:undoalone` block (count 0). `relayed_by:` is present.

## Known Stubs

None.

## Threat Flags

None. T-17-31 was mitigated by the pre-checks, the low-memory recipe, one run at a time and no retry. T-17-32 was mitigated because the grant came only from the relay. T-17-01 was mitigated because the :undo plants and the :undoalone probe ran green.

## Self-Check: PASSED

- FOUND: .planning/phases/17-run-level-undo/17-QUIET-WINDOW.md
- FOUND: d84843d, 4cd2b5b, 3f7e239
