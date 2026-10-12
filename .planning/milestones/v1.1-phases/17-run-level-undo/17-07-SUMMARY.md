---
phase: 17-run-level-undo
plan: 07
subsystem: undo-journal
status: complete
tags: [kotlin-jvm, undo, group-view, withheld, isolation, retention, journal-store, redaction]

requires:
  - phase: 17-06
    provides: "Footprints union-find, per-group IN_PROGRESS claim, partial restores, compensators, test fault injection"
provides:
  - "UndoGroup view (count N, withheld, entries, pending, isolated, parentGroupKey, revision), counts-only toString"
  - "UndoJournal.runClosed, withhold, group, undoEntry, storeFaults; Builder maxGroups, maxAgeMillis, clock, store"
  - "JournalStore write-through mirror (save/delete), no loadAll, no snapshots or fingerprints"
  - "internal Retention (count and age eviction, bounded tombstones), StoreMirror, Marks, Recorded"
  - "Canary redaction sweep over every public :undo surface"
affects: [17-08, 17-09, 17-10]

actuals:
  tokens: 19000
  tasks: 3
  commits: 3
plan_head_before: a481a3a74d94987f93bf8ac5c5ebf5301d674710
commits: 3

tech-stack:
  added: []
  patterns:
    - "Clock and store are app code: the clock is read before the lock and passed in as now, the store is called after the lock is given back, both through guardedCall"
    - "A journal gap never shrinks a command silently: unusable recordings are still listed, and the group is withheld (sticky)"
    - "Eviction runs on every journal operation, skips groups being undone, and leaves a bounded tombstone so a late record into a dropped key makes a withheld group"

key-files:
  created:
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoGroup.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStore.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Retention.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/StoreMirror.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/WithheldGroupTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/IsolationTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/LimitsTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStoreTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoRedactionTest.kt
  modified:
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Footprints.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Restorer.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Compensating.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/TicketState.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoTestSupport.kt

key-decisions:
  - "Q2, single-entry undo: undoEntry works for isolated entries only. An entangled entry gets Refused(Blocker(entry, null, ENTANGLED)) with nothing written and no expansion to its component; undoAll covers it. Isolation is computed over the pending entries, so an already undone action never entangles another"
  - "Q4, JournalStore: a write-through mirror with save(UndoGroup) and delete(groupKey) only. No loadAll, no snapshots, no fingerprints. The journal therefore still does not survive process death (as D-06 documents). Restore-from-store can be added later as a defaulted interface method"
  - "Q6, N and no-op entries: an applied entry that called nothingWritten(), or that declared nothing, counts in N and appears in Complete.restored; a failed-but-applied entry counts too"
  - "Late record into a group whose undo is running (17-06 handoff): it is appended and stays pending, it is not withheld. The running pass keeps the scope it claimed; the new action shows in count afterwards and a later undo covers it"
  - "Eviction versus undoing (17-06 handoff): both bounds skip a group whose undo is in progress, so the count can exceed maxGroups transiently"
  - "An unusable recording (null, foreign or reused ticket, unsealed commit) is still listed in entries with nothing to restore, so a withheld group's view shows every action that was reported; a withheld group has an empty isolated list"
  - "Activity for ordering and age is a change of the group (record, runClosed, withhold, a non-refused undo); reads do not count. A refused undo is not activity and not published to the store"
  - "runClosed with an empty set and no group is a complete no-op, also for a dropped key (nothing is missing); on an existing group it counts as a change (revision rises, the store is told)"
  - "A throwing clock never reaches the caller: the last good reading stands in (the clock is app code)"

patterns-established:
  - "Internal helper classes take no default parameters: the shape sweep in UndoApiShapeTest flags default-argument constructor stubs on every main class, internal ones included"
  - "Top-level const vals that are not public API are private: an internal const compiles to a public static field and fails the shape sweep"

requirements-completed: [UNDO-03, UNDO-04]

coverage:
  - id: D1
    description: "Group view: count is the applied actions not yet undone (failed-but-applied and nothing-written included); every gap (null, foreign or reused ticket, duplicate position, unsealed commit, missing position at runClosed, explicit withhold, record into a dropped key) withholds the group, sticky, and undoAll refuses JOURNAL_WITHHELD; appends after a run closed and the first parentGroupKey are kept"
    requirement: UNDO-04
    verification:
      - kind: unit
        ref: "WithheldGroupTest (16 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D2
    description: "Isolation and single-entry undo: footprint-disjoint pending actions are isolated, undoEntry checks only its own footprint, entangled gives ENTANGLED with nothing written, unknown group/entry, already undone, withheld and in-progress are answered"
    requirement: UNDO-03
    verification:
      - kind: unit
        ref: "IsolationTest (8 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D3
    description: "Bounds: defaults 50 groups and one hour, Builder rejects limits below 1, least recently active group evicted past the count, idle past the age evicted by the next operation, never while undoing, late record into a dropped key withheld, tombstones bounded"
    requirement: UNDO-04
    verification:
      - kind: unit
        ref: "LimitsTest (9 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D4
    description: "JournalStore mirror: save after every change with rising revision, delete on count and age eviction, a throwing store changes nothing and is counted, store cancellation propagates, no store means no calls, the seam has save and delete only and the view holds no user data"
    requirement: UNDO-04
    verification:
      - kind: unit
        ref: "JournalStoreTest (9 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D5
    description: "Redaction sweep: no CANARY in any toString of EntityKey, EntryRef (non-canary run id), Blocker, NotRestored, each UndoResult, UndoTicket, UndoGroup, UndoJournal, in errorClass, or in any exception message (over-long keys are not echoed)"
    requirement: UNDO-04
    verification:
      - kind: unit
        ref: "UndoRedactionTest (3 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D6
    description: "Plan-end gate: tests, detekt (zero baseline), banned-constructs scan, explicit API, module graph, zero deps and Metalava compat for :undo"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "./gradlew :undo:check (exit 0, 104 tests)"
        status: pass
    human_judgment: false
---

# Phase 17 Plan 07: Group view, withholding, isolation, bounds, store mirror and redaction Summary

`:undo` now gives an app "Undo all (N)" from one journal read and says "unavailable" (withheld) instead of N-1 whenever it could have missed an action; isolated actions get their own undo, the journal is bounded by count and age, an optional store mirrors it without ever affecting it, and no public surface prints user data.

## What was done

1. **Tracer: group view and withholding (commit 0ecd184).**
   - `UndoGroup` is a snapshot view with `count`, `withheld`, `entries`, `pending`, `isolated`, `parentGroupKey` and `revision`; it prints counts only.
   - `record` withholds the group on a null, foreign or reused ticket, a duplicate (run, position) and an unsealed ticket on a committed action. The unusable action is still listed so the view shows what was reported.
   - `runClosed(group, run, positions)` is an integrity check per run (never a seal) and `withhold(group)` is an explicit, sticky withhold. A group accepts appends after a run closed; the first non-null `parentGroupKey` is kept.
2. **Isolation, single-entry undo and bounds (commit 5627289).**
   - `Footprints.isolatedEntries()` builds `UndoGroup.isolated`; `undoEntry` restores an isolated action checking only its own footprint and refuses an entangled one with `ENTANGLED`. It shares the group's in-progress claim.
   - `Retention` evicts on every operation: first by age (idle for more than `maxAgeMillis`), then the least recently active beyond `maxGroups`, never a group being undone. Evicted keys leave a bounded tombstone, so a late record, `runClosed` or `withhold` into a dropped key creates a withheld group.
   - Snapshots are released when an action becomes undone and when a group leaves the journal.
3. **Store mirror and redaction sweep (commit 0676f9f).**
   - `JournalStore.save/delete` is called outside the lock, through `guardedCall`: a fault increments `storeFaults`, a cancellation propagates, and no journal state or result changes.
   - `UndoRedactionTest` plants a canary in entity ids, snapshots, fingerprints, payloads, group keys and adapter, compensator and store fault messages, and checks every public string, result field and exception message.

## Verification

- `./gradlew :undo:check` exits 0 with 104 tests (WithheldGroup 16, Isolation 8, Limits 9, JournalStore 9, Redaction 3, plus the earlier plans' classes). It includes detekt on main and test with zero baseline, the banned-constructs scan, explicit API, module graph, zero deps and the Metalava compat check against the seed (`undo/api.txt` is unchanged).
- Plan acceptance greps pass: UndoGroup constructor 1, runClosed signature 1, JOURNAL_WITHHELD 6 and @Test 16 (WithheldGroupTest), undoEntry signature 1, `GROUP_LIMIT: Int = 50` and `AGE_LIMIT_MILLIS: Long = 3_600_000` one line each, ENTANGLED 2 (IsolationTest), UNKNOWN_GROUP 2 (LimitsTest), save 1, delete 1, "does not survive process death" 1, `storeFaults` 1, CANARY 30 (UndoRedactionTest).
- RED evidence: for Tasks 1 and 2 the new tests were written first and failed to compile against the missing API (unresolved `group`, `runClosed`, `withhold`, `UndoGroup`, `maxGroups`, `clock`, `undoEntry`). For Task 3 the implementation was written before the tests, so there is no RED run for it.
- No device, API key, network publish or heavy gate was run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Files outside `files_modified`**
- `Restorer.kt`, `Compensating.kt` and `TicketState.kt` changed. detekt's TooManyFunctions (12) forced the three mark functions out of `JournalState` into a `Marks` class, so the two helpers take `Marks`; `Capture.snapshot` became a `var` so snapshots can be released. All internal, no public API change.

**2. [Rule 3 - Blocking] `GROUP_LIMIT` and `AGE_LIMIT_MILLIS` are `private const val`**
- **Issue:** an `internal const val` compiles to a public static field and `UndoApiShapeTest` rejects it.
- **Fix:** `private`. The Builder defaults and the acceptance greps are unaffected; tests read the defaults from `Builder()`.

**3. [Rule 3 - Blocking] `Recorded` class; no default arguments, no data classes**
- `append` had seven parameters (LongParameterList), so the action's ref, failed flag and frozen ticket travel as an internal `Recorded`. It is a plain class: `UndoApiShapeTest` rejects data-shaped and default-argument classes among internal ones too.

**4. [Rule 1 - Bug in my test] Redaction test used the group key as run id**
- `EntryRef.toString` prints its run id by design (the plan says non-canary run id), so the first run failed on a canary I planted in a run id. The test now uses a plain run id and a canary group key.

**5. [Rule 2 - Missing critical] Throwing clock guarded**
- The clock is app code. `now()` goes through `guardedCall` and falls back to the last reading, so a faulty clock never breaks a journal call (the journal's calls are documented to throw only for an invalid key).

**6. [Ordering note] Test support landed in the Task 1 and 2 commits**
- `Rig` gained a `configure` parameter, `group`/`groupOf`, `undoEntry` and `resume` helpers in `UndoTestSupport.kt`, shared by the later test classes.

**Total deviations:** 6 (3 Rule 3, 1 Rule 1, 1 Rule 2, 1 note). **Impact:** none on the frozen public surface beyond the planned members.

## Notes for the next plans

- A group handed to `JournalStore.save` is a view only; there is intentionally no way back in. Loading can be added later as a defaulted `JournalStore` method.
- After a cancelled undo the store may hold a stale view until the group's next change: the mirror is published after a normal return only.
- `UndoJournal` public members added in this plan: `runClosed`, `withhold`, `group`, `undoEntry`, `storeFaults`, `Builder.maxGroups/maxAgeMillis/clock/store`. `JournalStore` and `UndoGroup` are new public types. They are for the surface review in 17-10.
- The bridge in 17-08 should call `runClosed` with the run's applied positions once per run (held children use their own run id with positions from 0) and `withhold` when its own sink faulted.

## Self-Check: PASSED

- Created files exist: UndoGroup.kt, JournalStore.kt, Retention.kt, StoreMirror.kt, WithheldGroupTest.kt, IsolationTest.kt, LimitsTest.kt, JournalStoreTest.kt, UndoRedactionTest.kt.
- Commits 0ecd184, 5627289, 0676f9f: FOUND.
- No external API integration: this plan changes a dependency-free in-memory journal and calls no external service.
