# 18-08 host quiet-window request

grant: pending
requested: 2026-10-07
timebox_s: 3600
relayed_by:
date:

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

## Pre-checks

## Results
