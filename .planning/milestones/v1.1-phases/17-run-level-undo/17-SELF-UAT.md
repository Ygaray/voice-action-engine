---
status: complete
result: all_pass
gate: 1
phase: 17-run-level-undo
source: [ROADMAP Phase 17 success criteria 1-4]
device: none (headless JVM library phase; no adb, no device, no emulator touched by this run. No live provider call, no key read, no provider spend. The device "Undo all (N)" UI is the Phase 19 Gate-1 leg and is not re-driven here)
apk: n/a (no APK built or installed; undo.jar md5 ab97ffdda67d743a713352f43df74619 @ 7bb0f77; HEAD 7bb0f777474be5107659c9fee90879dc2a212366 on gsd/phase-17-run-level-undo; code under core/ providers/ keystore/ sample/ undo/ config/ scripts/ gradle/ has 0 uncommitted changes)
run: 2026-10-07T00:53:00Z @ 7bb0f77
---

# Self-UAT Log: Phase 17 (Run-Level Undo), one log for the phase

**Target:** headless (Gradle JVM test harness). `:undo` alone (its own test classpath has no engine class), and `:sample`'s JVM unit tests that drive the real `commandPipeline` with the reference `UndoCommitSink` bridge behind `compositeSink`, an in-memory `ItemStore` + `ItemAdapter`, an alarm board compensator, `ScriptedStrategy`/`ScriptedGate`/`FakeMutation`/`RecordingCommitSink` doubles. Same headless handling as `16-SELF-UAT.md`. **Device: none.** No adb call, no live provider call, no key read. Every sample undo scenario runs inside `NoNetworkGuard.during`.
**Build identity:** HEAD `7bb0f777474be5107659c9fee90879dc2a212366`. `git status --short core providers keystore sample undo config scripts gradle` is empty (0 lines). `undo/build/libs/undo.jar` md5 `ab97ffdda67d743a713352f43df74619` (built this run).
**Pre-flight:** MemAvailable 13508836 kB before the first Gradle run and 13557028 kB before the second (above the 5 GiB stop line); swap 2047/2047 MB used. One Gradle invocation at a time, `--no-daemon`, workers 2, parallel off, in-process Kotlin, `-Xmx1536m`, `--offline`. No daemon stop, no process killed, no earlyoom kill, no retry. Heavy repo scripts (negative controls, JitPack dry run, API dump) were NOT run; their results are the ones recorded in `17-QUIET-WINDOW.md`.
**Unit suites (forced fresh):** `rm -rf undo/build/test-results core/build/test-results sample/build/test-results`, then
`./gradlew --offline --no-daemon --max-workers=2 --no-build-cache --console=plain :undo:test --rerun :core:test --rerun :sample:testDebugUnitTest --rerun :undo:detekt --rerun :undo:verifyModuleGraph --rerun :undo:verifyUndoZeroDeps --rerun :undo:metalavaCheckCompatibility --rerun` -> `BUILD SUCCESSFUL in 36s`, `EXIT=0`, the 7 named tasks executed, 0 FROM-CACHE. JUnit XML parsed by script (all files written 18:51 local, this run):
- `:undo` 15 suites, **113 tests, 0 failures, 0 errors, 0 skipped**
- `:core` 112 suites, **1110 tests, 0 failures, 0 errors, 0 skipped** (Phase 16 log: 1091; +19 = `CompositeSinkTest` 10 + `HeldRunIdTest` 8 + 1 more)
- `:sample` 23 suites, **160 tests, 0 failures, 0 errors, 0 skipped** (incl. `UndoEndToEndTest` 9, `UndoEndToEndPlanTest` 2, `UndoBridgeParityTest` 1, `DocSnippetsTest` 14)

**Publication check (run by me):** `./gradlew --offline --no-daemon ... :undo:generatePomFileForReleasePublication :undo:generateMetadataFileForReleasePublication :undo:jar` -> `BUILD SUCCESSFUL`, `EXIT=0`. `undo/build/publications/release/pom-default.xml`: groupId `com.github.Ygaray.voice-action-engine`, artifactId `voice-action-engine-undo`, only dependency `org.jetbrains.kotlin:kotlin-stdlib:2.3.20`. `module.json` `apiElements` / `runtimeElements`: `[org.jetbrains.kotlin:kotlin-stdlib]` only. `jitpack.yml` install list names `:undo:publishReleasePublicationToMavenLocal`.
**Other gates, each run by me:** `:undo:detekt` green (zero baseline); `:undo:verifyModuleGraph` green (`:undo` allowed edges = empty set; `:sample` must reach `:undo`); `:undo:verifyUndoZeroDeps` green (resolves compile and runtime classpaths, fails on any project component or non-stdlib module, and fails if it saw no stdlib, so it cannot pass vacuously); `:undo:metalavaCheckCompatibility` green (against the header-only seed `undo/api.txt`, so vacuous until the v1.1.0 cut fills it; expected pre-tag).
**Coverage/Nyquist:** each ROADMAP criterion is mapped below to named, passing tests, re-derived from the ROADMAP text, not from `17-VERIFICATION.md` or the `17-0x-SUMMARY.md` files.
**Seed/fixture integrity:** all fixtures are in-tree test doubles (`TestEntityStore`/`TestAdapter`/`Rig` in `:undo`; `ItemStore`/`ItemAdapter`/`UndoRig`/alarm board in `:sample`). Nothing external, nothing to restore.
**Prior verdicts audited, not relied on:** `17-VERIFICATION.md` (passed, 8/8, two deferred obligations), `17-QUIET-WINDOW.md`, `17-REVIEW-FIX.md`. Counts and claims below were re-observed this run; I read the load-bearing production code (`internal/Verifier.kt`, `internal/TicketState.kt`, `sample/.../undo/UndoCommitSink.kt`, `UndoResult.kt`, the `verifyModuleGraph` / `verifyUndoZeroDeps` task bodies in `gradle/invariants.gradle.kts`) and the load-bearing assertions of the S1-S8 tests. No contradiction found.

## Criteria

### 1. SC1: `:undo` publishes as `com.github.Ygaray.voice-action-engine:voice-action-engine-undo` with no dependency at all, not even `:core` (the module-graph gate proves it); a non-voice JVM test app journals and undoes a change using `:undo` alone
result: passed
- **Rung:** 3 (generated publication metadata + resolved-classpath gates) and 1 (standalone JVM tests)
- **Target:** headless JVM harness
- **Expected:** the published coordinate is exactly the E7 one; the POM and Gradle module metadata carry no dependency beyond the Kotlin stdlib; a classpath gate (not convention) fails on any project or library edge; on the `:undo` test classpath the engine is absent and a plain store is journaled and undone.
- **Arranged (seeded):** a `TestEntityStore` row `a = v0`, a `TestAdapter`.
- **Did (drove):** generated the release POM + `module.json` and read them; ran `:undo:verifyModuleGraph` and `:undo:verifyUndoZeroDeps` fresh; read both task bodies; ran `:undo:test` fresh.
- **Observed:** POM/module metadata as in the header (coordinate correct, stdlib only, no `voice-action-engine-core`, no coroutines). Both gates green; `verifyUndoZeroDeps` walks `resolutionResult.allComponents` and throws on a `ProjectComponentIdentifier` or any non-allowlisted module. `UndoStandaloneTest` 4/4: `theEngineIsNotOnTheClasspath` (`Class.forName("...core.CommandInput")` throws `ClassNotFoundException`), `oneEditIsUndoneThroughATicket` (capture -> write -> settle -> record -> `undoAll` is `Complete`, store back to `v0`, one restore call), `aSecondUndoIsAlreadyUndoneAndWritesNothing`, `anUnknownGroupIsRefusedWithAGroupLevelBlocker`. The clean-cache `:undoalone` external-consumer proof is the 17-10 quiet-window result (`17-QUIET-WINDOW.md`, exit 0, runtime classpath names only `voice-action-engine-undo`), not re-run here by instruction.
- **Evidence:** `undo/build/publications/release/{pom-default.xml,module.json}`; `undo/build/test-results/test/TEST-io.github.ygaray.voiceactionengine.undo.UndoStandaloneTest.xml`; `gradle/invariants.gradle.kts` (`verifyModuleGraph`, `verifyUndoZeroDeps`); `jitpack.yml`.

### 2. SC2: before a mutation, the before-state of every touched entity is captured through one app adapter per entity type (read, write back, re-insert if deleted); undo restores the snapshots in reverse order; out-of-database side effects are reversed through explicitly registered compensators
result: passed
- **Rung:** 1 (journal JVM tests + pipeline-level sample tests)
- **Target:** headless JVM harness
- **Expected:** `ticket.capture` reads the snapshot + fingerprint through the adapter registered for that type before the write; a created entity is removed, an edited one is written back with every field, a deleted one comes back with its id (and children); restores run newest action first; compensators run after all restores, newest first, once; an effect kind with no registered compensator refuses before anything is written.
- **Arranged (seeded):** store rows (incl. a parent with two children), two entity types with their own adapters, compensations of kind `alarm` armed on a board.
- **Did (drove):** forced re-run of `:undo:test` and `:sample:testDebugUnitTest`; read `TicketReader.before/created` (adapter per `key.type`, missing adapter -> `NO_ADAPTER` problem) and `PlanBuilder.build` (steps sorted by descending entry sequence, then descending capture order).
- **Observed (all passing, fresh XML):** `AdapterRoundTripTest` 4/4 (`aCreatedEntityIsDeleted`, `anEditedEntityIsWrittenBackWithEveryField`, `aDeletedParentComesBackWithItsIdAndItsChildren`, `twoAdaptersRestoreInReverseJournalOrderEachThroughItsOwnAdapter`); `CompensatorTest` 8/8 (`compensatorsRunAfterEveryRestoreNewestFirstAndOnce`, `anUnregisteredCompensationKindRefusesBeforeAnythingIsWritten`, `aRefusedUndoRunsNoCompensator`, `aCompensatorFaultIsPartialAndARetryRerunsOnlyThatCompensator`, ...); `ChainVerifyTest.twoEditsOfOneEntityRestoreOnceToTheEarliestValue`. Through the real pipeline: `UndoEndToEndTest.s8CompensatorsRunAfterTheRestoresInReverseOnce` asserts the exact event log `[restore:item-2, restore:item-1, disarm:item-2, disarm:item-1]`, board empty, store equals the seed; `s8AFailedCompensatorIsRetriedAlone` (Partial naming `compensator == "alarm"`, retry runs only `disarm:item-2`, third call `AlreadyUndone`, no extra events).
- **Evidence:** `undo/build/test-results/test/TEST-...{AdapterRoundTripTest,CompensatorTest,ChainVerifyTest}.xml`; `sample/build/test-results/testDebugUnitTest/TEST-...sample.undo.UndoEndToEndTest.xml`; `undo/.../internal/{TicketState,Verifier,Compensating}.kt`.

### 3. SC3: an unchanged-since-commit check runs before every restore; an entity changed after the command makes undo refuse loudly for it and never overwrite it; the result reports complete or lists exactly what it couldn't restore, never a silent partial
result: passed
- **Rung:** 1 (journal JVM tests + pipeline-level sample tests; falsified with edited / deleted / recreated / unreadable entities)
- **Target:** headless JVM harness
- **Expected:** `Verifier` reads the live fingerprint of every entity in scope before any write; live == earliest before -> nothing to write; live == newest after with an unbroken settled chain -> restore; anything else is a blocker, and one blocker refuses the whole undo with zero writes; a failure during the restore phase is `Partial` with a non-empty `notRestored` list naming entry, entity/compensator and reason; `Refused`/`Partial` reject empty lists; a lost race at write time is `CHANGED_SINCE`, never a clobber.
- **Arranged (seeded):** journaled edits, then an out-of-band edit / delete / recreate of one entity; adapters that throw on capture, on fingerprint, or on one restore.
- **Did (drove):** forced re-run; read `Verifier.verify/checkLive/checkChain` (read-only, guarded live read -> `UNVERIFIABLE` on fault, `CHANGED_SINCE` when live differs from the newest after) and the `UndoResult` invariants (`require(blockers.isNotEmpty())`).
- **Observed (all passing, fresh XML):** `RefuseLoudlyTest` 13/13 (`anEntityEditedSinceIsRefusedAndNothingIsWritten`, `anEntityDeletedSinceIsRefused`, `anEntityRecreatedSinceIsRefused`, `oneMovedOnEntityStopsTheWholeGroupSoTheOtherIsNotRestored`, `aLiveStateThatCannotBeReadIsUnverifiableAndNothingIsWritten`, `aCapturedTypeWithNoAdapterIsRefusedNamingTheEntity`, `blockersComeNewestEntryFirstThenEntityTypeAndId`, ...); `PartialRestoreTest` 6/6 (`aRestoreThatLosesTheRaceIsChangedSinceAndNothingIsClobbered`, `aFailureStopsItsComponentAndSkipsTheKeysAfterIt`, `aRetryRestoresOnlyTheRemainderAndNeverTouchesADoneKey`, `theFaultMessageAppearsNowhereInTheResult`); `ResultShapeTest.refusedAndPartialRejectEmptyLists`; `ChainVerifyTest.anExternalEditBetweenTwoOfTheCommandsOwnWritesBreaksTheChain`; `CancelMidUndoTest` 3/3; `IdempotentUndoTest` 3/3. Through the real pipeline: `UndoEndToEndTest.s2AnUnrelatedEditRefusesTheWholeUndoAndWritesNothing` (three writes + an alarm, then `store.edit("a","x")`: `Refused` with an `item`/`a` `CHANGED_SINCE` blocker, store equals the post-edit snapshot, compensator log empty, alarm still armed, group count still 4).
- **Evidence:** `undo/build/test-results/test/TEST-...{RefuseLoudlyTest,PartialRestoreTest,ResultShapeTest,ChainVerifyTest,CancelMidUndoTest,IdempotentUndoTest}.xml`; `UndoEndToEndTest.xml`; `undo/.../internal/Verifier.kt`, `UndoResult.kt`.

### 4. SC4: wired into the pipeline, every committed action of a command is journaled under its `runId`, so an app can offer "Undo all (N)" for the whole command, entangled actions included; grouping follows A18 (entity footprints decide which actions are isolated)
result: passed
- **Rung:** 1 (sample end-to-end tests through the real `commandPipeline`, plus `:core` seam tests and `:undo` isolation tests)
- **Target:** headless JVM harness
- **Expected:** with `compositeSink(UndoCommitSink(journal), appSink)`, every applied action of a run is recorded in the group keyed by its `runId` and `group(runId).count` equals N; a held action confirmed later via `commitHeld` joins the held run's group (via `ActionEvent.heldRunId`) and restores the moved state, not the proposal; a clarification reply is its own group carrying `parentGroupKey`; a throwing sibling sink never stops the bridge or changes the outcome; any journal gap (an applied action with no ticket, or one the bridge never saw) withholds "Undo all" rather than shrinking N; actions with disjoint footprints are isolated, shared ones are entangled and covered by undo-all.
- **Arranged (seeded):** `ItemStore` seeded with `a = v0`; scripted strategies submitting `CreateItem`/`RenameItem`/alarm mutations with tickets; a `FakeMutation` with no ticket; a `DroppingSink` that hides one event from the bridge; a throwing first sink; `ScriptedGate.sequence(null, Hold)`.
- **Did (drove):** `pipeline.execute(...)`, `pipeline.commitHeld(...)`, then `journal.undoAll(runId)` on the real pipeline, in the forced re-run; read `UndoCommitSink` (group = `heldRunId ?: runId`, records every `applied` action, withholds on a rejected record, `runClosed` with the applied positions).
- **Observed (all passing, fresh XML):** `UndoEndToEndTest` 9/9: `s1OneCommandsThreeWritesAreUndoneTogether` (3 commits, `group("run-1").count == 3`, not withheld, `undoAll` `Complete` with 3 restored, store equals the seed, second call `AlreadyUndone`); `s3AHeldRenameConfirmedLaterRestoresTheMovedStateNotTheProposedOne` (held -> count 0 and `pendingHeld == 1`; confirmed child `runId == run-2`, `heldRunId == run-1`, joins `run-1`, undo restores `v1` = the moved state); `s4AClarificationReplyIsItsOwnGroupAndKeepsItsParentGroupKey`; `s5AThrowingFirstSinkNeverStopsTheBridgeOrChangesTheOutcome` (`Completed`, `SINK_ERROR` in trace, group count 2); `s6a...WithoutATicketWithholdsTheGroup` and `s6b...TheBridgeNeverSawWithholdsTheGroupAtRunClose` (both `withheld`, `Refused` with `JOURNAL_WITHHELD`, store unchanged); `UndoEndToEndPlanTest` 2/2 (`s7aAPlanPartialIsUndoableWithinTheRun`, `s7bConfirmingTheHeldStepJoinsTheSameGroup`); `UndoBridgeParityTest` 1/1 (the documented bridge is byte-identical to the one the sample proves). `:core`: `HeldRunIdTest` 8/8, `CompositeSinkTest` 10/10. `:undo`: `IsolationTest` 9/9 (`separateEntitiesAreAllIsolatedAndOneCanBeUndoneAlone`, `anEntangledActionIsRefusedAloneAndUndoAllStillCoversIt` (`ENTANGLED` alone, `Complete` with 2 restored via undo-all), `aSharedTouchesOnlyKeyEntanglesToo`, `sharingIsTransitiveAndAnUnrelatedActionStaysIsolated`); `WithheldGroupTest` 19/19.
- **Evidence:** `sample/build/test-results/testDebugUnitTest/TEST-...sample.undo.{UndoEndToEndTest,UndoEndToEndPlanTest,UndoBridgeParityTest}.xml`; `core/build/test-results/test/TEST-...{HeldRunIdTest,CompositeSinkTest}.xml`; `undo/build/test-results/test/TEST-...{IsolationTest,WithheldGroupTest}.xml`; `sample/src/main/kotlin/.../sample/undo/UndoCommitSink.kt`.

## Summary

total: 4
passed: 4
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **No Gate-2 obligation registered.** All four Phase 17 criteria are verifiable headlessly and were verified headlessly; no physical or device step is deferred, so no fragment was written under `.planning/uat-pending/` and `HUMAN-UAT-PENDING.md` was not touched (same handling as `15-SELF-UAT.md` and `16-SELF-UAT.md`). The device "Undo all (N)" UI is Phase 19 Gate-1 scope. The two `deferred_obligations` in `17-VERIFICATION.md` are agent-owned (IN-04 redaction policy -> Phase 20; re-run of the JitPack dry run and live probe on the final tree -> Phase 19 gate run), not human Gate-2 steps.
- **Where the pipeline wiring lives (observation, not a FAIL).** The bridge that journals every committed action (`UndoCommitSink`) ships in `:sample` and in INTEGRATION.md, not in a published module: `:undo` may depend on nothing and `:core` may not depend on another hub, so the published pieces are the `:core` seams (`compositeSink`, `ActionEvent.heldRunId`) and the `:undo` journal, and each app copies the ~50-line bridge. `UndoBridgeParityTest` keeps the doc copy identical to the proven one. SC4 is met through the real pipeline with that bridge; whether to publish the bridge later is a product/packaging choice for the consumers (SB 178, CT), not a defect.
- **Metalava compat on `:undo` is vacuous pre-tag.** `undo/api.txt` is the header-only seed (1 line); it becomes meaningful when the v1.1.0 cut dumps it.
- **Not re-run by instruction:** `scripts/verify-negative-controls.sh`, `scripts/verify-api-dump.sh`, `scripts/jitpack-dry-run.sh` (quiet-window gated; results in `17-QUIET-WINDOW.md`, all exit 0). The `:providers` matrix and `:keystore` tasks were not re-run; Phase 17 touched neither.
- **Memory:** swap stayed full; MemAvailable stayed above 13 GB; no earlyoom kill, no retry. Pre-existing modified/untracked files (graphs, `.gsd/`, `state.json`, milestone docs, `milestone.lock`, stage-done markers) were left alone.
- No FAIL, no INFRA, no source code changed by this run.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 4 criteria PASS, re-observed on the JVM harness this run from a forced-fresh, uncached run on HEAD `7bb0f77` (undo 113, core 1110, sample 160 tests; 0 failures, 0 errors, 0 skipped; `:undo:detekt`, `:undo:verifyModuleGraph`, `:undo:verifyUndoZeroDeps`, `:undo:metalavaCheckCompatibility` green; generated POM/module metadata show coordinate `com.github.Ygaray.voice-action-engine:voice-action-engine-undo` with stdlib only). Gate-1 complete. No Gate-2 item is deferred for this phase, so no uat-pending fragment was registered.
