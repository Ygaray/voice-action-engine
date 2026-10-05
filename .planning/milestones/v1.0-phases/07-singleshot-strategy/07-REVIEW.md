---
phase: 07-singleshot-strategy
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpecProvider.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/UserTurn.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
  - config/detekt/detekt.yml
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeAiProvider.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotLimitsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotRequestTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotUserTurnTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/SingleShotWireTest.kt
findings:
  critical: 0
  warning: 4
  info: 7
  total: 11
status: resolved
---

# Phase 7: Code Review Report

**Reviewed:** 2026-10-01
**Depth:** standard
**Files Reviewed:** 18 (of the 33 named in scope)
**Status:** issues_found

## Summary

I read all 13 main-source files in full. I also read the `FakeAiProvider` fixture and four of the test files
(`SingleShotLimitsTest`, `SingleShotRequestTest` and `SingleShotUserTurnTest` partly, `SingleShotWireTest` in full).
The other 15 test files were not opened. I read `ToolStep`, `CommitCoordinator`, `TierWalk`, `BoundModel`,
`AnthropicTransport`, `ChatModels`, `ChatVendor`, `PolicyPreCheck` and `StrategyOutcome` as call-chain context.

The pipeline logic is mostly sound:
- The stop-reason ordering means a refused or truncated answer is never resolved into a write.
- The ceiling checks are consistent between the pre-call and post-call guards.
- Redaction in the `toString()` methods holds.
- Encoders are deterministic pure functions of the request.

No security issue or secret leak was found in the reviewed code, and there are no BLOCKER findings. There are four
behavioral defects worth fixing before the v1.0 tag freezes the API. Two of them are about what the public KDoc
promises versus what the code does.

## Warnings

### WR-01: `forceTool = false` still fails when the snapshot has no `singleShotTool`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:68`
**Issue:** `withTooling` returns `Failed(Other("single_shot_tool_missing"))` whenever `snapshot.singleShotTool` is
null. The name is only used to build `ToolChoice.Required(tool)` (line 86) and is ignored when `forceTool` is false.
`Builder.forceTool` is documented as "False lets the model choose among the offered tools". `ToolingSnapshot` says
`singleShotTool` is "or null". So an app that sets `forceTool = false` and builds a snapshot with a null
`singleShotTool` (the natural shape for "let the model choose") gets a loud failure with no provider call.
`SingleShotRequestTest.aSnapshotWithoutAForcedToolFailsWithNoProviderCall` only covers the `forceTool = true` default,
so nothing pins the `false` case.
**Fix:** Require the name only when forcing.
```kotlin
private suspend fun withTooling(input: CommandInput, session: CommandSession): StrategyOutcome {
    val snapshot = tooling.tooling(input)
    val forced = snapshot.singleShotTool
    if (forceTool && forced == null) return StrategyOutcome.Failed(FailureReason.Other(TOOL_MISSING_CODE))
    return withModel(Attempt(input, session, snapshot), forced)
}
// request(): if (forceTool) ToolChoice.Required(requireNotNull(tool)) else ToolChoice.Auto()
```
Add a test for `forceTool = false` with a null `singleShotTool`.

### WR-02: Default clock freezes the device time zone at builder time

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:144`
**Issue:** `Clock.systemDefaultZone()` captures `ZoneId.systemDefault()` once, when the `Builder` is constructed. The
strategy is built once, typically at app start, and lives for the process lifetime. If the user travels or the device
zone changes, `ZonedDateTime.now(clock)` keeps reporting the old zone and offset. The default renderer sends exactly
that to the model as "Current local date-time ... (zone)". Relative phrases such as "tomorrow at 9" or "tonight" then
resolve against the wrong local time, and they do so silently. The `Builder.clock` KDoc says "Defaults to the system
clock and zone", which a reader will take to mean the current zone.
**Fix:** Make the default re-read the zone on every call, for example:
```kotlin
private object CurrentZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
// Builder: public var clock: Clock = CurrentZoneClock
```
Add a test that changes `TimeZone.setDefault` between two commands.

### WR-03: The `DispatchResult` of every submit is discarded, so the reply is shown whether or not anything was applied

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:120-125`
**Issue:** `submitAll` ignores the `DispatchResult` from both `session.submit(...)` calls and always returns
`StrategyOutcome.Completed(steps.reply)`. The resolver prepares `reply` before the gate runs, so it cannot know the
outcome. When the gate holds the mutation, or an apply throws (`IS_ERROR`), the user still sees the resolver's
"Saved ..." text. The final `CommandOutcome.Completed` carries the effects, but nothing connects the reply to them.
The tier-level `Completed` is also reported to the trace as a success. This is the one place the engine could
surface "never a silent or opaque" failure, and it does not.
**Fix:** Decide the contract before the tag, because it is hard to change afterwards. Options:
- Let the resolver supply the reply as a function of the dispatch results, `Steps(steps, reply: ((List<DispatchResult>) -> String?)?)`.
- Or at least document on `Resolution.Steps.reply` that it is shown regardless of the gate's decision and of apply errors.
- Or return `Completed(null)` when any `DispatchResult.held` or `isError` is true.

### WR-04: `Resolution.Steps` is documented as "in order", but the tier reorders and merges steps

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:119-124`
(contract text at `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt:49`)
**Issue:** The public KDoc says "Steps the strategy submits to the engine, in order". `submitAll` actually submits all
`Finished` steps first, then folds every `Mutation` step into one combined `ToolStep.Mutation`. For
`[Mutation A, Finished(ERROR for B), Mutation C]`, the recorded order is `B, A, C`. The gate sees one proposal
instead of two, and the action positions in the commit sink differ from the order the app returned. The reordering is
explained only in a private code comment (line 119). An app that relies on the documented ordering, or on one gate
call per mutation step, gets different behavior.
**Fix:** Document the real semantics on `Resolution.Steps` and `SingleShotStrategy`. Say that finished steps go first
in list order, that all mutations are gated once as a single proposal, and that this is intentional. Or preserve the
list order and group only adjacent mutations.

## Info

### IN-01: Unreachable `MalformedResponse` branch in `route`

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:100`
**Issue:** `answer` calls `route` only after `decideResult` returned null. `stopOutcome` returns null only when
`toolCalls` is non-empty, so `calls.firstOrNull() ?: Failed(MalformedResponse())` can never run. The tool calls are
also derived twice, once in `answer` and once inside `stopOutcome`.
**Fix:** Pass the first call out of `decideResult`, or replace the branch with `calls.first()` plus a comment
stating the invariant.

### IN-02: `Resolution.Failed` and `Resolution.Escalate` lack the convenience constructors their `StrategyOutcome` twins have

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt:80,95`
**Issue:** `StrategyOutcome.Failed(reason)` and `StrategyOutcome.Escalate(reason)` have one-argument forms.
`Resolution.Failed(reason, details)` and `Resolution.Escalate(reason, carry)` force the caller to write an explicit
`null`, as the tests do (`Resolution.Failed(FailureReason.Refusal(), null)`). It works, but it is inconsistent. It is
also cheapest to fix before the tag, because Kotlin default arguments cannot be added later without breaking binary
compatibility.
**Fix:** Add `public constructor(reason: FailureReason) : this(reason, null)` and the same for `Escalate`.

### IN-03: `SingleShotStrategy` cannot declare its providers

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:48-51`
**Issue:** `capabilities` is never overridden and the builder has no setting for it, so every single-shot tier
declares `ANY_PROVIDER`. Consequences:
- The static `allowedProviders` pre-check is vacuous for this tier. Only the router's later bind-time check enforces it.
- An app cannot declare "cloud only" or "on-device only".
- `offlineOnly` always skips the tier, because it is neither empty nor on-device-only (`PolicyPreCheck.permits`).
**Fix:** Add `public var providers: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER` to the builder and
override `capabilities`. Doing it now is additive.

### IN-04: `ToolSpecProvider` KDoc contradicts its signature

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpecProvider.kt:8-9,14`
**Issue:** The doc says "The system text and the tool list must not depend on the command", yet `tooling(input)`
receives the command. An implementer reading the signature will reasonably vary the tools by language, which breaks
the prompt-cache prefix the doc is trying to protect. The cache-miss diagnostic will then fire with no obvious cause.
**Fix:** Reword the doc to say "should not depend on the command's content, only on stable facts such as the
language, and each variation costs a cache write", or drop the parameter.

### IN-05: OpenRouter strict-mode requests omit `parallel_tool_calls: false`

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt:68-71`
**Issue:** For `openai/...` models through OpenRouter, strict mode is turned on. `parallelToolCallsFalseOnForced` is
false for that vendor, so the switch is never sent. OpenAI documents that strict function calling is not guaranteed
to match the schema when parallel calls are generated. The strategy acts on the first call only, so the first call
could in rare cases violate its strict schema. `SingleShotWireTest.openRouterBodyOmitsParallelToolCallsAndUsesTheFirstCall`
pins this as intended, presumably because OpenRouter rejects the key for some backends. The resolver is required to
validate arguments anyway.
**Fix:** No change is required if the omission is deliberate. Note in the `ChatEncoder` KDoc that strict plus a
dropped parallel switch relies on the resolver's own validation.

### IN-06: Router model ids are matched case-insensitively for the vendor prefix but case-sensitively for the rules

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt:16-25`
(caller `ChatModels.key`)
**Issue:** `ChatModels.key` lowercases `vendorPrefix` and lowercases the id for the `gpt-oss` check. The id handed to
`OpenAiModelRules` is not lowercased, and every regex is lowercase-only. `openai/GPT-5.4-mini` is therefore treated as
OpenAI, but it falls to the `else -> completion` row. It gets no `reasoning_effort: none` and so would probably hit
the tools-with-effort 400 that the 5.4+ row exists to avoid. OpenRouter ids are normally lowercase, so this is an edge
case.
**Fix:** Lowercase the id once in `ChatModels.key` before it reaches `OpenAiModelRules`, or make the regexes
`IGNORE_CASE`.

### IN-07: The automatic-caching floor is applied to every `gpt-` id

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt:67-68`
**Issue:** `minCacheablePrefixTokens` returns 1,024 for any id matching `^gpt-.*`. That includes the `gpt-3.5` and
base `gpt-4` families, which I believe OpenAI does not auto-cache. I could not confirm this from the repo. If it is
right, the cache-miss diagnostic could raise false `CacheNotEngaged` events for those models. Treat this as a lead to
verify against OpenAI's prompt-caching list, not a confirmed defect.
**Fix:** Return the floor only for the families that cache (gpt-4o and newer, o-series). Otherwise return null so the
diagnostic stays silent, and keep the existing note that apps may override per model id.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
