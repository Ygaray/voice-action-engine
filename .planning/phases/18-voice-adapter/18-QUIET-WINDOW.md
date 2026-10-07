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

## Results
