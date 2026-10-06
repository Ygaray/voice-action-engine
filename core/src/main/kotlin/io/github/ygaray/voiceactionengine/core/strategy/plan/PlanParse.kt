package io.github.ygaray.voiceactionengine.core.strategy.plan

import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val STEPS_FIELD = "steps"
private const val ID_FIELD = "id"
private const val TOOL_FIELD = "tool"
private const val ARGUMENTS_FIELD = "arguments"
private const val MALFORMED_CODE = "malformed"

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
    /** The plan is well formed and every step names a tool it may call. */
    class Valid(val plan: ParsedPlan) : PlanVerdict()

    /** Nothing may run. [code] is a fixed token; [stepIndex] is the first offending step, or null. */
    class Rejected(val code: String, val stepIndex: Int?) : PlanVerdict()
}

/**
 * Reads the model's answer into a verdict. Pure and never throwing: the answer is untrusted, so only safe casts are
 * used and anything that does not match the shape is rejected.
 */
internal fun parsePlan(calls: List<AssistantPart.ToolCall>, snapshot: ToolingSnapshot, maxSteps: Int): PlanVerdict {
    val steps = calls.firstOrNull()
        ?.takeIf { it.name == planToolName() }
        ?.let { stepsOf(it.arguments, snapshot, maxSteps) }
    return if (steps == null) PlanVerdict.Rejected(MALFORMED_CODE, null) else PlanVerdict.Valid(ParsedPlan(steps))
}

private fun stepsOf(arguments: JsonObject, snapshot: ToolingSnapshot, maxSteps: Int): List<PlanStep>? =
    (arguments[STEPS_FIELD] as? JsonArray)
        ?.takeIf { it.size in 1..maxSteps }
        ?.map { stepOf(it, snapshot) }
        ?.takeIf { steps -> steps.none { it == null } }
        ?.filterNotNull()

private fun stepOf(element: JsonElement, snapshot: ToolingSnapshot): PlanStep? {
    val step = element as? JsonObject
    val tool = textOf(step?.get(TOOL_FIELD))?.takeIf { name -> isRunnable(snapshot, name) }
    val id = textOf(step?.get(ID_FIELD))
    val arguments = step?.get(ARGUMENTS_FIELD) as? JsonObject
    return tool?.let { t -> id?.let { i -> arguments?.let { a -> PlanStep(i, t, a) } } }
}

// A step may only call a tool the snapshot offered that changes state and does not end the run.
private fun isRunnable(snapshot: ToolingSnapshot, name: String): Boolean =
    snapshot.tools.any { it.name == name && it.mutating && !it.terminal }

private fun textOf(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf { it.isString }?.content
