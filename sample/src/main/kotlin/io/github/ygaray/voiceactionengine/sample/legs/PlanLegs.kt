package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.CommandTrace
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.sample.undo.ITEM_TOOL_CREATE
import io.github.ygaray.voiceactionengine.sample.undo.Item
import io.github.ygaray.voiceactionengine.sample.undo.ItemAdapter
import io.github.ygaray.voiceactionengine.sample.undo.ItemResolver
import io.github.ygaray.voiceactionengine.sample.undo.ItemStore
import io.github.ygaray.voiceactionengine.sample.undo.ItemToolExecutor
import io.github.ygaray.voiceactionengine.sample.undo.ItemTools
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import io.github.ygaray.voiceactionengine.undo.UndoJournal

private const val EXPECTED_COMMITS = 2
private const val ROUTER_ELIGIBLE = 2
private const val PLAN_TIER_INDEX = 1
private const val FIRST_TIER_INDEX = 0
private const val SELECTION_PICKED = "picked"
private const val SELECTION_FALLBACK = "router_fallback"

/** The id of the router leg's single-shot tier. */
internal val SINGLE_TIER = StrategyId("single")

/** The id of the plan tier, in both live legs. */
internal val PLAN_TIER = StrategyId("plan")

/** The id of the router leg's grammar head. */
internal val GRAMMAR_TIER = StrategyId("grammar")

// Sent to the router's provider. Synthetic words only; never an id, a title or a secret.
private const val SINGLE_DESCRIPTION = "One simple change to one item, with nothing that depends on an earlier change."
private const val PLAN_DESCRIPTION =
    "Several changes in a row, where a later change needs the new id that an earlier change makes."

/** Why a plan leg failed: the stable reason codes of the plan verdict. */
internal const val REASON_NOT_COMPLETED = "not_completed"
internal const val REASON_PARTIAL = "partial"
internal const val REASON_REMAINING_STEPS = "remaining_steps"
internal const val REASON_NOT_BOUND = "not_bound"

/** Why a router leg did not pass: the stable reason codes of the router verdict. */
internal const val REASON_NO_SELECTION = "no_selection"
internal const val REASON_ROUTER_FALLBACK = "router_fallback"
internal const val REASON_PICKED_FIRST = "picked_first"
internal const val REASON_SELECTION_INCOMPLETE = "selection_incomplete"
internal const val REASON_WRONG_ELIGIBLE = "wrong_eligible_count"
internal const val REASON_WALK_NOT_AT_PICK = "walk_not_at_pick"
internal const val REASON_SELECTION_TURNS = "selection_turns"
internal const val REASON_ROUTER_TOKENS = "router_tokens"

/**
 * One run's world for the stateful legs: a fresh [ItemStore], the journal that issues tickets over it and the executor
 * that writes to it. A fresh one per run keeps one run's items out of the next run's verdict.
 */
internal class ItemWorld(
    val store: ItemStore = ItemStore(),
    val journal: UndoJournal = UndoJournal { adapter(ItemAdapter(store)) },
) {
    /** The executor over [store] and [journal]. */
    val executor: ItemToolExecutor = ItemToolExecutor(store, journal)

    /** Counts only. */
    override fun toString(): String = "ItemWorld(items=${store.snapshot().size})"
}

/** A verdict with the measured numbers its `VAE_VERDICT` line carries. */
internal class LegJudgement(val verdict: Verdict, val extras: Map<String, Long>) {
    override fun toString(): String = "LegJudgement(verdict=$verdict)"
}

/**
 * The two stateful live legs of VER-06: `plan_live` (a PlanThenExecute command whose second step uses the first step's
 * new id) and `router_live` (a router-chosen start tier, visible in the trace, with its tokens counted). The tiers use an
 * [ItemWorld], never the canned executor, so a binding or a pick cannot pass vacuously.
 */
internal object PlanLegs {
    /**
     * The plan tier over [world]: the model plans, every step is prepared by the stateful executor. A tier may use only
     * the providers it declares, so the offline undo leg passes the capabilities that name its scripted provider.
     */
    fun planTier(world: ItemWorld, allowed: StrategyCapabilities? = null): CommandStrategy =
        PlanThenExecuteStrategy(PLAN_TIER) {
            tooling = ToolSpecProvider.fixed(ItemTools.snapshot(null))
            executor = world.executor
            if (allowed != null) capabilities = allowed
        }

    /** The single-shot tier of the router ladder: one forced `create_item` call, resolved by the same executor. */
    fun singleTier(world: ItemWorld): CommandStrategy = SingleShotStrategy(SINGLE_TIER) {
        tooling = ToolSpecProvider.fixed(ItemTools.snapshot(ITEM_TOOL_CREATE))
        resolver = ItemResolver(world.executor)
        forceTool = true
    }

    /** The router ladder's grammar head: the sample pack, which the router transcripts do not match. */
    fun grammarTier(world: ItemWorld): CommandStrategy = LocalGrammarStrategy(GRAMMAR_TIER) {
        pack = GrammarLeg.pack()
        resolver = ItemResolver(world.executor)
    }

    /**
     * The router ladder: the grammar head, then (unless [withSingle] is false) the single-shot tier, then the plan tier.
     * Without the single-shot tier only one model tier is eligible, so the router makes no call and records no pick.
     */
    fun routerLadder(world: ItemWorld, withSingle: Boolean = true): List<CommandStrategy> =
        listOfNotNull(grammarTier(world), if (withSingle) singleTier(world) else null, planTier(world))

    /**
     * The engine's router. Its descriptions are sent to the provider, so they are synthetic words; the router's own id
     * stays the default, which the sample's single selection maps to the leg's model.
     */
    fun routerSelector(): TierSelector.Router = TierSelector.Router {
        tierDescriptions = mapOf(SINGLE_TIER to SINGLE_DESCRIPTION, PLAN_TIER to PLAN_DESCRIPTION)
    }

    /**
     * The `plan_live` verdict. PASS only when the command completed, not partially, with exactly two commits and no
     * remaining step, and the store shows the second item under the first one's new id. Anything else is FAIL with the
     * first of `not_bound` (an unresolved reference, or a second item not under the first), `not_completed`, `partial`,
     * `commit_count_<n>` and `remaining_steps`. An unresolved reference is named first because it is the more specific
     * cause of the partial completion it leaves behind.
     */
    fun judgePlan(outcome: CommandOutcome, world: ItemWorld): LegJudgement {
        val codes = outcome.trace.codes
        val completed = outcome as? CommandOutcome.Completed
        val items = world.store.snapshot().values.toList()
        val bound = items.size == EXPECTED_COMMITS && secondUnderFirst(items)
        val remaining = completed?.remainingStepIds?.size ?: 0
        val reason = when {
            TraceCode.PLAN_BINDING_UNRESOLVED in codes -> REASON_NOT_BOUND
            completed == null -> REASON_NOT_COMPLETED
            completed.partial -> REASON_PARTIAL
            outcome.commits.size != EXPECTED_COMMITS -> "commit_count_${outcome.commits.size}"
            remaining > 0 -> REASON_REMAINING_STEPS
            !bound -> REASON_NOT_BOUND
            else -> null
        }
        val extras = linkedMapOf(
            "committed" to outcome.commits.size.toLong(),
            "bound" to if (bound) 1L else 0L,
            "remaining" to remaining.toLong(),
            "replanned" to if (TraceCode.PLAN_REPLANNED in codes) 1L else 0L,
        )
        return LegJudgement(if (reason == null) Verdict.pass() else Verdict.fail(reason), extras)
    }

    private fun secondUnderFirst(items: List<Item>): Boolean = items[1].parentId == items[0].id

    /**
     * The index, among the model tiers the picker was offered, of the first tier that ran and was among them; null
     * without a pick or when no offered tier ran. The grammar head is not offered, so it never counts.
     */
    fun firstModelIndex(trace: CommandTrace): Int? {
        val selection = trace.selection ?: return null
        val first = trace.attempts.firstOrNull { it.strategy in selection.eligible } ?: return null
        return selection.eligible.indexOf(first.strategy)
    }

    /**
     * The `router_live` verdict, read from the trace's start-tier selection (indexes are positions among the eligible
     * model tiers). PASS only when the pick was usable, named the plan tier (index 1), the first model tier that ran was
     * the pick, the pick took exactly one model turn and its tokens are counted in the trace's usage. The order of
     * rules: no selection is FAIL `no_selection` (a one-model-tier ladder makes no router call), an unusable pick is FAIL
     * `router_fallback`, a pick at index 0 is INCONCLUSIVE `picked_first`.
     */
    fun judgeRouter(outcome: CommandOutcome): LegJudgement {
        val trace = outcome.trace
        val selection = trace.selection
        if (selection == null) return LegJudgement(Verdict.fail(REASON_NO_SELECTION), emptyMap())
        val pickedIndex = selection.picked?.let { selection.eligible.indexOf(it) }?.takeIf { it >= 0 }
        val routerTokens = selection.usage.total
        val extras = linkedMapOf("eligible" to selection.eligible.size.toLong())
        if (pickedIndex != null) extras["picked_index"] = pickedIndex.toLong()
        extras["sel_turns"] = selection.turns.size.toLong()
        extras["router_tokens"] = routerTokens
        val verdict = when {
            selection.outcome == SELECTION_FALLBACK -> Verdict.fail(REASON_ROUTER_FALLBACK)
            selection.outcome != SELECTION_PICKED || pickedIndex == null ->
                Verdict.fail(REASON_SELECTION_INCOMPLETE)
            pickedIndex == FIRST_TIER_INDEX -> Verdict(VerdictKind.INCONCLUSIVE, REASON_PICKED_FIRST)
            selection.eligible.size != ROUTER_ELIGIBLE || pickedIndex != PLAN_TIER_INDEX ->
                Verdict.fail(REASON_WRONG_ELIGIBLE)
            firstModelIndex(trace) != pickedIndex -> Verdict.fail(REASON_WALK_NOT_AT_PICK)
            selection.turns.size != 1 -> Verdict.fail(REASON_SELECTION_TURNS)
            routerTokens <= 0L || routerTokens > trace.usage.total -> Verdict.fail(REASON_ROUTER_TOKENS)
            else -> Verdict.pass()
        }
        return LegJudgement(verdict, extras)
    }
}
