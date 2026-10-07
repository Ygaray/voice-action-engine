---
phase: 19-sample-gate-1-docs
plan: 07
subsystem: testing
tags: [gate-1, tester, device-window, live-spend, evidence, responses-probe, router-live, undo-all]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plans 19-01 (retargeted runner), 19-04..19-06 (grammar, plan, router and undo legs)
provides:
  - five committed Gate-1 evidence files from one TESTER window and one install (all PASS, trigger=ui)
  - 19-GATE1-RUNBOOK.md (the one-window driving order by resource id)
  - the relayed spend GO recorded and consumed in 19-LIVE-LEG-DECISION.md
affects: [19-08, 19-14, phase-20]

status: complete
actuals:
  tokens: 40000
  tasks: 4
  commits: 4
plan_head_before: 9b4a639a7f20b1e62b592feb36e34612ddcc43ac
commits: 4

key-files:
  created:
    - .planning/phases/19-sample-gate-1-docs/19-GATE1-RUNBOOK.md
    - .planning/phases/19-sample-gate-1-docs/evidence/gate1-grammar_offline.txt
    - .planning/phases/19-sample-gate-1-docs/evidence/gate1-undo_all.txt
    - .planning/phases/19-sample-gate-1-docs/evidence/gate1-plan_live.txt
    - .planning/phases/19-sample-gate-1-docs/evidence/gate1-router_live.txt
    - .planning/phases/19-sample-gate-1-docs/evidence/gate1-responses_probe.txt
  modified:
    - .planning/phases/19-sample-gate-1-docs/19-LIVE-LEG-DECISION.md

key-decisions:
  - "Task 1 (relay checkpoint) was satisfied by RT-05/RT-06 in 19-CONTEXT.md; recorded verbatim, no self-authored approval"
  - "No code, script or test was changed; the runner worked unmodified with env -u BASH_ENV -u ANDROID_SERIAL"

requirements-completed: [VER-06]

duration: 9min
completed: 2026-10-07
---

# Phase 19 Plan 07: TESTER device window Summary

**All five Phase 19 device legs (grammar_offline, undo_all, plan_live, router_live, responses_probe) passed on the TESTER R5CT10XNKQN in one window and one install, spending 4 of 16 requests (about USD 0.0044 of 0.05), with keys gone and the package uninstalled.**

## Relayed answer (Task 1)

Relayed by orchestrator yahir-gsd-control-plane-3b (via master) on 2026-10-07, quoted verbatim in the Approval record of
`19-LIVE-LEG-DECISION.md` (RT-05 spend GO: "ceiling 16 requests / USD 0.05, spend-capped test keys only, no retries past
the ceiling. ... the live API calls are plan + router + the astra smoke ONLY. The grammar and undo legs must make 0 API
calls; if either would spend, stop and tell me."; RT-06: "TESTER R5CT10XNKQN is free and granted for 19-07"). The start
handshake was completed by the master before dispatch. Decision set to `approved`, window `open 2026-10-07T16:03:52Z`.

## Window

- Open 2026-10-07T16:03:52Z, closed 2026-10-07T16:12:07Z. TESTER only (`adb -s R5CT10XNKQN`); no airplane mode; the personal phone was never addressed.
- Build-install (one, low-memory GRADLE_OPTS): `apk_md5=662317322d5b804ab0016958770a8927 head=1869950dca dirty=0 asset_fixture=absent`.
- Preflight: `SAMPLE_GATE1: OK sub=preflight target=R5CT10XNKQN model=SM-S908U sdk=35 installed=no`.

## Verdicts

```
VAE_VERDICT leg=grammar_offline verdict=PASS en=1 es=1 near_miss_capped=1 provider_turns=0 attempts=0 tripwire_calls=0 trigger=ui
VAE_VERDICT leg=undo_all verdict=PASS n=3 restored=3 blockers=1 partial_n=2 pending=1 trigger=ui
VAE_VERDICT leg=plan_live verdict=PASS committed=2 bound=1 remaining=0 replanned=0 key_charset=ok trigger=ui
VAE_VERDICT leg=router_live verdict=PASS eligible=2 picked_index=1 sel_turns=1 router_tokens=901 key_charset=ok trigger=ui
VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui
```

- grammar_offline: three cases, every `VAE_TRACE` shows `provider_turns=0 attempts=0 tripwire_calls=0`; `budget_used` stayed `requests 0/15 . optional 0/1` after both offline legs.
- undo_all: `undo_label` read **`Undo all (3)`** before the second press; sub-cases: undone (restored=3), refused (`changed_since`, blockers=1), PlanThenExecute partial (n=2, pending=1, restored=2).
- router_live: trace `sel=picked eligible=2 picked_index=1 first_model_index=1 bypassed=1 sel_turns=1`; closes P16 OI-6 (wording run live, PASS).
- responses_probe: exactly the Phase 12 line shape (typed `ModelUnsupported`, http=400, no `http_error`).

## Spend

```
VAE_BUDGET core=1 optional=0 anthropic=1 openai=0 openrouter=0 est_usd=0.00168     (plan_live)
VAE_BUDGET core=3 optional=0 anthropic=3 openai=0 openrouter=0 est_usd=0.00275     (router_live, cumulative)
VAE_BUDGET core=3 optional=1 anthropic=3 openai=1 openrouter=0 est_usd=unknown     (responses_probe, cumulative)
```

`P19 LIVE SPEND: requests=4 (plan=1 router=2 probe=1) est_cost_usd=0.00275 (+ probe unbilled: HTTP 400 bills nothing) ceiling=16 requests / USD 0.05`

- `budget_used` readings: before the live legs `requests 0/15 . optional 0/1 . est USD 0.00000`; after the probe `requests 3/15 . optional 1/1 . est USD unknown`.
- `import_status` after `import_test_keys`: `anthropic=Ready deleted=true in_datastore=false; openai=Ready deleted=true in_datastore=false; openrouter=Ready deleted=true in_datastore=false`.

## Evidence index

| File | Verdict | head in header |
|------|---------|----------------|
| evidence/gate1-grammar_offline.txt | PASS | 1869950dca |
| evidence/gate1-undo_all.txt | PASS | 1869950dca |
| evidence/gate1-plan_live.txt | PASS | 5e376f4ee6 |
| evidence/gate1-router_live.txt | PASS | 5e376f4ee6 |
| evidence/gate1-responses_probe.txt | PASS (model_unsupported, http=400) | 5e376f4ee6 |

(The evidence header `head` is the checkout HEAD at capture time; the installed APK is the one built at `head=1869950dca`. Later HEADs differ only by `.planning` commits, so no code changed under the install.)

## Cleanup

- `verify-keys-gone` after the import and again before cleanup: `SAMPLE_GATE1: OK sub=verify-keys-gone keys_gone=yes` (test-keys dir empty).
- `SAMPLE_GATE1: OK sub=cleanup target=R5CT10XNKQN` (package removed, deviceidle whitelist removed).
- Media stream 3 volume: `volume is 15 in range [0..15]` before and after the window (no change; no restore needed).
- Decision `consumed`, window `closed 2026-10-07T16:12:07Z`. The TESTER is released.

## Task Commits

1. Task 1: relayed GO and window recorded - `1869950`
2. Task 2: runbook, offline legs grammar_offline and undo_all - `5e376f4`
3. Task 3: live legs plan_live, router_live, responses_probe - `034db18`
4. Task 4: decision consumed, window closed - `3feeb99`

## Deviations from Plan

None - the plan was executed as written. The runner needed `env -u BASH_ENV -u ANDROID_SERIAL` (as the dispatch instructed) because BASH_ENV exports ANDROID_SERIAL; no script change. The on-screen key tails shown by the app in `key_state_<p>` were read once by the UI helper during the import check and are not recorded anywhere.

## Issues Encountered

None. No leg failed, no re-run, no INFRA.

## Self-Check: PASSED

All five evidence files, the runbook and this summary exist; commits 1869950, 5e376f4, 034db18, 3feeb99 exist; the plan's automated verifications for Tasks 1 to 4 exit 0; `git status --porcelain -- core providers keystore undo voice-adapter sample scripts` prints nothing.
