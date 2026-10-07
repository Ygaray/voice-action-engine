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

Per-module coordinates, never one aggregate. `<version>` is the immutable release tag named under "Version to pin" in
[`README.md`](README.md), or a commit SHA when you must test an unreleased fix, never a branch snapshot (JitPack builds
a commit SHA directly):

```kts
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-core:<version>")
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-providers:<version>")
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:<version>")
```

- `core` is pure Kotlin: the pipeline, the strategies, the seams and the neutral types. It has no HTTP dependency.
  `core` alone is enough for a pipeline, every seam and your own scripted `AiProvider`, so a JVM-only consumer (tests
  included) needs nothing else.
- `providers` is needed only to call Anthropic, OpenAI or OpenRouter over HTTP; it adds those transports over OkHttp. It compiles against the OkHttp 4.12
  floor and is tested on 4.12 and 5.x, so your app keeps its own OkHttp version; do not force one.
- `keystore` is an Android library (AAR, minSdk 35) that stores a bring-your-own API key encrypted with a device key.
  Add it only in an Android app, and skip it if you keep keys elsewhere. It exposes `androidx.datastore:datastore-preferences` as an `api` dependency (the store takes
  your `DataStore<Preferences>`), so step 7 needs no extra dependency line in the app.
- `voice-adapter` (optional) is an Android library (AAR, minSdk 35) that turns one final `:stt` segment into a
  `CommandInput`; its coordinate is `com.github.Ygaray.voice-action-engine:voice-action-engine-voice-adapter:<version>`.
  Add it only in an app that captures speech with `:stt`, and add `:stt` yourself; see section 12.
- What you get transitively: `core` exposes `kotlinx-serialization-json` (tool schemas and arguments are
  `JsonObject`s) and `kotlinx-coroutines-core`; `providers` exposes OkHttp. You do not declare these yourself.
- **Your `:app` module's Kotlin and Android Gradle Plugin.** The engine is built with Kotlin 2.3.20 and AGP 9.2.1 and
  emits JVM 11 bytecode. Use Kotlin 2.3.x (a compiler more than one minor version older cannot read the engine's
  metadata) and a JVM target of 11 or higher. AGP 9 has built-in Kotlin, so do **not** apply
  `org.jetbrains.kotlin.android` in the app module; with an older AGP, apply it as usual. You need no serialization
  compiler plugin to use the engine's `JsonObject`s, because `kotlinx-serialization-json` arrives transitively with
  `core`. Declare the plugin only if you write your own `@Serializable` classes.
- For tests (step 10) you add your own `junit:junit` and `org.jetbrains.kotlinx:kotlinx-coroutines-test` (for
  `runTest`) as test dependencies; the engine does not publish them.

## 3. Permissions

The provider transports use the network, so an app that depends on `providers` needs this in its manifest:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

It is needed only when you use `providers`: an app that depends on `core` (and `keystore`) alone, with its own
`AiProvider`, makes no network call through the engine and needs no permission for it. The engine itself never asks for
any other permission.

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
Tiers run in the order they are added, and the zero-call grammar head (the tiers at the head of the ladder that make no
model call) always runs first under `Linear`, `Custom` and `Router`; `Fixed(id)` is the exception, because it starts
exactly at its tier.

### Choosing where the model walk starts

- **Zero-call head first.** With `Custom` or `Router`, the tiers at the head of the ladder that make no model call (the
  grammar tier) always run first, for free. A picker runs only when they hand the command over, and it never sees a
  zero-call tier: it chooses among the eligible tiers that call a model. `Fixed(id)` is different: it starts exactly at
  that tier, so a grammar head below it does not run. A zero-call tier that sits after a model tier is not part of the
  head: if the pick lands past it, it is bypassed together with the model tiers the pick skips, and the trace does not
  count it in `tiersBypassed`.
- **Your own picker.** `TierSelector.Custom(picker)` takes your `StartTierPicker`: a suspend function that sees the
  command and the eligible model tiers in ladder order and returns one id, or null for "start at the first". Use
  `TierSelector.Custom(picker) { id = ...; capabilities = ... }` to name the picker and the providers it may call.
- **Map the picker's id.** The picker's model comes from your `ProviderSelectionSource`, asked with the picker's own id
  (`start_tier_picker` unless you set one). Map it exactly as you map a tier id. If you do not, every command records
  `provider_not_selected` and then `router_fallback`, and the walk starts at the first model tier. A picker id must
  differ from every tier id: the pipeline refuses to build otherwise.
- **Mistakes never fail or pay.** A null, an id that is not eligible, a throw or a timeout starts the walk at the first
  eligible model tier and records `router_fallback`. It is never a failure of the command, and your coroutine's
  cancellation still propagates. When the policy leaves no model tier, or forbids every provider the picker declares
  (offline-only, for example), the picker is never called, so it costs nothing. A call cut off by
  `pickerTimeoutMillis` (2,000 ms by default) is cancelled with no turn record, and the provider may still bill it; that
  spend is not in `trace.usage` or `tokensUsed`, so raise the timeout if a cold connection makes it fire often.
- **The engine's own classifier.** `TierSelector.Router { tierDescriptions = mapOf(...) }` is opt-in and off unless you
  set it. After the head it makes one forced call, only when two or more model tiers are eligible, and starts the walk at
  the tier the answer names. Map `start_tier_router` (or the `id` you give the Router) to a small, fast model; the engine
  names none. `tierDescriptions` is one line per tier id, in your own words, saying when that tier is the right start.
  They are sent to the router's provider, so they must never contain secrets.

The router and the mapping of its id, on a ladder with a grammar head and two model tiers (the router makes no call when
only one model tier is eligible). The block needs these imports on top of the ones the earlier blocks use:

```text
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
```

<!-- doc-snippet: router-selector -->
```kotlin
// Sent to the router's provider: one line per tier, in your own words, and never a secret.
fun routerSelector(): TierSelector.Router = TierSelector.Router {
    tierDescriptions = mapOf(
        StrategyId("single_shot") to "One simple change to one item.",
        StrategyId("agentic") to "Several changes, or a question that needs a lookup first.",
    )
}

// The router asks for its model with its own id (start_tier_router unless you set one): map it like a tier id.
fun routedSelection(aiProvider: AiProvider, router: TierSelector.Router): ProviderSelectionSource =
    ProviderSelectionSource { request ->
        val model = if (request.strategy == router.id) "my-small-model" else "my-model"
        ProviderSelection(aiProvider.id, model)
    }

fun routedLadder(
    aiProvider: AiProvider,
    credentialSource: CredentialSource,
    approval: PreApplyGate,
    items: MutableList<String>,
): CommandPipeline {
    val router = routerSelector()
    return buildPipeline(
        listOf(grammarTier(MyGrammarResolver(items, "en")), singleShotTier(items), agenticTier(items)),
        aiProvider,
        credentialSource,
        approval,
    ) {
        selector = router
        providerSelection = routedSelection(aiProvider, router)
    }
}

// Read what the pick did from the trace: how it ended, the tier it named and how many tiers it skipped.
fun pickSummary(outcome: CommandOutcome): String {
    val selection = outcome.trace.selection ?: return "no pick"
    return "${selection.outcome}: ${selection.picked?.value ?: "none"}, skipped ${selection.tiersBypassed}"
}
```

- **What a pick saved.** Read `trace.selection.tiersBypassed` for the number of tiers the pick skipped compared with
  `TierSelector.Linear`. It is an upper bound, not proven savings. A listener hears the same record as
  `PipelineEvent.StartTierSelected`, after the picker's own provider calls and before the first picked tier starts.

- **SingleShot** makes one provider call that forces the tool named by `ToolingSnapshot.singleShotTool`, hands the
  model's first tool call to your `OutcomeResolver`, and submits what it returns. The resolver validates the arguments
  itself and never writes: it returns `Resolution.Steps` (finished steps and `ToolStep.Mutation`), `Resolution.NoMatch`,
  `Resolution.Escalate` or `Resolution.Failed`. Only the first tool call of an answer is acted on; extra calls are
  dropped and a completed outcome is marked partial. A SingleShot tier cannot serve reads: its resolver gets only the
  first tool call and returns finished steps and mutations, so a read tool such as `find_items` is usable only in the
  AgenticLoop tier.
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

### The grammar tier (free, offline)

`LocalGrammarStrategy` answers a spoken command from phrasings you declare, with no provider call. It declares no
provider, so it costs nothing, needs no key and no network, and runs under `offlineOnly` and under any provider policy.
It is the zero-call head described above: put it first. Its changes still go through the gate like every other tier's.

- **Phrasings per intent.** A `GrammarPack` holds one `intent(toolName)` per tool, with its English phrasings in `en(...)`
  and its Spanish ones in `es(...)`. A phrasing is a template of words: plain words matched one for one, `[optional
  words]`, `(this|that)` groups, `<name>` sub-rules declared with `enRule` or `esRule`, and `{slot}`s. There is no regular
  expression. Case, vowel accents, apostrophes and punctuation at the edges of a word do not matter (`ñ` does), and a
  phrasing needs at least one literal word on every path. A mistake in the declarations throws when the pack is built,
  never while a command is matched.
- **Slots.** Declare each slot once per intent with `text(name, maxWords)`, `integer(name, min, max)`,
  `decimal(name, min, max)` or `choice(name) { option(id) { en(...); es(...) } }`. The one declaration serves the English
  and the Spanish phrasing, so both languages bind the same typed arguments. A slot written only inside `[ ]` is
  optional. `normalize(slot) { words, language -> value }` lets you replace a `text` or `choice` value with your own
  canonical one: answering null or blank rejects the match, and so does a hook that throws.
- **The tier never guesses.** The whole transcript must equal one phrasing (apart from the fillers you declare with
  `enFillers` and `esFillers`): never a prefix, never a part of a longer sentence. An ambiguous reading, a language label
  that is not `en` or `es`, a rejected slot and a transcript too long to match all hand the command on with the carry
  cleared, so the next tier starts fresh with the original transcript. A null label tries both packs, which must agree;
  `tryOtherLanguage` makes a labeled command try the other pack as well.
- **Which language matched.** `Extraction.matchedLanguage` is `"en"` or `"es"` for a grammar match. It is null after a
  model tier and when both packs agreed on a command that has no label, so fall back to your own locale when it is null.
  The resolver in the block below does that.
- **Terminal intents.** `terminal()` marks an intent that writes nothing, such as navigation or a question. On a match
  the tier ends handled with a `terminalCall` that carries the tool name and the slot values, with no resolver, no gate
  and no recorded action. A pack whose intents are all terminal needs no resolver.
- **Offline.** Under `offlineOnly` a command the grammar does not match ends `Unhandled` with `cappedByPolicy` true,
  because no model tier may run. Render that as a plain "not understood", not as a provider failure.
- **Why a command was handed on.** The trace codes `grammar_ambiguous`, `grammar_input_too_long`,
  `grammar_language_unsupported`, `grammar_normalize_error`, `grammar_resolver_rejected` and `grammar_slot_rejected` in
  `outcome.trace.codes` say why.
- **Test your phrasings alone.** `GrammarPack.match(transcript, language)` is pure: it returns a `GrammarMatch` (the tool
  name, the arguments, `matchedLanguage`, `terminal`, and a `ruleId` that is opaque, so do not parse it) or null. Run it
  over a corpus of what people say, in a plain unit test.

The block needs these imports on top of the ones the earlier blocks use:

```text
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
```

<!-- doc-snippet: grammar-tier -->
```kotlin
// The phrasings you declare, per tool. {title} is a slot of up to four words, [please] is optional and (add|put) is a
// choice. The one slot serves both languages, and no regular expression is involved.
fun myGrammarPack(): GrammarPack = GrammarPack {
    intent("create_item") {
        text("title", 4)
        en("[please] (add|put) {title} (to|on) my list")
        es("[por favor] (agrega|pon) {title} (a|en) mi lista")
    }
}

// matchedLanguage is "en" or "es" for a grammar match and null after a model tier, so keep your own locale as the
// fallback. Only the language is kept here, never the words.
class MyGrammarResolver(private val items: MutableList<String>, private val locale: String) : OutcomeResolver {
    val spoken: MutableList<String> = mutableListOf()

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        spoken.add(extraction.matchedLanguage ?: locale)
        return MyResolver(items).resolve(extraction, input)
    }
}

// The grammar tier costs nothing and calls no provider: a command it does not match is handed on unchanged.
fun grammarTier(grammarResolver: OutcomeResolver): CommandStrategy =
    LocalGrammarStrategy(StrategyId("grammar")) {
        pack = myGrammarPack()
        resolver = grammarResolver
    }

// Put it first. A command it matches ends there; every other command reaches the model tiers as if it had not run.
fun grammarLadder(
    aiProvider: AiProvider,
    credentialSource: CredentialSource,
    approval: PreApplyGate,
    items: MutableList<String>,
    grammarResolver: OutcomeResolver,
): CommandPipeline = buildPipeline(
    listOf(grammarTier(grammarResolver), singleShotTier(items)),
    aiProvider,
    credentialSource,
    approval,
)
```

### The plan tier (one planning call)

`PlanThenExecuteStrategy` is for a command that has several steps and needs no lookup, so the model can plan it from
the transcript alone. It makes one forced call to the engine's own `submit_plan` tool; the model answers with ordered
steps, each naming one of your non-terminal tools and its arguments. Your `ToolExecutor` prepares every step, and every
step is its own proposal to the gate, in plan order, so the gate sees each write (a SingleShot tier combines them).

- **Passing a result on.** An argument whose entire value is the string `$<stepId>.<key>` is replaced, before your
  executor sees the step, with the `targetIds[key]` that the earlier step committed. Return the key in the
  `StepResult` of your `apply`, and say in the write tool's description which key it returns, because the model reads
  that description to write the reference. A reference inside a longer string stays literal, a step may refer only to
  an earlier one, and the exact syntax and its limits (step id characters, the 64 character cap, whitespace) are in
  [`API.md`](API.md). A reference to an unknown step rejects the plan before any step runs; a key the earlier step did
  not return stops the plan before that step.
- **A step counts as run only when its change was committed.** A step that ends as a preview, a read result or an error
  is a failed step: nothing it produced can be passed on, and the plan stops there. Keep previews and reads out of a
  plan.
- **Lookups are not planned.** A plan that sets `needs_lookup`, or lists a read tool as a step, hands the command to the
  next tier with nothing run (trace code `plan_needs_lookup`). Put an `AgenticLoopStrategy` after it for those commands.
- **A hold ends the plan, partially.** The tier stops at the first held step and never resumes: the command ends
  `Completed(partial = true)`, the commits made before the hold are kept, the held proposal is in `held`, and
  `remainingStepIds` lists the steps that never ran, in plan order (the held step itself is in `held`).
  `commitHeld` applies that one proposal only and never runs the remaining steps. Render the result as "did X, couldn't
  finish", never as success, as the block below does.
- **Limits and replanning.** `maxSteps` (8 by default) caps the plan. When the plan is rejected before any step runs, or
  the first step fails before anything was applied or held, the tier asks once more; it never asks a third time and
  never asks again after a change was applied or held. The trace codes `plan_rejected`, `plan_replanned` and
  `plan_binding_unresolved` say what happened. A snapshot that already offers a tool named `submit_plan`, or offers no
  non-terminal tool, fails before any call.

The block needs these imports on top of the ones the earlier blocks use:

```text
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
```

<!-- doc-snippet: plan-tier -->
```kotlin
class MyRow(val id: String, val title: String, val parentId: String?)

// A write tool whose description names the key it returns. A later step passes that key on as $<stepId>.id.
object MyPlanTools {
    val createItem = ToolSpec(
        "create_item",
        "Creates one item with a title, optionally under the item named by parent_id. " +
            "Returns the key id: the id of the new item.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "string") }
                putJsonObject("parent_id") { put("type", "string") }
            }
            putJsonArray("required") { add("title") }
        },
        mutating = true,
    )
}

class MyPlanCreate(
    private val rows: MutableList<MyRow>,
    private val title: String,
    private val parentId: String?,
) : PendingMutation {
    override val toolName: String = "create_item"

    override suspend fun apply(): StepResult {
        val row = MyRow("row-${rows.size + 1}", title, parentId)
        rows.add(row)
        // The ids you return here are what a later step's $<stepId>.id is replaced with.
        return StepResult("""{"status":"created"}""", false, null, mapOf("id" to row.id))
    }
}

class MyPlanExecutor(private val rows: MutableList<MyRow>) : ToolExecutor {
    override suspend fun prepare(call: Extraction, input: CommandInput): ToolStep {
        val title = (call.arguments["title"] as? JsonPrimitive)?.contentOrNull
        val parentId = (call.arguments["parent_id"] as? JsonPrimitive)?.contentOrNull
        return if (call.toolName == "create_item" && !title.isNullOrBlank()) {
            ToolStep.Mutation(MyPlanCreate(rows, title, parentId))
        } else {
            ToolStep.Finished(call.toolName, FinishedKind.ERROR, StepResult("""{"status":"error"}""", true))
        }
    }
}

// One planning call, then each step runs in order and is its own proposal to the gate.
fun planTier(rows: MutableList<MyRow>): CommandStrategy =
    PlanThenExecuteStrategy(StrategyId("plan")) {
        tooling = ToolSpecProvider.fixed(
            ToolingSnapshot("You manage a plain list of items.", listOf(MyPlanTools.createItem), null),
        )
        executor = MyPlanExecutor(rows)
    }

// A hold ends the plan as a partial completion: say what was done, what waits and what never started.
fun planSummary(outcome: CommandOutcome): String = when (outcome) {
    is CommandOutcome.Completed ->
        if (outcome.partial) {
            "Did ${outcome.commits.size} action(s), couldn't finish " +
                "(${outcome.held.size} waiting, ${outcome.remainingStepIds.size} not started)"
        } else {
            "Done"
        }
    is CommandOutcome.Failed -> "Failed: ${outcome.reason.code}"
    is CommandOutcome.Unhandled -> "Not handled"
}
```

## 6. The write path

There is no default gate and no default sink: you choose how changes are approved and where they are reported, so
nothing is ever committed by accident. A `PreApplyGate` decides about the changes a tier submits; only
`GateDecision.Admit` lets them run, and a gate that throws is an error, never a hold (nothing is written, the trace records `gate_error`, each change is
reported to the sink as an `is_error` action, and there is no held change to `commitHeld`). In the agentic loop a
thrown gate is told to the model as `{"status":"error","reason":"internal_error"}` with the error flag set, and a tool
that errors twice ends the run as a tool failure. A `GateDecision.Hold` that your gate returns is a real hold and is never an error. There are two modes.

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
a change run twice. Use it for an undo journal: see section 11.

**What an action means.** Every `ExecutedAction` has a `kind`, `applied` and `mutating`:

| `kind` | `applied` | `mutating` | Meaning |
|---|---|---|---|
| `ActionKind.COMMITTED` | true | true | The change ran. |
| `ActionKind.HELD` | false | true | The gate held it; it waits for `commitHeld`. |
| `ActionKind.PREVIEW` | false | false | Shown as a preview, not applied. |
| `ActionKind.IS_ERROR` | true | true | Your `apply` ran and reported an error (siblings are not undone). |
| `ActionKind.IS_ERROR` | false | false | A call rejected before the gate (`FinishedKind.ERROR`); nothing could be written. |

`ActionKind` is an open set: keep an `else`. In the agentic loop a held change gives the model exactly
`{"applied":false,"status":"held_for_confirmation"}` as the tool result (not an error), and the model must not retry it. Read
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
  The exact signature: `CredentialSource` is a `fun interface` with one `suspend fun credential(provider: ProviderId): CredentialLookup`.
  Answer `CredentialLookup.Present(Credential(provider, apiKey))`, `CredentialLookup.Missing()` or
  `CredentialLookup.Unreadable(cause)`; `Credential.toString()` never shows the key. A source that serves one key the
  app already holds (from its own secure storage; the key is a parameter, never a literal in your code) is this short;
  it needs `io.github.ygaray.voiceactionengine.core.Credential` and `io.github.ygaray.voiceactionengine.core.provider.CredentialLookup`
  on top of the `ProviderId` and `CredentialSource` imports listed below:

<!-- doc-snippet: fixed-credentials -->
```kotlin
// Serves the one key the app already holds (read from its own secure storage); the key is a parameter, never a literal.
fun fixedCredentials(apiKey: String): CredentialSource = CredentialSource { provider ->
    if (provider == ProviderId.ANTHROPIC) {
        CredentialLookup.Present(Credential(provider, apiKey))
    } else {
        CredentialLookup.Missing()
    }
}
```

- **`:keystore`.** Spell out your key table (one `KeySlot` per provider: the AndroidKeyStore alias and the two
  preference names, copied verbatim from your existing storage so saved keys keep working), own one `DataStore` per
  file per process, and hand `KeystoreCredentialSource(ApiKeyStore(...))` to the pipeline. The snippet needs these
  imports; the DataStore classes come with `keystore`, which exposes `datastore-preferences` as an `api` dependency:

```text
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.KeySlot
import io.github.ygaray.voiceactionengine.keystore.KeystoreCauseCodes
import io.github.ygaray.voiceactionengine.keystore.KeystoreCredentialSource
```

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
    KeystoreCauseCodes.KEY_MISSING,
    KeystoreCauseCodes.DECRYPT_FAILED,
    KeystoreCauseCodes.STORED_VALUE_MALFORMED,
    -> "Key unreadable ($cause): re-enter key"
    KeystoreCauseCodes.KEYSTORE_UNAVAILABLE,
    KeystoreCauseCodes.STORAGE_UNREADABLE,
    -> "Key unreadable ($cause): transient, retry"
    else -> "Key unreadable ($cause): re-enter key"
}
```

A `ProviderId` is a value class whose toString() prints its wire value (anthropic, openai, openrouter, on_device), so
the names the snippet builds with it, such as `my_app_anthropic` and `my_app_anthropic_ct`, are clean strings.

An unreadable key reaches you as `FailureReason.CredentialUnreadable(provider, cause)` (and, from the store itself, as
`KeyState.Unreadable` for the four causes other than `key_missing`). The causes are stable codes, also public values on
`KeystoreCauseCodes` (getter-only values, so not usable in annotations): `key_missing`
(`KeystoreCauseCodes.KEY_MISSING`), `decrypt_failed` (`KeystoreCauseCodes.DECRYPT_FAILED`) and `stored_value_malformed`
(`KeystoreCauseCodes.STORED_VALUE_MALFORMED`) mean **re-enter the key**; `keystore_unavailable`
(`KeystoreCauseCodes.KEYSTORE_UNAVAILABLE`) and `storage_unreadable` (`KeystoreCauseCodes.STORAGE_UNREADABLE`) mean
**transient, retry**. `key_missing` (the device key is gone, for example after a backup restore) reaches you only through
`FailureReason.CredentialUnreadable` and `CredentialLookup.Unreadable`; the store reports the same situation as the
separate `KeyState.KeyMissing` state, so a `when` over `KeyState` must handle that leaf too. The set is open, so treat
an unknown cause as re-enter.

- **Tests: software keys.** A unit test on the JVM has no AndroidKeyStore. `:keystore` lets a test replace the device
  key store with software keys through `ApiKeyStore(dataStore, slots, keyAccess)`, which needs
  `@OptIn(DelicateKeyAccess::class)`. This is for tests only; production keeps the two-argument constructor. A fake
  must keep read-never-creates: `existingKey` returns null for an absent alias and never creates a key, and
  `getOrCreateKey` is the only creator. The fake below is not fit for key custody, because its keys live only in memory.
  It needs these imports on top of the ones above:

```text
import io.github.ygaray.voiceactionengine.keystore.DelicateKeyAccess
import io.github.ygaray.voiceactionengine.keystore.KeyAccess
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
```

<!-- doc-snippet: keystore-fake -->
```kotlin
// For unit tests only: software keys in place of the device key store. A fake must keep the contract that reading
// never creates a key; existingKey returns null for an absent alias, and getOrCreateKey is the only creator.
@OptIn(DelicateKeyAccess::class)
class MySoftwareKeys : KeyAccess {
    private val keys = ConcurrentHashMap<String, SecretKey>()

    override fun existingKey(alias: String): SecretKey? = keys[alias]

    override fun getOrCreateKey(alias: String): SecretKey = keys.computeIfAbsent(alias) {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}

@OptIn(DelicateKeyAccess::class)
fun testKeyStore(dataStore: DataStore<Preferences>, slots: List<KeySlot>): ApiKeyStore =
    ApiKeyStore(dataStore, slots, MySoftwareKeys())
```

- **Capability overrides.** `capabilities(provider, model) { ... }` patches what the engine believes about one exact
  model id (never a prefix or family); it wins over the provider's built-in table. Read the result with
  `pipeline.capabilityTable.lookup(provider, model)`.
- **On-device.** `OnDeviceCapability` defaults to "unavailable" (code `not_implemented`): version 1.0 ships no
  on-device provider. A tier whose only provider is on-device fails loudly instead of climbing to the cloud.

Sample: `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeySlots.kt` and
`sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/net/ProviderFactory.kt`.

## 8. Policy and telemetry

`TierPolicySource` supplies the limits for each command (`TierPolicy`: `offlineOnly`, `maxTier`, `allowedProviders`,
`maxIterations`, `tokenCeiling`, `maxTokensPerTurn`, `commandTimeoutMillis`, `pickerTimeoutMillis`); `TierPolicySource.fixed(...)` returns
one policy for every command. The engine enforces `offlineOnly`, `maxTier`, `allowedProviders`, `commandTimeoutMillis` and
`pickerTimeoutMillis` (how long a start-tier picker may take before the walk starts at the first model tier; default
2,000 ms, and the earlier of it and `commandTimeoutMillis` wins); `maxIterations`,
`tokenCeiling` and `maxTokensPerTurn` are limits the built-in strategies read and enforce themselves. A `PipelineEventListener` receives `PipelineEvent`s as a run happens; it cannot suspend, and if it throws
the command carries on and the trace records `listener_error`. The finished `outcome.trace` (`CommandTrace`) holds the
tier attempts, the model turns, the usage and the `TraceCode`s. `outcome.trace.selection` (a `StartTierSelection`)
holds the picker's turns, which count in `trace.usage`; the walk records `router_fallback` when the picker gave no
usable answer.

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

The snippet below calls `keyAdvice` from step 7 (the `keystore-wiring` block); copy that function with it. It needs
these imports, on top of the ones step 7 lists for `keyAdvice`:

```text
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Clarification
import io.github.ygaray.voiceactionengine.core.strategy.ClarificationOption
```

What the snippet reads: `CommandOutcome.Completed.reply` is the tier's text answer or null (a `String?`);
`FailureReason.code` and `EscalationReason.code` are the stable machine-readable codes of any reason (every
reason has one); `FailureReason.NotConfigured.provider` is the `ProviderId?` that has no usable key or configuration (null when
the failure is not specific to one provider); `EscalationReason.NoToolCall` means the model answered in words and called no tool (a class:
match it with `is`). [`API.md`](API.md) ("Shapes you construct or read") lists these members.

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
the id your app selects (so your tier declarations and selection apply unchanged) and `requiresCredential = false`.
The snippet needs these imports (`ArrayDeque` is Kotlin's own); the constructor shapes it uses
(`ModelResult.Success(ModelResponse(...))`, `ModelResult.Failure(reason)`, `FailureReason.Other(code)`,
`AssistantMessage(parts)`) are listed in [`API.md`](API.md) ("Shapes you construct or read"):

```text
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
```

The test dependencies are `kotlinx-coroutines-test` and `junit` (`testImplementation`; the engine does not publish
them). The last three imports are for the test that drives the provider, not for the provider class itself.

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

## 11. Undo a whole command

```kts
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-undo:<version>")
```

`undo` depends on nothing, not even `core` (only the Kotlin standard library), so a non-voice app can use it alone. The
first tag that carries it is v1.1.0. It gives you an `UndoJournal`: record each change a command applies, then one call
puts everything back, or refuses and writes nothing.

**What you write.** The journal never touches your storage; you describe it.

- Register one `EntityAdapter` per entity type: `read(id)` returns an opaque snapshot (a parent carries its children),
  `fingerprint(id)` returns a value that changes whenever the entity does (for a parent, also whenever any child that
  `read` snapshots changes, so fold the children's versions into it: a child edit the fingerprint cannot see would be
  overwritten by the restore), and `restoreIf(id, expectedFingerprint,
  snapshot)` checks the fingerprint and writes in one transaction of yours, re-inserting a missing entity with its
  original id and children. Build the journal with `UndoJournal { adapter(...) }`.
- Give every mutation its own ticket from `journal.newTicket()` and hand it to the engine as the `PendingMutation.context`
  of that mutation.
- Inside `apply`, in the same transaction as the write, call `ticket.capture(type, id)` before the write and
  `ticket.settle(type, id)` after it. Use `created(type, id)` for an entity whose id exists only after the write,
  `touches(type, id)` for related entities the write changed without a capture, `compensate(kind, payload)` for an effect
  outside the database, or `nothingWritten()` when the apply failed before writing. Declare the footprint there, never
  derive it from the tool's arguments. A held change is captured only when `commitHeld` applies it, so the snapshot is the
  state at that moment, not the state when it was proposed.

**The bridge.** A small `CommitSink` feeds the journal from the pipeline. This is the reference wiring, copied from the
compiled and executed sample:

<!-- doc-snippet: undo-bridge -->
```kotlin
// Feeds the undo journal from the pipeline. List it FIRST in compositeSink(...), so the journal already holds an
// action when the app's own sink reacts to it. The number in "Undo all (N)" is journal.group(key)?.count; held
// proposals that are not confirmed yet are shown apart (pendingHeld) and are never part of N. The three maps are
// small and keyed by run id; an app that runs for days should prune them with the journal's own limits.
class UndoCommitSink(private val journal: UndoJournal) : CommitSink {
    private val groups = ConcurrentHashMap<String, String>() // run id -> group key
    private val parents = ConcurrentHashMap<String, String>() // run id -> the run it answers
    private val pending = ConcurrentHashMap<String, Int>() // group key -> held proposals not yet confirmed

    fun groupOf(runId: String): String = groups[runId] ?: runId
    fun pendingHeld(groupKey: String): Int = pending[groupKey] ?: 0
    fun discarded(groupKey: String) {
        pending.computeIfPresent(groupKey) { _, count -> if (count <= 1) null else count - 1 }
    }

    override suspend fun onAction(event: ActionEvent) {
        val action = event.action
        val confirmed = event.heldRunId != null
        // A change confirmed later joins the command that held it; every other run is its own group.
        val group = event.heldRunId ?: event.runId
        // The first event of a confirmed child run settles one held proposal.
        if (groups.putIfAbsent(event.runId, group) == null && confirmed) discarded(group)
        event.parentRunId?.let { parents.putIfAbsent(event.runId, it) }
        if (!action.applied) return
        // A reply keeps the group it continues, so a combined undo stays possible; a confirmed child continues what
        // the held run continued, never the held run itself.
        val parent = (if (confirmed) parents[group] else event.parentRunId)?.let(::groupOf)
        try {
            val entry = EntryRef(event.runId, action.position, action.toolName)
            journal.record(group, parent, entry, action.kind == ActionKind.IS_ERROR, action.context as? UndoTicket)
        } catch (_: IllegalArgumentException) {
            // An action the journal cannot take must withhold "Undo all", never shrink it. A group key the journal
            // rejects (blank, over 256 characters) cannot be withheld either: no group exists to offer "Undo all" for.
            try {
                journal.withhold(group)
            } catch (_: IllegalArgumentException) {
                // nothing to withhold
            }
        }
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) {
        val group = groupOf(runId)
        if (termination.held.isNotEmpty()) pending.merge(group, termination.held.size, Int::plus)
        val applied = termination.executed.filter { it.applied }.map { it.position }.toSet()
        if (applied.isNotEmpty()) journal.runClosed(group, runId, applied)
    }
}
```

Wire it as `commitSink = compositeSink(UndoCommitSink(journal), yourSink)`, with the journal first so your own sink sees an
up-to-date count when it reacts. The reference wiring is
`sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/UndoCommitSink.kt`.

**Grouping and "Undo all (N)".** The bridge groups by `heldRunId ?: runId`, so a change confirmed later with `commitHeld`
joins the command that held it, and a clarification reply is its own group whose `parentGroupKey` names the group it
continues. N is `journal.group(key)?.count`: the applied actions not yet undone, which includes an action that reported an
error after it may have written and an action that wrote nothing. Show proposals that are still waiting for
confirmation apart from N, with `bridge.pendingHeld(key)`; they are never counted. When `group.withheld` is true the
journal missed an action: show "undo unavailable". It never offers N-1. `UndoGroup` is a snapshot, so ask again when the
screen refreshes. The bridge's three small maps are keyed by run id and are never pruned, so an app that runs for days
should drop entries as the journal drops groups.

**Undo and refusals.** `journal.undoAll(key)` verifies every entity the group touched before it writes anything. If any
entity changed, was deleted or was recreated since the command, the whole group is refused and nothing is written.
Otherwise it restores newest action first, then runs the compensators, once each, after all the restores. A compensator
may be retried, so make it idempotent. A second tap while an undo runs gets `UndoReason.IN_PROGRESS`.
`journal.undoEntry(key, entry)` undoes one action and only when it shares no entity with another action not yet undone
(it is in `group.isolated`); otherwise it is refused with `UndoReason.ENTANGLED` and nothing is written, and `undoAll` is
the way. A retry after a `Partial` result touches only what is left.

**Results.** `UndoResult` is a closed set: switch over it exhaustively, with no `else`.

| Result | Meaning | Carries |
|---|---|---|
| `UndoResult.Complete` | Every action in scope was restored. | `restored`, newest first |
| `UndoResult.Refused` | Nothing was written. | `blockers`, newest action first, never empty |
| `UndoResult.Partial` | The undo went ahead and at least one item was not restored. | `restored` (may be empty: the first step can fail) and `notRestored`, exact lists with the reason |
| `UndoResult.AlreadyUndone` | There was nothing left to undo. | nothing |

`UndoReason` is an open set (`CHANGED_SINCE`, `CHAIN_BROKEN`, `UNVERIFIABLE`, `ENTANGLED`, `JOURNAL_WITHHELD`,
`UNKNOWN_GROUP`, `UNKNOWN_ENTRY`, `IN_PROGRESS`, `NO_ADAPTER`, `RESTORE_FAILED`, `COMPENSATOR_FAILED`,
`SKIPPED_AFTER_FAILURE`): keep an `else` when you switch over it. A result carries class names, keys and counts only,
never an entity's content, and the library never logs.

To show the result in an undo status of your own, map it like this:

| `UndoResult` | Your status |
|---|---|
| `Complete` | `Undone(restored.size)` |
| `Refused` | `Refused(reason, entity id of the first blocker)`, where the blockers are ordered newest action first (no entity id when the first blocker names none, for example a withheld group) |
| `Partial` | `Failed`; show `notRestored` so the user knows what is still changed |
| `AlreadyUndone` | `NothingToUndo` |

**Limits and the store.** The journal lives in memory and does not survive process death. It keeps at most 50 groups and
drops a group idle for an hour by default (`maxGroups`, `maxAgeMillis` in the `UndoJournal { }` builder), so snapshots of
user data neither pile up nor linger. A late record into a dropped group (a held change confirmed long after its run)
makes a withheld group, but the journal remembers only the keys of the most recently dropped groups (at most the larger
of 1000 and 20 times `maxGroups`); past that, a record opens a fresh group that is not withheld, so confirm held changes
within that span. An optional `JournalStore` (`store = ...` in the builder) is told about each
group's view and about every dropped group, so you can show history somewhere else. It receives keys, references, flags
and counts, never a snapshot or a fingerprint, so it cannot restore the journal. A store that throws never changes what the
journal does; `journal.storeFaults` counts its faults.

## 12. Turn a final :stt segment into a CommandInput (optional)

`voice-adapter` is an optional Android library (AAR, minSdk 35) that turns one final speech segment from `:stt` into the
`CommandInput` the pipeline takes. The first tag that carries it is v1.1.0. Skip it if your app captures speech some other
way: `commandInputOf` below lets you build the same input without the `:stt` types.

**What it gives you.** One call maps one final segment. Nothing else is in the module: no types, only top-level functions.

- `toCommandInput()` on a `FinalSegment`, and `toCommandInput(context, parentRunId)`, for the run context and the id of
  the run a reply continues. Pass null for whichever of the two you do not have.
- `commandInputOf(text, label)` and `commandInputOf(text, label, context, parentRunId)`, for a caller that has no
  `:stt` type on its classpath (a different capture path, or a test). There is deliberately no form that takes only a
  context, so a run id can never be mistaken for a context.
- `normalizeSttLanguageLabel(raw)`, the label rule on its own.

The transcript is passed verbatim: it is not trimmed and not validated, so guard a blank transcript yourself before you
call `execute`. The segment id is dropped.

**One segment per call.** The adapter has no joiner. If your session produces several final segments, keep your own
aggregation (the longest, the last, or all of them joined) and pass the one result; the engine does not choose for you.

**The language label passes through, and null means unknown.** A label of `en` or `es` (ignoring case and surrounding
whitespace) becomes the `CommandInput` language. Anything else becomes null: a regional tag such as `en-US`, `auto`, a
blank, another language, or null itself. The engine never turns a null into `en`, and the adapter never guesses.

- Null means `:stt` produced no usable label. On API 34 and newer its native backend leaves `language` null when the
  detection is not confident, and a session that is not in `auto` mode carries no label.
- The server backend always answers `en` or `es`, with an `en` fallback that the adapter cannot tell apart from a real
  detection. Treat a server label as a hint, not as proof of what was spoken. A detected-versus-fallback signal is tracked
  in the `:stt` project's own backlog, not in this module, and the adapter works around nothing.

**Never log a segment.** A `FinalSegment`'s string form prints the raw transcript. Do not log it, interpolate it into a
message, or put it in a crash report. The adapter never stringifies one.

**The documented minimum.** `:stt` v0.7.0 or newer; the `language` property compiles against v0.6.0. The property exists
since v0.6.0, and the adapter is compiled against it, but v0.7.0 is the first version whose native `auto` mode fills it.
The module needs minSdk 35. Below API 34 the native `auto` mode stamps a literal `en` without detecting, which would
look like a detection and is not one.

**Add `:stt` yourself.** The adapter keeps `:stt` compile-only, so it is never transitive: it does not pull `:stt`, its
OkHttp 5.x, its corrections or its coroutines-android onto your app. You add the speech engine, and you let Gradle fetch
it from JitPack through a repository that serves exactly the two groups you need. In **`settings.gradle.kts`**:

```kts
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter {
                includeGroup("com.github.Ygaray.voice-action-engine")
                includeGroup("com.github.Ygaray.voice-engine-android")
            }
        }
        google()
        mavenCentral()
    }
}
```

Then, in your app module:

```kts
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-voice-adapter:<version>")
implementation("com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0")
```

Use the per-module `:stt` coordinate above, never the plain aggregator coordinate, which pulls all five `:stt` modules. An
app that already declares the JitPack repository plainly (step 1) may keep that and skip the filter.

## Notes and gotchas

- **Unsupported and uncached combinations.** OpenRouter model ids of the form `anthropic/<id>` are uncached in v1.0:
  no cache markers are sent through a router (OpenAI models cache automatically, Anthropic's own provider uses explicit
  breakpoints). The OpenAI ids the engine knows answer only on the Responses endpoint, `gpt-6-astra` and `gpt-6.1-sol`,
  fail with `FailureReason.ModelUnsupported` before any network call (through OpenRouter they work). Anthropic models that
  reject a forced tool choice (`claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1`, `claude-mythos-5-1`) are sent
  `auto` plus an instruction to call the tool; a dated id such as `claude-opus-5-5-20261001` is not in the table, so
  its first request is forced, refused and re-sent, which doubles that call's latency and spend until you add a
  `capabilities(...)` override with `supportsForcedToolChoice = false`. Direct OpenAI `gpt-5` and `gpt-6` `-pro` and
  `-codex` ids (for example `gpt-5-pro` or `gpt-5-codex`) are refused the same way, before any call, when the request
  carries tools. The list is targeted, not general: any other Responses-only id (for example `o3-pro`) is not on it, so
  its first request reaches OpenAI and fails with the mapped endpoint error. Add a `capabilities(...)` override with
  `supportsTools = false` for such an id to refuse it before any call.
  A single-tool SingleShot prefix is usually shorter than the provider's minimum cacheable prefix, so it will not cache
  on `claude-haiku-4-5` (4,096 tokens) or on OpenAI (1,024 tokens); claude-sonnet-5 needs 1,024 and claude-sonnet-5-5
  needs 512.
- **Map your picker or Router id** in your selection source the same way you map tier ids, and keep it different from
  every tier id. The Router's wording is engine-owned and may be tuned in a later version without an API change.
- **Open taxonomies need `else`:** `FailureReason`, `EscalationReason`, `Resolution`, `CredentialLookup`, `KeyState`,
  `ActionKind`, `FinishedKind`, `PipelineEvent`, `TraceCode`, `StopReason`, `ToolChoice`, `ModelResult`,
  `OnDeviceAvailability`, `TierSelector`, `AnthropicAttemptKind` and `ChatCompletionsAttemptKind`. Later versions add
  members without breaking you.
- **Closed taxonomies are matched exhaustively, with no `else`:** `CommandOutcome`, `GateDecision`, `RunTermination`,
  `ToolStep`, `StrategyOutcome`, `Message` and `AssistantPart`.
- **The engine never throws** out of `execute` or `commitHeld`, except for your own coroutine's cancellation or a JVM
  `Error`. A provider, strategy, gate or hook that throws becomes a typed failure, a hold or an error action. The one refusal is
  `commitHeld(held, amended)` with an empty `amended` list: it throws `IllegalArgumentException` before the proposal is
  used up.
- **One DataStore per file per process.** `:keystore` takes the DataStore you own and never creates one; a second
  DataStore on the same file throws at runtime.
- **A held change lives in memory only** and does not survive the process.
- **Model ids match exactly.** Built-in capability tables key Anthropic ids exactly and OpenAI ids by family pattern;
  anything else gets the unknown-model default until you override it.
- **Undo journal order and footprint.** Put the journal's sink first in `compositeSink(...)`, so it holds an action when
  your own sink reacts, and never derive the footprint from a tool's `targetIds`: declare it on the ticket inside `apply`.
- **Working example.** The `:sample` app (`sample/`) is the reference wiring and is never published; its tool names
  are synthetic.
