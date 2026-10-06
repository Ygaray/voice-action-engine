---
phase: 17-run-level-undo
plan: 09
subsystem: undo-docs-and-surface-review
status: complete
tags: [docs, undo, doc-snippet, parity-test, metalava, surface-review, quiet-window]

requires:
  - phase: 17-04
    provides: "manifest-driven docs coverage (MODULE_ALT) and the release-manifest proof"
  - phase: 17-07
    provides: "UndoGroup view, withheld groups, isolation, bounds and the store mirror"
  - phase: 17-08
    provides: "UndoCommitSink with the undo-bridge markers, ItemStore, ItemAdapter, mutations, end-to-end proofs"
provides:
  - "DocSnippetsTest region undo-bridge (byte copy of the sample bridge) and a test that runs it through commandPipeline"
  - "UndoBridgeParityTest: the compiled doc region equals the sample main bridge between its markers"
  - "INTEGRATION.md section 11: coordinate, adapters, ticket protocol, bridge snippet, grouping and N, refusals, result set, status mapping, limits and store"
  - "API.md: undo packages line, undo type table (12 types) and three Extension-points rows"
  - "17-SURFACE-REVIEW.md: frozen-surface review from the real isolated dumps, frozen names, resolutions, A18, handoff, open items, gate results"
  - "17-QUIET-WINDOW.md: relayable request for the heavy gates, grant: pending"
affects: [17-10, 19, 20]

actuals:
  tokens: 7500
  tasks: 3
  commits: 3
plan_head_before: 9f8934873e2ae4ab51b1b7899606b46a379b8dd8
commits: 3

key-files:
  created:
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoBridgeParityTest.kt
    - .planning/phases/17-run-level-undo/17-SURFACE-REVIEW.md
    - .planning/phases/17-run-level-undo/17-QUIET-WINDOW.md
  modified:
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
    - INTEGRATION.md
    - API.md

key-decisions:
  - "The doc region was generated from the sample main file by script (text between the markers), so the byte equality that C06 and the parity test check starts true instead of being hand-aligned"
  - "The :undo surface review records the real dump counts (undo=191 lines, UndoResult printed as 'abstract sealed exhaustive class' with exactly four nested members, UndoReason a JvmInline value class, no enum, no copy/componentN, no internal class)"
  - "Phase 17's :core additions are exactly ActionEvent.heldRunId (two added lines in the ActionEvent block, none removed) and the CompositeSinkKt block; comm -23 of the sorted committed core/api.txt against the dump is 0, so no gate-12 risk is carried to Phase 20"
  - ":undo carries two suppressions, not one (A6 said one per module): the file-level TooGenericExceptionCaught in internal/Guard.kt and a one-line MaxLineLength on UndoJournal.record. Recorded as OI-5 rather than changed, since this plan adds no main-source change"

requirements-completed: [UNDO-01, UNDO-02, UNDO-03, UNDO-04]

coverage:
  - id: D1
    description: "Tracer: the undo-bridge block in INTEGRATION.md equals the compiled DocSnippetsTest region (C06/C07), the region runs a two-mutation command through compositeSink(UndoCommitSink(journal), RecordingCommitSink()) and undoAll is Complete with 2 restored, and the region equals the sample main bridge"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "DocSnippetsTest.undoBridgeRegionJournalsAndUndoesACommand and UndoBridgeParityTest (14 + 1 tests, 0 failures) via :sample:testDebugUnitTest; scripts/verify-docs-coverage.sh DOC COVERAGE OK checks=25"
        status: pass
    human_judgment: false
  - id: D2
    description: "The undo docs cover the coordinate, one adapter per entity type, a ticket per mutation, capture inside apply, compensators after the restores, wiring order, group key, N, withheld, refusals and isolated-only single undo, limits and the store mirror, the four-member result set and the YAT status mapping"
    requirement: UNDO-02
    verification:
      - kind: other
        ref: "INTEGRATION.md section 11 and API.md undo table; scripts/verify-docs-coverage.sh (C03, C06, C19, C20, C21, C23, C25 green); acceptance greps (does not survive process death 1, four published modules 1, no Kotlin fence added to API.md)"
        status: pass
    human_judgment: true
  - id: D3
    description: "17-SURFACE-REVIEW.md matches the real isolated Metalava dumps and lists the frozen names, resolutions Q1-Q6, A18 and open items"
    requirement: UNDO-04
    verification:
      - kind: other
        ref: "scripts/api-dump-isolated.sh OK core=1956 providers=121 keystore=76 undo=191; sealed exhaustive UndoResult, no enum, no internal class, CompositeSinkKt present, ActionEvent block diff only adds heldRunId; 7 required headings"
        status: pass
    human_judgment: true
  - id: D4
    description: "Full phase gate: check is green, the four bash gates are green, baselines and untouched sources are byte-identical to 147a959, undo/api.txt is still the seed, coreAllowed is unchanged, the quiet-window request holds grant: pending"
    requirement: UNDO-01
    verification:
      - kind: integration
        ref: "./gradlew --offline --no-daemon -q check exit 0; MODULE MANIFEST OK; HYGIENE OK; DOC COVERAGE OK; RELEASE MANIFEST PROOF OK cases=8; git diff --quiet 147a959 on the api.txt baselines and providers/keystore main; seed test"
        status: pass
    human_judgment: false

duration: 45min
completed: 2026-10-06
---

# Phase 17 Plan 09: Undo docs, surface review and phase gate Summary

**The bridge an agent copies from INTEGRATION.md is the compiled, executed, parity-tested bridge the sample proves, the undo docs and API.md are written from the real isolated dumps, and the whole phase gate is green with a quiet-window request ready for plan 17-10.**

## Accomplishments
- Task 1 (tracer): `DocSnippetsTest` gained region `undo-bridge` (a script-made copy of the sample bridge, no import of the main class) and `undoBridgeRegionJournalsAndUndoesACommand`. `UndoBridgeParityTest` extracts, dedents and compares the region with the main file between `// undo-bridge:start` and `// undo-bridge:end`, naming the first differing line. INTEGRATION.md section 11 opens with the coordinate, the adapter and ticket protocol and the marked bridge block; section 6's sink paragraph points to it.
- Task 2: section 11 continues with grouping and "Undo all (N)" (including `pendingHeld`, `withheld`, and the note that the bridge's maps are never pruned and the journal sink goes first), undo and refusal behavior, the closed result table, the open `UndoReason` note, the YAT-style status mapping, and limits and the store mirror. API.md gets the `undo` packages line, the 12-type table and three Extension-points rows. `17-SURFACE-REVIEW.md` was written from the real dumps.
- Task 3: one `./gradlew check` plus the four bash gates and the byte-identity guards; results appended to the review. `17-QUIET-WINDOW.md` holds the relay text with `grant: pending`.

## Task Commits
1. **Task 1 (tracer): doc region, parity test, INTEGRATION undo block** - `6b8c67f` (feat)
2. **Task 2: rest of the undo docs, API.md rows, surface review** - `5dbc89a` (docs)
3. **Task 3: gate results and the quiet-window request** - `561e334` (docs)

## Deviations from Plan
None to scope or behavior. Notes:
- The coordinate line sits in the first block of section 11 only, so the acceptance count of `voice-action-engine-undo:<version>` is exactly 1; API.md refers to it in prose.
- The bridge KDoc comment says the three maps are small and run-id keyed; the docs add the "never pruned" statement 17-08 asked for.
- The Task 2 verify command's Gradle step was run as `api-dump-isolated.sh` alone (one job), then the other checks as plain shell, to keep to one Gradle job at a time.
- An idle Gradle daemon (about 25 minutes old, 0% CPU, left by an earlier session) was alive when Gradle was first started. It was not a build and was not stopped (`--stop` is forbidden); every job here used `--no-daemon` with 2 workers.

## Issues Encountered
None. No earlyoom kill, no test or gate failure.

## Notes for plan 17-10
- Do not rerun the plan 17-09 gates; run only the three heavy ones named in `17-QUIET-WINDOW.md` once its grant line is relayed. HEAD sha at request is `5dbc89a` (later commits are planning files only).
- This plan added the last tests of the phase (the region test and the parity test). The security audit sees no later test change from this plan.

## Verification
- `:sample:testDebugUnitTest --tests '*DocSnippets*' --tests '*UndoBridgeParity*'`: 14 and 1 tests, 0 failures. `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=107`.
- Full `./gradlew check`: exit 0. `verify-module-manifest` OK (core, providers, keystore, undo), `verify-repo-hygiene` OK, `verify-release-manifest` OK (8 cases).
- Acceptance greps for all three tasks passed (marker counts 1/1/1/1, README.md identical to 147a959, seed intact, baselines identical, 7 review headings, no Kotlin fence added to API.md, `coreAllowed` unchanged).
- No provider call, device, key or network publish was used. No `api.txt` was written into the repo.

## Known Stubs
None.

## Threat Flags
None. T-17-29 (doc drift) is covered by C06 plus the parity test, T-17-30 by the surface review, T-17-15 by the docs wording and the C21/C25 checks, T-17-31 by running one job at a time and deferring the heavy gates.

## Self-Check: PASSED
