package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A tool executor whose answers a test scripts. It records every [Extraction] it is asked to prepare and logs
 * `prepare:<toolName>` to [log].
 *
 * @param log an optional shared log, to assert the order of prepare, gate, apply and sink events.
 * @param prepare produces the step for each call.
 */
public class ScriptedToolExecutor(
    private val log: RecordingSink<String>?,
    private val prepare: suspend (Extraction, CommandInput) -> ToolStep,
) : ToolExecutor {
    private val seen = CopyOnWriteArrayList<Extraction>()

    /** Every call the executor was asked to prepare, in order. */
    public val calls: List<Extraction>
        get() = seen.toList()

    /** How many times the executor was asked. */
    public val callCount: Int
        get() = seen.size

    override suspend fun prepare(call: Extraction, input: CommandInput): ToolStep {
        seen.add(call)
        log?.record("prepare:${call.toolName}")
        return prepare.invoke(call, input)
    }

    /** Ready-made executors. */
    public companion object {
        /**
         * An executor that answers with [steps] in order. When the script runs dry the call fails with an
         * [AssertionError], an Error the engine's guarded collapse does not turn into a result.
         */
        public fun sequence(log: RecordingSink<String>?, vararg steps: ToolStep): ScriptedToolExecutor {
            val script = ScriptedResponses(steps.toList())
            val total = steps.size
            return ScriptedToolExecutor(log) { _, _ ->
                if (script.remaining == 0) {
                    throw AssertionError("ScriptedToolExecutor: script exhausted after $total calls")
                }
                script.next()
            }
        }
    }
}
