# Phase 2: Core Contract, Pipeline & Commit Seam - Research

**Researched:** 2026-09-30
**Domain:** Pure-Kotlin/JVM library API design (coroutine pipeline + engine-owned commit seam) whose public types are frozen by Metalava at the `v1.0.0` cut
**Confidence:** HIGH for tooling behaviour and port semantics (both verified this session: tooling by experiment in an isolated copy, ports by reading the source). MEDIUM for the handful of design choices listed under Open Questions.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [outcome]:** Separate sealed CommandOutcome (Completed | Failed | Unhandled) whose sealed parent declares ordered executed actions, commits, held proposals and CommandTrace; regular-class leaves _(source: ai-auto)_
- **D-02 [run-close]:** Dedicated closed RunTermination type carrying the executed actions; fired from finally under NonCancellable _(source: ai-auto)_
- **D-03 [taxonomy]:** Non-sealed open interfaces with engine leaves + Other(code); Failed carries httpStatus, provider error.type, requestId, never bodies; engine timeouts via withTimeoutOrNull → TIMEOUT _(source: ai-auto)_
- **D-04 [generics]:** No generics on public pipeline types; opaque app-implemented objects or Any? (context, snapshot, Hold reason) the app downcasts; Completed carries reply: String? + effects _(source: ai-auto)_
- **D-05 [write-path]:** pending.apply() does the write after Admit (SB's Mutate.apply model); CommitSink is the A17 notification/journal seam only _(source: ai-auto)_
- **D-06 [gate]:** fun interface with sealed GateDecision; proposal = ordered list of pending mutations; any non-cancellation throw → Hold; engine records its fail-closed cause as a trace code, never as a HoldReason (GATE-03) _(source: ai-auto)_
- **D-07 [confirm-helper]:** Full SB parity including a post-confirm amend hook (re-snapshot + merge authorization), so SB can move VoiceConfirmGate onto it _(source: ai-auto)_
- **D-08 [batch]:** Sequential, per-item isolation. A failed apply is reported as an `is_error` action EVENT to CommitSink (never a written placeholder row); siblings still apply; CT keeps its skip-and-count "Logged 2 of 3" UX by counting is_error events. Confirmed by caltracker-android-9a. _(source: human)_
- **D-09 [commit-held]:** Skip the gate, same coordinator path and events, idempotent; a second call is a no-op _(source: ai-auto)_
- **D-10 [held-runid]:** commitHeld opens a NEW runId linked via parentRunId with its own onRunClosed ("no commits after close" stays true); fits CT "Confirm all (N)" -> future "Undo all (N)". Confirmed by caltracker-android-9a. _(source: human)_
- **D-11 [sink-event]:** Per-action event = runId + engine-normalized `kind` (committed|held|preview|is_error) + `appOutcomeToken: String?` that is the app's VERBATIM outcome string (byte-identical, incl. SB's held token and full PREVIEW_ string; SB predicates key on it) + toolName + mutating + targetIds: Map<String,String> + opaque snapshot/context (gate may amend before apply) + engine-assigned position, monotonic across turns in dispatch order (identity; not the tool_use id). An is_error call is never also reported as committed. Confirmed by secondbrain-2c (maps 1:1 onto SB ExecutedToolCall). _(source: human)_
- **D-12 [finished-kind]:** ToolStep.Finished carries an explicit kind so preview and is_error mutating calls are streamed (GATE-04) _(source: ai-auto)_
- **D-13 [sink-delivery]:** Suspend + in-order awaited; onRunClosed from finally under NonCancellable so the cancelled path is delivered exactly once _(source: ai-auto)_
- **D-14 [committed-def]:** Any admitted mutation whose apply ran, including errored applies (may have partially written); held/preview don't count; enforced by the pipeline from coordinator counts, not by trusting the strategy _(source: ai-auto)_
- **D-15 [suppressed-escalation]:** `Completed` with a public, non-defaultable `partial: Boolean` field (true here) and the suppressed escalation reason in the trace; README must say partial renders as "did X, couldn't finish", never full success. Orchestrator ruling (option A). _(source: human)_
- **D-16 [held-escalation]:** A HOLD makes a tier terminal (pending proposal = future write, same as a commit for escalation safety). Orchestrator ruling: clarification of A17 / GATE-07, no amendment. _(source: human)_
- **D-17 [dsl]:** Required gate and commitSink (no silent auto-commit defaults), build-time misconfiguration throws, only execute() is never-throw; Phase 3 adds providers/selection as optional members _(source: ai-auto)_
- **D-18 [policy]:** Pipeline pre-check per execute() with static per-tier capability declarations; offlineOnly with no eligible tier → Failed(ProviderUnavailable) with zero strategy executions; Phase 3's ON_DEVICE gate plugs in here _(source: ai-auto)_
- **D-19 [usage-trace]:** Phase 2 defines Usage {inputUncached, cacheRead, cacheWrite, output} with SB's summed total, CommandSession hook, regular-class trace types with runId = command id; cross-provider parity test completes once Phases 4–5 land _(source: ai-auto)_
- **D-20 [listener]:** Non-suspending fun interface on the pipeline coroutine; listener throws caught via try/catch (no runCatching) and never abort the command; events carry ids/codes/counts/tool names only _(source: ai-auto)_
- **D-21 [r1-verdict]:** A17 payload clarifications are now contract text (1b064a0): verbatim appOutcomeToken alongside kind, monotonic position as identity, committed includes errored applies, high-confidence items commit in the original run. _(source: human — orchestrator R1 GO-WITH-CHANGES)_
- **D-22 [a19-clarification]:** A19 (contract 725d8d7): add `Completed.terminalCall: TerminalCall?` (toolName + arguments JsonObject; additive field, no new CommandOutcome variant), typed `Clarification(question, options: List<ClarificationOption(id, label)>)` with opaque app option ids, `ToolSpec.clarification(...)` builder + `TerminalCall.asClarification()`, and `CommandInput.parentRunId: String? = null` linking follow-up runs in the trace and CommitSink/onRunClosed (CORE-08/09). _(source: human — orchestrator, contract A19)_
- **D-23 [sb-gate-timeout]:** No engine-level timeout wraps `PreApplyGate.admit` in suspend mode beyond the app's own (SB's gate auto-holds at 120 s); provider HTTP timeouts are separate. SB R1 item via orchestrator. _(source: human — orchestrator)_

### Claude's Discretion

Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

### Deferred Ideas (OUT OF SCOPE)

See REQUIREMENTS.md v2 / LATER items. (LocalGrammar, PlanThenExecute, Router, `:undo`, `:voice-adapter`, a published `:testing` module, weighted/separate token budgets, Responses API dialect, `prompt_cache_key`, AICore/Nano.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CORE-01 | `commandPipeline { tier(...); selector = Linear; policy = ... }` DSL + `CommandInput(transcript, language, context)` | Type inventory (DSL, `CommandInput`), Pattern 3, Pitfalls 4, 10 |
| CORE-02 | Strategy returns exactly `Completed \| Escalate(reason, carry?) \| NoMatch \| Failed`; ladder climbs/stops/carries | Pattern 3 (tier walk), sealed-closed evidence (E5) |
| CORE-03 | `TierSelector.Linear` (default) and `Fixed(tier)` | Type inventory (abstract class with internal ctor keeps `Router` additive, E6) |
| CORE-04 | `TierPolicy` per-call source; `maxTier` as `StrategyId`; `allowedProviders`; 6 / 60,000 / 4,096; `maxIterations < 2` rejected; offlineOnly loud Failed, zero calls | Pattern 4 (pre-check), port evidence table, detekt MagicNumber note |
| CORE-05 | Never thrown; one collapse helper; cancellation propagates; timeouts are `TIMEOUT` not `NETWORK` | Pattern 1 (verified prototype), Pitfalls 5, 6 |
| CORE-06 | Open `FailureReason` at SB granularity or finer + request id | Taxonomy leaf list, D-03 shape |
| CORE-07 | Open taxonomies, regular classes, `ProviderId` value class with four constants | Metalava experiments E1-E6, API-shape rules, surface-lint recommendation |
| CORE-08 (A19) | `ToolSpec.terminal`, `Completed.terminalCall`, `Clarification`, `ToolSpec.clarification`, `asClarification()` | Open Question O1 (ToolSpec ownership), terminal section, test map |
| CORE-09 (A19) | `CommandInput.parentRunId`; follow-up linked in trace, `CommitSink`, `onRunClosed` | Type inventory, commitHeld child run |
| GATE-01 | One engine-owned prepare → `admit` → apply → `CommitSink` path; opaque context survives; gate may amend | Pattern 2 (coordinator), `Admit(amended)` shape |
| GATE-02 | Suspend mode (`AwaitingConfirmGate`, 120 s, fail-closed) and defer mode (`Hold` → `commitHeld`, amended) | SB `VoiceConfirmGate` port evidence, `AwaitingConfirmGate` spec, test map |
| GATE-03 | Hold reason optional app-typed; held never success; `{"applied":false,"status":"held_for_confirmation"}` | Port evidence (verbatim), engine-cause-as-trace-code rule |
| GATE-04 (A17) | `CommitSink` per action as it happens (committed/held/preview/is_error), `runId` + `ExecutedToolCall`-shaped payload | `ActionEvent` shape, SB `ExecutedToolCall` evidence, NonCancellable delivery (O2) |
| GATE-05 (A17) | `onRunClosed` exactly once on every exit path; one test per path | Pattern 1 lifecycle (verified prototype), Validation Architecture (five path tests) |
| GATE-06 (A17) | Every outcome carries ordered executed list + commits | `CommandOutcome` parent shape, `ExecutedAction.applied` |
| GATE-07 (A17) | Committed/held tier cannot escalate; `Completed(partial = true)`; Budget stays `Failed` | Pattern 4, escalation-safety test matrix |
| TEL-01 | `CommandTrace`: per-tier attempts, reasons, provider/model, normalized `Usage`, latency | Trace/Usage shapes; parity test completes in Phases 4-5 |
| TEL-02 | Optional typed callback receiving the same events live | `PipelineEventListener` (D-20), events-equal-trace test |
</phase_requirements>

## Summary

Phase 2 is almost entirely a design-and-discipline phase. There is no new dependency: everything is plain Kotlin on the `kotlinx-coroutines-core` and `kotlinx-serialization-json` artifacts Phase 1 already put on `:core`'s allow-listed classpath. The hard part is that every public type is about to be frozen by Metalava, so the research below is dominated by what the toolchain will and will not let you change after the tag. I verified that empirically this session by building a throwaway prototype and a Metalava growth experiment in an isolated copy of the repo (nothing in the real tree was touched).

The findings that change how the plan should be written: (1) Metalava **rejects** adding a subclass to a sealed type and rejects adding a defaulted parameter to an existing public constructor (it reports the old constructor as removed), but accepts added overloads, added members, added enum constants, added value-class constants, added members on abstract classes with an internal constructor, and added default-bodied interface methods. So "sealed" must be reserved for the four shapes the contract closes, and every growing input type needs a growth plan (builder or overloads) decided now. (2) A `private companion object { const val … }` in a public class leaks a `public static final` field into `api.txt`; top-level `private const val` does not. (3) detekt's default rules (`TooManyFunctions` = 11, `ReturnCount` = 2, `InstanceOfCheckForException`, `LongMethod` = 60, …) will bite a naive coordinator/pipeline; the layout below is shaped to pass them with exactly one justified `@Suppress`. (4) A leaked inner `TimeoutCancellationException` from app code (a gate that uses `withTimeout`) must be told apart from real cancellation, or `execute()` throws. (5) `onRunClosed` under `withContext(NonCancellable)` in a `finally` demonstrably fires exactly once on cancel, including when the sink suspends.

**Primary recommendation:** Build Phase 2 as five plans in strict dependency order: value types and taxonomy first (frozen shapes, redacted `toString`, surface-lint test), then the pipeline/policy/collapse core with scripted fakes, then the commit coordinator and sink with the five exit-path tests, then `AwaitingConfirmGate` and defer mode, then trace/events/canary, closing with a phase gate that dumps `api.txt` in an isolated copy and reviews it for leaks. Keep the public surface as small as possible: anything `:core`-only stays `internal` (Phase 3 lives in the same module and can promote later, which is additive), and only types that `:providers` or apps must construct or implement are public.

## Architectural Responsibility Map

This is a library, so "tiers" are layers of the engine and its consumers rather than client/server tiers.

| Capability | Primary Layer | Secondary Layer | Rationale |
|------------|---------------|-----------------|-----------|
| Tier walk, escalation, carry, policy pre-check | Engine pipeline (`:core`) | — | Contract §5.1 and D-18 make the pipeline the sole owner; strategies never decide escalation after a write |
| No-escalation-after-commit/hold enforcement | Engine pipeline (reads coordinator counts) | Commit coordinator (counts) | D-14: enforced from coordinator counts, never from what a strategy reports |
| Every write (gate → apply → sink) | Commit coordinator (`:core`, internal) | App seams (`PreApplyGate`, `PendingMutation.apply`, `CommitSink`) | GATE-01: strategies never write; `apply()` is the app's (D-05) |
| Confirm UX state (pending confirmation, resolve) | App / `AwaitingConfirmGate` helper | — | Engine ships the mutex/timeout/fail-closed helper; the app renders the sheet |
| Never-throw collapse, cancellation, timeouts | Engine pipeline (one internal helper) | — | CLAUDE.md: the collapse lives in one helper carrying the repo's only justified `@Suppress` |
| Run close delivery (`onRunClosed`) | Engine pipeline `finally` under `NonCancellable` | App `CommitSink` | D-02/D-13 |
| Domain knowledge (what a write means, targets, snapshots) | Consuming app | — | Library is domain-free; opaque `Any?` slots (D-04) |
| Trace/events | Engine (recorder) | App listener (optional) | TEL-01/02; events carry ids/codes/counts only (D-20) |
| Public-API freeze | Build (Metalava, explicit API) | Surface-lint test | Keystone constraint |

## Standard Stack

No new library enters in this phase. [VERIFIED: gradle/libs.versions.toml:1-28, read this session]

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin (`kotlin.jvm`) | 2.3.20 | Language; `-Xjdk-release=11`, JVM 11 bytecode | Consumer parity (`kotlin = "2.3.20"`, core/build.gradle.kts:21-24) |
| kotlinx-coroutines-core | 1.11.0 | `suspend`, `Mutex`, `CompletableDeferred`, `withTimeoutOrNull`, `NonCancellable`, `StateFlow` | Already `api` on `:core` (core/build.gradle.kts:29) |
| kotlinx-serialization-json | 1.11.0 | `JsonObject` for `TerminalCall.arguments`, `ToolSpec` schemas | Already `api` on `:core` (core/build.gradle.kts:30); no compiler plugin needed (tree API only) |

### Supporting (test only)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit | 4.13.2 | Test framework | All `:core` tests (ecosystem standard; already wired) |
| kotlinx-coroutines-test | 1.11.0 | `runTest`, virtual time for the 120 s gate timeout, cancel tests | Every pipeline/gate test |
| detekt | 1.23.8 (syntax-only) | Zero-baseline gate over main, test, testFixtures | `./gradlew :core:check` |
| Metalava plugin | 0.5.1 | `apiDump`/`apiCheck`, used here only in an isolated copy | Surface review at the phase gate (no `api.txt` is committed before `v1.0.0`) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Regular classes | `data class` | Banned by CORE-07 (copy/componentN freeze, default `toString` leaks secrets) |
| `runCatching` | `kotlin.Result` pipelines | `runCatching` is banned by `scanBannedConstructs` (invariants.gradle.kts:14); use the single guarded helper |
| Flow-based events | `SharedFlow` | Out of scope (REQUIREMENTS "Telemetry as a Flow API"); listener callback only (D-20) |
| Hand-written fakes | MockK / Turbine | Not used anywhere in the ecosystem (STACK.md); fakes are a deliverable |

**Installation:** none. `Version verification:` not applicable (no package added; all versions read from the catalog above).

## Package Legitimacy Audit

No external packages are added or changed in this phase, so there is nothing to run through the legitimacy gate. All artifacts already resolve from the Phase 1 catalog and were exercised by `./gradlew :core:check` in the scratch copy this session.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none new) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
App code (SB / CT / :sample)
  │  pipeline.execute(CommandInput(transcript, language, context, parentRunId?))
  ▼
┌────────────────────────── CommandPipeline.execute ─────────────────────────────┐
│ runId = generator()   startClock                                               │
│ try {                                                                          │
│   policy = TierPolicySource.current()        (guarded; throw → Failed)         │
│   pre-check: maxTier / allowedProviders / offlineOnly ──► no eligible tier?    │
│        └────────────────────────────────► Failed(NoEligibleTier |              │
│                                            ProviderUnavailable(ON_DEVICE))     │
│        (zero strategy executions)                                              │
│   selector.start(ladder, policy) ─► tier i                                     │
│   loop tiers i..: ┌── session = CommandSession(runId, policy, carry, coordinator, recorder)
│                   │   outcome = guarded { strategy.execute(input, session) }   │
│                   │        │                                                   │
│                   │        ├─ strategy reads/writes only via session:         │
│                   │        │    session.submit(ToolStep…) ─► CommitCoordinator │
│                   │        │      gate.admit(proposal)   (guarded; throw→Hold) │
│                   │        │       ├─ Admit(amended?) → for m in list, in order:
│                   │        │       │     m.apply() (guarded) → record action   │
│                   │        │       │     NonCancellable { sink.onAction(evt) } │
│                   │        │       └─ Hold(reason?)  → HeldProposal + HELD evt │
│                   │        ▼                                                   │
│                   │   Completed ─► stop    Failed ─► stop (effects carried)    │
│                   │   Escalate/NoMatch ─► coordinator.appliedCount>0 or        │
│                   │        heldCount>0 ? ─► Completed(partial=true), STOP      │
│                   │        else next tier with carry / Unhandled if none left  │
│ } finally { NonCancellable { sink.onRunClosed(runId, RunTermination) } }       │
│ returns CommandOutcome(executed, commits, held, trace)  — never throws         │
└────────────────────────────────────────────────────────────────────────────────┘
  listener.onEvent(e)   (non-suspending, guarded, ids/codes/counts only)

Defer mode later:  pipeline.commitHeld(held, amended?) ─► NEW runId (parentRunId = held.runId)
                   ─► same coordinator path, gate skipped, own onAction events + own onRunClosed
```

### Recommended Project Structure

Package root is fixed at `io.github.ygaray.voiceactionengine` and Phase 1 placed `:core` under `…core` (CoreModule.kt:1 is `package io.github.ygaray.voiceactionengine.core`, and fixtures live in `…core.testing`). [VERIFIED: core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CoreModule.kt:1]

```
core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/
├── CommandInput.kt, ProviderId.kt, StrategyId.kt, Credential.kt   # identities + input (root: most imported)
├── pipeline/     # CommandPipeline, PipelineBuilder (DSL), TierSelector, TierPolicy(+Source), CommandOutcome, TierWalk (internal)
├── strategy/     # CommandStrategy, StrategyOutcome, StrategyResult, CommandSession, StrategyCapabilities, ToolSpec, TerminalCall, Clarification
├── commit/       # ToolStep, PendingMutation, MutationResult, PreApplyGate, GateDecision, CommitProposal, CommitSink, ActionEvent, ActionKind, HeldProposal, RunTermination, AwaitingConfirmGate, CommitCoordinator (internal)
├── failure/      # FailureReason(+leaves), FailureDetails, EscalationReason(+leaves)
├── telemetry/    # CommandTrace, TierAttempt, TurnRecord, Usage, PipelineEvent(+leaves), PipelineEventListener
└── internal/     # Guarded.kt (the ONE broad-catch helper), RunRecorder, Clock/IdGenerator plumbing
core/src/testFixtures/kotlin/.../core/testing/   # existing ScriptedResponses, RecordingSink, NoNetworkGuard + new ScriptedStrategy, scripted/recording gates and sinks, FakeClock
core/src/test/kotlin/.../core/                   # one test class per requirement cluster (see Validation Architecture)
```
Delete the `internal object CoreModule` placeholder once real classes exist (its own KDoc says it only exists so the module produces class files). Packages are free to move until the tag; after the tag a move is a removal. Keep the number of packages small so the README's wiring snippets need few imports.

### Public versus internal inventory (the keystone decision table)

Rule of thumb verified by experiment: **anything `:core`-only is `internal`** (Phase 3 is in the same module and can promote to public later, which Metalava treats as additive). **Anything `:providers`/`:keystore`/apps must construct, implement or call is public** (`internal` does not cross modules).

| Type | Shape | Constructed by | Notes |
|------|-------|----------------|-------|
| `ProviderId`, `StrategyId` | `@JvmInline value class`, companion constants | app/engine | `ProviderId.ANTHROPIC/OPENAI/OPENROUTER/ON_DEVICE` (CORE-07). Metalava prints value-class ctors as `@KotlinOnly` (verified) |
| `Credential` | final regular class, redacted `toString` | `:keystore`/apps | Not in Phase 2's ID list, but ROADMAP Phase 6 "Depends on Phase 2 (`ProviderId` + `Credential`)" so it is an implicit deliverable. Keep minimal; Phase 3 adds the typed `CredentialSource` (its D-02) |
| `CommandInput` | regular class, **public ctor**, all defaults present now | app | `(transcript, language: String? = null, context: Map<String, Any?> = emptyMap(), parentRunId: String? = null)`; `toString` prints lengths only |
| `TierPolicy` | regular class with **builder** (`TierPolicy { maxIterations = 8 }`) and `internal` primary ctor | app | Many optional fields that will grow; builder is the growth-safe form (E1) |
| `TierPolicySource` | `fun interface` (`suspend fun current(): TierPolicy`) + `TierPolicySource.fixed(policy)` | app | Read once per `execute()` |
| `TierSelector` | `abstract class` with **internal ctor**; nested `Linear` object, `Fixed(tier: StrategyId)` class | app picks a variant | NOT sealed: v1.1 adds `Router`; Metalava allows adding a subclass here (E6) |
| `CommandStrategy` | interface: `id`, `capabilities` (default body), `suspend execute(input, session)` | app/engine | Adding members later must carry default bodies (E5) |
| `StrategyOutcome` | **sealed** (`Completed`, `Escalate`, `NoMatch`, `Failed`) | strategies (public ctors) | Contract-closed at four variants; Metalava enforces it (E4). Nested so `StrategyOutcome.Completed` does not clash with `CommandOutcome.Completed` |
| `CommandSession` | `abstract class`, **internal ctor** | engine only | Public members: `runId`, `parentRunId`, `policy`, `carry`, `submit(...)`, `recordTurn(...)`. Abstract class (not interface) so new members are additive |
| `CommandOutcome` | **sealed** class: `Completed`, `Failed`, `Unhandled`; leaves have **internal ctors** | engine only | Parent exposes `runId`, `parentRunId`, `executed`, `commits`, `held`, `trace`. `Completed(reply, terminalCall, partial)` — `partial` has no default (D-15) |
| `RunTermination` | **sealed**, engine-constructed, carries `runId`, `parentRunId`, `executed`, `trace` | engine only | Must be complete at the tag (E4). Four leaves cover the five A17 exit paths: `Done`, `Cancelled`, `Failed(reason)` (budget exceeded and provider error are distinct `reason`s), `Exhausted` |
| `ToolStep` | **sealed** (`Finished`, `Mutation`) | app/strategy (public ctors) | Closed by design (SB `ToolStep` is exactly two variants) |
| `PendingMutation` | interface; abstract: `toolName`, `suspend apply()`; default-bodied: `targetIds`, `context` | app | New optional members must have default bodies |
| `GateDecision` | **sealed** (`Admit(amended)`, `Hold(reason, appOutcomeToken)`) | app gate | Two variants, contract-closed (A2) |
| `PreApplyGate` | `fun interface` `suspend fun admit(proposal: CommitProposal): GateDecision` | app | Wrap the list in `CommitProposal` so hints can be added later without changing the signature (D-06 says "ordered list of pending mutations"; the wrapper carries exactly that) |
| `CommitSink` | interface: `suspend onAction(event: ActionEvent)`, `suspend onRunClosed(runId, termination)` | app | Single event objects, not positional params, so data growth never changes a signature |
| `ActionEvent`, `ExecutedAction` | regular classes, internal ctors | engine | Fields per D-11 plus `applied: Boolean` (see below) |
| `ActionKind`, `FinishedKind`, `BudgetBound` | `@JvmInline value class` with constants | app/engine | Not `enum`: Metalava accepts new enum constants but consumers' exhaustive `when` would break silently (E3). Four kinds today, forward-compatible via `else` |
| `FailureReason`, `EscalationReason` | non-sealed `interface` (`code: String`) + public-ctor leaves + `Other(code)` | `:providers` constructs leaves | Public ctors are mandatory: `:providers` maps HTTP to these |
| `FailureDetails` | regular class (`httpStatus`, `providerErrorType`, `requestId`) | engine/providers | Never a body (D-03) |
| `Usage` | regular class `(inputUncached, cacheRead, cacheWrite, output)`, `total` = sum | providers | SB's sum semantics (port evidence) |
| `CommandTrace`, `TierAttempt`, `TurnRecord` | regular classes, internal ctors | engine | `runId` = command id (D-19) |
| `PipelineEvent` | non-sealed `interface` + leaf classes | engine | Leaves for every TEL-02 event defined now, including `ProviderCall` and `CacheNotEngaged` (emitted by Phases 3-4) |
| `PipelineEventListener` | non-suspending `fun interface` | app | D-20 |
| `ToolSpec`, `TerminalCall`, `Clarification`, `ClarificationOption` | regular classes; `ToolSpec` validates `terminal && mutating` at construction | app/engine | See Open Question O1 (ownership vs Phase 3) |
| `HeldProposal` | regular class, internal ctor, atomic consumed flag | engine | Exposes `runId`, `mutations`, `reason` |
| `AwaitingConfirmGate` | regular class implementing `PreApplyGate` | app constructs | Ports `VoiceConfirmGate` |

### Pattern 1: One guarded helper, cancellation-correct lifecycle (verified prototype)

**What:** All broad catching lives in one internal function. `CancellationException` is rethrown only if the coroutine is really cancelled; a `TimeoutCancellationException` that leaks out of app code while the run's own job is still active becomes `Failed(Timeout)`. `LinkageError` (`NoSuchMethodError`/`NoClassDefFoundError`, the A1 failure mode of 4.12 bytecode on a different OkHttp) is collapsed too; plain `Exception` is the last catch.
**When to use:** the top-level collapse, and (via a small inline variant) around every app callback: gate, `apply()`, sink, listener, policy source.
**Example** [VERIFIED: compiled, detekt-clean, 6 tests green in a scratch copy of this repo, `./gradlew :core:check`]:
```kotlin
// Scratch prototype, not committed. Catch order matters: detekt InstanceOfCheckForException forbids `e is X` inside a general catch.
@Suppress("TooGenericExceptionCaught")
internal suspend fun collapse(block: suspend () -> CommandOutcome): CommandOutcome = try {
    block()
} catch (e: TimeoutCancellationException) {
    if (!currentCoroutineContext().isActive) throw e      // really cancelled: propagate
    CommandOutcome.Failed(Timeout(), emptyList())          // leaked inner withTimeout: a failure, not a cancel
} catch (e: CancellationException) {
    throw e
} catch (e: LinkageError) {
    CommandOutcome.Failed(Other(e::class.java.simpleName), emptyList())
} catch (e: Exception) {
    CommandOutcome.Failed(Other(e::class.java.simpleName), emptyList())
}

public suspend fun execute(runId: String): CommandOutcome {
    var termination: RunTermination? = null
    try {
        val result = collapse { withTimeoutOrNull(timeoutMs) { body() } ?: CommandOutcome.Failed(Timeout(), emptyList()) }
        termination = RunTermination.Done(result)
        return result
    } finally {
        val closing = termination ?: RunTermination.Cancelled
        withContext(NonCancellable) { sink.onRunClosed(runId, closing) }   // fires once, even on cancel, even if the sink suspends
    }
}
```
Verified behaviours (each a passing test in the scratch copy): a cancelled run delivered `onRunClosed` exactly once with the cancelled termination while the sink itself called `delay(10)` inside `NonCancellable`; an engine `withTimeoutOrNull` produced `Timeout`, not a cancellation; a throwing body collapsed to `Failed(IllegalStateException)` and still closed once; a body throwing `CancellationException` propagated and still closed once; a leaked `withTimeout` inside the body became `Failed(Timeout)`; a `NoSuchMethodError` collapsed. Production refinement (not prototyped): in the `finally`, classify an escaping non-Linkage `Error` as a failed termination rather than `Cancelled` (`termination ?: if (currentCoroutineContext().job.isCancelled) Cancelled else Failed(internal)`).

### Pattern 2: Commit coordinator sequence (the only write path)

```
submit(proposal: CommitProposal): DispatchResult        // internal CommitCoordinator, reached via CommandSession.submit
  decision = guardedGate { gate.admit(proposal) }        // non-cancellation throw → Hold, trace code GATE_ERROR (never a HoldReason)
  Admit(amended):  for m in (amended ?: proposal.mutations), sequentially:
      outcome = guarded { m.apply() }                    // throw → is_error, applied = true (may have partially written)
      action  = ExecutedAction(position = next++, kind = COMMITTED|IS_ERROR, applied = true, appOutcomeToken = outcome.token, …)
      record(action); withContext(NonCancellable) { sink.onAction(ActionEvent(runId, action, context)) }
  Hold(reason, token): for m in proposal.mutations:
      record ExecutedAction(kind = HELD, applied = false); deliver onAction(HELD); collect HeldProposal(runId, mutations, reason)
  returns content for the model: held → the byte-exact held JSON; committed → m.resultForModel
```
Invariants to encode as tests: nothing is recorded for a call whose **gate** was cancelled (SB contract, see port evidence); an is_error action is never also committed; positions are monotonic per run in dispatch order; a sink exception after a successful apply is caught and traced, never re-applies and never hides the action; a `Finished` step of kind preview/rejected-mutating is reported to the sink as `preview`/`is_error` without touching the gate (D-12), while a plain read is not reported (executed list = mutating calls only, Phase 9 D-05).

### Pattern 3: Tier walk and the escalation guard

`appliedCount` (applies that ran, including errored ones: D-14) and `heldCount` are read from the coordinator after each tier. `Escalate`/`NoMatch` with `appliedCount + heldCount > 0` becomes `Completed(reply = null, partial = true)` with the suppressed reason recorded on the `TierAttempt`; `Failed` after writes stays `Failed` carrying the effects (`BudgetExceeded` included). Preview-only and rejected-only tiers may still escalate, and their executed actions accumulate into the final outcome. `Fixed(tier)` starts at that `StrategyId`; if policy (`maxTier`) excludes it the result is `Failed(NoEligibleTier)`. Ladder exhausted → `Unhandled` (carries the last escalation reason).

### Pattern 4: Policy pre-check before any strategy runs

Per D-18: read `TierPolicySource.current()` once, then drop tiers by (a) `maxTier` position, (b) declared provider demand versus `allowedProviders`, (c) `offlineOnly` (only tiers that need no provider, or an available ON_DEVICE provider, survive). No survivor under `offlineOnly` → `Failed(ProviderUnavailable(ON_DEVICE, "offline_unavailable"))` after **zero** strategy executions. Phase 3 has not defined its ON_DEVICE availability type yet, so Phase 2 exposes an `internal` hook defaulting to "unavailable"; Phase 3 sits in the same module and plugs its gate in without a public change. Declare tier demand statically (`StrategyCapabilities`, default-bodied on `CommandStrategy`).

### Pattern 5: API-shape rules the plan must bake into every task (all verified, E1-E6)

1. Growing **input** classes (`TierPolicy`, `CommandInput`, `ToolStep.Finished`, `MutationResult`): either builder + internal ctor, or a public ctor whose growth is done by **adding overloads**, never by adding a defaulted parameter (E1).
2. Engine-**produced** classes (outcomes, trace, events, `ActionEvent`, `HeldProposal`, `CommandSession`): `internal` constructors, public getters; new getters are additive.
3. Seam interfaces implemented by apps: new members must have default bodies. Kotlin 2.3.20 here compiles them as real JVM `default` methods (E5).
4. Sealed only for: `StrategyOutcome`, `CommandOutcome`, `RunTermination`, `GateDecision`, `ToolStep`. Anything else non-sealed.
5. Constants: top-level `private const val`, never `private companion object { const val }` in a public class (E2).
6. Event-object parameters on seam methods (`onAction(event)`), not positional lists.
7. No `data class`, no public enums for growing sets, no default-parameter growth on public methods.

### Anti-Patterns to Avoid
- **Strategy that writes directly** (bypasses the gate and CT's weak-match confirm); only `ToolStep` values and `session.submit` exist.
- **Trusting the strategy's own "I committed" report** for the escalation guard; read coordinator counts (D-14).
- **A second broad catch** anywhere for "just this callback"; route through the one helper or detekt `TooGenericExceptionCaught` plus the one-`@Suppress` budget fails.
- **Reusing a `HoldReason` type for engine fail-closed causes** (GATE-03): the engine's cause is a trace code only.
- **Polling the `StateFlow` inside the engine**: the flow is for the app's UI; the gate awaits a `CompletableDeferred`.
- **Comments citing planning ids**: `scanBannedConstructs` fails any comment containing `T-<n>-<n>`, `WR-<n>` or `Phase <n> D-<n>` (invariants.gradle.kts:31). Strip them when porting SB comments.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Serialized confirmation wait | custom lock/queue | `kotlinx.coroutines.sync.Mutex` + `CompletableDeferred` + `withTimeoutOrNull` (SB's exact recipe) | Cancellation and timeout semantics already correct and proven in SB |
| Additive-API enforcement | hand diff of public symbols | Metalava `apiDump`/`apiCheck` in an isolated copy, plus the surface-lint test | Phase 1 wired it; it reports sealed growth and removed constructors (E1, E4) |
| Virtual-time timeout/cancel tests | `Thread.sleep`, real delays | `runTest` + `advanceTimeBy` + `Job.cancel()` | Verified working with `NonCancellable` cleanup |
| Clarification schema | string-built JSON | `buildJsonObject` (insertion-ordered, byte-stable) | Cache-prefix determinism downstream |
| Secret redaction | ad-hoc string scrubbing | A fixed `toString` per public type that prints ids/lengths/class names only (SB pattern), plus a canary test | Exact SB precedent (`PendingConfirmation.toString`) |
| ID generation | counters | `java.util.UUID.randomUUID()` behind an injectable `() -> String` | Deterministic in tests, collision-safe |

**Key insight:** in this domain the expensive mistakes are about what you can no longer change, not about algorithms. Every custom solution above is either already provided by coroutines or is a design rule enforced by the existing gates.

## Runtime State Inventory

Omitted: greenfield phase (new types only; no rename, refactor or migration). No stored data, service config, OS registration, secrets or build artifacts carry a string this phase changes.

## Common Pitfalls

### Pitfall 1: `private companion object { const val }` leaks into the frozen API
**What goes wrong:** The scratch dump showed `field public static final long DEFAULT_TIMEOUT_MS = 60000L` on a public builder class whose constant sat in a `private companion object`. After the tag it can never be removed. [VERIFIED: scratch `core/api.txt` from `metalavaGenerateSignature`]
**How to avoid:** Use top-level `private const val` (not listed in `api.txt`, verified) or an `internal` companion (verified hidden). The default limits (6 / 60,000 / 4,096) must still be named constants because detekt `MagicNumber` is active in main (only `-1, 0, 1, 2` ignored by default).
**Warning signs:** A `field public static final` line in the reviewed `api.txt` that is not `INSTANCE`/`Companion`.

### Pitfall 2: Adding a default parameter later is a binary break
**What goes wrong:** Changing `class A(a: Int)` to `A(a: Int, b: Int = 0)` makes Metalava report `Binary breaking change: Removed constructor …A(int) [RemovedMethod]`. [VERIFIED: scratch `:core:apiCheck`]
**How to avoid:** Decide the growth mechanism per type now (Pattern 5). Adding an overload (`constructor(a, b)`) and adding a property with an internal setter both passed the same check.

### Pitfall 3: A sealed hierarchy can never gain a variant
**What goes wrong:** Adding a subclass to a sealed interface fails `apiCheck` with `Added a subclass to a sealed interface that can be exhaustively matched [AddedSubclassToSealedClass]` [VERIFIED]. So `RunTermination` (D-02 "closed") and `CommandOutcome` must be complete now, and any future exit path has to be expressible as a new `reason` on an existing variant.
**How to avoid:** Use exactly the four-leaf `RunTermination` above. Keep taxonomies (`FailureReason`, events, kinds) open.

### Pitfall 4: detekt defaults will reject a naive coordinator
**What goes wrong:** Active defaults that matter here (extracted from the detekt-core-1.23.8 jar's `default-detekt-config.yml` this session): `TooManyFunctions` thresholds all 11 (files, classes, interfaces, objects), `LongMethod` 60, `CyclomaticComplexMethod` 15, `NestedBlockDepth` 4, `ReturnCount` max 2, `ThrowsCount` max 2, `LongParameterList` function threshold 6 (the repo config relaxes only the constructor side: constructorThreshold 8, ignoreDefaultParameters true, detekt.yml:9-11), `MaxLineLength` 120, `UseCheckOrError`, `UseRequire`, `MatchingDeclarationName`, `InstanceOfCheckForException`, `SwallowedException`, `TooGenericExceptionCaught` (tests excluded), `ThrowingExceptionFromFinally`. The prototype hit `MaxLineLength` and `InstanceOfCheckForException` on the first run.
**How to avoid:** Split `CommitCoordinator` by responsibility (gate step, apply step, recorder, held registry) and keep files under 11 top-level functions. Use `require`/`check` for build-time misconfiguration (D-17). Use separate `catch` clauses instead of `e is X`. Exactly **one** `@Suppress("TooGenericExceptionCaught")` (the guarded helper); the prototype needed no `SwallowedException` suppression because the exception class name is used.

### Pitfall 5: Leaked inner `TimeoutCancellationException`
**What goes wrong:** An app gate that uses `withTimeout` throws a `TimeoutCancellationException` (a `CancellationException`). A plain "always rethrow cancellation" turns it into a thrown `CancellationException` out of `execute()`, violating never-throw.
**How to avoid:** Pattern 1's `isActive` discrimination, with a named test. Reconciles with CORE-05's "CancellationException always propagates" because real cancellation still propagates (Open Question O3 asks the orchestrator to confirm this reading).

### Pitfall 6: Cancellation mid-apply and mid-delivery loses a commit
**What goes wrong:** If `apply()` has written and the coroutine is cancelled before `sink.onAction` runs (or while it runs), the undo journal never hears about a real write, which is exactly what A17 exists to prevent. SB's contract is the opposite for the **gate**: a cancel while the gate is suspended leaves no record (port evidence).
**How to avoid:** Deliver `onAction` under `withContext(NonCancellable)` after the apply outcome is known; record nothing for a gate that was cancelled; if `apply()` itself is cancelled, record an `is_error`, `applied = true` action (it may have partially written) and deliver it through the same NonCancellable path. See Open Question O2.

### Pitfall 7: Hold then Escalate
**What goes wrong:** A tier that only held (nothing committed) and then returns `Escalate` would let tier 2 write what the held proposal will later write via `commitHeld`. D-16 forbids it.
**How to avoid:** The guard counts `heldCount` as well as `appliedCount`. Assumption A2 covers the outcome shape.

### Pitfall 8: Escaping `Error` subclasses break "never thrown"
**What goes wrong:** `catch (e: Exception)` does not catch `NoSuchMethodError`/`NoClassDefFoundError`, which is precisely how 4.12-compiled bytecode fails on a different OkHttp (A1). The pipeline would throw.
**How to avoid:** Catch `LinkageError` explicitly (verified detekt-clean and test-green). Do not catch `OutOfMemoryError`.

### Pitfall 9: `commitHeld` double-tap
**What goes wrong:** Two concurrent `commitHeld` calls on one `HeldProposal` (CT double-tap) write twice.
**How to avoid:** An atomic compare-and-set consumed flag plus a stored `CompletableDeferred<CommandOutcome>`; the second call returns the first run's outcome without re-applying or re-emitting events. A cancelled first call leaves the handle consumed (a partial write may have happened, so a retry could duplicate).

### Pitfall 10: Redaction regressions in new types
**What goes wrong:** `Completed.reply` is model text, `carry`, `context`, `Hold.reason`, `snapshot` and `TerminalCall.arguments` are user content. A default `toString` or a string template in an event leaks them.
**How to avoid:** Every public type holding any of these overrides `toString` to ids, codes, lengths and simple class names (SB prints only `id` and `subject::class.simpleName`). A canary test sweeps trace, events and every `toString`.

### Pitfall 11: Scanner only scans `src/main`
`scanBannedConstructs` globs `src/main` only (invariants.gradle.kts:168). `src/test` and `src/testFixtures` are still linted by detekt (root build.gradle.kts:36-38 lists all three source dirs) but `runCatching`/`println` are not scanner-enforced there; keep fixtures clean anyway because Phases 4-9 reuse them.

### Pitfall 12: Name collisions inside one package
`Completed` (strategy vs command outcome) and `Timeout` (failure leaf) are easy to mis-import. Nest the two `Completed` types under their parents and prefix the leaf (`TimeoutFailure` or keep it nested under a `Failures` holder). Decide before the first file is written.

## Code Examples

### Port evidence (verbatim, read this session) that the examples and constants rely on

| Fact | Source (verbatim) |
|------|-------------------|
| Held `tool_result` bytes | `const val HELD_FOR_CONFIRMATION_CONTENT = """{"applied":false,"status":"held_for_confirmation"}"""` (SecondBrain `MutationGate.kt:40`) |
| SB held outcome token | `const val MUTATION_HELD_OUTCOME = "held"` (`MutationGate.kt:33`) |
| SB gate shape | `fun interface MutationGate {` / `suspend fun admit(toolName: String, input: JsonObject?): MutationGateDecision` (`MutationGate.kt:28-29`) |
| Loop defaults | `const val MAX_ITERATIONS = 6` (`AnthropicAgentLoop.kt:421`), `const val MAX_TOKENS_PER_TURN = 4096` (`:429`), `const val MAX_UTTERANCE_TOKENS = 60_000` (`:432`) |
| SB token sum | `parsed.usage.inputTokens + parsed.usage.outputTokens + parsed.usage.cacheCreationInputTokens + parsed.usage.cacheReadInputTokens` (`:284-285`) |
| Confirm timeout | `const val CONFIRM_TIMEOUT_MS = 120_000L` (`VoiceConfirmGate.kt:147`) |
| Mutex held for the whole window | `private suspend fun awaitConfirmation(subject: ConfirmSubject): MutationGateDecision = awaitMutex.withLock {` (`:132`) then `val confirmed = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() }` (`:138`) |
| Fail-closed | `if (confirmed == true) MutationGateDecision.Admit else MutationGateDecision.Hold` (`:139`) |
| Pending confirmation redaction | `override fun toString(): String = "PendingConfirmation(id=$id, subject=${subject::class.simpleName})"` (`:34`) |
| Two-variant step | `sealed interface ToolStep {` / `data class Finished(val execution: ToolExecution) : ToolStep` / `class Mutate(val apply: suspend () -> ToolExecution) : ToolStep` (`AnthropicToolRegistry.kt:27-32`) |
| Gate called only for Mutate, admit/Hold/apply | `when (gate.admit(spec.name, input)) { MutationGateDecision.Hold -> { held = true … } MutationGateDecision.Admit -> step.apply() }` (`AnthropicToolRegistry.kt:~236-242`) |
| Executed-call shape the sink event maps onto | `data class ExecutedToolCall(val toolUseId: String, val toolName: String, val isError: Boolean, val mutating: Boolean, val targetIds: Map<String, String>, val outcome: String?, val preMutationSnapshot: PreMutationSnapshot? = null,)` (`AgentLoopResult.kt:56-64`) |
| Effects on every variant | `sealed interface AgentLoopResult { val executedTools: List<ExecutedToolCall>` … `Done`, `Unavailable`, `BudgetExceeded` (`AgentLoopResult.kt:86-111`) |
| CT skip-and-count | `var failedCount = 0` (`VoiceLogViewModel.kt:839`), `failedCount++` for an unwritable row (`:860`) and in the `catch (_: Exception)` per-row isolation (`:869`), surfaced as `SuccessBatch.failedCount` (`:880`) |
| Package root | `package io.github.ygaray.voiceactionengine.core` (`CoreModule.kt:1`) |

### Held content lives in one engine constant
```kotlin
// Engine-owned; must equal SB's bytes exactly (GATE-03). Keep it internal and expose via DispatchResult.contentForModel.
internal const val HELD_FOR_CONFIRMATION = """{"applied":false,"status":"held_for_confirmation"}"""
```
A golden test asserts byte equality with the literal in the port-evidence table.

### Suspend-mode gate skeleton (port of the SB recipe; structure only)
```kotlin
public class AwaitingConfirmGate(/* needsConfirmation policy, timeout default 120_000 ms, optional post-confirm amend hook */) : PreApplyGate {
    private val awaitMutex = Mutex()
    public val pending: StateFlow<PendingConfirmation?> /* cleared in finally */
    public fun resolve(confirmationId: Long, confirmed: Boolean) { /* stale id or second call is a no-op */ }
    override suspend fun admit(proposal: CommitProposal): GateDecision = /* policy null → Admit; else
        awaitMutex.withLock { withTimeoutOrNull(timeout) { deferred.await() } → confirmed == true ? Admit(amendHook?.invoke(…)) : Hold } */
}
```
Cancellation rule copied from SB: a `CancellationException` while pending is never converted into a decision.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Kotlin interface bodies compiled to `DefaultImpls` only | Real JVM `default` methods (+ `DefaultImpls` for compat) | Kotlin 2.2+, effective in 2.3.20 [VERIFIED: `javap` on a scratch interface showed `public default … two(…)`] | New default-bodied members on app-implemented seams are binary-safe for already-compiled implementers |
| `runCatching` for never-throw | Explicit catch order in one helper | Project rule (BLD-04) | `runCatching` swallows `CancellationException`; banned |
| BCV / `abiValidation` | Metalava on all modules | Project decision (Phase 1) | Reports sealed growth and removed constructors, but does **not** flag added enum constants (E3) |

**Deprecated/outdated:** data classes for growing public types (CORE-07); `private companion` constants in public classes (Pitfall 1).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ProviderId` wire strings are lowercase (`"anthropic"`, `"openai"`, `"openrouter"`, `"on_device"`) | Inventory | Low; `.value` is a label, but Phase 4 logging/trace codes may key on it |
| A2 | A tier that only **held** and then escalates ends as `Completed(reply = null, partial = true)` with the held proposals listed | Pattern 3, Pitfall 7 | Medium: D-15/D-16 rule on committed-then-escalate and on hold-is-terminal, but do not spell out the hold-then-escalate outcome. Confirm with the orchestrator |
| A3 | Sink/listener exceptions after a successful apply are caught, recorded as a trace/event code and never change the outcome or re-apply | Pattern 2 | Medium: CONTEXT covers only listener throws (D-20); the sink rule is an extension |
| A4 | `commitHeld` second call returns the first run's outcome (idempotent) rather than an empty/typed "already resolved" value | Pitfall 9 | Low: D-09 says "a second call is a no-op"; CT ignores the second result |
| A5 | `TierPolicy` construction rejects `maxIterations < 2` with `IllegalArgumentException`, and a throwing `TierPolicySource` becomes a `Failed` at `execute()` | Test map | Low: success criterion says "rejected"; the exact mechanism is unspecified |
| A6 | `Hold` carries an optional `appOutcomeToken` so SB can keep its `"held"` token verbatim; engine-fail-closed holds carry null and consumers map by `kind` | Inventory | Medium: D-11 requires the token to be the app's verbatim string "incl. SB's held token" but does not say which seam supplies it on a hold |
| A7 | `asClarification()` returns null (never throws) for a non-conforming argument object; empty `options` is permitted | Test map | Low: A19 does not specify malformed handling |
| A8 | `clock` (monotonic millis) and `runIdGenerator` are public optional DSL properties so `:providers` tests (another module) can inject them | Inventory | Low |
| A9 | No engine-level cap on `CommitSink` delay (a slow sink delays cancellation under `NonCancellable`) | Pitfall 6 | Low: sinks are local DB journals |

## Open Questions

1. **O1. Who owns `ToolSpec`: Phase 2 or Phase 3?**
   - What we know: D-22 (Phase 2) requires `ToolSpec.clarification(...)` and CORE-08 requires "declaring it mutating fails at build time", but Phase 3's own D-11 says "`ToolSpec` carries `terminal: Boolean = false`", and ARCHITECTURE placed `ToolSpec` in the Phase 3 transcript package. Phase 9 D-03 adds the `mutating` flag.
   - What's unclear: the same field is claimed by two phases.
   - Recommendation: Phase 2 ships a **minimal** `ToolSpec` (`name`, `description`, `inputSchema: JsonObject`, `mutating`, `terminal`, construction check `terminal && mutating` rejected) plus `ToolSpec.clarification(name, description)`; Phase 3 adds `strict`, cache fields, etc. additively. Nothing is frozen until Phase 11, so this costs nothing. Tell the orchestrator so Phase 3's D-11 is marked satisfied.

2. **O2. Exact cancellation semantics around a write.**
   - What we know: D-13 covers `onRunClosed` only; A17's purpose is that nothing committed before a cancel is lost; SB records nothing when the gate is cancelled.
   - What's unclear: cancel during `apply()` and during `onAction` delivery.
   - Recommendation: deliver `onAction` under `NonCancellable`; a cancel mid-`apply()` is recorded as `is_error`, `applied = true`; a cancel during the gate records nothing. Add one named test for each of the three.

3. **O3. Confirm the reading of CORE-05 "CancellationException always propagates".**
   - Recommendation: propagate whenever the run's coroutine is actually cancelled; map a leaked inner `TimeoutCancellationException` to `Failed(Timeout)` when it is not. Ask the orchestrator/SB only if they object (low risk; the alternative throws out of `execute()`).

4. **O4. Do consumers need to construct `CommandOutcome` leaves in their own tests?**
   - What we know: published test fixtures are out of scope (BLD-09, LATER-04), and internal leaf ctors keep growth free.
   - Recommendation: keep leaf ctors internal; consumers build outcomes by running a real pipeline with a two-line custom `CommandStrategy` (its outcome types have public ctors). Raise with SB/CT at R2 if they push back.

5. **O5. How does the gate carry SB's post-confirm snapshot and merge authorization?**
   - Recommendation: via `Admit(amended = listOf(pendingWithSnapshotAndAuthorization))`, since `PendingMutation` is the app's own object (replaces SB's `MutationDispatchContext` coroutine-context hack). The `ActionEvent` exposes the amended mutation's `context`. Validate with secondbrain-2c at R2, not now.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Gradle build and tests | ✓ | OpenJDK 17.0.19 (`java -version`) | — |
| Gradle wrapper | everything | ✓ | 9.4.1 via `./gradlew` (ran `:core:check`, `:core:apiDump` in 6-8 s warm, including with `--offline`) | — |
| Gradle caches (detekt, Metalava, coroutines-test) | offline runs | ✓ | in `~/.gradle/caches` | normal online resolution |
| Network / devices / keys / JitPack | — | not needed | — | Phase 2 is JVM-only; no LLM, no HTTP, no device, no keys |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`, virtual time); hand-written fakes |
| Config file | none beyond `core/build.gradle.kts` and `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :core:test --tests '<class>' -q` |
| Full suite command | `./gradlew :core:check -q` (module), `./gradlew check` (phase gate; Phase 1 recorded ~45 s warm) |

### Phase Requirements → Test Map

All tests live in `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/` and use scripted fake strategies, `NoNetworkGuard.during { … }` where "zero provider calls" is asserted, and `RecordingSink` for ordering logs. File names are suggestions; the assertions are the contract.

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CORE-01 | DSL builds; duplicate `StrategyId`, zero tiers, missing gate, missing sink all rejected at build time | unit | `./gradlew :core:test --tests '*PipelineBuilderTest'` | ❌ Wave 0 |
| CORE-02 | Escalate/NoMatch climb with `carry` delivered verbatim; Completed/Failed stop; last-tier escalate → `Unhandled` | unit | `--tests '*TierWalkTest'` | ❌ Wave 0 |
| CORE-03 | `Linear` starts at tier 0; `Fixed(id)` starts mid-ladder; unknown id / excluded by `maxTier` → `Failed(NoEligibleTier)` | unit | `--tests '*TierSelectorTest'` | ❌ Wave 0 |
| CORE-04 | Defaults 6 / 60_000 / 4_096; `maxIterations = 1` rejected; source read on every `execute()` (counting source); `maxTier` skips later tiers; `allowedProviders` skips a disjoint tier; `offlineOnly` with no eligible tier → `Failed(ProviderUnavailable(ON_DEVICE, …))` and the strategy execution count stays 0 under `NoNetworkGuard`; internal ON_DEVICE hook = available lets a tier run | unit | `--tests '*TierPolicyTest'` | ❌ Wave 0 |
| CORE-05 | Strategy throws → typed `Failed`; gate throws → `Hold` + trace code; policy source throws → `Failed`; leaked inner `withTimeout` → `Failed(Timeout)`; engine deadline → `Timeout` ≠ `Network`; `NoSuchMethodError` collapsed; caller cancel propagates (assertThrows on join) | unit | `--tests '*NeverThrowTest'` | ❌ Wave 0 |
| CORE-06 | Every required leaf exists (auth, billing, rate-limit, overloaded, timeout, network, malformed-response, malformed-tool-args, refusal, max-tokens, no-tool-call, budget-exceeded, tool-failure, not-configured, provider-unavailable, plus model-unsupported, model-not-found, pause-turn, context-window-exceeded, unknown-stop, http-error, `Other`); codes unique; `FailureDetails` carries status/error type/request id and no body; `Failed.toString()` prints none of them | unit | `--tests '*FailureTaxonomyTest'` | ❌ Wave 0 |
| CORE-07 | `ProviderId` is a value class with exactly the four constants; **surface lint**: no public `data class` (no `copy`/`componentN`), no public enum in growing sets, sealed allow-list == {`StrategyOutcome`, `CommandOutcome`, `RunTermination`, `GateDecision`, `ToolStep`}, no stray `public static final` fields | unit + script | `--tests '*ApiShapeTest'` plus `scripts/review-api-surface.sh` (new; isolated-copy `apiDump`, same pattern as `scripts/verify-api-dump.sh`) | ❌ Wave 0 |
| CORE-08 | `ToolSpec(terminal = true, mutating = true)` rejected; `ToolSpec.clarification` yields terminal, non-mutating, strict-eligible schema; `asClarification()` matrix (valid, missing question, non-string id/label, empty options); scripted strategy: commit then `Completed(terminalCall)` → `reply == null`, commit carried, `partial == false`, no further tier runs; terminal alongside a held call carries the held list | unit | `--tests '*TerminalCallTest'` | ❌ Wave 0 |
| CORE-09 | `parentRunId` appears on the trace, every `ActionEvent`, `RunTermination`, and the `commitHeld` child run | unit | `--tests '*ParentRunIdTest'` | ❌ Wave 0 |
| GATE-01 | Order gate → apply → sink; strategy has no write API other than `session.submit`; `Admit(amended)` replaces the list that applies; opaque `context` object survives prepare → admit → apply → event by identity | unit | `--tests '*CommitPathTest'` | ❌ Wave 0 |
| GATE-02 | Suspend: confirm → Admit; decline/dismiss → Hold; 120 s virtual-time timeout → Hold; policy error → Hold; cancel while pending propagates and leaves no state; stale id / double resolve no-op; two concurrent admits serialize; `pending` flow publishes then clears; amend hook result used. Defer: `Hold` then `commitHeld` (gate not invoked), amended list, idempotent second and concurrent calls | unit | `--tests '*AwaitingConfirmGateTest' --tests '*DeferModeTest'` | ❌ Wave 0 |
| GATE-03 | Held result content equals the verbatim SB literal; held action never in `commits` and never `Completed` without being listed in `held`; opaque reason round-trips by identity; engine fail-closed cause is a trace code, never a `HoldReason` | unit | `--tests '*HeldReportingTest'` | ❌ Wave 0 |
| GATE-04 | `ActionEvent` fields per D-11 for committed / held / preview / is_error; `appOutcomeToken` byte-identical (including a `PREVIEW_…` string); position monotonic across turns; is_error never also committed; events arrive as each action happens (interleave with a strategy step log); batch of 3 with a failing middle item → `is_error` event, siblings applied, three events | unit | `--tests '*ActionEventTest' --tests '*BatchIsolationTest'` | ❌ Wave 0 |
| GATE-05 | **Five path tests, one per A17 exit path, each asserting `onRunClosed` exactly once, the expected `RunTermination` leaf, and the executed list so far:** `doneExitClosesOnce`, `cancelledExitClosesOnce` (cancel while a strategy is suspended; sink suspends inside `NonCancellable`), `budgetExceededExitClosesOnce` (strategy returns `Failed(BudgetExceeded)`), `providerErrorExitClosesOnce` (`Failed(Auth/Network/…)` with `FailureDetails`), `escalationExhaustedExitClosesOnce` (all tiers Escalate → `Unhandled`). Plus: every `onAction` precedes `onRunClosed`; no `onAction` after close; cancel after a commit includes it; throwing sink `onRunClosed` is caught; `commitHeld` closes its own run once | unit | `--tests '*RunClosedPathsTest'` | ❌ Wave 0 |
| GATE-06 | Executed list contains all four kinds in dispatch order on `Completed`, `Failed` and `Unhandled`; `commits` is the committed subset; `applied` is true for committed and errored applies, false for held/preview/rejected; list is cumulative across tiers | unit | `--tests '*ExecutedListTest'` | ❌ Wave 0 |
| GATE-07 | Commit then Escalate → `Completed(partial = true)`, tier 2 execution count 0, apply count unchanged (no duplicate write), suppressed reason on the `TierAttempt`; same for NoMatch; errored apply counts; hold then Escalate is terminal (A2); preview-only and rejected-only tiers may escalate; `BudgetExceeded` after a commit stays `Failed` with the commit carried; `partial` is a constructor parameter without a default (reflection: no `DefaultConstructorMarker` constructor) | unit | `--tests '*EscalationSafetyTest'` | ❌ Wave 0 |
| TEL-01 | Trace has per-tier attempts, escalation reasons, provider/model slots, `Usage` fields, latency from an injected fake clock; `Usage.total` = four-way sum (SB semantics) with a worked Anthropic-style and OpenAI-style input; `runId` = command id | unit | `--tests '*TraceTest'` | ❌ Wave 0 |
| TEL-02 | Listener receives tier started/finished, provider call, commit, hold, run closed, cache-not-engaged, live and in order; listener events == trace contents; a throwing listener never aborts the command; events carry no payload | unit | `--tests '*EventsTest'` | ❌ Wave 0 |
| (TEL-04 Phase-2 slice) | Canary strings in transcript, context, carry, reply, tool args, hold reason, snapshot never appear in any `toString`, trace, event or failure message | unit | `--tests '*RedactionCanaryTest'` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the touched test class via `./gradlew :core:test --tests '<class>' -q` (a few seconds) plus `./gradlew :core:detekt -q` when main sources changed.
- **Per wave merge:** `./gradlew :core:check :providers:check -q` (detekt, scanner, structural gates, all `:core` tests; `:providers` confirms the testFixtures dependency still compiles).
- **Phase gate:** `./gradlew check` green, `scripts/verify-negative-controls.sh` and `scripts/verify-repo-hygiene.sh` still green (they must not regress), `scripts/verify-api-dump.sh` still OK, and the new surface review reviewed by a human reading `api.txt` from the isolated copy. No `api.txt` is committed (BLD-05: dumps land only at the `v1.0.0` cut; the compat task stays `onlyIf` api.txt exists, root build.gradle.kts:46-48).

### Wave 0 Gaps
- [ ] `core/src/testFixtures/…/testing/ScriptedStrategy.kt` — scripted fake `CommandStrategy` (built on the existing `ScriptedResponses`), able to call `session.submit(...)`, suspend, throw, and count executions
- [ ] `…/testing/` recording gate and sink (`RecordingCommitSink`, scripted/admit-all/hold-all gates), `RecordingEventListener`, `FakeClock`/fixed id generator (public, explicit-visibility; linted by detekt like main, scanned by nothing else)
- [ ] All test classes named above
- [ ] `scripts/review-api-surface.sh` — isolated-copy `apiDump` + grep for `sealed` allow-list, `copy(`/`component1`, unexpected `public static final` fields
- [ ] Framework install: none

## Security Domain

`security_enforcement` is enabled (config: `security_enforcement: true`, `security_asvs_level: 1`).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No credentials handled beyond the minimal redacted `Credential` type |
| V3 Session Management | no | — |
| V4 Access Control | yes (internal) | Fail-closed gate: only an explicit Admit applies a write; engine faults become Hold |
| V5 Input Validation | yes | Build-time validation (`require`/`check`), `maxIterations >= 2`, `ToolSpec` terminal/mutating check, tolerant `asClarification()` parse |
| V6 Cryptography | no | None in this phase (`:keystore` is Phase 6) |
| V7 Error Handling and Logging | yes | No logging in library code (`android.util.Log`/`println` banned); redacted `toString`; failures carry status and error type only |
| V8 Data Protection | yes | Transcript, context, carry, reply, args, snapshot never reach trace, events, exceptions or `toString` |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret or user content leaking via `toString`, trace, event or exception message | Information disclosure | Hand-written `toString` on every public type, events limited to ids/codes/counts/tool names, exception classes recorded by simple name only, canary test |
| Gate throws/times out and the write proceeds (fail-open) | Elevation / Tampering | Any non-cancellation throw → `Hold`; engine cause recorded as a trace code; GATE-03 test |
| Duplicate write through escalation or a double `commitHeld` | Tampering | Coordinator-count guard (applied + held), atomic consumed flag on `HeldProposal` |
| Undo-journal gap after cancel | Repudiation | `onAction` under `NonCancellable`; `onRunClosed` exactly-once from `finally` |
| Silent failure (swallowed sink/listener/gate errors) | Repudiation | Each guarded failure becomes a trace code and an event; never discarded silently |
| Unbounded token/iteration policy | DoS | `maxIterations >= 2`, token ceiling in `TierPolicy` with defaults |

## Recommended Plan / Wave Breakdown

Phase 1's six plans ran as six sequential waves and finished with a verified phase gate; the same shape fits here. Every plan carries its own tests (there is no separate "tests" wave) and every plan ends with `./gradlew :core:check` green.

| Plan | Wave | Scope | Requirements | Depends on |
|------|------|-------|--------------|------------|
| 02-01 Contract types and taxonomy | 1 | Identities (`ProviderId`, `StrategyId`, `Credential`), `CommandInput` (+`parentRunId`), `FailureReason`/`EscalationReason`/`FailureDetails` + leaves, `Usage`, `ActionKind`/`FinishedKind`/`BudgetBound`, minimal `ToolSpec`/`TerminalCall`/`Clarification` (O1), `TierPolicy` builder + `TierPolicySource`; redacted `toString`; `ApiShapeTest`; delete `CoreModule` | CORE-06, CORE-07, CORE-08 (types), CORE-09 (field), CORE-04 (policy type) | — |
| 02-02 Pipeline, policy, collapse | 2 | `CommandStrategy`/`StrategyOutcome`/`CommandSession`/`StrategyCapabilities`, `CommandOutcome`/`RunTermination` shells, `commandPipeline {}` DSL, `TierSelector`, tier walk with carry, policy pre-check, the single guarded helper, deadlines, cancellation, run lifecycle `finally`; `ScriptedStrategy` fixture | CORE-01..05, GATE-05 (skeleton), part of CORE-08 (terminal pass-through) | 01 |
| 02-03 Commit coordinator, sink, held | 3 | `ToolStep`, `PendingMutation`, `MutationResult`, `GateDecision`/`PreApplyGate`/`CommitProposal`, internal `CommitCoordinator`, `CommitSink`/`ActionEvent`, `HeldProposal`, `commitHeld` (new linked run), batch isolation, escalation guard wired to coordinator counts, executed/commits/held on every outcome; the five exit-path tests; recording gate/sink fixtures | GATE-01, 03..07, CORE-09 | 02 |
| 02-04 AwaitingConfirmGate and defer mode | 4 | Port of `VoiceConfirmGate` mechanics (mutex, 120 s, `StateFlow`, `resolve`, policy, post-confirm amend hook) and the suspend/defer end-to-end tests | GATE-02 | 03 |
| 02-05 Trace, events, canary | 4 (parallel with 04 if worktrees allow; disjoint files) | `CommandTrace`/`TierAttempt`/`TurnRecord` recording, `PipelineEvent` leaves, `PipelineEventListener` dispatch through the guarded helper, `CommandSession.recordTurn`, `Usage` math, `FakeClock`, redaction canary | TEL-01, TEL-02 | 03 |
| 02-06 Phase gate | 5 | Surface review (`review-api-surface.sh`, human read of the isolated `api.txt`), README/KDoc hand-off notes (below), full `./gradlew check`, rerun negative controls, hygiene and api-dump scripts, SUMMARY | all | 04, 05 |

Hand-off notes that belong in KDoc now and the Phase 10 README later (capture them in the Phase 2 SUMMARY so they are not lost): `Completed.partial = true` renders as "did X, couldn't finish", never full success (D-15, VER-04); every open taxonomy needs an `else`; `PendingMutation.apply()` must re-validate because a deferred commit runs against possibly-changed state; the system prompt must tell the model that `held_for_confirmation` means "not done, don't retry"; dispatch is sequential because the confirm mutex is held for the whole wait; `HeldProposal` is in-memory only.

## Project Constraints (from CLAUDE.md)

Extracted from `.claude/CLAUDE.md` (project) and the user's global profile:
- `:core` depends on no other hub and has no HTTP dependency; its classpath is enforced by `verifyCoreDependencyAllowlist` (any new `:core` dependency must extend the allowlist deliberately).
- detekt zero baseline on library modules, plain `detekt` task only, `buildUponDefaultConfig = true`, `maxIssues: 0`, no baseline file; tune rules with a one-line justification rather than bank debt.
- `explicitApi()` Strict on `:core`; growing public types are regular classes, not `data class`; sealed only for contract-closed shapes.
- The never-throw collapse lives in **one** internal helper that carries the repo's only justified `@Suppress`; everywhere else catches specific types. `runCatching`, `println`, `printStackTrace`, `android.util.Log`, DI annotations and `okhttp3.internal.*` are banned.
- Secrets policy: API keys, transcripts, tool args and results never reach logs, telemetry, exceptions or `toString()`.
- Domain-free: no note/card/food names in library code; all app knowledge enters through seams (opaque `Any?` slots).
- API evolution: strictly additive after the tag; contract changes only via §10 amendments through the orchestrator; never edit `CROSS-REPO-SCOPE-CONTRACT.md` or §11.
- Quality bar: two-gate UAT where device-verifiable (none here), most of `:core` JVM-tested.
- User profile directives relevant to execution: explain root cause with every fix, keep explanations brief, never re-break working behavior, flag any state change.
- GSD workflow: start work through a GSD command; do not commit here (research only); always `git pull --rebase` before committing because peers commit contract changes to this repo (STATE.md).

## Sources

### Primary (HIGH confidence)
- Repo files read this session: `.planning/phases/02-…/02-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`, `.planning/v1.0-DECISION-MAP.md` (§ Phase 2 and dependents), `.planning/research/{SUMMARY,ARCHITECTURE,PITFALLS}.md`, `.planning/cross-repo/{HANDOFF,RECONVENE-BRIEF}.md`, `CROSS-REPO-SCOPE-CONTRACT.md` (§5, §6.2, A2, A6, A17, A19, E1, E4-E7, §11)
- Phase 1 output: `core/build.gradle.kts`, root `build.gradle.kts`, `gradle/invariants.gradle.kts`, `gradle/libs.versions.toml`, `config/detekt/detekt.yml`, `core/src/**` (main, test, testFixtures), `scripts/verify-api-dump.sh`, `01-VALIDATION.md`, `01-06-SUMMARY.md`
- Port sources (read-only): SecondBrain `core/agent/{MutationGate,VoiceConfirmGate,AgentLoopResult,MutationDispatchContext,PreMutationSnapshot,AnthropicAgentLoop,AnthropicToolRegistry}.kt`; CalTracker `ui/voice/VoiceLogViewModel.kt`
- detekt 1.23.8 `default-detekt-config.yml` extracted from the detekt-core jar in the local Gradle cache
- Empirical runs in an isolated copy of the repo (session scratchpad, outside the repo): `:core:check`, `:core:apiDump`, `:core:apiCheck` over prototype pipeline/outcome/gate/sink shapes and growth scenarios E1-E6 below

### Secondary (MEDIUM confidence)
- Project research files (ARCHITECTURE/PITFALLS) for design intent; they predate Phase 1 and the A19/R1 changes and were reconciled against CONTEXT.md

### Tertiary (LOW confidence)
- Kotlin coroutines documentation pages could not be fetched this session (the two URLs tried returned empty/404); coroutine semantics are therefore backed by the passing prototype tests, not by a quoted doc passage.

**Experiment log (for the planner; all in the scratch copy):**
- E1: adding a defaulted parameter to a public constructor → Metalava `RemovedMethod` (binary break); adding an overload, or a property with an internal setter → pass.
- E2: `private companion object { const val }` leaks a `public static final` field; top-level `private const` and `internal` companion do not.
- E3: adding an enum constant, a value-class companion constant, or a member to a class passes Metalava (but breaks consumers' exhaustive `when`).
- E4: adding a subclass to a sealed interface → `AddedSubclassToSealedClass` error.
- E5: adding a default-bodied method to an interface passes; `javap` shows a real JVM `default` method.
- E6: adding a subclass to an abstract class with an internal constructor passes.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no change; every version read from the catalog and exercised by a green build.
- Architecture: HIGH for mechanics (prototype-verified lifecycle, cancellation, timeout, collapse, Metalava behaviour); MEDIUM for type naming and the choices in Open Questions O1-O5.
- Pitfalls: HIGH — the top five were reproduced, not recalled.

**Research date:** 2026-09-30
**Valid until:** 2026-10-30 (toolchain pins are frozen for the milestone; re-check only if Kotlin, Metalava or detekt versions move)
