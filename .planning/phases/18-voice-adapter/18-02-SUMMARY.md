---
phase: 18-voice-adapter
plan: 02
subsystem: voice-adapter
tags: [docs, ecosystem, integration, roadmap]
requires: [18-01]
provides:
  - "ECOSYSTEM.md :voice-adapter row and corrected unpublished-modules sentence"
  - "INTEGRATION.md section 12 (single segment, label pass-through, minimum, consumer repository block, server fallback caveat)"
  - "README and API.md voice-adapter entries"
  - "ROADMAP Phase 18 criterion 1 and consumer-map row 18 reworded for single-segment-only"
affects: [18-03, 18-07]
tech-stack:
  added: []
  patterns: ["docs name only the frozen function set (toCommandInput, commandInputOf, normalizeSttLanguageLabel); no kotlin fences"]
key-files:
  created: []
  modified:
    - ECOSYSTEM.md
    - INTEGRATION.md
    - README.md
    - API.md
    - .planning/ROADMAP.md
key-decisions:
  - "PD-03: documented minimum worded ':stt v0.7.0 or newer; the language property compiles against v0.6.0'"
requirements-completed: [ADPT-01]
status: complete
completed: 2026-10-06
plan_head_before: fd580da5ac5602a60aca75ba728af4cdc4bf3241
commits: 3
actuals:
  tokens: 5000
  tasks: 3
  commits: 3
---

# Phase 18 Plan 02: Voice adapter docs and roadmap wording Summary

The docs and roadmap now say the adapter maps one final `:stt` segment per call (no joiner, apps keep their own aggregation), passes `en`/`es` labels through with null meaning unknown, documents `:stt` v0.7.0 or newer, and show a consumer `exclusiveContent` block listing both JitPack groups with the per-module `:stt` coordinate.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1 Tracer: ECOSYSTEM row + sentence | 98af31e | hygiene OK; row, compileOnly note, "not yet published, first tag v1.1.0", repin matrix untouched |
| 2 INTEGRATION section 12, README, API.md | 4425496 | doc coverage OK (25 checks), hygiene OK; two `includeGroup(` lines, no kotlin fence added |
| 3 ROADMAP criterion 1 and map row 18 | f137ba8 | scoped two-line edit; phase heading count unchanged (9) |

## Verification (plan-level only)

- `scripts/verify-docs-coverage.sh` prints `DOC COVERAGE OK checks=25 types=107`; `scripts/verify-repo-hygiene.sh` prints `HYGIENE OK`.
- All task acceptance greps pass (section 12 heading, minimum wording, v0.6.0 note, exclusiveContent, fallback caveat, never-log warning, forbidden-word scan clean).
- No Gradle run, no device or behavioral verification.

## Deviations from Plan

None. Minor: the ECOSYSTEM paragraph names the artifact id `voice-action-engine-voice-adapter` once more so the plan's "at least 2" grep holds; the plan's replacement sentence itself does not carry it.

## Notes

- The docs describe the full frozen surface (three overloads of `toCommandInput` and `commandInputOf`); only one `toCommandInput` overload exists until plan 03 lands. Plan 07's doc-versus-dump check covers this.
- API.md's "four published modules" intro line was left alone (Phase 19 DOC-02 owns full parity).

## Self-Check: PASSED

Modified files exist; commits 98af31e, 4425496, f137ba8 exist on gsd/phase-18-voice-adapter.
