---
phase: 17-run-level-undo
plan: 05
subsystem: undo-journal
tags: [kotlin-jvm, undo, journal, memento, verify-then-restore, closed-result-set]

requires:
  - phase: 17-01
    provides: ":undo module scaffold, UndoReason, zero-deps gate, header-only api.txt seed"
provides:
  - "EntityAdapter and Compensator seams (non-generic, Any? snapshots)"
  - "UndoTicket capture-inside-apply protocol (capture, settle, created, touches, compensate, nothingWritten)"
  - "UndoJournal facade: Builder DSL, newTicket, record, undoAll"
  - "UndoResult closed set (Complete, Refused, Partial, AlreadyUndone) with Blocker, NotRestored, EntryRef, EntityKey"
  - "Internal Verifier: whole-scope read-only verification before any write, deterministic blocker order"
  - "internal/Guard.kt: the module's one never-throw collapse point"
affects: [17-06, 17-07, 17-08, 17-09, 17-10, 18, 19, 20]

actuals:
  tokens: 19000
  tasks: 3
  commits: 3
plan_head_before: 7acd513e32a3c5285b626a2f99299a39b98a858a
commits: 3

tech-stack:
  added: []
  patterns:
    - "Kotlin-stdlib-only journal: synchronized state blocks that never call app code, adapter calls outside any lock"
    - "Verify the whole scope read-only, then write: any blocker refuses with nothing written"
    - "Value in a plan result is a class name or a count, never an id, snapshot, fingerprint, payload or message"

key-files:
  created:
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoTicket.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/EntityAdapter.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/EntryRef.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoResult.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Guard.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/TicketState.kt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Verifier.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoTestSupport.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoStandaloneTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/AdapterRoundTripTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/ResultShapeTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/UndoApiShapeTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/RefuseLoudlyTest.kt
    - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/ChainVerifyTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt

key-decisions:
  - "Capture problems (adapter threw, no adapter, settled without capture) count as sealed: the journal keeps the entity and refuses at undo with UNVERIFIABLE or NO_ADAPTER naming it, rather than withholding the whole group"
  - "A restore that fails or races after verification gives Partial; the first failure stops the pass and later steps are reported SKIPPED_AFTER_FAILURE (plan 17-06 refines this per component)"
  - "Per-entry restoredKeys is tracked now, so a retry after a partial pass verifies only what is left and never mistakes an already-restored entity for CHANGED_SINCE"
  - "The restore skips an entity whose live fingerprint equals its earliest before-fingerprint, so a no-op write and an entity created and deleted inside one command write nothing"

patterns-established:
  - "Test-side runSuspending (startCoroutine + CountDownLatch) keeps the :undo test classpath free of any coroutines library"

requirements-completed: [UNDO-01, UNDO-02, UNDO-03]

coverage:
  - id: D1
    description: "A plain JVM app with no engine on the classpath records one edit through a ticket and undoes it; second undoAll is AlreadyUndone; unknown group is Refused UNKNOWN_GROUP"
    requirement: UNDO-01
    verification:
      - kind: unit
        ref: "UndoStandaloneTest (4 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D2
    description: "Adapters restore created, edited and deleted-with-children entities, and across two adapters in reverse journal order"
    requirement: UNDO-02
    verification:
      - kind: unit
        ref: "AdapterRoundTripTest (4 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D3
    description: "Whole-scope verification: edited, deleted or recreated since, chain broken, unverifiable, no adapter each refuse with nothing written and no compensator run; blocker order is deterministic"
    requirement: UNDO-03
    verification:
      - kind: unit
        ref: "RefuseLoudlyTest (13 tests) and ChainVerifyTest (3 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D4
    description: "UndoResult is a closed set of exactly four members; reasons stay open; no enum, data shape, public static field or default-argument constructor in :undo main"
    requirement: UNDO-03
    verification:
      - kind: unit
        ref: "ResultShapeTest (8 tests) and UndoApiShapeTest (7 tests) via :undo:test"
        status: pass
    human_judgment: false
  - id: D5
    description: "Plan-end gate: tests, detekt, scanBannedConstructs, explicit API, module graph, zero deps and Metalava compat for :undo, plus :core:detekt"
    requirement: UNDO-01
    verification:
      - kind: integration
        ref: "./gradlew :undo:check :core:detekt (exit 0, 39 tests)"
        status: pass
    human_judgment: false
---

# Phase 17 Plan 05: :undo journal core (seams, closed result set, verify-then-restore) Summary

A dependency-free `:undo` journal where an app records each applied action through a capture-inside-apply `UndoTicket`, and `undoAll(group)` verifies the whole scope read-only, then restores newest-first through per-entity adapters, or refuses loudly with nothing written.

## What was done

1. **Tracer (commit 9803e90).**
   - Public surface in `io.github.ygaray.voiceactionengine.undo`: `EntryRef` (equality on run and position), `EntityKey` (prints type and id length only), `EntityAdapter` (`entityType`, `read`, `fingerprint`, `restoreIf`), `Compensator`, `UndoResult` (sealed, four members, codes `complete`, `refused`, `partial`, `already_undone`), `Blocker`, `NotRestored`, `UndoTicket`, `UndoJournal` with `Builder` and `Companion.invoke`.
   - Internal: `Guard.kt` (`guardedCall`, `errorClassOf`, `requireToken`; the one `@file:Suppress`), `TicketState.kt` (ticket state, frozen `TicketData`, the guarded `TicketReader`), `JournalState.kt` (entries with a journal-wide sequence, groups, scope, the undo pass).
   - `UndoStandaloneTest` proves the journal works with the engine absent from the classpath (`Class.forName` of the core `CommandInput` throws).
2. **Adapter round trips and frozen-shape pins (commit eb58017).** Created, edited (two fields), deleted-with-children (original id and both children re-inserted) and two-adapter reverse order (`restoreIf:i` before `restoreIf:f`). `ResultShapeTest` pins the four members, an exhaustive `when` with no `else`, the twelve reason wire values and counts-only `toString`. `UndoApiShapeTest` sweeps every `:undo` main class (no enum, no copy/componentN pair, no public static field except INSTANCE/Companion, no default-argument constructor stub), non-vacuously. The core `Guarded.kt` comment now says the suppression is "this module's only one".
3. **Refuse loudly (commit 151f3fe).** The tracer's inline planner became `internal/Verifier.kt`. Per entity: live equals the earliest before-fingerprint means already as it was (nothing to write); otherwise every write must be settled, each must start where the previous left the entity (else `CHAIN_BROKEN` on the later entry), and live must equal the newest after-fingerprint (else `CHANGED_SINCE` on the newest entry). A failed entry with nothing captured and no `nothingWritten()` is `UNVERIFIABLE` with a null entity, a capture fault or unreadable live state is `UNVERIFIABLE`, a type with no adapter is `NO_ADAPTER`. Any blocker refuses the whole group. Blockers are ordered newest entry first, then entity type, then id, so the first blocker is the single-valued `changedItem` of the consumer's UI.

## Verification

- `./gradlew :undo:check :core:detekt` exits 0 (39 tests: Standalone 4, AdapterRoundTrip 4, ResultShape 8, UndoApiShape 7, RefuseLoudly 13, ChainVerify 3). `:undo:check` includes detekt (zero baseline, defaults on main and test), scanBannedConstructs, explicit API, module graph, zero deps and the Metalava compat check against the header-only seed.
- All plan acceptance greps pass, including the `core` Guarded.kt comment-only diff (2 added, 2 removed lines against 147a959).
- RED evidence for Task 3: before `Verifier.kt` existed, the new tests failed on the planned behavior (7 of 16: chain broken restored anyway, failed-unsettled and capture-fault cases reported `changed_since`, a failed uncaptured entry came back Complete, the no-op and created-then-deleted cases made a restore call). All 16 went green after the Verifier.
- No device, API key, network publish or heavy gate (negative-control suite, jitpack dry run, live probe, release cut) was run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MAX_TOKEN_LENGTH` is private to Guard.kt instead of `internal const val`**
- **Found during:** Task 1 design, against the Task 2 sweep.
- **Issue:** an `internal const val` compiles to a public static field, which the plan's own shape sweep ("no public static field other than INSTANCE/Companion") rejects. A non-const `internal val` would trip detekt MayBeConst.
- **Fix:** `private const val MAX_TOKEN_LENGTH = 256` in Guard.kt; only `requireToken` needs it, and the tests use a 257-character string.
- **Files:** `internal/Guard.kt`.

**2. [Rule 3 - Blocking] The catch order in `guardedCall` differs from the plan's wording**
- **Found during:** Task 1 detekt run.
- **Issue:** a single `catch (e: Exception)` that rethrows when `e is CancellationException` trips `InstanceOfCheckForException`.
- **Fix:** `catch (e: CancellationException) { throw e }` first, then `LinkageError`, then `Exception`. Behavior is the same (a cancellation is always rethrown), and the module still has exactly one `@file:Suppress`.
- **Files:** `internal/Guard.kt`.

**3. [Rule 3 - Blocking] `record(...)` is one line with a function-level `@Suppress("MaxLineLength")`**
- **Found during:** Task 1 acceptance check.
- **Issue:** the plan's grep needs the exact five-parameter signature on one line (129 columns), and detekt's maximum is 120.
- **Fix:** a one-line justification comment and `@Suppress("MaxLineLength")` on that function only. It is not a file-level suppression, so the module's `@file:Suppress` count stays 1.
- **Files:** `UndoJournal.kt`.

**4. [Rule 3 - Blocking] One extra internal file, `internal/TicketState.kt`**
- **Found during:** Task 1, to stay under detekt's TooManyFunctions (12 per class) with `UndoTicket`.
- **Fix:** ticket state, frozen data and the guarded reader live in `TicketState.kt`, which the plan's `files_modified` did not list.

**5. [Design choice] Compensations are stored but not run in this plan**
- The ticket and journal keep declared compensations and registered compensators (`UndoJournal.compensators` is `internal`), but running them, in reverse order and exactly once, is plan 17-06. An entry whose entities are all restored is marked undone here regardless of compensations; 17-06 changes that condition.

**Total deviations:** 5 (4 Rule 3 blocking adjustments, 1 scope note). **Impact:** none on the frozen public surface, which matches the plan's names.

## Notes for the next plans

- `Partial` handling here is deliberately minimal (stop at the first failed or raced restore, report the rest as `SKIPPED_AFTER_FAILURE`). 17-06 adds independent components via footprints, compensators and the in-progress flag.
- `touches(...)` is stored on the frozen ticket data but nothing reads it yet; 17-06 and 17-07 use it for entanglement and isolation.
- `parentGroupKey` is stored on the group (first record wins) and is not yet exposed; the group view in 17-07 reads it.
- Internal members of public classes compile to `$undo`-suffixed public methods, so any reflection-based shape check must filter names containing `$` (done in `UndoApiShapeTest`).

## Self-Check: PASSED

- All 16 files in key-files (created) exist; `core/.../Guarded.kt` modified.
- Commits 9803e90, eb58017, 151f3fe: FOUND.
- No external API integration: this plan adds a dependency-free in-memory journal; it calls no external service, SDK or HTTP endpoint.
