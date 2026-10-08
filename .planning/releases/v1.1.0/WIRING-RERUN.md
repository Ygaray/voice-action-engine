---
status: pass
tested_sha: 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1
tested_sha10: 4bdb663b4c
verify_line: "WIRING TEST: PASS checks=13"
isolation: headless claude -p --model sonnet --permission-mode bypassPermissions --no-session-persistence, throwaway CLAUDE_CONFIG_DIR (credentials only), removed
consulted_only_workspace: true
cfg_removed: yes
ancestors_clean: yes
date: 2026-10-08
release: v1.1.0
dispatched_by: the milestone master / orchestrating layer (headless run delegated by the master; plan 20-09 Task 1)
---

# WIRING-RERUN: isolated fresh-agent wiring test (v1.1.0), Phase 20 record

Tested SHA = `4bdb663b4c7c1bf02d4588751705dfcc8b356ef1` (W, the Phase 20 wiring SHA: the last commit that changes anything outside
`.planning/`, fixed in window 20-07b and pushed to origin/main in plan 20-08). The engine came from **JitPack**, not from a local
publication: the workspace was prepared with `scripts/agent-wiring-test.sh prepare 4bdb663b4c` (the JitPack path, not `prepare-local`), its
`settings.gradle.kts` lists `google()`, `mavenCentral()` and `https://jitpack.io` only, and the version it resolves (`4bdb663b4c`) is the same
JitPack ref that the 20-08 live probe proved (`LIVE PROBE PASS ref=4bdb663b4c`, JitPack commit = W). The four docs in the workspace were
byte-identical to `git show 4bdb663b4c:<doc>` (`cmp` on README.md, INTEGRATION.md, API.md and ECOSYSTEM.md), and none of the four changed
between W and HEAD. This record replaces the Phase 19 record (tested SHA `090fd8ec76`, local publication), which stays in git history.

## Verify output (verbatim, empty Gradle cache, JitPack)

Command: `scripts/agent-wiring-test.sh verify /tmp/vae-wiring-20 4bdb663b4c`, low-memory recipe in `GRADLE_OPTS`
(`-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process
-Dorg.gradle.jvmargs=-Xmx1536m`), run once, 2026-10-08T01:19:34Z to 01:21:03Z, exit status 0, MemAvailable 12319880 kB before and
12305772 kB after. No earlyoom kill, no retry.

```text
WIRING TEST: PASS checks=13
```

The agent's own build, before the verify (its own words): `./gradlew :jvmconsumer:test :app:compileDebugKotlin` green, all six tests in
`WireTest.kt` passing; it wrote `Wire.kt`, `WireTest.kt` and `AppWire.kt`; the plan-tier and undo tests also start the walk through a scripted
router answer; the app module depends on core, keystore and undo, not providers.

## Dispatch facts

- Prepared: `WIRING PREPARED dir=/tmp/vae-wiring-20 version=4bdb663b4c` (plan 20-09 Task 1, 2026-10-08T01:14Z; `selftest-source` exit 0,
  `WIRING SOURCE SELFTEST OK`). The workspace sits under `/tmp` via `WIRING_DIR`, so no ancestor directory holds a CLAUDE.md or a `.claude`.
- Process: a separate headless `claude -p --model sonnet --permission-mode bypassPermissions --no-session-persistence`, cwd = the workspace,
  `CLAUDE_CONFIG_DIR` = a throwaway directory under the session scratchpad (`wiring-cfg-20-09`) holding only a copy of `.credentials.json`,
  never the real `~/.claude`. The prompt was `TASK.md` (4101 bytes) verbatim plus one working-directory line.
- Dispatched by the milestone master / orchestrating layer: start 2026-10-08T01:16:18Z, end 2026-10-08T01:18:42Z, agent exit 0; the agent's
  final message is 1151 bytes. The throwaway config directory was removed afterwards with a plain `rm` (`cfg_removed=yes`).
- One Gradle process at a time: the agent's build, then the verify. The agent's idle Gradle daemon (started 01:17:46Z, ~1 GiB RSS) was still
  alive at verify start; it was not touched (never killed, no `--stop`), and the verify ran `--no-daemon` with its own empty cache.
- The window is `20-QUIET-WINDOW-02b.md` (quiet window 20-09, opened 2026-10-08T01:12:35Z, relayed by yahir-gsd-control-plane-3b).
- The workspace `/tmp/vae-wiring-20` was removed with a plain `rm -rf` at 2026-10-08T01:21Z, after the evidence was copied.

## Isolation audit

- No `CLAUDE.md` and no `.claude` in the workspace or in any ancestor up to `/` (`find` in the workspace, `ls -a` of `/tmp` and `/`):
  `ancestors_clean=yes`.
- `CONSULTED.md` names only workspace-relative paths (`docs/README|INTEGRATION|API|ECOSYSTEM.md`, `TASK.md`, the Gradle files, the app
  manifest and `jvmconsumer/build/test-results/test/`, the agent's own test output): no line starts with `/` or `~`, no host-repository or
  engine path. `consulted_only_workspace: true`. Copied verbatim to `.planning/releases/v1.1.0/evidence/wiring-consulted-p20.txt`.
- No network fetch by the agent: neither the agent's final message nor `CONSULTED.md` shows a URL (`grep -E 'https?://'`: no hit). Only the
  Gradle build reaches the repositories in `settings.gradle.kts` (google, mavenCentral, jitpack.io).
- No engine or sample source read: no path of engine or sample source appears in `CONSULTED.md` (`grep` for `voice-action-engine`,
  `/sample`, `core/src`, `providers/src`, `keystore/src`: no hit), and the workspace held no engine or sample source (docs, skeleton,
  `TASK.md` and the agent's own three files only).
- `find /tmp/vae-wiring-20 -name .credentials.json`: nothing. `STUMBLES.md`, `CONSULTED.md` and the agent's final message were scanned for
  key-shaped strings, URLs and home paths (`https?://`, `/home/`, `~/`, `sk-…`, `AKIA`, `ghp_`, `api_key`, `Bearer `) before being copied:
  no hit.

## Stumbles (verbatim; clarity input for a later patch, NOT fixed here)

Copied verbatim to `.planning/releases/v1.1.0/evidence/wiring-stumbles-p20.txt`. A fix to any of these is a doc change to README,
INTEGRATION, API or ECOSYSTEM and would void this pass (Rerun rule).

```text
- API.md, "Shapes you construct or read" (and INTEGRATION.md §5 plan tier): `StepResult` is used positionally as `StepResult(text, isError, <third>, targetIds)` but its constructor is never listed and the third parameter (passed `null`) is never named or explained. Needed: the signature, to return `targetIds` for the plan tier. Resolved by copying the call shape from the plan-tier snippet.
- API.md, "Shapes you construct or read" / INTEGRATION.md §5 (SingleShot): `Resolution.Escalate(reason, carry)` and `EscalationReason.Other(code)` are used (prose in §5 and no snippet) but neither constructor is in API.md. Needed: arity and the type of the second argument. Resolved by following the §5 prose literally, `Resolution.Escalate(EscalationReason.Other("read_call"), null)`, and it compiled.
- INTEGRATION.md §11 / API.md ("undo" table): `UndoCommitSink` is described as the reference wiring but is not a published type, so it has to be copied (about 45 lines) into the app. `PendingMutation.context`, `ExecutedAction.position`, and `ActionEvent.heldRunId` are used by that bridge but are not listed in API.md. Resolved by copying the snippet verbatim.
```

## Triage

**None of the five Phase 19 stumbles recurred** (carry C9; plan 20-04 doc fixes): no stumble on the router's answer shape (the agent
scripted the router answer in its plan and undo tests without a stumble), the `submit_plan` argument schema, the undo-bridge imports, SingleShot
with a read tool in the snapshot (the doc's read-call recipe, INTEGRATION.md §5 `Resolution.Escalate(EscalationReason.Other("read_call"),
null)`, is what the agent followed and it compiled), or the `UndoJournal { }` `store` shadowing.

Each new stumble was checked against the four docs of W and against the published source of W. All three are **clarity-only** (information
missing from a reference table; every name and shape the docs give is correct). None is a factual documentation error, so none blocks the tag.

| # | Stumble | Evidence (docs of W vs source of W) | Class | Patch later in |
|---|---|---|---|---|
| S1 | `StepResult` constructor and its third argument not listed | API.md line 108 lists `StepResult` as a class with no constructor. The snippets are right: INTEGRATION.md line 510 `StepResult("""…""", false, null, mapOf("id" to row.id))` matches `StepResult(contentForModel: String, isError: Boolean, appOutcomeToken: String?, targetIds: Map<String, String>)` in `core/commit/ToolStep.kt`, plus the 1- and 2-argument secondary constructors the other snippets use. Nothing is misnamed. | clarity-only | API.md "Shapes you construct or read": add the `StepResult` constructors and name `appOutcomeToken` |
| S2 | `Resolution.Escalate(reason, carry)` and `EscalationReason.Other(code)` constructors not listed in API.md | INTEGRATION.md line 199 gives the exact call, and it matches the source: `Resolution.Escalate(reason: EscalationReason, carry: Any?)` (`core/strategy/OutcomeResolver.kt`) and `EscalationReason.Other(code: String)` (`core/failure/EscalationReason.kt`). The agent followed it and it compiled. API.md lists only `FailureReason.Other(code)` and `EscalationReason.NoToolCall()`. | clarity-only | API.md "Shapes you construct or read": add the `Resolution.Escalate(reason, carry)` and `EscalationReason.Other(code)` rows |
| S3 | `UndoCommitSink` is copy-in wiring, not a published type; `PendingMutation.context`, `ExecutedAction.position`, `ActionEvent.heldRunId` "not listed" | INTEGRATION.md §11 gives the whole bridge as the `undo-bridge` snippet (`class UndoCommitSink(...) : CommitSink`) and names the sample file as the reference. It does not claim a published type, but it does not say "copy this into your app" either. The members exist exactly as used (`CommitSink.kt`: `ActionEvent.heldRunId`, `ExecutedAction.position`, `ExecutedAction.context`; `ToolStep.kt`: `PendingMutation.context`). API.md line 120 does list `heldRunId`, and line 121 lists `ExecutedAction` "context". `position` is only implied (EntryRef, line 198), and `PendingMutation.context` appears only in INTEGRATION.md line 1046. The agent's "not listed" is partly mistaken (`heldRunId` is listed), and nothing is wrong. | clarity-only | INTEGRATION.md §11: say that `UndoCommitSink` is app code to copy, not a library type. API.md `ExecutedAction` / `PendingMutation` rows: name `position` and `context` |

The class of every row was decided on evidence. Every name and shape matched the source, so this is not an error, and the gap is all
missing reference-table entries. These three are recorded as input for a later docs patch (a v1.1.x patch or v1.2). They are NOT fixed before
the v1.1.0 cut: a fix would edit the four docs, which voids this pass.

## Consulted (verbatim)

```text
docs/README.md
docs/INTEGRATION.md
docs/API.md
docs/ECOSYSTEM.md
TASK.md
settings.gradle.kts
build.gradle.kts
gradle.properties
app/build.gradle.kts
jvmconsumer/build.gradle.kts
app/src/main/AndroidManifest.xml
jvmconsumer/build/test-results/test/
```

## Rerun rule

Any edit to README.md, INTEGRATION.md, API.md or ECOSYSTEM.md after this pass voids it, because those four files are exactly what the agent
reads. A docs edit needs a new `prepare` on the new pushed SHA (or `prepare-local` for an unpushed tree) and a new dispatch. A script or
reference-solution fix is legal only before the wiring SHA. `scripts/release-cut.sh gate wiring <sha>` reads this record from HEAD. It requires
`status: pass`, a `tested_sha` equal to the full SHA given, `consulted_only_workspace: true` and a `WIRING TEST: PASS` line. W must stay
`4bdb663b4c7c1bf02d4588751705dfcc8b356ef1` through the cut, and only `.planning` commits may follow it.
