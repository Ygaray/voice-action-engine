---
status: pass
tested_sha: 5f0a6b03b41d97df143e1badc59db18bed49b41e
tested_sha10: 5f0a6b03b4
verify_line: "WIRING TEST: PASS checks=9"
isolation: headless claude -p --model sonnet --no-session-persistence, throwaway CLAUDE_CONFIG_DIR (credentials only), removed
consulted_only_workspace: true
cfg_removed: yes
ancestors_clean: yes
date: 2026-10-03
release: v1.0.1
dispatched_by: milestone master (isolated run performed 2026-10-03)
---

# 11-WIRING-RERUN: isolated fresh-agent wiring test (tag-gating record), current = v1.0.1 on W'

W' = `5f0a6b03b41d97df143e1badc59db18bed49b41e` (short `5f0a6b03b4`): the v1.0.1 patch head (XR-171-01, XR-171-03 ruling a, doc patch, tag-agnostic release tooling). `scripts/jitpack-live-probe.sh 5f0a6b03b4` gave LIVE PROBE PASS.

## Verify output (verbatim, empty Gradle cache, JitPack)

Command: `scripts/agent-wiring-test.sh verify /home/yahir/.cache/vae-wiring-test/5f0a6b03b4 5f0a6b03b4`

```text
WIRING TEST: PASS checks=9
```

## Isolation audit

- Process: a separate headless `claude -p --model sonnet --permission-mode bypassPermissions --no-session-persistence`, with cwd = the workspace and `CLAUDE_CONFIG_DIR` = a throwaway directory holding only `.credentials.json` (removed afterwards: cfg_removed=yes). The prompt was TASK.md verbatim plus one working-directory line.
- No ancestor of the workspace has a CLAUDE.md (ancestors_clean=yes). CONSULTED.md lists only workspace files (docs/README|INTEGRATION|API|ECOSYSTEM.md, TASK.md, the Gradle files, the app manifest): `.planning/releases/v1.0.1/evidence/wiring-consulted.txt`.
- The agent reported `./gradlew :jvmconsumer:test :app:compileDebugKotlin` green with both tests passing. It hit 4 minor stumbles, recorded as v1.0.x/v1.1 doc follow-ups and NOT fixed before the tag: `.planning/releases/v1.0.1/evidence/wiring-stumbles.txt`.

---

# Previous record: v1.0.0 (W = be49ea8fc5), kept for history

# 11-WIRING-RERUN: isolated fresh-agent wiring test on W (tag-gating record)

W = `be49ea8fc5036f0cb3c8203a0d3accb19146480a` (short `be49ea8fc5`), pushed to origin/main and JitPack-built (`evidence/wiring-sha-jitpack.txt`, LIVE PROBE PASS). It carries the Phase 11 API changes and doc fixes (plans 11-02, 11-03) plus the 11-04/11-05 release tooling, so it supersedes the `36c578f464` pass recorded in `10-WIRING-TEST.md` for the tag.

## Verify output (verbatim, empty Gradle cache, JitPack)

Command: `scripts/agent-wiring-test.sh verify /home/yahir/.cache/vae-wiring-test/be49ea8fc5 be49ea8fc5`

```text
WIRING TEST: PASS checks=9
```

Master's resume line (verbatim): `wiring dir=/home/yahir/.cache/vae-wiring-test/be49ea8fc5 sha=be49ea8fc5 cfg_removed=yes ancestors_clean=yes` then `judge: WIRING TEST: PASS checks=9`. The agent reported `./gradlew :jvmconsumer:test :app:compileDebugKotlin` green on the first compile, both tests passing.

## Isolation audit

- Process: separate headless `claude -p --model sonnet --no-session-persistence`, cwd = the workspace, `CLAUDE_CONFIG_DIR` = a throwaway directory holding only `.credentials.json`; prompt = TASK.md verbatim plus one working-directory line.
- Every CONSULTED.md entry is a relative path inside the workspace (TASK.md, docs/README|INTEGRATION|API|ECOSYSTEM.md, the Gradle files, the app manifest): holds, `consulted_only_workspace: true`.
- `find <workspace> -name .credentials.json`: nothing (checked on resume).
- Master reported `cfg_removed=yes` and `ancestors_clean=yes`.
- STUMBLES.md and CONSULTED.md were scanned for key-shaped strings and fixture references before copying: no hit.

## STUMBLES (verbatim)

- INTEGRATION.md, section 10 (scripted-provider snippet): the snippet has no import list, unlike the README snippet; I had to map `ModelResult`, `ProviderRequest`, `ModelResponse`, `AssistantMessage`, `AssistantPart`, `StopReason`, `Usage` and `FailureReason` to packages from API.md "Packages and imports". Resolved that way; it compiled first time.
- API.md, "Packages and imports"/"Surface at a glance": `FailureReason.Other(code)`, `ModelResult.Success(ModelResponse(...))`, `ModelResult.Failure(reason)` and the `ModelResponse`/`AssistantMessage` constructor shapes appear only inside the section 10 snippet, not in the API tables; I copied them from there.
- INTEGRATION.md, section 9 (render-outcome): the snippet needs `Clarification`, `ClarificationOption`, `EscalationReason`, `FailureReason`, `CommandOutcome` imports, and `outcome.reply`, `reason.code`, `FailureReason.NotConfigured.provider`, `EscalationReason.NoToolCall` are not described in API.md; I used the snippet's usage as-is.
- INTEGRATION.md, section 3 vs the workspace: the docs call for the INTERNET permission only for the provider transports; the scratch `:app` manifest had none. I added it, since `providers` is a dependency of `:app`. Not a doc error, but section 3 does not say it is only needed when `providers` is used.
- ECOSYSTEM.md, header: refers to `~/.claude/context/workflows/repin.md` and `~/.claude/context/deps/_index.md`, paths outside the consumer's workspace that an integrator cannot read; ignored.
- README.md, "Requirements"/INTEGRATION.md step 2: no Kotlin/AGP guidance for the `:app` module (e.g. whether a Kotlin Android plugin is needed with AGP 9, and that the `:app` needs `kotlinx-serialization` only transitively). It was not needed here, so no action.

## CONSULTED (verbatim)

TASK.md
docs/README.md
docs/INTEGRATION.md
docs/API.md
docs/ECOSYSTEM.md
build.gradle.kts
settings.gradle.kts
gradle.properties
jvmconsumer/build.gradle.kts
app/build.gradle.kts
app/src/main/AndroidManifest.xml

## New doc gaps (post-tag v1.0.x doc-patch follow-ups; none is HOLD)

Fixing any of these before the tag is a doc change, which voids W and forces a new isolated rerun (master ruling 2026-10-01). A docs-only patch leaves the API unchanged but still needs its own isolated wiring run.

1. INTEGRATION.md section 10: scripted-provider snippet has no import list. Follow-up.
2. API.md "Surface at a glance": constructor shapes of `FailureReason.Other`, `ModelResult.Success/Failure`, `ModelResponse`, `AssistantMessage` appear only in the section 10 snippet. Follow-up.
3. INTEGRATION.md section 9 / API.md: render members (`outcome.reply`, `reason.code`, `FailureReason.NotConfigured.provider`, `EscalationReason.NoToolCall`) are not described. Follow-up.
4. INTEGRATION.md section 3: does not say INTERNET is needed only when `providers` is used. Follow-up.
5. ECOSYSTEM.md header: links `~/.claude/context/...` paths that a consumer cannot reach (private-host paths in a public doc). The one worth doing first.
6. README.md / INTEGRATION.md step 2: no Kotlin/AGP guidance for the consumer `:app`. Follow-up.

## Ordering rule

The tag commit must be W or a descendant whose `git diff --name-only W` lists only the three `api.txt` files and `.planning/` paths (and, at most, section 11 ledger rows of CROSS-REPO-SCOPE-CONTRACT.md, which `scripts/release-cut.sh gate diff` reports as a ledger-only DIFF NOTE).
