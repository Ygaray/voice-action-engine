# Phase 11 Interim API Review (plan 11-02, Task 3)

Date: 2026-10-01
HEAD at dump: 11fdb0f (plus the uncommitted `scripts/api-dump-isolated.sh`, which is part of this task's commit)
Base: ded9647

## Commands and results

| Command | Result line |
|---|---|
| `./gradlew check --offline` | BUILD SUCCESSFUL (detekt zero on core, providers, keystore; core tests; providers OkHttp 4.12.0, 5.2.1, 5.5.0 legs; keystore unit tests; sample unit tests incl. DocSnippetsTest) |
| `scripts/api-dump-isolated.sh --out .planning/phases/11-cut-v1-0-0/evidence/interim-api` | `API DUMP ISOLATED OK … core=1698 providers=121 keystore=67` (line counts) |
| `scripts/review-api-surface.sh --expect-sealed-complete` | `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=181` |
| `scripts/verify-docs-coverage.sh` | `DOC COVERAGE OK checks=23 types=97` (includes `KeystoreCauseCodes`) |
| `scripts/verify-repo-hygiene.sh` | `HYGIENE OK`; the real tree holds no `api.txt`; `git tag --list` is empty |

The dumps are Metalava signature files from an isolated copy. They are stored as `.api.sig` under
`evidence/interim-api/`, never as `api.txt`. Plan 11-07 commits the real `api.txt` files, which must be
byte-identical to these.

## Shape rules (grep over the providers and keystore signatures)

- No `enum` declaration, no `copy(` or `componentN(` method, no sealed declaration in providers or keystore.
- The only `field public static` lines are `Companion` (providers: `AnthropicAttemptKind`, `AnthropicProvider`,
  `ChatCompletionsAttemptKind`, `ChatCompletionsProvider`) and `INSTANCE` (keystore: `KeystoreCauseCodes`). No String
  or other constant field is public.
- Core: the same rules are enforced by `review-api-surface.sh` (sealed set equals the seven allowed types).
- Suppressions in published main sources: `core/.../internal/Guarded.kt:1` (`@file:Suppress("TooGenericExceptionCaught")`)
  is the only one, as recorded by 11-01. No new `@Suppress`; `config/detekt/detekt.yml` is unchanged; no baseline.

## Public surface per module

Declaration counts are from the signature files: all public/protected class, interface and object declarations
including nested ones.

### core (181 declarations: 85 top-level, 96 nested; 7 sealed)

- `core`: CommandInput (final class), Credential (final class), ProviderId (value class), StrategyId (value class)
- `core.commit`: ActionEvent, ActionKind (value class), AwaitingConfirmGate, CommitProposal, CommitSink (interface),
  ConfirmAmendHook (fun interface), ConfirmationPolicy (fun interface), DispatchResult, ExecutedAction,
  FinishedKind (value class), GateDecision (sealed), HeldProposal, PendingConfirmation, PendingMutation (interface),
  PreApplyGate (fun interface), RunTermination (sealed), StepResult, ToolStep (sealed)
- `core.failure`: BudgetBound (value class), EscalationReason (interface), FailureDetails, FailureReason (interface)
- `core.pipeline`: CommandOutcome (sealed), CommandPipeline, PipelineBuilder, PipelineBuilderKt (file facade of
  `commandPipeline { }`), PipelineDsl (annotation), TierPolicy, TierPolicySource (fun interface), TierSelector (abstract)
- `core.provider`: AiProvider (interface), BoundModel (abstract), CachingMode (value class), CredentialLookup (abstract),
  CredentialSource (fun interface), ModelCapabilities, ModelCapabilityTable, ModelResult (abstract),
  OnDeviceAvailability (abstract), OnDeviceCapability (fun interface), ProviderRequest, ProviderSelection,
  ProviderSelectionSource (fun interface), SelectionRequest
- `core.strategy`: Clarification, ClarificationOption, CommandSession (abstract), CommandStrategy (interface),
  Extraction, OutcomeResolver (fun interface), Resolution (abstract), StrategyCapabilities, StrategyOutcome (sealed),
  TerminalCall, ToolExecutor (fun interface), ToolSpec, ToolSpecProvider (fun interface), ToolingSnapshot,
  UserTurnContext, UserTurnRenderer (fun interface)
- `core.strategy.agentic`: AgenticLoopStrategy
- `core.strategy.singleshot`: SingleShotStrategy
- `core.telemetry`: CommandTrace, PipelineEvent (interface), PipelineEventListener (fun interface), TierAttempt,
  TraceCode (value class), TurnRecord, Usage
- `core.transcript`: AssistantMessage, AssistantPart (sealed), CacheDirective, Message (sealed), ModelRequest,
  ModelResponse, NativeReplay, StopReason (value class), ToolChoice (abstract), ToolResult, ToolResultsMessage,
  UserMessage

Delta against the Phase 9 core dump (`.planning/phases/09-agentic-loop-strategy/evidence/agentic-surface-review.txt`,
lines 30 to 1701, diffed verbatim):

- Exactly one delta: three `ctor public ToolSpec(` lines are gone (the 3-, 4- and 5-argument `@JvmOverloads`
  constructors). `ToolSpec` now has the one six-argument constructor line. This is the intended Task 2 change.
- The Phase 9 file's trailing `==== END METALAVA DUMP ====` marker is review-record wrapping, not part of the dump.
- Nothing else differs: every other type, member and count is identical to Phase 9, so no unreviewed core surface
  arrived in Phases 10 and 11.

### providers (14 declarations: 8 top-level, 6 nested; no prior full dump, so this is the FIRST FULL REVIEW)

- `providers.anthropic`: AnthropicAttempt (final class), AnthropicAttemptKind (value class),
  AnthropicAttemptObserver (fun interface), AnthropicProvider (final class); nested `AnthropicAttemptKind.Companion`
  (FORCED_TOOL_RESHAPE, INITIAL, TRANSIENT_RETRY), `AnthropicProvider.Builder`, `AnthropicProvider.Companion`
  (`operator invoke(block)`)
- `providers.chat`: ChatCompletionsAttempt (final class), ChatCompletionsAttemptKind (value class),
  ChatCompletionsAttemptObserver (fun interface), ChatCompletionsProvider (final class); nested
  `ChatCompletionsAttemptKind.Companion` (INITIAL, TRANSIENT_RETRY), `ChatCompletionsProvider.Builder`,
  `ChatCompletionsProvider.Companion` (`openAi(block)`, `openRouter(block)`)

Whole-list review:

- Both providers implement `core.provider.AiProvider` and are constructed only through a builder block, with an
  injectable `OkHttpClient`, call and read timeouts and an attempt observer. OkHttp appears in the surface only as
  `okhttp3.OkHttpClient`, a 4.12 type present in 5.x (the A1 floor).
- `toString()` is declared on both providers and on the attempt types. The attempt records carry numbers, HTTP status,
  kind, finish reason and tool-call counts only; no key, transcript, argument or body field is exposed.
- No `internal` type leaks: the wire DTOs, mappers and transport helpers are absent.
- The two transports are a consistent pair (attempt, kind, observer, provider). `AnthropicAttemptKind` has one more
  constant (`FORCED_TOOL_RESHAPE`) than the chat kind, which is the only asymmetry.
- Verdict: intended as is.

### keystore (9 declarations: 5 top-level, 4 nested)

- `keystore`: ApiKeyStore (final class, two constructors: with and without an injected dispatcher),
  KeySlot (final class), KeyState (abstract class, open set) with nested NotConfigured, Ready(last4), KeyMissing,
  Unreadable(cause), KeystoreCauseCodes (object), KeystoreCredentialSource (final class)

Delta against the Phase 6 keystore dump (`.planning/phases/06-keystore/evidence/keystore-surface-review.txt`, lines
4 to 53, diffed verbatim):

- Exactly one delta: the new `KeystoreCauseCodes` declaration. It has five getters (`getKEY_MISSING`,
  `getDECRYPT_FAILED`, `getSTORED_VALUE_MALFORMED`, `getKEYSTORE_UNAVAILABLE`, `getSTORAGE_UNREADABLE`), the five
  matching `property` lines and `field public static final … INSTANCE`. There is no other field: no static String
  constant, no backing field. This is the intended Task 1 change.
- The Phase 6 record's trailing "Reading of the dump" and "Cause codes (pending orchestrator confirmation)" sections
  are prose, not dump. The cause codes are now public and documented with their UX in `KeystoreCauseCodes`, which
  closes the pending-confirmation note.
- Explanations for the remaining lines already reviewed in Phase 6: both `ApiKeyStore` constructors take the app's own
  `DataStore<Preferences>`; `KeyState` has exactly four leaves; no internal type (platform key access, crypto helper,
  cause-code holder `KeystoreCauses`) appears.

## Decision records

1. **ToolSpec has one public constructor (Task 2).** The Phase 9 dump listed four `ctor public ToolSpec(` lines
   (`@JvmOverloads`); `CommandInput`, which has defaults and no overloads, lists one. Kotlin call sites bind to the
   default-argument stub, not the overloads; all consumers (SecondBrain, CalTracker, the wiring test) are Kotlin;
   `git ls-files '*.java'` is empty and `getConstructor` appears for ToolSpec only in `ApiShapeTest`. Dropping the
   annotation removes three Java-only signatures that §11 rule 2 would otherwise freeze, and no tag exists, so nothing
   released is removed. The frozen surface is one constructor plus the default stub (asserted by
   `toolSpecDeclaresExactlyOnePublicConstructor`); the growth rule (later attributes as `with...` members, never a
   seventh parameter) stays in the KDoc.
2. **KeystoreCauseCodes as getter-only properties with no backing field (Task 1).** A `const val` or `@JvmField` in an
   object compiles to a public static field, which `KeystoreApiShapeTest` and `review-api-surface.sh` forbid. A plain
   `val` with a literal initializer trips detekt `style>MayBeConst`. A getter with no initializer gives detekt nothing
   to flag and leaves no field, so the compiled object has only `INSTANCE` plus five getters. No `@Suppress`, no
   `detekt.yml` change. `when (cause) { KeystoreCauseCodes.KEY_MISSING -> … }` compares by equality and works.
3. **Value-class kinds are kept** (`AnthropicAttemptKind`, `ChatCompletionsAttemptKind`, `ProviderId`, and the other
   core value classes `StrategyId`, `ActionKind`, `FinishedKind`, `BudgetBound`, `CachingMode`, `TraceCode`,
   `StopReason`): they are the house open-vocabulary pattern, a consistent pair across both transports, and every
   consumer is Kotlin. This is decided by the agents under A12 and is the 4b pre-freeze row W05 (category C) in
   `11-WAIVER-PACKET.md`. If Yahir answers needs-fix there, the loop returns to this plan's scope before 11-07's
   baseline (then 11-03's docs gate, a new W and an isolated rerun in 11-06).
4. **Suppressions:** `Guarded.kt` is the only one in published main sources (from 11-01).

INTERIM API REVIEW: INTENDED
