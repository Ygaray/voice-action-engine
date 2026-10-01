package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A terminal tool call ends the run as Completed with the typed call, carrying earlier commits and holds. */
class TerminalCallTest {
    private val first = StrategyId("tier-one")
    private val second = StrategyId("tier-two")

    private fun option(id: String, label: String): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
    }

    private fun clarificationCall(question: String, vararg options: JsonObject) = TerminalCall(
        CLARIFY,
        buildJsonObject {
            put("question", question)
            put("options", JsonArray(options.toList()))
        },
    )

    private fun ok(name: String) = FakeMutation(name, StepResult("saved"))

    @Test
    fun aTerminalCallAfterACommitCompletesWithTheCallAndEffectsAndNoLaterTier() = runTest {
        NoNetworkGuard.during {
            val call = clarificationCall("Which list?", option("L1", "Groceries"))
            val write = ok("write")
            val tierOne = ScriptedStrategy(first, { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed(null, call)
            })
            val tierTwo = ScriptedStrategy(second)
            val pipeline = commandPipeline {
                tier(tierOne)
                tier(tierTwo)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("add milk"))

            val completed = outcome as CommandOutcome.Completed
            assertNull(completed.reply)
            assertSame(call, completed.terminalCall)
            assertFalse(completed.partial)
            assertEquals(listOf("write"), completed.commits.map { it.toolName })
            assertTrue(completed.held.isEmpty())
            assertEquals(1, write.applyCount)
            assertEquals(0, tierTwo.executions)
            assertEquals("completed", completed.trace.attempts.single().outcome)
            assertEquals(first, completed.trace.attempts.single().strategy)
        }
    }

    @Test
    fun aTerminalCallAfterAHoldListsTheHeldProposalAndWritesNothing() = runTest {
        NoNetworkGuard.during {
            val call = clarificationCall("Which list?", option("L1", "Groceries"))
            val write = ok("write")
            val tierTwo = ScriptedStrategy(second)
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(first, { _, session ->
                        session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed(null, call)
                    }),
                )
                tier(tierTwo)
                gate = ScriptedGate.holdAll("needs confirm")
                commitSink = RecordingCommitSink()
            }

            val completed = pipeline.execute(CommandInput("add milk")) as CommandOutcome.Completed

            assertSame(call, completed.terminalCall)
            assertNull(completed.reply)
            assertEquals(listOf("write"), completed.held.single().mutations.map { it.toolName })
            assertTrue(completed.commits.isEmpty())
            assertEquals(0, write.applyCount)
            assertEquals(0, tierTwo.executions)
        }
    }

    @Test
    fun theClarificationSchemaAndTheTerminalCallReaderAgreeAndOpaqueIdsRoundTrip() = runTest {
        NoNetworkGuard.during {
            val properties = ToolSpec.clarification(CLARIFY).inputSchema["properties"]!!.jsonObject
            assertTrue(properties.containsKey("question"))
            assertTrue(properties.containsKey("options"))
            val opaque = "row:42/é?x=1&y=%20"
            val call = clarificationCall("Which one?", option(opaque, "Milk"), option("plain", "Eggs"))
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(first, { _, _ -> StrategyOutcome.Completed(null, call) }))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("add milk")) as CommandOutcome.Completed

            val clarification = outcome.terminalCall?.asClarification()
            assertNotNull(clarification)
            assertEquals("Which one?", clarification!!.question)
            assertEquals(listOf(opaque, "plain"), clarification.options.map { it.id })
            assertEquals(listOf("Milk", "Eggs"), clarification.options.map { it.label })
        }
    }

    @Test
    fun theOutcomeAndItsCallNeverPrintTheQuestionOrAnOptionLabel() = runTest {
        NoNetworkGuard.during {
            val question = "CANARY-QUESTION-TEXT"
            val label = "CANARY-OPTION-LABEL"
            val call = clarificationCall(question, option("CANARY-OPTION-ID", label))
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(first, { _, _ -> StrategyOutcome.Completed(null, call) }))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("add milk")) as CommandOutcome.Completed

            val printed = listOf(
                outcome.toString(),
                outcome.terminalCall.toString(),
                outcome.terminalCall!!.asClarification().toString(),
            )
            printed.forEach {
                assertFalse(it, it.contains(question))
                assertFalse(it, it.contains(label))
                assertFalse(it, it.contains("CANARY-OPTION-ID"))
            }
        }
    }

    private companion object {
        const val CLARIFY = "ask_clarification"
    }
}
