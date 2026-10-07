package io.github.ygaray.voiceactionengine.sample.docs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.AwaitingConfirmGate
import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.ConfirmationPolicy
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.Clarification
import io.github.ygaray.voiceactionengine.core.strategy.ClarificationOption
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.DelicateKeyAccess
import io.github.ygaray.voiceactionengine.keystore.KeyAccess
import io.github.ygaray.voiceactionengine.keystore.KeySlot
import io.github.ygaray.voiceactionengine.keystore.KeystoreCauseCodes
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.keystore.KeystoreCredentialSource
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import io.github.ygaray.voiceactionengine.sample.undo.CreateItem
import io.github.ygaray.voiceactionengine.sample.undo.ItemAdapter
import io.github.ygaray.voiceactionengine.sample.undo.ItemStore
import io.github.ygaray.voiceactionengine.sample.undo.RenameItem
import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.EntryRef
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import io.github.ygaray.voiceactionengine.undo.UndoReason
import io.github.ygaray.voiceactionengine.undo.UndoResult
import io.github.ygaray.voiceactionengine.undo.UndoTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

// The docs (README.md, INTEGRATION.md, API.md) quote the regions below byte for byte after removing their common
// indentation; scripts/verify-docs-coverage.sh compares them. A region holds only what a consumer would write: the
// public engine API, synthetic tool names and My-prefixed app types. Test plumbing (runTest, the no-network guard,
// assertions) stays outside every region.

// doc-snippet:start scripted-provider
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
// doc-snippet:end scripted-provider

// doc-snippet:start minimal-pipeline
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
// doc-snippet:end minimal-pipeline

// doc-snippet:start register-providers
fun PipelineBuilder.registerProviders(shared: OkHttpClient?) {
    provider(AnthropicProvider { httpClient = shared })
    provider(ChatCompletionsProvider.openAi { httpClient = shared })
    provider(ChatCompletionsProvider.openRouter { httpClient = shared })

    // A dated model id is not in the built-in table: tell the engine what it cannot do (exact ids only).
    capabilities(ProviderId.ANTHROPIC, "claude-opus-5-5-20261001") { supportsForcedToolChoice = false }

    // Version 1.0 ships no on-device provider, so the default already says "unavailable".
    onDevice = OnDeviceCapability { OnDeviceAvailability.Unavailable("not_installed") }
}
// doc-snippet:end register-providers

// doc-snippet:start agentic-tier
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
// doc-snippet:end agentic-tier

// doc-snippet:start gate-suspend
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
// doc-snippet:end gate-suspend

// doc-snippet:start gate-defer
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
// doc-snippet:end gate-defer

// doc-snippet:start keystore-wiring
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
// doc-snippet:end keystore-wiring

// doc-snippet:start keystore-fake
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
// doc-snippet:end keystore-fake

// doc-snippet:start fixed-credentials
// Serves the one key the app already holds (read from its own secure storage); the key is a parameter, never a literal.
fun fixedCredentials(apiKey: String): CredentialSource = CredentialSource { provider ->
    if (provider == ProviderId.ANTHROPIC) {
        CredentialLookup.Present(Credential(provider, apiKey))
    } else {
        CredentialLookup.Missing()
    }
}
// doc-snippet:end fixed-credentials

// doc-snippet:start render-outcome
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
// doc-snippet:end render-outcome

// doc-snippet:start clarification-follow-up
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
// doc-snippet:end clarification-follow-up

// doc-snippet:start telemetry
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
// doc-snippet:end telemetry

// doc-snippet:start undo-bridge
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
// doc-snippet:end undo-bridge

// doc-snippet:start grammar-tier
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
// doc-snippet:end grammar-tier

// doc-snippet:start plan-tier
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
// doc-snippet:end plan-tier

// doc-snippet:start router-selector
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
// doc-snippet:end router-selector

// doc-snippet:start undo-wiring
class MyItem(val id: String, val title: String, val version: Int)

// Stands in for your database. Every write takes a new version, which the adapter reports as the fingerprint.
class MyItemStore {
    private val items = LinkedHashMap<String, MyItem>()
    private var created = 0
    private var versions = 0

    fun get(id: String): MyItem? = items[id]

    fun titles(): List<String> = items.values.map { it.title }

    fun add(title: String): MyItem = MyItem("item-${++created}", title, ++versions).also { items[it.id] = it }

    fun rename(id: String, title: String) {
        if (items.containsKey(id)) items[id] = MyItem(id, title, ++versions)
    }

    // Do the check and the write in one transaction of yours. A null snapshot deletes the item.
    fun restoreIf(id: String, expected: String?, snapshot: MyItem?): Boolean {
        if (items[id]?.version?.toString() != expected) return false
        if (snapshot == null) items.remove(id) else items[id] = snapshot
        return true
    }
}

class MyItemAdapter(private val store: MyItemStore) : EntityAdapter {
    override val entityType: String = "item"

    override suspend fun read(id: String): Any? = store.get(id)

    override suspend fun fingerprint(id: String): String? = store.get(id)?.version?.toString()

    override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean =
        store.restoreIf(id, expectedFingerprint, snapshot as MyItem?)
}

// The ticket is the mutation's context: the bridge hands it to the journal when the action is recorded.
class MyAddItem(
    private val store: MyItemStore,
    private val ticket: UndoTicket,
    private val title: String,
) : PendingMutation {
    override val toolName: String = "create_item"
    override val context: UndoTicket = ticket

    override suspend fun apply(): StepResult {
        val item = store.add(title)
        ticket.created("item", item.id)
        return StepResult("""{"status":"created"}""")
    }
}

class MyRenameItem(
    private val store: MyItemStore,
    private val ticket: UndoTicket,
    private val id: String,
    private val title: String,
) : PendingMutation {
    override val toolName: String = "rename_item"
    override val context: UndoTicket = ticket

    override suspend fun apply(): StepResult {
        if (store.get(id) == null) {
            ticket.nothingWritten()
            return StepResult("""{"status":"not_found"}""", true)
        }
        ticket.capture("item", id)
        store.rename(id, title)
        ticket.settle("item", id)
        return StepResult("""{"status":"renamed"}""")
    }
}

fun myJournal(store: MyItemStore): UndoJournal = UndoJournal { adapter(MyItemAdapter(store)) }

// One ticket per mutation: build the steps in your ToolExecutor, so every change the engine applies has its own.
fun addItemStep(journal: UndoJournal, store: MyItemStore, title: String): ToolStep =
    ToolStep.Mutation(MyAddItem(store, journal.newTicket(), title))

fun renameItemStep(journal: UndoJournal, store: MyItemStore, id: String, title: String): ToolStep =
    ToolStep.Mutation(MyRenameItem(store, journal.newTicket(), id, title))

// The journal's sink goes FIRST, so it already holds an action when your own sink reacts to it.
fun PipelineBuilder.undoWiring(journal: UndoJournal, appSink: CommitSink): UndoCommitSink {
    val bridge = UndoCommitSink(journal)
    commitSink = compositeSink(bridge, appSink)
    return bridge
}

// N is journal.group(key)?.count. A withheld group means the journal missed an action, so offer nothing.
suspend fun undoLabel(journal: UndoJournal, groupKey: String): String? {
    val group = journal.group(groupKey) ?: return null
    return if (group.withheld || group.count == 0) null else "Undo all (${group.count})"
}

// UndoResult is closed, so the outer when has no else. UndoReason is open, so the inner one keeps an else.
suspend fun undoAllStatus(journal: UndoJournal, groupKey: String): String =
    when (val result = journal.undoAll(groupKey)) {
        is UndoResult.Complete -> "Undone (${result.restored.size})"
        is UndoResult.Refused -> when (val reason = result.blockers.first().reason) {
            UndoReason.CHANGED_SINCE -> "Not undone: something changed since"
            UndoReason.IN_PROGRESS -> "An undo is already running"
            else -> "Not undone (${reason.value})"
        }
        is UndoResult.Partial -> "Some changes could not be restored (${result.notRestored.size})"
        is UndoResult.AlreadyUndone -> "Nothing to undo"
    }
// doc-snippet:end undo-wiring

private val NO_KEYS = CredentialSource { CredentialLookup.Missing() }

private fun titleArgs(title: String): JsonObject = buildJsonObject { put("title", title) }

private fun clarificationArgs(): JsonObject = buildJsonObject {
    put("question", "Which list should I add it to?")
    putJsonArray("options") {
        add(buildJsonObject { put("id", "list-a"); put("label", "List A") })
        add(buildJsonObject { put("id", "list-b"); put("label", "List B") })
    }
}

private fun stepOf(id: String, title: String, parentRef: String? = null): JsonObject = buildJsonObject {
    put("id", id)
    put("tool", "create_item")
    putJsonObject("arguments") {
        put("title", title)
        if (parentRef != null) put("parent_id", parentRef)
    }
}

private fun planAnswer(vararg steps: JsonObject): ModelResult =
    toolCallAnswer("submit_plan", buildJsonObject { putJsonArray("steps") { steps.forEach { add(it) } } })

// Holds the second proposal it is asked about and admits every other one.
private class HoldSecondGate : PreApplyGate {
    private var asked = 0

    override suspend fun admit(proposal: CommitProposal): GateDecision =
        if (++asked == 2) GateDecision.Hold("needs_review") else GateDecision.Admit()
}

/** Runs every doc snippet region over the public API with the consumer's own scripted provider, offline. */
class DocSnippetsTest {

    @Test
    fun theMinimalPipelineCompletesWithOneCommit() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val provider = MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("buy paper"))))
            val sink = MyCommitSink()
            val pipeline = buildPipeline(listOf(singleShotTier(items)), provider, NO_KEYS, MyAdmitAll, sink)

            val outcome = pipeline.execute(CommandInput("add buy paper to my list", "en"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals(outcome.toString(), 1, completed.commits.size)
            assertEquals(listOf(ActionKind.COMMITTED), completed.executed.map { it.kind })
            assertEquals(listOf(ActionKind.COMMITTED), sink.kinds)
            assertEquals(listOf("buy paper"), items)
            assertEquals(false, completed.partial)
            assertEquals("Done: Added it.", renderOutcome(completed).headline)
        }
    }

    @Test
    fun theScriptedProviderUsesOnlyThePublicApi() = runTest {
        NoNetworkGuard.during {
            val provider = MyScriptedProvider(listOf(textAnswer("hello")))
            val request = ProviderRequest(
                "my-model",
                ModelRequest("system", listOf(UserMessage("hi")), 16),
                null,
                ModelCapabilities.UNKNOWN,
            )

            val first = provider.complete(request)
            val second = provider.complete(request)

            assertTrue(first.toString(), first is ModelResult.Success)
            assertTrue(second.toString(), second is ModelResult.Failure)
            assertEquals(2, provider.requests.size)
            assertEquals("my-model", provider.requests.first().model)
        }
    }

    @Test
    fun theSuspendGateCommitsOnConfirm() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val gate = confirmingGate()
            val provider = MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("buy paper"))))
            val pipeline = buildPipeline(listOf(singleShotTier(items)), provider, NO_KEYS, gate)
            val answering = answerWhenAsked(gate, confirmed = true)

            val outcome = pipeline.execute(CommandInput("add buy paper"))
            answering.join()

            val completed = outcome as CommandOutcome.Completed
            assertEquals(listOf(ActionKind.COMMITTED), completed.executed.map { it.kind })
            assertEquals(listOf("buy paper"), items)
            assertNull(gate.pending.value)
        }
    }

    @Test
    fun aDeclinedConfirmationHoldsTheChange() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val gate = confirmingGate()
            val provider = MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("buy paper"))))
            val pipeline = buildPipeline(listOf(singleShotTier(items)), provider, NO_KEYS, gate)
            val answering = answerWhenAsked(gate, confirmed = false)

            val outcome = pipeline.execute(CommandInput("add buy paper"))
            answering.join()

            assertEquals(0, outcome.commits.size)
            assertEquals(1, outcome.held.size)
            assertTrue(items.isEmpty())
        }
    }

    @Test
    fun theDeferGateHoldsThenCommits() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val sink = MyCommitSink()
            val provider = MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("buy paper"))))
            val pipeline = buildPipeline(listOf(singleShotTier(items)), provider, NO_KEYS, MyDeferGate, sink)

            val outcome = pipeline.execute(CommandInput("add buy paper"))

            assertEquals(1, outcome.held.size)
            assertEquals(0, outcome.commits.size)
            assertTrue(items.isEmpty())
            assertEquals(
                listOf("create_item: waiting for confirmation (applied=false, mutating=true)"),
                describeActions(outcome),
            )

            val committed = approveHeld(pipeline, outcome)

            assertNotNull(committed)
            assertEquals(1, committed!!.commits.size)
            assertEquals(outcome.runId, committed.parentRunId)
            assertEquals(listOf("buy paper"), items)
            assertEquals(listOf("create_item: done (applied=true, mutating=true)"), describeActions(committed))
            assertEquals(listOf(ActionKind.HELD, ActionKind.COMMITTED), sink.kinds)
        }
    }

    @Test
    fun anEditedProposalIsCommittedInstead() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val provider = MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("buy paper"))))
            val pipeline = buildPipeline(listOf(singleShotTier(items)), provider, NO_KEYS, MyDeferGate)
            val outcome = pipeline.execute(CommandInput("add buy paper"))

            val committed = approveEdited(pipeline, outcome, listOf(MyCreateItem(items, "buy pens")))

            assertEquals(1, committed!!.commits.size)
            assertEquals(listOf("buy pens"), items)
            assertNull(approveHeld(pipeline, committed))
        }
    }

    @Test
    fun renderOutcomeCoversEveryShape() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val tier = listOf(singleShotTier(items))

            val full = buildPipeline(
                tier,
                MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("a")))),
                NO_KEYS,
                MyAdmitAll,
            ).execute(CommandInput("add a"))
            assertTrue(renderOutcome(full).headline, renderOutcome(full).headline.startsWith("Done"))

            // Two tool calls in one answer: only the first is acted on, so the run is partial.
            val partial = buildPipeline(
                tier,
                MyScriptedProvider(
                    listOf(toolCallsAnswer("create_item" to titleArgs("a"), "create_item" to titleArgs("b"))),
                ),
                NO_KEYS,
                MyAdmitAll,
            ).execute(CommandInput("add a and b"))
            assertTrue((partial as CommandOutcome.Completed).partial)
            assertEquals("Did 1 action(s), couldn't finish", renderOutcome(partial).headline)

            val clarifying = buildPipeline(
                listOf(clarifyingTier(items)),
                MyScriptedProvider(listOf(toolCallAnswer("ask_user", clarificationArgs()))),
                NO_KEYS,
                MyAdmitAll,
            ).execute(CommandInput("add paper to my list"))
            val view = renderOutcome(clarifying)
            assertEquals("Which list should I add it to?", view.headline)
            assertEquals(listOf("list-a", "list-b"), view.choices.map { it.id })
            assertEquals(listOf("List A", "List B"), view.choices.map { it.label })

            val unknown = buildPipeline(
                tier,
                MyScriptedProvider(listOf(ModelResult.Failure(FailureReason.Other("weird_code")))),
                NO_KEYS,
                MyAdmitAll,
            ).execute(CommandInput("add a"))
            assertEquals("Failed: weird_code", renderOutcome(unknown).headline)

            val unhandled = buildPipeline(
                tier,
                MyScriptedProvider(listOf(textAnswer("I am not sure"))),
                NO_KEYS,
                MyAdmitAll,
            ).execute(CommandInput("hmm"))
            assertTrue(unhandled.toString(), unhandled is CommandOutcome.Unhandled)
            assertEquals("I could not tell which action you meant", renderOutcome(unhandled).headline)

            assertEquals("Not handled: custom", unhandledText(EscalationReason.Other("custom")))
            assertEquals("Not handled", unhandledText(null))
            assertEquals("Failed: auth (2 change(s) already committed)", failureText(FailureReason.Auth(), 2))
        }
    }

    @Test
    fun theFollowUpIsLinked() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val provider = MyScriptedProvider(
                listOf(
                    toolCallAnswer("ask_user", clarificationArgs()),
                    toolCallAnswer("create_item", titleArgs("paper")),
                ),
            )
            val pipeline = buildPipeline(listOf(clarifyingTier(items)), provider, NO_KEYS, MyAdmitAll)
            val first = pipeline.execute(CommandInput("add paper to my list")) as CommandOutcome.Completed
            val option = first.terminalCall!!.asClarification()!!.options.first { it.id == "list-b" }

            val second = chooseOption(pipeline, first, "add paper to my list", option) as CommandOutcome.Completed

            assertEquals(first.runId, second.parentRunId)
            assertEquals(1, second.commits.size)
            assertEquals(listOf("paper"), items)
            val text = (provider.requests[1].request.messages.first() as UserMessage).text
            assertTrue(text, "add paper to my list" in text)
            assertTrue(text, "Which list should I add it to?" in text)
            assertTrue(text, "The user chose: List B (list-b)" in text)
        }
    }

    @Test
    fun theAgenticTierRunsAToolTurn() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf("buy paper")
            val provider = MyScriptedProvider(
                listOf(
                    textAnswer("no tool"),
                    toolCallAnswer("find_items", buildJsonObject { put("query", "paper") }),
                    textAnswer("You have one item about paper."),
                ),
            )
            val pipeline = buildLadder(provider, NO_KEYS, MyAdmitAll, items)

            val outcome = pipeline.execute(CommandInput("what do I have about paper"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals("You have one item about paper.", completed.reply)
            assertEquals(0, completed.commits.size)
            assertEquals(3, provider.requests.size)
            assertEquals(listOf("single_shot", "agentic"), completed.trace.attempts.map { it.strategy.value })
            assertTrue(traceSummary(completed).startsWith("tiers=[single_shot, agentic]"))
        }
    }

    @Test
    fun telemetryAndProviderRegionsCompile() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val log = MyEventLog()
            val provider = MyScriptedProvider(listOf(toolCallAnswer("create_item", titleArgs("buy paper"))))
            val pipeline = buildPipeline(
                listOf(singleShotTier(items)),
                provider,
                NO_KEYS,
                MyAdmitAll,
                configure = { limitsAndTelemetry(log) },
            )

            pipeline.execute(CommandInput("add buy paper"))

            assertTrue(log.lines.toString(), log.lines.first().startsWith("started "))
            assertTrue(log.lines.toString(), "action committed create_item applied=true" in log.lines)
            // Registering the real providers is construction only; here beside a provider with another id.
            val registered = buildPipeline(
                listOf(singleShotTier(items)),
                MyScriptedProvider(emptyList(), ProviderId("scripted")),
                NO_KEYS,
                MyAdmitAll,
                configure = { registerProviders(null) },
            )
            val table = registered.capabilityTable
            assertEquals(false, table.lookup(ProviderId.ANTHROPIC, "claude-opus-5-5-20261001").supportsForcedToolChoice)
            assertEquals(true, table.lookup(ProviderId.ANTHROPIC, "claude-haiku-4-5").supportsForcedToolChoice)
        }
    }

    @Test
    fun theGrammarTierAnswersBothLanguagesWithoutAProviderCall() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val provider = MyScriptedProvider(emptyList())
            val resolver = MyGrammarResolver(items, "en")
            val pipeline = grammarLadder(provider, NO_KEYS, MyAdmitAll, items, resolver)

            val english = pipeline.execute(CommandInput("please add buy paper to my list", "en"))
            val spanish = pipeline.execute(CommandInput("por favor pon comprar papel en mi lista", "es"))

            assertTrue(english.toString(), english is CommandOutcome.Completed)
            assertTrue(spanish.toString(), spanish is CommandOutcome.Completed)
            assertEquals(listOf("buy paper", "comprar papel"), items)
            assertEquals(listOf("en", "es"), resolver.spoken)
            assertEquals(emptyList<ProviderRequest>(), provider.requests)
            assertEquals(listOf("grammar"), english.trace.attempts.map { it.strategy.value })
            assertEquals(0L, english.trace.usage.total)
            assertEquals(0L, spanish.trace.usage.total)
        }
    }

    @Test
    fun aGrammarNearMissIsNullAndAnOfflineCommandIsCappedByPolicy() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val pack = myGrammarPack()

            assertEquals("en", pack.match("please add buy paper to my list", "en")?.matchedLanguage)
            assertEquals("es", pack.match("agrega comprar papel a mi lista", "es")?.matchedLanguage)
            assertEquals(
                "buy paper",
                (pack.match("add buy paper to my list", "en")?.arguments?.get("title") as? JsonPrimitive)?.contentOrNull,
            )
            assertNotNull(pack.match("add buy paper to my list", null))
            assertNull(pack.match("add buy paper to my list right now", "en"))
            assertNull(pack.match("add buy paper to my list", "fr"))

            val provider = MyScriptedProvider(emptyList())
            val pipeline = buildPipeline(
                listOf(grammarTier(MyGrammarResolver(items, "en")), singleShotTier(items)),
                provider,
                NO_KEYS,
                MyAdmitAll,
                configure = { policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true }) },
            )

            val missed = pipeline.execute(CommandInput("add buy paper to my list right now", "en"))

            assertTrue(missed.toString(), missed is CommandOutcome.Unhandled)
            assertTrue(missed.toString(), (missed as CommandOutcome.Unhandled).cappedByPolicy)
            assertTrue(items.isEmpty())
            assertEquals(emptyList<ProviderRequest>(), provider.requests)
        }
    }

    @Test
    fun theSecondPlanStepReceivesTheFirstStepsId() = runTest {
        NoNetworkGuard.during {
            val rows = mutableListOf<MyRow>()
            val provider = MyScriptedProvider(
                listOf(planAnswer(stepOf("first", "Groceries"), stepOf("second", "Milk", "\$first.id"))),
            )
            val pipeline = buildPipeline(listOf(planTier(rows)), provider, NO_KEYS, MyAdmitAll)

            val outcome = pipeline.execute(CommandInput("make a list called groceries with milk under it", "en"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals(outcome.toString(), 2, completed.commits.size)
            assertEquals(false, completed.partial)
            assertTrue(completed.remainingStepIds.isEmpty())
            assertEquals(1, provider.requests.size)
            assertEquals(listOf("Groceries", "Milk"), rows.map { it.title })
            assertNull(rows[0].parentId)
            assertEquals(rows[0].id, rows[1].parentId)
            assertEquals("Done", planSummary(outcome))
        }
    }

    @Test
    fun aHoldEndsThePlanPartiallyAndListsTheStepsThatNeverRan() = runTest {
        NoNetworkGuard.during {
            val rows = mutableListOf<MyRow>()
            val provider = MyScriptedProvider(
                listOf(planAnswer(stepOf("first", "One"), stepOf("second", "Two"), stepOf("third", "Three"))),
            )
            val pipeline = buildPipeline(listOf(planTier(rows)), provider, NO_KEYS, HoldSecondGate())

            val outcome = pipeline.execute(CommandInput("make three items", "en"))

            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            assertEquals(1, completed.commits.size)
            assertEquals(1, completed.held.size)
            assertEquals(listOf("third"), completed.remainingStepIds)
            assertEquals(listOf("One"), rows.map { it.title })
            assertEquals(
                "Did 1 action(s), couldn't finish (1 waiting, 1 not started)",
                planSummary(outcome),
            )
        }
    }

    @Test
    fun aPlanThatNeedsALookupHandsOnWithNothingRun() = runTest {
        NoNetworkGuard.during {
            val rows = mutableListOf<MyRow>()
            val needsLookup = toolCallAnswer(
                "submit_plan",
                buildJsonObject {
                    putJsonArray("steps") { }
                    put("needs_lookup", true)
                },
            )
            val pipeline = buildPipeline(
                listOf(planTier(rows)),
                MyScriptedProvider(listOf(needsLookup)),
                NO_KEYS,
                MyAdmitAll,
            )

            val outcome = pipeline.execute(CommandInput("add the thing from last week", "en"))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Unhandled)
            assertTrue(rows.isEmpty())
            assertEquals("Not handled", planSummary(outcome))
        }
    }

    @Test
    fun aScriptedRouterAnswerStartsTheWalkAtTheNamedTier() = runTest {
        NoNetworkGuard.during {
            val items = mutableListOf<String>()
            val provider = MyScriptedProvider(
                listOf(
                    toolCallAnswer("pick_start_tier", buildJsonObject { put("tier", "agentic") }),
                    textAnswer("You have nothing about paper."),
                ),
            )
            val pipeline = routedLadder(provider, NO_KEYS, MyAdmitAll, items)

            val outcome = pipeline.execute(CommandInput("what do I have about paper", "en"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals("You have nothing about paper.", completed.reply)
            val selection = completed.trace.selection!!
            assertEquals("picked", selection.outcome)
            assertEquals(StrategyId("agentic"), selection.picked)
            assertEquals(listOf(StrategyId("single_shot"), StrategyId("agentic")), selection.eligible)
            assertEquals(1, selection.tiersBypassed)
            assertEquals("picked: agentic, skipped 1", pickSummary(completed))
            val ran = completed.trace.attempts.map { it.strategy.value }
            assertFalse(ran.toString(), "single_shot" in ran)
            assertEquals("agentic", ran.last())
            // The router asked for the small model; the tier that ran asked for its own.
            assertEquals(listOf("my-small-model", "my-model"), provider.requests.map { it.model })
        }
    }

    @Test
    fun theUndoWiringRegionCountsOneCommandAndUndoAllRestoresTheStore() = runTest {
        NoNetworkGuard.during {
            val store = MyItemStore()
            val seed = store.add("buy paper")
            val before = store.titles()
            val journal = myJournal(store)
            val recording = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier"), { _, session ->
                        session.submit(addItemStep(journal, store, "buy pens"))
                        session.submit(renameItemStep(journal, store, seed.id, "buy more paper"))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                undoWiring(journal, recording)
                runIds = { "run-1" }
            }

            pipeline.execute(CommandInput("add pens and rename the paper"))

            assertEquals(listOf("buy more paper", "buy pens"), store.titles())
            assertEquals("Undo all (2)", undoLabel(journal, "run-1"))
            assertNull(undoLabel(journal, "no-such-run"))
            assertEquals("Undone (2)", undoAllStatus(journal, "run-1"))
            assertEquals(before, store.titles())
            assertEquals("Nothing to undo", undoAllStatus(journal, "run-1"))
            assertEquals(2, recording.actions.size)
        }
    }

    @Test
    fun theUndoWiringRegionRefusesWhenTheItemChangedSinceTheCommand() = runTest {
        NoNetworkGuard.during {
            val store = MyItemStore()
            val seed = store.add("buy paper")
            val journal = myJournal(store)
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier"), { _, session ->
                        session.submit(renameItemStep(journal, store, seed.id, "buy more paper"))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                undoWiring(journal, RecordingCommitSink())
                runIds = { "run-1" }
            }
            pipeline.execute(CommandInput("rename the paper"))
            store.rename(seed.id, "edited elsewhere")

            assertEquals("Not undone: something changed since", undoAllStatus(journal, "run-1"))
            assertEquals(listOf("edited elsewhere"), store.titles())
        }
    }

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theKeystoreFakeRoundTripsAKeyOnTheJvm() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = folder.newFile("fake.preferences_pb").also { it.delete() }
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val store = testKeyStore(dataStore, MyKeySlots)

            assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))

            store.save(ProviderId.ANTHROPIC, "doc-test-key-wxyz") // secret-scan: allow (fixed non-secret test string)

            assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.ANTHROPIC))
            assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theKeystoreRegionDescribesEveryCause() {
        assertEquals(3, MyKeySlots.size)
        assertEquals(3, MyKeySlots.map { it.alias }.toSet().size)
        for (cause in listOf("key_missing", "decrypt_failed", "stored_value_malformed")) {
            assertTrue(keyAdvice(cause), keyAdvice(cause).endsWith("re-enter key"))
        }
        for (cause in listOf("keystore_unavailable", "storage_unreadable")) {
            assertTrue(keyAdvice(cause), keyAdvice(cause).endsWith("transient, retry"))
        }
        assertTrue(keyAdvice("a_future_cause").endsWith("re-enter key"))
        assertEquals(
            "Key unreadable (storage_unreadable): transient, retry",
            failureText(FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "storage_unreadable"), 0),
        )
    }

    @Test
    fun theFixedCredentialsRegionAnswersPresentOrMissing() = runTest {
        val source = fixedCredentials("test-key")
        val present = source.credential(ProviderId.ANTHROPIC)
        assertTrue(present.toString(), present is CredentialLookup.Present)
        assertEquals(ProviderId.ANTHROPIC, (present as CredentialLookup.Present).credential.provider)
        assertEquals(CredentialLookup.Missing(), source.credential(ProviderId.OPENAI))
    }

    @Test
    fun undoBridgeRegionJournalsAndUndoesACommand() = runTest {
        NoNetworkGuard.during {
            val store = ItemStore().also { it.seed("a", "v0") }
            val seeded = store.snapshot()
            val journal = UndoJournal { adapter(ItemAdapter(store)) }
            val recording = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier"), { _, session ->
                        session.submit(ToolStep.Mutation(CreateItem(store, journal.newTicket(), "n1", null)))
                        session.submit(ToolStep.Mutation(RenameItem(store, journal.newTicket(), "a", "v1")))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = compositeSink(UndoCommitSink(journal), recording)
                runIds = { "run-1" }
            }

            pipeline.execute(CommandInput("create and rename"))

            assertEquals(2, journal.group("run-1")?.count)
            val result = journal.undoAll("run-1")
            assertTrue(result.toString(), result is UndoResult.Complete)
            assertEquals(2, (result as UndoResult.Complete).restored.size)
            assertEquals(seeded, store.snapshot())
        }
    }
}
