package wire

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
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
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
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
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.EntryRef
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import io.github.ygaray.voiceactionengine.undo.UndoReason
import io.github.ygaray.voiceactionengine.undo.UndoResult
import io.github.ygaray.voiceactionengine.undo.UndoTicket
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap

/** One stored item. The version changes on every write and is what the undo adapter reports as the fingerprint. */
class WireItem(val id: String, val title: String, val parentId: String?, val version: Int)

/** Stands in for the app's database. */
class WireStore {
    private val items = LinkedHashMap<String, WireItem>()
    private var created = 0
    private var versions = 0

    fun get(id: String): WireItem? = items[id]

    fun titles(): List<String> = items.values.map { it.title }

    fun all(): List<WireItem> = items.values.toList()

    fun add(title: String, parentId: String? = null): WireItem =
        WireItem("item-${++created}", title, parentId, ++versions).also { items[it.id] = it }

    // One transaction in a real database: the check and the write together. A null snapshot deletes the item.
    fun restoreIf(id: String, expected: String?, snapshot: WireItem?): Boolean {
        if (items[id]?.version?.toString() != expected) return false
        if (snapshot == null) items.remove(id) else items[id] = snapshot
        return true
    }
}

class WireItemAdapter(private val store: WireStore) : EntityAdapter {
    override val entityType: String = "item"

    override suspend fun read(id: String): Any? = store.get(id)

    override suspend fun fingerprint(id: String): String? = store.get(id)?.version?.toString()

    override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean =
        store.restoreIf(id, expectedFingerprint, snapshot as WireItem?)
}

fun wireJournal(store: WireStore): UndoJournal = UndoJournal { adapter(WireItemAdapter(store)) }

/** Two domain-free tools: one mutating create (the plan passes the new id on as parent_id), one read, plus the ready-made clarification tool. */
object WireTools {
    val findItems = ToolSpec("find_items", "Finds items whose title matches a query.", schemaOf("query"))
    val createItem = ToolSpec(
        "create_item",
        "Creates an item with a title, optionally under the item named by parent_id. Returns the key id: the id of the new item.",
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

/** The ticket is the mutation's context: the bridge hands it to the journal when the action is recorded. */
class WireCreateItem(
    private val store: WireStore,
    private val ticket: UndoTicket,
    private val title: String,
    private val parentId: String?,
) : PendingMutation {
    override val toolName: String = "create_item"
    override val context: UndoTicket = ticket

    override suspend fun apply(): StepResult {
        val item = store.add(title, parentId)
        ticket.created("item", item.id)
        // The id returned here is what a later plan step's $<stepId>.id is replaced with.
        return StepResult("""{"status":"created"}""", false, null, mapOf("id" to item.id))
    }
}

/** One ticket per mutation: every change the engine applies has its own. */
fun createStep(journal: UndoJournal, store: WireStore, arguments: JsonObject): ToolStep? {
    val title = (arguments["title"] as? JsonPrimitive)?.contentOrNull
    val parentId = (arguments["parent_id"] as? JsonPrimitive)?.contentOrNull
    return if (title.isNullOrBlank()) null else ToolStep.Mutation(WireCreateItem(store, journal.newTicket(), title, parentId))
}

/** Resolver of the grammar and SingleShot tiers; it keeps only the language of each grammar match. */
class WireResolver(
    private val store: WireStore,
    private val journal: UndoJournal,
    private val locale: String,
) : OutcomeResolver {
    val spoken: MutableList<String> = mutableListOf()

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        spoken.add(extraction.matchedLanguage ?: locale)
        val step = if (extraction.toolName == "create_item") createStep(journal, store, extraction.arguments) else null
        return if (step != null) Resolution.Steps(listOf(step), "Added it.") else Resolution.NoMatch()
    }
}

/** The plan tier's executor: each planned step is prepared here, then gated and applied by the engine. */
class WirePlanExecutor(private val store: WireStore, private val journal: UndoJournal) : ToolExecutor {
    override suspend fun prepare(call: Extraction, input: CommandInput): ToolStep =
        (if (call.toolName == "create_item") createStep(journal, store, call.arguments) else null)
            ?: ToolStep.Finished(call.toolName, FinishedKind.ERROR, StepResult("""{"status":"error"}""", true))
}

/** The phrasings, per tool. {title} is a slot of up to four words, [please] is optional and (add|put) is a choice. */
fun wireGrammar(): GrammarPack = GrammarPack {
    intent("create_item") {
        text("title", 4)
        en("[please] (add|put) {title} (to|on) my list")
        es("[por favor] (agrega|pon) {title} (a|en) mi lista")
    }
}

fun wireGrammarTier(grammarResolver: OutcomeResolver): CommandStrategy =
    LocalGrammarStrategy(StrategyId("grammar")) {
        pack = wireGrammar()
        resolver = grammarResolver
    }

fun wireSingleShotTier(shotResolver: OutcomeResolver): CommandStrategy =
    SingleShotStrategy(StrategyId("single_shot")) {
        tooling = ToolSpecProvider.fixed(WireTools.snapshot(forced = "create_item"))
        resolver = shotResolver
    }

fun wirePlanTier(store: WireStore, journal: UndoJournal): CommandStrategy =
    PlanThenExecuteStrategy(StrategyId("plan")) {
        tooling = ToolSpecProvider.fixed(
            ToolingSnapshot("You manage a plain list of items.", listOf(WireTools.createItem), null),
        )
        executor = WirePlanExecutor(store, journal)
    }

/** Sent to the router's provider: one line per tier, in the app's own words. */
fun wireRouter(): TierSelector.Router = TierSelector.Router {
    tierDescriptions = mapOf(
        StrategyId("single_shot") to "One simple change to one item.",
        StrategyId("plan") to "Several related changes, one after another.",
    )
}

/** The router asks for its model with its own id (start_tier_router unless set): map it like a tier id. */
fun wireRoutedSelection(aiProvider: AiProvider, router: TierSelector.Router): ProviderSelectionSource =
    ProviderSelectionSource { request ->
        val model = if (request.strategy == router.id) "my-small-model" else "my-model"
        ProviderSelection(aiProvider.id, model)
    }

/** Records the kind of every action the engine reports, in order. */
class WireCommitSink : CommitSink {
    val kinds: MutableList<ActionKind> = mutableListOf()

    override suspend fun onAction(event: ActionEvent) {
        kinds.add(event.action.kind)
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit
}

/**
 * Feeds the undo journal from the pipeline. List it FIRST in compositeSink(...), so the journal already holds an
 * action when the app's own sink reacts to it.
 */
class WireUndoSink(private val journal: UndoJournal) : CommitSink {
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
        val parent = (if (confirmed) parents[group] else event.parentRunId)?.let(::groupOf)
        try {
            val entry = EntryRef(event.runId, action.position, action.toolName)
            journal.record(group, parent, entry, action.kind == ActionKind.IS_ERROR, action.context as? UndoTicket)
        } catch (_: IllegalArgumentException) {
            // An action the journal cannot take must withhold "Undo all", never shrink it.
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

val WireAdmitAll = PreApplyGate { GateDecision.Admit() }

/** No key stored: enough for a scripted provider that does not require one. */
val WireNoKeys = CredentialSource { CredentialLookup.Missing() }

/** The ladder: grammar first (free, no provider call), then SingleShot, then the plan tier. */
fun wireTiers(store: WireStore, journal: UndoJournal, resolver: OutcomeResolver): List<CommandStrategy> =
    listOf(wireGrammarTier(resolver), wireSingleShotTier(resolver), wirePlanTier(store, journal))

fun wirePipeline(
    tiers: List<CommandStrategy>,
    journal: UndoJournal,
    aiProvider: AiProvider,
    credentialSource: CredentialSource = WireNoKeys,
    sink: CommitSink = WireCommitSink(),
    configure: PipelineBuilder.() -> Unit = {},
): CommandPipeline = commandPipeline {
    tiers.forEach { tier(it) }
    provider(aiProvider)
    providerSelection = ProviderSelectionSource { ProviderSelection(aiProvider.id, "my-model") }
    credentials = credentialSource
    gate = WireAdmitAll
    // The journal's sink goes FIRST, so it already holds an action when the app's own sink reacts to it.
    commitSink = compositeSink(WireUndoSink(journal), sink)
    configure()
}

/** The same ladder with the opt-in router choosing the start tier. */
fun wireRoutedPipeline(
    store: WireStore,
    journal: UndoJournal,
    aiProvider: AiProvider,
    resolver: OutcomeResolver,
): CommandPipeline {
    val router = wireRouter()
    return wirePipeline(wireTiers(store, journal, resolver), journal, aiProvider) {
        selector = router
        providerSelection = wireRoutedSelection(aiProvider, router)
    }
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

/** N is journal.group(key)?.count. A withheld group means the journal missed an action, so offer nothing. */
suspend fun undoLabel(journal: UndoJournal, groupKey: String): String? {
    val group = journal.group(groupKey) ?: return null
    return if (group.withheld || group.count == 0) null else "Undo all (${group.count})"
}

/** UndoResult is closed, so the outer when has no else. UndoReason is open, so the inner one keeps an else. */
suspend fun undoAllText(journal: UndoJournal, groupKey: String): String =
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
