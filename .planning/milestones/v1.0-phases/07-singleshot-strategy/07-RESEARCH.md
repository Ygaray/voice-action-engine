# Phase 7: SingleShot Strategy - Research

**Researched:** 2026-10-01
**Domain:** Kotlin library strategy tier (`:core`) over the shipped provider/commit seams, plus a neutral "single tool call" request flag encoded in the `:providers` wire encoders.
**Confidence:** HIGH on integration points (every seam read in this session, file:line cited) and on the Anthropic/OpenAI wire facts (official docs fetched, plus Phase 5 live evidence). MEDIUM on one OpenAI model-family edge (o-series rejects `parallel_tool_calls`, community-sourced) and on cache behaviour of the new Anthropic field (docs silent).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [resolver-seam]:** Extraction wrapper (additively extensible for grammar intent+slots) → prepared ToolSteps or a verdict; the resolver never writes _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-02 [multi-item]:** One tool call with an items[] array expanded to N mutations (cap/drop rules app-side); first call only; neutral single-call flag encoded as disable_parallel_tool_use:true (Anthropic) + parallel_tool_calls:false (Chat). Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 4)_
- **D-03 [user-turn]:** App-controlled user-turn renderer hook with an engine default (neutral date-time + zone + transcript from an injected clock). SB requires byte-for-byte reproduction of its framing; CT needs only semantics: its dynamic "Today's date is <X>" goes through the hook, its static relative-date rule may move into the tool description (Phase 67 target_date behavior preserved). The cached prefix is never touched. Confirmed by secondbrain-2c + caltracker-android-9a. _(source: human)_
- **D-04 [signals]:** Opaque app context only (GATE-01); CT's per-item confidence / why-flagged ride in it; thresholds stay in CT's resolver+gate. Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-05 [batch-gate]:** Gate decides once per proposal: Hold only for a weak single or any 2+ batch per the app's policy; a high-confidence single is Admitted and commits in the ORIGINAL run (CT auto-log). Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-06 [amend]:** Replacement mutation list (edited qty, swapped match, changed shared date, dropped rows); no re-gate; per-item isolation + per-item results; idempotent handle. Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-07 [unmatched]:** Propose with an unresolved target (CT's ItemCorrectionDropdown recovery); still-unresolved rows skipped in the app's apply. Resolver returns NoMatch only when nothing is proposable (CT maps that onto its Unavailable); a model refusal stays a separate Failed(REFUSAL). Confirmed by caltracker-android-9a. _(source: human)_
- **D-08 [post-commit-edit]:** Stays app-side in v1.0 (CT's updateWithItemSwap path), not through the engine. Confirmed by caltracker-android-9a. _(source: human)_
- **D-09 [schema-validate]:** Pass through (keeps CT's "one bad item doesn't poison the batch"); engine-side validation only where PROV-12 requires it for non-strict calls, reported not fatal _(source: ai-auto)_
- **D-10 [fixtures]:** Neutral-named CT-shaped fixtures (items array + shared date, per-item confidence, catalog with scores and unmatched rows, a failing row, all-strong batch still held, defer-mode threshold gate, recording sink) run through the full pipeline _(source: ai-auto)_
- **D-11 [deferred-runid]:** Follow Phase 2's [held-runid] resolution (recommended: new linked run with parentRunId and its own close) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-12 [ext-single-call]:** Needs external research: disable_parallel_tool_use with forced tool_choice and with auto+strict on Anthropic (can auto still return >1 tool_use?); parallel_tool_calls:false with strict + forced + reasoning_effort none on OpenAI; whether require_parameters makes OpenRouter routing fail on parallel_tool_calls _(source: ai-auto)_ → ANSWERED in "Single-call flag wire facts (D-12)" below.
- **D-13 [a19-clarification]:** A19: in SingleShot, a (forced or auto) call to a terminal tool skips the OutcomeResolver and ends the run as `Completed(terminalCall = …)`. _(source: human — orchestrator, contract A19)_

Runtime Decisions (bottom of CONTEXT.md, refreshed in commit 3cb829f; these override the provisional wording above):

- **resolver-seam:** Map the extraction wrapper onto the existing `ToolStep` (core/commit/ToolStep.kt, sealed) and the `PendingMutation` lists the CommitCoordinator already applies. The resolver returns prepared ToolSteps or a verdict and NEVER writes, because writes go only through CommitCoordinator → PreApplyGate → apply. Keep the wrapper additively extensible (grammar intent+slots in v1.1).
- **multi-item:** No neutral single-call flag exists yet. Today ChatEncoder sends `parallel_tool_calls:false` only on forced/strict (ChatVendor.parallelToolCallsFalseOnForced: true for OpenAI, false for OpenRouter), and the Anthropic encoder sends no `disable_parallel_tool_use`. Phase 7 adds ONE neutral request flag in the provider request model. Encode it as Anthropic `tool_choice.disable_parallel_tool_use:true` and OpenAI Chat `parallel_tool_calls:false`. For OpenRouter, do NOT send `parallel_tool_calls` (the Phase 5 live probe got a 404, so it stays off). Enforce engine-side instead: take the first tool call only, and trace any extra calls dropped. The items[] array expands to N mutations, with cap/drop rules app-side.
- **signals:** Opaque app context only (GATE-01): `CommitProposal`/`ToolStep.Finished.context: Any?` already carry it. CT per-item confidence and why-flagged ride in it, and thresholds stay in CT.
- **batch-gate:** The existing `PreApplyGate` returns `GateDecision.Admit(amended?)` / `Hold(reason, appOutcomeToken?)` once per proposal (CommitCoordinator.kt:111). Hold covers a weak single or any 2+ batch per app policy. A high-confidence single is Admitted and commits in the ORIGINAL run (CT auto-log). The engine adds no batch logic.
- **amend:** Use the existing `CommandPipeline.commitHeld(held, amended: List<PendingMutation>)` (CommandPipeline.kt:138): a replacement list, no re-gate, idempotent (a second call returns the first outcome). Per-item isolation plus per-item results come from the existing applyAll/ActionEvent path. Add no new amend API.
- **deferred-runid:** `commitHeld` runs as a new linked run carrying the held run as parentRunId (HeldProposal.parentRunId, RunTermination.parentRunId, PipelineEvent.CommandStarted.parentRunId), with its own close; a cancel yields Failed(Other("commit_held_cancelled")). Phase 7 reuses this unchanged.
- **Limits precondition (orchestrator, carried from Phase 11):** wherever SingleShot loops or retries, it must enforce and TEST the 6 / 60000 / 4096 limits from `session.policy` / `session.tokensUsed`, because the v1.0.0 cut is blocked without these tests.

### Claude's Discretion

Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

### Deferred Ideas (OUT OF SCOPE)

See REQUIREMENTS.md v2 / LATER items. (Post-commit edit, LocalGrammar tier, on-device-backed SingleShot, `:undo` are not Phase 7.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SHOT-01 | `SingleShotStrategy` makes one forced-tool extraction call using the app's `ToolSpecProvider`, then resolves locally through the app's `OutcomeResolver` into a (possibly batch) commit proposal routed through the commit path (GATE-01) | Neither seam exists yet (verified: repo-wide grep finds `ToolSpecProvider`/`OutcomeResolver` only in docs). "Integration points" gives the exact `CommandStrategy`/`CommandSession`/`ToolStep` contracts; "Recommended seam shapes" gives the prescriptive types; "Pattern 1" gives the `execute` skeleton |
| SHOT-02 | No tool call / prose → `Escalate(NoToolCall)`; refusal → `Failed(REFUSAL)`; both overridable per tier; `parallel_tool_calls: false` on Chat Completions | "Outcome mapping table" (every `ModelResult`/`StopReason` row), "Single-call flag wire facts (D-12)", "Pattern 2/3" (encoder changes), the o-series pitfall |
| SHOT-03 | CT confirm scenarios as acceptance tests: weak match held, batch proposal, amended confirm, deferred `commitHeld` | "CT-shaped acceptance scenarios" (S1-S10), reuse list for the fixture harness (`ScriptedGate`, `FakeMutation`, `RecordingCommitSink`, `FakeAiProvider`, `NoNetworkGuard`), `DeferModeTest`/`BatchIsolationTest` as templates |
</phase_requirements>

## Summary

Phase 7 is almost entirely new `:core` code on top of seams that already shipped: `CommandStrategy`/`CommandSession` (strategy contract), `BoundModel.complete` (one routed provider call, turn auto-recorded), `ToolStep`/`PendingMutation`/`CommitCoordinator`/`PreApplyGate` (the only write path), `CommandPipeline.commitHeld` (deferred and amended commit as a linked child run), and `TierPolicy`/`session.tokensUsed` (advisory limits the strategy must enforce itself). What does NOT exist, and must be created and then frozen by Metalava at v1.0.0: `ToolSpecProvider`, `OutcomeResolver` (plus the `Extraction`/`Resolution` wrapper types), the user-turn renderer hook, `SingleShotStrategy` itself, and the neutral single-call flag on `ModelRequest`. Because these become immutable public API, the public shape rules matter as much as behaviour: no default-argument constructors (enforced by `ApiShapeTest`), no new `sealed` types (the surface script allow-lists exactly seven), no enums, no data classes, redacted `toString()` everywhere.

The single-call wire work is small and well-bounded. Anthropic documents `tool_choice.disable_parallel_tool_use` as a field inside the `tool_choice` object: with `auto` it means "at most one tool per response", with `any`/`tool` "exactly one tool". It does not touch the tools/system prefix the engine's one cache breakpoint covers (Anthropic's invalidation table lists `tool_choice` as affecting message blocks only). OpenAI documents `parallel_tool_calls:false` as "exactly zero or one tool"; the Phase 5 live capture (C1) already proved the full OpenAI combination strict + forced + `reasoning_effort:"none"` + `parallel_tool_calls:false` returns 200 `tool_calls` on `gpt-5.4-mini`. OpenRouter must keep it off: the Phase 5 live probe (R5) answered 404 "No endpoints found that can handle the requested parameters" when `parallel_tool_calls` was combined with `provider.require_parameters`. The engine therefore enforces "first call only" itself and traces the dropped extras. One latent defect surfaced: the existing Chat encoder sends `parallel_tool_calls:false` for every OpenAI model including o-series ids, and OpenAI rejects that parameter on o-series reasoning models (400); the fix is a one-line wire-rule gate in `OpenAiModelRules`.

SingleShot does not loop and does not retry (the one transient retry and the forced-tool reshape live below the `AiProvider` seam in `:providers`, ≤3 HTTP requests, invisible to the strategy). The v1.0.0-blocking limits obligation is therefore met by enforcing and testing three concrete things: `policy.maxTokensPerTurn` is passed as `ModelRequest.maxTokens`; `policy.tokenCeiling` is checked against `session.tokensUsed` before the call (a later tier after an expensive earlier tier) and again before dispatch (SB's order); and exactly one provider call is made under every policy including `maxIterations = 2`, never a hidden re-ask. `maxIterations` is satisfied by construction and must be tested as "never exceeds 1 turn".

**Primary recommendation:** Build `SingleShotStrategy` in `:core` (package `core.strategy.singleshot`) behind an internal-constructor builder DSL, add `ModelRequest.singleToolCall` as a 7-argument constructor next to the unchanged 6-argument one, encode it in both wire encoders (OpenRouter excluded), and prove behaviour with `FakeAiProvider`-driven pipeline tests (SHOT-03 scenarios S1-S10) plus MockWebServer byte tests in `:providers`. No live leg is warranted in Phase 7; carry one assertion to the Phase 10 smoke (VER-03).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Forced-tool request construction, outcome mapping, first-call-only, terminal-call routing | `:core` strategy (`SingleShotStrategy`) | — | Strategies talk only to `BoundModel`; `:core` has no HTTP dependency (L7/A7). Source: `CommandSession.model()` CommandSession.kt:61 |
| Single-call flag *meaning* | `:core` request model (`ModelRequest.singleToolCall`) | — | Neutral request vocabulary lives in `core.transcript` (ModelRequest.kt:20-27) |
| Single-call flag *encoding* (`disable_parallel_tool_use`, `parallel_tool_calls`) | `:providers` encoders | — | Wire dialects are `:providers` only (AnthropicEncoder.kt:90-100, ChatEncoder.kt:65-67) |
| Local resolution (catalog match, confidence, caps, drop rules) | App (`OutcomeResolver` impl) | — | Domain-free library: thresholds stay in CT's resolver+gate (D-04) |
| Confirm / hold / amend policy | App (`PreApplyGate` impl, `commitHeld`) | `:core` coordinator | Engine adds no batch logic (batch-gate runtime decision) |
| Writes, per-item isolation, ledger, sink delivery | `:core` `CommitCoordinator` (only write path) | App `PendingMutation.apply` | CommitCoordinator.kt:49-55, 137-145 |
| Limits (maxTokensPerTurn, tokenCeiling, maxIterations) | `:core` strategy (advisory in `TierPolicy`) | — | TierPolicy.kt:15-18: "advisory ... a strategy that ignores `session.policy` is unbounded" |
| Retry / forced-tool reshape | `:providers` transports | — | Below the `AiProvider` seam (AnthropicTransport.kt:36, 64-66) |
| Dynamic user turn (date, zone, transcript) | `:core` renderer hook (app-overridable) | App | Per-command text goes to messages only, never the cached prefix (PROV-06) |

## Standard Stack

No new external packages. Everything reuses the repo's pinned stack. `[VERIFIED: core/build.gradle.kts:29-30]`

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-coroutines-core | 1.11.0 | `suspend` seams, `runTest` | already `api` in `:core` (core/build.gradle.kts:29-30) |
| kotlinx-serialization-json | 1.11.0 | `JsonObject` extraction arguments | already `api` in `:core` |
| `java.time` (JDK 11 API surface) | JDK | default user-turn renderer date/zone | `-Xjdk-release=11` is set (core/build.gradle.kts:24) so `java.time.Clock`/`ZonedDateTime` are legal |

### Supporting (test only)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| junit:junit | 4.13.2 | all tests | ecosystem standard (CLAUDE.md STACK) |
| kotlinx-coroutines-test | 1.11.0 | `runTest` | every strategy test |
| okhttp mockwebserver (legacy `okhttp3.mockwebserver`) | 4.12.0 / 5.2.1 / 5.5.0 legs | `:providers` byte tests | only for the wire tests (07-06) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Builder DSL for `SingleShotStrategy` | public constructor with default args | REJECTED: `ApiShapeTest` fails any non-exempt class with a default-argument stub (ApiShapeTest.kt:117-141, test at :132); STUB_EXCEPTIONS is a closed list |
| `abstract class Resolution` (open) | `sealed class Resolution` | REJECTED: `scripts/review-api-surface.sh` allow-lists exactly seven sealed types (ALLOWED_SEALED line); an eighth fails the gate |
| Engine-side JSON-schema validator | none (resolver validates) | A validator is a hand-roll with no permitted dependency in `:core`; D-09 says pass through |

**Installation:** none.

**Version verification:** n/a (no external packages added). Gradle 9.4.1 and JDK 17.0.19 confirmed locally in this session.

## Package Legitimacy Audit

No external packages are installed or recommended in this phase. Nothing to audit.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Integration Points (grounded in shipped code)

All quotes below were read this session.

### The tier seam
`CommandStrategy` (core/strategy/CommandStrategy.kt:12-26):
```kotlin
public interface CommandStrategy {
    public val id: StrategyId
    public val capabilities: StrategyCapabilities
        get() = StrategyCapabilities.ANY_PROVIDER
    public suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome
}
```
Existing analogs: `ScriptedStrategy` (core/src/testFixtures/.../ScriptedStrategy.kt) is the only implementation in the repo; there is no LocalGrammar tier. `SingleShotStrategy` is the first real tier.

`StrategyOutcome` (StrategyOutcome.kt:8-68): `Completed(reply: String?, terminalCall: TerminalCall?)` (init `require(reply == null || terminalCall == null)`), `Escalate(reason: EscalationReason, carry: Any?)`, `NoMatch()`, `Failed(reason: FailureReason, details: FailureDetails?)`.

`CommandSession` (CommandSession.kt): `submit(step: ToolStep): DispatchResult` (:34), `tokensUsed: Long` (:41), `recordTurn(turn: TurnRecord)` (:50), `model(): BoundModel` (:61), `policy: TierPolicy` (:25), `carry: Any?` (:28). The constructor is `internal`, so only `RunSession` implements it (pipeline/RunSession.kt:35-40).

### Model call
`BoundModel.complete(request: ModelRequest): ModelResult` (provider/BoundModel.kt:49). `RoutedModel.complete` (BoundModel.kt:95-114) refuses tool requests when `!binding.capabilities.supportsTools` (records `CAPABILITY_REFUSED`, returns `Failure(ModelUnsupported())`), calls the provider under `guarded`, and **records the turn itself**: `recorder.turnRecorded(strategy, turnOf(result, ...))` with `toolNames = response?.message?.toolCalls?.map { it.name }.orEmpty()` (BoundModel.kt:128). Consequences: (a) SingleShot must NOT call `session.recordTurn` for this call (double count; `recordTurn` doc: "Turns made through [model] are recorded by the engine", CommandSession.kt:47-48); (b) extra tool calls already appear in the trace as `TurnRecord.toolNames.size > 1`.

A refused handle: `BoundModel.refusal: FailureReason?` non-null, `complete` returns `ModelResult.Failure(refusal)` with no provider call (BoundModel.kt:66-73). SingleShot converts it to `Failed(refusal)` and must never escalate an on-device refusal (BoundModel.kt:24-27 doc).

### Request model (where the flag goes)
`ModelRequest` (core/transcript/ModelRequest.kt:20-27), verbatim:
```kotlin
public class ModelRequest(
    public val system: String,
    messages: List<Message>,
    tools: List<ToolSpec>,
    public val toolChoice: ToolChoice,
    public val maxTokens: Int,
    public val cache: CacheDirective,
) {
    public constructor(system: String, messages: List<Message>, maxTokens: Int) :
        this(system, messages, emptyList(), ToolChoice.Auto(), maxTokens, CacheDirective(true))
    public constructor(system: String, messages: List<Message>, tools: List<ToolSpec>, maxTokens: Int) :
        this(system, messages, tools, ToolChoice.Auto(), maxTokens, CacheDirective(true))
```
Validation in `init`: non-empty messages, `maxTokens >= 1`, distinct tool names, a `ToolChoice.Required` must name a tool in `tools` (ModelRequest.kt:42-51). `ProviderRequest` wraps it (`request: ModelRequest`, ProviderRequest.kt:17-22), so encoders read `call.request.<flag>` with no `ProviderRequest` change.

### Encoders today
Anthropic `encodeToolChoice` (AnthropicEncoder.kt:90-100):
```kotlin
if (choice is ToolChoice.Required && !reshape && call.capabilities.supportsForcedToolChoice) {
    put(TYPE, "tool"); put("name", choice.toolName)
} else { put(TYPE, "auto") }
```
Body key order is `model, max_tokens, tools, tool_choice, system, messages` (AnthropicEncoder.kt:33-35 doc; AnthropicEncoderTest.kt:162 asserts it). Existing test asserts `tool_choice` equals exactly `{"type":"auto"}` when no flag (AnthropicEncoderTest.kt:181), so the new key must be OMITTED when the flag is false.

Chat `parallel_tool_calls` (ChatEncoder.kt:65-67):
```kotlin
if (vendor.parallelToolCallsFalseOnForced && (required != null || strictNames.isNotEmpty())) {
    put(KEY_PARALLEL_TOOL_CALLS, false)
}
```
`ChatVendor` (ChatVendor.kt:38-57): OPENAI `parallelToolCallsFalseOnForced = true`, `requireParametersOnForced = false`; OPENROUTER `requireParametersOnForced = true`, `parallelToolCallsFalseOnForced = false`. OpenRouter also sends `provider:{require_parameters:true}` when a tool is named (ChatEncoder.kt:71-73), which is the mechanism behind the R5 404.

Decoders: Anthropic returns `Failure(NoToolCall())` only when `toolRequired` (request had `ToolChoice.Required`) and the answer is a plain `END_TURN` with no tool call (AnthropicDecoder.kt:46-52); a refusal or truncation stays a `Success` carrying `StopReason.REFUSAL`/`MAX_TOKENS`. Chat does the same (ChatDecoder rows 5, 6, 8: refusal / `content_filter` → `Success(REFUSAL, emptyList())`; `length` → `Success(MAX_TOKENS)`; `stop` with no call and `toolRequired` → `Failure(NoToolCall())`). Both decoders can return **several** tool calls in `message.toolCalls`.

### Write path
- `ToolStep` is sealed: `Finished(toolName, kind, result, context)` and `Mutation(mutations: List<PendingMutation>)` with `require(this.mutations.isNotEmpty())` (ToolStep.kt:4-45).
- `PendingMutation` (ToolStep.kt:48-68): `toolName`, `targetIds` (default empty), `context: Any?` (default null), `suspend fun apply(): StepResult`.
- `CommitCoordinator.submit` (CommitCoordinator.kt:49-55) → `submitMutation` (:110-118): ONE `gateStep.decide(CommitProposal(runId, parentRunId, step.mutations))` per `ToolStep.Mutation`; `Admit` → `applyAll(decision.amended ?: step.mutations)`, `Hold` → `hold(...)` which records the `HeldProposal` and one `HELD` action per mutation and returns the fixed notice. `applyAll` (:137-145) applies items one at a time; "an item that fails never stops or undoes its siblings".
- `GateDecision` (PreApplyGate.kt:16-61): closed `Admit(amended: List<PendingMutation>?)` (empty list refused) and `Hold(reason: Any?, appOutcomeToken: String?)`. A throwing gate holds (GateStep.kt:19-22).
- `commitHeld(held)` / `commitHeld(held, amended)` (CommandPipeline.kt:131, 138-139): new run with `parentRunId = held.runId`, own close, idempotent, empty amended list throws `IllegalArgumentException` before the proposal is used up.
- `TierWalk` (pipeline/TierWalk.kt:77-90): `Completed` → `CommandOutcome.Completed(effects, reply, terminalCall, partial=false)`; `Escalate`/`NoMatch` after any applied or held action → `suppressed` → `Completed(partial = true)` (GATE-07); a strategy that throws becomes `Failed` + `STRATEGY_ERROR` code (:60-66).

### Limits (where enforced today)
`TierPolicy` constants (TierPolicy.kt:6-8): `DEFAULT_MAX_ITERATIONS = 6`, `DEFAULT_TOKEN_CEILING = 60_000L`, `DEFAULT_MAX_TOKENS_PER_TURN = 4_096`; `maxIterations >= 2` enforced (:9, :46). The class doc (:15-18) says the engine enforces only `offlineOnly`, `maxTier`, static `allowedProviders` and `commandTimeoutMillis`; "`maxIterations`, `tokenCeiling` and `maxTokensPerTurn` are advisory ... a strategy that ignores `session.policy` is unbounded". `BudgetBound.ITERATIONS` / `BudgetBound.TOKENS` and `FailureReason.BudgetExceeded(bound)` exist (BudgetBound.kt:15-18, FailureReason.kt:177). `session.tokensUsed` sums every reported turn across tiers (RunRecorder.kt `tokenTotal`, saturating). SB's reference order is token ceiling first, using strict `>` after adding the turn (`state.cumulativeTokens > MAX_UTTERANCE_TOKENS`, SecondBrain AnthropicAgentLoop.kt:191-194).

### Terminal tools
`ToolSpec(name, description, inputSchema, mutating=false, terminal=false, strict=null)` (ToolSpec.kt:45-52), `init { require(!(terminal && mutating)) }` (:55). `TerminalCall(toolName, arguments)` (TerminalCall.kt:14-17). `Completed(reply = null, terminalCall)` is the carrier. `ToolSpec.clarification(name)` builds the standard terminal clarification tool (ToolSpec.kt:67-71). No strategy handles terminal calls yet; SingleShot is the first. Rule to implement: look up the called tool name in the snapshot's tools; if its `terminal` is true, return `Completed(null, TerminalCall(name, args))` without invoking the resolver.

### Test harness (reuse, do not rebuild)
`core/src/testFixtures/.../testing/`: `FakeAiProvider` (scripted `ModelResult`s; records every `ProviderRequest` in `calls`/`callCount`; throws `AssertionError` when the script is exhausted; helpers `reply(...)`, `toolCall(callId, name, arguments, usage)`), `ScriptedStrategy`, `ScriptedGate` (`admitAll`, `holdAll(reason, token)`, `sequence`, lambda ctor; records `proposals`/`calls`), `FakeMutation` (`applyCount`, optional shared log), `RecordingCommitSink` (`actions`, `closes`, `closedRunIds`), `RecordingEventListener`, `ScriptedSelectionSource.fixed(ProviderSelection(provider, model))`, `ScriptedCredentialSource.keys(provider to "key")`, `FakeClock`, `NoNetworkGuard.during { }`. Routed pipeline wiring template: RedactionCanaryTest.kt:358-380 (`tier(...)`, `provider(fake)`, `providerSelection`, `credentials`, `gate`, `commitSink`). Scenario templates: `DeferModeTest` (commitHeld child run, amended list, idempotency), `BatchIsolationTest`, `HeldCommitGuardsTest`, `EscalationSafetyTest`, `TerminalCallTest`.

## Single-call flag wire facts (D-12 answers)

| Question | Answer | Source |
|----------|--------|--------|
| Where does `disable_parallel_tool_use` go? | Inside the `tool_choice` object; "It is not a top-level request parameter." | `[CITED: platform.claude.com/docs/en/agents-and-tools/tool-use/parallel-tool-use#disable-parallel-tool-use]` |
| Anthropic `auto` + flag | "setting `disable_parallel_tool_use: true` means Claude calls at most one tool per response. Claude can still answer in plain text without calling any tool." | same page |
| Anthropic `any` / `tool` + flag | "Claude calls exactly one tool. Claude Opus 5.5, Claude Sonnet 5.5, Claude Fable 5.1, and Claude Mythos 5.1 don't support these `tool_choice` types" | same page |
| Can `auto` still return >1 `tool_use` WITH the flag? | Docs state "at most one" with no strict-mode exception. Without the flag Anthropic says "By default, Claude may call multiple tools in a single response." So with the flag the answer is no; the engine's first-call-only guard is belt and braces. | `[CITED: same page]` |
| Forced-unsupported models (reshape path) | `auto` + strict + instruction is the documented replacement; the flag applies to `auto`, so the reshaped request MUST also carry `disable_parallel_tool_use:true` | `[CITED: platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools#forcing-tool-use]` (table row listing the four models) |
| Does the flag invalidate the cached prefix? | Invalidation table: "Tool choice ✓ ✓ ✘ ... Changes to `tool_choice` parameter only affect message blocks". There is **no row for `disable_parallel_tool_use`**. By analogy (it lives in `tool_choice`) it should be message-block-only, but absence is not evidence. `[ASSUMED]` (see A2); the engine's single breakpoint is on the last system block, so tools+system cache either way unless Anthropic changes the constructed tool prompt. | `[CITED: platform.claude.com/docs/en/build-with-claude/prompt-caching]`, absence noted |
| OpenAI Chat `parallel_tool_calls:false` | "ensures exactly zero or one tool is called"; docs do not detail the forced-choice interaction (forced + false = exactly one) | `[CITED: developers.openai.com/api/docs/guides/function-calling.md]` |
| OpenAI strict + forced + `reasoning_effort:"none"` + `parallel_tool_calls:false` together | Accepted: Phase 5 live call C1 (OpenAI `gpt-5.4-mini`, strict log tool, named `tool_choice`, `parallel_tool_calls:false`, `reasoning_effort:"none"`) returned HTTP 200, `finish=tool_calls`, `reasoning_tokens` 0; request bytes match golden `providers/src/test/resources/golden/chat/requests/openai.json:71` (`"parallel_tool_calls": false`) | `[VERIFIED: .planning/phases/05-openai-openrouter-transports/evidence/live-chat-capture.txt, C1 line + A1 finding]` |
| OpenAI o-series | `parallel_tool_calls` is rejected: 400 `Unsupported parameter: 'parallel_tool_calls' is not supported with this model.` (reported for o3-mini; reports also name o1/o4-mini) | `[CITED: github.com/langchain-ai/langchain/issues/29704]` (o3-mini); other o-series ids `[ASSUMED]` |
| OpenAI snapshot caveat | "`gpt-4.1-nano-2025-04-14` can sometimes include multiple tool calls for the same tool if parallel tool calls are enabled" | `[CITED: function-calling.md]` |
| OpenRouter + `parallel_tool_calls` | Docs: default true for most models; "When `parallel_tool_calls` is `false`, the model will only request one tool call at a time." Docs say nothing about `require_parameters` interplay. Live: R5 (`openai/gpt-5.4-mini`, forced, `require_parameters:true`, `parallel_tool_calls:false`) → HTTP 404 "No endpoints found that can handle the requested parameters", `failed_routing_step` "Filter by Parameters". R1 (identical minus the flag) → 200. | `[CITED: openrouter.ai/docs/guides/features/tool-calling]`, `[VERIFIED: evidence/live-chat-capture.txt A4 + R1/R5 lines; 05-12-SUMMARY.md:60,70]` |
| OpenRouter without `require_parameters` | Never probed. Not needed: engine-side first-call-only covers it. | `[ASSUMED]` unprobed |

**Prescribed encodings** (flag = `ModelRequest.singleToolCall == true`, tools non-empty):
- Anthropic: add `"disable_parallel_tool_use": true` to the `tool_choice` object in BOTH the forced (`{"type":"tool","name":...}`) and the auto/reshape (`{"type":"auto"}`) shapes, after `type`/`name`. Omit when false.
- Chat: send `"parallel_tool_calls": false` when `vendor.parallelToolCallsFalseOnForced && (required != null || strictNames.isNotEmpty() || request.singleToolCall)` and the model accepts the parameter (not o-series). Key position unchanged (after `tool_choice`).
- OpenRouter: unchanged bytes. Rely on first-call-only.

**Net byte effect on shipped goldens:** for the SingleShot request on OpenAI (`Required`), zero change (already sent). The new flag changes bytes only for Anthropic (both shapes) and for OpenAI `Auto` + non-strict. All existing goldens stay green because they do not set the flag.

## Architecture Patterns

### System Architecture Diagram

```
 CommandInput(transcript, language, context, parentRunId)
        │
        ▼
 CommandPipeline.execute ── TierPolicy snapshot ──► TierWalk ──► SingleShotStrategy.execute(input, session)
                                                                        │
              ┌─────────────────────────────────────────────────────────┤
              │ 1 ToolSpecProvider.tooling(input) → system, tools, extractionTool   (frozen prefix; app code)
              │ 2 limits pre-check: session.tokensUsed >= policy.tokenCeiling ──► Failed(BudgetExceeded(TOKENS)), no call
              │ 3 UserTurnRenderer.render(ctx) → user text   (date/zone/transcript; messages only)
              │ 4 ModelRequest(system, [UserMessage], tools, Required(extractionTool),
              │                maxTokens = policy.maxTokensPerTurn, CacheDirective(true), singleToolCall = true)
              ▼
        session.model().complete(request) ──► (refused handle ──► Failed(refusal))
              │            │
              │            └─ :providers  ENCODE: Anthropic tool_choice{+disable_parallel_tool_use} | Chat parallel_tool_calls:false (OpenAI only)
              │               transient retry / forced-tool reshape live HERE (≤3 HTTP, invisible)
              ▼
        ModelResult ──► OUTCOME MAPPING (table below)
              │
   Success + tool calls, not refusal/truncated
              │  take FIRST call; extras ──► TraceCode.EXTRA_TOOL_CALLS_DROPPED
              │  post-call check: tokensUsed > tokenCeiling ──► Failed(BudgetExceeded(TOKENS)) before any dispatch
              ├─ called tool is terminal ──► Completed(reply=null, TerminalCall(name,args))   (resolver skipped; D-13)
              ├─ called tool unknown ──► Escalate(MalformedExtraction)
              ▼
        OutcomeResolver.resolve(Extraction(toolName, args), input) ──► Resolution   (NEVER writes)
              │   Steps(steps[, reply]) | NoMatch | Escalate(reason, carry) | Failed(reason, details)
              ▼
   Steps: submit Finished steps in order, then ONE merged ToolStep.Mutation(all mutations)
              ▼
        session.submit ──► CommitCoordinator ──► PreApplyGate.admit (once per proposal)
              │                       ├─ Admit(amended?) ──► applyAll (per-item isolation) ──► CommitSink.onAction per item
              │                       └─ Hold(reason, token) ──► HeldProposal + HELD actions
              ▼
        StrategyOutcome.Completed(reply)  ──► CommandOutcome (commits / held / executed)
                                                    │
                  later, app UI: pipeline.commitHeld(held[, amended]) ──► new linked run, own close, idempotent
```

### Recommended Project Structure
```
core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/
├── strategy/
│   ├── ToolSpecProvider.kt      # fun interface + ToolingSnapshot (shared with Phase 9)
│   ├── OutcomeResolver.kt       # fun interface + Extraction + Resolution (abstract class, NOT sealed)
│   └── singleshot/
│       ├── SingleShotStrategy.kt   # strategy + Builder DSL
│       ├── UserTurn.kt             # UserTurnRenderer fun interface + UserTurnContext + default
│       └── SingleShotOutcomes.kt   # internal mapping (ModelResult/StopReason -> StrategyOutcome)
├── transcript/ModelRequest.kt   # + singleToolCall (7-arg ctor)
├── telemetry/TraceCode.kt       # + EXTRA_TOOL_CALLS_DROPPED
└── strategy/CommandSession.kt   # + internal recordCode hook (RunSession implements)
providers/src/main/kotlin/.../providers/
├── anthropic/AnthropicEncoder.kt  # encodeToolChoice + flag
└── chat/{ChatEncoder.kt, OpenAiModelRules.kt}  # flag + o-series gate
core/src/test/... SingleShot*Test.kt ; providers/src/test/... SingleShotWireTest.kt
```

### Recommended seam shapes (Claude's discretion; prescriptive so the planner does not re-derive)
These obey the frozen-API rules (regular classes, `internal` constructors where state is engine-built, no default args, `with...`/secondary constructors for growth, redacted `toString`). Names follow contract §5.2 and ARCHITECTURE.md §4. They are public API from v1.0.0, so confirm with `caltracker-android-9a` and the orchestrator before the freeze (see Open Question 1).

```kotlin
// strategy/ToolSpecProvider.kt  -- shared with AgenticLoop (Phase 9); extraction tool is nullable for the loop
public fun interface ToolSpecProvider {
    /** Called once per command. The system text and tool list must not depend on the command (cached prefix). */
    public suspend fun tooling(input: CommandInput): ToolingSnapshot
    public companion object { public fun fixed(snapshot: ToolingSnapshot): ToolSpecProvider = ToolSpecProvider { snapshot } }
}
public class ToolingSnapshot(public val system: String, tools: List<ToolSpec>, public val singleShotTool: String?) {
    public val tools: List<ToolSpec> = tools.toList()
    init { require(singleShotTool == null || this.tools.any { it.name == singleShotTool }) { "singleShotTool must name a tool in tools" } }
    override fun toString(): String = "ToolingSnapshot(systemLength=${system.length}, tools=${tools.map { it.name }}, singleShotTool=$singleShotTool)"
}

// strategy/OutcomeResolver.kt
public fun interface OutcomeResolver { public suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution }
public class Extraction(public val toolName: String, public val arguments: JsonObject) {  // grammar intent+slots = later members
    override fun toString(): String = "Extraction(toolName=$toolName, argumentCount=${arguments.size})"
}
public abstract class Resolution internal constructor() {          // open set, like ModelResult; NOT sealed
    public class Steps(steps: List<ToolStep>, public val reply: String?) : Resolution()   // + secondary ctor without reply; require non-empty
    public class NoMatch : Resolution()
    public class Escalate(public val reason: EscalationReason, public val carry: Any?) : Resolution()
    public class Failed(public val reason: FailureReason, public val details: FailureDetails?) : Resolution()
}

// strategy/singleshot/UserTurn.kt
public fun interface UserTurnRenderer { public fun render(context: UserTurnContext): String }
public class UserTurnContext internal constructor(
    public val input: CommandInput, public val dateTime: java.time.ZonedDateTime, public val carry: Any?,
)   // carry lets a later-tier SingleShot render the earlier tier's hint after the prefix (never in system)
```
Default renderer (engine, domain-free): `"Current local date-time: <ISO offset date-time> (<zone id>)\n\n<transcript>"`, clock = `java.time.Clock` injected on the builder (default `Clock.systemDefaultZone()`). This matches SB's framing shape without SB's "Voice command:" label (SecondBrain AnthropicAgentLoop.kt:377-383); SB keeps byte-exactness by supplying its own renderer.

Strategy builder (growth-safe, mirrors `TierPolicy.Builder`):
```kotlin
public class SingleShotStrategy internal constructor(...) : CommandStrategy {
    public companion object {
        public operator fun invoke(id: StrategyId, block: Builder.() -> Unit): SingleShotStrategy
    }
    public class Builder internal constructor() {
        public var tooling: ToolSpecProvider? = null          // required
        public var resolver: OutcomeResolver? = null          // required
        public var userTurn: UserTurnRenderer = DefaultUserTurn
        public var clock: java.time.Clock = java.time.Clock.systemDefaultZone()
        public var forceTool: Boolean = true                  // false => ToolChoice.Auto (lets a terminal tool be chosen)
        public var onNoToolCall: (NoToolCallContext) -> StrategyOutcome  // default Escalate(EscalationReason.NoToolCall())
        public var onRefusal: (RefusalContext) -> StrategyOutcome        // default Failed(FailureReason.Refusal())
    }
}
```
Build-time validation: `requireNotNull(tooling)`/`requireNotNull(resolver)` → `IllegalArgumentException` (same style as PipelineBuilder.build, PipelineBuilder.kt:117-119). The override hooks are plain function-typed builder properties so no new public context classes are required if the planner prefers `(ModelResponse?) -> StrategyOutcome`; keep them simple.

### Pattern 1: `execute` as a `when` chain (detekt `ReturnCount` default is max 2)
**What:** one linear path; each stage is a small private function returning `StrategyOutcome?` and the chain is a `when`/`?:` elvis, so no function has more than two returns.
```kotlin
// Source: shapes read from CommandStrategy.kt:25, CommandSession.kt:34-61, StrategyOutcome.kt, BoundModel.kt:49
override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome {
    val snapshot = tooling.tooling(input)
    val tool = snapshot.singleShotTool ?: return StrategyOutcome.Failed(FailureReason.Other("no_single_shot_tool"))
    val model = session.model()
    return model.refusal?.let { StrategyOutcome.Failed(it) }          // refused handle: loud Failed, never Escalate
        ?: overCeiling(session)                                       // pre-call: tokensUsed >= ceiling
        ?: callAndMap(input, session, model, snapshot, tool)
}
private suspend fun callAndMap(...): StrategyOutcome {
    val request = ModelRequest(
        snapshot.system, listOf(UserMessage(userTurn.render(context))), snapshot.tools,
        if (forceTool) ToolChoice.Required(tool) else ToolChoice.Auto(),
        session.policy.maxTokensPerTurn, CacheDirective(true), true,   // 7th arg = singleToolCall
    )
    return when (val result = model.complete(request)) {
        is ModelResult.Success -> mapResponse(result.response, ...)
        is ModelResult.Failure -> mapFailure(result)                   // NoToolCall -> Escalate; else Failed(reason, details)
        else -> StrategyOutcome.Failed(FailureReason.Other("unknown_model_result"))   // ModelResult is open
    }
}
```
`ModelResult` is an open class, so the `when` needs an `else` (ModelResult.kt:12 doc: "keep an `else` branch").

### Pattern 2: Anthropic flag encoding (providers)
```kotlin
// Source: AnthropicEncoder.kt:90-100 (existing) + the new key
private fun encodeToolChoice(call: ProviderRequest, reshape: Boolean): JsonObject {
    val choice = call.request.toolChoice
    return buildJsonObject {
        if (choice is ToolChoice.Required && !reshape && call.capabilities.supportsForcedToolChoice) {
            put(TYPE, "tool"); put("name", choice.toolName)
        } else {
            put(TYPE, "auto")
        }
        if (call.request.singleToolCall) put("disable_parallel_tool_use", true)   // inside tool_choice, both shapes
    }
}
```

### Pattern 3: Chat flag encoding + o-series gate (providers)
```kotlin
// Source: ChatEncoder.kt:65-67 (existing) extended
val oneCall = required != null || strictNames.isNotEmpty() || request.singleToolCall
if (vendor.parallelToolCallsFalseOnForced && rules.acceptsParallelToolCalls && oneCall) {
    put(KEY_PARALLEL_TOOL_CALLS, false)
}
```
`rules` is `ChatWireRules` (OpenAiModelRules.kt, internal), which already carries per-family wire facts; add `acceptsParallelToolCalls` (false for `O_SERIES = ^o\d.*$` on OpenAI-hosted ids, true otherwise) there. `ChatWireRules` is `internal` and not part of the public API, so this is not an API change. `routedDefaultRules()` and the OpenRouter path never reach the key because the vendor flag is false.

### Pattern 4: outcome mapping inside the strategy
Closed decision table (every row uses classes verified to exist).

| Provider result | Default `StrategyOutcome` | Source / note |
|-----------------|---------------------------|---------------|
| `refusal != null` on the handle | `Failed(handle.refusal)` | BoundModel.kt:42-43; on-device refusal never escalates |
| `Failure(NoToolCall)` | `Escalate(EscalationReason.NoToolCall())` | SHOT-02; EscalationReason.kt:14 |
| `Failure(other reason, details)` | `Failed(reason, details)` | Auth, RateLimited, Network, Timeout, MalformedToolArgs, ModelUnsupported, HttpError ... pass through typed (CORE-06) |
| `Success`, stop `REFUSAL` | `Failed(FailureReason.Refusal())` | Check BEFORE inspecting tool calls (Anthropic refusal is a Success) |
| `Success`, stop `MAX_TOKENS` | `Failed(FailureReason.MaxTokens())` | never trust truncated args |
| `Success`, stop `PAUSE_TURN` | `Failed(FailureReason.PauseTurn())` | |
| `Success`, stop `CONTEXT_WINDOW_EXCEEDED` | `Failed(FailureReason.ContextWindowExceeded())` | |
| `Success`, no tool call, `END_TURN` | `Escalate(NoToolCall)` | reachable when `forceTool=false` or a provider not setting `toolRequired` |
| `Success`, no tool call, `OTHER` | `Failed(FailureReason.UnknownStop())` | |
| `Success` with ≥1 tool call | take `toolCalls.first()`; if `size > 1` record `EXTRA_TOOL_CALLS_DROPPED` | OpenRouter and any model that ignores the flag |
| called tool is `terminal` in the snapshot | `Completed(null, TerminalCall(name, args))`, resolver not invoked | D-13 / A19 |
| called tool name not in snapshot | `Escalate(EscalationReason.MalformedExtraction())` | strict mode guarantees a valid name, non-strict does not |
| `post-call tokensUsed > tokenCeiling` | `Failed(BudgetExceeded(BudgetBound.TOKENS))` before resolve/submit | SB order: ceiling check before dispatch |
| `Resolution.Steps` | submit; `Completed(reply)` | see Pattern 5 |
| `Resolution.NoMatch` | `StrategyOutcome.NoMatch()` | D-07: CT maps it to its Unavailable |
| `Resolution.Escalate(reason, carry)` | `Escalate(reason, carry)` | |
| `Resolution.Failed(reason, details)` | `Failed(reason, details)` | |
| resolver throws | let it propagate | TierWalk collapses to `Failed(Unexpected)` + `STRATEGY_ERROR` (TierWalk.kt:60-66); do not catch `Exception` (detekt `TooGenericExceptionCaught`) |

### Pattern 5: submit steps, one proposal
Submit `ToolStep.Finished` steps in order; merge every `ToolStep.Mutation` into ONE `ToolStep.Mutation(allMutations)` and submit once, so the gate decides once per proposal (D-05; ARCHITECTURE §3.4 "all Mutation parts → ONE CommitProposal"). `[ASSUMED]` that merging is wanted when a resolver returns several Mutation steps (A3); the common CT case returns exactly one. Return `Completed(reply)`. Never return `Escalate` after a submit (the coordinator's counts would suppress it anyway, TierWalk.kt:86-89).

### Anti-Patterns to Avoid
- **Calling `session.recordTurn` for the model call:** `RoutedModel.complete` already records it; doing it again double-counts `tokensUsed` and breaks the ceiling math (BoundModel.kt:108).
- **Putting date/zone/language/transcript into `system`:** busts the cache prefix (PROV-06). Per-command text only in the `UserMessage`.
- **Resolver writes:** the resolver returns `ToolStep`s; `apply` runs only after the gate (PendingMutation.apply doc, ToolStep.kt:63-66).
- **Catching `Exception` around the resolver:** swallows the never-throw collapse design and trips detekt.
- **A retry loop on prose or a second "nudge" call:** would make SingleShot a looping strategy and create the 6/60000/4096 obligations on a new path. Escalate instead.
- **Adding a `sealed` Resolution/Outcome type:** fails `review-api-surface.sh`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Batch write isolation, per-item results | a loop in the strategy | `CommitCoordinator.applyAll` via `session.submit(ToolStep.Mutation(list))` | already isolates failures and emits one `ActionEvent` per item (CommitCoordinator.kt:137-145) |
| Deferred / amended confirm | a new amend API | `CommandPipeline.commitHeld(held, amended)` | idempotent, linked run, tested (DeferModeTest) |
| Suspending confirm UI helper | custom mutex/timeout | `AwaitingConfirmGate` (core/commit/AwaitingConfirmGate.kt) | already ports SB's `VoiceConfirmGate` |
| Never-throw collapse | try/catch in the strategy | let TierWalk's `guarded` collapse; use `internal/Guarded.kt` helpers if a guard is needed | one justified `@Suppress` lives there (CLAUDE.md detekt table) |
| JSON schema validation of tool args | a validator | the resolver (app) validates; transports already reject non-object input (`MalformedToolArgs`) | no permitted dependency in `:core`; D-09 pass-through |
| Date formatting | manual string building | `java.time.ZonedDateTime` + `DateTimeFormatter.ISO_OFFSET_DATE_TIME` | JDK 11 surface is allowed |
| Request-id/redaction-safe printing | ad hoc | follow the `toString()` pattern of existing types (counts and names only) | `RedactionCanaryTest` sweeps it |

**Key insight:** every hard part of the CT flow (gate-once, hold, amend, isolate, defer, idempotent, linked close) is already shipped and tested at the pipeline level. Phase 7's job is the thin strategy that feeds those seams the right `ToolStep`, plus the wire flag and the tests that prove CT's four flows through it.

## CT-shaped acceptance scenarios (SHOT-03, D-10)

Neutral fixture vocabulary (library stays domain-free, CLN-02 scans `src/main` only, and tests still use neutral names per D-10): tool `record_entries` with `entries[]` of `{label, quantity, unit, confidence}` plus a shared `target_date`; a fixture "catalog" with candidates `{id, label, score}` and an unmatched case; a fixture resolver that applies CT's shape of rule (confidence floor, match-score threshold, unit mismatch, non-positive quantity) and stamps the verdict into the opaque `PendingMutation.context` (D-04); a `ScriptedGate` lambda that reads that context. Thresholds live in the fixture, never in `src/main`.

Run every scenario through the full pipeline: `commandPipeline { tier(SingleShotStrategy(...)); provider(FakeAiProvider(...)); providerSelection = ScriptedSelectionSource.fixed(...); credentials = ScriptedCredentialSource.keys(...); gate = ...; commitSink = RecordingCommitSink() }`, inside `NoNetworkGuard.during { }`, `runTest`.

| # | Scenario | Setup | Assertions |
|---|----------|-------|------------|
| S1 | strong single auto-logs in the ORIGINAL run (D-05) | one entry, confidence/score above thresholds, gate admits | `commits.size == 1`, `held` empty, sink one `COMMITTED`, one run close `Done`, no `commitHeld` |
| S2 | weak single held, then deferred commit (D-11) | score below threshold, gate `Hold(reason, token)` | `outcome.held.single()`, mutation `applyCount == 0`, action kind `HELD`; `commitHeld(held)` → child with `parentRunId == original.runId`, own close, `applyCount == 1`; second `commitHeld` returns the same outcome object (assertSame), no extra sink events |
| S3 | all-strong 3-item batch is still held, gate asked once | three strong entries | `gate.calls == 1`, `gate.proposals.single().mutations.size == 3`, three `HELD` actions, nothing applied |
| S4 | amended confirm (D-06) | held batch; amended list = edited quantity, swapped match, shared date change, one row dropped | `commitHeld(held, amended)` applies only the amended mutations (`held` mutations `applyCount == 0`), per-item `ActionEvent`s in order |
| S5 | failing row does not poison siblings | one amended/admitted mutation returns `isError` or throws | siblings still applied, `IS_ERROR` action for the bad one, outcome still `Completed` (read `executed`) |
| S6 | unmatched row proposed, recovered at amend (D-07) | entry with no catalog match → mutation with unresolved target held; amend supplies target; a still-unresolved row's `apply` no-ops | held includes the unresolved row; amended apply resolves it; skipped row yields a per-item result; resolver returns `NoMatch` only when nothing is proposable → `Unhandled` / next tier |
| S7 | refusal and prose | stop `REFUSAL` response; separately a `Failure(NoToolCall)` with a 2nd tier | refusal → `CommandOutcome.Failed(Refusal)`, gate `calls == 0`; NoToolCall → second `ScriptedStrategy` ran and carry null |
| S8 | suspend-style admit with amended proposal | `GateDecision.Admit(amendedList)` in the original run | amended applied in the original run, original mutations never applied |
| S9 | terminal call | model calls a `ToolSpec.clarification(...)` tool | `Completed.terminalCall.asClarification()` non-null, `resolver` invocations `== 0`, `reply == null` |
| S10 | first call only | response with two tool calls | resolver invoked once with the FIRST call's arguments; trace `codes` contains `extra_tool_calls_dropped`; `attempts.single().turns.single().toolNames.size == 2` |

Also: GATE-07 interplay (held then the resolver's tier asks to escalate is impossible by construction; add one assertion that a SingleShot tier followed by a second tier never runs the second after a Hold), and the `commitHeld` cancel path is already covered by `HeldCommitGuardsTest` (reuse, do not duplicate).

## Limits: what SingleShot enforces and tests (v1.0.0 cut blocker)

SingleShot makes exactly one provider turn, so it is not a loop and has no retry of its own. The obligation is still discharged explicitly and with named tests (`SingleShotLimitsTest`), because Phase 11 checks for them:

| Limit | Enforcement | Test |
|-------|-------------|------|
| `maxTokensPerTurn` (4096) | `ModelRequest.maxTokens = session.policy.maxTokensPerTurn` | default policy → `fake.calls.single().request.maxTokens == 4096`; `TierPolicy { maxTokensPerTurn = 777 }` → 777 |
| `tokenCeiling` (60000), pre-call | if `session.tokensUsed >= policy.tokenCeiling` → `Failed(BudgetExceeded(BudgetBound.TOKENS))`, zero provider calls | two-tier ladder: tier 1 `ScriptedStrategy` calls `session.recordTurn(TurnRecord(..., Usage(...)))` totalling the ceiling then `Escalate`; tier 2 SingleShot; assert `fake.callCount == 0`; boundaries ceiling-1 (calls) and ceiling (refuses) |
| `tokenCeiling`, post-call (SB order) | after the response, `session.tokensUsed > policy.tokenCeiling` → same `Failed`, before resolver/submit | response whose `Usage` pushes over; assert gate `calls == 0`, nothing applied |
| `maxIterations` (6; min 2) | satisfied by construction: one turn | with `maxIterations = 2` and with default 6, a NoToolCall/prose response makes `fake.callCount == 1` (no re-ask) and `attempts.single().turns.size == 1` |

Pre-call uses `>=` (a call must cost at least one token, so at the ceiling nothing more may be spent); post-call uses `>` to mirror SB (AnthropicAgentLoop.kt:191-194). `[ASSUMED]` boundary choice (A6): flag to the orchestrator in the plan; the tests pin whichever is decided.

## User-turn renderer and cache safety (D-03)

- Lives in `core.strategy.singleshot`; `UserTurnRenderer.render(UserTurnContext)` returns the text of the single `UserMessage`. The context carries `input` (so a clarification follow-up can use `input.parentRunId` / `input.context` to put original transcript + question + choice in, per A19), `dateTime` (from the injected `java.time.Clock`), and `carry` (an escalating tier's hint, goes after the prefix).
- The cached prefix is `tools` then `system` only (AnthropicEncoder.kt:33-36 doc: "Per-request content (the user's words, language, date) only ever appears under `messages`"). Because the renderer output can only reach `messages`, the prefix is untouched by construction. Prove it with a test: run two commands with different transcript/language/clock; assert `fake.calls[0].request.system == fake.calls[1].request.system` and the two `tools` lists are equal element-wise (names and the same `ToolSpec` instances/schemas).
- The default renderer must not read `System.getProperty`/`getenv` or `java.io.File` (NoHardCodedConstantsTest `settingsAccess` list). `Clock.systemDefaultZone()` is fine.

## Common Pitfalls

### Pitfall 1: default-argument constructors on new public classes
**What goes wrong:** `ApiShapeTest.noClassOutsideTheDocumentedExceptionsDeclaresADefaultArgumentConstructorStub` fails the whole `:core:test`. **Why:** the growth rule (STUB_EXCEPTIONS is closed: CommandInput, ToolSpec, ActionDetails, ExecutedAction, CommandTrace, RunRecorder, TierAttempt). **Avoid:** builder DSL or explicit overloads. Applies to `SingleShotStrategy`, `ToolingSnapshot`, `Extraction`, `Resolution.Steps`, `UserTurnContext`. **Warning sign:** a Kotlin `= ...` in any public constructor.

### Pitfall 2: an eighth sealed type
**What goes wrong:** `scripts/review-api-surface.sh` fails ("sealed type ... outside the allow-list"). **Avoid:** `abstract class ... internal constructor()` for `Resolution`, as `ModelResult` and `ToolChoice` do.

### Pitfall 3: `ModelRequest` primary constructor change breaks callers
**What goes wrong:** many tests and `:providers` code call the 6-argument constructor (TranscriptTypesTest.kt:45, ChatPipelineRetryTest.kt `ModelRequest(FIXED_SYSTEM, ..., CacheDirective(true))`). **Avoid:** keep the 6-argument form as a public secondary constructor delegating with `singleToolCall = false`; make the new 7-argument form the primary. No defaults. Update `toString()`.

### Pitfall 4: the new `tool_choice` key breaks existing exact-match assertions
**What goes wrong:** AnthropicEncoderTest.kt:181 asserts `{"type":"auto"}` exactly. **Avoid:** emit `disable_parallel_tool_use` only when the flag is true; existing goldens/tests then pass untouched.

### Pitfall 5: sending `parallel_tool_calls` to OpenRouter
**What goes wrong:** 404 "No endpoints found that can handle the requested parameters" (live R5), because the forced OpenRouter body also carries `provider.require_parameters:true`. **Avoid:** keep `ChatVendor.OPENROUTER.parallelToolCallsFalseOnForced = false`, gate the new flag on the same vendor field, add a golden/MockWebServer assertion that the OpenRouter SingleShot body has no `parallel_tool_calls`.

### Pitfall 6: `parallel_tool_calls` on OpenAI o-series (latent, already shipped)
**What goes wrong:** forced/strict call on an o-series id returns 400 `Unsupported parameter`. The shipped encoder sends the key for any OpenAI model (ChatEncoder.kt:65). **Avoid:** `acceptsParallelToolCalls` in `ChatWireRules`, false for `^o\d`. Add an encoder test (o-series forced → no key) and note it as a Phase 5 follow-up fix delivered in 07-02. Confidence MEDIUM (A1).

### Pitfall 7: forgetting Anthropic's forced-unsupported models
**What goes wrong:** claude-class models in the capability table that reject forced `tool`/`any` go through `reshape` (`auto`); if the flag is only added to the forced shape those requests can return multiple `tool_use`. **Avoid:** flag in both shapes (Pattern 2) and a test for the reshape body.

### Pitfall 8: refusal and truncation are `Success`
**What goes wrong:** treating `Success` as "has tool call" runs a resolver on truncated or empty output. **Avoid:** check `StopReason.REFUSAL`, `MAX_TOKENS`, `PAUSE_TURN`, `CONTEXT_WINDOW_EXCEEDED` first (Pattern 4).

### Pitfall 9: repo-wide source scans trip on innocuous text
- `NoHardCodedConstantsTest`: no model-family words (`claude`, `sonnet`, `opus`, `haiku`, `gpt`, `gemini` ...) in any `src/main` file **including KDoc and comments**; no `const val` named `DEFAULT_*`/`MIN_*`/`MAX_*` or containing `TOKEN`/`ITERATION`/`CEILING` outside the owner files (TierPolicy.kt, ModelCapabilities.kt, AwaitingConfirmGate.kt, ToolSpec.kt); no literals `60000`/`4096` outside TierPolicy.kt; no `java.io.File`, `System.getenv`, `System.getProperty`, `DataStore`, `android.` references. Wording like "the four forced-unsupported models" is safe, naming them is not.
- `gradle/invariants.gradle.kts` scanners: no `runCatching`, `println`, `System.out/err`, `printStackTrace`, FQ DI annotations, `okhttp3.internal`, and no planning ids in comments (`T-12-34`, `WR-nn`, `Phase NN D-nn`). Do not write "D-03" or "Phase 7 D-xx" in main comments (CLN-05).
- detekt (zero baseline, defaults on): `ReturnCount` (max 2), `LongMethod`, `CyclomaticComplexMethod`, `MagicNumber`, `TooGenericExceptionCaught`. Build with `when`/elvis and small private functions.
- CLN-02: no `log_food`/`LogFood*`/app tool names in `src/main`.

### Pitfall 10: redaction
New public types print counts and names only. Add the new carriers to `RedactionCanaryTest`'s sweep (`sweepBuiltTypes`, RedactionCanaryTest.kt:389): `Extraction`, `ToolingSnapshot`, `UserTurnContext`, `Resolution.*`, plus a SingleShot-routed run whose transcript, tool args, system text and reply carry canaries.

### Pitfall 11: Gradle contention
`:providers` compiles `:core` (`api(project(":core"))`, providers/build.gradle.kts:27). Editing `:core` main while a `:providers` task runs in the same working tree can fail the providers compile mid-edit. Sequence per "Recommended plan decomposition" or use isolated worktrees.

## Code Examples

### Limits test skeleton (pre-call ceiling via a prior tier)
```kotlin
// Source: pattern of DeferModeTest.kt (pipeline + ScriptedStrategy) and CommandSession.recordTurn (CommandSession.kt:50)
val first = ScriptedStrategy(StrategyId("grammar"), { _, session ->
    session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, TierPolicy.DEFAULT.tokenCeiling), 1L))
    StrategyOutcome.Escalate(EscalationReason.NoToolCall())
})
val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.toolCall("c1", "record_entries", args, Usage(1, 0, 0, 1)))
val outcome = pipeline(first, singleShot, fake).execute(CommandInput("two things"))
assertTrue(outcome is CommandOutcome.Failed && (outcome.reason as FailureReason.BudgetExceeded).bound == BudgetBound.TOKENS)
assertEquals(0, fake.callCount)
```

### Deferred weak-match flow (S2)
```kotlin
// Source: DeferModeTest.kt:60-86
val gate = ScriptedGate { proposal -> if (weak(proposal)) GateDecision.Hold("weak_match", "needs_confirm") else GateDecision.Admit() }
val original = pipeline.execute(CommandInput("add the thing"))
val held = original.held.single()
val child = pipeline.commitHeld(held)            // or commitHeld(held, amendedMutations)
assertEquals(original.runId, child.parentRunId)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hard-coded set of "forced tool 400" model ids (CT `AnthropicKnownTool400Ids`) | per-model `supportsForcedToolChoice` capability + reactive reshape in the transport | Phase 4 (PROV-07) | SingleShot always asks `Required`; the transport reshapes. The flag must ride both shapes |
| `parallel_tool_calls` only on forced/strict Chat calls | neutral `singleToolCall` flag also covers auto + non-strict | Phase 7 | OpenAI `Auto` single-shot (terminal-capable `forceTool=false`) now gets one call |
| Forced `tool_choice` on every Anthropic model | Four named models return 400 for `any`/`tool`; use `auto` + strict | docs current 2026-10 | `[CITED: define-tools#forcing-tool-use]` |

**Deprecated/outdated:** none relevant.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | OpenAI o-series ids other than o3-mini (o1, o4-mini) also reject `parallel_tool_calls`; and the original `gpt-5`/`gpt-5.N<4` reasoning ids accept it | Pitfall 6 | Gate too narrow: a 400 on an unlisted reasoning id. Mitigation: app can set `singleToolCall=false`? (it cannot via capabilities; consider also honoring an internal rule per id family). Only live-proven id is `gpt-5.4-mini` (C1) |
| A2 | `disable_parallel_tool_use` does not invalidate the tools+system cache (docs have no row for it) | Wire facts | Cache miss on the very first SingleShot request after enabling; observable via existing `CacheNotEngaged` event. Cheap check at Phase 10 smoke: two identical calls, expect `cache_read_input_tokens > 0` |
| A3 | Coalescing several `ToolStep.Mutation` steps into one proposal is the desired semantics | Pattern 5 | CT returns one Mutation step so no impact; a resolver wanting two gate decisions would be surprised. Confirm with `caltracker-android-9a` |
| A4 | Seam names/shapes (`ToolingSnapshot`, `Extraction`, `Resolution`, `UserTurnContext`, builder knobs) are acceptable to CT and SB | Seam shapes | Public API frozen at v1.0.0; a rename later is a breaking change. Confirm before the surface review |
| A5 | No engine-side JSON-schema validation in v1.0 (resolver owns it); PROV-12's "local schema validation" remains the app's job | Don't Hand-Roll | A consumer expects the engine to flag off-schema args. Mitigation: KDoc on `OutcomeResolver` says so |
| A6 | Ceiling boundary: pre-call `>=`, post-call `>` | Limits | Off-by-one vs the orchestrator's expectation; the tests pin whichever is chosen |
| A7 | `forceTool=false` (Auto) knob is wanted so a terminal/clarification tool is reachable | Seam shapes | Unused API surface frozen forever; defer if CT does not need it |
| A8 | OpenRouter `parallel_tool_calls:false` without `require_parameters` was never probed and is irrelevant | Wire facts | None (engine does not send it) |

## Open Questions

1. **Seam shape sign-off (A4, A3, A7).** What we know: contract §5.2 and ARCHITECTURE §4 name the seams but only sketch signatures; nothing exists in code. What's unclear: whether CT (and SB for `ToolSpecProvider`, which Phase 9 reuses) accepts `ToolingSnapshot(system, tools, singleShotTool?)`, `Extraction`/`Resolution`, `UserTurnContext`. Recommendation: the planner adds a `checkpoint:human-verify` (or an orchestrator message per A13) at the end of 07-03, before 07-04 builds on the shapes, since they freeze at v1.0.0.
2. **o-series fix scope.** The `acceptsParallelToolCalls` gate edits Phase 5 shipped code (`OpenAiModelRules`). Recommendation: include it in 07-02 (tiny, internal, same file family as the flag) and report it to the orchestrator as a Phase 5 follow-up.
3. **Live Anthropic probe.** Do we want the optional 2-call check that the flag keeps `cache_read_input_tokens > 0`? Recommendation below: no live leg in Phase 7; carry the assertion to the Phase 10 smoke.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | all builds | ✓ | OpenJDK 17.0.19 | — |
| Gradle wrapper (offline) | all tasks | ✓ | 9.4.1 (`./gradlew --version --offline`) | — |
| `with-test-keys` | any opt-in live leg | ✓ | `/home/yahir/.local/bin/with-test-keys` | not needed (no live leg recommended) |
| Android device (TESTER) | Gate-1 | not needed | — | SingleShot is JVM-only; no device-verifiable behaviour in this phase |

**Missing dependencies with no fallback:** none.

Measured this session (warm daemon, `--offline`): `:core:test` 424 tests (all green); `:providers:test` (4.12.0 leg) 411 tests, 26 s with `--no-build-cache`; detekt `:core` + `:providers` 4 s; `./gradlew :core:check :providers:check --rerun-tasks --no-build-cache` 1 min 4 s including the 5.2.1 and 5.5.0 legs.

## Validation Architecture

> `workflow.nyquist_validation: true` in .planning/config.json, so this section is required.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`); `:providers` adds legacy `okhttp3.mockwebserver` |
| Config file | none beyond `core/build.gradle.kts`, `providers/build.gradle.kts`, `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :core:test --tests '*SingleShot*' --offline` (seconds) |
| Full suite command | `./gradlew :core:check :providers:check --offline` (~65 s cold; includes detekt, scanners, ApiShapeTest, NoHardCodedConstantsTest, A1 matrix legs) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SHOT-01 | one forced-tool request: tools, `Required(extractionTool)`, `maxTokens` from policy, single `UserMessage`, `singleToolCall=true` | unit (pipeline + FakeAiProvider) | `./gradlew :core:test --tests '*SingleShotRequestTest'` | ❌ Wave 0 |
| SHOT-01 | resolver gets FIRST call's `Extraction`; steps submitted; Finished before Mutation; one merged proposal; resolver never writes | unit | `... --tests '*SingleShotResolveTest'` | ❌ Wave 0 |
| SHOT-01 | seam types: redacted `toString`, `ToolingSnapshot` validation, `Resolution.Steps` non-empty, no default-arg stubs | unit | `... --tests '*SingleShotSeamTypesTest' --tests '*ApiShapeTest'` | ❌ Wave 0 (+ ApiShapeTest ✅) |
| SHOT-01 | cache-safe: two commands, same `system` + `tools`, different user text | unit | `... --tests '*SingleShotUserTurnTest'` | ❌ Wave 0 |
| SHOT-02 | outcome mapping table (every row) incl. refusal-before-calls, MAX_TOKENS, NoToolCall, refused handle, override hooks | unit | `... --tests '*SingleShotOutcomeMappingTest'` | ❌ Wave 0 |
| SHOT-02 | terminal call skips resolver (forced and auto) | unit | `... --tests '*SingleShotTerminalTest'` | ❌ Wave 0 |
| SHOT-02 | `ModelRequest.singleToolCall` read-back; 6-arg ctor still works | unit | `... --tests '*TranscriptTypesTest'` | ✅ extend |
| SHOT-02 | Anthropic `tool_choice` carries `disable_parallel_tool_use:true` in forced AND reshape shapes; omitted when false; key order unchanged | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest'` | ✅ extend |
| SHOT-02 | Chat `parallel_tool_calls:false` for OpenAI forced and Auto+flag; absent on OpenRouter; absent on o-series | unit | `./gradlew :providers:test --tests '*ChatEncoderTest' --tests '*OpenAiModelRulesTest'` | ✅ extend |
| SHOT-02 | real wire through `SingleShotStrategy` → providers → MockWebServer: Anthropic reshape + flag, OpenAI forced, OpenRouter no key; `Failure(NoToolCall)` → `Escalate` | integration (JVM) | `./gradlew :providers:test --tests '*SingleShotWireTest'` and the two matrix legs | ❌ Wave 0 |
| SHOT-03 | S1-S10 acceptance scenarios | integration (full pipeline, fakes) | `... --tests '*SingleShotAcceptanceTest'` | ❌ Wave 0 |
| (Phase 11 blocker) | limits: `maxTokensPerTurn` pass-through, pre/post ceiling, exactly one turn at `maxIterations` 2 and 6 | unit | `... --tests '*SingleShotLimitsTest'` | ❌ Wave 0 |
| TEL-04 (regression) | no canary in anything SingleShot returns, delivers or prints | unit | `... --tests '*RedactionCanaryTest'` | ✅ extend |

### Sampling Rate
- **Per task commit:** the quick command for the touched test class(es) (seconds).
- **Per wave merge:** `./gradlew :core:check` (if only `:core` touched) or `:core:check :providers:check` (if the flag/encoders touched).
- **Phase gate:** `./gradlew check --offline` green, `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK`, `scripts/verify-repo-hygiene.sh` clean, then `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `core/src/test/.../SingleShotRequestTest.kt`, `SingleShotResolveTest.kt`, `SingleShotOutcomeMappingTest.kt`, `SingleShotTerminalTest.kt`, `SingleShotLimitsTest.kt`, `SingleShotUserTurnTest.kt`, `SingleShotSeamTypesTest.kt`, `SingleShotAcceptanceTest.kt` (+ a shared `SingleShotFixtures.kt` with the neutral entries tool, catalog, resolver, gate lambda)
- [ ] `core/src/testFixtures/.../FakeAiProvider.kt`: add `refusal(usage)` and `toolCalls(vararg ...)` helpers (additive; Phase 9 reuses them)
- [ ] `providers/src/test/.../SingleShotWireTest.kt` (one file, three dialect cases)
- [ ] No framework install needed.

## Security Domain

> `security_enforcement` is enabled (absent = enabled; `security_asvs_level: 1` in config).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no credential handling added; keys flow only via `ProviderRequest.credential`) | existing `Credential` redaction |
| V3 Session Management | no | — |
| V4 Access Control | yes (writes) | the `PreApplyGate`/`CommitCoordinator` only write path; fail-closed gate (GateStep.kt:19-22) |
| V5 Input Validation | yes | model output and transcript are untrusted: resolver validates and re-validates in `apply`; engine passes a `JsonObject` only; unknown tool name → `Escalate(MalformedExtraction)` |
| V6 Cryptography | no | — |
| V7 Error handling / logging | yes | typed reasons, redacted `toString`, no payloads in trace/codes; `RedactionCanaryTest` extension |
| V8 Data protection | yes | transcript/args never in `system`, trace, events or exceptions (TEL-04) |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via transcript steering tool args to a harmful write | Tampering / Elevation | writes only through the gate; resolver validates; held proposals need user confirm; transcript only in the user message |
| Model calls an unknown/unauthorised tool name | Spoofing | check called name against the snapshot's tools; terminal tools are non-mutating by construction (ToolSpec.kt:56) |
| Oversized batch (`entries[]` of thousands) | DoS | `maxTokensPerTurn` bounds output; caps/drop rules app-side (D-02); token ceiling checks |
| Truncated tool arguments committed | Tampering | `MAX_TOKENS`/`PAUSE_TURN` map to `Failed` before any resolve |
| Secret/transcript leakage via logs or exceptions | Information disclosure | counts-and-names-only `toString`, redaction canary, no `println`/logging (detekt + scanner) |
| Retry re-executing a write | Tampering | retry is below the provider seam and re-sends HTTP only (PROV-09; ChatPipelineRetryTest) |

## Recommended Plan Decomposition

Sequenced by module contention (`:providers` compiles `:core`). Each plan ≤3 tasks. Parallelism requires isolated worktrees; in a shared tree serialize Wave 2 (the cost is seconds-to-a-minute of Gradle per plan).

| Plan | Wave | Module | Tasks | Gate |
|------|------|--------|-------|------|
| 07-01 neutral flag + trace plumbing | 1 | `:core` | (1) `ModelRequest.singleToolCall` 7-arg primary + unchanged 6-arg + `toString`, extend `TranscriptTypesTest`; (2) `TraceCode.EXTRA_TOOL_CALLS_DROPPED` + `internal` `CommandSession.recordCode` implemented in `RunSession` + test; (3) `FakeAiProvider.refusal/toolCalls` helpers | `./gradlew :core:check` |
| 07-02 wire encoding | 2 | `:providers` | (1) Anthropic flag in both `tool_choice` shapes + tests; (2) Chat flag + `acceptsParallelToolCalls` o-series gate + OpenRouter-absent assertion + tests; (3) provider-level golden/byte assertions, run all three matrix legs | `./gradlew :providers:check` |
| 07-03 seams and types | 2 | `:core` | (1) `ToolSpecProvider`/`ToolingSnapshot`; (2) `Extraction`/`Resolution`/`OutcomeResolver`; (3) `UserTurnRenderer`/`UserTurnContext`/default + `SingleShotSeamTypesTest`, canary sweep additions. Ends with the A4 sign-off checkpoint | `./gradlew :core:check` |
| 07-04 strategy | 3 | `:core` | (1) `SingleShotStrategy` + Builder + request building + limits (pre/post ceiling, maxTokensPerTurn); (2) outcome mapping + first-call-only + terminal routing + override hooks; (3) Request/Resolve/OutcomeMapping/Terminal/Limits/UserTurn tests | `./gradlew :core:check` |
| 07-05 acceptance | 4 | `:core` | (1) neutral fixtures (`SingleShotFixtures.kt`); (2) S1-S6 tests; (3) S7-S10 + GATE-07 assertion + RedactionCanary routed-run extension | `./gradlew :core:check` |
| 07-06 real-wire tests | 4 | `:providers` | (1) `SingleShotWireTest` Anthropic (reshape + forced), (2) OpenAI + OpenRouter bodies and `Failure(NoToolCall)` → `Escalate`, (3) run on 4.12.0, 5.2.1, 5.5.0 legs | `./gradlew :providers:check` |
| 07-07 phase gate | 5 | both | full `./gradlew check --offline`, `review-api-surface.sh --expect-sealed-complete`, hygiene script, evidence file listing the limits tests (for Phase 11), Gate-1 recorded as N/A (JVM-only) | all green |

07-05 and 07-06 touch different modules; 07-06 depends on 07-02 and 07-04 only. 07-05 depends on 07-04.

## Live leg recommendation

**No live leg in Phase 7.** Reasons: (1) OpenAI's full parameter combination is already proven live (C1, evidence above) and the SingleShot OpenAI body is byte-identical to it; (2) the Anthropic field and its placement are documented explicitly for both `tool` and `auto`; (3) OpenRouter is deliberately not sending the parameter; (4) Phase 10 VER-03 already makes one live single-shot smoke call per provider through the real strategy (A16), which is the correct place to catch wire errors fakes cannot. **Carry to Phase 10:** assert the Anthropic smoke body contains `disable_parallel_tool_use` and returns 200, and (optionally, 2 Haiku calls) that a repeat shows `cache_read_input_tokens > 0` to retire A2. If the orchestrator wants a probe anyway: extend the existing opt-in `liveAnthropicCapture` task (providers/build.gradle.kts, at most 6 requests, `VAE_LIVE_ANTHROPIC=1`) by at most 2 calls via `with-test-keys`, outside `check`.

## Project Constraints (from CLAUDE.md)

- Detekt zero baseline on library modules; no baseline XML; tune rules with a one-line justification, never bank debt.
- `explicitApi()` strict on `:core`/`:providers`; public API grows strictly additively once tagged; `api.txt` is generated only at the v1.0.0 cut (additivity is by discipline now; do not commit an `api.txt`).
- `:core` depends on no other hub and has no HTTP dependency (L7/A7); coroutines-core and serialization-json only.
- Library is domain-free: no note/card/food names; all app knowledge enters through §5.2 seams (CLN-02).
- Secrets: API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`.
- Banned constructs: `runCatching`, `print`/`println`, `System.out/err`, `printStackTrace`, `android.util.Log`, DI annotations, `okhttp3.internal.*`, `mockwebserver3`, `okhttp3.coroutines`; catch specific exceptions (one justified collapse helper exists).
- Contract changes only through §10 amendments via the control plane; never commit §11. Tag cuts are agent-owned under A12.
- GSD workflow: work starts through a GSD command; two-gate UAT where device-verifiable (not applicable here).
- Process: ask the orchestrator (yahir-gsd-control-plane-f2) before the user; A13 stop/reconvene; seam shapes feed the Wave-1 migrations of SB and CT.

## Sources

### Primary (HIGH confidence)
- Repo source read this session (file:line cited inline): CommandStrategy.kt, StrategyOutcome.kt, CommandSession.kt, ToolSpec.kt, TerminalCall.kt, ToolStep.kt, PreApplyGate.kt, CommitCoordinator.kt, ApplyStep.kt, CommandPipeline.kt, TierPolicy.kt, TierWalk.kt, RunSession.kt, BoundModel.kt, ModelRequest.kt, ModelResponse.kt, FailureReason.kt, EscalationReason.kt, BudgetBound.kt, TraceCode.kt, RunRecorder.kt, AnthropicEncoder.kt, AnthropicDecoder.kt, ChatEncoder.kt, ChatDecoder.kt, ChatVendor.kt, OpenAiModelRules.kt, ApiShapeTest.kt, NoHardCodedConstantsTest.kt, scripts/review-api-surface.sh, gradle/invariants.gradle.kts, config/detekt/detekt.yml, core and providers build.gradle.kts, test fixtures.
- `.planning/phases/05-openai-openrouter-transports/evidence/live-chat-capture.txt` and `05-12-SUMMARY.md` (live C1 and R5 results).
- Anthropic docs: parallel-tool-use ("Disable parallel tool use"), define-tools ("Forcing tool use"), prompt-caching invalidation table, strict-tool-use (fetched 2026-10-01).
- OpenAI function-calling guide (`parallel_tool_calls` text), fetched 2026-10-01.

### Secondary (MEDIUM confidence)
- OpenRouter tool-calling guide (`parallel_tool_calls` default and semantics; silent on `require_parameters`).
- SecondBrain `AnthropicAgentLoop.kt:191-194, 377-383` and CalTracker `VoiceLogViewModel.kt` / `LogFoodRequestBuilder.kt:200-206` (read-only port sources: ceiling order, user-turn framing, threshold names).

### Tertiary (LOW confidence)
- langchain-ai/langchain issue 29704 (o3-mini rejects `parallel_tool_calls`) and search-summary reports for o1/o4-mini: community sources, marked A1.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies, versions read from build files.
- Architecture: HIGH, every integration point read in source; the seam shapes are discretionary and flagged for sign-off (A3, A4, A7).
- Wire facts: HIGH for Anthropic, OpenAI (gpt-5.4 family) and OpenRouter (live evidence); MEDIUM for o-series and cache behaviour.
- Pitfalls: HIGH, each tied to an enforcing test or script in the repo.

**Research date:** 2026-10-01
**Valid until:** 2026-10-15 for the model/vendor facts (fast-moving: model ids and forced-tool support change); the in-repo integration findings are valid until Phase 7 plans execute.
