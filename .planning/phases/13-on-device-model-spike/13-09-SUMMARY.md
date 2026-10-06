---
phase: 13-on-device-model-spike
plan: 09
subsystem: testing
tags: [on-device, verdict, spike-02, litertlm, gemma-4-e2b, grading-tolerance, rt-03]

requires:
  - phase: 13-on-device-model-spike
    provides: "committed filtered evidence and the window record (13-08); locked thresholds (13-01); verdict code and --print/--check script (13-03)"
provides:
  - "13-VERDICT.md: machine-computed per-envelope verdict (small RED, sb RED) with numbers next to thresholds, D-10 rows, cost, facts; --check reproduces it"
  - "13-VERDICT-MESSAGE.md: 9-line relay body, handed to the milestone master for relay"
  - "RT-03 grading tolerance in the host-side matcher plus an aggregate-only host re-score test of the private raw sb rows"
affects: [13-10, 13-11, 19]

actuals:
  tokens: 45000
  tasks: 3
  commits: 3

plan_head_before: c54cab2b819cdcabe94ad1fead1ca9556c306411
commits: 3

key-files:
  created:
    - .planning/phases/13-on-device-model-spike/13-VERDICT.md
    - .planning/phases/13-on-device-model-spike/13-VERDICT-MESSAGE.md
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/SbRescoreTest.kt
  modified:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/gold/GoldMatcher.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/TrialRunner.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/GoldMatcherTest.kt
    - spike-ondevice/build.gradle.kts

key-decisions:
  - "Verdict stays a pure function of committed evidence: the RT-03 re-score changed 0 rows, so no rescored evidence file was introduced; the effect is a documented host-side note with aggregates"
  - "Tolerance is id-gated in GoldMatcher (five plural items by id, article rule on SB-envelope ids), so the small envelope is graded strictly exactly as before"
  - "The verdict doc says tag-search instead of the SB tool name and carries no per-item ids, per the plan's disclosure prohibition"
  - "The SB peak-PSS 525 MB is reported with a caveat (winner-cell-only reading of a partial screen), not edited"

requirements-completed: []
requirements-advanced: [SPIKE-02]

status: complete
completed: 2026-10-06
---

# Phase 13 Plan 09: Spike verdict and relay message Summary

**The verdict is small RED (a measured fail on latency, memory, accuracy and false writes) and sb RED (unmeasured: the 4 h time-box ended at screen trial 28 of 80), reproduced by `scripts/verify-spike-verdict.sh --check` as `SPIKE_VERDICT_CHECK: OK lines=4`; the relay body is written and handed to the milestone master.**

## Verdict lines (verbatim, code SHA e362fb228b)

```
SPIKE_VERDICT envelope=small verdict=red reasons=fail:warm_p50,fail:warm_p95,fail:cold,unmeasured:sustained,fail:peak_pss,unmeasured:process_deaths,fail:semantic_en,fail:semantic_es,fail:false_writes cell=e2b.cpu.b.auto warm_p50_ms=20278 warm_p95_ms=28941 cold_ms=28182 sustained_ratio=na thermal_max=moderate peak_pss_mb=2268 deaths=na schema_valid=142/144 en=43/55 en_lb=0.656 es=35/55 es_lb=0.504 false_writes=2/34 kv_reuse=not_reused rf_enforced=ignored rf_enum_nested=enforced gpu_adreno730=gpu_ok route_ab=a:8/40:28632,b:40/40:18887
SPIKE_VERDICT envelope=sb verdict=red reasons=unmeasured:warm_p50,unmeasured:warm_p95,unmeasured:cold,unmeasured:sustained,unmeasured:process_deaths,unmeasured:schema_valid,unmeasured:semantic_en,unmeasured:semantic_es,unmeasured:false_writes cell=e2b.cpu.b.auto warm_p50_ms=na warm_p95_ms=na cold_ms=na sustained_ratio=na thermal_max=none peak_pss_mb=525 deaths=na schema_valid=na en=na en_lb=na es=na es_lb=na false_writes=na kv_reuse=not_reused rf_enforced=ignored rf_enum_nested=enforced gpu_adreno730=gpu_ok route_ab=a:5/20:47432,b:8/8:225707
SPIKE_CONTROL envelope=small model=g3_1b status=skipped_gated cell=none schema_valid=na p50_ms=na peak_pss_mb=na
SPIKE_VERDICT_META thresholds_sha=ec4933fb harness_sha=08ada3366b toolchain=ok pin=0.17.1
SPIKE_VERDICT_CODE sha=e362fb228b
```

`--check` last line: `SPIKE_VERDICT_CHECK: OK lines=4`.

## Tasks

| Task | Commit | Result |
|---|---|---|
| RT-03 tolerance and host re-score (pre-task, from the master's instruction) | `e362fb228b` | GoldMatcher tolerance, 2 new unit tests, SbRescoreTest; 4 suites pass (GoldMatcherTest 15, TrialRunnerTest 9, GoldSetTest 19, SbRescoreTest 1; 0 failures); HYGIENE OK |
| 1 (tracer) 13-VERDICT.md, `--check` reproduces it | `f4f2d86` | `SPIKE_VERDICT_CHECK: OK lines=4`; 2 SPIKE_VERDICT lines, 1 CODE line, 0 b_ id matches |
| 2 relay message | `35c1b88` | `MESSAGE OK []`; body 9 lines (10 lines before `relayed_to:`); fields present once each |
| 3 relay checkpoint | (in `35c1b88`) | Not performed by the executor. Handoff, see below |

## Grading tolerance (RT-03): mechanism and effect

- **Rule:** one tolerance, not a relabel (gold labels untouched). The tag-search tool does not fold plurals, so for five SB tag-search plural items a singular-stem query scores as equally correct; list-item articles are acceptable either way on SB-envelope items.
- **Where:** `GoldMatcher.argsMatch(expected, actual, itemId)` folds both sides, then applies the strict rules; `TrialRunner` passes the item id so a future device run grades the same way. The two-argument form and any item outside the sets stay strict.
- **Matcher change SHA:** `e362fb228bd5adc360efef03be6490676e68072d` (also the verdict code SHA).
- **Mechanism chosen:** verdict code stays over the committed evidence; the effect is a documented host-side note. Reason: the host re-score of the host-private raw rows (28 rows, 20 positive) gave device-scored 7 correct, host strict 7, host tolerant 7, changed by tolerance 0, and host strict equalled the device on every row (`device_vs_host_strict_mismatch=0`). None of the five plural items was in the 28 trials, and the article tolerance changed no row. So no SB number the verdict reads changed, and no rescored evidence file was needed. `--check` stays OK.
- **Small envelope:** untouched. It was scored on device by the pre-tolerance matcher; the gating is by item id (small ids never start with `b_`, the small gold has no tag-search tool and no list-item arrays), so the tolerant matcher gives the same small numbers.
- **Raw files:** `~/.local/share/vae-spike/raw/screen_sb.jsonl` and `sb-gold.json` stayed host-private and uncommitted; the re-score output holds aggregate counts only.

## Relay checkpoint (Task 3): handoff, not delivery

The executor cannot message the orchestrator. `13-VERDICT-MESSAGE.md` carries `relayed_to: milestone master, for orchestrator yahir-gsd-control-plane-3b (relay performed by the milestone master from this stage's return notes)` and `relayed_at` set to the handoff time, plus a `note: HANDOFF, not a confirmed delivery` line. The master sends the 9-line relay body from the stage return notes. Per-envelope verdict words: small=RED, sb=RED. No section 11 ledger row was written or committed, and nothing was published.

## Deviations from Plan

**1. [Instruction from the master, RT-03] Matcher tolerance and host re-score added before Task 1.** Scope as above; it changes no number.

**2. [Rule 2 - disclosure prohibition] The verdict doc and message say "tag-search" rather than naming the SB tool,** because the plan forbids SB tool names in the verdict and message. No b_ item ids appear in either.

**3. [Judgment] Task 3's recipient and time are a handoff record.** Filled as the master directed, with an explicit note that delivery is the master's act.

**4. [Finding, not fixed] The verdict code reports the sb peak PSS of the provisional winner cell only.** The sb winner `e2b.cpu.b.auto` (8 of 20 screen trials) never had a memory-peak sample before the time-box, so the `peak_pss_mb=525` is its init reading, while the sampled cell `e2b.cpu.a.auto` peaked at 4267 MB. 13-VERDICT.md states this under the SB table and in the message ("the one sampled sb cell peaked at 4267 MB"). Changing the code would change the verdict, so it was left as a candidate follow-up for 13-10 or the next milestone's harness work.

## Authentication Gates

None. No device access, no network, no spend (no adb or runner device subcommand was run).

## Verification

- `scripts/verify-spike-verdict.sh --check | tail -1` is `SPIKE_VERDICT_CHECK: OK lines=4`.
- Task 2 python check: `MESSAGE OK []`. `grep -c` of `relayed_to:` and `relayed_at:` each 1. Body before `relayed_to:` is 10 lines.
- `grep -cE 'b_(en|es|neg)_[0-9]{3}'` on 13-VERDICT.md and 13-VERDICT-MESSAGE.md prints 0. Acceptance greps for Gemma 4 E2B and 2027-02-01 hold.
- Not done by design: any device or behavioral verification.

## Self-Check: PASSED

- Files exist: 13-VERDICT.md, 13-VERDICT-MESSAGE.md, SbRescoreTest.kt.
- Commits present: `e362fb2`, `f4f2d86`, `35c1b88` (measured `git rev-list --count c54cab2..HEAD` = 3 before this summary).
