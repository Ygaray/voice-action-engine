package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private const val STEPS_FIELD = "steps"
private const val ID_FIELD = "id"
private const val TOOL_FIELD = "tool"
private const val ARGUMENTS_FIELD = "arguments"
private const val NEEDS_LOOKUP_FIELD = "needs_lookup"

// The fixed vocabulary of rejection codes. A replan sends these back to the model, so they carry no model text.
private const val MALFORMED_CODE = "malformed"
private const val TOO_MANY_STEPS_CODE = "too_many_steps"
private const val EMPTY_CODE = "empty"
private const val UNKNOWN_TOOL_CODE = "unknown_tool"
private const val TERMINAL_TOOL_CODE = "terminal_tool"
private const val BAD_ID_CODE = "bad_id"
private const val DUPLICATE_ID_CODE = "duplicate_id"
private const val BAD_REFERENCE_CODE = "bad_reference"

/** One planned step: its id, the tool it calls and the arguments the model gave. */
internal class PlanStep(val id: String, val tool: String, val arguments: JsonObject) {
    /** Prints the tool name and the argument count only, never an id or a value. */
    override fun toString(): String = "PlanStep(tool=$tool, arguments=${arguments.size})"
}

/** A plan that passed validation: its steps, in the order they run. */
internal class ParsedPlan(val steps: List<PlanStep>) {
    /** Prints the step count only. */
    override fun toString(): String = "ParsedPlan(steps=${steps.size})"
}

/** What reading the model's answer produced: a plan to run, or the reason nothing may run. */
internal sealed class PlanVerdict {
    /** The plan is well formed, every step names a tool it may call and every reference names an earlier step. */
    class Valid(val plan: ParsedPlan) : PlanVerdict()

    /**
     * Nothing may run. [code] is a fixed token; [stepIndex] is the engine's 0-based index of the first offending step
     * (never the model's id), or null when the whole plan is at fault.
     */
    class Rejected(val code: String, val stepIndex: Int?) : PlanVerdict() {
        /** True when the plan named a tool the snapshot never offered. */
        val unknownTool: Boolean get() = code == UNKNOWN_TOOL_CODE
    }

    /** The command needs information the plan cannot have, so nothing may run and the next tier takes over. */
    object NeedsLookup : PlanVerdict()
}

/**
 * Reads the model's answer into a verdict. Pure and never throwing: the answer is untrusted, so only safe casts are
 * used and anything that does not match the shape is rejected. The whole plan is validated before any step runs, in
 * this order, and the first failing check decides the verdict:
 *
 * 1. the first call must be `submit_plan`, else `malformed` (a direct app-tool call is never dispatched; calls after
 *    the first are ignored);
 * 2. `needs_lookup` absent or a JSON boolean, else `malformed`; `true` gives [PlanVerdict.NeedsLookup] whatever
 *    `steps` holds; `steps` must be a JSON array, else `malformed`;
 * 3. more than [maxSteps] steps gives `too_many_steps`;
 * 4. every step must be an object with a string `id`, a string `tool` and an object `arguments`, else `malformed`
 *    with the step's index;
 * 5. a step naming a read tool (offered, neither terminal nor mutating) gives [PlanVerdict.NeedsLookup];
 * 6. no steps gives `empty`;
 * 7. per step, in plan order: `unknown_tool`, `terminal_tool`, `bad_id`, `duplicate_id`, then `bad_reference` (a
 *    reference to a step not declared earlier, including the step itself).
 */
internal fun parsePlan(calls: List<AssistantPart.ToolCall>, snapshot: ToolingSnapshot, maxSteps: Int): PlanVerdict =
    calls.firstOrNull()
        ?.takeIf { it.name == planToolName() }
        ?.let { readPlan(it.arguments, snapshot, maxSteps) }
        ?: PlanVerdict.Rejected(MALFORMED_CODE, null)

private fun readPlan(arguments: JsonObject, snapshot: ToolingSnapshot, maxSteps: Int): PlanVerdict {
    val flag = arguments[NEEDS_LOOKUP_FIELD]
    val lookup = (flag as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
    val steps = arguments[STEPS_FIELD] as? JsonArray
    return when {
        flag != null && lookup == null -> PlanVerdict.Rejected(MALFORMED_CODE, null)
        lookup == true -> PlanVerdict.NeedsLookup
        steps == null -> PlanVerdict.Rejected(MALFORMED_CODE, null)
        steps.size > maxSteps -> PlanVerdict.Rejected(TOO_MANY_STEPS_CODE, null)
        else -> readSteps(steps.map { shapeOf(it) }, snapshot)
    }
}

private fun readSteps(shaped: List<PlanStep?>, snapshot: ToolingSnapshot): PlanVerdict {
    val steps = shaped.filterNotNull()
    return when {
        steps.size != shaped.size -> PlanVerdict.Rejected(MALFORMED_CODE, shaped.indexOfFirst { it == null })
        steps.any { isReadTool(snapshot, it.tool) } -> PlanVerdict.NeedsLookup
        steps.isEmpty() -> PlanVerdict.Rejected(EMPTY_CODE, null)
        else -> firstProblem(steps, snapshot) ?: PlanVerdict.Valid(ParsedPlan(steps))
    }
}

private fun shapeOf(element: JsonElement): PlanStep? {
    val step = element as? JsonObject
    val id = textOf(step?.get(ID_FIELD))
    val tool = textOf(step?.get(TOOL_FIELD))
    val arguments = step?.get(ARGUMENTS_FIELD) as? JsonObject
    return id?.let { i -> tool?.let { t -> arguments?.let { a -> PlanStep(i, t, a) } } }
}

private fun firstProblem(steps: List<PlanStep>, snapshot: ToolingSnapshot): PlanVerdict.Rejected? {
    val declared = HashSet<String>()
    for ((index, step) in steps.withIndex()) {
        val code = problemOf(step, snapshot, declared)
        if (code != null) return PlanVerdict.Rejected(code, index)
        declared.add(step.id)
    }
    return null
}

// A step may only call a tool the snapshot offered that does not end the run, under a fresh valid id, and may only
// refer to the results of steps declared before it.
private fun problemOf(step: PlanStep, snapshot: ToolingSnapshot, declared: Set<String>): String? =
    when {
        snapshot.tools.none { it.name == step.tool } -> UNKNOWN_TOOL_CODE
        snapshot.tools.any { it.name == step.tool && it.terminal } -> TERMINAL_TOOL_CODE
        !isStepId(step.id) -> BAD_ID_CODE
        step.id in declared -> DUPLICATE_ID_CODE
        referencedStepIds(step.arguments).any { it !in declared } -> BAD_REFERENCE_CODE
        else -> null
    }

private fun isReadTool(snapshot: ToolingSnapshot, name: String): Boolean =
    snapshot.tools.any { it.name == name && !it.mutating && !it.terminal }

private fun textOf(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf { it.isString }?.content
