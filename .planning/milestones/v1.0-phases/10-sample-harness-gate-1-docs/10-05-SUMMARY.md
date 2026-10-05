---
phase: 10-sample-harness-gate-1-docs
plan: 05
subsystem: sample
tags: [legs, ver-02, ver-03, smoke, multi-turn, clarification, responses-probe, spend-guard]
status: complete
requires: [10-04]
provides:
  - "LegCatalog: nine legs with provider, model, strategy, two prompt variants, policy and request reservation"
  - "LegRunner: preconditions, one-leg-at-a-time, prompt-variant reruns, verdicts and evidence for every leg, plus followUp for A19"
  - "ProviderFactory / AttemptTap / LegContext: the three real providers on one interceptor-free OkHttp client, every HTTP attempt counted and logged"
  - "DemoProvider / FollowUpTurnRenderer / FollowUpContext: offline clarification and partial demos"
  - "RequestBudget.runsOf / recordRun: persisted per-leg run counts"
affects: [10-06, 10-07, 10-10]
tech-stack:
  added: []
  patterns: ["fake transports report attempts through the same tap the real observers use", "preconditions refuse loudly before any provider call", "capture-only verdict for a model the endpoint rejects"]
key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/DemoProvider.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/net/ProviderFactory.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/LegTestSupport.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SmokeLegTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/AgenticLegTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/MultiTurnLegTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/ClarificationFlowTest.kt
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt
key-decisions:
  - "A demo tier must declare the demo provider: the engine refuses a provider the tier did not declare, so the demo strategies use StrategyCapabilities(setOf(demo)) and run on their own SampleEngine"
  - "SmokeResolver delegates to CannedToolExecutor.prepare, so the resolver records tool name and argument key names only and still returns the canned mutation"
  - "VAE_ENV is emitted for the VER-02 leg only; smokes and multi legs do not need the cache minimum or prefix size"
  - "LegRunner takes demoSink and demo provider as defaulted constructor parameters ahead of nowSeconds, so tests can observe the follow-up's commit sink and requests without changing the production call"
requirements-completed: [VER-02, VER-03, VER-04]
commits: 3
plan_head_before: 6a3d274b8bd8d02974fda2e101497f255d5899a8
actuals:
  tokens: 18400
  tasks: 3
  commits: 3
duration: 55 min
completed: 2026-10-01
---

# Phase 10 Plan 05: Gate-1 Legs Summary

Every leg the Gate-1 tester will press now exists as host-tested code over the one composition root: the VER-02 cold agentic run, the three single-shot EDIT smokes, the two extended multi-turn legs, the optional Responses-only probe, and two offline demos for A19 clarification follow-ups and partial outcomes. No key, device or network was used.

## What was built

- **Task 1 (tracer, 4152506):** `LegCatalog` (smoke entries), `ProviderFactory` with `AttemptTap` and `LegContext`, `LegRunner` (preconditions, mutex, prompt variants, smoke judging, evidence), `RequestBudget.legRuns`, and the test rig (`MemoryVault`, `ListSink`, `AttemptingFake`, `legRig`). The Anthropic EDIT smoke runs end to end and yields the literal `VAE_ATTEMPT`, `VAE_SMOKE ... optional_absent=true prompt_variant=0`, `VAE_VERDICT ... verdict=PASS key_charset=ok trigger=ui` and `VAE_BUDGET core=1` lines.
- **Task 2 (feat, 86ff270):** `ver02` (fixture agentic tier, warm-window refusal at 360 s, `VAE_ENV` with `min_cacheable` and `prefix_chars`, cache verdict) and `multi_openai` / `multi_openrouter` (live synthetic tools, tool-result replay, multi-turn verdict that records the turn-2 cache read).
- **Task 3 (feat, 1c7a7ed):** `DemoProvider`, `FollowUpTurnRenderer`, `LegRunner.followUp`, `demo_clarify`, `demo_partial` and `responses_probe`.

## Final leg table

| Leg | Provider / model | Kind | Reservation (ceiling) | Expected requests | Prompt variants |
|-----|------------------|------|-----------------------|-------------------|-----------------|
| ver02 | anthropic / claude-haiku-4-5 | agentic over LE-1 fixture, 6 iterations | 6 | 2-4 | 2 |
| smoke_anthropic | anthropic / claude-haiku-4-5 | single-shot forced edit_item | 3 | 1 | 2 |
| smoke_openai | openai / gpt-5.4-mini | single-shot forced edit_item | 3 | 1 | 2 |
| smoke_openrouter | openrouter / openai/gpt-5.4-mini | single-shot forced edit_item | 3 | 1 | 2 |
| multi_openai | openai / gpt-5.4-mini | agentic, 3 iterations, read tool find_items | 6 | 2 | 2 |
| multi_openrouter | openrouter / openai/gpt-5.4-mini | agentic, 3 iterations, read tool find_items | 6 | 2 | 2 |
| responses_probe | openai / gpt-6-astra | forced create_item, optional pool, CAPTURED | 1 | 0-1 | 1 |
| demo_clarify | demo / demo-model | agentic incl. ask_user, offline | 0 | 0 | 1 |
| demo_partial | demo / demo-model | single-shot forced create_item, offline | 0 | 0 | 1 |

Core ceiling stays 33 (+1 optional), as set in 10-04. A rerun picks `min(runsOf(leg), variants - 1)`.

## Test counts

New: SmokeLegTest 7, AgenticLegTest 4, MultiTurnLegTest 3, ClarificationFlowTest 3 (17 tests, 0 failures). Whole `:sample:testDebugUnitTest`: 16 suites, 0 failures.

## Gate results

| Gate | Result |
|------|--------|
| `:sample:testDebugUnitTest` (all suites) | green |
| `:sample:compileReleaseKotlin` (substitute for the non-existent release unit-test task) and `:sample:assembleDebug` | green |
| `./gradlew check --offline` | green |
| `scripts/verify-repo-hygiene.sh` | HYGIENE OK |
| `git diff --stat 6a3d274 -- core providers keystore` | empty |
| `addInterceptor` / `addNetworkInterceptor` in ProviderFactory.kt | 0 |

## Deviations from Plan

**1. [Rule 3 - Blocking] `:sample:testReleaseUnitTest` does not exist** (known from 10-02 to 10-04). Used `:sample:testDebugUnitTest` plus `:sample:compileReleaseKotlin`. No build file changed.

**2. [Rule 1 - Bug] Demo tiers were refused by the engine (`provider_unavailable`).** The pipeline only lets a tier use providers it declared, and the default declaration covers the three real providers and on-device. The demo strategies now declare `StrategyCapabilities(setOf(demo))`. Found by the first run of the demo tests; fixed inside Task 3.

**3. [Scope detail] Two defaulted constructor parameters on `LegRunner`** (`demoSink`, `demo`), placed before `nowSeconds`, so the follow-up's commit sink and the demo provider's requests are observable in tests. The plan's constructor list is otherwise unchanged.

**Total deviations:** 3 (one Rule 3, one Rule 1, one scope note). **Impact:** none on the contract.

## Authentication Gates

None. No device, adb, push-test-key, key file or live provider call was touched (D-01, D-13). Every test runs under `NoNetworkGuard`; key placeholders are obviously fake.

## Notes for downstream plans

- `LegRunner(engine, fixture, vault, budget, tap, sink, demoSink = NoOpCommitSink, demo = DemoProvider(), nowSeconds)`; production wiring is `ProviderFactory.create(tap, budget)` into one `SampleEngine`. `run(leg, trigger)` and `followUp(previous, option, trigger)` are the two entry points a UI needs.
- A refusal is one loud `VAE_VERDICT ... verdict=REFUSED reason=<code>` line. Codes: `another_leg_running`, `fixture_absent|sha_mismatch|malformed`, `key_<StateWord>`, `budget`, `warm_window` (with `remaining=<s>`), and for follow-ups `not_a_clarification`, `unknown_option`.
- The probe prints `verdict=CAPTURED reason=<failure code> http=<status>`; it is never PASS or FAIL.
- `key_charset=ok` is written only when the leg's own provider answered HTTP 200 in that run.

## Self-Check: PASSED

- All nine created files and the modified `RequestBudget.kt` exist on disk.
- Commits 4152506, 86ff270, 1c7a7ed present; `commits: 3` measured with `git rev-list --count` against `plan_head_before`.
- No change under core/, providers/, keystore/; no STATE.md or ROADMAP.md edit; nothing under .planning/graphs, .gsd, intel, config.json, state.json staged.
