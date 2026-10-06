# Phase 17 frozen-surface review (UNDO-01..04, D-01 .. D-10; one-way at the v1.1.0 tag)

Reviewed on branch `gsd/phase-17-run-level-undo` after plans 17-01 .. 17-08, against the real Metalava dumps of every
manifest module produced by `scripts/api-dump-isolated.sh` in an isolated copy of the working tree (`API DUMP ISOLATED
OK core=1956 providers=121 keystore=76 undo=191`). The dumps live in a scratch directory outside the repository; no
`api.txt` was created or edited. The committed `core/api.txt`, `providers/api.txt` and `keystore/api.txt` are
byte-identical to commit 147a959, and `undo/api.txt` is still the header-only seed (`// Signature format: 4.0`, D-09), so
every Phase 17 addition reaches an `api.txt` only at the v1.1.0 cut. Until then the dumps below are the proof.

Question asked of every member: what will a consumer ask next, and can it be added without removing anything?

## Dumps

| Module | Real tree `api.txt` (lines) | Isolated dump (lines) | Reading |
|---|---|---|---|
| `core` | 1698 | 1956 | 258 added lines, 0 removed (`comm -23` of the sorted files is 0) |
| `providers` | 121 | 121 | identical |
| `keystore` | 67 | 76 | 9 added lines: `DelicateKeyAccess` and `KeyAccess` (opt-in test seam, Phase 12), not Phase 17 |
| `undo` | 1 (seed) | 191 | the whole new surface |

The 258 `core` lines come from Phases 12, 14, 15, 16 and 17; only the two Phase 17 additions are listed in the next
sections.

## :undo surface

Everything below is `public` in the dump, grouped by type. `kotlin.coroutines.Continuation` parameters (the suspend
marker) are left out of the method lists.

| Type | Kind in the dump | Public members |
|---|---|---|
| `UndoJournal` | `final class` | `newTicket()`, `record(groupKey, parentGroupKey, entry, failed, ticket)`, `runClosed(groupKey, runId, appliedPositions)`, `withhold(groupKey)`, `group(groupKey)`, `undoAll(groupKey)`, `undoEntry(groupKey, entry)`, `storeFaults`, `toString()`, `Companion` |
| `UndoJournal.Builder` | `static final class` | `adapter(a)`, `compensator(kind, c)`, vars `maxGroups`, `maxAgeMillis`, `store`, `clock` |
| `UndoJournal.Companion` | `static final class` | `invoke(block)` |
| `UndoTicket` | `final class` | `capture`, `settle`, `created`, `touches`, `compensate`, `nothingWritten`, `toString()` |
| `EntityAdapter` | `interface` | `entityType`, `read`, `fingerprint`, `restoreIf` |
| `Compensator` | `fun interface` | `compensate(payload)` |
| `EntryRef` | `final class` | `EntryRef(runId, position, toolName)`, getters, `equals`, `hashCode`, `toString` |
| `EntityKey` | `final class` | `type`, `id`, `equals`, `hashCode`, `toString` (internal constructor) |
| `UndoResult` | `abstract sealed exhaustive class` | `code`; nested `Complete`, `Refused`, `Partial`, `AlreadyUndone` (exactly four) |
| `UndoResult.Complete` | `static final class` | `restored`, `code`, `toString` |
| `UndoResult.Refused` | `static final class` | `blockers`, `code`, `toString` |
| `UndoResult.Partial` | `static final class` | `restored`, `notRestored`, `code`, `toString` |
| `UndoResult.AlreadyUndone` | `static final class` | `code`, `toString` |
| `Blocker` | `final class` | `entry`, `entity`, `reason`, `toString` |
| `NotRestored` | `final class` | `entry`, `entity`, `compensator`, `reason`, `errorClass`, `toString` |
| `UndoReason` | `@JvmInline final value class` | `value`, `toString`; `Companion` holds twelve constants |
| `UndoGroup` | `final class` | `groupKey`, `parentGroupKey`, `revision`, `withheld`, `entries`, `pending`, `isolated`, `count`, `toString` |
| `JournalStore` | `interface` | `save(group)`, `delete(groupKey)` |

Confirmed from the dump:

- `UndoResult` is printed as `abstract sealed exhaustive class UndoResult` with exactly four nested members.
- `UndoReason` is a value class (`@kotlin.jvm.JvmInline public final value class`); its twelve constants are
  `property` lines of the companion, so a later version adds a constant without removing anything.
- No `enum`, no `copy` and no `componentN` method (the grep over the dump counts 0), so no data-shaped class exists.
- None of the internal names (`JournalState`, `Verifier`, `Restorer`, `Footprints`, `Compensating`, `Retention`,
  `StoreMirror`, `Guard`) appears as a class in the dump (count 0).
- Every constructor that a consumer must not call is internal (absent from the dump), apart from `EntryRef`, which a
  bridge builds.

## :core additions

Phase 17 adds exactly two things to `core`:

1. `ActionEvent.heldRunId`: in the dump's `ActionEvent` block, two added lines
   (`method @InaccessibleFromKotlin public String? getHeldRunId();` and `property public String? heldRunId;`) and no
   removed or changed line. The block diff, committed `core/api.txt` against the dump (the verify command's own diff):

```
=== ActionEvent
2a3
>     method @InaccessibleFromKotlin public String? getHeldRunId();
6a8
>     property public String? heldRunId;
```

2. A new `CompositeSinkKt` block holding `public static CommitSink compositeSink(CommitSink... sinks)`.

Whole-file check: `comm -23 <(sort -u core/api.txt) <(sort -u core.api.sig)` prints 0 lines, so no line of the committed
snapshot is missing from the dump. There is therefore no Phase 20 gate-12 risk from `core` to list.

## Frozen names

These become one-way at v1.1.0. D-08 (the closed result set, DB-first then compensators) is **one-way**.

| Group | Frozen names and wire values |
|---|---|
| `UndoResult` members and `code` | `Complete` (`complete`), `Refused` (`refused`), `Partial` (`partial`), `AlreadyUndone` (`already_undone`); the set is closed |
| `UndoReason` values (open set) | `changed_since`, `chain_broken`, `unverifiable`, `entangled`, `journal_withheld`, `unknown_group`, `unknown_entry`, `in_progress`, `no_adapter`, `restore_failed`, `compensator_failed`, `skipped_after_failure` |
| Ticket methods | `capture`, `settle`, `created`, `touches`, `compensate(kind, payload)`, `nothingWritten` |
| Adapter | `entityType`, `read(id)`, `fingerprint(id)`, `restoreIf(id, expectedFingerprint, snapshot)`; `Compensator.compensate(payload)` |
| Store | `save(UndoGroup)`, `delete(groupKey)` (no `loadAll`); `UndoJournal.storeFaults` |
| Journal methods | `newTicket`, `record`, `runClosed`, `withhold`, `group`, `undoAll`, `undoEntry` |
| Builder | `adapter(...)`, `compensator(kind, ...)`, `maxGroups = 50`, `maxAgeMillis = 3_600_000`, `store = null`, `clock` |
| `UndoGroup` | `groupKey`, `parentGroupKey`, `revision`, `withheld`, `entries`, `pending`, `isolated`, `count` |
| Core seams | `compositeSink(vararg CommitSink)`, `ActionEvent.heldRunId` |
| Coordinate | `voice-action-engine-undo` (explicit artifact id), first tag v1.1.0, no dependency at all |

Evolution notes: `EntityAdapter` is a plain interface, so adding an abstract method later would break implementors; any
future member must be a defaulted method. `JournalStore` is the same, and its `loadAll` is such an addition. `Compensator`
is a `fun interface`, so a second abstract method would break every lambda; richer input would need a new interface. `UndoReason` gains constants freely.

## Resolutions

| Item | Resolution |
|---|---|
| Q1 `compositeSink` fault policy | Run every child, then throw one fixed-text, counts-only exception; the pipeline records `sink_error` and the outcome is unchanged. A child's own exception is never forwarded. Accepted by the orchestrator (17-02) |
| Q2 single-entry undo | `undoEntry` works for isolated entries only; an entangled entry is `Refused(ENTANGLED)` with nothing written and no expansion to its component |
| Q3 seed | `undo/api.txt` stays the header-only seed until the Phase 20 cut (D-09) |
| Q4 `JournalStore` | A write-through mirror (`save`, `delete`) carrying keys, references, flags and counts. No snapshots, so it cannot restore the journal; `loadAll` can be added later as a defaulted method |
| Q5 bridge duplication | `UndoBridgeParityTest` compares the compiled doc region with the sample's main bridge; `verify-docs-coverage.sh` stays single-file (no clash with Phase 19) |
| Q6 N and no-op entries | An applied entry that called `nothingWritten()`, declared nothing, or reported an error counts in N and in `Complete.restored` |
| A3 adapter | Non-generic `EntityAdapter` with an `Any?` snapshot; the app casts inside its own adapter |
| A6 suppressions | `:undo` carries two: the file-level `TooGenericExceptionCaught` in `internal/Guard.kt` (the one never-throw collapse, the module's counterpart of the one in `:core`) and a one-line `MaxLineLength` on `UndoJournal.record`, kept on one line because an earlier plan's contract check greps the exact signature. See OI-5 |
| A8 default | An applied action whose ticket was never settled refuses at undo with `UNVERIFIABLE` rather than assuming nothing was written; `nothingWritten()` is the escape hatch |
| D-03 / D-04 | Capture happens inside `apply`; the footprint is declared on the ticket there, never derived from tool arguments |
| D-05 | Every applied action is journaled; a journal fault or a missed action withholds "Undo all" loudly, never N-1 |
| D-07 | Whole-group verify-then-restore through the atomic `restoreIf`; a refusal writes nothing |

## A18

Answers row 8 (final, orchestrator + SB + CT): app glue + sample + doc snippet + additive `compositeSink`; no sixth
module, and SB's condition that `compositeSink` isolates a throwing child sink from its siblings and from the pipeline.
Confirmed met: `compositeSink` exists in the `core` dump; the sample bridge behind `compositeSink(bridge, recording)`
is proven end to end through the real `commandPipeline` (S1 to S8, eleven tests, 17-08); a throwing first sink does
not stop the bridge or change the outcome (S5); and the documented bridge is the compiled, executed, parity-tested
bridge (`DocSnippetsTest` region `undo-bridge`, `UndoBridgeParityTest`, check C06). This closes D-01's "confirm with the
orchestrator".

## Handoff

Phase 19:

- Generalize C01 and C20 in `scripts/verify-docs-coverage.sh` to the manifest (they still name three modules), and write
  the README install block and pin. This phase left `README.md` untouched on purpose.
- The clean-tree path list in `run-sample-gate1.sh` gains `undo`.
- The undo-all leg of the Gate-1 run reuses `ItemStore`, `ItemAdapter` and `UndoCommitSink` from
  `sample/src/main/kotlin/.../sample/undo/`.

Phase 20:

- Gate 10 (`api-dump`): regenerate `undo/api.txt` at the cut; until then it differs from the fresh dump for `undo`.
- Gate 12 (`api-check`): add the "new in this release, no baseline" branch for `undo`, or the loop goes red with "is
  not in the previous release".
- Selftest step 4 dumps and commits `undo/api.txt` in its sandbox and goes red until that branch lands.
- A freeze-confirmation row for the undo surface (the Frozen names table above is its source).

## Open items for orchestrator

- **OI-1 `JournalStore` as a mirror.** The seam is `save` and `delete` only; it cannot restore the journal, and the
  journal does not survive process death. Confirm with SB 178 and CT that a mirror is enough for v1.1.0 (a defaulted
  `loadAll` stays addable later).
- **OI-2 `compositeSink` fault policy.** The composite rethrows one fixed-text exception after every child ran, so the
  trace carries `sink_error` while the outcome and the applies are unchanged. Confirm that SB's wording "isolates from
  the pipeline" is read as "the outcome is unaffected" (the orchestrator accepted this in 17-02).
- **OI-3 defaults.** 50 groups and one hour of idle time. Confirm these suit "Undo all" in SB 178 and CT; both are
  Builder vars and a consumer can change them.
- **OI-4 N counts no-op entries.** An applied action that wrote nothing, or reported an error, is in N and in
  `Complete.restored`. Confirm the UI copy "Undo all (N)" is acceptable when some of the N restore nothing.
- **OI-5 suppressions.** `:undo` has the one file-level `TooGenericExceptionCaught` suppression (A6) and a one-line
  `MaxLineLength` suppression on `record`. Ask whether the second should be removed (wrap the signature and relax the
  earlier contract grep) in a later patch; removing it changes no API.
- **OI-6 bridge hygiene for consumers.** The bridge's three maps are never pruned and the journal must be listed first
  in `compositeSink`. Both are stated in the sample comment and INTEGRATION.md section 11.
- **OI-7 heavy gates.** The full negative-control suite, `scripts/verify-api-dump.sh` and the clean-cache
  `scripts/jitpack-dry-run.sh` (with the `:undoalone` consumer) have not run. They wait for the quiet window requested in
  `17-QUIET-WINDOW.md` (plan 17-10); the fallback is a deferred obligation owned by Phase 19's gate run.
- **OI-8 keystore dump.** The isolated `keystore` dump holds 9 lines the committed `keystore/api.txt` lacks
  (`DelicateKeyAccess`, `KeyAccess`, the opt-in constructor). They pre-date Phase 17 (Phase 12); they only matter to
  Phase 20's regenerate and compat gates.
