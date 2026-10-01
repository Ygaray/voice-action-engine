package io.github.ygaray.voiceactionengine.sample.tools

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.ToolExecutor
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import java.util.concurrent.atomic.AtomicInteger

/** What every read tool answers. Fixed text: the call's arguments are never echoed. */
internal const val CANNED_READ =
    """{"items":[{"id":"canned-1","title":"canned item one"},{"id":"canned-2","title":"canned item two"}],"count":2}"""

/** What every applied mutation answers. */
internal const val CANNED_WRITE = """{"ok":true,"id":"canned-write-1"}"""

/** What a call to a tool the sample does not know answers. */
internal const val CANNED_UNKNOWN = """{"status":"error","reason":"unknown_tool"}"""

/**
 * The fake [ToolExecutor] for Gate-1 (VER-01): it never touches app data. A read tool is finished with [CANNED_READ], a
 * mutating tool becomes a mutation whose apply answers [CANNED_WRITE], and an unknown tool is an error step.
 */
internal class CannedToolExecutor(specs: List<ToolSpec>) : ToolExecutor {
    private val byName: Map<String, ToolSpec> = specs.associateBy { it.name }
    private val reads = AtomicInteger()
    private val writes = AtomicInteger()

    /** How many read calls were prepared. */
    val readCalls: Int get() = reads.get()

    /** How many mutations were applied (admitted by the gate). */
    val writeApplies: Int get() = writes.get()

    override suspend fun prepare(call: Extraction, input: CommandInput): ToolStep {
        val spec = byName[call.toolName]
        return when {
            spec == null ->
                ToolStep.Finished(call.toolName, FinishedKind.ERROR, StepResult(CANNED_UNKNOWN, true))
            spec.mutating -> ToolStep.Mutation(CannedMutation(spec.name))
            else -> {
                reads.incrementAndGet()
                ToolStep.Finished(spec.name, FinishedKind.READ, StepResult(CANNED_READ))
            }
        }
    }

    private inner class CannedMutation(override val toolName: String) : PendingMutation {
        override suspend fun apply(): StepResult {
            writes.incrementAndGet()
            return StepResult(CANNED_WRITE)
        }
    }

    /** Counts only, never a tool name or argument. */
    override fun toString(): String = "CannedToolExecutor(tools=${byName.size}, reads=$readCalls, writes=$writeApplies)"
}
