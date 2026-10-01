package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** One scripted attempt: it may submit steps through the session and must return an outcome. */
public typealias StrategyStep = suspend (CommandInput, CommandSession) -> StrategyOutcome

/**
 * A strategy that plays a script. Each [execute] runs the next step; when the script runs dry the call fails loudly.
 * It counts how often it ran and remembers the carry each run was given.
 *
 * @param id the tier's id.
 * @param steps one step per expected execution, in order.
 * @param capabilities the providers the tier declares; any provider unless a test says otherwise.
 */
public class ScriptedStrategy(
    override val id: StrategyId,
    steps: List<StrategyStep>,
    override val capabilities: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER,
) : CommandStrategy {
    /** A strategy playing [steps] in order. */
    public constructor(id: StrategyId, vararg steps: StrategyStep) : this(id, steps.toList())

    /** A strategy declaring [capabilities] and playing [steps] in order. */
    public constructor(id: StrategyId, capabilities: StrategyCapabilities, vararg steps: StrategyStep) :
        this(id, steps.toList(), capabilities)

    private val script = ScriptedResponses(steps)
    private val executionCount = AtomicInteger()
    private val carries = CopyOnWriteArrayList<Any?>()

    /** How many times [execute] has been called. */
    public val executions: Int
        get() = executionCount.get()

    /** The carry each execution's session held, in order; null for a tier that was given nothing. */
    public val receivedCarries: List<Any?>
        get() = carries.toList()

    override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome {
        executionCount.incrementAndGet()
        carries.add(session.carry)
        return script.next()(input, session)
    }
}
