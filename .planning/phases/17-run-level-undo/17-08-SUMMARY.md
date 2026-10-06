---
phase: 17-run-level-undo
plan: 08
subsystem: sample-undo-bridge
status: complete
tags: [kotlin, sample, undo, commit-sink, composite-sink, end-to-end]

requires:
  - phase: 17-02
    provides: "ActionEvent.heldRunId and compositeSink"
  - phase: 17-07
    provides: "UndoJournal group view, withheld groups, runClosed, isolation"
provides:
  - "UndoCommitSink: the app-side pipeline -> journal bridge (reference wiring between undo-bridge:start and undo-bridge:end markers, 38 lines)"
  - "ItemStore, ItemAdapter, CreateItem/RenameItem/DeleteItem: stateful in-memory item store and its EntityAdapter and mutations, reusable by Phase 19"
  - "UndoRig test rig and S1-S8 end-to-end proofs through the real commandPipeline"
affects: [17-09, 17-10, 19]

actuals:
  tokens: 9100
  tasks: 3
  commits: 3
plan_head_before: c2aeada85829ee4d7610353d2265f99996c1d857
commits: 3

key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemStore.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemAdapter.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemMutations.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoCommitSink.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoTestRig.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoEndToEndTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoEndToEndPlanTest.kt
  modified: []

key-decisions:
  - "No :core or :undo main change: the bridge is :sample glue only (D-01). Every scenario passed against the engine and journal as released by 17-02 and 17-07, so no engine or journal defect was found"
  - "A confirmed child's parent group is parents[heldRunId] (what the held run continued), never the held run itself: commitHeld sets the child's parentRunId to the held run, which would otherwise make a group its own parent"
  - "The bridge resolves a reply's parent through groupOf(parentRunId), so a reply to a confirmed child continues that child's group, not the child's run id"
  - "Item ids are deterministic (item-1, item-2, ...) from a creation counter apart from the write stamp; seed(id, title) is an addition to the plan's store API so tests can start from item a"

requirements-completed: [UNDO-04, UNDO-02, UNDO-03]

coverage:
  - id: D1
    description: "S1 tracer: one command (create, rename, create-linked) behind compositeSink(bridge, recording) is journaled under its run id, count 3, undoAll Complete with 3 restored and the store equal to its seed (original stamp back), second undoAll AlreadyUndone"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s1OneCommandsThreeWritesAreUndoneTogether via :sample:testDebugUnitTest"
        status: pass
    human_judgment: false
  - id: D2
    description: "S2 an unrelated edit makes undoAll Refused naming item a with CHANGED_SINCE; nothing written, no restore or disarm call, alarm still armed"
    requirement: UNDO-03
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s2AnUnrelatedEditRefusesTheWholeUndoAndWritesNothing"
        status: pass
    human_judgment: false
  - id: D3
    description: "S3 a held rename confirmed after the item moved on is captured at apply: undo restores the moved state; pendingHeld is 1 while held (group not counted) and 0 once confirmed; the child event carries heldRunId"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s3AHeldRenameConfirmedLaterRestoresTheMovedStateNotTheProposedOne"
        status: pass
    human_judgment: false
  - id: D4
    description: "S4 a clarification reply is its own group with parentGroupKey run-p; the parent group's count and revision are untouched"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s4AClarificationReplyIsItsOwnGroupAndKeepsItsParentGroupKey"
        status: pass
    human_judgment: false
  - id: D5
    description: "S5 a throwing first sink never stops the bridge: all applied actions journaled, outcome Completed, sink_error in the trace, each write applied once"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s5AThrowingFirstSinkNeverStopsTheBridgeOrChangesTheOutcome"
        status: pass
    human_judgment: false
  - id: D6
    description: "S6a/S6b an applied action with no ticket, and an action the bridge never saw, withhold the group: undoAll Refused JOURNAL_WITHHELD with the store untouched"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s6aAnAppliedActionWithoutATicketWithholdsTheGroup, s6bAnActionTheBridgeNeverSawWithholdsTheGroupAtRunClose"
        status: pass
    human_judgment: false
  - id: D7
    description: "S7 a real PlanThenExecute run commits two steps then holds the third (positions 0,1,2, remainingStepIds [s4], one provider call): count 2 with pendingHeld 1, undoAll restores both; after commitHeld the group holds 3 and undoAll restores all three"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "UndoEndToEndPlanTest (2 tests)"
        status: pass
    human_judgment: false
  - id: D8
    description: "S8 compensators (alarm) run after both item restores, newest first, once; a failing one gives Partial COMPENSATOR_FAILED with both items already restored; the retry runs only that compensator and returns Complete; a third call is AlreadyUndone"
    requirement: UNDO-02
    verification:
      - kind: integration
        ref: "UndoEndToEndTest.s8CompensatorsRunAfterTheRestoresInReverseOnce, s8AFailedCompensatorIsRetriedAlone"
        status: pass
    human_judgment: false
  - id: D9
    description: "Plan-end gate: all *Undo* sample tests (11) plus :undo:verifyModuleGraph and :core:verifyModuleGraph; the sample commits touch only sample/ paths"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "./gradlew :sample:testDebugUnitTest --tests '*Undo*' :undo:verifyModuleGraph :core:verifyModuleGraph (no failure output, 11/11 tests pass)"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-06
---

# Phase 17 Plan 08: Sample undo bridge end to end Summary

**The app-side `UndoCommitSink` behind `compositeSink(bridge, recording)`, with a stateful `ItemStore`, its adapter and three mutations, proven through the real pipeline for 8 grouping and failure scenarios (11 tests), with no engine or journal change.**

## Accomplishments
- `UndoCommitSink` (38 lines between `// undo-bridge:start` and `// undo-bridge:end`): groups by `event.heldRunId ?: event.runId`, journals each applied action with its ticket (`action.context as? UndoTicket`), withholds the group on an `IllegalArgumentException`, tracks unconfirmed held proposals apart from N (`pendingHeld`, `discarded`), and calls `journal.runClosed` with the applied positions.
- The item store keeps one write stamp per item and restores it with `restoreIf`, so a restored item has the very fingerprint it had before the command (S1 compares whole snapshots including stamps).
- `UndoRig` wires store, logging adapter, alarm board, journal, bridge, recording sink and a one-tier pipeline; tests choose the sink list, so S5 and S6b put a throwing sink or a dropping wrapper beside the bridge.
- S7 uses the public `PlanThenExecuteStrategy` builder and a `FakeAiProvider` `submit_plan` answer: distinct positions 0, 1, 2 hold through the pipeline, the held third step is excluded from N until confirmed, and the confirmed child joins the same group.

## Task Commits

1. **Task 1 (tracer): bridge, store, adapter, mutations, S1** - `c4c32f2` (feat)
2. **Task 2: S2-S6b and the rig** - `85ee5a7` (test)
3. **Task 3: S7 plan partial, S8 compensators** - `8c8fbb9` (test)

## Deviations from Plan

None to scope or behavior. Small notes, all inside the plan's file list:
- `ItemStore.seed(id, title, parentId)` was added so tests start from item `a`; the plan's method list did not name it.
- `Item` is a `data class` (the plan said an immutable class) so whole-store snapshots compare with `assertEquals`.
- The rig's `undoRig(gate, vararg sinksBefore)` became a class, `UndoRig`, with `pipeline(gate, vararg steps, sink = ..., runIds = ...)`: a tier's steps cannot share a vararg with the sink list. S1 builds its `commandPipeline` by hand so the documented `compositeSink(bridge, recording)` wiring is visible in the tracer.
- S2's command also arms an alarm (4 actions), so "no compensator ran" is a real assertion rather than a vacuous one.
- One test mistake caught during Task 3: a constant `runIds = { "run-1" }` gave the `commitHeld` child the same run id as the original run, which repeats a (run, position) pair and (correctly) leaves the confirmed action out of the group. The fix was a counter, no product change.

## Issues Encountered
- Inside `UndoJournal { ... }`, `store` resolves to the Builder's `JournalStore?` property, not the rig's `ItemStore`; the rig refers to it as `this@UndoRig.store`.

## Notes for 17-09 (docs)
- The bridge's three maps (`groups`, `parents`, `pending`) are keyed by run id and not pruned; the bridge comment says so. A consumer that runs for days should prune them with the journal's own limits.
- The bridge needs `journal` listed first in `compositeSink`; the sample comment says why.

## Verification
- `:sample:testDebugUnitTest --tests '*Undo*' :undo:verifyModuleGraph :core:verifyModuleGraph` (one low-memory invocation): no failure output; 9 tests in `UndoEndToEndTest` and 2 in `UndoEndToEndPlanTest`, 0 failures.
- Acceptance greps: one `// undo-bridge:start`, one `// undo-bridge:end`, one `event.heldRunId ?: event.runId`, `compositeSink(bridge` present in `UndoEndToEndTest`, 6 `fun s(2..6b)` tests, `parentGroupKey` 1, `SINK_ERROR` 1, `JOURNAL_WITHHELD` 2, `PlanThenExecuteStrategy(` 1, `remainingStepIds` 1, `pendingHeld` 2, `COMPENSATOR_FAILED` 1.
- The Task 1 commit touches only `sample/` paths; no `:core` or `:undo` main file changed in this plan. No provider call, device, key or network was used (`NoNetworkGuard` around every scenario).

## Known Stubs
None.

## Threat Flags
None. T-17-22 (S6a, S6b), T-17-05 (S5), T-17-14 (S2), T-17-27 (S3), T-17-28 (S4) are each covered by a passing end-to-end test.

## Self-Check: PASSED
