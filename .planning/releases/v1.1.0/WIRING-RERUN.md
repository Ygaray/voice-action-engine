---
status: pass
tested_sha: 090fd8ec761178d5922523faaf24dc3ffb7b686b
tested_sha10: 090fd8ec76
verify_line: "WIRING TEST: PASS checks=13"
isolation: headless claude -p --model sonnet --permission-mode bypassPermissions --no-session-persistence, throwaway CLAUDE_CONFIG_DIR (credentials only), removed
consulted_only_workspace: true
cfg_removed: yes
ancestors_clean: yes
date: 2026-10-07
release: v1.1.0
dispatched_by: orchestrator (headless run delegated by the milestone master; plan 19-14 Task 1)
---

# WIRING-RERUN: isolated fresh-agent wiring test (v1.1.0), Phase 19 record

Tested SHA = `090fd8ec761178d5922523faaf24dc3ffb7b686b` (the Phase 19 wiring SHA candidate: the last commit that changes anything outside `.planning/`). The engine was NOT taken from JitPack (`main` is unpushed through Phase 19): the docs, the skeleton and `TASK.md` come from that tree, and the engine came from plan 19-13's clean-cache dry-run publication (`dryrun-ec24a19786`, HEAD `ec24a19`, whose non-`.planning` content is identical to the tested SHA: `git diff 090fd8ec76 ec24a19 -- . ':!.planning'` is empty) in an isolated file-repository maven-local. Phase 20 re-runs this test on its own final SHA (carry register C4); this record is rewritten there.

## Verify output (verbatim, empty Gradle cache, local file repository)

Command: `scripts/agent-wiring-test.sh verify /tmp/vae-wiring-19/ws dryrun-ec24a19786` (low-memory Gradle recipe), 2026-10-07T18:19:15Z to 18:20:44Z, exit status 0, MemAvailable 8237708 kB before.

```text
WIRING TEST: PASS checks=13
```

The agent's own build, before the verify: `./gradlew :jvmconsumer:test :app:compileDebugKotlin` green, six `WireTest` tests passing (grammar, plan, the router, undo-all and the keystore).

## Dispatch facts

- Prepared: `WIRING PREPARED dir=/tmp/vae-wiring-19/ws version=dryrun-ec24a19786` (the workspace sits under `/tmp` via `WIRING_DIR`, so no ancestor directory holds a CLAUDE.md or a `.claude`).
- Process: a separate headless `claude -p --model sonnet --permission-mode bypassPermissions --no-session-persistence`, cwd = the workspace, `CLAUDE_CONFIG_DIR` = a throwaway directory holding only `.credentials.json` (removed afterwards: `cfg_removed=yes`). The prompt was `TASK.md` verbatim plus one working-directory line.
- Dispatch 2026-10-07T18:14:19Z, finish 2026-10-07T18:18:25Z, agent exit 0, nothing on stderr.
- The window is plan 19-13/19-14's single relayed quiet window (`19-QUIET-WINDOW.md`); the mempalace mine ran beside it under the orchestrator ruling and was never touched.

## Isolation audit

- No `CLAUDE.md` and no `.claude` in the workspace or in any ancestor up to `/` (`find` upward from the workspace): `ancestors_clean=yes`.
- `CONSULTED.md` names only workspace-relative paths (`TASK.md`, `docs/README|INTEGRATION|API|ECOSYSTEM.md`, the Gradle files, the app manifest): no absolute path, no `~` path, no engine path. `consulted_only_workspace: true`. Copied verbatim to `.planning/releases/v1.1.0/evidence/wiring-consulted.txt`.
- No network fetch: the agent's final message and `CONSULTED.md` show no URL; `settings.gradle.kts` points at a `file://` maven-local only.
- No engine source read: no path under the host repository appears in `CONSULTED.md`, and the workspace holds no engine or sample source (docs, skeleton, `TASK.md` only).
- `find <workspace> -name .credentials.json`: nothing. `STUMBLES.md`, `CONSULTED.md` and the agent's final message were scanned for key-shaped strings, URLs and home paths: no hit.

## Stumbles (verbatim; Phase 20 input, NOT fixed here)

Copied verbatim to `.planning/releases/v1.1.0/evidence/wiring-stumbles.txt`. Fixing any of these before Phase 20's cut is a doc change to README, INTEGRATION, API or ECOSYSTEM and voids this pass.

- INTEGRATION.md, "Choosing where the model walk starts" (and API.md "Telemetry and trace"): a test must script the router's answer, but the tool name and argument shape of the router's forced call are not given (only the trace turn name `pick_start_tier` is mentioned). I needed the answer shape; I guessed a tool call `pick_start_tier` with `{"tier":"plan"}` and confirmed it by the trace's `selection.picked` being `plan`. Resolved by guess plus assertion, without reading any engine source.
- INTEGRATION.md, "The plan tier" and API.md "Strategies and tools": a test must script the model's `submit_plan` answer, but the argument schema is not documented (field names for steps, step id, tool name, arguments, `needs_lookup`). I guessed `{"steps":[{"id","tool","arguments"}]}` and confirmed it by the second step receiving `$s1.id` resolved to the first item's id.
- INTEGRATION.md, section 11 "The bridge" (`undo-bridge` block): the block lists no imports, and the later imports list covers only the undo types. The block also needs `EntryRef` (undo package), `ActionEvent`, `ActionKind`, `CommitSink`, `RunTermination` (core.commit) and `java.util.concurrent.ConcurrentHashMap`. `EntryRef` appears only in the API.md package table, so I found its package there.
- INTEGRATION.md, section 5 "SingleShot" vs the task's "SingleShot over a mutating tool and a read tool": the docs say a SingleShot tier cannot serve reads (only the first tool call is resolved), so a read tool in its snapshot is only advisory. I offered `find_items` in the snapshot but force `create_item` and resolve only that. Unclear what the doc intends a SingleShot resolver to do with a read call.
- INTEGRATION.md, section 11 / API.md "undo": the `UndoJournal { }` builder has a `store` property (for `JournalStore`). Inside the builder a consumer's own `store` variable is shadowed by it, so `adapter(ItemAdapter(store))` failed to compile with a confusing type mismatch (`JournalStore?` vs my store). Not a doc error, but the doc's own snippet only works because its variable is not named `store`; worth a warning. I renamed mine to `items`.

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
```

## Rerun rule

Any edit to README.md, INTEGRATION.md, API.md or ECOSYSTEM.md after this pass voids it: those four files are exactly what the agent reads. A docs edit needs a new `prepare-local` (or `prepare` for a pushed SHA) and a new dispatch on the new SHA. A script or reference-solution fix is legal only before the wiring SHA. `scripts/release-cut.sh gate wiring <sha>` reads this record from HEAD and requires `status: pass`, `tested_sha` equal to the SHA given, `consulted_only_workspace: true` and a `WIRING TEST: PASS` line; Phase 20 re-runs the test on its final SHA and rewrites this file.
