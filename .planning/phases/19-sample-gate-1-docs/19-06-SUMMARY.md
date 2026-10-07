---
phase: 19-sample-gate-1-docs
plan: 06
subsystem: testing
tags: [gate-1, undo, undo-all, plan-then-execute, vae-undo, evidence-vocabulary]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 19-05 ItemToolExecutor / ItemWorld / PlanLegs.planTier over a stateful ItemStore, and the leg runner routing
provides:
  - undo_all leg (offline, no key, no budget, zero provider calls): two presses, three sub-cases, one verdict
  - VAE_UNDO evidence line in lockstep (ALLOW_PATTERN, host filter ALLOW_RE, one golden line, guard-derived counts, LEGS)
  - undo_all button and undo_label text on the screen (UiTags), awaiting_undo status
affects: [19-07, 19-08, phase-20]

actuals:
  tokens: 17200
  tasks: 3
  commits: 4

plan_head_before: ea8e24da1134c889b1ea10dd511b578ddfd1dee8
commits: 4

tech-stack:
  added: []
  patterns:
    - "A scripted offline provider (UndoScriptProvider) that answers submit_plan with one fixed plan per sub-case, on the demo provider id"
    - "A fresh UndoWorld (seeded store, journal, bridge, executor) per sub-case so one sub-case cannot mask another"
    - "Verdict rules are a pure function over CaseCounts and UndoStep, so every FAIL code is tested without the engine"
    - "Two-press leg: the first press stores an UndoSession in the runner, the second press finishes it (the clarify-option precedent)"

key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/UndoLeg.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UndoLegTest.kt
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/PlanLegs.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/MainActivity.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/SampleScreen.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/HeaderText.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/EvidenceLineTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UiTagsTest.kt
    - sample/src/test/resources/evidence-lines.golden.txt
    - scripts/sample-evidence-filter.sh
    - scripts/run-sample-gate1.sh

key-decisions:
  - "PD-06 as planned: the main sub-case is UI-tapped (run_undo_all then undo_all); the refusal and partial sub-cases run inside the undo press, so the tester presses two controls"
  - "The first press returns a LegResult with an UndoPrompt and an INCONCLUSIVE/awaiting_undo placeholder verdict that is never written to a line; the view model shows it as the status word awaiting_undo (amber)"
  - "A refusal over a touched store is wrong_refusal_reason (the refusal was not clean); a missing refusal is refusal_missing; the first code in the fixed order is the one reported"
  - "A plan tier must declare the providers it may use, so PlanLegs.planTier takes optional StrategyCapabilities; the undo leg passes its scripted provider"

patterns-established:
  - "N is read from journal.group(key).count and pending from the bridge's pendingHeld; the screen prints a held count apart from the label"

requirements-completed: []  # VER-06 / DOC-02 are not ticked here; the phase verifier owns them

status: complete
duration: 70min
completed: 2026-10-07
---

# Phase 19 Plan 06: undo_all leg and the VAE_UNDO line Summary

**An offline `undo_all` leg now shows "Undo all (3)" after a scripted three-action command (two creates, one rename of a seeded item) on a stateful store, undoes the whole command through `journal.undoAll` on the second press, proves a `changed_since` refusal after a later edit and a run-level undo of a PlanThenExecute partial (N = 2, one pending), and writes exactly one `VAE_VERDICT leg=undo_all`; `VAE_UNDO` carries counts, booleans and stable codes only.**

## Accomplishments
- `UndoLeg.kt`: `UndoScriptProvider` (offline, `requiresCredential` false, answers `submit_plan`), `HoldNthGate` and `AdmitAllGate` (sample `PreApplyGate`s, `GateDecision.Hold` for the third proposal), `UndoWorld` (seeded `ItemStore`, journal, `UndoCommitSink`, own `SampleEngine` whose sink is `compositeSink(bridge, NoOpCommitSink)`, journal first), `UndoPlans`, `UndoVerdicts.judge` (pure), `UndoSession` and `UndoLeg.start`.
- Press 1 (`run_undo_all`): the whole-command case runs, `N` is read from `journal.group(key).count`, `VAE_UNDO case=1 phase=counted n=3 committed=3 pending=0 ...` is the only line, status `awaiting_undo`, the screen shows `undo_label` "Undo all (3)" and the `undo_all` button. No verdict yet, no key, no budget (the budget file is untouched).
- Press 2 (`undo_all`): `journal.undoAll` for the whole command (`result=complete restored=3 store_ok=true`); then the refusal case (one rename, `store.edit` of the same item, `undoAll` -> `refused`, `blockers=1`, `reason=changed_since`, `store_ok=true` meaning untouched by the undo); then the partial case (four-step plan, third proposal held: `partial=true committed=2 pending=1 remaining=1 n=2`, then undone with `restored=2 store_ok=true`). Five `VAE_UNDO` lines, then one verdict.
- Verdict FAIL codes (first in this order wins): `undo_incomplete`, `refusal_missing`, `wrong_refusal_reason`, `not_partial`, `wrong_count`; all five are unit-tested through `UndoVerdicts.judge`, and a real FAIL is proven by a write between the presses (`undo_incomplete`, `reason=changed_since`).
- N semantics pinned through the same glue: an applied error (create under a missing parent) counts (N = 2 with one commit), a gate-refused proposal does not count, a held proposal is pending and never in N.
- `VAE_UNDO` lockstep: `ALLOW_PATTERN` (Kotlin), `ALLOW_RE` (host filter), one golden line, the guard's golden-derived counts (unchanged, derived), `undo_all` in `LEGS` and `LegId`; `UndoFacts` closes the phase and result words (`oneOf`), the reason goes through `token`.
- View model: `undoAll()` ignores a press while a leg runs or when no label is showing (changes nothing); another leg's result leaves a waiting label in place.

## Task Commits
1. Task 1 (tracer): `c18c135` feat - undo_all counted from the journal, then undone, `VAE_UNDO` in lockstep (tracer gate: the Task 1 verify, incl. the device guard, was green end to end before Task 2 was started)
2. Task 2 RED: `21f006f` test - refusal, partial and single-verdict expectations (3 of 9 failing for the intended reason: no case 2 / case 3 lines yet)
3. Task 2 GREEN: `b06721d` feat - refusal and PlanThenExecute-partial cases, the combined verdict and the pure judge tests
4. Task 3: `267a91a` test - N semantics and the two-press view-model flow

## Verification
- `:sample:testDebugUnitTest` full suite: 210 tests, 0 failures, 0 errors (UndoLegTest 16 `@Test`, SampleViewModelTest +3, EvidenceLineTest +1, UiTagsTest extended).
- `scripts/verify-sample-device-guard.sh`: `SAMPLE DEVICE GUARD OK scenarios=41` (incl. `filter_keeps_golden` and `leg_list_parity` with the VAE_UNDO golden line); `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`; `bash -n scripts/sample-evidence-filter.sh` clean.
- Acceptance greps all pass: one `^VAE_UNDO ` golden line, `UNDO` in the filter, `LEGS` holds `undo_all`, `undo_all` / `undo_label` in `UiTags.kt`, `compositeSink` and `GateDecision.Hold` in `UndoLeg.kt`, `changed_since` and `Undo all (3)` in the tests, at least 6 `@Test`.
- `git diff` of `UndoCommitSink.kt` is empty (byte-copied doc snippet; `UndoBridgeParityTest` green); `git diff -- core providers keystore undo voice-adapter` over the plan is empty.

## Spend / device / API
No provider, key, money, network or device was touched. The undo leg's engine registers only the in-process scripted provider (zero HTTP, outside the request tap and the budget wrapper); every test also runs under `NoNetworkGuard`. The undo_all leg makes 0 API calls, so no `live_probe_finding` applies.

## Deviations from Plan
None of Rules 1 to 4. Notes:
- `AppGraph.kt` (listed in `files_modified`) needed no edit: the undo leg owns its world and engine, so `LegRunner`'s defaults already wire it.
- `PlanLegs.planTier` gained an optional `StrategyCapabilities` parameter (a tier may use only the providers it declares; the first tracer run returned Unhandled until the undo tier named the scripted provider). Existing callers are unchanged.
- The tracer commit carries a reduced `finish` (whole-command undo only) so that the Task 2 RED/GREEN split is real; the full three-case press arrives in the Task 2 GREEN commit as planned.
- `LegCatalog` prompts for the undo leg are the three committed transcripts (whole, refusal, partial); the scripted provider ignores their text and answers a fixed plan.
- Observation, not changed: the second `UndoSession.finish` runs the refusal and partial cases only on the undo press, so a tester who never presses `undo_all` sees `awaiting_undo` and no verdict line; `capture-save undo_all` correctly refuses then.

## Self-Check: PASSED
- Created files present: UndoLeg.kt, UndoLegTest.kt; commits c18c135, 21f006f, b06721d, 267a91a present.
