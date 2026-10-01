package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.http.OneShotJsonBody
import io.github.ygaray.voiceactionengine.providers.http.await
import io.github.ygaray.voiceactionengine.providers.http.cleanClient
import io.github.ygaray.voiceactionengine.providers.http.isHeaderSafe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate

private const val OPT_IN_VAR = "VAE_LIVE_CHAT"
private const val CALLS_VAR = "VAE_LIVE_CHAT_CALLS"
private const val OPENAI_KEY_VAR = "OPENAI_API_KEY"
private const val OPENROUTER_KEY_VAR = "OPENROUTER_API_KEY"
private const val RAW_DIR_PROPERTY = "vae.raw.dir"
private const val GOLDEN_DIR_PROPERTY = "vae.golden.dir"

// A syntactically valid credential that no vendor accepts, for the 401 probes.
private const val INVALID_CREDENTIAL = "invalid-credential-for-capture"
private const val CHAT_PATH = "chat/completions"
private const val CALL_TIMEOUT_MILLIS = 60_000L
private const val SUCCESS_MIN = 200
private const val SUCCESS_MAX = 299
private const val NO_VALUE = "-"

// The only models the capture may call: the cheap tool-capable ones named by the capture plan.
internal const val OPENAI_CAPTURE_MODEL = "gpt-5.4-mini"
internal const val ROUTED_CAPTURE_MODEL = "openai/gpt-5.4-mini"
internal const val HAIKU_CAPTURE_MODEL = "anthropic/claude-haiku-4.5"

// Hard ceilings on HTTP requests: one request per call, no retries, counted before each call.
internal const val MAX_HTTP_REQUESTS = 12
internal const val MAX_REQUESTS_PER_VENDOR = 6

private const val TEST_TIMEOUT_MILLIS = 900_000L
private const val MAX_OUTPUT_TOKENS = 512
private const val MIN_LONG_SYSTEM_CHARS = 6_000

internal const val VENDOR_OPENAI = "openai"
internal const val VENDOR_OPENROUTER = "openrouter"

/** What a call is meant to produce, in the manifest's own words, plus the extra live facts it must also show. */
internal class IntendedOutcome(
    val expected: String,
    val absentKeys: List<String> = emptyList(),
    val cacheRead: Boolean = false,
    val requestId: Boolean = false,
    val status: Int? = null,
) {
    override fun toString(): String = "IntendedOutcome($expected)"
}

/**
 * One planned call: [code] selects it (C1..C5, R1..R6), [label] names its captured file, and [intended] is null when
 * the call only records what happens (R5).
 */
internal class PlannedCall(
    val code: String,
    val label: String,
    val vendor: ChatVendor,
    val model: String,
    val request: ModelRequest,
    val invalidKey: Boolean,
    val intended: IntendedOutcome?,
) {
    /** The vendor's name as used in captured file names and the manifest. */
    val vendorName: String
        get() = if (vendor.providerId == ProviderId.OPENAI) VENDOR_OPENAI else VENDOR_OPENROUTER

    override fun toString(): String = "PlannedCall($code $label)"
}

private const val EDIT_ABSENT = "title,items.0.item_id,items.0.completed_at"

private object Intents {
    val logFood = IntendedOutcome("success:tool_use:log_food")
    val repeat = IntendedOutcome("success:tool_use:log_food", cacheRead = true)
    val edit = IntendedOutcome("success:tool_use:edit_list_card", absentKeys = EDIT_ABSENT.split(","))
    val prose = IntendedOutcome("success:end_turn:-")
    val openAiKey = IntendedOutcome("failure:auth", requestId = true, status = 401)
    val routedKey = IntendedOutcome("failure:auth", status = 401)
}

/** The eleven labelled calls, built as data; nothing here touches the network or a key. */
internal object CapturePlan {

    private val parallelProbeVendor = ChatVendor(
        providerId = ChatVendor.OPENROUTER.providerId,
        productionBaseUrl = ChatVendor.OPENROUTER.productionBaseUrl,
        requestIdHeader = ChatVendor.OPENROUTER.requestIdHeader,
        requestIdInBody = ChatVendor.OPENROUTER.requestIdInBody,
        routedModelIds = ChatVendor.OPENROUTER.routedModelIds,
        requireParametersOnForced = ChatVendor.OPENROUTER.requireParametersOnForced,
        parallelToolCallsFalseOnForced = true,
    )

    // A deterministic system text well past the shortest prefix either vendor caches automatically.
    private fun longSystem(): String {
        val sentence = "The assistant turns each spoken food note into one log entry, keeping the user's words. "
        return FIXED_SYSTEM + "\n\n" + sentence.repeat(MIN_LONG_SYSTEM_CHARS / sentence.length + 1)
    }

    private fun logFoodRequest(): ModelRequest = ModelRequest(
        longSystem(),
        listOf(UserMessage("I had two scrambled eggs and a banana for breakfast.")),
        listOf(logFoodTool()),
        ToolChoice.Required("log_food"),
        MAX_OUTPUT_TOKENS,
        CacheDirective(true),
    )

    private fun editRequest(): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage("On card card-7 change the first item's text to oat milk. Set no other field.")),
        listOf(editListCardTool()),
        ToolChoice.Required("edit_list_card"),
        MAX_OUTPUT_TOKENS,
        CacheDirective(true),
    )

    private fun proseRequest(): ModelRequest = ModelRequest(
        FIXED_SYSTEM,
        listOf(UserMessage("Which has more protein, an egg or a banana? Answer in one short sentence.")),
        listOf(logFoodTool()),
        ToolChoice.Auto(),
        MAX_OUTPUT_TOKENS,
        CacheDirective(true),
    )

    private fun openAi(
        code: String,
        label: String,
        request: ModelRequest,
        intended: IntendedOutcome,
        invalidKey: Boolean = false,
    ) = PlannedCall(code, label, ChatVendor.OPENAI, OPENAI_CAPTURE_MODEL, request, invalidKey, intended)

    private fun routed(
        code: String,
        label: String,
        request: ModelRequest,
        intended: IntendedOutcome?,
        vendor: ChatVendor = ChatVendor.OPENROUTER,
        model: String = ROUTED_CAPTURE_MODEL,
        invalidKey: Boolean = false,
    ) = PlannedCall(code, label, vendor, model, request, invalidKey, intended)

    /** All calls in run order. */
    val all: List<PlannedCall> by lazy {
        listOf(
            openAi("C1", "forced_log_food", logFoodRequest(), Intents.logFood),
            openAi("C2", "forced_log_food_repeat", logFoodRequest(), Intents.repeat),
            openAi("C3", "forced_edit", editRequest(), Intents.edit),
            openAi("C4", "auto_prose", proseRequest(), Intents.prose),
            openAi("C5", "invalid_key", proseRequest(), Intents.openAiKey, invalidKey = true),
            routed("R1", "forced_log_food", logFoodRequest(), Intents.logFood),
            routed("R2", "forced_edit", editRequest(), Intents.edit),
            routed("R3", "forced_log_food_repeat", logFoodRequest(), Intents.repeat),
            routed("R4", "invalid_key", proseRequest(), Intents.routedKey, invalidKey = true),
            routed("R5", "parallel_probe", logFoodRequest(), null, vendor = parallelProbeVendor),
            routed("R6", "haiku_route", logFoodRequest(), Intents.logFood, model = HAIKU_CAPTURE_MODEL),
        )
    }

    /** The calls named in [filter] (comma-separated codes, case-insensitive), or all of them when it is blank. */
    fun selected(filter: String?): List<PlannedCall> {
        val wanted = filter.orEmpty().split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return all
        val unknown = wanted.filter { code -> all.none { it.code == code } }
        require(unknown.isEmpty()) { "unknown call codes in $CALLS_VAR: $unknown" }
        return all.filter { it.code in wanted }
    }

    /** One message per broken rule of the plan; empty when the plan is sound. */
    fun violations(calls: List<PlannedCall>): List<String> = buildList {
        if (calls.map { it.code }.toSet().size != calls.size) add("call codes are not unique")
        val files = calls.map { "${it.vendorName}-${it.label}" }
        if (files.toSet().size != files.size) add("captured file names are not unique")
        calls.groupBy { it.vendorName }.forEach { (vendor, group) ->
            if (group.size > MAX_REQUESTS_PER_VENDOR) {
                add("$vendor plans ${group.size} requests, over $MAX_REQUESTS_PER_VENDOR")
            }
        }
        if (calls.size > MAX_HTTP_REQUESTS) add("plan has ${calls.size} requests, over $MAX_HTTP_REQUESTS")
    }
}

/**
 * What one call observed, reduced to facts that are safe to print: the status (null when no answer came), the typed
 * outcome in the manifest's words, and the decoded response when there was one.
 */
private class Observation(
    val call: PlannedCall,
    val status: Int?,
    val outcome: String,
    val requestIdPresent: Boolean,
    val errorType: String?,
    val finish: String?,
    val response: ModelResponse?,
) {
    override fun toString(): String = "Observation(${call.code} $outcome)"

    val toolNames: List<String> get() = response?.message?.toolCalls?.map { it.name }.orEmpty()
}

/** Reads one answer through the production decoder or error parser, exactly as the transport would. */
private fun interpret(call: PlannedCall, status: Int, headerId: String?, body: String?): Observation {
    if (status !in SUCCESS_MIN..SUCCESS_MAX) {
        val info = parseChatError(status, headerId, body, call.vendor.requestIdInBody)
        val outcome = "failure:${info.reason().code}"
        return Observation(call, status, outcome, info.requestId != null, info.errorType, null, null)
    }
    val toolRequired = call.request.toolChoice is ToolChoice.Required
    val decoded = decodeChatResponse(body, headerId, call.model, call.vendor, toolRequired)
    return when (val result = decoded.result) {
        is ModelResult.Success -> {
            val response = result.response
            val names = response.message.toolCalls.joinToString(",") { it.name }.ifEmpty { NO_VALUE }
            val outcome = "success:${response.stopReason.value}:$names"
            Observation(call, status, outcome, response.requestId != null, null, decoded.finishReason, response)
        }
        is ModelResult.Failure -> {
            val details = result.details
            val outcome = "failure:${result.reason.code}"
            val type = details?.providerErrorType
            Observation(call, status, outcome, details?.requestId != null, type, decoded.finishReason, null)
        }
        else -> Observation(call, status, "failure:other", false, null, decoded.finishReason, null)
    }
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

/** The unmet parts of the call's intended outcome, in words that carry no body text; empty when it was met. */
private fun unmetExpectations(observed: Observation): List<String> {
    val call = observed.call
    val intended = call.intended ?: return emptyList()
    val code = call.code
    return buildList {
        if (observed.outcome != intended.expected) {
            add("$code expected ${intended.expected} but got ${observed.outcome}")
        }
        if (intended.status != null && observed.status != intended.status) {
            add("$code expected http ${intended.status} but got ${observed.status}")
        }
        if (intended.requestId && !observed.requestIdPresent) add("$code carried no request id")
        if (intended.cacheRead && (observed.response?.usage?.cacheRead ?: 0L) <= 0L) {
            add("$code read nothing from the cache")
        }
        val arguments = observed.response?.message?.toolCalls?.firstOrNull()?.arguments
        intended.absentKeys.forEach { path ->
            if (arguments == null || resolves(arguments, path)) add("$code holds or cannot check optional key $path")
        }
    }
}

private fun presence(present: Boolean): String = if (present) "present" else "absent"

/** The one printable line per call: ids, statuses, names and counts only, never a body, text or argument value. */
private fun liveLine(observed: Observation): String {
    val call = observed.call
    val usage = observed.response?.usage
    val usageText = if (usage == null) {
        "-"
    } else {
        "in:${usage.inputUncached},cache_read:${usage.cacheRead},cache_write:${usage.cacheWrite},out:${usage.output}"
    }
    return "LIVE_CAPTURE call=${call.code} label=${call.label} vendor=${call.vendorName} model=${call.model} " +
        "http=${observed.status ?: NO_VALUE} error_type=${observed.errorType ?: NO_VALUE} " +
        "request_id=${presence(observed.requestIdPresent)} finish=${observed.finish ?: NO_VALUE} " +
        "tools=${observed.toolNames} usage=$usageText outcome=${observed.outcome}"
}

/** A suggested manifest row for 05-12 to review, in the nine-column order of the golden manifest. */
private fun manifestRow(observed: Observation): String {
    val call = observed.call
    val status = observed.status ?: 0
    val success = status in SUCCESS_MIN..SUCCESS_MAX
    val choice = when {
        !success -> NO_VALUE
        call.request.toolChoice is ToolChoice.Required -> "required"
        else -> "auto"
    }
    val absent = call.intended?.absentKeys?.joinToString(",")?.ifEmpty { null } ?: NO_VALUE
    return listOf(
        "${call.vendorName}_${call.label}_captured",
        call.vendorName,
        "captured",
        "captured/${call.vendorName}-${call.label}.json",
        status.toString(),
        choice,
        observed.outcome,
        absent,
        "captured ${LocalDate.now()} ${call.model} ${call.label}",
    ).joinToString("\t", prefix = "MANIFEST_ROW\t")
}

/**
 * Sends the selected calls one request each and keeps the answers. [keys] maps a vendor name to its key from the
 * environment; the invalid-credential probes never use it. [baseUrlFor] is the vendor's production endpoint in the live
 * run; a key-free test points it at a loopback server.
 */
internal class CaptureRun(
    private val keys: Map<String, String>,
    private val rawDir: File,
    private val goldenDir: File,
    private val baseUrlFor: (ChatVendor) -> String = { it.productionBaseUrl },
) {
    private val client = cleanClient(null, CALL_TIMEOUT_MILLIS, CALL_TIMEOUT_MILLIS)
    private val sent = mutableMapOf<String, Int>()
    val unmet = mutableListOf<String>()

    val requests: Int get() = sent.values.sum()

    fun perVendor(vendor: String): Int = sent[vendor] ?: 0

    fun run(calls: List<PlannedCall>) {
        calls.forEach { call ->
            if (requests + 1 > MAX_HTTP_REQUESTS || perVendor(call.vendorName) + 1 > MAX_REQUESTS_PER_VENDOR) {
                println("LIVE_CAPTURE call=${call.code} label=${call.label} result=skipped_request_ceiling")
                unmet.add("${call.code} skipped by the request ceiling")
            } else {
                capture(call)
            }
        }
    }

    // The count goes up before the request is built, so a call that fails to send still counts.
    private fun capture(call: PlannedCall) {
        sent[call.vendorName] = perVendor(call.vendorName) + 1
        val key = if (call.invalidKey) INVALID_CREDENTIAL else keys.getValue(call.vendorName)
        val bytes = encodeChatRequest(chatCall(call.vendor, call.model, call.request, key), call.vendor)
        val reply = try {
            runBlocking { client.newCall(request(call, key, bytes)).await() }
        } catch (failure: IOException) {
            val cause = failure.javaClass.simpleName
            println("LIVE_CAPTURE call=${call.code} label=${call.label} result=no_answer cause=$cause")
            unmet.add("${call.code} got no answer")
            return
        }
        val headerId = call.vendor.requestIdHeader?.let { reply.headers[it] }
        val observed = interpret(call, reply.code, headerId, reply.body)
        println(liveLine(observed))
        println(manifestRow(observed))
        unmet.addAll(unmetExpectations(observed))
        keep(observed, headerId, reply.body)
    }

    private fun request(call: PlannedCall, key: String, bytes: ByteArray): Request =
        Request.Builder()
            .url(baseUrlFor(call.vendor) + CHAT_PATH)
            .header("Authorization", "Bearer $key")
            .header("content-type", "application/json")
            .post(OneShotJsonBody(bytes))
            .build()

    // Raw bodies stay under build/; only the sanitized copy goes where it can be committed. The sanitized copy must
    // decode to the same outcome, or the committed golden would not replay.
    private fun keep(observed: Observation, headerId: String?, body: String?) {
        val call = observed.call
        val name = "${call.vendorName}-${call.label}.json"
        rawDir.mkdirs()
        File(rawDir, name).writeText(body.orEmpty())
        val clean = try {
            ChatGoldenSanitizer.sanitize(body.orEmpty())
        } catch (refused: IllegalArgumentException) {
            println("LIVE_CAPTURE call=${call.code} golden=refused cause=${refused.javaClass.simpleName}")
            unmet.add("${call.code} body could not be sanitized")
            return
        }
        goldenDir.mkdirs()
        File(goldenDir, name).writeText(clean + "\n")
        val replayed = interpret(call, observed.status ?: 0, headerId, clean)
        if (replayed.outcome != observed.outcome) {
            unmet.add("${call.code} sanitized copy replays as ${replayed.outcome}")
        }
    }
}

/**
 * Opt-in capture of real OpenAI and OpenRouter answers, run only by the liveChatCompletionsCapture task and never by
 * check. It is skipped unless VAE_LIVE_CHAT is 1 and the keys of the selected calls are in the environment. It makes at
 * most 12 HTTP requests (6 per vendor, one per call, no retries) and prints ids, statuses and counts: never a key, a
 * body, message text or tool arguments.
 */
class ChatCompletionsLiveCaptureTest {

    private fun keyFor(vendor: String): String? =
        System.getenv(if (vendor == VENDOR_OPENAI) OPENAI_KEY_VAR else OPENROUTER_KEY_VAR)
            ?.takeIf { it.isNotBlank() && isHeaderSafe(it) }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun boundedCaptureAgainstOpenAiAndOpenRouter() {
        Assume.assumeTrue("opt-in variable not set", System.getenv(OPT_IN_VAR) == "1")
        val calls = CapturePlan.selected(System.getenv(CALLS_VAR))
        val broken = CapturePlan.violations(CapturePlan.all) + CapturePlan.violations(calls)
        check(broken.isEmpty()) { "capture plan is unsound: $broken" }
        val needed = calls.filterNot { it.invalidKey }.map { it.vendorName }.toSet()
        val keys = needed.mapNotNull { vendor -> keyFor(vendor)?.let { vendor to it } }.toMap()
        Assume.assumeTrue("no usable key for $needed", keys.keys == needed)

        val run = CaptureRun(
            keys,
            File(checkNotNull(System.getProperty(RAW_DIR_PROPERTY)) { "$RAW_DIR_PROPERTY is not set" }),
            File(checkNotNull(System.getProperty(GOLDEN_DIR_PROPERTY)) { "$GOLDEN_DIR_PROPERTY is not set" }),
        )
        run.run(calls)

        println(
            "LIVE_CAPTURE requests=${run.requests} ceiling=$MAX_HTTP_REQUESTS " +
                "openai=${run.perVendor(VENDOR_OPENAI)} openrouter=${run.perVendor(VENDOR_OPENROUTER)} " +
                "per_vendor_ceiling=$MAX_REQUESTS_PER_VENDOR",
        )
        assertTrue("HTTP requests over the ceiling: ${run.requests}", run.requests <= MAX_HTTP_REQUESTS)
        assertTrue("live expectations not met: ${run.unmet}", run.unmet.isEmpty())
    }
}
