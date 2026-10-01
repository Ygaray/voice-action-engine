package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.anthropic.anthropicRequest
import io.github.ygaray.voiceactionengine.providers.anthropic.decodeAnthropicResponse
import io.github.ygaray.voiceactionengine.providers.anthropic.encodeAnthropicRequest
import io.github.ygaray.voiceactionengine.providers.chat.ChatVendor
import io.github.ygaray.voiceactionengine.providers.chat.chatCall
import io.github.ygaray.voiceactionengine.providers.chat.decodeChatResponse
import io.github.ygaray.voiceactionengine.providers.chat.encodeChatRequest
import io.github.ygaray.voiceactionengine.providers.http.HttpReply
import io.github.ygaray.voiceactionengine.providers.http.OneShotJsonBody
import io.github.ygaray.voiceactionengine.providers.http.await
import io.github.ygaray.voiceactionengine.providers.http.cleanClient
import io.github.ygaray.voiceactionengine.providers.http.isHeaderSafe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.LocalDate

private const val DIALECT_ANTHROPIC = "anthropic"
private const val DIALECT_OPENAI = "openai"
private const val DIALECT_OPENROUTER = "openrouter"
private const val FAMILY_CHAT = "chat"
private const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/"
private const val ANTHROPIC_PATH = "v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val CHAT_PATH = "chat/completions"
private const val CALL_TIMEOUT_MILLIS = 60_000L
private const val THINKING_CONVERSATION = "A2"
private const val COUNT_ITEMS = "count_items"
private const val LOOKUP_ITEM = "lookup_item"
private const val NO_VALUE = "-"
private const val KEY_MESSAGES = "messages"
private const val KEY_CONTENT = "content"
private const val KEY_CHOICES = "choices"
private const val KEY_MESSAGE = "message"
private const val KEY_TYPE = "type"
private const val KEY_ROLE = "role"
private const val ROLE_ASSISTANT = "assistant"
private const val REASONING_DETAILS = "reasoning_details"
private const val REASONING = "reasoning"
private const val PROVENANCE_CAPTURED = "captured"
private const val FIRST_TURN = 1
private const val SECOND_TURN = 2
private const val TAG_PARALLEL = "parallel"
private const val TAG_ZERO_ARG = "zero_arg"
private const val TAG_ERROR_RESULT = "error_result"
private const val TAG_INTERLEAVED = "interleaved_text"
private const val TAG_THINKING = "thinking"
private const val TAG_REDACTED = "redacted_thinking"
private const val TAG_LONG_SYSTEM = "long_system"

/**
 * One planned conversation: [code] selects it, [dialect] says which wire it speaks (anthropic, openai or openrouter),
 * [maxRequests] is the most requests it may send, and [echoProbeOf] names the conversation whose stored first turn it
 * echoes (a one-request probe) or is null for a normal conversation.
 */
internal class ConversationPlan(
    val code: String,
    val dialect: String,
    val model: String,
    val longSystem: Boolean,
    val maxTokens: Int,
    val maxRequests: Int,
    val echoProbeOf: String? = null,
) {
    override fun toString(): String = code
}

/**
 * The bounded plan of the live multi-turn capture, stated in code: at most 19 requests, each counted before it is
 * sent and never retried. The cheapest model of each route is used; the two Sonnet 5.5 legs are the deliberate
 * exception, because Haiku 4.5 does not think without a thinking parameter, and they are capped at 6 requests together.
 */
internal object ConversationPlans {
    const val MAX_ANTHROPIC_REQUESTS = 6
    const val MAX_CHAT_REQUESTS = 13
    const val MAX_OPENAI_REQUESTS = 4
    const val MAX_OPENROUTER_REQUESTS = 9
    const val MAX_PHASE_REQUESTS = 19

    private const val CONVERSATION_REQUESTS = 3
    private const val PROBE_REQUESTS = 1

    private const val HAIKU = "claude-haiku-4-5"
    private const val SONNET = "claude-sonnet-5-5"
    private const val OPENAI_MINI = "gpt-5.4-mini"
    private const val ROUTED_MINI = "openai/gpt-5.4-mini"
    private const val ROUTED_OSS = "openai/gpt-oss-120b"
    private const val ROUTED_SONNET = "anthropic/claude-sonnet-5.5"

    /** The only models a plan may name. */
    val ALLOWED_MODELS: Set<String> = setOf(HAIKU, SONNET, OPENAI_MINI, ROUTED_MINI, ROUTED_OSS, ROUTED_SONNET)

    private val plain = ConversationScript.MAX_TOKENS
    private val thinking = ConversationScript.THINKING_MAX_TOKENS

    /** The Anthropic conversations: Haiku with a long cached system, and Sonnet 5.5 that must think. */
    val ANTHROPIC: List<ConversationPlan> = listOf(
        ConversationPlan("A1", DIALECT_ANTHROPIC, HAIKU, true, plain, CONVERSATION_REQUESTS),
        ConversationPlan("A2", DIALECT_ANTHROPIC, SONNET, false, thinking, CONVERSATION_REQUESTS),
    )

    /** The Chat conversations: OpenAI, its echo probe, and the three routed ones. */
    val CHAT: List<ConversationPlan> = listOf(
        ConversationPlan("O1", DIALECT_OPENAI, OPENAI_MINI, false, plain, CONVERSATION_REQUESTS),
        ConversationPlan("OP", DIALECT_OPENAI, OPENAI_MINI, false, plain, PROBE_REQUESTS, "O1"),
        ConversationPlan("R1", DIALECT_OPENROUTER, ROUTED_MINI, false, plain, CONVERSATION_REQUESTS),
        ConversationPlan("R2", DIALECT_OPENROUTER, ROUTED_OSS, false, thinking, CONVERSATION_REQUESTS),
        ConversationPlan("R3", DIALECT_OPENROUTER, ROUTED_SONNET, false, thinking, CONVERSATION_REQUESTS),
    )

    /** The ceiling on requests to one provider. */
    fun vendorCeiling(dialect: String): Int = when (dialect) {
        DIALECT_ANTHROPIC -> MAX_ANTHROPIC_REQUESTS
        DIALECT_OPENAI -> MAX_OPENAI_REQUESTS
        DIALECT_OPENROUTER -> MAX_OPENROUTER_REQUESTS
        else -> 0
    }

    /** The family a dialect belongs to: the Messages API alone, or the two Chat Completions providers together. */
    fun familyOf(dialect: String): String = if (dialect == DIALECT_ANTHROPIC) DIALECT_ANTHROPIC else FAMILY_CHAT

    /** The ceiling on requests to one family. */
    fun familyCeiling(family: String): Int =
        if (family == DIALECT_ANTHROPIC) MAX_ANTHROPIC_REQUESTS else MAX_CHAT_REQUESTS

    /** The plans named in [filter] (comma-separated codes, any case), in plan order; all of [plans] if it is blank. */
    fun selected(filter: String?, plans: List<ConversationPlan>): List<ConversationPlan> {
        val wanted = filter.orEmpty().split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return plans
        val unknown = wanted.filter { code -> plans.none { it.code == code } }
        require(unknown.isEmpty()) { "unknown conversation codes: $unknown" }
        return plans.filter { it.code in wanted }
    }

    /** One message per broken rule of [plans]; empty when they are sound. */
    fun violations(plans: List<ConversationPlan>): List<String> = buildList {
        if (plans.map { it.code }.toSet().size != plans.size) add("conversation codes are not unique")
        plans.filter { it.model !in ALLOWED_MODELS }.forEach { add("${it.code} names a model that is not allowed") }
        plans.filter { it.maxRequests < 1 }.forEach { add("${it.code} may send no request") }
        plans.groupBy { it.dialect }.forEach { (dialect, group) ->
            if (group.sumOf { it.maxRequests } > vendorCeiling(dialect)) add("$dialect plans over its request ceiling")
        }
        plans.groupBy { familyOf(it.dialect) }.forEach { (family, group) ->
            if (group.sumOf { it.maxRequests } > familyCeiling(family)) add("$family plans over its request ceiling")
        }
        if (plans.sumOf { it.maxRequests } > MAX_PHASE_REQUESTS) add("plans are over the phase request ceiling")
        plans.filter { it.echoProbeOf != null }.forEach { probe ->
            val source = plans.indexOfFirst { it.code == probe.echoProbeOf }
            if (source < 0 || source > plans.indexOf(probe)) {
                add("${probe.code} needs ${probe.echoProbeOf} planned before it")
            }
        }
    }
}

// What the echo probe needs from its source conversation: the second request as sent, and the first stored turn.
private class Echo(val secondRequest: JsonObject, val storedTurn: JsonObject) {
    override fun toString(): String = "Echo"
}

// The decoded answer of one request, with the raw response object it came from.
private class Heard(val response: ModelResponse, val raw: JsonObject) {
    override fun toString(): String = "Heard(${response.stopReason})"
}

private enum class Step { CONTINUE, COMPLETE, FAILED }

// One conversation in progress.
private class Conversation {
    val messages = mutableListOf<Message>(UserMessage(ConversationScript.USER_PROMPT))
    val recorded = mutableListOf<RecordedTurn>()
    val tags = linkedSetOf<String>()
    var firstTurnTags: Set<String> = emptySet()
    var storedTurn: JsonObject? = null
    var secondRequest: JsonObject? = null

    override fun toString(): String = "Conversation(turns=${recorded.size})"
}

private fun productionBaseUrl(dialect: String): String = when (dialect) {
    DIALECT_ANTHROPIC -> ANTHROPIC_BASE_URL
    DIALECT_OPENAI -> ChatVendor.OPENAI.productionBaseUrl
    else -> ChatVendor.OPENROUTER.productionBaseUrl
}

private fun vendorOf(dialect: String): ChatVendor =
    if (dialect == DIALECT_OPENAI) ChatVendor.OPENAI else ChatVendor.OPENROUTER

private fun wireOf(dialect: String): WireDialect = when (dialect) {
    DIALECT_ANTHROPIC -> AnthropicWire
    DIALECT_OPENAI -> ChatWire(ChatVendor.OPENAI, DIALECT_OPENAI)
    else -> ChatWire(ChatVendor.OPENROUTER, DIALECT_OPENROUTER)
}

// The production encoder with the real key. The conformance bindings hold a fake key, so they are not used here.
private fun encoded(plan: ConversationPlan, request: ModelRequest, key: String): ByteArray =
    if (plan.dialect == DIALECT_ANTHROPIC) {
        encodeAnthropicRequest(anthropicRequest(plan.model, request, key))
    } else {
        val vendor = vendorOf(plan.dialect)
        encodeChatRequest(chatCall(vendor, plan.model, request, key), vendor)
    }

private fun decodedAnswer(plan: ConversationPlan, reply: HttpReply): ModelResponse? {
    val result = if (plan.dialect == DIALECT_ANTHROPIC) {
        decodeAnthropicResponse(reply.body, reply.headers["request-id"], plan.model)
    } else {
        val vendor = vendorOf(plan.dialect)
        val headerId = vendor.requestIdHeader?.let { reply.headers[it] }
        decodeChatResponse(reply.body, headerId, plan.model, vendor, false).result
    }
    return (result as? ModelResult.Success)?.response
}

private fun objectOf(text: String?): JsonObject? =
    try {
        text?.let { Json.parseToJsonElement(it) as? JsonObject }
    } catch (ignored: IllegalArgumentException) {
        null
    }

private fun textOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

// The assistant message object of a Chat answer, as the provider sent it.
private fun chatMessageOf(raw: JsonObject): JsonObject? =
    ((raw[KEY_CHOICES] as? JsonArray)?.firstOrNull() as? JsonObject)?.get(KEY_MESSAGE) as? JsonObject

private fun anthropicBlockTypes(raw: JsonObject): List<String?> =
    (raw[KEY_CONTENT] as? JsonArray).orEmpty().map { textOf((it as? JsonObject)?.get(KEY_TYPE)) }

private fun hasReasoningDetails(raw: JsonObject): Boolean =
    (chatMessageOf(raw)?.get(REASONING_DETAILS) as? JsonArray)?.isNotEmpty() == true

private fun hasThinking(dialect: String, raw: JsonObject): Boolean =
    if (dialect == DIALECT_ANTHROPIC) {
        anthropicBlockTypes(raw).any { it == TAG_THINKING }
    } else {
        !textOf(chatMessageOf(raw)?.get(REASONING)).isNullOrBlank() || hasReasoningDetails(raw)
    }

private fun presence(present: Boolean): String = if (present) "present" else "absent"

private fun usageText(response: ModelResponse?): String {
    val usage = response?.usage ?: return NO_VALUE
    return "in:${usage.inputUncached},cache_read:${usage.cacheRead},cache_write:${usage.cacheWrite},out:${usage.output}"
}

/** The one printable line per request: codes, statuses, counts and presence flags only, never any text. */
private fun turnLine(
    plan: ConversationPlan,
    turn: Int,
    status: Int,
    response: ModelResponse?,
    raw: JsonObject?,
): String {
    val thinking = raw != null && hasThinking(plan.dialect, raw)
    val details = raw != null && hasReasoningDetails(raw)
    return "LIVE_CAPTURE conv=${plan.code} turn=$turn status=$status stop=${response?.stopReason ?: NO_VALUE} " +
        "tool_calls=${response?.message?.toolCalls?.size ?: 0} thinking=${presence(thinking)} " +
        "reasoning_details=${presence(details)} usage=${usageText(response)}"
}

/** The scenario tags one answer shows. */
private fun turnTags(dialect: String, message: AssistantMessage, raw: JsonObject): Set<String> = buildSet {
    val calls = message.toolCalls
    if (calls.size >= 2) add(TAG_PARALLEL)
    if (calls.any { it.name == COUNT_ITEMS }) add(TAG_ZERO_ARG)
    if (calls.any { it.name == LOOKUP_ITEM }) add(TAG_ERROR_RESULT)
    if (calls.isNotEmpty() && message.parts.any { it is AssistantPart.Text }) add(TAG_INTERLEAVED)
    if (hasThinking(dialect, raw)) add(TAG_THINKING)
    if (dialect == DIALECT_ANTHROPIC && anthropicBlockTypes(raw).any { it == TAG_REDACTED }) add(TAG_REDACTED)
    if (hasReasoningDetails(raw)) add(REASONING_DETAILS)
}

private fun manifestRow(row: ConversationRow): String {
    val tags = KNOWN_TAGS.filter { it in row.tags }.joinToString(",").ifEmpty { NO_VALUE }
    return listOf(row.case, row.dialect, row.provenance, row.model, row.file, tags, row.note)
        .joinToString("\t", prefix = "MANIFEST_ROW\t")
}

/**
 * Runs planned conversations against the providers, a request at a time, and keeps the answers. [keys] maps a dialect
 * name to its key from the environment. [baseUrlFor] gives the endpoint of a dialect: the provider in the live run, a
 * loopback server in a key-free test.
 *
 * Every request is counted before it is sent and none is ever retried. A conversation whose worst case would pass a
 * ceiling is skipped. Raw request messages and raw responses go under [rawDir] only; a conversation that completes is
 * sanitized as a whole with one [ConversationSanitizer], re-proved through [replayConversation], and only then written
 * under [goldenDir]. Whatever a conversation did not achieve is in [unmet]; what it showed besides is in [findings].
 * Nothing printed ever holds a key, a header, a body, message text, thinking or a tool argument.
 */
internal class ConversationRecorder(
    private val keys: Map<String, String>,
    private val rawDir: File,
    private val goldenDir: File,
    private val baseUrlFor: (String) -> String = ::productionBaseUrl,
) {
    private val client = cleanClient(null, CALL_TIMEOUT_MILLIS, CALL_TIMEOUT_MILLIS)
    private val sent = HashMap<String, Int>()
    private val echoes = HashMap<String, Echo>()

    /** What a plan expected and did not get, in words that carry no body text. */
    val unmet = mutableListOf<String>()

    /** What the conversations showed that no plan required (parallel calls, cache reads, reasoning details). */
    val findings = mutableListOf<String>()

    /** The requests sent so far. */
    val requests: Int get() = sent.values.sum()

    /** The requests sent so far to one dialect's provider. */
    fun perVendor(dialect: String): Int = sent[dialect] ?: 0

    /** Runs [plans] in order. */
    fun run(plans: List<ConversationPlan>) {
        for (plan in plans) {
            when {
                !fits(plan) -> skip(plan)
                plan.echoProbeOf != null -> probe(plan)
                else -> converse(plan)
            }
        }
    }

    private fun fits(plan: ConversationPlan): Boolean {
        val family = ConversationPlans.familyOf(plan.dialect)
        val inFamily = sent.filterKeys { ConversationPlans.familyOf(it) == family }.values.sum()
        return requests + plan.maxRequests <= ConversationPlans.MAX_PHASE_REQUESTS &&
            inFamily + plan.maxRequests <= ConversationPlans.familyCeiling(family) &&
            perVendor(plan.dialect) + plan.maxRequests <= ConversationPlans.vendorCeiling(plan.dialect)
    }

    private fun skip(plan: ConversationPlan) {
        println("LIVE_CAPTURE skipped=${plan.code} reason=request_ceiling")
        unmet.add("${plan.code} skipped by the request ceiling")
    }

    private fun usableKey(plan: ConversationPlan): String? {
        val key = keys[plan.dialect]?.takeIf { it.isNotBlank() && isHeaderSafe(it) }
        if (key == null) unmet.add("${plan.code} has no usable key")
        return key
    }

    private fun converse(plan: ConversationPlan) {
        val key = usableKey(plan)
        if (key != null) {
            val state = Conversation()
            var outcome = Step.CONTINUE
            var turn = 0
            while (outcome == Step.CONTINUE && turn < plan.maxRequests) {
                turn += 1
                outcome = step(plan, key, turn, state)
            }
            keepEcho(plan, state)
            when (outcome) {
                Step.COMPLETE -> finish(plan, state)
                Step.CONTINUE -> unmet.add("${plan.code} incomplete: still asking for calls after ${plan.maxRequests}")
                Step.FAILED -> Unit
            }
        }
    }

    private fun keepEcho(plan: ConversationPlan, state: Conversation) {
        val second = state.secondRequest
        val stored = state.storedTurn
        if (second != null && stored != null) echoes[plan.code] = Echo(second, stored)
    }

    // One request of a conversation: encode, count, send, keep the raw copy, read the answer.
    private fun step(plan: ConversationPlan, key: String, turn: Int, state: Conversation): Step {
        val request = ConversationScript.request(state.messages.toList(), plan.longSystem, plan.maxTokens)
        val bytes = try {
            encoded(plan, request, key)
        } catch (refused: IllegalArgumentException) {
            unmet.add("${plan.code} turn $turn could not be encoded (${refused.javaClass.simpleName})")
            null
        }
        val body = bytes?.let { objectOf(it.toString(Charsets.UTF_8)) }
        val reply = bytes?.let { post(plan, key, turn, it) }
        reply?.let { save(plan, turn, body?.get(KEY_MESSAGES), it.body) }
        val heard = reply?.let { hear(plan, turn, it) }
        return if (body != null && heard != null) advance(plan, turn, body, heard, state) else Step.FAILED
    }

    // Counts the request first, so one that fails to send still counts.
    private fun post(plan: ConversationPlan, key: String, turn: Int, bytes: ByteArray): HttpReply? {
        sent[plan.dialect] = perVendor(plan.dialect) + 1
        return try {
            runBlocking { client.newCall(httpRequest(plan, key, bytes)).await() }
        } catch (failure: IOException) {
            println("LIVE_CAPTURE conv=${plan.code} turn=$turn result=no_answer cause=${failure.javaClass.simpleName}")
            unmet.add("${plan.code} turn $turn got no answer")
            null
        }
    }

    private fun httpRequest(plan: ConversationPlan, key: String, bytes: ByteArray): Request {
        val builder = Request.Builder().header("content-type", "application/json").post(OneShotJsonBody(bytes))
        return if (plan.dialect == DIALECT_ANTHROPIC) {
            builder.url(baseUrlFor(plan.dialect) + ANTHROPIC_PATH)
                .header("x-api-key", key)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .build()
        } else {
            builder.url(baseUrlFor(plan.dialect) + CHAT_PATH).header("Authorization", "Bearer $key").build()
        }
    }

    // The raw request messages and the raw response, kept under the raw directory only.
    private fun save(plan: ConversationPlan, turn: Int, messages: JsonElement?, response: String?) {
        val dir = File(rawDir, "${plan.dialect}/${plan.code}")
        dir.mkdirs()
        File(dir, "turn-$turn.messages.json").writeText(messages.toString())
        File(dir, "turn-$turn.response.json").writeText(response.orEmpty())
    }

    // Prints the line for this request and returns the decoded answer, or null (recorded as unmet) when it is unusable.
    private fun hear(plan: ConversationPlan, turn: Int, reply: HttpReply): Heard? {
        val raw = if (reply.isSuccessful) objectOf(reply.body) else null
        val response = raw?.let { decodedAnswer(plan, reply) }
        println(turnLine(plan, turn, reply.code, response, raw))
        if (raw == null || response == null) {
            unmet.add("${plan.code} turn $turn status ${reply.code} gave no usable answer")
        }
        return if (raw != null && response != null) Heard(response, raw) else null
    }

    private fun advance(plan: ConversationPlan, turn: Int, body: JsonObject, heard: Heard, state: Conversation): Step {
        val response = heard.response
        state.recorded += RecordedTurn(body[KEY_MESSAGES] as JsonArray, heard.raw)
        observe(plan, turn, heard, state)
        if (turn == FIRST_TURN && plan.dialect != DIALECT_ANTHROPIC) state.storedTurn = chatMessageOf(heard.raw)
        if (turn == SECOND_TURN) state.secondRequest = body
        return when {
            response.stopReason == StopReason.MAX_TOKENS -> {
                unmet.add("${plan.code} turn $turn was truncated at the token limit")
                Step.FAILED
            }
            response.message.toolCalls.isEmpty() -> Step.COMPLETE
            else -> answerCalls(plan, turn, response.message, state)
        }
    }

    private fun answerCalls(plan: ConversationPlan, turn: Int, message: AssistantMessage, state: Conversation): Step =
        try {
            val results = ConversationScript.results(message)
            state.messages += message
            state.messages += results
            Step.CONTINUE
        } catch (refused: IllegalArgumentException) {
            val cause = refused.javaClass.simpleName
            unmet.add("${plan.code} turn $turn asked for calls that cannot be answered ($cause)")
            Step.FAILED
        }

    private fun observe(plan: ConversationPlan, turn: Int, heard: Heard, state: Conversation) {
        val tags = turnTags(plan.dialect, heard.response.message, heard.raw)
        if (turn == FIRST_TURN) state.firstTurnTags = tags
        state.tags += tags
        if (TAG_PARALLEL in tags) finding("${plan.code} turn $turn made parallel tool calls")
        if (turn > FIRST_TURN && heard.response.usage.cacheRead > 0L) {
            finding("${plan.code} turn $turn read from the cache")
        }
        if (REASONING_DETAILS in tags) finding("${plan.code} turn $turn carried reasoning_details")
    }

    private fun finding(text: String) {
        findings.add(text)
        println("LIVE_CAPTURE finding=$text")
    }

    // A complete conversation becomes a golden only after it is sanitized whole and its copy replays.
    private fun finish(plan: ConversationPlan, state: Conversation) {
        if (plan.code == THINKING_CONVERSATION && TAG_THINKING !in state.firstTurnTags) {
            unmet.add("${plan.code} turn 1 carried no thinking block")
        } else {
            val text = sanitized(plan, state)
            if (text != null) write(plan, state, text)
        }
    }

    private fun sanitized(plan: ConversationPlan, state: Conversation): String? =
        try {
            ConversationSanitizer().conversation(state.recorded)
        } catch (refused: IllegalArgumentException) {
            println("LIVE_CAPTURE refused=${plan.code} stage=sanitize cause=${refused.javaClass.simpleName}")
            unmet.add("${plan.code} conversation could not be sanitized")
            null
        }

    private fun write(plan: ConversationPlan, state: Conversation, text: String) {
        val case = "${PROVENANCE_CAPTURED}_${plan.code.lowercase()}"
        val tags = state.tags + if (plan.longSystem) setOf(TAG_LONG_SYSTEM) else emptySet()
        val note = "captured ${LocalDate.now()}: ${state.recorded.size} turns, ${state.recorded.size} requests"
        val file = "${plan.dialect}/$case.json"
        val row = ConversationRow(case, plan.dialect, PROVENANCE_CAPTURED, plan.model, file, tags, note)
        val violations = replayConversation(wireOf(plan.dialect), row, parseConversation(text)).violations
        if (violations.isEmpty()) {
            val target = File(goldenDir, row.file)
            target.parentFile.mkdirs()
            target.writeText(text + "\n")
            println(manifestRow(row))
        } else {
            unmet.add("${plan.code} sanitized copy does not replay: $violations")
        }
    }

    // One request: the source conversation's second request, with its assistant element replaced by the unfiltered
    // message the provider first sent. Whether that is accepted is a finding; nothing is written but the raw copy.
    private fun probe(plan: ConversationPlan) {
        val echo = echoes[plan.echoProbeOf]
        val key = usableKey(plan)
        when {
            key == null -> Unit
            echo == null -> unmet.add("${plan.code} has no second request of ${plan.echoProbeOf} to echo")
            else -> sendProbe(plan, key, echo)
        }
    }

    private fun sendProbe(plan: ConversationPlan, key: String, echo: Echo) {
        val messages = echo.secondRequest[KEY_MESSAGES] as JsonArray
        val at = messages.indexOfFirst { textOf((it as? JsonObject)?.get(KEY_ROLE)) == ROLE_ASSISTANT }
        if (at < 0) {
            unmet.add("${plan.code} found no assistant message to replace")
        } else {
            val replaced = JsonArray(
                messages.mapIndexed { index, message -> if (index == at) echo.storedTurn else message },
            )
            val body = JsonObject(echo.secondRequest + (KEY_MESSAGES to replaced))
            val reply = post(plan, key, FIRST_TURN, body.toString().toByteArray(Charsets.UTF_8))
            if (reply != null) {
                save(plan, FIRST_TURN, replaced, reply.body)
                println(turnLine(plan, FIRST_TURN, reply.code, null, null))
                val verdict = if (reply.isSuccessful) "accepted" else "rejected"
                finding("${plan.code} echo of the unfiltered stored message was $verdict with status ${reply.code}")
            }
        }
    }
}
