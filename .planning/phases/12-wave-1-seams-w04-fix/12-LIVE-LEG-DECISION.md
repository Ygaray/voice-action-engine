# Phase 12 live-leg request (PROV-16)

decision: consumed
consumed: 2026-10-05T16:03:14-06:00 — TESTER window closed after the one granted leg (2/4 requests); re-running needs a new relayed approval
relayed_by: orchestrator yahir-gsd-control-plane-3b via the coordinator (control-plane 4146165), spend approval only
date: 2026-10-05

requested_by: executor of plan 12-05, for the orchestrator relay that plan 12-08 waits on

The runner (`scripts/run-sample-gate1.sh push-keys`) refuses to move any key until the line above reads exactly
`decision: approved`. Only plan 12-08, quoting a relayed orchestrator answer, may change it.

## Approval record (relayed by the coordinator for plan 12-08)

GO on 12-08: the live gpt-6-astra W04 smoke, ceiling 4 requests / USD 0.05, using the spend-capped openai test key on TESTER R5CT10XNKQN only. Source: Yahir's ruling 'do the smoke' (relayed by orchestrator yahir-gsd-control-plane-3b, control-plane 4146165).

Scope note: the decision line above reads `approved` for the spend approval. The TESTER DEVICE WINDOW is a separate grant and
was GRANTED afterwards (12-CONTEXT.md Runtime Decisions RT-01, commit f8d4d73) for 12-08 Task 3 only: one responses_probe leg,
about 15 min, 3 requests left of the 4 / USD 0.05 ceiling after Probe C, `adb -s R5CT10XNKQN` only, full cleanup afterwards.

## Budget (bounded)

| Leg | Name | Where | Model | Expected / ceiling requests |
|-----|------|-------|-------|-----------------------------|
| P1 | Probe C: one direct Chat Completions request with the post-fix wire from the `astra_direct` golden, run under `with-test-keys --only openai` | host | gpt-6-astra (expected HTTP 400, bills nothing) | 1 / 2 |
| P2 | `:sample` `responses_probe` leg | TESTER R5CT10XNKQN | gpt-6-astra (expected HTTP 400, bills nothing) | 1 / 2 (an infra re-run needs a reinstall: the optional pool allows one probe per install) |

Totals: expected 2 requests, ceiling 4 requests, ceiling USD 0.05. A 400 on an unsupported model bills nothing, so the expected cost is USD 0.00.

## Conditions

- TESTER only: USB serial R5CT10XNKQN (yahirs-s22-ultra-2). Never the personal phone, never an emulator.
- Announce the TESTER window to the orchestrator before the first device step, and again when it closes.
- Never overlap Phase 13's spike or Phase 19's Gate-1 (one TESTER, one tester at a time).
- Keys travel only by file reference through `push-test-key` and `with-test-keys`, using the spend-capped OpenAI test key.
  The existing guarded `push-keys` step moves the three providers' test keys, but only the OpenAI leg runs.
- `verify-keys-gone` and `cleanup` run before the window closes.
- Evidence stays inside the runner's closed vocabulary (`VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui`).
  The `:sample` leg judges PASS only on the typed `model_unsupported` after a real provider answer (an HTTP status present).

## Reconciliation note for the orchestrator (Phase 19 D-13)

PROV-16's only reachable live result for `gpt-6-astra` on Chat Completions is the typed `ModelUnsupported` (the model takes no
function tools there). Phase 19's D-13 wording ("must succeed with no 400 on reasoning_effort") should therefore be restated to
reuse this evidence line rather than spend a second call.

## Relay

Plan 12-08's executor records the answer as follows: replace the `decision: pending` line with the relayed value (exactly
`decision: approved` to allow the live call), and add `relayed_by:` and `date:` lines quoting the relay. Any value other than
`approved` means no live call is made.
