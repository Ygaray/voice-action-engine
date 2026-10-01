# voice-action-engine

A generic, domain-free Android library that turns a spoken command into a typed outcome. Your app hands it a
transcript (`CommandInput(transcript, language)`); the engine walks a **tier ladder that you compose**
(single-shot, agentic loop, more tiers later) over pluggable providers (Anthropic, OpenAI, OpenRouter), cheap tier
first, escalating only when needed. Every write goes through a gate you control, every failure comes back as a
specific reason, and keys, transcripts and tool arguments never reach a log. It knows nothing about your app: you
bring the tools, the resolver, the gate and the sink.

**Docs for agents & integrators:**
- **[`INTEGRATION.md`](INTEGRATION.md)**: numbered adoption steps from repository to a rendered outcome, ending in notes and gotchas.
- **[`API.md`](API.md)**: the public surface at a glance, one section per area, and every extension point.
- **[`ECOSYSTEM.md`](ECOSYSTEM.md)**: the coordinate table, the consumers and the invariants.
- Working example: the `:sample` app in [`sample/`](sample/), never published. It is in the repository
  (<https://github.com/Ygaray/voice-action-engine>), not in the published artifacts, so a consumer workspace does not
  contain it; the docs are written to be followed without it. Start with
  `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngine.kt` (the composition root),
  `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt` and
  `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/OutcomeText.kt`.

**Status:** v1.0 is in verification. Until a release tag exists, pin a commit SHA.

## Install (JitPack)

Add the JitPack repository in your **`settings.gradle.kts`**:

```kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Depend on the modules you use, per module, never as one aggregate. `<version>` is an immutable release tag or a
commit SHA, never a branch snapshot:

```kts
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-core:<version>")
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-providers:<version>")
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:<version>")
```

`providers` compiles against OkHttp 4.12 and is tested on 4.12 and 5.x, so your app keeps its own OkHttp version.
`keystore` is an Android library (AAR) for bring-your-own-key storage; `core` is pure Kotlin.

## Minimal usage

This is the whole wiring of one tier (the code below is compiled and run by the repository's tests). Nothing is
committed unless your gate admits it, so a gate and a sink are required. The types live in sub-packages of
`io.github.ygaray.voiceactionengine` (not directly in it); the imports the snippet needs are:

```text
import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
```

[`API.md`](API.md) has the package of every public type, for anything beyond this snippet.

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

```text
val pipeline = buildPipeline(listOf(singleShotTier(items)), provider, credentials, MyAdmitAll)
val outcome = pipeline.execute(CommandInput("add buy paper to my list", "en"))
```

Here `items` is your own storage, `provider` an `AiProvider` (`AnthropicProvider { }`, or a scripted one in a test)
and `credentials` a `CredentialSource` (the `keystore` module provides one).

`CommandOutcome` is closed: `Completed`, `Failed` or `Unhandled`. Render all three. A `Completed(partial = true)`
means some work was done and the rest was not: show "did X, couldn't finish", never full success. The numbered steps
in [`INTEGRATION.md`](INTEGRATION.md) cover the rest.

## Requirements

Kotlin 2.3.20, JVM 11 bytecode, Android `minSdk 35` for `keystore`. The `:sample` app is never published.
