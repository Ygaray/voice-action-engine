---
phase: 19-sample-gate-1-docs
plan: 01
subsystem: testing
tags: [gate-1, bash, device-guard, decision-file, modules-manifest]

requires:
  - phase: 12-wave-1-seams-w04-fix
    provides: guarded runner scripts/run-sample-gate1.sh and its offline guard verifier
provides:
  - runner decision file and evidence dir retargeted to Phase 19 via env overrides (VAE_GATE1_DECISION_FILE, VAE_GATE1_EVIDENCE_DIR)
  - manifest-driven dirty check in build-install (core, providers, keystore, undo, voice-adapter)
  - guard verifier at 41 scenarios (was 33)
  - 19-LIVE-LEG-DECISION.md in the pending state with the 16-request / USD 0.05 ask
affects: [19-07, 19-13, 19-14, phase-20]

actuals:
  tokens: 14000
  tasks: 3
  commits: 3

plan_head_before: 67500a7ce0785ff1fc7cebae137379c5223da166
commits: 3

tech-stack:
  added: []
  patterns:
    - "Planning-file paths are env overrides with a derived default; no variable can move the adb target"
    - "Runner degrades to dirty=unknown when the module manifest is unreadable, never failing the install"

key-files:
  created:
    - .planning/phases/19-sample-gate-1-docs/19-LIVE-LEG-DECISION.md
  modified:
    - scripts/run-sample-gate1.sh
    - scripts/verify-sample-device-guard.sh

key-decisions:
  - "Env names VAE_GATE1_DECISION_FILE and VAE_GATE1_EVIDENCE_DIR beside VAE_GATE1_PHASE_DIR; derived decision name is the text before the first hyphen of the phase directory basename"
  - "Manifest unreadable => dirty=unknown (degrade), and the guard sandbox copies lib/modules.sh and modules.list so the real path is exercised"
  - "LEGS= line left untouched; it changes with the LegId enum in plans 04-06 (leg_list_parity)"

requirements-completed: []  # VER-06 is only enabled here (runner retarget); the live Gate-1 legs that satisfy it run in later plans

status: complete
duration: 20min
completed: 2026-10-07
---

# Phase 19 Plan 01: Retarget the Gate-1 runner Summary

**Gate-1 runner now reads Phase 19's own decision file and evidence dir by default (env-overridable, never edited per run), checks dirty state from the module manifest, and refuses `consumed`; 41 offline guard scenarios prove it.**

## Accomplishments
- `scripts/run-sample-gate1.sh`: `PHASE_DIR` defaults to `.planning/phases/19-sample-gate-1-docs`; `DECISION_FILE` = `VAE_GATE1_DECISION_FILE` or `<prefix>-LIVE-LEG-DECISION.md` in `PHASE_DIR`; `EVIDENCE_DIR` = `VAE_GATE1_EVIDENCE_DIR` or `PHASE_DIR/evidence`. The exact-line `decision: approved` check is unchanged, so pending, deferred, consumed and missing all refuse.
- build-install dirty check reads `vae_modules` plus `sample scripts gradle build.gradle.kts settings.gradle.kts`; undo and voice-adapter are now covered.
- Guard verifier: retargeted to the Phase 19 default; new scenarios `decision_override_wins_approved`, `decision_override_wins_pending`, `decision_derived_name` (20-cut-sample), `push_keys_consumed`, `evidence_dir_override`, `build_install_clean_git`, `build_install_dirty_undo`, `build_install_manifest_missing`. Total 41 (plan required at least 38).
- `19-LIVE-LEG-DECISION.md` created with `decision: pending` and a Relay section that asks (does not grant) 16 requests / USD 0.05, TESTER R5CT10XNKQN only.

## Task Commits
1. Task 1 (tracer): `137f395` feat - env-overridable paths, Phase 19 default, pending decision file
2. Task 2: `2a0a261` test - override / derived name / consumed / evidence-dir scenarios
3. Task 3: `fa24fcf` feat - manifest-driven dirty list and scenarios

## Verification
- `bash -n` on both scripts passes; `scripts/verify-sample-device-guard.sh` ends `SAMPLE DEVICE GUARD OK scenarios=41`.
- Tracer gate: the guard's `push_keys_happy` / `push_keys_not_approved` now exercise the Phase 19 default and pass.
- All task acceptance greps pass; `scripts/sample-evidence-filter.sh`, `scripts/modules.list`, `scripts/lib/modules.sh` unchanged.
- No device, key, provider or Gradle was touched (fake adb and fake push-test-key only).

## Deviations from Plan
None to the plan's tasks. Bookkeeping note: `requirements.mark-complete VER-06` ticked VER-06 in REQUIREMENTS.md, but this plan only prepares the runner; the requirement needs the live TESTER legs, so I reverted that tick and left `requirements-completed` empty. Three extra scenarios beyond the minimum (clean control, missing manifest, second override direction) were added to make the dirty and override proofs discriminating.

## Decisions Made
See `key-decisions`. The decision line was NOT changed from `pending`; only a relayed orchestrator answer (plan 19-07) may do so.

## Self-Check: PASSED
- 19-LIVE-LEG-DECISION.md present with exact `decision: pending`; commits 137f395, 2a0a261, fa24fcf present.
