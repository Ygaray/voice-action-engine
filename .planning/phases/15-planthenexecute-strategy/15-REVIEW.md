---
phase: 15-planthenexecute-strategy
reviewed: 2026-10-06T00:00:00Z
depth: deep
files_reviewed: 32
files_reviewed_list:
  - API.md
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/PreparedStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanParse.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanReplan.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanRun.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanSchema.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanBindingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanParseTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanSchemaTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanTestSupport.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteBindingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteHoldTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteLimitsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteLookupTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteOutcomeMappingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteRedactionTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteReplanTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteRunTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteSuppressionTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RemainingStepIdsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - providers/build.gradle.kts
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/PlanBindingLiveProbeTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/PlanThenExecuteWireTest.kt
findings:
  critical: 0
  warning: 3
  info: 5
  total: 8
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-10-06
**Depth:** deep
**Files Reviewed:** 32 (every path in files.txt)
**Status:** issues_found

## Summary

I traced the call chain `PlanThenExecuteStrategy` -> `PlanFlow` -> `parsePlan` / `PlanRun` -> `CommitCoordinator.submit` -> `StrategyOutcome` -> `TierWalk` -> `CommandOutcome`, and checked the D-04 binding grammar, the replan predicate, RT-01 hold semantics and redaction paths against the code and the tests.

The core design holds up. I could not break any of these:

- **RT-01 hold semantics.** A hold after at least one committed step becomes a terminal partial `Completed` that keeps the commits. A hold with nothing committed becomes `Escalate(plan_step_held)`, which `TierWalk.hasWorked()` suppresses, because the coordinator's own `heldCount` is what it reads. `outcomeOf` uses `run.committedSteps`, not the held step's position.
- **Replan predicate.** `PlanRun.worked` (any applied or held action) matches `TierWalk.hasWorked()` (`appliedCount + heldCount`). Both count an applied-but-errored or throwing apply, so a replan cannot repeat a write that may have happened. The replan limit is 1 and a fresh `PlanRun` starts the second plan with an empty binding map.
- **Remaining step ids.** `plan.steps.drop(run.preparedSteps)` correctly leaves out the held step and the failed step, and includes a step stopped by an unresolved reference.
- **Binding.** It works on parsed string primitives, never on serialized text. Object keys are never touched. Substitution is single pass, so a bound value is not re-resolved. Conflicting target keys stay dropped. The regex is linear in input size, with no ReDoS shape.
- **Secrets.** `PlanStep`, `ParsedPlan`, `CommandOutcome.Completed`, `StrategyOutcome.*`, `PlanThenExecuteStrategy` and the replan digest print only counts, codes and engine-assigned indices. The canary sweep test covers them.
- **Public surface.** `StrategyOutcome.Completed` and `Escalate` keep their old public constructor signatures. The longer primary constructors are `internal`, and `CommandOutcome.Completed` gains only a getter, so the change is `+`-only.

No blockers. The findings below are one test-reliability gap on a one-way decision, one carry-forwarding inconsistency, and one step-classification gap, plus minor items.

## Warnings

### WR-01: Live binding probe passes when scenarios are silently skipped or not exercised

**File:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/PlanBindingLiveProbeTest.kt:122-125, 149-150, 217`

**Issue:** This probe is the evidence for the one-way D-04 syntax freeze, and its pass condition is weaker than it looks.

1. `runScenario` returns `ProbeVerdict(..., "NOT_RUN", "budget")` when `used + SCENARIO_WORST_CASE (4) > MAX_HTTP_REQUESTS (8)`. The final assertion is `verdicts.none { it.verdict == "FAIL" }`, so `NOT_RUN` counts as a pass.
2. The scenarios run in order: Anthropic S1, Anthropic S2, OpenAI S1, OpenAI S2. If any two earlier scenarios use two requests each (one replan each, which is exactly the behaviour the probe exists to observe), `used` is 6 when the fourth scenario starts, and 6 + 4 > 8 skips it. The test stays green with a model-scenario pair never run, and nothing says so.
3. `judgeLiteral` maps `literal_not_exercised` (the model did not keep the dollar text) to `PASS`. The S2 check that `$5.00` is not treated as a reference is then vacuous for a model that rewrote the amount.

The commit history reports "PASS, 4 of 8 requests", so the happy path ran. The assertion still cannot catch a regression or a different model's behaviour.

**Fix:** Fail loudly on anything that did not produce evidence:
```kotlin
assertTrue("scenarios not run: ${verdicts.filter { it.verdict == "NOT_RUN" }}", verdicts.none { it.verdict == "NOT_RUN" })
assertTrue("literal not exercised", verdicts.none { it.reason == "literal_not_exercised" })
```
Also make the budget guard honest. Either raise `MAX_HTTP_REQUESTS` to `4 * SCENARIO_WORST_CASE`, or use a per-scenario worst case of 2 (one plan plus one replan, with retries counted by the observer), so the guard cannot skip a scenario on the expected path.

### WR-02: A prose answer to the plan call drops the incoming carry, unlike every other plan escalation

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt:85-89` (hooks), compare `:243` and `:281-282`

**Issue:** The intent of D-11 is that SingleShot's carry still reaches Agentic through the plan tier. `malformed()` and the needs-lookup escalation forward `session.carry`. The `onNoToolCall` hook is built at strategy construction, where no session exists:
```kotlin
{ StrategyOutcome.Escalate(EscalationReason.NoToolCall()) }
```
So `Escalate(NoToolCall)` carries `null`, and the next tier starts without the previous tier's carry. This happens on the `ModelResult.Failure(NoToolCall)` path and on the END_TURN prose-answer path. It is realistic on providers or models declared `supportsForcedToolChoice = false`, where the tool choice is downgraded to auto and a prose answer is common.

`PlanThenExecuteOutcomeMappingTest.aNoToolCallFailureOrAProseAnswerEscalates...` asserts the reason but not the carry, so nothing pins this. `aTruncatedPlanEscalatesMalformedWithTheIncomingCarry` and the replan tests check carry only for the paths that already work. SingleShot has the same default, but its carry is not the property D-11 set out to preserve.

**Fix:** Intercept in `PlanFlow` rather than in the strategy-level hook. For example, build the hooks per attempt so the `onNoToolCall` lambda closes over `session.carry`:
```kotlin
private fun hooksFor(session: CommandSession, settings: Builder) = OutcomeHooks(
    { StrategyOutcome.Escalate(EscalationReason.NoToolCall(), session.carry) },
    { StrategyOutcome.Failed(FailureReason.Refusal()) },
    settings.onFailed,
)
```
Add a carry assertion to the no-tool-call mapping test.

### WR-03: A previewed, empty or read-finished step is classified as a failed step, which triggers a paid replan and `plan_step_failed`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanRun.kt:113-123`, consumed at `PlanThenExecuteStrategy.kt:260`

**Issue:** `dispatch` treats anything that is neither held nor "non-empty and all `COMMITTED`" as `StepFailed`. That also covers outcomes that are not failures:

- `ToolStep.Finished(PREVIEW)`: recorded as a `PREVIEW` action, not applied.
- `ToolStep.Finished(READ)`: `CommitCoordinator.finished` returns an empty `actions` list.
- `ToolStep.Mutation` with an empty mutation list: `applyAll` returns an empty `actions` list.

For such a step at index 0 with nothing yet applied, `canReplan(false)` is true. The tier then spends a second model call telling the model "plan_rejected / step_failed", and on a second occurrence escalates `plan_step_failed` to Agentic. For a step after a commit it ends the plan as a partial with `plan_step_failed` in the trace, which mislabels an intentional preview. `PlanThenExecuteSuppressionTest.aPreviewAfterStepOneIsSuppressed` codifies the label.

The digest sent to the model says the step failed, so the second plan may "fix" a step that was never wrong.

**Fix:** Decide deliberately and document it. Options:

1. Report a preview as its own non-replannable stop. It stays unbindable, ends the plan and lists the later steps, but does not set `canReplan`.
2. Document in `API.md` and the KDoc that any step not fully committed or held, including a preview, is a failure and may replan.

Either way, add a test for a first-step preview and for an empty `ToolStep.Mutation`, since neither is covered today.

## Info

### IN-01: `CommandOutcome.Completed.partial` KDoc says "two cases" and lists three

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt:350-360`

**Issue:** The `@property partial` text reads "in either of two cases", then adds a second "Or ..." and a third "Or ...". The RT-01 case 2 sentence was appended without updating the count.

**Fix:** Change to "in any of three cases", or restructure as a short list.

### IN-02: Frozen field-name constants are duplicated across `PlanParse.kt` and `PlanSchema.kt`

**File:** `PlanParse.kt:11-15` and `PlanSchema.kt:22-26`

**Issue:** `STEPS_FIELD`, `ID_FIELD`, `TOOL_FIELD`, `ARGUMENTS_FIELD` and `NEEDS_LOOKUP_FIELD` are each declared twice as private constants, and the schema string is documented as frozen at the tag. The writer (schema) and reader (parser) can drift silently. Nothing but tests catches a one-sided rename.

**Fix:** Declare them once as `internal const val` in `PlanSchema.kt` and import them in `PlanParse.kt`.

### IN-03: The big binding KDoc is attached to a private helper class

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt:16-28`

**Issue:** The KDoc describing the whole reference grammar sits directly above `private class Reference`, so it documents a two-field holder and not the `bindArguments` / `referencedStepIds` entry points that callers read.

**Fix:** Move the grammar description to `bindArguments`, or to a file-level comment, and give `Reference` a one-line comment.

### IN-04: `\s` / `\S` in the reference regex is ASCII-only, so near-references with Unicode whitespace or a trailing newline slip through as literals

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt:13`

**Issue:** `Regex("[\$](ID)\\.(\\S+)")` uses Java's default `\S`, which treats only ASCII whitespace as whitespace.

- `"$s1.item_id "` matches, with NBSP as part of the key. It then fails as `plan_binding_unresolved` at run time rather than being caught in whole-plan validation.
- `"$s1.item_id\n"` does not match because `matchEntire` needs the whole string. It reaches the app as a literal string with no rejection.

The KDoc says the key is "characters that are not whitespace". This is edge behaviour on model-emitted text and is not wrong per D-04. It does contradict the documented contract in the first case and silently passes a malformed id to the app in the second.

**Fix:** Either trim trailing whitespace before matching, or compile the regex with `RegexOption.UNICODE_CHARACTER_CLASS` so `\S` means non-Unicode-whitespace. Add a test for each.

### IN-05: Step ids have no length cap and are exposed verbatim in `remainingStepIds`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt:14,37` (`ID_FRAGMENT` / `isStepId`), consumed at `PlanThenExecuteStrategy.kt:261`

**Issue:** `^[A-Za-z][A-Za-z0-9_-]*$` has no maximum length. A model can encode transcript text into long ids such as `buy_milk_and_eggs_...`, and those ids land in a public list on the outcome. The KDoc already says "show them as data", and `toString` prints only the count, so the engine itself does not leak them to a sink. A length cap would make the guarantee mechanical, in the spirit of the project's secrets rule.

**Fix:** Reject ids longer than a small constant (for example 32) with `bad_id`, and say so in the `submit_plan` description (a frozen-description change, so a decision for the owner) or only in `API.md`.

---

_Reviewed: 2026-10-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
