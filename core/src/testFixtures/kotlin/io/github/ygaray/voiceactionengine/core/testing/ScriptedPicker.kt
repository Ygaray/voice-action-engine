package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.PickContext
import io.github.ygaray.voiceactionengine.core.pipeline.StartTierPicker
import java.util.concurrent.CopyOnWriteArrayList

/** One scripted pick: it may use the context's model and must return a tier id or null. */
public typealias PickerStep = suspend (CommandInput, List<StrategyId>, PickContext) -> StrategyId?

/**
 * A start-tier picker that plays a script. Each [pick] runs the next step; when the script runs dry the call fails
 * loudly with an assertion error, which the engine does not swallow, so an unplanned call fails the test. It records
 * how often it ran and what it was shown.
 *
 * @param steps one step per expected call, in order.
 */
public class ScriptedPicker(steps: List<PickerStep>) : StartTierPicker {
    /** A picker playing [steps] in order. */
    public constructor(vararg steps: PickerStep) : this(steps.toList())

    private val script = ScriptedResponses(steps)
    private val seenEligible = CopyOnWriteArrayList<List<StrategyId>>()
    private val seenInputs = CopyOnWriteArrayList<CommandInput>()

    /** How many times [pick] has been called. */
    public val calls: Int
        get() = seenInputs.size

    /** The eligible list each call was given, in order. */
    public val eligibleSeen: List<List<StrategyId>>
        get() = seenEligible.toList()

    /** The input each call was given, in order. */
    public val inputsSeen: List<CommandInput>
        get() = seenInputs.toList()

    override suspend fun pick(input: CommandInput, eligible: List<StrategyId>, ctx: PickContext): StrategyId? {
        seenInputs.add(input)
        seenEligible.add(eligible.toList())
        if (script.remaining == 0) throw AssertionError("picker script exhausted: an unplanned pick call")
        return script.next()(input, eligible, ctx)
    }

    override fun toString(): String = "ScriptedPicker(calls=$calls)"
}
