---
phase: 18-voice-adapter
plan: 07
subsystem: api-surface-gates
tags: [metalava, api-seed, surface-review, quiet-window, aar]
requires:
  - phase: 18-voice-adapter
    provides: "plans 02 (docs), 03 (tests) and 06 (stt gates) that the phase gate runs over"
provides:
  - "scripts/verify-api-seed.sh chooses the Metalava task from the manifest packaging (aar -> metalavaCheckCompatibilityRelease, jar -> metalavaCheckCompatibility) and VAE_PRINT_TASK=1 prints it without Gradle"
  - "18-SURFACE-REVIEW.md: gate results, verbatim isolated dump, frozen names, docs-versus-dump check, carry list"
  - "18-QUIET-WINDOW.md with grant: pending for plan 18-08"
affects: [18-08]
actuals:
  tokens: 9000
  tasks: 3
  commits: 3
tech-stack:
  added: []
  patterns: ["task name derived from the packaging column, with a Gradle-free print mode so both branches are provable", "surface frozen only after reading a real dump from an isolated copy"]
key-files:
  created:
    - .planning/phases/18-voice-adapter/18-SURFACE-REVIEW.md
    - .planning/phases/18-voice-adapter/18-QUIET-WINDOW.md
  modified:
    - scripts/verify-api-seed.sh
key-decisions:
  - "The two facade class names (FinalSegmentCommandInput, SttLanguageLabels) are frozen but intentionally not named in the docs: Kotlin callers never write them, the docs name the functions"
  - "No api.txt, release-cut.sh or sample change; both are owned by the release-cut phase and Phase 19"
requirements-completed: [ADPT-01]
status: complete
duration: 20 min
completed: 2026-10-07
plan_head_before: a498a83f7fbda8702b1645fbbf282c0acd4646af
commits: 3
coverage:
  - id: D1
    description: "The seed proof understands an AAR module: the voice-adapter header-only seed is accepted by Metalava and a planted removal goes red"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: "scripts/verify-api-seed.sh voice-adapter -> API SEED OK module=voice-adapter executed=yes removal=red (exit 0)"
        status: pass
      - kind: command
        ref: "VAE_PRINT_TASK=1 for undo, voice-adapter and keystore -> :undo:metalavaCheckCompatibility, :voice-adapter:metalavaCheckCompatibilityRelease, :keystore:metalavaCheckCompatibilityRelease"
        status: pass
    human_judgment: false
  - id: D2
    description: "The whole autonomous phase gate is green in one recorded run"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: ":voice-adapter:check :core:check plus the manifest, hygiene, docs-coverage, stt-confinement and release-manifest scripts (with selftests) -> all exit 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "The real public surface of :voice-adapter matches the frozen set (3 toCommandInput, 3 commandInputOf, 1 normalizeSttLanguageLabel, :stt only in the segment overloads, no collection type) and every function is named in the docs"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: "scripts/api-dump-isolated.sh --out /tmp/vae-18-dump plus count and grep assertions on voice-adapter.api.sig and INTEGRATION.md / API.md -> exit 0"
        status: pass
    human_judgment: false
  - id: D4
    description: "Whether the frozen names are the right names for consumers (one-way at v1.1.0)"
    requirement: ADPT-01
    verification: []
    human_judgment: true
    rationale: "Name quality is a design judgment recorded in 18-SURFACE-REVIEW.md for the milestone UAT; no test asserts it"
---

# Phase 18 Plan 07: Surface freeze review and quiet-window request Summary

The seed proof now picks the Metalava task from the module packaging, the autonomous phase gate is green in one run, the real isolated dump of `:voice-adapter` matches the frozen set exactly, and the heavy gates are queued behind a pending quiet-window request.

## Accomplishments

- `scripts/verify-api-seed.sh` reads the packaging column and uses `:<m>:metalavaCheckCompatibilityRelease` for aar and `:<m>:metalavaCheckCompatibility` for jar; `VAE_PRINT_TASK=1` prints the chosen task and exits before any other check. `scripts/verify-api-seed.sh voice-adapter` printed `API SEED OK module=voice-adapter executed=yes removal=red`.
- One run of `:voice-adapter:check :core:check` and every bash gate (module manifest and selftest, repo hygiene, docs coverage, stt confinement and selftest, release manifest) exited 0. `free -h` before the run: 13 GiB available, swap full (2.0 GiB of 2.0 GiB), above the 5 GiB stop line.
- The isolated dump (`API DUMP ISOLATED OK ... voice-adapter=18`) shows two facade classes and seven public functions: 3 `toCommandInput`, 3 `commandInputOf`, 1 `normalizeSttLanguageLabel`; `:stt` `FinalSegment` appears only in the three `toCommandInput` signatures; no collection or iterable type (no joiner).
- `18-SURFACE-REVIEW.md` records the gate results, the verbatim dump, the frozen names (one-way at v1.1.0), the docs-versus-dump check and the carry list. `18-QUIET-WINDOW.md` was written with `grant: pending`.

## Task Commits

| Task | Commit | Description |
|---|---|---|
| 1 (tracer) | 8ea90f1 | Packaging-aware task selection and `VAE_PRINT_TASK` in `scripts/verify-api-seed.sh` |
| 2 | 8d2cfd2 | Gate results section of the surface review |
| 3 | 5e491b8 | Dump-based surface review, carry list, pending quiet-window request |

The tracer was verified end to end (print mode for both packagings, then the full seed proof) before the other tasks.

## Carry items for the release-cut phase (repeated from the review)

- (a) `scripts/release-cut.sh` gates 10 and 12 and the self-test step 4 need a new-module branch for BOTH `:undo` and `:voice-adapter`; both ship header-only `api.txt` seeds. This plan did not edit that script. Release-cut owns handling the two modules together.
- (b) The clean-cache `:adapteralone` consumer probe (an app that resolves the adapter and adds `:stt` itself) is deferred to the release-cut phase's dry run because it needs network access to the external repository for `:stt`.
- Also recorded in the review: (c) Phase 19 `:sample` wiring edges, (d) DOC-02 and the hard-coded module lists, (e) D-07 stays with the `:stt` v3.2 seed, (f) consumers may delete their own label helpers, (g) consider the confinement script in the release preflight.

## Remaining for 18-08 (heavy steps, need the relayed quiet window)

1. `scripts/verify-negative-controls.sh` (includes Part 6 `:stt` controls and the voice-adapter source plants).
2. `scripts/verify-api-dump.sh`.
3. Clean-cache `scripts/jitpack-dry-run.sh` (publishes the voice-adapter AAR).

`18-QUIET-WINDOW.md` stays `grant: pending` until the orchestrator relays an answer; fallback is a deferred obligation owned by Phase 19.

## Deviations from Plan

None - plan executed exactly as written. Note: the Gradle log of the `check` run ends with the detekt negative-control fixture report (`config/negative-controls/detekt/ForbiddenImports.kt`); that is expected self-test output, the exit status was 0 and no FAILED token appeared.

## Authentication Gates

None. No device, no API key, no heavy gate, no change to any `api.txt` or `scripts/release-cut.sh`.

## Self-Check: PASSED

- FOUND: `.planning/phases/18-voice-adapter/18-SURFACE-REVIEW.md`, `18-QUIET-WINDOW.md` (`grant: pending`), `scripts/verify-api-seed.sh`
- FOUND commits: 8ea90f1, 8d2cfd2, 5e491b8
- `git diff --exit-code` on all `api.txt` files exits 0; `voice-adapter/api.txt` is still the header-only seed.
