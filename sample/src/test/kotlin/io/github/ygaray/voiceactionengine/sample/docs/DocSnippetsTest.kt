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
import io.github.ygaray.voiceactionengine.undo.EntryRef
import io.github.ygaray.voiceactionengine.undo.UndoJournal
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

private val NO_KEYS = CredentialSource { CredentialLookup.Missing() }

private fun titleArgs(title: String): JsonObject = buildJsonObject { put("title", title) }

private fun clarificationArgs(): JsonObject = buildJsonObject {
    put("question", "Which list should I add it to?")
    putJsonArray("options") {
        add(buildJsonObject { put("id", "list-a"); put("label", "List A") })
        add(buildJsonObject { put("id", "list-b"); put("label", "List B") })
    }
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
