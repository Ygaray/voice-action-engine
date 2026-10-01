---
phase: 10-sample-harness-gate-1-docs
plan: 04
subsystem: sample
tags: [verdicts, evidence, spend-guard, ver-02, ver-03, cache-band, closed-vocabulary]
status: complete
requires: [10-03]
provides:
  - "CacheVerdict: VER-02 classification (WARM first, http/outcome, single_turn, cache_not_engaged, no_cache_read, read_write_parity, OUT_OF_BAND, PASS) with edges derived from anchor 7016 and band 5%"
  - "SmokeVerdict / MultiTurnVerdict: VER-03 smoke and multi-turn rules, Anthropic single initial 200 carry, optional-absent word"
  - "EvidenceLine closed vocabulary (11 line types), LegId (9 wire strings), EvidenceSink, LogcatEvidenceSink (tag VaeSample), EvidenceListener, ALLOW_PATTERN"
  - "RequestBudget / FileBudgetStore / BudgetedProvider: persisted 33(+1) request ceiling, 3-request worst case per call, 360 s warm window"
  - "CostEstimate: per-turn USD estimate from usage; unknown models report unknown"
affects: [10-05, 10-06, 10-07, 10-10]
tech-stack:
  added: []
  patterns: ["pure verdicts over recorded facts", "private-constructor closed vocabulary with token alphabet", "persisted spend guard that refuses before sending"]
key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/verdict/VerdictTypes.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/verdict/CacheVerdict.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/verdict/SmokeVerdict.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/CostEstimate.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/CacheVerdictTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SmokeVerdictTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/EvidenceLineTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/RequestBudgetTest.kt
    - sample/src/test/resources/evidence-lines.golden.txt
  modified: []
key-decisions:
  - "The cache verdict order puts WARM first, then transport/outcome failures, then single_turn, cache_not_engaged, no_cache_read, parity, and only then the band, so an out-of-band number is reported with its measured write and never as a pass"
  - "optional_absent is true only on PASS, inconclusive for model_filled_optional or an unevaluable call, false when the call broke the schema (required_missing, unknown_key)"
  - "VAE_BUDGET lists the three known providers (anthropic, openai, openrouter) plus core, optional and est_usd"
requirements-completed: [VER-02, VER-03]
commits: 3
plan_head_before: d5c13d99f87f2e29dc8d881842720a9ff243464a
actuals:
  tokens: 19600
  tasks: 3
  commits: 3
duration: 40 min
completed: 2026-10-01
---

# Phase 10 Plan 04: Verdicts, Evidence Vocabulary and Spend Guard Summary

Every Gate-1 decision is now pure, host-tested Kotlin: the VER-02 cache verdict with the 7,016 +-5% band and warm-run rule, the VER-03 smoke and multi-turn verdicts, a closed-vocabulary evidence line type that cannot carry free text, and a persisted request ceiling that refuses a call before it is sent.

## What was built

- **Task 1 (tracer, 379d644):** `VerdictTypes` (`VerdictKind`, `Verdict`, `AttemptRecord`, `OutcomeSummary.of`), `EvidenceLine` (private constructor, typed factories, token alphabet `[A-Za-z0-9_.:/,\[\]-]` up to 96 chars, else `invalid_token`), `EvidenceListener`, `LogcatEvidenceSink`, `CacheVerdict`. The tracer runs a fake two-turn Anthropic agentic run through `SampleEngine` and asserts the two literal SecondBrain-format `VAE_TURN` lines and `VAE_VERDICT leg=ver02 verdict=PASS turn1_write=7016 min_read=7016 calls=2 trigger=ui`.
- **Task 2 (feat, 3a6232a):** full cache matrix tests (band edges 6665/6666/7366/7367, parity edges 6945/6946/7086/7087, warm-wins, single turn, not engaged, later zero read, http and outcome failures), `SmokeVerdict` (required and optional key sets read from the tool schema, `anthropicStrict` needs exactly one initial 200, a model-filled optional is INCONCLUSIVE), `MultiTurnVerdict` (records, never asserts, the turn-2 cache read).
- **Task 3 (feat, 6823709):** `RequestBudget` with `FileBudgetStore` (temp file then atomic move), `BudgetedProvider` (returns `sample_budget_exhausted` without calling the delegate), `CostEstimate`, the canary, allow-pattern and golden tests, and `evidence-lines.golden.txt`.

## Gate results

| Gate | Result |
|------|--------|
| `CacheVerdictTest` / `SmokeVerdictTest` / `EvidenceLineTest` / `RequestBudgetTest` | 11 / 9 / 7 / 11 tests, 0 failures (38 new) |
| Whole `:sample:testDebugUnitTest` | 12 suites, 0 failures |
| `:sample:compileReleaseKotlin` (substitute for the non-existent release unit-test task) | green |
| `./gradlew check --offline` | green (exit 0) |
| `scripts/verify-repo-hygiene.sh` | HYGIENE OK |
| Golden file | 11 lines, one per line type; every line starts `VAE_<TYPE> `; matches `ALLOW_PATTERN` |
| `grep -nE '\b(6665\|6666\|7366\|7367)\b' CacheVerdict.kt` | prints nothing |
| `Log.` calls outside `EvidenceLine.kt` in sample main | none |
| `git diff --stat PLAN_BASE -- core providers keystore` | empty |

Cost check pinned in the tests: Haiku `Usage(40, 0, 7016, 20)` prices to 0.00891 USD and gpt-5.4-mini `Usage(1000, 0, 0, 100)` to 0.00120 USD.

## Deviations from Plan

**1. [Rule 3 - Blocking] `:sample:testReleaseUnitTest` does not exist** (known from 10-02 and 10-03). Used `:sample:testDebugUnitTest` plus `:sample:compileReleaseKotlin`. No build file changed.

**2. [Ordering] `EvidenceLine.budget` added in Task 3, not Task 1.** It takes a `BudgetSnapshot`, which lives in `RequestBudget.kt` (Task 3). Adding it in Task 1 would not have compiled. All other factories landed in Task 1 as planned.

**3. [Scope detail] Test count of the plan's behaviors.** Beyond the listed behaviors I added a few guard tests: derived band edges (a different anchor and band move them), a token-length boundary test, list-injection (`a,b` as a tool name is replaced, not split), loud-line rules, wrapper identity, a restart-survival check that no temp file is left, and cache-bucket pricing.

**Total deviations:** 3 (one Rule 3, two ordering/scope notes). **Impact:** none on the contract.

## Authentication Gates

None. No device, adb, push-test-key, key file or live provider call was touched (D-01, D-13). All canaries are synthetic and not key-shaped.

## Notes for downstream plans

- 10-05 adds `legRuns`, `runsOf` and `recordRun` to `BudgetState` / `RequestBudget`. `BudgetState` is a plain class, so the extension is additive. `RequestBudget.record(provider: ProviderId, optional: Boolean)` is the call the attempt tap makes.
- `EvidenceListener(leg, sink, prefixChars: Int? = null)` exposes `turns`; pass `clock = { 0L }` in tests that compare literal `latency_ms` values.
- Field names for hosts that parse lines: `VAE_VERDICT` carries `leg verdict [reason] <extras> [key_charset] trigger`; `VAE_BUDGET` carries `core optional anthropic openai openrouter est_usd`; `VAE_ATTEMPT` carries `leg provider n kind http finish tool_calls`.
- `optional_absent` words: `true` (PASS), `false` (schema broken), `inconclusive` (filled optional or not evaluable).

## Self-Check: PASSED

- All eleven created files exist on disk.
- Commits 379d644, 3a6232a, 6823709 present; `commits: 3` measured from the persisted ledger against `plan_head_before`.
- Acceptance criteria re-run; no change under core/, providers/, keystore/; no STATE.md or ROADMAP.md edit.
