package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val RESPONSES_DIR = "/golden/chat/responses/"
private const val DERIVED_PREFIX = "derived.json#"
private const val NO_VALUE = "-"
private const val EDIT_DERIVED_MARK = "edit_absent_optional"
private const val EDIT_CAPTURED_MARK = "forced_edit_captured"
private const val EDIT_TOOL = "edit_list_card"
private const val EDIT_TRANSCRIPT = "put milk on card c-7"
private const val EDIT_MAX_TOKENS = 512
private const val TEST_TIMEOUT_MILLIS = 60_000L
private const val COLUMN_VENDOR = 1
private const val COLUMN_SOURCE = 3
private const val COLUMN_ABSENT = 7
private const val COLUMN_COUNT = 9

/** One EDIT row of the response manifest: the answer the server gives and the paths that must stay absent. */
private class EditRow(val case: String, val body: String, val absentKeys: List<String>, val derived: Boolean) {
    override fun toString(): String = "EditRow($case)"
}

/** Reads the EDIT rows of one vendor from the response manifest; an unreadable or empty set throws, never skips. */
private object EditRows {

    fun forVendor(vendorName: String): List<EditRow> {
        val derived = Json.parseToJsonElement(text(RESPONSES_DIR + "derived.json")).jsonObject
        val lines = text(RESPONSES_DIR + "MANIFEST.tsv").lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val rows = lines.drop(1).map { it.split("\t") }.filter { cols ->
            check(cols.size == COLUMN_COUNT) { "row '${cols.first()}' has ${cols.size} columns" }
            cols[COLUMN_VENDOR] == vendorName && isEdit(cols.first())
        }.map { cols ->
            val source = cols[COLUMN_SOURCE]
            val isDerived = source.startsWith(DERIVED_PREFIX)
            val body = if (isDerived) {
                checkNotNull(derived[source.removePrefix(DERIVED_PREFIX)]) { "${cols[0]}: no derived body" }.toString()
            } else {
                text(RESPONSES_DIR + source)
            }
            val absent = if (cols[COLUMN_ABSENT] == NO_VALUE) emptyList() else cols[COLUMN_ABSENT].split(",")
            EditRow(cols[0], body, absent, isDerived)
        }
        check(rows.isNotEmpty()) { "no EDIT rows for $vendorName in the manifest" }
        check(rows.all { it.absentKeys.isNotEmpty() }) { "an EDIT row for $vendorName lists no absent_keys" }
        return rows
    }

    private fun isEdit(case: String): Boolean = EDIT_DERIVED_MARK in case || case.endsWith(EDIT_CAPTURED_MARK)

    private fun text(path: String): String =
        checkNotNull(EditRows::class.java.getResourceAsStream(path)) { "missing resource $path" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
}

/** A pending change that remembers the arguments the model sent for it. */
private class ArgumentsMutation(override val toolName: String, val arguments: JsonObject) : PendingMutation {
    override suspend fun apply(): StepResult = StepResult("edited", false, "tok", emptyMap())
}

/** What one forced EDIT call produced: the arguments the app's mutation received and the tool the server saw. */
private class EditRun(
    val outcome: CommandOutcome,
    val received: JsonObject,
    val sentTool: JsonObject,
    val commits: Int,
)

// Follows a dotted path (a number indexes an array) and says whether it leads to a value.
private fun resolves(root: JsonElement, path: String): Boolean {
    var node: JsonElement? = root
    for (segment in path.split(".")) {
        node = when (node) {
            is JsonObject -> node[segment]
            is JsonArray -> segment.toIntOrNull()?.let { node.getOrNull(it) }
            else -> null
        }
    }
    return node != null
}

/** The arguments the answer's first tool call carries, read straight from the body without the decoder. */
private fun modelSent(body: String): JsonObject {
    val call = Json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
        .jsonObject["tool_calls"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["arguments"]!!
    return if (call is JsonPrimitive) Json.parseToJsonElement(call.content).jsonObject else call.jsonObject
}

/** A forced EDIT-shaped call through the pipeline and the real Chat provider: omitted optionals reach the app absent. */
class ChatAbsentOptionalTest {

    private fun forcedRequest(tool: ToolSpec): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage(EDIT_TRANSCRIPT)),
        listOf(tool),
        ToolChoice.Required(tool.name),
        EDIT_MAX_TOKENS,
        CacheDirective(true),
    )

    private fun runEdit(vendor: ChatVendor, model: String, body: String, tool: ToolSpec): EditRun = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            server.start()
            val configure: ChatCompletionsProvider.Builder.() -> Unit = { baseUrl = server.url("/") }
            val chat: AiProvider = if (vendor == ChatVendor.OPENAI) {
                ChatCompletionsProvider.openAi(configure)
            } else {
                ChatCompletionsProvider.openRouter(configure)
            }
            val mutations = CopyOnWriteArrayList<ArgumentsMutation>()
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(forcedRequest(tool)) as ModelResult.Success
                val call = result.response.message.toolCalls.single()
                val mutation = ArgumentsMutation(call.name, call.arguments)
                mutations.add(mutation)
                session.submit(ToolStep.Mutation(mutation))
                StrategyOutcome.Completed(null)
            }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("edit"), step))
                provider(chat)
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
                credentials = ScriptedCredentialSource.keys(vendor.providerId to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput(EDIT_TRANSCRIPT, "en", null))

            val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            EditRun(
                outcome,
                mutations.single().arguments,
                sent["tools"]!!.jsonArray.single().jsonObject,
                sink.actions.size,
            )
        }
    }

    private fun assertEditContract(vendor: ChatVendor, model: String, row: EditRow) {
        val tool = editListCardTool()
        val run = runEdit(vendor, model, row.body, tool)

        assertTrue("${row.case}: ${run.outcome}", run.outcome is CommandOutcome.Completed)
        val function = run.sentTool["function"]!!.jsonObject
        assertNull("${row.case}: strict was sent", function["strict"])
        assertEquals(tool.inputSchema.toString(), function["parameters"].toString())
        assertEquals("${row.case}: the app received something the model did not send", modelSent(row.body), run.received)
        assertNotNull("${row.case}: card_id missing", run.received["card_id"])
        assertNotNull("${row.case}: items missing", run.received["items"])
        row.absentKeys.forEach { path ->
            assertFalse("${row.case}: $path is present", resolves(run.received, path))
        }
        if (row.derived) {
            assertEquals(setOf("card_id", "items"), run.received.keys)
            assertEquals(setOf("text"), run.received["items"]!!.jsonArray[0].jsonObject.keys)
        }
        assertEquals(1, run.commits)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anOmittedOptionalOnOpenAiArrivesAtTheMutationAbsentWithTheSchemaSentUntouched() {
        EditRows.forVendor("openai").forEach { assertEditContract(ChatVendor.OPENAI, "gpt-5.4-mini", it) }
    }
}
