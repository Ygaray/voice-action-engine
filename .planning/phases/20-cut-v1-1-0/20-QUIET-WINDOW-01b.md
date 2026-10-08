# 20-07 host quiet window 20-07b (re-run of plan 20-07 Task 3 after the 20-13 fix)

grant: consumed
relayed_by: yahir-gsd-control-plane-3b
date: 2026-10-08T00:16:49Z
opened: 2026-10-08T00:16:49Z
timebox_s: 14400
gates_started: 2026-10-08T00:17:19Z
heavy_gates: green
wiring_sha: 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1
closed: 2026-10-08T00:42:25Z

Supersedes the red first attempt recorded in `20-QUIET-WINDOW-01.md` (window 20-01, `selftest all` red on the stale
`contract-ledger-only` fixture, fixed by plan 20-13 at 4bdb663). Only the orchestrator relay may change the `grant` line. The timebox
clock (14400 s) starts at `gates_started:`.

## Relay

Request string was "quiet window 20-07b" (sent by the master after plan 20-13). Terms: R2 as ruled in `20-QUIET-WINDOW-01.md`
(swap-full accepted provided MemAvailable at least 8 GiB at window open; pause a gate below 5 GiB with a single bounded re-check, then
return a checkpoint rather than run; single Gradle process; one retry per earlyoom kill; at most 4 h).

## Relay log (verbatim)

Resume signal: `quiet window 20-07b open 2026-10-08 (UTC now) relayed_by=yahir-gsd-control-plane-3b`

Orchestrator verbatim (open): "open 20-07b. I hold the lock (db12d34). I verified the W candidate myself: 7f11d0ec..4bdb663 touches only scripts/release-cut.sh (+65/-2), so the gate 10/12 OKs stand. MemAvailable 10.2 GiB; same R2 terms. Send "quiet done" with the selftest result line, the bash gate lines and the fixed W."

## Pre-checks

At open (2026-10-08T00:16:49Z), HEAD f285c20238132204d248bc2c532d7c9547d74cec:

```
MemAvailable:   10673448 kB   (10.18 GiB, at least 8 GiB: R2 ruling condition MET)
SwapTotal:       2097148 kB
SwapFree:            200 kB   (about 0%, under the 25% rule; accepted by the relayed R2 ruling)
pgrep -af '[G]radleDaemon': empty (no Gradle daemon)
git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd': empty
```

## Results

Recipe for every Gradle-driving step: `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false
-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"`; output to a log file in the session scratchpad
(never a pipe), exit status read directly.

### Step 1: `scripts/release-cut.sh gate api-baseline v1.1.0`

start 2026-10-08T00:17:15Z, end 2026-10-08T00:17:15Z, exit 0. Pre: MemAvailable 10696288 kB, SwapFree 200 kB. Final lines:

```
API NOTE: new in this release (no baseline): undo voice-adapter
API NOTE: api.txt compared with the baseline released in v1.0.1 (additive only)
```
GATE OK api-baseline

### Step 2: `scripts/release-cut.sh gate api-dump` (gate 10)

start 2026-10-08T00:17:19Z (`gates_started`), end 2026-10-08T00:17:36Z, exit 0. Pre: MemAvailable 10734828 kB, SwapFree 200 kB, no VAE
Gradle running. Post: MemAvailable 10685032 kB, SwapFree 200 kB. Whole output (one line):

GATE OK api-dump

### Step 3: `scripts/release-cut.sh gate api-check v1.1.0` (gate 12)

start 2026-10-08T00:17:57Z, end 2026-10-08T00:19:18Z, exit 0. Pre: MemAvailable 10651060 kB, SwapFree 200 kB, no VAE Gradle running. Whole
output:

```
API NOTE: new in this release (no baseline): undo voice-adapter
API NOTE: api.txt compared with the baseline released in v1.0.1 (additive only)
```
GATE OK api-check

### Step 4: `scripts/release-cut.sh selftest all` (single attempt, no earlyoom kill, no retry used)

start 2026-10-08T00:19:24Z, end 2026-10-08T00:40:45Z (21 min 20 s), exit 0. Pre: MemAvailable 10622412 kB, SwapFree 44 kB, no VAE Gradle
running, tree clean outside .planning. Post: MemAvailable 14627488 kB, SwapFree 229864 kB; tree clean outside .planning. No `FAIL` line,
47 `ok` control lines (40 negatives went red as planted, 7 positives stayed green).

Happy path in the sandbox (preflight and cut):

```
PREFLIGHT OK tag=v1.0.1 commit=9c66cef6fba4f7d9673b5e11c5278adbe71b820a wiring=794385f26cf71e4d0dbe1343f9afa80a5ddf8cd9 gates=tag-format,tags-absent,create-tag,clean,pushed,wiring,diff,waiver,check,api-dump,hygiene,api-check,dry-run,leak,version
CUT OK tag=v1.0.1 commit=9c66cef6fba4f7d9673b5e11c5278adbe71b820a tag_object=f9cd4e59742b06c25b429c189e729561833e2eaf pushed=refs/tags/v1.0.1
sandbox remote: the previous tag v1.0.0 plus exactly one new annotated tag v1.0.1 peeling to 9c66cef6fb
```

The control that failed in window 20-01 and the two new controls from plan 20-13:

```
ok    [contract-ledger-only] stayed green (diff: matched 'DIFF NOTE: ledger-only contract change')
ok    [contract-row-in-table-last] stayed green (ledger-row-placement: matched 'planted row is the last row of the table')
ok    [contract-row-in-table-prose] stayed green (ledger-row-placement: matched 'planted row is the last row of the table')
```

Final lines:

```
real-repo guard: unchanged (tags, remote tags, status, config.json, contract)
```
RELEASE SELFTEST OK happy=1 negatives=40 positives=7

### Step 5: the bash gates

Each run as its own plain command, exit status read directly, last line verbatim (UTC start to end, all exit 0):

| # | Command | UTC | Last line |
|---|---------|-----|-----------|
| 1 | `bash scripts/verify-docs-coverage.sh` | 00:41:08 to 00:41:09 | `DOC COVERAGE OK checks=32 types=119` (preceded by `DOC COVERAGE NOTE: C23: README.md pins v1.1.0 but that tag does not exist yet (pre-tag allowance; VAE_DOCS_REQUIRE_PINNED_TAG=1 makes this a failure)`) |
| 2 | `bash scripts/verify-docs-coverage.sh --selftest` | 00:41:10 to 00:41:23 | `DOC COVERAGE SELFTEST OK plants=13` |
| 3 | `bash scripts/verify-stt-confinement.sh` | 00:41:23 | `STT CONFINEMENT OK checks=6` |
| 4 | `bash scripts/verify-stt-confinement.sh --selftest` | 00:41:24 to 00:41:31 | `STT CONFINEMENT SELFTEST OK cases=12` |
| 5 | `bash scripts/verify-module-manifest.sh` | 00:41:34 | `MODULE MANIFEST OK modules=core,providers,keystore,undo,voice-adapter` |
| 6 | `PRE_RELEASE=0 bash scripts/verify-repo-hygiene.sh` | 00:41:36 to 00:41:37 | `HYGIENE OK` |
| 7 | `bash scripts/verify-release-manifest.sh` | 00:41:37 to 00:41:39 | `RELEASE MANIFEST PROOF OK cases=8` |
| 8 | `bash scripts/agent-wiring-test.sh selftest-source` | 00:41:40 | `WIRING SOURCE SELFTEST OK` |
| 9 | `scripts/verify-sample-device-guard.sh` | 00:41:43 to 00:41:45 | `SAMPLE DEVICE GUARD OK scenarios=43` |
| 10 | `scripts/verify-binary-diff.sh --selftest` | 00:41:45 to 00:41:56 | `BINARY DIFF SELFTEST OK cases=13` |
| 11 | `scripts/release-cut.sh gate leak` | 00:41:56 to 00:41:59 | `GATE OK leak` (preceded by `content_check=skipped(no local fixture)`) |

DOC COVERAGE OK checks=32 types=119
STT CONFINEMENT OK checks=6
RELEASE MANIFEST PROOF OK cases=8
BINARY DIFF SELFTEST OK cases=13

RT-02 verdict: gates 10 (api-dump) and 12 (api-check) and api-baseline are GREEN on the real tree (steps 1 to 3, re-run on HEAD after
the 20-13 fix); selftest step 4 (`selftest all`) is GREEN again (the stale `contract-ledger-only` fixture is fixed, the two new controls
are green in the full suite); no tracked api.txt changed (tree clean outside .planning); all 11 bash gates are green; heavy_gates: green.

## Wiring SHA

W = the last commit that changes anything outside .planning/ (`git log -1 --format=%H -- . ':!.planning'`), equal to the plan 20-13 fix
commit and to the expected value:

wiring_sha: 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1

From this moment no script, doc, sample or module source changes (rule R3, gate 7).

## Close

closed 2026-10-08T00:42:25Z (about 25 min after gates_started, inside the 4 h timebox). At close: MemAvailable 14510020 kB, SwapTotal
2097148 kB, SwapFree 229872 kB; no VAE Gradle process; `git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd'` empty.
Earlyoom kills in-window: none observed (every step exited 0 on its first attempt; the retry budget was not used).

Message for the master to relay to the orchestrator (the build lock can be released):

quiet done
