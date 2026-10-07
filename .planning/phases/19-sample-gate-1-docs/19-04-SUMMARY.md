---
phase: 19-sample-gate-1-docs
plan: 04
subsystem: testing
tags: [gate-1, grammar, offline-proof, tripwire, evidence-vocabulary, vae-trace]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 19-01 retargeted runner and the env-overridable decision/evidence paths
provides:
  - grammar_offline leg (EN match, ES match, near-miss capped by policy) with three explicit zero counts
  - TripwireProvider (counts every call, answers typed failure tripwire_called)
  - VAE_TRACE evidence line in lockstep in all five places
affects: [19-05, 19-07, phase-20]

actuals:
  tokens: 29000
  tasks: 3
  commits: 3

plan_head_before: e50725dc622b380726be6b9c4261fbcb8956ab56
commits: 3

tech-stack:
  added: []
  patterns:
    - "Zero calls proven by provider_turns, attempts (request tap) and tripwire_calls, never by the absence of HTTP"
    - "Closed-set words plus typed TraceCode keep tier ids, transcripts and slot values out of VAE_TRACE"
    - "Guard capture counts derived from the golden file line count"

key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/TripwireProvider.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/GrammarLeg.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/GrammarLegTest.kt
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/AppGraph.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/EvidenceLineTest.kt
    - sample/src/test/resources/evidence-lines.golden.txt
    - scripts/sample-evidence-filter.sh
    - scripts/run-sample-gate1.sh
    - scripts/verify-sample-device-guard.sh

key-decisions:
  - "TraceFacts holder lives in EvidenceLine.kt; its words (kind, matched_lang, sel) are checked against closed sets and codes are typed TraceCode (the sample cannot construct one), so a tier id that fits the token alphabet still renders invalid_token"
  - "The grammar spec reports as the demo provider so the screen treats the leg as offline (no live readout); LegRunner routes the kind before key/fixture/budget preconditions"
  - "GrammarLeg.run takes policy, pack and cases as seams; the leak test lifts offlineOnly and proves only the tripwire is reached and the leg FAILs provider_called"
  - "first_model_index is reported as none for this leg (the field is for the router leg in plan 05)"

requirements-completed: []  # VER-06 is not ticked here; the phase verifier owns it

status: complete
duration: 35min
completed: 2026-10-07
---

# Phase 19 Plan 04: grammar_offline leg and the VAE_TRACE line Summary

**The free grammar tier answers an EN and an ES command and hands a near-miss on to Unhandled(cappedByPolicy=true), proven by provider_turns=0, attempts=0 and tripwire_calls=0 on every case, with the new VAE_TRACE line landing in all five vocabulary places in one commit.**

## Accomplishments
- `GrammarLeg`: sample pack (one create-item intent with a title text slot, one EN and one ES phrasing), a ladder of LocalGrammar then a single-shot tier whose capabilities and selection name only the tripwire, under `TierPolicy { offlineOnly = true }`. The real providers stay registered behind the request tap in the app (AppGraph passes the same provider list plus one tripwire).
- Verdict: PASS only when EN and ES complete in the expected language, the near-miss is Unhandled with cappedByPolicy, and all three counts are 0; otherwise FAIL with the first of provider_called, http_attempted, en_not_completed, es_not_completed, wrong_language, near_miss_not_capped. The line ends with one `VAE_VERDICT leg=grammar_offline` plus extras `en`, `es`, `near_miss_capped`, `provider_turns`, `attempts`, `tripwire_calls`.
- `VAE_TRACE` (leg, case, kind, capped, tiers_run, provider_turns, attempts, tripwire_calls, matched_lang, sel, eligible, picked_index, first_model_index, bypassed, sel_turns, codes): ALLOW_PATTERN, ALLOW_RE, one golden line, guard counts (derived from the golden line count) and LEGS (`grammar_offline`) changed together in the first commit.
- `GrammarLegTest` (7 tests): three-case PASS, near-miss never partial, policy leak reaches only the tripwire and FAILs, a tap-seen attempt FAILs `http_attempted` with zero turns and zero tripwire calls, wrong language and not-completed codes, an uncapped near-miss, canary title and transcript word absent from every line.

## Task Commits
1. Task 1 (tracer): `b71200b` feat - grammar_offline tracer and VAE_TRACE in five-place lockstep
2. Task 2: `92824e1` feat - the three cases and every vacuous-pass path
3. Task 3: `29008f5` test - VAE_TRACE carries only counts, codes and indexes

## Verification
- `:sample:testDebugUnitTest` full suite: 169 tests, 0 failures, 0 errors.
- `scripts/verify-sample-device-guard.sh`: `SAMPLE DEVICE GUARD OK scenarios=41` (filter_keeps_golden and leg_list_parity both cover the new line and leg).
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`. The golden file passes the host filter unchanged (kept=13 dropped=0).
- All plan acceptance greps pass; `git diff -- core providers keystore undo voice-adapter` is empty.

## Spend / device
No provider, key, money or device was touched (RT-05): the leg's tripwire and the tap counts are all 0 in every passing case, and the unit tests used fakes only. No finding to report.

## Deviations from Plan
None. Notes: the plan's `LegRunner` parameter for the leg's engine is `grammar: GrammarLegRig` (default `GrammarLegRig.standalone()`, tripwire as the only provider), placed before `nowSeconds` so existing test rigs compile unchanged. `TraceFacts` is in `EvidenceLine.kt` (no new file beyond the planned ones).

## Self-Check: PASSED
- Created files present: TripwireProvider.kt, GrammarLeg.kt, GrammarLegTest.kt; commits b71200b, 92824e1, 29008f5 present.
