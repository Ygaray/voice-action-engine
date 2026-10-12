---
phase: 17-run-level-undo
verified: 2026-10-07T01:10:00Z
status: passed
score: 8/8 must-haves verified
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/17-run-level-undo/17-01-PLAN.md
  - .planning/phases/17-run-level-undo/17-01-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-02-PLAN.md
  - .planning/phases/17-run-level-undo/17-02-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-03-PLAN.md
  - .planning/phases/17-run-level-undo/17-03-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-04-PLAN.md
  - .planning/phases/17-run-level-undo/17-04-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-05-PLAN.md
  - .planning/phases/17-run-level-undo/17-05-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-06-PLAN.md
  - .planning/phases/17-run-level-undo/17-06-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-07-PLAN.md
  - .planning/phases/17-run-level-undo/17-07-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-08-PLAN.md
  - .planning/phases/17-run-level-undo/17-08-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-09-PLAN.md
  - .planning/phases/17-run-level-undo/17-09-SUMMARY.md
  - .planning/phases/17-run-level-undo/17-10-PLAN.md
  - .planning/phases/17-run-level-undo/17-10-SUMMARY.md
  - API.md
  - ECOSYSTEM.md
  - INTEGRATION.md
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CompositeSink.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CompositeSinkTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldRunIdTest.kt
  - gradle/invariants.gradle.kts
  - jitpack.yml
  - sample/build.gradle.kts
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemAdapter.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemMutations.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemStore.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoCommitSink.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoBridgeParityTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoEndToEndPlanTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoEndToEndTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoTestRig.kt
  - scripts/agent-wiring-test.sh
  - scripts/api-dump-isolated.sh
  - scripts/jitpack-consumer-probe.sh
  - scripts/jitpack-dry-run.sh
  - scripts/jitpack-live-probe.sh
  - scripts/lib/modules.sh
  - scripts/lib/published_versions.py
  - scripts/modules.list
  - scripts/release-cut.sh
  - scripts/review-api-surface.sh
  - scripts/verify-api-dump.sh
  - scripts/verify-api-seed.sh
  - scripts/verify-docs-coverage.sh
  - scripts/verify-ml-denial-controls.sh
  - scripts/verify-module-manifest.sh
  - scripts/verify-negative-controls.sh
  - scripts/verify-release-manifest.sh
  - scripts/verify-repo-hygiene.sh
  - settings.gradle.kts
  - undo/api.txt
  - undo/build.gradle.kts
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/EntityAdapter.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/EntryRef.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStore.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoGroup.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoReason.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoResult.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoTicket.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Compensating.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Footprints.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Guard.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Restorer.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Retention.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/StoreMirror.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/TicketState.kt
  - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Verifier.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/AdapterRoundTripTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/CancelMidUndoTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/ChainVerifyTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/CompensatorTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/IdempotentUndoTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/IsolationTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStoreTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/LimitsTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/PartialRestoreTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/RefuseLoudlyTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/ResultShapeTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoApiShapeTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoRedactionTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoStandaloneTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoTestSupport.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/WithheldGroupTest.kt
covered_digest: "v1:sha256:c99703878130bb22ff2e4ebef3f8ecb85ddcc4c811b3dc3b3952ffbc2a85d8e3"
behavior_unverified: 0
overrides_applied: 0
re_verification: false
human_verification: []
deferred_obligations:
  - item: "IN-04: settle the ActionEvent.toString() redaction policy (parentRunId vs heldRunId) with the why in the KDoc"
    owner: "Phase 20 (frozen-surface reconciliation, before the v1.1.0 tag)"
    why_not_blocking: "Policy call over a shipped v1.0 class; no phase-17 truth depends on it; HeldRunIdTest pins today's behavior"
  - item: "Re-run scripts/jitpack-dry-run.sh and scripts/jitpack-live-probe.sh on the final tree (IN-05/IN-06/WR-08 script edits landed after the 17-10 quiet window; checked by bash -n, verify-module-manifest.sh and verify-release-manifest.sh only)"
    owner: "Phase 19 gate run (before the v1.1.0 cut), alongside the deferred heavy-gate obligation pattern of 17-QUIET-WINDOW.md"
    why_not_blocking: "Heavy gates are host-memory gated; the same scripts passed in the 17-10 quiet window and `./gradlew check` is green after the edits"
gaps: []
deferred: []
---

# Phase 17: Run-Level Undo Verification Report

**Phase Goal:** Any app, voice or not, can undo everything one command did. A standalone journal restores each touched entity safely and refuses loudly rather than clobber a later change, and the engine pipeline feeds it every command's commits so an app can offer "Undo all (N)".
**Verified:** 2026-10-07T01:10:00Z
**Status:** passed (two non-blocking items recorded as deferred obligations; orchestrator reclassified from the verifier's human_needed, see deferred_obligations)
**Re-verification:** No, initial verification

Gradle and the heavy scripts were not run, by instruction. Test evidence is the on-disk JUnit XML from the post-fix full `./gradlew check` (undo 113 tests, core 1110, sample 160; 0 failures, 0 errors, 0 skipped everywhere), the recorded 17-QUIET-WINDOW.md results, and direct reading of the code. This verifier did run `scripts/verify-module-manifest.sh` (bash only): `MODULE MANIFEST OK modules=core,providers,keystore,undo`.

## Goal Achievement

### Observable Truths (ROADMAP success criteria first)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `:undo` publishes as `voice-action-engine-undo` with no dependency at all, not even `:core`; a non-voice JVM test app journals and undoes using `:undo` alone | VERIFIED | `undo/build.gradle.kts` has only `testImplementation(libs.junit)`, explicit artifactId `voice-action-engine-undo`, JVM 11, `explicitApi()`. `gradle/invariants.gradle.kts:375-409` defines `verifyUndoZeroDeps` (fails on any project edge or non-stdlib/annotations artifact, and on a vacuous empty classpath) wired to `check`; `allowedEdges[":undo"]` is empty. `scripts/modules.list` row `undo jar voice-action-engine-undo undo no`; `jitpack.yml` install line names `:undo`. Quiet-window dry run: four artifacts published, `:undoalone` consumer runtimeClasspath names only `voice-action-engine-undo` (no core, no coroutines), `DRY RUN OK` / `PROBE OK`. `UndoStandaloneTest` (4 tests, green) asserts `:core` is not on the classpath and undoes a plain-store edit via a ticket. |
| 2 | Before-state captured through one app adapter per entity type (read, write back, re-insert if deleted); undo restores in reverse order; out-of-DB side effects reversed by registered compensators | VERIFIED | `EntityAdapter` (read / fingerprint / `restoreIf`, null snapshot means delete, absent entity re-inserted with id and children), `Compensator`, `UndoTicket.capture/settle/created/touches/compensate/nothingWritten` are real implementations, not stubs. `Verifier.kt` builds steps ordered newest action first and, inside one action, last-captured first. `Compensating.kt` runs compensators only after restores, newest first, reverse declared order, marks done only after success so a retry runs only the remainder. Tests green: `AdapterRoundTripTest`, `CompensatorTest`, `PartialRestoreTest`, `IdempotentUndoTest`; sample `s8CompensatorsRunAfterTheRestoresInReverseOnce` and `s8AFailedCompensatorIsRetriedAlone`. |
| 3 | Unchanged-since-commit check before every restore; a changed entity makes undo refuse loudly and never overwrite; result is complete or lists exactly what could not be restored, never a silent partial | VERIFIED (behavioral test evidence) | `Verifier.verify` reads live fingerprints for the whole scope and writes nothing; any blocker (`CHANGED_SINCE`, `CHAIN_BROKEN`, `UNVERIFIABLE`, `NO_ADAPTER`) makes the whole undo `Refused` with a non-empty blocker list (init `require`). `Restorer` uses atomic `restoreIf(expectedFingerprint)`; a lost race becomes a `NotRestored(CHANGED_SINCE)` and the rest of that footprint component is `SKIPPED_AFTER_FAILURE`. `UndoResult` is a closed set of four (`Complete`, `Refused`, `Partial`, `AlreadyUndone`); `Partial` requires at least one `NotRestored`. Behavior-dependent, so single-test evidence: `RefuseLoudlyTest` (13), `ChainVerifyTest`, `PartialRestoreTest` (6), `CancelMidUndoTest`, `ResultShapeTest`, sample `s2AnUnrelatedEditRefusesTheWholeUndoAndWritesNothing` (asserts store unchanged, no restore or disarm calls logged, group count intact). All green in the post-fix XML. |
| 4 | Wired into the pipeline, every committed action is journaled under its `runId` so an app can offer "Undo all (N)", entangled actions included; grouping follows A18 (entity footprints decide what is isolated) | VERIFIED | `:core` additive seams: `ActionEvent.heldRunId` (internal ctor) threaded `HeldCommit.kt` -> `CommitCoordinator` -> `ActionDelivery`/`ApplyStep` -> `ActionEvent`; `compositeSink(vararg)` isolates a throwing child from siblings and from the pipeline, throws a count-only fault, still propagates cancellation. Reference bridge `sample/.../undo/UndoCommitSink.kt` journals each applied action by group (`heldRunId ?: runId`), keeps `parentRunId` for clarification replies, withholds the group on any journaling anomaly or any applied position missed at `onRunClosed`. `UndoGroup.count` is the N; `isolated` computed from footprints; `undoEntry` refuses an entangled action with `ENTANGLED`. Run through the real `commandPipeline`: sample `UndoEndToEndTest` (9) S1 three writes undone together, S3 held rename confirmed later restores the moved state, S4 clarification reply own group keeping parent key, S5 throwing first sink does not stop bridge, S6a/S6b withheld on missing ticket or missed action; `UndoEndToEndPlanTest` (2) PlanThenExecute partial undoable within the run and a confirmed held step joins the group. Core: `CompositeSinkTest` (10), `HeldRunIdTest` (8). `UndoBridgeParityTest` plus `DocSnippetsTest` prove the INTEGRATION.md snippet is the compiled bridge. |
| 5 | (Plan truth, D-09/D-10) Manifest is the single module list; `undo/api.txt` is the header-only seed and Metalava still accepts additions and rejects removals | VERIFIED | `scripts/modules.list` read by `scripts/lib/modules.sh`; `verify-module-manifest.sh` re-run now: OK. `undo/api.txt` is exactly `// Signature format: 4.0`. Quiet window: `API DUMP PROOF OK` (undo dump 191 lines in an isolated copy, planted class, additive green, removal red; real tree untouched). |
| 6 | (Plan truth) Gates catch regressions on `:undo`: negative controls for banned constructs, forbidden `undo -> core` edge, library or ML dependency, JDK 12+ API in a JVM-11 module | VERIFIED | Quiet window `verify-negative-controls.sh`: exit 0, 121 ok lines, `negative-control failures: 0`, `ML DENIAL CONTROLS OK plants=9`; all `:undo` plants went red and the clean `:undo` tree stayed green. |
| 7 | (Plan truth, D-05/D-06) Journal failure withholds "Undo all" loudly (never a silent N-1); in-memory with optional store mirror whose faults never change a result; bounds and eviction | VERIFIED | `UndoJournal.record/runClosed/withhold` and `JournalState` withhold on null ticket, foreign or reused ticket, duplicate position, unsealed non-failed ticket, record into a dropped group (tombstones bounded at max(1000, 20 x maxGroups)). `StoreMirror` counts faults in `storeFaults`. Tests green: `WithheldGroupTest` (19), `JournalStoreTest`, `LimitsTest`, `IsolationTest`. |
| 8 | (Plan truth) Secrets and user data never reach `toString()`, exceptions or logs | VERIFIED | `EntityKey` prints type and id length only; `Blocker`/`NotRestored`/`UndoGroup`/`UndoTicket` print counts and flags; `NotRestored.errorClass` carries a class name, never a message; `ActionEvent` prints only whether `heldRunId` is set. `UndoRedactionTest` (sweep) green; CLN scan and negative-control plants for `println`/`printStackTrace`/`android.util.Log` red-then-green. Review item IN-07 (sample `Item` printed its title) fixed in 9786efb. |

**Score:** 8/8 truths verified (0 present, behavior-unverified).

Behavior-dependent truths (refuse-loudly, cancellation mid-undo, compensator ordering, exactly-once retry) were each upgraded from presence to VERIFIED by named tests that exist and passed in the post-fix XML, not by symbol presence.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `undo/build.gradle.kts`, `settings.gradle.kts` (`include(":undo")`) | stdlib-only published module | VERIFIED | Substantive, wired into settings, jitpack, manifest, invariants. |
| `undo/src/main/.../UndoJournal.kt`, `UndoTicket.kt`, `UndoResult.kt`, `UndoReason.kt`, `UndoGroup.kt`, `EntryRef.kt`, `EntityAdapter.kt`, `JournalStore.kt` | public journal API | VERIFIED | 250/89/112/53/43/53/54/20 lines of real logic and KDoc. |
| `undo/.../internal/Verifier.kt`, `Restorer.kt`, `Compensating.kt`, `Footprints.kt`, `JournalState.kt`, `TicketState.kt`, `Retention.kt`, `StoreMirror.kt`, `Guard.kt` | verify-then-restore engine | VERIFIED | Substantive (e.g. `Verifier` 147, `JournalState` 344 lines); every internal class referenced from `UndoPass`/`UndoJournal`. |
| `core/.../commit/CompositeSink.kt`, `ActionEvent.heldRunId` (`CommitSink.kt`, `ApplyStep.kt`, `CommitCoordinator.kt`, `HeldCommit.kt`, `CommandPipeline.kt`) | additive `:core` seams | VERIFIED | Wired end to end; `:core` gained no dependency. |
| `sample/.../undo/UndoCommitSink.kt`, `ItemAdapter.kt`, `ItemStore.kt`, `ItemMutations.kt` | reference bridge and adapters | VERIFIED | Exercised by 11 end-to-end tests through the real pipeline. |
| `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh` | one module list and consistency gate | VERIFIED | Gate re-run OK. |
| `gradle/invariants.gradle.kts` (`verifyUndoZeroDeps`, `allowedEdges`) | classpath-enforced zero deps | VERIFIED | Wired to `check`; vacuity guarded. |
| `API.md`, `INTEGRATION.md` section 11, `ECOSYSTEM.md` | docs an agent can wire from | VERIFIED | Undo rows, coordinate and compiled bridge snippet present; `verify-docs-coverage.sh` recorded `DOC COVERAGE OK`. |
| `undo/api.txt` | header-only seed (D-09) | VERIFIED | One line, by design; real dump generated at the v1.1.0 cut. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `commitHeld` (`HeldCommit.kt:61`) | `ActionEvent.heldRunId` | `CommitCoordinator(runId, held.runId, held.runId, ...)` -> `ActionDelivery` -> `ApplyStep` | WIRED | Fresh runs pass `null` (`CommandPipeline.kt:88`). `HeldRunIdTest` green. |
| pipeline `commitSink` slot | `UndoCommitSink` and app sink | `compositeSink(bridge, recording)` | WIRED | `UndoEndToEndTest` S1-S8 use it. |
| `UndoCommitSink.onAction` | `UndoJournal.record` | `action.context as? UndoTicket` (the `PendingMutation.context`) | WIRED | Missing ticket withholds the group (S6a). |
| `UndoCommitSink.onRunClosed` | `UndoJournal.runClosed` | applied positions | WIRED | Missed action withholds (S6b). |
| `UndoPass` | `Verifier` -> `Restorer` -> `Compensating` | whole-scope verify before any write | WIRED | Order enforced in `JournalState.kt`; refusal tests assert nothing written. |
| `scripts/modules.list` | settings, jitpack, allowedEdges, api.txt, every release/hygiene script | `scripts/lib/modules.sh` | WIRED | Consistency gate OK; planted-module selftest recorded in 17-01. |

### Data-Flow Trace (Level 4)

Not applicable to UI rendering. The one dynamic value, "Undo all (N)" (`UndoGroup.count`), derives from real recorded journal entries (`pending.size`), asserted against real pipeline commits in S1/S3 (count 3, 1, 4, and 0 for an unconfirmed held proposal). FLOWING.

### Behavioral Spot-Checks

| Behavior | Evidence | Status |
|----------|----------|--------|
| `:undo` unit suite | `undo/build/test-results`: 113 tests, 0 failures, 0 errors, 0 skipped (timestamps after the last fix commit) | PASS |
| `:core` suite incl. `CompositeSinkTest` 10, `HeldRunIdTest` 8 | 1110 tests, 0 failures | PASS |
| `:sample` suite incl. undo end-to-end 12 | 160 tests, 0 failures | PASS |
| Module manifest consistency (run by verifier, bash only) | `MODULE MANIFEST OK modules=core,providers,keystore,undo` | PASS |
| `bash -n` over every script in `scripts/` and `scripts/lib/` | no syntax errors | PASS |
| Heavy gates (recorded in 17-QUIET-WINDOW.md, not re-run) | negative controls 0 failures; `API DUMP PROOF OK`; `DRY RUN OK` / `PROBE OK` | PASS (recorded) |

### Probe Execution

The phase declares probe-based checks only through the heavy gate scripts (`scripts/jitpack-live-probe.sh`, `jitpack-consumer-probe.sh`), which need a quiet window or a published ref. Their last green run is the recorded 17-10 dry run. SKIPPED here by instruction; see human verification item 2.

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| UNDO-01 | 17-01, 17-03, 17-04, 17-05, 17-09, 17-10 | `:undo` module depends on nothing, usable by a non-voice app alone | SATISFIED | Truth 1, 5, 6 |
| UNDO-02 | 17-05, 17-06, 17-08, 17-09 | Journal/memento design, per-entity adapters, compensators | SATISFIED | Truth 2 |
| UNDO-03 | 17-05, 17-06, 17-07, 17-08, 17-09 | Unchanged-since-commit check, refuse loudly, complete or exact report | SATISFIED | Truth 3 |
| UNDO-04 | 17-02, 17-07, 17-08, 17-09 | Pipeline journals by `runId`, "Undo all (N)", entangled included | SATISFIED | Truth 4 and 7 |

All four IDs in REQUIREMENTS.md mapped to Phase 17 (UNDO-01..04) appear in at least one PLAN `requirements:` field. No orphaned requirements. REQUIREMENTS.md already marks all four Complete.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (phase files) | n/a | `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` grep over `undo/src`, `core/.../commit`, `sample/.../undo`, `scripts`, `gradle` | none | No debt markers. |
| (phase files) | n/a | Empty implementations, hardcoded empty data, console-only handlers | none | The grep hits for `emptyList`/`null` are initial state overwritten by recording, or typed empty results, not stubs. |

Code review: 17-REVIEW.md status resolved; 14 of 15 findings fixed, IN-04 skipped by design (carried to human verification item 1). Fix commits left `undo/api.txt` untouched and no public signature changed.

### Human Verification Required

1. **Settle the `ActionEvent.toString()` redaction policy (IN-04).** Test: decide whether `parentRunId` is redacted like `heldRunId` or `heldRunId` is printed like `parentRunId`; add the why to the KDoc. Expected: one recorded decision before the v1.1.0 tag, consistent with `HeldRunIdTest`. Why human: it is a policy choice over a v1.0 class and the frozen surface.
2. **Re-run the JitPack dry run and live probe on the final tree at the Phase 19/20 gates.** Test: `scripts/jitpack-dry-run.sh` and `scripts/jitpack-live-probe.sh` in a quiet window. Expected: `DRY RUN OK`, `PROBE OK`. Why human: small script edits (IN-05, IN-06, WR-08) landed after the 17-10 window and were checked by syntax and the manifest gate only; heavy gates are host-memory gated.

Device-verifiable behavior (Gate-1 on the TESTER) is Phase 19's scope and is not listed here. Carry-forward for Phase 20 (from CONTEXT RT-01): reconcile the keystore `api.txt` re-dump (+9 `KeyAccess` lines) and the two `:undo` suppressions.

### Gaps Summary

No gaps. Every ROADMAP success criterion and plan-level truth is backed by existing, substantive, wired code and by named tests that passed in the post-fix full check, with the heavy gates green in the recorded quiet window. The status is `human_needed` only because of one pre-tag policy decision (IN-04) and one deferred re-run of heavy scripts edited after the window; neither blocks the phase goal.

---

_Verified: 2026-10-07T01:10:00Z_
_Verifier: Claude (gsd-verifier)_
