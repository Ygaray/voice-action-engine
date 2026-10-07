# Phase 19 live-leg request (VER-06, D-13)

decision: consumed
consumed: 2026-10-07T16:12:07Z requests=4 of 16 (core 3, optional 1) est_usd=0.00443 of 0.05
requested_by: plan 19-01 for the relay plan 19-07 waits on
relayed_by: yahir-gsd-control-plane-3b (via master)
date: 2026-10-07
window: closed 2026-10-07T16:12:07Z

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

Recorded by plan 19-07 Task 1 from the relayed answers in 19-CONTEXT.md (RT-05, RT-06). Quoted verbatim:

RT-05 [p19-relay] (2026-10-07, orchestrator yahir-gsd-control-plane-3b, relayed by the master), item (1) SPEND GO:

> ceiling 16 requests / USD 0.05, spend-capped test keys only, no retries past the ceiling. Per Yahir's D-01 + D-13, the live API calls are plan + router + the astra smoke ONLY. The grammar and undo legs must make 0 API calls; if either would spend, stop and tell me.

RT-05 item (2) TESTER start handshake: pre-approved; the handshake was completed by the master before this dispatch (the plan 19-07 run was re-dispatched after "device start tester 19-07"). Release the TESTER right after cleanup.

RT-06 [tester-window] (2026-10-07):

> CONFIRMED by orchestrator yahir-gsd-control-plane-3b: TESTER R5CT10XNKQN is free and granted for 19-07. Use `adb -s R5CT10XNKQN` only and stay within the 16 req / USD 0.05 ceiling. Do full cleanup and uninstall, and restore the volume. The master sends "device done tester" with the verdict lines and the spend line.

Interpretation: GO for 16 requests and USD 0.05 (equal to the ask, so the app's hard ceiling 15 core + 1 optional is not above the GO). Window open on R5CT10XNKQN, one install, every leg in this window.
