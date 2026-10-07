# Phase 19 live-leg request (VER-06, D-13)

decision: pending
requested_by: plan 19-01 for the relay plan 19-07 waits on

The runner (`scripts/run-sample-gate1.sh push-keys`) refuses to move any key until the line above reads exactly
`decision: approved`. Pending, deferred, consumed and a missing file all refuse. The ceilings below are the ASK the
master relays, never authority: only a relayed answer is authority, and only plan 19-07, quoting that answer, may change
the decision line.

## Budget (bounded)

| Leg | Where | Model | Expected / worst-case requests |
|-----|-------|-------|--------------------------------|
| plan_live | TESTER R5CT10XNKQN | claude-haiku-4-5 | 1-2 / 6 |
| router_live | TESTER R5CT10XNKQN | claude-haiku-4-5 | 2-3 / 9 |
| responses_probe | TESTER R5CT10XNKQN | gpt-6-astra under the supportsTools override (optional pool; an HTTP 400 bills nothing) | 1 / 1 |
| grammar_offline | host or TESTER, offline | none | 0 / 0 |
| undo_all | host or TESTER, offline | none | 0 / 0 |

Totals: ceiling ASK 16 requests (15 core worst case + 1 optional) and USD 0.05 for the plan, router and gpt-6-astra legs.
Expected about 4-6 requests and about USD 0.01.

## Conditions

- TESTER only: USB serial R5CT10XNKQN (yahirs-s22-ultra-2). Never the personal phone, never an emulator.
- One install and one window for every leg; announce the TESTER window to the orchestrator before the first device step
  and again when it closes.
- Keys travel only by file reference through `push-test-key`. `push-keys` moves the anthropic, openai and openrouter test
  keys; only anthropic and openai spend. A missing key is a loud stop: tell Yahir which key file to create, do not work around.
- `verify-keys-gone` and `cleanup` run before the window closes.
- Evidence stays inside the runner's closed vocabulary (the evidence filter allow-list); nothing is hand-written.
- The v1.0 legs are not re-run (D-01).

## Relay

Request text for the master to relay (one message):

> Phase 19 (VER-06) asks for a spend GO with ceilings of 16 requests (15 core worst case + 1 optional) and USD 0.05 in
> total for the plan, router and gpt-6-astra legs, plus one TESTER window on R5CT10XNKQN only (one install, every leg in
> that window, full cleanup before it closes). Expected use is about 4-6 requests and about USD 0.01.

Plan 19-07's executor records the answer as follows: replace the `decision: pending` line with the relayed value (exactly
`decision: approved` to allow the live calls), and add `relayed_by:`, `date:` and a window line quoting the relay. Any
value other than `approved` means no live call is made. Nothing in this file may be edited to `approved` by any other path.

## Approval record

