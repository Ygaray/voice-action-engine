package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.sample.tools.CANNED_READ
import io.github.ygaray.voiceactionengine.sample.tools.CANNED_UNKNOWN
import io.github.ygaray.voiceactionengine.sample.tools.CANNED_WRITE
import io.github.ygaray.voiceactionengine.sample.tools.CannedToolExecutor
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANARY_ARG_VALUE = "CANARY_ARG_VALUE"

/** The fake executor's contract (VER-01). */
class CannedToolExecutorTest {

    private val input = CommandInput("a command")

    private fun args(): JsonObject = buildJsonObject { put("query", CANARY_ARG_VALUE) }

    @Test
    fun aReadToolIsFinishedWithTheCannedRead() = runTest {
        val executor = CannedToolExecutor(SyntheticTools.all)

        val step = executor.prepare(Extraction("find_items", args()), input)

        assertTrue(step.toString(), step is ToolStep.Finished)
        step as ToolStep.Finished
        assertEquals("find_items", step.toolName)
        assertEquals(FinishedKind.READ, step.kind)
        assertEquals(CANNED_READ, step.result.contentForModel)
        assertFalse(step.result.isError)
        assertEquals(1, executor.readCalls)
        assertEquals(0, executor.writeApplies)
    }

    @Test
    fun aMutatingToolIsAMutationWithTheCannedWrite() = runTest {
        val executor = CannedToolExecutor(SyntheticTools.all)

        val step = executor.prepare(Extraction("edit_item", args()), input)

        assertTrue(step.toString(), step is ToolStep.Mutation)
        step as ToolStep.Mutation
        assertEquals(0, executor.writeApplies)
        val mutation = step.mutations.single()
        assertEquals("edit_item", mutation.toolName)
        val result = mutation.apply()
        assertEquals(CANNED_WRITE, result.contentForModel)
        assertFalse(result.isError)
        assertEquals(1, executor.writeApplies)
        assertEquals(0, executor.readCalls)
    }

    @Test
    fun anUnknownToolIsAnErrorStep() = runTest {
        val executor = CannedToolExecutor(SyntheticTools.all)

        val step = executor.prepare(Extraction("not_a_tool", args()), input)

        assertTrue(step.toString(), step is ToolStep.Finished)
        step as ToolStep.Finished
        assertEquals(FinishedKind.ERROR, step.kind)
        assertEquals(CANNED_UNKNOWN, step.result.contentForModel)
        assertTrue(step.result.isError)
    }

    @Test
    fun cannedResultsNeverEchoArguments() = runTest {
        val executor = CannedToolExecutor(SyntheticTools.all)
        val contents = mutableListOf<String>()
        for (name in listOf("find_items", "create_item", "not_a_tool")) {
            when (val step = executor.prepare(Extraction(name, args()), input)) {
                is ToolStep.Finished -> contents.add(step.result.contentForModel)
                is ToolStep.Mutation -> step.mutations.forEach { contents.add(it.apply().contentForModel) }
            }
        }
        assertEquals(3, contents.size)
        contents.forEach { assertFalse(it, it.contains(CANARY_ARG_VALUE)) }
        assertFalse(executor.toString().contains(CANARY_ARG_VALUE))
    }
}
