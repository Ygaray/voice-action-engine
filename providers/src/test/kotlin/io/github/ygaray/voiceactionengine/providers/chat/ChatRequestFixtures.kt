package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Synthetic tools, a request builder and the golden loader shared by the Chat Completions request tests. */

internal const val FIXED_SYSTEM = "You turn short spoken notes into tool calls. Keep every answer brief."

/** A closed schema in which every property is required, so the engine may send it strict. Optionals are nullable. */
internal fun logFoodTool(strict: Boolean? = null): ToolSpec = ToolSpec(
    name = "log_food",
    description = "Logs the foods the user ate.",
    inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                put("minItems", 1)
                put("uniqueItems", true)
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "string")
                            put("minLength", 1)
                        }
                        putJsonObject("quantity") {
                            put("type", "number")
                            put("exclusiveMinimum", 0)
                        }
                        putJsonObject("unit") { putJsonArray("type") { add("string"); add("null") } }
                        putJsonObject("confidence") {
                            put("type", "number")
                            put("minimum", 0)
                            put("maximum", 1)
                        }
                    }
                    putJsonArray("required") { add("name"); add("quantity"); add("unit"); add("confidence") }
                    put("additionalProperties", false)
                }
            }
            putJsonObject("target_date") {
                putJsonArray("type") { add("string"); add("null") }
                put("format", "date")
            }
            putJsonObject("source") {
                putJsonArray("type") { add("string"); add("null") }
                put("format", "uri")
            }
        }
        putJsonArray("required") { add("items"); add("target_date"); add("source") }
        put("additionalProperties", false)
    },
    strict = strict,
)

/** A closed schema with an optional property at the root and below it: strict mode would make the model fill them. */
internal fun editListCardTool(strict: Boolean? = null): ToolSpec = ToolSpec(
    name = "edit_list_card",
    description = "Edits one list card.",
    inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("card_id") { put("type", "string") }
            putJsonObject("title") {
                put("type", "string")
                put("minLength", 1)
            }
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("item_id") { put("type", "string") }
                        putJsonObject("text") { put("type", "string") }
                        putJsonObject("completed_at") {
                            putJsonArray("type") { add("string"); add("null") }
                            put("format", "date-time")
                        }
                    }
                    putJsonArray("required") { add("text") }
                    put("additionalProperties", false)
                }
            }
        }
        putJsonArray("required") { add("card_id") }
        put("additionalProperties", false)
    },
    strict = strict,
)

/** A call for [model] carrying the table capabilities the real provider would report for it. */
internal fun chatCall(
    vendor: ChatVendor,
    model: String,
    request: ModelRequest,
    key: String = "sk-test-key",
): ProviderRequest = ProviderRequest(
    model,
    request,
    Credential(vendor.providerId, key),
    ChatModels.capabilities(vendor, model),
)

/** The compact serialization of the case named [case] in `golden/chat/requests/<file>.json`. */
internal fun goldenRequest(file: String, case: String): String {
    val path = "/golden/chat/requests/$file.json"
    val text = checkNotNull(ChatEncoderTest::class.java.getResourceAsStream(path)) { "missing golden $path" }
        .use { it.readBytes().toString(Charsets.UTF_8) }
    val cases = Json.parseToJsonElement(text) as JsonObject
    return Json.encodeToString(JsonObject.serializer(), checkNotNull(cases[case] as? JsonObject) { "no case $case" })
}

/** The names of all cases in `golden/chat/requests/<file>.json`, in file order. */
internal fun goldenCaseNames(file: String): List<String> {
    val path = "/golden/chat/requests/$file.json"
    val text = checkNotNull(ChatEncoderTest::class.java.getResourceAsStream(path)) { "missing golden $path" }
        .use { it.readBytes().toString(Charsets.UTF_8) }
    return (Json.parseToJsonElement(text) as JsonObject).keys.toList()
}
