package io.github.ygaray.voiceactionengine.providers.conformance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val HEADER = listOf("case", "dialect", "provenance", "model", "file", "tags", "note").joinToString("\t")

private fun key(): String = "sk-" + "a1B2".repeat(5)

private fun row(
    case: String = "derived_parallel",
    dialect: String = "anthropic",
    provenance: String = "derived",
    model: String = "golden-model",
    file: String = "$dialect/$case.json",
    tags: String = "parallel,zero_arg",
    note: String = "a note",
): String = listOf(case, dialect, provenance, model, file, tags, note).joinToString("\t")

private fun manifest(vararg lines: String): String = (listOf("# a comment", "", HEADER) + lines).joinToString("\n")

private fun conversation(messages: JsonArray, response: JsonObject): JsonObject = buildJsonObject {
    putJsonArray("turns") {
        add(
            buildJsonObject {
                put("messages", messages)
                put("response", response)
            },
        )
    }
}

private fun userMessages(): JsonArray = buildJsonArray {
    add(
        buildJsonObject {
            put("role", "user")
            put("content", "hello")
        },
    )
}

private fun turnOf(messages: JsonElement?, response: JsonElement?): JsonObject = buildJsonObject {
    if (messages != null) put("messages", messages)
    if (response != null) put("response", response)
}

private fun hygiene(response: JsonObject): List<String> =
    conversationHygieneViolations(canonicalJson(conversation(userMessages(), response).toString()))

class ConversationGoldenTest {

    @Test
    fun aWellFormedRowParsesAndTagsSplitOnCommas() {
        val rows = parseConversationManifest(manifest(row()))
        assertEquals(1, rows.size)
        val parsed = rows.single()
        assertEquals("derived_parallel", parsed.case)
        assertEquals("anthropic", parsed.dialect)
        assertEquals("derived", parsed.provenance)
        assertEquals("golden-model", parsed.model)
        assertEquals("anthropic/derived_parallel.json", parsed.file)
        assertEquals(setOf("parallel", "zero_arg"), parsed.tags)
        assertEquals("a note", parsed.note)
        assertEquals("ConversationRow(derived_parallel)", parsed.toString())
    }

    @Test
    fun aDashMeansNoTagsAndEveryDialectAndProvenanceIsAccepted() {
        val rows = parseConversationManifest(
            manifest(
                row(case = "derived_a", tags = "-"),
                row(case = "captured_b", provenance = "captured", dialect = "openai"),
                row(case = "derived_c", dialect = "openrouter"),
            ),
        )
        assertEquals(emptySet<String>(), rows[0].tags)
        assertEquals(listOf("anthropic", "openai", "openrouter"), rows.map { it.dialect })
    }

    @Test
    fun theSameCaseNameIsAllowedOncePerDialect() {
        val rows = parseConversationManifest(
            manifest(row(), row(dialect = "openai"), row(dialect = "openrouter")),
        )
        assertEquals(listOf("anthropic", "openai", "openrouter"), rows.map { it.dialect })
        assertEquals(setOf("derived_parallel"), rows.map { it.case }.toSet())
    }

    @Test
    fun aManifestWithOnlyAHeaderHasNoRows() {
        assertEquals(emptyList<ConversationRow>(), parseConversationManifest(manifest()))
    }

    @Test
    fun badManifestsFailWithTheirLineNumber() {
        val bad = mapOf(
            "wrong header" to "# c\nnot\tthe\theader",
            "six columns" to manifest(row().substringBeforeLast("\t")),
            "dialect" to manifest(row(dialect = "gemini", file = "gemini/derived_parallel.json")),
            "provenance" to manifest(row(provenance = "recorded")),
            "case prefix" to manifest(row(case = "captured_x", file = "anthropic/captured_x.json")),
            "file" to manifest(row(file = "anthropic/other.json")),
            "tag" to manifest(row(tags = "fancy")),
            "empty model" to manifest(row(model = "")),
            "duplicate" to manifest(row(), row()),
        )
        bad.forEach { (name, text) ->
            val failure = assertThrows(name, IllegalArgumentException::class.java) { parseConversationManifest(text) }
            assertTrue("$name: ${failure.message}", Regex("line \\d+").containsMatchIn(failure.message.orEmpty()))
        }
    }

    @Test
    fun theLineNumberCountsCommentAndBlankLines() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            parseConversationManifest(manifest(row(tags = "fancy")))
        }
        assertTrue(failure.message, failure.message.orEmpty().contains("line 4"))
    }

    @Test
    fun aMissingHeaderIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { parseConversationManifest("# only a comment\n") }
    }

    @Test
    fun canonicalJsonCompactsNormalizesNumbersAndIsIdempotent() {
        val pretty = "{\n  \"a\": 1,\n  \"b\": [true, null]\n}"
        assertNotEquals(pretty, canonicalJson(pretty))
        assertEquals("{\"a\":1,\"b\":[true,null]}", canonicalJson(pretty))
        assertEquals("{\"a\":1000.0}", canonicalJson("{\"a\":1e3}"))
        val compact = "{\"a\":[1,2],\"b\":\"x\"}"
        assertEquals(compact, canonicalJson(compact))
        assertEquals(canonicalJson("{\"a\":1e3}"), canonicalJson(canonicalJson("{\"a\":1e3}")))
    }

    @Test
    fun aConversationParsesIntoItsTurns() {
        val text = conversation(userMessages(), buildJsonObject { put("id", "msg_GOLDEN1") }).toString()
        val turns = parseConversation(text)
        assertEquals(1, turns.size)
        assertEquals(1, turns.single().messages.size)
        assertEquals("msg_GOLDEN1", (turns.single().response["id"] as JsonPrimitive).content)
        assertEquals("ConversationTurn(messages=1)", turns.single().toString())
    }

    @Test
    fun malformedConversationsAreRejected() {
        val ok = buildJsonObject { put("x", 1) }
        val rejected = listOf(
            "{}",
            "[]",
            "{\"turns\":[]}",
            "{\"turns\":{}}",
            buildJsonObject {
                putJsonArray("turns") { add(turnOf(userMessages(), ok)) }
                put("extra", 1)
            }.toString(),
            buildJsonObject {
                putJsonArray("turns") {
                    add(
                        buildJsonObject {
                            put("messages", userMessages())
                            put("response", ok)
                            put("extra", 1)
                        },
                    )
                }
            }.toString(),
            buildJsonObject { putJsonArray("turns") { add(turnOf(JsonArray(emptyList()), ok)) } }.toString(),
            buildJsonObject { putJsonArray("turns") { add(turnOf(ok, ok)) } }.toString(),
            buildJsonObject { putJsonArray("turns") { add(turnOf(userMessages(), userMessages())) } }.toString(),
            buildJsonObject { putJsonArray("turns") { add(turnOf(userMessages(), null)) } }.toString(),
            "not json at all",
        )
        rejected.forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) { parseConversation(text) }
        }
    }

    @Test
    fun hygieneFlagsKeysBearerValuesAndRealIds() {
        val keyInThinking = hygiene(buildJsonObject { put("thinking", "use ${key()} here") })
        assertTrue(keyInThinking.toString(), keyInThinking.isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("text", "Authorization: Bearer abc123") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("signature", "Bearer abc123") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("tool_call_id", "call_abc123") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("id", "toolu_01abc") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("id", "msg_x7") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("note", "see req_9") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("id", "chatcmpl-abc") }).isNotEmpty())
        assertTrue(hygiene(buildJsonObject { put("id", "gen-abc") }).isNotEmpty())
    }

    @Test
    fun hygieneNeverRepeatsTheOffendingText() {
        val violations = hygiene(buildJsonObject { put("text", "${key()} and call_abc123") })
        assertTrue(violations.isNotEmpty())
        violations.forEach {
            assertFalse(it, it.contains(key()))
            assertFalse(it, it.contains("call_abc123"))
        }
    }

    @Test
    fun hygieneAcceptsGoldenIdsAndExemptReasoningText() {
        listOf("call_GOLDEN1", "toolu_GOLDEN2", "msg_GOLDEN1", "chatcmpl-GOLDEN1", "gen-GOLDEN1", "req_GOLDEN1")
            .forEach { id ->
                assertEquals(id, emptyList<String>(), hygiene(buildJsonObject { put("id", id) }))
            }
        val exempt = buildJsonObject {
            put("thinking", "call_abc and toolu_x")
            put("signature", "toolu_x msg_abc req_1 call_zz")
            put("data", "call_abc")
            put("reasoning", "gen-abc then chatcmpl-abc")
            putJsonArray("reasoning_details") {
                add(
                    buildJsonObject {
                        put("text", "call_abc")
                        put("id", "toolu_real")
                    },
                )
            }
        }
        assertEquals(emptyList<String>(), hygiene(exempt))
    }

    @Test
    fun hygieneRefusesAFileThatIsNotJson() {
        assertTrue(conversationHygieneViolations("not json").isNotEmpty())
    }

    @Test
    fun everyManifestRowIsPresentCanonicalHygienicAndWellFormed() {
        conversationRows().forEach { row ->
            val text = conversationText(row)
            assertEquals("${row.case} is not canonical", canonicalJson(text), text.trim())
            assertEquals("${row.case} breaks hygiene", emptyList<String>(), conversationHygieneViolations(text))
            assertTrue("${row.case} has no turns", parseConversation(text).isNotEmpty())
        }
    }

    @Test
    fun everyDialectHasADerivedRow() {
        val derived = conversationRows().filter { it.provenance == "derived" }
        DIALECTS.forEach { dialect ->
            assertTrue("$dialect has no derived row", derived.any { it.dialect == dialect })
        }
    }

    @Test
    fun theKnownTagSetIsClosed() {
        assertEquals(
            setOf(
                "parallel", "zero_arg", "error_result", "interleaved_text", "thinking",
                "redacted_thinking", "reasoning_details", "empty_args_forms", "long_system",
            ),
            KNOWN_TAGS,
        )
        assertEquals(setOf("thinking", "signature", "data", "reasoning", "reasoning_details"), EXEMPT_KEYS)
    }

    @Test
    fun theIdPatternNeedsAWordBoundaryBeforeThePrefix() {
        assertTrue(CONVERSATION_ID_IN_TEXT.containsMatchIn("x call_abc"))
        assertFalse(CONVERSATION_ID_IN_TEXT.containsMatchIn("recall_abc"))
        assertEquals("abc", CONVERSATION_ID_IN_TEXT.find("toolu_abc")!!.groupValues[2])
    }

    @Test
    fun aGoldenWithAnUnknownPlacementOfExemptKeysStillScansIdsElsewhere() {
        val response = buildJsonObject {
            putJsonObject("usage") { put("note", "call_real") }
            put("thinking", "call_real")
        }
        assertEquals(1, hygiene(response).size)
    }
}
