# 20-07 host quiet window 20-07b (re-run of plan 20-07 Task 3 after the 20-13 fix)

grant: open
relayed_by: yahir-gsd-control-plane-3b
date: 2026-10-08T00:16:49Z
opened: 2026-10-08T00:16:49Z
timebox_s: 14400
gates_started:
heavy_gates:
wiring_sha:
closed:

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

<!-- window-results-continue -->
