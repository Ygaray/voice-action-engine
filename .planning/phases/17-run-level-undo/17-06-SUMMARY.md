---
phase: 17-run-level-undo
plan: 06
subsystem: undo-journal
tags: [kotlin-jvm, undo, partial-restore, compensators, cancellation, in-progress-claim]

requires:
  - phase: 17-05
    provides: "UndoJournal facade, Verifier, closed UndoResult set, Guard.kt, per-entry restoredKeys"
provides:
  - "internal/Footprints.kt: union-find over captured, created and touched entities (clusters, componentOf, isolated)"
  - "internal/Restorer.kt: per-component restore with exact Partial bookkeeping and per-key marks"
  - "internal/Compensating.kt: ordered, once-only compensators with per-effect done marks"
  - "Per-group IN_PROGRESS claim, released in finally"
  - "Test fault injection: throw, race, cancel, cancel-after-write and suspend per id; RecordingCompensator; launch helper"
affects: [17-07, 17-08, 17-09, 17-10]

actuals:
  tokens: 12500
  tasks: 3
  commits: 3
plan_head_before: 9d65251d61a474301f4b47aa7263859b724318d9
commits: 3

tech-stack:
  added: []
  patterns:
    - "A mark is written only after the call that justified it returned, so a cancelled call leaves its key or effect unmarked and the verifier's live == before rule settles it on retry"
    - "Claim and release: the in-progress flag is taken in the same locked step that reads the scope, and given back in finally"
    - "Independent components: a failure stops its own footprint component and the pass carries on with the others"

key-files:
  created:
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Footprints.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Restorer.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Compensating.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/PartialRestoreTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/CompensatorTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/CancelMidUndoTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/IdempotentUndoTest.kt
  modified:
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Verifier.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoTestSupport.kt

key-decisions:
  - "Compensators of different actions, and of one action, are independent: one failing never stops the rest. Within an action they run in reverse declaration order"
  - "An action's compensators run when all its own entities are restored, even if another component of the pass failed; an action with an unrestored entity gets SKIPPED_AFTER_FAILURE for every pending effect"
  - "An unregistered compensation kind is a Blocker(entry, null, NO_ADAPTER) at verification, for any effect still pending"
  - "Inside one action the entity captured last is restored first (reverse of the write order), so 'a failure skips the keys after it' means the keys captured before it"
  - "The IN_PROGRESS check comes before the withheld check; a group with nothing left to undo is never claimed, so it stays AlreadyUndone"

patterns-established:
  - "Test adapters restore the snapshot's own version, so a restored entity has the fingerprint it had before (the live == before retry rule is testable)"

requirements-completed: [UNDO-02, UNDO-03]

coverage:
  - id: D1
    description: "A restore that fails after verification gives an exact Partial (RESTORE_FAILED with the class name, CHANGED_SINCE for a race, SKIPPED_AFTER_FAILURE in the same component); independent components still restore; a retry restores only the remainder and treats live == before as done"
    requirement: UNDO-03
    verification:
      - kind: unit
        ref: "PartialRestoreTest (6 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D2
    description: "Compensators run after every restore, newest first, once; a failing one gives COMPENSATOR_FAILED and a retry re-runs only it; none for a refusal or after a failed restore; an unregistered kind refuses before any write; an action with an effect and no entity still counts as restored"
    requirement: UNDO-02
    verification:
      - kind: unit
        ref: "CompensatorTest (8 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D3
    description: "A second undoAll while the first is suspended in an adapter is Refused(IN_PROGRESS) and writes nothing; another group runs meanwhile; afterwards AlreadyUndone"
    requirement: UNDO-03
    verification:
      - kind: unit
        ref: "IdempotentUndoTest (3 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D4
    description: "CancellationException from an adapter or compensator reaches the caller, nothing is left in progress, finished restores stay marked, and a retry completes the rest without a second write"
    requirement: UNDO-03
    verification:
      - kind: unit
        ref: "CancelMidUndoTest (3 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D5
    description: "Plan-end gate: tests, detekt, scanBannedConstructs, explicit API, module graph, zero deps and Metalava compat for :undo"
    requirement: UNDO-03
    verification:
      - kind: integration
        ref: "./gradlew :undo:check (exit 0, 59 tests)"
        status: pass
    human_judgment: false
---

# Phase 17 Plan 06: Partial restores, compensators, cancellation and concurrent calls Summary

Every failure after verification in `:undo` is now exact and retryable: a failed or raced restore gives a `Partial` with exact lists per footprint component, compensators run after the data in reverse order exactly once, a second concurrent `undoAll` is `Refused(IN_PROGRESS)`, and a cancelled undo propagates without stranding the group.

## What was done

1. **Tracer: exact Partial per component (commit 0da770e).**
   - `Footprints` is a union-find over each action's captured, created and touched entities. It gives clusters of actions and `isolated(entry)` (used by 17-07).
   - `Restorer` walks the verified steps newest action first. A fault becomes `RESTORE_FAILED` with the class name, a `false` from `restoreIf` becomes `CHANGED_SINCE` (the raced entity keeps its live value), and the rest of that component is `SKIPPED_AFTER_FAILURE`. Other components carry on. A key is marked restored the moment `restoreIf` returned true.
   - `JournalState` marks per key, so an action is undone when all its entities are restored. A retry sees only the remainder; a key already equal to its before state is counted restored with no call.
2. **Compensators (commit 59499c1).**
   - `Compensating` runs after the entity restores, actions newest first, effects of one action in reverse declaration order, each through `guardedCall`. A compensator is marked done only after it returned.
   - A fault is `NotRestored(entry, null, kind, COMPENSATOR_FAILED, errorClass)`. Effects of an action whose entities were not all restored are `SKIPPED_AFTER_FAILURE` and never run.
   - `Verifier` refuses an unregistered kind (`NO_ADAPTER`, entity null) before any write. An action is undone only when entities are restored and effects are reversed.
3. **In-progress claim and cancellation (commit bb3d529).**
   - `JournalState.claim` reads the scope and takes the group's `undoing` flag in one locked step; `UndoPass.run` releases it in `finally`. A second call gets `Refused([Blocker(null, null, IN_PROGRESS)])` with no adapter call, and another group is unaffected.
   - Cancellation from an adapter or a compensator propagates, the group is released, finished restores stay marked, and the retry completes the remainder.

## Verification

- `./gradlew :undo:check` exits 0 with 59 tests (earlier plan classes plus PartialRestore 6, Compensator 8, Idempotent 3, CancelMidUndo 3). It includes detekt on main and test with zero baseline, the banned-constructs scan, explicit API, module graph, zero deps and the Metalava compat check. The public API is unchanged (no `api.txt` diff).
- RED evidence: before the Restorer, 5 of 6 PartialRestoreTest tests failed. Before Compensating, 6 of 8 CompensatorTest tests failed. Before the claim, the concurrent-call test failed (1 of 3 IdempotentUndoTest).
- Plan acceptance greps pass: SKIPPED_AFTER_FAILURE 1, RESTORE_FAILED 2 (PartialRestoreTest); COMPENSATOR_FAILED 1, `compensate:p2` 2 (CompensatorTest); IN_PROGRESS 1 (IdempotentUndoTest); CancellationException 2 (CancelMidUndoTest); `finally` 1 (JournalState.kt).
- No device, API key, network publish or heavy gate was run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `UndoJournal.kt` changed (not in files_modified)**
- **Issue:** the pass needs the registered compensators, and `UndoPass` was built from the adapters only.
- **Fix:** `UndoPass(state, adapters, compensators)`; `compensators` became `private` (it was `internal` and unused elsewhere). No public API change.

**2. [Rule 3 - Blocking] Component class named `Cluster`, field named `entryKeys`**
- **Issue:** detekt MemberNameEqualsClassName flagged a `footprints` member in `Footprints` and a `components` member next to a `Component` class.
- **Fix:** `Cluster` for the group of actions, `entryKeys` for the map. `Footprints.components` keeps the plan's name.

**3. [Rule 1 - Bug in test support] `TestEntityStore.restoreIf` restores the snapshot's own version**
- **Found during:** Task 1 retry test ("live == before counts as restored").
- **Issue:** the store gave every write a fresh stamp, including a restore, so a restored entity never had its original fingerprint and the retry refused `CHANGED_SINCE`. That is a test-store artifact: an adapter whose fingerprint covers content restores to the same fingerprint.
- **Fix:** restore puts back the snapshot record with its stamp. All earlier tests still pass.

**4. [Ordering note] Test support for Tasks 2 and 3 landed in the Task 1 commit**
- `UndoTestSupport.kt` is shared, so `RecordingCompensator`, the suspend gate and `launch` were added once, in commit 0da770e.

**5. [Observation] CancelMidUndoTest passed before the Task 3 code**
- Per-key marks written after the call (Task 1) and the done marks (Task 2) already made cancellation safe. Task 3 added the in-progress claim and its release in `finally`, which the concurrent-call test drives. The cancel tests stay as regression pins and also assert the group is not stuck.

**Total deviations:** 5 (3 Rule 3/1 adjustments, 2 notes). **Impact:** none on the frozen public surface.

## Notes for the next plans

- `Footprints.isolated(entry)` is ready: an entry is isolated when its cluster has one entry. 17-07 builds `UndoGroup.isolated` and `undoEntry` on it, and should build `Footprints` from pending entries only.
- `JournalState.claim` is the single place that decides refusal reasons for a group (unknown, in progress, withheld). 17-07's eviction must not drop a group whose `undoing` is set.
- A late `record` into a group that is being undone is not blocked by the claim. 17-07 decides whether that withholds the group.

## Self-Check: PASSED

- Created files exist: Footprints.kt, Restorer.kt, Compensating.kt, PartialRestoreTest.kt, CompensatorTest.kt, CancelMidUndoTest.kt, IdempotentUndoTest.kt.
- Commits 0da770e, 59499c1, bb3d529: FOUND.
- No external API integration: this plan changes a dependency-free in-memory journal and calls no external service.
