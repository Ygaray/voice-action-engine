# Architecture Research

**Domain:** Android/JitPack voice-command engine library (tier ladder over pluggable providers)
**Milestone:** v1.1 (Grammar, Plan, Router, Undo, Spike, Adapter; phases 12-20; tag `v1.1.0`)
**Researched:** 2026-10-05
**Confidence:** HIGH for every integration point read from source (file:line cited); MEDIUM for the design choices marked "Decision" (they are recommendations, not facts); LOW only for the two external unknowns called out in "Open items" (Anthropic's `claude-sonnet-5` cache minimum, the full list of OpenAI ids that reject `"none"`).

Scope: NEW work only. Base is `v1.0.1`. All line numbers are against the working tree at HEAD (`5058077`). Every public change must be strictly additive against `core/api.txt`, `providers/api.txt`, `keystore/api.txt` (Metalava `--check-compatibility:api:released`); internal members are free to change because they are not in the dumps.

---

## 1. Headline findings (read this first)

These are the places where the approved sketches in `RECONVENE-BRIEF-R-v1.1.md` meet source reality. Items marked **GAP** are not obvious from the brief and change the plan.

1. **GAP: `providerCallId` cannot reach `ExecutedAction` by "just passing the id through" at SingleShot `:141` / AgenticDispatch `:131`.** Those lines build an `Extraction` for the *app's* resolver/executor. The `ExecutedAction` is built much later, in `ActionLedger.record` (`commit/ActionLedger.kt:39`) from an `ActionDetails` that only the `CommitCoordinator`/`ApplyStep` fill (`CommitCoordinator.kt:109,139,156`, `ApplyStep.kt:101,115`). The only bridge between a strategy and the coordinator is `CommandSession.submit(step)` (`CommandSession.kt:35`) which carries no call id. Fix: an `internal` `submit(step, providerCallId)` on `CommandSession` (overridden by `RunSession`) that threads the id down to `ActionDetails`, **plus** carrying it on `HeldProposal` (internal field) so a later `commitHeld` stamps the committed action too (`HeldCommit.kt:71`). No public API beyond the new `ExecutedAction.providerCallId` / `Extraction.callId`.
2. **GAP: a `PickContext` model call made before the first tier starts is silently dropped from the trace.** `RunRecorder.turnRecorded` appends to `book.turns` (`RunRecorder.kt:72`) and `TierBook.start` clears `turns` (`RunRecorder.kt:172`). A router call has no tier in flight, so its turn would vanish from `trace.attempts` (the tokens would still be counted in `tokenTotal`, so only the *trace* requirement fails). Fix: run the picker inside a pseudo-attempt (`tierStarted(pickerId)` ... `tierFinished(pickerId, "picked" | "router_fallback", ...)`).
3. **GAP: `KeyAccess` has two abstract members** (`existingKey`, `getOrCreateKey`; `KeyAccess.kt:14,21`), so the brief's `fun interface KeyAccess` does not compile. It must be a plain `interface`. Everything else in the keystore sketch holds.
4. **GAP: W04 has two independent root causes**, both internal. (a) `OpenAiModelRules.wireRules` tests `GPT_6_FAMILY` *before* the Responses-only case when `viaRouter == false` (`OpenAiModelRules.kt:84-87`), so direct `gpt-6-astra` gets `reasoning_effort: "none"`. (b) `ChatErrors.refine` only recognises the `v1/responses` message marker (`ChatErrors.kt:137-146,151-156`); the captured 400 (`param=reasoning_effort`, `code=unsupported_value`) matches neither, falls through `STATUS_REASONS` (no 400 entry, `:49-59`) and becomes `HttpError`.
5. **GAP: the `claude-sonnet-5-5` row already exists** and *rejects* forced tool choice (`AnthropicModels.kt:7,43`). The new id is `claude-sonnet-5` (no `-5` suffix), which today falls into `unknownModel` (`:39,45`) and must get a row that *allows* forced tool choice. Easy to conflate.
6. **GAP: the gate script `allowedEdges.getValue(modulePath)` throws `NoSuchElementException` for any module not in the map** (`gradle/invariants.gradle.kts:272,291`), and `:voice-adapter`'s dependency on `:stt` is an *external* coordinate, which `verifyModuleGraph` (project edges only, `:277-281`) cannot see. Both need new gates (section 8).
7. **GAP: `commitHeld` child runs and clarification-reply runs are indistinguishable by ids.** Both set `parentRunId` (`HeldCommit.kt:60` vs `CommandInput.parentRunId`). "Undo all (N)" grouping for a held-then-confirmed command needs an exact marker. Recommend an additive `ActionEvent.heldRunId: String?` (internal ctor today, `CommitSink.kt:29`).
8. **`:undo` integration must be an app/sample-side bridge, not a `:core` seam.** `:undo` depends on nothing and `:core` must not depend on `:undo`; the existing `CommitSink` + `PendingMutation.context/targetIds` + `runId` already carry everything. Section 6.
9. **`:voice-adapter` must be an Android library** (the `:stt` artifact is an AAR; a `kotlin.jvm` module cannot resolve it) and needs a JitPack repo entry in `settings.gradle.kts`, which today forbids project repos (`repositoriesMode FAIL_ON_PROJECT_REPOS`). Section 7.

---

## 2. System overview (as built at v1.0.1, with v1.1 additions marked)

```
 App composition root                      (apps wire everything; no DI in the library)
 ┌────────────────────────────────────────────────────────────────────────────────────┐
 │ commandPipeline { tier(...); gate; commitSink; selector; policy; providerSelection }│
 └───────────────────────────────┬────────────────────────────────────────────────────┘
                                 │ CommandPipeline.execute(CommandInput)       :core
 ┌───────────────────────────────▼────────────────────────────────────────────────────┐
 │ drive() -> runCommand -> withDeadline -> walk()                  CommandPipeline.kt │
 │   PolicyPreCheck.check(policy)  -> Ladder(tiers, selector, onDevice, refusal)       │
 │                                    + [v1.1] cappedByPolicy                          │
 │   TierWalk.run(input)           -> [v1.1] pre-pass + picker, else Linear/Fixed      │
 │      runTier -> RunSession(scope, id, declared providers, carry)                    │
 │         strategy.execute(input, session)                                            │
 │   ┌────────────────┬──────────────────┬─────────────────┬──────────────────┐        │
 │   │[v1.1] LocalGram│ SingleShot       │[v1.1] PlanThen  │ AgenticLoop      │ tiers  │
 │   │ NO_PROVIDER    │ 1 forced-tool    │ Execute         │ multi-turn       │        │
 │   └───────┬────────┴────────┬─────────┴────────┬────────┴────────┬─────────┘        │
 │           │ session.submit(ToolStep)  (ONLY write path)           │ session.model()  │
 │   ┌───────▼───────────────────────────────┐        ┌──────────────▼───────────────┐  │
 │   │ CommitCoordinator (mutex, serialized) │        │ ModelRouter.bind -> BoundModel│  │
 │   │  GateStep -> ApplyStep -> ActionLedger│        │  RoutedModel.complete        │  │
 │   │  ActionDelivery -> CommitSink.onAction│        │  -> recorder.turnRecorded    │  │
 │   └───────────────────────────────────────┘        └──────────────┬───────────────┘  │
 │   RunRecorder (trace, codes, tokens, events)  <───────────────────┘                  │
 └──────────────────────────────────────────────────────────────────┬──────────────────┘
                                                                    │ AiProvider.complete
 :providers  encodeAnthropicRequest / encodeChatRequest (pure fn of ProviderRequest)    │
             AnthropicModels / OpenAiModelRules / ChatErrors  (capability + wire rules) ▼
 :keystore   ApiKeyStore -> KeystoreCredentialSource (CredentialSource)

 Standalone / bridging (v1.1):
   :undo           (depends on NOTHING)   <---- app/sample bridge: CommitSink -> UndoJournal
   :voice-adapter  (Android lib) --api--> :core, --api--> :stt (external AAR)
```

### Seams v1.1 hooks into (all verified in source)

| Seam | Where | v1.1 use |
|---|---|---|
| Single write path `CommandSession.submit` -> `CommitCoordinator.submit` | `CommandSession.kt:35`, `CommitCoordinator.kt:55` | Grammar and Plan submit through it unchanged (gate, ledger, sink); `providerCallId` rides an internal overload |
| `OutcomeResolver` + `Resolution` | `OutcomeResolver.kt:17,47` | Grammar reuses it verbatim; `Resolution.NoMatch` -> `StrategyOutcome.NoMatch` (`SingleShotOutcomes.kt:60`) |
| `ToolExecutor.prepare` + `ToolStep` | `ToolExecutor.kt:25` | Plan runs steps through it, one at a time |
| `TierWalk.hasWorked` / `suppressed` | `TierWalk.kt:111,117` | Gives Plan "no Escalate after first commit" for free |
| `RunRecorder` (trace/events/tokens) | `RunRecorder.kt` | `carryIn`, picker pseudo-attempt, `router_fallback` |
| `Ladder`/`PolicyPreCheck` | `PolicyPreCheck.kt:15,39` | `cappedByPolicy`, eligible-LLM-tier computation |
| Encoders as pure functions of `ProviderRequest` | `AnthropicEncoder.kt:53`, `ChatEncoder.kt:54` | `ReasoningMode` read from `call.request` |

---

## 3. Wave-1 seams (Phase 12): exact integration

### 3.1 `onFailed` hook (SingleShot, then Plan)

**Today.** `failureOutcome` (`singleshot/SingleShotOutcomes.kt:31-36`):

```kotlin
when (failure.reason) {
    is FailureReason.NoToolCall -> hooks.onNoToolCall(null)
    is FailureReason.Refusal   -> hooks.onRefusal(null)
    else -> StrategyOutcome.Failed(failure.reason, failure.details)   // :35  <- the only line that changes
}
```

`OutcomeHooks` (`:15-18`) carries two hooks; `SingleShotStrategy` builds it at `:72`; the public hooks live on `Builder` at `:201` and `:208`.

**Change (modified, no new files).**
- `OutcomeHooks` gains `val onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome`.
- `:35` becomes `else -> hooks.onFailed(failure.reason, failure.details)`.
- `SingleShotStrategy.kt:72` passes `settings.onFailed`; `Builder.onFailed` defaults to `{ r, d -> StrategyOutcome.Failed(r, d) }` (so unset == v1.0.1 behavior byte-for-byte).

**Why this satisfies "provider failures only".** `failureOutcome` is reached only from `decideResult` for a `ModelResult.Failure` (`SingleShotOutcomes.kt:26`). The other `Failed` producers never go through it: token ceiling (`ceilingReached`/`ceilingCrossed`, `SingleShotStrategy.kt:75,123`), binding refusal (`model.refusal`, `:91`), `single_shot_tool_missing` (`:82`), `stopFailure` for truncation (`SingleShotOutcomes.kt:42`), gate faults (coordinator, never a strategy outcome) and `strategy_error` (collapsed in `TierWalk.executeGuarded`, `TierWalk.kt:56-66`). Keep it that way; add one test per excluded path asserting the hook is NOT invoked.

**Plan reuse.** `OutcomeHooks`/`decideResult` are `internal` in package `strategy.singleshot`; `PlanThenExecuteStrategy` (same module) imports them. Do not copy. (Optional tidy: move to `strategy/` in the Phase 14 helper extraction, section 4.)

**Hook throw safety.** A throwing hook is already contained: `TierWalk.executeGuarded` turns it into `Failed(Unexpected)` + `strategy_error` (`TierWalk.kt:60-66`); cancellation propagates.

### 3.2 `ReasoningMode`: flow and "wire defaults unchanged"

Path: `Builder.reasoning` -> `ModelRequest.reasoning` -> `ProviderRequest.request` -> encoder.

| Step | Site | Change |
|---|---|---|
| New type | `core/transcript/ReasoningMode.kt` (NEW) | `@JvmInline value class ReasoningMode internal constructor(val value: String)` with `OFF`, `PROVIDER_DEFAULT` in the companion (same open-set pattern as `TraceCode.kt:11`, `ActionKind.kt:11`) |
| Request | `ModelRequest.kt:24` | Make the 8-arg ctor primary (`reasoning` last); **re-declare the 7-arg ctor explicitly** delegating `OFF` (the existing 6/3/4-arg ctors at `:34,44,48` keep delegating to it). No Kotlin default argument (it would drop the 7-arg JVM ctor). `toString` adds `reasoning` |
| SingleShot | `SingleShotStrategy.kt:99-107` | pass `settings.reasoning`; `Builder.reasoning: ReasoningMode = OFF` |
| Agentic | `AgenticLoopStrategy.kt:94,172-181` | `AgenticRun` (internal ctor) takes `reasoning`; `Builder.reasoning = OFF` |
| Anthropic encoder | `AnthropicEncoder.kt:53-69` | **no change**; it already omits `thinking`. Add a golden asserting `OFF` bytes == v1.0.1 bytes |
| Chat encoder | `ChatEncoder.kt:72` | `rules.reasoningEffortWithTools?.let { put(KEY_REASONING_EFFORT, it) }` stays the single emit point; wrap in `when (request.reasoning)` where `OFF` and `PROVIDER_DEFAULT` both resolve to `wireRules` in v1.1 (brief: "no on-wire change at v1.1.0") |

**Decision D-REASON (recommend):** in v1.1 both values emit identical bytes; the knob exists to pin today's default as a tested contract and to give a later version room to add values without a new ctor. Do NOT make `PROVIDER_DEFAULT` omit `reasoning_effort` on Chat: on gpt-5.4+/gpt-6 the rule value `"none"` is what lets the endpoint accept tools (`OpenAiModelRules.kt:12,86-87`), so omitting it would turn a knob into a guaranteed 400. Cache impact: none (`reasoning_effort` sits after `tool_choice`, before the output-limit key, per the encoder KDoc `ChatEncoder.kt:37-41`; unchanged).

### 3.3 W04 fix (same code as 3.2, plan together)

- **Wire rule (`OpenAiModelRules.kt:80-93`).** Insert, before the `GPT_6_FAMILY || isLaterGpt5` branch (`:86`), `!viaRouter && RESPONSES_ONLY.matches(id) -> completion` (no effort). Via router the existing first branch (`:84`, effort `low`) is unchanged. Add an encoder golden: `gpt-6-astra` and `gpt-6.1-sol` direct, with tools, never contain `"reasoning_effort":"none"`. Then enumerate every id the table maps to `EFFORT_NONE` and assert none is Responses-only (research item: "every model that rejects none").
- **Classifier (`ChatErrors.kt:137-146`).** Add a `refine` arm: `status == 400 && code == "unsupported_value" && textField(error, "param") == "reasoning_effort"` -> `FailureReason.ModelUnsupported()`. Read `param` into a local only (the file's discipline: "the body and the error message are never stored", `:62-64`). Place it before the generic arms but after `quota`. MockWebServer replay of the captured body on all three OkHttp legs (`providers/build.gradle.kts` matrix).
- No API change; `FailureReason.ModelUnsupported` exists.
- **Interaction:** after the wire fix, direct `gpt-6-astra` with a `supportsTools` override sends no effort and the endpoint answers the Responses-only message, which `isUnsupportedEndpoint` (`:151-156`) already maps to `ModelUnsupported`. The new `refine` arm is the safety net for the exact captured shape and for any future id that rejects `none`.

### 3.4 `claude-sonnet-5` capability row

`AnthropicModels.kt:42-46` is an exact-id `when`. Add `private const val SONNET_5 = "claude-sonnet-5"` and a dedicated `ModelCapabilities` (forced tool choice allowed = default true, `caching = EXPLICIT_BREAKPOINTS`, `minCacheablePrefixTokens` from Anthropic's docs at plan time: **open item**, do not guess). Internal only; no api.txt change. Keep `SONNET_5_5` untouched.

### 3.5 `TierAttempt.carryIn`

- Public: `TierAttempt.carryIn: Boolean` (`CommandTrace.kt:53`; ctor is internal; `TierAttempt` is constructed in exactly two places, `RunRecorder.kt:89,106`). Add to `toString`.
- **Plumbing (better than the brief's "via tierFinished"):** record it at *start*, not finish. `TierWalk.kt:75` calls `recorder.tierStarted(strategy.id)` while `carry` is in scope (`:76`). Change to `tierStarted(strategy.id, carryIn = carry != null)`; store it in `TierBook` next to `inFlight` (`RunRecorder.kt:163-184`); `close()` reads it. This also covers `flushInFlight` (`:100-111`, the timeout/cancel path), which never sees a `tierFinished` call. Changing `tierFinished`'s signature would miss that path and touch five call sites (`TierWalk.kt:79,83,94,101,119`).

### 3.6 `Unhandled.cappedByPolicy`

- The two skip sites are `PolicyPreCheck.kt:50` (tiers cut by `maxTier`) and `:53` (tiers refused by `permits`). Compute `capped = within.size < strategies.size || eligible.size < within.size` in `check` and add `val cappedByPolicy: Boolean` to `Ladder` (`:15`, internal).
- `TierWalk.kt:44`: `CommandOutcome.Unhandled(effects(), lastReason, ladder.cappedByPolicy)`; `Unhandled` (`CommandOutcome.kt:134`, internal ctor) gains `public val cappedByPolicy: Boolean` and a `toString` field.
- Semantics to document: `Unhandled` is only produced when at least one tier ran and all handed up (`TierWalk.kt:44`). When policy leaves **no** eligible tier the outcome is `Failed(NoEligibleTier | offline_unavailable)` (`PolicyPreCheck.kt:59,64-72`, `TierWalk.kt:41-43`), never `Unhandled`; so the SB offline-only case works only because a `NO_PROVIDER` grammar tier stays eligible. State both in the KDoc (SB condition: "true for every `tier_skipped_policy` case, offline-only included").
- Optional parity: add the same field to `RunTermination.Exhausted` (`RunTermination.kt:86`, mapped at `CommandPipeline.kt:231`) so a `CommitSink` sees it. Cheap, additive.

### 3.7 `Extraction.callId` and `ExecutedAction.providerCallId`

Flow (all NEW/MODIFIED unless noted):

```
ToolCall.id (AssistantPart.kt:31, exists)
  ├─ SingleShot :141  Extraction(name, args, call.id)        -> app resolver (may read callId)
  │   └─ :149 submitAll(session, steps, call.id) ──┐
  └─ AgenticDispatch :131 Extraction(name,args,call.id)      │
      └─ :100 session.submit(step, call.id) ────────────────┤
                                                              ▼
 CommandSession.submit(step, providerCallId)  [internal open, default = submit(step)]
   RunSession override -> scope.coordinator.submit(step, providerCallId)   (RunSession in RunSession.kt:59)
 CommitCoordinator.submit(step, id)   (:55)
   finished(step,id) :101   ActionDetails(..., providerCallId=id)   preview/is_error actions
   submitMutation :116 -> applyAll(mutations, id) :167 -> ApplyStep.run(mutation, id) :81
        recordOutcome :99 / journalCancelled :112 -> ActionDetails(.., id)
   hold(step, decision, id) :134 -> HeldProposal(..., providerCallId=id) internal field
                                    + ActionDetails(.., id) for the HELD actions
   gateFault(step, id) :152
 ActionDetails (ActionLedger.kt:15) -> ActionLedger.record :39 -> ExecutedAction(providerCallId)
 HeldCommit.run :71 -> coordinator.applyWithoutGate(mutations, held.providerCallId)  (:67)
```

- `Extraction` (`OutcomeResolver.kt:31`): make the 3-arg `(toolName, arguments, callId: String?)` primary, **re-declare the 2-arg public ctor** delegating `null`. Add `callId` to `toString` only as presence, never the value (house style).
- `ExecutedAction` (`CommitSink.kt:51`, internal ctor): add `public val providerCallId: String?` (trailing). Update the KDoc of `position` ("never a provider's tool-use id", `CommitSink.kt:40-41`): position stays the identity; `providerCallId` is a separate, nullable fact, null for zero-call tiers (grammar) and for Plan steps (no per-step provider call; see Decision D-PLANID).
- Keep `submit(step, id)` **internal**: every v1.1 strategy lives in `:core`, so no frozen public surface is needed. Apps' own custom strategies can still use the public `submit(step)`.
- One id can stamp several actions (one SingleShot call combines all mutations into one proposal, `SingleShotStrategy.kt:149-154`); that is correct: they all came from that call.

### 3.8 `KeyAccess` public + `@DelicateKeyAccess`, default path untouched

Today: `internal interface KeyAccess` (`KeyAccess.kt:9`); `ApiKeyStore` primary ctor is `internal` with 4 args (`ApiKeyStore.kt:36-41`); the two public ctors pass `AndroidKeyStoreKeyAccess` (internal object, `:48-53`, `AndroidKeyStoreKeyAccess.kt:29`). `keystore/api.txt` lists only the 2- and 3-arg (DataStore, slots[, dispatcher]) ctors.

Change (all in `:keystore`):
1. NEW `DelicateKeyAccess.kt`: `@RequiresOptIn(level = ERROR, message = "...") @Retention(BINARY) public annotation class DelicateKeyAccess` (targets: class, constructor, function).
2. `KeyAccess.kt`: `internal interface` -> `@DelicateKeyAccess public interface KeyAccess` (plain `interface`, two members; see headline 3).
3. `ApiKeyStore.kt`: add `@DelicateKeyAccess public constructor(dataStore, slots, keyAccess)` delegating to the internal 4-arg primary with `Dispatchers.IO`. Do **not** change the existing public ctors; they keep pointing at `AndroidKeyStoreKeyAccess`, so the default path is unchanged and requires no opt-in. Leave the 4-arg primary internal (tests keep using it) unless D-KEY4 below is chosen.
4. `AndroidKeyStoreKeyAccess` (internal object implementing a now opt-in interface) and `SecretReader(keyAccess)` need `@OptIn(DelicateKeyAccess::class)` at their declarations (opt-in applies inside the module too).
5. Metalava: additive (new annotation class, new interface, new ctor). Add a compile-fail check that constructing the 3-arg ctor without `@OptIn` does not compile (a Gradle `verify...` task compiling a snippet, or a `kotlinc`-expected-failure test), since "without it, compilation fails" is a success criterion.
6. Docs: the ~10-line software `KeyAccess` fake goes in INTEGRATION.md as a compiled `DocSnippetsTest` region (the docs-coverage script requires regions: `scripts/verify-docs-coverage.sh:32`).

**Decision D-KEY4:** publish only the 3-arg ctor (matches the brief; smallest frozen surface). A JVM test under `runTest` works with `Dispatchers.IO`. Add a 4-arg only if a consumer proves it needs an injected dispatcher.

---

## 4. LocalGrammarStrategy and PlanThenExecuteStrategy (Phases 14, 15)

Both are `CommandStrategy` implementations in `:core` (pure Kotlin, no HTTP: stays inside the `verifyCoreDependencyAllowlist`, `invariants.gradle.kts:335-346`). Neither needs a pipeline change.

### 4.1 Shared refactor first (de-risks 14 ‖ 15)

Extract, as `internal` top-level functions in a NEW `core/strategy/StepSubmission.kt`:
- `resolutionOutcome` (currently `SingleShotOutcomes.kt:54-64`) and `SingleShotStrategy.submitAll` (`:149-154`) -> `submitSteps(session, steps, providerCallId)`.
- From `AgenticDispatch.kt`: `guardWrites` (`:107-123`) and the `prepare` guard (`:127-131`) -> `prepareGuarded(...)`.

This is a pure move (no behavior or API change); land it as the first commit of whichever of 14/15 starts first, so the two phases never edit the same functions. SingleShot and Agentic then call the shared helpers.

### 4.2 `LocalGrammarStrategy` (NEW, `core/strategy/grammar/`)

| Piece | Notes |
|---|---|
| `GrammarPack` DSL (public) | per-language rules -> intent/tool + typed slots; EN and ES; builder + `GrammarPack { language("en") { rule(...) } }`. Public builders with `internal` ctors; explicit-API strict |
| Slot types + number words | `internal` EN/ES number-word parsers (compound ES forms are a research item). Per-slot `normalize: (raw, language) -> String?` runs **before** typed parse and resolution |
| `LocalGrammarStrategy(id) { pack; resolver }` | `capabilities = StrategyCapabilities.NO_PROVIDER` (`StrategyCapabilities.kt:31`), so `PolicyPreCheck.permits` always passes it (`PolicyPreCheck.kt:82`: `providers.isEmpty()`), including `offlineOnly` and any `allowedProviders` |

Execution (mirrors SingleShot's tail, no model):

```
execute(input, session):
  match = pack.match(input.transcript, input.language)   // pure; null language => try both packs,
                                                         // ambiguous (>1 distinct result) => NoMatch, never guess
  if (match == null) return StrategyOutcome.NoMatch()    // TierWalk.startFresh clears carry (TierWalk.kt:100-105)
  resolution = resolver.resolve(Extraction(tool, argsFromSlots, callId = null), input)
  return resolutionOutcome(resolution) { submitSteps(session, it, providerCallId = null) }
```

- `session.model()` is never called -> zero turns, `TierAttempt.provider == null`, `providerCallId == null`. Add a `NoNetworkGuard` test (testFixtures already has one, `core/src/testFixtures/.../NoNetworkGuard.kt`).
- Writes use the identical gate -> commit -> sink path via the shared helper; a held grammar action is reported held (`DispatchResult.held`), `Completed` is returned exactly as SingleShot does.
- `Resolution.NoMatch` from the app resolver -> `StrategyOutcome.NoMatch` -> next tier with carry cleared. If the grammar tier had already applied something, `TierWalk` suppresses (`TierWalk.kt:88-89`). No new pipeline code.
- `OutcomeResolver`'s KDoc already reserves "an intent and slots from a grammar tier" as future `Extraction` members (`OutcomeResolver.kt:25`). v1.1 does not need them: pass typed slot values as `JsonPrimitive`s in `arguments`. Add `intent/slots` only if Phase 14 proves a resolver needs them (additive, as a `with...` member).
- Domain-freedom: the CLN-02 scanner (`invariants.gradle.kts:47-49`) scans `main`; example packs live in `:sample`/tests only.

### 4.3 `PlanThenExecuteStrategy` (NEW, `core/strategy/plan/`)

Dependencies reused: `ToolSpecProvider` (tools), `ToolExecutor` (steps), `session.model()`, the shared `prepareGuarded`/`submitSteps`, `OutcomeHooks` (for `onFailed`), `ceilingReached/Crossed`.

Flow:

```
execute:
  ceilingReached ?: tooling = ToolSpecProvider.tooling(input)
  model = session.model(); model.refusal -> Failed
  plan = model.complete(ModelRequest(system, [user], tools = [engine-owned submit_plan tool], Required(submit_plan), ..., reasoning))
         decideResult(result, hooks)  // shared: provider failure -> hooks.onFailed (SC 5), no-tool-call/refusal hooks
  validate plan BEFORE running anything:
     unknown tool name / malformed -> Escalate(MalformedExtraction)
     any step whose ToolSpec.mutating == false and !terminal (a lookup/read) -> Escalate(<needs-lookup>)   // contract §4; detection is static, from the snapshot
  for step in steps (strictly sequential):
     args = bind(step.arguments, committedStepOutputs)          // §4.4
     prepared = prepareGuarded(executor.prepare(Extraction(tool, args, callId=null), input))
     dispatch = session.submit(prepared, providerCallId = null)  // each step is its own gate proposal
     if dispatch.isError or binding failed -> failedStep
  failedStep: at most ONE replan model call (ceilingReached first), then Escalate. 
```

- **One gate proposal per step, not one combined proposal** (unlike SingleShot, `SingleShotStrategy.kt:151-153`): a later step can only bind to an earlier step's write output after that write ran, so steps must submit sequentially. A held step ends the independent chain: later steps that bind to it cannot resolve, so the tier returns `Escalate`; because `hasWorked()` is true (held counted, `TierWalk.kt:111`) the walk converts it to a partial `Completed` + `escalation_suppressed` (`TierWalk.kt:117-121`). That is exactly SC 4; add the Plan-specific test.
- **Model-call accounting.** The planning call and the replan go through `session.model().complete`, so `RoutedModel` records each turn (`BoundModel.kt:108`) and the trace shows exactly how many calls ran (SC 3). The replan is guarded by `ceilingReached(session)`.
- **Engine-owned `submit_plan` ToolSpec:** non-mutating, not terminal, distinct name (`ModelRequest` already enforces distinct tool names, `ModelRequest.kt:60-61`). Its `steps[].tool` should be an enum of the snapshot's mutating tool names. Schema/binding syntax is the Phase 15 research item.
- **Decision D-PLANID:** Plan steps get `providerCallId = null`. The plan is one provider call but not one tool call per step; fabricating per-step ids would misstate provenance. `position` already orders them.

### 4.4 Write-output binding via `ExecutedAction.targetIds`

`DispatchResult.actions` (`ToolStep.kt:111-120`) hands the strategy the recorded `ExecutedAction`s of the step it just submitted; `targetIds` is `mutation.targetIds + result.targetIds` (`ApplyStep.kt:101-104`, result wins). So binding = after each `submit`, keep `outputs[stepIndex] = dispatch.actions.filter { it.kind == ActionKind.COMMITTED }.flatMap { targetIds }`; a reference `{step: i, key: "noteId"}` in a later step's arguments is replaced by `outputs[i][key]` before `prepareGuarded`.

Rules (these make "a binding that doesn't resolve fails that step"):
- Only `COMMITTED` actions are bindable. A `HELD` action's `targetIds` come only from the pre-apply mutation descriptor (`CommitCoordinator.kt:134-141`), `PREVIEW`/`IS_ERROR` are not writes; never bind to them.
- A reference to a step that is not earlier, to a missing key, or to a step that never committed -> `bindingFailed` -> counts as a failed step (one replan, then `Escalate`).
- Resolve in `JsonObject` space (kotlinx.serialization tree); never string-substitute.
- The bound values are app ids; they must not reach telemetry (`TurnRecord`/`PipelineEvent` carry names and counts only).

---

## 5. `TierSelector.Custom(StartTierPicker)` and Router (Phase 16)

### 5.1 Why it does not fit `TierWalk.run` today

`TierWalk.run` (`TierWalk.kt:39-45`) calls `ladder.selector.startIndex(ids)` synchronously and returns `Failed(NoEligibleTier)` on null (`:41-43`). `TierSelector` has an `internal constructor()` (`TierSelector.kt:12`) and `internal abstract fun startIndex(eligible): Int?` (`:14`); nothing there can suspend, see the input, or call a model. `Linear` and `Fixed` stay exactly as they are (do not touch `startIndex`; it keeps both built-ins byte-identical).

### 5.2 Design (additive public, internal restructure)

Public (all NEW, in `pipeline/`):

```kotlin
public fun interface StartTierPicker {
    public suspend fun pick(input: CommandInput, eligible: List<StrategyId>, ctx: PickContext): StrategyId?
}
public abstract class PickContext internal constructor() {   // mirrors CommandSession MINUS submit
    public abstract val runId: String
    public abstract val policy: TierPolicy
    public abstract val tokensUsed: Long
    public abstract suspend fun model(): BoundModel
    public abstract suspend fun recordTurn(turn: TurnRecord)
}
// nested in TierSelector:
public class Custom(public val picker: StartTierPicker, public val id: StrategyId = StrategyId("start-tier-picker"),
                    public val capabilities: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER) : TierSelector()
public class Router(...) : TierSelector()   // built on the same internal path; off unless the app sets it
```

**No `submit` on `PickContext`**: a picker (app code or the Router's classifier) can therefore never write, so the gate invariant ("a strategy never reaches `apply` any other way", `CommitCoordinator.kt` KDoc) cannot be bypassed by a selector.

`TierSelector` stays non-sealed with an internal ctor (KDoc already warns to keep an `else`, `TierSelector.kt:5-9`); `Custom`/`Router` extend an `internal abstract class PickingSelector : TierSelector()`.

### 5.3 The suspend pick path inside `TierWalk`

Replace `TierWalk.run`'s first lines with a branch (everything below `climb` is unchanged):

```
run(input):
  if (ladder.refusal != null) -> Failed(...)                              // unchanged :41-43
  val picking = ladder.selector as? PickingSelector
  if (picking == null) -> start = selector.startIndex(ids) ?: Failed; return climb(ladder.tiers.drop(start))   // Linear/Fixed: unchanged
  return walkWithPicker(picking, input)

walkWithPicker:
  head  = ladder.tiers.takeWhile { it.capabilities.providers.isEmpty() }     // zero-call head tiers (grammar)
  runHead = climb(head, input)           // free pre-pass; a handled/failed/suppressed outcome ends the walk
  if (runHead != null) return runHead
  rest = ladder.tiers.drop(head.size)
  llm  = rest.filter { it.capabilities.providers.isNotEmpty() }.map { it.id }   // NEVER contains a zero-call tier
  if (llm.isEmpty()) { recordCode(ROUTER_FALLBACK); return climb(rest) ?: Unhandled(...) }   // picker NOT called, no model call (SB condition)
  choice = pickGuarded(picking, input, llm)                                  // §5.4
  idx = rest.indexOfFirst { it.id == choice }.takeIf { it >= 0 }
  if (idx == null) { recordCode(ROUTER_FALLBACK); idx = 0 }                   // null / ineligible / throw => Linear
  return climb(rest.drop(idx)) ?: Unhandled(...)
```

- Offline-only: `PolicyPreCheck` already removes provider-backed tiers (`PolicyPreCheck.kt:82`), so `llm` is empty and the picker is never called (SC 4). `maxTier`/`allowedProviders` are likewise already applied before the picker sees `eligible`.
- Pre-pass uses the existing `runTier`, so gate/suppression/trace behave identically; `hasWorked()` after a grammar partial still yields `escalation_suppressed`.
- Fixed/Linear tiers above the chosen start that are zero-call but mid-ladder simply run as part of `climb(rest.drop(idx))`.
- `Unhandled` creation must use the same `cappedByPolicy` (3.6); factor a `unhandled()` helper.

### 5.4 `pickGuarded`, budget, trace (resolves headline 2)

```
pickGuarded(picking, input, llm):
  val ctx = RunPickContext(scope, picking.id, picking.capabilities.providers)   // wraps a RunSession(scope, picking.id, declared, carry=null)
  recorder.tierStarted(picking.id, carryIn = false)                              // pseudo-attempt so turns land in the trace
  val picked = guarded(onFault = { null }) { picking.picker.pick(input, llm, ctx) }   // guarded (Guarded.kt:38): cancellation propagates
  recorder.tierFinished(picking.id, outcome = if (picked in llm) "picked" else "router_fallback", ...)
  return picked
```

- **Budget:** `ctx.model()` binds through `scope.router.bind(picking.id, declared, policy, recorder)` (`RunSession.kt:74`, `ModelRouter.kt:86`) and `RoutedModel.complete` calls `recorder.turnRecorded` (`BoundModel.kt:108`) -> `tokenTotal` -> `session.tokensUsed` for every later tier, so `ceilingReached` (`StrategyLimits.kt`) charges the router's tokens against the run's `tokenCeiling` (SC 1).
- **Trace:** the pseudo-attempt makes the picker's turns visible in `trace.attempts` and in `CommandTrace.usage` (sum of attempts, `CommandTrace.kt:32`). Consumers that render "handled by" must skip it; document that the picker id is never a ladder tier id and add `TierAttempt`'s outcome vocabulary entries (`picked`, `router_fallback`). `PipelineBuilder.build` must `require` the picker id does not collide with a tier id (extend the check at `PipelineBuilder.kt:113-115`).
- **App seam obligation:** `ProviderSelectionSource.select(SelectionRequest(strategy))` is asked for the picker's `StrategyId` (`ModelRouter.kt:128`), so the app must answer for it. If it answers null, `bind` returns a refused handle (`provider_not_selected`), the picker sees `model().refusal`, returns null, and the walk records `router_fallback`. Document this in INTEGRATION (it is the Router's "default cheap model comes from policy/selection, never hard-coded").
- **`router_fallback`** is a new `TraceCode` (`TraceCode.kt`, open set, additive). Optional second code `picker_error` for the throw case (diagnosability); the brief only requires `router_fallback`.
- **Telemetry for "tiers saved vs Linear" (ROUT-05):** count it as *tiers bypassed* = number of eligible LLM tiers below the chosen one (a static count; Linear is never run). Surface via a NEW `PipelineEvent.TierPicked(runId, picker, picked, bypassed)` (open-set interface, `PipelineEvent.kt:15`) and the pseudo-attempt. Name it "bypassed", not "saved": Linear might have stopped earlier, so a savings claim would be unprovable.
- **Router (engine)** = a `StartTierPicker` that calls `ctx.model().complete(...)` with an engine-owned forced `pick_start_tier` tool whose `tier` is an enum of `eligible`. Its model/provider come from the app's selection for the picker id; classifier prompt text is a Phase 16 research item. Off by default: `PipelineBuilder.selector` stays `Linear` (`PipelineBuilder.kt:44`).

---

## 6. `:undo` and pipeline integration (Phase 17)

### 6.1 The module

- NEW `:undo`, `kotlin.jvm`, **stdlib only** (no coroutines, no `:core`; use `suspend` as a language feature and `synchronized` rather than `Mutex`). Publishes jar `voice-action-engine-undo` (E7). Packaging copies `:providers` (jar, `from(components["java"])`, `providers/build.gradle.kts`).
- API shape (Phase 17 research item, sketch): `EntityAdapter<S>` (read, write back, re-insert), `Compensator`, `UndoJournal` (open scope by `runId`; `capture(entityType, id)` before write; `seal(...)` after commit storing a post-commit fingerprint), `UndoPlan`/`UndoResult { restored, refused: List<Refusal> }` (never silently partial), footprint-based isolation computation.
- Gates: a zero-dependency allowlist task (new, section 8) asserting `:undo`'s `compileClasspath`/`runtimeClasspath` contain only `kotlin-stdlib` (+ `org.jetbrains:annotations`); `allowedEdges[":undo"] = emptySet()`.

### 6.2 Inversion: how the pipeline "integrates" without `:core -> :undo`

The engine already exposes everything a journal needs, by `runId`:
- `CommitSink.onAction(ActionEvent(runId, parentRunId, action))` is awaited before the next change and runs under `NonCancellable` (`ApplyStep.kt:55-62`, KDoc `CommitSink.kt:6-8`), so it is the right moment to **seal** a journal entry after the write.
- `PendingMutation.apply()` is the only place that is both app code and *before* the write, and the KDoc already warns a held change applies "against state that has moved on" (`ToolStep.kt:63-66`). Capture of the before-state therefore belongs inside `apply()` (app side), not at prepare time.
- `ExecutedAction.targetIds` / `context` (opaque, by identity) / `providerCallId` identify and tag the entry; `runId`+`parentRunId` group it.

**Decision D-UNDO (recommend): the bridge is app/sample-side code** (`UndoCommitSink : CommitSink` ~40 lines: on `COMMITTED` actions seal the journal entry for `event.runId`; on `onRunClosed` finalize the group), shipped as the reference wiring in `:sample` and as a compiled `DocSnippetsTest` region in INTEGRATION. `:core` gains **no** `:undo` knowledge and **no** new undo API. Optionally add a tiny additive `CommitSink` fan-out helper in `:core` (`compositeSink(a, b)`) so an app that already has a sink can add the journal; skip if SB/CT already compose their own.

Alternative (only if Yahir wants turn-key): a 6th published module `voice-action-engine-undo-bridge` (depends on `:core` + `:undo`). It satisfies both directions of the dependency rule but needs a contract amendment (A7-style) and a sixth install-list entry and coordinate. Flag at Phase 17 discuss; default is the bridge-in-app.

**Decision D-HELD (recommend, additive, Phase 12 or 17):** add `ActionEvent.heldRunId: String?` set only for actions applied by `commitHeld` (known at `HeldCommit.open`, `HeldCommit.kt:58-62`). Without it, grouping "Undo all (N)" for hold-then-confirm cannot tell a `commitHeld` child from a clarification-reply child (both carry `parentRunId`). Group key = `heldRunId ?: runId`.

`UNDO-04` therefore means: INTEGRATION + `:sample` wiring + an integration test where a real pipeline run (`FakeAiProvider`, `RecordingCommitSink` from testFixtures) feeds `:undo` through the bridge sink and "Undo all (N)" reverts a multi-action command including a held-then-confirmed one. `:sample` gets `implementation(project(":undo"))` (extend `sampleAllowedEdges`, `invariants.gradle.kts:274`).

---

## 7. `:voice-adapter` (Phase 18) placement and direction

```
:voice-adapter --api--> :core                          (CommandInput is public in :core)
:voice-adapter --api--> com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0   (:stt, EXTERNAL AAR)
:core / :providers / :keystore / :undo  --X--> :stt    (must never resolve it)
```

- **Direction:** adapter depends on both; nothing depends on the adapter. An app that does not add `:voice-adapter` never resolves `:stt` (SC 2). The adapter is the *only* module allowed an `:stt` edge, and `allowedEdges[":voice-adapter"] = setOf(":core")` (project edges).
- **Packaging:** Android library (`com.android.library`, AGP built-in Kotlin, `singleVariant("release") { withSourcesJar() }`), copied from `keystore/build.gradle.kts:1-33,106-118`. Reason: `:stt` publishes an AAR (`stt/build.gradle.kts`: `com.android.library`, `from(components["release"])`); a `kotlin.jvm` module cannot select an AAR variant. `minSdk` must be >= `:stt`'s 33; use 35 like `:keystore`. The `verifyBytecodeLevel` gate already handles AARs (`invariants.gradle.kts:221-226`).
- **Coordinate (from the §11 ledger row, contract line 284):** `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0` (group = the mirror repo, artifact = `voice-engine-android`; the build file's literal `groupId = "com.github.Ygaray"`/`version "0.7.0"` is only what `publishToMavenLocal` writes, JitPack rewrites it). Do not pin the aggregator `com.github.Ygaray:voice-engine-android`.
- **Scope:** `api` at the v0.7.0 floor (a plain Gradle minimum, same philosophy as OkHttp A1), because `FinalSegment` appears in the adapter's public signature. Do not use `strictly`.
- **Repo wiring (not present today):** `settings.gradle.kts` `dependencyResolutionManagement` allows only `google()` + `mavenCentral()` and forbids project repos. Add `maven("https://jitpack.io") { content { includeGroup("com.github.Ygaray.voice-engine-android") } }` (content filter = supply-chain hygiene).
- **API sketch:** `public fun FinalSegment.toCommandInput(context: Any? = null, parentRunId: String? = null): CommandInput` (declared with explicit overloads, no reliance on default-arg JVM ctor behavior) mapping `language` strictly: `"en"`/`"es"` pass through, **anything else (including `null`) -> `null`, never a guess** (`CommandInput.language` contract `CommandInput.kt:9`). Confirm `FinalSegment.language`'s value set (`stt/.../FinalSegment.kt:32-37`) and whether it can carry region tags such as `en-US` (normalize `en-*`/`es-*` only if stt documents them).
- **Gates:** a new external-coordinate deny gate on every module except `:voice-adapter` (section 8), plus a positive assertion that `:voice-adapter` resolves `:stt` at >= v0.7.0.

---

## 8. "Add a published module" plumbing checklist (owner: Phase 17 lands it first; 18 and 13-if-green rebase)

Every item below is a concrete edit site found in the repo. A missing one is discovered late (JitPack, release script), so make this a single plan with a verification checklist.

| # | File | Edit |
|---|---|---|
| 1 | `settings.gradle.kts:25` | `include(":core", ":providers", ":keystore", ":undo", ":voice-adapter", ":sample")`; for `:voice-adapter`, add the JitPack repo entry (section 7) |
| 2 | `<module>/build.gradle.kts` (NEW x2) | `:undo`: copy `providers/build.gradle.kts` skeleton (kotlin.jvm, explicitApi, JVM 11, `-Xjdk-release=11`, `maven-publish`, detekt, metalava, `artifactId = "voice-action-engine-undo"`, `apply(from = invariants)` at the end). `:voice-adapter`: copy `keystore/build.gradle.kts` (AGP lib, `singleVariant`, `afterEvaluate { from(components["release"]) }`) |
| 3 | `jitpack.yml:6` | extend the **single** `./gradlew` line with `:undo:publishReleasePublicationToMavenLocal :voice-adapter:publishReleasePublicationToMavenLocal` (+ the on-device module if Phase 13 is green). Never name `:sample` |
| 4 | `gradle/invariants.gradle.kts:272` | `allowedEdges`: add `":undo" to emptySet()`, `":voice-adapter" to setOf(":core")`. Without it `allowedEdges.getValue(modulePath)` (`:291`) throws for the new module |
| 5 | `gradle/invariants.gradle.kts:273-274` | `sampleAllowedEdges += ":undo"` (and `":voice-adapter"` only if the sample uses it; do not add to `sampleRequiredEdges` unless the sample must) |
| 6 | `gradle/invariants.gradle.kts` (NEW tasks) | (a) `verifyUndoZeroDeps`: `:undo` resolved `compileClasspath`/`runtimeClasspath` components must be within `{kotlin-stdlib, org.jetbrains:annotations}`, no `ProjectComponentIdentifier`, not vacuous; (b) `verifyNoSttOutsideAdapter`: on every published module except `voice-adapter`, no resolved component with group `com.github.Ygaray.voice-engine-android`; `verifyAdapterHasStt` positive on the adapter. Pattern: copy `verifyNoDi` (`:310-329`) |
| 7 | `gradle/invariants.gradle.kts:335-346` | **no change** to `coreAllowed`: `:core` gains no dependency (this is the gate that proves the inversion in section 6) |
| 8 | `<module>/api.txt` | created at the Phase 20 dump (`apiDump`); `verifyApiDumpPresent` (`build.gradle.kts:60-76`) makes a missing dump a hard failure once a `v*` tag exists, so new modules need their dump committed *with* the cut, not before (pre-tag the compat check is skipped by `onlyIf api.txt exists`, `build.gradle.kts:49-51`) |
| 9 | `scripts/` hard-coded lists | `release-cut.sh:82` (`MODULES`), `:315` and `:327` (allowed-changed-paths include `core|providers|keystore/api.txt`), `:694`, `:801`, `:917-921`; `verify-api-dump.sh:23,40`; `api-dump-isolated.sh:16`; `review-api-surface.sh:33`; `jitpack-dry-run.sh:39-40,45` (exact artifact-set equality + `spec` list with `jar`/`aar`); `jitpack-live-probe.sh:9,18,89`; `jitpack-consumer-probe.sh:58,72`; `verify-repo-hygiene.sh:27,38,44,60`; `agent-wiring-test.sh:117,125-128,223` (the coordinate regex `(core|providers|keystore)`); `verify-docs-coverage.sh:101,118,139` (+ `REQUIRED_REGIONS`, `:32`) |
| 10 | `ECOSYSTEM.md` coordinates table | add the two rows (and the "Planned for v1.1, not yet published" sentence becomes past tense); README install section and INTEGRATION steps |
| 11 | `config/negative-controls/` | none needed unless a new banned construct is added; `verifyInvariantScannerControls` still runs once on `:core` |
| 12 | Metalava/detekt | applied via the module `plugins {}` block; `buildUponDefaultConfig`/zero baseline is wired for all subprojects by `build.gradle.kts:29-46` automatically |

---

## 9. New vs modified, by module

### `:core`

| Status | Component | File(s) |
|---|---|---|
| NEW | `ReasoningMode` | `transcript/ReasoningMode.kt` |
| NEW | `StartTierPicker`, `PickContext`, `TierSelector.Custom`, `TierSelector.Router`, internal `PickingSelector`, `RunPickContext` | `pipeline/StartTierPicker.kt`, `pipeline/TierSelector.kt` (nested), `pipeline/RunSession.kt` (impl) |
| NEW | `LocalGrammarStrategy`, `GrammarPack`, slot types, EN/ES number words | `strategy/grammar/*` |
| NEW | `PlanThenExecuteStrategy`, plan schema, binder, replan | `strategy/plan/*` |
| NEW (internal move) | shared `submitSteps`, `prepareGuarded` | `strategy/StepSubmission.kt` |
| NEW | `TraceCode.ROUTER_FALLBACK` (+ optional `PICKER_ERROR`), `PipelineEvent.TierPicked` | `telemetry/TraceCode.kt`, `telemetry/PipelineEvent.kt` |
| NEW (test fixtures) | `ScriptedPicker`, `ScriptedPlanResponses`, grammar fixtures | `core/src/testFixtures/.../testing/*` |
| MODIFIED | `ModelRequest` (+`reasoning`, explicit 7-arg ctor kept) | `transcript/ModelRequest.kt:24` |
| MODIFIED | `Extraction` (+`callId`, 2-arg ctor kept) | `strategy/OutcomeResolver.kt:31` |
| MODIFIED | `SingleShotStrategy` (+`onFailed`, `reasoning`, callId pass-through) | `singleshot/SingleShotStrategy.kt:72,99,141,149,201`; `SingleShotOutcomes.kt:15,35` |
| MODIFIED | `AgenticLoopStrategy`/`AgenticRun`/`AgenticDispatch` (+`reasoning`, callId) | `agentic/AgenticLoopStrategy.kt:94,172`; `AgenticDispatch.kt:100,131` |
| MODIFIED | `CommandSession` (+internal `submit(step, id)`), `RunSession` override | `strategy/CommandSession.kt:35`, `pipeline/RunSession.kt:59` |
| MODIFIED | `CommitCoordinator`, `ApplyStep`, `ActionLedger`/`ActionDetails`, `HeldProposal`, `HeldCommit` (callId thread; `heldRunId`) | `commit/*.kt`, `pipeline/HeldCommit.kt:71` |
| MODIFIED | `ExecutedAction` (+`providerCallId`), `ActionEvent` (+`heldRunId`) | `commit/CommitSink.kt:29,51` |
| MODIFIED | `TierAttempt` (+`carryIn`), `RunRecorder` (carryIn at start), `TierBook` | `telemetry/CommandTrace.kt:53`, `RunRecorder.kt:51,100,163-184` |
| MODIFIED | `Ladder`/`PolicyPreCheck` (+`cappedByPolicy`) | `pipeline/PolicyPreCheck.kt:15,50,53` |
| MODIFIED | `CommandOutcome.Unhandled` (+`cappedByPolicy`), optional `RunTermination.Exhausted` | `pipeline/CommandOutcome.kt:134`, `commit/RunTermination.kt:86`, `CommandPipeline.kt:231` |
| MODIFIED | `TierWalk` (picker branch, `carryIn`, unhandled helper) | `pipeline/TierWalk.kt:39-45,75-76` |
| MODIFIED | `PipelineBuilder.build` (picker id collision check) | `pipeline/PipelineBuilder.kt:113-115` |

### `:providers`

| Status | Component | File |
|---|---|---|
| MODIFIED | Chat encoder reads `request.reasoning` | `chat/ChatEncoder.kt:72` |
| MODIFIED | W04 wire-rule ordering + rejects-`none` audit | `chat/OpenAiModelRules.kt:80-93` |
| MODIFIED | W04 `refine` arm (`param`+`code`) | `chat/ChatErrors.kt:137-146` |
| MODIFIED | `claude-sonnet-5` row | `anthropic/AnthropicModels.kt:42-46` |
| UNCHANGED | `AnthropicEncoder` (golden only) | `anthropic/AnthropicEncoder.kt:53` |

### `:keystore`

| Status | Component | File |
|---|---|---|
| NEW | `@DelicateKeyAccess` | `DelicateKeyAccess.kt` |
| MODIFIED | `KeyAccess` -> public, opt-in, plain interface | `KeyAccess.kt:9` |
| MODIFIED | `ApiKeyStore` (+opt-in 3-arg ctor; existing ctors untouched) | `ApiKeyStore.kt:36-53` |
| MODIFIED | `@OptIn` at `AndroidKeyStoreKeyAccess`, `SecretReader` | internal |

### NEW modules

| Module | Packaging | Depends on |
|---|---|---|
| `:undo` | jar (`voice-action-engine-undo`) | nothing (stdlib) |
| `:voice-adapter` | AAR (`voice-action-engine-voice-adapter`) | `:core` (api), `:stt` v0.7.0 (api, external) |
| on-device provider (Phase 13, only if green) | AAR, `@Experimental` | `:core` only plus its ML runtime; **never** into `:core`/`:providers` (L10, `verifyCoreDependencyAllowlist` stays green) |
| `:sample` (never published) | app | + `:undo` (edge allow-list), Gate-1 legs for grammar/plan/router/undo |

---

## 10. Data-flow changes

### Walk with a picker (Custom/Router only; Linear/Fixed unchanged)

```
execute(input)
  PolicyPreCheck.check -> Ladder(tiers, selector, onDevice, refusal, cappedByPolicy)
  TierWalk.run
    refusal?                                  -> Failed(NoEligibleTier | offline_unavailable)
    selector is Linear/Fixed                  -> climb(drop(startIndex))            [as v1.0.1]
    selector is Custom/Router:
      pre-pass: climb(zero-call head tiers)   -> handled? done
      llm = remaining provider-backed tier ids
      llm empty?                              -> ROUTER_FALLBACK, climb(rest)       [no picker call]
      pseudo-attempt(picker): pick(input, llm, PickContext)  -- model turns, tokens counted
      null | ineligible | throw               -> ROUTER_FALLBACK, climb(rest)       [Linear]
      else                                    -> climb(rest.drop(indexOf(choice)))
    climb exhausted                           -> Unhandled(effects, lastReason, cappedByPolicy)
```

### Call-id and carry facts

```
provider response ToolCall.id ─▶ Extraction.callId (resolver may read)
                              └▶ session.submit(step, id) ─▶ ActionDetails ─▶ ExecutedAction.providerCallId ─▶ CommitSink / CommandOutcome.executed
held: id kept on HeldProposal ─▶ commitHeld child run ─▶ committed ExecutedAction.providerCallId (+ ActionEvent.heldRunId)
carry: TierWalk.carry != null ─▶ recorder.tierStarted(carryIn) ─▶ TierAttempt.carryIn   (presence only)
```

### Plan binding

```
plan(1 call) ─▶ validate(static: tools known, no read/lookup step) ─▶ for step i: bind(args, outputs[0..i-1]) ─▶ prepare ─▶ submit
   outputs[i] = COMMITTED actions' targetIds (never HELD/PREVIEW/IS_ERROR)
   step fails (error | unresolved binding) ─▶ ≤1 replan call ─▶ else Escalate ─▶ TierWalk: suppressed if anything committed/held
```

---

## 11. Build order

Dependency facts that drive the order:
- 12 changes the shapes every later core phase uses, and touches the hottest files (`TierWalk`, `RunRecorder`, `CommitCoordinator`, `SingleShotStrategy`, `AgenticLoopStrategy`). Land it first and serially inside `:core`.
- 14 and 15 are independent of each other; both depend on 12 and on the 4.1 helper extraction.
- 16 edits `TierWalk.run` and `RunRecorder`/`TraceCode`; schedule it **after 12** and preferably after 14/15 have merged to avoid conflicts. Only its real-grammar pre-pass proof needs 14 (develop against a `ScriptedStrategy` with `NO_PROVIDER` first, `ScriptedStrategy` exists in testFixtures).
- 17's module is independent; its pipeline integration needs 12 (`providerCallId`, and `heldRunId` if D-HELD). The module plumbing (section 8) must land before 18 and 13-if-green.
- 18 has no in-milestone dependency except the plumbing.
- 19 needs 12, 14-18 and 13's verdict; 20 needs 19's green Gate-1.

Recommended sequence (numeric order stays valid; this is the safe parallel cut):

| Step | Work | Parallel with | Notes |
|---|---|---|---|
| 0 | Phase 13 spike (device) | everything JVM | TESTER only; do not overlap with 12's live smoke (PROV-16) or 19 |
| 1a | 12-A: `:providers` W04 + `claude-sonnet-5` row + `ReasoningMode` encoder goldens | 1b, 1c | no `:core` hot files except `ModelRequest.kt` |
| 1b | 12-B: `:core` seams in one ordered plan: `Extraction.callId` + internal `submit(id)` thread + `ExecutedAction.providerCallId` + `HeldProposal` id; `TierAttempt.carryIn`; `Unhandled.cappedByPolicy`; `onFailed`; `Builder.reasoning`; (opt) `ActionEvent.heldRunId` | 1a, 1c | serialize inside one plan: `SingleShotStrategy.kt` is edited by `onFailed`, `reasoning` and callId |
| 1c | 12-C: `:keystore` `DelicateKeyAccess` | 1a, 1b | independent module |
| 1d | 12-D: PROV-16 live smoke (device) + DOC-01 docs | after 1a | key workflow; one device user |
| 2 | 17-P: module plumbing (section 8) + empty `:undo` scaffold | 1a-1c | owns the shared plumbing commit; 18 and 13-green rebase on it |
| 3a | 14: helper extraction (4.1) then LocalGrammar | 3b, 3c | extraction is commit 1 |
| 3b | 15: Plan (+ Plan-specific suppression test) | 3a, 3c | needs 12 + 4.1 |
| 3c | 17: `:undo` module (UNDO-01..03) | 3a, 3b | no `:core` edits |
| 3d | 18: `:voice-adapter` | 3a-3c | needs step 2 plumbing; confirm `FinalSegment.language` values first |
| 4 | 16: Custom/Router | after 3a/3b merged (or against a fake head tier earlier) | edits `TierWalk.run`, `RunRecorder`, `TraceCode`, `PipelineBuilder` |
| 5 | 17: UNDO-04 bridge + sample wiring + integration test | after 1b, 3c | consumes `providerCallId`/`heldRunId` |
| 6 | 19: `:sample` Gate-1 (grammar offline, plan with binding, router trace, undo-all), docs, doc-coverage, wiring test | - | TESTER, bounded keys |
| 7 | 20: cut `v1.1.0` | - | quiet window, single-use daemon, run `apiDump` for core/providers/keystore (`+`-only) and new dumps for undo/voice-adapter |

---

## 12. Patterns to follow

1. **Additive API recipe (the v1.0 house style):** keep the old JVM ctor by declaring it explicitly and delegating; never rely on a Kotlin default argument to preserve a ctor (`Completed` at `StrategyOutcome.kt:19-28`, `ToolStep.Finished` at `ToolStep.kt:13-21` are the templates). New facts on outcomes are properties, not new subclasses (sealed `CommandOutcome` would break exhaustive `when`).
2. **Open-set value classes for vocab** (`TraceCode`, `ActionKind`, `ReasoningMode`): `internal` ctor, companion constants, consumers keep an `else`.
3. **Single write path:** every new tier submits through `CommandSession.submit`; a selector/picker context has no `submit`.
4. **Guard every app callback** with `guarded` (`Guarded.kt:38`) so a throw becomes a typed fault and cancellation propagates; the picker and `onFailed` follow this.
5. **Never put content in telemetry:** ids, codes, counts, tool names only (`TurnRecord`, `PipelineEvent`, `TierAttempt`); call ids and bound ids are presence-only in `toString`.
6. **Pure-function encoders:** any new request field must be read from `call.request` in the encoder and covered by a byte golden; cache prefix order (`model, max_tokens, tools, tool_choice, system, messages` / Chat key order) must not move.
7. **Gate-first for new modules:** add the invariant gate in the same change that adds the module (graph edge, classpath allowlist), with a negative control, as v1.0 did for `:core`.

## 13. Anti-patterns to avoid

1. **Making `:core` depend on `:undo` (or `:stt`)** "for integration". It breaks L7/A7 and `verifyCoreDependencyAllowlist`. Integrate by inversion (section 6) or a separate bridge module.
2. **Passing the call id by widening public `ToolStep`/`CommandSession`.** The resolver does not know the id and a public overload freezes surface for no consumer benefit. Keep it internal.
3. **Running the router outside a pseudo-attempt** (headline 2): tokens counted, trace blind.
4. **Binding to HELD/PREVIEW actions' `targetIds`**, or string-substituting into serialized JSON.
5. **One combined gate proposal for Plan** (the SingleShot shape). It makes write-output binding impossible.
6. **Letting `PROVIDER_DEFAULT` omit `reasoning_effort`** on models that need `"none"` to take tools.
7. **Counting "tiers saved" as a claim.** It is tiers bypassed; Linear is never executed.
8. **Hard-coding a router model/provider** in the library. It comes from the app's selection for the picker id.
9. **Skipping the script/hard-coded-list sweep** (section 8 item 9). `jitpack-dry-run.sh:39` asserts exact set equality and will fail the cut; `allowedEdges.getValue` fails the first `check`.
10. **Guessing the `claude-sonnet-5` cache minimum** or the `none`-rejecting id list. Both are open research items; the cache diagnostic stays silent for an unknown minimum by design (`AnthropicModels.kt:36-39`).

## 14. Scalability notes (kept realistic for a library)

| Concern | Now | v1.1 consideration |
|---|---|---|
| Ladder size | 2-4 tiers per app | Picker `eligible` list is small; O(tiers) scans in `TierWalk` are fine |
| Grammar matching | n/a | Linear scan over the pack's rules per command; keep rules per intent indexed by first token only if a pack passes ~hundreds of rules (do not pre-optimize) |
| Trace size | attempts = tiers run | +1 pseudo-attempt when a picker is configured |
| Undo journal memory | n/a | Journal entries hold snapshots of touched entities only; bound by commands per session, app owns persistence/eviction |
| Build memory | host is memory-constrained (earlyoom killed 5 cut attempts) | New modules add Gradle projects: keep the cut run single-daemon; do not add Kotlin compile of `:stt` sources (consume the AAR only) |

---

## 15. Open items and risks

| # | Item | Owner/phase | Impact |
|---|---|---|---|
| O1 | Anthropic's documented minimum cacheable prefix for `claude-sonnet-5` | Phase 12 plan (web check) | Value in the new row; wrong value gives false/absent cache diagnostics |
| O2 | Full list of `OpenAiModelRules` ids that reject `"none"` | Phase 12 plan | Completeness of the W04 golden (SC 4a) |
| O3 | D-REASON (both values identical on wire in v1.1) | Phase 12 discuss | If Yahir wants `PROVIDER_DEFAULT` to differ, that is a wire change and the "no on-wire change" claim breaks |
| O4 | D-UNDO bridge location (app/sample vs 6th module) | Phase 17 discuss | A 6th module needs a contract amendment and another install-list/script sweep |
| O5 | D-HELD `ActionEvent.heldRunId` | Phase 12 or 17 | Without it, held-then-confirmed commands group wrongly |
| O6 | Plan binding syntax and plan schema | Phase 15 research | Public-facing prompt/schema; must survive strict-mode schema rules for OpenAI (`ChatSchemaStrip`) and Anthropic strict |
| O7 | `FinalSegment.language` value set (region tags?) | Phase 18 | Mapping rule `en`/`es`/`null` |
| O8 | Does the Phase 13 provider need `ON_DEVICE` capability-gate wiring beyond the existing `OnDeviceCapability` seam? | Phase 13 | Module scaffold depends on section 8 plumbing; `@Experimental` marker needs its own opt-in annotation |
| O9 | Router classifier prompt and default cheap model | Phase 16 research | Prompt text lives in the library; model id must come from the app's selection |
| O10 | A12 ordering: ledger row fields for two new coordinates and the stt dependency | Phase 20 | Orchestrator writes the §11 row |

## Sources

- Source files read at HEAD (primary, HIGH): `core/.../pipeline/{TierWalk,TierSelector,PolicyPreCheck,CommandOutcome,CommandPipeline,PipelineBuilder,RunSession,HeldCommit,TierPolicy}.kt`; `core/.../telemetry/{RunRecorder,CommandTrace,TraceCode,PipelineEvent,TurnRecord}.kt`; `core/.../commit/{CommitCoordinator,ApplyStep,ActionLedger,ToolStep,CommitSink,HeldProposal,RunTermination,ActionKind}.kt`; `core/.../strategy/{CommandSession,CommandStrategy,StrategyOutcome,StrategyCapabilities,StrategyLimits,OutcomeResolver,ToolExecutor,ToolSpec}.kt`; `core/.../strategy/singleshot/*`, `strategy/agentic/*`; `core/.../provider/{BoundModel,ModelRouter}.kt`; `core/.../transcript/ModelRequest.kt`; `core/.../internal/Guarded.kt`.
- `providers/.../chat/{OpenAiModelRules,ChatEncoder,ChatErrors}.kt`, `providers/.../anthropic/{AnthropicEncoder,AnthropicModels}.kt`, `providers/build.gradle.kts`.
- `keystore/.../{KeyAccess,ApiKeyStore,AndroidKeyStoreKeyAccess}.kt`, `keystore/build.gradle.kts`, `keystore/api.txt`.
- Build plumbing: `settings.gradle.kts`, `jitpack.yml`, `build.gradle.kts`, `gradle/invariants.gradle.kts`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `scripts/*.sh` (module-list greps), `ECOSYSTEM.md`.
- Planning inputs: `.planning/PROJECT.md`, `.planning/ROADMAP.md` (phases 12-20), `.planning/cross-repo/RECONVENE-BRIEF-R-v1.1.md` (sections 1, 2, 6, 10), `CROSS-REPO-SCOPE-CONTRACT.md` (A18 line 253, E7 line 224, §11 stt v0.7.0 row line 284).
- `stt-engine/android/stt/src/main/kotlin/io/github/ygaray/sttengine/FinalSegment.kt`, `stt-engine/android/stt/build.gradle.kts`, `stt-engine/android/jitpack.yml` (adapter inputs: AAR packaging, `language` semantics).
- Not independently verified (no web lookup done; flagged as O1/O2): Anthropic cache minimum for `claude-sonnet-5`; OpenAI ids rejecting `reasoning_effort: "none"`.

---
*Architecture research for: voice-action-engine v1.1 (integration with existing v1.0.1 code)*
*Researched: 2026-10-05*
