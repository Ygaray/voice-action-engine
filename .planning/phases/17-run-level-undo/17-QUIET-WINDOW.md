# 17-10 host quiet-window request

grant: open
requested: 2026-10-06
timebox_s: 3600
relayed_by: orchestrator yahir-gsd-control-plane-3b via the milestone master
date: 2026-10-06

Only the orchestrator relay may change the `grant` line. Plan 17-10 writes `open` or `deferred` from a relayed answer,
never otherwise, and sets `consumed` when the window closes. Until then the grant is pending and no heavy gate runs:
the host has little memory and swap is full, so another Gradle build beside these would be killed by earlyoom.

## Relay

VAE Phase 17 requests one host quiet window (no other Gradle build on the host, swap reset by Yahir if full), at most 1 h
(`timebox_s: 3600`), to run `scripts/verify-negative-controls.sh` (with its ML-denial part), `scripts/verify-api-dump.sh`
and the clean-cache `scripts/jitpack-dry-run.sh`. The last one includes the `:undoalone` consumer, which proves
`voice-action-engine-undo` resolves with no `:core`. No device, no keys, no spend. The network is used only by the dry
run's empty-cache consumer resolution. HEAD sha at request: 5dbc89a (plan 17-09 added only planning files after it).
Every autonomous gate is already green at that sha (`17-SURFACE-REVIEW.md`, "Gate results").
Fallback if no window arrives before Phase 17 closes: the heavy gates become a deferred obligation owned by Phase 19's
gate run, before the v1.1.0 cut.

## Relay log (verbatim)

"QUIET WINDOW CONFIRMED (17-CONTEXT.md Runtime Decisions RT-01, commit ee9dd8f): orchestrator yahir-gsd-control-plane-3b holds the VAE build lock (control-plane 65c20f2); no other repo is running Gradle. Memory bounds: a transient 4.4 GB mempalace mine is also running (MemAvailable ~8.4 GiB at grant) - run every Gradle step with --no-daemon (or at most one daemon), workers.max=2, parallel=false; check /proc/meminfo before each heavy step and STOP (needs_human, type quiet_window_memory) if MemAvailable drops below 5 GiB rather than risking an earlyoom kill."
