# 20-07 host quiet-window request (written by plan 20-07 Task 2 preparation)

grant: pending
relayed_by:
date:
opened:
requested: 2026-10-07T23:28Z
timebox_s: 14400
gates_started:
heavy_gates:
wiring_sha:
closed:

Only the orchestrator relay may change the `grant` line (RT-09(4), D-07). Until a relayed answer to "quiet window 20-01" is quoted below,
the grant is pending and no heavy gate runs. The timebox clock (14400 s) starts at `gates_started:`, written when the first heavy gate
starts, not at `opened:`.

## Relay

Request string for the master to send the orchestrator (exact): `quiet window 20-01`

Context to send with it: VAE Phase 20 asks one host quiet window under the pre-approved RT-09(4) terms (the orchestrator takes the VAE
build lock and confirms; MemAvailable at least 5 GiB; the operator resets swap if it is full, see `free -h`; at most one Gradle process at
a time; the orchestrator's section 11 commits to this repository are frozen for the window; the master sends "quiet done" afterwards).
Purpose: the real-tree gates 10 and 12 (RT-02), `release-cut.sh selftest all`, the bash gates, then W (wiring_sha) is fixed. Up to 4 h
(`timebox_s: 14400`). No device, no spend.

HEAD at request: `a77dc6c6f852f0d364f0e952010a3f03784bef80` (branch gsd/phase-20-cut-v1-1-0; only planning files differ from the last
non-.planning commit; the waiver packet is accepted and `scripts/release-cut.sh gate waiver v1.1.0` printed `GATE OK waiver`).

Read-only host readings at request (2026-10-07T23:27:56Z), taken by the executor, nothing run beyond these reads:

```
$ free -h
               total        used        free      shared  buff/cache   available
Mem:            31Gi        25Gi       1.0Gi       360Mi       5.8Gi       6.0Gi
Swap:          2.0Gi       2.0Gi        68Ki
$ grep -E "MemAvailable|SwapTotal|SwapFree" /proc/meminfo
MemAvailable:    6282608 kB
SwapTotal:       2097148 kB
SwapFree:             68 kB
$ pgrep -af '[G]radleDaemon'
(empty, exit 1: no Gradle daemon)
```

R2 swap check: SwapTotal 2097148 kB, SwapFree 68 kB (0.003% free, the rule needs at least 25%, i.e. 524287 kB). RESULT: FAIL. Swap is full
again even though relay 2 reported an operator swap reset; the operator must reset swap (agents never run sudo) before gates_started,
or a relayed ruling to proceed must be quoted here (rule R2). MemAvailable 6282608 kB (6.0 GiB) is above the 5 GiB floor but moves with
other processes; it is re-read before every heavy step.

## Relay log (verbatim)

## Pre-checks

## Results
