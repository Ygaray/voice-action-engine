---
phase: 17-run-level-undo
reviewed: 2026-10-06T00:00:00Z
depth: standard
files_reviewed: 78
files_reviewed_list:
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
findings:
  critical: 0
  warning: 8
  info: 7
  total: 15
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-10-06
**Depth:** standard
**Files Reviewed:** 78
**Status:** issues_found

## Summary

Reviewed the new `:undo` module (journal, ticket, verifier, restorer, compensating, retention, store mirror), the two
additive `:core` seams (`ActionEvent.heldRunId`, `compositeSink`), the sample bridge and its doc twin, the build gates
(`invariants.gradle.kts`, `undo/build.gradle.kts`) and the manifest-driven release/verification scripts. The public
surface review in `17-SURFACE-REVIEW.md` was not repeated, and the open items it already lists for Phase 20
(`api_baseline_check` "no baseline" branch for a new module, selftest step 4, `verify-docs-coverage.sh` C01/C20,
`run-sample-gate1.sh` path list) are not re-reported here.

The undo algorithm itself holds up under tracing: verify-then-restore, shared claim, mark-as-you-go resume, snapshot
hand-off before `dropSnapshots`, lock discipline (no app code under `synchronized`), cancellation re-throw and the
redaction rules (no value, id, payload or message reaches `toString()` or an exception message) all check out. No
BLOCKER was found. The findings are guarantees that are weaker than the KDoc/docs state (retention tombstones,
withheld-group snapshot retention, fingerprint coverage of children, `Partial` semantics), a wall-clock dependency in
the retention logic, one ticket-state inconsistency, and a fail-open branch in the live probe. Because the `:undo`
surface freezes at v1.1.0, the doc/behaviour mismatches (WR-05, WR-06) are worth settling before the tag, not after.

## Warnings

### WR-01: The "late record into a dropped group is withheld" guarantee silently expires after `maxGroups` further drops

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Retention.kt:34-37` (with `JournalState.kt:216-223`)
**Issue:** `Retention` remembers dropped keys in a tombstone set capped at `maxGroups` (oldest forgotten first).
`JournalState.open()` creates a group withheld only when `wasDropped(key)` still finds the tombstone. Once more than
`maxGroups` (default 50) groups have been dropped since, a late record for the old key (the realistic case is a held
change confirmed with `commitHeld` long after the run) opens a brand-new group that is NOT withheld. That group holds
only the late action, so `group(key).count` is 1 and `undoAll` offers "Undo all (1)" for a command that really
applied more. That is exactly the "N-1 instead of a loud refusal" outcome D-05 and the KDoc ("never offers a partial
undo") rule out, and nothing tells the consumer. The comment on the class admits the cap but the public docs do not.
**Fix:** Make the guarantee unconditional or state its bound. Options: (a) keep tombstones for as long as a held
proposal could still be confirmed, e.g. an age bound of the same `maxAgeMillis` window times some factor plus a hard
cap far above `maxGroups`; (b) treat a record whose `parentGroupKey`/held origin is unknown and whose group key is
absent as withheld when `heldRunId` was set (the bridge knows it: call `journal.withhold(group)` first when
`confirmed && group` is not known to the journal, i.e. `journal.group(group) == null`). Whatever is chosen, add the
bound to the `UndoJournal.record` KDoc and INTEGRATION section 11.

### WR-02: Retention uses the wall clock, and a failed first clock read poisons `lastActive`

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt:46, 79, 224`
**Issue:** The default clock is `System.currentTimeMillis()`. A wall-clock step forward of more than `maxAgeMillis`
(NTP correction, user changing the time or time zone handling that moves the epoch reading) makes `now - lastActive >
maxAgeMillis` true for every group at the next sweep, so every pending undo disappears at once (`UNKNOWN_GROUP`).
Separately, `lastNow` starts at 0 and `now()` returns it when the clock throws: if the first reading faults, a group is
stamped `lastActive = 0`, and the next good reading (about 1.7e12) drops that group immediately as "idle for 55 years".
Both lose undo history silently (it fails safe, but the user-visible effect is "Undo all" vanishing).
**Fix:** Default to a monotonic source and keep the injectable seam: `public var clock: () -> Long = { System.nanoTime() / 1_000_000 }`
(update the KDoc, which says "the system clock"). For the fault fallback, do not stamp a group with the bootstrap 0:
initialise `lastNow` from the first successful reading, or fall back to `Long.MIN_VALUE` semantics that never age a
group (e.g. skip the age rule when no reading has ever succeeded).

### WR-03: `nothingWritten()` leaves declared `touches` behind, so a no-op action can entangle real ones

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/TicketState.kt:105-112`
**Issue:** `clearForNothingWritten()` clears `captures` and `compensations` but not `touched`. The public KDoc on
`UndoTicket.nothingWritten()` says "what was captured and declared so far is dropped". A ticket that called
`touches(type, id)` and then `nothingWritten()` (an apply that bailed after declaring) freezes with a non-empty
`touches` set, and `Entry.footprint = keys + data.touches` makes that no-op action share an entity with later real
actions. Effect: `UndoGroup.isolated` loses entries and `undoEntry` returns `ENTANGLED` for actions that are in fact
independent; `Restorer` also puts them in one failure component.
**Fix:**
```kotlin
fun clearForNothingWritten() {
    synchronized(lock) {
        checkOpen()
        captures.clear()
        touched.clear()
        compensations.clear()
        nothingWritten = true
    }
}
```

### WR-04: A withheld group keeps its entity snapshots until age/limit eviction

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt:127-163`
**Issue:** The KDoc promises that "the snapshots of user data in it do not pile up or linger", and `dropSnapshots()` is
called for undone entries and dropped groups. But when a group becomes withheld (`append` anomaly, `runClosed`
mismatch, `withhold`) the snapshots already stored by earlier, valid entries are kept. A withheld group can never be
undone (`claim` returns `JOURNAL_WITHHELD`, and `withheld` never clears), so those snapshots are dead weight of user
data held for up to an hour. `append` already avoids storing data for new entries of a withheld group (`kept` is null),
which shows the intent; the transition itself is missing.
**Fix:** In every place that sets `group.withheld = true` (the three above), also call `group.dropSnapshots()`.
A small helper keeps it in one spot:
```kotlin
private fun Group.withhold() { withheld = true; dropSnapshots() }
```

### WR-05: The fingerprint contract does not cover children, and touched-only entities are never verified, so "refuses when anything moved on" can be violated

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/EntityAdapter.kt:19-26`; `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Verifier.kt:93-107`
**Issue:** `read` is documented to return a snapshot that carries a parent's children, and `restoreIf` re-inserts
children. `fingerprint` is documented only as "an opaque hash of the entity's current content and version". The
Verifier fingerprints only captured keys; entities declared with `touches(...)` (the cascade-child case the ticket
KDoc names) are never read or compared. If an app's parent fingerprint does not include its children, a user's later
edit of a child leaves the parent fingerprint unchanged, the pass verifies, and `restoreIf` overwrites the child with
the snapshot: silent loss of a later user edit, against the central promise of UNDO-01. The docs (INTEGRATION section
11, API.md) say "a value that changes whenever the entity does" without saying what "the entity" includes.
**Fix:** State the requirement where adapters are written: `fingerprint` of a parent MUST change when any child that
`read` snapshots changes (and `restoreIf` must compare that same fingerprint). Say it in `EntityAdapter.fingerprint`
KDoc, in INTEGRATION section 11 under the adapter bullet, and add a line to `ItemAdapter` or a test adapter showing a
parent fingerprint that folds in children. Optionally add a test where a child edit between command and undo must give
`CHANGED_SINCE`.

### WR-06: A pass that wrote nothing can return `Partial` with an empty `restored`, contradicting the documented meaning

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt:319-332` (result shape in `UndoResult.kt:44-65`)
**Issue:** `restore()` returns `Partial(restored, notRestored)` whenever `notRestored` is non-empty. If the very first
step loses its race (`restoreIf` returns false, `CHANGED_SINCE`) or throws (`RESTORE_FAILED`), `restored` is empty and
nothing was written, yet the result is `Partial`. `UndoResult.Partial` is documented as "Some of the group was restored
and some was not", INTEGRATION's table says the same, and `Refused` is "Nothing was written". A consumer that maps
`Partial` to "some of your changes are undone" is wrong in this case. The result set is frozen at v1.1.0 (D-08), so
the wording is the only thing that can still change cheaply.
**Fix:** Either document that `Partial.restored` may be empty ("the undo went ahead and at least one item was not
restored; `restored` lists what was") in `UndoResult.Partial`, INTEGRATION and API.md, or (behaviour change) keep the
shape but add a stable test pinning the empty-`restored` case so it is a conscious contract.

### WR-07: A cancelled undo neither refreshes the group's activity nor tells the store

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/JournalState.kt:302-317`; `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt:186-189, 212-215`
**Issue:** `UndoPass.run` sets `changed` only after `restore()` returns. When `restore()` is cancelled partway (the
case `CancelMidUndoTest` exercises), entities may already be restored and marked (revision bumped by `Marks`), but
`release(..., changed = false)` skips `group.touch`, and the exception skips `UndoJournal.undone()` so `publish` is never
called. Consequences: the group's `lastActive` stays old, so a group the user just half-undid can be age-dropped at the
next sweep even though a retry is exactly what is expected; and a `JournalStore` mirror keeps showing the pre-undo view
until some other change arrives (the revision it holds is stale).
**Fix:** Track "something was written" from the marks rather than from the result: e.g. have `Marks` flag the group, or
set `changed = true` in a `finally` when `state.marks` recorded any restore during this pass; and publish in a `finally`
inside `undoAll`/`undoEntry` (`try { ... } finally { publish(groupKey) }` is safe since `publish` only calls the guarded store).

### WR-08: `jitpack-live-probe.sh` core-dependency check fails open for an unknown module and finds the manifest through the caller's cwd

**File:** `scripts/jitpack-live-probe.sh:92-104` (plus `scripts/lib/modules.sh:10-16`)
**Issue:** `needs_core="$(vae_module_field "$mod" dependsOnCore 2>/dev/null || echo unknown)"`: when the module is not in
the manifest (an `EXPECT_MODULES` override, or an artifact id that does not strip to a manifest name), `needs_core` is
`unknown` and neither the "must depend on core" nor the "must not depend on core" branch runs, so the check passes
silently with `CHECK_CORE_DEP=1`. Before this change providers/keystore were hard-checked by name. Also, this script
does not set `VAE_MODULES_FILE`, so `_vae_file` resolves the manifest from `git rev-parse --show-toplevel` of the
caller's working directory, not from the script's own repo; run from another checkout it reads (or fails to find) a
different manifest, while every sibling script sets `VAE_MODULES_FILE` explicitly.
**Fix:** Set the manifest relative to the script (`export VAE_MODULES_FILE="${VAE_MODULES_FILE:-$HERE_LIB/modules.list}"`,
using `$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/modules.list`), and make `unknown` a failure when `CHECK_CORE_DEP=1`:
```bash
[ "$needs_core" != unknown ] || fail "$m: not in scripts/modules.list, cannot decide its core dependency (set CHECK_CORE_DEP=0 to skip)"
```

## Info

### IN-01: The reference bridge's recovery path can itself throw for the one input that triggers it

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoCommitSink.kt:41-45` (copied into INTEGRATION.md section 11 and `DocSnippetsTest`)
**Issue:** The `catch (_: IllegalArgumentException)` falls back to `journal.withhold(group)`. If the IAE came from an
invalid group key (a blank or over-256-character run id), `withhold` runs `requireToken("groupKey", ...)` and throws the
same IAE out of `onAction`; the engine records `sink_error`, and nothing is withheld (and `undoAll` for that key would
throw too). Real run ids are short, so this is theoretical, but the snippet is the pattern apps copy.
**Fix:** Guard the fallback (`runCatching` is banned by the scanner, so use a nested try/catch), or validate the key
once at the top and skip journaling for an invalid key with a comment.

### IN-02: Dead field and stale naming in `Footprints`

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Footprints.kt:10, 13-15`
**Issue:** `Cluster.keys` is computed for every component (`members.flatMap { ... }.toSet()`) and never read. The class
KDoc says "The entryKeys of a set of actions", a leftover name from an earlier shape (`entryKeys` is now only a private
map).
**Fix:** Drop `keys` from `Cluster` (or use it), and reword the KDoc to "The footprints of a set of actions".

### IN-03: A `@Suppress("MaxLineLength")` exists only to satisfy a planning-document grep

**File:** `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoJournal.kt:127-129`
**Issue:** The project policy (detekt zero baseline, "tune the rule with a one-line justification, never bank debt";
`:core`'s only justified suppression is the never-throw collapse) is bent for a reason that lives in a plan ("the plan's
contract check greps the exact signature"). Library source now carries a process reference, and a frozen public method
is kept on one 120+ column line so an old check keeps matching. `SURFACE-REVIEW` A6 already records it as accepted, so
this is a note, not a defect.
**Fix:** Update the plan's check to match the multi-line signature (or the Metalava dump, which now exists), then format
`record(...)` one parameter per line and remove the suppression and the planning reference.

### IN-04: `ActionEvent.toString()` redacts `heldRunId` but prints `parentRunId`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt:37-41`
**Issue:** Both are engine-generated run ids of the same kind. Hiding one and printing the other gives a redaction rule
with no stated reason and makes logs harder to correlate (the held run is exactly what you want to see when debugging a
confirmed change). The existing `HeldRunIdTest` may pin the current text.
**Fix:** Print both, or hide both, and say why in the KDoc. If the ids can be app-chosen strings that carry user text
(the `runIds` seam is app code), hide both consistently.

### IN-05: Three independent manifest parsers and a few lax edges in them

**File:** `scripts/lib/modules.sh:30-44`, `scripts/release-cut.sh:86-89`, `scripts/lib/published_versions.py:23-34`
**Issue:** The manifest is parsed in bash (`modules.sh`, strict, 5 fields), in `release-cut.sh` (awk, silently skips a
row that does not have exactly 5 fields) and in Python (strict, exits 2). A malformed row is therefore ignored by the
release script's `MODULES` (gates 7, 10, 12 and the tag message) and only caught later by gate 11. Also: `set -- $line`
in `_vae_rows` is unquoted and subject to pathname expansion (a `*` in a field expands against the cwd, hence the
`shellcheck disable`); duplicate module names or artifact ids are accepted and `vae_modules` would list a module twice;
only the name column is validated; and `run_cut` assigns `coords` and `m` without `local`.
**Fix:** Run `set -f` around the split (and restore), reject duplicate name/artifactId in `_vae_rows`, have
`release-cut.sh` source `lib/modules.sh` from HEAD's archive instead of re-implementing the parse, and add the two
variables to the `local` list.

### IN-06: `jitpack-dry-run.sh` compares a locale-sorted list with a `C`-sorted one

**File:** `scripts/jitpack-dry-run.sh:46-49`
**Issue:** `expected` comes from `vae_artifacts_sorted` (`LC_ALL=C sort`), while `found` is built with a bare `sort` in the
caller's locale. The two agree for today's four artifact ids only because they differ in no punctuation-sensitive way;
a future id such as `voice-action-engine-voice-adapter` next to `...-voice...` could sort differently under a non-C
locale and fail the dry run for no real reason (`release-cut.sh` exports `LC_ALL=C`, standalone runs do not).
**Fix:** `found="$(... | LC_ALL=C sort | tr '\n' ' ')"`.

### IN-07: The sample `Item` data class prints user text in `toString()`

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemStore.kt:8`
**Issue:** `data class Item(val id, val title, ...)` gets a generated `toString()` that includes the title (user data), and
the sample is documented as the reference wiring. The journal itself never prints a snapshot, so nothing leaks today, but
an app that copies the pattern (and logs a snapshot or a `restored` assertion message) breaks the "user data never
reaches `toString()`" rule this phase is built around.
**Fix:** Override `toString()` to print only `id` length and `stamp` (like `EntityKey`), or make it a plain class and note
why in the KDoc.

---

_Reviewed: 2026-10-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
