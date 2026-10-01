package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MANIFEST_RESOURCE = "/golden/chat/responses/MANIFEST.tsv"
private const val DERIVED_RESOURCE = "/golden/chat/responses/derived.json"
private const val COLUMN_COUNT = 9
private const val SUCCESS_STATUS_MIN = 200
private const val SUCCESS_STATUS_MAX = 299
private const val NO_VALUE = "-"
private const val GOLDEN_MODEL = "golden-model"

private val MANIFEST_HEADER = listOf(
    "case", "vendor", "provenance", "source", "http_status", "tool_choice", "expected", "absent_keys", "note",
)

/** One manifest row: which golden body to replay, how, and the typed outcome it must produce. */
private class GoldenRow(
    val case: String,
    val vendor: ChatVendor,
    val body: String,
    val status: Int,
    val toolChoice: String,
    val expected: String,
) {
    override fun toString(): String = "GoldenRow($case)"
}

private fun resourceText(path: String): String =
    checkNotNull(ChatGoldenReplayTest::class.java.getResourceAsStream(path)) { "missing test resource $path" }
        .bufferedReader().use { it.readText() }

private fun vendorOf(name: String): ChatVendor = when (name) {
    "openai" -> ChatVendor.OPENAI
    "openrouter" -> ChatVendor.OPENROUTER
    else -> error("unknown vendor '$name'")
}

private fun loadRows(manifest: String, derived: JsonObject): List<GoldenRow> {
    val lines = manifest.lines().filter { it.isNotBlank() && !it.startsWith("#") }
    check(lines.first().split("\t") == MANIFEST_HEADER) { "manifest header is wrong" }
    return lines.drop(1).map { line ->
        val cols = line.split("\t")
        check(cols.size == COLUMN_COUNT) { "row has ${cols.size} columns: ${cols.first()}" }
        val source = cols[3]
        val body = checkNotNull(derived[source.removePrefix("derived.json#")]) { "no derived body for ${cols[0]}" }
        GoldenRow(cols[0], vendorOf(cols[1]), body.toString(), cols[4].toInt(), cols[5], cols[6])
    }
}

private fun outcomeOf(result: ModelResult): String =
    if (result is ModelResult.Success) {
        val names = result.response.message.toolCalls.joinToString(",") { it.name }.ifEmpty { NO_VALUE }
        "success:${result.response.stopReason.value}:$names"
    } else {
        "failure:${(result as ModelResult.Failure).reason.code}"
    }

private fun replay(row: GoldenRow): String =
    if (row.status in SUCCESS_STATUS_MIN..SUCCESS_STATUS_MAX) {
        outcomeOf(decodeChatResponse(row.body, null, GOLDEN_MODEL, row.vendor, row.toolChoice == "required").result)
    } else {
        "failure:" + parseChatError(row.status, null, row.body, row.vendor.requestIdInBody).reason().code
    }

class ChatGoldenReplayTest {

    private fun rows(): List<GoldenRow> =
        loadRows(resourceText(MANIFEST_RESOURCE), Json.parseToJsonElement(resourceText(DERIVED_RESOURCE)) as JsonObject)

    @Test
    fun everyManifestRowReplaysToItsTypedOutcome() {
        val rows = rows()
        assertTrue("manifest has no rows", rows.isNotEmpty())
        val mismatches = rows.mapNotNull { row ->
            val actual = replay(row)
            if (actual == row.expected) null else "${row.case}: expected ${row.expected} but got $actual"
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }
}
