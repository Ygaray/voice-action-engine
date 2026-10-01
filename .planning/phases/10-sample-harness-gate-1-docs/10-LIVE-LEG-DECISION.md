# Phase 10 live-leg decision (D-13)

decision: approved
relayed_by: milestone master, relaying the orchestrator yahir-gsd-control-plane-f2 (answer relayed 2026-10-01)
date: 2026-10-01T22:03:07Z
resume_signal: approve-live-legs: L1-L6 and the optional L7, on the TESTER R5CT10XNKQN under Yahir's standing test-key policy. Ceiling is 33+1 requests and USD 0.20; report actual count and cost after Gate-1. Before the first device step, announce the TESTER window to me, and announce again when it closes. Never leave the TESTER, never use an emulator fallback, and keep the fixture to LE-7 rules (gitignored asset; no content, tool names or sha suffix in evidence). (1) Accepted: cache-write-via-router is not exercisable in v1.0 (LATER-02). This does not close the uat-pending/05 item. That still needs Yahir's waiver as a P11 precondition, so list it in the P11 waiver packet. (2) Accepted: host golden + live 200 on first attempt is sufficient proof for disable_parallel_tool_use.

## Budget (bounded, enforced in-app by 10-04/10-05)

Legs and models, cheapest capable, all on the TESTER yahirs-s22-ultra-2 (USB R5CT10XNKQN) through the :sample UI:

| Leg | Name | Model | Expected / ceiling requests |
|-----|------|-------|-----------------------------|
| L1 | ver02 (Anthropic agentic cold run) | claude-haiku-4-5 | 2-4 / 6 (one infra re-run allowed: 12) |
| L2 | smoke_anthropic | claude-haiku-4-5 | 1 / 3 |
| L3 | smoke_openai | gpt-5.4-mini | 1 / 3 |
| L4 | smoke_openrouter | openai/gpt-5.4-mini via OpenRouter | 1 / 3 |
| L5 | multi_openai | gpt-5.4-mini | 2 / 6 |
| L6 | multi_openrouter | openai/gpt-5.4-mini via OpenRouter | 2 / 6 |
| L7 | responses_probe (optional) | gpt-6-astra (expected 400, bills nothing) | 0-1 / 1 |

Expected about 14 HTTP requests; hard ceiling 33 core plus 1 optional. Expected cost about USD 0.04; ceiling about USD 0.20.

## Conditions carried from the relay

- TESTER R5CT10XNKQN only: never leave the TESTER, no emulator fallback, the personal phone is never touched.
- Announce the TESTER window to the master before the first device step, and again when it closes.
- LE-7 fixture rules: the fixture is a gitignored asset; evidence contains no fixture content, no fixture tool names and no sha suffix (prefix only), on top of the closed evidence vocabulary.
- Report-back obligation: after Gate-1 the tester reports to the master the line `GATE1 LIVE SPEND: requests=<total> (anthropic=<a> openai=<o> openrouter=<r>) est_cost_usd=<x> ceiling=33+1`.
- Cache-write via OpenRouter (uat-pending/05 item 3(c)) is not exercisable in v1.0 (LATER-02). It is NOT closed here; it needs Yahir's waiver and is listed in the Phase 11 waiver packet.
- `disable_parallel_tool_use`: the host goldens plus the live 200 on the first attempt (L2) are accepted as sufficient proof.

Procedure: `GATE1-RUNBOOK.md` section 4 (approved).
