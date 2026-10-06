---
phase: 15-planthenexecute-strategy
fixed_at: 2026-10-06T13:20:00-06:00
review_path: .planning/phases/15-planthenexecute-strategy/15-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 5
skipped: 3
status: resolved
---

# Phase 15: Code Review Fix Report

**Fixed at:** 2026-10-06
**Source review:** .planning/phases/15-planthenexecute-strategy/15-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (fix_scope: all)
- Fixed: 5
- Skipped: 3 (all documented acceptable-skips; no open blocker, critical or high)

**Verification environment:** all gates ran in the main checkout (not an isolated worktree), on branch
`gsd/phase-15-planthenexecute-strategy`. The orchestrator required the main tree (dirty unrelated `.planning` files,
one Gradle job at a time, host memory tight), so no worktree, temp branch or recovery sentinel was created.
Final gate, run after the last commit: `:core:test :core:detekt :core:scanBannedConstructs :core:apiCheck
:providers:test :providers:detekt` all green, detekt zero baseline held. `core/api.txt`, `StepSubmission.kt`,
`strategy/singleshot` and the commit package are untouched. The live probe was not run (WR-01 is compile-checked only).

## Fixed Issues

### WR-01: Live binding probe passes when scenarios are silently skipped or not exercised

**Files modified:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/PlanBindingLiveProbeTest.kt`
**Commit:** 7b3653b
**Applied fix:** Only a `PASS` verdict now counts. A budget-skipped scenario (`NOT_RUN`) and a model that rewrote the
dollar text (`literal_not_exercised`, now verdict `NOT_EXERCISED`) fail the final assertion, which prints the
offending `PLAN_PROBE` lines (codes and counts only). The 8-request hard ceiling and the per-scenario worst case of 4
are unchanged on purpose: raising the cost cap is an owner decision, and on the expected path (one request per
scenario) the guard never skips. If an earlier scenario replans and the guard skips a later one, the probe now fails
loudly instead of passing silently. Compile and detekt checked only; not run live.

### WR-02: A prose answer to the plan call drops the incoming carry

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt`,
`core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteOutcomeMappingTest.kt`
**Commit:** ab2ca86
**Applied fix:** `PlanFlow` now builds its `OutcomeHooks` per command and the `onNoToolCall` lambda closes over
`session.carry`; the strategy keeps only `onFailed`. New test
`aNoToolCallFailureOrAProseAnswerEscalatesWithTheIncomingCarry` pins the carry on both the `NoToolCall` failure path
and the END_TURN prose path (it is delivered to the next tier). Status: fixed, requires human verification (carry
semantics; the test passes).

### WR-03: A previewed, empty or read-finished step is classified as a failed step

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt`,
`core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteSuppressionTest.kt`
**Commit:** 73cafbc
**Applied fix:** Behavior deliberately unchanged: this is decision OI-6 (PREVIEW/READ/no-action from a mutating step
is a failed step), and changing it would alter RT-01-adjacent semantics. The review's option 2 was applied: the
strategy KDoc now states the rule (a step counts as run only when its changes were committed; any other result is a
failed step, with the one replan when nothing was applied or held before it). A new test pins a PREVIEW or READ as the
first step: one replan, then `plan_step_failed` escalation to the next tier, nothing committed. The reviewer's "empty
`ToolStep.Mutation`" case cannot occur: `ToolStep.Mutation` requires at least one mutation in its `init`, so no test
was added for it. Status: fixed (documentation and test only), requires human verification of the OI-6 wording.

### IN-01: `CommandOutcome.Completed.partial` KDoc says "two cases" and lists three

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt`
**Commit:** 2685c5e
**Applied fix:** "in either of two cases" became "in any of three cases". KDoc only.

### IN-03: The big binding KDoc is attached to a private helper class

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt`
**Commit:** 3333256
**Applied fix:** The grammar description moved onto `bindArguments`; `Reference` got a one-line comment pointing at it.
Comments only; the regex and logic are untouched.

## Skipped Issues

### IN-02: Frozen field-name constants are duplicated across `PlanParse.kt` and `PlanSchema.kt`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanParse.kt:11-15`
**Reason:** Acceptable skip: the fix cannot be applied without breaking a project gate. The change was tried and
committed locally, then dropped (never kept in history). A top-level `internal const val` compiles to a public static
field, which `ApiShapeTest.noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion` rejects ("public static
fields are frozen into the API"). A plain `internal val` avoids that but trips detekt `MayBeConst`, and the project
forbids banking lint debt or adding `@Suppress` beyond the one justified helper. The duplication is five short private
string constants that `PlanSchemaTest` and `PlanParseTest` exercise end to end, so one-sided drift is caught by tests.
**Original issue:** The five frozen field-name constants are declared in both files, so writer and reader could drift.

### IN-04: `\s` / `\S` in the reference regex is ASCII-only

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt:13`
**Reason:** Acceptable skip: both suggested fixes (trim trailing whitespace, or compile the regex with
`UNICODE_CHARACTER_CLASS`) change which strings count as references, which changes the frozen D-04 reference grammar
that the live probe (15-07) validated and the `submit_plan` description documents. D-04 is a one-way freeze and a
change needs a recorded probe failure and a gap plan, not a review fix. Noted for the owner: the near-reference edge
(trailing newline or NBSP) is a documented-literal behavior today.
**Original issue:** Unicode whitespace or a trailing newline lets a near-reference through as a literal or an
unresolved binding, against the KDoc's "not whitespace" wording.

### IN-05: Step ids have no length cap and are exposed verbatim in `remainingStepIds`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt:14,37`
**Reason:** Acceptable skip: a length cap would add a new `bad_id` rejection and, per the review's own note, belongs in
the frozen `submit_plan` description or the frozen id grammar (`ID_FRAGMENT`, part of D-04). Both are frozen at the
tag, so this is an owner decision for a later additive change. The engine itself never prints ids (`toString` shows
counts only) and the KDoc already tells consumers to treat `remainingStepIds` as data.
**Original issue:** Unbounded step ids could carry transcript text into the public `remainingStepIds` list.

---

_Fixed: 2026-10-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
