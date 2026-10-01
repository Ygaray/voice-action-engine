# INTEGRATION.md: adopt voice-action-engine in a new app

> **Audience: coding agents (and humans) wiring the engine into an app.** Follow the numbered steps in order. Every
> Kotlin block below is compiled and run by the repository's tests (`DocSnippetsTest`) over the public API only, so it
> is safe to copy. Imports are left out: the types live in sub-packages of `io.github.ygaray.voiceactionengine`, and [`API.md`](API.md) ("Packages and imports") gives the package of every public type. The names starting
> with `My` are your app's own types. The engine is domain-free: the tool names `find_items`, `create_item` and
> `ask_user` are examples.

Read [`API.md`](API.md) for the full public surface. The working example is the `:sample` app (`sample/`); each step
below says which of its files does the same thing. The `sample/` paths are in the repository
(<https://github.com/Ygaray/voice-action-engine>), not in the published artifacts, so a consumer workspace does not
have them; every step here stands on its own without them.

## 1. Add the JitPack repository

In **`settings.gradle.kts`**:

```kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

## 2. Depend on the modules you need

Per-module coordinates, never one aggregate. `<version>` is an immutable release tag or a commit SHA, never a branch
snapshot (JitPack builds a commit SHA directly):

```kts
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-core:<version>")
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-providers:<version>")
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:<version>")
```

- `core` is pure Kotlin: the pipeline, the strategies, the seams and the neutral types. It has no HTTP dependency.
- `providers` adds the Anthropic, OpenAI and OpenRouter transports over OkHttp. It compiles against the OkHttp 4.12
  floor and is tested on 4.12 and 5.x, so your app keeps its own OkHttp version; do not force one.
- `keystore` is an Android library (AAR) that stores a bring-your-own API key encrypted with a device key. Skip it if
  you keep keys elsewhere. It exposes `androidx.datastore:datastore-preferences` as an `api` dependency (the store takes
  your `DataStore<Preferences>`), so step 7 needs no extra dependency line in the app.
- What you get transitively: `core` exposes `kotlinx-serialization-json` (tool schemas and arguments are
  `JsonObject`s) and `kotlinx-coroutines-core`; `providers` exposes OkHttp. You do not declare these yourself.
- For tests (step 10) you add your own `junit:junit` and `org.jetbrains.kotlinx:kotlinx-coroutines-test` (for
  `runTest`) as test dependencies; the engine does not publish them.

## 3. Permissions

The provider transports use the network, so the app manifest needs:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The engine itself never asks for any other permission.

## 4. Describe your tools

A tool is a `ToolSpec(name, description, inputSchema, mutating, terminal, strict)`; the schema is a kotlinx
`JsonObject`.

- Set `mutating = true` on every tool that changes your app's state. Every call to it goes through the gate (step 6).
- A `terminal` tool ends the run, and a terminal tool must not be mutating (the constructor refuses it).
  `ToolSpec.clarification("ask_user")` is the ready-made terminal tool for asking the user a question (step 9).
- Leave `strict` null. The engine then asks a provider for strict schema adherence only when the tool has no optional
  property, so optional fields stay optional.
- Tool names use letters, digits, underscore and dash, at most 64 characters (the Chat Completions vendors enforce it).
- A `ToolSpecProvider` returns a `ToolingSnapshot(system, tools, singleShotTool)` once per command. Keep the system
  text and the tool list stable: together they form the prefix a provider caches. Per-command text (the date, the
  transcript) belongs in the user turn. Derive counts from your tool registry; never hard-code a tool count.

Sample: `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/SyntheticTools.kt`.

## 5. Pick your tiers

The ladder is the order of your `tier(...)` calls: the first tier added runs first and a tier hands the command up
only by returning an escalation. Tier ids (`StrategyId`) must be unique. By default a command starts at the first tier
that may run (`TierSelector.Linear`); `TierSelector.Fixed(id)` starts at a named tier.

- **SingleShot** makes one provider call that forces the tool named by `ToolingSnapshot.singleShotTool`, hands the
  model's first tool call to your `OutcomeResolver`, and submits what it returns. The resolver validates the arguments
  itself and never writes: it returns `Resolution.Steps` (finished steps and `ToolStep.Mutation`), `Resolution.NoMatch`,
  `Resolution.Escalate` or `Resolution.Failed`. Only the first tool call of an answer is acted on; extra calls are
  dropped and a completed outcome is marked partial.
- **AgenticLoop** runs a bounded conversation over your tools. Your `ToolExecutor` prepares each call: a read returns
  `ToolStep.Finished` with `FinishedKind.READ`, a rejected call `FinishedKind.ERROR`, a change `ToolStep.Mutation`. It
  never writes either. Limits come from the `TierPolicy` (step 8). A tool that errors twice ends the run as a tool
  failure; the first call to a terminal tool ends it with that call as the outcome's `terminalCall`.
- The user turn defaults to `UserTurnRenderer.standard()` (the local date-time and the transcript). Supply your own
  `UserTurnRenderer` for other framing (step 9 does).

The minimal single-shot pipeline (the same snippet as in the README):

<!-- doc-snippet: minimal-pipeline -->
```kotlin
object MyTools {
    val findItems = ToolSpec("find_items", "Finds items whose title matches a query.", schemaOf("query"))
    val createItem = ToolSpec("create_item", "Creates an item.", schemaOf("title"), mutating = true)
    val askUser = ToolSpec.clarification("ask_user")

    fun snapshot(forced: String?) = ToolingSnapshot(
        "You manage a plain list of items.",
        listOf(findItems, createItem, askUser),
        forced,
    )

    private fun schemaOf(field: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject(field) { put("type", "string") } }
        putJsonArray("required") { add(field) }
        put("additionalProperties", false)
    }
}

class MyCreateItem(private val items: MutableList<String>, private val title: String) : PendingMutation {
    override val toolName: String = "create_item"

    // Runs only after the gate admits. A held change may run later, so re-validate before writing.
    override suspend fun apply(): StepResult {
        items.add(title)
        return StepResult("""{"status":"created"}""")
    }
}

class MyResolver(private val items: MutableList<String>) : OutcomeResolver {
    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        val title = (extraction.arguments["title"] as? JsonPrimitive)?.contentOrNull
        return if (extraction.toolName == "create_item" && !title.isNullOrBlank()) {
            Resolution.Steps(listOf(ToolStep.Mutation(MyCreateItem(items, title))), "Added it.")
        } else {
            Resolution.NoMatch()
        }
    }
}

class MyCommitSink : CommitSink {
    val kinds: MutableList<ActionKind> = mutableListOf()

    override suspend fun onAction(event: ActionEvent) {
        kinds.add(event.action.kind)
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit
}

val MyAdmitAll = PreApplyGate { GateDecision.Admit() }

fun singleShotTier(items: MutableList<String>): CommandStrategy =
    SingleShotStrategy(StrategyId("single_shot")) {
        tooling = ToolSpecProvider.fixed(MyTools.snapshot(forced = "create_item"))
        resolver = MyResolver(items)
    }

fun buildPipeline(
    tiers: List<CommandStrategy>,
    aiProvider: AiProvider,
    credentialSource: CredentialSource,
    approval: PreApplyGate,
    sink: CommitSink = MyCommitSink(),
    configure: PipelineBuilder.() -> Unit = {},
): CommandPipeline = commandPipeline {
    tiers.forEach { tier(it) }
    provider(aiProvider)
    providerSelection = ProviderSelectionSource { ProviderSelection(aiProvider.id, "my-model") }
    credentials = credentialSource
    gate = approval
    commitSink = sink
    configure()
}
```

A ladder with a cheap single-shot tier first and the agentic tier when the first hands the command up:

<!-- doc-snippet: agentic-tier -->
```kotlin
class MyToolExecutor(private val items: MutableList<String>) : ToolExecutor {
    override suspend fun prepare(call: Extraction, input: CommandInput): ToolStep = when (call.toolName) {
        "find_items" -> {
            val query = (call.arguments["query"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val matches = items.count { it.contains(query, ignoreCase = true) }
            ToolStep.Finished("find_items", FinishedKind.READ, StepResult("""{"matches":$matches}"""))
        }
        "create_item" -> {
            val title = (call.arguments["title"] as? JsonPrimitive)?.contentOrNull
            if (title.isNullOrBlank()) rejected(call.toolName) else ToolStep.Mutation(MyCreateItem(items, title))
        }
        else -> rejected(call.toolName)
    }

    private fun rejected(toolName: String): ToolStep =
        ToolStep.Finished(toolName, FinishedKind.ERROR, StepResult("""{"status":"error"}""", true))
}

fun agenticTier(items: MutableList<String>): CommandStrategy =
    AgenticLoopStrategy(StrategyId("agentic")) {
        tooling = ToolSpecProvider.fixed(MyTools.snapshot(forced = null))
        executor = MyToolExecutor(items)
    }

// The ladder: the cheap tier is tried first, the agentic tier only when the first one hands the command up.
fun buildLadder(
    aiProvider: AiProvider,
    credentialSource: CredentialSource,
    approval: PreApplyGate,
    items: MutableList<String>,
): CommandPipeline = buildPipeline(
    listOf(singleShotTier(items), agenticTier(items)),
    aiProvider,
    credentialSource,
    approval,
)
```

Sample: `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngine.kt` (the one composition root)
and `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/CannedToolExecutor.kt`.

## 6. The write path

There is no default gate and no default sink: you choose how changes are approved and where they are reported, so
nothing is ever committed by accident. A `PreApplyGate` decides about the changes a tier submits; only
`GateDecision.Admit` lets them run, and a gate that throws holds. There are two modes.

**Suspend mode** waits inside the gate for the user. `AwaitingConfirmGate` takes a `ConfirmationPolicy` (return a
subject for your confirmation UI, or null to admit without asking) and a timeout. Observe `pending`, show the
question, and call `resolve(id, confirmed)` with the user's answer. A decline or a timeout holds the change; a policy
error fails closed:

<!-- doc-snippet: gate-suspend -->
```kotlin
fun confirmingGate(): AwaitingConfirmGate = AwaitingConfirmGate(
    ConfirmationPolicy { proposal ->
        // Return what your UI needs to ask the question, or null to admit without asking.
        if (proposal.mutations.any { it.toolName == "create_item" }) "Add this item?" else null
    },
    60_000L,
)

// In your UI layer: show gate.pending while it is not null, then answer it with the user's choice.
fun CoroutineScope.answerWhenAsked(gate: AwaitingConfirmGate, confirmed: Boolean): Job = launch {
    val asked = gate.pending.filterNotNull().first()
    gate.resolve(asked.id, confirmed)
}
```

**Defer mode** returns `GateDecision.Hold` at once. The change waits in `outcome.held`; later, `commitHeld(held)`
applies it as a new run linked by `parentRunId`, without asking the gate again, and `commitHeld(held, amended)` applies
your edited changes instead. A held proposal is resolved at most once (a second call returns the first call's result
and writes nothing), lives in memory only, and may be applied against state that moved on, so re-validate inside each
change's `apply`:

<!-- doc-snippet: gate-defer -->
```kotlin
val MyDeferGate = PreApplyGate { GateDecision.Hold("needs_review") }

// Later, when the user approves (optionally with edited changes):
suspend fun approveHeld(pipeline: CommandPipeline, outcome: CommandOutcome): CommandOutcome? {
    val held = outcome.held.firstOrNull() ?: return null
    return pipeline.commitHeld(held)
}

suspend fun approveEdited(
    pipeline: CommandPipeline,
    outcome: CommandOutcome,
    edited: List<PendingMutation>,
): CommandOutcome? {
    val held = outcome.held.firstOrNull() ?: return null
    return pipeline.commitHeld(held, edited)
}

// Held, previewed and failed changes are told apart by kind; applied says whether your apply ran.
fun describeActions(outcome: CommandOutcome): List<String> = outcome.executed.map { action ->
    val what = when (action.kind) {
        ActionKind.COMMITTED -> "done"
        ActionKind.HELD -> "waiting for confirmation"
        ActionKind.PREVIEW -> "previewed only"
        ActionKind.IS_ERROR -> "failed"
        else -> action.kind.value
    }
    "${action.toolName}: $what (applied=${action.applied}, mutating=${action.mutating})"
}
```

**The sink.** Your `CommitSink` hears every action through `onAction`, in order and awaited, and hears
`onRunClosed` exactly once on every exit path, including cancellation. A throw from either is recorded and never makes
a change run twice. Use it for an undo journal.

**What an action means.** Every `ExecutedAction` has a `kind`, `applied` and `mutating`:

| `kind` | `applied` | `mutating` | Meaning |
|---|---|---|---|
| `ActionKind.COMMITTED` | true | true | The change ran. |
| `ActionKind.HELD` | false | true | The gate held it; it waits for `commitHeld`. |
| `ActionKind.PREVIEW` | false | false | Shown as a preview, not applied. |
| `ActionKind.IS_ERROR` | true | true | Your `apply` ran and reported an error (siblings are not undone). |
| `ActionKind.IS_ERROR` | false | false | A call rejected before the gate (`FinishedKind.ERROR`); nothing could be written. |

`ActionKind` is an open set: keep an `else`. In the agentic loop a held change gives the model exactly
`{"applied":false,"status":"held_for_confirmation"}` as the tool result, and the model must not retry it. Read
`outcome.commits` and `outcome.executed` to learn what was written, never the outcome type alone: a failure can follow
real writes.

Sample: the canned-admit gate in `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngine.kt`.

## 7. Providers, selection and keys

Register the providers you use. Each provider id may be registered once; hand the transports your app's shared
`OkHttpClient` if you have one (the provider derives its own copy and never changes yours):

<!-- doc-snippet: register-providers -->
```kotlin
fun PipelineBuilder.registerProviders(shared: OkHttpClient?) {
    provider(AnthropicProvider { httpClient = shared })
    provider(ChatCompletionsProvider.openAi { httpClient = shared })
    provider(ChatCompletionsProvider.openRouter { httpClient = shared })

    // A dated model id is not in the built-in table: tell the engine what it cannot do (exact ids only).
    capabilities(ProviderId.ANTHROPIC, "claude-opus-5-5-20261001") { supportsForcedToolChoice = false }

    // Version 1.0 ships no on-device provider, so the default already says "unavailable".
    onDevice = OnDeviceCapability { OnDeviceAvailability.Unavailable("not_installed") }
}
```

- **Selection.** A `ProviderSelectionSource` tells the engine which provider and model each tier uses, asked once per
  command per tier; return `ProviderSelection(provider, model)` or null for "nothing configured". The engine never
  names a model; model ids are yours. A tier may only use the providers it declares (`StrategyCapabilities`, any
  provider by default).
- **Keys.** A `CredentialSource` answers `CredentialLookup.Present`, `Missing` or `Unreadable` for the provider about
  to be called, and the engine refuses a credential stamped for another provider. `CredentialLookup` is an open set; `Missing` is a class, so write `CredentialLookup.Missing()`.
- **`:keystore`.** Spell out your key table (one `KeySlot` per provider: the AndroidKeyStore alias and the two
  preference names, copied verbatim from your existing storage so saved keys keep working), own one `DataStore` per
  file per process, and hand `KeystoreCredentialSource(ApiKeyStore(...))` to the pipeline:

<!-- doc-snippet: keystore-wiring -->
```kotlin
val MyKeySlots: List<KeySlot> = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)
    .map { provider ->
        KeySlot(
            provider = provider,
            alias = "my_app_$provider",
            ciphertextKey = "my_app_${provider}_ct",
            ivKey = "my_app_${provider}_iv",
        )
    }

// One DataStore per file per process, and the app owns it.
private val Context.myKeyDataStore: DataStore<Preferences> by preferencesDataStore(name = "my_app_keys")

fun keyCredentials(context: Context): CredentialSource =
    KeystoreCredentialSource(ApiKeyStore(context.applicationContext.myKeyDataStore, MyKeySlots))

// What to tell the user when a key is stored but cannot be read. The causes are an open set.
fun keyAdvice(cause: String): String = when (cause) {
    "key_missing", "decrypt_failed", "stored_value_malformed" -> "Key unreadable ($cause): re-enter key"
    "keystore_unavailable", "storage_unreadable" -> "Key unreadable ($cause): transient, retry"
    else -> "Key unreadable ($cause): re-enter key"
}
```

An unreadable key reaches you as `FailureReason.CredentialUnreadable(provider, cause)` (and `KeyState.Unreadable` from
the store). The causes are stable codes: `key_missing`, `decrypt_failed` and `stored_value_malformed` mean **re-enter
the key**; `keystore_unavailable` and `storage_unreadable` mean **transient, retry**. The set is open, so treat an
unknown cause as re-enter.

- **Capability overrides.** `capabilities(provider, model) { ... }` patches what the engine believes about one exact
  model id (never a prefix or family); it wins over the provider's built-in table. Read the result with
  `pipeline.capabilityTable.lookup(provider, model)`.
- **On-device.** `OnDeviceCapability` defaults to "unavailable" (code `not_implemented`): version 1.0 ships no
  on-device provider. A tier whose only provider is on-device fails loudly instead of climbing to the cloud.

Sample: `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeySlots.kt` and
`sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/net/ProviderFactory.kt`.

## 8. Policy and telemetry

`TierPolicySource` supplies the limits for each command (`TierPolicy`: `offlineOnly`, `maxTier`, `allowedProviders`,
`maxIterations`, `tokenCeiling`, `maxTokensPerTurn`, `commandTimeoutMillis`); `TierPolicySource.fixed(...)` returns
one policy for every command. The engine enforces `offlineOnly`, `maxTier`, `allowedProviders` and `commandTimeoutMillis`; `maxIterations`,
`tokenCeiling` and `maxTokensPerTurn` are limits the built-in strategies read and enforce themselves. A `PipelineEventListener` receives `PipelineEvent`s as a run happens; it cannot suspend, and if it throws
the command carries on and the trace records `listener_error`. The finished `outcome.trace` (`CommandTrace`) holds the
tier attempts, the model turns, the usage and the `TraceCode`s.

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

**Never log** keys, transcripts or tool arguments. The engine never does: events and traces carry ids, codes, counts
and tool names only, and every `toString()` prints lengths and names, never content. Keep your own listener and sink
the same.

## 9. Render every outcome

`CommandOutcome` is closed: handle `Completed`, `Failed` and `Unhandled`, with no `else`, so the compiler tells you
when a case is missing. The reasons inside are open sets and always need an `else`.

- `Completed(partial = true)` means some work was done and the rest was not (a tier committed or held a change and then
  asked for a later tier, which the engine blocks so nothing is written twice; or extra tool calls of one answer were
  dropped). Render it as "did X, couldn't finish", never as full success. `commits` and `held` say what was done.
- A `Failed` can follow real writes. Offer a retry only when `commits` is empty.
- A `Completed` whose `terminalCall` is a clarification (`terminalCall.asClarification()`) is a question, not a
  result. Render the question and one pressable choice per `ClarificationOption`. The option ids are yours; the engine
  never interprets them.
- A pressed option is a new command, not a resumed one. Start it with `parentRunId = previous.runId` and let your
  `UserTurnRenderer` write the choice into the user turn (the renderer reads it from `input.context`), so the model
  sees the original transcript, the question and the answer. The outcome and the sink both carry the link.

The snippet below calls `keyAdvice` from step 7 (the `keystore-wiring` block); copy that function with it.

<!-- doc-snippet: render-outcome -->
```kotlin
class MyOutcomeView(val headline: String, val choices: List<ClarificationOption> = emptyList())

fun renderOutcome(outcome: CommandOutcome): MyOutcomeView = when (outcome) {
    is CommandOutcome.Completed -> renderCompleted(outcome)
    is CommandOutcome.Failed -> MyOutcomeView(failureText(outcome.reason, outcome.commits.size))
    is CommandOutcome.Unhandled -> MyOutcomeView(unhandledText(outcome.lastReason))
}

fun renderCompleted(outcome: CommandOutcome.Completed): MyOutcomeView {
    val clarification: Clarification? = outcome.terminalCall?.asClarification()
    val choices = clarification?.options.orEmpty()
    return when {
        // Some work was done and the rest was not: never render this as full success.
        outcome.partial -> MyOutcomeView("Did ${outcome.executed.size} action(s), couldn't finish", choices)
        // Render the question and one pressable choice per option.
        clarification != null -> MyOutcomeView(clarification.question, choices)
        else -> MyOutcomeView(outcome.reply?.let { "Done: $it" } ?: "Done")
    }
}

fun failureText(reason: FailureReason, committed: Int): String {
    val what = when (reason) {
        is FailureReason.CredentialUnreadable -> keyAdvice(reason.cause)
        is FailureReason.NotConfigured -> "Not configured: add a key for ${reason.provider ?: "a provider"}"
        else -> "Failed: ${reason.code}"
    }
    // A failure can follow real writes: offer a retry only when nothing was committed.
    return if (committed == 0) what else "$what ($committed change(s) already committed)"
}

fun unhandledText(reason: EscalationReason?): String = when (reason) {
    null -> "Not handled"
    is EscalationReason.NoToolCall -> "I could not tell which action you meant"
    else -> "Not handled: ${reason.code}"
}
```

<!-- doc-snippet: clarification-follow-up -->
```kotlin
class MyChoice(val question: String, val option: ClarificationOption)

class MyUserTurn : UserTurnRenderer {
    override suspend fun render(context: UserTurnContext): String {
        val choice = context.input.context as? MyChoice
        val transcript = context.input.transcript
        return if (choice == null) {
            transcript
        } else {
            "$transcript\n\nYou asked: ${choice.question}\nThe user chose: ${choice.option.label} (${choice.option.id})"
        }
    }
}

fun clarifyingTier(items: MutableList<String>): CommandStrategy =
    SingleShotStrategy(StrategyId("single_shot")) {
        tooling = ToolSpecProvider.fixed(MyTools.snapshot(forced = null))
        forceTool = false
        resolver = MyResolver(items)
        userTurn = MyUserTurn()
    }

// The user pressed an option: that is a new command, linked to the first by parentRunId.
suspend fun chooseOption(
    pipeline: CommandPipeline,
    previous: CommandOutcome.Completed,
    transcript: String,
    option: ClarificationOption,
): CommandOutcome {
    val question = previous.terminalCall?.asClarification()?.question.orEmpty()
    return pipeline.execute(
        CommandInput(transcript, language = null, context = MyChoice(question, option), parentRunId = previous.runId),
    )
}
```

Sample: `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/OutcomeText.kt` and
`sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt` (the follow-up).

## 10. Test your wiring with your own scripted AiProvider

Write a small `AiProvider` that plays canned `ModelResult`s and records the requests. The engine's own
`FakeAiProvider` lives in its test sources and is not published, so every consumer owns this twenty-line class. Give it
the id your app selects (so your tier declarations and selection apply unchanged) and `requiresCredential = false`:

<!-- doc-snippet: scripted-provider -->
```kotlin
/**
 * A consumer's own scripted provider: plays canned answers and records every request the engine sends. It takes the
 * id of the provider your app selects, so your tier declarations and selection work unchanged in a test.
 */
class MyScriptedProvider(
    answers: List<ModelResult>,
    override val id: ProviderId = ProviderId.ANTHROPIC,
) : AiProvider {
    private val remaining = ArrayDeque(answers)

    /** Every request the engine sent, in order. */
    val requests: MutableList<ProviderRequest> = mutableListOf()

    override val requiresCredential: Boolean = false

    override suspend fun complete(call: ProviderRequest): ModelResult {
        requests.add(call)
        return remaining.removeFirstOrNull() ?: ModelResult.Failure(FailureReason.Other("script_exhausted"))
    }
}

/** An answer in which the model calls tools, in order (call ids are call_1, call_2, ...). */
fun toolCallsAnswer(vararg calls: Pair<String, JsonObject>): ModelResult {
    val parts = calls.mapIndexed { index, (name, arguments) ->
        AssistantPart.ToolCall("call_${index + 1}", name, arguments)
    }
    return answerOf(parts, StopReason.TOOL_USE)
}

/** An answer in which the model calls one tool. */
fun toolCallAnswer(name: String, arguments: JsonObject): ModelResult = toolCallsAnswer(name to arguments)

/** An answer in which the model only writes text. */
fun textAnswer(text: String): ModelResult = answerOf(listOf(AssistantPart.Text(text)), StopReason.END_TURN)

private fun answerOf(parts: List<AssistantPart>, stopReason: StopReason): ModelResult =
    ModelResult.Success(ModelResponse(AssistantMessage(parts), stopReason, Usage(100, 0, 0, 20)))
```

`Usage` takes four token counts in order: uncached input, cache read, cache write, output. Drive the pipeline with `runTest` and assert on the outcome (`commits`, `held`, `executed`, `trace`) and on
`requests`. Return `ModelResult.Failure(...)` to test your failure rendering; no network is involved.

Sample: `sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt` runs every snippet
in this file, and `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/DemoProvider.kt` is a scripted
provider in an app.

## Notes and gotchas

- **Unsupported and uncached combinations.** OpenRouter model ids of the form `anthropic/<id>` are uncached in v1.0:
  no cache markers are sent through a router (OpenAI models cache automatically, Anthropic's own provider uses explicit
  breakpoints). OpenAI ids that answer only on the Responses endpoint, such as `gpt-6-astra` and `gpt-6.1-sol`, fail
  with `FailureReason.ModelUnsupported` before any network call (through OpenRouter they work). Anthropic models that
  reject a forced tool choice (`claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1`, `claude-mythos-5-1`) are sent
  `auto` plus an instruction to call the tool; a dated id such as `claude-opus-5-5-20261001` is not in the table, so
  its first request is forced, refused and re-sent, which doubles that call's latency and spend until you add a
  `capabilities(...)` override with `supportsForcedToolChoice = false`.
- **Open taxonomies need `else`:** `FailureReason`, `EscalationReason`, `Resolution`, `CredentialLookup`, `KeyState`,
  `ActionKind`, `FinishedKind`, `PipelineEvent`, `TraceCode`, `StopReason`, `ToolChoice`, `ModelResult`,
  `OnDeviceAvailability`, `TierSelector`, `AnthropicAttemptKind` and `ChatCompletionsAttemptKind`. Later versions add
  members without breaking you.
- **Closed taxonomies are matched exhaustively, with no `else`:** `CommandOutcome`, `GateDecision`, `RunTermination`,
  `ToolStep`, `StrategyOutcome`, `Message` and `AssistantPart`.
- **The engine never throws** out of `execute` or `commitHeld`, except for your own coroutine's cancellation or a JVM
  `Error`. A provider, strategy, gate or hook that throws becomes a typed failure or a hold. The one refusal is
  `commitHeld(held, amended)` with an empty `amended` list: it throws `IllegalArgumentException` before the proposal is
  used up.
- **One DataStore per file per process.** `:keystore` takes the DataStore you own and never creates one; a second
  DataStore on the same file throws at runtime.
- **A held change lives in memory only** and does not survive the process.
- **Model ids match exactly.** Built-in capability tables key Anthropic ids exactly and OpenAI ids by family pattern;
  anything else gets the unknown-model default until you override it.
- **Working example.** The `:sample` app (`sample/`) is the reference wiring and is never published; its tool names
  are synthetic.
