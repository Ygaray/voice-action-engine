package io.github.ygaray.voiceactionengine.providers

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptObserver
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptObserver
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val OPT_IN_VAR = "VAE_LIVE_PLAN"
private const val ANTHROPIC_KEY_VAR = "ANTHROPIC_API_KEY"
private const val OPENAI_KEY_VAR = "OPENAI_API_KEY"

// The cheapest models the existing live captures already call.
private const val ANTHROPIC_PROBE_MODEL = "claude-haiku-4-5"
private const val OPENAI_PROBE_MODEL = "gpt-5.4-mini"

// Hard ceiling on HTTP requests for the whole probe, counted before each scenario and never retried.
private const val MAX_HTTP_REQUESTS = 8

// A scenario makes at most two provider calls (the plan and one replan), and each may be sent twice.
private const val SCENARIO_WORST_CASE = 4
private const val PROBE_TIMEOUT_MILLIS = 600_000L

private const val PROBE_SYSTEM = "You turn spoken commands into item operations."
private const val CREATE_ITEM = "create_item"
private const val TAG_ITEM = "tag_item"
private const val ITEM_ID_KEY = "item_id"
private const val SCENARIO_REFERENCE = "S1"
private const val SCENARIO_LITERAL = "S2"
private const val REASON_OK = "ok"
private const val REASON_NOT_COMPLETED = "not_completed"
private const val REASON_UNEXPECTED_CALLS = "unexpected_tool_calls"

private class ProbeScenario(val code: String, val transcript: String)

private class ProbeModel(val id: String, val providerId: ProviderId, val key: String, val provider: AiProvider)

/** One scenario's verdict; it holds codes and counts only, never a body, an argument, an id or a transcript. */
private class ProbeVerdict(
    val model: String,
    val scenario: String,
    val verdict: String,
    val reason: String,
    val calls: Int = 0,
    val replans: Int = 0,
    val refBound: Boolean = false,
    val literalKept: Boolean = false,
) {
    fun line(): String =
        "PLAN_PROBE model=$model scenario=$scenario verdict=$verdict reason=$reason calls=$calls replans=$replans " +
            "ref_bound=$refBound literal_kept=$literalKept"
}

/** What the fake executor saw and the ids its create step published, in order. */
private class ProbeLog {
    val tools = mutableListOf<String>()
    val arguments = mutableListOf<JsonObject>()
    val published = mutableListOf<String>()

    fun step(call: Extraction): ToolStep {
        tools.add(call.toolName)
        arguments.add(call.arguments)
        val ids = if (call.toolName == CREATE_ITEM) mapOf(ITEM_ID_KEY to publish()) else emptyMap()
        return ToolStep.Mutation(FakeMutation(call.toolName, StepResult("ok", false, "tok", ids)))
    }

    private fun publish(): String = "it-${published.size + 1}".also { published.add(it) }

    fun argumentsOf(tool: String): List<JsonObject> = arguments.filterIndexed { index, _ -> tools[index] == tool }
}

private fun stringSchema(vararg fields: String): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") { fields.forEach { putJsonObject(it) { put("type", "string") } } }
    putJsonArray("required") { fields.forEach { add(it) } }
}

private fun probeTools(): List<ToolSpec> = listOf(
    ToolSpec(CREATE_ITEM, "Creates one item and returns item_id, the id of the new item.", stringSchema("name"), true),
    ToolSpec(TAG_ITEM, "Adds a tag to an existing item, given its item_id.", stringSchema(ITEM_ID_KEY, "tag"), true),
)

private fun probeTier(log: ProbeLog): PlanThenExecuteStrategy =
    PlanThenExecuteStrategy(StrategyId("plan")) {
        tooling = ToolSpecProvider.fixed(ToolingSnapshot(PROBE_SYSTEM, probeTools(), null))
        executor = ScriptedToolExecutor(null) { call, _ -> log.step(call) }
    }

private fun runScenario(model: ProbeModel, scenario: ProbeScenario, used: AtomicInteger): ProbeVerdict {
    if (used.get() + SCENARIO_WORST_CASE > MAX_HTTP_REQUESTS) {
        return ProbeVerdict(model.id, scenario.code, "NOT_RUN", "budget")
    }
    val log = ProbeLog()
    val outcome = runBlocking {
        commandPipeline {
            tier(probeTier(log))
            provider(model.provider)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(model.providerId, model.id))
            credentials = ScriptedCredentialSource.keys(model.providerId to model.key)
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }.execute(CommandInput(scenario.transcript, "en", null))
    }
    return judge(model, scenario, outcome, log)
}

private fun judge(model: ProbeModel, scenario: ProbeScenario, outcome: CommandOutcome, log: ProbeLog): ProbeVerdict {
    val calls = outcome.trace.attempts.sumOf { it.turns.size }
    val replans = outcome.trace.codes.count { it == TraceCode.PLAN_REPLANNED }
    val done = outcome is CommandOutcome.Completed && !outcome.partial
    val (reason, refBound, literalKept) = when {
        !done -> Triple(REASON_NOT_COMPLETED, false, false)
        scenario.code == SCENARIO_REFERENCE -> judgeReference(log)
        else -> judgeLiteral(outcome, log)
    }
    val passed = reason == REASON_OK || reason == "literal_not_exercised"
    val verdict = if (passed) "PASS" else "FAIL"
    return ProbeVerdict(model.id, scenario.code, verdict, reason, calls, replans, refBound, literalKept)
}

// S1: the model wrote a whole-value reference and the engine resolved it to the id the create step published.
private fun judgeReference(log: ProbeLog): Triple<String, Boolean, Boolean> {
    val tagged = log.argumentsOf(TAG_ITEM).singleOrNull()
    val bound = (tagged?.get(ITEM_ID_KEY) as? JsonPrimitive)?.contentOrNull
    return when {
        log.tools != listOf(CREATE_ITEM, TAG_ITEM) -> Triple(REASON_UNEXPECTED_CALLS, false, false)
        bound == null || bound != log.published.lastOrNull() -> Triple("ref_not_bound", false, false)
        else -> Triple(REASON_OK, true, false)
    }
}

// S2: dictated amounts are never treated as references. literal_kept says whether the model kept the dollar text.
private fun judgeLiteral(outcome: CommandOutcome, log: ProbeLog): Triple<String, Boolean, Boolean> {
    val codes = outcome.trace.codes
    val names = log.argumentsOf(CREATE_ITEM).map { (it["name"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
    val kept = names.isNotEmpty() && names.all { Regex("[\$][0-9]").containsMatchIn(it) }
    return when {
        TraceCode.PLAN_REJECTED in codes -> Triple("plan_rejected", false, kept)
        TraceCode.PLAN_BINDING_UNRESOLVED in codes -> Triple("binding_unresolved", false, kept)
        log.tools != listOf(CREATE_ITEM, CREATE_ITEM) -> Triple(REASON_UNEXPECTED_CALLS, false, kept)
        else -> Triple(if (kept) REASON_OK else "literal_not_exercised", false, kept)
    }
}

/**
 * Opt-in probe of the D-04 binding syntax against the two cheapest models, run only by the livePlanProbe task and
 * never by check. The class name contains Live, so the default test task and both OkHttp matrix legs exclude it.
 *
 * It is skipped unless VAE_LIVE_PLAN is 1 and both ANTHROPIC_API_KEY and OPENAI_API_KEY are set. It makes at most
 * eight HTTP requests, counted before each scenario and never retried, and prints only PLAN_PROBE lines made of codes
 * and counts: never a key, a body, an argument, an id or a transcript.
 */
class PlanBindingLiveProbeTest {

    private fun anthropicModel(key: String, used: AtomicInteger): ProbeModel {
        val provider = AnthropicProvider { attemptObserver = AnthropicAttemptObserver { used.incrementAndGet() } }
        return ProbeModel(ANTHROPIC_PROBE_MODEL, ProviderId.ANTHROPIC, key, provider)
    }

    private fun openAiModel(key: String, used: AtomicInteger): ProbeModel {
        val provider = ChatCompletionsProvider.openAi {
            attemptObserver = ChatCompletionsAttemptObserver { used.incrementAndGet() }
        }
        return ProbeModel(OPENAI_PROBE_MODEL, ProviderId.OPENAI, key, provider)
    }

    @Test(timeout = PROBE_TIMEOUT_MILLIS)
    fun boundedPlanBindingProbe() {
        Assume.assumeTrue("opt-in variable not set", System.getenv(OPT_IN_VAR) == "1")
        val anthropicKey = System.getenv(ANTHROPIC_KEY_VAR).orEmpty()
        val openAiKey = System.getenv(OPENAI_KEY_VAR).orEmpty()
        Assume.assumeTrue("both API keys are needed", anthropicKey.isNotBlank() && openAiKey.isNotBlank())
        val used = AtomicInteger(0)
        val scenarios = listOf(
            ProbeScenario(SCENARIO_REFERENCE, "create an item called alpha and tag it red"),
            ProbeScenario(SCENARIO_LITERAL, "create an item called price \$5.00 and another called fee \$3.50"),
        )

        val verdicts = listOf(anthropicModel(anthropicKey, used), openAiModel(openAiKey, used))
            .flatMap { model -> scenarios.map { runScenario(model, it, used).also { v -> println(v.line()) } } }

        println("PLAN_PROBE requests=${used.get()} limit=$MAX_HTTP_REQUESTS")
        assertTrue("requests over the ceiling", used.get() <= MAX_HTTP_REQUESTS)
        assertTrue("a scenario failed", verdicts.none { it.verdict == "FAIL" })
    }
}
