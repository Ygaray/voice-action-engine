---
status: pending-rerun
mechanical_verdict: PASS
tested_sha: 338d85ffa3
rerun_sha: TBD
date: 2026-10-01
dispatched_by: milestone master (relayed answer p10-dispatch-answer.txt; fresh general-purpose Sonnet subagent)
---

# 10-WIRING-TEST: D-07 agent wiring test (VER-04)

## Result on 338d85ffa3

`scripts/agent-wiring-test.sh verify /home/yahir/.cache/vae-wiring-test/338d85ffa3 338d85ffa3` (empty Gradle cache,
JitPack, `--no-daemon`):

```text
WIRING TEST: PASS checks=9
```

The fresh agent reported `./gradlew :jvmconsumer:test :app:compileDebugKotlin` green with both `WireTest` tests passing.
The mechanical judge agrees. It also recorded 12 stumbles (below), so the docs were not good enough to call this a
clean pass: they were fixed in Phase 10 and the test must be rerun against the fixed docs (status `pending-rerun`).

Tested SHA 338d85ffa3 adds only a `.planning` commit on top of 42a12ae and is doc/API-identical to it. Phase 10 creates
no tag.

## Isolation caveat (recorded honestly)

The subagent ran inside this repository's Claude session, so the harness auto-loaded this repository's
`.claude/CLAUDE.md` into its context. It admitted taking the `datastore-preferences` version 1.2.1 from those pins. It
still recorded the missing dependency line as a stumble, and every other gap was found from the docs plus compiler
output. `CONSULTED.md` lists only workspace files (`docs/README.md`, `docs/INTEGRATION.md`, `docs/API.md`, the build
files, `TASK.md`): no outside path. The rerun cannot fully remove the CLAUDE.md leak either; the verdict is therefore
"docs-only plus a project pin file", which is weaker than a pristine run and is stated as such.

## Stumbles and the doc fix for each (all fixed in Phase 10 after the test)

| # | Stumble (agent's words, condensed) | Fix |
|---|---|---|
| 1 | README says every type is in `io.github.ygaray.voiceactionengine.*`; real packages are sub-packages and no doc lists them | README "Minimal usage" now carries the exact import block for its snippet and states the types are in sub-packages |
| 2 | API.md header: "`core` has no suffix beyond `.core`" and transports "under `.providers`" are wrong or misleading | API.md header reworded; new "Packages and imports" section maps every public type to its package (incl. `commandPipeline`, `openAi`/`openRouter`) |
| 3 | INTEGRATION step 4 and `minimal-pipeline`: no import list or type-to-package table | INTEGRATION header points to API.md "Packages and imports"; README has the copy-paste block |
| 4 | `kotlinx-serialization-json` need and transitivity not stated | INTEGRATION step 2 and API.md state that `core` exposes serialization-json and coroutines-core as `api` |
| 5 | `:keystore` needs `datastore-preferences`; no dependency line or version | INTEGRATION step 2 states `:keystore` exposes it as an `api` dependency (the keystore build verifies the POM and module metadata), so no extra line is needed |
| 6 | `CredentialLookup.Missing` is a class: examples never show `Missing()` | INTEGRATION step 7 and API.md "Credentials and keystore" spell `Missing()` |
| 7 | scripted-provider snippet: `Usage(100, 0, 0, 20)` arguments unexplained | INTEGRATION step 10 states the four-argument order |
| 8 | no test libraries named (`kotlinx-coroutines-test`, JUnit) | INTEGRATION step 2 names `junit:junit` and `kotlinx-coroutines-test` as the consumer's own test dependencies |
| 9 | `render-outcome` snippet depends on `keyAdvice` from step 7 | INTEGRATION step 9 says to copy `keyAdvice` with it |
| 10 | README links `ECOSYSTEM.md`, absent from the doc set given | `scripts/agent-wiring-test.sh` now copies ECOSYSTEM.md into `docs/`; AGENT-PROMPT names it |
| 11 | `sample/...` pointers cannot be followed from a consumer workspace | README and INTEGRATION header say the sample is in the repository (GitHub URL), not in the artifacts, and that each step stands alone |
| 12 | keystore AAR `minSdk 35` not mentioned in INTEGRATION step 3 (README Requirements has it; not a blocker) | Left as is: README Requirements states it; recorded as minor |

`scripts/verify-docs-coverage.sh` still prints `DOC COVERAGE OK checks=23 types=96` after the edits, and
`DocSnippetsTest` (the compiled snippets) is unchanged.

## Rerun rule and carry

Any change to `README.md`, `INTEGRATION.md`, `API.md` or `ECOSYSTEM.md` or to a public engine signature after the tested
SHA means a rerun before Phase 11 cuts the tag. The tested SHA must precede the v1.0.0 tag and be API-identical to it.
The docs were changed after 338d85ffa3, so a rerun is required. Phase 11 must not cut v1.0.0 until this file records
`status: pass` for a SHA that is an ancestor of the tag commit and API-identical to it. Phase 10 creates no tag.

Rerun procedure (master-performed; the executor has no Agent tool):

1. `git pull --rebase --autostash` then `git push origin main` (stage nothing new; never `.planning/graphs/` or other
   bookkeeping files).
2. `SHA10=$(git rev-parse --short=10 origin/main)`.
3. `scripts/jitpack-live-probe.sh $SHA10` until it prints `LIVE PROBE PASS`.
4. `scripts/agent-wiring-test.sh prepare $SHA10` (prints `WIRING PREPARED dir=<dir>`).
5. Dispatch a FRESH subagent whose entire prompt is `<dir>/TASK.md`, working directory `<dir>`. To close the isolation
   caveat, dispatch it from a session outside this repository (or one that does not load this repository's
   `.claude/CLAUDE.md`).
6. `scripts/agent-wiring-test.sh verify <dir> $SHA10`, then record `status: pass|fail`, the stumbles and CONSULTED.md here.
