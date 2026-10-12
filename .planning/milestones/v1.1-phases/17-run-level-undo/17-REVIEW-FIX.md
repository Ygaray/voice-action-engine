---
phase: 17-run-level-undo
fixed_at: 2026-10-06T00:00:00Z
review_path: .planning/phases/17-run-level-undo/17-REVIEW.md
iteration: 1
findings_in_scope: 15
fixed: 14
skipped: 1
status: resolved
---

# Phase 17: Code Review Fix Report

**Fixed at:** 2026-10-06
**Source review:** .planning/phases/17-run-level-undo/17-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 15 (8 warning, 7 info; fix_scope = all)
- Fixed: 14
- Skipped: 1 (IN-04, a documented acceptable-skip)

No public API signature changed. `undo/api.txt` is untouched, and every KDoc or doc edit leaves the signatures alone. `:undo` still depends only on the Kotlin standard library.

## Verification

All gates ran in the **isolated review-fix worktree** (`.claude/worktrees/rf-17-43693-1791333141`, branch `gsd-reviewfix/17-43693`), which was then fast-forwarded into `gsd/phase-17-run-level-undo`. The tree is identical at HEAD, so the results reproduce from the main checkout. Before each Gradle run MemAvailable was about 8.9 GiB, above the 5 GiB floor. Every run was single-use (`daemon=false`, `workers.max=2`, in-process Kotlin, `-Xmx1536m`).

- `./gradlew :undo:detekt :undo:test`: BUILD SUCCESSFUL, 113 tests, 0 failures, detekt clean (zero baseline).
  - Run 1 failed detekt (`MemberNameEqualsClassName` on the IN-02 rename). I fixed it forward in commit 66369a9.
  - Run 2 failed `UndoApiShapeTest.noMainClassLeaksAPublicStaticField...` because `const val NO_READING` compiled to a public static field. I fixed it forward in e2768d2 / fc7c567 (it is now a plain `internal val`).
- `./gradlew :sample:testDebugUnitTest --tests '*sample.undo*' --tests '*DocSnippetsTest*'`: BUILD SUCCESSFUL, 26 tests, 0 failures. This includes `UndoBridgeParityTest` for IN-01.
- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=107`. It byte-compares the INTEGRATION `undo-bridge` block with the DocSnippetsTest region.
- `scripts/verify-module-manifest.sh`: `MODULE MANIFEST OK modules=core,providers,keystore,undo`.
- Shell edits: `bash -n` on every touched script. I also ran ad-hoc probes of `lib/modules.sh` on the real manifest, a duplicate-row manifest (rejected) and a `*` field (stays literal).
- Not run (per the run rules): full `check`, the negative-control suite, the JitPack dry run, the live probe and Metalava `apiCheck`. WR-08 and IN-06 are verified by syntax check and code reading only.

Process note: given the host-memory rule, each finding was committed after its Tier-1 check, and Gradle ran over the accumulated `:undo` commits instead of once per finding. The two Gradle failures above were fixed forward with follow-up commits, not rolled back.

## Fixed Issues

### WR-01: The "late record into a dropped group is withheld" guarantee silently expires after `maxGroups` further drops

**Files modified:** `undo/.../internal/Retention.kt`, `undo/.../UndoJournal.kt`, `INTEGRATION.md`, `undo/src/test/.../LimitsTest.kt`
**Commit:** 801f6c2
**Applied fix:** The tombstone set is now bounded at `max(1000, 20 * maxGroups)` instead of `maxGroups`. The remaining bound is stated in the `UndoJournal.record` KDoc and in INTEGRATION section 11 ("Limits and the store"). I chose option (a). Option (b), a bridge-side withhold for unknown groups, would change the reference bridge's semantics. New tests: a key dropped 30 drops ago stays withheld at `maxGroups = 1`, and a key past the 1000-entry bound is forgotten.
**Status:** fixed: requires human verification. This is a policy choice: the guarantee is now much longer but still bounded, not unconditional.

### WR-02: Retention uses the wall clock, and a failed first clock read poisons `lastActive`

**Files modified:** `undo/.../UndoJournal.kt`, `undo/.../internal/Retention.kt`, `undo/.../internal/JournalState.kt`, `undo/src/test/.../LimitsTest.kt`
**Commits:** 294262b, e2768d2, fc7c567
**Applied fix:**
- The default `clock` is now `System.nanoTime() / 1_000_000` (monotonic). The KDoc says only differences matter. The property signature is unchanged.
- `lastNow` and `Group.lastActive` start at an internal `NO_READING` sentinel. A sweep with no reading ages nothing. A group stamped before the first good reading is adopted at that reading and starts aging from it.
- New test: a clock that throws on the first read and then returns ~1.7e12 keeps the group, and only ages it out `maxAgeMillis` after the first good reading.

**Status:** fixed: requires human verification (time-handling logic).

### WR-03: `nothingWritten()` leaves declared `touches` behind, so a no-op action can entangle real ones

**Files modified:** `undo/.../internal/TicketState.kt`, `undo/src/test/.../IsolationTest.kt`
**Commit:** 998853b
**Applied fix:** `clearForNothingWritten()` now clears `touched` as well. New test: a ticket that calls `touches("item","a")` and then `nothingWritten()` leaves both actions isolated, and `undoEntry` succeeds.

### WR-04: A withheld group keeps its entity snapshots until age/limit eviction

**Files modified:** `undo/.../internal/JournalState.kt`, `undo/src/test/.../WithheldGroupTest.kt`
**Commit:** 372e925
**Applied fix:** Added `Group.withhold()`, which sets `withheld` and calls `dropSnapshots()`. All three transitions use it: the append anomaly, the runClosed mismatch and `withhold`. The new internal-level tests cover each transition and assert that the capture's snapshot is released.

### WR-05: The fingerprint contract does not cover children, and touched-only entities are never verified

**Files modified:** `undo/.../EntityAdapter.kt`, `INTEGRATION.md`, `API.md`
**Commit:** ad7d3c8
**Applied fix (doc/contract only):** The `EntityAdapter.fingerprint` KDoc, the INTEGRATION section 11 adapter bullet and the API.md `EntityAdapter` row now state the requirement: a parent's fingerprint must change when any child that `read` snapshots changes, and `restoreIf` must compare that same fingerprint. I did not add the optional child-edit test or the `ItemAdapter` example. The behaviour depends on the app's adapter, and the doc is the contract surface.

### WR-06: A pass that wrote nothing can return `Partial` with an empty `restored`

**Files modified:** `undo/.../UndoResult.kt`, `INTEGRATION.md`
**Commit:** 4359b68
**Applied fix (doc only):** `UndoResult.Partial` KDoc and the INTEGRATION results table now say that Partial means "the undo went ahead and at least one item was not restored", and that `restored` may be empty. The empty-`restored` case is already pinned by `PartialRestoreTest.aFailureStopsItsComponentAndSkipsTheKeysAfterIt`, so no new test was needed. API.md only lists the result names and needed no change.

### WR-07: A cancelled undo neither refreshes the group's activity nor tells the store

**Files modified:** `undo/.../internal/JournalState.kt`, `undo/.../UndoJournal.kt`, `undo/src/test/.../LimitsTest.kt`, `undo/src/test/.../JournalStoreTest.kt`
**Commit:** 15990ec
**Applied fix:**
- In `UndoPass.run`, `changed` now defaults to `true`. Only a `Refused` result, which wrote nothing, leaves activity alone. A pass cut short by a cancellation still touches the group on `release`.
- `undoAll`/`undoEntry` publish to the store in a `finally`.
- New tests: a cancelled undo keeps the group alive past the old age deadline, and a store sees the post-cancel view (count 1, revision rose).
- Caveat: when a cancellation interrupts the undo, the `finally` publish calls a suspending store. If that store also throws a cancellation, it replaces the original one. Both are `CancellationException`, so this is acceptable without `NonCancellable`, which `:undo` cannot use because it is stdlib-only.

**Status:** fixed: requires human verification (state handling on cancellation).

### WR-08: `jitpack-live-probe.sh` core-dependency check fails open for an unknown module

**Files modified:** `scripts/jitpack-live-probe.sh`
**Commit:** 1fd0bb5
**Applied fix:** The script now exports `VAE_MODULES_FILE="${VAE_MODULES_FILE:-$HERE_LIB/modules.list}"`, so it reads the manifest next to the script rather than the caller's git toplevel. With `CHECK_CORE_DEP=1`, a `needs_core=unknown` module now fails, and the message says to set `CHECK_CORE_DEP=0` to skip. Verified by `bash -n` only; the live probe was not run.

### IN-01: The reference bridge's recovery path can itself throw for the one input that triggers it

**Files modified:** `sample/.../undo/UndoCommitSink.kt`, `sample/src/test/.../docs/DocSnippetsTest.kt`, `INTEGRATION.md`
**Commit:** 39e92cf
**Applied fix:** The `withhold(group)` fallback is now wrapped in a nested `try/catch (IllegalArgumentException)`, because `runCatching` is banned. A comment explains that a rejected key has no group to offer "Undo all" for. All three copies were changed identically. The parity test and the doc-coverage byte compare both pass.

### IN-02: Dead field and stale naming in `Footprints`

**Files modified:** `undo/.../internal/Footprints.kt`
**Commits:** dd40ae2, 66369a9
**Applied fix:** Dropped `Cluster.keys` and reworded the KDoc to "The footprints of a set of actions". The private map is now named `entryFootprints`. My first rename to `footprints` tripped detekt `MemberNameEqualsClassName`.

### IN-03: A `@Suppress("MaxLineLength")` exists only to satisfy a planning-document grep

**Files modified:** `undo/.../UndoJournal.kt`
**Commit:** e1b5653
**Applied fix:** `record(...)` is now formatted one parameter per line, and both the suppression and the planning-reference comment are gone. The signature is unchanged. The grep in `17-05-PLAN.md` is a historical acceptance check of an executed plan, so I left it alone. The Metalava dump is the live contract.

### IN-05: Three independent manifest parsers and a few lax edges in them

**Files modified:** `scripts/lib/modules.sh`, `scripts/release-cut.sh`
**Commit:** 85d2060
**Applied fix:**
- `_vae_rows` now splits with `set -f`, restoring the caller's noglob state, and rejects duplicate module names and duplicate artifactIds.
- `run_cut` declares `coords` and `m` local.
- Not done: making `release-cut.sh` source `lib/modules.sh` from HEAD's archive. That is a structural change to the tag-cut script, and it should be made deliberately and run through the release selftest, which is out of scope for this host-memory-limited run.

### IN-06: `jitpack-dry-run.sh` compares a locale-sorted list with a `C`-sorted one

**Files modified:** `scripts/jitpack-dry-run.sh`
**Commit:** 24c3371
**Applied fix:** `found` is now sorted with `LC_ALL=C sort`.

### IN-07: The sample `Item` data class prints user text in `toString()`

**Files modified:** `sample/.../undo/ItemStore.kt`
**Commit:** 9786efb
**Applied fix:** Overrode `toString()` to `Item(idLength=…, stamp=…)`, with a comment saying why. `equals`/`hashCode` and the other data-class behaviour are unchanged.

## Skipped Issues

### IN-04: `ActionEvent.toString()` redacts `heldRunId` but prints `parentRunId`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt:37-41`
**Reason:** Documented acceptable-skip: it needs a redaction-policy decision that I can't safely guess. The two options in the review move in opposite directions:
- Hiding `parentRunId` changes the `toString()` of a `:core` class that shipped in v1.0.
- Printing `heldRunId` reverses an explicit redaction choice that `HeldRunIdTest` pins (`heldRunId=set`).

Whether run ids can carry app-chosen user text (the `runIds` seam) decides which option is right. I could not add a "why" to the KDoc without knowing the original rationale. Settle it before the v1.1.0 tag together with the surface review.
**Original issue:** Both fields are engine-generated run ids of the same kind, yet one is hidden and the other is printed, with no stated reason.

---

_Fixed: 2026-10-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
