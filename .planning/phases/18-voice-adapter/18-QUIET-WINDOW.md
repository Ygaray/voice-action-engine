# 18-08 host quiet-window request

grant: open
requested: 2026-10-07
timebox_s: 3600
relayed_by: orchestrator yahir-gsd-control-plane-3b via the milestone master
date: 2026-10-06
opened: 2026-10-07T03:05:00Z

Only the orchestrator relay may change the `grant` line. Plan 18-08 writes `open` or `deferred` from a relayed answer,
never otherwise, and sets `consumed` when the window closes. Until then the grant is pending and no heavy gate runs:
the host has little memory and swap is full, so another Gradle build beside these would be killed by earlyoom.

## Relay

VAE Phase 18 requests one host quiet window (no other Gradle build on the host, swap reset by the operator if full), at
most 1 h (`timebox_s: 3600`), to run `scripts/verify-negative-controls.sh` (now including its Part 6 `:stt` controls and
the voice-adapter source plants), `scripts/verify-api-dump.sh` and the clean-cache `scripts/jitpack-dry-run.sh` (which
publishes the voice-adapter AAR). No device, no keys, no spend. The network is used only by the dry run's empty-cache
consumer resolution. HEAD sha at request: 8d2cfd2 (this plan adds only planning files after it). Every autonomous gate is
already green at that sha (`18-SURFACE-REVIEW.md`, "Gate results").
Fallback if no window arrives before Phase 18 closes: the heavy gates become a deferred obligation owned by Phase 19's
gate run, before the v1.1.0 cut.

## Relay log (verbatim)

RT-02 [quiet-window] (2026-10-06): CONFIRMED by orchestrator yahir-gsd-control-plane-3b, which holds the VAE build lock (control-plane 5e8eca1), after Yahir reset swap. Host: MemAvailable 8.7 GiB, swap 1.1 GB free, a mempalace mine (3.3 GB) still running. Rules: at most one Gradle daemon (or --no-daemon); check memory between steps; STOP if MemAvailable < 5 GiB. If earlyoom kills a step, re-run that step ONCE only, then report. The master sends 'quiet done' when 18-08 finishes.

(source: 18-CONTEXT.md RT-02, commit 3c58f34; the master's dispatch confirmed the grant)

## Pre-checks

- opened 2026-10-07T03:05:00Z (= 2026-10-06 21:05 local MDT); run recipe GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"; one Gradle process at a time.
- Before verify-negative-controls.sh (2026-10-07T03:04:41Z): MemAvailable 12176064 kB (11 GiB), swap 0B used of 2.0Gi, no Gradle daemon (pgrep exit 1).
- During the run MemAvailable stayed 8.2-11.0 GiB (spot readings 8175420, 9855944, 10980764, 9911868 kB).
- After verify-negative-controls.sh (2026-10-07T04:02:50Z): MemAvailable 8409216 kB (8.0 GiB), swap 2.0Gi used (full again), no earlyoom kill. A Gradle daemon pid 818577 was present: it belongs to another project (/home/yahir/Projects/AndroidApps/Shared/BlackJackTrainer, started 2026-10-06 21:43 local, last build finished 21:52, idle at 0% CPU); it was not killed or touched.

## Results

### Step 1: scripts/verify-negative-controls.sh
started 2026-10-07T03:04:41Z, finished 2026-10-07T04:02:50Z (about 58 min), exit status 0, no earlyoom kill, no retry. Full output kept in the session scratchpad (nc.out, 151 ok lines, 0 FAIL lines).

ok    [DI import (voice-adapter)] went red (weighted issues)
ok    [DI import (voice-adapter)] went red (Banned constructs)
ok    [api.txt missing once released (voice-adapter)] went red (api.txt is missing)
ok    [voice-adapter gains an ML dependency] went red (resolves ML artifacts)
ok    [:voice-adapter clean tree] stayed green
ok    [:voice-adapter publication gate clean tree] stayed green
ok    [adapter publishes the speech engine] went red (must keep :stt compileOnly)
ok    [:keystore gains the speech engine] went red (resolves the :stt group)
ok    [:core gains the speech engine] went red (resolves the :stt group)
ok    [:providers gains the adapter] went red (forbidden project dependencies)
STT NEGATIVE CONTROLS OK plants=7
ok    [stt negative controls]
negative-control failures: 0
