package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val GOLDEN_ROOT = "/golden/chat"
private const val RESPONSES_DIR = "$GOLDEN_ROOT/responses/"
private const val MANIFEST_RESOURCE = RESPONSES_DIR + "MANIFEST.tsv"
private const val DERIVED_RESOURCE = RESPONSES_DIR + "derived.json"
private const val DERIVED_PREFIX = "derived.json#"
private const val CAPTURED_PREFIX = "captured/"
private const val COLUMN_COUNT = 9
private const val SUCCESS_STATUS_MIN = 200
private const val SUCCESS_STATUS_MAX = 299
private const val NO_VALUE = "-"
private const val TOOL_REQUIRED = "required"
private const val TOOL_AUTO = "auto"
private const val GOLDEN_MODEL = "golden-model"
private const val MIN_ROWS = 30
private const val MIN_SCANNED_FILES = 3
private const val DERIVED = "derived"
private const val CAPTURED = "captured"

private val REQUIRED_KINDS = listOf(
    "success:tool_use:",
    "success:refusal:",
    "success:max_tokens:",
    "failure:malformed_tool_args",
    "failure:model_unsupported",
)

private val MANIFEST_HEADER = listOf(
    "case", "vendor", "provenance", "source", "http_status", "tool_choice", "expected", "absent_keys", "note",
)

private val EXPECTED_GRAMMAR =
    Regex("success:[a-z][a-z0-9_]*:(-|[A-Za-z0-9_.-]+(,[A-Za-z0-9_.-]+)*)|failure:[a-z][a-z0-9_]*")
private val ABSENT_GRAMMAR = Regex("-|[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*(,[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*)*")

/** One manifest row: which golden body to replay, how, and the typed outcome it must produce. */
private class GoldenRow(
    val case: String,
    val vendor: ChatVendor,
    val body: String,
    val status: Int,
    val toolChoice: String,
    val expected: String,
    val absentKeys: List<String>,
) {
    override fun toString(): String = "GoldenRow($case)"
}

private fun resourceText(path: String): String? =
    ChatGoldenReplayTest::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

/** Reads the manifest text and checks that every row is complete and consistent before anything is replayed. */
private object GoldenManifest {

    /**
     * Parses [manifest] into rows. [readCaptured] returns the text of a captured file by its manifest source, or null
     * when the file does not exist. Anything unexpected throws with the case name, so no row can be skipped.
     */
    fun parse(manifest: String, derived: JsonObject, readCaptured: (String) -> String?): List<GoldenRow> {
        val lines = manifest.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        check(lines.isNotEmpty() && lines.first().split("\t") == MANIFEST_HEADER) { "manifest header is wrong" }
        val seen = mutableSetOf<String>()
        return lines.drop(1).map { line ->
            val cols = line.split("\t")
            check(cols.size == COLUMN_COUNT) { "row '${cols.first()}' has ${cols.size} columns, not $COLUMN_COUNT" }
            check(seen.add(cols[0])) { "duplicate case '${cols[0]}'" }
            row(cols, derived, readCaptured)
        }
    }

    private fun row(cols: List<String>, derived: JsonObject, readCaptured: (String) -> String?): GoldenRow {
        val case = cols[0]
        val status = checkNotNull(cols[4].toIntOrNull()) { "$case: http_status '${cols[4]}' is not a number" }
        checkColumns(case, cols, status)
        val body = bodyOf(case, cols[2], cols[3], derived, readCaptured)
        return GoldenRow(
            case = case,
            vendor = checkNotNull(vendors[cols[1]]) { "$case: unknown vendor '${cols[1]}'" },
            body = body,
            status = status,
            toolChoice = cols[5],
            expected = cols[6],
            absentKeys = if (cols[7] == NO_VALUE) emptyList() else cols[7].split(","),
        )
    }

    private fun checkColumns(case: String, cols: List<String>, status: Int) {
        val success = status in SUCCESS_STATUS_MIN..SUCCESS_STATUS_MAX
        check(!success || cols[5] == TOOL_REQUIRED || cols[5] == TOOL_AUTO) {
            "$case: tool_choice '${cols[5]}' must be required or auto on a 2xx row"
        }
        check(success || cols[5] in listOf(NO_VALUE, TOOL_REQUIRED, TOOL_AUTO)) { "$case: tool_choice '${cols[5]}'" }
        check(EXPECTED_GRAMMAR.matches(cols[6])) { "$case: expected '${cols[6]}' is outside the grammar" }
        check(ABSENT_GRAMMAR.matches(cols[7])) { "$case: absent_keys '${cols[7]}' is outside the grammar" }
        check(cols[8].isNotBlank()) { "$case: the note is empty" }
        check(cols[2] != DERIVED || cols[8].startsWith("derived:")) { "$case: a derived note starts with 'derived:'" }
    }

    private fun bodyOf(
        case: String,
        provenance: String,
        source: String,
        derived: JsonObject,
        readCaptured: (String) -> String?,
    ): String = when (provenance) {
        DERIVED -> {
            check(source.startsWith(DERIVED_PREFIX)) { "$case: a derived row's source must start with $DERIVED_PREFIX" }
            checkNotNull(derived[source.removePrefix(DERIVED_PREFIX)]) { "$case: no derived body '$source'" }.toString()
        }
        CAPTURED -> {
            check(source.startsWith(CAPTURED_PREFIX)) { "$case: a captured source must start with $CAPTURED_PREFIX" }
            checkNotNull(readCaptured(source)) { "$case: captured file '$source' is missing" }
        }
        else -> error("$case: unknown provenance '$provenance'")
    }

    private val vendors = mapOf("openai" to ChatVendor.OPENAI, "openrouter" to ChatVendor.OPENROUTER)
}

/** Runs a row through the production code and reads the typed outcome back as the manifest's `expected` text. */
private object GoldenReplay {

    fun outcome(row: GoldenRow): String =
        if (row.status in SUCCESS_STATUS_MIN..SUCCESS_STATUS_MAX) {
            format(decode(row).result)
        } else {
            "failure:" + parseChatError(row.status, null, row.body, row.vendor.requestIdInBody).reason().code
        }

    /** The absent_keys paths that the first decoded tool call's arguments nevertheless hold. */
    fun presentAbsentKeys(row: GoldenRow): List<String> {
        if (row.absentKeys.isEmpty()) return emptyList()
        val result = decode(row).result
        val arguments = (result as? ModelResult.Success)?.response?.message?.toolCalls?.firstOrNull()?.arguments
        return if (arguments == null) {
            row.absentKeys.map { "no decoded tool call to check $it" }
        } else {
            row.absentKeys.filter { resolves(arguments, it) }
        }
    }

    private fun decode(row: GoldenRow): ChatDecoded =
        decodeChatResponse(row.body, null, GOLDEN_MODEL, row.vendor, row.toolChoice == TOOL_REQUIRED)

    private fun format(result: ModelResult): String =
        if (result is ModelResult.Success) {
            val names = result.response.message.toolCalls.joinToString(",") { it.name }.ifEmpty { NO_VALUE }
            "success:${result.response.stopReason.value}:$names"
        } else {
            "failure:${(result as ModelResult.Failure).reason.code}"
        }

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
}

private val FINGERPRINT_VALUE = Regex("\"system_fingerprint\"\\s*:\\s*([^,}\\s]+)")
private val CREATED_VALUE = Regex("\"created\"\\s*:\\s*([^,}\\s]+)")
private val USER_ID_KEY = Regex("\"user_id\"")

/**
 * Checks one golden file's text against the sanitizer rules and returns one message per violated rule (empty when
 * clean): no key-shaped string, no Bearer value, every chatcmpl-, gen- and call_ id ends in GOLDEN with optional
 * digits, system_fingerprint is null or absent, created is 0, and there is no user_id key. A message names the rule and
 * never repeats the offending text.
 */
internal fun goldenHygieneViolations(text: String): List<String> = buildList {
    if (KEY_IN_TEXT.containsMatchIn(text)) add("key-shaped string (sk- followed by 16 or more key characters)")
    if (BEARER_IN_TEXT.containsMatchIn(text)) add("Bearer value")
    if (ID_IN_TEXT.findAll(text).any { !GOLDEN_TAIL.matches(it.groupValues[2]) }) {
        add("id that does not end in GOLDEN (chatcmpl-, gen- or call_)")
    }
    if (FINGERPRINT_VALUE.findAll(text).any { it.groupValues[1] != "null" }) add("system_fingerprint is not null")
    if (CREATED_VALUE.findAll(text).any { it.groupValues[1] != "0" }) add("created is not 0")
    if (USER_ID_KEY.containsMatchIn(text)) add("user_id key")
}

class ChatGoldenReplayTest {

    private fun derived(): JsonObject =
        Json.parseToJsonElement(checkNotNull(resourceText(DERIVED_RESOURCE))) as JsonObject

    private fun rows(): List<GoldenRow> = GoldenManifest.parse(
        checkNotNull(resourceText(MANIFEST_RESOURCE)),
        derived(),
    ) { source -> resourceText(RESPONSES_DIR + source) }

    private val header = MANIFEST_HEADER.joinToString("\t")

    private fun parse(vararg rowLines: String, captured: (String) -> String? = { null }): List<GoldenRow> =
        GoldenManifest.parse((listOf("# comment", header) + rowLines).joinToString("\n"), derived(), captured)

    private fun line(
        case: String = "openai_forced_log_food",
        vendor: String = "openai",
        provenance: String = DERIVED,
        source: String = "derived.json#openai_forced_log_food_stop",
        expected: String = "success:tool_use:log_food",
    ): String = listOf(case, vendor, provenance, source, "200", TOOL_REQUIRED, expected, NO_VALUE, "derived: n")
        .joinToString("\t")

    // ---- replay of the real manifest -------------------------------------------------------------------------

    @Test
    fun everyManifestRowReplaysToItsTypedOutcome() {
        val rows = rows()
        assertTrue("manifest has no rows", rows.isNotEmpty())
        val mismatches = rows.mapNotNull { row ->
            val actual = GoldenReplay.outcome(row)
            if (actual == row.expected) null else "${row.case}: expected ${row.expected} but got $actual"
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun omittedOptionalKeysStayAbsentInTheDecodedArguments() {
        val checked = rows().filter { it.absentKeys.isNotEmpty() }
        assertEquals("both vendors need an EDIT row with absent keys", 2, checked.map { it.vendor }.toSet().size)
        val present = checked.flatMap { row ->
            GoldenReplay.presentAbsentKeys(row).map { "${row.case}: $it is present" }
        }
        assertTrue(present.joinToString("\n"), present.isEmpty())
    }

    @Test
    fun theManifestCoversBothVendorsAndEveryKindOfOutcome() {
        val rows = rows()
        assertTrue("fewer than $MIN_ROWS rows: ${rows.size}", rows.size >= MIN_ROWS)
        assertEquals("both vendors are needed", 2, rows.map { it.vendor }.toSet().size)
        val expected = rows.map { it.expected }
        REQUIRED_KINDS.forEach { kind -> assertTrue("no row expects $kind", expected.any { it.startsWith(kind) }) }
    }

    // ---- the loader cannot silently skip --------------------------------------------------------------------

    @Test
    fun aGoodRowParsesAndACapturedRowWithItsFileParses() {
        assertEquals(1, parse(line()).size)
        val captured = line("cap", source = "captured/openai-c1.json", provenance = CAPTURED)
        assertEquals(1, parse(captured, captured = { "{}" }).size)
    }

    @Test
    fun aCapturedRowWhoseFileIsMissingFailsNamingTheCase() {
        val row = line("cap_missing", source = "captured/openai-c9.json", provenance = CAPTURED)
        val failure = assertThrows(IllegalStateException::class.java) { parse(row) }
        assertTrue(failure.message, failure.message!!.contains("cap_missing"))
    }

    @Test
    fun malformedRowsAreRejected() {
        val eightColumns = line().substringBeforeLast("\t")
        val rejected = mapOf(
            "8 columns" to eightColumns,
            "unknown provenance" to line(provenance = "recorded"),
            "unknown vendor" to line(vendor = "anthropic"),
            "derived row with a captured source" to line(source = "captured/openai-c1.json"),
            "captured row with a derived source" to line(provenance = CAPTURED),
            "expected outside the grammar" to line(expected = "success:tool_use"),
            "expected with a bad failure code" to line(expected = "failure:Bad Code"),
        )
        rejected.forEach { (label, row) ->
            assertThrows("$label must be rejected", IllegalStateException::class.java) {
                parse(row, captured = { "{}" })
            }
        }
    }

    @Test
    fun aDuplicateCaseIsRejected() {
        assertThrows(IllegalStateException::class.java) { parse(line(), line()) }
    }

    // ---- golden hygiene -------------------------------------------------------------------------------------

    @Test
    fun theHygieneScanFlagsEachUnsanitizedShape() {
        val key = "sk-" + "a".repeat(20)
        val unsafe = listOf(
            "token $key",
            "Authorization: Bearer abc123",
            """{"id":"chatcmpl-abc123"}""",
            """{"id":"gen-1234"}""",
            """{"id":"call_abc"}""",
            """{"system_fingerprint":"fp_44709d6fcb"}""",
            """{"created":1790000000}""",
            """{"user_id":"user_1"}""",
        )
        unsafe.forEach { sample ->
            assertTrue("not flagged: ${sample.take(24)}", goldenHygieneViolations(sample).isNotEmpty())
        }
    }

    @Test
    fun theHygieneScanPassesGoldenStyleSamples() {
        val clean = listOf(
            """{"id":"chatcmpl-GOLDEN","created":0,"system_fingerprint":null}""",
            """{"id": "gen-GOLDEN2", "created": 0}""",
            """{"tool_call_id":"call_GOLDEN1","text":"task-list-card-with-a-long-name"}""",
        )
        clean.forEach { sample -> assertEquals(sample, emptyList<String>(), goldenHygieneViolations(sample)) }
    }

    @Test
    fun everyGoldenFileOnTheClasspathIsClean() {
        val url = checkNotNull(ChatGoldenReplayTest::class.java.getResource(GOLDEN_ROOT)) { "no $GOLDEN_ROOT resource" }
        assertEquals("file", url.protocol)
        val files = File(url.toURI()).walkTopDown().filter { it.isFile }.toList()
        assertTrue("fewer than $MIN_SCANNED_FILES golden files: ${files.size}", files.size >= MIN_SCANNED_FILES)
        val dirty = files.flatMap { file -> goldenHygieneViolations(file.readText()).map { "${file.name}: $it" } }
        assertTrue(dirty.joinToString("\n"), dirty.isEmpty())
    }
}
