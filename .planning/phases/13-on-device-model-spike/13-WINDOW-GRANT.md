# Phase 13 TESTER window grant

The runner (`scripts/run-spike-ondevice.sh`) refuses every device subcommand, before the lock and before any adb call,
until the first block of lines below holds the exact line `grant: open`. Only the orchestrator relay may change the
`grant` line. Plan 13-08 writes it from a relayed answer (never otherwise) and sets `grant: consumed` when the window
closes. Until then the grant is pending and no device step can run.

grant: pending
relayed_by:
date:
timebox_s: 14400
sb_labels: default
sb_fixture_sha8: 8bc739ed
sb_fixture_tools: 19
sb_fixture_source: SB .planning/cross-repo/sb-a10-fixture.json (SB c26502e55 / XR-175-01), host-private copy, never committed
gemma3_1b: skipped_gated
gemma3_1b_source: orchestrator ruling (yahir-gsd-control-plane-3b, 2026-10-05)

## Settled grant inputs (relayed by the master, 2026-10-05; see 13-SB-LABELS-ANSWER.md)

- `timebox_s: 14400`: one TESTER window of at most 4 h, requested at the start of 13-08 and released when device work is done.
- `sb_labels: default`: the SB gold labels are the executor-authored private file, digest-pinned to SB's CURRENT fixture
  (19 tools including `ask_user_to_choose`, sha256 prefix `8bc739ed`). `push-private` takes `sb-fixture.json` and
  `sb-gold.json` from `VAE_SPIKE_PRIVATE_DIR` (default `~/.local/share/vae-spike`) and refuses when the fixture digest does
  not start with `sb_fixture_sha8`. The tool count is a reported dimension in the results, never a hard assumption (SB 179
  may cut a narrower subset).
- `gemma3_1b: skipped_gated`: no Hugging Face download, the E2B verdict is unaffected. The runner refuses `push-model g3_1b`
  while this value starts with `skip`.

## Relay (the text the driver relays with the window request, plan 13-08)

D-07 bar locked unchanged in 13-THRESHOLDS.md (committed before any device step); only implicit definitions added: schema-valid is a point estimate over N>=100 (a Wilson bound is unreachable at N=100), cold is process-cold with a warm page cache (true cold is impossible rootless), PSS is in-process totalPss at 250 ms with a one-time dumpsys cross-check.
Time-box: one TESTER window of at most 4 h; any gating metric unmeasured at expiry makes that envelope red (`unmeasured:<metric>`).
Open question 1: who supplies the SB gold labels? Default: the executor authors them privately from the re-pinned SB fixture (digest pinned at authoring, never committed). Answered: default stands, re-pinned to SB's current fixture.
Open question 2: does Yahir accept the Gemma 3 1B HF terms? Default: skip the control row, recorded `skipped_gated`. Answered: skipped_gated (orchestrator ruling).
TESTER R5CT10XNKQN only, no API keys, no spend, one window of at most 4 h, full cleanup.
