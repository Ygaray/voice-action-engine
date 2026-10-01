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

private const val SMOKE_ITERATIONS = 2
private const val SMOKE_RESERVATION = 3

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

    private val specs: Map<LegId, LegSpec> = listOf(
        smoke(LegId.SMOKE_ANTHROPIC, ProviderId.ANTHROPIC, HAIKU),
        smoke(LegId.SMOKE_OPENAI, ProviderId.OPENAI, GPT_MINI),
        smoke(LegId.SMOKE_OPENROUTER, ProviderId.OPENROUTER, OPENROUTER_GPT_MINI),
    ).associateBy { it.id }

    /** The spec of [id]. */
    fun spec(id: LegId): LegSpec = checkNotNull(specs[id]) { "no leg spec for ${id.wire}" }

    /** The tool a leg's read check looks for; shared with the tests. */
    const val READ_TOOL: String = TOOL_FIND
}
