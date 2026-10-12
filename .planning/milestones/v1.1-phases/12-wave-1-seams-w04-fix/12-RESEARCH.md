# Phase 12: Wave-1 Seams & W04 Fix - Research

**Researched:** 2026-10-05
**Domain:** Kotlin/JVM + Android library API evolution (additive seams on `:core` / `:keystore`), OpenAI Chat Completions wire rules and error classification (`:providers`), Anthropic capability table, doc/gate maintenance, one bounded live device smoke
**Confidence:** HIGH (code anchors read this session; Anthropic and OpenAI facts fetched from official docs 2026-10-05). Two items are MEDIUM/ASSUMED and listed in the Assumptions Log.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 [onfailed]:** The `else` arm only. Binding refusals stay loud per the BoundModel contract (an app's broad `else -> Escalate` must not lift an on-device/NotConfigured refusal to the cloud). A throwing hook → strategy_error like the other hooks. _(source: ai-auto)_
- **D-02 [reasoning-wire]:** Byte-identical in v1.1, KDoc defines OFF as "no engine-added reasoning request", never "the model does not think" (omitting reasoning_effort 400s gpt-5.4+/gpt-6 tool calls; thinking:disabled 400s Sonnet 5.5). Value class with internal ctor, OFF/PROVIDER_DEFAULT on the companion; explicit 7-arg ModelRequest ctor kept; no default args anywhere (also Extraction); no api.txt edits in Phase 12. _(source: ai-auto)_
- **D-03 [capped]:** The first option (code-backed; meets SB's condition as written). SUMMARY.md:59's "offline doesn't auto-set" is wrong for the code. _(source: ai-auto)_
- **D-04 [call-id]:** Internal overload + HeldProposal field; one SingleShot call stamps all its actions. Correction to the brief: it is internal plumbing, not a pass-through at SingleShot:141/AgenticDispatch:131. `heldRunId` and other extras stay out of Phase 12. _(source: ai-auto)_
  - **Consumer condition (binding):** Orchestrator accepted the brief correction: providerCallId travels via internal `submit()` plumbing (answers file, correction b).
- **D-05 [w04-wire]:** Targeted deny-list (an allow-table regresses unlisted working ids to 400; existing goldens pin "none" for gpt-6/-sol/-luna). Plus the narrow classifier backstop: 400 + param=reasoning_effort + code=unsupported_value → ModelUnsupported (after quota), replayed via MockWebServer on all three OkHttp legs. Confirm whether gpt-5.x-pro/-codex take tools on Chat at all (maybe refuse pre-call instead). _(source: ai-auto)_
  - **Consumer condition (binding):** Orchestrator accepted that W04 has two internal causes (answers file, correction c).
- **D-06 [prov16]:** Reuse the leg, judge it, and first retarget the runner's PHASE_DIR/decision path off the archived Phase 10 dir. TESTER only, never overlapping Phase 13. _(source: ai-auto)_
- **D-07 [keyaccess]:** Plain interface + opt-in on interface and the one new ctor + negative-compile proof. Correction to the brief/REQUIREMENTS SEAM-07 ("fun interface" can't compile; collapsing to one member breaks read-never-creates) — tell the orchestrator. _(source: ai-auto)_
  - **Consumer condition (binding):** Orchestrator accepted the correction: KeyAccess is a plain `interface`, still opt-in `@DelicateKeyAccess` (answers file, correction a); REQUIREMENTS SEAM-07 wording to follow.
- **D-08 [docs]:** The gated form (verify-docs-coverage.sh C06/C07/C20 would go red otherwise). _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEAM-01 | `SingleShotStrategy.Builder.onFailed` (else arm only) | `SingleShotOutcomes.kt` `failureOutcome` / `OutcomeHooks`; D-01; section "Code map" row SEAM-01 |
| SEAM-02 | `ReasoningMode` value class, `ModelRequest.reasoning`, `Builder.reasoning` on SingleShot + AgenticLoop, wire byte-identical | `ModelRequest.kt` ctor chain; two `ModelRequest(` call sites; encoders need no change; golden pins; Pitfall 2 |
| SEAM-03 | `claude-sonnet-5` row, 1,024 min prefix, forced choice allowed | `AnthropicModels.kt`; official docs verified 2026-10-05 (below) |
| SEAM-04 | `TierAttempt.carryIn` | `RunRecorder.tierStarted` / `TierBook` / `TierWalk.runTier` |
| SEAM-05 | `Unhandled.cappedByPolicy` | `PolicyPreCheck.check` -> `Ladder` -> `TierWalk.run`; D-03 |
| SEAM-06 | `Extraction.callId`, `ExecutedAction.providerCallId` | internal `submit` overload plumbing chain (Code map); D-04 |
| SEAM-07 | public `KeyAccess` (plain interface) + opt-in 3-arg `ApiKeyStore` ctor | `KeyAccess.kt`, `ApiKeyStore.kt`; D-07; negative-compile proof design |
| PROV-14 | Responses-only / `none`-rejecting ids never sent `reasoning_effort: "none"` | `OpenAiModelRules.wireRules`; id table verified from OpenAI docs |
| PROV-15 | 400 `param=reasoning_effort` + `code=unsupported_value` -> `ModelUnsupported` | `ChatErrors.kt` `refine`; MockWebServer replay on 3 legs |
| PROV-16 | Live `:sample` smoke `gpt-6-astra` -> typed `ModelUnsupported` | `LegCatalog`/`LegRunner` RESPONSES_PROBE; `run-sample-gate1.sh` retarget; Open Questions 1-3 |
| DOC-01 | 3 v1.0.1 wiring stumbles + Haiku/OpenAI cache note | `wiring-stumbles.txt`; INTEGRATION §7, §10, §5/6, Notes; docs-coverage gate constraints |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- `:core` depends on no other hub and no HTTP (L7, A7); OkHttp compile floor 4.12, CI green on 4.12.x and 5.x (A1) — never use `okhttp3.internal.*`, `mockwebserver3`, `QueueDispatcher`.
- Public API grows strictly additively (§11 rule 2). Kotlin explicit API mode on all three library modules; detekt zero baseline (plain `detekt` task only, no baseline file, tune rules with a one-line justification).
- Domain-free: no app domain names (and no "food"/"card" words in docs, gate C21; no `SecondBrain`/`CalTracker` or planning ids such as `T-01-02`, `WR-07`, `Phase 12 D-03` in source comments — scanner rule "app planning id in comment").
- Secrets: API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`.
- No `runCatching`, `println`, `printStackTrace`, `System.out`, DI annotations, `android.util.Log` in library code (`gradle/invariants.gradle.kts` scanner).
- Contract changes only via §10 amendments through the control plane; never commit §11 ledger rows; tag cuts are not part of Phase 12 (Phase 20).
- GSD workflow enforcement: file edits happen inside GSD plan execution.
- User global rules that bite here: test keys only via `push-test-key` / `with-test-keys` (never read/paste key files); TESTER only (`…-s22-ultra-2`, USB serial `R5CT10XNKQN`), never the personal phone; always `adb -s <serial>`; read `~/.claude/context/devices/common.md` before any device step.

## Summary

Phase 12 is eleven small, mostly independent changes that fall into four code regions, so the plan can be sliced by file ownership: (1) `:providers` — the W04 wire/classifier fix and the `claude-sonnet-5` row (no `:core` files); (2) `:core` strategy/request seams — `ReasoningMode`, `onFailed`, `Extraction.callId`; (3) `:core` outcome/telemetry facts — `carryIn`, `cappedByPolicy`, `providerCallId` plumbing through the commit path; (4) `:keystore` `KeyAccess` opt-in plus docs and the live smoke. Everything is internal-constructor or additive, so Metalava compat against the committed v1.0.1 `api.txt` passes without edits to `api.txt` (decision D-02: no `api.txt` edits in this phase; they are regenerated at the v1.1.0 cut).

The two research flags are resolved from official docs fetched 2026-10-05. `claude-sonnet-5` has a **1,024-token** minimum cacheable prefix (not 512 — that is `claude-sonnet-5-5`) and is **not** on Anthropic's forced-tool-use rejection list, so its row is `caching = EXPLICIT_BREAKPOINTS`, `minCacheablePrefixTokens = 1_024`, forced choice left at the default (allowed). For W04, OpenAI documents that `gpt-6-astra` and `gpt-6.1-sol` reject `none` and take function tools only on the Responses API; `gpt-5.5-pro` and `gpt-5.3-codex` reject `none` **and Chat Completions entirely**. The current `wireRules` sends `"none"` to every `gpt-6*` id and to every `gpt-5.N` with N>=4, which wrongly includes the Responses-only pair and the `-pro` variants. The deny-list fix is: direct Responses-only ids get no effort (checked before `GPT_6_FAMILY`), `-pro`/`-codex` are excluded from the `gpt-5.N>=4` "none" rule, everything else is unchanged so existing goldens stay green.

One cross-phase conflict needs the orchestrator before execution: Phase 19's D-13 says the live `gpt-6-astra` smoke "must succeed with no 400 on `reasoning_effort`", but on Chat Completions that model cannot take tools at all, so the correct and only reachable live result is the typed `ModelUnsupported` that PROV-16 asks for (Open Question 1).

**Primary recommendation:** Slice into ~7 plans by file ownership (providers W04+row; `ReasoningMode`+`onFailed`; `carryIn`+`cappedByPolicy`; `providerCallId` plumbing; `KeyAccess`; docs; live smoke), sequence the three plans that touch `SingleShotStrategy.kt` / `CommitCoordinator.kt`, run a free host-side "Probe C" of the post-fix wire against the real API before touching the device, and keep every new public constructor free of default arguments.

## Architectural Responsibility Map

This is a library, so "tier" means module/layer.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `ReasoningMode`, `ModelRequest.reasoning`, `Builder.reasoning` | `:core` (transcript + strategy) | `:providers` encoders (read-only, ignore in v1.1) | Neutral request type lives in `:core`; encoders stay byte-identical (D-02) |
| `onFailed` hook | `:core` SingleShot strategy | — | Pure strategy-outcome mapping, no provider knowledge |
| `claude-sonnet-5` row | `:providers` (anthropic) | `:core` `ModelCapabilities` (existing type) | Per-provider internal capability table |
| W04 wire rule | `:providers` (chat) `OpenAiModelRules` | — | Dialect detail kept out of public capabilities |
| W04 classifier | `:providers` (chat) `ChatErrors.kt` | `:core` `FailureReason.ModelUnsupported` (existing) | Error body parsing is transport-side |
| `carryIn`, `cappedByPolicy` | `:core` pipeline + telemetry | — | Engine facts derived from the walk/ladder, never from a strategy's claim |
| `providerCallId` | `:core` (strategy -> session -> commit ledger) | `:core` `HeldProposal` | One write path (`CommitCoordinator`); internal overload only |
| `KeyAccess` / opt-in ctor | `:keystore` | `:sample` (negative-compile consumer + doc snippet) | Opt-in is only provable from a *different module* than the one that owns the marker |
| Doc fixes + coverage gate | repo root docs + `scripts/verify-docs-coverage.sh` | `:sample` `DocSnippetsTest` (region source of truth) | Every kotlin fence must equal a test region (C06) |
| Live W04 smoke | `:sample` leg runner on TESTER | host script `run-sample-gate1.sh`, `push-test-key` | Real Android runtime + real OkHttp 5.x variant |

## Standard Stack

No new libraries. All work uses the pinned stack from CLAUDE.md (Kotlin 2.3.20, AGP 9.2.1, kotlinx.serialization 1.11.0, coroutines 1.11.0, JUnit 4.13.2, legacy `okhttp3.mockwebserver` on the 4.12.0 / 5.2.1 / 5.5.0 legs, detekt 1.23.8 plain task, Metalava 0.5.1). [VERIFIED: .claude/CLAUDE.md Technology Stack]

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Targeted deny-list in `wireRules` | Positive allow-table of `none`-accepting ids | Rejected by D-05: regresses unlisted working ids to a 400 |
| `ReasoningMode` as a value class | Plain final class with internal ctor + companion constants | Same Kotlin-visible API; D-02 locks value class. Only fall back (via a new discuss pass) if the synthetic-ctor check in Pitfall 2 fails |
| Module-wide `-opt-in` for `:keystore` internals | `@OptIn` per use site / `@file:OptIn` | Either works; module-wide is fewer edits but the positive test inside `:keystore` then proves nothing about opt-in, so the negative proof MUST live in `:sample` |

**Package Legitimacy Audit:** not required — Phase 12 installs no external packages. **Packages removed due to [SLOP]:** none. **Flagged [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
 app code
   |  Builder.onFailed / Builder.reasoning / KeyAccess fake / Extraction.callId
   v
 CommandPipeline.execute --> PolicyPreCheck.check(policy)
   |                           |-- tierSkipped(TIER_SKIPPED_POLICY) per dropped tier
   |                           '-- Ladder(eligible, ..., cappedByPolicy = eligible.size < strategies.size)   [SEAM-05]
   v
 TierWalk.runTier(strategy)
   |-- recorder.tierStarted(id, carryIn = carry != null)                                                    [SEAM-04]
   |-- RunSession(carry) --> strategy.execute
   |      SingleShot: request(reasoning=Builder.reasoning) --> BoundModel.complete --> ModelResult
   |         Failure(NoToolCall)->onNoToolCall | Failure(Refusal)->onRefusal | Failure(other)->onFailed       [SEAM-01]
   |         Success -> first ToolCall(id,name,args) -> Extraction(name,args,callId=id) -> resolver           [SEAM-06]
   |         submit(step, providerCallId = call.id)  (internal overload)
   |      AgenticLoop: per call: Extraction(..., call.id); submit(step, call.id)
   v
 CommitCoordinator.submit(step, callId) --> gate --> applyAll / hold(HeldProposal.providerCallId) / finished
   |-- ActionDetails(providerCallId) --> ActionLedger.record --> ExecutedAction.providerCallId              [SEAM-06]
   '-- commitHeld --> applyWithoutGate(mutations, held.providerCallId)
   v
 Outcome: Completed / Failed / Unhandled(cappedByPolicy)  +  trace.attempts[i].carryIn

 :providers (wire):
   ModelRequest --> encodeChatRequest --> ChatModels.wireRules --> OpenAiModelRules.wireRules
        direct Responses-only id --> reasoning_effort omitted                                                [PROV-14]
   HTTP 400 --> parseChatError --> refine(): quota > context_length > /v1/responses marker >
        (400 && param=reasoning_effort && code=unsupported_value) --> ModelUnsupported                       [PROV-15]
```

### Code map (exact files; all paths under repo root)

Abbreviations: `C` = `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`, `P` = `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers`, `K` = `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore`.

| Req | Files to change | What changes |
|-----|-----------------|--------------|
| SEAM-01 | `C/strategy/singleshot/SingleShotOutcomes.kt` (`OutcomeHooks`, `failureOutcome`), `C/strategy/singleshot/SingleShotStrategy.kt` (`hooks = OutcomeHooks(settings.onNoToolCall, settings.onRefusal)`, `Builder`) | Add third hook `onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome`; `else ->` arm of `failureOutcome` calls it; Builder default `{ reason, details -> StrategyOutcome.Failed(reason, details) }`. Binding refusals return from `withModel` before `complete`, so they never reach it. Hook is not separately guarded: a throw lands in `TierWalk.executeGuarded` -> `strategy_error` |
| SEAM-02 | new `C/transcript/ReasoningMode.kt`; `C/transcript/ModelRequest.kt`; `SingleShotStrategy.kt` (`request()`), `C/strategy/agentic/AgenticLoopStrategy.kt` (`AgenticRun.request()` + `Builder`) | Value class, internal ctor, `OFF`/`PROVIDER_DEFAULT` on companion. `ModelRequest` primary ctor becomes 8-arg (`..., singleToolCall, reasoning`); the existing 7-arg, 6-arg, 4-arg and 3-arg ctors stay as secondary ctors delegating with `ReasoningMode.OFF` (the 7-arg JVM signature must survive). `Builder.reasoning: ReasoningMode = ReasoningMode.OFF` on both builders (a property default on a Builder is fine; the "no default args" rule is about constructors). Encoders (`P/anthropic/AnthropicEncoder.kt`, `P/chat/ChatEncoder.kt`) are NOT changed: bytes must stay identical |
| SEAM-03 | `P/anthropic/AnthropicModels.kt` | Add `private const val SONNET_5 = "claude-sonnet-5"`, `SONNET_5_MIN_CACHEABLE_PREFIX_TOKENS = 1_024`, a dedicated `ModelCapabilities { caching = EXPLICIT_BREAKPOINTS; minCacheablePrefixTokens = ... }` and a `when` arm. Update the KDoc "checked on" date. Exact-id match only (see Pitfall 4) |
| SEAM-04 | `C/telemetry/CommandTrace.kt` (`TierAttempt`), `C/telemetry/RunRecorder.kt` (`tierStarted`, `TierBook.start/close`, `flushInFlight`), `C/pipeline/TierWalk.kt` (`runTier`) | `TierAttempt(…, carryIn: Boolean, …)` internal ctor + public `val carryIn`; recorder stores it at `tierStarted(strategy, carryIn)`; `TierWalk.runTier` passes `carry != null`. Keep a default arg on the internal `TierAttempt` ctor (it has `turns = emptyList()`): `ApiShapeTest.STUB_EXCEPTIONS` lists `TierAttempt`, and `everyDocumentedStubExceptionStillDeclaresAStub` fails if the stub disappears |
| SEAM-05 | `C/pipeline/PolicyPreCheck.kt` (`Ladder`, `check`), `C/pipeline/TierWalk.kt` (`run`), `C/pipeline/CommandOutcome.kt` (`Unhandled`) | `Ladder` gets `cappedByPolicy = eligible.size < strategies.size` (true when either skip site fired: maxTier cut or `permits` refusal, offline-only included). `TierWalk.run` -> `CommandOutcome.Unhandled(effects(), lastReason, ladder.cappedByPolicy)`. `Unhandled` internal ctor gains the param; public `val cappedByPolicy: Boolean` with the KDoc required by the success criteria. Refused ladders (all tiers dropped) are `Failed(NoEligibleTier / ProviderUnavailable)`, never `Unhandled` |
| SEAM-06 | `C/strategy/OutcomeResolver.kt` (`Extraction`), `C/strategy/CommandSession.kt`, `C/pipeline/RunSession.kt`, `C/commit/CommitCoordinator.kt`, `C/commit/ActionLedger.kt` (`ActionDetails`, `record`), `C/commit/CommitSink.kt` (`ExecutedAction`), `C/commit/HeldProposal.kt`, `C/commit/ApplyStep.kt` (`ApplyStep.run`, `recordOutcome`, `journalCancelled`), `C/pipeline/HeldCommit.kt` (`applyWithoutGate`), `C/strategy/singleshot/SingleShotStrategy.kt` (`resolve`, `submitAll`), `C/strategy/agentic/AgenticDispatch.kt` (`prepare`, `settle`) | See "Pattern 3". `Extraction(toolName, arguments, callId)` primary + the existing 2-arg as secondary. `CommandSession` gets an **internal** abstract `submit(step, providerCallId)`; the public `submit(step)` stays and delegates with `null` |
| SEAM-07 | `K/KeyAccess.kt`, `K/ApiKeyStore.kt`, new `K/DelicateKeyAccess.kt`, `K/AndroidKeyStoreKeyAccess.kt` (needs the opt-in to implement), `K/SecretReader.kt`, `keystore/build.gradle.kts` | See "Pattern 4" |
| PROV-14 | `P/chat/OpenAiModelRules.kt` (`wireRules`, optionally `toolsOnChat`), tests `OpenAiModelRulesTest`, `ChatEncoderTest`, `ChatModelsTest`, golden `providers/src/test/resources/golden/chat/requests/openai.json` | See "Pattern 5" |
| PROV-15 | `P/chat/ChatErrors.kt` (`KEY_PARAM`, `refine`), tests `ChatErrorMapTest` + a `ChatTransportTest` MockWebServer replay | See "Pattern 6" |
| PROV-16 | `scripts/run-sample-gate1.sh` (`PHASE_DIR`, `DECISION_FILE`), `scripts/verify-sample-device-guard.sh` (mirrors the paths at the `.planning/phases/10-…` lines), `sample/…/legs/LegCatalog.kt` (RESPONSES_PROBE spec, comment "Recorded as CAPTURED, never judged"), `sample/…/legs/LegRunner.kt` (`judge`, the `LegKind.RESPONSES_PROBE` arm), `sample/…/verdict/VerdictTypes.kt` (`VerdictKind`), `sample/src/test/…/SmokeLegTest.kt` (`theResponsesProbeIsCapturedNotPassed` asserts `CAPTURED`), `scripts/sample-evidence-filter.sh` if a new reason word is needed, new `.planning/phases/12-wave-1-seams-w04-fix/12-LIVE-LEG-DECISION.md` | Turn the leg from CAPTURED into a judged PASS (reason `model_unsupported`, http 400) / FAIL (`http_error`); retarget runner paths; update the test |
| DOC-01 | `INTEGRATION.md` (§5/§6, §7, §10, Notes), `API.md` (new public types), `sample/src/test/…/docs/DocSnippetsTest.kt` (new region for the `KeyAccess` fake) | See "Pattern 7" |

### Pattern 1: `onFailed` goes in the `else` arm only
**What:** extend the existing hook holder; the three failure families stay in the order NoToolCall -> Refusal -> else.
**Example:**
```kotlin
// SingleShotOutcomes.kt (shape; names follow the file's own style)
internal class OutcomeHooks(
    val onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome,
    val onRefusal: suspend (ModelResponse?) -> StrategyOutcome,
    val onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome,
)

private suspend fun failureOutcome(failure: ModelResult.Failure, hooks: OutcomeHooks): StrategyOutcome =
    when (failure.reason) {
        is FailureReason.NoToolCall -> hooks.onNoToolCall(null)
        is FailureReason.Refusal -> hooks.onRefusal(null)
        else -> hooks.onFailed(failure.reason, failure.details)
    }
```
`decideResult` is the shared entry point Phase 15 reuses (DECISION-MAP Phase 15 note), so keep its signature `(result, hooks)`.

### Pattern 2: new public ctor shapes without default arguments
`ModelRequest` and `Extraction` follow the repo's overload idiom (explicit secondary ctors), because `ApiShapeTest` flags any public class with a default-argument ctor stub and the rule is "frozen constructor shapes".
```kotlin
// Extraction: 3-arg primary, existing 2-arg kept as secondary (same JVM signature as today)
public class Extraction(
    public val toolName: String,
    public val arguments: JsonObject,
    public val callId: String?,
) {
    public constructor(toolName: String, arguments: JsonObject) : this(toolName, arguments, null)
    // init { require(toolName.isNotBlank()) ... } unchanged; toString must still print only toolName + argumentCount
}
```

### Pattern 3: `providerCallId` plumbing (internal only)
Chain, every hop internal: `AssistantPart.ToolCall.id` -> `Extraction.callId` (public, for the resolver/executor) and -> `session.submit(step, call.id)` (internal overload) -> `CommitCoordinator.submit(step, providerCallId)` -> `ActionDetails.providerCallId` -> `ActionLedger.record` -> `ExecutedAction.providerCallId` (public).
- `finished()` (PREVIEW/ERROR) builds `ActionDetails` at `CommitCoordinator.finished`; `hold()` stores the id on `HeldProposal` (internal field) and on each `HELD` action; `gateFault()` stamps the `IS_ERROR` actions; `applyAll(mutations, providerCallId)` -> `ApplyStep.run(mutation, providerCallId)` (both `recordOutcome` and `journalCancelled` build `ActionDetails`).
- `commitHeld`: `HeldCommit.run` calls `child.coordinator.applyWithoutGate(mutations)`; pass `held.providerCallId` so committed-later actions keep the id.
- SingleShot stamps the one call's id on every action it submits (finished steps and the combined mutation). AgenticLoop stamps `call.id` per call in `settle`. Zero-call tiers never call `submit`, so `null`.
- `ExecutedAction.toString()`, `HeldProposal.toString()` and `Extraction.toString()` stay unchanged (redaction posture; `RedactionCanaryTest` exists).
- `ExecutedAction` keeps its trailing default (`mutating: Boolean = true`): put `providerCallId` BEFORE `mutating` so the default stays last and the `STUB_EXCEPTIONS` entry stays valid.

### Pattern 4: `KeyAccess` public + opt-in
Facts (read this session): today `KeyAccess` is `internal interface` with exactly two members, `existingKey(alias): SecretKey?` and `getOrCreateKey(alias): SecretKey` (`keystore/.../KeyAccess.kt:9-22`), so `fun interface` cannot compile (D-07).
```kotlin
// K/DelicateKeyAccess.kt
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "...")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
public annotation class DelicateKeyAccess

// K/KeyAccess.kt: public interface, annotated @DelicateKeyAccess, KDoc kept (read-never-creates contract)
// K/ApiKeyStore.kt: add ONE public ctor
@DelicateKeyAccess
public constructor(dataStore: DataStore<Preferences>, slots: List<KeySlot>, keyAccess: KeyAccess) :
    this(dataStore, slots, Dispatchers.IO, keyAccess)
```
- The internal 4-arg primary `(dataStore, slots, ioDispatcher, keyAccess)` and `SecretReader(keyAccess)` use the marked type in signatures, and `AndroidKeyStoreKeyAccess : KeyAccess` implements it, so `:keystore` itself needs the opt-in (module-wide `optIn` in `keystore/build.gradle.kts`'s `kotlin { compilerOptions { … } }`, or `@OptIn` per file). The 3-arg public ctor overload is distinct from the existing `(…, CoroutineDispatcher)` ctor by type, so no JVM clash.
- **Negative-compile proof must be in a different module.** Recommended: a plant in `scripts/verify-negative-controls.sh` Part 1 style that drops a consumer file into `:sample` (main or test) constructing `ApiKeyStore(ds, slots, fake)` without `@OptIn`, and asserts `:sample:compileDebugKotlin` (or the unit-test compile) goes red with the opt-in marker text. The positive proof is the doc-snippet region (compiles with `@OptIn(DelicateKeyAccess::class)`) in `DocSnippetsTest`.
- API docs gate C20 requires `API.md` to name `KeyAccess` and `DelicateKeyAccess` in backticks.

### Pattern 5: W04 wire deny-list (verified id facts)
Current code (read this session): `RESPONSES_ONLY = Regex("""^gpt-6(?:-astra|\.1-sol)(?:-.*)?$""")` (`OpenAiModelRules.kt:16`), `GPT_6_FAMILY = Regex("""^gpt-6(?:[.-].*)?$""")` (`:21`), and in `wireRules` (`:83-92`):
```
viaRouter && RESPONSES_ONLY.matches(id) -> rules(EFFORT_LOW, …)
GPT_6_FAMILY.matches(id) || isLaterGpt5(id) -> rules(EFFORT_NONE, …)
GPT_5_BASE.matches(id) || GPT_5_MINOR.matches(id) || O_SERIES.matches(id) -> completion
```
so a direct `gpt-6-astra` falls into the second arm and gets `"none"` (the W04 bug, evidence Probe B in `W04-host-wording-check.txt`). Fix:
```
viaRouter && RESPONSES_ONLY.matches(id) -> low                       (unchanged)
!viaRouter && RESPONSES_ONLY.matches(id) -> completion (no effort)   (NEW, before GPT_6_FAMILY)
(GPT_6_FAMILY.matches(id) || isLaterGpt5(id)) && !isProOrCodex(id) -> none
... remaining arms unchanged; a -pro/-codex id falls through to GPT_5_MINOR/GPT_5_BASE -> completion (no effort)
```
Why this is safe: with no effort sent, OpenAI's own answer for the Responses-only pair is the Probe A text ("…use /v1/responses or set reasoning_effort to 'none'"), which the existing `RESPONSES_ENDPOINT_MARKER = "v1/responses"` (`ChatErrors.kt:42`) already maps to `ModelUnsupported`. Update the `wireRules` KDoc ordering sentence.

Verified `none` facts (official docs, fetched 2026-10-05):

| Id | Rejects `none`? | Chat Completions | Engine should send (direct OpenAI) | Source |
|----|----|----|----|----|
| `gpt-6-astra` | yes (HTTP 400) | tools: no (Responses only) | no effort | [CITED: developers.openai.com/api/docs/guides/reasoning.md] |
| `gpt-6.1-sol` | yes (`none`, `minimal`) | tools: no | no effort | [CITED: same] |
| `gpt-6-sol`, `gpt-6-luna` | no; tools need `"none"` | yes, tools only with `"none"` | `"none"` (current rule right) | [CITED: developers.openai.com/api/docs/guides/latest-model.md] |
| `gpt-5.4+` (non-pro, non-codex) | no; tools need `"none"` | yes | `"none"` (current rule right) | [CITED: latest-model.md] |
| `gpt-5.5-pro` | yes (`medium, high, xhigh`) | **not supported at all** | no effort | [CITED: developers.openai.com/api/docs/models/gpt-5.5-pro.md] |
| `gpt-5.3-codex` | yes (`low`+) | **not supported at all** | no effort | [CITED: developers.openai.com/api/docs/models/gpt-5.3-codex.md] |
| `gpt-5.4-pro`, `gpt-5.2-pro`, `gpt-5.2-codex`, `gpt-5-pro` | per prior milestone research: reject `none` | Responses only | no effort | [ASSUMED] (STACK.md, not re-fetched individually) |

D-05's open question ("do `-pro`/`-codex` take tools on Chat at all?") is answered: no. Recommended (small, optional, planner may keep to the wire exclusion only): also make `toolsOnChat` false for direct `gpt-5(.N)?-pro` and `gpt-5(.N)?-codex` ids so they refuse pre-call with the specific `ModelUnsupported` (same mechanism as the Astra pair). The requirement text ("never receives `none`") is satisfied by the wire exclusion alone.

### Pattern 6: classifier backstop
`refine()` today runs: quota -> `context_length_exceeded` -> `isUnsupportedEndpoint(status, message)` -> 403 moderation. Add a fourth `ModelUnsupported` arm reading two new fields from the `error` object without touching the message: `status == 400 && textField(error, "param") == "reasoning_effort" && textField(error, KEY_CODE) == "unsupported_value"`. Order: after quota (a 429-style quota error is never reached anyway) and after the existing marker arm. Add `private const val KEY_PARAM = "param"`, `PARAM_REASONING_EFFORT`, `CODE_UNSUPPORTED_VALUE` consts (detekt MagicNumber/duplicate-string hygiene; no message text stored). The captured W04 body fields (from `W04-host-wording-check.txt` Probe B): `http=400 type=invalid_request_error param=reasoning_effort code=unsupported_value` with message `Unsupported value: 'reasoning_effort' does not support 'none' with this model. Supported values are: 'low', 'medium', 'high', and 'xhigh'.` Probe A, by contrast, is `param=reasoning_effort code=null` and is already handled by the marker, so the backstop must key on `code=unsupported_value`, not on `param` alone.

MockWebServer replay: model it on `ChatTransportTest.route(...)` (`ChatTransportTest.kt:98-123`), enqueue `MockResponse().setResponseCode(400).setBody(<W04 JSON body>)` and assert `failure.reason.code == "model_unsupported"` and `details.httpStatus == 400`. It needs no new Gradle wiring: `check` already depends on `testOkhttp521` and `testOkhttp550` (`providers/build.gradle.kts:112-142`), which re-run the same 4.12-compiled test classes, so one test covers all three legs. Use legacy `okhttp3.mockwebserver` only.

### Pattern 7: docs under the coverage gate
`scripts/verify-docs-coverage.sh`: C06 every ```` ```kotlin ```` fence in README/INTEGRATION/API must be preceded by `<!-- doc-snippet: NAME -->` and equal the region `// doc-snippet:start NAME` … `end NAME` in `sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt` byte for byte (common indent stripped); C07 every region must be used by a doc; C20 `API.md` must name every `public` top-level type in backticks (new: `ReasoningMode`, `KeyAccess`, `DelicateKeyAccess`); C21 no `food`/`card` words; C14 requires INTEGRATION to keep `uncached`, `anthropic/`, `gpt-6-astra`, `ModelUnsupported`, `forced`; C25 no `~/` or `/home/` paths. Import lists are shown in ```` ```text ```` fences (not checked), as INTEGRATION §7 and §10 already do.
- **§7 (`ProviderId`):** add one line: a `ProviderId` is a value class whose `toString()` prints its wire value (`"anthropic"`, `"openai"`, `"openrouter"`, `"on_device"`), so `"my_app_$provider"` yields clean names. [VERIFIED: core/…/ProviderId.kt `override fun toString(): String = value` and the four constants]
- **§10:** add `kotlinx.coroutines.test.runTest` and the JUnit imports (`org.junit.Assert.assertEquals`, `org.junit.Test`) to the existing ```` ```text ```` import block, and say the test dependencies are `kotlinx-coroutines-test` and `junit` (INTEGRATION:57 already says the engine does not publish them).
- **§5/6:** one sentence: a SingleShot tier cannot serve reads — its resolver returns only finished steps and mutations for the first tool call; a read tool such as `find_items` is usable only in the AgenticLoop tier.
- **Cache note** (Notes bullet "Unsupported and uncached combinations", already carries `uncached`): a single-tool SingleShot prefix is usually below the provider minimum, so it will not cache on Haiku (4,096 tokens) or OpenAI (1,024); `claude-sonnet-5` is 1,024 and `claude-sonnet-5-5` is 512. [CITED: platform.claude.com/docs/en/build-with-claude/prompt-caching]
- **KeyAccess fake (~10 lines):** new region (suggested name `keystore-fake`) in `DocSnippetsTest` + `<!-- doc-snippet: keystore-fake -->` in §7 after the `:keystore` bullet. The snippet carries `@OptIn(DelicateKeyAccess::class)` and a `ConcurrentHashMap<String, SecretKey>` + `KeyGenerator.getInstance("AES")` (256-bit) implementation; the in-repo model is `SoftwareKeyAccess` in `keystore/src/test/…/KeystoreTestSupport.kt:30-48`. "Round-trips a key on the JVM" = a `@Test` in `DocSnippetsTest` that builds a temp-file preferences DataStore, `ApiKeyStore(ds, slots, fake)`, saves and reads back `KeyState.Ready(last4)`. State in the prose that a fake must keep read-never-creates (`existingKey` returns null for an absent alias).
- Docs edits void the isolated wiring-test result: re-run is Phase 19/20's job, not Phase 12's (v1.0.1 `11-WIRING-RERUN.md`).

### Recommended plan slicing (by file ownership, to avoid hot-file collisions)
1. **12-A providers:** PROV-14 + PROV-15 + SEAM-03 (+ tests, goldens). Touches no `:core` file.
2. **12-B core request seams:** SEAM-02 (`ReasoningMode`, `ModelRequest`, two Builders) + SEAM-01 (`onFailed`). Both edit `SingleShotStrategy.kt`.
3. **12-C core outcome facts:** SEAM-04 + SEAM-05 (`RunRecorder`, `TierWalk`, `PolicyPreCheck`, `CommandOutcome`, `CommandTrace`).
4. **12-D call-id plumbing:** SEAM-06 (touches `SingleShotStrategy.kt`, `AgenticDispatch.kt`, whole commit path). Sequence after 12-B; `TierWalk.kt` is not touched by it.
5. **12-E keystore:** SEAM-07 (+ negative-compile plant).
6. **12-F docs:** DOC-01 + API.md for the new types + `DocSnippetsTest` region; depends on 12-B/12-E for final names.
7. **12-G live smoke:** PROV-16 (runner retarget, verdict judge, sample test, decision file, device run); depends on 12-A.
Wave plan: A ‖ B ‖ C ‖ E in wave 1 (disjoint files), then D, then F, then G.

### Anti-Patterns to Avoid
- **Passing the call id as a public `submit` parameter or via a coroutine-context element** — rejected by D-04; keep the public `submit(step)` unchanged.
- **Special-casing offline for `cappedByPolicy`** — wrong for the code (D-03); both `tierSkipped(…, TIER_SKIPPED_POLICY)` sites count.
- **Sending `thinking: {"type":"disabled"}` or omitting `reasoning_effort` as a "real" OFF** — both 400 on current models (D-02); OFF = byte-identical v1.0 wire.
- **An allow-table for `"none"`** — regresses unlisted working ids (D-05).
- **Adding default arguments to a public ctor or removing a default from an internal ctor listed in `ApiShapeTest.STUB_EXCEPTIONS`** — `ApiShapeTest` fails both ways.
- **Prefix/regex matching for the Anthropic row** — the table is exact-id by design.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Opt-in gating of a delicate seam | A custom runtime check or a naming convention | Kotlin `@RequiresOptIn(level = ERROR)` + `@OptIn` | Compile-time enforcement is the SEAM-07 acceptance test; stable since Kotlin 1.7 |
| Public-API compatibility proof | A hand-written signature diff | Existing `apiCheck` (Metalava) against the committed `api.txt` | Already gates `check`; additions pass, removals fail |
| JSON error-body reading | A regex over the raw body | Existing `parseObject` / `textField` in `ChatErrors.kt` | Tolerates non-JSON/oddly shaped bodies and never stores the message |
| HTTP stubbing for the W04 replay | A fake `OkHttpClient` | Legacy `okhttp3.mockwebserver.MockWebServer` | Runs on 4.12.0 / 5.2.1 / 5.5.0 legs unchanged |
| Doc/code agreement | Hand-synced snippets | `DocSnippetsTest` regions + `verify-docs-coverage.sh` | The gate fails any drift |
| Test key handling on device | Reading key files, inline keys | `push-test-key openai --device <serial> --package <appId>`, host probes under `with-test-keys --only openai --` | Key never enters a transcript or argv |
| Software `KeyAccess` for tests | Mocking AndroidKeyStore | A ~10-line `KeyGenerator` + `ConcurrentHashMap` fake (model: `SoftwareKeyAccess`) | AndroidKeyStore is unavailable on the JVM |

**Key insight:** every item here is a narrow edit inside an existing mechanism (hook holder, ctor-overload idiom, `ActionDetails` chain, `wireRules` table, `refine()` list, doc-region gate). The risk is not design; it is breaking one of the repo's mechanical gates, so most pitfalls below are gate pitfalls.

## Common Pitfalls

### Pitfall 1: `ApiShapeTest` stub rule bites in both directions
**What goes wrong:** removing the default `turns = emptyList()` from `TierAttempt` (or `mutating = true` from `ExecutedAction`) makes `everyDocumentedStubExceptionStillDeclaresAStub` fail ("stale stub exceptions"); adding a default to a *public* ctor (e.g. `ModelRequest(..., reasoning = OFF)`) makes the growth-rule test flag the class.
**How to avoid:** new params on internal-ctor classes go before the existing trailing default; new public ctor shapes use explicit overloads. [VERIFIED: core/src/test/…/ApiShapeTest.kt:147-153 and the STUB_EXCEPTIONS list]
**Warning signs:** `ApiShapeTest` red after a "harmless" signature tidy.

### Pitfall 2: value-class constructor parameters compile to a marker-suffixed ctor
**What goes wrong:** a primary ctor with a `ReasoningMode` (value class) parameter is compiled with a `DefaultConstructorMarker`-style synthetic JVM signature, not a plain public one, so the old 7-arg JVM ctor must be kept as its own real secondary ctor (it is, if you keep it as a secondary delegating to the new primary). A Java caller cannot reach the 8-arg ctor.
**How to avoid:** keep the 7-arg ctor as an explicit secondary; add a test (reflection, like `ApiShapeTest`) asserting the `(String, List, List, ToolChoice, int, CacheDirective, boolean)` public ctor still exists. `ApiShapeTest` already treats "a value class stub ends in the marker alone and is not flagged" ([VERIFIED: KeystoreApiShapeTest.kt KDoc]; the same wording applies to `:core`'s test). Confirm the synthetic shape with `javap -p` on the built class before relying on it (see Assumption A2).

### Pitfall 3: `TierWalk` hot path — `carryIn` must come from the walk, not the strategy
**What goes wrong:** reading `carry` from the strategy's outcome instead of the walk's own `carry` field reports the wrong tier (the carry is set when tier N escalates and consumed by tier N+1).
**How to avoid:** `RunSession(scope, strategy.id, …, carry)` is constructed in `runTier` from the walk's `carry`; use exactly that value (`carry != null`) for `tierStarted`. `startFresh` (NoMatch) clears carry, so the next tier gets `carryIn=false`. Cover: escalate-with-carry, escalate-without-carry, no-match-then-tier.

### Pitfall 4: the Anthropic table is exact-id and neighbours exist
**What goes wrong:** `claude-sonnet-5` and `claude-sonnet-5-5` differ in forced-tool-choice support and in minimum prefix (1,024 vs 512). A prefix/regex row would swallow the neighbour. Also `ChatModels.routedAnthropicCapabilities` feeds `id.replace('.', '-')` into `AnthropicModels.capabilities`, so `anthropic/claude-sonnet-5` through OpenRouter now resolves to the new row (forced allowed; caching stays `NONE` there by design) and `anthropic/claude-sonnet-5.5` still resolves to the rejecting row.
**How to avoid:** `when` arm on the exact const; tests: `claude-sonnet-5` -> (forced allowed, EXPLICIT_BREAKPOINTS, 1,024); `claude-sonnet-5-5` unchanged (rejects, 512); a dated id `claude-sonnet-5-20261001` -> unknown default; routed `anthropic/claude-sonnet-5` and `anthropic/claude-sonnet-5.5` through `ChatModels`.

### Pitfall 5: the classifier must not widen into false `ModelUnsupported`
**What goes wrong:** keying on `param=reasoning_effort` alone also matches Probe A (fine, same reason) but would also mask other `reasoning_effort` errors. Keying on 400 only without `code=unsupported_value` is too broad.
**How to avoid:** require all three (status 400, `param`, `code`); add negative tests (same body with status 500 -> `http_error`; `param=temperature` -> `http_error`; `code=null` and no marker text -> `http_error`).

### Pitfall 6: existing goldens and "named cases" assertions
**What goes wrong:** `ChatEncoderTest.goldenFilesHoldExactlyTheNamedCases` pins the exact list of cases in `openai.json` / `openrouter.json`. Adding a golden case (e.g. direct `gpt-6-astra` under override) without updating that list fails the test. The existing `theBothModels…`/`aToolsRequestToAResponsesOnlyModelOnOpenAiIsRefused…` tests assert refusal before any call, so they stay green; `anAppOverrideForTheExactIdWinsAndTheCallReachesTheProvider` only checks the call reaches the provider.
**How to avoid:** update the named-case list when adding goldens; add the direct-Astra case to `openai.json` with `reasoning_effort` absent, plus rule-level assertions for every id in the table above (including still-`none` positives `gpt-6-sol`, `gpt-6-luna`, `gpt-5.5`) in `OpenAiModelRulesTest`. Also fix `OpenAiModelRulesTest.theFamilyMatchIsAnchored`, which asserts `gpt-6-astral` routed -> `"none"` (still true, keep).

### Pitfall 7: Probe B vs the post-fix wire — assume nothing about the live answer
**What goes wrong:** Probe A (tools, no effort) answered with the `/v1/responses` text, but the engine's real post-fix wire also carries forced `tool_choice`, `parallel_tool_calls:false`, a strict tool and `max_completion_tokens`. Another validation could fire first and produce a different 400 (then PROV-16 would see `http_error` on the device).
**How to avoid:** before any device time, replay the exact post-fix bytes (from the new encoder golden) as a host `curl` under `with-test-keys --only openai --`. A 400 is not billed (W04 probes cost nothing). If the answer lacks the `v1/responses` text, extend the classifier or wire rule on the host, not on the device. This is the cheapest de-risk in the phase.

### Pitfall 8: `run-sample-gate1.sh` is hard-wired to the archived Phase 10 directory
**What goes wrong:** `PHASE_DIR=".planning/phases/10-sample-harness-gate-1-docs"` and `DECISION_FILE="$PHASE_DIR/10-LIVE-LEG-DECISION.md"` (`run-sample-gate1.sh:39-40`) point at a directory that no longer exists, so `push-keys` refuses ("live legs are not approved"). `verify-sample-device-guard.sh` builds a fake repo with the same path (`:124-125`, `EVID_REL` at `:274`), and `agent-wiring-test.sh` has its own `PHASE_DIR` (`:19`; that one is the wiring-test prompt, leave it for Phase 19).
**How to avoid:** make the runner path a single variable pointing at `.planning/phases/12-wave-1-seams-w04-fix`, update the guard-verifier fixture to match, and keep the guard's "TESTER only, no override" property tests green (run `scripts/verify-sample-device-guard.sh`). Phase 19 will need the same retarget (its own decision file); a parameterized phase dir avoids doing it twice, but D-06's guard property "no argument or environment variable that changes the target" is about the DEVICE, not the phase dir.

### Pitfall 9: host memory
**What goes wrong:** at research time the host had swap fully used (2.0Gi/2.0Gi, 3.3Gi free, 9.2Gi available); the VAE release-cut memory note records earlyoom kills under that condition. Gradle runs for this phase are smaller than a cut but still non-trivial.
**How to avoid:** run Gradle with `-Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false` for the matrix legs; never `./gradlew --stop` while another repo's daemon is live; do not loop on an earlyoom kill, report it. [VERIFIED: `free -h` this session; memory note vae-release-cut-host-oom]

## Code Examples

### `claude-sonnet-5` row
```kotlin
// AnthropicModels.kt (additions; existing constants quoted from AnthropicModels.kt:6-13)
private const val SONNET_5 = "claude-sonnet-5"
private const val SONNET_5_MIN_CACHEABLE_PREFIX_TOKENS = 1_024   // not STANDARD_..._TOKENS (512)

private val sonnet5 = ModelCapabilities {
    caching = CachingMode.EXPLICIT_BREAKPOINTS
    minCacheablePrefixTokens = SONNET_5_MIN_CACHEABLE_PREFIX_TOKENS
    // supportsForcedToolChoice stays at its default (true): Sonnet 5 is not on Anthropic's forced-tool-use list
}

fun capabilities(model: String): ModelCapabilities = when (model) {
    OPUS_5_5, SONNET_5_5, FABLE_5_1, MYTHOS_5_1 -> rejectsForcedToolChoice
    SONNET_5 -> sonnet5
    HAIKU_4_5 -> haiku
    else -> unknownModel
}
```
Sources: 1,024 for Claude Sonnet 5 [CITED: platform.claude.com/docs/en/build-with-claude/prompt-caching, fetched 2026-10-05, "1,024 tokens for Claude Opus 4.8, Claude Sonnet 5, Claude Sonnet 4.6, …"; "512 tokens for … Claude Sonnet 5.5 …"; "4,096 tokens for Claude Haiku 4.5"]. Forced-tool rejection list [CITED: platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools, "Claude Opus 5.5, Claude Sonnet 5.5, Claude Fable 5.1, and Claude Mythos 5.1 … any and tool return a 400 error"]; Sonnet 5 is absent from it. That last inference is an absence in a documented list (not a constraint), so it is `[ASSUMED]` for "forced choice works on `claude-sonnet-5`"; the milestone brief records a live positive ("live OK 4/4 per SB Gate-1", `RECONVENE-BRIEF-R-v1.1.md` row XR-173-01(c)), which is the evidence to cite in the plan. Add an `AnthropicForcedToolTest`-style assertion at the encoder level (forced `tool_choice` emitted, no reshape) for the new id.

### `Unhandled.cappedByPolicy` KDoc (required wording)
```kotlin
/**
 * ...
 * @property cappedByPolicy true when the command's policy skipped at least one tier (a `tier_skipped_policy` in the
 * trace: a tier above `maxTier`, or a tier its provider restriction or offline-only mode does not permit, offline-only
 * included) and no tier handled the command. A ladder whose tiers were all skipped is not unhandled: it fails with
 * `NoEligibleTier` or `ProviderUnavailable`.
 */
```
Truth table for tests: (maxTier cut, none ran handled) -> true; offline-only dropping a cloud tier while an on-device/no-provider tier ran and handed up -> true; nothing skipped, all tiers hand up -> false; all skipped -> `Failed`, not `Unhandled`; a tier skipped and a later tier `Completed` -> not Unhandled at all.

### W04 MockWebServer replay body (fields from the captured Probe B)
```kotlin
private const val W04_BODY =
    """{"error":{"message":"Unsupported value: 'reasoning_effort' does not support 'none' with this model. """ +
    """Supported values are: 'low', 'medium', 'high', and 'xhigh'.","type":"invalid_request_error",""" +
    """"param":"reasoning_effort","code":"unsupported_value"}}"""
// server.enqueue(MockResponse().setResponseCode(400).setBody(W04_BODY)) -> reason.code == "model_unsupported"
```
The body text is paraphrased into JSON from the evidence file (the file records fields and message, not raw bytes); keep a comment saying so. No key, transcript or tool args appear.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Blanket `GPT_6_FAMILY -> "none"` | Deny-list: Responses-only pair and `-pro`/`-codex` get no effort | This phase | Fixes W04; keeps `gpt-6-sol`/`-luna`/`gpt-5.4+` on `"none"` |
| `claude-sonnet-5` in the unknown-model default (diagnostic silent) | Exact row, 1,024-token minimum | This phase | Cache-not-engaged diagnostic active for it |
| Sonnet 5.5 min prefix 512 | unchanged | — | Do not conflate with Sonnet 5 (1,024) |

**Deprecated/outdated:** SUMMARY.md:59's "offline doesn't auto-set `cappedByPolicy`" (wrong for the code; D-03). REQUIREMENTS SEAM-07's "public `fun interface KeyAccess`" (cannot compile; D-07; wording follows from the orchestrator).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Forced tool choice works on `claude-sonnet-5` (inferred from absence in Anthropic's rejection list; backed by the brief's "live OK 4/4 per SB Gate-1") | Code Examples (row) | If it rejects, the diagnostic is right but the encoder 400s; the existing reshape-on-reject path would catch it and double latency until the row is flipped |
| A2 | A primary ctor with a value-class parameter compiles to a marker-suffixed synthetic JVM ctor, so keeping the 7-arg ctor as a real secondary ctor preserves binary compat | Pitfall 2 | If `ReasoningMode` as a value class forces an unwanted JVM shape, Metalava may show an odd ctor; fallback (new discuss pass) is a final class |
| A3 | `gpt-5.4-pro`, `gpt-5.2-pro`, `gpt-5.2-codex`, `gpt-5-pro` also reject `none` and are Responses-only on Chat (only `gpt-5.5-pro` and `gpt-5.3-codex` were re-fetched individually) | Pattern 5 | A wrongly excluded id sends no effort; harmless for Responses-only ids, but a Chat-capable id that needs `none` for tools would 400 (low likelihood; the exclusion is limited to `-pro`/`-codex` suffixes) |
| A4 | Routed ids (`openai/gpt-5.5-pro` via OpenRouter) are fine with no effort because the router translates | Pattern 5 | A routed `-pro` id could 400 at the router; PROV-14 is stated for direct OpenAI. Option: apply the `-pro`/`-codex` exclusion for `!viaRouter` only |
| A5 | The Kotlin error text for an opt-in violation contains "opt-in" (used as the negative-compile marker) | Pattern 4 | Marker mismatch makes the plant look "wrong reason red"; run it once and copy the real text |
| A6 | `kotlin { compilerOptions { optIn.add("…DelicateKeyAccess") } }` is accepted by the AGP 9 built-in Kotlin `kotlin {}` block in `:keystore` | Pattern 4 | Fall back to `@file:OptIn` per file, or `freeCompilerArgs.add("-opt-in=…")` (the file already uses `freeCompilerArgs.add` for `-Xjdk-release=11`) |
| A7 | The real post-fix W04 wire (with forced tool_choice, strict tool, etc.) draws the `/v1/responses` text, not a different 400 | Pitfall 7 | Mitigated by the host Probe C before the device run |
| A8 | `:sample` unit-test classpath resolves `PreferenceDataStoreFactory` for the JVM round-trip test | Pattern 7 | Add `datastore-preferences-core` as a `testImplementation` of `:sample` (already used test-only per CLAUDE.md stack table) |
| A9 | `:keystore:testDebugUnitTest` is the unit-test task name | Validation | Use `./gradlew :keystore:tasks --all` to confirm |

## Open Questions

1. **P19 D-13 contradicts PROV-16.**
   - What we know: P19 CONTEXT D-13 (control-plane 4146165) says the live `gpt-6-astra` smoke "must succeed with no 400 on `reasoning_effort`". PROV-16 / ROADMAP SC5 say the call "returns the specific typed outcome (`ModelUnsupported`, never `http_error`)". OpenAI docs: Astra has no function calling on Chat Completions; the only reachable live result under the `supportsTools` override is a 400 mapped to `ModelUnsupported`.
   - What's unclear: whether Yahir meant "no 400 *on reasoning_effort*" (true post-fix: the 400 is about endpoint, via the marker) — the sentence is reconcilable only if "succeed" means "classifies correctly".
   - Recommendation: message the orchestrator to restate P19 D-13 as "the PROV-16 evidence line is reused/re-confirmed; no second paid call", so one bounded call serves both. Do not block Phase 12 on it.
2. **Live-leg approval file.** The runner requires `decision: approved` in the decision file. Phase 12 needs its own `12-LIVE-LEG-DECISION.md` (request ceiling, TESTER window announcement). Per the multi-repo process, ask the orchestrator (not Yahir) for the relay; expected cost is near zero (a 400 bills nothing) with a ceiling of 1-2 requests. The existing optional-pool budget allows exactly one probe per install (`SmokeLegTest` asserts the second run is `REFUSED budget`).
3. **Scope of the `-pro`/`-codex` pre-call refusal** (Pattern 5): wire exclusion only, or also `toolsOnChat=false`? Recommendation: do both for direct OpenAI ids (docs say Chat is unsupported for them); the planner may drop the second if it wants the smallest diff.
4. **REQUIREMENTS.md SEAM-07 wording** still says `fun interface`; the orchestrator accepted the correction. Update the line in REQUIREMENTS.md during Phase 12 (docs-only) so the verifier does not trip on it.
5. **Device scheduling.** Phase 13 (spike) and Phase 19 also use the TESTER; PROV-16 must not overlap them. Both phones were listed as `device` by `adb devices` at research time (USB serial `R5CT10XNKQN` and `100.118.21.106:1496`); use only `R5CT10XNKQN` with `-s`, and check nobody is testing before the run.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | all Gradle builds | yes | OpenJDK 17.0.19 | — |
| `adb` + TESTER (`R5CT10XNKQN`) | PROV-16 live smoke, keystore instrumented (not needed here) | yes, listed as `device` | adb present | PROV-16 is blocked without it (no emulator fallback by the approved decision pattern) |
| `push-test-key`, `with-test-keys` | PROV-16 key delivery, host Probe C | yes (`~/.local/bin`) | — | Missing key file = loud stop; Yahir creates it in his own terminal (test-keys workflow) |
| Host memory headroom | Gradle matrix legs | marginal: swap 2.0Gi/2.0Gi used, ~9.2Gi available | — | Reduce Gradle workers; ask Yahir for a swap reset only if earlyoom kills a run |
| Network to platform.claude.com / developers.openai.com | doc verification (done) | yes | — | — |

**Missing dependencies with no fallback:** none found.
**Missing dependencies with fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest` / `runBlocking`), legacy `okhttp3.mockwebserver` in `:providers`; hand-written fakes in `core/src/testFixtures` |
| Config file | per-module `build.gradle.kts`; detekt `config/detekt/detekt.yml`; scanner `gradle/invariants.gradle.kts`; docs gate `scripts/verify-docs-coverage.sh` |
| Quick run command | `./gradlew :core:test --tests '<Class>' -Dorg.gradle.workers.max=2` (per module; classes below) |
| Full suite command | `./gradlew check` (includes `testOkhttp521`/`testOkhttp550`, detekt, Metalava compat, scanner) then `scripts/verify-docs-coverage.sh` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SEAM-01 | `onFailed` fires for HTTP-400-style `Failure` (else arm), not for NoToolCall/Refusal hooks, binding refusal, ceiling, `strategy_error`; unset -> `Failed(reason, details)`; throwing hook -> `strategy_error` | unit | `./gradlew :core:test --tests '*SingleShotOutcomeMappingTest*'` | extend existing; new cases |
| SEAM-02 | `reasoning` defaults to OFF on both Builders and every ModelRequest ctor; OFF and PROVIDER_DEFAULT produce byte-identical bodies on Anthropic, OpenAI, OpenRouter equal to existing goldens; 7-arg ctor still exists | unit/golden | `./gradlew :core:test --tests '*SingleShotRequestTest*' --tests '*TranscriptTypesTest*'`; `./gradlew :providers:test --tests '*AnthropicEncoderTest*' --tests '*ChatEncoderTest*'` | extend existing; new ctor-shape test |
| SEAM-03 | `claude-sonnet-5` row; neighbour `claude-sonnet-5-5` unchanged; dated id unknown; routed ids | unit | `./gradlew :providers:test --tests '*AnthropicModelsTest*' --tests '*ChatModelsTest*'` | extend existing |
| SEAM-04 | `carryIn` true only for a tier that received a non-null carry; in-flight (`timeout`/`cancelled`) attempts too | unit | `./gradlew :core:test --tests '*TierWalkTest*' --tests '*TraceTest*' --tests '*InFlightTierTraceTest*'` | extend existing |
| SEAM-05 | `cappedByPolicy` truth table (maxTier cut, permits refusal, offline-only, none skipped, all skipped -> Failed) | unit | `./gradlew :core:test --tests '*TierPolicyTest*' --tests '*TierWalkTest*'` | extend existing |
| SEAM-06 | SingleShot stamps one id on all actions (finished + combined mutation); AgenticLoop per call; held -> `commitHeld` keeps id; gate-fault/preview/error actions stamped; zero-call tier null; `Extraction` 2-arg ctor still exists; toString unchanged | unit | `./gradlew :core:test --tests '*CommitPathTest*' --tests '*HeldReportingTest*' --tests '*SingleShotResolveTest*' --tests '*AgenticLoopDispatchTest*' --tests '*RedactionCanaryTest*'` | extend existing |
| SEAM-07 | `ApiKeyStore(ds, slots, keyAccess)` works with a software fake (save/read round trip, read never creates); without `@OptIn` compilation fails | unit + negative-compile | `./gradlew :keystore:testDebugUnitTest` ; `scripts/verify-negative-controls.sh` (new plant) | new test + new plant |
| PROV-14 | rule-level + encoder golden: direct `gpt-6-astra`, `gpt-6.1-sol`, `gpt-5.5-pro`, `gpt-5.3-codex` send no `reasoning_effort`; `gpt-6-sol`, `gpt-6-luna`, `gpt-5.5`, `gpt-6` still `"none"`; routed Astra still `"low"` | unit/golden | `./gradlew :providers:test --tests '*OpenAiModelRulesTest*' --tests '*ChatEncoderTest*'` | extend existing; new golden case |
| PROV-15 | W04 body -> `model_unsupported`, details status 400 on all three OkHttp legs; negative variants stay `http_error`; Probe A text still `model_unsupported` | unit + MockWebServer | `./gradlew :providers:test --tests '*ChatErrorMapTest*' --tests '*ChatTransportTest*'`, then `:providers:testOkhttp521` / `:providers:testOkhttp550` with the same `--tests` | extend existing |
| PROV-16 | Live `:sample` RESPONSES_PROBE leg returns PASS with reason `model_unsupported`, http 400, evidence line logged, request count bounded | JVM test of the judge + manual-only device run | `./gradlew :sample:testDebugUnitTest --tests '*SmokeLegTest*'`; device: `scripts/run-sample-gate1.sh preflight` ... per runbook | judge test extend; device run is manual-only (real TESTER, real key; justified: only proof of the real wire) |
| DOC-01 | docs say the four things; gate green | gate | `scripts/verify-docs-coverage.sh` and `./gradlew :sample:testDebugUnitTest --tests '*DocSnippetsTest*'` | extend existing |

Phase-wide gates: `./gradlew detekt scanBannedConstructs apiCheck`, `core` `ApiShapeTest`, `KeystoreApiShapeTest`, `ProvidersApiShapeTest`.

### Sampling Rate
- **Per task commit:** the single touched test class (command above for the module), under `-Dorg.gradle.workers.max=2`.
- **Per wave merge:** `./gradlew :core:test :providers:test :keystore:testDebugUnitTest` plus `scripts/verify-docs-coverage.sh` once docs exist.
- **Phase gate:** `./gradlew check` green (matrix legs included), `scripts/verify-docs-coverage.sh` OK, `scripts/verify-negative-controls.sh` for the new opt-in plant (not part of `check`), `scripts/verify-sample-device-guard.sh` after the runner retarget, then the single live device run, before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] New negative-compile plant for the opt-in (in `scripts/verify-negative-controls.sh`, `:sample` consumer file) — covers SEAM-07
- [ ] `DocSnippetsTest` region `keystore-fake` + JVM round-trip `@Test` — covers SEAM-07 / DOC-01
- [ ] `ModelRequest` 7-arg-ctor-exists reflection test; `Extraction` 2-arg-ctor-exists test — covers SEAM-02 / SEAM-06 additivity
- [ ] `12-LIVE-LEG-DECISION.md` (orchestrator relay) — blocks PROV-16 device run only
- [ ] Host Probe C script (scratchpad, not committed; free 400) — de-risks PROV-16
- None needed for framework install (existing infrastructure covers everything else)

## Security Domain

`security_enforcement` is enabled in `.planning/config.json` (ASVS level 1).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no user auth added) | — |
| V3 Session Management | no | — |
| V4 Access Control | yes (API surface) | `@RequiresOptIn(ERROR)` on `KeyAccess` and the 3-arg `ApiKeyStore` ctor; negative-compile proof |
| V5 Input Validation | yes | Error-body parsing keeps tolerating hostile/non-JSON bodies (`parseObject`, `safeToken` for `code`); the new classifier reads only `param` and `code` text fields and never stores the message |
| V6 Cryptography | yes | No new crypto: the existing AES/GCM `AesGcm` path is untouched. The doc fake must use `javax.crypto.KeyGenerator` (never a hand-rolled cipher) and say it is for tests only |
| V7 Error Handling and Logging | yes | `providerCallId` is a provider id only; no `toString()` change; evidence line for the smoke stays inside the closed allow-list (no key, transcript, tool args) |
| V8/V9 Data protection / communications | yes (smoke) | Test key moves by file reference via `push-test-key` (TESTER only), plaintext key directory is verified empty afterwards (`verify-keys-gone`); never inline a key |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Misusing a public key-custody seam in production code (a `KeyAccess` fake with a software key) | Elevation of privilege / Information disclosure | Opt-in ERROR level + KDoc "tests only" + docs |
| Provider echo of a secret in an error body reaching a failure/log | Information disclosure | Classifier reads two enum-like fields only; existing `ChatErrorInfo` never stores body or message |
| Leaking a transcript/tool args through the new call-id field | Information disclosure | Id only; keep `toString()` unchanged; run `RedactionCanaryTest` |
| Live key left on the TESTER | Information disclosure | `push-test-key` (debuggable build, app-private storage), `verify-keys-gone`, `cleanup` subcommand |
| Running the smoke on the wrong phone | Tampering | Runner guard (USB serial pinned, identity check); never touch the personal phone; do not add any override |

## Sources

### Primary (HIGH confidence)
- platform.claude.com/docs/en/build-with-claude/prompt-caching — fetched 2026-10-05: minimum cacheable prompt per model (Sonnet 5 = 1,024; Sonnet 5.5 / Opus 5.5 / Fable / Mythos = 512; Haiku 4.5 = 4,096); up to 4 cache breakpoints
- platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools — fetched 2026-10-05: forced tool use rejected on Claude Opus 5.5, Sonnet 5.5, Fable 5.1, Mythos 5.1 (400)
- developers.openai.com/api/docs/guides/reasoning.md — fetched 2026-10-05: Astra rejects `none` (HTTP 400); 6.1 Sol rejects `none` and `minimal`; Chat Completions does not support function calling with Astra or 6.1 Sol
- developers.openai.com/api/docs/guides/latest-model.md — fetched 2026-10-05: gpt-6-sol/-luna and gpt-5.4+ take Chat function tools only with `reasoning_effort: "none"`
- developers.openai.com/api/docs/models/gpt-5.5-pro.md and gpt-5.3-codex.md — fetched 2026-10-05: Chat Completions "Not supported"; effort sets exclude `none`
- Repository files read this session: `AnthropicModels.kt`, `OpenAiModelRules.kt`, `ChatErrors.kt`, `TraceCode.kt`, `KeyAccess.kt`, `ApiShapeTest.kt`, `run-sample-gate1.sh` (lines 38-42), plus SingleShot/AgenticLoop/commit/telemetry/pipeline sources, `INTEGRATION.md`, `scripts/verify-docs-coverage.sh`, `W04-host-wording-check.txt`, `.planning/releases/v1.0.1/evidence/wiring-stumbles.txt`

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` / `ARCHITECTURE.md` / `PITFALLS.md` (milestone research, same-day) — cross-checked above for the items re-fetched; the unchecked `-pro`/`-codex` variants are tagged A3

### Tertiary (LOW confidence)
- None used as authority

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; pins from project CLAUDE.md
- Architecture / code map: HIGH — every file and hook read this session
- Pitfalls: HIGH for gate pitfalls (test sources read); MEDIUM for A2/A5/A6 (Kotlin/AGP behavior stated from knowledge, to confirm in the first task that touches them)
- Provider facts: HIGH for Sonnet 5 minimum and the OpenAI `none` list as fetched; MEDIUM for A1/A3

**Research date:** 2026-10-05
**Valid until:** 2026-10-12 (model catalogs and docs move weekly; re-check the two doc tables if planning slips past that)
