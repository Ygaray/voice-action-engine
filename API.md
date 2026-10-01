# API.md: the public surface of voice-action-engine

> **Audience: coding agents (and humans) integrating the engine.** This is the map of what you can call and what you can
> implement. For a step-by-step adoption, read [`INTEGRATION.md`](INTEGRATION.md); for install and a minimal pipeline,
> [`README.md`](README.md). The package root is `io.github.ygaray.voiceactionengine`; the types are in sub-packages,
> listed in "Packages and imports" below (`.core` and its sub-packages, `.providers.anthropic` and `.providers.chat`,
> `.keystore`). The engine is domain-free; no
> example here names an app's data. No concrete version appears in these docs: pin an immutable release tag or a commit
> SHA.

The public API grows strictly additively once tagged. Open sets (marked **open** below) gain members in later
versions, so always keep an `else` branch when you switch over one; closed sets (**closed**, the sealed classes) are
matched exhaustively with no `else`.

## Packages and imports

Import each type from its own package (Kotlin has no wildcard re-export). Root `io.github.ygaray.voiceactionengine`:

```text
core                    CommandInput, Credential, ProviderId, StrategyId
core.commit             ActionEvent, ActionKind, AwaitingConfirmGate, CommitProposal, CommitSink, ConfirmAmendHook,
                        ConfirmationPolicy, DispatchResult, ExecutedAction, FinishedKind, GateDecision, HeldProposal,
                        PendingConfirmation, PendingMutation, PreApplyGate, RunTermination, StepResult, ToolStep
core.failure            BudgetBound, EscalationReason, FailureDetails, FailureReason
core.pipeline           CommandOutcome, CommandPipeline, PipelineBuilder, PipelineDsl, TierPolicy, TierPolicySource,
                        TierSelector, and the function commandPipeline
core.provider           AiProvider, BoundModel, CachingMode, CredentialLookup, CredentialSource, ModelCapabilities,
                        ModelCapabilityTable, ModelResult, OnDeviceAvailability, OnDeviceCapability, ProviderRequest,
                        ProviderSelection, ProviderSelectionSource, SelectionRequest
core.strategy           Clarification, ClarificationOption, CommandSession, CommandStrategy, Extraction,
                        OutcomeResolver, Resolution, StrategyCapabilities, StrategyOutcome, TerminalCall, ToolExecutor,
                        ToolSpec, ToolSpecProvider, ToolingSnapshot, UserTurnContext, UserTurnRenderer
core.strategy.agentic   AgenticLoopStrategy
core.strategy.singleshot SingleShotStrategy
core.telemetry          CommandTrace, PipelineEvent, PipelineEventListener, TierAttempt, TraceCode, TurnRecord, Usage
core.transcript         AssistantMessage, AssistantPart, CacheDirective, Message, ModelRequest, ModelResponse,
                        NativeReplay, StopReason, ToolChoice, ToolResult, ToolResultsMessage, UserMessage
providers.anthropic     AnthropicAttempt, AnthropicAttemptKind, AnthropicAttemptObserver, AnthropicProvider
providers.chat          ChatCompletionsAttempt, ChatCompletionsAttemptKind, ChatCompletionsAttemptObserver,
                        ChatCompletionsProvider (openAi { } and openRouter { } are on its companion)
keystore                ApiKeyStore, KeySlot, KeyState, KeystoreCredentialSource
```

For example `import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline` and
`import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec`. Nested types (`CommandOutcome.Completed`,
`FailureReason.NotConfigured`, `AssistantPart.ToolCall`) come with their parent. The tool schemas and arguments are
kotlinx `JsonObject`s: `kotlinx-serialization-json` and `kotlinx-coroutines-core` are `api` dependencies of `core`,
so they reach your compile classpath through it (import `kotlinx.serialization.json.*` helpers such as
`buildJsonObject`, `put` and `JsonPrimitive` directly).

## Surface at a glance

Every public top-level type of the three published modules. Kinds: class, interface, fun interface (a single-method
interface you can pass as a lambda), value class (a typed string id), sealed class, abstract class (engine-created
leaves), annotation class.

### `core` (pure Kotlin, no HTTP dependency)

| Type | Kind | Set | Purpose |
|---|---|---|---|
| `CommandInput` | class | | One spoken command: transcript, language `"en"`/`"es"`/null, an opaque `context`, and `parentRunId`. |
| `ProviderId` | value class | | Identity of a provider: `ANTHROPIC`, `OPENAI`, `OPENROUTER`, `ON_DEVICE`, or your own. |
| `StrategyId` | value class | | Identity of one tier; persist it (for example as `TierPolicy.maxTier`), never a ladder index. |
| `Credential` | class | | An API key stamped with its provider; `toString()` never shows the key. |
| `PipelineBuilder` | class | | The builder behind `commandPipeline { }`: tiers, gate, sink, providers, selection, credentials, policy, listener. |
| `PipelineDsl` | annotation class | | DSL marker for the builder. |
| `CommandPipeline` | class | | The composed ladder: `execute`, `commitHeld`, `tiers`, `capabilityTable`. |
| `CommandOutcome` | sealed class | closed | The typed result: `Completed`, `Failed` or `Unhandled`. |
| `TierPolicy` | class | | The limits one command runs under (`TierPolicy { }` builder). |
| `TierPolicySource` | fun interface | | Supplies the policy for each command (`fixed(policy)`). |
| `TierSelector` | abstract class | open | Which tier a command starts at: `Linear` (default) or `Fixed(tier)`. |
| `CommandStrategy` | interface | | One tier of the ladder; implement it to write your own. |
| `StrategyCapabilities` | class | | The providers a tier declares it may use (`ANY_PROVIDER`, `NO_PROVIDER`). |
| `StrategyOutcome` | sealed class | closed | What a tier returns: `Completed`, `Escalate`, `NoMatch` or `Failed`. |
| `CommandSession` | abstract class | | What the engine hands a tier: run id, policy, carry, the one write path (`submit`), `model()`. |
| `SingleShotStrategy` | class | | The one-call tier: force one tool, resolve it locally. |
| `AgenticLoopStrategy` | class | | The bounded multi-turn tier over your tools. |
| `OutcomeResolver` | fun interface | | Your local step that turns an `Extraction` into a `Resolution`. |
| `Extraction` | class | | The tool name and untouched arguments the model called. |
| `Resolution` | abstract class | open | A resolver's verdict: `Steps`, `NoMatch`, `Escalate` or `Failed`. |
| `ToolExecutor` | fun interface | | Your step for each tool call of an agentic run. |
| `ToolSpec` | class | | A tool the model may call (`mutating`, `terminal`, `strict`); `ToolSpec.clarification(name)`. |
| `ToolSpecProvider` | fun interface | | Supplies the system text and tools once per command (`fixed(snapshot)`). |
| `ToolingSnapshot` | class | | System text, tools and the single-shot tool name. |
| `UserTurnRenderer` | fun interface | | Renders the one user message of a request (`standard()`). |
| `UserTurnContext` | class | | What a renderer sees: the input, the date-time, the carry. |
| `TerminalCall` | class | | The terminal tool call that ended a run; `asClarification()`. |
| `Clarification` | class | | A question with options, read from a terminal call. |
| `ClarificationOption` | class | | One pressable choice: an opaque `id` and a `label`. |
| `ToolStep` | sealed class | closed | A strategy's request to the engine: `Finished` (no gate) or `Mutation` (gated). |
| `PendingMutation` | interface | | One change waiting for the gate; its `apply()` makes the change. |
| `StepResult` | class | | What a tool call or an apply produced for the model and the sink. |
| `DispatchResult` | class | | What the engine tells a strategy after a step (`held`, `isError`, `actions`). |
| `FinishedKind` | value class | open | How a finished call is classified: `READ`, `PREVIEW`, `ERROR`. |
| `PreApplyGate` | fun interface | | The decision point before any change is written. |
| `GateDecision` | sealed class | closed | The gate's answer: `Admit` or `Hold`. |
| `CommitProposal` | class | | The changes a strategy submitted, put to the gate. |
| `AwaitingConfirmGate` | class | | The ready-made suspend-mode gate: `pending`, `resolve`. |
| `ConfirmationPolicy` | fun interface | | Decides which proposals need confirmation and what subject to show. |
| `ConfirmAmendHook` | fun interface | | Runs after a confirm; may replace the changes to apply. |
| `PendingConfirmation` | class | | A confirmation waiting for the user's answer. |
| `HeldProposal` | class | | Changes the gate held; commit later with `commitHeld`. |
| `CommitSink` | interface | | Hears every action and every run close (undo journal, audit). |
| `ActionEvent` | class | | One action reaching the sink. |
| `ExecutedAction` | class | | One recorded action: `kind`, `applied`, `mutating`, tool name, ids, context. |
| `ActionKind` | value class | open | `COMMITTED`, `HELD`, `PREVIEW`, `IS_ERROR`. |
| `RunTermination` | sealed class | closed | How a run ended for the sink: `Done`, `Failed`, `Exhausted`, `Cancelled`. |
| `AiProvider` | interface | | A model provider: one neutral request in, a `ModelResult` out. |
| `ProviderRequest` | class | | What a provider is handed for one round trip. |
| `ModelResult` | abstract class | open | A provider's answer: `Success` or `Failure`. |
| `BoundModel` | abstract class | | The model a tier calls, frozen for the command; may be refused. |
| `ProviderSelectionSource` | fun interface | | Tells the engine which provider and model a tier uses. |
| `ProviderSelection` | class | | A provider, a model and an optional on-device fallback. |
| `SelectionRequest` | class | | What the selection source is asked about (the tier). |
| `CredentialSource` | fun interface | | Where the engine gets an API key from. |
| `CredentialLookup` | abstract class | open | A lookup's answer: `Present`, `Missing` or `Unreadable`. |
| `OnDeviceCapability` | fun interface | | Whether on-device inference can run now (default: unavailable). |
| `OnDeviceAvailability` | abstract class | open | `Available`, `Downloadable`, `Downloading`, `Unavailable(code)`. |
| `ModelCapabilities` | class | | What a model can do: tools, forced tool choice, caching, minimum prefix. |
| `ModelCapabilityTable` | class | | The capabilities after your overrides (`lookup(provider, model)`). |
| `CachingMode` | value class | open | How a model's provider caches prompts. |
| `FailureReason` | interface | open | Why a command or call failed; every reason has a stable `code`. |
| `EscalationReason` | interface | open | Why a tier handed the command up. |
| `FailureDetails` | class | | Transport facts (status, error type, request id), never a body. |
| `BudgetBound` | value class | open | Which budget a run exceeded: `ITERATIONS`, `TOKENS`. |
| `PipelineEventListener` | fun interface | | Receives `PipelineEvent`s live. |
| `PipelineEvent` | interface | open | Something that happened in a run (ids, codes, counts only). |
| `CommandTrace` | class | | What a run did: tier attempts, codes, usage, duration. |
| `TierAttempt` | class | | One tier's entry in the trace. |
| `TurnRecord` | class | | One model round trip in the trace. |
| `TraceCode` | value class | open | A stable code the engine records in a trace. |
| `Usage` | class | | Tokens in four buckets (uncached input, cache read, cache write, output). |
| `Message` | sealed class | closed | A neutral conversation message: user, assistant or tool results. |
| `UserMessage` | class | | What the user said. |
| `AssistantMessage` | class | | One model turn: parts plus an optional native replay. |
| `AssistantPart` | sealed class | closed | Text or a tool call inside an assistant turn. |
| `ToolResultsMessage` | class | | The results of one assistant turn's tool calls, sent back together. |
| `ToolResult` | class | | The app's answer to one tool call. |
| `NativeReplay` | class | | A provider's own assistant turn, kept verbatim for replay. |
| `ModelRequest` | class | | The neutral request: system, messages, tools, tool choice, limits. |
| `ToolChoice` | abstract class | open | Whether the model may (`Auto`) or must (`Required`) call a tool. |
| `CacheDirective` | class | | Where the provider should place cache breakpoints. |
| `ModelResponse` | class | | What a model answered: message, stop reason, usage, request id. |
| `StopReason` | value class | open | Why a model stopped. |

### `providers` (OkHttp transports, 4.12 compile floor)

| Type | Kind | Set | Purpose |
|---|---|---|---|
| `AnthropicProvider` | class | | The Anthropic Messages API as an `AiProvider` (`AnthropicProvider { }`). |
| `AnthropicAttemptObserver` | fun interface | | Hears every HTTP attempt of an Anthropic call. |
| `AnthropicAttempt` | class | | One HTTP attempt: number, kind, status. |
| `AnthropicAttemptKind` | value class | open | Why an attempt was sent: initial, transient retry, forced-tool reshape. |
| `ChatCompletionsProvider` | class | | Chat Completions as an `AiProvider`: `openAi { }` and `openRouter { }`. |
| `ChatCompletionsAttemptObserver` | fun interface | | Hears every HTTP attempt of a Chat Completions call. |
| `ChatCompletionsAttempt` | class | | One HTTP attempt: number, kind, status. |
| `ChatCompletionsAttemptKind` | value class | open | Why an attempt was sent. |

### `keystore` (Android AAR)

| Type | Kind | Set | Purpose |
|---|---|---|---|
| `ApiKeyStore` | class | | Stores one encrypted bring-your-own key per provider in your DataStore. |
| `KeySlot` | class | | One row of your key table: provider, alias, ciphertext name, IV name. |
| `KeyState` | abstract class | open | What the store knows: `NotConfigured`, `Ready`, `KeyMissing`, `Unreadable`. |
| `KeystoreCredentialSource` | class | | Adapts an `ApiKeyStore` to the engine's `CredentialSource`. |

The one public function is `commandPipeline { }`, which composes a `CommandPipeline`.

## Pipeline and outcomes

`commandPipeline { }` collects tiers (`tier(...)`, first added runs first), `provider(...)`, a required `gate`, a
required `commitSink`, `providerSelection`, `credentials`, a `policy`, an optional `listener`, `capabilities(...)`
overrides, an `onDevice` capability, a `selector` and replaceable `clock` and `runIds`. It throws
`IllegalArgumentException` at build time for a misconfiguration (no tier, no gate, duplicate tier or provider ids).

`CommandPipeline.execute(input)` returns a `CommandOutcome` and does not throw, except for your own coroutine's
cancellation or a JVM `Error`. **`CommandOutcome` is closed**: `Completed(reply, terminalCall, partial)`,
`Failed(reason, details)` and `Unhandled(lastReason)`. Every outcome carries `runId`, `parentRunId`, `executed`,
`commits`, `held` and `trace`. `Completed(partial = true)` means some work was done and the rest was not: render it
as "did X, couldn't finish", never as full success. `FailureReason` and `EscalationReason` are **open**; every reason
has a stable `code`.

## Strategies and tools

- `SingleShotStrategy(id) { tooling, resolver, userTurn, forceTool, onNoToolCall, onRefusal, capabilities, clock }`:
  one provider call, one local resolution. Only the first tool call of an answer is acted on.
- `AgenticLoopStrategy(id) { tooling, executor, userTurn, capabilities, clock }`: a bounded conversation; limits come
  from `TierPolicy`.
- `CommandStrategy` is the interface behind both; a custom tier submits every write as a `ToolStep` through
  `CommandSession.submit`, so the gate and the sink always see it. `StrategyOutcome` (**closed**) is what a tier
  returns.
- `OutcomeResolver` returns a `Resolution` (**open**): `Steps`, `NoMatch`, `Escalate`, `Failed`. `ToolExecutor`
  returns a `ToolStep` (**closed**): `Finished(toolName, FinishedKind, StepResult)` or `Mutation(PendingMutation)`.
  `FinishedKind` is **open**: `READ`, `PREVIEW`, `ERROR`.
- `ToolSpec.clarification(name)` is the terminal tool for asking a question; read the call with
  `TerminalCall.asClarification()`, which returns a `Clarification` of `ClarificationOption`s or null.

## The write path and gate

`PreApplyGate.admit(proposal)` returns a `GateDecision` (**closed**): `Admit(amended)` or `Hold(reason, token)`. There
is no default gate and no default sink. Suspend mode: `AwaitingConfirmGate` (observe `pending`, answer with
`resolve(id, confirmed)`). Defer mode: return `Hold`, then `CommandPipeline.commitHeld(held)` or
`commitHeld(held, amended)`; a proposal commits at most once and lives in memory only.

`CommitSink.onAction` hears every `ExecutedAction`; `onRunClosed` hears a `RunTermination` (**closed**) exactly once.
An action's `kind` is an `ActionKind` (**open**):

| Kind | `applied` | `mutating` | Meaning |
|---|---|---|---|
| `ActionKind.COMMITTED` | true | true | The change ran. |
| `ActionKind.HELD` | false | true | The gate held it. |
| `ActionKind.PREVIEW` | false | false | Shown as a preview only. |
| `ActionKind.IS_ERROR` | true or false | true or false | `apply` reported an error (true, true), or the call was rejected before the gate (false, false). |

In the agentic loop a held change gives the model exactly `{"applied":false,"status":"held_for_confirmation"}` as the
tool result; the model must not retry it. Read `commits` and `executed`, not the outcome type, to learn what was
written.

## Providers, capabilities and selection

- `AiProvider` has an `id`, `requiresCredential` (default true), `capabilities(model)` (default
  `ModelCapabilities.UNKNOWN`) and `complete(ProviderRequest)`, which returns a `ModelResult` (**open**: `Success`
  or `Failure`) and never throws for an expected failure.
- `AnthropicProvider { httpClient, callTimeoutMillis, readTimeoutMillis, attemptObserver }`,
  `ChatCompletionsProvider.openAi { }` and `.openRouter { }`. Pass your shared `OkHttpClient`; the provider derives its
  own copy without your interceptors, cookies or authenticators.
- `ProviderSelectionSource` returns a `ProviderSelection(provider, model)` per tier (or null: not configured). An
  on-device selection may declare one cloud `fallback`.
- `capabilities(provider, model) { }` on the builder patches one exact model id; `capabilityTable.lookup(...)` reads
  the result (override, then provider default, then unknown). `CachingMode` is **open**.
- `OnDeviceCapability.availability()` returns an `OnDeviceAvailability` (**open**); the default is unavailable
  (`not_implemented`).
- Known combinations: OpenRouter `anthropic/*` ids are uncached in v1.0; OpenAI ids that answer only on the Responses
  endpoint fail with `FailureReason.ModelUnsupported` before any call; Anthropic models without forced tool choice are
  sent `auto` plus an instruction. See [`INTEGRATION.md`](INTEGRATION.md).

## Credentials and keystore

`CredentialSource.credential(provider)` returns a `CredentialLookup` (**open**): `Present(Credential)`, `Missing()` (a
class: construct it with parentheses) or `Unreadable(cause)`. `KeystoreCredentialSource(ApiKeyStore(dataStore, slots))` is the ready-made source: the app owns
the `DataStore` (one per file per process) and the `KeySlot` table. `ApiKeyStore` offers `save`, `delete`, `read` and
`observe`; the plaintext key never leaves through a public member. `KeyState` is **open**. The unreadable causes are
stable codes: `key_missing`, `decrypt_failed`, `stored_value_malformed` (re-enter the key) and `keystore_unavailable`,
`storage_unreadable` (transient, retry); the engine reports them as `FailureReason.CredentialUnreadable`.

## Telemetry and trace

`PipelineEventListener.onEvent(event)` (it cannot suspend; a throw becomes the `listener_error` trace code) receives
`PipelineEvent`s (**open**): started, tier started or skipped, provider call, action recorded, engine code, cache not
engaged, tier ended, closed. `CommandOutcome.trace` is a `CommandTrace` of `TierAttempt`s, `TurnRecord`s,
`TraceCode`s (**open**) and `Usage`. Events and traces carry ids, codes, counts and tool names only; never log keys,
transcripts or tool arguments.

<!-- doc-snippet: telemetry -->
```kotlin
class MyEventLog : PipelineEventListener {
    val lines: MutableList<String> = mutableListOf()

    // Events carry ids, codes, counts and tool names only. Keep it that way: never add transcripts or arguments.
    override fun onEvent(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.CommandStarted -> lines.add("started ${event.runId}")
            is PipelineEvent.ActionRecorded -> lines.add("action ${event.kind} ${event.toolName} applied=${event.applied}")
            is PipelineEvent.EngineCode -> lines.add("code ${event.code}")
            else -> Unit // an open set: later versions add events
        }
    }
}

fun PipelineBuilder.limitsAndTelemetry(log: PipelineEventListener) {
    policy = TierPolicySource.fixed(TierPolicy { commandTimeoutMillis = 120_000L })
    listener = log
}

fun traceSummary(outcome: CommandOutcome): String {
    val trace = outcome.trace
    return "tiers=${trace.attempts.map { it.strategy }} codes=${trace.codes} tokens=${trace.usage.total}"
}
```

## Transcript types

The neutral conversation a provider maps to its wire format: `Message` (**closed**: `UserMessage`,
`AssistantMessage`, `ToolResultsMessage`), `AssistantPart` (**closed**: `Text`, `ToolCall`), `ModelRequest`
(with `ToolChoice`, **open**: `Auto`, `Required`, and a `CacheDirective`), `ModelResponse` (with `StopReason`,
**open**, and `Usage`) and `NativeReplay`, a provider's own turn kept verbatim so it can be sent back unchanged to the
same provider and model.

## Extension points

Everything you implement or pass; each seam is a small interface you give the engine:

| Seam | You provide | Where it plugs in |
|---|---|---|
| `ToolSpecProvider` | system text and tools | a strategy's `tooling` |
| `OutcomeResolver` | local resolution of the model's tool call | `SingleShotStrategy.resolver` |
| `ToolExecutor` | preparation of each tool call | `AgenticLoopStrategy.executor` |
| `UserTurnRenderer` | the user message text | a strategy's `userTurn` |
| `PreApplyGate` | approval before any write | builder `gate` |
| `CommitSink` | journal of actions and run endings | builder `commitSink` |
| `ProviderSelectionSource` | provider and model per tier | builder `providerSelection` |
| `CredentialSource` | the API key per provider | builder `credentials` |
| `TierPolicySource` | limits per command | builder `policy` |
| `PipelineEventListener` | live events | builder `listener` |
| `OnDeviceCapability` | on-device availability | builder `onDevice` |
| `capabilities(provider, model) { }` | facts about one exact model id | builder, once per pair |
| `AiProvider` | a whole provider (also your test fake) | builder `provider(...)` |
| `CommandStrategy` | a whole tier | builder `tier(...)` |
| `ConfirmationPolicy`, `ConfirmAmendHook` | what to confirm, and a refresh after confirm | `AwaitingConfirmGate` |

## Safety model

- **Never-throw collapse.** A strategy, gate, sink, listener, policy source, selection source or credential source that
  throws becomes a typed failure, a hold or a recorded trace code; the run still closes exactly once. Only your own
  coroutine's cancellation and a JVM `Error` escape.
- **No logging inside the library.** It has no logging dependency and no `println`; events and traces hold no content.
- **Secrets stay out.** API keys, transcripts, tool arguments and results never reach logs, telemetry, exceptions or
  `toString()`; every type prints lengths, counts and names only.
- **Held results.** A held change is reported as `ActionKind.HELD` and, to a model, as `held_for_confirmation`;
  nothing is written until `commitHeld`.
- **OkHttp floor.** `providers` compiles against OkHttp 4.12 and is tested on 4.12 and 5.x; your app keeps its own
  OkHttp version.
