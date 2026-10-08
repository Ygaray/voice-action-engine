# 20-08 host quiet-window request 20-02 (written by plan 20-08 Task 1 preparation)

grant: pending
timebox_s: 7200
requested: 2026-10-08T00:45Z
wiring_sha: 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1
w10: 4bdb663b4c

Only the orchestrator relay may change the `grant` line (RT-09(4), D-07, rule R5). Until a relayed answer to "quiet window 20-02" is
quoted below, the grant is pending and no Gradle or JitPack step runs. The timebox clock (7200 s) starts at `gates_started:`, written
when the first heavy step (the live JitPack probe) starts, not at `opened:`. This one window covers plan 20-08 and plan 20-09 (one
request, one grant, closed in plan 20-09).

## Relay

Request string for the master to send the orchestrator (exact): `quiet window 20-02`

Context to send with it: VAE Phase 20 asks host quiet window 20-02 under the same terms as window 20-01 (the orchestrator takes the VAE
build lock and confirms; MemAvailable at least 5 GiB, pause below it with a single bounded re-check; swap per the relayed R2 ruling; at
most one Gradle process at a time; one retry per earlyoom kill; the orchestrator's section 11 commits to this repository are frozen for
the window; the master sends "quiet done" after plan 20-09). Purpose: the live JitPack probe of the pushed W (`4bdb663b4c`) with a
clean-cache consumer Gradle build, the D-02 artifact download and binary diff against v1.0.1, then the isolated fresh-agent wiring rerun
on W and its empty-cache verify. Up to 2 h (`timebox_s: 7200`). Network to jitpack.io only. No device, no spend.

Second message (exact form, the sha is the tip of main after the commit that adds this file; it is filled in by the master from the
checkpoint return): `pushing main <40-hex sha of HEAD>`, with the note that W is `4bdb663b4c7c1bf02d4588751705dfcc8b356ef1` and that only
.planning commits follow it. Plain `git push origin main`, no force, no tag.

## Pre-checks

Taken read-only by the executor on 2026-10-08T00:44:53Z, branch main (fast-forwarded from gsd/phase-20-cut-v1-1-0, no merge needed:
origin/main was still `97801136ae456ba6b0a8861ee1ea49932cc7bb75`, an ancestor of HEAD). HEAD before this file's commit:
`67ea3cb791061d56c8e69b0928ee2feb14058fdb`.

```
$ git fetch origin main      -> origin/main = 97801136ae456ba6b0a8861ee1ea49932cc7bb75 (unchanged; no new section 11 rows; no merge)
$ scripts/release-cut.sh gate diff 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1
GATE OK diff
$ free -h
               total        used        free      shared  buff/cache   available
Mem:            31Gi        18Gi       7.6Gi       359Mi       6.4Gi        13Gi
Swap:          2.0Gi       1.8Gi       224Mi
MemTotal:       32800860 kB
MemAvailable:   13866816 kB   (13.2 GiB, at least 8 GiB)
SwapTotal:       2097148 kB
SwapFree:         229924 kB   (about 11%, under the 25% rule; same state as accepted by the relayed R2 ruling for window 20-07b)
pgrep -af '[G]radleDaemon': empty (no Gradle daemon)
```

The swap check of rule R2 is red by the strict rule (SwapFree under 25%); the heavy step does not start until the orchestrator's relay
rules on it (the 20-07b ruling accepted swap-full with MemAvailable at least 8 GiB at open). Re-read the memory and swap readings again at
open and before the probe.

## Relay log (verbatim)

(none yet: waiting for the relayed answers to "quiet window 20-02" and "pushing main <sha>")
