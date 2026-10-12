# Phase 13: On-Device Model Spike - Disposition (SPIKE-03)

branch: red
envelopes_green: none
spike03: N/A-deferred
harness_sha: 08ada3366b
phase17_plumbing: absent
date: 2026-10-06

## Branch computation (D-08, mechanical)

Input: the two `SPIKE_VERDICT` lines of `13-VERDICT.md` (code SHA `e362fb228b`).

| Envelope | Verdict |
|---|---|
| small | red |
| sb | red |

Rule applied: both lines read `verdict=red`, so the branch is `red`. (Had either read green, the branch would be `green_ship` when
`settings.gradle.kts` includes `:undo` and `jitpack.yml` has an `:undo` publish task on a non-comment line, else `green_defer`.
Phase 17 plumbing was absent, but that only matters for a green verdict.)

## Rationale

Per D-08, a red verdict means nothing ships: no module, no code, and the `v1.1.0` tag is not blocked (L10). The spike module is
removed from the build (its include, its catalog entries and its device-only scripts) so that JitPack never configures it. The
harness stays reproducible from history (`harness_sha` is the commit that produced the evidence; the verdict code, which `verify-spike-verdict.sh --check` rebuilds, is `e362fb228b`) and from the kept filter and verdict scripts, the committed evidence, the
verdict and the thresholds. The sb envelope is red because it is unmeasured (time-box), not measured-and-failed; D-07 treats an
unmeasured gating metric as red, so the branch is the same.

## Follow-ups for the driver

- Red: announce the disposition together with the verdict thread (small RED measured, sb RED unmeasured by the time-box); nothing to insert.
- No Phase 13.1 is requested, `ROADMAP.md` gains no inserted phase, and `v1.1.0` is not blocked (L10).
- Candidate for the next milestone's harness work, if the spike is ever re-run: the verdict code reads sb peak PSS on the winning cell only (13-09 deviation 4). The harness is recoverable at `e362fb228b` (the verdict code, with the RT-03 grading tolerance and the host-side sb re-score); the evidence was produced by `08ada3366b` (`harness_sha`).
