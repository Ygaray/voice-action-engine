package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.providers.chat.BEARER_IN_TEXT
import io.github.ygaray.voiceactionengine.providers.chat.GOLDEN_TAIL
import io.github.ygaray.voiceactionengine.providers.chat.KEY_IN_TEXT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val CONVERSATIONS_DIR = "golden/conversations/"
private const val MANIFEST_RESOURCE = "/" + CONVERSATIONS_DIR + "MANIFEST.tsv"
private const val COLUMN_COUNT = 7
private const val NO_VALUE = "-"
private const val DERIVED = "derived"
private const val CAPTURED = "captured"
private const val KEY_TURNS = "turns"
private const val KEY_MESSAGES = "messages"
private const val KEY_RESPONSE = "response"

private val MANIFEST_HEADER = listOf("case", "dialect", "provenance", "model", "file", "tags", "note")
private val CASE_NAME = Regex("[a-z][a-z0-9_]*")
private val PROVENANCES = setOf(DERIVED, CAPTURED)

/** The wire dialects a conversation golden can be written in. */
internal val DIALECTS: Set<String> = setOf("anthropic", "openai", "openrouter")

/** The closed set of scenario tags a manifest row may carry. */
internal val KNOWN_TAGS: Set<String> = setOf(
    "parallel",
    "zero_arg",
    "error_result",
    "interleaved_text",
    "thinking",
    "redacted_thinking",
    "reasoning_details",
    "empty_args_forms",
    "long_system",
)

/**
 * Keys whose values are provider-signed or provider-opaque reasoning. They are never rewritten and never searched for
 * ids; they are still searched for key-shaped and Bearer text.
 */
internal val EXEMPT_KEYS: Set<String> = setOf("thinking", "signature", "data", "reasoning", "reasoning_details")

/** An id prefix (call_, toolu_, msg_, chatcmpl-, gen-, req_) not glued to a longer word; group 2 is the id tail. */
internal val CONVERSATION_ID_IN_TEXT =
    Regex("(?<![A-Za-z0-9_])(call_|toolu_|msg_|chatcmpl-|gen-|req_)([A-Za-z0-9_-]*)")

/** One manifest row: a conversation golden and what it shows. */
internal class ConversationRow(
    val case: String,
    val dialect: String,
    val provenance: String,
    val model: String,
    val file: String,
    val tags: Set<String>,
    val note: String,
) {
    override fun toString(): String = "ConversationRow($case)"
}

/** One request/response exchange: the messages array as sent, and the response body that came back. */
internal class ConversationTurn(val messages: JsonArray, val response: JsonObject) {
    override fun toString(): String = "ConversationTurn(messages=${messages.size})"
}

/**
 * Parses the manifest text into rows. Blank lines and lines starting with # are skipped; the first other line must be
 * the exact header. A bad line fails with a fixed message and its 1-based line number, never the line's text.
 */
internal fun parseConversationManifest(text: String): List<ConversationRow> {
    val lines = text.lines().withIndex().filter { (_, line) -> line.isNotBlank() && !line.startsWith("#") }
    require(lines.isNotEmpty()) { "the manifest has no header line" }
    val header = lines.first()
    require(header.value.split("\t") == MANIFEST_HEADER) { "line ${header.index + 1}: the header is wrong" }
    val seen = mutableSetOf<String>()
    return lines.drop(1).map { (index, line) -> conversationRow(index + 1, line, seen) }
}

private fun conversationRow(line: Int, text: String, seen: MutableSet<String>): ConversationRow {
    val cols = text.split("\t")
    require(cols.size == COLUMN_COUNT) { "line $line: expected $COLUMN_COUNT columns" }
    val (case, dialect, provenance) = cols
    val model = cols[3]
    require(CASE_NAME.matches(case)) { "line $line: the case name is not lower snake case" }
    require(dialect in DIALECTS) { "line $line: unknown dialect" }
    require(provenance in PROVENANCES) { "line $line: provenance is neither derived nor captured" }
    require(case.startsWith("${provenance}_") && case.length > provenance.length + 1) {
        "line $line: the case name must start with its provenance"
    }
    require(model.isNotBlank()) { "line $line: the model is empty" }
    require(cols[4] == "$dialect/$case.json") { "line $line: the file must be <dialect>/<case>.json" }
    // A case name is unique within its dialect: the same scenario is written once per wire dialect.
    require(seen.add("$dialect/$case")) { "line $line: duplicate case" }
    return ConversationRow(case, dialect, provenance, model, cols[4], tagsOf(line, cols[5]), cols[6])
}

private fun tagsOf(line: Int, column: String): Set<String> {
    if (column == NO_VALUE) return emptySet()
    val tags = column.split(",")
    require(tags.all { it in KNOWN_TAGS }) { "line $line: unknown tag" }
    return tags.toSet()
}

/** The rows of the committed manifest. */
internal fun conversationRows(): List<ConversationRow> = parseConversationManifest(
    checkNotNull(ConversationRow::class.java.getResourceAsStream(MANIFEST_RESOURCE)) { "the manifest is missing" }
        .bufferedReader().use { it.readText() },
)

/** The text of a row's golden file; a missing file fails with the case name. */
internal fun conversationText(row: ConversationRow): String =
    checkNotNull(ConversationRow::class.java.getResourceAsStream("/" + CONVERSATIONS_DIR + row.file)) {
        "the golden file for ${row.case} is missing"
    }.bufferedReader().use { it.readText() }

/** Splits a golden into its turns; anything but `{"turns":[{"messages":[...],"response":{...}}, ...]}` is refused. */
internal fun parseConversation(text: String): List<ConversationTurn> {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (ignored: IllegalArgumentException) {
        throw IllegalArgumentException("the conversation is not JSON")
    }
    require(root is JsonObject && root.keys == setOf(KEY_TURNS)) { "the conversation root must be {\"turns\": [...]}" }
    val turns = root[KEY_TURNS] as? JsonArray
    require(turns != null && turns.isNotEmpty()) { "turns must be a non-empty array" }
    return turns.mapIndexed { index, turn -> conversationTurn(index, turn) }
}

private fun conversationTurn(index: Int, turn: JsonElement): ConversationTurn {
    require(turn is JsonObject && turn.keys == setOf(KEY_MESSAGES, KEY_RESPONSE)) {
        "turn $index must hold exactly messages and response"
    }
    val messages = turn[KEY_MESSAGES] as? JsonArray
    val response = turn[KEY_RESPONSE] as? JsonObject
    require(messages != null && messages.isNotEmpty()) { "turn $index: messages must be a non-empty array" }
    require(response != null) { "turn $index: response must be an object" }
    return ConversationTurn(messages, response)
}

/** The one printer: compact, order-preserving and idempotent, the same function the engine encodes requests with. */
internal fun canonicalJson(text: String): String =
    Json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))

/**
 * One fixed rule name per kind of violation found in a golden's text, empty when clean. Key-shaped and Bearer strings
 * are flagged in every string; an id that does not end in GOLDEN is flagged everywhere except under [EXEMPT_KEYS],
 * so reasoning text and signatures are never mistaken for ids. A message never repeats the offending text.
 */
internal fun conversationHygieneViolations(text: String): List<String> {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (ignored: IllegalArgumentException) {
        return listOf("not JSON")
    }
    val found = linkedSetOf<String>()
    scan(root, false, found)
    return found.toList()
}

private fun scan(element: JsonElement, exempt: Boolean, found: MutableSet<String>) {
    when (element) {
        is JsonObject -> element.forEach { (key, value) -> scan(value, exempt || key in EXEMPT_KEYS, found) }
        is JsonArray -> element.forEach { scan(it, exempt, found) }
        is JsonPrimitive -> if (element.isString) scanText(element.content, exempt, found)
    }
}

private fun scanText(text: String, exempt: Boolean, found: MutableSet<String>) {
    if (KEY_IN_TEXT.containsMatchIn(text)) found += "key-shaped string (sk- followed by 16 or more key characters)"
    if (BEARER_IN_TEXT.containsMatchIn(text)) found += "Bearer value"
    if (!exempt && CONVERSATION_ID_IN_TEXT.findAll(text).any { !GOLDEN_TAIL.matches(it.groupValues[2]) }) {
        found += "id that does not end in GOLDEN"
    }
}
