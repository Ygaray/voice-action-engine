# Phase 17: Run-Level Undo - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 14 groups (new/modified)
**Analogs found:** 13 / 14 (all analogs verified git-tracked)

Paths below are relative to `/home/yahir/Projects/Reusable/android/voice-action-engine`. `C=core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`. Line numbers were read this session; re-grep before editing. RESEARCH.md (17-RESEARCH.md) has the full edit-site table for the scripts; this file does not repeat it.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `undo/build.gradle.kts` | config | build | `providers/build.gradle.kts:1-49` | exact (drop deps) |
| `undo/api.txt` | config | seed | v1.0 empty-surface dump (one header line) | exact |
| `undo/src/main/.../undo/UndoJournal.kt` (+ `UndoTicket`, `EntityAdapter`, `JournalStore`) | service/seam | CRUD, request-response | `$C/commit/CommitSink.kt` (seam style), `ActionLedger` (state) | role-match |
| `undo/src/main/.../undo/UndoResult.kt` | model | closed set + open reasons | `$C/commit/RunTermination.kt` (closed) + `ActionKind.kt` (open value class) | exact |
| `undo/src/main/.../undo/internal/Guard.kt` | utility | never-throw collapse | `$C/internal/Guarded.kt:51-69` | role-match (no coroutines dep) |
| `undo/src/test/...` | test | request-response | `core/src/test/.../ActionEventTest.kt` + `RecordingCommitSink` | role-match |
| `$C/commit/CommitSink.kt` (add `heldRunId`) | model | event-driven | itself (`ActionEvent` :29-35) | exact |
| `$C/commit/ApplyStep.kt`, `CommitCoordinator.kt`, `pipeline/HeldCommit.kt` (thread `heldRunId`) | service | event-driven | themselves | exact |
| `$C/commit/CompositeSink.kt` (new) | utility | event-driven fan-out | `ActionDelivery.deliver` (`ApplyStep.kt:48-60`) + `Guarded.kt` | role-match |
| `core/src/test/.../CompositeSinkTest.kt`, `ActionEventTest.kt` ext | test | event-driven | `RecordingCommitSink` hooks (testFixtures) | exact |
| `sample/src/main/.../sample/undo/UndoCommitSink.kt`, `ItemStore`, `ItemAdapter` | service | event-driven | `core/src/testFixtures/.../RecordingCommitSink.kt` (CommitSink impl); `sample/.../tools/CannedToolExecutor.kt` (executor) | role-match |
| `sample/src/test/.../docs/DocSnippetsTest.kt` (`undo-bridge` region) + INTEGRATION.md | test/doc | transform | existing regions, e.g. `// doc-snippet:start scripted-provider` (:109) | exact |
| `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh` | config/utility | batch | `scripts/verify-repo-hygiene.sh` (bash style: `set -euo pipefail`, `violate()`) | role-match |
| `gradle/invariants.gradle.kts` (`allowedEdges`, `verifyUndoZeroDeps`) | config | build | same file :272-300, :365-400 | exact |
| `settings.gradle.kts`, `jitpack.yml`, existing scripts | config | build | themselves | exact |

## Pattern Assignments

### `undo/build.gradle.kts` (config)
**Analog:** `providers/build.gradle.kts` lines 1-49. Copy the plugin block minus `kotlin.serialization`, the `java {}` and `kotlin { explicitApi(); jvmTarget 11; -Xjdk-release=11 }` blocks verbatim (lines 9-24); `dependencies { testImplementation(libs.junit) }` only (no `api(project(":core"))`, no coroutines-test, no testFixtures); publication:
```kotlin
val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra
publishing { publications { register<MavenPublication>("release") {
    groupId = engineGroup; artifactId = "voice-action-engine-undo"; version = engineVersion
    from(components["java"]) } } }
```
Last line: `apply(from = rootProject.file("gradle/invariants.gradle.kts"))` (as in sibling modules). Do not copy the OkHttp matrix (lines 52+).

### `gradle/invariants.gradle.kts`
**Analog:** itself. Edit `:272` `allowedEdges` (add `":undo" to emptySet<String>()`), `:273-274` sample edge sets (add `:undo`), error strings at `:295,:300`. Model `verifyUndoZeroDeps` on `verifyCoreDependencyAllowlist` (:373-400): same `compileClasspath`/`runtimeClasspath` loop, filter out `ProjectComponentIdentifier`, but allow only `org.jetbrains.kotlin:kotlin-stdlib` and `org.jetbrains:annotations`, wrap in `if (project.name == "undo")`, fail on an empty classpath (non-vacuous), and `tasks.named("check") { dependsOn(...) }`.

### `$C/commit/CommitSink.kt` + threading `heldRunId` (model, event-driven)
**Analog:** itself. Current shape (:29-35):
```kotlin
public class ActionEvent internal constructor(
    public val runId: String,
    public val parentRunId: String?,
    public val action: ExecutedAction,
) {
    override fun toString(): String = "ActionEvent(runId=$runId, parentRunId=$parentRunId, action=$action)"
}
```
Add `public val heldRunId: String?` (KDoc `@property`, as at :25-27), toString prints presence only. Thread: `ActionDelivery(runId, parentRunId, sink, recorder)` (`ApplyStep.kt:48-54`, only construction site of the event at :58 `ActionEvent(runId, parentRunId, action)`) gets trailing `heldRunId: String? = null`; `CommitCoordinator` (`CommitCoordinator.kt:~38-44`, creates `ActionDelivery(runId, parentRunId, sink, recorder)`) likewise; `HeldCommit.open` (`HeldCommit.kt:58-62`) passes `held.runId`:
```kotlin
return ChildRun(runId, held, CommitCoordinator(runId, held.runId, gate, sink, recorder), recorder)
```
`CommandPipeline.startRun` (~:88) passes nothing. Do not touch `core/api.txt` (Phase 20 regenerates). Test in `core/src/test/.../ActionEventTest.kt`.

### `$C/commit/CompositeSink.kt` (new, utility, event-driven)
**Analog:** `ActionDelivery.deliver` (`ApplyStep.kt:55-60`) and `Guarded.kt`.
```kotlin
withContext(NonCancellable) {
    guardedUncancellable(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) }) {
        sink.onAction(ActionEvent(runId, parentRunId, action))
    }
}
```
Pattern for the composite: `public fun compositeSink(vararg sinks: CommitSink): CommitSink` (require non-empty, copy array); a private class implementing `onAction`/`onRunClosed` that loops children in order, each inside `guardedUncancellable(onFault = { fault -> record first/addSuppressed }) { child.onAction(event) }` (import `core.internal.guardedUncancellable`, `errorClassOf`; never add a new try/catch, `Guarded.kt:51-52` says its `@file:Suppress` is the only one), then rethrow the first fault so the pipeline records `TraceCode.SINK_ERROR`. `toString` prints child count only. Explicit API: `public`, KDoc on every public symbol (see CommitSink.kt:3-20 doc style; plain prose, no emojis).

### `undo/.../UndoResult.kt` (model)
**Analog:** `$C/commit/RunTermination.kt:55-139` (`public sealed class`, internal ctors, redacted `toString`) for the four members `Complete/Refused/Partial/AlreadyUndone`; `$C/commit/ActionKind.kt` for `UndoReason`: `@JvmInline public value class UndoReason internal constructor(public val value: String)` with companion constants. `Blocker`, `NotRestored`, `EntryRef`, `EntityKey` get redacted `toString` (no ids/snapshots; error class name only), mirroring `ExecutedAction.toString` (`CommitSink.kt:65-68`, prints counts and class names).

### `undo/.../internal/Guard.kt` (utility)
**Analog:** `$C/internal/Guarded.kt:1,38-69,92-97`. `:undo` cannot import `:core`/coroutines, so write a non-suspend `inline fun <T> guardedPlain`-style helper (see `guardedPlain` :75-81: catches `LinkageError` then `Exception`, returns `onFault(errorClassOf(e))`) with `@file:Suppress("TooGenericExceptionCaught")`; copy `errorClassOf` (:92-97) for class-name-only reporting. Suspend adapter calls: catch `kotlin.coroutines.cancellation.CancellationException` and rethrow when appropriate (stdlib has it). One suppress per module, or tune by path in `config/detekt/detekt.yml` with a comment; record the choice in the plan.

### `undo/.../UndoTicket.kt`, `EntityAdapter.kt`, `UndoJournal.kt`
**Analog (seam style):** `CommitSink.kt:14-20` (interface of `suspend` methods, KDoc stating call/await/throw semantics) and `PendingMutation` (the `context: Any?` slot read once before apply and returned by identity: `ApplyStep.kt:32-36`, `ExecutedAction.context` `CommitSink.kt:48,61`). Semantics are in RESEARCH Patterns 1-3 (capture-inside-apply, `(runId, position)` identity + journal sequence, whole-group verify then `restoreIf`). No domain-state analog exists in repo; use `ActionLedger.record` (`ActionLedger.kt:41-44`, mutex-serialized position assignment) as the in-repo example of ordered append state, but `:undo` must use `synchronized`, never a held lock across suspend, and no coroutines.

### Tests
- `:undo` tests: JUnit 4 (`testImplementation(libs.junit)` only), stdlib `runSuspending` helper (`startCoroutine` + `CountDownLatch`), no `testFixtures(project(":core"))` (trips `verifyModuleGraph`).
- `:core` tests: `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/` (flat, e.g. `ActionEventTest.kt`); use testFixtures `RecordingCommitSink` (hooks `onActionHook`/`onRunClosedHook` can throw/suspend, `.actions`, `.closes`), `FakeMutation(toolName, behavior, targetIds, context)`, `ScriptedGate`, `ScriptedStrategy`, all in `core/src/testFixtures/kotlin/.../core/testing/`.
- `:sample` e2e (S1-S8, RESEARCH table): real `commandPipeline { commitSink = compositeSink(bridge, recording) }`.

### `sample/.../sample/undo/UndoCommitSink.kt` (bridge)
**Analog:** `RecordingCommitSink.kt` (implements `CommitSink` with `onAction`/`onRunClosed`). Core logic: ignore unless `event.action.applied`; `groupKey = event.heldRunId ?: event.runId`; ticket from `event.action.context`; `journal.record(...)`; any failure -> `journal.withhold(groupKey, ...)`; `onRunClosed` -> `runClosed(groupKey, runId, expectedPositions)` using `termination.executed.filter { it.applied }`. Keep a verbatim copy in a `DocSnippetsTest.kt` `// doc-snippet:start undo-bridge` ... `// doc-snippet:end undo-bridge` region (regions begin at e.g. :109, :149, :226; code inside uses My-prefixed, domain-free names) plus a matching `<!-- doc-snippet: undo-bridge -->` block in INTEGRATION.md (docs gate C06/C07), and a parity test.

### Module plumbing scripts
**Analog:** `scripts/verify-repo-hygiene.sh` (header comment block, `set -euo pipefail`, `ROOT="$(git rev-parse --show-toplevel)"`, `violations=()` + `violate()` accumulation, prints "HYGIENE OK" only when empty). Existing `MODULES="core providers keystore"` lines (`api-dump-isolated.sh:16`, `release-cut.sh:82`) are replaced by a reader of `scripts/modules.list` (`name packaging artifactId kotlinPackage dependsOnCore`). `jitpack.yml` line 6 is the single `./gradlew` line: append `:undo:publishReleasePublicationToMavenLocal`. `settings.gradle.kts:25`: add `":undo"`.

## Shared Patterns

### Never-throw collapse, class name only
**Source:** `$C/internal/Guarded.kt:38-97`. Apply to `compositeSink` (reuse directly) and `:undo` Guard (copy the shape). Errors report `errorClassOf(e)` only.

### Secrets / redacted toString
**Source:** `CommitSink.kt:34,65-68`. Every new public class with user data (entity ids, snapshots, fingerprints, compensator payloads) prints counts/class names only.

### Explicit API + KDoc + additive-only
**Source:** `CommitSink.kt` (public/internal ctor, KDoc `@property`). `explicitApi()` is on; `internal constructor` on result/event types; do not regenerate `core/api.txt`; `undo/api.txt` is header-only `// Signature format: 4.0` seed.

### Hygiene constraints
Package `io.github.ygaray.voiceactionengine.undo` under `io/github/ygaray/voiceactionengine/` (repo-hygiene gate); no `runCatching`, `println`, `android.util.Log`, DI imports, domain words (note/card/food); detekt zero baseline.

## No Analog Found

| File | Role | Reason |
|---|---|---|
| `undo` journal state machine internals (Verifier, Restorer, Footprints union-find) | service | No stateful undo or graph logic in repo; use RESEARCH Pattern 3 and YAT/SB references cited there (`UndoGroupTypes.kt`, `VoiceUndoFootprint.kt`) |

## Metadata

**Analog search scope:** `core/src/{main,testFixtures,test}`, `providers/build.gradle.kts`, `gradle/invariants.gradle.kts`, `scripts/`, `sample/src`, `jitpack.yml`
**Extraction date:** 2026-10-06
