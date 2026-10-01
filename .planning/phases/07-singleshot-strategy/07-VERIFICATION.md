---
phase: 07-singleshot-strategy
verified: 2026-10-01T09:00:00Z
status: passed
score: 3/3 roadmap success criteria verified (plus all PLAN must-have truths spot-checked against code)
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/07-singleshot-strategy/07-01-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-01-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-02-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-02-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-03-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-03-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-04-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-04-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-05-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-05-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-06-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-06-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-07-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-07-SUMMARY.md"
  - ".planning/phases/07-singleshot-strategy/07-08-PLAN.md"
  - ".planning/phases/07-singleshot-strategy/07-08-SUMMARY.md"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpecProvider.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/UserTurn.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotAcceptanceTest.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt"
covered_digest: "v1:sha256:2226f90c14320508fc78eb37bb3b256505d714ad24b807409f2da94d7555c2b9"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 7: SingleShot Strategy Verification Report

**Phase Goal:** A consumer can handle a command with one forced-tool extraction call that is resolved locally into a (possibly batch) proposal and committed through the gate. This covers CT's real confirm flows.
**Verified:** 2026-10-01
**Status:** passed
**Re-verification:** No, initial verification

I read the code and tests directly. I did not rely on the SUMMARY.md files. I ran `./gradlew check --offline` once at HEAD (06f839c, which includes the WR-01..04 and IN-01/03/04/06 review fixes). It exited 0. Results from the JUnit XML it produced:

| Suite | Tests | Failures / errors / skipped |
|---|---|---|
| core | 532 | 0 / 0 / 0 |
| providers (4.12.0) | 430 | 0 / 0 / 0 |
| providers (5.2.1) | 430 | 0 / 0 / 0 |
| providers (5.5.0) | 430 | 0 / 0 / 0 |

`SingleShotAcceptanceTest` ran 11 tests, `SingleShotLimitsTest` 10, `SingleShotWireTest` 6, all green. `evidence/phase-gate.txt` shows 526 core tests. The 6 extra are the tests the review fixes added.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A `SingleShotStrategy` tier makes one forced-tool call using the app's `ToolSpecProvider`. The app's `OutcomeResolver` turns the result into a (possibly batch) proposal, which commits only through the gate then `CommitSink` path. | VERIFIED | `SingleShotStrategy.kt:84-153`. `tooling.tooling(input)` supplies the snapshot. The choice is `ToolChoice.Required(singleShotTool)`, and the request carries `singleToolCall=true`, `CacheDirective(true)` and `policy.maxTokensPerTurn`. There is one `model.complete`, and `resolver.resolve(Extraction, input)` follows. `submitAll` sends only `session.submit(...)`. Finished steps go first. Every `ToolStep.Mutation` is folded into one step, so the gate decides once per proposal. `CommandSession.submit` is `scope.coordinator.submit`, the gate and sink path (`RunSession.kt:59`). The strategy has no direct write path and no `apply` call. `SingleShotAcceptanceTest` s1, s3 and s8 exercise it through the real pipeline, gate and `RecordingCommitSink`. |
| 2 | By default a reply with no tool call or prose escalates with `NoToolCall`, and a refusal fails with `REFUSAL`. Both mappings are overridable per tier, and Chat Completions requests carry `parallel_tool_calls: false`. | VERIFIED | `SingleShotOutcomes.kt`: `StopReason.REFUSAL` goes to `hooks.onRefusal(response)` before any tool call is read. END_TURN with no calls goes to `hooks.onNoToolCall`. The defaults are `Escalate(NoToolCall)` and `Failed(Refusal)` (`SingleShotStrategy.kt:200-208`). A `Failure(NoToolCall)` or `Failure(Refusal)` result goes through the same hooks with a null argument. Hooks are per builder, and `oneTiersOverrideNeverChangesASecondSingleShotTier` plus the hook tests in `SingleShotOutcomeMappingTest` (26 tests) cover this. Wire: `ChatEncoder.kt:68-70` emits `parallel_tool_calls:false` for OpenAI when forced, strict or `singleToolCall`, unless the model is in the o-series (`acceptsParallelToolCalls`). Anthropic emits `disable_parallel_tool_use:true` inside `tool_choice` (`AnthropicEncoder.kt:103`). `SingleShotWireTest` shows the real providers over MockWebServer sending the flag and committing (OpenAI, Anthropic forced, Anthropic reshaped). It also shows prose escalating and a refusal failing, all green on the 4.12.0, 5.2.1 and 5.5.0 legs. See the OpenRouter note under Warnings. |
| 3 | CT's confirm scenarios pass as acceptance tests: weak match held for confirmation, batch proposal, amended confirm, and deferred `commitHeld`. | VERIFIED | `SingleShotAcceptanceTest.kt` runs the full pipeline with `FakeAiProvider` and the fixture resolver. The gate is a real defer-mode `ScriptedGate` that reads only the opaque `PendingMutation.context`. s1: a strong single commits in the original run with one `COMMITTED` and one `Done` close. s2: a weak single is `HELD` with `applyCount` 0, and `commitHeld` then runs as a linked child run (`parentRunId == original.runId`, new runId, `applyCount` 1). A second `commitHeld` returns the same object (`assertSame`) with no new sink action. s3: an all-strong batch of 3 is still held, with `gate.calls == 1` and 3 mutations. s4: an amended `commitHeld` applies only the replacement list (edited quantity, swapped match, changed date, row dropped), and the held mutations stay at `applyCount` 0. Also s5 (failing row does not poison siblings), s6 (unmatched row recovered at amend), s7 to s10 (refusal, prose, gate-amended admit, clarification, first-call-only), and a held proposal never reaching the next tier. |

**Score:** 3/3 roadmap truths verified. 0 present-but-behavior-unverified.

### PLAN must-haves (spot-checked against code, not SUMMARY)

| Must-have | Status | Evidence |
|---|---|---|
| 07-01: `ModelRequest.singleToolCall` end to end, `TraceCode.EXTRA_TOOL_CALLS_DROPPED = "extra_tool_calls_dropped"`, internal `CommandSession.recordCode`, no new public `CommandSession` member | VERIFIED | `ModelRequest.kt:30,70`. `TraceCode.kt:102`. `CommandSession.kt` declares `internal abstract suspend fun recordCode`, and `RunSession.kt:65` implements it. `SingleShotAcceptanceTest.s10` asserts exactly one dropped-call code and that the turn lists both tool names. |
| 07-02: Anthropic `disable_parallel_tool_use` (forced and reshape paths), OpenAI `parallel_tool_calls:false`, OpenRouter never, o-series gate | VERIFIED | `AnthropicEncoder.kt:94-105`. `ChatEncoder.kt:68-70` (the vendor flag `parallelToolCallsFalseOnForced` is unchanged: OpenAI true, OpenRouter false). `OpenAiModelRules.acceptsParallelToolCalls` is present. |
| 07-03: seam types `ToolSpecProvider`/`ToolingSnapshot`, `OutcomeResolver`/`Extraction`/`Resolution`, `UserTurnRenderer`/`UserTurnContext`, with redaction-safe `toString` and a blocking sign-off | VERIFIED | All present with the planned shapes. Every `toString` prints names, counts and lengths only. `evidence/seam-signoff.txt` ends `SIGNOFF: APPROVE`, relayed from the orchestrator, with a Phase 9 carry. No post-signoff change to a signed-off seam shape: the review fixes added `Builder.capabilities` (additive) and KDoc, and left signatures alone. |
| 07-04: `SingleShotStrategy` shape and builder, first-call-only, terminal calls (A19), tool missing from snapshot gives `Escalate(MalformedExtraction)`, no retry or second call | VERIFIED | `SingleShotStrategy.kt:130-137` (terminal returns `Completed(null, TerminalCall)` without the resolver). `route` records the code once for N>=2 calls. The class has no catch, no retry and no logging. |
| 07-05: limits 6 / 60000 / 4096 read only from `session.policy` and `tokensUsed` and tested; no literal 60000 or 4096 in core main outside `TierPolicy.kt` | VERIFIED | `ceilingReached` before the call (`>=` ceiling) and `ceilingCrossed` after the call, before the resolver, gate or write (`>` ceiling). `maxTokens = policy.maxTokensPerTurn`. `grep` for `60000/60_000/4096/4_096` in `core/src/main` outside `TierPolicy.kt` returns nothing. `SingleShotLimitsTest` has 10 tests, all green. |
| 07-06: neutral CT-shaped fixtures, D-10, no `src/main` change | VERIFIED | `SingleShotFixtures.kt` uses neutral names (`record_entries`, `entries`, `label`, `catalog`). Thresholds live only in the test resolver and gate. |
| 07-07: wire acceptance over real providers (Anthropic forced and reshaped, OpenAI, OpenRouter, prose, refusal) on 3 OkHttp legs | VERIFIED | `SingleShotWireTest` has 6 tests, green on all three legs in my run. |
| 07-08: phase gate, API-surface review, hygiene, no `api.txt`, no tag | VERIFIED | `./gradlew check --offline` exit 0 (my run). `git ls-files '*api.txt'` is empty and `git tag` is empty. `evidence/phase-gate.txt` records the other three scripts as exit 0. I did not re-run those scripts. |

### Required Artifacts

| Artifact | Status | Details |
|---|---|---|
| `core/.../strategy/singleshot/SingleShotStrategy.kt` (221 lines) | VERIFIED | Substantive. Wired through the companion `invoke`, `CommandStrategy`, and the pipeline in 11+ test classes. |
| `core/.../strategy/singleshot/SingleShotOutcomes.kt` (78 lines) | VERIFIED | Internal mapping used by the strategy. |
| `core/.../strategy/OutcomeResolver.kt`, `ToolSpecProvider.kt`, `UserTurn.kt` | VERIFIED | Public seams, each imported by the strategy. |
| `ModelRequest.kt` (`singleToolCall`) | VERIFIED | Read by both encoders. |
| `AnthropicEncoder.kt`, `ChatEncoder.kt`, `OpenAiModelRules.kt` | VERIFIED | Flag encoded as specified. |
| 11 `SingleShot*Test` classes in core, `SingleShotWireTest` in providers | VERIFIED | Real assertions. I read the acceptance test in full. |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| `SingleShotStrategy` | gate then `CommitSink` | `session.submit` to `RunSession.submit` to `scope.coordinator.submit` | WIRED |
| `SingleShotStrategy` | provider wire | `ModelRequest(..., singleToolCall=true)` to `AnthropicEncoder` and `ChatEncoder` | WIRED (MockWebServer bodies asserted) |
| `SingleShotStrategy` | trace | `session.recordCode(EXTRA_TOOL_CALLS_DROPPED)` to `recorder.recordCode` | WIRED |
| `commitHeld` | linked child run, amend list, idempotent | Existing `CommandPipeline.commitHeld`, unchanged and reused (D-06, D-08, D-11) | WIRED (s2, s4, s6) |

### Data-Flow Trace (Level 4)

Not a rendering phase. The data that matters flows from provider response, to first tool call, to `Extraction`, to the resolver's `PendingMutation`s, to the gate, to apply and the sink. The acceptance and wire tests assert the applied values end to end (e.g. `applied:alpha:3.0:item-a:2026-10-02`). FLOWING.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Whole offline gate | `./gradlew check --offline` | exit 0 | PASS |
| Counts | JUnit XML, 4 test tasks | 532 / 430 / 430 / 430, 0 failures | PASS |
| No `api.txt`, no tag | `git ls-files '*api.txt'`, `git tag` | empty | PASS |
| No stray limit literals | grep `60000/4096` in `core/src/main` outside `TierPolicy.kt` | none | PASS |
| No debt markers or banned constructs in the touched strategy files and `ChatEncoder.kt` | grep `TBD/FIXME/XXX/runCatching/println/printStackTrace/@Suppress` | none | PASS |

Probe execution: no phase-declared probes. SKIPPED.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| SHOT-01 | 07-03, 07-04, 07-05 and others | Forced-tool extraction via `ToolSpecProvider`, local resolve via `OutcomeResolver`, routed through the commit path | SATISFIED | Truth 1 |
| SHOT-02 | 07-01, 07-02, 07-04, 07-07 | NoToolCall escalates, refusal fails, both overridable, `parallel_tool_calls:false` on Chat Completions | SATISFIED | Truth 2 (see the OpenRouter note) |
| SHOT-03 | 07-06 | CT confirm scenarios as acceptance tests | SATISFIED | Truth 3 |

All three IDs from the orchestrator are claimed by plan frontmatter and are in `REQUIREMENTS.md:79-81`. No orphaned Phase 7 requirements. Bookkeeping: `REQUIREMENTS.md` still shows SHOT-01..03 as `[ ]` and `Pending` (lines 79-81 and 201-203), so the orchestrator should flip them when it records phase completion.

### Anti-Patterns Found

None blocking. No TBD, FIXME or XXX markers, no stubs or empty handlers, and no swallowed exceptions in the new strategy files. A throwing resolver propagates to TierWalk's collapse by design, covered by `aThrowingResolverCollapsesToUnexpectedWithAStrategyErrorCode`.

### Warnings and Notes (not gaps, nothing blocking)

1. **OpenRouter does not send `parallel_tool_calls: false`.** The literal wording of SHOT-02 and ROADMAP SC2 is "Chat Completions requests carry `parallel_tool_calls: false`". The implementation does this for OpenAI, but deliberately not for OpenRouter. The Phase 5 live capture R5 returned 404 "No endpoints found" when OpenRouter was sent that flag with `require_parameters` (`05-12-SUMMARY.md:60,70`). Phase 7 compensates by acting on the first tool call only and recording `extra_tool_calls_dropped`. `SingleShotWireTest.openRouterBodyOmitsParallelToolCallsAndUsesTheFirstCall` pins this. I count this as satisfied because the omission is evidence-driven, recorded in the CONTEXT Runtime Decisions, and covered by an engine-side fallback. If you want strict literalism, record an override on SC2 for the OpenRouter leg. The orchestrator should confirm it accepts that reading.
2. **WR-03 reply policy needs orchestrator sign-off before the tag.** The fixer chose to withhold the resolver's `reply` when the apply reported `isError`, and to keep it when the change is held. This is a behavior and contract choice, flagged in `07-REVIEW-FIX.md`, and tests cover it. It is not part of SHOT-01..03. It belongs on the pre-tag checklist, alongside WR-01 (`forceTool=false` without a single-shot tool), WR-02 (default clock reads the zone per command) and IN-06 (case-insensitive router model ids).
3. **Skipped review items:** IN-02 (convenience constructors on `Resolution.Failed` and `Resolution.Escalate`, additive, can follow the tag), IN-05 (OpenRouter strict-mode omission, same reasoning as note 1), IN-07 (the caching floor applied to every `gpt-` id, unverified). None blocks the goal.
4. **Carry-forwards (not Phase 7 gaps):**
   - Phase 9 must accept the same `userTurn: UserTurnRenderer`, per the sign-off.
   - Phase 10 VER-03 must assert that the live Anthropic smoke body contains `disable_parallel_tool_use` and returns 200. It may also add two cheap calls showing `cache_read_input_tokens > 0` to retire RESEARCH A2.
   - Live provider legs (Anthropic forced with the flag, OpenAI) are exercised only against MockWebServer in this phase. The phase's own scope excludes live legs, and Gate-1 is N/A for a JVM-only phase.

### Human Verification Required

None for Phase 7. The Gate-1 device leg is N/A. The live-provider legs are Phase 10 scope (VER-03).

### Gaps Summary

No gaps. All three roadmap success criteria and the PLAN must-haves are backed by code I read and by a fresh, fully green `./gradlew check --offline` at HEAD. Phase 7 achieves its goal. The open items above are orchestrator decisions and carry-forwards, not defects.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
