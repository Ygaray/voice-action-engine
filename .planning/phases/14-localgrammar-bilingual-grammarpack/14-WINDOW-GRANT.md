# 14-09 TESTER window grant

The runner (`scripts/run-stt-capture.sh`) refuses every device subcommand, before the lock and before any adb call,
until the first block of lines below holds the exact line `grant: open`. Only the orchestrator relay may change the
`grant` line. Plan 14-09 writes it from a relayed answer (never otherwise) and sets `grant: consumed` when the window
closes, or `grant: deferred` plus a `deferred_obligation:` line if the relayed answer is that no window comes before
Phase 14 closes. Until then the grant is pending and no device step can run. Touch no device while `grant: pending`.

grant: pending
requested: 2026-10-06
timebox_s: 3600
device: R5CT10XNKQN

## Relay log (verbatim)

- 2026-10-06, master relay from orchestrator yahir-gsd-control-plane-3b:
  "14-09 PRE-APPROVED: timebox_s=3600, TESTER R5CT10XNKQN only (adb -s pinned), no radio/airplane changes. The phone plays TTS aloud: keep volume moderate and restore it after."

Start handshake: the device must not be held during host plans. The orchestrator confirms it is free when 14-09 actually starts; the master then re-dispatches with `grant: open`. Touch no device while `grant: pending`.

## Relay

VAE Phase 14 D-12 requests one TESTER window (R5CT10XNKQN only), at most 2 h (the orchestrator pre-approved 1 h, `timebox_s: 3600`), to be consumed when the phase reaches plan 14-09 (after its JVM gate; the request is sent now so it can be scheduled). No API keys, no spend, full cleanup (uninstall, pushed files removed), no overlap with Phase 19 Gate-1.
Purpose: record what the platform recognizer emits for synthetic TTS prompts (EN/ES numbers, decimals, accents) through an opt-in :sample instrumentation tool (about 90 prompts). HEAD sha at request: 3295025.
Fallback if no window arrives before Phase 14 closes: ship the strict golden table (it rejects what it cannot verify, so a late finding only widens acceptance) and record a deferred obligation; please confirm the fallback (open item OI-5).
