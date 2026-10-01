package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val KEY_MODEL = "model"
private const val KEY_MESSAGES = "messages"
private const val KEY_TOOLS = "tools"
private const val KEY_TOOL_CHOICE = "tool_choice"
private const val KEY_PARALLEL_TOOL_CALLS = "parallel_tool_calls"
private const val KEY_REASONING_EFFORT = "reasoning_effort"
private const val KEY_PROVIDER = "provider"
private const val KEY_REQUIRE_PARAMETERS = "require_parameters"
private const val KEY_ROLE = "role"
private const val KEY_CONTENT = "content"
private const val KEY_TYPE = "type"
private const val KEY_FUNCTION = "function"
private const val KEY_NAME = "name"
private const val ROLE_SYSTEM = "system"
private const val ROLE_USER = "user"
private const val CHOICE_AUTO = "auto"

/**
 * Encodes one request as the Chat Completions body.
 *
 * The bytes are a pure function of the request. Top-level keys are always written as `model`, `messages`, `tools`,
 * `tool_choice`, `parallel_tool_calls`, `reasoning_effort`, then exactly one output-limit key, then `provider`, so the
 * start of the body (model, system prompt and sorted tools) is the same for every call that shares them, which is what
 * automatic prompt caching needs. Per-request content only appears under `messages`. Nothing outside those keys is
 * sent.
 *
 * The parallel tool-call switch goes off for a forced, strict or single-call request when the vendor does that and the
 * model accepts the switch.
 *
 * A tool is marked strict only when the engine's strict decision holds and the model is an OpenAI model; that tool's
 * schema is the stripped copy. Every other tool goes out without a `strict` key and with the app's schema untouched.
 *
 * A required tool is sent as a named choice when the model can be forced to call it. Otherwise the choice is `auto` and
 * one line naming the tool closes the last user message; the system prompt and the tools stay as they are. The line
 * is written for a single-turn call: in a multi-turn tool loop the history ends with assistant and tool messages, so
 * the line closes an earlier user turn rather than being the last thing the model reads.
 */
internal fun encodeChatRequest(call: ProviderRequest, vendor: ChatVendor): ByteArray {
    val request = call.request
    val tools = request.tools.sortedBy { it.name }
    val rules = ChatModels.wireRules(vendor, call.model)
    val required = request.toolChoice as? ToolChoice.Required
    val named = required?.takeIf { call.capabilities.supportsForcedToolChoice }
    val reshaped = required != null && named == null
    val strictNames = strictToolNames(tools, vendor, call.model)
    val body = buildJsonObject {
        put(KEY_MODEL, call.model)
        put(KEY_MESSAGES, conversation(call, vendor, required?.takeIf { reshaped }))
        if (tools.isNotEmpty()) {
            put(KEY_TOOLS, encodeTools(tools, strictNames))
            put(KEY_TOOL_CHOICE, encodeToolChoice(named))
            val oneCall = required != null || strictNames.isNotEmpty() || request.singleToolCall
            if (vendor.parallelToolCallsFalseOnForced && rules.acceptsParallelToolCalls && oneCall) {
                put(KEY_PARALLEL_TOOL_CALLS, false)
            }
            rules.reasoningEffortWithTools?.let { put(KEY_REASONING_EFFORT, it) }
        }
        put(rules.tokenParam, maxOf(request.maxTokens, rules.minTokens))
        if (vendor.requireParametersOnForced && named != null) {
            putJsonObject(KEY_PROVIDER) { put(KEY_REQUIRE_PARAMETERS, true) }
        }
    }
    return Json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
}

// The system prompt leads when there is one; the instruction line, when the call was reshaped, closes the last user
// turn.
private fun conversation(call: ProviderRequest, vendor: ChatVendor, instructed: ToolChoice.Required?): JsonArray {
    val messages = encodeChatMessages(call, vendor).withInstruction(instructed?.let(::requiredToolInstruction))
    val system = call.request.system
    if (system.isBlank()) return messages
    return buildJsonArray {
        add(
            buildJsonObject {
                put(KEY_ROLE, ROLE_SYSTEM)
                put(KEY_CONTENT, system)
            },
        )
        messages.forEach { add(it) }
    }
}

private fun strictToolNames(tools: List<ToolSpec>, vendor: ChatVendor, model: String): Set<String> =
    if (ChatModels.routesToOpenAi(vendor, model)) {
        tools.filter(::effectiveChatStrict).map { it.name }.toSet()
    } else {
        emptySet()
    }

private fun encodeTools(tools: List<ToolSpec>, strictNames: Set<String>): JsonArray = buildJsonArray {
    tools.forEach { tool ->
        val strict = tool.name in strictNames
        add(
            buildJsonObject {
                put(KEY_TYPE, KEY_FUNCTION)
                putJsonObject(KEY_FUNCTION) {
                    put(KEY_NAME, tool.name)
                    put("description", tool.description)
                    // A non-strict tool carries the app's own schema object: its key order is part of the cached
                    // prefix.
                    put("parameters", if (strict) stripForChatStrict(tool.inputSchema) else tool.inputSchema)
                    if (strict) put("strict", true)
                }
            },
        )
    }
}

private fun encodeToolChoice(named: ToolChoice.Required?): JsonElement =
    if (named == null) {
        JsonPrimitive(CHOICE_AUTO)
    } else {
        buildJsonObject {
            put(KEY_TYPE, KEY_FUNCTION)
            putJsonObject(KEY_FUNCTION) { put(KEY_NAME, named.toolName) }
        }
    }

private fun requiredToolInstruction(choice: ToolChoice.Required): String =
    "Call the ${choice.toolName} tool with your result."

// The line goes after a blank line at the very end of the last user message's text.
private fun JsonArray.withInstruction(instruction: String?): JsonArray {
    val target = indexOfLast { ((it as? JsonObject)?.get(KEY_ROLE) as? JsonPrimitive)?.contentOrNull == ROLE_USER }
    if (instruction == null || target < 0) return this
    return buildJsonArray {
        this@withInstruction.forEachIndexed { index, message ->
            add(if (index == target) (message as JsonObject).withTrailingText(instruction) else message)
        }
    }
}

private fun JsonObject.withTrailingText(instruction: String): JsonObject = buildJsonObject {
    this@withTrailingText.forEach { (key, value) ->
        val text = (value as? JsonPrimitive)?.takeIf { key == KEY_CONTENT && it.isString }?.content
        put(key, if (text != null) JsonPrimitive("$text\n\n$instruction") else value)
    }
}
