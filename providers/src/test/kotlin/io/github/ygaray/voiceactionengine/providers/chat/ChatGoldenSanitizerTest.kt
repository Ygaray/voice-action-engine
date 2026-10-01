package io.github.ygaray.voiceactionengine.providers.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val REDACTED = "redacted"
private const val SECRET_MARKER = "SECRET-MARKER-9f3a"

private val OPENAI_BODY = """
    {"id":"chatcmpl-AbC123","object":"chat.completion","created":1790000000,"model":"gpt-5.4-mini-2026-03-17",
     "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"refusal":null,
       "tool_calls":[
         {"id":"call_a","type":"function","function":{"name":"log_food","arguments":"{\"items\":[]}"}},
         {"id":"call_b","type":"function","function":{"name":"log_food","arguments":"{\"items\":[1]}"}}]}}],
     "usage":{"prompt_tokens":1200,"completion_tokens":30,"total_tokens":1230,
       "prompt_tokens_details":{"cached_tokens":1024}},
     "service_tier":"default","system_fingerprint":"fp_x"}
""".trimIndent()

private val OPENROUTER_BODY = """
    {"id":"gen-123-abc","provider":"OpenAI","model":"openai/gpt-5.4-mini","object":"chat.completion",
     "created":1790000001,
     "choices":[{"finish_reason":"tool_calls","native_finish_reason":"completed","index":0,
       "message":{"role":"assistant","content":"","refusal":null,"reasoning":null,
         "reasoning_details":[{"type":"reasoning.encrypted","id":"rs_1","format":"openai-responses-v1","index":0}],
         "tool_calls":[{"id":"toolu_77","type":"function","function":{"name":"log_food","arguments":"{}"}}]}}],
     "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,"cost":0.0001,
       "cost_details":{"upstream_inference_cost":0.0001},"prompt_tokens_details":{"cached_tokens":0}}}
""".trimIndent()

private val OPENROUTER_ERROR = """
    {"error":{"message":"Provider returned error: prompt text echoed here","code":429,
      "metadata":{"raw":"raw upstream text","provider_name":"Acme","error_type":"rate_limited"}},
     "user_id":"user_abc"}
""".trimIndent()

// Built at run time so no key-shaped literal sits in the source.
private const val KEY_LIKE ="sk-" + "abcdefghijklmnopqrstuvwxyz"

private val OPENAI_ERROR = """
    {"error":{"message":"Incorrect API key provided: $KEY_LIKE",
      "type":"invalid_request_error","param":null,"code":"invalid_api_key"}}
""".trimIndent()

private val SAMPLES = listOf(OPENAI_BODY, OPENROUTER_BODY, OPENROUTER_ERROR, OPENAI_ERROR)

class ChatGoldenSanitizerTest {

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun sanitized(body: String): JsonObject = parse(ChatGoldenSanitizer.sanitize(body))

    private fun toolCallIds(root: JsonObject): List<String> =
        root["choices"]!!.jsonArray.flatMap { choice ->
            choice.jsonObject["message"]!!.jsonObject["tool_calls"]?.jsonArray.orEmpty()
                .map { it.jsonObject["id"]!!.jsonPrimitive.content }
        }

    @Test
    fun anOpenAiBodyKeepsItsFactsAndLosesItsIdentifiers() {
        val result = sanitized(OPENAI_BODY)
        assertEquals("chatcmpl-GOLDEN", result["id"]!!.jsonPrimitive.content)
        assertEquals(0, result["created"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, result["system_fingerprint"])
        assertFalse(result.containsKey("service_tier"))
        assertEquals(listOf("call_GOLDEN1", "call_GOLDEN2"), toolCallIds(result))
        val original = parse(OPENAI_BODY)
        assertEquals(original["usage"], result["usage"])
        assertEquals(original["model"], result["model"])
        val choice = result["choices"]!!.jsonArray.single().jsonObject
        val originalChoice = original["choices"]!!.jsonArray.single().jsonObject
        assertEquals(originalChoice["finish_reason"], choice["finish_reason"])
        val message = choice["message"]!!.jsonObject
        val originalMessage = originalChoice["message"]!!.jsonObject
        assertEquals(originalMessage["content"], message["content"])
        assertEquals(originalMessage["refusal"], message["refusal"])
        val arguments = message["tool_calls"]!!.jsonArray.map {
            it.jsonObject["function"]!!.jsonObject["arguments"]
        }
        val originalArguments = originalMessage["tool_calls"]!!.jsonArray.map {
            it.jsonObject["function"]!!.jsonObject["arguments"]
        }
        assertEquals(originalArguments, arguments)
    }

    @Test
    fun anOpenRouterBodyKeepsProviderAndReasoningButDropsCost() {
        val result = sanitized(OPENROUTER_BODY)
        val original = parse(OPENROUTER_BODY)
        assertEquals("gen-GOLDEN", result["id"]!!.jsonPrimitive.content)
        assertEquals(original["provider"], result["provider"])
        val choice = result["choices"]!!.jsonArray.single().jsonObject
        val originalChoice = original["choices"]!!.jsonArray.single().jsonObject
        assertEquals(originalChoice["native_finish_reason"], choice["native_finish_reason"])
        assertEquals(
            originalChoice["message"]!!.jsonObject["reasoning_details"],
            choice["message"]!!.jsonObject["reasoning_details"],
        )
        assertEquals(listOf("call_GOLDEN1"), toolCallIds(result))
        val usage = result["usage"]!!.jsonObject
        assertFalse(usage.containsKey("cost"))
        assertFalse(usage.containsKey("cost_details"))
        assertEquals(original["usage"]!!.jsonObject["prompt_tokens"], usage["prompt_tokens"])
        assertEquals(original["usage"]!!.jsonObject["prompt_tokens_details"], usage["prompt_tokens_details"])
    }

    @Test
    fun anOpenRouterErrorBodyRedactsTheTextFieldsAndKeepsTheCodes() {
        val error = sanitized(OPENROUTER_ERROR)["error"]!!.jsonObject
        assertEquals(REDACTED, error["message"]!!.jsonPrimitive.content)
        val metadata = error["metadata"]!!.jsonObject
        assertEquals(REDACTED, metadata["raw"]!!.jsonPrimitive.content)
        assertEquals(REDACTED, metadata["provider_name"]!!.jsonPrimitive.content)
        assertEquals("rate_limited", metadata["error_type"]!!.jsonPrimitive.content)
        assertEquals(429, error["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun anOpenAiErrorBodyRedactsTheMessageAndKeepsTypeAndCode() {
        val result = ChatGoldenSanitizer.sanitize(OPENAI_ERROR)
        val error = parse(result)["error"]!!.jsonObject
        assertEquals(REDACTED, error["message"]!!.jsonPrimitive.content)
        assertEquals("invalid_request_error", error["type"]!!.jsonPrimitive.content)
        assertEquals("invalid_api_key", error["code"]!!.jsonPrimitive.content)
        assertFalse(result.contains("abcdefghijkl"))
    }

    @Test
    fun aUserIdKeyIsDroppedAtAnyDepth() {
        val nested = """{"a":{"b":[{"user_id":"u1","keep":1}]},"user_id":"u2"}"""
        val result = ChatGoldenSanitizer.sanitize(nested)
        assertFalse(result.contains("user_id"))
        val kept = parse(result)["a"]!!.jsonObject["b"]!!.jsonArray.single().jsonObject
        assertEquals(1, kept["keep"]!!.jsonPrimitive.int)
        assertFalse(sanitized(OPENROUTER_ERROR).containsKey("user_id"))
    }

    @Test
    fun aKeyShapedStringOrBearerValueInsideTextIsScrubbed() {
        val body = """{"choices":[{"message":{"content":"use sk-${"a".repeat(24)} or Bearer abc123token"}}]}"""
        val result = ChatGoldenSanitizer.sanitize(body)
        assertEquals(emptyList<String>(), goldenHygieneViolations(result))
        assertFalse(result.contains("abc123token"))
    }

    @Test
    fun anIdLeftInsideTextIsReducedToItsGoldenForm() {
        val body = """{"choices":[{"message":{"content":"see chatcmpl-xyz789 and gen-55 and call_zz"}}]}"""
        val result = ChatGoldenSanitizer.sanitize(body)
        assertEquals(emptyList<String>(), goldenHygieneViolations(result))
        assertTrue(result.contains("chatcmpl-GOLDEN"))
    }

    @Test
    fun theSanitizerIsDeterministicIndentsByTwoAndPassesTheHygieneScan() {
        SAMPLES.forEach { sample ->
            val first = ChatGoldenSanitizer.sanitize(sample)
            assertEquals(first, ChatGoldenSanitizer.sanitize(sample))
            assertEquals(emptyList<String>(), goldenHygieneViolations(first))
            assertTrue(first.lines().any { it.startsWith("  \"") && !it.startsWith("   ") })
            assertTrue(first.lines().all { (it.length - it.trimStart().length) % 2 == 0 })
        }
    }

    @Test
    fun aNonJsonBodyIsRejectedWithoutQuotingIt() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ChatGoldenSanitizer.sanitize("not json $SECRET_MARKER {")
        }
        assertFalse(failure.message.orEmpty().contains(SECRET_MARKER))
        val notObject = assertThrows(IllegalArgumentException::class.java) {
            ChatGoldenSanitizer.sanitize("[\"$SECRET_MARKER\"]")
        }
        assertFalse(notObject.message.orEmpty().contains(SECRET_MARKER))
    }

    @Test
    fun theSystemFingerprintBecomesNullEvenWhenNested() {
        val result = ChatGoldenSanitizer.sanitize("""{"x":{"system_fingerprint":"fp_1","created":5}}""")
        val inner = parse(result)["x"]!!.jsonObject
        assertEquals(JsonNull, inner["system_fingerprint"])
        assertEquals(0, inner["created"]!!.jsonPrimitive.int)
    }
}
