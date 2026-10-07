package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.tools.SYNTHETIC_SYSTEM
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools

private const val HAIKU = "claude-haiku-4-5"
private const val GPT_MINI = "gpt-5.4-mini"
private const val OPENROUTER_GPT_MINI = "openai/gpt-5.4-mini"

private const val TOOL_FIND = "find_items"
private const val TOOL_EDIT = "edit_item"
private const val TOOL_CREATE = "create_item"

// Answers only on the Responses endpoint, so Chat Completions rejects it (Phase 5 carry).
private const val RESPONSES_ONLY_MODEL = "gpt-6-astra"

private const val SMOKE_ITERATIONS = 2
private const val SMOKE_RESERVATION = 3
private const val VER02_ITERATIONS = 6
private const val VER02_RESERVATION = 6
private const val MULTI_ITERATIONS = 3
private const val MULTI_RESERVATION = 6
private const val PROBE_RESERVATION = 1
private const val DEMO_ITERATIONS = 3

// A plan leg asks for a plan at most twice; each ask is one logical call of at most PER_CALL_WORST_CASE (3) requests.
private const val PLAN_ITERATIONS = 4
private const val PLAN_RESERVATION = 6

// The router leg: the router's own call, the plan call and one replan, each at the worst case of 3 requests.
private const val ROUTER_RESERVATION = 9

/** What a leg runs: the strategy, the data behind it and how its verdict is decided. */
internal enum class LegKind {
    /** An agentic loop over the loaded LE-1 fixture (VER-02, the Anthropic cold run). */
    AGENTIC_FIXTURE,

    /** An agentic loop over the live synthetic tools (the extended multi-turn legs). */
    AGENTIC_SYNTHETIC,

    /** One forced single-shot call (the VER-03 smokes). */
    SINGLE_SHOT,

    /** One forced call to a Responses-only model, recorded and never judged. */
    RESPONSES_PROBE,

    /** The offline clarification demo (A19). */
    DEMO_CLARIFY,

    /** The offline partial-outcome demo. */
    DEMO_PARTIAL,

    /** The offline grammar leg: EN and ES grammar commands and a capped near-miss, with zero provider calls. */
    GRAMMAR_OFFLINE,

    /** A live PlanThenExecute command over a stateful store: the second step uses the first step's new id. */
    PLAN,

    /** A live router-chosen start tier: two eligible model tiers behind a grammar head, picked by the engine's router. */
    ROUTER,

    /** The offline undo-all leg: a multi-action command counted, then undone, refused after a later edit, and a partial. */
    UNDO_ALL,
}

/**
 * Everything that makes one leg what it is. All text is committed and synthetic.
 *
 * @property prompts the prompt variants, weakest first; a rerun of the same leg uses the next one.
 * @property forcedTool the tool a single-shot leg forces, or null.
 * @property readTool the tool a multi-turn leg must call on its first turn, or null.
 * @property requestedOptionals the optional argument keys the prompt asks for, so they do not count as model-filled.
 * @property maxIterations the policy's iteration limit for the leg.
 * @property reservation the most HTTP requests the leg may send; a leg starts only while the budget covers it.
 * @property optional whether the leg spends from the optional pool.
 */
internal class LegSpec(
    val id: LegId,
    val provider: ProviderId,
    val model: String,
    val kind: LegKind,
    val prompts: List<String>,
    val forcedTool: String?,
    val readTool: String?,
    val requestedOptionals: Set<String>,
    val maxIterations: Int,
    val reservation: Int,
    val optional: Boolean,
    val needsKey: Boolean,
    val needsFixture: Boolean,
) {
    override fun toString(): String = "LegSpec(id=${id.wire}, provider=$provider, model=$model, kind=$kind)"
}

/** The legs the Gate-1 tester presses, fixed here so the models and request counts are reviewable in one place. */
internal object LegCatalog {
    /** The tools a live model sees: no clarification tool, so a live leg cannot end on a question. */
    val liveTools: List<ToolSpec> = listOf(SyntheticTools.findItems, SyntheticTools.createItem, SyntheticTools.editItem)

    /** The live snapshot, naming [forcedTool] when a single-shot leg forces one. */
    fun liveSnapshot(forcedTool: String?): ToolingSnapshot = ToolingSnapshot(SYNTHETIC_SYSTEM, liveTools, forcedTool)

    private fun smoke(id: LegId, provider: ProviderId, model: String): LegSpec = LegSpec(
        id = id,
        provider = provider,
        model = model,
        kind = LegKind.SINGLE_SHOT,
        prompts = listOf(
            "In item c-42, change only the body to: buy more paper. Leave every other field unchanged.",
            "Edit item c-42: set the body to buy more paper. Send only the id and body fields; " +
                "do not include title or tags at all.",
        ),
        forcedTool = TOOL_EDIT,
        readTool = null,
        requestedOptionals = setOf("body"),
        maxIterations = SMOKE_ITERATIONS,
        reservation = SMOKE_RESERVATION,
        optional = false,
        needsKey = true,
        needsFixture = false,
    )

    // VER-02: the cold agentic run on the LE-1 fixture. The prompts are generic and never name a fixture tool.
    private val ver02 = LegSpec(
        id = LegId.VER02,
        provider = ProviderId.ANTHROPIC,
        model = HAIKU,
        kind = LegKind.AGENTIC_FIXTURE,
        prompts = listOf(
            "Before you answer, look up my saved items that mention the word paper, then tell me how many you found.",
            "Do not answer from memory. First use your search or list tools to look up my saved items that mention " +
                "paper; only after you have the result, tell me how many there are.",
        ),
        forcedTool = null,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = VER02_ITERATIONS,
        reservation = VER02_RESERVATION,
        optional = false,
        needsKey = true,
        needsFixture = true,
    )

    // The extended VER-03 multi-turn legs: the model reads, gets the tool result replayed, then answers.
    private fun multi(id: LegId, provider: ProviderId, model: String): LegSpec = LegSpec(
        id = id,
        provider = provider,
        model = model,
        kind = LegKind.AGENTIC_SYNTHETIC,
        prompts = listOf(
            "Look up the items that mention paper, then tell me how many there are.",
            "First call find_items with the query paper. After you get its result, answer with the number of items.",
        ),
        forcedTool = null,
        readTool = TOOL_FIND,
        requestedOptionals = emptySet(),
        maxIterations = MULTI_ITERATIONS,
        reservation = MULTI_RESERVATION,
        optional = false,
        needsKey = true,
        needsFixture = false,
    )

    // Optional probe: a Responses-only model through Chat Completions. Judged: PASS only on the typed model_unsupported after a real provider answer.
    private val responsesProbe = LegSpec(
        id = LegId.RESPONSES_PROBE,
        provider = ProviderId.OPENAI,
        model = RESPONSES_ONLY_MODEL,
        kind = LegKind.RESPONSES_PROBE,
        prompts = listOf("Create an item titled probe."),
        forcedTool = TOOL_CREATE,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = SMOKE_ITERATIONS,
        reservation = PROBE_RESERVATION,
        optional = true,
        needsKey = true,
        needsFixture = false,
    )

    // Offline demos: provider id demo, no key, no budget, no network.
    private fun demo(id: LegId, kind: LegKind, prompt: String, forcedTool: String?): LegSpec = LegSpec(
        id = id,
        provider = DEMO_PROVIDER,
        model = DEMO_MODEL,
        kind = kind,
        prompts = listOf(prompt),
        forcedTool = forcedTool,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = DEMO_ITERATIONS,
        reservation = 0,
        optional = false,
        needsKey = false,
        needsFixture = false,
    )

    // Offline grammar leg: no key, no budget, no network. It reports as the demo provider so the screen treats it as offline.
    private val grammarOffline = LegSpec(
        id = LegId.GRAMMAR_OFFLINE,
        provider = DEMO_PROVIDER,
        model = DEMO_MODEL,
        kind = LegKind.GRAMMAR_OFFLINE,
        prompts = listOf(GRAMMAR_EN_TRANSCRIPT, GRAMMAR_ES_TRANSCRIPT, GRAMMAR_NEAR_MISS_TRANSCRIPT),
        forcedTool = null,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = DEMO_ITERATIONS,
        reservation = 0,
        optional = false,
        needsKey = false,
        needsFixture = false,
    )

    // Live plan leg: the second step must use the first step's new id on a stateful store. Two calls at the worst case.
    private val planLive = LegSpec(
        id = LegId.PLAN_LIVE,
        provider = ProviderId.ANTHROPIC,
        model = HAIKU,
        kind = LegKind.PLAN,
        prompts = listOf(
            "Create an item called alpha, and then create an item called beta under it.",
            "First create an item called alpha. Then create an item called beta whose parent is the new alpha item: " +
                "pass the id that the first step returns as the parent_id of the second step, through the step reference.",
        ),
        forcedTool = null,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = PLAN_ITERATIONS,
        reservation = PLAN_RESERVATION,
        optional = false,
        needsKey = true,
        needsFixture = false,
    )

    // Live router leg: the transcript needs two dependent writes, so the plan tier (index 1 of the two model tiers) is the
    // only correct start; the single-shot tier cannot know the first item's new id.
    private val routerLive = LegSpec(
        id = LegId.ROUTER_LIVE,
        provider = ProviderId.ANTHROPIC,
        model = HAIKU,
        kind = LegKind.ROUTER,
        prompts = listOf(
            "Create an item called gamma, and then create an item called delta under it.",
            "First create an item called gamma. Then create an item called delta under the new gamma item. " +
                "The second change needs the id that the first change makes.",
        ),
        forcedTool = null,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = PLAN_ITERATIONS,
        reservation = ROUTER_RESERVATION,
        optional = false,
        needsKey = true,
        needsFixture = false,
    )

    // Offline undo-all leg: no key, no budget, no network. The scripted provider answers a fixed plan per sub-case.
    private val undoAll = LegSpec(
        id = LegId.UNDO_ALL,
        provider = DEMO_PROVIDER,
        model = DEMO_MODEL,
        kind = LegKind.UNDO_ALL,
        prompts = listOf(UNDO_TRANSCRIPT_WHOLE),
        forcedTool = null,
        readTool = null,
        requestedOptionals = emptySet(),
        maxIterations = PLAN_ITERATIONS,
        reservation = 0,
        optional = false,
        needsKey = false,
        needsFixture = false,
    )

    private val specs: Map<LegId, LegSpec> = listOf(
        ver02,
        smoke(LegId.SMOKE_ANTHROPIC, ProviderId.ANTHROPIC, HAIKU),
        smoke(LegId.SMOKE_OPENAI, ProviderId.OPENAI, GPT_MINI),
        smoke(LegId.SMOKE_OPENROUTER, ProviderId.OPENROUTER, OPENROUTER_GPT_MINI),
        multi(LegId.MULTI_OPENAI, ProviderId.OPENAI, GPT_MINI),
        multi(LegId.MULTI_OPENROUTER, ProviderId.OPENROUTER, OPENROUTER_GPT_MINI),
        responsesProbe,
        demo(LegId.DEMO_CLARIFY, LegKind.DEMO_CLARIFY, "add paper to my list", null),
        demo(LegId.DEMO_PARTIAL, LegKind.DEMO_PARTIAL, "add paper and pens", TOOL_CREATE),
        grammarOffline,
        planLive,
        routerLive,
        undoAll,
    ).associateBy { it.id }

    /** The spec of [id]. */
    fun spec(id: LegId): LegSpec = checkNotNull(specs[id]) { "no leg spec for ${id.wire}" }
}
