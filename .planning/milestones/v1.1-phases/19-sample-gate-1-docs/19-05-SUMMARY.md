---
phase: 19-sample-gate-1-docs
plan: 05
subsystem: testing
tags: [gate-1, plan-then-execute, router, start-tier-selection, spend-guard, vae-trace]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 19-04 VAE_TRACE line, TraceFacts, GrammarLeg pack and the leg runner routing
provides:
  - plan_live leg (PlanThenExecute over a stateful ItemStore; second step bound to the first step's new id)
  - router_live leg (grammar head, single-shot and plan tiers, TierSelector.Router, PickContext accounting)
  - ItemToolExecutor / ItemTools (create_item with parent_id, rename_item, both returning the key id)
  - in-app request ceilings lowered to 15 core / 16 total, equal to the ASK in 19-LIVE-LEG-DECISION.md
affects: [19-06, 19-07, phase-20]

actuals:
  tokens: 16400
  tasks: 3
  commits: 3

plan_head_before: 28893a6964172df851f3e203945b4967a1ac5b6b
commits: 3

tech-stack:
  added: []
  patterns:
    - "A stateful ItemWorld (fresh store + journal + executor) per run, so a binding is proven by the store, never by a count"
    - "Router verdict read from CommandTrace.selection; indexes are positions among the eligible model tiers"
    - "Reservations read from LegCatalog in the budget fit test, so a growing reservation fails the test"

key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemToolExecutor.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/PlanLegs.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/PlanLegTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/RouterLegTest.kt
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt
    - sample/src/debug/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/EvidenceLineTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/RequestBudgetTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
    - scripts/run-sample-gate1.sh

key-decisions:
  - "An unresolved plan reference is named first (not_bound) because it is the more specific cause of the partial completion it leaves behind; otherwise partial would hide it"
  - "PD-05 router verdict order: no selection -> FAIL no_selection; router_fallback -> FAIL; picked_index 0 -> INCONCLUSIVE picked_first; then eligible/pick, walk-at-pick, one selection turn, router tokens within trace.usage"
  - "Verdict extras only carry counts and indexes; picked_index is omitted (not -1) when no tier was picked"

requirements-completed: []  # VER-06 is not ticked here; the phase verifier owns it

status: complete
duration: 55min
completed: 2026-10-07
---

# Phase 19 Plan 05: plan_live, router_live and the 15/16 ceilings Summary

**Two live legs are now JVM-proven with fakes: `plan_live` shows the second step of a PlanThenExecute command using the first step's new id on a stateful store, and `router_live` shows a router-chosen start tier (index 1, the plan tier) in the trace with the router's tokens counted; the in-app spend guard now stops at the requested 15 core / 16 total requests.**

## Accomplishments
- `ItemTools` / `ItemToolExecutor`: `create_item` (title required, `parent_id` optional) and `rename_item` (id and title) map to the existing `CreateItem` / `RenameItem` mutations with tickets from a journal; both descriptions say they return the key `id`. A bad or unknown call is a finished error step with fixed text, never a throw. `ItemResolver` adapts the executor to the single-shot tier.
- `PlanLegs`: plan tier, router ladder (grammar head, single, plan) with `TierSelector.Router` and synthetic one-line `tierDescriptions`, and the two verdict classifiers. `plan_live` PASS = Completed, not partial, 2 commits, no remaining steps, store shows item 2 under item 1; FAIL codes `not_bound`, `not_completed`, `partial`, `commit_count_<n>`, `remaining_steps`. Extras `committed`, `bound`, `remaining`, `replanned`.
- `router_live` verdict: PASS needs `picked`, picked_index 1, first model tier that ran equal to the pick, exactly one selection turn, router tokens > 0 and not above `trace.usage`. `picked_index 0` is INCONCLUSIVE `picked_first`; `router_fallback` and no selection (a one-model-tier ladder makes no router call) FAIL.
- `LegRunner` runs both through `runLocked` (budget, key, listener, tap) and emits `VAE_TRACE` (case 1) before `VAE_OUTCOME`, `VAE_VERDICT`, `VAE_BUDGET` for the PLAN and ROUTER kinds only; every v1.0 leg's lines are unchanged. `LegId.PLAN_LIVE` / `ROUTER_LIVE`, `LEGS` and the wire-vocabulary test moved together (leg_list_parity green).
- Ceilings: `CORE_REQUEST_CEILING = 15`, `TOTAL_REQUEST_CEILING = 16`; reservations plan 6, router 9 (worst case 3 requests per call). A new fit test reads the real reservations and proves both legs fit one install (including plan fully spent first, then router) and the optional pool admits exactly one probe request.

## Router fixture index-1 unambiguity check (RT-05 e)
Done and pinned in `RouterLegTest.theFixtureMakesThePlanTierTheOnlyCorrectPick`:
- Both router prompts ask for exactly two writes where the second is "under" the first ("create an item called gamma, and then create an item called delta under it"; the variant adds "The second change needs the id that the first change makes").
- The tier at index 0 of the model tiers is the single-shot tier, forced to one `create_item` call, so it cannot know the first item's new id. Its description to the router says "One simple change to one item, with nothing that depends on an earlier change"; the plan tier's says "Several changes in a row, where a later change needs the new id that an earlier change makes". The transcript matches only the second.
- The transcript is not a grammar phrasing (`add {title} to my list`), so the grammar head hands it on (trace shows the grammar attempt `no_match`; the engine records `grammar_input_too_long`).
- A failed live router leg later is a FINDING (wording), never re-run, per RT-05.

## Task Commits
1. Task 1 (tracer): `1286c7d` feat - plan_live: second step bound to the first step's new id on a stateful store
2. Task 2: `2450f3e` feat - router_live with TierSelector.Router, selection in VAE_TRACE, all non-PASS paths
3. Task 3: `9391797` feat - ceilings 15/16 and the reservation fit test

## Verification
- `:sample:testDebugUnitTest` full suite: 187 tests, 0 failures, 0 errors (PlanLegTest 8, RouterLegTest 9 incl. the one-tier ladder and a no-selector linear walk).
- `scripts/verify-sample-device-guard.sh`: `SAMPLE DEVICE GUARD OK scenarios=41`; `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`.
- Acceptance greps all pass: `parent_id` in the executor, 0 `CannedToolExecutor` in PlanLegs, `LEGS` holds plan_live and router_live, `not_bound`, `tierDescriptions`, `TierSelector.Router`, `picked_first`, `no_selection`, ceilings 15/16, no `requests N/33` in tests, RouterLegTest has 9 `@Test`.
- `git diff` of core, providers, keystore, undo and voice-adapter over the plan's commits is empty.

## Spend / device
No provider, key, money or device was touched: every test uses scripted fakes behind the real tap and budget wrapper. The legs run live for the first time in plan 07 under the relayed GO; if that GO is below 16 requests, plan 07 stops before any device step (PD-04).

## Deviations from Plan
None of Rules 1 to 4. Notes:
- `DebugTools.kt`'s KDoc ("33+1 request budget") was updated to "15+1" so the debug-intent comment matches the new constants; it is outside the plan's `files_modified` but a documentation-only touch.
- `ItemTools` (the shared spec object) lives in `ItemToolExecutor.kt` beside the executor, as the plan's "tool specs and a ToolExecutor" artifact implies.
- Observation, not changed: the per-call guard (`headroomFor`) still assumes 3 requests per logical call for the optional probe as well, so if both live legs consumed their full worst-case reservations (core 15), a probe call would be refused with `sample_budget_exhausted` (fail closed) even though `canStart(1, optional)` admits it. Real use is about 4 to 6 requests, so the probe is expected to run; the window plan should run the probe before relying on that margin if the counts climb.

## Self-Check: PASSED
- Created files present: ItemToolExecutor.kt, PlanLegs.kt, PlanLegTest.kt, RouterLegTest.kt; commits 1286c7d, 2450f3e, 9391797 present.
