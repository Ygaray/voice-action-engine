---
phase: 07-singleshot-strategy
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/07-singleshot-strategy/07-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 8
skipped: 3
status: resolved
---

# Phase 7: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/07-singleshot-strategy/07-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (0 critical, 4 warning, 7 info; fix_scope = all)
- Fixed: 8
- Skipped: 3 (all documented acceptable-skips, see below)

**Verification:** per-fix `./gradlew :core:test :core:detekt --offline` (or `:providers:test :providers:detekt` for IN-06),
green each time. Final `./gradlew check --offline` in the main checkout (no worktree) is BUILD SUCCESSFUL: all OkHttp
legs, detekt zero baseline (no new `@Suppress`, no config change), Metalava compatibility. Final
`scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK` with the seven expected sealed types.
`SingleShotLimitsTest` (6 / 60000 / 4096), the SHOT-03 acceptance tests, `RedactionCanaryTest`, `ApiShapeTest` and the
files under `evidence/` are untouched. No tag, no `api.txt`, no change to the signed-off seam type shapes.

## Fixed Issues

### WR-01: `forceTool = false` still fails when the snapshot has no `singleShotTool`

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotRequestTest.kt`
**Commit:** c948c65
**Applied fix:** The tool name is now required only when forcing. `withTooling` computes the `ToolChoice` once (`Required(name)` when `forceTool` and a name exists, `Auto()` when not, the `single_shot_tool_missing` failure only for `forceTool` with no name) and carries it on the private `Attempt`, so the tool-name parameter no longer threads through the request path. `Builder.forceTool` KDoc now states both behaviors. New test `forceToolFalseWorksWhenTheSnapshotNamesNoSingleShotTool` (null `singleShotTool`, `forceTool = false`: one provider call, resolver runs, `ToolChoice.Auto`). The existing `forceTool = true` missing-tool test is unchanged and still passes. Logic change: fixed, requires human verification.

### WR-02: Default clock freezes the device time zone at builder time

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotUserTurnTest.kt`
**Commit:** ffd5a47
**Applied fix:** Added a private `CurrentZoneClock` (zone read from `ZoneId.systemDefault()` on every call, instant from `Instant.now()`) as the `Builder.clock` default; the property type stays `Clock`, so the public signature is unchanged. KDoc now says the zone is read again for every command. New test builds the strategy once, switches `TimeZone.setDefault` between two commands (Asia/Tokyo then America/New_York, restored in `finally`) and asserts each rendered user turn carries the zone current at that command. Logic change: fixed, requires human verification.

### WR-03: The `DispatchResult` of every submit is discarded, so the reply is shown whether or not anything was applied

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotResolveTest.kt`
**Commit:** 4cb62a0
**Applied fix:** Used the reviewer's third option, narrowed. `submitAll` now keeps the `DispatchResult` of the combined mutation step and returns `Completed(null)` when it reports `isError`, so a resolver reply such as "Saved ..." is never shown after a failed apply (the outcome's `executed` list still carries the `IS_ERROR` actions). A held proposal keeps the reply on purpose: held is the normal pending state in the confirm flow (SHOT-03 s2/s3), the outcome carries `held`, and dropping it broke `RedactionCanaryTest.noCanaryLeaksFromASingleShotRun`, which pins the reply surviving a hold. The `Finished` steps' results are not used for the decision, because a resolver-authored rejection step is an expected error. No public signature changed (`Resolution.Steps` untouched); `Resolution.Steps.reply` KDoc documents the rule, including that a reply should read correctly for a held change. New tests: held keeps the reply, a failing apply withholds it, a rejection step beside an applied mutation keeps it. Logic change: fixed, requires human verification of the contract choice (reply policy) by the orchestrator before the tag.

### WR-04: `Resolution.Steps` is documented as "in order", but the tier reorders and merges steps

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`
**Commit:** da631a6
**Applied fix:** Documentation only, describing the real behavior (kept as intentional, already pinned by `SingleShotResolveTest`): finished steps are submitted first in list order; all mutations are then folded, in list order, into one combined mutation step so the gate decides once; the commit sink therefore sees finished steps before mutations. Added to both the `Resolution.Steps` KDoc and the `SingleShotStrategy` class KDoc.

### IN-01: Unreachable `MalformedResponse` branch in `route`

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`
**Commit:** c2bc9d7
**Applied fix:** Replaced `calls.firstOrNull() ?: Failed(MalformedResponse())` with `calls.first()` and a comment stating the invariant (`decideResult` returns null only for a successful answer holding at least one tool call). The double derivation of the tool calls was left as is (private, no behavior difference).

### IN-03: `SingleShotStrategy` cannot declare its providers

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotRequestTest.kt`
**Commit:** 6f6f3f9
**Applied fix:** Added `Builder.capabilities: StrategyCapabilities` (default `ANY_PROVIDER`, so existing behavior is unchanged) and `override val capabilities` on the strategy. Purely additive; no signed-off seam type changed. New test checks the default and a narrowed declaration.

### IN-04: `ToolSpecProvider` KDoc contradicts its signature

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpecProvider.kt`
**Commit:** 31e008c
**Applied fix:** Reworded the KDoc: the system text and tools should not depend on the command's content; the command is passed only so a stable fact such as the language can pick among a small fixed set of snapshots, each costing its own cache write. Signature untouched (signed-off).

### IN-06: Router model ids are matched case-insensitively for the vendor prefix but case-sensitively for the rules

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModels.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModelsTest.kt`
**Commit:** 556813e
**Applied fix:** Verified from the repo that `ChatModels.key` passed the id unchanged to the all-lowercase rule patterns. The routed id is now lowercased once in `key` (lookup only; the wire id and the app-override key are still the id the app passed, and the direct-vendor path is untouched). The `gpt-oss` check no longer lowercases a second time. Test: `openai/GPT-5.4-mini` and `OpenAI/GPT-4o-Mini` get the same wire rules as their lowercase forms. Logic change: fixed, requires human verification.

## Skipped Issues

### IN-02: `Resolution.Failed` and `Resolution.Escalate` lack the convenience constructors their `StrategyOutcome` twins have

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt:80,95`
**Reason:** Acceptable-skip: signed-off shape, defer to orchestrator. `Resolution` is a seam type approved at 07-03 (`evidence/seam-signoff.txt`: SIGNOFF: APPROVE). The review's urgency premise does not hold: a one-argument secondary constructor is purely additive and can be added after the tag exactly as `Steps(steps)` already is, so nothing is lost by waiting; the orchestrator can decide whether to add it in a later additive release.
**Original issue:** Inconsistent constructor shape; callers must write an explicit `null` for `details` / `carry`.

### IN-05: OpenRouter strict-mode requests omit `parallel_tool_calls: false`

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt:68-71`
**Reason:** Acceptable-skip: intentional and pinned. `SingleShotWireTest.openRouterBodyOmitsParallelToolCallsAndUsesTheFirstCall` asserts the omission, the strategy acts on the first call only, and the resolver is required to validate arguments. The review itself says no change is required if the omission is deliberate.
**Original issue:** Strict function calling is not guaranteed to match the schema when parallel calls are generated.

### IN-07: The automatic-caching floor is applied to every `gpt-` id

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt:67-68`
**Reason:** Acceptable-skip: unverifiable from the repo. The review marks it an unconfirmed lead; the Phase 5 research records `gpt-*` / `o<digit>` at 1,024 tokens as a deliberate rule, the repo holds no evidence that the `gpt-3.5` / base `gpt-4` families do not cache, and `OpenAiModelRulesTest` pins the current table. Changing it without a checked OpenAI source risks a wrong silent diagnostic. Apps can already override per model id.
**Original issue:** Possible false `CacheNotEngaged` events for non-caching GPT families.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
