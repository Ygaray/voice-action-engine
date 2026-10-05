package wire

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
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
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.Clarification
import io.github.ygaray.voiceactionengine.core.strategy.ClarificationOption
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

/** Two domain-free tools: one mutating create, one read, plus the ready-made clarification tool. */
object WireTools {
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

class WireCreateItem(private val items: MutableList<String>, private val title: String) : PendingMutation {
    override val toolName: String = "create_item"

    override suspend fun apply(): StepResult {
        items.add(title)
        return StepResult("""{"status":"created"}""")
    }
}

class WireResolver(private val items: MutableList<String>) : OutcomeResolver {
    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        val title = (extraction.arguments["title"] as? JsonPrimitive)?.contentOrNull
        return if (extraction.toolName == "create_item" && !title.isNullOrBlank()) {
            Resolution.Steps(listOf(ToolStep.Mutation(WireCreateItem(items, title))), "Added it.")
        } else {
            Resolution.NoMatch()
        }
    }
}

/** Records the kind of every action the engine reports, in order. */
class WireCommitSink : CommitSink {
    val kinds: MutableList<ActionKind> = mutableListOf()

    override suspend fun onAction(event: ActionEvent) {
        kinds.add(event.action.kind)
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit
}

val WireAdmitAll = PreApplyGate { GateDecision.Admit() }

/** No key stored: enough for a scripted provider that does not require one. */
val WireNoKeys = CredentialSource { CredentialLookup.Missing() }

fun wireTier(items: MutableList<String>): CommandStrategy =
    SingleShotStrategy(StrategyId("single_shot")) {
        tooling = ToolSpecProvider.fixed(WireTools.snapshot(forced = "create_item"))
        resolver = WireResolver(items)
    }

fun wirePipeline(
    items: MutableList<String>,
    aiProvider: AiProvider,
    credentialSource: CredentialSource = WireNoKeys,
    sink: CommitSink = WireCommitSink(),
): CommandPipeline = commandPipeline {
    tier(wireTier(items))
    provider(aiProvider)
    providerSelection = ProviderSelectionSource { ProviderSelection(aiProvider.id, "my-model") }
    credentials = credentialSource
    gate = WireAdmitAll
    commitSink = sink
}

class WireView(val headline: String, val choices: List<ClarificationOption> = emptyList())

/** Renders every outcome: the closed outcome type has no else; the open reason sets do. */
fun render(outcome: CommandOutcome): WireView = when (outcome) {
    is CommandOutcome.Completed -> renderCompleted(outcome)
    is CommandOutcome.Failed -> WireView(failureText(outcome.reason, outcome.commits.size))
    is CommandOutcome.Unhandled -> WireView(unhandledText(outcome.lastReason))
}

fun renderCompleted(outcome: CommandOutcome.Completed): WireView {
    val clarification: Clarification? = outcome.terminalCall?.asClarification()
    val choices = clarification?.options.orEmpty()
    return when {
        outcome.partial -> WireView("Did ${outcome.executed.size} action(s), couldn't finish", choices)
        clarification != null -> WireView(clarification.question, choices)
        else -> WireView(outcome.reply?.let { "Done: $it" } ?: "Done")
    }
}

fun failureText(reason: FailureReason, committed: Int): String {
    val what = when (reason) {
        is FailureReason.NotConfigured -> "Not configured: add a key for ${reason.provider ?: "a provider"}"
        else -> "Failed: ${reason.code}"
    }
    return if (committed == 0) what else "$what ($committed change(s) already committed)"
}

fun unhandledText(reason: EscalationReason?): String = when (reason) {
    null -> "Not handled"
    is EscalationReason.NoToolCall -> "I could not tell which action you meant"
    else -> "Not handled: ${reason.code}"
}
