# Phase 17: Run-Level Undo - Research

**Researched:** 2026-10-06
**Domain:** a dependency-free Kotlin/JVM undo journal (`:undo`), one additive `:core` seam pair (`ActionEvent.heldRunId`, `compositeSink`), and the "add a published module" build plumbing
**Confidence:** HIGH for the `:core` seams and the plumbing edit sites (read from the repo this session); MEDIUM for the `:undo` API shape (a design, not a verified fact; names are indicative, semantics are binding); MEDIUM for Metalava behavior on a header-only baseline (partly verified, see A1)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [bridge]:** App-side glue + the additive `compositeSink` helper; `:undo` has zero edges, `:core` gains none. Confirm with the orchestrator that this satisfies A18's "pipeline integrates :undo" (else SB and CT each hand-write the seal/group logic). _(source: ai-auto)_
  - **Consumer condition (binding):** FINAL (orchestrator + SB + CT, answer #8): app glue + sample + doc snippet + additive `compositeSink`; no 6th module. SB condition: `compositeSink` isolates a throwing child sink from its siblings and from the pipeline. A18 is met by the seam + an end-to-end sample proof.
- **D-02 [grouping]:** Additive `ActionEvent.heldRunId` (internal ctor, safe), lands in Phase 17; clarification replies form their own group. N counts applied actions (COMMITTED + IS_ERROR applied=true); pending held shown as pending, not counted. Confirm with SB 178. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #5): keep `parentRunId` on the clarification-reply group so a combined undo stays possible later.
- **D-03 [capture]:** Capture inside apply (held changes commit against state that moved on; gate-time capture restores stale data). SB's display-only PreMutationSnapshot can't be ported as-is as the memento. _(source: ai-auto)_
- **D-04 [footprint]:** App-declared at seal (SB's footprint has snapshot-only keys; created ids exist only after apply). _(source: ai-auto)_
- **D-05 [journal-scope]:** Every applied action; journal failure → "Undo all" withheld loudly, never silently N-1. _(source: ai-auto)_
- **D-06 [persistence]:** In-memory + optional store interface, documented "does not survive process death" (YAT UndoHistoryStore is session-scoped too). _(source: ai-auto)_
- **D-07 [refuse]:** Atomic restoreIf + whole-group verify-then-restore (maps to YAT's `Refused` = nothing written). Single isolated action checks only its own footprint. _(source: ai-auto)_
- **D-08 [result]:** The closed set above (frozen at the tag, so get it right now), DB-first then compensators. Decide YAT `changedItem` mapping (first blocker vs joined list). — **Reversibility:** one-way — the undo result closed set is frozen at the v1.1.0 tag _(source: ai-auto)_
- **D-09 [api-seed]:** Header-only seed (gate stays strict; additions stay compat-green); update release-cut.sh:315's api.txt path allowlist and the stale "real tree never receives an api.txt" script headers. Verify Metalava 0.5.1 accepts a header-only baseline on kotlin.jvm in the scaffold plan. _(source: ai-auto)_
- **D-10 [plumbing]:** Manifest + consistency gate, landed with the `:undo` scaffold; Phases 18 and 13-if-green rebase onto it. `:undo` = kotlin.jvm, JVM 11, explicitApi, stdlib only (no coroutines), explicit artifactId `voice-action-engine-undo`. _(source: ai-auto)_

**Runtime Decisions (appended to CONTEXT.md 2026-10-06, binding):** [grouping] refreshed (dependency P12 complete): additive `ActionEvent.heldRunId`, a new property on the existing internal-constructor ActionEvent (commit/CommitSink.kt:29); it lands in Phase 17. Clarification replies form their own group and that group KEEPS `parentRunId` (SB binding, R-v1.1 answers row 5). N counts applied actions (`ActionKind.COMMITTED` plus `ActionKind.IS_ERROR` with `applied=true`; CommitCoordinator.kt:170 records IS_ERROR `applied=false` for un-applied failures, which are NOT counted). A pending held action is shown as pending, not counted. SB 178 confirmed OK (row 5). Phase 15 ruling: a PlanThenExecute partial after a hold keeps its committed steps, each with a distinct ordinal, and those are undoable within the run.

### Claude's Discretion

Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UNDO-01 | a `:undo` module (`voice-action-engine-undo`) depends on nothing, not even `:core`, and a non-voice app can use it alone | Module recipe (copy `providers/build.gradle.kts`, drop every dependency), `allowedEdges[":undo"]=∅`, new `verifyUndoZeroDeps` classpath allowlist, stdlib-only test helper, a `:undoalone` consumer in `jitpack-consumer-probe.sh` that asserts `:core` is absent from the resolved graph (see Module plumbing, Validation Architecture) |
| UNDO-02 | a journal/memento design with per-entity adapters (read, write back, re-insert if deleted) and explicit compensators for out-of-DB side effects | `EntityAdapter` + `UndoTicket` + `Compensator` design, capture-inside-apply protocol (D-03), footprint declared at settle (D-04) |
| UNDO-03 | unchanged-since-commit check before every restore; changed entity makes undo refuse loudly and never clobber; complete or exact list of what could not be restored | Fingerprint model (before/after per entity), whole-group verify-then-`restoreIf` (D-07), closed result set `Complete`/`Refused`/`Partial`/`AlreadyUndone` (D-08) |
| UNDO-04 | the pipeline journals each command's committed actions by `runId`, so an app can offer "Undo all (N)" for a whole command, entangled actions included | App-side bridge `CommitSink` + `compositeSink` + `ActionEvent.heldRunId` (D-01, D-02), integrity seal at `onRunClosed`, `:sample` end-to-end JVM proof, compiled doc snippet |
</phase_requirements>

## Summary

Phase 17 is three loosely coupled pieces. (1) A new, dependency-free `:undo` module (UNDO-01..03): a journal of per-action tickets, one app adapter per entity type, idempotent compensators for out-of-DB effects, and a refuse-loudly undo that verifies the *whole group* before writing anything. (2) Two additive `:core` seams for UNDO-04: `ActionEvent.heldRunId` (the only way a bridge can tell a `commitHeld` child run from a clarification-reply run, since both carry `parentRunId`) and `compositeSink(...)` (the pipeline takes exactly one sink). `:core` gains no edge to `:undo`; the bridge is ~40 lines of app glue shipped in `:sample` and as a compiled doc snippet (final, orchestrator answer #8). (3) The shared "add a published module" plumbing, which Phase 17 owns and Phases 18 and 13-if-green rebase onto: a module manifest plus a consistency gate, new `allowedEdges`/zero-dependency gates, jitpack.yml, a header-only `undo/api.txt` seed, and the ~15 hard-coded `core providers keystore` lists in `scripts/`.

The repo already supplies every fact the bridge needs. `ExecutedAction.context` is read **once, before apply** (`ApplyStep.kt:36`) and handed to the sink **by identity**, so an app can put a mutable ticket in `PendingMutation.context`, fill it inside `apply()`, and read the same object in `onAction`: this makes D-03 (capture inside apply) and D-04 (footprint declared after apply) implementable with no engine change. `commitHeld` opens a child run with a fresh ledger (positions restart at 0) and `parentRunId = held.runId` (`HeldCommit.kt:58-62`), so a journal entry must be keyed `(runId, position)` and ordered by a journal-assigned sequence, never by `position` alone.

The two one-way doors are the **result type** (D-08) and the **adapter/ticket method set**. Recommendation for D-08: keep exactly the four members `Complete`/`Refused`/`Partial`/`AlreadyUndone`, carry the open-set part in an extensible `UndoReason` value class (the `ActionKind` pattern), and express group-level refusals (unknown/evicted group, withheld journal, undo already running) as a `Blocker` with a null entity inside `Refused`, so no fifth member is needed.

**Primary recommendation:** Land in this order: (a) module plumbing + `:undo` scaffold with the header-only seed and its Metalava proof, (b) `:core` seams `heldRunId` + `compositeSink` with tests (independent of `:undo`, can run in parallel with (a)), (c) the `:undo` model/journal/verify-restore, (d) the `:sample` bridge + end-to-end proof + doc snippet + docs. Treat every Gradle run as a host-memory risk (swap is full, 1.8 GB free this session): run single tasks with the single-use-daemon flags, and keep the heavy clean-cache probe/dry-run in a non-autonomous quiet-window plan.

## Project Constraints (from CLAUDE.md)

Directives from `./.claude/CLAUDE.md` that the plan must honor (treated as locked):

- **Dependency structure:** `:core` depends on no other hub and has no HTTP dependency (L7, A7). `:core` must not gain an edge to `:undo`; `verifyCoreDependencyAllowlist` stays unchanged.
- **API evolution:** public API grows strictly additively once tagged (§11). The result closed set is frozen at `v1.1.0`.
- **Domain-free:** library names no note/card/food; app knowledge enters through seams (the `scanBannedConstructs` rawRules deny-list applies to `:undo` main as to every module).
- **Quality:** detekt zero baseline on library modules (`buildUponDefaultConfig = true`, `maxIssues: 0`, no baseline); two-gate UAT where device-verifiable; mostly JVM-tested.
- **Secrets:** API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`. For `:undo` this extends to **entity ids, snapshots, fingerprints and compensator payloads** (user data): redacted `toString`, class-name-only error reports.
- **Process:** contract changes only via §10 amendments through the control plane; tag cuts are agent-owned under A12. Phase 17 cuts no tag.
- **Stack pins:** Kotlin 2.3.20, JVM 11 bytecode (`-Xjdk-release=11`), explicit API, JUnit 4.13.2, detekt 1.23.8 plain `detekt` task (syntax-only), Metalava 0.5.1, no `runCatching`, no `println`, no DI imports, no `jvmToolchain()`.
- **GSD workflow:** no repo edits outside a GSD flow (planning only here).
- Global rule: host memory is tight; do not run Gradle in research. Anything that needs the TESTER device or live spend must be a non-autonomous checkpoint (none is expected in this phase: undo is offline and device proof is Phase 19).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Before-state capture, post-commit fingerprint, footprint | App (inside `PendingMutation.apply`, via `UndoTicket`) | `:undo` (ticket + adapters) | Only the app can read/hash its entities inside its own transaction; `:core` cannot know entities |
| Entity read / `restoreIf` / re-insert | App adapter (one per entity type) | `:undo` (calls it) | Storage is app domain; `:undo` is storage-agnostic |
| Journal state, grouping, isolation, verify-then-restore, result | `:undo` | — | Reusable and engine-independent (A18) |
| Out-of-DB reversal (alarm, notification, file) | App-registered `Compensator` | `:undo` (ordering, retry bookkeeping) | Effects live outside the DB; must be idempotent |
| Pipeline → journal bridge (`CommitSink`) | App glue (`:sample` reference + doc snippet) | `:core` (`compositeSink`, `heldRunId`) | `:undo` can't see `:core`, `:core` must not see `:undo` (final, row 8) |
| Group key (`heldRunId ?: runId`) emission | `:core` (`ActionEvent.heldRunId`) | — | Engine alone knows a child run resolves a held proposal |
| Sink fan-out and fault isolation | `:core` (`compositeSink`) | — | Pipeline takes one sink; isolation must be engine-grade |
| "Undo all (N)" affordance and pending-held display | App UI (YAT v2.4.0 affordance) | `:undo` (`count`, `isolated`) | Rendering is app/UX; the journal supplies facts |
| Module list single source of truth + gates | Build tooling (`scripts/`, `gradle/invariants.gradle.kts`) | — | Phase 17 owns the plumbing; 18/13 rebase |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib (via KGP) | 2.3.20 | the only runtime dependency of `:undo` (`suspend`, `kotlin.coroutines`, `kotlin.coroutines.cancellation.CancellationException`) | Same pin as every module [VERIFIED: gradle/libs.versions.toml `kotlin = "2.3.20"`] |
| JVM `java.util.concurrent`, `synchronized` | JDK 11 API surface | journal state, atomic transitions | `-Xjdk-release=11` is enforced in every module build [VERIFIED: providers/build.gradle.kts:22 `freeCompilerArgs.add("-Xjdk-release=11")`] |
| JUnit | 4.13.2 | tests | Ecosystem standard, already in the catalog [VERIFIED: gradle/libs.versions.toml `junit = "4.13.2"`] |

No new library, no new catalog entry, no new plugin. `:undo` applies the same four plugins as `:core` minus `java-test-fixtures`: `kotlin.jvm`, `maven-publish`, `detekt`, `metalava`.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlinx-coroutines-test` | 1.11.0 | `runTest` in tests | **Avoid in `:undo` tests**: write a 10-line stdlib-only `runSuspending { }` helper (`startCoroutine` + `CountDownLatch`) so the UNDO-01 "alone" proof also holds for the test classpath. Allowed in `:core`/`:sample` tests [ASSUMED: helper is straightforward with `kotlin.coroutines.startCoroutine`] |
| `testFixtures(project(":core"))` | in-repo | `RecordingCommitSink`, `ScriptedGate`, `ScriptedStrategy`, `FakeMutation` | `:sample` and `:core` tests only; **never** `:undo` (`verifyModuleGraph` counts every configuration, so it would trip the zero-edge gate) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| stdlib `synchronized` for journal state | `kotlinx-coroutines` `Mutex` | Breaks "depends on nothing" for no gain: never hold a lock across a suspend call; undo concurrency is a per-group CAS flag instead |
| non-generic `EntityAdapter` (snapshot is `Any?`) | `EntityAdapter<S>` | Generic gives app type-safety but forces an unchecked cast (and a second `@Suppress`) inside `:undo`. Prescribed: non-generic, the app casts in its own adapter; revisit only if the planner finds a cast-free erasure |
| `Refused` for group-level failures | a fifth `Unavailable` result member | Fifth member is cleaner to read but changes the D-08 closed set; avoid unless a new discuss pass approves |

**Installation:** none (no external packages). Module scaffold only.

**Version verification:** not applicable, no registry package is introduced. Versions above are read from `gradle/libs.versions.toml` this session.

## Package Legitimacy Audit

No external package is installed or added by this phase (`:undo` is stdlib-only by decision D-10 and by gate). The `package-legitimacy` seam was not needed.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 app executor.prepare()                      engine pipeline                               app glue (bridge)           :undo
 ──────────────────────                      ───────────────                               ─────────────────           ─────
 ticket = journal.newTicket()
 PendingMutation(context = ticket) ──submit──► CommitCoordinator.submit
                                               gate ──Hold──► HeldProposal ─(later)─► pipeline.commitHeld
                                                 │ Admit                                   │ child run
                                                 ▼                                         │ (new ledger, heldRunId = held.runId)
                                    ApplyStep.run (context read ONCE, line 36)             │
                                         mutation.apply():                                 │
                                           ticket.capture(type,id)   ◄── before-state ─────┼────────────────────────────► adapter.read / fingerprint
                                           ...write...                                     │
                                           ticket.settle / created(type,id) ◄ after-fp,    │
                                           ticket.touches(...) footprint                   │
                                         ledger.record → ExecutedAction                    │
                                         ActionDelivery.deliver (NonCancellable)           │
                                               │ ActionEvent(runId, parentRunId, heldRunId, action)
                                               ▼
                                       compositeSink(journalSink, appSink)   (order, awaited, each guarded)
                                               │ onAction                               ┌────────────────────────┐
                                               ├────────────────────────────────────────► UndoCommitSink          │
                                               │                                        │  applied? → groupKey =  │ ──record(groupKey, run, position,
                                               │ onRunClosed(runId, termination)        │  heldRunId ?: runId     │            toolName, ticket)──► journal
                                               └────────────────────────────────────────►  integrity: expected     │ ──runClosed(groupKey, run,
                                                                                         │  positions ⊆ recorded   │            expectedPositions)──► withheld?
                                                                                         └────────────────────────┘
 UI: "Undo all (N)" ──► journal.undoAll(groupKey)
        verify (every entity: live==after → restore, live==before → already restored, else blocker)
        ├─ any blocker ───────────────► Refused(blockers)         (nothing written, no compensator run)
        ├─ restore DB entities (reverse sequence, adapter.restoreIf atomic) 
        ├─ then compensators (reverse sequence, only for entries whose DB restore succeeded)
        └─ Complete | Partial(restored, notRestored) | AlreadyUndone
```

### Recommended Project Structure

```
undo/
├── build.gradle.kts            # copy of providers/build.gradle.kts minus every dependency and the OkHttp matrix
├── api.txt                     # header-only seed at scaffold ("// Signature format: 4.0")
└── src/
    ├── main/kotlin/io/github/ygaray/voiceactionengine/undo/
    │   ├── UndoJournal.kt        # public surface: builder DSL, record/runClosed/withhold, queries, undoAll/undoEntry
    │   ├── UndoTicket.kt         # public, app-facing capture protocol
    │   ├── EntityAdapter.kt      # public seam (+ Compensator)
    │   ├── UndoResult.kt         # public closed set + Blocker/NotRestored/EntryRef/EntityKey + UndoReason
    │   ├── JournalStore.kt       # public optional persistence seam (see Open Question 4)
    │   └── internal/             # GroupState, Entries, Footprints (union-find), Verifier, Restorer, Guard (the one @Suppress)
    └── test/kotlin/.../undo/     # JUnit 4, stdlib-only helper, in-memory fake store
core/src/main/kotlin/.../core/commit/CompositeSink.kt     # new, public compositeSink(...)
sample/src/main/kotlin/.../sample/undo/                  # UndoCommitSink (bridge), ItemStore + ItemAdapter (reference, reusable by Phase 19)
scripts/modules.list + scripts/verify-module-manifest.sh # plumbing (bash, no Gradle)
```

Package must be `io.github.ygaray.voiceactionengine.undo` and the path must sit under `io/github/ygaray/voiceactionengine/` [VERIFIED: scripts/verify-repo-hygiene.sh:27-35 requires every `*.kt` below `io/github/ygaray/voiceactionengine` and a `package io.github.ygaray.voiceactionengine...` line; scripts/api-dump-isolated.sh greps `^package io.github.ygaray.voiceactionengine.<module>`].

### Verified `:core` anchors (read this session)

| What | Where | Verbatim |
|------|-------|----------|
| `ActionEvent` has an internal ctor and only three fields today | `core/.../commit/CommitSink.kt:29-35` | `public class ActionEvent internal constructor(public val runId: String, public val parentRunId: String?, public val action: ExecutedAction,)` and `override fun toString(): String = "ActionEvent(runId=$runId, parentRunId=$parentRunId, action=$action)"` |
| Only production construction site of `ActionEvent` | `core/.../commit/ApplyStep.kt:58` | `sink.onAction(ActionEvent(runId, parentRunId, action))` inside `ActionDelivery.deliver`, wrapped in `withContext(NonCancellable)` and `guardedUncancellable(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) })` (`ApplyStep.kt:55-60`) |
| `ActionDelivery` and `CommitCoordinator` are internal and have exactly these construction sites | `ApplyStep.kt:48`, `CommitCoordinator.kt:34,47`, `HeldCommit.kt:61`, `CommandPipeline.kt:88` | `ActionDelivery(runId, parentRunId, sink, recorder)`; `CommitCoordinator(runId, held.runId, gate, sink, recorder)`; `CommitCoordinator(runId, input.parentRunId, gate, sink, recorder)`. No test constructs either (grep over core/providers/sample) |
| The commit-held child run: fresh coordinator, `parentRunId = held.runId` | `core/.../pipeline/HeldCommit.kt:58-62` | `private fun open(held: HeldProposal): ChildRun {` … `return ChildRun(runId, held, CommitCoordinator(runId, held.runId, gate, sink, recorder), recorder)` |
| Position is per run, from 0, in record order | `core/.../commit/ActionLedger.kt:41-44` | `suspend fun record(kind: ActionKind, applied: Boolean, details: ActionDetails): ExecutedAction {` … `position = actions.size,` |
| Context is read once, before apply, and handed back by identity | `ApplyStep.kt:32-36` and `:85` | `MutationFacts(mutation.toolName, mutation.targetIds.toMap(), mutation.context)` inside `factsOf`; `run` calls `factsOf(mutation, recorder)` before `attempt(mutation)` |
| Applied-error kinds (what N counts) | `ApplyStep.kt:115-116`, `:124`; `CommitCoordinator.kt:170` | `val kind = if (failed) ActionKind.IS_ERROR else ActionKind.COMMITTED` / `ledger.record(kind, applied = true, details = details)`; cancelled apply: `ledger.record(ActionKind.IS_ERROR, applied = true, details = details)`; gate fault: `ledger.record(ActionKind.IS_ERROR, applied = false, details = details)` |
| `ActionKind` is an open-set value class | `core/.../commit/ActionKind.kt` | `@JvmInline public value class ActionKind internal constructor(public val value: String)`; constants `COMMITTED`, `HELD`, `PREVIEW`, `IS_ERROR` |
| `RunTermination` is the closed-set precedent (sealed, internal ctors, redacted `toString`) | `core/.../commit/RunTermination.kt:55-139`; `core/api.txt:213` | `public sealed class RunTermination`; api.txt prints `public abstract sealed exhaustive class RunTermination`; subclasses `Done`, `Failed`, `Exhausted`, `Cancelled`; `termination.executed`, `.commits`, `.held` public |
| One sink on the builder, no default | `core/.../pipeline/PipelineBuilder.kt:41` | `public var commitSink: CommitSink? = null` ("Required.") |
| The never-throw helpers a `:core` composite must reuse | `core/.../internal/Guarded.kt:38-49` | `guarded(onFault, block)`, `guardedUncancellable(onFault, block)`; the file-level `@file:Suppress("TooGenericExceptionCaught")` is "the repository's only one" (Guarded.kt:51-52) |
| `SINK_ERROR` trace code | `core/.../telemetry/TraceCode.kt:27` | `public val SINK_ERROR: TraceCode = TraceCode("sink_error")` |

`core/api.txt` is still the v1.0.0/v1.0.1 baseline (last commit `c43c65e feat(11-07): v1.0.0 public API baseline`); **do not regenerate or commit `core/api.txt` in this phase**. Additions (`getHeldRunId`, `CompositeSinkKt`) must pass `:core:apiCheck` against it; the regenerated dump belongs to the Phase 20 cut.

### Pattern 1: Capture-inside-apply ticket protocol (D-03, D-04)

**What:** One mutable `UndoTicket` per `PendingMutation`, created in the executor's `prepare` and stored in `PendingMutation.context`. Inside `apply()`: `ticket.capture(type, id)` before the write (reads before-snapshot + before-fingerprint through the registered adapter); after the write: `ticket.settle(type, id)` (post-commit fingerprint, `null` when the entity no longer exists), `ticket.created(type, id)` for an entity whose id exists only now (before = absent), `ticket.touches(type, id)` for footprint-only keys (cascade children, related entities), `ticket.compensate(kind, payload)` for out-of-DB effects, `ticket.nothingWritten()` when apply failed before writing.
**When to use:** every mutating tool. A held mutation is captured when `commitHeld` applies it, never at prepare/gate time.
**Rule table the bridge/journal applies when a ticket reaches `record`:**

| Event | Ticket state | Journal action |
|-------|--------------|----------------|
| `COMMITTED`, `applied=true` | sealed (every captured entity settled, or `nothingWritten`) | entry READY |
| `COMMITTED`, `applied=true` | unsealed | withhold the group, reason "journal gap" (app bug, loud) |
| `IS_ERROR`, `applied=true` | sealed | entry READY (it "may have written") |
| `IS_ERROR`, `applied=true` | captured but not settled | entry UNVERIFIED: at undo, live == before-fingerprint means nothing to restore; otherwise refuse with an "unverifiable" blocker (prefer a false refusal over a false restore) |
| `IS_ERROR`, `applied=true` | never captured | blocker "may have written, uncaptured" unless the app called `nothingWritten()` |
| `IS_ERROR`, `applied=false`, `HELD`, `PREVIEW` | any | ignore (not applied; N does not count them) |

**Example:**
```kotlin
// Indicative names; semantics are binding. Source: design for this phase (no external source).
class SaveItem(private val journal: UndoJournal, private val store: ItemStore, private val args: Args) : PendingMutation {
    override val toolName = "save_item"
    override val context: UndoTicket = journal.newTicket()          // read once by the engine BEFORE apply
    override suspend fun apply(): StepResult {
        context.capture("item", args.id)                            // before-state, same transaction as the write
        val updated = store.update(args.id, args.title)             // the write
        context.settle("item", args.id)                             // post-commit fingerprint
        return StepResult("saved", false, null, mapOf("id" to args.id))
    }
}
```

### Pattern 2: Group key and ordering

Group key = `event.heldRunId ?: event.runId`. Entry identity = `(runId, position)` (SB keys undo by runId + ordinal, answer #4). Order = a journal-assigned monotonic sequence taken at `record` time, because the original run and its held child both have positions 0..n. Reverse-sequence restore, never reverse-position. Clarification replies carry `parentRunId` and no `heldRunId`, so they are their own group and keep `parentRunId` for a later combined undo (SB binding). A held child of a clarification reply groups under the reply's runId.

A group must accept appends after the original run closed: the held child's actions arrive after `onRunClosed(original)`. So there is no terminal "seal" state; `runClosed(groupKey, runId, expectedPositions)` is a per-run **integrity check**, and `undoAll` sees whatever entries exist at call time.

### Pattern 3: Verify-then-restore (D-07), idempotent, never silently partial

1. **Verify (read-only), whole group.** For each entity, take the *latest* entry (highest sequence) that settled it: expected live fingerprint = that entry's after-fingerprint. Live == after → needs restore. Live == the earliest entry's before-fingerprint → already restored, skip (this also makes retry after a crash or cancellation idempotent). Anything else → `Blocker(entry, entity, CHANGED_SINCE)`. Also check the chain: an entry's before-fingerprint must equal the previous in-run settle of the same entity, else `CHAIN_BROKEN`. A missing adapter, an unsettled ticket, a withheld group, an unknown/evicted group are blockers too. Any blocker → `Refused(all blockers)`, nothing written, no compensator run.
2. **Restore DB entities** in reverse sequence through `adapter.restoreIf(id, expectedFingerprint, snapshot)` (atomic check+write in the app's transaction; `null` snapshot = delete, `null` expected = must still be absent; returns `false` when the live state differed). Components of the footprint graph are independent; on a failure inside a component, stop that component and continue the others.
3. **Run compensators** in reverse sequence, only for entries whose DB restore completed. Each compensator is idempotent (documented, tested twice); a per-entry "done" mark prevents re-running on retry.
4. **Result:** all entries restored → `Complete`; some written, some not → `Partial` with exact lists; nothing left to do → `AlreadyUndone`.

`restoreIf` raced (returns false after verify passed) is a `Partial` reason, not a clobber: the adapter performs the compare and the write atomically.

### Pattern 4: `compositeSink` (D-01, SB condition)

```kotlin
// core/.../commit/CompositeSink.kt (new, additive). Source: design; reuses core/.../internal/Guarded.kt.
public fun compositeSink(vararg sinks: CommitSink): CommitSink   // require(sinks.isNotEmpty()); copies the array
```

- Children are invoked **sequentially in the order given**, each awaited, for both `onAction` and `onRunClosed` (the `CommitSink` contract is "awaited before the next change is applied").
- Every child call goes through `guarded` (not a raw `catch`), so the repository keeps its single justified `@Suppress`. The pipeline always calls the sink under `NonCancellable` (`ApplyStep.kt:56`, `CommandPipeline.kt` `closeRun`), where a `CancellationException` from a child is foreign and must be a fault, exactly what `guarded`/`guardedUncancellable` do. Never swallow a real caller cancellation outside that context.
- A throwing child never stops its later siblings. After all children ran, if any faulted, rethrow the **first** fault with the others `addSuppressed`, so the pipeline's existing guard records `sink_error` (`TraceCode.SINK_ERROR`) exactly as it does for a single throwing sink and the run, apply count and outcome are unaffected (see Open Question 1 for the stricter reading).
- `toString` prints the child count only. No logging, no message text.
- Recommend putting the journal child **first** and the app/UI child after, so the UI sink sees an up-to-date count when it is notified.

### Pattern 5: `heldRunId` threading (internal, additive)

Add `public val heldRunId: String?` to `ActionEvent` (KDoc: set only for actions applied by `commitHeld`, equal to the held proposal's `runId`; null on every other run) and extend `toString` with the id presence only. Thread it as an optional trailing parameter with default `null` (internal classes, no public overload churn): `CommitCoordinator(..., recorder, heldRunId: String? = null)` → `ActionDelivery(..., heldRunId)` → `ActionEvent(runId, parentRunId, heldRunId, action)`; `HeldCommit.open` (`HeldCommit.kt:61`) passes `held.runId`; `CommandPipeline.startRun` (`:88`) passes nothing. Do **not** add `heldRunId` to `RunTermination`: the bridge learns the group of a closing run from the `runId → groupKey` it recorded in `onAction`. Metalava impact is additive only: `getHeldRunId()` plus `property heldRunId` lines in the `ActionEvent` block of `core/api.txt` (`core/api.txt:51-59` shows the current getter/property shape).

### Anti-Patterns to Avoid

- **Capturing at gate/prepare time** (SB's `PreMutationSnapshot` shape): restores stale data for a held change.
- **Deriving the footprint from `ExecutedAction.targetIds`**: created ids exist only after apply and SB's footprint has snapshot-only keys (D-04). The ticket declares it.
- **Counting by `position` across runs**: held children restart at 0.
- **A fifth "unavailable" result member or a force-overwrite flag**: out of scope and reverses D-08.
- **`:undo` importing `:core`, or `testFixtures(project(":core"))` in `:undo` tests**: trips `verifyModuleGraph`.
- **Holding a lock across a suspend call** in the journal.
- **Logging or `toString`-ing ids, snapshots, fingerprints, payloads** (the secrets constraint extended to user data).
- **A bridge that swallows its own failure**: a throwing `record` must call `withhold(...)`; "Undo all (N-1)" must be impossible.

## Result type: closed-set options (D-08, one-way door)

Frozen at `v1.1.0`. The existing closed-set precedent is `RunTermination` (`public sealed class`, internal subclass constructors, redacted `toString`, `code` string) and the open-set precedent is `ActionKind` (`@JvmInline value class` with constants).

**Option A (recommended): exactly the D-08 four members.**

| Member | Meaning | Carries |
|--------|---------|---------|
| `Complete` | every entry in scope restored and compensated | `restored: List<EntryRef>` |
| `Refused` | nothing was written; verification found blockers (or the group cannot be undone) | `blockers: List<Blocker>` (never empty) |
| `Partial` | some writes done, some not (restore/compensator failed or raced after verification) | `restored: List<EntryRef>`, `notRestored: List<NotRestored>` (never empty) |
| `AlreadyUndone` | no entry pending; idempotent no-op | nothing |

Supporting types: `EntryRef(runId, position, toolName)`, `EntityKey(type, id)` (equals/hashCode on both; redacted `toString`), `Blocker(entry: EntryRef?, entity: EntityKey?, reason: UndoReason)`, `NotRestored(entry, entity?, compensator: String?, reason, errorClass: String?)` (error **class name only**, never a message), `UndoReason` = open-set `@JvmInline value class` with constants `CHANGED_SINCE`, `CHAIN_BROKEN`, `UNVERIFIABLE`, `ENTANGLED`, `JOURNAL_WITHHELD`, `UNKNOWN_GROUP` (never journaled or evicted), `IN_PROGRESS`, `NO_ADAPTER`, `RESTORE_FAILED`, `COMPENSATOR_FAILED`, `SKIPPED_AFTER_FAILURE`. Reasons are open (new constants are additive); the **result members are closed**, so consumers keep an exhaustive `when` over four types and an `else` over reasons. Group-level refusals use `Blocker(entry = null, entity = null, reason)`.

**Option B: add a fifth `Unavailable(reason)`.** Slightly clearer for withheld/unknown groups, but it changes the locked "closed set above" and costs every consumer a branch. Not recommended without a new discuss pass.

**Counter-argument checked:** a double tap while an undo is running. Prescribed: `Refused` with `IN_PROGRESS` (a per-group CAS flag released in `finally`); the UI should disable the button while running. Returning `AlreadyUndone` would misreport in-flight work.

**YAT `changedItem` mapping (D-08 sub-decision):** map to the **first blocker** in deterministic order (sequence descending, then entity key), as a single nullable string, because YAT's `Refused(reason, changedItem: String?)` is single-valued and UI may render it raw. The full ordered list stays on `Refused.blockers`; nothing joins ids into one field. Reference shapes [VERIFIED: ~/Projects/Reusable/android/yahirandroidtaste/.../feedback/UndoGroupTypes.kt]: `data class Undone(val count: Int)`, `data class Refused(val reason: String, val changedItem: String? = null)`, `data object Failed`, `data object NothingToUndo`; statuses `Undoable`, `PartiallyResolved`, `FullyResolved`, `Empty`.

| `UndoResult` | YAT `UndoGroupResult` | Note |
|--------------|----------------------|------|
| `Complete(restored)` | `Undone(restored.size)` | |
| `Refused(blockers)` | `Refused(reason, blockers.first().entity?.id)` | nothing written, retry possible |
| `Partial` | `Failed` (lossy) | YAT has no partial; the app should show `notRestored` from the journal result, not only the YAT status |
| `AlreadyUndone` | `NothingToUndo` | |

This mapping is app glue (documented in INTEGRATION), not code in `:undo`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Never-throw collapse of app callbacks in `:core` | a new try/catch in `compositeSink` | `core/.../internal/Guarded.kt` `guarded`/`guardedUncancellable` | One justified `@Suppress`; handles timeout leaks and foreign cancellation correctly |
| Recording fan-out in tests | a new fake sink | `RecordingCommitSink` (hooks can throw/suspend) | Already in `core/src/testFixtures` |
| Module list in each script | another copy of `core providers keystore` | `scripts/modules.list` + one reader | The silent-skip hazard (Pitfall 9) |
| Atomic check-then-restore | `:undo` reading, comparing and writing | the adapter's `restoreIf` inside the app's transaction | A read→compare→write loop in `:undo` is a TOCTOU race |
| Entanglement | ad-hoc id matching per tool | footprint sets + union-find over entity keys declared by the ticket | Matches SB's `isolatedIndices` (`VoiceUndoFootprint.kt`) and A18 |
| Serialization of snapshots | JSON/kotlinx in `:undo` | the app (adapter + optional `JournalStore`) | `:undo` has no dependency (UNDO-01) |
| API-compat guard | hand diff | Metalava `apiCheck` (already wired) | Existing `onlyIf api.txt exists` + `verifyApiDumpPresent` |

**Key insight:** the dangerous part of undo is the *check-and-restore atomicity* and the *group verification*, not the bookkeeping. Push atomicity to the adapter (the app owns the transaction) and keep `:undo` a pure state machine that is trivially JVM-testable.

## Runtime State Inventory

Not a rename/refactor/migration phase (greenfield module plus additive seams). One note for completeness: no stored data, live service config, OS-registered state or secrets are renamed. Build artifacts: `settings.gradle.kts`, `jitpack.yml`, `api.txt` and script lists must be updated together; "Nothing found in category" otherwise.

## Common Pitfalls

### Pitfall 1: `:undo` accidentally depends on `:core`, or "stdlib-only" is read as "no POM dependency"
**What goes wrong:** the module graph or POM shows an edge; or the zero-dependency gate fails on `kotlin-stdlib`.
**Why:** KGP adds `kotlin-stdlib` to every Kotlin module; SC1's "no dependency at all" means no hub and no third-party artifact. `coreAllowed` already allows exactly `org.jetbrains.kotlin:kotlin-stdlib` and `org.jetbrains:annotations` and passes on Kotlin 2.3.20 [VERIFIED: gradle/invariants.gradle.kts:373-376].
**Avoid:** `verifyUndoZeroDeps` allows only those two coordinates on `compileClasspath`/`runtimeClasspath`, no `ProjectComponentIdentifier`, and fails if the classpath is empty (non-vacuous), copying `verifyCoreDependencyAllowlist` (`invariants.gradle.kts:385-400`). State the stdlib wording in ECOSYSTEM/INTEGRATION and in the Phase 20 freeze-confirmation row.
**Warning signs:** `project(` or `libs.` in `undo/build.gradle.kts`; `NoSuchElementException` from `allowedEdges.getValue` (new module missing from the map).

### Pitfall 2: new module green on `check` only after an `api.txt` exists
**What goes wrong:** `verifyApiDumpPresent` throws "a release exists (tags: v1.0.0, v1.0.1) but api.txt is missing" (`build.gradle.kts:60-76`), because `git tag --list v*` returns tags.
**Avoid:** commit `undo/api.txt` containing only `// Signature format: 4.0` in the scaffold plan. The v1.0 proof recorded that an empty-surface Metalava dump on `kotlin.jvm` modules is exactly that one header line [VERIFIED: .planning/milestones/v1.0-phases/01-scaffold-publishing-proof/01-04-SUMMARY.md:123 "each exactly 1 line (the `Signature format` header)"]. What is **not** yet proven is `metalavaCheckCompatibility` using a header-only file as the released baseline while the module has public API (v1.0 step (c) used a non-empty baseline). Prove it in the scaffold plan (A1) with a single-module task before relying on it.
**Also:** the isolated-dump scripts and `verify-api-dump.sh` headers still say "the real tree never receives an api.txt" and delete/forbid `api.txt` (`api-dump-isolated.sh:2-5,57` `for m in $MODULES; do rm -f "$m/api.txt"; done`, `verify-api-dump.sh:2-3,40` `rm -f core/api.txt providers/api.txt keystore/api.txt`). Those lines are stale since v1.0.0 (D-09): update the headers, and make the module loops read the manifest.

### Pitfall 3: hard-coded module lists silently skip `:undo`
**What goes wrong:** gates stay green while `:undo` has no API check, hygiene scan, docs coverage or negative control.
**Edit sites (all read this session):** see the Module plumbing table. The silent ones are `verify-repo-hygiene.sh:27,38,44,60`, `verify-api-dump.sh:23,40`, `api-dump-isolated.sh:16`, `verify-negative-controls.sh:55,77,92`, `verify-docs-coverage.sh:101,118,139`, `verify-ml-denial-controls.sh:55`, `review-api-surface.sh:33`. The loud ones are `jitpack-dry-run.sh:39-40,45` (exact artifact-set equality) and `invariants.gradle.kts:291` (`allowedEdges.getValue`).

### Pitfall 4: release-cut gates that assume every module depends on `:core`
`release-cut.sh` gate 15 (`version`) requires every non-core POM to depend on `voice-action-engine-core` (`release-cut.sh:694-715` (the non-core branch starts at `:707`): `if m != "core": deps = [... == core]; if not deps: bad.append(... "the POM does not depend on" ...)`), and `jitpack-live-probe.sh:91` (`grep -q '<artifactId>voice-action-engine-core</artifactId>' ... || fail "$m.pom does not depend on voice-action-engine-core"`). `:undo` has no core edge, so the manifest needs a `dependsOnCore` column and both must honor it (Phase 20 decision `[module-list]` expects this). Phase 17 should at least land the column and make `MODULES` (`release-cut.sh:82`), the gate-7 allowlist (`:315`) and its message (`:327`), and the tag message (`:801`) read the manifest; the selftest sandbox (`release-cut.sh` selftest) must plant a module.

### Pitfall 5: the held child group and a late append
**What goes wrong:** the journal treats `onRunClosed(original)` as final, then drops the held child's entries; or counts position collisions.
**Avoid:** Pattern 2 (no terminal seal; sequence ordering; `(runId, position)` identity).

### Pitfall 6: a throwing bridge makes "Undo all (N-1)"
**Avoid:** the bridge wraps its body, calls `journal.withhold(groupKey, reason)` on any failure, **and** `runClosed(groupKey, runId, expectedPositions)` compares `termination.executed.filter { it.applied }.map { it.position }` against the positions recorded for that run; any gap withholds the group. This catches a sibling failure the bridge never saw.

### Pitfall 7: `compositeSink` observability vs isolation
See Open Question 1. Swallowing every child fault hides a failing journal from the trace; rethrowing after all siblings ran preserves `sink_error`.

### Pitfall 8: docs gates will fail on a new Kotlin fence or region
`verify-docs-coverage.sh` C06 rejects "a kotlin fence without a doc-snippet marker" and C07 requires every region in `DocSnippetsTest.kt` to be used by a doc. A new `undo-bridge` region therefore needs the matching `<!-- doc-snippet: undo-bridge -->` block in `INTEGRATION.md` in the same change; keep the snippet domain-free (no note/card/food, use "item"). `public_types()` (`verify-docs-coverage.sh:100-104`) only scans `core providers keystore`, so `:undo` types are not yet demanded in `API.md`; generalizing that is Phase 19's `[docs]` decision, but add the `:undo` types to API.md now so Phase 19 does not start red.

### Pitfall 9: detekt defaults bite a state machine
`buildUponDefaultConfig = true` with `config/detekt/detekt.yml` overriding only `TooManyFunctions.thresholdInClasses: 12`, `LongParameterList` and two naming excludes. Expect default `ReturnCount` (2), `LongMethod`, `CyclomaticComplexMethod`, `NestedBlockDepth`, `MagicNumber`, `SwallowedException`, `TooGenericExceptionCaught`. Split the executor into small internal classes (Verifier, Restorer, Compensating, Footprints); name limits as constants; one primary type per file named for it. App-code exceptions must be caught: put the **one** `@file:Suppress("TooGenericExceptionCaught")` helper in `undo/.../internal/Guard.kt` (it can't import `:core`'s) and update the "repository's only one" comments to "one per module", **or** tune it by path in `detekt.yml` with a justification comment. Both are within D-10; pick one in the plan and record it. `runCatching` is banned by `scanBannedConstructs` (`invariants.gradle.kts:14`).

### Pitfall 10: heavy Gradle on a tight host
Swap is full and 1.8 GB free this session. Any Gradle verification: use the single-use-daemon flags from the host-OOM memory, one task at a time, real exit status, never `--stop` while another project's daemon is live, never run during another project's build.

## Module plumbing: edit sites and the manifest (D-09, D-10)

**Manifest:** `scripts/modules.list`, one row per published module: `name packaging artifactId kotlinPackage dependsOnCore`. Rows today: `core jar voice-action-engine-core core no`, `providers jar voice-action-engine-providers providers yes`, `keystore aar voice-action-engine-keystore keystore yes`; Phase 17 adds `undo jar voice-action-engine-undo undo no`. (`voice-adapter`'s Kotlin package is not `voice-adapter`; the column exists for Phase 18.) Provide `scripts/lib/modules.sh` helpers (names, field lookup) sourced by every script, and `scripts/verify-module-manifest.sh` (bash, no Gradle) that cross-checks, and fails loudly with the disagreeing item named:

1. manifest names == `include(...)` entries in `settings.gradle.kts:25` minus `:sample`;
2. every manifest module has `maven-publish` and an `artifactId = "<manifest artifactId>"` literal in its `build.gradle.kts` (`providers/build.gradle.kts:44` shows the form);
3. `jitpack.yml` install line (`jitpack.yml:6`) names `:<m>:publishReleasePublicationToMavenLocal` for exactly the manifest set and never `:sample`;
4. `allowedEdges` (`invariants.gradle.kts:272`) has a key per manifest module, `:undo` mapped to `emptySet()`;
5. `dependsOnCore=no` modules have no `project(":core")` in their build file;
6. `<m>/api.txt` exists and starts with `// Signature format: 4.0`.

Add a **planted-module negative control** (temp copy: add a fake `zz` row or `include`, expect red naming `zz`), run in bash without Gradle so it is cheap enough to run per commit.

**Edit-site table (line numbers read this session; re-grep before editing):**

| File | Anchor | Edit |
|------|--------|------|
| `settings.gradle.kts` | `:25` `include(":core", ":providers", ":keystore", ":sample")` | add `":undo"` |
| `undo/build.gradle.kts` | new | copy `providers/build.gradle.kts:1-24` (plugins minus serialization, `java {}`, `kotlin { explicitApi(); jvmTarget JVM_11; -Xjdk-release=11 }`), no `dependencies` main block, `testImplementation(libs.junit)` only, publication `artifactId = "voice-action-engine-undo"` (form at `providers/build.gradle.kts:40-49`), `apply(from = rootProject.file("gradle/invariants.gradle.kts"))` last. Do **not** copy `java-test-fixtures` or the OkHttp matrix |
| `jitpack.yml` | `:6` | append `:undo:publishReleasePublicationToMavenLocal` to the **single** `./gradlew` line |
| `gradle/invariants.gradle.kts` | `:272` `val allowedEdges = mapOf(":core" to emptySet<String>(), ":providers" to setOf(":core"), ":keystore" to setOf(":core"))` | add `":undo" to emptySet<String>()` |
| same | `:273-274` `sampleRequiredEdges = setOf(":providers", ":keystore")`, `sampleAllowedEdges = setOf(":core", ":providers", ":keystore")` | add `":undo"` to allowed; recommend also required (the end-to-end proof must not be silently dropped); update the two error strings that name the graph `(:sample -> {:providers, :keystore} -> :core)` (`:295,:300`) |
| same | after `:329` | new `verifyUndoZeroDeps` (guarded by `if (project.name == "undo")`), wired to `check` |
| same | `:333` `if (project.name in setOf("core", "providers", "keystore"))` (`verifyNoMlArtifacts`) | add `"undo"` (cheap, keeps SC4 uniform); update `verify-ml-denial-controls.sh:55` loop |
| `sample/build.gradle.kts` | dependencies block | `implementation(project(":undo"))` |
| `build.gradle.kts` (root) | `:60-76` `verifyApiDumpPresent`, `:49-50` `onlyIf("api.txt exists")` | no edit; `undo/api.txt` must exist before `:undo:check` |
| `undo/api.txt` | new | one line: `// Signature format: 4.0` |
| `scripts/release-cut.sh` | `:82` `MODULES="core providers keystore"`; `:315` `core/api.txt \| providers/api.txt \| keystore/api.txt \| .planning/*) ;;`; `:327` message; `:694` python loop; `:801` tag message; `:917-921` selftest api.txt paths | read manifest; add `undo/api.txt` to the gate-7 allowlist; gate 15 honors `dependsOnCore`; header comment at `:30-32` ("the three api.txt files") |
| `scripts/api-dump-isolated.sh` | `:16` `MODULES=...`, `:2-5,57` stale "never receives an api.txt", the per-module package grep in the post-dump loop | read manifest; fix headers; package grep must use `kotlinPackage` |
| `scripts/verify-api-dump.sh` | `:23,40` | same |
| `scripts/jitpack-dry-run.sh` | `:39-40` exact set, `:45` `for spec in core:jar providers:jar keystore:aar` | derive from manifest (`name:packaging`), message `:40` |
| `scripts/jitpack-consumer-probe.sh` | `:3-4,46,57,69-72` | add a third consumer `:undoalone` (kotlin.jvm, only `voice-action-engine-undo`), compile a `:undo` type, and **assert `voice-action-engine-core` is absent** from its `runtimeClasspath` (UNDO-01 mechanical proof); keep the existing consumers |
| `scripts/jitpack-live-probe.sh` | `:9,18,89-93` | manifest-driven `EXPECT_MODULES`; core-dependency check only when `dependsOnCore=yes` |
| `scripts/verify-repo-hygiene.sh` | `:27,38,44,60-63` | add `undo` to the `find` roots, main-source count, ECOSYSTEM artifact loop, api.txt-tracked loop |
| `scripts/verify-negative-controls.sh` | `:55,77,92` | per-module loops include `undo`; add: `:undo` gains `project(":core")` → `verifyModuleGraph` red; gains an extra dependency → `verifyUndoZeroDeps` red; `api.txt` missing with `-PvaeAssumeReleased` → red |
| `scripts/review-api-surface.sh` | `:33` | module loop |
| `scripts/verify-docs-coverage.sh` | `:101,118,139` and `REQUIRED_REGIONS :32` | at least the coordinate checks C01/C03 must accept `voice-action-engine-undo`; full generalization is Phase 19 |
| `scripts/agent-wiring-test.sh` | `:117,124-127,218` regex `(core\|providers\|keystore)` | accept `undo`; wiring of the new module is Phase 19 |
| `ECOSYSTEM.md` | `:31` "Planned for v1.1, **not yet published**: `voice-action-engine-undo` and ..." | add the `:undo` row (jar, depends on nothing but the Kotlin stdlib); keep "not yet published" until the cut |
| `INTEGRATION.md`, `README.md`, `API.md` | `INTEGRATION.md` §6 "The write path" (sink paragraph at ~`:329-333` already says "Use it for an undo journal") | undo section + `undo-bridge` marked snippet; install coordinate line `…:voice-action-engine-undo:<version>`; API.md rows for `compositeSink`, `ActionEvent.heldRunId`, `:undo` types |

Scope split (recommended, planner may adjust): Phase 17 lands the manifest, gate, helper library, the `:undo` rows/edits above, and converts the scripts whose silent skip would hide `:undo` (hygiene, api-dump x2, negative controls, dry-run, ml-denial, release-cut `MODULES`/`:315`/`:327`/gate-15/tag message). Phase 19 owns docs-coverage generalization and the agent wiring test for new modules; Phase 20 owns gate 12's "new in this release, no baseline" branch for `undo/api.txt` (decision `[new-module-baseline]`, depends on 17).

## `:sample` end-to-end proof (UNDO-04, SB condition "sample proves the bridge end to end")

`:sample` is an Android app module with JVM unit tests (`testImplementation(libs.junit)`, `libs.coroutines.test`, `testFixtures(project(":core"))`; `sample/build.gradle.kts`). Layout recap: main `…/sample/{legs,tools,keys,net,ui,evidence,verdict,fixture}`, tests under `src/test/kotlin/.../sample` and `docs/DocSnippetsTest.kt` (regions between `// doc-snippet:start <name>` / `// doc-snippet:end <name>`). The existing offline fake `CannedToolExecutor` has no `targetIds` and no state (`sample/.../tools/CannedToolExecutor.kt`: `CANNED_WRITE = """{"ok":true,"id":"canned-write-1"}"""`), so it cannot prove undo; Phase 19's `[store]` decision plans a stateful in-memory store. Build that store **here** (`ItemStore` + `ItemAdapter`, fingerprint = content hash + revision counter) and let Phase 19 reuse it, to avoid two copies.

Place the bridge in **`sample/src/main/.../sample/undo/UndoCommitSink.kt`** (Phase 19's undo-all leg lives in sample main and needs it) and keep a verbatim copy in a `DocSnippetsTest` region `undo-bridge` (the doc comparator reads only that file); add a small parity test that fails when the two drift (planner may instead extend the comparator, accepting the Phase 19 conflict). The bridge: on `onAction` ignore everything but `applied == true`, `groupKey = event.heldRunId ?: event.runId`, take the ticket from `event.action.context` (an app whose context wraps the ticket exposes it), `journal.record(...)`, on any exception `journal.withhold(...)`; on `onRunClosed` call `runClosed(groupKey, runId, expectedPositions)` and track pending held count from `termination.held.size` (and decrement per child run; the app tells the bridge when a proposal is discarded, because the engine emits no discard signal).

JVM end-to-end scenarios (all offline, zero provider calls; `ScriptedStrategy`/`ScriptedGate` from testFixtures; a real `commandPipeline { commitSink = compositeSink(bridge, recording) }`):

| # | Scenario | Asserts |
|---|----------|---------|
| S1 | one command, 3 mutations (create, edit, create linked) | N = 3; `undoAll` → `Complete`; store equals the initial state; second `undoAll` → `AlreadyUndone` |
| S2 | an unrelated edit of a touched entity between commit and undo | `Refused` lists that entity; **store unchanged**; no compensator ran |
| S3 | hold then confirm (`commitHeld`) with state moved between hold and confirm | child actions carry `heldRunId`; N counts the held child once confirmed; restore returns the **moved** state (capture inside apply), not the prepare-time one |
| S4 | clarification reply (`CommandInput.parentRunId` set) | separate group; `parentRunId` still on the event |
| S5 | first child sink throws | journal child still recorded; trace has `sink_error`; run outcome and apply count unchanged |
| S6 | journal `record` throws / a position is missing at `runClosed` | `undoAll` → `Refused(JOURNAL_WITHHELD)`; never "N-1" |
| S7 | plan-shaped run: k committed steps then a hold (distinct positions) | all k undoable in the run; held step pending, not counted |
| S8 | compensator (alarm-like) registered and ordered after DB restore | runs once, in reverse sequence; idempotent on retry |

Device/TESTER: none in Phase 17. The on-device "Undo all (N)" leg is Phase 19 (offline, spend reservation 0) and must be a non-autonomous TESTER checkpoint there; Phase 17 only leaves the reusable bridge, store and adapter.

## Code Examples

### Bridge sketch (what the doc snippet shows)

```kotlin
// Indicative. Source: design for this phase; CommitSink/ActionEvent/RunTermination shapes verified in core.
class UndoCommitSink(private val journal: UndoJournal) : CommitSink {
    override suspend fun onAction(event: ActionEvent) {
        val action = event.action
        if (!action.applied) return                       // held, preview, un-applied errors are not journaled
        val group = event.heldRunId ?: event.runId
        try {
            journal.record(group, event.runId, action.position, action.toolName, action.kind == ActionKind.IS_ERROR,
                action.context as? UndoTicket)
        } catch (e: IllegalArgumentException) {           // any specific failure: withhold, never N-1
            journal.withhold(group, UndoReason.JOURNAL_WITHHELD)
        }
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) {
        val applied = termination.executed.filter { it.applied }.map { it.position }.toSet()
        if (applied.isNotEmpty()) journal.runClosed(groupFor(runId), runId, applied)
    }
}
// wiring: commandPipeline { commitSink = compositeSink(UndoCommitSink(journal), appSink) }
```
(`groupFor(runId)` is the `runId → groupKey` map the bridge fills in `onAction`; an app that cannot name the failure type it catches uses the repository's single guarded helper, not a bare `catch (e: Exception)`.)

### Adapter contract (semantics binding, names indicative)

```kotlin
public interface EntityAdapter {
    public val entityType: String
    public suspend fun read(id: String): Any?                       // before-state; null = absent
    public suspend fun fingerprint(id: String): String?             // live content+version hash; null = absent
    public suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean
    // atomic in the app's transaction: if live fingerprint == expectedFingerprint make the entity equal `snapshot`
    // (null snapshot = delete; absent entity = re-insert with its ORIGINAL id and cascade children), return true;
    // if the live state differs return false and write nothing; throw for a real failure.
}
public fun interface Compensator { public suspend fun compensate(payload: String) }   // MUST be idempotent
```
Interface members added after `v1.1.0` need default bodies (Kotlin interface defaults compile as JVM defaults here; precedent in api.txt per PITFALLS). Keep the method set minimal now.

### `compositeSink` test shape

```kotlin
// core/src/test: a throwing first child and a RecordingCommitSink second; assert the second saw every event,
// the pipeline outcome is unchanged, and the trace contains TraceCode.SINK_ERROR.
val recorder = RecordingCommitSink()
val thrower = object : CommitSink { /* onAction throws IllegalStateException("x") */ }
val pipeline = commandPipeline { /* … */ commitSink = compositeSink(thrower, recorder) }
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SB: per-run `VoiceRunJournal` keyed by ordinal, context handed to the engine by identity and cross-checked, `PreMutationSnapshot` display-only | `UndoTicket` filled inside apply, same identity trick, entity adapters | v1.1 (A18) | SB 178 ports `VoiceUndoOperations` onto adapters; `ReminderArmer.disarm` becomes a compensator run after the DB restore (`VoiceUndoOperations.softDeleteCreatedReminder` already disarms *after* the lock) [VERIFIED: SecondBrain core/agent/VoiceUndoOperations.kt, VoiceUndoFootprint.kt, VoiceRunJournal.kt read this session] |
| YAT `UndoHistoryStore`: session-scoped, first-consumer-wins CAS, `Refused` = nothing written | same semantics, journal in `:undo` | v1.1 | The "does not survive process death" documentation matches YAT's session scope (D-06) |
| SB isolation: a call is isolated iff its footprint is disjoint from every other state-affecting call; held and preview never overlap; a committed no-op or `is_error` counts | same rule over declared footprints | v1.1 | `undoEntry` is offered only for isolated entries; entangled entries refuse with `ENTANGLED` and are reachable via `undoAll` |

**Deprecated/outdated:** the "real tree never receives an api.txt" statements in `api-dump-isolated.sh`/`verify-api-dump.sh` (D-09); `ARCHITECTURE.md` line anchors (`invariants.gradle.kts:272`, `HeldCommit.kt:60`, `CommitSink.kt:29`) are v1.0.1 numbers, re-verified here where cited.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Metalava 0.5.1 `metalavaCheckCompatibility` accepts a header-only `api.txt` as the released baseline for a `kotlin.jvm` module that has public API (the empty-surface **dump** being 1 line is verified; the **compat check against it** is not) | Pitfall 2, Module plumbing | Scaffold plan must fall back to committing a real dump at phase end (and regenerating at Phase 20), or an `unreleasedModules` skip; costs one plan task |
| A2 | `compositeSink` should rethrow the first child fault after all siblings ran (so `sink_error` stays observable), reading "isolates from the pipeline" as "the pipeline's outcome and applies are unaffected" | Pattern 4, Open Question 1 | If SB meant "the pipeline never sees an exception", rethrow changes the trace (an extra `sink_error`); swapping to swallow-with-callback later is additive |
| A3 | Non-generic `EntityAdapter` with `Any?` snapshot is acceptable to SB/CT (they cast in their own adapters) | Standard Stack, Adapter contract | A generic adapter would need an unchecked cast + `@Suppress` inside `:undo` |
| A4 | detekt 1.23.8 default thresholds (`ReturnCount` 2, `LongMethod` 60, `NestedBlockDepth` 4, `MaxLineLength` 120) apply as in the other modules; `config/detekt/detekt.yml` is the only override layer | Pitfall 9 | Plan sizing of classes/methods; no functional risk |
| A5 | A stdlib-only `runSuspending` test helper (`startCoroutine` + `CountDownLatch`) is sufficient for the `:undo` tests | Supporting stack | If not, allow `kotlinx-coroutines-test` in `:undo` test scope (already in the catalog) and drop the "alone on the test classpath" nicety |
| A6 | A second `@file:Suppress("TooGenericExceptionCaught")` helper in `:undo` is acceptable to the project owner ("repository's only one" becomes "one per module") | Pitfall 9 | Alternative is a path-scoped detekt.yml tune with a justification comment |
| A7 | Shipping a minimal `JournalStore` seam (save/delete/loadAll with public holder types) in v1.1.0 is wanted, not just a persist-only hook | Open Question 4 | Biggest unforced frozen surface after D-08; deferring it (D-06's second option) is the safer reversal if SB/CT do not need durability in 178/75 |
| A8 | Undo of an `IS_ERROR`/`applied=true` entry with an unsettled ticket refuses (`UNVERIFIABLE`) rather than assuming nothing was written | Pattern 1 rule table | Wrong default makes failed writes block "Undo all"; `nothingWritten()` is the escape hatch for apps |

## Open Questions

1. **`compositeSink` fault policy (SB condition wording).**
   - Known: row 8 requires isolation "from its siblings and from the pipeline"; the engine already guarantees a sink throw is only a trace code (`CommitSink` KDoc: "A throw from either is caught and recorded; it never causes a change to be applied again").
   - Unclear: whether SB means the pipeline must never see the exception (no `sink_error` code) or just that the run is unaffected.
   - Recommendation: rethrow-after-all-siblings (A2), confirm with the orchestrator in the plan's discuss/checkpoint step. Both readings pass the same sibling/outcome assertions; only the trace code differs.
2. **Single-entry undo scope.** A18 says isolated actions get an individual Undo; SB offers it only for isolated calls. Recommendation: `undoEntry` for isolated entries only, `Refused(ENTANGLED)` otherwise (no auto-expansion to the component). Confirm no consumer needs component-expansion.
3. **Header-only seed versus real dump at phase end.** D-09 says header-only and regenerate at Phase 20. A real `undo/api.txt` committed at the end of Phase 17 would make Phases 18/19 changes compat-checked and make Phase 20's gate 10 trivially equal, at the cost of freezing early. Recommendation: keep D-09 literal; planner may add a last-wave "dump and review" task as discretion.
4. **`JournalStore` shape.** Ship `save(GroupRecord)`, `delete(groupKey)`, `loadAll(): List<GroupRecord>` with plain (non-data) holder classes carrying only strings and opaque `Any?` payloads, or ship a persist-only hook, or defer entirely? Recommendation: minimal three-method seam with explicit constructors; document "does not survive process death" regardless. Needs a quick SB/CT yes via the orchestrator.
5. **Bridge duplication between sample main and the doc region.** Parity test (recommended) vs extending `verify-docs-coverage.sh` to read a second snippet file (conflicts with Phase 19 `[docs]`).
6. **N and `NO_OP` entries.** An applied entry that called `nothingWritten()` counts in N (D-02) but restores nothing; confirm the UI copy ("Undo all (N)" where some entries are no-ops) is acceptable.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build/tests | ✓ | OpenJDK 17.0.19 | — |
| Gradle wrapper | build/tests | ✓ (distribution cached) | 9.4.1 | — (do not run in research; host memory) |
| Bash, git, python3 | gate scripts, release-cut | ✓ | python 3.12.3 | — |
| Gradle caches (offline) | `--offline` runs | ✓ | 8.13, 8.14.3, 9.4.1 dirs present | — |
| Free RAM / swap | any Gradle run | ✗ tight | 1.8 GB free, swap 2047/2047 MB used (this session) | single-use daemon flags, quiet window, ask Yahir for `swapoff/swapon` if full |
| Android device (TESTER) | none in this phase | n/a | — | Phase 19 owns it (non-autonomous checkpoint there) |
| Live provider keys / spend | none | n/a | — | undo is offline; zero provider calls |

**Missing dependencies with no fallback:** none for planning.
**Missing dependencies with fallback:** host memory headroom (flags above).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (`libs.junit`); `kotlinx-coroutines-test` 1.11.0 only in `:core`/`:sample` tests; `:undo` uses a stdlib-only helper |
| Config file | none (per-module `build.gradle.kts`); gates in `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml` |
| Quick run command | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline :undo:test` (or `:core:test --tests '*CompositeSink*' --tests '*HeldRunId*'`) [flags from the host-OOM memory and `scripts/verify-ml-denial-controls.sh:25-27`] |
| Full suite command | same flags: `./gradlew --offline :undo:check :core:check :sample:testDebugUnitTest`, then `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-docs-coverage.sh` |
| Bash-only fast gates (no Gradle) | `scripts/verify-module-manifest.sh` (+ planted-module control), `scripts/verify-docs-coverage.sh`, `scripts/verify-repo-hygiene.sh` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UNDO-01 | non-voice JVM app journals and undoes using `:undo` alone | unit (`UndoStandaloneTest`, stdlib-only helper, no engine types) | `:undo:test --tests '*UndoStandaloneTest*'` | ❌ Wave 0 |
| UNDO-01 | no dependency beyond stdlib/annotations; no project edge | gate | `:undo:verifyUndoZeroDeps :undo:verifyModuleGraph` + negative controls | ❌ Wave 0 |
| UNDO-01 | consumer resolves `voice-action-engine-undo` with `:core` absent | clean-cache probe (heavy) | `scripts/jitpack-dry-run.sh` (extended probe `:undoalone`) | ❌ Wave 0; **non-autonomous quiet window** |
| UNDO-02 | adapter round trip: created→delete, edited→write back, deleted→re-insert same id with children | unit | `:undo:test --tests '*AdapterRoundTrip*'` | ❌ Wave 0 |
| UNDO-02 | compensator after DB restore, reverse order, idempotent twice, failure→Partial, not run when Refused | unit | `:undo:test --tests '*Compensator*'` | ❌ Wave 0 |
| UNDO-03 | edit between commit and undo → `Refused` lists entity, store untouched; deleted-since; recreated-since | unit | `:undo:test --tests '*RefuseLoudly*'` | ❌ Wave 0 |
| UNDO-03 | two entries on one entity: latest-entry verification; chain broken | unit | `:undo:test --tests '*ChainVerify*'` | ❌ Wave 0 |
| UNDO-03 | restore fails mid-way → `Partial` exact lists; retry touches only remainder; live==before treated as restored; cancellation mid-undo leaves consistent per-entry state | unit | `:undo:test --tests '*Partial*' --tests '*Cancel*'` | ❌ Wave 0 |
| UNDO-03 | `AlreadyUndone` idempotence; concurrent second call → `Refused(IN_PROGRESS)` | unit | `:undo:test --tests '*Idempotent*'` | ❌ Wave 0 |
| UNDO-03 | closed result set exhaustiveness (a test with an exhaustive `when` and no `else`) and `UndoReason` open set | unit + frozen api.txt | `:undo:test --tests '*ResultShape*'` | ❌ Wave 0 |
| UNDO-03 / D-05 | withheld group on `record` failure or missing position; never N-1 | unit | `:undo:test --tests '*Withheld*'` | ❌ Wave 0 |
| UNDO-03 | isolation: `undoEntry` ok for isolated, `ENTANGLED` otherwise; held/preview never overlap | unit | `:undo:test --tests '*Isolation*'` | ❌ Wave 0 |
| UNDO-03 | bounded by count and age; evicted → `UNKNOWN_GROUP` | unit | `:undo:test --tests '*Limits*'` | ❌ Wave 0 |
| Secrets | every public `toString` contains no id/snapshot/fingerprint/payload canary | unit | `:undo:test --tests '*Redaction*'` | ❌ Wave 0 |
| UNDO-04 | `heldRunId` null on normal and clarification child; = `held.runId` on `commitHeld` (both overloads); two proposals in one run | unit (`HeldRunIdTest`) | `:core:test --tests '*HeldRunId*'` | ❌ Wave 0 |
| UNDO-04 | `compositeSink`: order, awaiting, throwing first/last/all, `onRunClosed` isolation, foreign cancellation as fault, empty array refused, `sink_error` recorded, redacted `toString` | unit (`CompositeSinkTest`) | `:core:test --tests '*CompositeSink*'` | ❌ Wave 0 |
| UNDO-04 | `:core` has no `:undo` edge; allowlist unchanged | gate | `:core:verifyCoreDependencyAllowlist :core:verifyModuleGraph` | ✅ existing |
| UNDO-04 | end-to-end S1..S8 through a real pipeline | unit (`:sample`) | `:sample:testDebugUnitTest --tests '*UndoEndToEnd*'` | ❌ Wave 0 |
| UNDO-04 | doc region compiled/executed and equal to INTEGRATION; parity with sample main bridge | unit + script | `:sample:testDebugUnitTest --tests '*DocSnippets*'` + `scripts/verify-docs-coverage.sh` | ✅ extend |
| D-09 | header-only baseline accepted; additive classes stay green; removal goes red | Metalava proof | `:undo:apiCheck` on a planted class (scaffold plan, isolated copy) | ❌ Wave 0 |
| D-10 | manifest consistent with settings/jitpack/publish/allowedEdges; planted module goes red | bash gate | `scripts/verify-module-manifest.sh` and its planted control | ❌ Wave 0 |
| API | `:core` additions are additive vs the v1.0.1 baseline | Metalava | `:core:apiCheck` | ✅ existing task |

### Sampling Rate

- **Per task commit:** the single most relevant quick command above (one Gradle task, flags applied) plus the bash gates for plumbing tasks.
- **Per wave merge:** `:undo:check :core:check` (detekt, scanBanned, bytecode, explicit API, module graph, no-DI, zero-deps, api check) then `:sample:testDebugUnitTest --tests '*Undo*' --tests '*DocSnippets*'`.
- **Phase gate:** full suite green plus `scripts/verify-negative-controls.sh` (heavy, quiet window) and the clean-cache `jitpack-dry-run.sh` with the `:undoalone` probe before `/gsd-verify-work`. These last two are **non-autonomous plans** (host memory); neither needs the TESTER or live spend.

### Wave 0 Gaps

- [ ] `undo/` module: `build.gradle.kts`, `api.txt` seed, source/test dirs, `runSuspending` helper, in-memory fake store
- [ ] `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh` + planted-module control
- [ ] `verifyUndoZeroDeps` + negative controls in `verify-negative-controls.sh`
- [ ] `core` tests: `HeldRunIdTest`, `CompositeSinkTest` (fixtures already exist)
- [ ] `sample`: `ItemStore`/`ItemAdapter`, `UndoCommitSink`, `UndoEndToEndTest`, `undo-bridge` region + INTEGRATION block + parity test
- [ ] Framework install: none

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high) [VERIFIED: .planning/config.json `"security_enforcement": true, "security_asvs_level": 1, "security_block_on": "high"`].

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — (library, no principals) |
| V3 Session Management | no | — |
| V4 Access Control | no | the app decides who may offer "Undo all"; `:undo` has no ambient authority |
| V5 Input Validation | yes | validate entity type/id (non-blank, bounded length), group keys, `expectedPositions` at the public boundary; `require`/`IllegalArgumentException` at the seam; fingerprints compared as opaque strings |
| V6 Cryptography | no | fingerprints are app-defined integrity hashes, not security primitives; **never hand-roll crypto in `:undo`** |
| V7 Error handling / Logging | yes | no logging in the library (`android.util.Log`, `println`, `printStackTrace` banned by scan); exceptions and results carry error **class names only**; redacted `toString` on every public type |
| V8 Data protection | yes | the journal holds user-data snapshots in memory: bound by count and age, drop snapshots once a group is `Complete`/evicted, never serialize inside `:undo` |
| V14 Configuration / supply chain | yes | no new third-party dependency; zero-dep gate |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Snapshot/id/payload leaks via `toString`, logs, exception messages, `Partial.notRestored` | Information disclosure | redacted `toString` (counts/lengths/types only), `errorClass` only, canary-string `RedactionTest`, banned-construct scan |
| Undo overwrites a later edit (clobber) | Tampering | after/before fingerprint verification, atomic `restoreIf`, whole-group refuse, prefer false refusal |
| Double or concurrent undo, retry after crash | Tampering / Repudiation | per-group CAS state, per-entry done marks, idempotent compensators, live==before treated as restored |
| Unbounded journal growth (memory DoS) | Denial of service | count and age limits, eviction → `UNKNOWN_GROUP` |
| A failing journal silently drops an action | Repudiation | `withhold`, integrity check at `runClosed`, never "N-1" |
| A throwing sibling sink breaks the journal | Denial of service | `compositeSink` isolation, journal child first |

## Sources

### Primary (HIGH confidence, read this session)
- `core/src/main/kotlin/.../commit/{CommitSink,CommitCoordinator,ApplyStep,ActionLedger,ActionKind,HeldProposal,RunTermination,ToolStep,PreApplyGate}.kt`, `pipeline/{HeldCommit,CommandPipeline,PipelineBuilder,CommandOutcome}.kt`, `internal/Guarded.kt`, `telemetry/TraceCode.kt`: all anchors above
- `core/api.txt` (v1.0.x baseline), `core/build.gradle.kts`, `providers/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `jitpack.yml`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml`
- `scripts/{release-cut,api-dump-isolated,verify-api-dump,jitpack-dry-run,jitpack-consumer-probe,jitpack-live-probe,verify-repo-hygiene,verify-negative-controls,verify-ml-denial-controls,review-api-surface,verify-docs-coverage,agent-wiring-test}.sh`
- `sample/build.gradle.kts`, `sample/src/**` layout, `docs/DocSnippetsTest.kt`, `tools/CannedToolExecutor.kt`; `INTEGRATION.md`, `ECOSYSTEM.md`
- `.planning/` CONTEXT, REQUIREMENTS, ROADMAP §Phase 17, v1.1-DECISION-MAP §Phase 17/20, R-v1.1-CONSUMER-ANSWERS rows 4/5/8, research/{SUMMARY,ARCHITECTURE §6/§8,PITFALLS §17-19/9,FEATURES §4,STACK §Phase 17}, `milestones/v1.0-phases/01-.../01-04-SUMMARY.md` (empty-surface dump)
- `CROSS-REPO-SCOPE-CONTRACT.md` A18 (`:253-259`) and E7 (`:224`)
- Reference shapes: YAT `feedback/UndoGroupTypes.kt`, `UndoHistoryEntry.kt`; SB `feature/voicecommand/VoiceUndoFootprint.kt`, `core/voiceengine/VoiceRunJournal.kt`, `core/agent/VoiceUndoOperations.kt`, `MutationGate.kt`, `PreMutationSnapshot.kt`

### Secondary (MEDIUM confidence)
- v1.1 milestone research (`.planning/research/*`) for design intent (written against v1.0.1; line numbers re-verified where cited)

### Tertiary (LOW confidence)
- Metalava behavior on a header-only released baseline during `metalavaCheckCompatibility` (A1): inferred from the verified empty-surface dump and common practice of empty signature files; must be proven in the scaffold plan.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new; read from the catalog and build files
- Architecture (core seams, bridge, plumbing): HIGH, verified against current code
- `:undo` API/semantics: MEDIUM, a design bounded by D-01..D-10 and A18; names indicative
- Pitfalls: HIGH for plumbing and gates, MEDIUM for undo edge cases (derived from SB/YAT shapes and PITFALLS 17-19)

**Research date:** 2026-10-06
**Valid until:** 2026-11-05 (stable; re-grep the script/gate line numbers before editing because Phases 18/13 and Phase 20 tooling work also touch them)
