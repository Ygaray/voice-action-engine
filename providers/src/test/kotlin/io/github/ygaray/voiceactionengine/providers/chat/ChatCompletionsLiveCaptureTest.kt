package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import org.junit.Assume
import org.junit.Test

private const val OPT_IN_VAR = "VAE_LIVE_CHAT"
private const val CALLS_VAR = "VAE_LIVE_CHAT_CALLS"

// The only models the capture may call: the cheap tool-capable ones named by the capture plan.
internal const val OPENAI_CAPTURE_MODEL = "gpt-5.4-mini"
internal const val ROUTED_CAPTURE_MODEL = "openai/gpt-5.4-mini"
internal const val HAIKU_CAPTURE_MODEL = "anthropic/claude-haiku-4.5"

// Hard ceilings on HTTP requests: one request per call, no retries, counted before each call.
internal const val MAX_HTTP_REQUESTS = 12
internal const val MAX_REQUESTS_PER_VENDOR = 6

private const val TEST_TIMEOUT_MILLIS = 600_000L
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
 * Opt-in capture of real OpenAI and OpenRouter answers, run only by the liveChatCompletionsCapture task and never by
 * check. It is skipped unless VAE_LIVE_CHAT is 1.
 */
class ChatCompletionsLiveCaptureTest {

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun boundedCaptureAgainstOpenAiAndOpenRouter() {
        Assume.assumeTrue("opt-in variable not set", System.getenv(OPT_IN_VAR) == "1")
        val calls = CapturePlan.selected(System.getenv(CALLS_VAR))
        val broken = CapturePlan.violations(CapturePlan.all) + CapturePlan.violations(calls)
        check(broken.isEmpty()) { "capture plan is unsound: $broken" }
    }
}
