# DISPATCH: how the master runs the isolated fresh-agent wiring test (v1.1.0)

This is the recipe the master follows in plan 19-14. The script `scripts/agent-wiring-test.sh` prepares and judges; the
dispatch itself is the master's job (an executor has no Agent tool). The recipe is the one that passed for v1.0.0 and
v1.0.1 (`.planning/milestones/v1.0-phases/11-cut-v1-0-0/11-WIRING-RERUN.md`, "Isolation audit").

## When to dispatch

Only after plan 19-13's JitPack dry run and clean-clone selftest are green, on the wiring SHA, and inside the same
quiet window (plan 19-14 carries the dispatch as a relayed checkpoint). One Gradle build at a time on this host: the
agent's `./gradlew :jvmconsumer:test :app:compileDebugKotlin` is that build, and the empty-cache `verify` afterwards is
the next one. Never start either while another VAE or other-project Gradle build runs.

## Prepare

`main` stays unpushed through Phase 19, so the engine is published locally and the workspace points at that file
repository:

1. Publish the wiring SHA's tree into an isolated maven-local with the `jitpack.yml` install list (the clean-clone
   selftest of plan 19-13 does this; keep its `m2` directory or repeat it).
2. `scripts/agent-wiring-test.sh prepare-local <m2dir> <version>` prints
   `WIRING PREPARED dir=<dir> version=<version>`. The workspace holds the four docs of HEAD, the skeleton and `TASK.md`
   only: no engine or sample source, no planning tree, no reference solution, no key.
3. For a pushed, JitPack-built SHA the older path is unchanged: `scripts/agent-wiring-test.sh prepare <sha>`.

## Flags re-confirmed from `claude --help` (Claude Code 2.1.292, 2026-10-07)

```text
  --model <model>                       Model for the current session. Provide
                                        an alias for the latest model (e.g.
                                        'fable', 'opus', or 'sonnet') or a
                                        model's full name.
  --no-session-persistence              Disable session persistence - sessions
                                        will not be saved to disk and cannot be
                                        resumed (only works with --print)
  --permission-mode <mode>              Permission mode to use for the session
                                        (choices: "acceptEdits", "auto",
                                        "bypassPermissions", "manual",
                                        "dontAsk", "plan")
  -p, --print                           Print response and exit (useful for
                                        pipes). Note: The workspace trust dialog
                                        is skipped when Claude is run in
```

`--bare` exists in newer builds but was not exercised for this isolation; it is NOT adopted (research, "Wiring test
redesign"). Use exactly the four flags above.

## The recipe

1. Set `WS=<dir>` from the prepare line. Make a throwaway config directory holding only the credentials file:

   ```bash
   CFG="$(mktemp -d)"
   cp "$HOME/.claude/.credentials.json" "$CFG/.credentials.json"
   chmod 600 "$CFG/.credentials.json"
   ```

2. Run the agent headless, with the workspace as the working directory and the throwaway config directory as its
   whole configuration (so no user CLAUDE.md, skills, hooks, memory or MCP servers):

   ```bash
   cd "$WS" && CLAUDE_CONFIG_DIR="$CFG" claude -p --model sonnet --permission-mode bypassPermissions \
     --no-session-persistence "$(cat TASK.md; printf '\nYour working directory is %s.\n' "$WS")"
   ```

   The prompt is `TASK.md` verbatim plus one line naming the working directory, nothing else.
3. Afterwards remove the throwaway directory: `rm -rf "$CFG"` (record `cfg_removed=yes`).

## Isolation audit (record all of it in `WIRING-RERUN.md`)

- No `CLAUDE.md` in the workspace or in any ancestor directory of it (`ancestors_clean=yes`): check
  `find` upward from `$WS` for `CLAUDE.md` and `.claude`.
- `CONSULTED.md` names only workspace paths (`TASK.md`, `docs/*.md`, the Gradle files, the app manifest). Any absolute
  path, `~` path or engine path fails the audit (`consulted_only_workspace`).
- No network fetch: the agent's final message and `CONSULTED.md` show no URL being opened. The repository is a
  `file://` URL.
- No engine source read: no path under the host repository appears in `CONSULTED.md`.
- `find "$WS" -name .credentials.json` finds nothing; `STUMBLES.md` and `CONSULTED.md` are scanned for key-shaped strings
  before they are copied anywhere.

## Verify

`scripts/agent-wiring-test.sh verify <dir> <version>` from an EMPTY Gradle cache against the repository in the
workspace's `settings.gradle.kts` (the file repository for a local prepare). Expected last line:
`WIRING TEST: PASS checks=13`. One `WIRING TEST: FAIL <id>: <why>` line per failed check otherwise.

## The record the plan-14 executor writes

`.planning/releases/v1.1.0/WIRING-RERUN.md`, with front matter:

```yaml
status: pass | fail
tested_sha: <full sha the docs and engine were taken from>
consulted_only_workspace: true | false
cfg_removed: yes | no
ancestors_clean: yes | no
```

and the body: the verbatim `WIRING TEST:` line(s), the isolation audit result, the agent's `STUMBLES.md` and
`CONSULTED.md` verbatim.

## Rerun rule

Any edit to README.md, INTEGRATION.md, API.md or ECOSYSTEM.md after the pass voids it: those four files are exactly
what the agent reads. A docs edit after the pass needs a new `prepare-local` and a new dispatch on the new SHA. A script
or reference-solution fix is legal before the wiring SHA only.
